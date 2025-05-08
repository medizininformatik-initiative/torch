
//Patient enc-period
Instance: torch-test-enc-period-pat-1
InstanceOf: https://www.medizininformatik-initiative.de/fhir/core/modul-person/StructureDefinition/Patient
* meta.profile[+] = "http://fhir.de/ConsentManagement/StructureDefinition/Patient"
* insert AddGender(female)

// Encounter Patient-1
Instance: torch-test-enc-period-enc-1
InstanceOf: https://www.medizininformatik-initiative.de/fhir/core/modul-fall/StructureDefinition/KontaktGesundheitseinrichtung
//* meta.profile[0] = "https://www.medizininformatik-initiative.de/fhir/core/modul-fall/StructureDefinition/KontaktGesundheitseinrichtung|2024.0.1"
* insert AddEncounter(MII_0000001, finished, IMP, einrichtungskontakt, normalstationaer, torch-test-enc-period-pat-1, 0100)
* period.start = "2024-02-14"
* period.end = "2024-02-22"
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
