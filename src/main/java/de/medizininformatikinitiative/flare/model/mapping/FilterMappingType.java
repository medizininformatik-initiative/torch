package de.medizininformatikinitiative.flare.model.mapping;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum FilterMappingType implements JsonEnum {
    @JsonProperty("code") CODE(false),
    @JsonProperty("concept") CONCEPT(false),
    @JsonProperty("composite-quantity") COMPOSITE_QUANTITY(true),
    @JsonProperty("composite-concept") COMPOSITE_CONCEPT(true),
    @JsonProperty("reference") REFERENCE(false),
    @JsonProperty("quantity") QUANTITY(false),
    @JsonProperty("Age") AGE(false);

    private final boolean compositeType;

    FilterMappingType(boolean compositeType) {
        this.compositeType = compositeType;
    }

    public boolean isCompositeType() {
        return compositeType;
    }

    @Override
    public String jsonName() {
        try {
            var field = FilterMappingType.class.getField(name());
            return field.getAnnotation(JsonProperty.class).value();
        } catch (NoSuchFieldException e) {
            // can not happen
            throw new Error(e);
        }
    }
}
