package de.medizininformatikinitiative.torch.model.mapping;

import de.medizininformatikinitiative.torch.model.sq.ContextualTermCode;
import de.medizininformatikinitiative.torch.model.sq.TermCode;

public class AttributeMappingNotFoundException extends MappingException {

    public AttributeMappingNotFoundException(ContextualTermCode mappingKey, TermCode attributeCode) {
        super("Attribute mapping for the term code `%s` in mapping with key `%s` was  not found."
                .formatted(mappingKey, attributeCode));
    }
}
