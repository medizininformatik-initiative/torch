package de.medizininformatikinitiative.torch.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import de.medizininformatikinitiative.torch.model.fhir.Query;
import de.medizininformatikinitiative.torch.model.fhir.QueryParams;

import java.util.List;
import java.util.Optional;

import static java.util.Objects.requireNonNull;

/**
 * One OR-alternative of an inclusion criterion OR-group: a translated FHIR search query and the search param of its
 * consent date, if its resource type has one.
 *
 * <p>The translation of a job is persisted in this form (see {@link JobPersistenceService#inclusionQueries}), so the
 * params are kept in their order and with their values rendered as sent to the FHIR server. OR-joined token values
 * therefore stay one comma-separated value.
 *
 * @param type      the resource type of the query
 * @param params    the query params, in query order
 * @param dateParam the search param of the type's consent date, or empty if the type is not restricted by consent date
 */
public record OrQuery(@JsonProperty("type") String type,
                      @JsonProperty("params") List<Param> params,
                      @JsonProperty("dateParam") Optional<String> dateParam) {

    public OrQuery {
        requireNonNull(type);
        params = List.copyOf(params);
        requireNonNull(dateParam);
    }

    public static OrQuery of(Query query, Optional<String> dateParam) {
        return new OrQuery(query.type(), query.params().params().stream()
                .map(param -> new Param(param.name(), param.value().toString()))
                .toList(), dateParam);
    }

    /**
     * @return the query with the rendered param values as string values
     */
    public Query query() {
        return Query.of(type, new QueryParams(params.stream()
                .map(param -> new QueryParams.Param(param.name(), QueryParams.stringValue(param.value())))
                .toList()));
    }

    /**
     * A query param with its value rendered as sent to the FHIR server.
     */
    public record Param(@JsonProperty("name") String name, @JsonProperty("value") String value) {

        public Param {
            requireNonNull(name);
            requireNonNull(value);
        }
    }
}
