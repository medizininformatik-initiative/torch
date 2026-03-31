# Data Extraction

TORCH relies on FHIR StructureDefinitions to interpret resource structure and ensure profile conformance during extraction and reconstruction.
All required StructureDefinitions (including base definitions and referenced profiles) are loaded from a configured directory at startup.
Custom profiles can be supported by adding their StructureDefinitions to this directory before startup, but changes are not applied dynamically at runtime.

All referenced StructureDefinitions must be available at startup.

After resources are loaded they are assigned to the attribute groups that define how they should be extracted.

The extraction process consists of two main steps:

1. **Attribute Extraction**: Extract only the attributes specified in the profiles.
2. **Redaction**:
   Remove unnecessary data and ensure that the resulting resources remain compliant with their profiles.  
   See [Redaction](redaction.md) for details.

## Attribute Extraction

Attribute extraction is driven by the attribute groups defined in the CRTDL and the referenced profiles.
The attributes provide the FHIRPath expressions, which are evaluated against the source resources conforming to the referenced profiles to determine which elements should be retained.

The extraction process is as follows:

1. **Merge extraction rules**  
   If multiple attribute groups select the same resource, the union of all extraction rules is applied.
   This applies when a resource fulfills multiple attribute groups, for example because it conforms to multiple profiles
   or because it was loaded through different extraction contexts or reference paths. In such cases, TORCH does not duplicate the resource. Instead, it combines the extraction instructions from all matching attribute groups and extracts the resource once using the merged rule set.
   Hierarchical extraction is supported: selecting an element via a FHIRPath expression (e.g. Patient.name) implicitly includes all of its child elements (e.g. Patient.name.given, Patient.name.family, etc.).

2. **Evaluate FHIRPath expressions**  
   The configured FHIRPath expressions are evaluated against the source resources to identify matching elements.

3. **Reconstruct resources**  
   The selected elements are written to new output resources using HAPI FHIR model operations.

## Nested List Handling

FHIRPath evaluation operates on matching elements without preserving full list context.  
TORCH therefore applies a structure-aware reconstruction step to ensure that nested list elements are handled correctly.

- Nested elements are preserved **together with their enclosing list structure** where possible.
- Extraction operates on **element matches**, but reconstruction ensures structurally valid FHIR resources.
- Multiple matching elements from lists are retained without flattening unrelated siblings.

For example, consider an `Observation` with a `CodeableConcept` containing multiple `coding` entries:

- Each `CodeableConcept` has a list of `coding` elements
- Each `coding` contains fields such as `system` and `code`

A FHIRPath expression such as:

`Observation.code.coding.code`

matches all `code` elements across all `coding` entries and returns them as a flat collection.

During reconstruction, TORCH ensures that:

- Each matching `code` is placed back into its corresponding `coding` element
- The `coding` elements remain grouped within their original `CodeableConcept`
- Unrelated sibling elements are not merged or flattened

As a result, the output preserves a valid FHIR structure even though the matching step itself is not structure-aware.

### Limitations and Considerations

- **List context is not part of FHIRPath semantics**  
  FHIRPath expressions operate on matching elements without preserving full positional or relational context within nested lists.
  As a result, extraction is based on element matches, and some structural intent may not be fully recoverable in complex cases.

- **Ambiguity in deeply nested lists**  
  For deeply nested or highly complex list structures, it may not always be possible to fully reconstruct the original semantic relationships between elements.

- **Prefer selecting higher-level elements**  
  To ensure predictable results, it is recommended to extract at a higher level (e.g.
  `Patient.name`) rather than targeting deeply nested primitives (e.g.
  `Patient.name.given`), unless the structure is well understood.
