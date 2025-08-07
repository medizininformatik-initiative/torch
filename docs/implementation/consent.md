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

1. TORCH retrieves relevant patient **Consent** resources from the FHIR server.
2. These resources are interpreted against the current extraction request.
3. TORCH determines:
    - Whether the requested resources are allowed
    - Which elements or attributes must be masked or omitted

---

## 3. Enforcement in Data Processing

- Disallowed data is **excluded** from the result set.
- Sensitive elements that require masking are replaced with `null` or removed.
- All enforcement happens **before** results are packaged into NDJSON bundles.

---

## 4. Integration with CRTDL

- CRTDL definitions can reference consent rules directly.
- This allows **per-patient, per-resource** consent enforcement during structured extraction.

---

## 5. Audit & Traceability

- TORCH maintains logs of consent checks and enforcement actions.
- This provides a traceable record of:
    - Which consent rules were applied
    - Which data was included, masked, or omitted

---

## Summary

Consent handling in TORCH is:

- **Standards-based** (FHIR Consent)
- **Fine-grained** (resource & element level)
- **Privacy-first** (masking & exclusion)
- **Auditable** (traceable enforcement)
