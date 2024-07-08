package de.medizininformatikinitiative.flare.model.mapping;

import de.medizininformatikinitiative.flare.model.sq.ContextualTermCode;

public class MappingNotFoundException extends MappingException {

    public MappingNotFoundException(ContextualTermCode contextualTermCode) {
        super("Mapping for the contextual term code %s not found.".formatted(contextualTermCode));
    }
}
