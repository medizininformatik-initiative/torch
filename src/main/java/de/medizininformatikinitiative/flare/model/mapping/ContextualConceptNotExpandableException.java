package de.medizininformatikinitiative.flare.model.mapping;

import de.medizininformatikinitiative.flare.model.sq.ContextualConcept;

public class ContextualConceptNotExpandableException extends MappingException {

    public ContextualConceptNotExpandableException(ContextualConcept contextualConcept) {
        super("None of the following contextual term codes %s was found in the code tree. Please check the code tree configured by the env var FLARE_MAPPING_CONCEPT_TREE_FILE.".formatted(contextualConcept));
    }
}
