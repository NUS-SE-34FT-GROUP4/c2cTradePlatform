# Performance and QA demonstration plan — CUI

Responsibility confirmed by CUI: Huailing presents business use cases; CUI presents performance and QA. This is not CUI's script for the entire 13-minute demo. Agree the exact allocation and transition with Huailing before timing rehearsal. Proposed CUI allocation below is four minutes, subject to agreement.

| Proposed time | Screen/action | Spoken point |
|---|---|---|
| 0:00–0:30 | Environment/version and workload command | This is the 5006 single-host deployment. The read-only workload covers product listing and keyword search. State dataset and client count. |
| 0:30–2:00 | Run the approved 90-second demo profile | Explain VUs versus requests/second, median and P95. Show server metrics only if available. Do not imply auto scaling exists. |
| 2:00–2:45 | Actual summary | State observed throughput, per-operation median/P95, errors and whether the run completed. Compare against median <1s/P95 <2s. If failed, state the failure. |
| 2:45–3:30 | Quality evidence | Show identified CI run: unit/integration counts, separate instruction/branch/line coverage. Explain low branch coverage and coverage limits. |
| 3:30–4:00 | Acceptance/defect log | Explain bank-card hold-to-settlement verification, blocked workflows and retest ownership. Hand back to Huailing. |

## Preparation and dependencies

- Read-only scripts: scripts/perf/read-workload.js and run.sh; run smoke first.
- Owner confirms environment, deployment, no overlapping workload and permitted test window.
- Run the 200-VU capacity experiment separately with owner coordination (the archived capacity profile is an abrupt constant-VU load step); do not claim 200-user compliance from a 10-VU demo.
- Freeze workload/keywords/dataset between comparison runs. Retain failures as evidence.
- Record only CUI's performance/QA segment as backup once a real run is available. Mark video segment timestamps and fallback points after recording, not before.
- Smoke, demo and 200-VU runs are recorded. The post-PR142 200-VU run completed with 0% errors but search P95 5.09s, above target. See M5-PR142-after-comparison-2026-10-23.md; dataset changed from 12 to 13 products, so this is not an isolated causal comparison.
- Existing quality evidence: M5-quality-segment-draft.md. Update numbers if the selected CI SHA changes.

## English opening

I will now show the performance and quality evidence for our 5006 application. This scripted workload reads product listings and performs keyword searches. We measure throughput, median response time, the ninety-fifth percentile, and failures. Our latency targets are a median below one second and P95 below two seconds. This short demonstration is not, by itself, proof of our two-hundred-user capacity target.

## Result narration template

The run [completed / stopped early]. It processed [measured load requests] over [actual measured duration], at [observed requests per second]. Listing P95 was [value], and search P95 was [value]. The error rate was [value]. Therefore, this specific workload [met / did not meet] the stated checks. These results apply to the recorded environment and dataset.

## Q&A boundaries

No automatic orchestration is deployed in this single-host Compose system. More application instances alone would not remove database/search bottlenecks. Passed UI settlement observations do not establish concurrent ledger correctness. We retain unresolved defects and only mark a fix verified after testing its deployed version.
