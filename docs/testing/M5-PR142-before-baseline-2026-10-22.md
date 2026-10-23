# PR #142 pre-fix baseline — 22 October 2026

Window: 23:16–23:21 Singapore time (15:16–15:21 UTC). Target: http://178.128.107.99. PR #142 remained open throughout. Latest successful GitHub staging deployment: 7248310901, SHA `3e816c9fe9a30c5c080d05a3d4ea022094f0cccc`. Newer deployment 7255660996 had no status entries before or after. This establishes deployment-record provenance, not independent inspection of the running container digest or manual deployments.

Frozen workload: existing read-workload.js, k6 2.3.0, constant 10 VUs for 90s followed by constant 200 VUs for 3m, one-second think time, 10-second request timeout, <1% error abort guard after 20s. List/search alternate. Due to parity in the existing selector, actual load searches use **iPhone only**; QA Test Book is used only in preflight. Keep this exact behavior for the paired retest. Preflight returned 12 list items and one search item. Do not claim two search keywords were load-tested.

| VUs | Workload requests | Error rate | List median / P95 ms | Search median / P95 ms | Exit code |
|---|---|---|---|---|---|
| 10 | 837 | 0.00% | 63.79 / 204.64 | 58.52 / 144.04 | 0 |
| 200 | 9953 | 0.00% | 837.84 / 1893.44 | 4300.52 / 5894.31 | 99 |

Both runs completed the planned load duration with zero interrupted iterations. The 200-VU run exited 99 because latency thresholds failed, not because the error guard aborted. Native HTTP totals include two extra preflight requests; the table excludes those. This run does not pass the 200-user latency requirement. Keep the earlier 19:45 aborted run as separate evidence of variability; do not overwrite it. No SQL query counts were measured by these client tests.

After the run, a list probe returned HTTP 200 in 61.84 ms. After #142 is approved, merged and deployed, verify the new deployment SHA, retain the same data, script hash, endpoint, load-generator location and settings, and repeat both profiles. Compare latency and failures as well as completion/abort behavior. Server resource conditions remain uncontrolled; collect host and database metrics where possible.

Evidence folders: evidence/2026-10-22/20261022-231609-demo and evidence/2026-10-22/20261022-231801-capacity. Raw time series remain in local scripts/perf/results. No deployment or merge was performed during this task.
