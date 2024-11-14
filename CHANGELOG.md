# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),

## [UNRELEASED] - yyyy-mm-dd

### Added

### Changed

### Deprecated

### Removed

### Fixed

### Security

## [v1.0.0-alpha.1] - 2024-10-21

### Added

- **Open Id Connect Authentication**
- **Filter Resources by Consent selected in CCDL**
- **Expand concept code filter**
- **CQL cohort execution**
- **Ontology integration**

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


