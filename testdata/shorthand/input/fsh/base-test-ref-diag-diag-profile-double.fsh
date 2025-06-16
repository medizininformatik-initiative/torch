
//Patient diag-diag-profile-double
Instance: torch-test-diag-diag-profile-double-pat-1
InstanceOf: https://www.medizininformatik-initiative.de/fhir/core/modul-person/StructureDefinition/Patient
* meta.profile[+] = "http://fhir.de/ConsentManagement/StructureDefinition/Patient"
* insert AddGender(female)

// Diagnosis Diabetes - One profile diagnosis
Instance: torch-test-diag-diag-profile-double-diag-1
InstanceOf: https://www.medizininformatik-initiative.de/fhir/core/modul-diagnose/StructureDefinition/Diagnose
* insert AddDiagnose(E13.0, 2023, torch-test-diag-diag-profile-double-pat-1, 2024-02-12, Diabetes)
* onsetDateTime = "2024-02-21"
* recordedDate = "2024-02-21"

// Diagnosis Diabetes - Three profiles, one unknown
Instance: torch-test-diag-diag-profile-double-diag-2
InstanceOf: https://www.medizininformatik-initiative.de/fhir/ext/modul-onko/StructureDefinition/mii-pr-onko-diagnose-primaertumor
* meta.profile[0] = "https://testProfile-unknownToTorch"
* meta.profile[1] = "https://www.medizininformatik-initiative.de/fhir/core/modul-diagnose/StructureDefinition/Diagnose"
* meta.profile[2] = "https://www.medizininformatik-initiative.de/fhir/ext/modul-onko/StructureDefinition/mii-pr-onko-diagnose-primaertumor"
* insert AddDiagnose(E13.0, 2023, torch-test-diag-diag-profile-double-pat-1, 2024-02-12, Diabetes)
* extension[Feststellungsdatum].valueDateTime = "2024-02-01"
* onsetDateTime = "2024-02-01"
* recordedDate = "2024-02-22"

// Diagnosis Diabetes - one profile prim diag
Instance: torch-test-diag-diag-profile-double-diag-3
InstanceOf: https://www.medizininformatik-initiative.de/fhir/ext/modul-onko/StructureDefinition/mii-pr-onko-diagnose-primaertumor
* meta.profile[0] = "https://www.medizininformatik-initiative.de/fhir/ext/modul-onko/StructureDefinition/mii-pr-onko-diagnose-primaertumor"
* insert AddDiagnose(E13.4, 2023, torch-test-diag-diag-profile-double-pat-1, 2024-02-12, Diabetes)
* extension[Feststellungsdatum].valueDateTime = "2024-02-01"
* onsetDateTime = "2024-02-01"
* recordedDate = "2024-02-22"

