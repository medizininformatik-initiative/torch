package de.medizininformatikinitiative.torch.model.management;

import de.medizininformatikinitiative.torch.model.extraction.ExtractionId;
import de.medizininformatikinitiative.torch.model.extraction.IdentifierReference;
import org.hl7.fhir.r4.model.DomainResource;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Contains all information needed for the extraction and redaction operations
 *
 * @param resource       Resource to be processed
 * @param profiles       profiles of structure definitions of the applied groups
 * @param references     map from elementid to reference string
 * @param copyTree       merged attribute copy tree for the extraction
 * @param identifierIndex index of all resources currently in the bundle by their {@code identifier}, used to resolve
 *                        {@code Reference.identifier}-only values against {@code references}
 */
public record ExtractionRedactionWrapper(DomainResource resource, Set<String> profiles,
                                         Map<String, Set<ExtractionId>> references,
                                         CopyTreeNode copyTree,
                                         Map<IdentifierReference, Set<ExtractionId>> identifierIndex) {

    public ExtractionRedactionWrapper {
        Objects.requireNonNull(resource);
        Objects.requireNonNull(copyTree);
        profiles = Set.copyOf(profiles);
        references = Map.copyOf(references);
        identifierIndex = Map.copyOf(identifierIndex);
    }

    public ExtractionRedactionWrapper updateWithResource(DomainResource resource) {
        return new ExtractionRedactionWrapper(resource, profiles, references, copyTree, identifierIndex);
    }

}
