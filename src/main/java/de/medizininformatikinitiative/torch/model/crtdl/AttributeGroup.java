package de.medizininformatikinitiative.torch.model.crtdl;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import de.medizininformatikinitiative.torch.model.fhir.Query;
import de.medizininformatikinitiative.torch.model.fhir.QueryParams;
import de.medizininformatikinitiative.torch.model.mapping.DseMappingTreeBase;

import java.util.ArrayList;
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
        List<Filter> filter,
        @JsonProperty
        Boolean includeReferenceOnly) {

    /**
     * @param groupReference
     * @param attributes
     * @param filter         Primary constructor for directly accessed groups
     */
    public AttributeGroup(
            String groupReference,
            List<Attribute> attributes,
            List<Filter> filter
    ) {
        this(groupReference, attributes, filter, false); // Default value for includeReferenceOnly
    }

    // Canonical Constructor with validation for filter duplicates and UUID generation
    public AttributeGroup {
        requireNonNull(groupReference);
        attributes = List.copyOf(attributes);
        filter = filter == null ? List.of() : List.copyOf(filter);
        if (containsDuplicateDateFilters(filter)) {
            throw new IllegalArgumentException("Duplicate date type filter found");
        }
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


    public AttributeGroup addStandardAttributes(String className) {
        List<Attribute> tempAttributes = new ArrayList<>(attributes);

        tempAttributes.add(new Attribute(className + ".id", true));
        tempAttributes.add(new Attribute(className + ".meta.profile", true));

        if (!"Patient".equals(className) && !"Consent".equals(className)) {
            tempAttributes.add(new Attribute(className + ".subject.reference", true));
        }
        if ("Consent".equals(className)) {
            tempAttributes.add(new Attribute(className + ".patient.reference", true));
        }
        if ("Observation".equals(className)) {
            tempAttributes.add(new Attribute(className + ".status", true));
        }
        return new AttributeGroup(groupReference, tempAttributes, filter);
    }

    //TODO Should be extracted from StructureDef Type attribute.
    public String resourceType() {
        return attributes.getFirst().attributeRef().split("\\.")[0];
    }

    public boolean hasMustHave() {
        return attributes.stream().anyMatch(Attribute::mustHave);
    }
}
