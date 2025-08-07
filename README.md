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

## Documentation

Documentation can be found [here](https://medizininformatik-initiative.github.io/torch/).

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
- Value Set based Slicing

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
