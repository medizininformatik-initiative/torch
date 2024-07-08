package de.medizininformatikinitiative.flare.model.mapping;

import de.medizininformatikinitiative.flare.model.sq.ContextualTermCode;
import de.medizininformatikinitiative.flare.model.sq.TermCode;

public class AttributeMappingNotFoundException extends MappingException {

    public AttributeMappingNotFoundException(ContextualTermCode mappingKey, TermCode attributeCode) {
        super("Attribute mapping for the term code `%s` in mapping with key `%s` was  not found."
                .formatted(mappingKey, attributeCode));
    }
}
