# Pipeline scanning and release evidence (#66)

## What the workflow now retains

The existing CI workflow still builds/tests PRs and publishes images only on
`main`. Environment approvals, release tags, deploy/rollback scripts and the
existing advisory security policy are unchanged. Required check names remain
unchanged. A new **Release evidence summary** job collects the results after
staging finishes/skips/fails; it does not wait for the production approval gate.

All evidence artifacts retain **30 days** and include the run attempt in their
names, so reruns cannot accidentally mix old artifacts:

| Artifact | Contents |
|---|---|
| `dependency-scan-evidence-<attempt>` | Trivy repository vulnerability JSON |
| `image-release-evidence-<attempt>` | Backend/frontend Trivy JSON and `images.json` |
| `staging-deployment-evidence-<attempt>` | Configuration, restart, smoke, live-record and rollback outcomes |
| `release-evidence-<attempt>` | Combined raw reports, release JSON, readable Markdown and slide excerpt |

The existing JaCoCo and test-report artifacts remain available separately.
Artifact paths are restricted to `.evidence`, with hidden-directory inclusion
enabled explicitly; the workspace, `.env`, SSH keys and deployment secrets are
not uploaded. Trivy's retained reports use `scanners=vuln`; the existing redacted
Gitleaks scan remains the separate secret check.

## Reading the evidence honestly

- `images.json` associates the commit, run ID/attempt and immutable commit tags
  with **registry manifest digests**, read back from GHCR after successful push.
  Local Docker image IDs are separately labelled and are not registry digests.
  An invalid/unreadable digest after a successful push fails the capture step
  (and prevents the dependent staging job), while retaining the failure metadata.
- PR builds explicitly say `not_requested` for publication. Failed/partial pushes
  say `not_verified`; missing data is never reconstructed as a successful release.
- Trivy records actual `steps.<id>.outcome`, not the success-masked conclusion of
  an advisory step. Valid reports distinguish `findings`, `clean_in_scope` and
  `scanner_error`. Missing/malformed reports show unknown counts, not zero.
- The vulnerability scope remains **HIGH/CRITICAL with `ignore-unfixed=true`**.
  Counts are findings across packages/targets, not unique CVE counts. No finding
  in this filtered scope does not mean an image has no vulnerabilities.
- Staging verification requires configured secrets, successful restart, health
  smoke and live-version recording, with no rollback and a successful staging
  job. Missing secrets, failed deployment, skipped smoke or rollback cannot
  produce a verified deployment claim.
- A complete retained chain requires successful test/build jobs, executed scans
  with retained results, verified published digests and verified staging health.
  Advisory findings are still visible and do not silently become blocking gates.
- This records a deployment at the run's time, not the current remote container.
  It does not verify the running image by digest, full transaction acceptance or
  production deployment. Production and live rollback rehearsal remain M1 work.

The Actions step summary contains `release-evidence.md`. The short
`slide-excerpt.md` has the source URL, commit, stage status, scan counts, registry
digests and explicit limitations, ready to copy into the team's slides. No
illustrative counts or placeholder digests are used.

## Command-line export (no browser)

Use Python 3.10+ and an authenticated `gh` CLI with repository/artifact read access:

```sh
python3 scripts/ci/export_release_evidence.py \
  --run <run-id> --directory .evidence/run-<run-id>
```

Optionally use `--attempt <number>`; the default is the latest attempt. Use an
empty/new output directory to prevent mixing runs. The exporter only reads
GitHub APIs/artifacts; it does not approve, dispatch or rerun workflows, change
secrets, log into a registry, publish images or contact the deployment server.

It saves selected run metadata, job/step metadata, `observed-pipeline.md`, the
release bundle and slide excerpt. When available, downloaded artifacts remain
under `retained/` and their original scan outcomes are preserved rather than
replaced by green job conclusions. Historical/expired/missing artifacts remain
explicitly unknown. Export needed evidence before the 30-day retention expires
and hand the selected excerpts to M1 for the DevSecOps segment.

## Actual historical observations — checked 13 October 2026

The exporter was exercised against both real runs below; these are observations
of their recorded steps, **not evidence that this new workflow has run on main**:

- [Run 38153290140](https://github.com/NUS-SE-34FT-GROUP4/c2cTradePlatform/actions/runs/38153290140),
  attempt 1, commit `3363f2d2b4cf40c742cb4bfa422878adb69e2f86`: backend tests,
  frontend build, repository/image scans, GHCR push, staging restart/health smoke
  and production health smoke were recorded as successful in that single run.
  This establishes an observed test → scan → publish → deploy chain. It did not
  retain the new structured scan/digest bundle, so exact scan counts/digests are
  not asserted and the exporter marks full retained evidence incomplete.
- [Run 38400096077](https://github.com/NUS-SE-34FT-GROUP4/c2cTradePlatform/actions/runs/38400096077),
  attempt 1, commit `74975baa22157d3372d3da8275061c6e7682706d`: tests, scans and
  GHCR push succeeded, but staging restart failed, health smoke was skipped and
  the automatic rollback step succeeded. This is **not** a successful deployment
  case, nor proof that rollback passed a health smoke test. Production was skipped.

## Verification and remaining acceptance

Local validation on 13 October 2026:

- 28 standard-library Python tests passed (success/findings/errors/skips, digest
  identity, malformed/cross-run data, reruns, no-secret metadata and real-export
  fallback logic). They run in the existing backend CI job as well.
- `actionlint` 1.7.12 passed for both `ci.yml` and the unchanged `rollback.yml`.
- The Docker Buildx manifest JSON command was checked against a real public
  registry image; it was not passed off as a project image digest.
- The exporter read the two real GitHub runs above correctly, with no deployment
  mutation. `git diff --check` passed. No application source or schema is changed.

After merge, the remaining acceptance is a **real main-branch release**:

- [ ] M1 resolves any deployment failure and performs the existing environment
      approval; this PR does not bypass it or trigger a release itself.
- [ ] Verify all three vulnerability JSON files and both GHCR digests are retained.
- [ ] Verify successful staging restart/health/live recording in that same run.
- [ ] Export the new `release-evidence-<attempt>` and hand the slide excerpt to M1.

Keep #66 open until this current-format end-to-end evidence is captured. A green
PR pipeline cannot satisfy publication/deployment acceptance because those steps
intentionally skip on PRs.
