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
  <OASpec spec-url="../openapi.json" />
</ClientOnly>

## Implementation Details

The TORCH REST API follows the [Asynchronous Bulk Data Request Pattern][1].

### $extract-data Kick-off

The `$extract-data` endpoint initiates the extraction. It expects a FHIR
`Parameters` resource containing a Base64 encoded **CRTDL** definition.
resource with a **_crtdl_** parameter containing a
valueBase64Binary [CRTDL](https://github.com/medizininformatik-initiative/clinical-resource-transfer-definition-language).
In all examples torch is configured with the base url **`http://localhost:8080`**.

```sh
scripts/create-parameters.sh src/test/resources/CRTDL/CRTDL_observation.json | curl -s 'http://localhost:8080/fhir/$extract-data' -H "Content-Type: application/fhir+json" -d @- -v
```

## Request Body Structure

The Parameters resource created by [
`create-parameters.sh`](https://github.com/medizininformatik-initiative/torch/blob/main/scripts/create-parameters.sh) look like this:

```
{
  "resourceType": "Parameters",
  "parameter": [
    {
      "name": "crtdl",
      "valueBase64Binary": "<Base64 encoded CRTDL>"
    }
  ]
}
```

Optionally patient ids can be submitted for a known cohort, bypassing the cohort selection in the CRTDL:

```
{
  "resourceType": "Parameters",
  "parameter": [
    {
      "name": "crtdl",
      "valueBase64Binary": "<Base64 encoded CRTDL>"
    },
    {
      "name": "patient",
      "valueString": "<Patient Id 1>"
    },
    {
      "name": "patient",
      "valueString": "<Patient Id 2>"
    }
  ]
}
```

### Result Files

Upon successful completion, the data extraction results consist of **multiple NDJSON files**:

- **One NDJSON file per patient batch**, each containing **FHIR transaction Bundles**
- **One `core.ndjson` file**, containing a **single FHIR transaction Bundle**
  with all **non-patient-specific resources**

#### Patient Batch Files

Each patient batch NDJSON file contains:

- **One transaction Bundle per patient**
- Each Bundle includes:
    - exactly one `Patient` resource
    - all patient-specific resources extracted according to the CRTDL (e.g. `Encounter`, `Condition`,
      `Observation`, etc.)

Patient batch files can be processed **independently and in any order**.

#### Core Bundle File

The `core.ndjson` file contains:

- a single transaction Bundle
- all **non-patient-specific resources** (e.g. shared reference data such as `Medication`)

For **referential integrity**, `core.ndjson` **must be processed before** any patient batch files.  
For this reason, the provided [transfer script](./../getting-started.md#transfer-script) uploads `core.ndjson` first.

If `core.ndjson` contains resources but **no patient batch files are present**, this indicates that:

> **No patient survived the extraction**, but core (non-patient) resources were still loaded.

---

### Job Completion Manifest Extensions

When a job completes, the `GET /fhir/__status/{jobId}` response follows the
[FHIR Async Bulk Data manifest format][1].
The `output` array contains **only FHIR NDJSON Bundle files** — one file per patient batch (each
containing one Bundle per patient) and one `core.ndjson`. Non-resource data (diagnostics, issues) is never placed in `output`, because
downstream consumers process every entry there as a FHIR resource file.

TORCH-specific data is surfaced in the `extension` array. Three extension URLs are defined:

| `url`                                                     | Content                                                                                                                                      | Present when                                                   |
|-----------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------|----------------------------------------------------------------|
| `https://torch.mii.de/fhir/StructureDefinition/torch-job` | Full serialised job state (batches, timings, version)                                                                                        | Always                                                         |
| `torch-job-diagnostics-summary`                           | Per-exclusion-kind patient and resource counts, cohort total and final patient total                                                         | Diagnostics were collected (i.e. at least one batch completed) |
| `torch-job-diagnostics`                                   | `valueUrl` pointing to the full diagnostics OperationOutcome file (per-stage timings, per-criterion counts and timing); see [Job Diagnostics](./diagnostics.md) | Same as above |
| `torch-job-issues`                                        | List of `{severity, msg, diagnostics}` objects recording warnings and errors that occurred during processing (e.g. skipped batches, retries) | At least one issue was recorded                                |

Example completed manifest:

```json
{
  "transactionTime": "2026-01-15T10:30:00Z",
  "request": "http://localhost:8080/fhir/__status/<jobId>",
  "requiresAccessToken": false,
  "output": [
    {
      "type": "NDJSON Bundle",
      "url": "http://fileserver/<jobId>/<batchId>.ndjson"
    },
    {
      "type": "NDJSON Bundle",
      "url": "http://fileserver/<jobId>/core.ndjson"
    }
  ],
  "extension": [
    {
      "url": "https://torch.mii.de/fhir/StructureDefinition/torch-job",
      "valueObject": {
        "id": "<jobId>",
        "status": "COMPLETED",
        ...
      }
    },
    {
      "url": "torch-job-diagnostics-summary",
      "valueObject": {
        "cohortPatientsTotal": 100,
        "finalPatientsTotal": 87,
        "exclusionsByKind": [
          {
            "kind": "CONSENT",
            "patientsExcluded": 5,
            "resourcesExcluded": 0
          },
          {
            "kind": "MUST_HAVE",
            "patientsExcluded": 8,
            "resourcesExcluded": 42
          }
        ]
      }
    },
    {
      "url": "torch-job-diagnostics",
      "valueUrl": "http://fileserver/<jobId>/reports/job-summary.json"
    },
    {
      "url": "torch-job-issues",
      "valueObject": [
        {
          "severity": "WARNING",
          "msg": "Batch <id> skipped: no consenting patients",
          "diagnostics": ""
        }
      ]
    }
  ]
}
```

---

### Example

Given:

- Bundle size: `20`
- `100` patients
- Per patient:
    - `1` Encounter
    - `1` Diagnosis
- Shared references:
    - `30` Medication resources

The extraction result will contain:

- **5 patient batch NDJSON files** (each containing 20 transaction Bundles)
- **1 `core.ndjson` file**

Each **patient batch file** contains:

- one transaction Bundle per patient
- each Bundle includes:
    - `1` Patient
    - `≥1` Diagnosis
    - `≥1` Encounter

The **`core.ndjson`** contains:

- a single transaction Bundle
- `30` Medication resources

[1]: <http://hl7.org/fhir/R5/async-bulk.html>
