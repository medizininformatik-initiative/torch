
//Patient diag-enc-diag
Instance: torch-test-diag-enc-diag-pat-1
InstanceOf: https://www.medizininformatik-initiative.de/fhir/core/modul-person/StructureDefinition/Patient
* meta.profile[+] = "http://fhir.de/ConsentManagement/StructureDefinition/Patient"
* insert AddGender(female)

// Encounter Patient-1
Instance: torch-test-diag-enc-diag-enc-1
InstanceOf: https://www.medizininformatik-initiative.de/fhir/core/modul-fall/StructureDefinition/KontaktGesundheitseinrichtung
//* meta.profile[0] = "https://www.medizininformatik-initiative.de/fhir/core/modul-fall/StructureDefinition/KontaktGesundheitseinrichtung|2024.0.1"
* insert AddEncounter(MII_0000001, finished, IMP, einrichtungskontakt, normalstationaer, torch-test-diag-enc-diag-pat-1, 0100)
* period.start = "2024-02-14"
* period.end = "2024-02-22"
* diagnosis[0].condition = Reference(torch-test-diag-enc-diag-diag-1)
* diagnosis[0].use = $diagnosis-role#AD "Admission diagnosis"
* diagnosis[0].rank = 1
* diagnosis[1].condition = Reference(torch-test-diag-enc-diag-diag-2)
* diagnosis[1].use = $diagnosis-role#AD "Admission diagnosis"
* diagnosis[1].rank = 2
* location[Zimmer].location.identifier.system = "https://www.charite.de/fhir/sid/Zimmernummern"
* location[Zimmer].location.identifier.value = "RHC-06-210b"
* location[Zimmer].location.display = "RHC-06-210b"
* location[Zimmer].physicalType = $location-physical-type#ro
* location[Bett].location.identifier.system = "https://www.charite.de/fhir/sid/Bettennummern"
* location[Bett].location.identifier.value = "RHC-06-210b-02"
* location[Bett].location.display = "RHC-06-210b-02"
* location[Bett].physicalType = $location-physical-type#bd
* location[Station].location.identifier.system = "https://www.charite.de/fhir/sid/Stationsnummern"
* location[Station].location.identifier.value = "RHC-06"
* location[Station].location.display = "RHC-06"
* location[Station].physicalType = $location-physical-type#wa


// Diagnosis Diabetes
Instance: torch-test-diag-enc-diag-diag-1
InstanceOf: https://www.medizininformatik-initiative.de/fhir/core/modul-diagnose/StructureDefinition/Diagnose
//* meta.profile[0] = "https://www.medizininformatik-initiative.de/fhir/core/modul-diagnose/StructureDefinition/Diagnose|2024.0.0"
* insert AddDiagnose(E13.0, 2023, torch-test-diag-enc-diag-pat-1, 2024-02-12, Diabetes)
* onsetDateTime = "2024-02-21"
* recordedDate = "2024-02-21"
* encounter =  Reference(torch-test-diag-enc-diag-enc-1)

// Other Diag Ref by Diab Enc
Instance: torch-test-diag-enc-diag-diag-2
InstanceOf: https://www.medizininformatik-initiative.de/fhir/core/modul-diagnose/StructureDefinition/Diagnose
//* meta.profile[0] = "https://www.medizininformatik-initiative.de/fhir/core/modul-diagnose/StructureDefinition/Diagnose|2024.0.0"
* insert AddDiagnose(E13.0, 2023, torch-test-diag-enc-diag-pat-1, 2024-02-12, Diabetes)
* onsetDateTime = "2024-02-01"
* recordedDate = "2024-02-22"
* encounter =  Reference(torch-test-diag-enc-diag-enc-1)


// Other Diag not Ref by Diab Enc
Instance: torch-test-diag-enc-diag-diag-3
InstanceOf: https://www.medizininformatik-initiative.de/fhir/core/modul-diagnose/StructureDefinition/Diagnose
//* meta.profile[0] = "https://www.medizininformatik-initiative.de/fhir/core/modul-diagnose/StructureDefinition/Diagnose|2024.0.0"
* insert AddDiagnose(E-NOT-REF, 2023, torch-test-diag-enc-diag-pat-1, 2024-02-12, Diag not ref by diab enc)
* recordedDate = "2024-02-25"