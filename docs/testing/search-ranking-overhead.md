# Search ranking overhead

SQL remains authoritative for membership, filters and display data. Elasticsearch
only ranks multiple SQL matches. Zero/one-match searches now skip ES; hydration
and highlighting still run. Multi-match ordering, SQL-only matches, the 1,000-hit
ES limit and outage fallback are unchanged. ES responses include only the `id`
source field; indexed fields still participate in filtering and scoring.

Each completed search logs its path and monotonic elapsed milliseconds:

- `sql_media_ms`: SQL membership query and media hydration.
- `es_ms`: ES adapter wall time, including client waiting; zero when skipped.
- `dto_highlight_ms`: DTO assembly, seller/credit queries and highlighting.
- `total_ms`: service elapsed time, including ranking reconciliation. This is not
  end-to-end HTTP latency and excludes time before entering the service.

`sql_no_ranking_needed` distinguishes the new shortcut from `sql_browse`,
`elasticsearch_ranked` and `sql_fallback`. Failed ES attempts retain their elapsed
time. Database/DTO exceptions still propagate and do not produce a completed-search
timing record. Logs exclude search terms, location values and exception messages.

## Validation and handoff

Focused unit tests cover zero/one-match bypass, preserved fields/highlighting,
multi-match ranking/membership, fallback/recovery and the ID-only source filter.
The existing `SearchParityIT` exercises real Elasticsearch ID deserialization and
SQL/ES membership parity in CI.

No local performance test was run for this change. After review and deployment,
QA should retain the original benchmark and additionally exercise zero, one and
multiple matches. Compare stage timings and HTTP percentiles on the same dataset,
environment and concurrency. A passing single-match workload does not establish
200-user search compliance or identify the cause of the previous 5.09 s P95.
