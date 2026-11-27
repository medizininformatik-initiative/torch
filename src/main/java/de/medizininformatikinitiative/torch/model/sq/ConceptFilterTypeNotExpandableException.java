package de.medizininformatikinitiative.torch.model.sq;

import de.medizininformatikinitiative.torch.model.mapping.FilterMappingType;
import de.medizininformatikinitiative.torch.model.mapping.MappingException;

public class ConceptFilterTypeNotExpandableException extends MappingException {
    public ConceptFilterTypeNotExpandableException(FilterMappingType type) {
        super("Concept Filter of Mapping Filter Type %s could not be expanded.".formatted(type.name()));
    }
}
