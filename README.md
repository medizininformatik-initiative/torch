# TORCH - Transfer Of Resources in Clinical Healthcare



**T**ransfer **O**f **R**esources in **C**linical **H**ealthcare

## Goal

The goal of this project is to provide a service that allows the execution of data extraction queries on a FHIR-server.

The tool will take an DEQ, Whitelists and KDS Profiles to extract defined Patient ressources from a FHIR Server
 and then apply the data extraction with the Filters defined in the **DEQ**. Additionally Whitelists can be applied. 


The tool internally uses the [HAPI](https://hapifhir.io/) implementation for handling FHIR Resources.  


## CRTDL
The **C**linical **R**esource **T**ransfer **D**efinition **L**anguage or **CRTDL** is a JSON format that describes attributes to be extracted with attributes filter.


## Whitelists
TBD

## KDS Profiles

TBD

## Supported Features

- Loading of KDS StructureMaps
- Testing  if found Extensions  generally legal in Ressource
- Redacting and Copying of Ressources
- Parsing CRTDL


## Outstanding Features

- Loading of Whitelists
- Handling of Backbone Elements in Redaction 
- Handling nested Lists? 
- Handling of Backbone Elements in Factory
- Handling of Extension Slicing
- Multiple Profiles per Resource
 




## Build
TBD


## Documentation
TBD

## License

Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.

[1]: <https://en.wikipedia.org/wiki/ISO_8601>
