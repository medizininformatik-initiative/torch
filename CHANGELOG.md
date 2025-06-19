# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),

## [v1.0.0-alpha.5] - 2025-06-19

TODO: compile from https://github.com/medizininformatik-initiative/torch/milestone/12?closed=1

## [v1.0.0-test1] - 2024-04-23

### Added

- Rework of Batching with async writing operation to File
- Reference Resolve with Automatic Patient Group Linking
- Fetching resources by reference
- Support of core resources
- Filter Operation on Resources
- Validation of CRTDL

### Fixed

- Diverse Bug Fixes in extraction process and reactive chain

### Removed

- Dummy Patient and Encounter

## [v1.0.0-alpha.3] - 2024-11-15

### Added

- Update cql aliases

## [v1.0.0-alpha.2] - 2024-11-14

### Added

- Removing of Unknown Slices and Profiles

### Fixed

- Diverse Bug Fixes in extraction process and reactive chain
- Fixed Search Params in Queries

## [v1.0.0-alpha.1] - 2024-10-21

### Added

- Open Id Connect Authentication
- Filter Resources by Consent selected in CCDL
- Expand concept code filter
- CQL cohort execution
- Ontology integration

## [v1.0.0-alpha] - 2024-09-19

### Added

- **FHIR Server and Flare Server Integration**: Implemented interaction with a local FHIR server and Flare Server to
  extract patient resources using CRTDL and CDS profiles.
- **CRTDL Support**: Added support for parsing the Clinical Resource Transfer Definition Language (CRTDL) in JSON
  format, allowing specification of attributes and filters.
- **$Extract-Data Endpoint**: Introduced the `$extract-data` operation, allowing bulk data extraction via FHIR
  Parameters resources.
- **Async Bulk Pattern**: Implemented the async bulk pattern with a kick-off request and polling location for data
  extraction results.
- **Batch Processing**: Implemented NDJSON format for batched transformation results, including links to the generated
  data bundles.
- **Multi FHIR Profile Handling**: Added the ability to handle multiple FHIR profiles per resource, selecting the first
  known profile greedily.
- **Resource Redaction and Copying**: Introduced basic functionality for redacting and copying patient resources.
