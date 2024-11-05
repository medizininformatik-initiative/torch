package de.medizininformatikinitiative.torch.model.crtdl;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import de.medizininformatikinitiative.torch.model.fhir.Query;
import de.medizininformatikinitiative.torch.model.fhir.QueryParams;
import de.medizininformatikinitiative.torch.model.mapping.DseMappingTreeBase;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import static de.medizininformatikinitiative.torch.model.fhir.QueryParams.EMPTY;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AttributeGroup(

        @JsonProperty(required = true)
        String groupReference,
        @JsonProperty(required = true)
        List<Attribute> attributes,
        List<Filter> filter,
        UUID uuid
) {


    // Canonical Constructor with validation for filter duplicates and UUID generation
    public AttributeGroup {
        if (containsDuplicateDateType(filter)) {
            throw new IllegalArgumentException("Duplicate date type filter found");
        }
        uuid = uuid != null ? uuid : UUID.randomUUID();
    }

    public boolean hasFilter() {
        return filter != null && !filter.isEmpty();
    }

    // Helper method to check for duplicate 'date' type filters
    private static boolean containsDuplicateDateType(List<Filter> filterList) {
        boolean dateTypeFound = false;
        for (Filter filter : filterList) {
            if ("date".equals(filter.type())) {
                if (dateTypeFound) {
                    return true;  // Duplicate found
                }
                dateTypeFound = true;
            }
        }
        return false;
    }

    public List<Query> queries(DseMappingTreeBase mappingTreeBase) {
        List<QueryParams> paramsList = queryParams(mappingTreeBase);
        return paramsList.stream()
                .map(x -> new Query(resourceType(), x))
                .collect(Collectors.toList());
    }

    public List<QueryParams> queryParams(DseMappingTreeBase mappingTreeBase) {

        List<QueryParams> paramsList = filter.stream()
                .filter(f -> "token".equals(f.type()))
                .map(f -> f.codeFilter(mappingTreeBase))
                .filter(Objects::nonNull)
                .flatMap(code -> code.params().stream())
                .map(param -> {
                    QueryParams individualCodeParams = new QueryParams(List.of(param));
                    return individualCodeParams;
                })
                .collect(Collectors.toList());

        QueryParams dateParams = filter.stream()
                .filter(f -> "date".equals(f.type()))
                .findFirst()
                .map(Filter::dateFilter)
                .orElse(EMPTY);

        if (paramsList.isEmpty()) {
            // Add a single QueryParams with the date filter (if available) and profile parameter
            QueryParams defaultParams = EMPTY
                    .appendParams(dateParams)
                    .appendParam("_profile", QueryParams.stringValue(groupReference));
            paramsList.add(defaultParams);
        } else {
            paramsList = paramsList.stream()
                    .map(p -> p.appendParams(dateParams))
                    .map(p -> p.appendParam("_profile", QueryParams.stringValue(groupReference)))
                    .collect(Collectors.toList());
        }
        return paramsList;
    }


    public String resourceType() {
        return attributes.getFirst().attributeRef().split("\\.")[0];
    }

    public boolean hasMustHave() {
        return attributes.stream().anyMatch(Attribute::mustHave);
    }
}
