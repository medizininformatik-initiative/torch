# Provenance and Attribute Groups

During processing, TORCH assigns each resource to one or more **attribute groups**
based on the CRTDL definition and the reference resolution process.

After extraction, TORCH documents the relationship between attribute groups and the resulting resources using
**FHIR Provenance** resources. Each attribute group produces one corresponding Provenance resource.

## Purpose

The Provenance resources provide **traceability and interpretability** of the extraction result:

- They document **which attribute group caused which resources to be included** in the output.
- They allow consumers to understand the **origin of extracted data** within the CRTDL definition.
- They support auditing and reproducibility of the extraction process.

## Structure

For each attribute group:

- A **Provenance** resource is created.
- The **targets** of the
  Provenance resource are the extracted FHIR resources that were assigned to that attribute group.
- The Provenance resource references the **attribute group identifier**.

This establishes a many-to-many relationship:

- A resource can be linked to multiple attribute groups.
- An attribute group can reference multiple resources.

## Semantics

- Provenance reflects the **assignment after extraction**.
- It captures **why a resource is present in the output**, i.e. which attribute group selected it.

## Limitations and Considerations

- **Resource-level granularity**  
  Provenance is recorded at the resource level and does not indicate which specific elements within a resource were selected.

- **Shared resources across groups**  
  If a resource is relevant to multiple attribute groups, it will appear in multiple Provenance resources.
