# PR #142 deployment performance comparison

After-test window: 23 October 2026, 16:19–16:24 Singapore time. GitHub staging deployment 7256999762 succeeded for ce6212ab7eba6cf06dd74c0c86a6cb39bbc3485f and remained latest after testing. Baseline deployment-record SHA: 3e816c9fe9a30c5c080d05a3d4ea022094f0cccc. Running container digests and instance counts were not independently inspected.

Identical workload script (byte comparison against baseline snapshot passed), k6 2.3.0, http://178.128.107.99, 10 VUs/90s then 200 VUs/3m, one-second think time, 10s timeout and unchanged thresholds. Actual load alternates listing and iPhone search; QA Test Book is only a preflight keyword due to selector parity. Both new runs completed with zero interrupted iterations. The 200-VU exit 99 indicates latency failure at completion, not an error-triggered early abort.

| Run / VUs | Workload requests | Requests / whole-run seconds | Errors | List median / P95 ms | Search median / P95 ms | Exit |
|---|---|---|---|---|---|---|
| Before 10 | 837 | 9.24 | 0.00% | 63.79 / 204.64 | 58.52 / 144.04 | 0 |
| After 10 | 802 | 8.59 | 0.00% | 91.63 / 283.34 | 120.84 / 332.56 | 0 |
| Before 200 | 9953 | 54.22 | 0.00% | 837.84 / 1893.44 | 4300.52 / 5894.31 | 99 |
| After 200 | 12599 | 69.36 | 0.00% | 286.58 / 1050.21 | 3182.10 / 5092.83 | 99 |

## Conclusion and limitations

10 VUs passed. At 200 VUs, zero recorded errors and a complete run do not establish compliance: search latency still failed the median <1s / P95 <2s objectives. Keep all prior failed and successful evidence. No SQL query counts were measured by this test.

This is a before/after deployment comparison, not an isolated causal experiment. Catalogue size changed from 12 to 13 and list response length from 8,523 to 10,090 decoded characters. The deployment also contains PR #141 frontend changes and other intervening commits. Host contention, network conditions and caches were not controlled. Do not attribute all differences to batching, claim a universal capacity gain, or call the 200-user target passed.

Post-run listing probe: HTTP 200, 73.803ms. Raw samples are retained locally in scripts/perf/results; summaries, console output, script snapshots and context are under evidence/2026-10-23. Compare with M5-PR142-before-baseline-2026-10-22.md. No deployment or code changes performed by QA.
