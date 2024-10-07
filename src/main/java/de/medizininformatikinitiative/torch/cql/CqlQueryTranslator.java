package de.medizininformatikinitiative.torch.cql;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.medizininformatikinitiative.torch.model.ccdl.StructuredQuery;
import de.numcodex.sq2cql.Translator;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/**
 * A translator for translating a {@link de.medizininformatikinitiative.torch.model.ccdl.StructuredQuery} into its CQL representation.
 */
@RequiredArgsConstructor
public class CqlQueryTranslator implements QueryTranslator {

    @NonNull
    private final Translator translator;

    @NonNull
    private final ObjectMapper jsonUtil;

    @Override
    public String translate(StructuredQuery query) throws QueryTranslationException {
        de.numcodex.sq2cql.model.structured_query.StructuredQuery structuredQuery;
        try {
            structuredQuery = jsonUtil.readValue(jsonUtil.writeValueAsString(query),
                    de.numcodex.sq2cql.model.structured_query.StructuredQuery.class);
        } catch (JsonProcessingException e) {
            throw new QueryTranslationException("cannot encode/decode structured query as JSON", e);
        }

        try {
            return translator.toCql(structuredQuery).print();
        } catch (Exception e) {
            throw new QueryTranslationException("cannot translate structured query to CQL format", e);
        }
    }
}