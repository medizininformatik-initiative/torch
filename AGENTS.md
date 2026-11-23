# Agent Instructions

## Project Goal

TORCH (Transfer Of Resources in Clinical Healthcare) extracts FHIR data for MII Data Use Projects. It takes
a CRTDL (cohort definition + data extraction), resolves the cohort via CQL or Flare, loads the patients'
resources from a FHIR server (Blaze), applies consent, reference resolution, must-have checks and
profile-driven redaction, and writes the result as NDJSON bundles served by nginx.

## Commands

Requires JDK 21.

* Build: `mvn -P download-ontology -B package -DskipTests`
* All unit + integration tests: `mvn -P download-ontology -B verify`
* Single test class: `mvn -pl . -Dtest=JobSchedulerTest test`
* Single IT class: `mvn -pl . -Dit.test=DataStoreIT -Dtest=none -Dsurefire.failIfNoSpecifiedTests=false verify`
* Image: `docker build -t torch:latest .`
* Black-box tests (need `torch:latest`): `mvn -P blackbox-integration-tests -B verify`
* Dev stack: `docker compose up` (Blaze :8082, Flare :8084, nginx :8085, TORCH :8086)
* Docs: `npm run docs:dev` / `npm run docs:build` in `docs/`

## Project Structure

```
src/main/java/de/medizininformatikinitiative/torch/
  rest/          # FhirController: $extract-data, status, Task API
  jobhandling/   # JobScheduler, JobPersistenceService, WorkUnits
  service/       # ExtractDataService and pipeline steps
  model/crtdl/   # CRTDL input records
  consent/       # consent fetching and calculation
  diagnostics/   # BatchDiagnostics, exclusions, stage timings
mappings/        # type_to_consent.json
docs/            # VitePress documentation site (published)
ontology/        # downloaded StructureDefinitions (not committed)
output/          # runtime job data (not committed)
```

For the request lifecycle, the extraction pipeline and the external services in detail, read
`ARCHITECTURE.md`. Do not reconstruct cross-component behaviour from memory.

* **Request** — `POST /fhir/$extract-data` → `FhirController` validates the CRTDL, creates a job, answers
  `202 Accepted` with a `Content-Location` status URL.
* **CRTDL** — `CrtdlValidatorService` validates it into an `AnnotatedCrtdl`. StructureDefinition metadata is
  resolved once at job creation and reused across batches.
* **Job pipeline** — `JobScheduler` polls `JobPersistenceService` for the sealed `WorkUnit`s
  `ProcessCohortWorkUnit`, `ProcessBatchWorkUnit` and `ProcessCoreWorkUnit`. Jobs live in memory and in
  `output/<jobId>/job.json` and are recovered on restart.
* **Cohort query** — `torch.useCql` selects CQL (`CqlClient`, Blaze `$evaluate-measure`) or Flare.
* **Extraction** — `ExtractDataService` runs per batch: consent → compartment load (`DirectResourceLoader`)
  → reference resolution (`ReferenceResolver`) → cascading delete (`CascadingDelete`) → must-have check
  → copy/redact (`BatchCopierRedacter`) → NDJSON (`ResultFileManager`).
* **Consent** — `ConsentHandler` → `ConsentFetcher` → `ConsentAdjuster` → `ConsentCalculator`.
* **StructureDefinitions** — loaded at startup from `ontology/structureDefinitions/` by
  `StructureDefinitionHandler`.

## Code Conventions

* **Reactive:** Spring Boot 4 / Project Reactor WebFlux. All I/O in the reactive path must be
  non-blocking. Offload unavoidable blocking work (file I/O, blocking scheduling) to
  `Schedulers.boundedElastic()`.
* **Errors:** Propagate errors as `Mono#error`. `RetryabilityUtil` decides retryable vs. permanent failures.
* **Warnings:** Do not throw exceptions for warnings. Record them in the per-batch `BatchDiagnostics`:
  `BatchExclusions` for patient/resource exclusions, `BatchDetails.nanosElapsed` for per-`PipelineStage`
  timings.
* **Jackson:** Records deserialized by Jackson need `@JsonProperty` on every component. This includes
  `model/crtdl/` and every record passed to `readValue`, `bodyToMono`, `bodyToFlux` or `@RequestBody`.
* **Think before coding:** State assumptions that affect the implementation. Surface ambiguity and
  tradeoffs instead of choosing silently. Ask when the ambiguity could change behaviour, scope,
  compatibility or architecture; otherwise choose the smallest reasonable interpretation and state it.
  Push back when a simpler solution exists.
* **Simplicity:** Apply YAGNI, then SOLID where it helps, then DRY only for stable shared concepts. No
  speculative features, configurability or extension points. No abstraction for a single use. Do not
  handle states the contract rules out. Do not keep backward compatibility when the task permits a
  breaking change.
* **Surgical changes:** Change only what the task needs and match the surrounding style. Do not
  refactor, reformat or rename unrelated code. Mention unrelated problems instead of fixing them. Remove
  only code that your own change made obsolete. Every changed line must be explainable by the task.
* **Comments and Javadoc:** Document intent and contract, not implementation steps. Add Javadoc to public
  APIs and to methods whose contract, side effects or reactive behaviour (laziness, empty completion,
  error propagation, scheduler requirements) is not clear from name and signature. Follow the Javadoc
  style of the package. Do not restate the code. Use `@param`, `@return` and `@throws` only when they add
  information beyond names and types. Update Javadoc that your change makes wrong.

## Testing

* Before writing tests or changing production code, read `.claude/skills/torch-testing/SKILL.md`.
* **Test first:** Do not change production code without a failing test that captures the new behaviour.
  For a bug fix, write the regression test first and see it fail. Not required for changes without
  observable behaviour (pure refactoring, logging, documentation).
* Test through public behaviour, not internals.
* **Validation:** Cover accepted and rejected inputs.
* **Refactoring:** Preserve externally observable behaviour.
* **Diagnostics:** Use the minimum targeted diagnostics to test a hypothesis. Do not add speculative
  logging or broad instrumentation.
* `*Test.java` — plain JUnit 5, no containers.
* `*IT.java` — integration tests. With the `test` profile, `TestConfig` starts Blaze and Flare via
  `ContainerManager` and `src/test/resources/docker-compose.yml`.
* `*BlackBoxIT.java` — need a locally built `torch:latest` and the `blackbox-integration-tests` profile.
* Run the narrowest test that gives sufficient evidence. Do not run the full IT or black-box suite for a
  local change.
* For races and concurrency bugs, confirm that the test fails on the unfixed code before you trust it.

## Documentation

* When a change adds config or error codes or alters client-observable behaviour, read
  `.claude/skills/torch-docs/SKILL.md`.
* Document new `TORCH_*` environment variables in `docs/configuration.md` (heading, description,
  default, `Since` badge).
* Add new error codes to `docs/implementation/error-codes.md`.
* User-facing changes need an update of the matching page under `docs/`.
* `CHANGELOG.md` is written at release time from the milestone (see `DEVELOPMENT.md`), not in feature PRs.
* Update `ARCHITECTURE.md` and the Project Structure section above when a change affects the described
  pipeline, job lifecycle or component responsibilities.

## Workflow

* **Every change requires a GitHub issue.** If none exists, create one before writing code. Set the
  issue type (`Bug`, `Feature`, `Task`), not a `bug`/`feature` label: `gh issue create --type <type>`, or
  `gh api repos/medizininformatik-initiative/torch/issues -f type=<type> ...` with an older `gh`. Use
  `Bug` only if something is actually broken in production code.
* Fetch issue details with `gh issue view <issue-number>`.
* Branch from `main` as `<issue-number>-<short-title>` (e.g. `1392-level1-only-consent-global-deny`).
  All PRs target `main`. Never push to `main`.
* Issue title, commit subject and PR title are identical: imperative mood, title case, starting with a
  verb (e.g. `Fix Expired Page Links In Paged Search`). No Conventional Commit prefixes.
* One commit per issue, with only `Closes #<issue-number>` in the body.
* Update branches by rebasing onto `main`, never by merging.
* No `Co-Authored-By` or other AI/tool attribution in commits or PRs.
* Do not comment on GitHub issues or PRs, or post reviews, without asking first.
* Never commit secrets, `output/` or downloaded `ontology/` data.

## Procedures

Step-by-step procedures live in skills. Other agents read them as plain markdown.

* `.claude/skills/torch-testing/SKILL.md` — write and run tests: test level, red/green, the narrowest
  test command.
* `.claude/skills/torch-docs/SKILL.md` — update docs pages, `TORCH_*` variables, error codes and
  `ARCHITECTURE.md` for a change.
* `.claude/skills/torch-pr/SKILL.md` — finish an issue: checklist, squash into one commit on top of
  `main`, force-push, open or update the PR.

Agents load `torch-testing` and `torch-docs` when needed. `torch-pr` is started only by a human with
`/torch-pr`.

## Releases

See `DEVELOPMENT.md`.
