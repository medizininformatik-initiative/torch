# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),

## [v1.0.0-alpha.10] - 2025-09-05

### Changed

- Update Shipped Structure Definitions [#481](https://github.com/medizininformatik-initiative/torch/pull/481)

## [v1.0.0-alpha.9] - 2025-09-05

### Added

- Add Security Policy [#471](https://github.com/medizininformatik-initiative/torch/pull/471)

## [v1.0.0-alpha.8] - 2025-08-25

### Added

- Sign Docker images with cosign [#444](https://github.com/medizininformatik-initiative/torch/pull/444)
- Add Permit Type and Consent Status in Consent
  Calculation [#408](https://github.com/medizininformatik-initiative/torch/pull/408)

### Fixed

- Fix NullPointer Exception oauth if no params provided and set default to no
  params [#423](https://github.com/medizininformatik-initiative/torch/pull/423)
- Fix Torch does not provide Base URL in Status
  Response [#436](https://github.com/medizininformatik-initiative/torch/pull/436)

## [v1.0.0-alpha.7] - 2025-08-04

### Added

- Transfer script to FHIR DUP Server [#394](https://github.com/medizininformatik-initiative/torch/pull/394)

### Fixed

- Fix Bundle PUT URL Not Set To Relative URL [#392](https://github.com/medizininformatik-initiative/torch/pull/392)
- Bug Fix ProfileMustHaveChecker Does Not Strip
  Versions [#397](https://github.com/medizininformatik-initiative/torch/pull/397)

## [v1.0.0-alpha.6] - 2025-07-23

### Added

- Multi FHIR Profile Handling in Redaction [#361](https://github.com/medizininformatik-initiative/torch/pull/361)
- Redact Primitive Extensions [#336](https://github.com/medizininformatik-initiative/torch/pull/336)
- Added Redaction Handling Fallback for Unknown
  Elements [#356](https://github.com/medizininformatik-initiative/torch/pull/356)

### Changed

- Precalculation of compiled StructureDefinition [#337](https://github.com/medizininformatik-initiative/torch/pull/337)
- CI: Split Build And Test phases For Better
  Parallelization [#349](https://github.com/medizininformatik-initiative/torch/pull/349)
- CI: Cancel PR Workflows on Update [351](https://github.com/medizininformatik-initiative/torch/pull/351)

### Fixed

- Disable Oauth when no params provided [#335](https://github.com/medizininformatik-initiative/torch/pull/335)
- Bundle URL [#345](https://github.com/medizininformatik-initiative/torch/pull/345)
- Missing :below Modifier In Profile Search [#340](https://github.com/medizininformatik-initiative/torch/pull/340)

## [v1.0.0-alpha.5] - 2025-06-19

### Added

- Performance integration tests
- Extract only reference String from references

### Changed

- Ontology Update to v.3.8.0
- Performance Improvements in Reference Resolve and Redaction
- Improved Env Vars
- CI move from Dependabot to Renovate

### Fixed

- Encounter Update in Consent Handling

### Removed

- Multi FHIR Profile Handling in Redaction

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
