# TaskFlow

TaskFlow is a production-style distributed job scheduling and execution platform.

The current milestone persists **one-time HTTP jobs** in PostgreSQL and **executes them when `scheduledAt` arrives**. A single application instance polls for due work, claims each job atomically, then performs the HTTP request.

This is still a **single-instance scheduler**. Redis, distributed locks, multiple workers, and Kafka are intentionally not implemented yet.

## Current scope

Supported:

- One-time HTTP jobs (`GET`, `POST`, `PUT`, `PATCH`, `DELETE`)
- `POST /api/v1/jobs` — create a job
- `GET /api/v1/jobs/{id}` — retrieve a job, including execution metadata
- `GET /api/v1/jobs` — list jobs
- `DELETE /api/v1/jobs/{id}` — cancel a `SCHEDULED` job (soft cancel)
- PostgreSQL persistence (JSONB headers/body)
- Background scheduler (every 1 second by default)
- Atomic `SCHEDULED → RUNNING` claim
- HTTP execution with 2xx → `COMPLETED`, anything else → `FAILED`

Not implemented yet:

- Cron / recurring schedules
- Retries / backoff
- Distributed locking or multiple application instances
- Redis, Kafka, worker microservices
- Pagination, authentication, UI
- Flyway / Liquibase migrations

## Job lifecycle

```
CREATE → SCHEDULED → RUNNING → COMPLETED
                      RUNNING → FAILED
         SCHEDULED → CANCELLED
```

A job is claimed with an atomic `UPDATE ... WHERE status = SCHEDULED`. Only one claim succeeds. HTTP execution happens **after** that transaction commits, so a slow target does not hold a database lock.

## Tech stack

- Java 21
- Spring Boot 4.1
- Spring MVC
- Spring Scheduling
- Spring `RestClient` (JDK HTTP client)
- Spring Data JPA / Hibernate 7
- Jakarta Bean Validation
- PostgreSQL 16
- H2 (tests)
- Docker Compose
- Maven Wrapper

## Local PostgreSQL

Requires Docker (or Colima):

```bash
docker compose up -d
```

| Setting  | Value      |
|----------|------------|
| Database | `taskflow` |
| Username | `taskflow` |
| Password | `taskflow` |
| Port     | `5432`     |

## Configuration

| Variable / property                 | Purpose                         | Default                                           |
|-------------------------------------|---------------------------------|---------------------------------------------------|
| `DB_URL`                            | JDBC URL                        | `jdbc:postgresql://localhost:5432/taskflow`       |
| `DB_USERNAME`                       | Database user                   | `taskflow`                                        |
| `DB_PASSWORD`                       | Database password               | `taskflow`                                        |
| `JPA_DDL_AUTO`                      | Hibernate schema mode           | `update` (local only)                             |
| `taskflow.scheduler.enabled`        | Turn the poller on/off          | `true`                                            |
| `taskflow.scheduler.fixed-delay`    | Milliseconds between scans      | `1000`                                            |
| `taskflow.http.connect-timeout`     | Outbound connect timeout        | `2s`                                              |
| `taskflow.http.read-timeout`        | Outbound read timeout           | `5s`                                              |

`ddl-auto=update` is for local development. Production should use Flyway or Liquibase.

## Run the application

```bash
docker compose up -d
./mvnw spring-boot:run
```

API: `http://localhost:8080`  
Health: `http://localhost:8080/actuator/health`

## Run tests

```bash
./mvnw test
./mvnw -DskipTests compile
```

Tests use H2 and disable the scheduler (`taskflow.scheduler.enabled=false`) so ticks do not fire during API tests. HTTP execution tests use a local JDK mock server, not the public internet.

For a manual local target: `python3 scripts/local-http-target.py` listens on `http://127.0.0.1:9099`.

## API endpoints

| Method   | Path                 | Success | Description                                      |
|----------|----------------------|---------|--------------------------------------------------|
| `POST`   | `/api/v1/jobs`       | `201`   | Create a one-time HTTP job                       |
| `GET`    | `/api/v1/jobs/{id}`  | `200`   | Get a job, including `startedAt` / `completedAt` |
| `GET`    | `/api/v1/jobs`       | `200`   | List jobs                                        |
| `DELETE` | `/api/v1/jobs/{id}`  | `200`   | Cancel a `SCHEDULED` job                         |

See [docs/api.md](docs/api.md) and [docs/architecture.md](docs/architecture.md).

## Example: create a job

`scheduledAt` must be in the future. Use a timestamp a few seconds ahead to watch execution locally.

```bash
curl -sS -X POST http://localhost:8080/api/v1/jobs \
  -H 'Content-Type: application/json' \
  -d '{
    "name": "Ping local target",
    "scheduledAt": "2026-09-11T18:00:00",
    "target": {
      "url": "http://127.0.0.1:9099/jobs",
      "method": "POST",
      "headers": {
        "Content-Type": "application/json"
      },
      "body": {
        "reportType": "DAILY"
      }
    }
  }'
```

Create response:

```json
{
  "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "status": "SCHEDULED",
  "message": "Job scheduled successfully"
}
```

After execution, `GET /api/v1/jobs/{id}` includes `status`, `startedAt`, `completedAt`, and `lastError`.
