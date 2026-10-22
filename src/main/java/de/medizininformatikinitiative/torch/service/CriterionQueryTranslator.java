package de.medizininformatikinitiative.torch.service;

import de.medizininformatikinitiative.torch.model.fhir.Query;
import de.medizininformatikinitiative.torch.model.mapping.MappingContext;
import de.medizininformatikinitiative.torch.model.mapping.MappingException;
import de.medizininformatikinitiative.torch.model.sq.Criterion;
import de.medizininformatikinitiative.torch.model.sq.expanded.ExpandedCriterion;

import java.util.List;

import static java.util.Objects.requireNonNull;

/**
 * Translates a single CCDL criterion into the FHIR search queries Flare would execute for it (ported from Flare).
 */
public class CriterionQueryTranslator {

    private final MappingContext mappingContext;

    public CriterionQueryTranslator(MappingContext mappingContext) {
        this.mappingContext = requireNonNull(mappingContext);
    }

    /**
     * @return the queries whose union of matching resources fulfils {@code criterion}
     * @throws MappingException if the criterion cannot be translated with the ontology's mappings
     */
    public List<Query> toQuery(Criterion criterion) throws MappingException {
        return criterion.expand(mappingContext).stream().map(ExpandedCriterion::toQuery).toList();
    }
}
