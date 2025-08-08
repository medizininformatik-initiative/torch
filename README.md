# TORCH - Transfer Of Resources in Clinical Healthcare

## Goal

**T**ransfer **O**f **R**esources in **C**linical **H**ealthcare or **Torch** is a project that aims to provide
a service that allows the execution of data extraction queries on a FHIR-server.

The tool will take a **CRTDL** and StructureDefinitions to extract specific resources from a FHIR server.
It first extracts a cohort based on the cohort definition part of the **CRTDL** using either CQL or FLARE and FHIR
Search.
It then extracts resources for the cohort as specified in the cohort extraction part of the **CRTDL**, which specifies
which resources
to extract (Filters) and which attributes to extract for each resource.

The tool internally uses the [HAPI](https://hapifhir.io/) implementation for handling FHIR Resources.

## CRTDL

The **C**linical **R**esource **T**ransfer **D**efinition **L**anguage or **CRTDL** is a JSON format, which specifies a
data request.
This request is composed of two parts:
The cohort definition (for who (which patients) should data be extracted)
The data extraction (what data should be extracted)

## Prerequisites

TORCH interacts with the following components directly:

- a CQL ready FHIR Server like [Blaze](https://github.com/samply/blaze) **OR
  ** [FLARE](https://github.com/medizininformatik-initiative/flare)
- A FHIR Server / FHIR Search API
- Reverse Proxy (NGINX)

The reverse proxy allows for integration into a site's multi-server infrastructure and provides a means of serving
the extracted data.

### Cohort Selection

TORCH supports CQL or FHIR Search for the cohort selection part.

If your FHIR server does not support CQL itself then the FLARE component must be used to extract the
cohort based on the cohort definition of the **CRTDL**.

The cohort evaluation strategy can be set using the TORCH_USE_CQL setting in
the [enviroment variables](#environment-variables)

## Container version

For simplicity torch is integrated in
the [feasibility-triangle](https://github.com/medizininformatik-initiative/feasibility-deploy/tree/main/feasibility-triangle)
of the feasibility-deploy repository, but can also be installed without it.

For the latest build see: https://github.com/medizininformatik-initiative/torch/pkgs/container/torch

## Environment Variables

| Name                                                | Default                             | Description                                                                                          |
|:----------------------------------------------------|:------------------------------------|:-----------------------------------------------------------------------------------------------------|
| SERVER_PORT                                         | 8080                                | The Port of the server to use                                                                        |
| TORCH_PROFILE_DIR                                   | structureDefinitions                | The directory for profile definitions.                                                               |
| TORCH_MAPPING_CONSENT                               | mappings/consent-mappings_fhir.json | The file for consent mappings in FHIR format.                                                        |
| TORCH_MAPPING_TYPE_TO_CONSENT                       | mappings/type_to_consent.json       | The file mapping Resource Types to time fields against which the consent is checked                  |
| TORCH_FHIR_USER                                     | –                                   | The FHIR server user.                                                                                |
| TORCH_FHIR_PASSWORD                                 | –                                   | The FHIR server password.                                                                            |
| TORCH_FHIR_OAUTH_ISSUER_URI                         | –                                   | The URI for the OAuth issuer.                                                                        |
| TORCH_FHIR_OAUTH_CLIENT_ID                          | –                                   | The client ID for OAuth.                                                                             |
| TORCH_FHIR_OAUTH_CLIENT_SECRET                      | –                                   | The client secret for OAuth.                                                                         |
| TORCH_FHIR_URL                                      | –                                   | The base URL of the FHIR server to use.                                                              |
| TORCH_FHIR_MAX_CONNECTIONS                          | 5                                   | The maximum number of concurrent connections to use - has to be (TORCH_MAXCONCURRENCY + 1)           |
| TORCH_FHIR_PAGE_COUNT                               | 500                                 | The number of pages in a FHIR search response.                                                       |
| TORCH_FHIR_DISABLE_ASYNC                            | false                               | Set to `true` in order to disable use of [Asynchronous Interaction Request Pattern][5].              |
| TORCH_FLARE_URL                                     | –                                   | The base URL of the FLARE server to use.                                                             |
| TORCH_RESULTS_DIR                                   | output/                             | The directory for storing results.                                                                   |
| TORCH_RESULTS_PERSISTENCE                           | PT2160H                             | Time Block for result persistence in ISO 8601 <br/> format in hours/minutes/seconds. Default 90 days |
| TORCH_BATCHSIZE                                     | 500                                 | The batch size used for processing data.                                                             |
| TORCH_MAXCONCURRENCY                                | 4                                   | The maximum concurrency level for processing.                                                        |
| TORCH_MAPPINGS_FILE                                 | ontology/mapping_cql.json           | The file for ontology mappings using CQL.                                                            |
| TORCH_BUFFERSIZE                                    | 100                                 | Size in MB of the webclientbuffer that interacts with the FHIR server                                | 
| TORCH_CONCEPT_TREE_FILE                             | ontology/mapping_tree.json          | The file for the concept tree mapping.                                                               |
| TORCH_DSE_MAPPING_TREE_FILE                         | ontology/dse_mapping_tree.json      | The file for DSE concept tree mapping.                                                               |
| TORCH_USE_CQL                                       | true                                | Flag indicating if CQL should be used.                                                               |
| LOG_LEVEL<br/>_DE_MEDIZININFORMATIKINITIATIVE_TORCH | info                                | Log level for torch core functionality.                                                              |
| LOG_LEVEL<br/>_CA_UHN_FHIR                          | error                               | Log level for HAPI FHIR library.                                                                     |
| SPRING_PROFILES_ACTIVE                              | active                              | The active Spring profile.                                                                           |
| SPRING_CODEC_MAX_IN_MEMORY_SIZE                     | 100MB                               | The maximum in-memory size for Spring codecs.                                                        |

## TORCH REST API (based on FHIR Bulk Data Request)

Torch implements the FHIR [Asynchronous Bulk Data Request Pattern][2].

### $extract-data

The $extract-data endpoint implements the kick-off request in the Async Bulk Pattern. It receives a FHIR parameters
resource with a **_crtdl_** parameter containing a
valueBase64Binary [CRTDL](https://github.com/medizininformatik-initiative/clinical-resource-transfer-definition-language).
All examples are with a torch configured with **http://localhost:8086**.

```sh
scripts/create-parameters.sh src/test/resources/CRTDL/CRTDL_observation.json | curl -s 'http://localhost:8086/fhir/$extract-data' -H "Content-Type: application/fhir+json" -d @- -v
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

That location header can be used in the following status query:
E.g. location:"/fhir/__status/1234"

### Status Request

Torch provides a Status Request Endpoint which can be called using the location from the extract Data Endpoint.

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

the result is a looks something like this:

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

## Output Files

After [Response Complete](#response---complete) is returned the result files in ndjson format
are located in Output directory set in [enviroment variables](#environment-variables).
In the case of error an error.json can be found in the Output directory detailing the fatal error.

Note that each patient batch is written to the output directory and the files are written before the process completes.
This can be used to track the progress of your data extraction.

#### Download Data

If a server is set up for the files e.g. NGINX, the files can be fetched by a Request on the URL set in
TORCH_OUTPUT_FILE_SERVER_URL in [enviroment variables](#environment-variables).
**Torch assumes that the files are served from the baseurl that is next to the fhir api**
E.g.: if localhost:8080/test/fhir is the url from which torch gets called (before forwarding), then
the file url would start with **http://localhost:8080/test/**

```sh
curl -s "http://localhost:8080/da4a1c56-f5d9-468c-b57a-b8186ea4fea8/f33634bd-d51b-463c-a956-93409d96935f.ndjson"
```

### NDJSON: Result Bundles

The ndjson will contain one **transaction bundle** per Patient.

### Global Status Request

Torch provides a Status Request Endpoint which provides a overview extract Data Endpoint.

```sh
curl -s http://localhost:8080/fhir/__status/
```

### Masked Fields

Required fields that were not extracted and slices that are unknown in the Structure Definition are set to Data Absent
Reason "masked".

For example a CRTDL only extracting Condition.onset will result in this:

```json
{
  "resourceType": "Condition",
  "id": "TestID",
  "meta": {
    "profile": [
      "https://www.medizininformatik-initiative.de/fhir/core/modul-diagnose/StructureDefinition/Diagnose"
    ]
  },
  "code": {
    "extension": [
      {
        "url": "http://hl7.org/fhir/StructureDefinition/data-absent-reason",
        "valueCode": "masked"
      }
    ]
  },
  "subject": {
    "extension": [
      {
        "url": "http://hl7.org/fhir/StructureDefinition/data-absent-reason",
        "valueCode": "masked"
      }
    ]
  },
  "onsetDateTime": "2024-10"
}
```

## Transfer Script

TORCH provides a companion **transfer script** designed to automate the workflow of submitting a data extraction
request, polling the status, and transferring the resulting files to a target FHIR server.

The transfer script will:

1. Take the **CRTDL** and generate a FHIR parameters resource to send to TORCH.
2. Execute $extract-data operation on the TORCH server using parameters resource as input.
3. Poll the TORCH status endpoint until the export is complete.
4. Download the resulting patient-oriented FHIR bundles into a temp dir.
5. Upload these files to a configured target FHIR server using the `blazectl` tool.
6. Provide progress feedback and error handling at each step.

### Usage Example

```bash
./transfer-extraction-to-dup-fhir-server.sh -c src/test/resources/CRTDL/CRTDL_observation.json -t  http://target-fhir-server:8080/fhir
```

### Environment Variables

The transfer script respects several environment variables to configure server URLs, directories, retry counts, and
timing:

| Variable       | Default               | Description                                         |
|----------------|-----------------------|-----------------------------------------------------|
| TORCH_BASE_URL | http://localhost:8080 | Base URL of the TORCH server                        |
| MAX_RETRIES    | 60                    | Number of status polling attempts before timing out |
| SLEEP_SECONDS  | 5                     | Seconds to wait between polling attempts            |

## Supported Features

- Loading of StructureDefinitions
- Redacting and Copying of Resources based on StructureDefinitions
- Parsing CRTDL
- Interaction with a Flare and FHIR Server
- MII Consent handling
- OAuth Support
- Multi Profile Support
- Extended Reference Resolving

## Outstanding Features

- Auto Extracting modifiers
- Black or Whitelisting of certain ElementIDs locally
- Validating against Profiles

## License

Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the
License. You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "
AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific
language governing permissions and limitations under the License.

[1]: <https://en.wikipedia.org/wiki/ISO_8601>

[2]: <http://hl7.org/fhir/R5/async-bulk.html>

[3]: <https://github.com/medizininformatik-initiative/flare/releases/>

[4]: <https://github.com/medizininformatik-initiative/feasibility-deploy/tree/main/feasibility-triangle>

[5]: <https://hl7.org/fhir/R5/async-bundle.html>
