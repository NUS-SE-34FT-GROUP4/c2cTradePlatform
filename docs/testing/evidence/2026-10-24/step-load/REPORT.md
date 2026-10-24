# 5006 stepped-load test — 24 October 2026

Status: six levels completed; load stopped because the exclusive testing window could not be extended. No failing level identified yet. This is not a maximum-capacity finding.

Deployment: c3f7719510e2540ebbae786611a77a056dfc5729. Version before and after every completed level matched. This differs from the earlier 200-VU run; do not attribute differences solely to load or a specific fix.

User confirmed server collection started before testing. Server-side samples have not yet been received or correlated for this session.

## Method

k6 2.3.0; three minutes per constant-VU level; one-second think time; ten-second request timeout; read-only listing/search on http://178.128.107.99:8080. Sequential runs with recovery gaps. Preserve previous selector: loaded searches use iPhone only; QA Test Book is used in preflight. Two preflight HTTP requests count toward HTTP totals and throughput but not per-endpoint read latency or business errors. This is an exploratory, sequential/warm-cache test, not a randomized experiment or a full-workflow capacity guarantee.

Pass: both listing and search median <1000 ms and P95 <2000 ms; business error rate <1%. All completed levels finished with exit 0 and zero business errors.

| VUs | HTTP req/s | List median ms | List P95 ms | Search median ms | Search P95 ms | Result |
|---:|---:|---:|---:|---:|---:|---|
| 10 | 9.25 | 58.78 | 222.13 | 48.35 | 189.25 | PASS |
| 25 | 23.38 | 45.06 | 183.37 | 40.90 | 168.62 | PASS |
| 50 | 46.36 | 53.70 | 221.63 | 46.87 | 207.14 | PASS |
| 75 | 64.77 | 92.50 | 490.57 | 76.75 | 434.06 | PASS |
| 100 | 86.43 | 114.38 | 457.35 | 97.34 | 409.57 | PASS |
| 125 | 105.38 | 144.51 | 510.48 | 118.00 | 491.07 | PASS |

## Original files and collection windows

- **10 VUs**: 2026-10-24T07:25:46Z–2026-10-24T07:28:49Z (UTC; add 8 hours for Singapore). [Raw summary](runs/20261024T072546Z-10vu/summary.json), [console](runs/20261024T072546Z-10vu/console.log). Version stable: True.
- **25 VUs**: 2026-10-24T07:30:10Z–2026-10-24T07:33:11Z (UTC; add 8 hours for Singapore). [Raw summary](runs/20261024T073010Z-25vu/summary.json), [console](runs/20261024T073010Z-25vu/console.log). Version stable: True.
- **50 VUs**: 2026-10-24T07:34:45Z–2026-10-24T07:37:47Z (UTC; add 8 hours for Singapore). [Raw summary](runs/20261024T073445Z-50vu/summary.json), [console](runs/20261024T073445Z-50vu/console.log). Version stable: True.
- **75 VUs**: 2026-10-24T07:39:23Z–2026-10-24T07:42:25Z (UTC; add 8 hours for Singapore). [Raw summary](runs/20261024T073923Z-75vu/summary.json), [console](runs/20261024T073923Z-75vu/console.log). Version stable: True.
- **100 VUs**: 2026-10-24T07:43:58Z–2026-10-24T07:47:00Z (UTC; add 8 hours for Singapore). [Raw summary](runs/20261024T074358Z-100vu/summary.json), [console](runs/20261024T074358Z-100vu/console.log). Version stable: True.
- **125 VUs**: 2026-10-24T07:48:45Z–2026-10-24T07:51:47Z (UTC; add 8 hours for Singapore). [Raw summary](runs/20261024T074845Z-125vu/summary.json), [console](runs/20261024T074845Z-125vu/console.log). Version stable: True.

## Interpretation and next steps

100 VUs passed in this measured read-only scenario; 125 VUs also passed. The failure boundary is not yet known. In a newly confirmed testing window, test 150, 175, and 200 VUs, stopping at the first failing level and repeating the adjacent passing/failing levels after recovery. Preserve original 200-VU failure records; these new results do not invalidate earlier measurements on a different version. Review server metrics before making causal claims.

The 07:25:22 UTC 10-VU attempt and 07:54:33 UTC 150-VU attempt failed during version precheck and generated no load; both are excluded from results. The 150-VU workload did not start.

## Retention

Original per-request timeseries.json files are retained locally and not included in Git because of their combined size. SHA256SUMS.json records their checksums and sizes alongside the committed evidence. Summary and console files are copied byte-for-byte. No credentials are required for these read-only endpoints.
