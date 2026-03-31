# Limitations

This page summarizes the current implementation limits of TORCH that are relevant when designing CRTDLs, configuring profile support, and interpreting extraction results.

## Supported FHIR version

TORCH currently supports **FHIR R4** only.

Other FHIR versions such as DSTU2 or R5 are not supported by the current implementation and documentation.

## Profile availability at startup

TORCH does not resolve arbitrary profiles on demand during extraction.

All relevant
`StructureDefinition` resources must be made available at startup through configuration. If a CRTDL references a profile that is not available locally, validation fails.

This means TORCH currently supports only the profile set that is mounted or configured before the service starts.

In principle, FHIR resources can often be matched to a profile based on their content even without explicit profile metadata.

In practice, however, this kind of profile inference is expensive, can be ambiguous, and is difficult to make robust across heterogeneous real-world data.

For that reason, TORCH intentionally uses a claim-based profile model.

In practice, TORCH primarily works with declared profile claims, especially canonical profile URLs in the CRTDL and profile annotations on resources.

The canonical URL in the CRTDL must match a locally available
`StructureDefinition`. Local file names or informal profile aliases are not sufficient.
The canonical URL in the CRTDL must match a locally available
StructureDefinition. Local file names or informal profile aliases are not sufficient.
All StructureDefinitions are loaded at startup from the configured profile directory; profile loading is not dynamic and changes require a restart.

For the same practical reason, resources should also be annotated with their profile information, typically via
`meta.profile`.

If resources are missing this annotation, TORCH has no information available to match them against the configured profile set, which prevents those resources from being processed.
TORCH relies on profile constraints to retrieve resources via FHIR Search.

This also limits how far TORCH can rely on profile inheritance alone. If processing depends on a specific derived profile, that profile should be claimed explicitly rather than assumed only through inheritance relationships.

## Terminology-backed slicing

TORCH does not provide its own terminology service.

Because of that, slicing that depends on terminology expansion or value set membership is currently not supported.

In particular, value-set-based slice discrimination cannot be evaluated by TORCH alone.

## Reference format restrictions

TORCH expects resource references in the relative FHIR form `ResourceType/id`.

The current extraction implementation does not support:

- absolute references such as `https://example.org/fhir/Patient/123`
- URN references such as `urn:uuid:...`
- malformed references that do not match `ResourceType/id`

Unsupported references are rejected during parsing or ignored during grouping and resolution, depending on where they are encountered.

## Nested resource handling

TORCH does not fully resolve deeply nested resource structures.

Nested content can be preserved as part of the extracted field, but it is not generally expanded into independently resolved substructures during extraction.

This matters especially for complex backbone elements or nested structures that would require deeper traversal than a direct attribute-based extraction path.

## Validation scope

TORCH validates CRTDL structure, profile references, attributes, linked groups, and a number of technical requirements before extraction starts.

TORCH does not perform full profile conformance validation of extracted output resources.  
This is a deliberate design decision based on its role in the data processing pipeline.

TORCH focuses on extracting, transforming, and preparing data for downstream use. In many scenarios, the extracted data is further processed, aggregated, or flattened rather than used as fully validated FHIR resources. As a result, strict end-of-pipeline profile validation would add significant overhead without providing proportional benefit.

Instead, TORCH ensures structural correctness during extraction and reconstruction and relies on upstream data quality and downstream validation steps where full profile conformance is required.

As a result, extraction is profile-aware, but TORCH should not be interpreted as a full profile validator.

## Consent model boundaries

Consent handling in TORCH is based on the consent rules expressed in the CRTDL and the resource mappings implemented in the application.

More complex consent models, institution-specific policy engines, or unsupported resource type mappings may require custom implementation work.

## Interoperability and server behavior

TORCH produces FHIR-based output and relies on standard FHIR server behavior where possible.

Even so, interoperability is not guaranteed across all vendor-specific FHIR implementations. Differences in search behavior, profile quality, reference consistency, and bulk export support can affect extraction results.

Large extractions are also constrained by the performance and feature set of the connected FHIR server.

## Known feature gaps

The following capabilities are currently not available in TORCH:

- automatic extraction of modifiers
- local blacklisting or whitelisting of selected element ids
- full validation against profiles
- value-set-based slicing support

These areas should be considered current product limitations.
