# Expense Tracker API

Stateless REST API using Java 25, Spring Boot 4.1.2, Maven, Spring Security,
JJWT, JPA, Flyway, MySQL 8.0 and Swagger (springdoc).

## Dependencies

Spring Boot's BOM manages the Spring starters, MySQL connector, Flyway, Lombok
and test dependencies. Independent releases are pinned to springdoc 3.2.0
and JJWT 0.13.0:

- https://github.com/springdoc/springdoc-openapi/releases/tag/v3.2.0
- https://github.com/jwtk/jjwt/releases/tag/0.13.0

The Flyway starter is retained because Spring Boot 4 uses it to configure
and run migrations. The JPA, WebMVC and Security test starters supply Boot 4 test slices.
JJWT implementation and Jackson integration are runtime dependencies.

## Configuration

| Environment variable | Default / purpose |
| --- | --- |
| `SPRING_DATASOURCE_URL` | `jdbc:mysql://localhost:3306/expense_tracker` |
| `SPRING_DATASOURCE_USERNAME` | `expense_tracker` |
| `SPRING_DATASOURCE_PASSWORD` | Required; no password is committed |
| `MYSQL_DATABASE` | Compose only: `expense_tracker` |
| `MYSQL_ROOT_PASSWORD` | Required by Compose for MySQL initialization |
| `JWT_SECRET` | Required Base64 encoding of at least 32 cryptographically random bytes; no default |
| `JWT_EXPIRATION_SECONDS` | Positive token lifetime in seconds; defaults to `3600` |

For local execution, create the MySQL database and grant the application user
permissions to migrate its schema. Set the datasource variables in your shell
or IDE run configuration, then run `./mvnw spring-boot:run`
(`.\mvnw.cmd spring-boot:run` in PowerShell). A `.env` file is read by Docker
Compose, not by Spring Boot when launched directly.

Hibernate uses `ddl-auto=validate`; Flyway is the sole schema initializer.
Open-in-view is disabled for the REST API.

## Schema baseline

`src/main/resources/db/migration/V1__init_schema.sql` consolidates the old
project's V1, V2 and V3 into a baseline for a **new database**. It preserves
the four tables, column types, indexes, defaults and constraints, including
monthly/weekly budgets (`period`, `type`), unique budgets and composite
ownership foreign keys. Amounts use `DECIMAL(15,2)`.

Do not apply this consolidated V1 to the old migrated database: its Flyway
history and V1 checksum differ. Future schema changes require a new migration.

## Docker

Set `SPRING_DATASOURCE_PASSWORD`, `MYSQL_ROOT_PASSWORD` and `JWT_SECRET` in your environment
or in an ignored `.env` file, then run:

```sh
docker compose up --build
```

The multi-stage Dockerfile runs Maven verify on JDK 25 and copies the executable
JAR into a Temurin 25 JRE image running as a non-root user. Compose exposes
8080 for the application and 3306 for MySQL. The application waits for the
database health check and connects to `mysql-db` over the Compose network.
MySQL data persists in the `mysql-data` named volume. Credentials in Compose
initialize a new volume; changing environment values does not change accounts
in an existing volume. Stop the services with `docker compose down`.

## Security and verification

Public routes are `POST /api/v1/auth/register`, `POST /api/v1/auth/login`,
`/v3/api-docs/**`, `/swagger-ui/**` and `/swagger-ui.html`. Every other route
requires a verified Bearer access token. Missing, malformed or expired credentials
produce JSON 401; ownership/authorization failures produce JSON 403.
There are no default users or generated login passwords. Password encoding uses
`DelegatingPasswordEncoder`. Sessions, form login and HTTP Basic authentication
are disabled; CSRF is disabled because authentication uses the Authorization
header rather than an automatically attached cookie.

```sh
./mvnw verify
```

On Windows, use `.\mvnw.cmd verify`. Ensure `JAVA_HOME` points to JDK 25.
Tests use H2 in MySQL mode and run the production Flyway migration; they do not
connect to local MySQL. Repository tests use `@DataJpaTest` and verify ownership,
date/category filters, scoped mutations and the SQL count for entity graphs.
Service tests use Mockito without a Spring context. The suite also checks startup,
exact money storage, ownership foreign keys, budget uniqueness/types and
JWT validation, auth HTTP responses and protected/public access rules.
H2 does not replace validation against MySQL for future database-specific changes.
`OwnershipIsolationIntegrationTests` exercises registration/login and real HTTP
controller calls over H2, including category/transaction/budget creation,
Dashboard/alerts, rejected cross-user reads/updates/deletes, and profile/password
isolation. It also checks that owned resources can still be updated and deleted.

## Domain and persistence (phase 2)

Entities live in `user`, `category`, `transaction` and `budget`. Every owner
association uses LAZY `ManyToOne`; transactions and budgets also reference a
LAZY `Category`. There are no cascading writes or deletes between owners and
their data. The existing Flyway schema and composite ownership foreign keys
are preserved, and Hibernate validates entity mappings against that schema.

Services use Lombok constructor injection and return response DTOs. Caller
code must supply the authenticated user's ID; it must never take that ID from
an untrusted request body. Read/update/delete queries include the owner ID.
Missing and foreign resources both produce `AccessDeniedException`, without
performing an unscoped lookup to disclose their existence. Repositories expose
only the operations needed by the domain rather than inherited unscoped CRUD.
Business HTTP controllers must obtain their owner ID from the verified JWT
principal rather than a client-supplied owner ID.

- `UserService`: profile lookup/update and password changes using the configured
  delegating encoder; response DTOs never contain password hashes.
- `CategoryService`: creation, paged listing/filtering, rename and deletion.
  Category type is immutable so historical income/expense classification stays
  stable. Referenced categories cannot be deleted because of the database FKs.
- `TransactionService`: owner-scoped creation/read/update/delete, filtering by
  category/type/inclusive date range, and Dashboard income/expense/balance totals.
- `BudgetService`: serialized creation of expense budgets by category, period and
  type, limit-only updates, paged lookup/filtering/deletion, and paged budget alerts.
  Duplicate creation fails with 409; updates require an existing owned budget ID.
  Category, period and type cannot change during an update. Monthly periods
  use `YYYY-MM`; weekly periods use ISO week-based years (`YYYY-Www`, Monday to
  Sunday), including weeks spanning calendar years. Invalid week 53 is rejected.

All money uses `BigDecimal` with exact two-decimal storage; values requiring
rounding, non-positive amounts and values exceeding `DECIMAL(15,2)` are rejected.
All list services require `Pageable`, cap pages at 100 rows and sort by descending
ID for stable paging. Unpaged requests are rejected. HTTP endpoints are listed
below, together with the supported filters and pagination contract.

Transaction/budget list and detail queries use
`@EntityGraph(attributePaths = {"category", "user"})`. Category queries fetch
`user`, its only association. Dashboard totals run as database aggregates.
Budget alerts fetch a page of budgets and one grouped expense query for that
page, avoiding an aggregate query per budget. Alert statuses are `WITHIN_LIMIT`,
`LIMIT_REACHED` and `EXCEEDED`; only spending strictly above the limit is exceeded.
Alerts are read on demand for the supplied period, including its entire date
range, rather than automatically returned by transaction mutations.

The implementation follows the fetch-graph examples in
[Spring Data's official sample](https://github.com/spring-projects/spring-data-examples/blob/main/jpa/jpa21/README.md)
and the [JPA query documentation](https://docs.spring.io/spring-data/jpa/reference/jpa/query-methods.html).

## Authentication (phase 3)

Generate a secret with a cryptographic RNG, for example `openssl rand -base64 32`,
and supply its output through `JWT_SECRET` in your environment or an ignored
Compose `.env` file. Base64 is the storage encoding; the **decoded key** must be
at least 32 bytes. Missing, invalid or weak configuration prevents startup.
The public fixture key in `src/test/resources/application-test.properties`
is used only for tests and is not packaged in the runtime JAR.

| Endpoint | Request fields | Success |
| --- | --- | --- |
| `POST /api/v1/auth/register` | `email`, `password`, `displayName` | 201 and access token |
| `POST /api/v1/auth/login` | `email`, `password` | 200 and access token |

The response is `{ "accessToken": "...", "tokenType": "Bearer", "expiresInSeconds": 3600 }`.
Use `Authorization: Bearer <accessToken>` on protected requests. Email addresses
are trimmed and lowercased with `Locale.ROOT` before validation, storage and
lookup. Registration creates role `USER`; clients cannot choose a role.
Passwords must be nonblank, at least 8 characters for registration, and at most
72 UTF-8 bytes for BCrypt. Password whitespace is preserved.

Duplicate registration returns 409, including a concurrent database uniqueness
violation. Wrong passwords, missing accounts and disabled accounts all return
the same 401 message. Login also checks a dummy password hash for unknown accounts
to reduce timing differences. Neither passwords nor tokens are logged, and auth
DTO `toString()` methods redact sensitive fields.

JWTs use HS256. Only that algorithm is accepted by the parser. The signed
subject is the numeric user ID, with a `role` claim, `iat` and `exp`; there is no
email or password in the token. `JwtAuthenticationFilter` verifies the token
before setting `AuthPrincipal(userId, role)` in a fresh SecurityContext.
Controllers can receive it with `@AuthenticationPrincipal AuthPrincipal principal`.
The filter is installed only in the Spring Security chain, avoiding duplicate
servlet-filter registration. Invalid tokens stay anonymous; public endpoints
remain accessible and protected endpoints return 401.

The key and expiration setting are read at startup; changes require a restart.
Key rotation invalidates tokens signed with the previous key. Tokens are checked
locally on each request without a database lookup or server session. There is
no refresh/revocation endpoint in this phase: password changes, role changes or
account disabling do not revoke an already issued token before its expiration.

API and security errors share `status`, `error`, `message`, `timestamp` (UTC)
and `path`. Validation responses add `fieldErrors`, a list of `field`/`message`
pairs, without echoing rejected values. Unexpected failures return a generic 500;
logs retain the exception type and stack trace while removing exception messages
and causes that may contain credentials or PII.
Hibernate's raw JDBC error/warning loggers are disabled for the same reason;
application error handling still records sanitized system failures.

The design follows [JJWT 0.13.0's signed-claims parsing guidance](https://github.com/jwtk/jjwt/blob/0.13.0/README.adoc)
and [Spring Security's JWT sample](https://github.com/spring-projects/spring-security-samples/tree/main/servlet/spring-boot/java/jwt/login).
JJWT supplies signing/verification with the already installed dependency;
`SessionCreationPolicy.STATELESS` supplies session-free request authentication.
No dependencies were added in this phase.

## REST endpoints (phase 4)

All routes below require `Authorization: Bearer <accessToken>`. The owner ID
always comes from `@AuthenticationPrincipal AuthPrincipal`; no path, query or
request DTO accepts an owner ID. IDs in resource paths identify the resource,
and the service checks that it belongs to the authenticated user.

| Method | Path | Request / query | Success |
| --- | --- | --- | --- |
| GET | `/api/v1/users/me` | Current principal | 200 profile |
| PUT | `/api/v1/users/me` | `displayName` | 200 profile |
| PUT | `/api/v1/users/me/password` | `currentPassword`, `newPassword` | 200, empty body |
| POST | `/api/v1/categories` | `name`, `type` (`CHI` / `THU`) | 201 + Location |
| GET | `/api/v1/categories` | Pagination; optional `type` | 200 page |
| GET | `/api/v1/categories/{id}` | Resource ID | 200 category |
| PUT | `/api/v1/categories/{id}` | `name` only (`CategoryRenameRequest`) | 200 category |
| DELETE | `/api/v1/categories/{id}` | Resource ID | 204 |
| POST | `/api/v1/transactions` | `categoryId`, `amount`, `occurredOn`, optional `note` | 201 + Location |
| GET | `/api/v1/transactions` | Pagination; optional `startDate`, `endDate`, `categoryId`, `type` | 200 page |
| GET | `/api/v1/transactions/{id}` | Resource ID | 200 transaction |
| PUT | `/api/v1/transactions/{id}` | Same fields as transaction creation | 200 transaction |
| DELETE | `/api/v1/transactions/{id}` | Resource ID | 204 |
| GET | `/api/v1/transactions/dashboard` | Optional `startDate`, `endDate` | 200 totals |
| POST | `/api/v1/budgets` | `categoryId`, `period`, `limitAmount` | 201 + Location |
| GET | `/api/v1/budgets` | Pagination; optional `categoryId`, `type`, `period` | 200 page |
| GET | `/api/v1/budgets/{id}` | Resource ID | 200 budget |
| PUT | `/api/v1/budgets/{id}` | `limitAmount` only (`BudgetLimitRequest`) | 200 budget |
| DELETE | `/api/v1/budgets/{id}` | Resource ID | 204 |
| GET | `/api/v1/budgets/alerts` | Required `type`, `period`; pagination | 200 page of alerts |

Creation returns the DTO and a relative Location such as `/api/v1/categories/10`.
Category type is immutable. Budget updates cannot change category, period or type.
Creating a duplicate budget returns 409, and deleting a category referenced by
transactions or budgets returns 409. Missing/foreign owned resources both return
403 through `AccessGuard`. Invalid bodies, dates, enum values, IDs and sort
fields return the shared JSON 400 response; missing/invalid tokens return 401.

Dates use `YYYY-MM-DD`, and range bounds are inclusive. An omitted start/end
includes all supported earlier/later dates (1000-01-01 through 9999-12-31).
Budget filters use, for example, `type=MONTHLY&period=2026-11` or
`type=WEEKLY&period=2026-W45`; `period` requires `type`. The creation body nests
the period: `{"categoryId":10,"period":{"type":"MONTHLY","period":"2026-11"},"limitAmount":500.00}`.

Pagination uses `page` (zero-based, default 0), `size` (default 20, capped at 100)
and optional `sort=id,desc`. The existing service ordering remains descending ID.
Other sort fields **and ascending ID sort** are rejected with 400 instead of
silently changing or ignoring the requested ordering. Responses contain DTOs
under `content`, with page metadata including `size`, `number` and `totalElements`.

### Swagger UI

Open `http://localhost:8080/swagger-ui.html` (redirects to the Swagger UI).
Run register or login under **Authentication**, copy `accessToken`, then click
**Authorize** and paste the token **without** the `Bearer ` prefix. Swagger adds
the prefix through the HTTP Bearer security scheme. Register/login have no Bearer
requirement. `/v3/api-docs` publishes the OpenAPI document, including query filters,
request DTOs and the common error schema. Principal/userId is hidden from inputs.

### PowerShell curl example

Use `curl.exe` rather than the Windows PowerShell `curl` alias. Set
`DEMO_PASSWORD` in the current shell to your own password (at least 8 characters).
Temporary UTF-8 JSON files preserve quotes and Unicode on Windows PowerShell;
the last command deletes those payloads, which contain the demo credentials.

```powershell
$baseUrl = 'http://localhost:8080'
$demoEmail = 'demo@example.invalid'

function Write-JsonTemp($value) {
    $file = [System.IO.Path]::GetTempFileName()
    $encoding = [System.Text.UTF8Encoding]::new($false)
    [System.IO.File]::WriteAllText($file, ($value | ConvertTo-Json -Compress -Depth 5), $encoding)
    return $file
}

$registrationFile = Write-JsonTemp @{
    email = $demoEmail
    password = $env:DEMO_PASSWORD
    displayName = 'Demo'
}
curl.exe -sS -X POST "$baseUrl/api/v1/auth/register" `
    -H 'Content-Type: application/json' --data-binary "@$registrationFile" | Out-Null

$loginFile = Write-JsonTemp @{
    email = $demoEmail
    password = $env:DEMO_PASSWORD
}
$auth = curl.exe -sS -X POST "$baseUrl/api/v1/auth/login" `
    -H 'Content-Type: application/json' --data-binary "@$loginFile" | ConvertFrom-Json
$authorization = "Authorization: Bearer $($auth.accessToken)"

$categoryFile = Write-JsonTemp @{
    name = 'Food'
    type = 'CHI'
}
$category = curl.exe -sS -X POST "$baseUrl/api/v1/categories" `
    -H $authorization -H 'Content-Type: application/json' --data-binary "@$categoryFile" | ConvertFrom-Json

$transactionFile = Write-JsonTemp @{
    categoryId = $category.id
    amount = [decimal]'12.34'
    occurredOn = '2026-11-08'
    note = 'Lunch'
}
curl.exe -i -X POST "$baseUrl/api/v1/transactions" `
    -H $authorization -H 'Content-Type: application/json' --data-binary "@$transactionFile"

curl.exe -i --get "$baseUrl/api/v1/transactions/dashboard" -H $authorization `
    --data-urlencode 'startDate=2026-11-01' --data-urlencode 'endDate=2026-11-30'
curl.exe -i --get "$baseUrl/api/v1/transactions" -H $authorization `
    --data-urlencode 'size=1000' --data-urlencode 'sort=id,desc'

Remove-Item -LiteralPath $registrationFile, $loginFile, $categoryFile, $transactionFile
```
