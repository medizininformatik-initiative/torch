# Job Diagnostics

When a job completes, TORCH writes a full diagnostics report as an
[OperationOutcome](https://www.hl7.org/fhir/operationoutcome.html) JSON file served by the file
server alongside the NDJSON output. The URL is provided in the completion manifest via the
`torch-job-diagnostics` extension (see [API — Job Completion Manifest Extensions](./api.md#job-completion-manifest-extensions)).

The report contains two kinds of data:

- **Root extensions** — job-level totals and timing (cohort query, pipeline stages)
- **Issues** — one entry per exclusion criterion, with patient/resource counts and timing

---

## Accessing the Report

The manifest extension carries a direct URL:

```json
{
  "url": "torch-job-diagnostics",
  "valueUrl": "http://fileserver/<jobId>/reports/job-summary.json"
}
```

Fetch it with a plain `GET`.

---

## Root Extensions

| `url`                    | Value type     | Description                                                                 |
|--------------------------|----------------|-----------------------------------------------------------------------------|
| `jobId`                  | `valueString`  | UUID of the job                                                             |
| `cohortPatientsTotal`    | `valueInteger` | Total patients in the cohort across all batches                             |
| `finalPatientsTotal`     | `valueInteger` | Patients remaining after all exclusions                                     |
| `cohortQueryDurationMs`  | `valueInteger` | Wall-clock time for the Flare / CQL cohort query in ms (omitted when 0)    |
| `stage.<name>`           | nested         | Per-pipeline-stage throughput (see [Stage Extensions](#stage-extensions))   |

### Stage Extensions

Each `stage.<name>` extension is a nested object with:

| `url`                 | Value type     | Description                                            |
|-----------------------|----------------|--------------------------------------------------------|
| `durationMs`          | `valueInteger` | Total wall-clock time spent in this stage (ms)         |
| `resourcesProcessed`  | `valueInteger` | Resources (or patients) that passed through the stage  |
| `resourcesPerMinute`  | `valueInteger` | Throughput rate derived from the two fields above      |

Stage names (lower-kebab-case suffixes after `stage.`):

| Stage                  | What is counted as a "resource"         |
|------------------------|-----------------------------------------|
| `consent-fetch`        | patients                                |
| `direct-load`          | FHIR resources loaded                   |
| `reference-resolve`    | FHIR resources after resolution         |
| `cascading-delete`     | FHIR resources remaining after deletion |
| `copy-redact`          | FHIR resources written to output        |

---

## Issues (per-criterion exclusions)

Each `issue` entry corresponds to one exclusion criterion. All have `"severity": "information"`.

The `code` field maps the exclusion kind to a FHIR issue code:

| Exclusion kind            | FHIR `code`     |
|---------------------------|-----------------|
| `MUST_HAVE`               | `business-rule` |
| `CONSENT`                 | `suppressed`    |
| `REFERENCE_NOT_FOUND`     | `not-found`     |
| `REFERENCE_INVALID`       | `structure`     |
| `REFERENCE_OUTSIDE_BATCH` | `informational` |

`details.coding` carries the exclusion kind using system
`https://torch.mii.de/fhir/CodeSystem/exclusion-kind`.

`expression` (when present) holds the FHIRPath attribute reference from the CRTDL.

Each issue has the following extensions:

| `url`               | Value type     | Description                                                  |
|---------------------|----------------|--------------------------------------------------------------|
| `elementId`         | `valueString`  | CRTDL element ID of the criterion (omitted when absent)      |
| `groupRef`          | `valueString`  | Resource group reference (omitted when absent)               |
| `patientsExcluded`  | `valueInteger` | Patients excluded by this criterion                          |
| `resourcesExcluded` | `valueInteger` | Individual resources excluded by this criterion              |
| `durationMs`        | `valueInteger` | Cumulative processing time for this criterion (ms)           |
| `invocations`       | `valueInteger` | Number of times this criterion was evaluated                 |

---

## Example

```json
{
  "resourceType": "OperationOutcome",
  "extension": [
    { "url": "jobId",               "valueString":  "<jobId>" },
    { "url": "cohortPatientsTotal", "valueInteger": 100 },
    { "url": "finalPatientsTotal",  "valueInteger": 87 },
    { "url": "cohortQueryDurationMs", "valueInteger": 1240 },
    {
      "url": "stage.consent-fetch",
      "extension": [
        { "url": "durationMs",         "valueInteger": 850 },
        { "url": "resourcesProcessed", "valueInteger": 100 },
        { "url": "resourcesPerMinute", "valueInteger": 7058 }
      ]
    },
    {
      "url": "stage.direct-load",
      "extension": [
        { "url": "durationMs",         "valueInteger": 3200 },
        { "url": "resourcesProcessed", "valueInteger": 4300 },
        { "url": "resourcesPerMinute", "valueInteger": 80625 }
      ]
    }
  ],
  "issue": [
    {
      "severity": "information",
      "code": "suppressed",
      "details": {
        "coding": [
          {
            "system": "https://torch.mii.de/fhir/CodeSystem/exclusion-kind",
            "code": "CONSENT"
          }
        ]
      },
      "extension": [
        { "url": "patientsExcluded",  "valueInteger": 5 },
        { "url": "resourcesExcluded", "valueInteger": 0 },
        { "url": "durationMs",        "valueInteger": 320 },
        { "url": "invocations",       "valueInteger": 100 }
      ]
    },
    {
      "severity": "information",
      "code": "business-rule",
      "details": {
        "coding": [
          {
            "system": "https://torch.mii.de/fhir/CodeSystem/exclusion-kind",
            "code": "MUST_HAVE"
          }
        ],
        "text": "Observation.code"
      },
      "expression": [ "Observation.code" ],
      "extension": [
        { "url": "elementId",         "valueString":  "obs-code-check" },
        { "url": "groupRef",          "valueString":  "Observation" },
        { "url": "patientsExcluded",  "valueInteger": 8 },
        { "url": "resourcesExcluded", "valueInteger": 42 },
        { "url": "durationMs",        "valueInteger": 610 },
        { "url": "invocations",       "valueInteger": 95 }
      ]
    }
  ]
}
```
