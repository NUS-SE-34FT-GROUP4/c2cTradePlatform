# C2C SecTrade

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

```bash
docker compose up -d --build
```

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
