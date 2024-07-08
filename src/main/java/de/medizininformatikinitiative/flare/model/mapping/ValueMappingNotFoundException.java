package de.medizininformatikinitiative.flare.model.mapping;

import de.medizininformatikinitiative.flare.model.sq.ContextualTermCode;

public class ValueMappingNotFoundException extends MappingException {

    public ValueMappingNotFoundException(ContextualTermCode contextualTermCode) {
        super("Value mapping for mapping with key `%s` was not found.".formatted(contextualTermCode));
    }
}
