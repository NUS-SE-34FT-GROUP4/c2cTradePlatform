# C2C SecTrade

[![CI](https://github.com/NUS-SE-34FT-GROUP4/c2cTradePlatform/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/NUS-SE-34FT-GROUP4/c2cTradePlatform/actions/workflows/ci.yml)

A campus-oriented consumer-to-consumer marketplace for second-hand goods, built for the
**SWE5006 Practice Module — Designing Modern Software Systems** (Team 4).

The system is a modular monolithic Spring Boot backend with a Vue 3 single-page frontend, delivered
across five two-week sprints.

## Sprint 1 Scope

This increment delivers the shared technical foundation plus the first two feature slices:

- **User registration and login** — credential validation, BCrypt password hashing, stateless JWT
  authentication with a refresh endpoint, and a captcha challenge on registration and login.
- **Item browsing** — an item list with category, price, condition and location filtering, and an
  item detail page, both served from seeded catalogue data.

Deferred to later sprints: item publishing and editing, keyword search, media upload, cart, orders,
payment, bargaining, real-time chat, reviews, credit scoring and recommendation.

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 17 |
| Backend | Spring Boot 3.2.3, Spring Security, MyBatis |
| Frontend | Vue 3, Vue Router, Pinia, Axios |
| Database | MySQL 8.0 |
| Cache | Redis 7 (captcha store) |
| Auth | JWT (jjwt) + BCrypt |
| Packaging | Docker, Docker Compose, Nginx |

## Running Locally

Requires Docker and Docker Compose.

The backend has no built-in JWT signing key, so create a local `.env` once
(it is git-ignored; `make up` does this for you if it is missing):

```bash
echo "JWT_SECRET=$(openssl rand -base64 64 | tr -d '\n')" > .env
docker compose up -d --build
```

Running the backend outside Docker (e.g. from the IDE) needs the same
`JWT_SECRET` environment variable.

| Service | URL |
|---|---|
| Frontend | http://localhost |
| Backend API | http://localhost:8080 |
| Health check | http://localhost:8080/actuator/health |

The database schema and seed data in `init.sql` are applied automatically on first start. To reset:

```bash
docker compose down -v && docker compose up -d --build
```

## Seeded Accounts

All seeded accounts use the password `admin123`.

| Username | Role |
|---|---|
| `admin` | Administrator |
| `seller_lvl1` … `seller_lvl5` | Ordinary user (seller of the seeded items) |

Twelve catalogue items across the electronics, books and clothing categories are seeded so that the
item list and detail pages have data to render.

## API Endpoints (Sprint 1)

| Method | Endpoint | Auth | Description |
|---|---|---|---|
| `POST` | `/api/auth/register` | — | Register a new account |
| `POST` | `/api/auth/login` | — | Authenticate and receive a JWT |
| `GET` | `/api/captcha/**` | — | Issue a captcha challenge |
| `GET` | `/api/products` | — | List items, with optional filters |
| `GET` | `/api/products/{id}` | — | Item detail |
| `GET` | `/api/users/me` | JWT | Current user profile |
| `GET` | `/api/users/{username}` | JWT | Look up a user |
| `PUT` | `/api/users/display-name` | JWT | Update display name |

## Project Structure

```
src/main/java/sg/edu/nus/iss/c2csectrade/
├── config/          # Spring Security, MyBatis, Redis, captcha configuration
├── controller/      # REST endpoints
├── security/        # JWT filter, token provider, UserDetails
├── service/         # Business logic
├── mapper/          # MyBatis mappers
├── entity/          # Domain entities
├── dto/, payload/   # Request and response models
└── exception/       # Global exception handling

frontend/src/
├── views/           # Login, Register, Forgot password, Home, Product detail
├── store/           # Pinia auth store
├── api/             # Axios service layer
└── router/          # Vue Router with auth guard
```

## Database integration tests

Run `mvn test` for unit tests, or `mvn verify` with Docker running for the full suite.
`CheckoutIT` starts MySQL 8 through Testcontainers and loads the actual root `init.sql`
(no duplicated test schema). It exercises JWT-protected checkout through Spring MVC,
real MyBatis XML, price snapshots, seller splitting and transaction rollback. Only file
storage is mocked; repositories and transaction management are real. A temporary JWT
key is generated per test JVM. Docker is required and missing Docker fails the suite.
CI runs `mvn -B verify` and publishes both Surefire and Failsafe reports.

## Post-transaction reviews (#42)

Ported from the supplied `c2csectrade-main.zip`: review DTOs/entity, the buyer/completed-order
checks in `ReviewServiceImpl`, review persistence, and `ProductReviews.vue`. Adapted to this
repository's Long IDs, `oms_order` schema, JWT client and shared file storage. Credit scoring
and recommendation side effects remain out of scope for Sprint 3.

An order can be reviewed once. For a multi-item order the buyer chooses which purchased
item to rate; it is never silently assigned to the first line. Only COMPLETED orders qualify.
Item and seller ratings are independent (1–5), comments use `HtmlSanitizer.clean`, and up to
five HTTP(S) image URLs are stored as JSON. Uploads use `/api/reviews/images` (images only,
5MB each). Anonymous responses omit buyer ID, avatar and order ID as well as the name.

The buyer's completed order offers **Write a review**; reviews appear on the chosen product's
page. The database's unique order key rejects duplicate concurrent submissions.

For an existing database, apply `scripts/migrations/003_reviews.sql` before deploying the
new application, e.g. `docker compose exec -T mysql sh -c 'exec mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" trade' < scripts/migrations/003_reviews.sql`.
The staging workflow applies this additive migration automatically before starting the new backend.
Do not rerun the destructive `init.sql` on an existing database. Fresh installations already
include the review table in `init.sql`. Run `mvn verify` to exercise review endpoints and
constraints against MySQL; payment and fulfilment implementation remain separate issues.
