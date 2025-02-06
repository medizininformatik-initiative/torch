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

    /**
     * Looks up the resourceType from ElementIds
     *
     * @return "unknown" if no ElementId specified and ResourceType otherwise.
     */
    public String resourceTypeFromElementIDs() {
        return attributes.isEmpty() ? "unknown" : attributes.getFirst().attributeRef().split("\\.")[0];
    }

    public AttributeGroup addAttributes(List<Attribute> newAttributes) {

        List<Attribute> tempAttributes = new ArrayList<>(attributes);
        tempAttributes.addAll(newAttributes);
        return new AttributeGroup(name, id, groupReference, tempAttributes, filter, includeReferenceOnly);
    }

    public boolean hasMustHave() {
        return attributes.stream().anyMatch(Attribute::mustHave);
    }

}
