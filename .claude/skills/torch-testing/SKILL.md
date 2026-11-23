---
name: torch-testing
description: Write and run tests for a TORCH change - test-first for bug fixes and behaviour changes, choosing unit vs integration tests, and the narrowest test command. Use before writing or changing tests or production code.
---

# Write and run tests for a change

The rules are in `AGENTS.md`, Testing. This is the procedure.

## 1. Observable behaviour?

Decide whether the change alters observable behaviour. If not (pure refactoring, logging, docs), skip
to step 5.

## 2. Choose the level

* **Unit test (`*Test.java`, default):** plain JUnit 5, no Spring context, no containers. Put it next to
  the existing test of the class under test, in the same package under `src/test/java`.
* **Integration test without containers:** if you need real StructureDefinitions, copier or redaction,
  use `setup/IntegrationTestSetup` (see `util/ProvisionExtractorIT`).
* **Integration test with Blaze/Flare (`*IT.java`):** only when the behaviour depends on the FHIR server
  or Flare. Use `@SpringBootTest(classes = Torch.class)` with `@ActiveProfiles("test")` (see
  `service/DataStoreIT`). The `test` profile's `config/TestConfig` starts the containers via
  `setup/ContainerManager`, from `src/test/resources/docker-compose.yml`.
* **Black-box tests (`*BlackBoxIT.java`):** only for end-to-end behaviour of the built image. They are
  excluded from the normal failsafe run and need `torch:latest` and `-P blackbox-integration-tests`.

Helpers (paths below `src/test/java/de/medizininformatikinitiative/torch/`):

* `assertions/Assertions.assertThat(Bundle)` / `assertThat(Resource)` — AssertJ-style `BundleAssert`
  and `ResourceAssert`.
* `TestUtils` — e.g. `loadCrtdl(path)`, `emptyJobParams()`, `readCsv(...)`.
* Test data lives in `src/test/resources` (e.g. `CRTDL/`, `CRTDL_Parameters/`, `InputResources/`).

## 3. Red

Write the test, run it (step 5), and confirm that it fails for the expected reason: an assertion on the
new behaviour, not a compile error, a missing container or a missing ontology. For a bug fix, this is
the regression test.

Tests need the downloaded ontology in `ontology/` (`mvn -P download-ontology -B package -DskipTests`
once). A fresh worktree has none, so tests fail there for the wrong reason.

## 4. Green

Make the smallest production change that passes the test.

## 5. Run the narrowest command

Requires JDK 21 (`JAVA_HOME`).

* Single unit test class:
  `mvn -pl . -Dtest=<Class> test`
* Single integration test class (failsafe, skips the unit tests):
  `mvn -pl . -Dit.test=<ClassIT> -Dtest=none -Dsurefire.failIfNoSpecifiedTests=false verify`
* Single method: `-Dtest=<Class>#<method>` or `-Dit.test=<ClassIT>#<method>`.

Never run the full IT or black-box suite for a local change.

## 6. Test design

* Test through public behaviour, not internals.
* For reactive code, use Reactor's `StepVerifier` (used throughout `src/test`) and assert the emitted
  values, empty completion and errors, not only the happy path.
* For validation, cover accepted and rejected inputs.

## 7. Races and concurrency

Run the test against the unfixed code and confirm that it fails before you trust it. A plausible
mechanism is not proof that the test triggers it.

## 8. Report

State which tests you ran, with the exact command, and their result. Say what you could not verify.
