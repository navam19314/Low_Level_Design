# Notification System — LLD Interview Guide

Total time: ~45 minutes across 5 phases.

---

## Phase 1 — Requirements (5 min)

Ask these before writing anything.

**Questions to ask:**
- What triggers a notification? (user action, system event?)
- What channels are supported? (email, SMS, push?)
- Can a user have multiple preferred channels?
- Should notifications be sent synchronously or asynchronously?
- Who stores user channel preferences?
- Out of scope: retry logic, templates, rate limiting, delivery receipts?

**Agree on this scope:**
- Notifications triggered by events (order placed, payment done etc.)
- Channels: EMAIL, SMS, PUSH
- Each user has preferred channels stored in the system
- Support both sync and async sending
- Out of scope: retry, templates, rate limiting

---

## Phase 2 — Core Entities (5 min)

Draw this before writing code.

```
  ┌─────────────────────────────────────────────────────────┐
  │                        model/                            │
  │                                                          │
  │  ┌──────────────────┐  ┌──────────────┐  ┌───────────┐  │
  │  │   Notification   │  │UserPreference│  │ChannelType│  │
  │  │                  │  │              │  │  (enum)   │  │
  │  │ - userId: String │  │ - userId     │  │           │  │
  │  │ - message: String│  │ - channels   │  │  EMAIL    │  │
  │  └──────────────────┘  │   Set<Channel│  │  SMS      │  │
  │                         │   Type>      │  │  PUSH     │  │
  │                         └──────────────┘  └───────────┘  │
  └─────────────────────────────────────────────────────────┘

  ┌──────────────────────────────────────────────────────────┐
  │                       channel/                            │
  │                                                           │
  │         <<interface>> NotificationChannel                 │
  │              + send(Notification)                         │
  │                       │                                   │
  │         ┌─────────────┼──────────────┐                   │
  │         ▼             ▼              ▼                    │
  │   EmailChannel    SmsChannel    PushChannel               │
  └──────────────────────────────────────────────────────────┘

  ┌──────────────────────────────────────────────────────────┐
  │                       factory/                            │
  │                                                           │
  │   NotificationChannelFactory                              │
  │   + getChannel(ChannelType) → NotificationChannel         │
  │     (switch on ChannelType → returns right impl)          │
  └──────────────────────────────────────────────────────────┘

  ┌──────────────────────────────────────────────────────────┐
  │                       service/                            │
  │                                                           │
  │  UserPreferenceService          NotificationDispatcher    │
  │  - preferences:                 - preferenceService       │
  │    ConcurrentHashMap            + dispatch(Notification)  │
  │  + savePreference(pref)           → looks up user prefs   │
  │  + getPreference(userId)          → loops channels        │
  │    (default: EMAIL)               → factory.getChannel()  │
  │                                   → channel.send()        │
  └──────────────────────────────────────────────────────────┘

  ┌──────────────────────────────────────────────────────────┐
  │                         api/                              │
  │                                                           │
  │  NotificationService          AsyncNotificationService    │
  │  (synchronous)                (asynchronous)              │
  │                                                           │
  │  + sendNotification()         - ExecutorService           │
  │    → dispatcher.dispatch()      (10 threads)              │
  │                               + sendNotification()        │
  │                                 → executor.submit(        │
  │                                     dispatcher::dispatch) │
  └──────────────────────────────────────────────────────────┘

  DISPATCH FLOW:
  ──────────────
  sendNotification(notification)
         │
         ▼
  dispatcher.dispatch(notification)
         │
         ▼
  preferenceService.getPreference(userId)
         │ returns UserPreference (set of ChannelType)
         ▼
  for each ChannelType:
         │
         ▼
  NotificationChannelFactory.getChannel(channelType)
         │ returns EmailChannel / SmsChannel / PushChannel
         ▼
  channel.send(notification)
```

---

## Phase 3 — Class Design (8 min)

Write skeletons first, bodies after.

### `model/` — pure data, no logic
```java
enum ChannelType { EMAIL, SMS, PUSH }

class Notification {
    private final String userId;
    private final String message;
    // constructor + getters
}

class UserPreference {
    private final String userId;
    private final Set<ChannelType> preferredChannels;
    // constructor + getters
}
```

### `channel/` — Strategy pattern
```java
interface NotificationChannel {
    void send(Notification notification);
}
// EmailNotificationChannel, SmsNotificationChannel, PushNotificationChannel
// each just prints: "Sending EMAIL/SMS/PUSH to user X: message"
```

### `factory/` — Factory pattern
```java
class NotificationChannelFactory {
    static NotificationChannel getChannel(ChannelType type) {
        return switch (type) {
            case EMAIL -> new EmailNotificationChannel();
            case SMS   -> new SmsNotificationChannel();
            case PUSH  -> new PushNotificationChannel();
        };
    }
}
```

### `service/` — business logic
```java
class UserPreferenceService {
    private final Map<String, UserPreference> preferences = new ConcurrentHashMap<>();
    // savePreference(UserPreference)
    // getPreference(userId) → default EMAIL if not found
}

class NotificationDispatcher {
    private final UserPreferenceService preferenceService;
    // dispatch(Notification)
}
```

### `api/` — entry points
```java
class NotificationService {                 // synchronous
    private final NotificationDispatcher dispatcher;
    // sendNotification(Notification) → dispatcher.dispatch()
}

class AsyncNotificationService {            // asynchronous
    private final NotificationDispatcher dispatcher;
    private final ExecutorService executorService = Executors.newFixedThreadPool(10);
    // sendNotification(Notification) → executorService.submit(() -> dispatcher.dispatch())
}
```

---

## Phase 4 — Implementation (20 min)

### Write `NotificationDispatcher.dispatch()` — the core method

```java
public void dispatch(Notification notification) {
    UserPreference preference = preferenceService.getPreference(notification.getUserId());
    Set<ChannelType> channels = preference.getPreferredChannels();

    for (ChannelType channelType : channels) {
        NotificationChannel channel = NotificationChannelFactory.getChannel(channelType);
        channel.send(notification);
    }
}
```

Three lines of logic. Clean.

### Write `UserPreferenceService` — note the default fallback

```java
public UserPreference getPreference(String userId) {
    return preferences.getOrDefault(
            userId,
            new UserPreference(userId, Set.of(ChannelType.EMAIL))  // default to EMAIL
    );
}
```

If the user has no preference saved, they still get an EMAIL. Never returns null.

### Write the 3 channel implementations — fast (1 min each)

```java
// Same pattern for all three — just change the prefix
public void send(Notification notification) {
    System.out.println("Sending EMAIL to user "
            + notification.getUserId() + ": " + notification.getMessage());
}
```

### Write `AsyncNotificationService` — shows threading knowledge

```java
public void sendNotification(Notification notification) {
    executorService.submit(() -> dispatcher.dispatch(notification));
}
```

One line. The lambda captures `notification` and runs `dispatch` on a thread pool thread — caller returns immediately.

---

## Phase 5 — Extensibility (7 min, discuss only)

| "What if..." | Answer |
|---|---|
| Add WhatsApp channel | Add `WHATSAPP` to `ChannelType`, implement `WhatsAppNotificationChannel`, add one case in factory switch — zero other changes |
| Retry on failure | Wrap `channel.send()` in try-catch inside dispatcher, re-attempt N times |
| Rate limiting | Add a `RateLimiter` map in dispatcher keyed by userId — check before sending |
| Notification templates | Add a `TemplateService` that formats message per channel type before dispatch |
| Per-event-type preferences | Add `Map<EventType, Set<ChannelType>>` to `UserPreference` instead of flat set |
| Shutdown async service cleanly | Call `executorService.shutdown()` — waits for in-flight notifications to complete |

---

## Design Patterns Used

| Pattern | Where | Why |
|---|---|---|
| **Strategy** | `NotificationChannel` interface | Each channel is interchangeable — add new ones without touching dispatcher |
| **Factory** | `NotificationChannelFactory` | Dispatcher doesn't `new` channel objects directly — decouples creation from use |

---

## Time Budget

| Phase | Time |
|---|---|
| Requirements | 5 min |
| Core entities + diagram | 5 min |
| Class skeletons | 8 min |
| `dispatch()` + channels + async | 15 min |
| `Main` wiring | 5 min |
| Extensibility discussion | 7 min |
| **Total** | **~45 min** |

---

## Order to Write Code in Interview

1. `ChannelType.java` — 30 sec
2. `Notification.java` + `UserPreference.java` — 3 min
3. `NotificationChannel.java` interface — 1 min
4. `EmailNotificationChannel`, `SmsNotificationChannel`, `PushNotificationChannel` — 3 min
5. `NotificationChannelFactory.java` — 2 min  ← mention Factory pattern here
6. `UserPreferenceService.java` — 3 min  ← mention ConcurrentHashMap here
7. `NotificationDispatcher.java` — 5 min  ← most marks here
8. `AsyncNotificationService.java` — 3 min  ← mention ExecutorService here
9. `NotificationService.java` (sync) — 1 min
10. `Main.java` — 3 min

---

## What Interviewers Are Checking

| Checkpoint | What they want to see |
|---|---|
| Factory pattern | `NotificationChannelFactory` — don't hardcode channel creation in dispatcher |
| Strategy pattern | `NotificationChannel` interface — adding a channel = one new class |
| Thread safety | `ConcurrentHashMap` in `UserPreferenceService` — not plain `HashMap` |
| Default fallback | `getOrDefault` with EMAIL — never return null preferences |
| Sync vs async | Both `NotificationService` and `AsyncNotificationService` — shows awareness |
| Clean separation | model/channel/factory/service/api packages — not everything in one class |

---

## Common Mistakes to Avoid

- Creating channels with `if-else` inside dispatcher instead of a Factory
- Putting `userId`, `email`, `phone` inside `Notification` — it's a message, not a user profile
- Using plain `HashMap` in `UserPreferenceService` — not thread-safe when async service is running
- Forgetting the default fallback in `getPreference()` — NPE when user has no saved preference
- Making `NotificationChannelFactory.getChannel()` non-static — it holds no state, static is correct
