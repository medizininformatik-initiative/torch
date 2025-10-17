## Must Have Checking in TORCH Pipeline

The must have checking i.e. ensuring required elements are present is a crucial step to ensure
that the extracted data meets the criteria defined in the Clinical Resource Transfer Definition Language (CRTDL).
Required elements are defined in the CRTDL using a must have flag.

Must Have checking is performed after resources are loaded from the FHIR server and during the reference resolve step.

Must have conditions can be violated in two ways:
1. A resource is missing a required attribute as defined in the CRTDL.
2. A resource is missing a required reference to another resource as defined in the CRTDL.

So the must-have checking is done in two steps:

## 1. **Field Must Have Check**

Based on fhir path expressions, the must have check verifies that all required attributes specified in the CRTDL are
filled in a resource.
If any required attribute is missing, the extraction for that resource is stopped, and an error is logged.

## 2. **Reference Resolve**

The first must have check is done on the resources that are loaded directly from the FHIR server.
In this step, references are only checked for existence, not for correctness.

A reference attribute in the CRTDL can be marked as must have and has a link to other attribute groups,
that define the requirements for the referenced resource.

During [Reference Resolve](reference-resolve.md), TORCH does the basic must have check on the resolved reference with the linked
attribute
groups.
So not only does the reference need to be resolved, but the referenced resource also needs to pass the must have check
for the linked attribute groups.
If any must have condition is violated, the original referencing resource is considered to not fulfill its must have criteria 
and is marked for deletion and then cleaned up during the [cascading delete](cascading-delete.md) step.

