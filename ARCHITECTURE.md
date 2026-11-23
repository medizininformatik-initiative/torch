# Architecture

Detailed reference. Read when working on the request lifecycle, extraction pipeline, or service integration. Day-to-day orientation lives in `AGENTS.md`.

## Request lifecycle
1. `POST /fhir/$extract-data` → `FhirController` validates the CRTDL, creates a `Job` via `JobPersistenceService`, returns `202 Accepted` with a `Content-Location` header pointing to `/fhir/__status/{jobId}`.
2. `JobScheduler` runs a fixed worker-pool that polls `JobPersistenceService` for `WorkUnit`s to execute.
3. Three `WorkUnit` types form the job pipeline (sealed interface):
   - `ProcessCohortWorkUnit` — calls `CohortQueryService` for matching patient IDs (Flare or CQL per `torch.useCql`).
   - `ProcessBatchWorkUnit` — calls `ExtractDataService.processBatch()` for a patient batch.
   - `ProcessCoreWorkUnit` — calls `ExtractDataService.processCore()` for non-patient-scoped resources.
4. Completed NDJSON output is written under `torch.results.dir` (default `output/`) and served by nginx.

## Extraction pipeline (`ExtractDataService`)
For each patient batch, the reactive pipeline runs:
1. **Consent resolution** — `ConsentHandler` computes per-patient consent periods; patients without a valid period are filtered out (skips batch if none consent). Skipped when `torch.disableConsentCalculation` is set or the CRTDL has no consent codes; all patients are then treated as fully consented.
2. **Patient compartment load** — `DirectResourceLoader` fetches resources directly from FHIR.
3. **Reference resolution** — `ReferenceResolver` resolves cross-resource references.
4. **Cascading delete** — `CascadingDelete` removes resources whose must-have constraints are violated.
5. **Post-cascade must-have check** — removes patients whose direct-load groups have unmet must-haves.
6. **Copy + Redact** — `BatchCopierRedacter` applies StructureDefinition-driven attribute filters.
7. **Write NDJSON** — `ResultFileManager` persists per-batch and core output files.

`ExtractDataService.processCore()` runs the same stages without consent on non-patient resources; an unmet must-have of a directly loaded group there fails the job (`FAILED`) instead of excluding patients.

Diagnostics are recorded per batch in `BatchDiagnostics`: `BatchExclusions` collects patient and resource exclusions, `BatchDetails` per-`PipelineStage` timings. `DiagnosticsStore` writes them into a directory per batch, so a re-processed batch only touches its own files. Errors bubble up as `Mono#error`; `RetryabilityUtil` decides retry vs permanent fail.

## Key concepts
- **CRTDL** (`model/crtdl/`) — JSON input with `cohortDefinition` (structured query) and `dataExtraction` (attribute groups per resource type). `CrtdlValidatorService` validates → `AnnotatedCrtdl`.
- **AnnotatedCrtdl / AnnotatedAttributeGroup** — enriched CRTDL with StructureDefinition metadata resolved and attached; produced once at job creation, reused across all batches.
- **StructureDefinitionHandler** — loads all FHIR StructureDefinition JSON from `ontology/structureDefinitions/` at startup.
- **JobPersistenceService** — in-memory `ConcurrentHashMap` job registry backed by on-disk JSON (`output/<jobId>/job.json`); jobs recovered from disk on restart. On job completion `DiagnosticsStore` merges the per-batch diagnostics into `output/<jobId>/reports/`:
  - `patient-exclusions.csv`, `resource-exclusions.csv` — one row per excluded patient or resource.
  - `raw-provisions.csv`, `final-periods.csv`, `consent-considered-resources.csv`, `consent-conflicts.csv` — consent diagnostics.
  - `job-summary.json` — FHIR OperationOutcome with job-level totals, stage timings, and exclusion counts.
- **Consent** — `ConsentHandler` → `ConsentFetcher` → `ConsentAdjuster` → `ConsentCalculator`. Code mapping in `mappings/type_to_consent.json`.
- **Cohort query** — `torch.useCql`: CQL path uses `CqlClient` against Blaze's `$evaluate-measure`; Flare path POSTs a structured query to Flare.
- **`Acc<T>`** — immutable accumulator record carrying `List<Issue>` alongside a value through the pipeline without throwing on warnings.

## External services (via `application.yml` / env vars)
| Service | Config key | Purpose |
|---|---|---|
| FHIR server (Blaze) | `torch.fhir.url` | Resource storage and CQL evaluation |
| Flare | `torch.flare.url` | Structured-query → patient list (when `torch.useCql=false`) |
| nginx | `torch.output.file.server.url` | Serves NDJSON output files |
