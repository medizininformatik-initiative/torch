## CRTDL

The Clinical Resource Transfer Definition Language (CRTDL) is a JSON-based format used to define cohorts and data
extraction rules for TORCH.
It allows users to specify who to extract data for, what data to extract, and how to handle consent and masking.

### Key Features of CRTDL

- **Cohort Definition**: Specify the population of interest using FHIR resources and criteria.
- **Data Extraction Rules**: Define which FHIR resources and fields to extract.
- **Consent Handling**: Integrate consent rules to ensure compliance with privacy regulations.

### CRTDL Structure

A CRTDL definition is structured as a JSON object with the following key components:

- **`cohort`**: Defines the population of interest using FHIR resources and criteria.
- **`data`**: Specifies the FHIR resources and fields to extract.

### Cohort Selection

The cohort selection uses
the [CCDL](https://github.com/medizininformatik-initiative/clinical-cohort-definition-language/tree/main) (Cohort
Definition Definition Language) to define the population of interest.

TORCH supports CQL or FHIR Search for the cohort selection execution.

If your FHIR server does not support CQL itself then the FLARE component must be used to extract the
cohort based on the cohort definition of the **CRTDL**.

The cohort evaluation strategy can be set using the TORCH_USE_CQL setting in
the [enviroment variables](./../configuration.md#environment-variables).
