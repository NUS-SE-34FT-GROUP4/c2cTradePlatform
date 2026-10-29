# Catalogue volume and load recovery results

Author: CUI ZIJIAN. Executed 29 October 2026 (Singapore time).

The read-only deployed-service experiment completed a gradual 10 → 175 → 10 VU cycle with zero business errors and normal response recovery. A separate local experiment checked catalogue sizes of 100, 1,000 and 10,000 products with two VUs. All three local datasets passed response-content and configured latency/error checks. The local result is evidence about this synthetic workload on the recorded Mac, rather than production capacity.

Both experiments used application revision `e73fde73b42956411c95dc7145da6eb29dec778b`. The deployed experiment checked `/actuator/info` before and after; the local experiment compiled that unchanged source for ARM64. Original summaries, timelines, scripts and environment records are retained under [29 October evidence](evidence/2026-10-29/README.md). These different workloads do not supersede historical 200-VU failures; the [23 October PR #142 comparison](M5-PR142-after-comparison-2026-10-23.md) remains an earlier baseline.

## Deployed service load and recovery

Target: `http://178.128.107.99:8080`. The user confirmed a window without other testing, deployment or restart. This test only read catalogue endpoints; it created no listings or orders. k6 2.3.0 ran with a one-second think time and the `iPhone` keyword.

The script ran 10 VUs for 30 seconds, paused 10 seconds, ramped from 10 to 175 VUs over 90 seconds, held 175 VUs for 120 seconds, and ramped down to 10 over 30 seconds. After another short gap it observed 10 VUs for 90 seconds. The first sample set is a short baseline, not a long warmed-up reference.

| Phase | Business requests | Errors | Median | P95 |
|---|---:|---:|---:|---:|
| Baseline, 10 VUs | 273 | 0 | 97.80 ms | 309.97 ms |
| Pressure, including ramp, hold and down-ramp | 22,087 | 0 | 371.25 ms | 1,068.09 ms |
| Recovery, 10 VUs | 877 | 0 | 22.87 ms | 57.87 ms |

All 23,237 business requests succeeded. Two setup requests are excluded from the table. Recovery listing/search P95 values were 66.59/48.87 ms. The run exited normally with all configured checks passing. The pressure figure combines its changing load stages; it is not a measurement of the 175-VU plateau alone. Configured latency thresholds covered baseline/recovery; pressure also had its recorded error-rate check, not a sustained-capacity acceptance gate.

This shows response recovery for this bounded read-only experiment. It does not determine maximum capacity, resolve the historical 200-VU startup EOF failures, or prove a particular resource bottleneck. No simultaneous server-resource collection is asserted. A gradual start differs from the historical independent constant-VU starts; 200 VUs were not exercised here.

Sources: [summary](evidence/2026-10-29/load-recovery/summary.json), [phase analysis](evidence/2026-10-29/load-recovery/result.json), [script](evidence/2026-10-29/load-recovery/recovery.js), [console](evidence/2026-10-29/load-recovery/console.log), and the losslessly compressed per-request timeline in the evidence directory.

## Isolated local catalogue volume experiment

The dedicated Docker project `cui-5006-volume-1029` had its own MySQL, Redis, Elasticsearch and object-storage service. The API bound only to `127.0.0.1:18086`; the database had no published host port. The application was built from the pinned source without modifying it. No production data was copied or changed.

Resource limits were backend 2 CPUs/1 GiB, MySQL 1 CPU/1 GiB, and Elasticsearch 1 CPU/1 GiB with a 256 MiB heap. The Docker host exposed 10 CPUs and approximately 7.75 GiB RAM. Native ARM64 image identities, versions and limits are recorded in [environment.json](evidence/2026-10-29/local-volume/environment.json). The k6 client shared this host.

Each dataset used exactly 20 synthetic sellers and 100, 1,000 or 10,000 active products, with two media metadata rows per product. Product price, stock, description and timestamps were deterministic. Order/review history was empty. Image URLs were metadata fixtures: image objects were neither uploaded nor downloaded.

At every size, the same two VUs ran for 45 seconds with a one-second think time after three selective/broad warm-up pairs. Requests alternated among catalogue listing, a selective search always returning 10 products, a broad search returning all products, and an empty search. This separates growing stored data with fixed result size from growing returned data. Database/index counts, exact returned ID membership, uniqueness and both media records per product were checked before timing.

| Products | Media rows | Requests | Errors | List P95 | Fixed 10-match search P95 | Broad search P95 | Empty search P95 |
|---|---:|---:|---:|---:|---:|---:|---:|
| 100 | 200 | 86 | 0 | 142.66 ms | 183.71 ms | 91.62 ms | 58.55 ms |
| 1,000 | 2,000 | 86 | 0 | 56.42 ms | 61.57 ms | 123.99 ms | 15.17 ms |
| 10,000 | 20,000 | 76 | 0 | 337.41 ms | 169.48 ms | 384.38 ms | 89.85 ms |

All 248 measured requests succeeded. All configured per-operation median <1,000 ms / P95 <2,000 ms checks passed, as did result-content checks. Throughput was 1.902, 1.910 and 1.666 requests/s respectively for these closed-loop runs. The request rate is an outcome of the workload, not a separately fixed arrival rate.

Each operation has a small sample. The size order was ascending and the JVM/cache state can change, so the lower 1,000-product P95 is not proof that increasing data improves performance. Repeated trials with controlled order would be needed for statistical comparisons. This experiment does not cover large transaction/review history, actual media transfers, long-duration reliability, maximum dataset size or production throughput.

Sources: [machine-readable results](evidence/2026-10-29/local-volume/result.json), [metrics CSV](evidence/2026-10-29/local-volume/metrics.csv), [workload](evidence/2026-10-29/local-volume/volume.js), and per-size directories containing raw summaries/timelines, deterministic seed SQL, correctness results, row/index counts, versions, database counters, backend logs and eight container-resource samples per run.

## Observed limitations

1. **The catalogue API does not paginate.** At 10,000 products, broad search returned 10,527,474 bytes. Requests with `page=0` and `page=1`, both with `size=12`, returned the same complete set of 10,000 IDs. The current controller does not implement those pagination parameters; this result does not demonstrate a bounded response. See [pagination observation](evidence/2026-10-29/local-volume/pagination-observation.json).
2. **The 10,000-product startup index call timed out.** The application logged a 1,000 ms timeout during its index rebuild. The index subsequently held all 10,000 documents, and content checks passed. The retained log contains no per-request SQL-fallback warnings during measurement; per-request path tracing was not enabled. Retain the bootstrap warning separately from the measured HTTP error rate. See [backend log](evidence/2026-10-29/local-volume/runs/10000/backend.log) and [final index count](evidence/2026-10-29/local-volume/final-index-count.json).

Local services were stopped after testing to release resources. Their separate volumes remain available locally for reproduction. Local credentials and database volume contents are excluded from Git; the retained seed scripts reconstruct the synthetic catalogue.
