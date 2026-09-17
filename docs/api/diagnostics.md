# Job Diagnostics

When a job completes, TORCH writes three diagnostic reports: a job summary, patient exclusions and resource exclusions. 
The job summary has a fixed size containing only a few measurements and is therefore directly injected into the status response.
The patient excluisons and resource exclusions list as many exclusion events as occured during processing, and can therefore
grow to arbitrary sizes. The URLs to the files are provided in the completion manifest via the `torch-patient-exclusions`
and `torch-resource-exclusions` extensions (see [API — Job Completion Manifest Extensions](./api#job-completion-manifest-extensions)).

Three further reports, the [consent diagnostics](#consent-diagnostics), are written only if explicitly requested via the opt-in
`consentDiagnostics` request parameter (see [API — Request Body Structure](./api#request-body-structure)).

# Job Summary
A JSON object containing the following fields:
- `Num-Cohort-Patients`: the number of patients of the initial cohort before extraction.
- `Num-Final-Patients`: the number of patients after extraction, which can be smaller when patients were discarded due to
consent violations or other reasons (see [Patient Exclusions](#patient-exclusions)).
- `Cohort-Query-Duration-Ms`: the amount of milliseconds the job-wide cohort query took to run, or absent if patient
IDs were supplied directly instead of being determined by a cohort query. This is a single job-wide measurement, not
a per-batch one, and is therefore not part of `Duration-Measurements`.
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
"3e8ed3b3-92b2-431c-b311-5fda0aa18460","CASCADING_DELETE","med-adm-group","","MedicationAdministration/med-adm-4","pat-3"
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
- `CASCADING_DELETE`: The resource was not itself invalid, but was removed because a resource it depended on (directly or
transitively) was invalidated. The `Attribute` field is always empty for this reason - see
[Cascading Delete](../implementation/cascading-delete.md) for how invalidation propagates through the reference graph.

Each chain of cascading invalidation has an origin where a resource was invalidated *before* cascading delete ran; that
origin resource is marked by one of the other reasons stated above (e.g. `MUST_HAVE`, `REFERENCE_NOT_FOUND`), not
`CASCADING_DELETE`. This includes the case where a must-have reference resolves to a target that itself turns out
invalid: that is discovered and reported (as `MUST_HAVE`) during reference resolution, before cascading delete ever
runs, even though the underlying cause is transitive.

---

## Consent Diagnostics

An opt-in breakdown of how each patient's consent was calculated and applied, enabled by setting the
`consentDiagnostics` request parameter to `true` (see [API — Request Body Structure](./api#request-body-structure)).
It consists of three job-wide CSV files, each linked via its own extension (see
[API — Job Completion Manifest Extensions](./api#job-completion-manifest-extensions)):

```
reports/
  raw-provisions.csv                 (torch-raw-provisions)
  final-periods.csv                  (torch-final-periods)
  consent-considered-resources.csv   (torch-consent-considered-resources)
```

All three files list every patient, sorted by `Patient-ID`, so that all rows of a patient are contiguous — to debug a
single patient, search for its ID. A file is only written if it contains at least one row. A patient whose consent
evaluation produced no final periods has no rows in `final-periods.csv`, but is listed with stage `CONSENT_FETCH` in the
[Patient Exclusions](#patient-exclusions).

### Raw Provisions

`raw-provisions.csv` lists every consent provision fetched from the patients' `Consent` resources, before
encounter-shift adjustment (see [Consent Documentation](../implementation/consent.md)) — TORCH's "Initial Provision
Periods".

#### Example
```csv
"Batch-ID","Patient-ID","Consent-ID","Code","Permit","Period-Start","Period-End"
"fe95e52b-7db6-428b-b610-df697b13dae0","pat-1","consent-1","2.16.840.1.113883.3.1937.777.24.5.3.6","true","2021-01-01","2025-12-31"
```

#### Explanation

| Column         | Description                                                             |
|----------------|--------------------------------------------------------------------------|
| `Batch-ID`     | The ID of the batch in which the patient was part of                     |
| `Patient-ID`   | The ID of the patient the provision belongs to                           |
| `Consent-ID`   | The ID of the source `Consent` resource                                  |
| `Code`         | The provision's consent code                                             |
| `Permit`       | Whether the provision is a permit (`true`) or a deny (`false`)           |
| `Period-Start` | The provision's period start                                             |
| `Period-End`   | The provision's period end                                               |

### Final Periods

`final-periods.csv` lists each patient's final, intersected data-extraction consent periods — the disjoint segments
computed after combining all raw provisions.

#### Example
```csv
"Batch-ID","Patient-ID","Period-Start","Period-End"
"fe95e52b-7db6-428b-b610-df697b13dae0","pat-1","2021-01-01","2025-12-31"
```

#### Explanation

| Column         | Description                                          |
|----------------|------------------------------------------------------|
| `Batch-ID`     | The ID of the batch in which the patient was part of |
| `Patient-ID`   | The ID of the patient the period belongs to          |
| `Period-Start` | The segment's start                                  |
| `Period-End`   | The segment's end                                    |

### Consent-Considered Resources

`consent-considered-resources.csv` lists every resource for which the patient's consent was checked during
extraction, whether the resource was kept or excluded.

#### Example
```csv
"Batch-ID","Patient-ID","Resource-ID","Included","Date"
"fe95e52b-7db6-428b-b610-df697b13dae0","pat-1","Observation/obs-1","true","2022-04-20"
"fe95e52b-7db6-428b-b610-df697b13dae0","pat-1","Condition/cond-1","false","2018-04-20"
```

#### Explanation

| Column        | Description                                                                     |
|---------------|-----------------------------------------------------------------------------------|
| `Batch-ID`    | The ID of the batch in which the patient was part of                              |
| `Patient-ID`  | The ID of the patient the resource belongs to                                     |
| `Resource-ID` | The ID of the considered resource                                                 |
| `Included`    | Whether the resource passed the consent check                                     |
| `Date`        | The date value evaluated against the consent periods, empty if not applicable     |
