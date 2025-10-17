# Handling Attribute Groups Directly

TORCH distinguishes between directly loaded and referenced attributeGroups, where referenced attributeGroups are identified by the flag
`IncludeReferenceOnly`.
Directly loaded attributeGroups can be loaded directly as their inclusion in the extraction does not depend on any referencing.

TORCH loads all resources of the directly loaded attributeGroups based on the Fhir Profile specified in the attribute group and the filters applied to the attribute group.
I.e. resources are loaded based on the Fhir Profile specified in the attribute group and
the [filters](../crtdl/filter.md) applied to
the attribute group.

The processing has two separate workflows due to the different nature of the resources:

1. **Core Resources**: These are resources that are outside the patient compartment (no direct link to Patient - e.g. Medication).
2. **Patient Resources**: These are resources that are directly loaded and processed for each patient.

## 1. Directly Loaded Core Attribute Groups

Core attributes are simply loaded and processed in a single step.

This means that all resources that are part of the core attribute group are loaded and processed in a single fhir search
request.

- After that a profile and must-have check is done on the resources.
    - Must-have conditions are violated if **not a single** resource passes this check.
- If any **must-have** condition is violated, the extraction is fully stopped for the job.
- At the same time it is sufficient if the must-have conditions are fulfilled for a single "global" resource.

```mermaid
flowchart TD
	Start[Start Core Attribute Processing] --> Load[Load all core resources in single FHIR search request]
	Load --> Profile[Profile check on resources]
	Profile --> MustHave[Must-have check on resources]
	MustHave --> Check{Any must-have condition violated?}
	Check -->|yes| Stop[Stop extraction for job]
    Check -->|no| Global[At least one resource fulfills must-have condition]
	Global --> Complete[Core processing complete]
	style Stop fill: #ffcccc
	style Complete fill: #ccffcc
    style CoreWorkflow fill: #e1f5fe
```

## 2. Directly Loaded Patient Attribute Groups

Patient attribute groups are loaded and processed for each patient.

All resources from a patient attribute group are loaded and processed in a single fhir search request
and then assigned to their patient.

- First the consent is checked for each of the loaded resources and only resources that pass the consent check are processed further.
    - When **no consent** key is defined, all resources are considered to be consenting by
      default.
- A profile and must-have check is done on every resource.

- If after the group processing a must-have condition is violated, the respective patient is marked for deletion. Once all patients are processed,
  all resources (including the patient resource itself) for a patient marked for deletion are deleted. If no resources are left the whole batch is deleted.

```mermaid
flowchart TD
	Start[Start Patient Attribute Processing] --> ForEach[For each patient]
	ForEach --> Load[Load patient resources in single FHIR search request]
	Load --> Assign[Assign resources to patient]
	Assign --> Consent[Consent check on patient resources]
	Consent --> ConsentKey{Consent key defined?}
	ConsentKey -->|no| AllConsent[All resources considered consenting by default]
	ConsentKey -->|yes| OnlyConsent[Process only consenting resources]
	AllConsent --> ProfileCheck[Profile and must-have check on every resource]
	OnlyConsent --> ProfileCheck
    ProfileCheck --> MustHaveViolated{Group processing violated must-have condition?}
	MustHaveViolated -->|yes| MarkDelete[Mark patient for deletion]
	MustHaveViolated -->|no| PatientComplete[Patient processing complete]
	MarkDelete --> MorePatients{More patients to process?}
	PatientComplete --> MorePatients
	MorePatients -->|yes| ForEach
	MorePatients -->|no| DeleteMarked[Delete marked patient resources from batch]
	DeleteMarked --> AnyLeft{Any patients left in batch?}
	AnyLeft -->|no| DeleteBatch[Delete entire batch]
	AnyLeft -->|yes| BatchComplete[Batch processing complete]
	style MarkDelete fill: #ffeeaa
	style DeleteBatch fill: #ffcccc
	style BatchComplete fill: #ccffcc
    style PatientWorkflow fill: #f3e5f5
```


