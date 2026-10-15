# Sprint 4 system acceptance plan (#77)

Owner: CUI ZIJIAN (M5). Prepared 15 October 2026 against main `3c29e67`.
Execution window: 18–21 October; feature cutoff 17 October; freeze 21 October 24:00 (Singapore).
**Preparation only: no staging cases or demo passes have been executed by this plan.**
Closed GitHub issues and automated tests do not establish staging acceptance.

## Entry checklist

- [x] Record the c2cTradePlatform staging URL: frontend http://178.128.107.99/ ; backend http://178.128.107.99:8080 (provided by CUI on 15 October). Do not use the 5001/ass CloudFront endpoint.
- [ ] Record the deployed SHA/image digest and deployment run; endpoint availability does not establish the deployed version.
- [ ] Confirm backend health and UI access. Latest observed main run [38576777677](https://github.com/NUS-SE-34FT-GROUP4/c2cTradePlatform/actions/runs/38576777677) was waiting on 15 October; this does not prove the latest SHA is deployed.
- [ ] Confirm dedicated test data with module owners. Use buyer A, buyer B, seller, friend/helper, administrator, fresh unrated user and a disposable moderation target. Keep login and payment secrets outside this document.
- [ ] Provision active stock, a last-unit item, multiple categories, insufficient-balance buyer, pending/paid/shipped/completed/refunded orders and expiring campaigns. Record initial balances, stock, reservations and IDs.
- [ ] Reserve demo products 4 and 5 for the agreed demo runs; use separate listings for negative/concurrency tests. Do not reset shared data or execute init.sql during QA.
- [ ] Use separate browser profiles for buyer, seller and admin; tabs share authentication state.
- [ ] Agree service-outage/restart/multi-instance checks with M1 and M3. QA records evidence; service owners operate the shared deployment.

## Connectivity precheck — 15 October 2026

Read-only HTTP checks: frontend `/` on port 80 returned 200 with the SecTrade marketplace application shell. Backend `/actuator/health` on port 8080 returned 200 and UP, including MySQL and Redis. Backend `/` returned 401 (unauthenticated access); it is not the frontend entry. This verifies connectivity and reported health only, not browser rendering, login, deployed SHA or business acceptance. No accounts or business data were changed.

## Execution and evidence rules

For every criterion below, replace NOT RUN with PASS, FAIL, BLOCKED or N/A (N/A needs a reason and owner agreement). Capture timestamp/timezone, deployed SHA, account aliases, item/order IDs, exact steps, expected/actual and an evidence link. Never include passwords, tokens, cookies or personal data in committed screenshots/logs.

Each AC row is independently assessed, even when one walkthrough covers several rows. Code/design criteria require linked code and test reports; pipeline criteria require a real run; operational criteria require staging evidence. Do not infer PASS from issue CLOSED.

Use the issue's current assignees for defect ownership; if none exist, confirm the module owner before assigning. Shared-service scenarios can be BLOCKED without pretending they passed. Retest fixes on the deployed replacement SHA and retain the original failure evidence.

## Scenario instructions

| Issues | Procedure and evidence |
|---|---|
| #61–63 | As guest/buyer, try admin navigation and direct endpoints; submit item and user reports with reason/text, repeat duplicate submission; admin paginates queue, dismisses one and upholds another. Verify visible decisions, audit records, target not notified, ban blocks login/listing/order including existing sessions; reinstate and repeat. Use disposable targets. |
| #64, #66, #84 | With M1, retain approval/rejection, test/scan/publish/deploy artifacts, digest-to-SHA mapping, previous-image rollback and forward restoration timings. Verify unchanged running image after rejection and health after rollback. Seed demo data only after final deployment. |
| #65 | Use the same recorded query/filter set before ES outage, during owner-controlled outage and after recovery without backend restart. Compare listing-ID sets (ordering may differ), page success and search-path log events. Follow search-parity.md. |
| #67, #70 | Start campaign from item UI; record price/deadline, duplicate active campaign rejection, unique helper reductions, repeated helper refusal and floor. Wait for expiry, attempt another help and check expired UI plus unchanged reservations. Verify scheduler implementation with owner. |
| #71 | Separate buyer/seller sessions: offer, counter both directions, reject and accept on separate threads; inspect notifications and negotiated checkout amount. Retry on closed/expired threads. With two buyers and one available unit, coordinate simultaneous acceptance and inspect order/reservation results. |
| #68, #69, #109 | Record balances/stock/ledger before payment; pay, verify hold and unchanged seller balance; ship then confirm, verify exactly one payout. Separate paid order: refund and repeat refund; inspect original payment plus refund rows. Refuse completed-order refund. Repeat settlement timing for each simulated gateway. Coordinate concurrent pay/expiry, pay/refund and receipt/refund; inspect one legal outcome, clear loser response, no negative stock or extra ledger rows. |
| #72 | On every main-line screen exercise empty data, throttled request, failed request, insufficient balance and invalid transition. Check actionable empty state, loading, readable refusal and browser console. Do not disable shared services merely to trigger a UI error. |
| #73 | Record documented score inputs; compare fresh unrated account, completed sale, review submission and refreshed account/list badges. Link boundary unit tests; exclude uncompleted sales from calculation. |
| #74 | Request both recommendation strategies, unknown strategy and unavailable-strategy fallback; verify usable item page. Link registry/factory and isolated strategy tests; inspect that adding a strategy does not edit caller or hard-code implementations. |
| #75 | Collect current main mvn verify reports, real-database integration coverage for each named path, controller/mapper evidence and JaCoCo root instruction/branch/line counters. Read current pom gate rather than copying historical percentages. |
| #78 | With M3/M1, record unpaid order and campaign deadlines, observed expiry and stock release, rejection of later help, restart mid-window and two-instance single-processing evidence. Record actual elapsed timings for demo. |
| #76, #79–83, #85–94 | Review each artifact against its individual AC: board configuration, code-aligned design write-ups, demo seed/script, fallback video, timed slides, report, architecture and presenter notes. These are artifact reviews, not simulated UI passes; future-due work remains NOT RUN/BLOCKED with its due date. |

Reference procedures: [demo script](../demo/demo-script.md), [cart entry points](cart-entry-points.md), [search parity](search-parity.md), [admin UI](admin-ui.md), [bargaining UI](bargaining-ui.md), [release evidence](release-evidence.md), [M5 integration evidence](M5-sprint4-integration.md).
The demo script's 14 October missing-cart finding is historical: latest main contains cart-entry fixes; verify them live instead of copying that finding as a new defect.

## Acceptance matrix

AC text below is a 15 October GitHub snapshot; check for changed criteria before execution. Issue #77 is assessed by this plan's execution records and summary, not by checking itself off recursively.

### #61 — Administrator back office: report queue, ban and reinstate

[Issue and current owners](https://github.com/NUS-SE-34FT-GROUP4/c2cTradePlatform/issues/61) · source status: CLOSED.

| ID | Expected acceptance | Result | Evidence / defect |
|---|---|---|---|
| 61.1 | A report queue lists open reports with reporter, target, reason and time | NOT RUN | — |
| 61.2 | An administrator can uphold a report (ban the target) or dismiss it, with a recorded decision | NOT RUN | — |
| 61.3 | A banned account cannot log in, list items, or place orders; the refusal states the reason | NOT RUN | — |
| 61.4 | Reinstating a banned account restores every capability, and both actions are audit-logged | NOT RUN | — |
| 61.5 | Only an account with the administrator role reaches any of these endpoints | NOT RUN | — |

### #62 — Administrator back office pages

[Issue and current owners](https://github.com/NUS-SE-34FT-GROUP4/c2cTradePlatform/issues/62) · source status: CLOSED.

| ID | Expected acceptance | Result | Evidence / defect |
|---|---|---|---|
| 62.1 | The back office is reachable only for the administrator role; other roles never see the entry | NOT RUN | — |
| 62.2 | The report queue paginates and shows each report's decision state | NOT RUN | — |
| 62.3 | Uphold and dismiss are confirmable actions whose outcome is visible without a page reload | NOT RUN | — |
| 62.4 | Ban and reinstate are available from the account view, with the current state shown | NOT RUN | — |
| 62.5 | Empty, loading and error states are present, because this screen is demonstrated live | NOT RUN | — |

### #63 — Report an item or a user

[Issue and current owners](https://github.com/NUS-SE-34FT-GROUP4/c2cTradePlatform/issues/63) · source status: CLOSED.

| ID | Expected acceptance | Result | Evidence / defect |
|---|---|---|---|
| 63.1 | An item page and an account page each offer a report action with a reason and free text | NOT RUN | — |
| 63.2 | A report is stored with reporter, target, reason, free text and time, and enters the queue in S4-01 | NOT RUN | — |
| 63.3 | The same reporter cannot file duplicate open reports against the same target | NOT RUN | — |
| 63.4 | The reporter is told the report was received; the target is not notified | NOT RUN | — |
| 63.5 | Reporting requires a logged-in account | NOT RUN | — |

### #64 — Approval gate and rollback job in the deployment workflow

[Issue and current owners](https://github.com/NUS-SE-34FT-GROUP4/c2cTradePlatform/issues/64) · source status: CLOSED.

| ID | Expected acceptance | Result | Evidence / defect |
|---|---|---|---|
| 64.1 | Deployment to staging waits on a GitHub environment approval before it runs | NOT RUN | — |
| 64.2 | A rollback job redeploys the previously published image tag, selectable without editing YAML | NOT RUN | — |
| 64.3 | The workflow records which image tag is live after each deployment | NOT RUN | — |
| 64.4 | A rejected approval leaves the running version untouched | NOT RUN | — |
| 64.5 | The whole sequence is runnable on demand, because it is demonstrated on 26 October | NOT RUN | — |

### #65 — Search degradation does not silently change the result set

[Issue and current owners](https://github.com/NUS-SE-34FT-GROUP4/c2cTradePlatform/issues/65) · source status: CLOSED.

| ID | Expected acceptance | Result | Evidence / defect |
|---|---|---|---|
| 65.1 | A test exercises the same query set against both the Elasticsearch path and the SQL path | NOT RUN | — |
| 65.2 | For the demo query set, both paths return the same listings; only ordering may differ | NOT RUN | — |
| 65.3 | Which path served a request is observable in the logs or a health endpoint | NOT RUN | — |
| 65.4 | Taking Elasticsearch down does not fail the page, and recovery needs no restart | NOT RUN | — |

### #66 — Image scanning and release evidence collected from the pipeline

[Issue and current owners](https://github.com/NUS-SE-34FT-GROUP4/c2cTradePlatform/issues/66) · source status: CLOSED.

| ID | Expected acceptance | Result | Evidence / defect |
|---|---|---|---|
| 66.1 | The Trivy stage's findings are retained as a build artefact, not only in the log | NOT RUN | — |
| 66.2 | A run is captured showing test, scan, image publish and deploy in one pipeline | NOT RUN | — |
| 66.3 | The image digest published to GHCR is recorded against the commit that produced it | NOT RUN | — |
| 66.4 | The evidence is exported in a form that drops straight into the slides | NOT RUN | — |

### #67 — Bargaining frontend: campaign page and offer thread

[Issue and current owners](https://github.com/NUS-SE-34FT-GROUP4/c2cTradePlatform/issues/67) · source status: CLOSED.

| ID | Expected acceptance | Result | Evidence / defect |
|---|---|---|---|
| 67.1 | An item page offers starting a campaign, and shows a running campaign's current price and deadline | NOT RUN | — |
| 67.2 | The offer thread shows each offer, counter-offer and its outcome in order | NOT RUN | — |
| 67.3 | Accepting an offer moves the buyer into checkout at the negotiated price | NOT RUN | — |
| 67.4 | An expired campaign renders as expired rather than disappearing | NOT RUN | — |
| 67.5 | Empty, loading and error states are present | NOT RUN | — |

### #68 — Refund recorded as an independent ledger transaction

[Issue and current owners](https://github.com/NUS-SE-34FT-GROUP4/c2cTradePlatform/issues/68) · source status: CLOSED.

| ID | Expected acceptance | Result | Evidence / defect |
|---|---|---|---|
| 68.1 | A refund writes a new ledger entry; the original payment entry is never updated or deleted | NOT RUN | — |
| 68.2 | The buyer's balance increases by the refunded amount, and the seller's pending amount drops | NOT RUN | — |
| 68.3 | The order's state reflects the refund, and the pair of entries is traceable from the order | NOT RUN | — |
| 68.4 | A second refund attempt on the same order is refused | NOT RUN | — |
| 68.5 | Refunding and paying concurrently cannot both succeed | NOT RUN | — |

### #69 — Stock and ledger stay consistent under concurrent payment

[Issue and current owners](https://github.com/NUS-SE-34FT-GROUP4/c2cTradePlatform/issues/69) · source status: CLOSED.

| ID | Expected acceptance | Result | Evidence / defect |
|---|---|---|---|
| 69.1 | A test fires concurrent payment attempts against one unit of stock; exactly one succeeds | NOT RUN | — |
| 69.2 | Stock never goes negative, and the ledger has no entry for the losing attempt | NOT RUN | — |
| 69.3 | Concurrent expiry and payment on the same order resolve to one outcome, not both | NOT RUN | — |
| 69.4 | The losing attempt gets a clear refusal, not a timeout or a 500 | NOT RUN | — |

### #70 — Bargain campaign: create, invite help and expire

[Issue and current owners](https://github.com/NUS-SE-34FT-GROUP4/c2cTradePlatform/issues/70) · source status: CLOSED.

| ID | Expected acceptance | Result | Evidence / defect |
|---|---|---|---|
| 70.1 | A buyer starts a campaign on an available item, with a starting price and a deadline | NOT RUN | — |
| 70.2 | A friend can help once per campaign; each help reduces the price by a bounded amount | NOT RUN | — |
| 70.3 | The price never falls below the seller's floor, however many friends help | NOT RUN | — |
| 70.4 | A campaign expires at its deadline and stops accepting help, releasing nothing it did not hold | NOT RUN | — |
| 70.5 | One active campaign per buyer per item | NOT RUN | — |
| 70.6 | Expiry is driven by the same scheduling approach as unpaid-order expiry, not a new mechanism | NOT RUN | — |

### #71 — Make, counter, accept or reject an offer

[Issue and current owners](https://github.com/NUS-SE-34FT-GROUP4/c2cTradePlatform/issues/71) · source status: CLOSED.

| ID | Expected acceptance | Result | Evidence / defect |
|---|---|---|---|
| 71.1 | A buyer makes an offer; the seller may accept, reject or counter; the buyer may counter back | NOT RUN | — |
| 71.2 | Accepting produces an order at the negotiated price, reserving stock like any other order | NOT RUN | — |
| 71.3 | An accepted offer closes the thread; no further offers are possible on it | NOT RUN | — |
| 71.4 | Rejected and expired offers leave no reservation behind | NOT RUN | — |
| 71.5 | Two buyers' offers on the last unit cannot both be accepted | NOT RUN | — |
| 71.6 | Both parties are notified of each outcome through the existing notification path | NOT RUN | — |

### #72 — Demo-facing UI polish: empty, loading and error states

[Issue and current owners](https://github.com/NUS-SE-34FT-GROUP4/c2cTradePlatform/issues/72) · source status: CLOSED.

| ID | Expected acceptance | Result | Evidence / defect |
|---|---|---|---|
| 72.1 | Every screen on the demo main line has an empty state with a next action | NOT RUN | — |
| 72.2 | Requests show a loading state rather than an unchanged screen | NOT RUN | — |
| 72.3 | A failed request shows a readable message, never a stack trace or a bare status code | NOT RUN | — |
| 72.4 | A refused action (insufficient balance, invalid transition) states the reason | NOT RUN | — |
| 72.5 | Walking the full demo script produces no console error | NOT RUN | — |

### #73 — Credit score derived from completed transactions and reviews

[Issue and current owners](https://github.com/NUS-SE-34FT-GROUP4/c2cTradePlatform/issues/73) · source status: CLOSED.

| ID | Expected acceptance | Result | Evidence / defect |
|---|---|---|---|
| 73.1 | A score is computed from completed orders and the reviews received, by a documented rule | NOT RUN | — |
| 73.2 | The score appears on the account view and beside listings in the item list | NOT RUN | — |
| 73.3 | A new review changes the score without a manual recompute step | NOT RUN | — |
| 73.4 | An account with no history shows as unrated, not as zero | NOT RUN | — |
| 73.5 | The computation is unit-tested over the boundary cases, including no history | NOT RUN | — |

### #74 — Recommendation strategy chosen by a self-registering factory

[Issue and current owners](https://github.com/NUS-SE-34FT-GROUP4/c2cTradePlatform/issues/74) · source status: CLOSED.

| ID | Expected acceptance | Result | Evidence / defect |
|---|---|---|---|
| 74.1 | At least two strategies exist (for example most-recent and same-category-by-credit) | NOT RUN | — |
| 74.2 | A factory resolves the strategy by name; adding a third needs no change to the caller | NOT RUN | — |
| 74.3 | Strategies register themselves; the factory holds no hard-coded list of implementations | NOT RUN | — |
| 74.4 | An unknown or unavailable strategy falls back to a default rather than failing the page | NOT RUN | — |
| 74.5 | Each strategy is unit-tested in isolation, and the factory's resolution is tested | NOT RUN | — |

### #75 — Integration tests over the payment and fulfilment paths

[Issue and current owners](https://github.com/NUS-SE-34FT-GROUP4/c2cTradePlatform/issues/75) · source status: CLOSED.

| ID | Expected acceptance | Result | Evidence / defect |
|---|---|---|---|
| 75.1 | Integration tests cover pay, ship, confirm receipt, cancel and expire against a real database | NOT RUN | — |
| 75.2 | Refund (S4-08) and one bargaining acceptance (S4-11) are covered | NOT RUN | — |
| 75.3 | Controller and mapper layers are exercised, since that is where the coverage gap sits | NOT RUN | — |
| 75.4 | Line coverage rises from 44.5% and the 40% CI gate still passes | NOT RUN | — |
| 75.5 | The suite runs in `mvn verify` under the existing failsafe setup, not as a manual step | NOT RUN | — |

### #76 — Board estimates and iteration configured for the Sprint 4 burndown

[Issue and current owners](https://github.com/NUS-SE-34FT-GROUP4/c2cTradePlatform/issues/76) · source status: CLOSED.

| ID | Expected acceptance | Result | Evidence / defect |
|---|---|---|---|
| 76.1 | Sprint 4 iteration set to 11–24 October, Sprint 5 to 25 October – 7 November | NOT RUN | — |
| 76.2 | Every Sprint 4 card carries an `Estimate` before development starts | NOT RUN | — |
| 76.3 | The Insights chart plots `Sum of Estimate` filtered to `-status:Done`, so the line falls | NOT RUN | — |
| 76.4 | No Sprint 4 card is left in the Sprint 5 iteration, or the S4 line will not reach zero | NOT RUN | — |

### #78 — Expiry scheduler verified against real Redis on staging

[Issue and current owners](https://github.com/NUS-SE-34FT-GROUP4/c2cTradePlatform/issues/78) · source status: OPEN.

| ID | Expected acceptance | Result | Evidence / defect |
|---|---|---|---|
| 78.1 | On staging, an unpaid order expires at its deadline and its stock returns | NOT RUN | — |
| 78.2 | A bargain campaign expires on staging and stops accepting help | NOT RUN | — |
| 78.3 | Expiry still behaves correctly after the application restarts mid-window | NOT RUN | — |
| 78.4 | Two application instances do not double-process the same expiry | NOT RUN | — |
| 78.5 | The observed timings are recorded, so the demo allows enough time on stage | NOT RUN | — |

### #79 — Design problem write-up: pluggable file storage

[Issue and current owners](https://github.com/NUS-SE-34FT-GROUP4/c2cTradePlatform/issues/79) · source status: CLOSED.

| ID | Expected acceptance | Result | Evidence / defect |
|---|---|---|---|
| 79.1 | The design problem is stated with the brute-force symptom it causes in the current code | NOT RUN | — |
| 79.2 | Candidate patterns are listed, with why each was considered | NOT RUN | — |
| 79.3 | The motivation for the selected pattern is argued against the alternatives, not asserted | NOT RUN | — |
| 79.4 | Implementation decisions are recorded, naming the classes that actually exist | NOT RUN | — |
| 79.5 | The file is committed by its own author, under `docs/design-patterns/` | NOT RUN | — |
| 79.6 | Diagrams are explicitly deferred to the Sprint 5 issue, not forgotten | NOT RUN | — |

### #80 — Design problem write-up: swapping the search engine

[Issue and current owners](https://github.com/NUS-SE-34FT-GROUP4/c2cTradePlatform/issues/80) · source status: CLOSED.

| ID | Expected acceptance | Result | Evidence / defect |
|---|---|---|---|
| 80.1 | The design problem is stated with the brute-force symptom it causes in the current code | NOT RUN | — |
| 80.2 | Candidate patterns are listed, with why each was considered | NOT RUN | — |
| 80.3 | The motivation for the selected pattern is argued against the alternatives, not asserted | NOT RUN | — |
| 80.4 | Implementation decisions are recorded, naming the classes that actually exist | NOT RUN | — |
| 80.5 | The file is committed by its own author, under `docs/design-patterns/` | NOT RUN | — |
| 80.6 | Diagrams are explicitly deferred to the Sprint 5 issue, not forgotten | NOT RUN | — |

### #81 — Design problem write-up: order state transitions

[Issue and current owners](https://github.com/NUS-SE-34FT-GROUP4/c2cTradePlatform/issues/81) · source status: OPEN.

| ID | Expected acceptance | Result | Evidence / defect |
|---|---|---|---|
| 81.1 | The design problem is stated with the brute-force symptom it causes in the current code | NOT RUN | — |
| 81.2 | Candidate patterns are listed, with why each was considered | NOT RUN | — |
| 81.3 | The motivation for the selected pattern is argued against the alternatives, not asserted | NOT RUN | — |
| 81.4 | Implementation decisions are recorded, naming the classes that actually exist | NOT RUN | — |
| 81.5 | The file is committed by its own author, under `docs/design-patterns/` | NOT RUN | — |
| 81.6 | Diagrams are explicitly deferred to the Sprint 5 issue, not forgotten | NOT RUN | — |

### #82 — Design problem write-up: notifying both parties

[Issue and current owners](https://github.com/NUS-SE-34FT-GROUP4/c2cTradePlatform/issues/82) · source status: OPEN.

| ID | Expected acceptance | Result | Evidence / defect |
|---|---|---|---|
| 82.1 | The design problem is stated with the brute-force symptom it causes in the current code | NOT RUN | — |
| 82.2 | Candidate patterns are listed, with why each was considered | NOT RUN | — |
| 82.3 | The motivation for the selected pattern is argued against the alternatives, not asserted | NOT RUN | — |
| 82.4 | Implementation decisions are recorded, naming the classes that actually exist | NOT RUN | — |
| 82.5 | The file is committed by its own author, under `docs/design-patterns/` | NOT RUN | — |
| 82.6 | Diagrams are explicitly deferred to the Sprint 5 issue, not forgotten | NOT RUN | — |

### #83 — Design problem write-up: selecting a recommendation strategy

[Issue and current owners](https://github.com/NUS-SE-34FT-GROUP4/c2cTradePlatform/issues/83) · source status: CLOSED.

| ID | Expected acceptance | Result | Evidence / defect |
|---|---|---|---|
| 83.1 | The design problem is stated with the brute-force symptom it causes in the current code | NOT RUN | — |
| 83.2 | Candidate patterns are listed, with why each was considered | NOT RUN | — |
| 83.3 | The motivation for the selected pattern is argued against the alternatives, not asserted | NOT RUN | — |
| 83.4 | Implementation decisions are recorded, naming the classes that actually exist | NOT RUN | — |
| 83.5 | The file is committed by its own author, under `docs/design-patterns/` | NOT RUN | — |
| 83.6 | Diagrams are explicitly deferred to the Sprint 5 issue, not forgotten | NOT RUN | — |

### #84 — Staging deployment verified and rollback rehearsed for the demo

[Issue and current owners](https://github.com/NUS-SE-34FT-GROUP4/c2cTradePlatform/issues/84) · source status: OPEN.

| ID | Expected acceptance | Result | Evidence / defect |
|---|---|---|---|
| 84.1 | The frozen code is deployed to staging through the approval gate from S4-04 | NOT RUN | — |
| 84.2 | A rollback to the previous image is performed and the application is confirmed working after it | NOT RUN | — |
| 84.3 | The forward deployment is then re-run, leaving staging on the version to be demonstrated | NOT RUN | — |
| 84.4 | The timings of each stage are recorded, so the live run can be narrated to length | NOT RUN | — |
| 84.5 | Demo data is loaded afterwards, not before, so the rollback does not wipe it | NOT RUN | — |

### #85 — Demo script and seeded demo data for the main transaction line

[Issue and current owners](https://github.com/NUS-SE-34FT-GROUP4/c2cTradePlatform/issues/85) · source status: CLOSED.

| ID | Expected acceptance | Result | Evidence / defect |
|---|---|---|---|
| 85.1 | A step-by-step script covers the full main line, with the exact accounts and items to use | NOT RUN | — |
| 85.2 | A seed script creates the accounts, listings, balances and one order ready to expire on stage | NOT RUN | — |
| 85.3 | The script states who speaks at each step and roughly how long it takes | NOT RUN | — |
| 85.4 | The expiry step's wait is timed from the figures recorded in S4-18 | NOT RUN | — |
| 85.5 | Re-running the seed resets staging to the same starting state | NOT RUN | — |

### #86 — Fallback demo video recorded end to end

[Issue and current owners](https://github.com/NUS-SE-34FT-GROUP4/c2cTradePlatform/issues/86) · source status: OPEN.

| ID | Expected acceptance | Result | Evidence / defect |
|---|---|---|---|
| 86.1 | The full main line is recorded on staging, following S4-25's script | NOT RUN | — |
| 86.2 | The live addition of a fifth payment strategy is recorded | NOT RUN | — |
| 86.3 | One complete pipeline run, including the rollback, is recorded | NOT RUN | — |
| 86.4 | The file is in the shared drive and playable without the repository or staging | NOT RUN | — |
| 86.5 | Its length is known, so it can be dropped into the presentation if staging fails | NOT RUN | — |

### #87 — Presentation slides assembled, timed and finalised

[Issue and current owners](https://github.com/NUS-SE-34FT-GROUP4/c2cTradePlatform/issues/87) · source status: OPEN.

| ID | Expected acceptance | Result | Evidence / defect |
|---|---|---|---|
| 87.1 | All five segment issues (S4-30 ~ S4-34) are merged into one deck with one visual style | NOT RUN | — |
| 87.2 | The deck covers all five briefing deliverables and nothing is represented twice | NOT RUN | — |
| 87.3 | Section order matches the order the team will speak in, with handover points marked | NOT RUN | — |
| 87.4 | The deck is timed against the per-group limit once that limit is confirmed | NOT RUN | — |
| 87.5 | Every design problem named on a slide matches its one-pager's pattern | NOT RUN | — |
| 87.6 | No tool metadata or placeholder text remains in the file | NOT RUN | — |

### #88 — Sprint 4 progress report

[Issue and current owners](https://github.com/NUS-SE-34FT-GROUP4/c2cTradePlatform/issues/88) · source status: OPEN.

| ID | Expected acceptance | Result | Evidence / defect |
|---|---|---|---|
| 88.1 | Stories completed against planned, with the point total and the real burndown from the board | NOT RUN | — |
| 88.2 | Per-member hours, confirmed with each member rather than estimated | NOT RUN | — |
| 88.3 | Coverage figures stated as the distinct metrics they are (instruction, branch, line) | NOT RUN | — |
| 88.4 | Anything not merged by 17 October is listed as backlog, with the reason | NOT RUN | — |
| 88.5 | Technical debt carried into Sprint 5 is named | NOT RUN | — |
| 88.6 | The scope statement notes that Sprint 4 includes documentation deliverables, so the point total is not comparable to Sprint 3's code-only 45 | NOT RUN | — |
| 88.7 | Submitted to Canvas before 24:00 on 24 October | NOT RUN | — |

### #89 — Architecture and transition strategy written up for the presentation

[Issue and current owners](https://github.com/NUS-SE-34FT-GROUP4/c2cTradePlatform/issues/89) · source status: OPEN.

| ID | Expected acceptance | Result | Evidence / defect |
|---|---|---|---|
| 89.1 | The monolithic architecture is described as built, with its layers and their responsibilities | NOT RUN | — |
| 89.2 | A component diagram shows the application, MySQL, Redis, Elasticsearch and MinIO, and what talks to what | NOT RUN | — |
| 89.3 | The transition strategy states how the system would move to a different deployment shape, and what in the current design makes that possible | NOT RUN | — |
| 89.4 | The technology selections are justified against the alternatives recorded in the proposal's selection table | NOT RUN | — |
| 89.5 | It fits on one or two slides and is also usable as a report section | NOT RUN | — |
| 89.6 | A native `.drawio` ships next to the exported diagram | NOT RUN | — |

### #90 — Demo segment: the main transaction line and the live fifth payment strategy

[Issue and current owners](https://github.com/NUS-SE-34FT-GROUP4/c2cTradePlatform/issues/90) · source status: OPEN.

| ID | Expected acceptance | Result | Evidence / defect |
|---|---|---|---|
| 90.1 | The segment is drafted by the member who will present it | NOT RUN | — |
| 90.2 | It fits the minutes allotted to it, spoken aloud once to check | NOT RUN | — |
| 90.3 | Every figure and number on it comes from the repository, the board or a recorded run — nothing illustrative | NOT RUN | — |
| 90.4 | It is handed to the PM by 24 October for assembly in S4-27 | NOT RUN | — |
| 90.5 | Speaker notes carry what will be said, not a repeat of the slide text | NOT RUN | — |

### #91 — DevSecOps segment: pipeline walkthrough with a rollback

[Issue and current owners](https://github.com/NUS-SE-34FT-GROUP4/c2cTradePlatform/issues/91) · source status: OPEN.

| ID | Expected acceptance | Result | Evidence / defect |
|---|---|---|---|
| 91.1 | The segment is drafted by the member who will present it | NOT RUN | — |
| 91.2 | It fits the minutes allotted to it, spoken aloud once to check | NOT RUN | — |
| 91.3 | Every figure and number on it comes from the repository, the board or a recorded run — nothing illustrative | NOT RUN | — |
| 91.4 | It is handed to the PM by 24 October for assembly in S4-27 | NOT RUN | — |
| 91.5 | Speaker notes carry what will be said, not a repeat of the slide text | NOT RUN | — |

### #92 — Architecture segment: system shape and transition strategy

[Issue and current owners](https://github.com/NUS-SE-34FT-GROUP4/c2cTradePlatform/issues/92) · source status: OPEN.

| ID | Expected acceptance | Result | Evidence / defect |
|---|---|---|---|
| 92.1 | The segment is drafted by the member who will present it | NOT RUN | — |
| 92.2 | It fits the minutes allotted to it, spoken aloud once to check | NOT RUN | — |
| 92.3 | Every figure and number on it comes from the repository, the board or a recorded run — nothing illustrative | NOT RUN | — |
| 92.4 | It is handed to the PM by 24 October for assembly in S4-27 | NOT RUN | — |
| 92.5 | Speaker notes carry what will be said, not a repeat of the slide text | NOT RUN | — |

### #93 — Agile process segment: burndowns, backlog and ceremony evidence

[Issue and current owners](https://github.com/NUS-SE-34FT-GROUP4/c2cTradePlatform/issues/93) · source status: OPEN.

| ID | Expected acceptance | Result | Evidence / defect |
|---|---|---|---|
| 93.1 | The segment is drafted by the member who will present it | NOT RUN | — |
| 93.2 | It fits the minutes allotted to it, spoken aloud once to check | NOT RUN | — |
| 93.3 | Every figure and number on it comes from the repository, the board or a recorded run — nothing illustrative | NOT RUN | — |
| 93.4 | It is handed to the PM by 24 October for assembly in S4-27 | NOT RUN | — |
| 93.5 | Speaker notes carry what will be said, not a repeat of the slide text | NOT RUN | — |

### #94 — Quality segment: coverage metrics, integration tests and the defect log

[Issue and current owners](https://github.com/NUS-SE-34FT-GROUP4/c2cTradePlatform/issues/94) · source status: OPEN.

| ID | Expected acceptance | Result | Evidence / defect |
|---|---|---|---|
| 94.1 | The segment is drafted by the member who will present it | NOT RUN | — |
| 94.2 | It fits the minutes allotted to it, spoken aloud once to check | NOT RUN | — |
| 94.3 | Every figure and number on it comes from the repository, the board or a recorded run — nothing illustrative | NOT RUN | — |
| 94.4 | It is handed to the PM by 24 October for assembly in S4-27 | NOT RUN | — |
| 94.5 | Speaker notes carry what will be said, not a repeat of the slide text | NOT RUN | — |

### #109 — Settle the seller at confirm-receipt instead of at payment

[Issue and current owners](https://github.com/NUS-SE-34FT-GROUP4/c2cTradePlatform/issues/109) · source status: CLOSED.

| ID | Expected acceptance | Result | Evidence / defect |
|---|---|---|---|
| 109.1 | Paying debits the buyer and holds the amount against the order; the seller's balance does not move yet | NOT RUN | — |
| 109.2 | Confirming receipt releases the held amount to the seller, in the same transaction as the state change | NOT RUN | — |
| 109.3 | A refund of a paid, undispatched order returns the held amount to the buyer and never depends on the seller's current balance | NOT RUN | — |
| 109.4 | A refund is refused once the order is COMPLETED, because the money is already the seller's | NOT RUN | — |
| 109.5 | The held amount is visible on the order, so the demo can show it | NOT RUN | — |
| 109.6 | Concurrent confirm-receipt and refund resolve to one outcome | NOT RUN | — |
| 109.7 | The three simulated gateways follow the same timing as balance payment | NOT RUN | — |

## Two staging demo runs

Follow docs/demo/demo-script.md with distinct orders per run. Main line: search → item → add to cart → cart navigation → checkout → payment/held amount → seller ship → buyer confirm receipt → payout ledger. Also exercise refund, review/credit refresh and recommendation UI. Do not work around missing UI using an API and call the UI demo passed.

| Run | Date / SHA / deployment | Orders / opening balances / stock | Outcome / duration | Evidence / defects |
|---|---|---|---|---|
| A | Pending | Pending | NOT RUN | — |
| B | Pending; repeat after fixes if needed | Fresh orders; record new starting values | NOT RUN | — |

Compare balance deltas, not hard-coded balances. A blocked run is not one of two successful end-to-end passes.

## Defect record template

Create an issue labelled `bug` after reproducing a failure; obtain authorization to publish if not already granted. Include:

- Title: [S4 QA] concise user-visible failure
- AC ID, deployed SHA/image, timestamp, browser, account aliases and fixture IDs
- Preconditions and numbered reproduction steps
- Expected versus actual result; sanitized evidence
- Severity, module owner/assignee and rationale
- Triage: fix-before-freeze or backlog; agreed target date
- Retest: replacement deployed SHA, date, result, evidence

Prioritize blocked transaction flows, incorrect balances/stock and access-control failures before cosmetic issues. Agree unresolved freeze decisions with PM; do not quietly close failures.

## One-page execution summary (fill after testing)

| Field | Record |
|---|---|
| Environment / deployed versions / dates | Pending |
| Coverage | AC total: pending; PASS / FAIL / BLOCKED / NOT RUN / justified N/A: pending |
| Two completed demo runs | Pending, with run evidence |
| Defects found / fixed and retested / still open | Pending issue links |
| Fix-before-freeze decisions | Pending owner and decision |
| Backlog and operational dependencies | Pending owner and rationale |
| Recommendation and remaining risk | Not evaluated; preparation is not sign-off |

Use this evidence in #94; refresh coverage from the chosen CI SHA and distinguish instruction, branch and line coverage. #94 currently specifies delivery to PM by 24 October; confirm any conflicting later presentation schedule with PM. UML pack #100 is a separate Sprint 5 deliverable.
