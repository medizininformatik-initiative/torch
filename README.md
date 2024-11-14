# TORCH - Transfer Of Resources in Clinical Healthcare

## Goal

**T**ransfer **O**f **R**esources in **C**linical **H**ealthcare or **Torch** is a project that aims to provide
a service that allows the execution of data extraction queries on a FHIR-server.

The tool will take an **CRTDL** and StructureDefinitions to extract defined Patient resources from a FHIR
Server and then apply the data extraction with the Filters and selected Attributes defined in the **CRTDL**.

The tool internally uses the [HAPI](https://hapifhir.io/) implementation for handling FHIR Resources.

## CRTDL

The **C**linical **R**esource **T**ransfer **D**efinition **L**anguage or **CRTDL** is a JSON format that describes
attributes to be extracted with attributes filter.

## Prerequisites

TORCH interacts with the following components directly:

- A Feasibility Analysis Request Executor (FLARE)
- A FHIR Server
- Reverse Proxy (NGINX)

The reverse proxy allows for integration into a site's multi-server infrastructure.

### CQL Support

CQL is supported. If your FHIR server does not support CQL itself then the FLARE component must be used as a kind of
translation mediator.

### Component Interchangeability

All components work with well-defined interfaces making them interchangeable. Thus, there are different middleware
clients and FHIR servers to chose from.

This leads to the following setup options:

- FLARE (FHIR Search) - FHIR Server (not CQL ready)
- FHIR Server (CQL ready)

**When choosing a FHIR server, make sure it supports either CQL or the required FHIR search capabilities.**
The Setup can be set using the TORCH_USE_CQL setting in the [enviroment variables](#environment-variables)

## Build

```sh
mvn clean install
```

## Run

```sh
java -jar target/torch-0.0.1-SNAPSHOT.jar 
```

```sh
mvn spring-boot:run
```

## Docker

For simplicity torch is integrated in
the [feasibility-triangle](https://github.com/medizininformatik-initiative/feasibility-deploy/tree/main/feasibility-triangle)
of feasibility-deploy

## Environment Variables

| Name                                                            | Default                             | Description                                                                                          |
|:----------------------------------------------------------------|:------------------------------------|:-----------------------------------------------------------------------------------------------------|
| SERVER_PORT                                                     | 8080                                | The Port of the server to use                                                                        |
| TORCH_PROFILE_DIR                                               | structureDefinitions                | The directory for profile definitions.                                                               |
| TORCH_MAPPING_CONSENT                                           | mappings/consent-mappings_fhir.json | The file for consent mappings in FHIR format.                                                        |
| TORCH_MAPPING_CONSENT_TO_PROFILE                                | mappings/profile_to_consent.json    | The file mapping profiles to consent codes.                                                          |
| TORCH_FHIR_USER                                                 | ""                                  | The FHIR server user.                                                                                |
| TORCH_FHIR_PASSWORD                                             | ""                                  | The FHIR server password.                                                                            |
| TORCH_FHIR_OAUTH_ISSUER_URI                                     | ""                                  | The URI for the OAuth issuer.                                                                        |
| TORCH_FHIR_OAUTH_CLIENT_ID                                      | ""                                  | The client ID for OAuth.                                                                             |
| TORCH_FHIR_OAUTH_CLIENT_SECRET                                  | ""                                  | The client secret for OAuth.                                                                         |
| TORCH_FHIR_URL                                                  | http://localhost:8081/fhir          | The base URL of the FHIR server to use.                                                              |
| TORCH_FHIR_PAGE_COUNT                                           | 500                                 | The number of pages in a FHIR search response.                                                       |
| TORCH_FLARE_URL                                                 | http://localhost:8084               | The base URL of the FLARE server to use.                                                             |
| TORCH_RESULTS_DIR                                               | output/                             | The directory for storing results.                                                                   |
| TORCH_RESULTS_PERSISTENCE                                       | PT2160H                             | Time Block for result persistence in ISO 8601 <br/> format in hours/minutes/seconds. Default 90 days |
| TORCH_BATCHSIZE                                                 | 100                                 | The batch size used for processing data.                                                             |
| TORCH_MAXCONCURRENCY                                            | 100                                 | The maximum concurrency level for processing.                                                        |
| TORCH_MAPPINGS_FILE                                             | ontology/mapping_cql.json           | The file for ontology mappings using CQL.                                                            |
| TORCH_CONCEPT_TREE_FILE                                         | ontology/mapping_tree.json          | The file for the concept tree mapping.                                                               |
| TORCH_DSE_MAPPING_TREE_FILE                                     | ontology/dse_mapping_tree.json      | The file for DSE concept tree mapping.                                                               |
| TORCH_USE_CQL                                                   | true                                | Flag indicating if CQL should be used.                                                               |
| NGINX_SERVERNAME                                                | http://localhost:8080               | The server name configuration for NGINX.                                                             |
| NGINX_FILELOCATION                                              | http://localhost:8080/output        | The URL to access Result location TORCH_RESULTS_DIR  via  NGINX                                      |
| LOG_LEVEL_org<br/>_SPRINGFRAMEWORK_WEB_REACTIVE_FUNCTION_CLIENT | info                                | Log level for Spring Web Reactive client functions.                                                  |
| LOG_LEVEL<br/>_REACTOR_NETTY                                    | info                                | Log level for Netty reactor-based networking.                                                        |
| LOG_LEVEL_REACTOR                                               | info                                | Log level for Reactor framework.                                                                     |
| LOG_LEVEL<br/>_DE_MEDIZININFORMATIKINITIATIVE_TORCH             | info                                | Log level for torch core functionality.                                                              |
| LOG_LEVEL<br/>_CA_UHN_FHIR                                      | error                               | Log level for HAPI FHIR library.                                                                     |
| SPRING_PROFILES_ACTIVE                                          | active                              | The active Spring profile.                                                                           |
| SPRING_CODEC_MAX_IN_MEMORY_SIZE                                 | 100MB                               | The maximum in-memory size for Spring codecs.                                                        |

## Examples of Using Torch

Below, you will find examples for typical use cases.
To demonstrate the simplicity of the RESTful API,
the command line tool curl is used in the following examples for direct HTTP communication.

## Flare REST API

Torch implements the FHIR [Asynchronous Bulk Data Request Pattern][2].

### $extract-data

The $extract-data endpoint implements the kick-off request in the Async Bulk Pattern. It receives a FHIR parameters
resource with a CRTDL parameter containing a valueBase64Binary.

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

#### Download Data

If a server is set up for the files e.g. NGINX, the files can be fetched by a Request on the URL set in
NGINX_FILELOCATION in [enviroment variables](#environment-variables).

```sh
curl -s "http://localhost:8080/da4a1c56-f5d9-468c-b57a-b8186ea4fea8/f33634bd-d51b-463c-a956-93409d96935f.ndjson"
```

### NDJSON: Result Bundles

The ndjson will contain one **transaction bundle** per Patient with "Dummy Resources" for maintaining referential
integrity for Patient and Encounter resources, if the adequate Resources were not part of the extraction process.
I.e. there is always at least a Patient resource containing profile and id set.

For example:

```json
{
  "resourceType": "Patient",
  "id": "Patient_1",
  "meta": {
    "profile": [
      "https://www.medizininformatik-initiative.de/fhir/core/modul-person/StructureDefinition/Patient"
    ]
  }
}
```

Analogously an Encounter containing profile, status and id is set.

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

## Supported Features

- Loading of StructureDefinitions
- Redacting and Copying of Resources based on StructureDefinitions
- Parsing CRTDL
- Interaction with a Flare and FHIR Server
- MII Consent handling
- OAuth Support

## Outstanding Features

- Operation Outcomes for Errors
- Auto Extracting modifiers
- Black or Whitelisting of certain ElementIDs locally
- Clinic internal code System for internal
- MultiProfile Support
- Verifying against the CDS Profiles

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
