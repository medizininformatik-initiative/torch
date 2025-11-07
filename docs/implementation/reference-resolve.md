## Reference Resolve

Torch needs to resolve references in the extracted data to ensure that all resources are correctly extracted and linked.
This is done in the **Reference Resolve** step of the Torch pipeline.

### Resolving References

Torch manages for each Attribute Group the Resources that have been assigned to it.
This assignment is called a ResourceGroup (e.g. AttributeGroup_Observation,Observation1).

To resolve references, Torch uses the following steps for each Patient or the Core Resources:

1. **Get all valid ResourceGroups**: Torch retrieves all ResourceGroups that are valid for the current extraction.
2. **Extract References**: For each ResourceGroup, Torch extracts the references (as string) from the resources.
    - If the specified field for which referenced groups are defined is a reference, extract directly.
    - Otherwise, a recursive reference extract is done.
3. **Load References**: Torch checks if it already loaded the referenced resources in its cache.
    - If the resource is not loaded, Torch bundles all references and loads them in a single FHIR search request.
4. **Assign References**: Torch assigns the loaded resources to the ResourceGroup.
    - Performs a [must-have check](must-have.md) on the loaded resources.
    - For Patient resources (i.e. resources inside the patient compartment), it also checks if the resources are allowed 
by the patient's [consent](consent.md).
    - If any must-have condition is violated, the referencing resource group is marked for deletion.
5. **Update ResourceGroup**: The ResourceGroup is updated with the loaded resources and their references.
6. **Repeat for all ResourceGroups**: Torch repeats the process for all newly added ResourceGroups until all references
   are resolved.
