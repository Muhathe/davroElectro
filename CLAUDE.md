# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Davron Elektro: a backend for an electrical-goods store. It covers POS, warehouse (sklad), cash, debts, expenses, finance forecasting, double-entry accounting and an AI assistant. Stack: Java 17, Spring Boot 3.3.5, PostgreSQL, JWT (jjwt 0.12), Apache POI (Excel import), springdoc/Swagger, Lombok. Root package: `univer.university`. Code comments, log messages and user-facing error messages are in Uzbek (Latin script); keep new ones in Uzbek too.

## Commands

There is no Maven wrapper, so use a system `mvn`.

```bash
docker compose up -d          # local PostgreSQL 16 (db: davron_elektro, user postgres)
mvn compile
mvn spring-boot:run           # port = server.port (8082 at the moment), Swagger at /swagger-ui.html
mvn test                      # integration tests on in-memory H2 (profile "test"), no PostgreSQL needed
mvn test -Dtest=BusinessFlowTest#creditSaleAndCustomerPayment   # run a single test
```

`application.properties` reads its config from env vars: `DB_URL`, `DB_USER`, `DB_PASSWORD`, `JWT_SECRET` (at least 32 chars), `CORS_ORIGINS`, `SEED_PASSWORD`, `AI_API_KEY`, `AI_MODEL`, `AI_BASE_URL`, `FILES_DIR`, `FISCAL_ENABLED` and the `PRINT_*` variables. If the AI key is empty, AI endpoints return 503. `AiService` speaks the OpenAI-compatible chat API (default: Groq free tier) and answers through read-only tools filtered by the caller's permissions.

The defaults in `application.properties` do not match `docker-compose.yml`: the app defaults to database `postgres` with password `root123`, while compose creates `davron_elektro` with password `postgres` (or `DB_PASSWORD`). Set `DB_URL` and `DB_PASSWORD` when using compose. The README's port (8080) and its `SEED_PASSWORD` and CORS defaults are also out of date; trust `application.properties`.

`spring.jpa.hibernate.ddl-auto=update` manages the schema; there are no migrations. The `prod` profile (`application-prod.properties`) also turns off demo data. `config/DataSeeder` seeds the role matrix, expense categories, the chart of accounts, users (`davron` EGASI, `admin`, `buxgalter`, `sardor`/`jasur` SOTUVCHI) and, when `app.seed.demo-data=true`, demo products, customers and opening cash.

Tests extend `ApiTestSupport` (`@SpringBootTest` + MockMvc, one shared context and H2 database seeded by `DataSeeder` with password `test12345`). Tests share state, so create your own products/customers via `newProduct(...)` instead of relying on seed quantities. `EndpointSmokeTest` lists every GET endpoint; add new ones there.

`ddl-auto=update` never drops a NOT NULL constraint or column. When you relax an entity constraint, also give the manual `ALTER TABLE` for existing databases (see the README). It also never updates the CHECK constraints Hibernate generates for enum columns: a new enum value on a live PostgreSQL database is rejected until the constraint is gone, so add it to `DataSeeder.dropEnumChecks()`.

CI/CD is `.github/workflows/deploy.yml`: `mvn -B package` on PRs and pushes to `master`, then a `master` push uploads the jar and runs `deploy/deploy.sh` on the server (`/opt/back`, systemd service `davron-back`, port 8082), which rolls back to the previous jar if the app does not respond within 180 s. `ci-cd.yml` triggers on `main`, which does not exist here, and `deploy/davron-elektro.service` points at the old `/opt/davron-elektro` path; neither is in use.

## Architecture

The layers are `controller → service → repository`. Entities never leave the service layer; controllers return DTOs only.

- **DTOs** are grouped into one file per module. Each file is a `final` holder class with nested `record`s, e.g. `PosRequests.Checkout` and `PosResponses.SaleDto` in `dto/request` and `dto/response`. Add new DTOs as records in the matching holder.
- **Errors**: throw `BusinessException`, which gives 422 by default, or its `conflict()` (409) and `badRequest()` (400) factories, or `NotFoundException`. `GlobalExceptionHandler` maps these to `ErrorResponse`.
- **Authorization**: each endpoint uses `@PreAuthorize("@perm.view|create|edit|delete('MODULE')")`. `perm` is `security/PermissionChecker`, which checks the `role_permissions` matrix (`PermissionMatrixService`) against the `Module` enum. The `EGASI` (owner) role is always allowed. The matrix can be edited at runtime through `/api/roles/matrix`. A per-user row in `user_permissions` replaces the role row for that module (`/api/roles/users/{id}/permissions`). A new module needs a `Module` enum value, seed rows and a `PermissionCatalog` entry; seeding only adds missing rows, so edited permissions survive restarts.
- **Money and quantities**: always round through `component/MoneyUtils` and `QtyUtils`, and use `BigDecimal` everywhere. Time zone is `Asia/Tashkent`; get "now" and dates from `DateUtils`.

### Event-driven accounting journal (core invariant)

Business services never write journal entries themselves. Inside their `@Transactional` method they publish a domain event (`event/*Event` records). Current events: `SaleCompletedEvent`, `ReturnCreatedEvent`, `PurchaseCreatedEvent`, `ExpenseCreatedEvent`, `ExpenseCancelledEvent`, `CustomerPaymentEvent`, `SupplierPaymentEvent`, `StockAdjustedEvent`, `OpeningBalanceEvent`. `service/JournalListener` handles each one with `@TransactionalEventListener(phase = BEFORE_COMMIT)` and calls `AccountingService.post(...)` with debit/credit rows (`dt(...)`/`kt(...)`).

The journal runs in the same transaction on purpose. If posting fails, the sale or expense rolls back too, so the turnover sheet always balances. Do not switch to `AFTER_COMMIT`. Account codes are constants in `AccountingService`: 5010 cash, 5110 card, 4010 stock, 4110 customers, 3310 suppliers, 8410 owner, 6010 revenue, 6090 returns, 9010 COGS, 9410 expenses.

A new money-moving operation needs four things: an event record, a publish call in the service, a listener method in `JournalListener`, and a cash record when cash moves.

### Concurrency and single sources of truth

- **Cash**: `CashOperation` is the only record of cash. All cash writes go through `CashService.record(...)`. Outgoing payments take a pessimistic lock (`DocCounterRepository.lockByName("CASH_LOCK")`) and check the balance per payment method, so NAQD (cash) is checked separately from KARTA/CLICK (card). Negative balances are rejected unless `app.cash.allow-negative=true`.
- **Stock**: change product quantities only after locking rows with `ProductRepository.lockAllByIdIn(...)`, and record a `StockMovement` for each change.
- **Document numbers**: generated by `DocNumberService`, which uses a row-locked `DocCounter` (e.g. `CHK-000001`).
- **Audit**: services record changes through `AuditService`.
- **Store settings**: `StoreSettings` is a singleton entity (always `id = 1`) holding the store name, address, phone and tax ID printed on receipts. Managed via `SettingsController` (`/api/settings`).
- **Staff stats**: `StaffService` / `StaffController` (`/api/staff`) aggregates per-cashier sales, returns and revenue for a date range; read-only, no journal involvement.
- **Product images**: `ProductImageService` re-encodes uploads to 800/200 px JPEG named by content hash under `app.files.dir` (`FILES_DIR`); `FileController` serves `/files/**` without a token. Old files are deleted after commit.
- **Receipt printing**: the USB printer sits on the cashier PC, so the server never prints. Controllers call `PrintService.autoEnqueue` after commit, passing the browser's `X-Kassa-Id` so the job goes to that cashier PC (`PrintTerminal`, one token each; jobs without a terminal go to any agent). The PowerShell agent (`src/main/resources/print-agent/`, served by `/api/print-agent/script` and self-updating by SHA-256 version) long-polls `/api/print-agent/jobs/next` with `X-Agent-Token` and writes the ESC/POS bytes rendered by `ReceiptRenderer`/`EscPos` (ASCII only) straight to the USB device via Windows' built-in usbprint interface, so no vendor driver is needed. A Windows print queue (RAW) is the fallback. Admins create a terminal with `POST /api/print/terminals`, which returns a double-click `DavronPrinter-kassaN.cmd` installer.
- **Fiscal receipts**: `FiscalService` sends sales/returns to a `fiscal.FiscalProvider` *after* the sale commits (called from the controllers), so a fiscal failure never rolls back a sale. Status lives in the embedded `FiscalData` (`PENDING` → retried by a `@Scheduled` job → `SUCCESS`/`FAILED`). No real provider is implemented yet; `app.fiscal.enabled=false` by default. Tests use `TestFiscalProvider` and toggle `enabled` via reflection.

### Deliberate deviations from the spec (TZ)

The README lists these, and they matter when you change finance logic:

- Returns also post Dt 4010 / Kt 9010.
- Owner-draw expenses go to 8410, not 9410, so they stay out of P&L.
- Opening cash and stock (seed and Excel import) post against 8410.
- A customer payment larger than the customer's debt is rejected. Debt is settled FIFO against the aggregate balance, not per receipt.
- An Excel stock import becomes a single `Purchase`.
- A new product's `initialQty` posts against 8410 as opening stock; later stock arrives through incoming only.
- A mixed-payment sale (`PaymentType.ARALASH`, parts in `SalePayment`, debt in `Sale.creditAmount`) is refunded from NAQD only, and its credit part stays in the customer's debt. Older sales have no `SalePayment` rows and a null `creditAmount`.
