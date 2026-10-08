# Coffee Machine (and Pizza Ordering)

> **Amazon:** ★ reported in 2026 (Coffee Machine **and** Pizza Ordering). Your guide: *"extensibility and Decorator; recipe/ingredient inventory; handle requirements added mid-interview."*
>
> **The crux (what's really being tested):**
> 1. **Decorator:** add-ons stack at runtime (`Caramel(ExtraShot(Latte))`); each layer adds its price, ingredients and name.
> 2. **The inventory is all-or-nothing:** a drink takes every ingredient it needs, or none.
> 3. **Absorbing new requirements** by **adding** a class, not editing old ones. The interviewer *will* add requirements mid-way to test this.
>
> **Family:** F7 Add-on composition. See [foundations](../../00_AMAZON_LLD_FOUNDATIONS.md). Pizza is the same problem with toppings instead of add-ons (Q1), plus the Decorator-vs-Builder question.

---

## 1. Plain-language picture

### Russian dolls
```
Latte (₹150, 150 ml milk)
  wrapped in ExtraShot (+₹40, +18 g beans)
    wrapped in Caramel (+₹35, +20 ml syrup)
= "Latte + extra shot + caramel", ₹225
```
Ask the outermost doll its price: it asks the doll inside, then adds its own ₹35. That doll asks *its* inner doll and adds ₹40, and so on down to the Latte. Every layer has the **same shape** (`description()`, `cost()`, `recipe()`), so you can stack them in any order and any number.

### Why not subclasses?
`LatteWithCaramel`, `LatteWithCaramelAndShot`, `LatteWithShot`, `EspressoWithMilk`...: with 4 drinks and 4 add-ons that's 4 × 2⁴ = **64 classes**, and adding one add-on doubles it. With decorators: **4 + 4 = 8 classes**, combined freely.

### A decorator can do more than add
"Large" isn't +₹X. It's **×1.5 of everything inside it**. So the order matters:
```
Large(Milk(Espresso))  → the milk is scaled too  → ₹195, 150 ml milk
Milk(Large(Espresso))  → normal milk on a large  → ₹180, 100 ml milk
```
That's why decorators are classes with behaviour, not just rows of prices.

### The containers
The machine has beans, water, milk, syrup and chocolate. A latte needs 18 g beans + 30 ml water + 150 ml milk. If there's only 120 ml milk, **refuse the whole order**: don't grind the beans and then discover there's no milk. Check everything, then take everything, under one lock.

---

## 2. From story to design

| # | Decision | Rejected | Why |
|---|---|---|---|
| D1 | `Beverage` interface: `description()`, `cost()`, `recipe()` | separate price tables | Every layer answers all three, so any stack works. |
| D2 | `AddOn implements Beverage` **and** wraps a `Beverage` (Decorator) | subclass per combination | n add-ons = n classes, not 2ⁿ. |
| D3 | `recipe()` composes too: inner recipe + this layer's extra | ingredients tracked separately | The inventory checks the **whole** stack in one call. |
| D4 | `LargeSize` scales the inner drink | a size field on every drink | Size works on any drink and any add-on stack, with no edits elsewhere. |
| D5 | `Inventory.consume()` is synchronized and **all-or-nothing** | decrementing ingredient by ingredient | A failed order must use nothing; two orders racing for the last milk → one wins. |
| D6 | `Menu` maps names → constructors (Factory) | `if (name.equals("milk"))` chains | The touchscreen sends strings; a new add-on = one map entry. |
| D7 | `CoffeeMachine` only knows `Beverage` | knowing each drink type | Adding drinks and add-ons never touches the machine. |
| D8 | Money as `long` rupees; Large = ×3/2 in integer maths | `double` | Exact prices. |

### Class shape
```
CoffeeMachine                   ← brew(beverage) → Receipt · canMake · needsRefill
  Inventory                       synchronized: refill · has · consume (all-or-nothing) · lowStock
Menu                            ← Factory: build("latte", ["extra_shot","caramel"])

«interface» Beverage  description · cost · recipe
  ├── Espresso  ├── Americano  ├── Latte  ├── HotChocolate          (bases)
  └── «abstract» AddOn(inner)                                        (Decorator)
        ├── Milk  ├── ExtraShot  ├── Caramel                         (+ price, + ingredients)
        └── LargeSize                                                (× 1.5 everything inside)
Ingredient enum · Receipt { orderId, description, price }
```

---

## 3. Patterns that earn their place

| Pattern | In the base? | Problem it solves | Without it |
|---|---|---|---|
| **Decorator** (`AddOn`) | **Yes** | stackable add-ons, each with its own price/ingredients/behaviour | 2ⁿ subclasses, or one class full of `if (hasMilk)` flags |
| **Factory** (`Menu`) | **Yes** | names from the UI → the right objects | string `if`-chains wherever orders are built |
| **Observer** (low stock) | No | alert the café staff to refill | Q5 |
| **Builder** (pizza) | Alternative | when toppings are just a list with prices, no per-topping behaviour | Q1 |

**Say:** *"Decorator, because add-ons stack at runtime and each changes price, ingredients and name. With n add-ons that's n classes instead of 2ⁿ. A Factory turns the UI's names into the stack."*

**Tempting but wrong:** Singleton machine; State pattern (a coffee machine *can* have states, but brewing isn't asked; see Vending Machine for that); Strategy for add-ons (strategies **replace** behaviour; decorators **stack** it).

---

## 4. The 45-minute run

### Clarify (min 0–5)
> **You:** A few base drinks, plus add-ons a customer can stack (milk, extra shot, caramel). Each has a price and uses ingredients from the machine. Right?
> **Interviewer:** Yes.
> **You:** Can add-ons repeat (two extra shots)? Sizes?
> **Interviewer:** Repeats yes. Sizes maybe later.
> **You:** If an ingredient runs out, refuse the order and don't use anything?
> **Interviewer:** Yes.
> **You:** Several orders at once (an app plus the touchscreen)?
> **Interviewer:** Possible.

```
In scope:  4 base drinks · stackable add-ons (Decorator) · price + description + recipe through the stack
           inventory: all-or-nothing consume, refill, low-stock list · menu by names · thread-safe
Out:       payment, brewing states, loyalty/discounts (good mid-interview additions)
```

### Timeline
| Min | Do |
|---|---|
| 5–9 | Class shape. Say *"Decorator"* and the 2ⁿ argument. |
| 9–13 | Code step 1: `Ingredient`, `Beverage`, two bases. |
| 13–21 | Code step 2: **`AddOn`** (+ `innerRecipePlus`), `Milk`, `ExtraShot`, `Caramel`. |
| 21–28 | Code step 3: **`Inventory.consume`** (all-or-nothing), `CoffeeMachine.brew`. |
| 28–33 | Code step 4: `Menu` (names → constructors). |
| 33–37 | Dry run: price of `Caramel(ExtraShot(Latte))`. |
| 37–45 | **Mid-interview requirements** (Q2–Q4): show each is one new class. |

### The code you write, in this order

**Step 1: the contract + bases** ([beverage/](beverage/), [model/Ingredient.java](model/Ingredient.java))
```java
public enum Ingredient { COFFEE_BEANS, WATER, MILK, CARAMEL_SYRUP, CHOCOLATE }   // grams / ml

public interface Beverage {
    String description();
    long cost();                                  // rupees
    Map<Ingredient, Integer> recipe();            // what it uses up
}

public class Espresso implements Beverage {
    public String description()              { return "Espresso"; }
    public long cost()                       { return 100; }
    public Map<Ingredient, Integer> recipe() { return Map.of(Ingredient.COFFEE_BEANS, 18, Ingredient.WATER, 30); }
}

public class Latte implements Beverage {
    public String description()              { return "Latte"; }
    public long cost()                       { return 150; }
    public Map<Ingredient, Integer> recipe() {
        return Map.of(Ingredient.COFFEE_BEANS, 18, Ingredient.WATER, 30, Ingredient.MILK, 150);
    }
}
// Americano (₹120, beans 18, water 150), HotChocolate (₹130, chocolate 30, milk 200): same shape
```

**Step 2: the decorators** ([addon/](addon/))
```java
public abstract class AddOn implements Beverage {     // IS a Beverage, HAS a Beverage
    protected final Beverage inner;

    protected AddOn(Beverage inner) { this.inner = Objects.requireNonNull(inner); }

    public Beverage getInner() { return inner; }      // lets rules look inside the layers (Q3)

    // the inner drink's recipe plus this layer's extras
    protected Map<Ingredient, Integer> innerRecipePlus(Map<Ingredient, Integer> extra) {
        Map<Ingredient, Integer> recipe = new EnumMap<>(Ingredient.class);
        recipe.putAll(inner.recipe());
        extra.forEach((ingredient, qty) -> recipe.merge(ingredient, qty, Integer::sum));
        return recipe;
    }
}

public class Milk extends AddOn {
    public Milk(Beverage inner) { super(inner); }
    public String description()              { return inner.description() + " + milk"; }
    public long cost()                       { return inner.cost() + 30; }
    public Map<Ingredient, Integer> recipe() { return innerRecipePlus(Map.of(Ingredient.MILK, 100)); }
}

public class ExtraShot extends AddOn {
    public ExtraShot(Beverage inner) { super(inner); }
    public String description()              { return inner.description() + " + extra shot"; }
    public long cost()                       { return inner.cost() + 40; }
    public Map<Ingredient, Integer> recipe() {
        return innerRecipePlus(Map.of(Ingredient.COFFEE_BEANS, 18, Ingredient.WATER, 30));
    }
}
// Caramel (+₹35, +20 ml syrup): same shape

public class LargeSize extends AddOn {                // behaviour, not just data: scales everything inside
    public LargeSize(Beverage inner) { super(inner); }
    public String description() { return "Large (" + inner.description() + ")"; }
    public long cost()          { return inner.cost() * 3 / 2; }
    public Map<Ingredient, Integer> recipe() {
        Map<Ingredient, Integer> scaled = new EnumMap<>(Ingredient.class);
        inner.recipe().forEach((ingredient, qty) -> scaled.put(ingredient, qty * 3 / 2));
        return scaled;
    }
}
```

**Step 3: inventory + machine** ([model/Inventory.java](model/Inventory.java), [CoffeeMachine.java](CoffeeMachine.java))
```java
public class Inventory {
    private final Map<Ingredient, Integer> stock = new EnumMap<>(Ingredient.class);

    public synchronized void refill(Ingredient ingredient, int quantity) {
        if (quantity <= 0) throw new IllegalArgumentException("Refill must be > 0");
        stock.merge(ingredient, quantity, Integer::sum);
    }

    // all-or-nothing: check every ingredient, then take every ingredient, under one lock
    public synchronized void consume(Map<Ingredient, Integer> recipe) {
        List<String> missing = new ArrayList<>();
        for (Map.Entry<Ingredient, Integer> e : recipe.entrySet()) {
            int have = stock.getOrDefault(e.getKey(), 0);
            if (have < e.getValue()) missing.add(e.getKey() + " (need " + e.getValue() + ", have " + have + ")");
        }
        if (!missing.isEmpty()) throw new IllegalStateException("Not enough " + missing);
        for (Map.Entry<Ingredient, Integer> e : recipe.entrySet()) {
            stock.merge(e.getKey(), -e.getValue(), Integer::sum);
        }
    }
    // + has(recipe), get(ingredient), lowStock(threshold): all synchronized
}

public class CoffeeMachine {
    private final Inventory inventory;
    private final AtomicLong orderSeq = new AtomicLong();

    public CoffeeMachine(Inventory inventory) { this.inventory = inventory; }

    public Receipt brew(Beverage drink) {
        inventory.consume(drink.recipe());        // throws if short; then nothing is used
        return new Receipt("ORD-" + orderSeq.incrementAndGet(), drink.description(), drink.cost());
    }

    public boolean canMake(Beverage drink) { return inventory.has(drink.recipe()); }   // grey out on the screen
}
```

**Step 4: the menu (Factory)** ([Menu.java](Menu.java))
```java
public class Menu {
    private static final int MAX_ADD_ONS = 5;
    private final Map<String, Supplier<Beverage>> bases = new LinkedHashMap<>();
    private final Map<String, UnaryOperator<Beverage>> addOns = new LinkedHashMap<>();

    public Menu() {
        bases.put("espresso", Espresso::new);
        bases.put("latte", Latte::new);
        addOns.put("milk", Milk::new);                 // Milk::new is a Beverage → Beverage function
        addOns.put("extra_shot", ExtraShot::new);
        addOns.put("large", LargeSize::new);
        // + americano, hot_chocolate, caramel
    }

    public Beverage build(String base, List<String> addOnNames) {
        Supplier<Beverage> baseMaker = bases.get(base);
        if (baseMaker == null) throw new IllegalArgumentException("Unknown drink: " + base);
        if (addOnNames.size() > MAX_ADD_ONS) throw new IllegalArgumentException("At most " + MAX_ADD_ONS + " add-ons");
        Beverage drink = baseMaker.get();
        for (String name : addOnNames) {
            UnaryOperator<Beverage> wrap = addOns.get(name);
            if (wrap == null) throw new IllegalArgumentException("Unknown add-on: " + name);
            drink = wrap.apply(drink);                 // wrap in the order the customer chose
        }
        return drink;
    }
}
```

### Dry run
```
Caramel(ExtraShot(Latte)).cost()
  Caramel   → inner.cost() + 35
  ExtraShot → inner.cost() + 40
  Latte     → 150
  = 150 + 40 + 35 = ₹225
recipe: Latte {beans 18, water 30, milk 150} + shot {beans 18, water 30} + caramel {syrup 20}
      = {beans 36, water 60, milk 150, syrup 20}
brew: inventory.consume(...) → all present → subtract all → Receipt "Latte + extra shot + caramel = ₹225"
With only 120 ml milk → "Not enough [MILK (need 150, have 120)]", and the beans are untouched.
```
The driver also runs 20 simultaneous latte orders with milk for 10: exactly 10 served, 0 ml left.

---

## 5. Follow-ups: answer out loud first, then open

<details>
<summary><b>Q1. Pizza ordering: same thing? And when would you use Builder instead?</b></summary>

**Decorator version:** identical shape, with toppings instead of add-ons:
```java
public interface Pizza { String description(); long cost(); }

public class Margherita implements Pizza {
    public String description() { return "Margherita"; }
    public long cost() { return 250; }
}

public abstract class Topping implements Pizza {
    protected final Pizza inner;
    protected Topping(Pizza inner) { this.inner = inner; }
}

public class Olives extends Topping {
    public Olives(Pizza inner) { super(inner); }
    public String description() { return inner.description() + " + olives"; }
    public long cost() { return inner.cost() + 40; }
}
// new ExtraCheese(new Olives(new Margherita())) → ₹350
```
**Builder version**, for when toppings are just *a list with prices* and no topping has behaviour of its own:
```java
public class PizzaOrder {
    private final String size;
    private final String crust;
    private final List<String> toppings;
    private final long price;

    private PizzaOrder(Builder b) { size = b.size; crust = b.crust; toppings = List.copyOf(b.toppings); price = b.price; }

    public static class Builder {
        private static final Map<String, Long> TOPPING_PRICES = Map.of("olives", 40L, "cheese", 60L, "paneer", 70L);
        private String size = "MEDIUM";
        private String crust = "THIN";
        private final List<String> toppings = new ArrayList<>();
        private long price = 250;

        public Builder size(String s)  { size = s; if (s.equals("LARGE")) price += 150; return this; }
        public Builder crust(String c) { crust = c; return this; }
        public Builder topping(String t) {
            Long p = TOPPING_PRICES.get(t);
            if (p == null) throw new IllegalArgumentException("Unknown topping " + t);
            toppings.add(t);
            price += p;
            return this;
        }
        public PizzaOrder build() {
            if (toppings.size() > 5) throw new IllegalStateException("Max 5 toppings");
            return new PizzaOrder(this);
        }
    }
}
// new PizzaOrder.Builder().size("LARGE").topping("paneer").topping("olives").build() → ₹510
```
**Say which and why:** *"Decorator when each add-on carries its own behaviour (Large scales, a discount multiplies, an ingredient is consumed). Builder when it's a fixed object assembled once, with validation at `build()`."*
</details>

<details>
<summary><b>Q2. Mid-interview: "20% off during happy hour, 4–6 PM, on any drink."</b></summary>

One new decorator, a **price-only** layer. Nothing existing changes:
```java
public class HappyHour extends AddOn {
    private final LocalTime now;
    public HappyHour(Beverage inner, LocalTime now) { super(inner); this.now = now; }

    public String description() { return inner.description() + (isHappyHour() ? " (happy hour −20%)" : ""); }
    public long cost() { return isHappyHour() ? inner.cost() * 80 / 100 : inner.cost(); }
    public Map<Ingredient, Integer> recipe() { return inner.recipe(); }       // doesn't change ingredients

    private boolean isHappyHour() {
        return !now.isBefore(LocalTime.of(16, 0)) && now.isBefore(LocalTime.of(18, 0));
    }
}
// wrap last, so it discounts the whole stack: new HappyHour(drink, LocalTime.now(clock))
```
Pass a `Clock` in production so you can test 5 PM without waiting.
</details>

<details>
<summary><b>Q3. Mid-interview: "No more than 2 extra shots per drink."</b></summary>

Walk the layers and count. That's why `AddOn` exposes `getInner()`:
```java
static int countShots(Beverage b) {
    int shots = 0;
    while (b instanceof AddOn) {
        if (b instanceof ExtraShot) shots++;
        b = ((AddOn) b).getInner();
    }
    return shots;
}
// in Menu.build or CoffeeMachine.brew: if (countShots(drink) > 2) throw new IllegalArgumentException("Max 2 extra shots");
```
Rules like "no milk in an Americano" are the same: walk the stack and check the types.
</details>

<details>
<summary><b>Q4. Mid-interview: "Add oat milk, and whipped cream."</b></summary>

`OatMilk extends AddOn` (+₹45, + `OAT_MILK` ingredient) and `WhippedCream extends AddOn`. Add the ingredients to the enum and one line each to `Menu`. **The machine, the inventory and every existing drink don't change** (Open/Closed). Say it out loud: that's the whole point of the round.

If add-ons only ever differ by name/price/ingredients, you could also make *one* data-driven `SimpleAddOn(name, price, ingredients)` class loaded from config, and keep real classes only for behaviour (Large, HappyHour). That's a nice senior trade-off to mention.
</details>

<details>
<summary><b>Q5. Tell staff when milk is running low.</b></summary>

**Observer** on the inventory: after `consume`, if any ingredient drops below its threshold, notify the listeners (`onLowStock(ingredient, left)`). The staff tablet and a supplier reorder job subscribe. Fire outside the lock and wrap each listener in try/catch. `CoffeeMachine.needsRefill(threshold)` already gives the "refill" light its data.
</details>

<details>
<summary><b>Q6. The machine brews one drink at a time, but orders arrive from the app and the screen.</b></summary>

Orders go into a `BlockingQueue<Order>`; one brewing thread takes them in order (producer–consumer, like the Job Scheduler). Ingredients are reserved at **order** time (`consume` succeeds or the order is rejected immediately), not at brew time, so a customer never pays and then hears "out of milk". `canMake` greys out drinks on the screen, but `consume` is the real check.
</details>

<details>
<summary><b>Q7. How do you test it?</b></summary>

Price and description of a known stack (₹225), the recipe of a stack, Large's order sensitivity (₹195 vs ₹180), the menu with unknown names, a failed order consumes nothing (beans unchanged), refill then success, and 20 concurrent orders with milk for 10 → exactly 10 served, 0 ml left. All are in the driver.
</details>

---

## 6. Traps
1. A subclass per combination (`LatteWithCaramel`).
2. Boolean flags on one class (`hasMilk`, `shots`, `isLarge`) with `if`s in `cost()`.
3. Decrementing ingredients one by one: a failed order leaves half-used stock.
4. The machine knowing concrete add-on types.
5. Confusing Strategy (replace) with Decorator (stack).
6. Not noticing that decorator **order** matters (Large, discounts).
7. Editing existing classes when a new requirement arrives.

## 7. Recall check
1. Why n classes instead of 2ⁿ? Show it with 4 add-ons.
2. Walk `cost()` down `Caramel(ExtraShot(Latte))`.
3. Why is `LargeSize` a decorator and not a field? Why does order matter?
4. What makes `consume` all-or-nothing, and why the lock?
5. Decorator vs Builder for pizza: one sentence each.
6. Happy hour arrives mid-interview: what do you add, and what changes?

**Rebuild in 12 minutes:** `Beverage` · 2 bases · `AddOn` (+ `innerRecipePlus`) · `Milk` · `ExtraShot` · `LargeSize` · `Inventory.consume` · `CoffeeMachine.brew` · `Menu.build`.

---

**Files:** `CoffeeMachine` · `Menu` · `beverage/` (`Beverage`, `Espresso`, `Americano`, `Latte`, `HotChocolate`) · `addon/` (`AddOn`, `Milk`, `ExtraShot`, `Caramel`, `LargeSize`) · `model/` (`Ingredient`, `Inventory`, `Receipt`) · `CoffeeMachineDriver` (decorated prices, Large order, menu, all-or-nothing inventory, 20-thread test)
```bash
mvn -q compile exec:java -Dexec.mainClass=com.conceptcoding.interviewquestions.hello_all_questions.composition.coffeemachine.CoffeeMachineDriver
```
