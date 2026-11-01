# Expense Tracker API

Phase 1 foundation for a REST API using Java 25, Spring Boot 4.1.2, Maven,
Spring Security, JPA, Flyway and MySQL 8.0. No Thymeleaf is included.

## Dependencies

Spring Boot's BOM manages the Spring starters, MySQL connector, Flyway, Lombok
and test dependencies. Independent releases are pinned to springdoc 3.2.0
and JJWT 0.13.0:

- https://github.com/springdoc/springdoc-openapi/releases/tag/v3.2.0
- https://github.com/jwtk/jjwt/releases/tag/0.13.0

The Flyway starter is retained because Spring Boot 4 uses it to configure
and run migrations. The WebMVC and Security test starters supply Boot 4 test slices.
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
connect to local MySQL. They check startup, exact money storage, ownership
foreign keys, budget uniqueness/types and deny-by-default HTTP access.
H2 does not replace validation against MySQL for future database-specific changes.
