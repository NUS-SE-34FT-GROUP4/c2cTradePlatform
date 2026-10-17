# Staging acceptance report — 17–18 October 2026

Environment: http://178.128.107.99/ (5006 / c2cTradePlatform).
Timezone: Asia/Singapore. Execution: manual browser walkthrough with dedicated buyer, seller and disposable QA listing. Deployed SHA remains unverified.

This is partial UI acceptance, not a complete sign-off against the Sprint 4 acceptance plan. Results below describe visible behavior; database ledger correctness, concurrency and API authorization require separate evidence. Account secrets and raw transaction identifiers are excluded.

## Summary

Core purchase, refund, review, negotiated purchase and tested campaign paths passed their observed UI checks. Parent-category filtering failed. This report is not full system acceptance: administrator workflows, concurrency, ledger invariants, operational recovery and deployed-version verification remain outstanding. PASS applies only to the stated steps. Evidence consists of live browser observations recorded during the session; screenshots, network traces and database extracts are not included.

## Observed results

| Scenario | Result | Observation |
|---|---|---|
| Listing creation and self-purchase prevention | PASS (UI) | Dedicated seller published the QA listing; own-listing purchase was disabled. |
| First purchase walkthrough | PASS (UI) | Simulated Alipay payment → dispatch → receipt confirmation → completed. Held amount appeared while paid/shipped and disappeared after completion. Seller balance subsequently increased by the purchase amount. |
| Review and duplicate prevention | PASS (UI) | Completed purchase accepted one five-star item/seller review; listing displayed it. A second visit to the review form was refused. |
| Paid-order refund | PASS (UI) | Simulated PayNow purchase refunded by the dedicated seller. Stock restored; buyer balance credited; seller balance unchanged; held amount removed. Ledger and repeated-refund API behavior not checked. |
| Refunded-order review | PASS (UI) | Review route refused the refunded purchase. |
| Second purchase walkthrough | PASS (UI) | Refunded balance funded a second purchase. Dispatch and receipt confirmation completed; stock decreased by one and hold disappeared. Subsequent seller login confirmed the balance increased by exactly the second purchase amount. Database ledger verification remains separate. |
| Credit score refresh | PASS (observed values) | Seller badge moved from unrated to 81 after the first completed/reviewed purchase, and to 82 after the second completion. Refund did not increase it. Boundary cases remain untested. |
| Keyword search | PASS (UI) | Searching for the QA listing title returned the dedicated listing. |
| Location filter and empty state | PASS (UI) | Matching QA location retained the listing; unique nonexistent location returned “No products found”. Clear Filters reset inputs and restored the catalog. |
| Offer, counter-offer and negotiated purchase | PASS (UI happy path) | At approximately 21:19, buyer submitted an offer at 80% of listing price. Thread showed Pending / Negotiating and a success message. Seller received the offer notification. At approximately 21:45, seller countered at 90% of listing price; original offer became Countered and the new offer became Pending, awaiting buyer response. At approximately 22:18, buyer accepted the counter-offer. Checkout used 90% of listing price; simulated WeChat Pay succeeded, and order total and held amount matched the agreement. Stock decreased by one. Thread showed Deal agreed / Accepted, with no new-offer controls. Seller view confirmed the negotiated total and hold; seller balance remained unchanged before dispatch. Dispatch succeeded and status became Shipped, with the negotiated amount still held and dispatch/refund controls removed. Buyer confirmed receipt successfully. A fresh page confirmed Completed persisted, the held-amount display disappeared, and no repeat receipt control remained. Subsequent seller login confirmed the balance increased by exactly the negotiated purchase amount and credit increased from 82 to 83. Database ledger correctness, concurrent settlement and backend idempotency remain unverified. |
| Insufficient balance | PASS (UI) | With balance below the negotiated total, entering the payment password did not enable balance payment. Selecting a simulated gateway enabled payment. Backend refusal remains untested. |
| Price boundary filters | PASS (UI) | QA listing matched inclusive min=max at its listing price; raising the minimum above that price excluded it. |
| Condition filters | PASS (UI) | QA listing matched Like new (9/10), and was excluded by Brand new. |
| Completed order seller controls | PASS (UI) | Fresh seller detail showed Completed with no dispatch or refund action. Direct API enforcement remains untested. |
| Campaign starting-price validation | PASS (browser validation) | Below-floor and above-listing-price inputs were blocked by native validation. Backend validation was not exercised. |
| Campaign creation | PASS (UI) | At approximately 23:32 Singapore, blank starting price created an Active campaign at 80% of listing price, with a 60% floor and zero helpers. Deadline displayed 19 October 2026 23:31 local time. Refresh preserved the campaign. Creation form and self-help control were absent for its creator; backend duplicate creation was not tested. |
| First campaign help and repeat-help UI | PASS (UI) | At approximately 23:40 Singapore, an independent helper reduced the campaign price from 80% to 75% of listing price. Helper count became one and progress became 25%. Help button changed to disabled “You helped this campaign”; campaign refresh preserved price, count and disabled state. Listing stock remained unchanged. The subsequent fresh-page duplicate attempt is covered below. |
| Seller offer rejection and thread isolation | PASS (seller UI) | Seller confirmed rejection of the independent helper offer. Its status became Rejected and persisted after thread refresh; the page stated that a new offer remains possible. The original buyer thread remained Deal agreed at its accepted price, and listing stock stayed unchanged. Resubmission and later status polling are covered below; notification toast delivery remains unverified. |
| Offer resubmission after rejection | PASS (UI) | Helper saw the original Rejected offer and successfully submitted a new offer at 72% of listing price, shown as Pending alongside the rejected history. No rejection toast was observed in this fresh page; notification delivery is unverified. |
| Repeat help after fresh login/page | PASS (duplicate refused), UI issue | Previously assisted helper saw an enabled help button on a fresh page despite being listed as a helper. Clicking returned “You already helped this campaign”, disabled the button and left price/helper count unchanged. |
| Short-deadline campaign expiry (18 October) | PASS (UI) | Helper created a separate campaign at 61% of listing price, with a 60% floor and deadline 18 October 00:26 Singapore. It displayed approximately two minutes remaining. At the deadline, refresh showed Expired and no help action, with history retained and a new-campaign form available. Price, helper count and displayed stock remained unchanged. A separate buyer selected this expired helper campaign and also saw Expired, preserved price/history and no help action. The buyer’s original campaign remained Active. This does not independently establish scheduler execution, backend late-help rejection, restart safety or multi-instance behavior. |
| Campaign floor clamp (18 October) | PASS (single help crossing floor) | Using isolated Chrome helper and in-app buyer sessions, helper created a new campaign at 61% of listing price after its earlier campaign expired. Buyer help reduced it to exactly the 60% floor, rather than below it; progress became 100% and helper count one. Chrome creator view independently updated to the same price/count. Another member’s disabled at-floor action is covered below; direct requests and concurrent helps remain untested. New campaign deadline: 20 October 00:42 Singapore. |
| At-floor action restriction | PASS (UI) | Safari seller, a separate member who had not helped the new campaign, saw a disabled Floor price reached action at the floor. No further-help API request was issued. |
| Resubmitted offer cleanup and polling | PASS (status sync) | Seller rejected the pending helper offer; Chrome helper view automatically changed to Rejected. No notification toast was observed, so toast delivery remains unverified. No pending helper offer remains from these tests. |
| Admin route restriction | PASS (buyer route) | Buyer navigation to /admin/reports redirected to the marketplace home. Admin API authorization remains untested. |
| Non-owner listing update | PASS (save refused), UI issue | Buyer could load the dedicated seller listing edit form, but submitting its unchanged values returned “You can only modify your own listings”. No intentional field changes were submitted. |
| Cart decrement to zero | PASS (UI) | Adding the QA listing succeeded; decrementing quantity one removed the line and showed an empty cart. No checkout was performed and the cart was left empty. |
| User report required reason | PASS (UI validation only) | User-report dialog showed reason choices, optional details and disabled Submit without a reason. Cancel closed it; no report was sent. |
| Parent category search | FAIL | Searching QA Test Book with Books selected returned No products found; clearing category and repeating the same query returned the QA listing. Listing detail identifies Books & Media and edit form identifies Books. |

## Functional defect: parent-category filtering

Reproduction: search the dedicated QA listing, select Books as main category with subcategory All, apply, then clear filters and repeat the keyword search. Expected: a listing published as Books remains visible under Books. Actual: it is excluded under Books and returns without the category filter.

Local source supports a likely explanation (deployed SHA unverified): ProductCreate.vue stores `books`, while HomeView.vue getCategoryFilter returns only child category values and omits `books`. ProductMapper.xml matches category membership exactly using FIND_IN_SET. This is a source-supported diagnosis, not a deployed-code identity claim. No application code was changed during acceptance.

## Minor observations

- Non-owners can open a listing edit form; saving is correctly refused. Prefer an early read-only/error state.
- Fresh helper page initially enables the help button for an existing helper; duplicate attempt is refused correctly, then the button becomes disabled. Earlier same-page refresh preserved the disabled state, so it did not cover fresh-page initialization.
- Inverted price range (minimum 2, maximum 1) showed only “No products found”, without an invalid-range explanation. This is a usability observation, not evidence of incorrect filtering.
- Payment selection presents five methods, but its helper text still says “All four methods”.
- Refunded status capitalization differs between list and detail views.

## Unverified coverage and prerequisites

| Area | Outstanding verification | Prerequisite |
|---|---|---|
| Administrator workflow | Submit and deduplicate reports; dismiss/uphold; ban and reinstate; audit log and existing-session enforcement | Authorized admin session and disposable moderation targets |
| Concurrency and idempotency | Last-unit contention; simultaneous helps; duplicate payment/settlement; payment versus refund/expiry races | Controlled test plan and ledger/stock evidence |
| Backend authorization | Direct unauthenticated, wrong-owner and wrong-role requests across endpoints | API test session and agreed scope |
| Ledger correctness | Exactly-once payment/refund/payout records and reservation accounting | Owner-provided database evidence |
| Operational expiry and recovery | Unpaid-order deadlines, restart recovery, multi-instance processing | Deployment-owner coordination |
| Search resilience | Search outage fallback and recovery parity | Deployment-owner coordination |
| Payment coverage | Simulated bank-card payment, wrong payment password, refund/settlement combinations for each method | Dedicated accounts and test orders |
| Notifications | Rejection toast delivery, offline delivery and remaining event types | Isolated account sessions |
| Other features and edges | Favorites, chat, recommendation strategies, image upload/edit, excess-stock quantities and remaining filter combinations | Separate scenario execution |
| Release evidence | Deployed SHA/digest, current automated suite and coverage, criterion-level mapping | Deployment and CI evidence |
| Defect regression | Parent-category filtering and recorded UI issues after fixes | Fixed deployment |

## Test-data handoff

- Dedicated purchase and negotiated-purchase flows were completed; the separate PayNow refund was completed.
- The resubmitted helper offer was rejected for cleanup; the buyer cart was left empty.
- Original buyer campaign remains scheduled to expire on 19 October at 23:31 Singapore. The helper floor-test campaign expires on 20 October at 00:42. Their eventual expiry has not been observed.
- The separate short-deadline helper campaign expired on 18 October at 00:26.
- An older transaction involving a seeded seller remains outside the dedicated-account refund walkthrough and needs owner-assisted cleanup.
- No shared infrastructure was disrupted. No application code or deployment configuration was changed. Raw credentials and transaction identifiers are excluded.
