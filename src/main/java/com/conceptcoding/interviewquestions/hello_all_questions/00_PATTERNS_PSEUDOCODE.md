# Design Patterns: Short Pseudo Code

One page per pattern: **the idea in one line → pseudo code → how it's used in this deck → what to say**.

---

## State

**In one line:** an object behaves differently depending on its current state, so **each state is its own class**, and the object hands every action to its current state.

Real life: a traffic light. "Next" from RED goes to GREEN, from GREEN to YELLOW, from YELLOW to RED. The light doesn't use `if`s: each colour knows what comes next.

### Pseudo code
```
interface State:
    actionA(context)  → default: throw "not allowed in this state"
    actionB(context)  → default: throw "not allowed in this state"

class StateOne implements State:
    actionA(context):                      // only override what's LEGAL here
        ...do the work...
        context.setState(new StateTwo())   // move to the next state

class StateTwo implements State:
    actionB(context):
        ...do the work...
        context.setState(new StateOne())

class Context:
    state = new StateOne()
    setState(s):  state = s
    actionA():    state.actionA(this)      // just delegate, no if/else
    actionB():    state.actionB(this)
```
**Three parts:** a **state interface** (every action refused by default), **one class per state** (it overrides only its legal actions and picks the next state), and a **context** that delegates to its current state.

### Without it vs with it
```
// WITHOUT: every method checks the status, so forget one check and there's a bug
submit():  if (status == DRAFT) {...} else throw
issue():   if (status == APPROVED) {...} else throw
cancel():  if (status == DRAFT || status == APPROVED) {...} else throw

// WITH: each state class contains only what's allowed in it
DraftState:    submit()          → ApprovedState or RejectedState
ApprovedState: issue(), cancel() → IssuedState / CancelledState
FinalState:    nothing, so every action throws by default
```

### In this deck: Insurance (Extension 1)
```java
public interface ApplicationState {
    ApplicationStatus status();
    default void submit(Application app)  { throw notAllowed("submit"); }
    default Policy issue(Application app) { throw notAllowed("issue"); }
}

public class DraftState implements ApplicationState {          // only submit
    public void submit(Application app) { /* run rules */ app.setState(new ApprovedState()); }
}
public class ApprovedState implements ApplicationState {       // only issue
    public Policy issue(Application app) { app.setState(new FinalState(ISSUED)); return /* policy */; }
}
public class FinalState implements ApplicationState { }        // REJECTED / ISSUED: nothing allowed

// Application
private ApplicationState state = new DraftState();
public synchronized void submit() { state.submit(this); }      // delegate
```
Other uses in the deck: **Vending Machine** (Idle → HasMoney → Dispensing), and **Insurance**, which uses an enum in the base and this pattern as its Extension 1.

### Enum + `canMoveTo` or the State pattern?
| Use an **enum + `canMoveTo`** when... | Use the **State pattern** when... |
|---|---|
| states only limit **which move comes next** | each state does **different work** for the same action |
| e.g. Cab ride, Food order, Insurance base | e.g. Vending Machine: "insert coin" means something different in each state |
| short: one enum, one switch | more classes, but each one is small |

### What to say
> *"What you can do depends on the state, so each state is a class that allows only its own actions. Everything else is refused by default, so I can't forget a check. A new state is a new class. If states only limit the next move, an enum with `canMoveTo` is simpler, and I'd start there."*

---

## Observer

**In one line:** one object announces "something happened", and everyone who signed up gets told. The announcer doesn't know or care who's listening.

Real life: a YouTube channel and its subscribers. Upload a video, and every subscriber gets a notification. A new subscriber doesn't change the channel.

### Pseudo code
```
interface Listener:
    onEvent(data)

class Subject:
    listeners = []
    addListener(l):  listeners.add(l)

    doSomething():
        ...change state...
        for l in listeners:          // tell everyone
            l.onEvent(data)

class EmailListener implements Listener:  onEvent(data) → send email
class SmsListener   implements Listener:  onEvent(data) → send SMS
```
**Three parts:** a **listener interface**, a **list** in the subject, and a **loop** that notifies after the change.

### Without it vs with it
```
// WITHOUT: the subject calls everyone itself, so a new screen means editing the subject
board.update(...);  app.push(...);  sign.update(...);

// WITH: the subject announces once, and a new screen is a new listener
for (SpotListener l : listeners) l.spotChanged(spot, nowFree);
```

### In this deck
- **Parking Lot (Extension 3):** `SpotListener` → `FloorDisplayBoard` shows free spots per floor.
- **Insurance (Extension 5):** `StatusListener` → email, CRM, audit log.
- **Inventory (Extension 1):** `StockListener` → low-stock alert, automatic reorder.
- **Notification Service:** the whole problem is an event bus built on Observer.

**Pull vs push:** without Observer, the board keeps **asking** "how many are free?" (pull). With it, the subject **tells** the board when something changes (push).

### What to say
> *"Several things react to this event, so the subject just announces it and listeners decide what to do. A new channel is a new listener. Listeners must be quick, because they may run inside a lock. Anything slow, like an email, goes on a queue or runs after the lock is released."*
