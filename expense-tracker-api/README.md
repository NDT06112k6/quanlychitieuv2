# Expense Tracker API

Domain and persistence foundation for a REST API using Java 25, Spring Boot 4.1.2, Maven,
Spring Security, JPA, Flyway and MySQL 8.0. No Thymeleaf is included.

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

Set `SPRING_DATASOURCE_PASSWORD` and `MYSQL_ROOT_PASSWORD` in your environment
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

All endpoints, including Swagger UI and `/v3/api-docs`, return 403 until
authentication and endpoint permissions are implemented in a later phase.
There are no default users or generated login passwords. Password encoding
uses `DelegatingPasswordEncoder`.

```sh
./mvnw verify
```

On Windows, use `.\mvnw.cmd verify`. Ensure `JAVA_HOME` points to JDK 25.
Tests use H2 in MySQL mode and run the production Flyway migration; they do not
connect to local MySQL. Repository tests use `@DataJpaTest` and verify ownership,
date/category filters, scoped mutations and the SQL count for entity graphs.
Service tests use Mockito without a Spring context. The suite also checks startup,
exact money storage, ownership foreign keys, budget uniqueness/types and
deny-by-default HTTP access.
H2 does not replace validation against MySQL for future database-specific changes.

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
User registration and HTTP/JWT authentication belong to the next phase.

- `UserService`: profile lookup/update and password changes using the configured
  delegating encoder; response DTOs never contain password hashes.
- `CategoryService`: creation, paged listing/filtering, rename and deletion.
  Category type is immutable so historical income/expense classification stays
  stable. Referenced categories cannot be deleted because of the database FKs.
- `TransactionService`: owner-scoped creation/read/update/delete, filtering by
  category/type/inclusive date range, and Dashboard income/expense/balance totals.
- `BudgetService`: serialized upsert of expense budgets by category, period and
  type, paged lookup/filtering/deletion, and paged budget alerts. Monthly periods
  use `YYYY-MM`; weekly periods use ISO week-based years (`YYYY-Www`, Monday to
  Sunday), including weeks spanning calendar years. Invalid week 53 is rejected.

All money uses `BigDecimal` with exact two-decimal storage; values requiring
rounding, non-positive amounts and values exceeding `DECIMAL(15,2)` are rejected.
All list services require `Pageable`, cap pages at 100 rows and sort by descending
ID for stable paging. Unpaged requests are rejected. There are no API endpoints
in this phase, so the deny-all HTTP policy from phase 1 remains in effect.

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
