# Expense Tracker API

Stateless REST API using Java 25, Spring Boot 4.1.2, Maven, Spring Security,
JJWT, JPA, Flyway, MySQL 8.4 in Docker Compose and Swagger (springdoc).

## Architecture

Packages follow features: `auth`, `user`, `category`, `transaction`, `budget`.
Requests pass through JWT verification, then DTO-only controllers, transactional
services, owner-scoped repositories and MySQL. Shared validation, pagination and
error responses live in `common`; application wiring lives in `config`.
Flyway owns the schema; JPA validates it. Money uses `BigDecimal` / `DECIMAL(15,2)`.
Entity graphs load required owner/category associations for list queries.
Controllers obtain the owner exclusively from the authenticated principal.

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
| `SPRING_DATASOURCE_USERNAME` | `expense_tracker`; Compose also uses it for the MySQL application account |
| `SPRING_DATASOURCE_PASSWORD` | Required; Compose also uses it for the MySQL application account password |
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

Create `.env` using the PowerShell script below, then replace the illustrative
database passwords. The checked-in JWT placeholder is deliberately invalid.
Compose derives the MySQL application account and password directly from
`SPRING_DATASOURCE_USERNAME` and `SPRING_DATASOURCE_PASSWORD`; separate
`MYSQL_USER` / `MYSQL_PASSWORD` entries in an older `.env` are not read.
Use a different MySQL administrator password. Run these commands from `expense-tracker-api`
with Docker Desktop's Linux engine running:

```sh
docker compose up --build -d
docker compose ps
```

The multi-stage Dockerfile caches dependency downloads from `pom.xml` before
copying `src`, runs Maven verify (including tests and the coverage gate) on JDK 25 and copies the executable
JAR into a Temurin 25 JRE image running as an unprivileged user. Compose exposes
8080 for the application and **3307 on the host to MySQL's internal port 3306**.
The application waits for the
database health check and connects to `mysql-db` over the Compose network.
MySQL data persists in the `mysql-data` named volume. Credentials in Compose
initialize a new volume; changing environment values does not change accounts
in an existing volume. Stop the services with `docker compose down`, which keeps
the volume. `docker compose down -v` deletes all database data; use it only for
an intentional reset of a disposable environment.
Compose supplies the application URL with the `mysql-db` service hostname;
it does not use the host's port 3307. For a directly launched Spring Boot process
connecting to this Compose database, set
`SPRING_DATASOURCE_URL=jdbc:mysql://localhost:3307/expense_tracker` (adjust the
database name if customized). The default localhost:3306 URL is for a separate
local MySQL server.

The MySQL container has a health check; the Spring container does not declare
one. Confirm MySQL is healthy, Spring is running, and `/` responds with 200.
On a fresh volume, Spring logs should contain `Successfully applied 1 migration`
and `Started ExpenseTrackerApplication`; on later starts, Flyway reports the
schema is up to date. Open `http://localhost:8080/` for the frontend and
`http://localhost:8080/swagger-ui.html` for Swagger.

### Generate JWT_SECRET in PowerShell

This works in Windows PowerShell 5.1 and PowerShell 7. Generate a separate key
for each environment; do not use the test profile key outside tests.

```powershell
if (Test-Path .env) {
    throw '.env already exists; update it deliberately instead of overwriting it.'
}
Copy-Item .env.example .env
$jwtBytes = New-Object byte[] 32
$jwtRandom = [System.Security.Cryptography.RandomNumberGenerator]::Create()
try {
    $jwtRandom.GetBytes($jwtBytes)
} finally {
    $jwtRandom.Dispose()
}
$env:JWT_SECRET = [Convert]::ToBase64String($jwtBytes)
$envLines = Get-Content .env
$envLines = $envLines -replace '^JWT_SECRET=.*$', ('JWT_SECRET=' + $env:JWT_SECRET)
$utf8 = [System.Text.UTF8Encoding]::new($false)
[System.IO.File]::WriteAllLines((Join-Path $PWD '.env'), $envLines, $utf8)
```

The script writes the secret directly to ignored `.env` without displaying it.
Edit the placeholder passwords before running Compose. For direct local launch,
set `SPRING_DATASOURCE_PASSWORD` (and URL/username if needed) in the shell or IDE;
the generated `$env:JWT_SECRET` is already available to that PowerShell process.

## Security and verification

Public routes are `POST /api/v1/auth/register`, `POST /api/v1/auth/login`,
`/v3/api-docs/**`, `/swagger-ui/**` and `/swagger-ui.html`. The frontend's
`GET`/`HEAD` routes `/`, `/index.html`, `/css/**`, `/js/**` and `/favicon.ico`
are also public. Other routes, including business APIs, require a verified
Bearer access token. Missing, malformed or expired credentials
produce JSON 401; ownership/authorization failures produce JSON 403.
There are no default users or generated login passwords. Password encoding uses
`DelegatingPasswordEncoder`. Sessions, form login and HTTP Basic authentication
are disabled; CSRF is disabled because authentication uses the Authorization
header rather than an automatically attached cookie.

Use HTTPS outside local development; Bearer tokens grant access until expiry.
Keep `.env`, passwords, signing keys and real tokens out of Git, Docker build
context, logs and command transcripts. Rotate exposed credentials immediately;
removing a file in a later commit does not remove it from Git history. Restrict
public MySQL port exposure for deployment and protect or disable Swagger where
appropriate. See [SECURITY_AUDIT.md](SECURITY_AUDIT.md) for the repository scan scope
and results. Test fixtures contain intentionally fake credentials only.

```sh
./mvnw verify
```



On Windows, use `.\mvnw.cmd verify`. Ensure `JAVA_HOME` points to JDK 25.
Use `clean verify` for a fresh build. `verify` generates JaCoCo HTML, XML and CSV
reports in `target/site/jacoco/`; open `target/site/jacoco/index.html` to inspect
coverage. The build fails below **80% line coverage for each `*Service` class**
and each `auth` package (including `auth.validation`). Service classes live in
their feature packages, so a per-class rule provides a stricter gate than one
aggregate service-package threshold. The report includes all production code.
Only the JaCoCo Maven plugin was added; application dependencies are unchanged.

GitHub Actions runs `./mvnw --batch-mode verify` with Temurin 25 and Maven cache
for pushes and pull requests targeting `main`, then uploads the generated
coverage directory. The workflow is at the **top level of the Git repository**
(`../.github/workflows/ci.yml` relative to this Maven project). The job's working
directory and cache path point to `expense-tracker-api`. Tests use the fake key
in `application-test.properties`; no production GitHub secrets are required.

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
All list services require `Pageable` and cap pages at 100 rows. The default sort
is descending ID; callers may choose the whitelisted fields documented below,
with descending ID appended when needed to break ties. Unpaged requests are
rejected. HTTP endpoints are listed
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
and optional `sort=field,asc` / `sort=field,desc` (repeat `sort` for multiple fields).
The default remains `id,desc`. Allowed fields are `id,name` for categories,
`id,occurredOn,amount` for transactions, and `id,limitAmount` for budgets and alerts.
For example, `?sort=amount,asc&sort=occurredOn,desc`. When no ID sort is specified,
`id,desc` is appended to break ties consistently across pages. Unlisted fields,
nested properties and ignore-case options return 400. The whitelist is enforced
by the shared pagination utility in both controllers and services. Responses contain DTOs
under `content`, with page metadata including `size`, `number` and `totalElements`.

### Swagger UI

Open `http://localhost:8080/swagger-ui.html` (redirects to the Swagger UI).
Run register or login under **Authentication**, copy `accessToken`, then click
**Authorize** and paste the token **without** the `Bearer ` prefix. Swagger adds
the prefix through the HTTP Bearer security scheme. Register/login have no Bearer
requirement. `/v3/api-docs` publishes the OpenAPI document, including query filters,
request DTOs and the common error schema. Principal/userId is hidden from inputs.

### PowerShell HTTP example

Use `Invoke-RestMethod` so request bodies and the Bearer header stay in memory.
The password prompt hides input; use 8 to 72 UTF-8 bytes for this example.
Do not print the auth response, token, request bodies or password, and do not
save them to files or shell transcripts. If using curl separately, call
`curl.exe` explicitly because Windows PowerShell's `curl` alias is different.

```powershell
$baseUrl = 'http://localhost:8080'
$demoEmail = 'demo@example.invalid'
$passwordPrompt = Read-Host 'Demo password' -AsSecureString
$demoCredential = [pscredential]::new('demo', $passwordPrompt)
$requestOptions = @{
    ContentType = 'application/json; charset=utf-8'
    ErrorAction = 'Stop'
}

$registrationBody = @{
    email = $demoEmail
    password = $demoCredential.GetNetworkCredential().Password
    displayName = 'Demo'
} | ConvertTo-Json -Compress
Invoke-RestMethod -Method Post -Uri "$baseUrl/api/v1/auth/register" `
    @requestOptions -Body $registrationBody | Out-Null

$loginBody = @{
    email = $demoEmail
    password = $demoCredential.GetNetworkCredential().Password
} | ConvertTo-Json -Compress
$auth = Invoke-RestMethod -Method Post -Uri "$baseUrl/api/v1/auth/login" `
    @requestOptions -Body $loginBody
$authorization = @{ Authorization = 'Bearer ' + $auth.accessToken }

$categoryBody = @{
    name = 'Food'
    type = 'CHI'
} | ConvertTo-Json -Compress
$category = Invoke-RestMethod -Method Post -Uri "$baseUrl/api/v1/categories" `
    @requestOptions -Headers $authorization -Body $categoryBody

$transactionBody = @{
    categoryId = $category.id
    amount = '12.34'
    occurredOn = [DateTime]::Today.ToString('yyyy-MM-dd')
    note = 'Lunch'
} | ConvertTo-Json -Compress
Invoke-RestMethod -Method Post -Uri "$baseUrl/api/v1/transactions" `
    @requestOptions -Headers $authorization -Body $transactionBody | Out-Null

Invoke-RestMethod -Method Get -Uri "$baseUrl/api/v1/transactions/dashboard" `
    -Headers $authorization -ErrorAction Stop
Invoke-RestMethod -Method Get `
    -Uri "$baseUrl/api/v1/transactions?size=1000&sort=id,desc" `
    -Headers $authorization -ErrorAction Stop

$passwordPrompt.Dispose()
Remove-Variable registrationBody, loginBody, auth, authorization, demoCredential
```

## Web frontend

After starting Spring Boot locally or with Docker, open **http://localhost:8080/**.
Use a modern browser with ES modules, BigInt, Fetch and sessionStorage enabled.
All HTML, CSS, icons and JavaScript are served from `src/main/resources/static`;
there is no npm build, framework, CDN or external font. Hash navigation keeps
every page on `index.html`, so refreshing `/#/transactions` needs no server routing.

| Page | Hash route | What it does |
| --- | --- | --- |
| Login | `#/login` | Email/password authentication; generic failure message |
| Register | `#/register` | Email, displayName, password; success returns to login |
| Dashboard | `#/dashboard` | Server totals for selected dates, comparison bars, paged monthly/weekly budget alerts |
| Transactions | `#/transactions` | Date/category/type filters, whitelisted sorting, pagination, create/edit/delete |
| Categories | `#/categories` | Pagination, type filters, sort, create/rename/delete; type stays immutable |
| Budgets | `#/budgets` | Pagination, category/type/period filters, create, edit limit only, delete |
| Profile | `#/profile` | Read email/role, change displayName and password |

Lists default to 10 rows and offer sizes up to 100. Category selectors also page
through the real category API, retaining a selection when moving between pages.
They do not download an unbounded category list. Dates display as `dd/MM/yyyy`.
Money inputs accept an ungrouped decimal such as `125000.50` or `125000,50` and
send normalized **JSON strings** (e.g. `"125000.50"`) into the existing BigDecimal
DTOs. A JSON-aware token scanner preserves response money/Long IDs as strings;
formatting uses BigInt cents and `Intl.NumberFormat('vi-VN')`, keeping precision
even at `DECIMAL(15,2)` limits. Number conversion is used only for chart scale.

### Screens and manual walkthrough

Screenshots were not captured because browser-control approval timed out in the
development session. The layout is described here for review: login has a pale
green introduction beside a white form; signed-in pages have a left navigation
rail, summary cards, filter panels and scrollable tables. On small screens the
navigation wraps above the content, cards stack, and wide tables scroll within
their panel. Budget warnings use both text and color. Loading, empty and error
states are separate; errors never become fake zero totals or empty successful data.

Use this checklist in the browser:

1. Register an account, then log in. Check empty/invalid fields and a duplicate email.
2. Create an expense category and an income category. Rename one; verify type cannot change.
3. Create a transaction (e.g. an expense of `125000.50` today), edit it and exercise
   date/category/type filters, sorting and pagination. Confirm before deleting a test item.
4. Open Dashboard, include the transaction date and confirm totals/decimal amounts.
5. Create a monthly budget for the expense category and current month with a limit
   below the expense (e.g. `100000.00`). Try creating it again and check the clear 409 message.
6. Open Dashboard budget alerts for the same month. Check the exceeded amount;
   increase only the budget limit and check that category/period/type stay unchanged.
7. Change displayName and password in Profile. Log out and log in with the new password.
8. Log out again; a protected hash route should return to login. Verify a fresh
   tab's storage behavior and that no token is placed in localStorage.
9. Repeat at phone width and with keyboard navigation. Enter an HTML-looking name
   such as `<img src=x onerror=alert(1)>`; it must display as text and never execute.
10. Check a second account cannot see or use the first account's categories/data.
    Check expired-token 401 returns to login, 403 has a friendly denial, and Swagger still opens.

### Frontend security and tests

JWTs are stored only in **sessionStorage**, scoped to the tab's browser session,
instead of localStorage's longer persistence. Session storage is still readable
by same-origin JavaScript; it does not make XSS harmless. DOM content uses text
nodes/createElement, the app CSP permits self-hosted resources only (no inline
scripts/styles), and static responses have nosniff, Referrer-Policy and frame
protection. Swagger/API docs are outside the app CSP so their UI keeps working.

Protected requests attach Bearer headers. A matching session's 401 clears its
token and redirects to login with an expiry notice; bad credentials on the
public login form show the generic login error. A late 401 from an older session
cannot clear a newer login. Errors 400 map to fields when provided; 403/409/500
show Vietnamese messages without technical details. Route changes abort pending
requests, and lists reject stale filter responses. Logout clears the current tab's
token; this backend does not revoke copied JWTs or other tabs' tokens, including
after a password change. Existing tokens remain valid until their normal expiry.

Run the Java/MockMvc security and decimal-contract checks with `mvnw.cmd clean verify`.
The dependency-free JavaScript tests use Node's built-in runner (Node 24), without npm:

```powershell
node --test --test-isolation=none src/test/frontend/*.test.mjs
```

These tests cover client requests, session lifecycle, error handling, XSS-safe DOM,
decimal precision, dates and validation. Browser layout/interaction review remains
the manual checklist above; JaCoCo measures Java code, not JavaScript coverage.
