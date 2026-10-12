# S4-07 / #67: bargaining UI verification

Scope: finish M4's product-page skeleton. The API stays in
`frontend/src/api/marketplaceService.js`; there is no new route or backend change.
The campaign and offer cards share styling and have loading, empty, error,
retry, success, pending-action and terminal states.

## Automated checks

From `frontend/`:

```bash
npm run test:bargain
npm run build
```

The dependency-free Node test runner checks the helpers and mounts the actual
Vue component setup/lifecycle logic with a headless renderer and mocked API.
The frontend CI job runs these tests before building. Coverage includes:

- future deadline and starting-price validation, UTC payloads, countdown expiry;
- campaign selection, multiple buyers and starting a separate campaign;
- one-help protection, duplicate-help refusal, floor and expired states;
- separate seller threads, latest-offer and counterparty permissions;
- accept/reject confirmation, counter-offer validation, double-submit prevention;
- buyer acceptance to payment, seller acceptance to order detail;
- accepted threads preventing new offers;
- refresh errors retaining the last good data, initial failure/retry;
- logout and unmount invalidating stale responses and navigation.

Browser QA can use an isolated, in-memory API fixture with the production build.
This validates rendered states and routing, **not** database/payment correctness.
Do not substitute mock results for the staging checks below.

Local verification on 12 October 2026: 28 automated tests passed; the changed
components, helpers and tests passed ESLint; the production build passed with
the existing asset-size / browser-data warnings. In-browser fixture checks
passed for campaign creation, offer submission, two seller threads, buyer
acceptance to the negotiated-amount payment page, seller acceptance to order
detail, expired/error states and narrow-screen layout without horizontal overflow.
The real staging checklist is still pending.

## Staging acceptance checklist (M2 with M4)

Use one seller, two buyers and another helper. Use a dedicated demo listing.

- [ ] A signed-out visitor sees login guidance and no actionable forms.
- [ ] Buyer A starts a campaign. Buyer B can view it and start their own.
- [ ] A helper lowers the price once; a repeated attempt produces feedback.
- [ ] Current, starting and floor prices and helper names are visible.
- [ ] Countdown updates; expired history remains, without a help button.
- [ ] Buyer A submits an offer; the seller sees it within 15 seconds or on Refresh.
- [ ] With Buyer B also offering, the seller can answer each buyer separately.
- [ ] Counter/reject has visible feedback; rejection allows another offer.
- [ ] Seller acceptance creates one order and opens seller order detail, not payment.
- [ ] Buyer acceptance of a seller counter opens payment at the negotiated amount.
- [ ] A buyer revisiting an accepted thread sees an order link, not a new-offer form.
- [ ] The existing order-detail payment action handles paid/cancelled orders correctly.
- [ ] Network failure is visible and Retry works; double-clicking cannot duplicate a request.
- [ ] Change product, log out or leave the page during a request: no stale state/navigation.
- [ ] Narrow-screen forms fit and keyboard focus is visible.

## Integration boundaries

- A campaign itself reserves no stock and is not checkout. An accepted offer
  creates the negotiated order using the existing backend order service.
- Amounts use the payment UI's existing `S$` label; the UI does not recompute
  an order total or send a price override to payment.
- Helpers' API entries expose display names, not IDs. The UI remembers confirmed
  help within this component session; the server remains authoritative after reload.
- Timestamp responses currently use zone-less `LocalDateTime`. The UI treats these
  as UTC for the existing default-UTC container deployment, matching its `...Z`
  creation payloads. Verify the countdown against staging's actual expiry. A server
  configured to a different JVM time zone needs an explicit offset-aware backend
  contract; that backend change is outside this frontend PR.
