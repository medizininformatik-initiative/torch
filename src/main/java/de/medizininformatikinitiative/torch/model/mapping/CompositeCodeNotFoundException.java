package de.medizininformatikinitiative.torch.model.mapping;

public class CompositeCodeNotFoundException extends MappingException {

    public CompositeCodeNotFoundException(JsonEnum type) {
        super("Expected mapping to have a `compositeCode` property because the type is `%s`.".formatted(type.jsonName()));
    }
}
