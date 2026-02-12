<style>

/* 1. Hide the "Servers" header by ID */
#servers, 
h2#servers {
    display: none !important;
}

/* 2. Hide the server selection box that immediately follows the header */
h2#servers + div,
.OAServers,
.v-openapi-servers {
    display: none !important;
}

/* 3. Hide the "Try It Out" buttons and their containers */
.OAPathContentEnd button {
    display: none !important;
}

/* 4. Hide the samples header */
.OAPathContentEnd h2[id$="-samples"] {
    display: none !important;
}

/* 5. Hide the code snippet blocks (curl, etc.) */
.OAPathContentEnd .vp-code-group {
    display: none !important;
}

/* 6. Hide the playground section */
.OAPathContentEnd .flex.flex-col.gap-2 {
    display: none !important;
}

/* 7. Make the responses column full width */
.OAPath .sm\:grid-cols-2 {
    grid-template-columns: 1fr !important;
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
