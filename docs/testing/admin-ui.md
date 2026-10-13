# Sprint 4 / #62: administrator back office

Independent admin changes, rebased onto main `b568ead` after bargaining PR #112
merged. Uses the existing
contract in `docs/api/moderation.md`. No backend, schema or dependency changes.

## Pages

- `/admin/reports`: status/type filters, pagination, reporter/target/reason,
  decision state, confirmation with a note, links to the item and target account.
- `/admin/users`: account search, status filter, current ban reason/time,
  ban (required reason) and reinstatement. Self/administrator direct bans are hidden.
- `/admin/audit-log`: read-only paged action history and filtering.

The menu and router require administrator status; losing the local role/token
clears the page. The backend's JWT/role checks remain the security authority.
`403 ACCOUNT_BANNED` and current-token `401` handling is installed on all three
Axios instances, not just global Axios. Old-token failures cannot sign out a
newer session. Ordinary `403` errors do not masquerade as session expiry.

## Local checks

From `frontend/`:

```bash
npm run test:admin
npm run build
```

The tests use Node's built-in runner, the actual Vue setup/lifecycle functions,
and a headless renderer with mocked APIs. They cover role/route restrictions,
endpoint shapes, filters/paging, confirmations, note validation, state refresh,
last-page adjustment, conflict/double-submit behaviour, stale requests, role
revocation, and banned/expired-session handling. CI runs this suite before build.

Browser verification uses the production build with an isolated, in-memory API:
report confirmation/filtering, account deep link, ban/reinstate, audit entries,
member redirect/menu hiding, banned-session redirect/reason, error states,
narrow-screen layout and dialog Tab/Escape behaviour. These are UI checks,
**not** proof of real server/database moderation effects.

Verified locally on 12 October 2026: all 25 tests, targeted ESLint and the
production build passed (existing asset-size/browser-data warnings remain).
The browser flows above passed, including no document-wide horizontal overflow
at a narrow viewport, focus cycling inside the dialog and Escape cancellation.
No live account was banned or reinstated during verification.

Report/audit zone-less times are labelled **server time**, rather than guessing
an offset. Offset-aware user timestamps are formatted in the browser's local time.

## Real staging acceptance (pending, with M1)

- [ ] An ordinary member/guest cannot reach any admin page; direct API calls remain 403.
- [ ] Use dedicated demo accounts/items, not real classmates' working accounts.
- [ ] Submit a report via #63; it appears in the queue with the correct reason/target.
- [ ] Uphold a product report: product delists; all open reports on that target close.
- [ ] Uphold a user report: account is banned and gets the reason on login/old-token requests.
- [ ] Dismiss a report: target stays unchanged and decision state/note are visible.
- [ ] Ban/reinstate from user accounts; verify current state and subsequent login behaviour.
- [ ] Verify audit entries, filters, pagination, empty/error/retry states on the real API.
- [ ] Verify concurrent/stale decisions return conflict and the list refreshes safely.

Backend follow-up for M1: `ModerationService.ban()` protects the current admin
and other administrators, but the USER-report `uphold()` path calls `applyBan()`
without those same guards. Review this contract inconsistency before testing
reports against administrator accounts. This frontend PR does not change backend policy.

## Parallel PR integration

PR #112 is already in main. Both `test:bargain` and `test:admin` package scripts
and frontend CI test steps are retained. The admin PR diff contains only its own
changes; it does not reintroduce bargaining files or depend on its helpers.
