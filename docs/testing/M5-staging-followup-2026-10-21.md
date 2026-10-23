# Staging acceptance follow-up — 21 October 2026

Environment: http://178.128.107.99/ (5006 / c2cTradePlatform).
Timezone: Asia/Singapore. Deployed SHA: unverified.
Baseline: [17–18 October report](M5-staging-acceptance-2026-10-17.md).

## Session precheck

The frontend loaded. A cached buyer identity appeared briefly, followed by a redirect to the login page when requests completed. Reauthentication was completed by the user. The buyer account is active in the in-app browser and the dedicated seller is active in Safari. Cached identity alone was not treated as evidence of a valid session.

## Independent follow-up cases

| Case | Expected evidence | Status |
|---|---|---|
| Favorites | Add dedicated listing, refresh, verify favorites entry, remove, verify removal persists | BLOCKED — no add-to-favorites control found in the current product detail UI |
| Parent category regression | Same dedicated listing found with keyword alone and with its parent category | FAIL — keyword-only finds the QA listing; Books + All excludes it |
| Chat | Dedicated buyer and seller receive each other's messages; history persists after reload | BLOCKED — seller avatar advertises chat, but two clicks produced no chat panel or navigation |
| Listing editing and image upload | Owner saves isolated listing and image; fresh detail shows saved values | BLOCKED — seller session available, but no edit control found on the dedicated listing detail |
| Wrong payment password | Single incorrect attempt refused, order remains unpaid, no balance change | PASS (UI refusal) — incorrect passwords rejected; order remained awaiting payment |
| Simulated bank-card purchase | Payment, hold, dispatch, receipt and seller balance delta recorded | PASS (UI) — simulated bank-card payment, dispatch and receipt completed; seller balance increased from S$2.90 to S$3.90 only after receipt |
| Excess-stock quantity | Quantity above available stock is refused without unintended order/payment | PASS (cart UI) — quantity 8 refused for stock 7; quantity stayed 7; test cart line removed |
| Helper button regression | Previously assisted user cannot submit another help on a fresh page | NOT RUN — eligible campaign/session required |

Statuses above distinguish executed observations from blocked and unexecuted checks. They do not constitute full system acceptance. Keep credentials out of evidence. Do not use 5001 results as 5006 evidence.

## Recorded observations (approximately 22:44–22:53 Singapore)

- Dedicated listing: QA Test Book - CUI, seller cui_qa_seller, price S$1.00, displayed stock 7. Description explicitly identifies it as a staging QA listing.
- Parent-category regression: search `QA Test Book - CUI` with no filters returned the listing. Selecting Books and leaving subcategory All, then applying, returned `No products found`. Clear Filters restored the listing. This reproduces the prior defect; no fixed deployed revision was established.
- Chat entry: clicked the dedicated seller avatar via accessibility and then its visible screenshot coordinates. No chat panel or route change appeared. Direct navigation to the source-defined /chat route displayed Conversations / No messages yet / Pick a conversation. This is an entry-point failure/blocker; message transport has not been tested.
- Favorites: no favorite action appeared in the full product-detail accessibility tree. Direct navigation to the source-defined /favorites route displayed Saved items / Nothing saved yet. This verifies the empty list view only; adding, persistence and removal remain blocked. Do not infer backend removal solely from the missing control.
- Stock boundary: sequentially increased cart quantity to 7; the next increment was refused with `Only 7 left of QA Test Book - CUI`. Quantity and total remained 7 / S$7.00. Removed the cart line and confirmed `Your cart is empty`. No checkout was performed for the boundary test. Rapid repeated earlier clicks only advanced one unit; this was not a concurrency test.
- Campaign follow-up: selector lists the old buyer and helper campaigns as Expired. The selected helper floor campaign shows S$0.60, one helper, 100% progress and no help action. This is after-deadline UI evidence only, not evidence of scheduler execution/restart recovery. Buyer campaign detail was not separately selected.
- Payment precondition: a new dedicated single-unit S$1 order was created after clearing the boundary-test cart. Bank card is explicitly labelled simulated, with no real money movement. The deliberately incorrect password returned `Payment password is incorrect, 4 attempt(s) left`; order remained Awaiting payment. The user then supplied a presumed correct password, but it was also refused, leaving 3 attempts. Further attempts were stopped and user verification requested. The user subsequently supplied the correct password and the simulated bank-card payment succeeded. No passwords are recorded here.
- Seller starting balance: S$2.90, credit 83/100 in Safari. Buyer starting balance: S$0.00.

## Evidence limits

Observations are from live browser accessibility trees and screenshots inspected during this session. The committed [stock-boundary accessibility capture](evidence/2026-10-21/cart-stock-boundary.txt) contains the refusal and retained quantity. No database ledger, server trace, deployed SHA or full automated report was obtained. Local source at 267cbd9 contains an `open-chat` dispatch but no matching listener found under frontend/src; this supports investigation only and is not a statement about deployed code identity.

## Simulated bank-card completion (approximately 22:59–23:02 Singapore)

The dedicated S$1 single-unit order was paid successfully with Bank card. Buyer detail displayed Paid and S$1 held. A newly loaded seller home still showed S$2.90 before dispatch, unchanged from the pre-payment baseline. Seller detail independently showed Paid with Bank card and S$1 held. Dispatch changed the order to Shipped and removed the seller dispatch/refund controls. Buyer navigated back to the order list and reopened detail to retrieve the current Shipped state, then confirmed receipt. The order became Completed; the held amount and receipt button disappeared. A freshly reloaded seller detail independently showed Completed, no held amount and no dispatch/refund controls. Seller home subsequently showed S$3.90 (exactly +S$1) and credit 84/100 (previously 83/100).

Seller refresh also displayed PAID, SHIPPED and COMPLETED notification toasts for this order. This is evidence of these three visible notifications after refresh, not a guarantee of immediate/offline delivery across all event types. Database ledger uniqueness and concurrent settlement remain untested. No real payment gateway was involved. The dedicated order is completed; no unpaid test order remains from this run.

## Repository and deployment reconciliation

After testing, GitHub confirmed [PR #135](https://github.com/NUS-SE-34FT-GROUP4/c2cTradePlatform/pull/135) merged category-filter and payment-method-copy fixes into main at `7a590400a394d2198c478332e62f74cdb736dbd2`. The corresponding [workflow run](https://github.com/NUS-SE-34FT-GROUP4/c2cTradePlatform/actions/runs/39598700575) was waiting when checked. This does not establish the live deployment SHA or identify which approval is waiting. Verify deployment before asking for a duplicate fix. The observed staging category failure remains valid, but is not evidence that the merged fix fails.

See [owner handoff](M5-defect-handoff-2026-10-21.md) for reproduction steps and retest criteria.
