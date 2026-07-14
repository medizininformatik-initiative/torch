// identifier

RuleSet: AddVersichertenIdGKV(identifierValue, identifierAssignerIdentifierValue)
* identifier[+]
  * use = #official
  * type = $identifier-type-de-basis#GKV
  * system = "http://fhir.de/sid/gkv/kvid-10"
  * value = "{identifierValue}"
  * assigner
    * identifier
      * type = $v2-0203#XX
      * system = "http://fhir.de/sid/arge-ik/iknr"
      * value = "{identifierAssignerIdentifierValue}"

RuleSet: AddPID(identifierValue, identifierSystem, identifierAssignerIdentifierValue)
* identifier[+]
  * type = $v2-0203#MR
  * system = "{identifierSystem}"
  * value = "{identifierValue}"
  * assigner
    * identifier
      * type = $v2-0203#XX
      * system = "https://www.medizininformatik-initiative.de/fhir/core/CodeSystem/core-location-identifier"
      * value = "{identifierAssignerIdentifierValue}"

RuleSet: AddVersichertennummerPKV(identifierValue, identifierAssignerDisplay)
* identifier[+]
  * use = #secondary
  * type = $identifier-type-de-basis#PKV
  * value = "{identifierValue}"
  * assigner
    * display = "{identifierAssignerDisplay}"

// name

RuleSet: AddName(nameFamily, nameFamilyNamenszusatz, nameFamilyNachname, nameFamilyVorsatzwort, nameFamilyGiven0, nameFamilyGiven1, nameFamilyPrefix, nameFamilyPrefixQualifier, nameFamilySuffix)
* name
  * use = #official
  * family = "{nameFamily}"
    * extension[0]
      * url = "http://fhir.de/StructureDefinition/humanname-namenszusatz"
      * valueString = "{nameFamilyNamenszusatz}"
    * extension[1]
      * url = "http://hl7.org/fhir/StructureDefinition/humanname-own-name"
      * valueString = "{nameFamilyNachname}"
    * extension[2]
      * url = "http://hl7.org/fhir/StructureDefinition/humanname-own-prefix"
      * valueString = "{nameFamilyVorsatzwort}"
  * given[0] = "{nameFamilyGiven0}"
  * given[1] = "{nameFamilyGiven1}"
  * prefix[0] = "{nameFamilyPrefix}"
    * extension[0]
      * url = "http://hl7.org/fhir/StructureDefinition/iso21090-EN-qualifier"
      * valueCode = #{nameFamilyPrefixQualifier}
  * suffix[0] = "{nameFamilySuffix}"

RuleSet: AddGeburtsname(nameFamily, nameFamilyNamenszusatz, nameFamilyNachname, nameFamilyVorsatzwort)
* name
  * use = #maiden
  * family = "{nameFamily}"
    * extension[0]
      * url = "http://fhir.de/StructureDefinition/humanname-namenszusatz"
      * valueString = "{nameFamilyNamenszusatz}"
    * extension[1]
      * url = "http://hl7.org/fhir/StructureDefinition/humanname-own-name"
      * valueString = "{nameFamilyNachname}"
    * extension[2]
      * url = "http://hl7.org/fhir/StructureDefinition/humanname-own-prefix"
      * valueString = "{nameFamilyVorsatzwort}"

// gender

RuleSet: AddGender(gender)
* gender = #{gender}

// address

RuleSet: AddStrassenanschrift(addressStadtteil, addressLine, addressLineStrasse, addressLineHausnummer, addressLineAdresszusatz, addressCity, addressCityGemeindeschlüssel, addressPostalCode, addressCountry)
* address
  * extension
    * url = "http://hl7.org/fhir/StructureDefinition/iso21090-ADXP-precinct"
    * valueString = "{addressStadtteil}"
  * type = #both
  * line[0] = "{addressLine}"
    * extension[0]
      * url = "http://hl7.org/fhir/StructureDefinition/iso21090-ADXP-streetName"
      * valueString = "{addressLineStrasse}"
    * extension[1]
      * url = "http://hl7.org/fhir/StructureDefinition/iso21090-ADXP-houseNumber"
      * valueString = "{addressLineHausnummer}"
    * extension[2]
      * url = "http://hl7.org/fhir/StructureDefinition/iso21090-ADXP-additionalLocator"
      * valueString = "{addressLineAdresszusatz}"
  * city = "{addressCity}"
    * extension[0]
      * url = "http://fhir.de/StructureDefinition/destatis/ags"
      * valueCoding = http://fhir.de/sid/destatis/ags#{addressCityGemeindeschlüssel}
  * postalCode = "{addressPostalCode}"
  * country = "{addressCountry}"

RuleSet: AddPostfach(addressStadtteil, addressLine, addressLinePostfach, addressCity, addressCityGemeindeschlüssel, addressPostalCode, addressCountry)
* address
  * extension
    * url = "http://hl7.org/fhir/StructureDefinition/iso21090-ADXP-precinct"
    * valueString = "{addressStadtteil}"
  * type = #postal
  * line[0] = "{addressLine}"
    * extension[0]
      * url = "http://hl7.org/fhir/StructureDefinition/iso21090-ADXP-postBox"
      * valueString = "{addressLinePostfach}"
  * city = "{addressCity}"
    * extension[0]
      * url = "http://fhir.de/StructureDefinition/destatis/ags"
      * valueCoding = http://fhir.de/sid/destatis/ags#{addressCityGemeindeschlüssel}
  * postalCode = "{addressPostalCode}"
  * country = "{addressCountry}"