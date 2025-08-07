# Consent Handling in TORCH

TORCH implements **privacy-aware consent handling** to ensure that extracted data strictly complies with patient
permissions and applicable regulations.

---

## 1. Consent Representation

- TORCH supports the [FHIR Consent](https://www.hl7.org/fhir/consent.html) resource as the canonical way to represent
  patient consent.
- Consent records define:
    - **Who** has granted consent
    - **What** data may be accessed
    - **Purpose** and permitted actions
    - **Conditions** or time limits

---

## 2. Consent Evaluation

Before any data extraction:

1. TORCH retrieves a consent key from the CRTDL
2. For a batch all patient **Consent** resources are fetched from the FHIR server.
3. For the current extraction request the valid consent records are identified based on:
    - Patient ID
    - Status
    - Provision codes

4. Shift start of Consent by related Encounter if applicable:
    - The assumption is that consents are valid from the start of the encounters that have the start of the consent
      within
      their encounter period.
5. Calculate the Consent Periods:
    - order them oldest to newest (by Consent.dateTime)
    - for each:
        - add permits to the current consent period
        - if denies are present subtract them from the current consent period
6. During extraction TORCH determines:
    - Whether the requested resources lie within the consent period for patient as calculated based on the CRTDL.
    - If any resources dates are outside the period, they are excluded from the result set.

## 3. Enforcement in Data Processing

- Data outside the specified consent period are excluded from the result set
- All enforcement happens **before** results are packaged into NDJSON bundles.

---

## 4. Integration with CRTDL

- CRTDL definitions can reference consent rules directly via the consent key in the cohort selection.
- This allows **per-patient, per-resource** consent enforcement during structured extraction.

---

## 5. Limitations and Considerations

- Consent records must be **up-to-date** and accurately reflect patient permissions.
- TORCH only supports the use of consent keys that are defined in the mapping configurations ( see [Configuration](../configuration.md)).
- The consent check is always defined for a specific field for each FHIR resource type. If no consent field is defined for a resource type, all resources of this type are considered as consented.
  (see the specified
  fields [type_to_consent.json](https://github.com/medizininformatik-initiative/torch/blob/main/mappings/type_to_consent.json)).

---
## Summary

Consent handling in TORCH is:

- **Standards-based** (FHIR Consent)
- Per Patient and per Resource
- Only checks specific fields of the patient compartment resource
