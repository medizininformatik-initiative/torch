# Configuration

Torch is configured using environment variables that control its behavior and integration with a FHIR server. This allows
for flexible deployment and customization based on the specific requirements of your environment.

## Configuration Overview

This document provides an overview of the environment variables used to configure TORCH, including their default values
and descriptions.
These variables can be set in the application.yml file during development or as system environment variables when running the application.

When running TORCH as a container, it is recommended to set these variables or pass them as environment variables during container startup.
## Backend

### Environment Variables

#### `SERVER_PORT` <Badge type="warning" text="Since 1.0.0-alpha"/>

The port on which TORCH server listens for incoming connections.
If running a container it is the port inside the container.

**Default:** `8080`

---

#### `TORCH_PROFILE_DIR` <Badge type="warning" text="Since 1.0.0-alpha"/>

The directory path where profile definitions are stored.

**Default:** `structureDefinitions`

---

#### `TORCH_MAPPING_CONSENT` <Badge type="warning" text="Since 1.0.0-alpha"/>

Path to the JSON file containing consent mappings formatted for FHIR.

**Default:** `mappings/consent-mappings_fhir.json`

---

#### `TORCH_MAPPING_TYPE_TO_CONSENT` <Badge type="warning" text="Since 1.0.0-alpha"/>

File that maps FHIR Resource Types to their associated time fields used to check consent validity.

**Default:** `mappings/type_to_consent.json`

---

#### `TORCH_FHIR_USER` <Badge type="warning" text="Since 1.0.0-alpha"/>

The username used for authentication with the FHIR server.

**Default:** – (none)

---

#### `TORCH_FHIR_PASSWORD` <Badge type="warning" text="Since 1.0.0-alpha"/>

The password used for authentication with the FHIR server.

**Default:** – (none)

---

#### `TORCH_FHIR_OAUTH_ISSUER_URI` <Badge type="warning" text="Since 1.0.0-alpha"/>

The URI of the OAuth issuer used for OAuth authentication.

**Default:** – (none)

---

#### `TORCH_FHIR_OAUTH_CLIENT_ID` <Badge type="warning" text="Since 1.0.0-alpha"/>

Client ID used for OAuth authentication.

**Default:** – (none)

---

#### `TORCH_FHIR_OAUTH_CLIENT_SECRET` <Badge type="warning" text="Since 1.0.0-alpha"/>

Client secret used for OAuth authentication.

**Default:** – (none)

---

#### `TORCH_FHIR_URL` <Badge type="warning" text="Since 1.0.0-alpha"/>

Base URL of the FHIR server that TORCH connects to.

**Default:** – (none)

---

#### `TORCH_FHIR_MAX_CONNECTIONS` <Badge type="warning" text="Since 1.0.0-alpha"/>

Maximum number of concurrent connections allowed to the FHIR server. Must be at least `TORCH_MAXCONCURRENCY + 1`.

**Default:** `5`

---

#### `TORCH_FHIR_PAGE_COUNT` <Badge type="warning" text="Since 1.0.0-alpha"/>

Number of entries per page in FHIR search responses.

**Default:** `500`

---

#### `TORCH_FHIR_DISABLE_ASYNC` <Badge type="warning" text="Since 1.0.0-alpha"/>

Set to `true` to disable the use of the Asynchronous Interaction Request Pattern for FHIR operations.

**Default:** `false`

---

#### `TORCH_FLARE_URL` <Badge type="warning" text="Since 1.0.0-alpha"/>

Base URL of the FLARE server used in the pipeline.

**Default:** – (none)

---

#### `TORCH_RESULTS_DIR` <Badge type="warning" text="Since 1.0.0-alpha"/>

Directory path where results are stored.

**Default:** `output/`

---

#### `TORCH_RESULTS_PERSISTENCE` <Badge type="warning" text="Since 1.0.0-alpha"/>

ISO 8601 duration indicating how long result files are persisted (e.g., `PT2160H` means 90 days).

**Default:** `PT2160H`

---

#### `TORCH_BATCHSIZE` <Badge type="warning" text="Since 1.0.0-alpha"/>

Size of data batches processed at once.

**Default:** `500`

---

#### `TORCH_MAXCONCURRENCY` <Badge type="warning" text="Since 1.0.0-alpha"/>

Maximum level of concurrency for data processing operations.

**Default:** `4`

---

#### `TORCH_MAPPINGS_FILE` <Badge type="warning" text="Since 1.0.0-alpha"/>

Path to the file containing ontology mappings defined using Clinical Quality Language (CQL).

**Default:** `ontology/mapping_cql.json`

---

#### `TORCH_BUFFERSIZE` <Badge type="warning" text="Since 1.0.0-alpha"/>

Size (in MB) of the buffer used by the web client interacting with the FHIR server.

**Default:** `100`

---

#### `TORCH_CONCEPT_TREE_FILE` <Badge type="warning" text="Since 1.0.0-alpha"/>

File containing the concept tree mapping used for resource classification.

**Default:** `ontology/mapping_tree.json`

---

#### `TORCH_DSE_MAPPING_TREE_FILE` <Badge type="warning" text="Since 1.0.0-alpha"/>

File containing the concept tree mapping specifically for DSE (Data Set Extensions).

**Default:** `ontology/dse_mapping_tree.json`

---

#### `TORCH_USE_CQL` <Badge type="warning" text="Since 1.0.0-alpha"/>

Flag indicating whether to enable Clinical Quality Language (CQL) for processing.

**Default:** `true`

---

#### `TORCH_BASE_URL` <Badge type="warning" text="Since 1.0.0-alpha"/>

Base server URL before any proxy, used for accessing TORCH directly.

**Default:** – (none)

---

#### `TORCH_OUTPUT_FILE_SERVER_URL` <Badge type="warning" text="Since 1.0.0-alpha"/>

URL used to access the result files in `TORCH_RESULTS_DIR` via a proxy or file server.

**Default:** – (none)

---

#### `LOG_LEVEL_DE_MEDIZININFORMATIKINITIATIVE_TORCH` <Badge type="warning" text="Since 1.0.0-alpha"/>

Logging level for core TORCH functionality (`error`/ `warn`/ `info`/ `debug`/ `trace`).

**Default:** `info`

---

#### `LOG_LEVEL_CA_UHN_FHIR` <Badge type="warning" text="Since 1.0.0-alpha"/>

Logging level for the HAPI FHIR library (`error`/ `warn`/ `info`/ `debug`/ `trace`, `fatal`, `off`).

**Default:** `error`

---

#### `SPRING_PROFILES_ACTIVE` <Badge type="warning" text="Since 1.0.0-alpha"/>

Active Spring profile used to configure the application context (only needed for development).

**Default:** `active`

---

#### `SPRING_CODEC_MAX_IN_MEMORY_SIZE` <Badge type="warning" text="Since 1.0.0-alpha"/>

Maximum allowed in-memory size for Spring codecs when processing data.

**Default:** `100MB`

---

[5]: https://www.hl7.org/fhir/http.html#async "FHIR Asynchronous Interaction Request Pattern"
