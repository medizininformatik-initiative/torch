package de.medizininformatikinitiative.flare.model.mapping;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum FixedCriterionType implements JsonEnum {

    @JsonProperty("code") CODE(false),
    @JsonProperty("coding") CODING(false),
    @JsonProperty("composite-concept") COMPOSITE_CONCEPT(true);

    private final boolean compositeType;

    FixedCriterionType(boolean compositeType) {
        this.compositeType = compositeType;
    }

    public boolean isCompositeType() {
        return compositeType;
    }

    @Override
    public String jsonName() {
        try {
            var field = FixedCriterionType.class.getField(name());
            return field.getAnnotation(JsonProperty.class).value();
        } catch (NoSuchFieldException e) {
            // can not happen
            throw new Error(e);
        }
    }
}
