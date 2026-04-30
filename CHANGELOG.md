# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),

## [v1.0.0-alpha.20] - 2026-04-30

### Fixed

- Fix Consistency Problem in Job Deletion [#792](https://github.com/medizininformatik-initiative/torch/issues/792)
- Consent: Handle Multiple Consent Blocks in CCDL [#864](https://github.com/medizininformatik-initiative/torch/issues/864)


- Fix Skipped Batches Point To Non Existing Files [#819](https://github.com/medizininformatik-initiative/torch/issues/819)

## [v1.0.0-alpha.19] - 2026-04-27

### Added

- Implement Job Handling Concept [#464](https://github.com/medizininformatik-initiative/torch/issues/464)
- Implement Task API [#703](https://github.com/medizininformatik-initiative/torch/issues/703)
- Implement Task Controller With FHIR Operations [#786](https://github.com/medizininformatik-initiative/torch/issues/786)
- Add Job Versioning [#777](https://github.com/medizininformatik-initiative/torch/issues/777)
- Add PAUSE CANCEL DELETE Operations in Persistence [#783](https://github.com/medizininformatik-initiative/torch/issues/783)
- Add No Op For Status Mismatch [#778](https://github.com/medizininformatik-initiative/torch/issues/778)
- Add TaskPatch To Persistence [#789](https://github.com/medizininformatik-initiative/torch/issues/789)
- Add Exception for Unknown JobId [#800](https://github.com/medizininformatik-initiative/torch/issues/800)
- Add Patient.identifier handling for traceability [#775](https://github.com/medizininformatik-initiative/torch/issues/775)

### Changed

- Re-Work Consent Calculation [#853](https://github.com/medizininformatik-initiative/torch/issues/853)
- Consent: Only handle consent for scientific use of mdat [#852](https://github.com/medizininformatik-initiative/torch/issues/852)
- Update HAPI FHIR Core to 6.9.0 [#846](https://github.com/medizininformatik-initiative/torch/issues/846)
- Update HAPI to 8.8.1 [#781](https://github.com/medizininformatik-initiative/torch/issues/781)

### Removed

- Remove Job Persistence Setting [#622](https://github.com/medizininformatik-initiative/torch/issues/622)

### Fixed

- Fix Skipped Batches Point To Non Existing Files [#819](https://github.com/medizininformatik-initiative/torch/issues/819)

### Breaking Changes

- **Job persistence setting removed** — deployments that previously configured this setting must remove it from
  their configuration.
- **Consent calculation results will differ** for patients with retrospective modifier provisions (`.46`).
  The effective data period is now correctly calculated per the MII V1 consent specification:
  - A `dataPeriodOffsetYears` (default: 25 years) is subtracted from the end of each permitted prospective
    provision to determine the actual data access window.
  - The retrospective modifier (`.46`) extends a prospective provision's start date by `lookbackYears`
    (default: 200 years) relative to the provision's own start date, not relative to today.
  - Retro-extended permits are immune to deny provisions — a retroactive grant supersedes prior revocations.
  - Provisions whose end date falls before their start date after offset application are discarded (with a
    warning in the logs). This can occur when a short provision is paired with a large offset.
- **Combined consent keys are no longer supported** in the CRTDL. Consent keys must reference individual MII
  OID provision codes directly (e.g. `2.16.840.1.113883.3.1937.777.24.5.3.8`). Combined keys such as
  `yes-yes-no-yes` from the `fdpg.consent.combined` system are no longer expanded.

## [v1.0.0-alpha.18] - 2026-03-18

### Added

- Add Patient Params To Transfer To Dup Script [#584](https://github.com/medizininformatik-initiative/torch/issues/584)
- Adding Diagnostic Monad for Collecting Operation Outcomes  [#666](https://github.com/medizininformatik-initiative/torch/issues/666)
- Add Patient.identifier handling for traceability  [#772](https://github.com/medizininformatik-initiative/torch/issues/772)

### Fixed

- Fix Filter Expansion Defaulting To Empty When Not Expandable [#769](https://github.com/medizininformatik-initiative/torch/issues/769)
- Fix: cascading delete not resolved correctly [#772](https://github.com/medizininformatik-initiative/torch/issues/772)

## [v1.0.0-alpha.17] - 2026-03-02

### Added

- Introduce a Reference Record [#751](https://github.com/medizininformatik-initiative/torch/issues/751)

### Changed

- Remove Torch Reference Restriction [#741](https://github.com/medizininformatik-initiative/torch/issues/741)
- Update sq2cql To 1.3.0 [#755](https://github.com/medizininformatik-initiative/torch/issues/755)
- Update Onto To v4.0.0 [#757](https://github.com/medizininformatik-initiative/torch/issues/757)

### Fixed

- Fix RuntimeException In Update Results In JobError [#743](https://github.com/medizininformatik-initiative/torch/issues/743)
- Fix do not suggest output if there is none [#742](https://github.com/medizininformatik-initiative/torch/issues/742)
- Ensure Unexpandable concept does not shut down torch [#749](https://github.com/medizininformatik-initiative/torch/issues/749)
- Large JSON files cause "create-parameters.sh" to fail [#675](https://github.com/medizininformatik-initiative/torch/issues/675)

## [v1.0.0-alpha.16] - 2026-02-16

### Fixed

- Fix slice with coding not resolved correctly [#737](https://github.com/medizininformatik-initiative/torch/pull/737)

## [v1.0.0-alpha.15] - 2026-02-11

### Fixed

- Fix Concurrency Issue In Batch Bundle Query Calls [#734](https://github.com/medizininformatik-initiative/torch/pull/734)

## [v1.0.0-alpha.14] - 2026-02-09

### Added

- Add FAQ and Error Numbers for Lookup [#594](https://github.com/medizininformatik-initiative/torch/issues/594)
- Implement Job Manager [#659](https://github.com/medizininformatik-initiative/torch/issues/659)

### Changed

- Update Ontology To 3.9.5 [#717](https://github.com/medizininformatik-initiative/torch/issues/717)
- Use Fhir Search instead of Code Filter [#296](https://github.com/medizininformatik-initiative/torch/issues/296)
- Improve Async $evaluate-measure Call [#691](https://github.com/medizininformatik-initiative/torch/issues/691)
- Improve Sync Retry Spec [#715](https://github.com/medizininformatik-initiative/torch/issues/715)

### Fixed

- Fix NPE in collectReferences [#726](https://github.com/medizininformatik-initiative/torch/issues/726)
- Fix NPE in DateTimeReading [#696](https://github.com/medizininformatik-initiative/torch/issues/696)
- Fix Torch Not Retrying On Prematurely Closed [#701](https://github.com/medizininformatik-initiative/torch/issues/701)
- Large JSON files cause "create-parameters.sh" to fail [#675](https://github.com/medizininformatik-initiative/torch/issues/675)

## [v1.0.0-alpha.13] - 2026-01-09

### Added

- Add metrics [#569](https://github.com/medizininformatik-initiative/torch/pull/569)

### Changed

- Improve Output File Documentation [#686](https://github.com/medizininformatik-initiative/torch/pull/686)

### Fixed

- Fix Unclear Message When Sliced Element Not Found in Defintion [#548](https://github.com/medizininformatik-initiative/torch/pull/619)
- Fix Trailing Slashes Handling in FHIR Server URL [#618](https://github.com/medizininformatik-initiative/torch/pull/620)
- Fix ResultFileManager Not Initialising Output Directory Not Critical Error [#642](https://github.com/medizininformatik-initiative/torch/pull/642)
- Abort Extraction on 429 Response [#272](https://github.com/medizininformatik-initiative/torch/pull/272)
- Fix unsupported FHIR time types in consent check [#645](https://github.com/medizininformatik-initiative/torch/pull/645)
- Set required recorded field on Provenance resource [#662](https://github.com/medizininformatik-initiative/torch/pull/662)
- Fix Missing Default For Base Url [#673](https://github.com/medizininformatik-initiative/torch/pull/673)
- Fix Resource Cache Not Filtered After Reference Handling [#678](https://github.com/medizininformatik-initiative/torch/pull/678)
- Fix NPE in Date Parsing Crashes Consent Extraction [#679](https://github.com/medizininformatik-initiative/torch/pull/679)

## [v1.0.0-alpha.12] - 2025-11-12

### Added

- Support nested Lists [#589](https://github.com/medizininformatik-initiative/torch/pull/589)
- Handle single consents [#478](https://github.com/medizininformatik-initiative/torch/pull/478)
- Add information about attributeGroup to resources [#525](https://github.com/medizininformatik-initiative/torch/pull/525)
- Support backbone reference resolve [#511](https://github.com/medizininformatik-initiative/torch/pull/511)

## [v1.0.0-alpha.11] - 2025-10-06

### Added

- Implement Conflict Handling in Consent [#513](https://github.com/medizininformatik-initiative/torch/pull/513)

### Fixed

- Do not write empty ndjson [#528](https://github.com/medizininformatik-initiative/torch/pull/528)
- Increase WebFlux Buffer Size [#514](https://github.com/medizininformatik-initiative/torch/pull/514)
- Fix Literal Quotes in Env Vars [#504](https://github.com/medizininformatik-initiative/torch/pull/504)

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
