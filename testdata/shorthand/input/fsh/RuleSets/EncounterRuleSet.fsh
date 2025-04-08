RuleSet: AddEncounter(identifierAufnahmenummerValue, statusCode, classCode, kontaktebeneCode, kontaktartCode, subject, fachabteilungsschluesselCode)
* identifier[Aufnahmenummer].type = $v2-0203#VN
* identifier[Aufnahmenummer].system = "https://www.charite.de/fhir/NamingSystem/Aufnahmenummern"
* identifier[Aufnahmenummer].value = "{identifierAufnahmenummerValue}"
* status = #{statusCode}
* class = $v3-ActCode#{classCode}
* type[Kontaktebene] = $Kontaktebene#{kontaktebeneCode}
* type[KontaktArt] = $Kontaktart#{kontaktartCode}
* subject = Reference({subject})
* serviceType.coding[Fachabteilungsschluessel] = $Fachabteilungsschluessel#{fachabteilungsschluesselCode}