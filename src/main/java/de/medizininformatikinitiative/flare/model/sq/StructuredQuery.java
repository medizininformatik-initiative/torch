package de.medizininformatikinitiative.flare.model.sq;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;

@JsonIgnoreProperties(ignoreUnknown = true)
public record StructuredQuery(CriterionGroup<CriterionGroup<Criterion>> inclusionCriteria,
                              Optional<CriterionGroup<CriterionGroup<Criterion>>> exclusionCriteria) {

    public StructuredQuery {
        requireNonNull(inclusionCriteria);
        requireNonNull(exclusionCriteria);
    }

    public static StructuredQuery of(CriterionGroup<CriterionGroup<Criterion>> inclusionCriteria) {
        return new StructuredQuery(inclusionCriteria, Optional.empty());
    }

    public static StructuredQuery of(CriterionGroup<CriterionGroup<Criterion>> inclusionCriteria,
                                     CriterionGroup<CriterionGroup<Criterion>> exclusionCriteria) {
        return new StructuredQuery(inclusionCriteria, Optional.of(exclusionCriteria));
    }

    @JsonCreator
    public static StructuredQuery fromJson(@JsonProperty("inclusionCriteria") List<List<Criterion>> inclusionCriteria,
                                           @JsonProperty("exclusionCriteria") List<List<Criterion>> exclusionCriteria) {
        if (inclusionCriteria == null) {
            throw new IllegalArgumentException("empty inclusion criteria");
        }

        var inclusionCriteriaGroups = inclusionCriteria.stream().flatMap(StructuredQuery::createCriterionGroup).toList();

        if (inclusionCriteriaGroups.isEmpty()) {
            throw new IllegalArgumentException("empty inclusion criteria");
        }

        var inclusionCriteriaGroup = new CriterionGroup<>(inclusionCriteriaGroups.get(0), inclusionCriteriaGroups.stream().skip(1).toList());

        if (exclusionCriteria == null) {
            return new StructuredQuery(inclusionCriteriaGroup, Optional.empty());
        }

        var exclusionCriteriaGroups = exclusionCriteria.stream().flatMap(StructuredQuery::createCriterionGroup).toList();

        if (exclusionCriteriaGroups.isEmpty()) {
            return new StructuredQuery(inclusionCriteriaGroup, Optional.empty());
        }
        return new StructuredQuery(inclusionCriteriaGroup, Optional.of(new CriterionGroup<>(exclusionCriteriaGroups.get(0),
                exclusionCriteriaGroups.stream().skip(1).toList())));
    }

    private static Stream<CriterionGroup<Criterion>> createCriterionGroup(List<Criterion> criteria) {
        return criteria == null || criteria.isEmpty()
                ? Stream.empty()
                : Stream.of(new CriterionGroup<>(criteria.get(0), criteria.stream().skip(1).toList()));
    }
}
