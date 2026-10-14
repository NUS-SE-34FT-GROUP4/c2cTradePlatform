# Search membership, degradation and recovery (#65)

## Contract and implementation

`GET /api/products` retains its array response, filters and engine-independent
highlighting. MySQL determines membership on **every** request using the existing
case-insensitive SQL substring contract. Only active listings with
`stock - reserved_stock > 0` qualify. Price, condition, location and category
filters are evaluated on current SQL rows, not on a potentially stale projection.

For keyword searches, `SearchService` asks `ProductSearchQuery` for Elasticsearch
ranked IDs. It takes matching SQL rows in that order, ignores extra/duplicate ES
IDs, and appends SQL-only rows in SQL order. A missing document, tokenisation
difference, stale field, empty index or ES's 1,000-hit window cannot remove a SQL
match. This does **not** claim that raw Elasticsearch and SQL matching semantics
are identical: the **public response sets** are equal; their ordering may differ.

If ES throws, the already fetched SQL result is returned. Subsequent requests
retry ES using the same Spring/client instances; no application restart or manual
mode switch is required. Blank queries skip ES. SQL/DTO conversion failures are
not swallowed as an ES outage.

### Deliberate trade-off

This is a correctness-first solution for the current unpaginated course catalogue.
ES provides relevance ranking, **not database offloading**: a healthy keyword
search still performs the SQL membership query and media hydration. No throughput
or latency improvement is claimed. At larger scale, a paginated shared matching
contract and reliable change delivery/reindexing would be needed before removing
SQL reconciliation. Existing indexing writes remain best-effort; this change does
not implement an indexing retry queue, but missed rows remain discoverable.

## Observable paths

SearchService writes one path event per successful membership/ranking selection:

- `catalogue_search path=elasticsearch_ranked results=... ranked=... sql_only=... ignored_es_hits=...`
- `catalogue_search path=sql_fallback results=... reason=...`
- `catalogue_search path=sql_browse results=... ranked=0 sql_only=0 ignored_es_hits=0`

`sql_only` counts returned SQL matches absent from the ES ranking. `ignored_es_hits`
counts ES IDs not consumed (including duplicates); neither counter by itself
proves index corruption because the engines have different matching semantics.
The path is selected before DTO conversion, so it is not an HTTP success metric.
These events contain no keyword or raw ES exception body. Existing application
logs can be inspected with `docker compose logs backend` (use the appropriate
Compose file/environment); no new public diagnostics endpoint is exposed.

## Automated verification

Run with JDK 17+, Maven and a running Docker engine:

```sh
mvn -B clean verify
```

The existing CI backend job already executes this command and uploads the
Surefire/Failsafe reports. No new test dependency is required.

`SearchServiceTest` covers ranking, missing/extra/duplicate IDs, an empty index,
1,005 SQL matches versus 1,000 ES IDs, outage/retry, blank queries, unchanged
highlighting and propagation of SQL/DTO failures.

`SearchParityIT` starts isolated MySQL 8.0.36 and Elasticsearch **8.10.4**, matching
the repository's Compose ES version. It uses the production schema, MyBatis XML,
real Elasticsearch query/index adapters and HTTP controller via MockMvc. It does
not use the developer's running database/search containers. Missing Docker fails
the suite instead of skipping it.

- Twelve deterministic queries cover phrases, uppercase/whitespace, substrings,
  punctuation (`C++`), Chinese, category-field matches, price bounds, condition,
  location, multiple categories, empty results and blank browsing.
- Each query's expected listing IDs are checked through HTTP while ES is healthy,
  while its container is paused (real socket timeout), and after unpausing it.
  Responses must be HTTP 200 and contain no duplicates. Expected sets, not just
  agreement between two potentially wrong paths, are asserted.
- Logs must show healthy, fallback and recovered ranking paths. The same service
  and ES client instances are used; the container is always unpaused in `finally`.
- Direct SQL changes simulate missed index updates: changed price, reserved stock,
  delisting and a new unindexed listing. The current SQL filters must win.
- Recreating an empty ES index must not hide the new SQL-only listing.

Testcontainers owns these temporary containers and removes them after the test
JVM exits. Tests never pause/delete a staging or pre-existing container.

### Recorded local run — 13 October 2026

`mvn -B clean verify` passed on Java 21 (compiled with the project's Java 17
release target): **159 unit tests + 58 integration tests = 217**, zero failures,
errors or skips. This includes all 12 `SearchServiceTest` cases and both
`SearchParityIT` cases. JaCoCo's configured coverage gate passed. CI independently
uses JDK 17; its result should be checked on the PR rather than inferred from this
local run.

## Staging follow-up

Automated local/CI container testing is not a claim of a completed live staging
rehearsal. Coordinate with M1 before stopping any shared service:

- [ ] Record one query/filter set and its IDs on the current staging build.
- [ ] During an agreed outage window, make only ES unavailable; repeat the set.
- [ ] Confirm HTTP success, the same IDs and `path=sql_fallback` in application logs.
- [ ] Restore ES without restarting the backend; repeat and record
      `path=elasticsearch_ranked` with the same IDs.
- [ ] Save commit/run identifiers and screenshots/log extracts for the demo.
