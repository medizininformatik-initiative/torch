RuleSet: AddDiagnose(codeCodingCode, codeCodingVersion, subject, recordedDate, noteText)
* clinicalStatus = $condition-clinical#active
* verificationStatus = $condition-ver-status#confirmed
* code.coding[icd10-gm] = $icd-10-gm#{codeCodingCode}
* code.coding[icd10-gm].version = "{codeCodingVersion}"
* subject = Reference({subject})
* recordedDate = "{recordedDate}"
* note.text = "{noteText}"