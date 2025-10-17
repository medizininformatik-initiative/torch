# Data Extraction

By directly loading and reference resolving resources are assigned to all attribute groups by which they should be
extracted.

The extraction process consists of the following 2 steps:
1. **Attribute Extraction**: Extract only the attributes specified in the profiles.
2. **Redaction**: Remove any unnecessary data from the resources according to predefined rules and fix resources to
   ensure compliance with the profiles.
   see [Redaction](redaction.md) for details.

## Attribute Extraction

Attribute extraction is based on the attribute groups defined in the CRTDL and the profiles referenced there.
It is based on fhir path expressions that are evaluated against the resources.
The extraction process is as follows:
1. Merge extraction behaviour e.g. if multiple attribute groups extract the same attribute, the union of all extraction
   rules is applied.
   Also, hierarchical extraction is supported, e.g. if a parent element is extracted the child elements don't need to
   be extracted explicitly.
2. Evaluate FHIR Path expressions against the resources to extract the specified attributes.
3. Write the extracted attributes to the output resources using HAPI Terser.

### Limitations and Considerations
- **Nested List Handling**:  Fhir path is evaluated at the element level, so if elements are within nested lists, the
  extraction may not behave as expected, since the nested list structure is not considered during evaluation and
  therefore not preserved.
    * For this reason only extracting elements until the first list level is supported, e.g. `Patient.name` is supported
      but `Patient.name.given` is not.
