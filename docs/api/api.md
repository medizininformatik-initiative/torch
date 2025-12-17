# API

TORCH provides a FHIR REST API for extracting data based on the Clinical Resource Transfer Definition Language (CRTDL).
It implements the FHIR [Asynchronous Bulk Data Request Pattern](http://hl7.org/fhir/R5/async-bulk.html).

### Key Features of the API

- **Asynchronous Requests**: Supports long-running data extraction tasks.
- **FHIR Compliant**: Adheres to FHIR standards for resource representation.
- **CRTDL Integration**: Uses CRTDL definitions to specify data extraction rules.

### API Endpoints

- **`/$extract-data`**: Initiates a data extraction job based on a CRTDL definition.
- **`/__status`**: Checks the status of an ongoing data extraction job.
- **`/metadata`**: Provides metadata about the TORCH server and its capabilities.

## TORCH REST API (based on FHIR Bulk Data Request)

Torch implements the FHIR [Asynchronous Bulk Data Request Pattern][1].

### $extract-data

The $extract-data endpoint implements the kick-off request in the Async Bulk Pattern. It receives a FHIR parameters
resource with a **_crtdl_** parameter containing a
valueBase64Binary [CRTDL](https://github.com/medizininformatik-initiative/clinical-resource-transfer-definition-language).
In all examples torch is configured with the base url **`http://localhost:8080`**.

```sh
scripts/create-parameters.sh src/test/resources/CRTDL/CRTDL_observation.json | curl -s 'http://localhost:8080/fhir/$extract-data' -H "Content-Type: application/fhir+json" -d @- -v
```

The Parameters resource created by `scripts/create-parameters.sh` look like this:

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

#### Response - Error (e.g. unsupported search parameter)

* HTTP Status Code of 4XX or 5XX

#### Response - Success

* HTTP Status Code of 202 Accepted
* Content-Location header with the absolute URL of an endpoint for subsequent status requests (polling location)


### Status Request

Torch provides a Status Request Endpoint which can be called using the Content-Location returned from the extract Data Endpoint.

```sh
curl -s http://localhost:8080/fhir/__status/{location} 
```

#### Response - In-Progress

* HTTP Status Code of 202 Accepted

#### Response - Error

* HTTP status code of 4XX or 5XX
* Content-Type header of application/fhir+json
* Operation Outcome with fatal issue

#### Response - Complete

* HTTP status of 200 OK
* Content-Type header of application/fhir+json
* A body containing a JSON file describing the file links to the batched transformation results

```sh
curl -s 'http://localhost:8080/fhir/$extract-data' -H "Content-Type: application/fhir+json" -d '<query>'
```

The result looks something like this:

```json
{
  "requiresAccessToken": false,
  "output": [
    {
      "type": "Bundle",
      "url": "http://localhost:8080/da4a1c56-f5d9-468c-b57a-b8186ea4fea8/6c88f0ff-0e9a-4cf7-b3c9-044c2e844cfc.ndjson"
    },
    {
      "type": "Bundle",
      "url": "http://localhost:8080/da4a1c56-f5d9-468c-b57a-b8186ea4fea8/a4dd907c-4d98-4461-9d4c-02d62fc5a88a.ndjson"
    },
    {
      "type": "Bundle",
      "url": "http://localhost:8080/da4a1c56-f5d9-468c-b57a-b8186ea4fea8/f33634bd-d51b-463c-a956-93409d96935f.ndjson"
    }
  ],
  "request": "http://localhost:8080//fhir/$extract-data",
  "deleted": [],
  "transactionTime": "2024-09-05T12:30:32.711151718Z",
  "error": []
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
