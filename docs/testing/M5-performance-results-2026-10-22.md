# 5006 read-only performance results — 22 October 2026

Environment: http://178.128.107.99/, approved by CUI for this test window. Singapore start times: 19:20:40 and 19:21:39. Deployed SHA and instance count unverified. No deployment or business-data mutation was performed by QA.

Workload: alternate GET /api/products and GET /api/products?keyword=..., using QA Test Book for preflight and iPhone for load searches (the parity selector does not alternate both keywords), one second think time after each request, constant VUs. Preflight list returned 12 products; first search returned 1. This is a small dataset. Script: scripts/perf/read-workload.js, k6 2.3.0.

| VUs | Planned load duration | Load requests | Load requests / whole-run seconds | List median / P95 ms | Search median / P95 ms | Errors | Gates |
|---|---|---|---|---|---|---|---|
| 2 | 30s | 54 | 1.69 | 146.22 / 242.60 | 96.06 / 145.03 | 0% | PASS |
| 10 | 90s | 820 | 8.99 | 92.17 / 269.01 | 84.61 / 194.73 | 0% | PASS |

Both runs finished with exit code 0 and zero interrupted iterations. Whole-run duration includes setup and graceful completion: approximately 31.99s and 91.22s. The throughput column excludes the two setup requests from its numerator but retains the whole-run denominator; it is a conservative run-average, not an instantaneous peak. Native http_reqs is 56 / 822 including setup. Custom read_ms and business_errors exclude setup. Zero error-rate successes appear under `fails` in the k6 Rate counter because the submitted failure boolean was false; this does not mean 820 requests failed.

All observed checks passed: HTTP 200 + JSON array; overall and per-operation median <1s/P95 <2s; additional QA error guard <1%. These are pass results only for the executed workload. The subsequent 200-VU attempt failed and was aborted; see below. The single-instance 200-concurrent requirement has not been demonstrated. There is no automatic scaling claim. Error-free short reads do not prove purchase concurrency or ledger correctness.

Evidence: [smoke](evidence/2026-10-22/20261022-192040-smoke/summary.json), [demo](evidence/2026-10-22/20261022-192139-demo/summary.json). Adjacent files contain console output and script hash/context. Raw timestamped samples and exact script snapshots are retained locally under scripts/perf/results/ (gitignored).

## Suggested spoken result

In this recorded run, ten virtual users alternated product listing and keyword search for ninety seconds, with a one-second think time. We completed 820 workload requests with no failures. Listing P95 was 269 milliseconds and search P95 was 195 milliseconds. Both met our two-second latency objective. This demonstrates the tested read workload on a twelve-product dataset; it does not establish our two-hundred-user capacity target.

## 200-VU capacity attempt — 19:45 Singapore time

Constant 200 VUs, planned three minutes, same read-only endpoints, keywords and one-second think time. The error guard stopped the run after approximately 22.01 seconds; process exit code 99. This was an abrupt load step, not a gradual ramp or a completed steady-state test.

- Recorded workload requests: 723 (705 successful, 18 failed); error rate **2.49%**, above the <1% guard.
- All 18 recorded failures were search request timeouts (10-second client timeout, status 0), not HTTP 503 responses.
- List: median **1,607.87 ms**, P95 **6,554.05 ms**.
- Search: median **7,221.66 ms**, P95 **9,932.90 ms**.
- Overall: median **4,348.88 ms**, P95 **8,949.01 ms**. Both latency objectives failed.
- Workload requests / whole-run seconds: approximately **32.84 requests/s**. Native HTTP count 725 includes two preflight requests. Aborted/in-flight work is not counted as completed business results.
- A single post-run list probe returned HTTP 200 in 53.99 ms. This shows that probe recovered, not that all services were healthy.

Search latency was worse than listing latency in this run. Root cause is not established: correlate application, database, host and load-generator metrics for 11:45:30–11:45:52 UTC before attributing the failure. Do not claim a database or server-capacity cause from client timings alone. Dataset remains only 12 products and deployed revision / instance count are unverified.

Evidence: [capacity summary](evidence/2026-10-22/20261022-194528-capacity/summary.json), with adjacent console log and execution context. This failed run must be retained alongside the successful 2- and 10-VU results.
