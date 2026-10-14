# Demo P0: cart entry points

Owner: YUAN CHENG (M2). Scope: the two missing frontend controls, using existing APIs.

- The product page renders `AddToCartAction` before reviews/bargaining. One click
  calls `cart.add(product.id, 1)`, shows loading/duplicate-click protection, and
  then success plus a **View cart** link or a readable backend error.
- Guests go to login. Own listings and visibly unavailable listings are disabled.
  The current product DTO exposes total stock, so the backend remains responsible
  for rejecting reserved/unavailable stock and excessive accumulated quantities.
- The logged-in app navigation exposes **Cart** on every route, using the existing
  authenticated `/cart` route. Switching product routes clears the previous listing
  and discards old detail responses before a purchase can target it.

## Local verification

```sh
cd frontend
npm run test:bargain
npm run test:admin
npm run test:cart
npm run build
```

On 14 October 2026, all **69 tests** passed (28 bargaining, 26 admin, 15 cart),
and the production build passed. The cart suite uses headless Vue rendering to
invoke the actual button handler and inspect its states/links. It also checks
the API request shape, page/navigation wiring and stale product requests.
The existing frontend CI job runs the cart tests. Workflow `actionlint` and
`git diff --check` passed. Existing bundle-size/browser-data warnings remain.

No browser automation, real staging purchase, database reset or account mutation
was performed. The checks above do not claim a completed end-to-end demo run.

## Shared staging acceptance (pending)

- [ ] M4 confirms the global Cart link and product button placement.
- [ ] As a buyer, open another seller's active listing and click **Add to cart**.
- [ ] Open Cart through the success link, then repeat through global navigation.
- [ ] Check product, seller, quantity and the existing checkout/order navigation.
- [ ] Own listing cannot be added; insufficient stock produces a readable refusal.

Out of scope: held-amount display and the three order/payment timezone fixes (WP3),
currency/condition/seller-name polish (#72), and background/media choices.
This change does not complete or close #72.
