package de.medizininformatikinitiative.torch.model.mapping;

import de.medizininformatikinitiative.torch.model.sq.ContextualTermCode;

public class MappingNotFoundException extends MappingException {

    public MappingNotFoundException(ContextualTermCode contextualTermCode) {
        super("Mapping for the contextual term code %s not found.".formatted(contextualTermCode));
    }
}
