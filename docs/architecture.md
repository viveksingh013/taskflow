# TaskFlow architecture

## Goal

Persist one-time HTTP jobs and execute them when `scheduledAt` is reached. The current process is a **single application instance**. Distributed coordination is a later milestone.

## Layering

```
Controller
    ↓
JobService                 (create / get / list / cancel)
    ↓
JobRepository  →  PostgreSQL

JobScheduler               (periodic due-job scan)
    ↓
JobExecutionService        (claim + persist result)
    ↓
HttpJobExecutor            (outbound HTTP only)
```

| Component              | Package                    | Responsibility                                      |
|------------------------|----------------------------|-----------------------------------------------------|
| Controller             | `com.taskflow.controller`  | HTTP API, validation                                |
| JobService             | `com.taskflow.service`     | Create/get/list/cancel, DTO mapping                 |
| JobExecutionService    | `com.taskflow.service`     | Atomic claim, result persistence                    |
| JobScheduler           | `com.taskflow.scheduler`   | Find due `SCHEDULED` jobs and trigger execution     |
| HttpJobExecutor        | `com.taskflow.http`        | Perform the target HTTP request                     |
| JobRepository          | `com.taskflow.repository`  | Due-job query + `claimScheduledJob` update          |
| Entity / DTOs          | `entity` / `dto`           | Persistence vs API shapes                           |

Controllers stay thin. The scheduler never talks to the HTTP client directly.

## Scheduling

`JobScheduler` uses Spring `@Scheduled` with `taskflow.scheduler.fixed-delay` (default 1000 ms). It is not a busy loop.

Each tick queries only due work:

```
status = SCHEDULED AND scheduledAt <= now
```

If the result is empty, the tick returns without logging. One failed job is caught and does not stop the rest of the batch.

The scheduler bean is off when `taskflow.scheduler.enabled=false` (used by tests).

## Claim then execute

```
1. UPDATE jobs SET status = RUNNING, started_at = now
   WHERE id = ? AND status = SCHEDULED
2. Commit that transaction.
3. Call the target URL (no open DB transaction).
4. In a new transaction, set COMPLETED or FAILED.
```

The claim is a single `UPDATE ... WHERE status = SCHEDULED`. If another tick already claimed the row, the update count is 0 and HTTP is skipped.

This prevents duplicate execution from overlapping scheduler scans on **one instance**. It is not a distributed lock. Multiple app instances are not supported yet.

HTTP is outside the claim transaction so a slow or hung target does not hold a database transaction open.

## Success and failure

| Outcome                         | Next status | Metadata                         |
|---------------------------------|-------------|----------------------------------|
| HTTP 2xx                        | `COMPLETED` | `completedAt` set, `lastError` cleared |
| HTTP non-2xx                    | `FAILED`    | `completedAt` + `lastError`      |
| Timeout, connect error, bad URL | `FAILED`    | `completedAt` + `lastError`      |

`startedAt` is set at claim time. Cancel remains `SCHEDULED → CANCELLED` only.

## HTTP client

Outbound calls use Spring `RestClient` with the JDK `HttpClient`:

- Connect and read timeouts from `taskflow.http.*`
- Methods: GET, POST, PUT, PATCH, DELETE
- JSON body is sent only for POST/PUT/PATCH when a body is stored
- Authorization and other header values are never logged

## Job table

| Column          | Type      | Notes                                      |
|-----------------|-----------|--------------------------------------------|
| `id`            | UUID      | Application-generated                      |
| `name`          | varchar   | Required                                   |
| `scheduled_at`  | timestamp | Must be in the future at create time       |
| `target_url`    | varchar   | `http` or `https` only                     |
| `target_method` | varchar   | GET/POST/PUT/PATCH/DELETE                  |
| `headers`       | jsonb     | Optional                                   |
| `body`          | jsonb     | Optional                                   |
| `status`        | varchar   | See lifecycle                              |
| `created_at`    | timestamp | `@PrePersist`                              |
| `updated_at`    | timestamp | `@PrePersist` / `@PreUpdate` / claim SQL   |
| `started_at`    | timestamp | Set when claimed                           |
| `completed_at`  | timestamp | Set on COMPLETED or FAILED                 |
| `last_error`    | varchar   | Set on FAILED, truncated to 2000 chars     |

Local schema: `spring.jpa.hibernate.ddl-auto=update`. Use Flyway/Liquibase before production.

## Intentionally not implemented

Redis, Kafka, distributed locks, multiple instances, worker services, cron, retries, DLQ, auth, UI, execution-history tables.
