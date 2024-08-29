# TORCH - Transfer Of Resources in Clinical Healthcare

**T**ransfer **O**f **R**esources in **C**linical **H**ealthcare

## Goal

The goal of this project is to provide a service that allows the execution of data extraction queries on a FHIR-server.

The tool will take an DEQ, Whitelists and CDS Profiles to extract defined Patient ressources from a FHIR Server
and then apply the data extraction with the Filters defined in the **DEQ**. Additionally Whitelists can be applied.

The tool internally uses the [HAPI](https://hapifhir.io/) implementation for handling FHIR Resources.

## CRTDL

The **C**linical **R**esource **T**ransfer **D**efinition **L**anguage or **CRTDL** is a JSON format that describes
attributes to be extracted with attributes filter.

## Prerequisites

Local FHIR Server with Patientdata and
a [Flare server with a cohort Endpoint](https://github.com/medizininformatik-initiative/flare/tree/178-add-cohort-extraction-endpoint)
Torch interacts with these components for the data extraction.

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

## Environment Variables

| Name                  | Default                        | Description                             |
|:----------------------|:-------------------------------|:----------------------------------------|
| SERVER_PORT           | 8080                           | The Port of the server to use           |
| TORCH_FHIR_URL        | http://localhost:8082/fhir     | The base URL of the FHIR server to use. |
| TORCH_FLARE_URL       | http://localhost:8084          | The base URL of the FLARE server to use.|
| TORCH_PROFILE_DIR     | src/test/resources/StructureDefinitions | The directory for profile definitions.  |
| TORCH_RESULTS_DIR     | bundles/                       | The directory for storing results.      |
| TORCH_RESULTS_PERSISTENCE | PT12H30M5S                | Time Block for result persistence in ISO 8601 format. |
| LOG_LEVEL_de_medizininformatikinitiative_torch_util | info | Log level for torch utility.              |
| LOG_LEVEL_de_medizininformatikinitiative_torch | info | Log level for torch core functionality.   |
| LOG_LEVEL_de_medizininformatikinitiative_torch_rest | info | Log level for torch REST services.        |
| LOG_LEVEL_org_springframework | info | Log level for Spring Framework.            |


## Examples of Using Torch

Below, you will find examples for typical use cases.
To demonstrate the simplicity of the RESTful API,
the command line tool curl is used in the following examples for direct HTTP communication.

## Flare REST API

Torch implements the FHIR [http://hl7.org/fhir/R5/async-bundle.html)

### $Extract-Data

The $Extract-Data-Endpoint implements the Kick-off Request in the Async Bulk Pattern.
It receives a FHIR parameters ressource with a crtdl parameter containing a valueBase64Binary.

```sh
curl -s http://localhost:8080/fhir/$extract-data -H "Content-Type: application/fhir+json" -d '<query>'
```

An example `<query>` would look like this

'''
{
"resourceType" : "Parameters",
"id" : "example",
"parameter" : [{
"name" : "crtdl",
"valueBase64Binary" : "Base64encodedcrdl""}
]
}
'''

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
* A body containing a Bundle Resource with a type of batch-response.

```sh
curl -s http://localhost:8080/fhir/$extract-data -H "Content-Type: application/fhir+json" -d '<query>'
```

the result is a looks something like this:

```json
{
  "resourceType": "Bundle",
  "type": "batch-response",
  "entry": [
    {
      "resource": {
        "resourceType": "Bundle",
        "id": "2",
        "type": "collection",
        "entry": [
          {
            ...
          }
        ]
      }
    },
    {
      "resource": {
        "resourceType": "Bundle",
        "id": "4",
        "type": "collection",
        "entry": [
          {
            ...
          }
        ]
      }
    }
  ]
}
```

### Metadata

#### Response -Success

* HTTP status of 200 OK
* Content-Type header of application/fhir+json
* A body containing a Bundle Resource with a type of batch-response.

## Whitelists

TBD

## CDS Profiles

TBD

## Supported Features
- Multiple Profiles per Resource (greedy tales first CDS conforming one)
- Loading of CDS StructureDefinitions
- Redacting and Copying of Ressources
- Parsing CRTDL
- Interaction with a Flare and FHIR Server 

## Outstanding Features

- CQL functionalities
- Loading of Whitelists- Testing if found Extensions generally legal in Ressource
- Handling nested Lists?
- Handling of Backbone Elements in Factory
- Handling of Extension Slicing at an element Level
- Verifiyng against the CDS Profiles


## License

Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the
License. You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "
AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific
language governing permissions and limitations under the License.

[1]: <https://en.wikipedia.org/wiki/ISO_8601>
