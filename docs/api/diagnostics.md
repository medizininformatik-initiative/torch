# Job Diagnostics

When a job completes, TORCH writes three diagnostic reports: a job summary, patient exclusions and resource exclusions. 
The job summary has a fixed size containing only a few measurements and is therefore directly injected into the status response.
The patient excluisons and resource exclusions list as many exclusion events as occured during processing, and can therefore
grow to arbitrary sizes. The URLs to the files are provided in the completion manifest via the `torch-patient-exclusions`
and `torch-resource-exclusions` extensions (see [API — Job Completion Manifest Extensions](./api#job-completion-manifest-extensions)).

# Job Summary
A JSON object containing the following fields:
- `Num-Cohort-Patients`: the number of patients of the initial cohort before extraction.
- `Num-Final-Patients`: the number of patients after extraction, which can be smaller when patients were discarded due to
consent violations or other reasons (see [Patient Exclusions](#patient-exclusions)).
- `Duration-Measurements`: the per-stage average and median amount of milliseconds it took each batch in the job to finish the stage.
Stages are: `CONSENT_FETCH`, `DIRECT_LOAD`, `REFERENCE_RESOLVE`, `CASCADING_DELETE`, `COPY_REDACT`.

---

## Patient Exclusions

A CSV file where each row represents a single patient exclusion event that occurred during processing. If there is for
example consent required in the CRTDL, but there is no consent data for a patient on the FHIR server, the patient and
their corresponding resources will not be processed further and the patient is therefore marked as excluded.

### Example
```csv
"Batch-ID","Stage","Patient-ID"
"fe95e52b-7db6-428b-b610-df697b13dae0","DIRECT_LOAD","pat-2"
"fe95e52b-7db6-428b-b610-df697b13dae0","DIRECT_LOAD","pat-3"
"fe95e52b-7db6-428b-b610-df697b13dae0","CONSENT","pat-1"
```

### Explanation

| Column       | Description                                                                                             |
|--------------|---------------------------------------------------------------------------------------------------------|
| `Batch-ID`   | The ID of the batch in which the patient was part of                                                    |
| `Stage`      | The stage at which the patient was excluded (one of `CONSENT_FETCH`, `DIRECT_LOAD`, `CASCADING_DELETE`) |
| `Patient-ID` | The ID of the patient that was excluded                                                                 |


In some cases it is not trivial to state a single reason why a patient was excluded. For example, if there are 10 resources
discarded at direct load, and then due to chained must-have constraints other 10 resources are discarded during cascading
delete, there might no resources be left for the patient, which is then marked as excluded. In this case there is no
single reason responsible for the exclusion, but multiple reasons (direct load, must-have constraints with different attributes, etc.).
Therefore, only the *stage* at which a patient is excluded is marked.
It might still be possible to deduce the reason for which the patient was excluded by examining the resource exclusions. 

---

### Resource Exclusions

A CSV file where each row represents a single resource exclusion event that occurred during processing.

### Example
```csv
"Batch-ID","Reason","Group","Attribute","Resource-ID","Patient-ID"
"fe95e52b-7db6-428b-b610-df697b13dae0","MUST_HAVE","med-adm-group","MedicationAdministration.category","MedicationAdministration/med-adm-1","pat-1"
"fe95e52b-7db6-428b-b610-df697b13dae0","MUST_HAVE","med-adm-group","MedicationAdministration.category","MedicationAdministration/med-adm-2","pat-2"
"3e8ed3b3-92b2-431c-b311-5fda0aa18460","CONSENT","med-adm-group","","MedicationAdministration/med-adm-3","pat-3"
```

### Explanation

| Column        | Description                                                                                                                    |
|---------------|--------------------------------------------------------------------------------------------------------------------------------|
| `Batch-ID`    | The ID of the batch in which the patient was part of                                                                           |
| `Reason`      | The reason why the resource was excluded in that moment (see [Resons for Resource Excusions](#reasons-for-resource-exclusions) |
| `Group`       | The ID of the AttributeGroup the resources originated from                                                                     |
| `Attribute`   | The attribute reference that might be the reason for the exclusion (can be empty)                                              |
| `Resource-ID` | The ID of the excluded resource                                                                                                |
| `Patient-ID`  | The ID of the patient corresponding to the resource (can be empty if it is a core resource)                                    |


### Reasons for Resource Exclusions
- `CONSENT`: The resource is not consented (see [Consent Documentation](../implementation/consent.md)).  
- `MUST_HAVE`: The resource violates a must-have constraint. In this case the `Attribute` field is always filled.
- `REFERENCE_NOT_FOUND`: A referenced resource could not be fetched (i.e. if referential integrity on the FHIR server is violated)
- `RESOURCE_OUTSIDE_BATCH`: A patient resource was referenced during core processing (Should usually not happen at all. 
Might indicate FHIR profile violations or bugs in TORCH).

Due to possibly complex interconnections between attribute groups and chained must-have constraints, it is not trivial
to find a single reason for why a resource is excluded during cascading delete. Therefore, such resources are not noted
as resource exclusion events. Since each such chain has some origin where a resource was invalidated *before* cascading 
delete, resources of the origin of such chains are still marked as usual by the reasons stated above, which can help
deducing why resources are missing that are not marked as excluded.
