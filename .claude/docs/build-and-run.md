# Build & Run

The Spring Boot app lives in **`backend/`** (monorepo also has `frontend/` and
`picker-frontend/`). Run all Maven commands from `backend/` (`cd backend` first).

## Prerequisites (verified present on this machine)
- JDK 21 (`java -version` → 21.0.9 LTS)
- Maven 3.9.9 (`mvn -version`)
- PostgreSQL 18.3 running on `localhost:5432`, database `shelflife`, user `postgres`

## Database setup
The `shelflife` database already exists. To recreate from scratch:
```powershell
$env:PGPASSWORD = 'Ashu1234@'
& "C:\Program Files\PostgreSQL\15\bin\psql.exe" -h localhost -p 5432 -U postgres -c "CREATE DATABASE shelflife;"
```
(The PG 15 client binaries connect fine to the PG 18 server.)

Connection config lives in `backend/src/main/resources/application.yml` under `spring.datasource`.

## Commands (run from `backend/`)
```powershell
cd backend
mvn clean compile      # compile + Lombok/MapStruct annotation processing
mvn test               # boots Spring context, applies Flyway migration, runs contextLoads test
mvn package            # build the jar (backend/target/shelflife-0.0.1-SNAPSHOT.jar)
mvn spring-boot:run    # run the app on port 8080
```

## What the test does
`ShelflifeApplicationTests.contextLoads()` is an end-to-end smoke check: it starts the full
Spring context, which connects to Postgres and runs Flyway. A green test means wiring +
datasource + migrations are all healthy.

## Inspecting the DB
```powershell
$env:PGPASSWORD = 'Ashu1234@'
$psql = "C:\Program Files\PostgreSQL\15\bin\psql.exe"
& $psql -h localhost -p 5432 -U postgres -d shelflife -c "\dt"
& $psql -h localhost -p 5432 -U postgres -d shelflife -c "SELECT version, description, success FROM flyway_schema_history;"
```

## Notes
- Spring Security is on the classpath. With no custom config yet, the app generates a random
  password at startup and secures all endpoints by default — expected for the skeleton.
- Flyway logs a warning that PG 18.3 is newer than its tested ceiling (17). Harmless.
