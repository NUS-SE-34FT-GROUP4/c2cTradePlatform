# Moderation API: reports, bans and the admin back office

Contract for Sprint 4 issues **#61** (admin back office service, Kiyuim), **#62**
(admin pages, nico-orange) and **#63** (report an item or a user, HuailingMa +
Kiyuim). Frontend and backend build against this page at the same time; change
it by PR so all three see the change.

Conventions follow the existing API: JSON bodies, `Authorization: Bearer <jwt>`,
errors as `{"error": "<short title>", "message": "<sentence for the user>"}`
(see `RestExceptionHandler`). Times are ISO-8601 strings. Lists are paged with
`page` (from 0) and `size` (default 20, max 100).

## 1. Data model (migration `scripts/migrations/004_moderation.sql`)

| Table / column | Type | Notes |
|---|---|---|
| `users.status` | `VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'` | `ACTIVE` or `BANNED` |
| `users.ban_reason` | `VARCHAR(500) NULL` | Shown to the user on every refusal |
| `users.banned_at` | `DATETIME NULL` | |
| **`report`** | | One row per submitted report |
| `id` | `BIGINT` PK | |
| `reporter_id` | `BIGINT` FK `users` | |
| `target_type` | `VARCHAR(10)` | `PRODUCT` or `USER` |
| `target_id` | `BIGINT` | `pms_product.id` or `users.id` |
| `reason` | `VARCHAR(30)` | One of the codes in §2.3 |
| `detail` | `VARCHAR(1000) NULL` | Free text from the reporter |
| `status` | `VARCHAR(20)` | `OPEN`, `UPHELD`, `DISMISSED` |
| `decided_by` | `BIGINT NULL` FK `users` | The administrator |
| `decision_note` | `VARCHAR(500) NULL` | |
| `created_at`, `decided_at` | `DATETIME` | |
| **`admin_audit_log`** | | Append-only; written in the same transaction as the action |
| `id` | `BIGINT` PK | |
| `admin_id` | `BIGINT` FK `users` | |
| `action` | `VARCHAR(30)` | `BAN_USER`, `REINSTATE_USER`, `UPHOLD_REPORT`, `DISMISS_REPORT`, `DELIST_PRODUCT` |
| `target_type`, `target_id` | | What the action was applied to |
| `report_id` | `BIGINT NULL` | Set when the action came from a report |
| `note` | `VARCHAR(500) NULL` | |
| `created_at` | `DATETIME` | |

Indexes: `report(status, created_at)`, `report(reporter_id)`,
`admin_audit_log(created_at)`. The same DDL is appended to `init.sql` for fresh
databases.

## 2. Users: submitting a report (#63)

### 2.1 `POST /api/reports`

Any signed-in, active user.

```json
{ "targetType": "PRODUCT", "targetId": 42, "reason": "PROHIBITED_ITEM", "detail": "Selling a used exam paper" }
```

| Response | When |
|---|---|
| `201` with the report (§4.1) | Stored with `status = OPEN`; it appears in the admin queue |
| `400` | Unknown `targetType` or `reason`, `detail` over 1000 characters, or reporting yourself or your own product |
| `404` | The product or user does not exist |
| `409` | This reporter already has an `OPEN` report on the same target |

### 2.2 `GET /api/reports/mine?page=0&size=20`

The caller's own reports, newest first, as a page (§4.3) of reports (§4.1).
Lets the reporter see whether a report was upheld or dismissed.

### 2.3 `GET /api/reports/reasons`

Public. Fixed list for the dropdown, so the frontend does not hard-code it:

```json
[
  { "code": "FRAUD",           "label": "Scam or fraud" },
  { "code": "PROHIBITED_ITEM", "label": "Prohibited item" },
  { "code": "MISLEADING",      "label": "Misleading listing" },
  { "code": "HARASSMENT",      "label": "Harassment or abuse" },
  { "code": "SPAM",            "label": "Spam" },
  { "code": "OTHER",           "label": "Other" }
]
```

## 3. Administrators: back office (#61, pages in #62)

Everything under `/api/admin/**` requires `ROLE_ADMIN` (already enforced in
`SecurityConfig`); anyone else gets `403`.

### 3.1 Report queue

`GET /api/admin/reports?status=OPEN&targetType=&page=0&size=20`

`status` defaults to `OPEN`; `targetType` is optional. Oldest first for `OPEN`
(a queue), newest first otherwise. Page (§4.3) of reports (§4.1).

`POST /api/admin/reports/{id}/uphold`, body `{ "note": "Confirmed with photos" }`

| Target | Effect, in one transaction |
|---|---|
| `USER` | The user is banned (§3.2) with `ban_reason` = the reason label + note |
| `PRODUCT` | The product is delisted (`pms_product.status = 0`); the seller is not banned automatically, the administrator can ban them from the user page |

Report becomes `UPHELD`; audit rows `UPHOLD_REPORT` plus `BAN_USER` or
`DELIST_PRODUCT`. Other `OPEN` reports on the same target are closed as `UPHELD`
with the same note, so the queue does not show a handled case twice.

`POST /api/admin/reports/{id}/dismiss`, body `{ "note": "Item is allowed" }`:
report becomes `DISMISSED`; audit row `DISMISS_REPORT`.

Both return the updated report (§4.1). `404` if the report does not exist,
`409` if it is no longer `OPEN`. `note` is optional (max 500).

### 3.2 Users

`GET /api/admin/users?status=&q=&page=0&size=20`: page of users (§4.2); `q`
matches username, display name or email; `status` is `ACTIVE` or `BANNED`.

`POST /api/admin/users/{id}/ban`, body `{ "reason": "Repeated scam listings" }`

- `reason` is **required** (1–500 characters); it is what the user is shown
- `400` when banning yourself or another administrator, `409` when already banned
- Sets `status = BANNED`, `ban_reason`, `banned_at`; audit row `BAN_USER`

`POST /api/admin/users/{id}/reinstate`, body `{ "note": "Appeal accepted" }`

- `409` when the user is not banned
- Sets `status = ACTIVE` and clears `ban_reason`, `banned_at`; audit row `REINSTATE_USER`
- Nothing else needs restoring: a ban only blocks requests, it deletes nothing

Both return the updated user (§4.2).

### 3.3 Audit log

`GET /api/admin/audit-log?action=&page=0&size=20`: newest first, page of

```json
{ "id": 7, "admin": { "id": 1, "username": "admin" }, "action": "BAN_USER",
  "targetType": "USER", "targetId": 15, "reportId": 3, "note": "Repeated scam listings",
  "createdAt": "2026-10-15T14:02:11" }
```

## 4. Shapes

### 4.1 Report

```json
{
  "id": 3,
  "reporter": { "id": 12, "username": "alice" },
  "targetType": "PRODUCT",
  "target": { "id": 42, "name": "Calculus textbook", "ownerId": 15, "ownerUsername": "bob", "status": 1 },
  "reason": "PROHIBITED_ITEM",
  "reasonLabel": "Prohibited item",
  "detail": "Selling a used exam paper",
  "status": "OPEN",
  "decidedBy": null,
  "decisionNote": null,
  "createdAt": "2026-10-15T13:40:00",
  "decidedAt": null
}
```

For `targetType = USER`, `target` is `{ "id": 15, "username": "bob", "displayName": "Bob", "status": "ACTIVE" }`.
If the target has since been deleted, `target` is `null`.

### 4.2 Admin user row

```json
{ "id": 15, "username": "bob", "displayName": "Bob", "email": "bob@example.com",
  "roles": ["ROLE_USER"], "status": "BANNED", "banReason": "Repeated scam listings",
  "bannedAt": "2026-10-15T14:02:11", "createdAt": "2026-09-20T10:00:00", "openReports": 2 }
```

### 4.3 Page

```json
{ "content": [ ... ], "page": 0, "size": 20, "totalElements": 57, "totalPages": 3 }
```

## 5. What a banned account sees

| Situation | Response |
|---|---|
| `POST /api/auth/login` with the right password | `403` `{"error": "ACCOUNT_BANNED", "message": "Your account is suspended: <ban_reason>"}`; no token is issued |
| Any authenticated request with a token issued before the ban (publish, order, pay, chat, …) | The same `403 ACCOUNT_BANNED` body, from the JWT filter, before the controller runs |
| Public pages (product list and detail) | Unchanged |
| After reinstatement | Everything works again with a fresh login; no data was removed |

Frontend (#62, and the global axios interceptor): on `403` with
`error = ACCOUNT_BANNED`, show `message`, clear the token and go to `/login`.

## 6. Pages (#62)

| Route | Content | Calls |
|---|---|---|
| `/admin/reports` | Queue with filter by status and type; uphold / dismiss with a note | §3.1 |
| `/admin/users` | Search, status filter, ban (reason required) / reinstate | §3.2 |
| `/admin/audit-log` | Read-only table | §3.3 |
| Report button on the product page and on a user's profile, plus "My reports" | Reason dropdown + free text | §2 |

The router guard sends non-admins away from `/admin/**`; the backend enforces it
regardless.

## 7. Seed data for the demo

`init.sql` already creates `admin` / `admin123` with `ROLE_ADMIN`. The demo
script (#85) adds one open report on a product and one on a user, so the queue
is not empty on 10/26.
