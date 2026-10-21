# M5 quality segment preparation (#94)

Status: evidence outline, not a finished or rehearsed deck. Use only 5006 evidence. Confirm the allotted speaking time with the PM; issue #94 requests handoff by 24 October.

## Suggested two-slide structure

1. **Quality evidence**: report instruction, branch and line coverage separately from one identified CI SHA/run; show unit/integration test counts and skipped tests separately. Explain what the tests exercise, without treating coverage as correctness. Add the observed bank-card lifecycle: pay and hold, dispatch, receipt, seller balance +S$1.
2. **Acceptance and open risks**: distinguish passed UI observations, reproducible category failure, blocked chat/favorites/edit entry flows and untested admin/concurrency/recovery areas. Show the defect loop: reproduce, hand off, deploy, retest. Category/copy fixes are merged in #135 but deployment must be reconciled before retest.

## Numbers to obtain before finalising

- One CI run URL, commit SHA, test counts and separate instruction/branch/line coverage from its retained reports.
- A deployed SHA/digest tied to the environment used for acceptance.
- Do not present the 13 October meeting's 72.43% line coverage as current without rechecking the chosen run.
- Do not reuse 5001's 32 checks, autoscaling results or oversell evidence.

## Draft speaker narrative

We combine automated tests with staging acceptance. We report instruction, branch and line coverage separately, tied to a specific CI run. Coverage tells us which code was exercised; it does not establish that every workflow is correct.

In our staging follow-up, the simulated bank-card purchase completed payment, dispatch and receipt. The seller's balance remained unchanged after payment and increased by exactly the order amount after receipt. Incorrect payment passwords and cart quantities above stock were refused.

We also record failures and gaps. Parent-category filtering failed on the tested deployment; its fix is merged and needs deployment verification and retesting. Chat, favorites and editing have entry-point blockers. Concurrency, ledger invariants and recovery still need separate evidence. We therefore report partial acceptance, not full system sign-off.

## Evidence links

- [17–18 October acceptance](M5-staging-acceptance-2026-10-17.md)
- [21 October follow-up](M5-staging-followup-2026-10-21.md)
- [Owner handoff](M5-defect-handoff-2026-10-21.md)

Before handoff: replace pending CI figures with verified results, agree slide duration, rehearse aloud and place the final narrative in speaker notes. Do not close #94 based on this outline alone.
