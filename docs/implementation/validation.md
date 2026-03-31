# Validation

TORCH validates CRTDL definitions before starting data extraction.

The goal of validation is to detect invalid extraction definitions early and to transform the input CRTDL into an annotated representation that can be processed consistently during extraction.

Validation in TORCH covers both:

- structural checks of the CRTDL
- annotation of attribute groups and attributes for downstream processing

## Overview

During validation, TORCH:

- resolves the referenced profiles of all attribute groups
- ensures that exactly one `Patient` attribute group exists
- validates that all referenced attributes exist in the corresponding profile
- validates linking rules for reference attributes
- validates that all linked groups are defined
- extracts consent codes from the CRTDL
- enriches attribute groups with standard attributes required for processing

The result of validation is an `AnnotatedCrtdl`.

## Validation Rules

### Known profiles

Each attribute group must reference a known profile via `groupReference`.

If TORCH cannot resolve the referenced profile, validation fails.

### Exactly one Patient attribute group

A valid CRTDL must contain exactly one attribute group whose referenced profile has resource type `Patient`.

Validation fails if:

- no patient attribute group exists
- more than one patient attribute group exists

This patient group is also used when generating standard patient reference attributes for other groups.

### Known attributes

Each attribute in an attribute group must reference an element that exists in the corresponding profile.

If an attribute reference cannot be resolved in the referenced profile, validation fails.

### Typed attributes

Each referenced element must have a type in the resolved profile definition.

Typeless attributes are rejected during validation.

### Reference attributes must declare linked groups

If an attribute is a pure `Reference` attribute, it must declare at least one linked group.

Validation fails for reference attributes without linked groups.

### Linked groups must exist

TORCH collects all linked group ids used by attributes and checks that all of them correspond to defined attribute groups.

Validation fails if linked groups are referenced but no matching attribute group exists.

### Consent validation

During validation, TORCH extracts consent codes from the CRTDL.

If the consent definition is malformed, validation fails with a consent-related validation error.

## Annotation of Attributes

After validation, user-defined attributes are converted into annotated attributes.

For each declared attribute, TORCH creates an annotated representation containing:

- the original `attributeRef`
- the resolved FHIRPath used internally for processing
- the `mustHave` flag
- the configured `linkedGroups`

This means validation does not only check correctness, but also prepares the CRTDL for later extraction steps.

## Standard Attributes Added During Validation

In addition to the explicitly defined CRTDL attributes, TORCH automatically adds a small set of standard attributes to each attribute group.

These attributes are generated during validation and do not need to be specified manually in the CRTDL.

### Added for all attribute groups

For every attribute group, TORCH adds:

- `<ResourceType>.id`
- `<ResourceType>.meta.profile`

These attributes are required for technical processing and profile-aware handling.

### Added for Patient groups

If the attribute group references the resource type `Patient`, TORCH additionally adds:

- `Patient.identifier`

### Added for resources in the patient compartment

If the resource type belongs to the patient compartment, TORCH checks whether the referenced profile contains the following elements:

- `<ResourceType>.patient`
- `<ResourceType>.subject`

If present, these attributes are added automatically.

These generated patient reference attributes are linked to the patient attribute group in order to preserve the relation between the resource and the patient.

### Summary of generated standard attributes

Depending on the resource type, validation may add:

- always:
    - `<ResourceType>.id`
    - `<ResourceType>.meta.profile`
- for `Patient`:
    - `Patient.identifier`
- for patient-compartment resources, if present in the profile:
    - `<ResourceType>.patient`
    - `<ResourceType>.subject`

## Result of Validation

If validation succeeds, TORCH returns an `AnnotatedCrtdl` containing:

- the original cohort definition
- annotated attribute groups
- extracted consent codes, if present

Each annotated attribute group contains:

- group metadata such as name, id, resource type, and group reference
- generated standard attributes
- validated and annotated user-defined attributes
- the original filters
- the original `includeReferenceOnly` setting

## Notes

Validation in TORCH is not limited to rejecting invalid input. It also performs normalization and enrichment needed for extraction.

In particular, validation ensures that downstream processing can rely on:

- resolved profiles
- resolved attribute definitions
- explicit linked-group information
- standard technical attributes such as `id` and `meta.profile`
- patient reference fields where needed for referential integrity
