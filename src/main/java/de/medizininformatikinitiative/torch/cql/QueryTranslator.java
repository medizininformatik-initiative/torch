package de.medizininformatikinitiative.torch.cql;


import de.medizininformatikinitiative.torch.model.ccdl.StructuredQuery;

/**
 * Describes an entity that is capable of translating a @{link StructuredQuery}
 * into another format and returning its string representation.
 */
public interface QueryTranslator {

    /**
     * Translates a given {@link StructuredQuery} into another format.
     *
     * @param query The structured query that gets translated.
     * @return A string representation of the translated structured query in the targeted format.
     * @throws QueryTranslationException If the translation fails.
     */
    String translate(StructuredQuery query) throws QueryTranslationException
    ;
}
