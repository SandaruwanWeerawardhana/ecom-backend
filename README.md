# E-Commerce Clothing Platform — Backend

> A modular-monolith, event-driven backend for a full-stack clothing e-commerce platform, covering online storefront, reseller marketplace, and in-store Point-of-Sale (POS).

[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![MySQL](https://img.shields.io/badge/MySQL-8-blue.svg)](https://www.mysql.com/)
[![Docker](https://img.shields.io/badge/Docker-ready-2496ED.svg)](https://www.docker.com/)
[![Build](https://img.shields.io/badge/build-Maven-C71A36.svg)](https://maven.apache.org/)

---

## Overview

This repository contains the backend service for a clothing e-commerce ecosystem. It is built as a **modular monolith** — a single deployable Spring Boot application internally split into strongly-isolated business modules, each owning its own database and communicating asynchronously over RabbitMQ.

**What it does**

- Powers an online storefront: product catalog, cart, checkout, orders, and delivery tracking.
- Runs a reseller marketplace: reseller onboarding, wallet, withdrawals, dashboards, and reseller-specific carts/orders.
- Drives an in-store POS: cashiers, shifts, terminals, POS carts/orders, and product sync.
- Handles admin operations: role/permission management, reporting dashboards, and audit trails.

**The problem it solves**

Running a storefront, a reseller channel, and physical retail on separate stacks causes duplicated catalog data, inventory drift, and reconciliation pain. This backend unifies catalog, inventory, payment, and delivery behind one codebase while keeping module boundaries clean via per-module databases and message-based integration — so each domain can evolve (and later be extracted into a microservice) with minimal coupling.

**Who it is for**

- Backend/platform engineers building or maintaining the commerce platform.
- Frontend and POS client teams consuming the REST APIs.
- DevOps engineers deploying and operating the service.

**Main objectives**

- Clear domain isolation (module per bounded context, database per module).
- Asynchronous, resilient inter-module communication (events + RPC over AMQP, with retry and dead-letter queues).
- Secure, stateless authentication with fine-grained authorization.
- Production-readiness: caching, pagination, health checks, and containerized deployment.

---

## Features

### Authentication & Authorization
- JWT-based stateless authentication (access + refresh tokens).
- Cookie-based login/logout variants alongside header-based tokens.
- User registration with email verification tokens.
- Password reset (forgot-password / reset-password) flow.
- Resend email verification.
- Peppered password hashing (`PepperedPasswordEncoder`).
- Role-based and permission-based authorization (roles, permissions, user-role and user-permission mappings).

### User & Admin Management
- Admin user creation, role assignment, and deletion (event-driven).
- Role management (create roles, assign permissions).
- Permission initialization and assignment.
- Admin activity logs, audit trail, and notifications.
- Login history tracking.

### Product Management
- Product CRUD with categories and attributes.
- Product image management (upload + storage).
- Product search (Elasticsearch integration; index: products/orders/users).
- Pagination and filtering (configurable default/max page size).
- Product caching (Caffeine) and category caching.

### Inventory Management
- Stock tracking and stock-status updates.
- Stock reservation with reserve-timeout window.
- Low-stock alerts and configurable low-stock threshold.
- Stock decrease/decrement on order events.

### Cart & Checkout
- Customer cart management.
- Reseller cart and POS cart variants.
- Checkout flow bridging cart → payment → order via events.
- Redis-backed cart cache management endpoints.

### Orders
- Customer order placement and retrieval.
- Order tracking.
- Admin order management.
- POS and reseller order flows.
- Business rules: cancellation window, auto-cancel of pending orders.

### Payments
- Checkout initiation.
- Payment method management, configuration, and per-method fees.
- OnePay payment gateway integration with callback handling.
- Payment ↔ order status synchronization via events.

### Delivery & Couriers
- Courier and courier-rate management.
- Shipping cost calculation and shipment creation (event-driven).
- Order status → courier tracking updates.
- Delivery completion events.

### Reseller Marketplace
- Reseller onboarding and admin reseller management.
- Reseller bank accounts.
- Reseller wallet and withdrawal management.
- Reseller dashboard metrics (RPC lookups).
- Reseller-specific product listing, cart, and orders.

### Point of Sale (POS)
- POS terminals, shifts, and cashiers.
- POS carts, orders, customers, and products.
- POS product sync with dedicated queues, retry (exponential backoff 1s/5s/30s), and dead-letter queues.
- POS inventory deduction and confirmation.
- POS search analytics.

### Promotions
- Promotion management.

### Reviews
- Customer product reviews with review images.

### Reporting & Dashboards
- Admin dashboard reporting.
- Sales reports and item reports.
- POS analytics.

### Notifications
- SMS integration (Dialog e-SMS) with delivery report handling.

### Platform
- OpenAPI/Swagger UI documentation.
- Spring Boot Actuator health/metrics/Prometheus endpoints.
- Response compression.
- Flyway database migrations.

---

## Tech Stack

| Category            | Technology                                                                 |
|---------------------|----------------------------------------------------------------------------|
| Language            | Java 21                                                                     |
| Backend Framework   | Spring Boot 4.0 (Web MVC, Data JPA, Security, Validation, Actuator, AMQP)   |
| Database            | MySQL (database-per-module)                                                 |
| ORM / Migrations    | Hibernate / JPA, Flyway                                                     |
| Authentication      | Spring Security, JWT (jjwt 0.12.5), peppered password encoding             |
| Messaging           | RabbitMQ (Spring AMQP) — events + RPC, DLQ, retry queues                    |
| Caching             | Redis (Lettuce), Caffeine (in-memory)                                       |
| Search              | Elasticsearch 8.11 (elasticsearch-java client)                             |
| Object Storage      | AWS S3 (AWS SDK v2, presigned URLs)                                         |
| DTO Mapping         | MapStruct 1.5.5                                                             |
| Boilerplate         | Lombok                                                                      |
| API Docs            | SpringDoc OpenAPI (Swagger UI) 2.7.0                                        |
| SMS                 | Dialog e-SMS (Adeonatech SmsAPI 1.0.8)                                      |
| Payments            | OnePay gateway                                                             |
| Build Tool          | Maven (Maven Wrapper included)                                              |
| Containerization    | Docker, Docker Compose                                                      |
| CI/CD               | GitHub Actions (deploy on push to `release/backend`)                        |
| Testing             | Spring Boot Test, Spring Security Test, Spring Rabbit Test                  |
| Frontend            | Not part of this repository (separate client; `FRONTEND_BASE_URL` config)  |

---

## Architecture

### Overall

The application is a **modular monolith** following a **layered architecture** per module. Each business module is a bounded context that:

- Owns its **own MySQL database** (see [Database](#database)).
- Exposes REST controllers for synchronous client traffic.
- Communicates with other modules **asynchronously** over RabbitMQ using:
  - **Events** (fire-and-forget domain events, e.g. `product.created`, `stock.updated`, `order.delivery.completed`).
  - **RPC** (request/reply queues for cross-module lookups, e.g. `customer.lookup.request`, `reseller.wallet.summary.lookup.request`).
- Reliability is built in via **dead-letter queues** and **retry queues** with exponential backoff (notably in the POS module).

### Layered pattern (per module)

```
Controller  →  Service (interface + impl)  →  Repository  →  Entity
     │              │
     │              ├── Consumer   (RabbitMQ listeners / event handlers)
     │              ├── RPC        (request-reply handlers)
     │              └── Events     (domain event payloads)
     └── DTO (request/response) ←→ MapStruct mapper
```

### Data flow (example: online checkout)

1. Client calls `POST /api/v1/checkout` (Payment module).
2. Cart items are fetched/cleared via RPC (`cart.items.checkout.request`).
3. Payment request is created; OnePay is invoked; callback hits `/api/v1/payments/callback`.
4. On success, a `payment.status.updated.order` event is published → Orders module creates/updates the order.
5. Orders emits stock-decrease and shipment-create requests → Inventory and Delivery react.
6. Delivery emits status/tracking events consumed back by Orders and Payment.

---

## Folder Structure

```
beyos_backend/
├── src/
│   ├── main/
│   │   ├── java/org/psint/beyosclothing/
│   │   │   ├── BeyosclothingApplication.java   # Spring Boot entry point
│   │   │   ├── common/                         # Shared constants, DTOs, enums, utils, services
│   │   │   ├── config/                         # App-wide config (e.g. CacheConfig)
│   │   │   ├── core/                           # Cross-cutting concerns
│   │   │   │   ├── audit/                       # Auditing support
│   │   │   │   ├── config/                      # Core configuration
│   │   │   │   ├── exception/                   # GlobalExceptionHandler
│   │   │   │   ├── security/                    # SecurityConfig, JWT, filters, password encoder
│   │   │   │   └── util/                        # Core utilities
│   │   │   ├── shared/                         # Shared DTOs across modules
│   │   │   └── modules/                        # Business modules (bounded contexts)
│   │   │       ├── auth/                        # Authentication, users, tokens
│   │   │       ├── admin/                       # Admin, roles, permissions
│   │   │       ├── users/                       # User domain
│   │   │       ├── customers/                   # Customer profiles, orders, reviews
│   │   │       ├── products/                    # Catalog, categories, attributes, images
│   │   │       ├── inventory/                   # Stock management
│   │   │       ├── cart/                        # Cart + Redis cache management
│   │   │       ├── orders/                      # Orders (customer/admin)
│   │   │       ├── payment/                     # Checkout, payment methods, OnePay callbacks
│   │   │       ├── delivery/                    # Couriers, rates, shipments
│   │   │       ├── promotions/                  # Promotions
│   │   │       ├── returns_refunds/             # Returns & refunds
│   │   │       ├── resellers/                   # Reseller marketplace, wallet, withdrawals
│   │   │       ├── pos/                         # Point of Sale
│   │   │       ├── report/                      # Dashboards & reports
│   │   │       └── sms/                         # SMS notifications
│   │   └── resources/
│   │       ├── application.yml                  # Base config
│   │       ├── application-dev.yml              # Dev profile
│   │       ├── application-prod.yml             # Prod profile (multi-datasource)
│   │       └── db/migration/                    # Flyway SQL migrations
│   └── test/                                   # Tests
├── Dockerfile                                  # Multi-stage build
├── docker-compose.yml                          # App + RabbitMQ + Redis
├── pom.xml                                      # Maven build & dependencies
├── mvnw / mvnw.cmd                             # Maven Wrapper
└── .github/workflows/deploy-backend.yml        # CI/CD deploy pipeline
```

A typical module (e.g. `auth`) contains: `controller/`, `service/` (+ `service/impl`), `repository/`, `entity/`, `dto/` (request/response), `consumer/`, `rpc/`, `events/`, and `document/`.

---

## Database

**Engine:** MySQL, using a **database-per-module** strategy. Hibernate runs in `ddl-auto: validate` mode; schema is owned by SQL migrations.

**Databases (per module):**

| Database              | Module        |
|-----------------------|---------------|
| `beyos_auth_db`       | Auth / Users  |
| `beyos_admin_db`      | Admin         |
| `beyos_customers_db`  | Customers     |
| `beyos_product_db`    | Products      |
| `beyos_inventory_db`  | Inventory     |
| `beyos_cart_db`       | Cart          |
| `beyos_order`         | Orders        |
| `beyos_payment`       | Payment       |
| `beyos_delivery`      | Delivery      |
| `beyos_promo_db`      | Promotions    |
| `beyos_reseller`      | Resellers     |
| `beyos_pos`           | POS           |

**Main tables (from Flyway migrations):**

- **Auth:** `users`, `user_role`, `user_permission`, `role_permission`, `permissions`, `email_verification_tokens`, `password_reset_tokens`, `login_history`.
- **Customers:** `customers`, `addresses`, `customer_reviews`, `review_images`.
- **Admin:** `admin`, `admin_roles`, `admin_role_permissions`, `admin_activity_logs`, `admin_audit_trail`, `admin_notifications`.

**Relationships (summary):**

- A `user` has many roles (`user_role`) and permissions (`user_permission`); roles map to permissions (`role_permission`).
- A `customer` has many `addresses` and many `customer_reviews`; a review has many `review_images`.
- An `admin` has roles (`admin_roles`) linked to permissions (`admin_role_permissions`); admin actions are recorded in `admin_activity_logs` and `admin_audit_trail`.

> Migrations live in `src/main/resources/db/migration`. Flyway is disabled in the base config (`spring.flyway.enabled: false`) and applied per-datasource in the module database configuration.

---

## API Documentation

Interactive docs are available at **`/swagger-ui.html`** (OpenAPI JSON at `/v3/api-docs`) once the app is running.

Base paths discovered across the modules (all served from `http://<host>:8080`):

| Method(s)                | Base Endpoint                              | Description                                   | Authentication |
|--------------------------|--------------------------------------------|-----------------------------------------------|----------------|
| POST                     | `/api/v1/auth/register`                     | Register a new user                           | Public         |
| POST                     | `/api/v1/auth/login`, `/login/cookie`       | Authenticate (token / cookie)                 | Public         |
| GET                      | `/api/v1/auth/me`                           | Current user profile                          | JWT            |
| POST                     | `/api/v1/auth/logout`, `/logout/cookie`     | Logout                                        | JWT            |
| POST                     | `/api/v1/auth/refresh-token`                | Refresh access token                          | Refresh token  |
| POST                     | `/api/v1/auth/forgot-password`, `/reset-password` | Password reset flow                     | Public         |
| POST                     | `/api/v1/auth/verify-email`, `/resend-verification` | Email verification                    | Public         |
| CRUD                     | `/products`, `/products/categories`, `/products/attributes`, `/products/images` | Product catalog | JWT / Role |
| CRUD                     | `/api/v1/inventory`                         | Inventory & stock                             | JWT / Role     |
| CRUD                     | `/api/v1/cart`, `/api/admin/cache`          | Cart & cache management                       | JWT            |
| CRUD                     | `/api/v1/orders`, `/api/v1/orders/{uuid}/tracking` | Customer orders & tracking             | JWT            |
| CRUD                     | `/api/v1/admin/orders`                      | Admin order management                        | JWT / Admin    |
| CRUD                     | `/api/v1/customers`, `/api/v1/customers/orders`, `/reviews` | Customers, orders, reviews     | JWT            |
| POST/GET                 | `/api/v1/checkout`, `/api/v1/payments/callback` | Checkout & payment callback               | JWT / Gateway  |
| CRUD                     | `/api/v1/payment-methods` (+ `/config`, `/fees`) | Payment methods, config, fees            | JWT / Admin    |
| CRUD                     | `/api/v1/couriers`, `/api/v1/courier-rates` | Courier & rate management                     | JWT / Admin    |
| CRUD                     | `/api/v1/resellers` (+ `/bank-accounts`, `/cart`, `/orders`, `/products`, `/wallet`, `/withdrawals`, `/dashboard`) | Reseller marketplace | JWT / Role |
| CRUD                     | `/api/v1/admin/resellers`                   | Admin reseller management                     | JWT / Admin    |
| CRUD                     | `/api/v1/pos/terminals`, `/shifts`, `/cashiers`, `/carts`, `/orders`, `/customers`, `/products` | POS operations | JWT / Role |
| GET                      | `/api/v1/admin/pos/analytics`               | POS analytics                                 | JWT / Admin    |
| CRUD                     | `/promotions`                               | Promotions                                    | JWT / Admin    |
| GET                      | `/api/v1/admin/reports/dashboard`, `/sales`, `/items` | Reporting dashboards                  | JWT / Admin    |
| CRUD                     | `/admin`, `/admin/roles`, `/api/admin/permissions` | Admin, roles, permissions              | JWT / Admin    |
| POST/GET                 | `/api/v1/sms`, `/api/sms`                    | SMS send & delivery reports                   | JWT / Admin    |
| GET                      | `/actuator/health`, `/metrics`, `/prometheus` | Operational endpoints                       | When authorized|

> The API exposes ~294 endpoints (138 GET, 81 POST, 30 PUT, 30 DELETE, 15 PATCH) across 49 controllers. See Swagger UI for the exhaustive, always-current list.

---

## Installation

### Prerequisites

- Java 21 (JDK)
- Maven (or use the bundled wrapper `./mvnw`)
- MySQL 8
- Redis 7
- RabbitMQ 3.13 (with management plugin)
- (Optional) Elasticsearch 8.11, AWS S3 credentials, OnePay + Dialog e-SMS credentials

### Steps

1. **Clone the repository**
   ```bash
   git clone <repository-url>
   cd beyos_backend
   ```

2. **Provide environment variables** — create a `.env` file in the project root (see [Environment Variables](#environment-variables)). The app imports it via `spring.config.import: optional:file:./.env`.

3. **Provision infrastructure** — start MySQL, Redis, and RabbitMQ (Docker Compose provides Redis + RabbitMQ; see [Deployment](#deployment)). The per-module databases are created automatically (`createDatabaseIfNotExist=true`).

4. **Run database migrations** — Flyway migrations run per-datasource on startup (managed in the module database configuration). No manual step is required for a normal boot.

5. **Build**
   ```bash
   ./mvnw clean package
   ```

6. **Start the application**
   ```bash
   ./mvnw spring-boot:run
   ```
   The API starts on `http://localhost:8080` (default `dev` profile).

> **Windows:** use `mvnw.cmd` instead of `./mvnw`. Convenience scripts `run.bat` and `run-with-java21.*` are also present.

---

## Environment Variables

Provided via `.env` (git-ignored) or the process environment. **Do not commit real secrets.**

| Variable                 | Description                                        | Required |
|--------------------------|----------------------------------------------------|----------|
| `SPRING_PROFILES_ACTIVE` | Active profile (`dev` / `prod`)                    | No (default `dev`) |
| `SERVER_PORT`            | HTTP port                                          | No (default `8080`) |
| `DB_HOST`                | MySQL host                                         | Yes      |
| `DB_USERNAME`            | MySQL username                                     | Yes      |
| `DB_PASSWORD`            | MySQL password                                     | Yes      |
| `REDIS_HOST`             | Redis host                                         | Yes      |
| `REDIS_PORT`             | Redis port                                         | Yes      |
| `REDIS_PASSWORD`         | Redis password                                     | Yes (env-dependent) |
| `RABBITMQ_HOST`          | RabbitMQ host                                      | Yes      |
| `RABBITMQ_PORT`          | RabbitMQ AMQP port                                 | Yes      |
| `RABBITMQ_USERNAME`      | RabbitMQ username                                  | Yes      |
| `RABBITMQ_PASSWORD`      | RabbitMQ password                                  | Yes      |
| `RABBITMQ_VHOST`         | RabbitMQ virtual host                              | No (default `/`) |
| `JWT_SECRET`             | JWT signing secret                                 | Yes      |
| `AWS_S3_BUCKET`          | S3 bucket name for uploads                         | Yes (for S3) |
| `AWS_REGION`             | AWS region                                         | No (default `eu-north-1`) |
| `AWS_ACCESS_KEY` / `AWS_ACCESS_KEY_ID` | AWS access key                       | Yes (for S3) |
| `AWS_SECRET_KEY` / `AWS_SECRET_ACCESS_KEY` | AWS secret key                   | Yes (for S3) |
| `ELASTICSEARCH_HOST`     | Elasticsearch host                                 | No (default `localhost`) |
| `ELASTICSEARCH_PORT`     | Elasticsearch port                                 | No (default `9200`) |
| `ELASTICSEARCH_USERNAME` | Elasticsearch username                             | No       |
| `ELASTICSEARCH_PASSWORD` | Elasticsearch password                             | No       |
| `ONEPAY_APP_ID`          | OnePay application ID                              | Yes (for payments) |
| `ONEPAY_HASH_SALT`       | OnePay hash salt                                   | Yes (for payments) |
| `ONEPAY_APP_TOKEN`       | OnePay app token                                   | Yes (for payments) |
| `ONEPAY_BASE_URL`        | OnePay API base URL                               | Yes (for payments) |
| `ESMS_USERNAME`          | Dialog e-SMS username                             | Yes (for SMS) |
| `ESMS_PASSWORD`          | Dialog e-SMS password                             | Yes (for SMS) |
| `ESMS_SOURCE_ADDRESS`    | SMS sender ID                                     | Yes (for SMS) |
| `ESMS_URL_MESSAGE_KEY`   | e-SMS API key                                     | Yes (for SMS) |
| `ESMS_BASE_URL`          | e-SMS base URL                                    | Yes (for SMS) |
| `FRONTEND_BASE_URL`      | Frontend origin (links, redirects)                | Yes      |
| `APP_BASE_URL`           | Public base URL for generated upload links        | No (default `http://localhost:8080`) |
| `CORS_ALLOWED_ORIGINS`   | Comma-separated allowed origins                   | No       |
| `CORS_ALLOWED_METHODS`   | Allowed HTTP methods                              | No       |
| `CORS_ALLOWED_HEADERS`   | Allowed headers                                   | No       |
| `CORS_ALLOW_CREDENTIALS` | Allow credentials flag                            | No       |

---

## Running the Project

| Task            | Command                                                        |
|-----------------|---------------------------------------------------------------|
| Development     | `./mvnw spring-boot:run` (profile `dev`)                       |
| Production run  | `SPRING_PROFILES_ACTIVE=prod java -jar target/*.jar`          |
| Build (jar)     | `./mvnw clean package`                                         |
| Build (skip tests) | `./mvnw clean package -DskipTests`                         |
| Test            | `./mvnw test`                                                 |
| Docker build    | `docker build -t backend:latest .`                            |
| Full stack up   | `docker compose up -d`                                         |

> **Lint / Formatting:** No dedicated lint/format plugin is configured in `pom.xml` — *Not implemented*. The project targets **SonarQube best practices** for code quality (per project conventions).

---

## Screenshots

_No UI screenshots — this is a backend-only service._

For a visual API reference, run the app and open **Swagger UI** at `http://localhost:8080/swagger-ui.html`.

---

## Authentication Flow

**Registration**
1. `POST /api/v1/auth/register` creates a user and issues an email-verification token (`email_verification_tokens`).
2. `POST /api/v1/auth/verify-email` (or `resend-verification`) confirms the account.

**Login**
1. `POST /api/v1/auth/login` validates credentials (peppered password hashing) and returns a signed **access token** (15 min) and **refresh token** (7 days).
2. `POST /api/v1/auth/login/cookie` issues the tokens as cookies instead.
3. Each request carries the access token; a JWT filter validates it and populates the security context.

**Refresh**
- `POST /api/v1/auth/refresh-token` exchanges a valid refresh token for a new access token. Login events are recorded in `login_history`.

**Password reset**
- `POST /api/v1/auth/forgot-password` issues a reset token (`password_reset_tokens`); `POST /api/v1/auth/reset-password` consumes it.

**Roles & permissions**
- Authorization is enforced through roles and permissions (`user_role`, `user_permission`, `role_permission`), plus a separate admin permission model (`admin_roles`, `admin_role_permissions`). Endpoints are guarded by Spring Security method/URL rules.

**Token settings** (`app.jwt`): issuer `beyos-clothing-backend`, audience `beyos-clothing-app`, HS-signed with `JWT_SECRET`.

---

## Error Handling

- **Validation:** Bean Validation (`spring-boot-starter-validation`) on request DTOs; binding errors are included in error responses (`include-binding-errors: always`).
- **Centralized exception handling:** `core/exception/GlobalExceptionHandler` (plus a module-specific `ProductExceptionHandler`) translates exceptions into consistent JSON responses.
- **Safe error output:** stack traces and exception classes are never exposed (`include-stacktrace: never`, `include-exception: false`); human-readable messages are included.
- **HTTP status codes:** standard semantics — `200/201` success, `400` validation, `401` unauthenticated, `403` unauthorized, `404` not found, `409` conflict, `5xx` server errors.

---

## Performance Optimizations

- **Caching:** Redis (distributed) + Caffeine (in-memory) for products, categories, inventory, and user-details caches; default TTL 1 hour.
- **Pagination:** default page size 20, max 100.
- **Search:** Elasticsearch indices for products/orders/users.
- **Batching:** Hibernate JDBC batch size 20 with ordered inserts/updates.
- **Connection pooling:** HikariCP pools per datasource; Lettuce Redis connection pool.
- **Compression:** HTTP response compression for JSON/XML/HTML/plain text.
- **Async offloading:** heavy cross-module work handled via RabbitMQ consumers rather than blocking request threads.
- **DB indexing:** dedicated optimization migration (`database-optimization-product-cards.sql`) for product-card queries.
- **`open-in-view: false`:** avoids lazy-loading over the request lifecycle.

---

## Security

- **Authentication:** stateless JWT (access + refresh), configurable via `app.jwt`.
- **Authorization:** Spring Security with role- and permission-based access control; separate admin permission model.
- **Password hashing:** peppered encoder (`PepperedPasswordEncoder`) on top of a strong hashing algorithm.
- **Input validation:** Bean Validation on all request DTOs.
- **SQL injection:** mitigated via JPA/Hibernate parameterized queries.
- **CORS:** configurable allowed origins/methods/headers/credentials (`CORS_*` env vars).
- **Transport / uploads:** file uploads capped (10 MB) with an allow-list of extensions; S3 access via time-limited presigned URLs.
- **Secrets management:** all credentials injected via environment (`.env` is git-ignored).
- **Operational visibility:** Actuator health details only shown `when-authorized`.
- **CSRF:** REST + JWT (stateless) design; CSRF protection is *Not applicable* for token auth (cookie-based flows should be reviewed before enabling in production).
- **Rate limiting:** *Not implemented* — consider adding at the gateway/reverse-proxy layer.
- **XSS:** APIs return JSON; output encoding is the responsibility of consuming clients.

---

## Deployment

### Docker Compose (local / single-host)

`docker-compose.yml` provisions the app plus RabbitMQ and Redis:

```bash
docker compose up -d
```

- App: `http://localhost:8080`
- RabbitMQ AMQP: `15672` (host) → management UI on `25672`
- Redis: `6379`

The `Dockerfile` uses a **multi-stage build** (Maven build stage → `eclipse-temurin:21-jre` runtime) with a container health check on `/actuator/health` and `MaxRAMPercentage=75`.

### CI/CD (GitHub Actions)

`.github/workflows/deploy-backend.yml` deploys on push to **`release/backend`**:

1. Builds the Docker image.
2. Saves and SCPs the image tarball to the VPS.
3. Loads the image and restarts the `backend` service via `docker compose` over SSH.

### Target environments

- **VPS (Contabo):** the `prod` profile targets a containerized MySQL and VPS host. An Nginx reverse-proxy config is referenced (`nginx-beyos-production.conf`, git-ignored) — *provide separately*.
- **AWS:** S3 is used for object storage; no other AWS deployment config is present.

---

## Future Improvements

- Add API rate limiting (gateway or filter-based).
- Introduce automated code formatting/linting in the build.
- Expand automated test coverage (unit + integration across modules).
- Implement the returns & refunds module endpoints (module scaffold present).
- Add centralized configuration/secrets management (e.g. Vault) for production.
- Provide a committed `.env.example` template.
- Consider extracting high-load modules (POS, Products) into standalone services — the database-per-module boundary already supports this.
- Add distributed tracing and log aggregation.

---

## Contributing

1. Fork the repository and create a feature branch: `git checkout -b feat/your-feature`.
2. Follow the code conventions: clear, descriptive names; comments for complex logic; no dead code or unused imports; SonarQube best practices.
3. Keep changes within module boundaries; prefer events/RPC for cross-module interaction.
4. Ensure `./mvnw test` passes.
5. Commit using conventional prefixes (`feat:`, `fix:`, `refactor:`, …) and open a pull request against the default branch.

---

## License

_No license file detected — **Not implemented**. Add a `LICENSE` file (e.g. MIT / Apache-2.0) to define usage terms._

---

