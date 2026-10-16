# Flash Sale — HLD (Hello Interview article, simplified)

> Follows the Hello Interview "Flash Sale" breakdown. Lines starting with **Say:** are what you speak in the interview.

---

## Part 1: Understand It First (2-minute read)

**The example:** Nike releases **10,000 pairs** of a limited-edition shoe at **noon**. **Tens of millions** of users try to buy within seconds.
We design only this part: letting users **fairly compete** for the limited stock and **buy without ever overselling**.

**The key idea — reserve first, pay later**
- Payment goes through a third party (Stripe) and takes **seconds**.
- You **can't hold inventory in an open DB transaction** for that long.
- So: **Reserve** = take one pair off the shelf **immediately**, with a timer (e.g. 10 min) → **Purchase** = pay afterwards.
- If the user doesn't pay in time, the reservation **expires** and the pair goes back.
- This "reserve" step is the requirement candidates most often miss.

🌍 **Shop picture:** you pick the shoe off the shelf and hold it while you walk to the cash counter. If you leave without paying, the shoe goes back on the shelf.

---

## Part 2: The Interview Answer

### 1. Requirements

**Functional (core)**
1. Users can **view** the flash sale item.
2. Users can **secure/reserve** an available unit **for a limited time** while the sale is active.
3. Users who reserved a unit can **complete payment** to purchase it.

**Out of scope:** browsing/searching the catalog · shipping, returns, refunds, order history · admins creating/managing sales.

**Non-functional (core)**
1. **Prevent overselling**, even when many people try to claim the same unit at the same moment.
2. **Handle millions of users** arriving within seconds of the sale starting.
3. **Fairness** — everyone gets a fair chance at the limited inventory.

**Out of scope:** GDPR/data protection · fault tolerance · secure payment transactions.

**Whiteboard:**
```
Functional                         Non-functional
1. View sale item                  1. Correctness under contention (no oversell)
2. Reserve item, limited time      2. Scale to ~10M users for the same product
3. Purchase reserved item          3. Fairness
```

### 2. The Set Up — plan the approach
**Say:** "I'll build the design one functional requirement at a time to get a working end-to-end system, then use the non-functional requirements — overselling, scale and fairness — to drive the deep dives."

---

### 3. Core Entities
| Entity | What it is |
|---|---|
| **Product** | the shoe: details, price, sale start/end, number of units available |
| **Reservation** | a temporary claim on one unit while the user checks out: user, product, expiry time, status |
| **Purchase** | a successful purchase: user, product, reservation, amount, payment status |
| **User** | the person trying to buy |

---

### 4. API
```
GET  /products/:productId                      -> Product
POST /products/:productId/reservations         -> Reservation
POST /reservations/:reservationId/purchases    -> Purchase
```
- **GET product** → everything to render the page: details, price, sale window, units available.
- **POST reservation** → if stock is available, returns `reservationId`, `productId`, `expiresAt` (client shows a countdown). This is **not** a purchase yet.
- **POST purchase** → charges the user and turns the reservation into a purchase. Fails if the reservation expired or payment fails.

---

### 5. High-Level Design

#### FR1 — View the item
```
Client → API Gateway → Sale Service → Database (Product: productId, inventoryCount, price, saleStartTime, saleEndTime)
```
- **Client** (web/app) → **API Gateway** (routing + auth, rate limiting, logging) → **Sale Service** (handles sale APIs) → **Postgres** (chosen for **ACID** guarantees, needed for correctness).
- For now, remaining stock is an `inventoryCount` column on the Product row.

#### FR2 — Reserve a unit for a limited time
Add a **Reservation** table: `reservationId, productId, quantity, userId, expiresAt`.

Flow:
1. Client → `POST /products/:productId/reservations`.
2. Sale Service **opens a transaction**, checks the product is on sale and has stock.
3. **Inserts a Reservation** with `expiresAt` ≈ 10 minutes ahead.
4. **Decrements `inventoryCount`** and **commits**.
5. Returns the reservation.

**Say:** "Steps 2 to 4 must be one transaction. If the check and the decrement are separate, two users can both see the last unit as available and both get a reservation — that's overselling."

**Expiry (simple version):** a **cron job** runs periodically, finds ACTIVE reservations past `expiresAt`, marks them **EXPIRED**, and **adds the unit back** to `inventoryCount` — both writes in **one transaction** (otherwise we lose inventory).
Trade-off: a unit may take a little while to come back, depending on how often the job runs. Fine for now; fixed in the deep dives.

#### FR3 — Pay for the reserved unit
Add a **Purchase** table: `purchaseId, reservationId, productId, userId, status (PENDING | COMPLETE | FAILED), createdAt`.
Reservation gets `status: ACTIVE | PURCHASED | EXPIRED`. Payments go through **Stripe**, so we never handle card details.

Flow:
1. Client → `POST /reservations/:reservationId/purchases`.
2. Sale Service checks the reservation **exists and hasn't expired**.
3. Creates a Purchase = **PENDING**.
4. Creates a Stripe **PaymentIntent** → returns the **client secret** to the client.
5. Client sends card details **directly to Stripe** and confirms.
6. Stripe sends a **webhook** (Stripe calls our API when the payment is final — no polling Stripe).
7. **Success** → Purchase COMPLETE + Reservation PURCHASED. **Failure** → Purchase FAILED; reservation stays active until it expires, so the user can retry.

**Telling the user they got the shoe:** our record only becomes final when the webhook arrives. Simplest: the client **polls** `GET /purchases/:purchaseId` about every second until the status is final. Only users holding reservations poll (a few thousand, not millions), so it's cheap. **SSE** also works.
*(Pattern: real-time updates — few clients, short wait → polling is enough.)*

```
Client → API Gateway → Sale Service → Database (Product, Reservation, Purchase)
                            │  ▲
              create payment│  │webhook
                            ▼  │
                         Stripe
                    + Expiration Cron
```
All three functional requirements are now met. It breaks under tens of millions of users — that's what the deep dives fix.

---

### 6. Deep Dives

#### Deep dive 1 — Consistency under extreme contention ⭐
**Correct but slow:** a **conditional update** prevents going below zero:
```sql
UPDATE Product
SET inventoryCount = inventoryCount - 1
WHERE productId = ? AND inventoryCount > 0;
```
1 row updated → you got a unit. 0 rows → sold out. Do it in the same transaction as creating the Reservation.

**The problem is contention, not correctness.** Millions of requests update the **same Product row**. Each decrement depends on the previous one, so the DB **serialises** them — one at a time, waiting on the row lock. More app servers don't help; they all queue on the same row → a **hot spot**.

🌍 **Store picture:** 10,000 pairs and a long line. A smart shop opens **10 registers** with 1,000 pairs each. Our single row is **one register** — however many cashiers you hire.

**Goal:** never give out more reservations than inventory, **while** handling extreme contention.

**The two approaches the article compares:**
| Approach | Idea | Trade-off |
|---|---|---|
| **Redis** as the serialization point | keep the count in Redis and claim units there (atomic, in-memory) | **much faster**, but still **one hot key**, and the source of truth is **split** between Redis and the DB |
| ✅ **ReservationUnit rows** (chosen) | store inventory as **separate rows — one per unit** (`productId, unitId`) that can be **locked independently** | many reservations proceed **at the same time**, and the claim + the reservation stay in **one DB transaction** |

**How a request claims a unit:** pick **any free unit row** that no one else has locked, mark it reserved, create the Reservation, commit — all in one transaction. In Postgres this is done with `SELECT … FOR UPDATE SKIP LOCKED` ("give me a row nobody is holding right now"), so requests don't wait on each other.

**Say:** "Instead of one counter row, I store 10,000 unit rows. Each request locks a different free row, so reservations run in parallel instead of in a single line — and the claim and reservation still commit together, so we can never oversell."
*(Pattern: dealing with contention — atomic claims, row locks, spreading contention across many rows.)*

#### Deep dive 2 — Releasing expired reservations quickly
**Problem with the cron:** a reservation expires at **12:10:01**, but the job only runs at **12:10:10** → the unit is stuck for **9 seconds**, and users may be told "sold out" while stock actually exists. In a flash sale, every unit counts → it must be available **the moment** it expires.

**Direction:** stop depending on when a background job happens to run — make an expired reservation **immediately claimable again** (e.g. the claim step treats a unit whose reservation has passed `expiresAt` as free), and keep a background job only for tidying up.

#### Deep dive 3 — Millions of users arriving at once ⭐
Even without the hot row, the DB has a **physical limit**. Example: load testing shows it safely handles **~10,000 reservation transactions/sec**. If hundreds of thousands or millions try at once, requests pile up, latency climbs, timeouts follow.
**No clever query fixes this** — it's a capacity limit. (Redis would raise the ceiling, but there's still a ceiling.)

**So control how fast users reach the reservation system → admission control.**
*(Pattern: scaling writes — partition writes across rows (done with ReservationUnit) and absorb bursts with queues / load shedding, which is the waiting room's job.)*

**Waiting room**
- Users join a **Waiting Room Service** (state in **Redis**) instead of hitting the reservation API directly.
- The service **admits users at a rate the reservation system can handle**.
- Admitted users get a **signed admission token**; only users with a valid token can call the reservation endpoint → no skipping ahead.

#### Deep dive 4 — Fair access to limited inventory
Once we decide who goes first, we're responsible for making it **fair**.

| Issue | Fix |
|---|---|
| **Pure FIFO** turns the first milliseconds into a race of network speed and bots | open the waiting room **before** the sale, hold everyone until **noon**, then **randomly order** everyone already waiting. Joining at 11:55 = no advantage over 11:59:59. People arriving **after** the start join the back in **FIFO** order |
| One person taking multiple places | require an **authenticated account**, **one active queue entry per user per sale** |
| Bots | **CAPTCHA** / bot challenge before joining |
| Skipping the line | the **signed admission token** — only users admitted by the Waiting Room Service can reserve |
| Waiting room **sharded across multiple Redis instances** | ordering must still be **global** — each user gets an **ordering value**, so the admission controller keeps the random pre-sale order, then FIFO, across all shards |

**Say:** "Being there when the sale starts matters; racing to be the first request through the door does not."

---

### 7. Final Design
```
Client → API Gateway ─┬─► Waiting Room Service ──► WaitingRoom (Redis) × N shards
                      │        (admits users → signed admission token)
                      │
                      └─► Sale Service ──► Database
                                │           Product (productId, inventoryCount, price, saleStartTime, saleEndTime)
                                │           ReservationUnit (productId, unitId)
                                │           Reservation (reservationId, productId, quantity, userId, expiresAt,
                                │                        status: ACTIVE | PURCHASED | EXPIRED)
                                │           Purchase (purchaseId, reservationId, productId, userId,
                                │                     status: PENDING | COMPLETE | FAILED, createdAt)
                                ├─► External Payment (Stripe) — create payment
                                └─◄ Stripe webhook
```

---

### 8. What's expected at each level
**Mid-level:** a complete end-to-end design (view → reserve → purchase) **without overselling**; understand that **check and decrement must be atomic**. The interviewer may guide you toward hot-row contention, admission control or expiry. A simple cleanup job is enough; **proactively reaching `SKIP LOCKED` or a waiting room is a bonus.**
*(The senior/staff expectations were cut off in your copy. As SDE II, aim to raise the hot row, `SKIP LOCKED`/unit rows, faster expiry and the waiting room yourself.)*

---

## Part 3: Revision Cheat Sheet (1 minute)
1. **Reserve ≠ purchase** — payment takes seconds; hold a unit with a timer, pay via Stripe; webhook finalises; client polls for the result.
2. **No oversell** — check + decrement + reservation in **one transaction** (conditional update `WHERE inventoryCount > 0`).
3. **Hot row** — one counter = one register → **ReservationUnit rows**, each lockable independently (`SKIP LOCKED`). Redis = faster but hot key + split source of truth.
4. **Expiry** — cron leaves units stuck until it runs → make expired units **claimable immediately**.
5. **Millions of users** — DB has a ceiling (~10K tx/s) → **waiting room** in Redis admits users at a safe rate + **signed admission token**.
6. **Fairness** — random order for everyone present at noon, FIFO after; one entry per logged-in user; CAPTCHA; global ordering value across Redis shards.
