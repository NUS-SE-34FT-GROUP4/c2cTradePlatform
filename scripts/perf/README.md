# 5006 read-only performance demonstration

Scope: c2cTradePlatform, not 5001/ass. List and search both use GET /api/products; search adds keyword. The controller returns an array and exposes no pagination parameters. Reads are anonymous according to the checked source. No orders, accounts or payments are created.

## Before running

Agree the environment and test window with its owner, verify there is no competing test/deployment, record deployed SHA, instance count, dataset size and infrastructure sizing. Keep the environment owner available for the 200-client test. Do not seed/reset shared data from this script.

Requires Docker; image grafana/k6:2.3.0. The script has no remote JavaScript imports. Shell paths are resolved from the runner location, avoiding missing-script errors when invoked from another directory.

```sh
export BASE_URL=http://178.128.107.99
# Choose keywords known to match the agreed fixture; keep them unchanged for comparisons.
export KEYWORDS='QA Test Book,iPhone'
./scripts/perf/run.sh smoke
# Proceed only after preflight and smoke checks pass and the window is authorised.
./scripts/perf/run.sh demo
# Separate coordinated capacity experiment, not the default live presentation.
./scripts/perf/run.sh capacity
```

| Profile | Constant virtual users | Duration |
|---|---:|---:|
| smoke | 2 | 30 seconds |
| demo | 10 | 90 seconds |
| capacity | 200 | 3 minutes |

Each virtual user alternates list/search requests with one second of think time after each response. VUs are not RPS and do not mean all users have an outstanding request simultaneously. Approximately half the workload is listing and half keyword search; observed throughput depends on response time. This is a read-only workload, not mixed checkout capacity. Running the 200-client profile alone does not prove a single-instance requirement: deployment topology must also be verified.

Two setup probes require HTTP 200 and a JSON array. An empty search is valid but should be explained; select representative known-match keywords before the run. Setup logs returned item counts. The API currently has no paging controls, so record dataset size; small catalogues do not establish large-data performance.

## Gates and results

Proposal latency objectives: median < 1,000 ms and P95 < 2,000 ms, assessed overall and for each operation using read_ms. An additional QA error guard requires <1% business failures and aborts on breach after 20 seconds; this guard is not presented as a proposal requirement. HTTP non-200 or non-array JSON counts as failure. Ten-second request timeout, no redirects. Stop manually with Ctrl+C if the owner sees environmental distress. Do not relax thresholds to hide failures.

Results are under scripts/perf/results/TIMESTAMP-PROFILE (gitignored), or RESULTS_DIR. Preserve console.log, summary.json and timeseries.json, including aborted runs. summary.json contains per-operation latency submetrics. http_reqs includes the two setup probes; use phase=load samples from timeseries.json for measured workload request counts and throughput. response_chars measures decoded string length, not network bytes; data_received is aggregate transfer volume. Do not call response_chars bytes.

Presentation captures: command/profile, running load, summary metrics, environment/deployed version, and corresponding server monitoring if available. Report duration actually executed, failures and abort reason alongside latency. A stopped run is not a completed pass. Single-host Compose has no automatic scaling in this scope.

Validation: shell syntax and k6 inspect passed. The 2-VU and 10-VU staging runs completed on 22 October; see ../../docs/testing/M5-performance-results-2026-10-22.md. The 200-VU baseline and post-PR142 tests completed but failed search latency objectives; see ../../docs/testing/M5-PR142-after-comparison-2026-10-23.md.

## Frozen comparison workload limitation

With two configured keywords, the current parity selector uses the second keyword (iPhone) for every load search; the first (QA Test Book) is used only for preflight. Preserve this behavior for comparisons with the archived runs. A future selector correction needs a new baseline and should include zero-hit and multi-hit cases.
