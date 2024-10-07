package de.medizininformatikinitiative.torch.model.ccdl;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

@JsonInclude(Include.NON_EMPTY)
@Builder
public record StructuredQuery(
        @JsonProperty URI version,
        @JsonProperty("inclusionCriteria") List<List<Criterion>> inclusionCriteria,
        @JsonProperty("exclusionCriteria") List<List<Criterion>> exclusionCriteria,
        @JsonProperty("display") String display
) {
    public static StructuredQuery createImmutableStructuredQuery(MutableStructuredQuery mutableStructuredQuery) {
        List<List<Criterion>> inclusionCriteria = new ArrayList<>();
        for (List<MutableCriterion> outerList : mutableStructuredQuery.getInclusionCriteria()) {
            List<Criterion> innerList = new ArrayList<>();
            for (MutableCriterion criterion : outerList) {
                innerList.add(Criterion.createImmutableCriterion(criterion));
            }
            inclusionCriteria.add(innerList);
        }

        List<List<Criterion>> exclusionCriteria = new ArrayList<>();
        for (List<MutableCriterion> outerList : mutableStructuredQuery.getExclusionCriteria()) {
            List<Criterion> innerList = new ArrayList<>();
            for (MutableCriterion criterion : outerList) {
                innerList.add(Criterion.createImmutableCriterion(criterion));
            }
            exclusionCriteria.add(innerList);
        }

        return StructuredQuery.builder()
                .version(mutableStructuredQuery.getVersion())
                .display(mutableStructuredQuery.getDisplay())
                .inclusionCriteria(inclusionCriteria)
                .exclusionCriteria(exclusionCriteria)
                .build();
    }
}
