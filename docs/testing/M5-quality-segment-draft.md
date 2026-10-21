# M5 quality segment preparation (#94)

Status: evidence outline, not a finished or rehearsed deck. Use only 5006 evidence. Confirm the allotted speaking time with the PM; issue #94 requests handoff by 24 October.

## Suggested two-slide structure

1. **Quality evidence**: report instruction, branch and line coverage separately from one identified CI SHA/run; show unit/integration test counts and skipped tests separately. Explain what the tests exercise, without treating coverage as correctness. Add the observed bank-card lifecycle: pay and hold, dispatch, receipt, seller balance +S$1.
2. **Acceptance and open risks**: distinguish passed UI observations, reproducible category failure, blocked chat/favorites/edit entry flows and untested admin/concurrency/recovery areas. Show the defect loop: reproduce, hand off, deploy, retest. Category/copy fixes are merged in #135 but deployment must be reconciled before retest.

## Verified CI evidence

Source: [CI run 39598700575](https://github.com/NUS-SE-34FT-GROUP4/c2cTradePlatform/actions/runs/39598700575), main SHA `7a590400a394d2198c478332e62f74cdb736dbd2`. Backend test job succeeded; Deploy to staging was waiting when checked. This is not proof of deployment.

| Metric | Result | Raw counters |
|---|---|---|
| Instruction coverage | 42.99% | 10,147 covered / 23,604 total |
| Branch coverage | 19.13% | 624 covered / 3,262 total |
| Line coverage | 72.48% | 2,036 covered / 2,809 total |
| Unit tests (Surefire) | 161 tests | 0 failures, 0 errors, 0 skipped |
| Integration tests (Failsafe) | 59 tests | 0 failures, 0 errors, 0 skipped |

Coverage uses JaCoCo report-root counters, not a service-layer-only calculation. Counts come from TEST XML suites and exclude frontend checks. Do not combine coverage metrics or describe all coverage as 72.48%. A line can execute without exercising all its instructions or decision branches; the lower branch result identifies a limitation, not a contradiction.

Machine-readable counters and source hashes: [CI summary](evidence/2026-10-21/ci-summary.json). Downloaded originals are in the local artifacts/5006-ci-39598700575 directory, while the workflow retains the named coverage and test artifacts.

Outstanding: obtain the deployed SHA/digest and confirm allotted speaking time. Do not reuse 5001 test or scaling evidence.

## Draft speaker narrative

We combine automated tests with staging acceptance. On the selected main commit, 161 unit tests and 59 integration tests passed, with no skipped tests. Line coverage is 72.48 percent, instruction coverage is 42.99 percent, and branch coverage is 19.13 percent. These measure different things. In particular, the low branch coverage means many decision outcomes remain unexercised. Coverage does not establish that every workflow is correct.

In our staging follow-up, the simulated bank-card purchase completed payment, dispatch and receipt. The seller's balance remained unchanged after payment and increased by exactly the order amount after receipt. Incorrect payment passwords and cart quantities above stock were refused.

We also record failures and gaps. Parent-category filtering failed on the tested deployment; its fix is merged and needs deployment verification and retesting. Chat, favorites and editing have entry-point blockers. Concurrency, ledger invariants and recovery still need separate evidence. We therefore report partial acceptance, not full system sign-off.

## Evidence links

- [17–18 October acceptance](M5-staging-acceptance-2026-10-17.md)
- [21 October follow-up](M5-staging-followup-2026-10-21.md)
- [Owner handoff](M5-defect-handoff-2026-10-21.md)

Before handoff: refresh the figures if the selected commit changes, agree slide duration, rehearse aloud and place the final narrative in speaker notes. Do not close #94 based on this outline alone.
