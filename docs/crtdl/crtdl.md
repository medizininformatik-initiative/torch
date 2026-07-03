## CRTDL

The Clinical Resource Transfer Definition Language (CRTDL) is a JSON-based format used to define cohorts and data
extraction rules for TORCH.
It allows users to specify who to extract data for, what data to extract, and how to handle consent and masking.

### Key Features of CRTDL

- **Cohort Definition**: Specify the population of interest using FHIR resources and criteria.
- **Data Extraction Rules**: Define which FHIR resources and fields to extract.
- **Consent Handling**: Integrate consent rules to ensure compliance with privacy regulations.

---
### CRTDL Structure

A CRTDL definition is structured as a JSON object with the following key components:

- **`cohort definition`**: Defines the population of interest.
- **`data extraction`**: Specifies the FHIR resources and fields to extract.

---
### Cohort Selection

The cohort selection uses
the [CCDL](https://github.com/medizininformatik-initiative/clinical-cohort-definition-language/tree/main) (Clinical Cohort
Definition Language) to define the population of interest.

TORCH supports CQL or FHIR Search for the cohort selection execution.

If your FHIR server does not support CQL, the FLARE component must be used to extract the cohort based on the cohort definition of the CRTDL.
Alternatively you can specify a list of patient IDs which TORCH will use to extract the data.

The cohort evaluation strategy can be set using the TORCH_USE_CQL setting in
the [enviroment variables](./../configuration.md#environment-variables).

Consent rules are embedded directly in the cohort definition as inclusion criteria entries with `context.code = "Einwilligung"`. When consent codes are present, TORCH filters resources against each patient's consent window during direct load and reference resolution — resources outside the window are excluded. See [Consent Handling](../implementation/consent.md) for the full evaluation logic.

#### Consent Codes in the Cohort Definition

Each consent code is a MII OID provision code listed under a `context.code = "Einwilligung"` criterion in `inclusionCriteria`. The two required codes for FDPG Zentrale Analyse — `MDAT wissenschaftlich nutzen` (`.8`, validity gate) and `MDAT erheben` (`.6`, data-extraction window) — must both be present:

```json
{
  "inclusionCriteria": [
    [
      {
        "context": {
          "code": "Einwilligung",
          "display": "Einwilligung",
          "system": "fdpg.mii.cds",
          "version": "1.0.0"
        },
        "termCodes": [
          {
            "code": "2.16.840.1.113883.3.1937.777.24.5.3.8",
            "display": "MDAT wissenschaftlich nutzen EU DSGVO NIVEAU",
            "system": "urn:oid:2.16.840.1.113883.3.1937.777.24.5.3",
            "version": "1.0.7"
          }
        ]
      }
    ],
    [
      {
        "context": {
          "code": "Einwilligung",
          "display": "Einwilligung",
          "system": "fdpg.mii.cds",
          "version": "1.0.0"
        },
        "termCodes": [
          {
            "code": "2.16.840.1.113883.3.1937.777.24.5.3.6",
            "display": "MDAT erheben",
            "system": "urn:oid:2.16.840.1.113883.3.1937.777.24.5.3",
            "version": "1.0.7"
          }
        ]
      }
    ]
  ]
}
```

If no `Einwilligung` entries are present, TORCH skips consent enforcement entirely and treats all resources as consented.

---
### Data Extraction Selection

The data extraction object contains an array defining **attributeGroups**, which bundle attributes together.

Each group has an identifier for the group called **groupReference**, a list of attributes to be extracted, called
**attributes**, and a filter object containing exactly one time filter and many code filter. The code filter contains an array of codes to be filtered (see [filters](filter.md)).

**Every data extraction must have exactly one attribute group that describes patient attributes**.

An attribute to be extracted contains an attribute reference **attributeRef** and a flag indicating whether the attribute is required: **mustHave**.

```json
{
  "attributeRef": "Medication.medicationCode",
  "mustHave": true
}
```

When `mustHave: true` is set on at least one attribute in a group, TORCH enforces that every patient has at least one resource of that group where all `mustHave: true` attributes are populated.
Patients for whom no such resource exists are **dropped from the extraction result entirely**.

When no attribute in a group carries `mustHave: true`, the group is optional: patients are retained even if they have no resources for that group.

Standard attributes (`id`, `meta.profile`, and patient compartment references such as `subject`) are enforced at pipeline level and are never subject to `mustHave` filtering.
Resources that lack `id` or `meta.profile` are filtered out before must-have checking runs.
For patient resources specifically, TORCH enriches the resource with the target profile if `meta.profile` is missing, so patients are not dropped for a missing profile alone.
See [must-have checking](../implementation/must-have.md) for the full semantics.

Filter definitions have a list of **FHIR search parameter operations** containing the type, name (corresponds the code field in FHIR Search Parameters) 
and corresponding parameters. Currently **token** and **date** are supported types.





```json
{
    "version": "http://json-schema.org/to-be-done/schema#",
    "display": "",
    "cohortDefinition": {
      "version": "http://to_be_decided.com/draft-1/schema#",
      "display": "",
      "inclusionCriteria": [
        [
            {
              "termCodes": [
                {
                  "code": "424144002",
                  "system": "http://snomed.info/sct",
                  "display": "Gegenwärtiges chronologisches Alter"
                }
              ],
              "context": {
                "code": "Patient",
                "system": "fdpg.mii.cds",
                "version": "1.0.0",
                "display": "Patient"
              },
              "valueFilter": {
                "type": "quantity-comparator",
                "unit": {
                  "code": "a",
                  "display": "a"
                },
                "value": 18,
                "comparator": "gt"
              }
            }
          ],
          [
            {
              "termCodes": [
                {
                  "code": "263495000",
                  "system": "http://snomed.info/sct",
                  "display": "Geschlecht"
                }
              ],
              "context": {
                "code": "Patient",
                "system": "fdpg.mii.cds",
                "version": "1.0.0",
                "display": "Patient"
              },
              "valueFilter": {
                "selectedConcepts": [
                  {
                    "code": "female",
                    "display": "Female",
                    "system": "http://hl7.org/fhir/administrative-gender"
                  }
                ],
                "type": "concept"
              }
            }
          ],
          [
            {
              "termCodes": [
                {
                  "code": "8-918",
                  "system": "http://fhir.de/CodeSystem/bfarm/ops",
                  "version": "2023",
                  "display": "Interdisziplinäre multimodale Schmerztherapie"
                }
              ],
              "context": {
                "code": "Procedure",
                "system": "fdpg.mii.cds",
                "version": "1.0.0",
                "display": "Prozedur"
              }
            }
          ]

      ],
        "dataExtraction": {
          "attributeGroups": [
            {
              "groupReference": "https://www.medizininformatik-initiative.de/fhir/core/modul-labor/StructureDefinition/ObservationLab",
              "attributes": [
                {
                  "attributeRef": "Observation.code",
                  "mustHave": false
                },
                {
                  "attributeRef": "Observation.value",
                  "mustHave": true
                }
              ],
              "filter": [
                {
                  "type": "token",
                  "name": "code",
                  "codes": [
                    {
                      "code": "718-7",
                      "system": "http://loinc.org",
                      "display": "Hemoglobin [Mass/volume] in Blood"
                    },
                    {
                      "code": "33509-1",
                      "system": "http://loinc.org",
                      "display": "Hemoglobin [Mass/volume] in Body fluid"
                    }
                  ]
                },
                {
                  "type": "date",
                  "name": "date",
                  "start": "2021-09-09",
                  "end": "2021-10-09"
                }
              ]
            }
          ]
        }
    }
}

```
