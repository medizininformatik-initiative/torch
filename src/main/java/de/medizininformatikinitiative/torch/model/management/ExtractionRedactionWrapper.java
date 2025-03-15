package de.medizininformatikinitiative.torch.model.management;

import org.hl7.fhir.r4.model.DomainResource;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Contains all information needed for the extraction and redaction operations
 *
 * @param resource   Resource to be processed
 * @param profiles   profiles of structure definitions of the applied groups
 * @param references map from elementid to reference string
 * @param attributes merged attributes for the extraction
 */
public record ExtractionRedactionWrapper(DomainResource resource, Set<String> profiles,
                                         Map<String, Set<String>> references,
                                         Set<de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttribute> attributes) {

    public ExtractionRedactionWrapper {
        Objects.requireNonNull(resource);
        profiles = Set.copyOf(profiles);
        attributes = Set.copyOf(attributes);
        references = Map.copyOf(references);
    }

}
