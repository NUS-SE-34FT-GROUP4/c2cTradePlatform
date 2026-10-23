# Search optimization review — deployed ce6212ab

Read-only source review; no application changes or deployments. Evidence: PR142 comparison report. Search P95 5092.83ms versus list 1050.21ms at 200 VUs. Differences between aggregate percentiles are not per-stage timings and cannot establish ES duration.

## Confirmed code paths

- SearchService.search performs SQL membership/filtering and media hydration before a synchronous Elasticsearch ranking request, then credit/seller hydration and highlighting. SQL determines membership; ES only changes ordering. Even zero or one SQL result still causes an ES request.
- ProductService now batches media and seller retrieval (#142). Do not report that fixed N+1 as still present in catalogue requests.
- ElasticsearchProductSearchQuery requests up to 1000 hits and materializes full ProductSearchDocument objects, although its caller only consumes IDs. A maximum of 1000 does not mean every request returns 1000 documents.
- ProductMapper.selectWithFilters performs substring LIKE predicates and SQL relevance calculations before ES ranks again; no SQL pagination. SQL substring matching and ES token matching are deliberately different, so replacing SQL membership with ES-only search changes semantics.
- CreditService.forSellers aggregates completed orders/reviews per request via CreditMapper.selectHistory. Batched query count does not imply bounded work as history grows.
- ES connect/socket timeouts are configured as 1s/2s; no explicit client pool/acquisition customization was found under src/main. These values do not establish a whole-request deadline or prove pool exhaustion.

## Priority and verification

1. Instrument SQL+media, ES wall time, DTO/credit and total elapsed time. Correlate sql_fallback logs, ES took/queue/rejections, HTTP connection acquisition wait, Hikari pending/active, CPU/GC. No server telemetry currently establishes which stage dominates.
2. Low-scope candidate: skip ES ranking when SQL returns zero or one result; preserve DTOs, highlighting and fallback result semantics. Unit tests should verify no ES call and identical output for zero/one; multiple-result relevance must remain unchanged. This improves only those cases, not general multi-hit capacity.
3. Return only the needed ID field from ES, preserving deserialization and ranking; restrict ranking to authoritative candidate IDs where feasible, with bounded request sizes. Test index lag and SQL-only matches. Do not simply reduce the 1000 cap without checking ordering.
4. If logs show repeated ES failure/queueing: bounded concurrency, acquisition/overall deadlines and a circuit breaker with periodic recovery probes can avoid waiting on every request. SQL fallback preserves membership but changes ranking; make degradation observable. Do not blindly enlarge pools or thread counts on the shared host.
5. Longer-term: define pagination and one coherent membership/ranking contract, then optimize SQL with EXPLAIN and appropriate indexes. Ordinary indexes are not a blanket solution for LOWER(column) LIKE '%keyword%'. Review credit aggregation plans and consider invalidated summaries. Avoid caching complete availability/stock responses without correctness controls.

Keep baseline script unchanged for paired comparisons. Its actual load keyword is iPhone; QA Test Book is preflight only. A one-hit optimization passing that workload does not establish general search capacity. Add multi-hit and no-hit cases separately, rather than silently changing the original comparator.
