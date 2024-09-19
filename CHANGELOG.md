# Changelog

## [v1.0.0-alpha] - 2024-09-19

### Added
- **FHIR Server and Flare Server Integration**: Implemented interaction with a local FHIR server and Flare Server to extract patient resources using CRTDL and CDS profiles.
- **CRTDL Support**: Added support for parsing the Clinical Resource Transfer Definition Language (CRTDL) in JSON format, allowing specification of attributes and filters.
- **$Extract-Data Endpoint**: Introduced the `$extract-data` operation, allowing bulk data extraction via FHIR Parameters resources.
- **Async Bulk Pattern**: Implemented the async bulk pattern with a kick-off request and polling location for data extraction results.
- **Batch Processing**: Implemented NDJSON format for batched transformation results, including links to the generated data bundles.
- **CDS Profile Loading**: Added the ability to load multiple Clinical Decision Support (CDS) profiles per resource, selecting the first CDS-compliant profile.
- **Resource Redaction and Copying**: Introduced basic functionality for redacting and copying patient resources.

### Changed
- **Environment Variables**: Added several configurable environment variables for customizing server behavior:
    - `SERVER_PORT`: Port on which the server runs (default: `8080`).
    - `TORCH_FHIR_URL`: Base URL of the FHIR server (default: `http://localhost:8082/fhir`).
    - `TORCH_FLARE_URL`: Base URL of the Flare server (default: `http://localhost:8084`).
    - `TORCH_PROFILE_DIR`: Directory for CDS profile definitions (default: `src/test/resources/StructureDefinitions`).
    - `TORCH_RESULTS_PERSISTENCE`: Persistence time for results (default: `PT12H30M5S`).
    - `LOG_LEVEL_*`: Configurable log levels for various components (default: `info`).

### Deprecated
- None.

### Removed
- None.

### Fixed
- None.

### Security
- None.

