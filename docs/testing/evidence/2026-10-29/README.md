# Raw evidence for catalogue volume and load recovery

See the [results and interpretation](../../M5-volume-and-recovery-results-2026-10-29.md) before comparing these experiments with earlier measurements.

- `local-volume/`: local ARM64 catalogue-size experiment, 100/1,000/10,000 products, two VUs per run.
- `load-recovery/`: deployed read-only gradual 10 → 175 → 10 VU experiment.
- `manifest.json`: SHA-256 values for retained files and the original uncompressed bytes.

The original `timeseries.json` and per-size `seed.sql` files are stored as deterministic gzip streams (`.gz`). Compression preserves their exact contents; it does not filter errors or samples. Raw terminal logs also retain their original banner/progress whitespace; a scoped `.gitattributes` entry excludes those logs from whitespace linting. Summaries, conclusions, correctness checks and metadata remain directly readable. Run this integrity check from the repository root:

```sh
python3 docs/testing/evidence/2026-10-29/verify_evidence.py
```

To inspect a timeline without changing the archive:

```sh
gzip -dc docs/testing/evidence/2026-10-29/local-volume/runs/10000/timeseries.json.gz
```

## Reproduce the local experiment

The retained runner is the version actually used. It uses the fixed disposable Compose project name `cui-5006-volume-1029`, resets that project's synthetic product catalogue, and refuses a database with any orders. Do not point it at a shared or production environment. A rerun should use a fresh work directory and run-output directories; it does not run directly inside the archived `runs/` tree.

The following preparation steps do not contact the deployed service:

1. Create an empty local work directory. Copy `local-volume/compose.json`, `volume.js` and `run-volume.py` into it.
2. Clone this repository into its `source/` subdirectory and check out `e73fde73b42956411c95dc7145da6eb29dec778b`. This supplies the original Dockerfile, schema and application source. The bootstrap schema is destructive and belongs only in this separate local database.
3. Create a private `.env` beside `compose.json` with fresh `LOCAL_DB_PASSWORD`, `LOCAL_MINIO_PASSWORD` and `LOCAL_JWT_SECRET` values. The JWT value must be standard Base64 encoding of at least 64 random bytes. The original generated values are intentionally not retained.
4. Build the original backend Dockerfile as `cui-5006-volume-backend:e73fde73`. Ensure the object-storage image referenced by the Compose file is available. The original local tag `ass-wp10-minio:2025-04-22` is machine-local; its exact image ID is recorded in `environment.json`. A replacement build/image must be recorded as a changed environment, not represented as the original image.
5. Start that Compose file's services with `docker compose -f compose.json up -d`. It exposes only a loopback API and uses independent named volumes. Keep the fixed project name because the safety check verifies the MySQL container's Compose label.
6. Run `python3 run-volume.py` from the fresh work directory. The runner seeds each level, restarts the local backend to rebuild its separate index, verifies content and records the measurement. It requires Docker and outbound access for images; Python uses only its standard library.
7. Use `docker compose -f compose.json stop` afterward. Keep or remove only this disposable project's volumes according to the intended local retention policy.

The original test used a Mac with native ARM64 images. A different platform, JVM, image digest or resource configuration is a new experiment. No real payment account or live credentials are needed.

## Scope of retained measurements

Each local run has 45 seconds of two-VU measurement, three prior selective/broad warm-up pairs, and a one-second think time. The request mix includes all-result and empty-result cases. The workload transfers JSON media metadata but does not fetch media objects. The local results do not demonstrate production capacity.

The recovery script targets the deployed service. **Do not rerun it without a new coordinated testing/deployment window.** Its raw output is historical evidence; copying or verifying the files generates no load.

For `business_errors`, k6's rate counts boolean `true` as an error: `passes` is the number of error observations and `fails` the non-error observations. Do not read the `fails` counter as failed HTTP requests.
