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

- **`inclusion criteria`**: Defines the population of interest.
- **`data extraction`**: Specifies the FHIR resources and fields to extract.

---
### Cohort Selection

The cohort selection uses
the [CCDL](https://github.com/medizininformatik-initiative/clinical-cohort-definition-language/tree/main) (Clinical Cohort
Definition Definition Language) to define the population of interest.

TORCH supports CQL or FHIR Search for the cohort selection execution.

If your FHIR server does not support CQL, the FLARE component must be used to extract the cohort based on the cohort definition of the CRTDL.
Alternatively you can specify a list of patient IDs which TORCH will use to extract the data.

The cohort evaluation strategy can be set using the TORCH_USE_CQL setting in
the [enviroment variables](./../configuration.md#environment-variables).

Torch uses the [Consent Key](consent-key.md) to enforce consent rules during the cohort selection and data extraction
process.

---
### Data Extraction Selection

The data extraction object contains an array defining **attributeGroups**, which bundle attributes together.

Each group has an identifier for the group called **groupReference**, a list of attributes to be extracted
**attributes
** and a filter object containing exactly one time filter and many code filter. The code filter contains an array of codes to be filtered (see [filters](filter.md)).

**Every data extraction must have exactly one attribute group that describes patient attributes**.

An attribute to be extracted contains an attribute reference **attributeRef** and information if the attribute is
required **must-have** e.g.

```json 
{
  "attributeRef": "Medication.medicationCode",
      "mustHave": true
          
          }
```


If a **must have** condition is **violated**, it will result in a complete stop of the extraction for a **patient**.

Filters definition have a list of **FHIR search parameter operations** containing the type supported, name and corresponding parameters. Currrently **token** and **date** are supported.





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
