package de.medizininformatikinitiative.torch.model.extraction;

import java.util.List;

/**
 * Result of extracting references from a single FHIR attribute.
 *
 * @param references           references resolved directly from a literal {@code Reference.reference}
 * @param identifierReferences unresolved logical references from a {@code Reference.identifier}
 */
public record ExtractedReferences(List<ExtractionId> references, List<IdentifierReference> identifierReferences) {
}
