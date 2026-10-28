# Replaceable file storage — Strategy

**Owner:** M1 YU CHENGLIN · **Sprint:** 3 (code), 4 (write-up, #79) · **Code:** `service/FileStorageService.java`,
`service/impl/MinioFileStorageService.java`; used by `AvatarController`, `ProductController`, `ReviewController` ·
**Tests:** `MinioFileStorageServiceTest`

## The design problem

Two features upload files: avatars (WP1, `AvatarController`) and listing photos and videos
(WP2, `ProductController`). Where the bytes end up is an infrastructure decision that has
already changed once and is likely to change again:

- During Sprint 2 the `minio/minio` image was withdrawn from Docker Hub and replaced with a
  community rebuild (commit `ad95ba6`). A storage backend can disappear underneath us.
- Local development, CI and staging have different needs. CI has no object store at all,
  and a production deployment might use a managed service such as S3 instead of running
  MinIO.
- Upload rules have to be applied the same way for every feature: the 10 MB limit, the
  image/mp4 whitelist, and names the uploader cannot guess or overwrite.

If each controller talked to MinIO directly, changing the backend would mean editing every
feature that uploads. The validation rules would also be copied into each of them and could
drift apart.

**The brute-force version, measured against today's code.** Three features upload now:
`AvatarController` (WP1), `ProductController` (WP2) and `ReviewController` (WP5, added in
Sprint 3). `MinioFileStorageService` holds 84 lines of logic that every uploader needs: the
size and type checks, the `folder/yyyy/MM/dd/<uuid>.<ext>` naming, the public-URL rewrite and
the bucket policy. Without the interface that logic would sit in three controllers, about 250
lines in three copies, and:

- the MinIO image change in Sprint 2 (`ad95ba6`) would have been three edits instead of zero;
- a fix to the type whitelist would have to be made three times, and missing one would let
  that feature accept files the others reject;
- each controller test would have to mock `MinioClient` and its builder arguments.

## The pattern

Strategy: define a family of interchangeable algorithms behind one interface, so the client
does not depend on which one it gets.

| Strategy role | In this code |
|---|---|
| Strategy interface | `FileStorageService` — `upload(file, folder)` returns a browser-loadable URL, `delete(objectName)` |
| Concrete strategy | `MinioFileStorageService` — validates, names the object `folder/yyyy/MM/dd/<uuid>.<ext>`, stores it in MinIO, returns a URL on the public endpoint |
| Context | `AvatarController`, `ProductController` — hold a `FileStorageService` and never name MinIO |
| Strategy selection | Spring dependency injection: the one `@Service` bean that implements the interface is injected |

```
AvatarController ──┐
                   ├──► «interface» FileStorageService ◄── MinioFileStorageService ──► MinioClient
ProductController ─┘         upload(file, folder)            (validation, naming,
                             delete(objectName)               public URL, bucket setup)
```

The interface speaks the callers' language: a *folder* such as `"avatar"` or `"product"` goes in
and a *URL* comes out. Buckets, endpoints and the Docker-internal hostname are all inside the
strategy. The URL detail matters: the backend reaches MinIO at `http://minio:9000`, which a
browser cannot resolve, so the strategy builds URLs from a separate public endpoint. No
controller has to know that.

## Why Strategy, and not something else

- **A plain helper class** would put upload in one place, but callers would still depend on
  the MinIO class, so swapping the backend would still touch every caller.
- **Adapter** fits when an existing interface has to be made to match another one. Here we
  are designing the interface ourselves, and the point is to be able to pick between
  implementations, which is what Strategy is for.
- **Factory** is not needed yet. Spring already picks the implementation. If a second
  strategy is added, the choice becomes a configuration switch (`@ConditionalOnProperty`) on
  the implementations, and the callers still do not change.

## What it would look like without it

- Two controllers each build `PutObjectArgs`, pick a bucket and assemble URLs, and the next
  feature that uploads (review photos, M5) would copy that again.
- Replacing MinIO would mean editing and re-testing every uploading feature, instead of
  adding one class.
- The MinIO client would have to be mocked in every controller test. With the interface,
  a controller test mocks a two-method interface instead.

## Consequences and honest limits

- **It has already paid off once.** When M5 added review photos in Sprint 3 (`93fecb0`),
  `ReviewController` needed one line, `storage.upload(file, "review")`, and no storage code,
  validation or MinIO knowledge. The new feature got the same limits and naming as the other
  two for free.

- **Adding a backend is one class.** An S3 or local-disk storage would implement
  `FileStorageService` and be selected by configuration. Neither `AvatarController` nor
  `ProductController` would change.
- **There is one concrete strategy today.** The flexibility has been paid for (an interface
  and injection) but not yet used by a second implementation. This was deliberate: we did not
  want to write a backend nobody runs. The first real candidate is a local-disk strategy for
  development without Docker.
- **Validation lives in the concrete strategy.** A second strategy would have to apply the
  same size and type rules. If that happens, the rules should move into a shared step before
  the strategy is called, so the two cannot differ.
- **Startup is not blocked by storage.** `ensureBucket()` logs and continues when MinIO is
  unreachable, so the rest of the site still works during a storage outage.

## Evidence

`MinioFileStorageServiceTest` (9 tests, no MinIO server needed — the client is mocked):

- object naming and the public URL, including the trailing-slash and fallback-endpoint cases
- rejection of empty, oversized and non-whitelisted files *before* anything is stored
- delete targets the configured bucket
- bucket creation and reuse on startup, and tolerating an unreachable MinIO

Coverage (JaCoCo, Sprint 3): `MinioFileStorageService` 98% lines / 91% branches, above the
80% required by NFR 6.3.

## Diagrams

The diagrams the report template counts (#96), each as native `.drawio` next to its exported `.png`:

- `diagrams/m1-file-storage/` — this design problem (§3.5), before and after the pattern:
  `01-before-class`, `02-after-class`, `03-before-sequence`, `04-after-sequence`. The "before"
  pair is the design without the interface, as described above: the three uploading controllers
  each holding `MinioClient` and their own copy of the validation, naming and URL code. The
  "after" pair is today's code; the sequence follows `ReviewController.image` through
  `FileStorageService.upload` to `MinioClient.putObject`.
- `diagrams/m1-moderation/` — the "administrator moderates a report" use case (§3.4), built in
  #61: `01-analysis-class`, `02-analysis-sequence`, `03-design-class`, `04-design-sequence`.
  The design pair follows `AdminModerationController.uphold` → `ModerationService.uphold` and its
  mappers as they are in the code, including the row lock on the report, the
  `checkBannable` refusal, and the audit row written in the same transaction.
