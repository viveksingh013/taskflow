# TaskFlow API

Base URL (local): `http://localhost:8080`

The API schedules **one-time HTTP jobs only**. After `scheduledAt`, the single-instance scheduler claims the job and calls `target.url`.

Timestamps in job payloads are ISO-8601 local date-times (`2026-09-11T18:00:00`). Error timestamps are UTC instants.

## 1. Create a future job

`POST /api/v1/jobs`

`scheduledAt` must be in the future. `target.url` must be `http` or `https`. `target.method` must be `GET`, `POST`, `PUT`, `PATCH`, or `DELETE`.

```json
{
  "name": "Generate Daily Report",
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
}
```

`201 Created`:

```json
{
  "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "status": "SCHEDULED",
  "message": "Job scheduled successfully"
}
```

`400 VALIDATION_ERROR` — missing/blank fields, unsupported method, non-HTTP URL  
`400 INVALID_SCHEDULE` — `scheduledAt` is now or in the past  
`400 INVALID_TARGET` — URL/method rejected by the service after bean validation

## 2. Fetch it while SCHEDULED

`GET /api/v1/jobs/{id}`

```json
{
  "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "name": "Generate Daily Report",
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
  },
  "status": "SCHEDULED",
  "createdAt": "2026-09-10T18:00:00",
  "updatedAt": "2026-09-10T18:00:00",
  "startedAt": null,
  "completedAt": null,
  "lastError": null
}
```

## 3. Wait until scheduledAt

The scheduler scans about once a second (`taskflow.scheduler.fixed-delay`). When `scheduledAt <= now` and status is still `SCHEDULED`, the job is claimed (`RUNNING`) and the HTTP request is sent.

## 4. Fetch it while RUNNING / COMPLETED / FAILED

`GET /api/v1/jobs/{id}` after a successful 2xx:

```json
{
  "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "name": "Generate Daily Report",
  "scheduledAt": "2026-09-11T18:00:00",
  "target": {
    "url": "http://127.0.0.1:9099/jobs",
    "method": "POST",
    "headers": {},
    "body": {}
  },
  "status": "COMPLETED",
  "createdAt": "2026-09-10T18:00:00",
  "updatedAt": "2026-09-11T18:00:01",
  "startedAt": "2026-09-11T18:00:01",
  "completedAt": "2026-09-11T18:00:01",
  "lastError": null
}
```

On HTTP 5xx, timeout, or connection error, `status` is `FAILED` and `lastError` explains why. `RUNNING` is usually brief; poll `GET` if you need to observe it.

`GET /api/v1/jobs` returns the same objects as an array, newest `createdAt` first.

## 5. Cancel a SCHEDULED job

`DELETE /api/v1/jobs/{id}`

```json
{
  "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "status": "CANCELLED",
  "message": "Job cancelled successfully"
}
```

A cancelled job is never claimed or executed. `RUNNING`, `COMPLETED`, `FAILED`, and `CANCELLED` cannot be cancelled (`409 INVALID_STATUS_TRANSITION`).

## Errors

```json
{
  "status": 400,
  "error": "VALIDATION_ERROR",
  "message": "Request validation failed",
  "timestamp": "2026-09-10T12:34:56.789Z"
}
```

| HTTP | `error`                     | When                                            |
|------|-----------------------------|-------------------------------------------------|
| 400  | `VALIDATION_ERROR`          | Bean validation, bad JSON, bad UUID             |
| 400  | `INVALID_SCHEDULE`          | `scheduledAt` not in the future                 |
| 400  | `INVALID_TARGET`            | URL/method rejected by the service              |
| 404  | `JOB_NOT_FOUND`             | Unknown job UUID                                |
| 409  | `INVALID_STATUS_TRANSITION` | Cancel of a non-`SCHEDULED` job                 |
| 500  | `INTERNAL_ERROR`            | Unexpected server failure                       |
