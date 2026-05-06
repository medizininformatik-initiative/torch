# Consent Handling in TORCH

TORCH implements **privacy-aware consent handling** to ensure that the extracted data strictly complies with patient
permissions and applicable regulations.

---

## 1. Consent Representation

- TORCH supports the [FHIR Consent](https://www.hl7.org/fhir/consent.html) resource as the canonical way to represent
  patient consent.
- The MII broad consent model typically represents a signing event as a FHIR Consent resource, with each consented
  policy code encoded as a sibling provision nested inside a single top-level deny provision. Sites may produce
  multiple Consent resources per signing event.
- Consent records define:
    - **Who** has granted consent
    - **What** data may be accessed
    - **Purpose** and permitted actions
    - **Conditions** or time limits

---

## 2. Supported Consent Codes

TORCH only processes consent provision codes listed in
[`mappings/consent-code-config.json`](https://github.com/medizininformatik-initiative/torch/blob/main/mappings/consent-code-config.json).
This file can be edited manually to adjust the supported codes for a deployment. The codes shipped with TORCH
represent the recommended MII default for FDPG-Project (Zentrale Analyse) use cases.

Each entry in the config describes a **prospective code** with:

| Field             | Meaning                                                                                                 |
|-------------------|---------------------------------------------------------------------------------------------------------|
| `validityGate`    | If `true`, today must fall within the patient's permitted period for this code — patient is excluded if not |
| `required`        | Other codes that must co-occur with this one in the CRTDL cohort definition                             |
| `retroModifiers`  | Optional modifier codes that extend this code's permitted period backwards to a fixed `lookbackDate`    |

**Shipped default:**

| Code                                                      | OID                                       | Role                                          |
|-----------------------------------------------------------|-------------------------------------------|-----------------------------------------------|
| MDAT wissenschaftlich nutzen EU DSGVO NIVEAU              | `2.16.840.1.113883.3.1937.777.24.5.3.8`   | Validity gate (today-in-period check)         |
| MDAT erheben                                              | `2.16.840.1.113883.3.1937.777.24.5.3.6`   | Data-extraction window                        |
| MDAT retrospektiv speichern, verarbeiten                  | `2.16.840.1.113883.3.1937.777.24.5.3.45`  | Retrospective modifier for `.6` (optional)    |
| MDAT retrospektiv wissenschaftlich nutzen EU DSGVO NIVEAU | `2.16.840.1.113883.3.1937.777.24.5.3.46`  | Retrospective modifier for `.6` (optional)    |

`.8` and `.6` are declared as mutually required — both must appear together in the CRTDL cohort definition.
Codes not listed in `consent-code-config.json` are silently ignored.

---

## 3. Consent Evaluation Pipeline

Before any data extraction:

1. **Extract consent codes from CRTDL** — codes are read from the `cohortDefinition` inclusion criteria. TORCH
   extracts only the provision codes that are present and ignores their Boolean combination (AND/OR).
2. **Validate co-occurrence** — for every supported code present, all codes listed in its `required` field must also
   be present. If `.6` appears without `.8` (or vice-versa), the request is rejected with a `ConsentFormatException`.
3. **Filter to supported codes** — only prospective codes defined in `consent-code-config.json` are retained.
4. **Fetch from FHIR** — **Active** Consent resources are fetched for the supported codes, plus any retro modifier codes
   that were explicitly requested in the CRTDL.
5. **Adjust by Encounter** — for data-period codes (non-gate codes, i.e. `.6`), the start of each permitted provision
   is shifted to the start of the earliest overlapping Encounter if that Encounter start is earlier. Gate codes
   (`.8`) are never encounter-adjusted by design.
6. **Apply retrospective modifiers** — within each Consent resource independently: if a permitted `.6` provision
   overlaps in time with a permitted retro modifier provision (`.45`/`.46`) **in the same resource**, the `.6`
   provision's start is shifted to `1900-01-01`. Retro modifier denies (`.45`/`.46` deny) in the same resource
   subtract from the retro-extended period before it is recorded. Modifiers and their denies from a different
   Consent resource have no effect.
7. **Merge and subtract** — only Consent resources that contain permits for **all** required codes (`.6` AND `.8`)
   contribute permit periods. Deny periods from **any** Consent resource are subtracted from the merged permits,
   including revocation documents that carry only denies. Retro-extended periods are immune to prospective code
   denies (`.6` deny) — only retro modifier denies (`.45`/`.46` deny, applied in step 6) can reduce them.
8. **Gate check** — for each validity-gate code (`.8`), today must fall within the merged permitted period. If the
   check fails for any gate code the patient is excluded from the result.
9. **Intersect data periods** — the allowed periods of all data-period codes (`.6`) are intersected to produce the
   patient's final data-extraction window.
10. **Enforce during extraction** — resources whose consent data field (as configured in `type_to_consent.json`)
    falls outside the consent window are excluded from the result.

---

## 4. Validity Gate vs. Data-Extraction Window

`.8` (MDAT wissenschaftlich nutzen) and `.6` (MDAT erheben) play distinct roles:

| Code | Role             | What it controls                                                          |
|------|------------------|---------------------------------------------------------------------------|
| `.8` | Validity gate    | Is the patient's consent currently active? Today must be within the period. |
| `.6` | Data window      | How far back in time may data be extracted?                               |

Both must be present in the CRTDL. If `.8` fails the gate check (e.g. the patient's consent has expired) the
patient is excluded entirely — `.6` is not evaluated.

---

## 5. Retrospective Modifier Semantics

The retrospective modifiers (`.45`, `.46`) act as **period extenders** for `.6`:

- A modifier is only applied if it was explicitly requested in the CRTDL.
- A modifier is only applied when it appears in the **same Consent resource** as the `.6` provision it extends.
  A `.45` permit in resource B does **not** extend a `.6` permit in resource A.
- A modifier affects a permitted `.6` provision when their periods **overlap within the same resource**.
- When applied, the `.6` provision's start date is replaced with `1900-01-01`.
- If a patient has no permitted `.6` provisions in the same resource as the modifier, the modifier has no effect.
- Retro modifier **denies** (`.45`/`.46` deny) in the same resource subtract from the retro-extended period.
- Prospective code **denies** (`.6` deny) do **not** reduce a retro-extended period — only the retro modifier
  deny can revoke the retroactive grant.

**Example:**

```
Consent resource A (signed 2020):
  .8  2020–2050  permit
  .6  2020–2025  permit
  .45 2020–2025  permit   ← same resource, overlaps .6 → extends .6 start to 1900-01-01
  .45 2000–2009  deny     ← same resource, subtracts [2000-01-01, 2009-12-31] from extended .6

Consent resource B (revocation, 2023):
  .6  2023–2025  deny     ← does NOT reduce the retro-extended .6 from resource A
  .45 2023–2025  permit   ← different resource from resource A's .6 → no effect
```

---

## 6. Enforcement in Data Processing

- Data outside the patient's consent window are excluded from the result set.
- All enforcement happens **before** results are packaged into NDJSON bundles.

---

## 7. Integration with CRTDL

- CRTDL definitions reference consent via the `cohortDefinition` inclusion criteria.
- The consent check uses a specific date field per FHIR resource type. If no field is configured for a resource
  type, all resources of that type are considered consented (see
  [type_to_consent.json](https://github.com/medizininformatik-initiative/torch/blob/main/mappings/type_to_consent.json)).

---

## 8. Limitations and Considerations

- Within a single consent block in the cohort definition (CCDL), TORCH extracts the individual consent provision
  codes and ignores their Boolean combination — the co-occurrence and period logic described above is applied instead.
- Consent records must be **up-to-date** and accurately reflect patient permissions.
- Only the codes listed in `consent-code-config.json` are evaluated — all others are ignored.
- The shipped default config supports the MII FDPG-Project (Zentrale Analyse) consent codes; other use cases may
  require additions to `consent-code-config.json`.
- TORCH treats all data in the FHIR server as MDAT (Medizinische Daten).
- TORCH does not consider consent versioning — provision codes are assumed to be unique and to retain the same
  meaning across all versions of the consent profile.

---

## Summary

Consent handling in TORCH is:

- **Standards-based** (FHIR Consent, MII KDS profile, MII broad consent structure)
- Per-patient: permits only from Consent resources that carry the complete required package (`.6` AND `.8`); denies applied globally; retro modifiers and their denies scoped to the resource they appear in
- Driven by `consent-code-config.json` — no code changes required for new consent codes that follow the same
  combination logic as the default set. **This is a fundamental limitation:** TORCH can only handle the validity-gate
    + data-window + retrospective-modifier model hardcoded in its pipeline. New codes that require different combination
      semantics (e.g. a different gate structure, alternative period logic, or additional provision types) cannot be
      supported through configuration alone and require code changes.
