package de.medizininformatikinitiative.flare.model.translate;

import com.fasterxml.jackson.annotation.JsonValue;
import de.medizininformatikinitiative.flare.model.fhir.Query;

import static java.util.Objects.requireNonNull;

/**
 * Expression that holds a single query.
 *
 * @param query the query
 */
public record QueryExpression(Query query) implements Expression {

    public QueryExpression {
        requireNonNull(query);
    }

    /**
     * The JSON value of a query expression is just the query string.
     *
     * @return a string representation of this query expression.
     */
    @JsonValue
    public String toJson() {
        return "[base]/" + query.toString();
    }
}
