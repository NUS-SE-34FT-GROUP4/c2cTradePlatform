# QA owner handoff — 21 October 2026

Scope: 5006 / c2cTradePlatform only. Staging: http://178.128.107.99/. Deployed SHA remains unverified. QA owns reproduction, evidence and retesting; implementation ownership should be confirmed by the team. No application code was changed.

| Item | Observation and reproduction | Required action | Retest criterion |
|---|---|---|---|
| Parent-category membership | Buyer searches QA Test Book - CUI: found without category. Select Books / All and Apply: No products found. Clear filters: listing returns. | Deployment owner first verifies PR #135 is live; category owner investigates only if still reproducible after that. | The same listing is returned under the correct parent category and keyword. |
| Chat entry | On home, click dedicated seller avatar labelled Click to chat. Two attempts produced no panel/navigation. Direct /chat opens an empty conversations page. | Frontend/chat owner checks event wiring and provides a usable new-conversation entry. Local historical source dispatched open-chat without a matching listener found; deployed root cause is unconfirmed. | Buyer starts conversation with the intended seller; both directions deliver and survive reload. |
| Favorites entry | Dedicated product detail contains no save/favorite action. Direct /favorites loads an empty list. | Confirm feature scope; if retained, frontend owner restores the action and navigation. | Add, reload, list, remove and reload all agree. |
| Owner edit entry | Safari seller owns dedicated listing but detail has no edit action. | Confirm supported navigation and feature scope; restore discoverability if required. | Owner reaches edit UI, saves text/image changes and sees them on fresh detail; non-owner is refused. |
| Payment copy | Five methods displayed but explanatory text says All four methods. | Already covered by merged PR #135; verify deployment, then retest. | Copy matches the available methods. |

## Previously reported observations not retested here

Fresh helper page may enable repeat help until the server rejects it; non-owner edit form may load before save is refused. Do not label these newly reproduced or resolved. Current campaigns are expired, and an eligible new campaign would be needed for the repeat-help regression.

## Passed follow-up checks

Cart quantity above stock refused; incorrect payment password refused; simulated bank-card payment/hold/dispatch/receipt completed; seller balance stayed S$2.90 after payment and became S$3.90 after receipt for a S$1 order. Database and concurrency guarantees are not inferred from UI results.

## Coordination still required

Admin moderation and permissions, direct API authorization, concurrent payment/refund/settlement, ledger inspection, restart/multi-instance expiry and search outage recovery require scoped owner cooperation. They remain unverified, not automatically implementation defects.

Full observations: [follow-up report](M5-staging-followup-2026-10-21.md). No passwords or tokens are included.
