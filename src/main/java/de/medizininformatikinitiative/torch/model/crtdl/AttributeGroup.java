package de.medizininformatikinitiative.torch.model.crtdl;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import de.medizininformatikinitiative.torch.model.fhir.Query;
import de.medizininformatikinitiative.torch.model.fhir.QueryParams;
import de.medizininformatikinitiative.torch.model.mapping.DseMappingTreeBase;

import java.util.List;

import static de.medizininformatikinitiative.torch.model.fhir.QueryParams.EMPTY;
import static de.medizininformatikinitiative.torch.model.fhir.QueryParams.stringValue;
import static java.util.Objects.requireNonNull;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AttributeGroup(
        @JsonProperty(required = true)
        String groupReference,
        @JsonProperty(required = true)
        List<Attribute> attributes,
        List<Filter> filter
) {

    // Canonical Constructor with validation for filter duplicates and UUID generation
    public AttributeGroup {
        requireNonNull(groupReference);
        attributes = List.copyOf(attributes);
        if (containsDuplicateDateFilters(filter)) {
            throw new IllegalArgumentException("Duplicate date type filter found");
        }
        filter = List.copyOf(filter);
    }

    private static boolean containsDuplicateDateFilters(List<Filter> filters) {
        return filters.stream().filter(filter -> "date".equals(filter.type())).count() > 1;
    }

    public List<Query> queries(DseMappingTreeBase mappingTreeBase) {
        return queryParams(mappingTreeBase).stream()
                .map(params -> Query.of(resourceType(), params))
                .toList();
    }

    private List<QueryParams> queryParams(DseMappingTreeBase mappingTreeBase) {
        List<QueryParams> codeParams = filter.stream()
                .filter(f -> "token".equals(f.type()))
                .flatMap(f -> f.codeFilter(mappingTreeBase).split())
                .toList();

        QueryParams dateParams = filter.stream()
                .filter(f -> "date".equals(f.type()))
                .findFirst()
                .map(Filter::dateFilter)
                .orElse(EMPTY);

        if (codeParams.isEmpty()) {
            // Add a single QueryParams with the date filter (if available) and profile parameter
            return List.of(dateParams.appendParam("_profile", stringValue(groupReference)));
        } else {
            return codeParams.stream()
                    .map(p -> p.appendParams(dateParams).appendParam("_profile", stringValue(groupReference)))
                    .toList();
        }
    }

    private String resourceType() {
        return attributes.getFirst().attributeRef().split("\\.")[0];
    }

    public boolean hasMustHave() {
        return attributes.stream().anyMatch(Attribute::mustHave);
    }
}
