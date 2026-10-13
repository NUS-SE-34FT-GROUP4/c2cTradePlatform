# Seller credit (#73)

The account dropdown, home account summary and listing cards display **Credit: n/100**.
An account with no completed sales displays **Unrated**, represented by `score: null`,
not zero. The badge tooltip shows completed-sale and review counts.

## Rule

For one seller, let C be the number of orders currently in COMPLETED and R the number of
reviews attached to those orders. Let A be the mean `seller_rating` (1–5), or the neutral
value 3 when completed sales exist but no buyer has reviewed them yet.

`score = round(16 × A + min(C, 20))`

Seller ratings supply up to 80 points and completed sales supply up to 20 points.
Examples: no sales → Unrated; one unreviewed sale → 49; one five-star sale → 81;
20 five-star sales → 100. A lower rating can reduce the score. The completed-sale count
is order-based, not item-quantity-based. This is a transparent project reputation rule,
not a financial credit rating or a guarantee about a seller.

Only the order's seller is credited. Pending, paid, shipped, cancelled, expired and refunded
orders do not count. Product ratings do not affect seller credit. The review table already
has one-review-per-order uniqueness, and order lines are not joined into the aggregate,
so neither multiple lines nor quantity inflate it.

`CreditMapper` aggregates a batch of seller IDs in one query; `CreditService` computes the
score. `ProductService.convertToDTOList` loads all sellers' credit together, avoiding a
new credit query per listing. There is no stored score, scheduled recomputation or cache:
new committed reviews appear on the next API read, including page refresh/account-menu
opening. This change does not promise a live push to an already-open page.

API additions (existing fields preserved):

- `GET /api/users/me`: `credit: {score, completedOrders, reviewCount}`.
- Product DTOs, including `GET /api/products`: `sellerCredit` with the same shape.
- `GET /api/recommendations/products/{id}/similar?strategy=same-category|most-recent`:
  optional algorithm selection; unknown names use the default. Limits are capped at 24;
  nonpositive limits return an empty array.

Boundary rules, batching, live review updates, seller isolation and order-line counts are
covered by `CreditServiceTest` and `CreditRecommendationIT`. No schema migration is needed.
