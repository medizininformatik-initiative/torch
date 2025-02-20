package de.medizininformatikinitiative.torch.model.crtdl;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

import static java.util.Objects.requireNonNull;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AttributeGroup(
        @JsonProperty()
        String name,
        @JsonProperty(required = true)
        String id,
        @JsonProperty(required = true)
        String groupReference,
        @JsonProperty(required = true)
        List<Attribute> attributes,
        List<Filter> filter,
        @JsonProperty()
        Boolean includeReferenceOnly) {

    /**
     * @param groupReference
     * @param attributes
     * @param filter         Primary constructor for directly accessed allGroups
     */
    public AttributeGroup(String id,
                          String groupReference,
                          List<Attribute> attributes,
                          List<Filter> filter
    ) {
        this("", id, groupReference, attributes, filter, false); // Default value for includeReferenceOnly
    }


    /**
     * Canonical Constructor with validation for filter duplicates and UUID generation
     *
     * @param id
     * @param groupReference
     * @param attributes
     * @param filter
     * @param includeReferenceOnly
     */
    public AttributeGroup {
        requireNonNull(id);
        requireNonNull(groupReference);
        attributes = List.copyOf(attributes);
        filter = filter == null ? List.of() : List.copyOf(filter);
        if (containsDuplicateDateFilters(filter)) {
            throw new IllegalArgumentException("Duplicate date type filter found");
        }
        if (includeReferenceOnly == null) {
            includeReferenceOnly = false;
        }
    }

    private static boolean containsDuplicateDateFilters(List<Filter> filters) {
        return filters.stream().filter(filter -> "date".equals(filter.type())).count() > 1;
    }

    /*
        public List<Query> queries(DseMappingTreeBase mappingTreeBase, String resourceType) {
            return queryParams(mappingTreeBase).stream()
                    .map(params -> Query.of(resourceType, params))
                    .toList();
        }

        private List<QueryParams> queryParams(DseMappingTreeBase mappingTreeBase) {
            List<QueryParams> codeParams = filter.stream()
                    .filter(f -> "token".equals(f.type()))
                    .flatMap(f -> f.codeFilter(mappingTreeBase).split())
                    .toList();

            QueryParams dateParams = "Patient".equals(resourceType()) ? EMPTY : filter.stream()
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
    */
    public AttributeGroup addAttributes(List<Attribute> newAttributes) {

        List<Attribute> tempAttributes = new ArrayList<>(attributes);
        tempAttributes.addAll(newAttributes);
        return new AttributeGroup(name, id, groupReference, tempAttributes, filter, includeReferenceOnly);
    }

    public boolean hasMustHave() {
        return attributes.stream().anyMatch(Attribute::mustHave);
    }

}
