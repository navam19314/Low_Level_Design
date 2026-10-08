# Kanban / Trello Board

> **Why it's asked:** boards, lanes, cards, and moving cards between lanes. It overlaps with the reported Ethos **frontend** task, so expect questions about drag-and-drop, ordering, and two people editing the same board.
>
> **The crux (what's really being tested):**
> 1. **Ordered containers:** cards have a position inside a lane; a move = remove + insert at an index.
> 2. **Atomic moves:** a card is **always in exactly one lane**, even with two people dragging at once.
> 3. **Validate before you mutate:** a rejected move (WIP limit, bad position, not a member) leaves the board untouched.
>
> **Family:** ordered composition + concurrency tool A/C (lock per board; lock ordering for per-lane locks). See [foundations](../../00_AMAZON_LLD_FOUNDATIONS.md).

---

## 1. Plain-language picture

### A whiteboard with sticky notes
```
| To Do          | Doing (max 2)      | Done        |
|----------------|--------------------|-------------|
| Deploy         | Quote API @rahul   |             |
| Write tests    | Login page         |             |
```
- **Board:** the whiteboard. Only team members may touch it.
- **Lane:** a column, left to right in a fixed order. It may have a **WIP limit** ("at most 2 things in progress"), which is how Kanban stops people starting too much at once.
- **Card:** a sticky note, top to bottom in order. It has a title, description and assignee.

### Moving a sticky note
Peeling a note off "To Do" and sticking it in "Doing" at position 1 is **one action**. Nobody should ever see the note in both columns, or in neither. In code: remove from the source list + insert into the target list, under **one lock**.

### Two people at the whiteboard
Priya drags "Login" to Doing while Rahul drags it to Done, at the same instant. With one marker (lock) per whiteboard, they take turns: one move happens fully, then the other. The card ends up in exactly one lane. Different boards have different markers, so other teams never wait.

### Positions are indexes, with one subtlety
Moving a card **within** its own lane: the card is removed first, so the lane is one shorter. Valid positions are `0..size−1`, not `0..size`.

---

## 2. From story to design

| # | Decision | Rejected | Why |
|---|---|---|---|
| D1 | `Board` owns `List<Lane>`; `Lane` owns `List<Card>` (display order = list order) | a `position` int field on every card | With a list, the order *is* the data. Position fields need renumbering on every move and drift out of sync. |
| D2 | **One lock per board** (`synchronized` board methods) | a lock per lane / card | A move touches 2 lanes + an index. One lock = no deadlock and simple code; boards are independent, so they scale. Per-lane is a follow-up (Q3). |
| D3 | Index `laneIdOfCard` (cardId → laneId) on the board | searching every lane to find a card | O(1) "which lane is this card in?". It's updated in the same locked method as the lists, so they can't disagree. |
| D4 | `moveCard(cardId, toLaneId, position)` handles **both** cross-lane moves and reordering | two methods | Same operation; the only difference is the max position (D5). |
| D5 | Same-lane max position = `size − 1`; other lane = `size` | one rule for both | The moving card is temporarily out of its own lane. |
| D6 | **Validate everything, then mutate** | remove first, then discover the target is full | A rejected move must leave the board unchanged; no half-moved card. |
| D7 | `Lane`/`Card` mutators are **package-private** | public setters | Only `Board` (holding the lock) can change them; the service can't bypass the rules. |
| D8 | Service checks **who** (membership); Board checks **what** (lanes, WIP, positions) | all checks in one place | Each class enforces the rules it owns. |
| D9 | Can't delete a lane that still has cards | silently deleting the cards | Losing work by accident is the worst UX bug. |

### Class shape
```
KanbanService                           ← the app calls this; checks membership
  Map<boardId, Board>
  createBoard · addMember · addLane · createCard · moveCard · assignCard · updateCard · deleteCard

Board                                   ← one lock; owns everything inside it
  memberIds · List<Lane> lanes · Map<laneId, Lane> · Map<cardId, Card> · Map<cardId, laneId>
  addLane · moveLane · removeLane · addCard · moveCard · removeCard · updateCard · assignCard · render

Lane { id, name, wipLimit, List<Card> cards }     insert / remove / isFull   (package-private)
Card { id, title, description, assigneeId, createdBy }                       (package-private setters)
```

---

## 3. Patterns that earn their place

| Pattern | In the base? | Problem it solves | Without it |
|---|---|---|---|
| **Composition** (Board → Lanes → Cards) | **Yes** | a natural ownership tree; each level guards its own rules | — |
| **Observer** (`BoardListener`) | No | activity log ("Priya moved Login to Doing"), live updates to other viewers | Q4 |
| **Optimistic versioning** | No | a stale browser tab overwriting newer changes | Q2 |

**Say:** *"This one is about data modelling and concurrency more than patterns: ordered lists, one lock per board, validate before mutate. I'd add Observer for the activity feed when it's asked for."*

**Tempting but wrong here:**
- **State pattern for card status:** the lane **is** the status, and lanes are user-defined. A hard-coded enum would break custom boards.
- **Composite pattern:** lanes and cards aren't the same kind of thing; there's no recursive "group of groups" here.
- **Singleton service:** create once and inject.

---

## 4. The 45-minute run

### Clarify (min 0–5)
> **You:** A user creates boards, adds members, adds lanes in order, creates cards in lanes, and moves cards between lanes or reorders them. Right?
> **Interviewer:** Yes.
> **You:** Do lanes have WIP limits?
> **Interviewer:** Optional per lane.
> **You:** Who can edit, members only?
> **Interviewer:** Members only.
> **You:** Can two people edit the same board at once?
> **Interviewer:** Yes, it's collaborative.
> **You:** Activity history, labels, due dates, real-time sync: later?
> **Interviewer:** Later.

```
In scope:  boards with members · ordered lanes (add, reorder, delete-if-empty, WIP limit)
           ordered cards (create, move across lanes, reorder, update, assign, delete)
           members-only · concurrent edits never lose or duplicate a card
Out:       activity log, labels/due dates, real-time push, undo, persistence
```

### Timeline
| Min | Do |
|---|---|
| 5–9 | Class shape. Say: *"Ordered lists; one lock per board; validate before mutate."* |
| 9–13 | Code step 1: `Card`, `Lane` (package-private insert/remove, `isFull`). |
| 13–28 | Code step 2: **`Board`**: `addLane`, `addCard`, **`moveCard`**, `removeCard`, helpers. |
| 28–34 | Code step 3: `KanbanService` (membership check + delegate). |
| 34–38 | Dry run: a reorder, a cross-lane move, a WIP rejection. |
| 38–45 | Follow-ups: two users, stale tabs. |

### The code you write, in this order

**Step 1: `Card` and `Lane`** ([model/Card.java](model/Card.java), [model/Lane.java](model/Lane.java))
```java
public class Card {
    private final String id;
    private String title;
    private String description;
    private String assigneeId;                       // null = unassigned
    private final String createdBy;

    public Card(String id, String title, String description, String createdBy) { ... }

    // package-private: only Board changes a card, under its lock
    void setTitle(String title)             { this.title = title; }
    void setDescription(String description) { this.description = description; }
    void setAssigneeId(String assigneeId)   { this.assigneeId = assigneeId; }
    // + getters
}

public class Lane {
    private final String id;
    private String name;
    private final int wipLimit;                      // 0 = no limit
    private final List<Card> cards = new ArrayList<>();

    public Lane(String id, String name, int wipLimit) {
        if (wipLimit < 0) throw new IllegalArgumentException("WIP limit must be >= 0");
        this.id = id;
        this.name = name;
        this.wipLimit = wipLimit;
    }

    void insert(Card card, int position) { cards.add(position, card); }
    void remove(Card card)               { cards.remove(card); }
    boolean isFull()                     { return wipLimit > 0 && cards.size() >= wipLimit; }
    int size()                           { return cards.size(); }

    public List<Card> getCards() { return new ArrayList<>(cards); }   // copy: callers can't reorder us
    // + getters
}
```

**Step 2: `Board`, the crux** ([model/Board.java](model/Board.java))
```java
public class Board {
    private final String id;
    private final String name;
    private final Set<String> memberIds = new LinkedHashSet<>();
    private final List<Lane> lanes = new ArrayList<>();                 // left to right
    private final Map<String, Lane> lanesById = new HashMap<>();
    private final Map<String, Card> cardsById = new HashMap<>();
    private final Map<String, String> laneIdOfCard = new HashMap<>();   // index: cardId → laneId

    public Board(String id, String name, String ownerId) {
        this.id = id;
        this.name = name;
        memberIds.add(ownerId);
    }

    public synchronized void addLane(Lane lane) {
        if (lanesById.containsKey(lane.getId())) throw new IllegalArgumentException("Lane exists: " + lane.getId());
        lanes.add(lane);
        lanesById.put(lane.getId(), lane);
    }

    public synchronized void addCard(String laneId, Card card) {
        Lane lane = requireLane(laneId);
        if (lane.isFull()) throw new IllegalStateException("'" + lane.getName() + "' is at its WIP limit of " + lane.getWipLimit());
        lane.insert(card, lane.size());                          // new cards go to the bottom
        cardsById.put(card.getId(), card);
        laneIdOfCard.put(card.getId(), laneId);
    }

    // cross-lane move OR reorder. Validate everything first: a rejected move changes nothing.
    public synchronized void moveCard(String cardId, String toLaneId, int position) {
        Card card = requireCard(cardId);
        Lane from = lanesById.get(laneIdOfCard.get(cardId));
        Lane to = requireLane(toLaneId);
        boolean sameLane = from == to;

        if (!sameLane && to.isFull()) {
            throw new IllegalStateException("'" + to.getName() + "' is at its WIP limit of " + to.getWipLimit());
        }
        int maxPosition = sameLane ? to.size() - 1 : to.size();   // same lane: the card doesn't count twice
        checkPosition(position, maxPosition);

        from.remove(card);
        to.insert(card, position);
        laneIdOfCard.put(cardId, toLaneId);
    }

    public synchronized void removeCard(String cardId) {
        Card card = requireCard(cardId);
        lanesById.get(laneIdOfCard.get(cardId)).remove(card);
        cardsById.remove(cardId);
        laneIdOfCard.remove(cardId);
    }

    public synchronized void removeLane(String laneId) {
        Lane lane = requireLane(laneId);
        if (lane.size() > 0) throw new IllegalStateException("Move or delete the cards in '" + lane.getName() + "' first");
        lanes.remove(lane);
        lanesById.remove(laneId);
    }

    public synchronized void assignCard(String cardId, String userId) {
        if (userId != null && !memberIds.contains(userId)) throw new IllegalArgumentException(userId + " is not on this board");
        requireCard(cardId).setAssigneeId(userId);
    }

    public synchronized boolean isMember(String userId) { return memberIds.contains(userId); }
    public synchronized void addMember(String userId)   { memberIds.add(userId); }

    private Lane requireLane(String laneId) {
        Lane lane = lanesById.get(laneId);
        if (lane == null) throw new NoSuchElementException("No lane " + laneId + " on board " + name);
        return lane;
    }

    private Card requireCard(String cardId) {
        Card card = cardsById.get(cardId);
        if (card == null) throw new NoSuchElementException("No card " + cardId + " on board " + name);
        return card;
    }

    private static void checkPosition(int position, int max) {
        if (position < 0 || position > max) {
            throw new IndexOutOfBoundsException("Position " + position + " is outside 0.." + max);
        }
    }
    // + moveLane, updateCard, render (snapshot under the lock), getters
}
```
**Shape to remember:** `moveCard` = find card + both lanes → check WIP (only if changing lane) → check position (`size−1` if same lane) → remove → insert → update the index.

**Step 3: the service** ([KanbanService.java](KanbanService.java))
```java
public class KanbanService {
    private final Map<String, Board> boards = new ConcurrentHashMap<>();
    private final AtomicLong seq = new AtomicLong();

    public Board createBoard(String name, String ownerId) {
        Board board = new Board("B-" + seq.incrementAndGet(), name, ownerId);
        boards.put(board.getId(), board);
        return board;
    }

    public Card createCard(String boardId, String actorId, String laneId, String title, String description) {
        if (title == null || title.isBlank()) throw new IllegalArgumentException("Title can't be empty");
        Card card = new Card("C-" + seq.incrementAndGet(), title, description, actorId);
        member(boardId, actorId).addCard(laneId, card);
        return card;
    }

    public void moveCard(String boardId, String actorId, String cardId, String toLaneId, int position) {
        member(boardId, actorId).moveCard(cardId, toLaneId, position);
    }

    // the "who" check; the Board does the "what" checks
    private Board member(String boardId, String actorId) {
        Board board = boards.get(boardId);
        if (board == null) throw new NoSuchElementException("Board not found: " + boardId);
        if (!board.isMember(actorId)) throw new SecurityException(actorId + " is not a member of " + board.getName());
        return board;
    }
    // + addMember, addLane, assignCard, updateCard, deleteCard, getBoard: all member(...).something(...)
}
```

### Dry run
```
To Do [Login, API, Tests, Deploy]   Doing (max 2) []   Done []

moveCard(Deploy, ToDo, 0)    same lane → max = 4−1 = 3 → 0 ok → remove, insert at 0
                             To Do [Deploy, Login, API, Tests]
moveCard(API, Doing, 0)      other lane, Doing not full, max = 0 → ok → Doing [API]
moveCard(Login, Doing, 1)    Doing [API, Login]            (now at its WIP limit 2)
moveCard(Tests, Doing, 0)    Doing.isFull() → IllegalStateException, nothing changed
moveCard(Tests, Done, 9)     Done is empty → max 0 → IndexOutOfBounds, nothing changed
```

---

## 5. Follow-ups: answer out loud first, then open

<details>
<summary><b>Q1. Priya and Rahul move the same card at the same instant.</b></summary>

Both calls reach `board.moveCard`, which is `synchronized`. One runs completely, then the other runs on the **updated** board (it re-reads the card's lane from the index). The card ends in the second user's target lane, and it's never duplicated or lost. The driver runs 2 users × 1,000 opposite moves: always 10 cards in 10 slots, no deadlock.

That's "last write wins", which is fine for a board. If the product wants "don't silently overwrite", use versioning (Q2).
</details>

<details>
<summary><b>Q2. A user's tab is 10 minutes old. Their drag shouldn't undo newer changes they never saw.</b></summary>

**Optimistic concurrency:** the board has a `version` that every change increments. The client sends the version it loaded; if it doesn't match, reject the move and the UI refreshes.
```java
private long version = 0;                           // every mutator ends with version++

public synchronized long getVersion() { return version; }

public synchronized void moveCard(String cardId, String toLaneId, int position, long expectedVersion) {
    if (expectedVersion != version) {
        throw new IllegalStateException("Board changed (you have v" + expectedVersion
                + ", current is v" + version + "). Refresh and try again.");
    }
    moveCard(cardId, toLaneId, position);           // reentrant; moveCard itself ends with version++
}
```
- **Board-level** versions reject a move if *anything* changed, which is simple but conflicts often on a busy board.
- **Per-card** versions (`card.version`) only conflict when *that* card changed. That's better for big teams.
- This is the same idea as an HTTP `ETag` + `If-Match`, or a DB `UPDATE ... WHERE version = ?`.
</details>

<details>
<summary><b>Q3. A huge board where 200 people edit at once. Is one lock per board a bottleneck?</b></summary>

Use **one lock per lane**; a move takes the two lanes' locks **in a fixed order** (by lane id), so two opposite moves can't deadlock. The card's lane must be re-checked after locking, because it may have moved while we waited:
```java
// sketch: lanesById and laneIdOfCard become ConcurrentHashMaps
public void moveCard(String cardId, String toLaneId, int position) {
    while (true) {
        Lane from = lanesById.get(laneIdOfCard.get(cardId));
        Lane to = requireLane(toLaneId);
        Lane first  = from.getId().compareTo(to.getId()) <= 0 ? from : to;   // fixed order → no deadlock
        Lane second = first == from ? to : from;
        synchronized (first) {
            synchronized (second) {                                         // same lane: reentrant, fine
                if (!from.getId().equals(laneIdOfCard.get(cardId))) continue;   // moved meanwhile: retry
                // ... the same validation + remove + insert + index update ...
                return;
            }
        }
    }
}
```
**Why it can deadlock without the ordering:** Priya moves A from To Do → Done (locks To Do, waits for Done). Rahul moves B from Done → To Do (locks Done, waits for To Do). Each waits forever. Always locking the smaller id first breaks the cycle.

**Say:** *"I'd start with one lock per board; it's correct, and boards rarely have heavy write contention. I'd move to per-lane locks only if measurements showed waiting."*
</details>

<details>
<summary><b>Q4. Activity log: "Priya moved Login page from To Do to Doing".</b></summary>

**Observer.** The board announces each change after it's applied; the activity log, notifications and live sync subscribe:
```java
public interface BoardListener {
    void onCardMoved(String boardId, Card card, String fromLaneId, String toLaneId, String actorId);
}

private final List<BoardListener> listeners = new CopyOnWriteArrayList<>();

// in moveCard, after the move succeeds (the service passes actorId down):
for (BoardListener l : listeners) {
    try {
        l.onCardMoved(id, card, from.getId(), toLaneId, actorId);
    } catch (Exception e) {
        System.err.println("Listener failed: " + e.getMessage());   // never undo the move
    }
}
```
In production, publish the event to a queue **after** the lock is released, so a slow subscriber never blocks the board.
</details>

<details>
<summary><b>Q5. List indexes need shifting on every insert. How does Trello/Jira do ordering at scale?</b></summary>

**Fractional ranks:** each card stores a sortable `rank`, and a move only changes **the moved card's** rank, set between its new neighbours:
```java
// cards in a lane are sorted by rank
static double rankBetween(Double before, Double after) {
    if (before == null && after == null) return 1000;      // empty lane
    if (before == null) return after - 1000;               // new top
    if (after == null)  return before + 1000;              // new bottom
    return (before + after) / 2;                           // between two cards
}
```
In a database that's **one row update per move**, not a renumbering of the whole lane. After many moves into the same gap the numbers get too close (doubles run out of precision after ~50 halvings), so a background job **rebalances** the lane's ranks. Jira uses string ranks ("LexoRank") for the same reason: they can always be split.
</details>

<details>
<summary><b>Q6. Undo the last move.</b></summary>

Store each move as a small object `{cardId, fromLaneId, fromPosition, toLaneId}` on a per-user stack. Undo = `moveCard(cardId, fromLaneId, fromPosition)`. Undo can fail if the board changed meanwhile (the original lane is now full); report it instead of forcing it. That's the Command idea: an action you can store and reverse.
</details>

<details>
<summary><b>Q7. Roles: viewers can only look; admins can delete lanes.</b></summary>

`Map<userId, Role>` on the board instead of a member set, with `enum Role { VIEWER, EDITOR, ADMIN }`. The service checks the role per action: `requireRole(board, actor, Role.EDITOR)` for moves, `Role.ADMIN` for `removeLane`. Keep the role checks in the service ("who"), and the board rules in `Board` ("what").
</details>

<details>
<summary><b>Q8. Everyone looking at the board should see moves live.</b></summary>

Each change produces an event (Q4) → pushed over **WebSocket** to everyone viewing that board. Each event carries the new board version (Q2), so a client that missed an event notices the gap and reloads the board. That's how the frontend stays consistent with the server without polling.
</details>

<details>
<summary><b>Q9. How would you store it in a database?</b></summary>

```sql
board(id, name)          board_member(board_id, user_id, role)
lane(id, board_id, name, wip_limit, rank)
card(id, board_id, lane_id, title, description, assignee_id, rank, version)
```
A move = `UPDATE card SET lane_id = ?, rank = ?, version = version + 1 WHERE id = ? AND version = ?`. Zero rows updated means someone else changed it, so it's a conflict (Q2). The WIP limit is checked in the same transaction: `SELECT COUNT(*) FROM card WHERE lane_id = ? FOR UPDATE`.
</details>

<details>
<summary><b>Q10. How would you know it's working in production?</b></summary>

- **Metrics:** moves/min, conflict rate (stale-version rejections), WIP-limit rejections per board, p99 latency of `moveCard`.
- **Invariant check:** every card is in exactly one lane, and `card.lane_id` matches the lane list. Alarm on any mismatch.
- **Product metrics:** cycle time (To Do → Done), from the activity log.
</details>

<details>
<summary><b>Q11. How do you test it?</b></summary>

- **Moves:** reorder to the top, the bottom and the middle; cross-lane at index 0 and at the end; same-lane max position = `size−1`.
- **Rejections leave the board unchanged:** take a snapshot (`render()`), try an invalid move, compare (in the driver).
- **Concurrency:** 2 threads × 1,000 opposite moves → finishes (no deadlock), and the card count is the same in the index and in the lanes.
</details>

---

## 6. Traps that cost points
1. A `position` field on cards that you renumber by hand.
2. Removing the card before validating the target: a rejected move loses the card.
3. The same-lane position off-by-one (`size` vs `size − 1`).
4. Two separate locks for the source and target lanes, taken in any order: deadlock.
5. Public setters on `Card`/`Lane`, bypassing the board's rules and lock.
6. A hard-coded `Status` enum for lanes when users define their own columns.
7. Deleting a lane that still has cards.

## 7. Recall check
1. Why is the order a list, not a position field?
2. Walk through `moveCard`: what is validated, in what order, and why before mutating?
3. Max position in the same lane vs another lane, and why?
4. Two users drag in opposite directions: why no deadlock with the board lock? What changes with per-lane locks?
5. Optimistic versioning: what does the client send, and what happens on a mismatch?
6. Fractional ranks: why do they help, and when do you need to rebalance?

**Rebuild in 10 minutes:** `Lane` (list + insert/remove/isFull) · `Board` (lanes, cardsById, laneIdOfCard) · `moveCard` (validate → remove → insert → index) · service `member()` check.

---

**Files:** `KanbanService` · `model/` (`Board`, `Lane`, `Card`) · `KanbanDriver` (reorder, cross-lane, assign, 6 rejected actions with the board unchanged, WIP freed, 2×1000 concurrent moves)
```bash
mvn -q compile exec:java -Dexec.mainClass=com.conceptcoding.interviewquestions.hello_all_questions.composition.kanban.KanbanDriver
```
