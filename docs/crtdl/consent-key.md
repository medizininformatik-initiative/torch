## Consent Key in CRTDL

The consent key in the Cohort Selection of the CRTDL is used to specify the consent rules that apply
to the cohort definition and data extraction. It allows for per-patient, per-resource consent enforcement
during structured extraction.

### Key Features

- **Configurable** : Controlled via the `TORCH_MAPPING_CONSENT` environment variable.
- **Shipped with MII specific Consent Key Mapping**: The default consent key mapping is provided by the
  MII [MII Consent Key Mapping](https://github.com/medizininformatik-initiative/torch/blob/main/mappings/consent-mappings_fhir.json).

### Example CRTDL with Consent Key

The following example shows how to use the consent key in a CRTDL definition.
The consent key is defined in the `cohortDefinition` section, which specifies the conditions under which a patient is
included in the cohort.
Consent keys are identified by the `context.code` field, which is set to **Einwilligung** (German for consent).
The key value is located as a termCode in this case with the code **yes-yes-no-yes**.

```json
{
  "version": "http://to_be_decided.com/draft-1/schema#",
  "display": "",
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
            "code": "yes-yes-no-yes",
            "display": "Verteilte, EU-DSGVO konforme Analyse, ohne Krankenassendaten, und mit Rekontaktierung",
            "system": "fdpg.consent.combined"
          }
        ]
      }
    ]
  ]
}

