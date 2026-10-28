# Order lifecycle — State

**Owner:** M3 ZHOU YUXIN · **Sprint:** 3 (code), 4 (write-up, #81) · **Code:**
`entity/OrderStatus.java`, `entity/OrderAction.java`, `service/OrderStateService.java`,
`mapper/OrderMapper.xml` (`transition`); called by `OrderController` ·
**Tests:** `OrderStateServiceTest`

## The design problem

An order is the only entity in the system whose meaning changes over its lifetime. The same
row is, at different moments, a claim on stock, a claim on the buyer's money, a job waiting on
the seller, and a closed record. Four questions have to be answered on every operation, and in
Sprint 2 each of them was answered wherever the operation happened to be written:

1. **Is this action allowed right now?** Shipping an unpaid order and cancelling a delivered
   one are both nonsense, but nothing stopped either except an `if` at the call site.
2. **Who is allowed to ask for it?** Cancelling belongs to the buyer, dispatching to the
   seller, expiry to the scheduler. The caller's identity was checked by whichever controller
   happened to own the endpoint.
3. **What is the order still holding?** While unpaid it holds a stock reservation; once paid it
   holds the buyer's money until receipt is confirmed. Releasing either one at the wrong moment
   either oversells the product or hands the seller money for goods that never arrived.
4. **What happens to the status when two people act at once?** Two "ship" clicks, or a payment
   landing in the same second as the expiry job, must not both take effect.

Answering these four at each call site meant the rules existed in as many versions as there
were endpoints, and the only way to know the real lifecycle was to read all of them. Adding
`REFUNDED` in Sprint 3 would have meant finding every status comparison in the codebase and
deciding, one by one, whether the new state belonged in it.

## The pattern

**State**, with the state object being the `OrderStatus` enum itself. Each constant that permits
anything overrides two methods, so the state — not the caller — decides what may happen in it:

```java
PAID {
    @Override public OrderStatus after(OrderAction action) {
        return switch (action) {
            case SHIP -> SHIPPED;
            case REFUND -> REFUNDED;
            default -> null;
        };
    }
    @Override public Actor actor(OrderAction action) { return Actor.SELLER; }
}
```

`after` returning `null` *is* the rejection: an action a state does not name is not permitted
there, and the four terminal states inherit a base implementation that permits nothing, so
being closed needs no special case anywhere. Two further predicates answer question 3 without
anyone naming an action — `holdsReservation()` is true only while unpaid, `holdsPayment()` is
true for `PAID` and `SHIPPED`.

`OrderStateService.apply(callerId, orderId, action)` then performs what every transition has in
common: look the order up, ask the state for the next one, check the caller against
`actor(action)`, move the row, settle whatever the old state was holding, and announce the
change once. The settlement is phrased as a question about the states rather than about the
action:

```java
if (current.holdsReservation() && next.isTerminal())      releaseReservation(orderId);
if (current.holdsPayment() && !next.holdsPayment())       paymentService.releaseToSeller(order);
```

The second line is the escrow release. It does not test for `CONFIRM_RECEIPT`; it observes that
the order is leaving the group of states that hold the buyer's money, which is the definition of
the money becoming the seller's. A new state added to that group inherits the behaviour for
free, and a refund — which leaves the same group but pays the buyer — is kept out of it by being
performed in `PaymentService` instead.

The transition itself is one conditional update, which is where the concurrency answer lives:

```sql
UPDATE oms_order SET status = #{toStatus}
WHERE id = #{id} AND status = #{fromStatus}
```

Naming the state being left means two concurrent attempts at the same transition cannot both
succeed: the second changes no row, and zero affected rows is reported as "this order has
already moved on". There is no read-then-write window, and no application-level lock.

Finally, one `OrderStateChangedEvent` is published per successful move, from the single place
that performs them. Observers receive it after the commit, so a notification that fails cannot
roll back the state change that caused it.

## Why State, and not something else

**Against a status column plus `if`s:** that is what Sprint 2 had. It works until a state is
added, at which point every comparison in the codebase is a site that might need editing, and
nothing tells you which. With the rules on the enum, `REFUNDED` was added by writing one
constant and one line in `PAID`.

**Against a transition table (a `Map<Status, Map<Action, Status>>`):** it answers question 1
only. The actor, and what the state is holding, would still need somewhere to live, and they
would end up back at the call sites — which is the problem we started with. Keeping all four
answers on the same object is the point.

**Against a workflow engine:** the lifecycle is seven states and six actions, with no human
approval chains, no cross-service compensation and one timer. An engine would have been the
heaviest dependency in the system, operated and monitored, in service of its smallest state
machine. It also would not have solved the concurrency question any better than the conditional
update does, because the authoritative state would still be a row in our database.

## What it would look like without it

Confirming receipt, in the Sprint 2 shape, is an endpoint that reads the order, compares the
status string to `"SHIPPED"`, compares the caller to the buyer id, writes `"COMPLETED"`, and
then — if whoever wrote it remembered — releases the escrow. Dispatching is the same five steps
with different constants, in a different file. Six actions written that way are six independent
chances to forget the escrow step, to compare against the wrong party, or to write the status
unconditionally and silently overwrite a concurrent change. The rule that money is released when
the order stops holding it would not be written down anywhere; it would be a habit.

## Consequences and honest limits

**`apply` is the single door for the three buyer- and seller-initiated actions only.** Cancel,
ship and confirm-receipt go through it from `OrderController`. Three other flows perform their
own conditional update, and this is the pattern's real boundary in our codebase:

- `PaymentService.pay` moves `PENDING_PAYMENT → PAID` itself, because it must write
  `payment_method` in the same update that claims the order, and must claim it *before* any
  money moves rather than after a double charge. It does not consult `OrderStatus` to do so,
  which means the permission rule for `PAY` is stated in the enum and then not read there.
- `PaymentService.refund` does ask the enum (`current.after(OrderAction.REFUND)`) before
  performing its own update, so the rule is centralised and only the mechanism is duplicated.
- `OrderService.expire` moves `PENDING_PAYMENT → EXPIRED` from the scheduled job, and releases
  the stock reservation with its own copy of the loop in `OrderStateService.releaseReservation`.

So the enum is a single source of truth for the *rules*, but the *mechanism* exists in four
places and one of them does not read the rules. The conditional update makes each of the four
safe on its own — none of them can overwrite a concurrent change — so this is duplication
rather than a defect, but it is duplication that a reader has to find. Routing payment and
expiry through `apply` would need the signature to carry per-action extras such as the payment
method, which is the change we would make next.

**Reading the status is still open.** `ReviewService` gates on
`OrderStatus.COMPLETED.name().equals(order.getStatus())` — a read, not a transition, so it
cannot corrupt anything, but it is a status comparison outside the enum of the kind this pattern
was meant to remove.

**The enum carries behaviour, so it is not a pure data type.** `OrderStatus` is persisted as its
own name, which keeps the database readable but means renaming a constant is a migration, not a
refactor.

## Evidence

`OrderStateServiceTest` (14 tests, mappers and `PaymentService` mocked):

- each permitted transition, for the right actor
- every rejected action in each state fails as `IllegalStateException` and changes no row
- the wrong party is refused even when the action itself is legal in that state
- a transition whose conditional update affects zero rows — the concurrent case — is reported as
  "already moved on" and performs no settlement
- the stock reservation is released when an unpaid order closes, and only then
- the escrow is released on the `holdsPayment` boundary, and not on a refund
- exactly one `OrderStateChangedEvent` per successful move, and none on rejection

## Diagrams

All four are in `diagrams/m3-state/`, each as a `.drawio` source with its PNG export.

| Diagram | Shows |
|---|---|
| `01-before-class` | The problem: `OrderService` with one method per action, each checking the status with an `if` and repeating its own side effects; `Order.status` is a plain string |
| `02-after-class` | The solution: `OrderStateService.apply`, `OrderStatus` with `after`, `actor`, `holdsReservation`, `holdsPayment` and `isTerminal`, `OrderAction`, the conditional `transition`, and `OrderStateChangedEvent` |
| `03-before-sequence` | The problem in motion: a buyer cancels while the expiry job expires the same unpaid order, using `OrderService` as at commit `4b765b0^`. Both paths release the reservation and the later unconditional `updateStatus` overwrites the earlier one |
| `04-after-sequence` | The same race after the pattern: `OrderStatus` decides the move and the actor, the `transition` naming `PENDING_PAYMENT` lets exactly one writer through, and the stock is released once |
