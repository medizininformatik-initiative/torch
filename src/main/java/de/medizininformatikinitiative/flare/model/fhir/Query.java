package de.medizininformatikinitiative.flare.model.fhir;

import static java.util.Objects.requireNonNull;

public record Query(String type, QueryParams params) {

    public Query {
        requireNonNull(type);
        requireNonNull(params);
    }

    public static Query of(String type, QueryParams params) {
        return new Query(type, params);
    }

    public static Query ofType(String type) {
        return new Query(type, QueryParams.EMPTY);
    }

    @Override
    public String toString() {
        return params.toString().isEmpty() ? type : type + "?" + params;
    }
}
