# Notifying both parties of an order state change — Observer

**Owner:** M4 MA HUAILING · **Sprint:** 3 · **Code:** `event/OrderStateChangedEvent.java`,
`event/OrderNotificationListener.java`, `service/NotificationService.java` ·
**Tests:** `OrderNotificationListenerTest`, `NotificationServiceTest`

## The design problem

An order does not change state silently. When it is paid, cancelled or expired, both the
buyer and the seller have to find out, and more concerns are already queuing up: credit
scores in Sprint 4, reviews, and whatever the next sprint adds. The state-change code
lives in the order services (`OrderService`, `PaymentService`), which are M3's files.

If those services called the notification code directly, two things would go wrong:

- Every new concern means editing M3's files again. The order service accumulates a call
  to every module that cares about a state change, and M4 cannot ship a feature without
  touching M3's code.
- A failure in a peripheral concern could roll back the state change itself. If writing a
  notification row throws inside the payment transaction, the buyer has paid and the
  payment is undone.

## The pattern

Observer: the subject publishes an event when something happens, and observers subscribe
to it. The subject does not know who is listening.

| Observer role | In this code |
|---|---|
| Event (the message) | `OrderStateChangedEvent` — a record carrying orderId, orderNo, both party ids, from/to states and the total |
| Subject (the publisher) | `PaymentService.pay` and `OrderService.releaseAndMark` — one `events.publishEvent(...)` line each, through Spring's `ApplicationEventPublisher` |
| Observer (the listener) | `OrderNotificationListener` — `@TransactionalEventListener(phase = AFTER_COMMIT)` |
| Notification handling | `NotificationService.deliver` — persists one row per recipient, then pushes to `/user/queue/system` per user |

```
PaymentService.pay ────────┐
                           ├──► OrderStateChangedEvent ──► OrderNotificationListener
OrderService (cancel/      │        (after commit)               │
             expire) ──────┘                                     ├─ persist (buyer)
                                                                 ├─ persist (seller)
        future: credit scores, reviews ──► just add listeners    ├─ push /user/queue/system (buyer)
                                                                 └─ push /user/queue/system (seller)
```

Two details carry the design:

1. **AFTER_COMMIT.** The listener runs only after the transaction that moved the order has
   committed. Combined with a try/catch around each delivery, a failing notification
   cannot roll back — or even delay — the state change that raised it. The acceptance
   criterion "a failing notification does not roll back the state change" is enforced by
   structure, not by discipline.
2. **Per-recipient isolation.** `deliver` is called once for the buyer and once for the
   seller, each with its own error boundary. The buyer's push failing on a broken broker
   still leaves the seller's row written and pushed.

Delivery reuses the chat infrastructure (Sprint 2): the same `SimpMessagingTemplate`
per-user queue style, the same shared frontend connection (`utils/realtime.js`). The
frontend already contained an unread-notification scaffold waiting for
`GET /api/chat/history/system`; the backend now implements exactly that contract, and
rows in `system_notification` mean a user who was offline at the time still sees the
message on next login.

## Why Observer, and not something else

- **Direct calls** would couple the order services to every concern. Exactly what the
  design problem says must not happen.
- **Messaging middleware (RabbitMQ/Kafka)** is the distributed version of the same idea.
  Nothing here needs another broker to run: notifications are in-process, the platform
  already runs six containers, and a second delivery mechanism (at-least-once, retry,
  dead-letter) would buy complexity before it buys value. If the platform ever splits
  into separate services, the event is already a message — moving to a broker means
  changing the publisher, not the listeners.
- **Spring application events vs a hand-rolled listener list.** The framework already
  owns lifecycle, threading and the transaction hook. A DIY `List<Observer>` would
  reimplement all three, worse.

## What it would look like without it

`PaymentService.pay` would end with:

```java
notificationService.orderPaid(order);
creditScoreService.orderPaid(order);
// ...next sprint's concern here
```

Each line is an edit to M3's file, each call can throw inside the payment transaction,
and unit-testing pay now requires mocking every downstream module. With the event, the
pay test mocks one no-op publisher; the listener is tested on its own; and neither side
knows the other's name.

## Consequences and honest limits

- Listeners run synchronously on the committing thread, after commit. A slow listener
  delays the HTTP response of the request that triggered the state change (it cannot
  corrupt it). At current scale that is milliseconds; if it ever matters, `@Async` on the
  listener is a one-line change.
- Events are fire-and-forget: if the application dies between commit and delivery, the
  notification is lost. The state is correct (the transaction committed); only the
  message is missing. True guaranteed delivery needs an outbox table and a relay, which
  is a Sprint 5 topic if this ever matters.
- The event payload is intentionally small and stable (ids, states, amount). Listeners
  that need more (addresses, item lists) re-read by id, which keeps old events valid as
  the order model evolves.

## Evidence

- `OrderNotificationListenerTest` — one event reaches both parties exactly once, with the
  order number and new state in the body.
- `NotificationServiceTest` — delivery persists then pushes; a broker failure is
  swallowed with the row persisted; a database failure is swallowed with no push; neither
  throws.
- Hand-check on staging: pay an order as the buyer; the seller's browser shows the toast
  without a refresh; a user who logs in later sees the unread message from
  `/api/chat/history/system`.
