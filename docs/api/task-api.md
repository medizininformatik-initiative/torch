# Task API

TORCH exposes job management as a FHIR [`Task`](https://www.hl7.org/fhir/task.html)-based control API under
`/fhir/Task`. This is separate from the [Asynchronous Bulk Data Request Pattern](./api.md) used to *kick off* and
*poll* an extraction — the Task API is for listing jobs, inspecting a single job, changing its priority, or
pausing / resuming / cancelling / deleting it. It is TORCH-specific and not as formalized as the bulk data API: there
is no external specification it implements, only a mapping from TORCH's internal job model onto the FHIR `Task`
resource.

<style>
/* Hide the "Servers" section */
#servers, h2#servers,
h2#servers + div,
.OAServers,
.v-openapi-servers {
  display: none !important;
}

/* The paths are rendered as a 2-column grid. Keep only the left column. */
.OAPath .sm\:grid-cols-2 {
  grid-template-columns: 1fr !important;
}

/* Hide the right column (contains: Body editor, Try it out, Samples/cURL tabs) */
.OAPath .sm\:grid-cols-2 > :nth-child(2) {
  display: none !important;
}
</style>

<ClientOnly>
  <OASpec spec-url="../openapi.json" :tags="['task-controller']" />
</ClientOnly>

## Job Status ↔ Task Mapping

Every job has an internal `JobStatus`, surfaced on the `Task` resource both as the standard FHIR
`Task.status` and, with the original TORCH status preserved verbatim, as `Task.businessStatus`
(system `https://medizininformatik-initiative.de/torch/job-status`). Use `businessStatus` if you need to
distinguish between the three different `in-progress` sub-states.

| `JobStatus`             | `Task.status` | `businessStatus` code   | Meaning                                    |
|--------------------------|----------------|--------------------------|---------------------------------------------|
| `PENDING`                | `requested`    | `PENDING`                | Queued, not yet claimed by a worker          |
| `RUNNING_GET_COHORT`     | `in-progress`  | `RUNNING_GET_COHORT`     | Fetching the cohort (CQL/Flare query running)|
| `RUNNING_PROCESS_BATCH`  | `in-progress`  | `RUNNING_PROCESS_BATCH`  | Extracting patient batches                   |
| `RUNNING_PROCESS_CORE`   | `in-progress`  | `RUNNING_PROCESS_CORE`   | Extracting core (non-patient) data           |
| `PAUSED`                 | `on-hold`      | `PAUSED`                 | Paused via `$pause`                          |
| `TEMP_FAILED`            | `on-hold`      | `TEMP_FAILED`            | Recoverable failure; will be retried         |
| `COMPLETED`              | `completed`    | `COMPLETED`              | Finished successfully                        |
| `FAILED`                 | `failed`       | `FAILED`                 | Terminal failure                             |
| `CANCELLED`              | `cancelled`    | `CANCELLED`              | Cancelled via `$cancel`                      |

A job's status is only final (no further transitions) for `COMPLETED`, `FAILED`, and `CANCELLED`.

## Priority

`Task.priority` maps onto an internal two-level priority used for worker scheduling:

| FHIR `Task.priority`        | Internal `JobPriority` |
|------------------------------|-------------------------|
| `asap`, `stat`, `urgent`      | `HIGH`                  |
| `routine` (or absent)         | `NORMAL`                |

`HIGH`-priority jobs are picked up by workers ahead of `NORMAL` ones (see [`PUT /fhir/Task/{id}`](#updating-priority)
below to change it after creation).

---

## Searching Tasks

`GET /fhir/Task` returns a FHIR `Bundle` (`searchset`) of matching tasks. Both parameters are optional and may be
combined; each accepts a comma-separated list:

- `_id` — job UUIDs to include
- `status` — one or more of the `JobStatus` values from the table above

By default, unsupported or invalid parameter values are silently ignored. Send `Prefer: handling=strict` to instead
get a `400` with an `OperationOutcome` listing exactly what was wrong:

```sh
curl -s 'http://localhost:8080/fhir/Task?status=RUNNING_PROCESS_BATCH,RUNNING_PROCESS_CORE' \
  -H 'Prefer: handling=strict'
```

## Reading a Single Task

```sh
curl -s 'http://localhost:8080/fhir/Task/<jobId>'
```

Returns `404` with an `OperationOutcome` if the job doesn't exist.

## Updating Priority

`PUT /fhir/Task/{id}` currently only supports changing **priority** — the request body must be a full `Task`
resource, but only `Task.priority` is read.

This endpoint uses optimistic locking: the `If-Match` header must carry the current version as a weak ETag
(`W/"<version>"`, taken from `Task.meta.versionId`). Without it, the request fails with `428 Precondition Required`;
with a stale version, it fails with `409 Conflict` (the underlying job version may have moved on, e.g. because a
batch finished processing in the meantime).

```sh
curl -s -X PUT 'http://localhost:8080/fhir/Task/<jobId>' \
  -H 'Content-Type: application/fhir+json' \
  -H 'If-Match: W/"3"' \
  -d '{"resourceType":"Task","id":"<jobId>","status":"in-progress","intent":"order","priority":"asap"}'
```

## Lifecycle Operations

Three operations transition a job between states. All three return the updated `Task`, or `409 Conflict` if the job
isn't in a state the operation allows:

| Operation  | Endpoint                       | Valid from                                              | Resulting status        |
|------------|----------------------------------|----------------------------------------------------------|--------------------------|
| `$pause`   | `POST /fhir/Task/{id}/$pause`   | Any `RUNNING_*` state                                     | `PAUSED`                |
| `$resume`  | `POST /fhir/Task/{id}/$resume`  | `PAUSED`                                                   | The state it was paused from |
| `$cancel`  | `POST /fhir/Task/{id}/$cancel`  | Any non-terminal state (i.e. not already `COMPLETED`/`FAILED`/`CANCELLED`) | `CANCELLED`              |

```sh
curl -s -X POST 'http://localhost:8080/fhir/Task/<jobId>/$pause'
curl -s -X POST 'http://localhost:8080/fhir/Task/<jobId>/$resume'
curl -s -X POST 'http://localhost:8080/fhir/Task/<jobId>/$cancel'
```

## Deleting a Task

`DELETE /fhir/Task/{id}` removes the job **and its associated result data** (NDJSON output, diagnostics report) from
disk. It returns `204 No Content` on success, `404` if the job doesn't exist, and `500` if the result files couldn't
be removed.

```sh
curl -s -X DELETE 'http://localhost:8080/fhir/Task/<jobId>' -o /dev/null -w '%{http_code}\n'
```

---

## Example Walkthrough

```sh
# 1. Find all currently running jobs
curl -s 'http://localhost:8080/fhir/Task?status=RUNNING_GET_COHORT,RUNNING_PROCESS_BATCH,RUNNING_PROCESS_CORE'

# 2. Inspect one of them
curl -s 'http://localhost:8080/fhir/Task/<jobId>'

# 3. Bump its priority (note the version from step 2's Task.meta.versionId)
curl -s -X PUT 'http://localhost:8080/fhir/Task/<jobId>' \
  -H 'Content-Type: application/fhir+json' \
  -H 'If-Match: W/"3"' \
  -d '{"resourceType":"Task","id":"<jobId>","status":"in-progress","intent":"order","priority":"asap"}'

# 4. Pause it, then resume it later
curl -s -X POST 'http://localhost:8080/fhir/Task/<jobId>/$pause'
curl -s -X POST 'http://localhost:8080/fhir/Task/<jobId>/$resume'

# 5. Or cancel it outright, and clean up its result data once you're done with it
curl -s -X POST 'http://localhost:8080/fhir/Task/<jobId>/$cancel'
curl -s -X DELETE 'http://localhost:8080/fhir/Task/<jobId>'
```
