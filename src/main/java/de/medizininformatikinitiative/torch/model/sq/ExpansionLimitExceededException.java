package de.medizininformatikinitiative.torch.model.sq;

import de.medizininformatikinitiative.torch.model.mapping.MappingException;

public class ExpansionLimitExceededException extends MappingException {

    public ExpansionLimitExceededException(ContextualConcept concept, int limit) {
        super("The criterion %s expands into more than %d FHIR searches. Please select more specific concepts."
                .formatted(concept, limit));
    }
}
