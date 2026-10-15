// One place to read the timestamps the API sends.
//
// The API serialises `LocalDateTime`, so most time fields arrive without a zone
// suffix, and the backend's own clock is UTC: `WalletService` takes
// `Clock.systemUTC()`, and no image in `compose.yaml` sets `TZ`. Reading such a
// value with `new Date()` makes the browser call it local time, which from
// Singapore moves every timestamp eight hours into the past — a brand-new order
// reports its payment window as already closed.
//
// So: keep an explicit offset when the value carries one, and interpret a
// zone-less value as UTC. `utils/admin.mjs` deliberately refuses to guess and
// prints "(server time)" instead, which is the honest answer for an audit
// trail, but not for a countdown — that cannot be computed at all without
// settling on a zone.

/** Milliseconds since the epoch, or NaN when the value is absent or unparseable. */
export function timestamp(value) {
  if (!value) return NaN;
  const text = String(value);
  return Date.parse(/(?:Z|[+-]\d{2}:?\d{2})$/i.test(text) ? text : `${text}Z`);
}

/** The value in the reader's own zone, e.g. "14 Oct 2026, 5:03 pm". */
export function dateTime(value) {
  const time = timestamp(value);
  return Number.isFinite(time)
    ? new Intl.DateTimeFormat('en-SG', { dateStyle: 'medium', timeStyle: 'short' }).format(time)
    : 'Time unavailable';
}

/** Milliseconds from `now` until the value, or NaN when it cannot be read. */
export function millisUntil(value, now) {
  const end = timestamp(value);
  return Number.isFinite(end) ? end - now : NaN;
}

/** A positive span as "m:ss", for a ticking payment window. */
export function countdownClock(millis) {
  const left = Math.max(0, millis);
  const minutes = Math.floor(left / 60000);
  const seconds = Math.floor((left % 60000) / 1000);
  return `${minutes}:${String(seconds).padStart(2, '0')}`;
}
