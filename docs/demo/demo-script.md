# Demo script — the main transaction line

Owner: Zhou Yuxin (M3) · Issue #85 · Presented segment: #90
Target: live demo, 26 Oct 2026. A recorded video exists only as a backup
to play if the live run fails (lecturer's email, 11 Oct 2026).

Provisional budget: **7 minutes**. The lecturer has not yet issued the
per-component time allocation inside the 45-minute slot, so this is an
estimate; the act list below is ordered so that acts 5 and 6 can be cut
without breaking the narrative.

---

## 1. What this segment has to prove

One claim, demonstrated rather than asserted: **the buyer's money is held
by the platform and becomes the seller's at exactly one moment — when the
buyer confirms receipt.** Everything else in the segment exists to make
that moment visible.

Two supporting claims, in acts 5 and 6:
- a refund cannot fail because the seller already spent the money, since
  the seller was never paid;
- a fifth payment method is a new class, not a new branch in the order flow.

---

## 2. Setup

### 2.1 Data

```bash
mysql -u root -p trade < init.sql            # schema + base seed
mysql -u root -p trade < scripts/demo-seed.sql   # demo buyer, stock, balances
```

`demo-seed.sql` prints a readback table. All six rows must look sane before
you rehearse; `buyer payment pwd set` printing `NO -- BROKEN` means the
buyer cannot pay and the whole segment is dead.

Two findings behind that file, both worth knowing because they bite silently:

- **`admin` (id 1) cannot pay.** `init.sql` creates it without a `balance`
  and without a `payment_password_hash`, so they are `0.00` and `NULL`.
  The demo therefore uses its own buyer, `demo_buyer` (id 201), not admin.
- **Never re-run `init.sql` once rehearsals have started.** Its drop list
  covers `users`, `roles`, `user_roles`, `pms_product`, `pms_product_media`
  and `review`, but *not* `oms_cart_item`, `oms_order`, `oms_order_item` or
  `oms_transaction`. Re-running it therefore deletes the demo buyer and
  every seller, resets stock to 1, and leaves the earlier orders and ledger
  rows behind pointing at users that no longer exist. Verified against a
  throwaway MySQL 8.0 container, 14 Oct. If you do re-run it, you must
  re-run `demo-seed.sql` straight afterwards, and you should expect the
  stale orders to stay.

### 2.2 Accounts

| Role | Username | Login pwd | Payment pwd | Note |
|---|---|---|---|---|
| Buyer | `demo_buyer` | `admin123` | `123456` | balance seeded high on purpose |
| Seller | `seller_lvl2` | `admin123` | `123456` | owns product 4; its balance is the figure the audience watches |

Item under demo: **product 4, "Introduction to Algorithms, 3rd Edition", S$55.00**, seller 102.

All seeded strings are English — product names, descriptions, locations (Singapore
districts) and display names — because the catalogue is on screen for most of the
presentation. `demo-seed.sql` rewrites them; `init.sql` is left alone, since it is
shared by the whole team and other suites may match on its strings.

> Coordination: product 4 and 5 are reserved for this segment. Other
> segments please use a different seller's catalogue so two people are not
> mutating the same rows during one presentation.

### 2.3 Browsers

Buyer and seller must be logged in **at the same time**, so they need two
**separate browser profiles or one normal + one incognito window** — not two
tabs. Two tabs share the token store and the second login silently replaces
the first.

Arrange them side by side before the session starts, both already logged in,
both already on the pages act 1 begins from. Logging in on stage wastes
40 seconds and is the most common place a live demo stalls.

### 2.4 Repeatability — read this before writing any narration

The data is **never reset between runs**. Stock is 20 and the buyer's
balance is 99999 so the flow simply works again; orders, ledger rows and
reviews accumulate, which makes the system look used rather than empty.

The cost is that three things drift upward with every rehearsal, so
**no absolute figure may be quoted on stage**:

| Drifts | Why | Say this instead |
|---|---|---|
| Buyer / seller balance | money moves each run | "the seller's balance has not changed" / "it has gone up by the price of the book" |
| Order list length | a new order per run | "the most recent order" — never "the second one down" |
| Seller rating | `16 × avg + min(sales, 20)`, rises until 20 completed sales | do not put a star figure on a slide (→ M4, for the deck) |

One sticky state to be aware of: a user can favourite a product only once
(`uk_fav_user_product`). Second run onwards the button already reads
"已收藏". This segment does not use favouriting; whoever's segment does
should either narrate that state as normal or use a different product.

Fallback: `scripts/demo-restore.sql` puts balances and stock back.
**Rehearsal only — do not run it on 26 Oct.** It deletes nothing and
touches neither Redis nor Elasticsearch.

---

## 2.5 Verified on 14 Oct — one blocker, three cosmetic issues

The stack was brought up locally (`docker compose up`, six services) and the
script was walked through the real UI. What that found:

**Blocker — the main transaction line cannot be started by clicking.**
The product detail page has no "add to cart" and no "buy now" control, and
nothing in the frontend links to `/cart`:

- `ProductDetail.vue` lines 244–252 are nine orphaned comments — `// Add to
  cart`, `// Buy now`, `// Toggle favorite status`, `// Contact seller`,
  `// Start a negotiation`, `// Delete product` — with no code under them.
- `marketplaceService.js` defines `cart.add(productId, quantity)`; nothing in
  the frontend calls it.
- Nothing links to the `/cart` route; the page is reachable only by typing
  the URL.

The backend is fine (`CartController` has `@PostMapping("/api/cart")`) and
`CartView.vue` lists, edits and checks out correctly. The gap is exactly two
frontend controls. **Acts 1 to 5 cannot run until they exist.** Owner: the
frontend pair; raised 14 Oct, three days before the 17 Oct feature freeze.

**Cosmetic, but all three are on screen during the demo:**

| Issue | Where | Note |
|---|---|---|
| Currency symbol is inconsistent | list card `¥55`, detail page `S$55.00`, header balance `¥99999.00` | pick one before the 25th |
| `7% New` | list card only | condition level 7/10 rendered as a percentage; the detail page gets it right |
| Product photos are unrelated stock images | all cards | a book listing shows a seaside cliff |

**Login carries a CAPTCHA.** That turns "both windows logged in before the
session" from advice into a hard requirement: on stage it is not just 40
seconds, it is reading a CAPTCHA correctly in front of the room.

**Why stock is 20 and not higher.** The detail page prints `Stock: N
available`. A secondhand book offered in the hundreds reads as fake, so the
figure is kept to the smallest number that still covers every rehearsal.

---

## 3. The run

Every row: what you click, what must appear, what you say. If the screen
does not match, say the line for the *next* act and move on — do not debug
on stage.

### Act 1 — Order placed. Stock is reserved, not deducted.

| Do | Screen must show | Say |
|---|---|---|
| Buyer window: home → search `Algorithms` → open product 4 | detail page, S$55.00, 20 available | — |
| Add to cart → `/cart` | the book, quantity 1 | — |
| Checkout | order detail `/orders/:id`, status **PENDING_PAYMENT** | "The order exists, but nothing has been paid and nothing has been deducted. The stock is *reserved*, not removed — the reservation is held in Redis with an expiry, so an order nobody pays for releases its stock by itself instead of needing a clean-up job to notice." |

Point at the status. It is the first of four states the audience will see.

### Act 2 — Paid. The money is held. The seller has not been paid.

| Do | Screen must show | Say |
|---|---|---|
| `/orders/:id/pay` → choose **Balance** → payment password `123456` | payment success, status **PAID** | "Paid by account balance." |
| Stay on order detail | an **amount held by the platform** of 55.00 | "The buyer has been charged, and the order now shows the amount the platform is holding. That figure is not stored on the order — it is derived from the ledger: payments, minus payouts, minus refunds." |
| **Switch to the seller window, refresh the profile/balance** | seller balance **unchanged** | "This is the point of the segment. The seller has been paid nothing. The balance has not moved." |

That switch is the single most important 10 seconds of the segment. Do it
deliberately, and let the unchanged number sit on screen for a beat.

### Act 3 — Dispatched. Still held.

| Do | Screen must show | Say |
|---|---|---|
| Seller window: `/orders` → the most recent order → Ship | status **SHIPPED** | "The seller has dispatched it. The state changed; the money did not. Both PAID and SHIPPED are states in which the payment is held, so moving between them settles nothing." |

### Act 4 — Confirm receipt. This is where the money moves.

| Do | Screen must show | Say |
|---|---|---|
| Buyer window: order detail → Confirm receipt | status **COMPLETED**, held amount gone | "The buyer confirms the book arrived." |
| Switch to the seller window, refresh | balance **increased by 55.00** | "Now the seller has the money. Not on payment, not on dispatch — here. In the code this is not a branch that names the confirm-receipt action: the order state itself answers whether it holds a payment, and the payout fires when the order leaves the group of states that do. Adding a state later cannot forget to settle." |
| Open the order's transaction list | **two** rows: `PAYMENT` 55.00, `PAYOUT` 55.00 | "And the ledger has two rows, not one amended row — the money coming in and the money going out are separate facts." |

### Act 5 — The refund line *(cuttable)*

| Do | Screen must show | Say |
|---|---|---|
| Place and pay a second order on product 4, same way | status **PAID** | "Same starting point, different ending." |
| Seller window: Refund instead of Ship | status **REFUNDED** | "The seller refunds rather than dispatching." |
| Buyer balance; then the transaction list | balance restored; rows `PAYMENT` + `REFUND` | "The buyer is whole again, and notice what *cannot* happen here: this refund cannot fail because the seller already spent the money, because the seller never had it. Holding the payment is what makes the refund path safe, not a compensation routine." |

### Act 6 — A fifth payment method *(cuttable)*

The branch exists: `m3/fifth-payment-method`, built and tested, **PayNow**.
Do **not** write code or restart the backend on stage. A backend restart is
over 40 seconds and a compile error ends the demo. Show the change instead:

| Do | Screen must show | Say |
|---|---|---|
| Editor/terminal: `git diff --stat` for the branch | six files — five of code: the enum, one new class, two Vue views, one test; the sixth is this script | "Here is everything a fifth payment method cost." |
| Open `PayNowPaymentStrategy` | one class, ~20 lines of code | "Three questions: take the money, hand it to the seller, give it back. PayNow answers two of them exactly as the other external methods do, so it inherits those. It differs in one: a PayNow transfer cannot be pulled back, so the refund comes back as account balance instead." |
| `git diff` on `PaymentService` and the order flow | **no changes** | "The order flow and the payment service are untouched, and there is no database migration — the method is a token the ledger stores. Spring collects every strategy into a map keyed by its method, so the new one registers itself." |

Say the limitations before you are asked in Q&A — all three are visible in
that same diff, so claiming it is purely additive would not survive a look:

- the `PaymentMethod` enum gains a value, and the payment page gains an option;
- the human-readable label for a method is written out **twice**, in
  `PaymentView.vue` and `OrderDetailView.vue`, so a fifth method has to be
  added to both or the order page prints the raw token;
- the extension point removes the branching from the *order flow*, which is
  where a fifth branch would have done real damage — not from the identifier
  list and not from the two label maps.

The column comment in `init.sql` still lists four methods. It is documentation
that went stale, and it was left alone on purpose: the migration has already
run everywhere, and editing an applied migration to fix a comment is worse
than the stale comment. The enum is the source of truth.

**If this branch is merged before the 26th** the option is simply there, and
act 6 can show PayNow being *used* — pay by PayNow, then refund, and the
buyer's balance moves — with the diff as the follow-up rather than the whole
act. That is the better version of this act, and it is the reason to merge it
rather than keep it parked as a demo prop.

---

## 4. If it breaks

| Symptom | Do this |
|---|---|
| Payment page rejects the password | you are logged in as `admin`, not `demo_buyer` — admin has no payment password |
| Product shows out of stock | `demo-seed.sql` was not run against the database the app is pointed at; if stock reads exactly 1, somebody re-ran `init.sql` |
| A readback row reads `MISSING` | the table was recreated under you — re-run `init.sql`, then `demo-seed.sql`, in that order |
| Seller's Ship button missing | you are in the buyer window, or the order is not PAID |
| Held amount missing on order detail | it is populated only on the single-order view, not the list — open the order itself |
| Anything else | stop, say "I'll come back to this", hand over to the next segment, and cut to the backup recording at the end if time allows |

Rehearsal log: record the run date, who drove, where it stalled, and the
elapsed time, in the rehearsal notes for 25 Oct.
