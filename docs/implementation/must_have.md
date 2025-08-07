## Must Have Checking in TORCH Pipeline

The must have checking in the TORCH pipeline is a crucial step to ensure that the extracted data meets the criteria
defined in the Clinical Resource Transfer Definition Language (CRTDL).

It is a two step process:

1. **Must Have Check**: This step verifies that all required attributes specified in the CRTDL are present in the
   extracted data.
2. **Reference Resolve**: This step resolves references in the extracted data to ensure that all resources are correctly
   linked.

## 1. **Must Have Check**

Based on fhir path expressions, the must have check verifies that all required attributes specified in the CRTDL are
filled in a resource.
If any required attribute is missing, the extraction for that resource is stopped, and an error is logged.

## 2. **Reference Resolve**

The first Must Have Check is done on the resources that are loaded directly from the FHIR server.
References are only checked for existence, not for correctness.

During **Reference Resolve**, TORCH will does the basic must have check on the resolved reference.
If at least a reference is found, the resource is considered valid for the extraction.
If no reference is found, the resource is considered invalid and will be marked for deletion in the next step of the
pipeline.
