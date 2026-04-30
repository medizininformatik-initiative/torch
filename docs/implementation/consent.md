# Consent Handling in TORCH

TORCH implements **privacy-aware consent handling** to ensure that the extracted data strictly complies with patient
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

## 2. Supported Consent Codes

TORCH only processes consent provision codes listed in
[
`mappings/consent-code-config.json`](https://github.com/medizininformatik-initiative/torch/blob/main/mappings/consent-code-config.json).
This file can be edited manually to adjust the supported codes for a deployment. The codes shipped with TORCH
represent the recommended MII default.

Each entry describes a **prospective code** (required for consent to be valid) and an optional
**retrospective modifier code** that can extend its valid period backwards in time, and an optional
**data period offset** that shifts the effective end of the permitted window.

**Shipped default:**

| Code                                                      | OID                                      | Role                              |
|-----------------------------------------------------------|------------------------------------------|-----------------------------------|
| MDAT wissenschaftlich nutzen EU DSGVO NIVEAU              | `2.16.840.1.113883.3.1937.777.24.5.3.8`  | Prospective (required)            |
| MDAT retrospektiv wissenschaftlich nutzen EU DSGVO NIVEAU | `2.16.840.1.113883.3.1937.777.24.5.3.46` | Retrospective modifier (optional) |

Codes not listed in `consent-code-config.json` are silently ignored.

---

## 3. Consent Evaluation Pipeline

Before any data extraction:

1. **Extract consent codes from CRTDL** — codes are read from the `cohortDefinition` inclusion criteria.
2. **Filter to supported codes** — only prospective codes defined in `consent-code-config.json` are retained.
3. **Fetch from FHIR
   ** — Consent resources are fetched for the supported codes plus their configured retrospective modifier codes.
4. **Adjust by Encounter
   ** — The start of each consent provision is shifted to the start of the earliest overlapping Encounter, if that Encounter start is earlier than the provision start.
5. **Apply data period offset
   ** — For each permitted prospective provision,
   `dataPeriodOffsetYears` is subtracted from its end date. Provisions whose shifted end date precedes their start date are discarded with a warning (this can occur when a short provision is paired with a large offset, which is an artifact of the specific MII consent modelling).
6. **Apply retrospective modifiers
   ** — For each permitted prospective provision that overlaps with a permitted retrospective modifier provision, the prospective provision's start date is shifted to
   `provisionStart − lookbackYears` (200 years for `.46`, e.g. a provision starting 2020 shifts to 1820).
   Deny provisions are never modified by a retrospective code.
7. **Merge and subtract** — Permitted provision periods are merged; denied provision periods are subtracted.
8. **Require all prospective codes
   ** — A patient's consent is valid only if every required prospective code has at least one non-empty allowed period.
9. **Intersect
   ** — The allowed periods of all required prospective codes are intersected to produce the patient's final consent window.
10. **Enforce during extraction
    ** — Resources whose relevant date field falls outside the consent window are excluded from the result.

---

## 4. Retrospective Modifier Semantics

The retrospective modifier (`.46`) acts as a **period extender** for its associated prospective code (`.8`):

- `.46` is only applied if `.8` is also in the requested codes.
- `.46` only modifies a permitted `.8` provision if their periods **overlap**.
- If a patient has no permitted `.8` provisions at all, `.46` has no effect.
- The resulting consent is still expressed as a single period for `.8`;
  `.46` does not appear independently in the result.

**Example** (from ticket [#853](https://github.com/medizininformatik-initiative/torch/issues/853)):

```
Input provisions:
  .8  2020–2025  permit
  .8  2027–2030  deny
  .8  2030–2035  permit
  .46 2030–2035  permit

After applying retrospective modifier:
  .8  2020–2025  permit          (no overlap with .46 2030–2035 → unchanged)
  .8  2027–2030  deny            (deny → never modified)
  .8  <lookback>–2035  permit   (overlaps .46 2030–2035 → start shifted to 2030 − 200y = 1830)

After merge and subtract:
  Allowed: <lookback>–2026, 2031–2035
```

---

## 5. Enforcement in Data Processing

- Data outside the patient's consent window are excluded from the result set.
- All enforcement happens **before** results are packaged into NDJSON bundles.

---

## 6. Integration with CRTDL

- CRTDL definitions reference consent via the
  `cohortDefinition` inclusion criteria.
- The consent check uses a specific date field per FHIR resource type. If no field is configured for a resource type, all resources of that type are considered consented (see
  [type_to_consent.json](https://github.com/medizininformatik-initiative/torch/blob/main/mappings/type_to_consent.json)).

---

## 7. Limitations and Considerations

- TORCH allows multiple consent blocks in the cohort definition (CCDL), which in the feasibility evaluate to or,
  but internally the stricter logic described in 4. is applied.
- Consent records must be **up-to-date** and accurately reflect patient permissions.
- Only the codes listed in `consent-code-config.json` are evaluated — all others are ignored.
- The file can be edited manually; the shipped default contains only
  `2.16.840.1.113883.3.1937.777.24.5.3.8` and its retrospective modifier `.46`.

---

## Summary

Consent handling in TORCH is:

- **Standards-based** (FHIR Consent, MII KDS profile)
- Per-patient and per-resource
- Limited to the MII MDAT scientific use provision and its retrospective modifier in V1
