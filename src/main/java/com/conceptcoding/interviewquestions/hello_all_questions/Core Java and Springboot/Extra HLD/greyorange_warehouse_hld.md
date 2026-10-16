# GreyOrange Warehouse Automation — HLD (Hello Interview format)

> Lines starting with **Say:** are what you speak out loud in the interview.

---

## Part 1: Understand It First (2-minute read)

**Restaurant analogy**
- **Client WMS** (Warehouse Management System) = **waiter**. Takes the order, knows the stock. *"Table 5 wants a pizza."*
- **GreyOrange system** = **kitchen**. Decides who does what, in what order, and gets it to the counter.

**One line:** The WMS decides **what** to ship. GreyOrange's robots **bring the shelf to the worker** (goods-to-person), so workers never walk the warehouse.

**Example — you order Nike shoes on Myntra**
1. WMS sends: *"Order #123: 1 Nike shoe."* Stock is reserved.
2. System finds the shoe is on **Rack 42** and picks the **nearest free robot** with enough battery.
3. Robot drives to Rack 42 (without hitting other robots), **lifts it**, brings it to **Pick Station 3**.
4. Worker's screen: *"Take 1 Nike shoe from slot 3."* Worker picks → presses **Confirm**.
5. Stock goes 10 → 9. Robot takes the rack back. Order moves to packing.

| Term | Meaning |
|---|---|
| Rack | Shelf the robot lifts and carries |
| Pick station | Where a worker picks items from the rack |
| Heartbeat | Small "I'm alive + my position" message from each robot (~every 200 ms) |
| MQTT | Lightweight messaging protocol made for IoT devices on unreliable networks |

---

## Part 2: The Interview Answer

### 0. Opening (30 sec)

**Say:** "Let me confirm the scope. We're designing the software that takes orders from the client's WMS and uses robots to bring racks to pick stations — goods-to-person — for one warehouse. The WMS already knows orders and stock; we handle tasks, robots and stations. Is that right?"

---

### 1. Requirements (~5 min)

**Functional — top 3**
1. The client WMS should be able to **submit orders and track their status**.
2. The system should **assign a robot to bring the right rack to a pick station, without collisions**.
3. The operator should be able to **see what to pick and confirm the pick**.

**Out of scope:** robot hardware/motor control, billing, returns, ML forecasting.

**Non-functional**
1. **Low latency** — robot commands and position updates **< 100 ms** (safety).
2. **High availability** — **99.99%**; a stopped warehouse loses money every minute.
3. **Strong consistency** for inventory and assignments — no item oversold, no robot/rack given two tasks.
4. **Fault tolerant** — robot crash or Wi-Fi drop must not lose a task.

**Scale (one quick calculation)**
- ~**1,000 robots** per warehouse, ~**50k order lines/hour** at peak.
- Heartbeats: 1,000 robots × 5/sec = **~5,000 messages/sec** per warehouse.

**Say:** "That 5,000 messages per second is the key number — it tells me robot state must live in memory, not in a normal database, and I need a lightweight protocol for robots."

---

### 2. Core Entities (~2 min)

| Entity | Key fields |
|---|---|
| **Order** | orderId, items[sku, qty], priority, status |
| **Task** | taskId, orderId, rackId, stationId, robotId, status |
| **Robot** | robotId, position (x,y), battery, state (IDLE / BUSY / CHARGING / ERROR) |
| **Rack** | rackId, home location |
| **Inventory** | sku, rackId, slot, quantity, reservedQty |
| **PickStation** | stationId, location, task queue |

**Say:** "The warehouse floor is a grid of cells — robots move cell to cell. That grid is what we'll use for routing."

---

### 3. API (~3 min)

**WMS → System (REST)**
```
POST /orders            { items: [{sku, qty}], priority }  → orderId
GET  /orders/{orderId}  → status
```

**Pick station (REST + WebSocket)**
```
GET  /stations/{id}/next-task
POST /tasks/{taskId}/confirm-pick   { sku, qty }
WS   /stations/{id}/events          → "rack arriving in 10s"
```

**Robots ↔ System (MQTT)**
```
robot  → system : heartbeat   { robotId, x, y, battery, state }   every ~200 ms
robot  → system : taskUpdate  { taskId, seq, status }
system → robot  : command     { taskId, path: [cells], action: MOVE | LIFT | DROP }
```

**Say:** "For robots I'd use MQTT, not REST. It's lightweight publish-subscribe, built for thousands of devices on flaky Wi-Fi, and has delivery guarantees (QoS)."

---

### 4. High-Level Design (~12 min) — build it one requirement at a time

**Step 1 → Requirement 1: Submit & track orders**
```
Client WMS ──REST──► Order Service ──► Postgres (orders, inventory)
```
**Say:** "The Order Service validates the order and **reserves stock in one Postgres transaction**, so we never oversell. Postgres because orders and inventory need strong consistency."

**Step 2 → Requirement 2: Robot brings rack to station**
```
Order Service ──OrderCreated (Kafka)──► Dispatcher ◄──► Redis (robot state, cell bookings)
                                            │ task + chosen robot
                                            ▼
                                      Fleet Manager ◄──MQTT──► Robots
```
**Say:**
- "The **Dispatcher** reads the event, finds which rack has the item, creates a task, and picks the best robot — nearest idle robot with enough battery."
- "The **Fleet Manager** plans a collision-free path, sends commands to the robot over MQTT, and receives heartbeats."
- "Live robot state goes in **Redis**, because it changes ~5,000 times a second."
- "**Kafka** between Order Service and Dispatcher decouples them — orders aren't lost if the Dispatcher restarts."

**Step 3 → Requirement 3: Operator picks**
```
Fleet Manager ──RackArrived──► Station Service ──WebSocket──► Operator screen
                                     │ confirm pick
                                     ▼
                               Order Service (inventory −1, order updated)
```
**Say:** "When the rack arrives, the Station Service pushes the pick to the screen. On confirm, inventory is decremented and the robot returns the rack."

**Final picture (draw this by the end)**
```
Client WMS ──REST──► Order Service ──────────► Postgres
                        │   ▲                  (orders, tasks, inventory)
              Kafka     │   │ confirm pick
                        ▼   │
                    Dispatcher ◄────────► Redis
                        │                 (robot state, cell bookings)
                        ▼
                  Fleet Manager ◄──MQTT──► Robots
                        │
                        ▼
                  Station Service ──WS──► Operator screen
```

| Store | Holds | Why |
|---|---|---|
| **Postgres** | Orders, tasks, inventory | ACID — correctness |
| **Redis** | Robot positions/state, cell bookings | In-memory — very fast, changes constantly |
| **Kafka** | Events between services | Decoupling, durable, replay |

---

### 5. Deep Dives (~15 min) — one per non-functional requirement

**Say:** "Let me go deeper on the 4 hardest parts: correctness, collisions, robot failure, and availability."

#### 5.1 No double assignment, no overselling → *Consistency*
- **Stock:** reserve with one atomic SQL update:
  `UPDATE inventory SET reserved = reserved + q WHERE sku = ? AND quantity - reserved >= q`
  → 0 rows updated means not enough stock. No race.
- **Robot:** claim it atomically — Redis `SET robot:{id}:task {taskId} NX`. Only one Dispatcher can win.
- **Rack:** one active task per rack. If two orders need the same rack, **batch them** to the same station — one trip, two picks.
- **Better matching (one line):** under heavy load, collect tasks for ~1 second and match them to robots together instead of one by one.
- **Battery:** robots below a threshold (e.g. 20%) get no new tasks and go to a charger.

#### 5.2 Avoiding collisions → *Safety & low latency*
- Floor = grid. Path = shortest route on the grid (A*).
- **Cell booking:** before moving, a robot **books its next few cells** in Redis (`SET cell:5:7 R9 NX` with a short expiry). It moves only into cells it has booked, and releases them after passing.
- **Face-to-face deadlock:** use **one-way aisles**, and the **lower-priority robot yields** and replans if it waits too long.
- **Safety net:** robots have their own sensors and **stop by themselves** on an obstacle or lost connection.

**Say:** "Booking cells is like booking seats — two robots can never book the same cell at the same time."

#### 5.3 A robot dies mid-task → *Fault tolerance*
1. Heartbeat every ~200 ms. **No heartbeat for ~2 sec → mark robot ERROR.**
2. Release its cell bookings, but **block the cell it's stuck in**.
3. **Requeue its task** so another robot takes it.
4. Task states: `CREATED → ASSIGNED → RACK_PICKED → AT_STATION → DONE` (+ `REQUEUED`).
5. Robot updates carry `taskId + seq` → **idempotent**; a robot that reconnects can't send old updates that corrupt state.
6. If it was carrying a rack → mark the rack unavailable and alert maintenance.

#### 5.4 High availability & latency → *99.99%, < 100 ms*
- **Run on-site (edge):** Dispatcher, Fleet Manager, Redis, MQTT run **inside the warehouse**. Cloud round-trips are too slow for robot control, and the warehouse keeps running if the internet goes down. Dashboards and analytics run in the cloud.
- **One stack per warehouse** → a failure in one site never affects another.
- **No single point of failure:** stateless services with 2+ replicas, Kafka/MQTT clusters, Redis with a replica.
- **Very large sites:** split the floor into **zones**, each with its own Fleet Manager; robots are handed over at zone borders.

---

### 6. Likely Follow-up Questions

| Question | Short answer |
|---|---|
| **Why MQTT, not REST/WebSocket for robots?** | Lightweight pub-sub, built for thousands of devices on flaky Wi-Fi, QoS delivery guarantees. REST polling is too heavy. |
| **Why not Kafka for robot commands?** | Kafka = durable events between services. MQTT = fast per-device messages. Different jobs. |
| **Why Redis, not Postgres, for robot state?** | ~5,000 updates/sec, needs sub-ms reads. Postgres stays for durable, transactional data. |
| **What if Redis goes down?** | Replica takes over. Robots keep sending heartbeats, so state rebuilds within seconds. Pause new assignments briefly. |
| **What if Wi-Fi drops for a robot?** | Robot stops safely. If it doesn't return in time, its task is requeued (5.3). |
| **Fleet Manager crashes?** | Its state is in Redis/Postgres, so another replica takes over. Robots stop safely meanwhile. |
| **Scale to 5,000 robots?** | Split into zones, each with its own Fleet Manager; shard Redis by zone. |
| **Urgent orders?** | Priority queue in the Dispatcher — high-priority tasks are assigned first. |
| **Worker finds fewer items than expected?** | Raise a discrepancy event and replan the remaining quantity from another rack. |
| **How do you know the system is healthy?** | Metrics: tasks/hour, robot utilization, average task time, stuck robots, station idle time. |

---

## Part 3: How to Present (45 min)

| Step | Time | What to draw / say |
|---|---|---|
| Opening + Requirements | 5 min | Confirm scope → 3 functional → 4 non-functional → "5,000 msgs/sec" |
| Entities + API | 5 min | Entity list → 3 API groups (REST, WebSocket, MQTT) |
| High-Level Design | 12 min | Build in 3 steps → walk through the Nike shoe order on the final diagram |
| Deep Dives | 15 min | 5.1 → 5.2 → 5.3 → 5.4 (offer them yourself) |
| Follow-ups | rest | Use the table above |

**Tips**
- Start simple, then improve — that's what Hello Interview rewards. Don't draw the final diagram first.
- **Offer the deep dives yourself** ("The hardest parts here are collisions and robot failure — shall I go into those?"). This shows seniority.
- Tie every deep dive back to a non-functional requirement.
- Show you know the company: GreyOrange's own products are its **GreyMatter** orchestration software and **Butler** goods-to-person robots — this design is basically what they do.
