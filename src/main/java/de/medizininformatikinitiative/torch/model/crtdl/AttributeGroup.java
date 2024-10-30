package de.medizininformatikinitiative.torch.model.crtdl;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import de.medizininformatikinitiative.torch.model.fhir.QueryParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AttributeGroup(

        @JsonProperty("groupReference")
        String groupReference,

        @JsonProperty("attributes")
        List<Attribute> attributes,

        @JsonProperty("filter")
        List<Filter> filter,

        UUID uuid
) {
    private static final Logger logger = LoggerFactory.getLogger(AttributeGroup.class);

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

    public List<QueryParams> queryParams() {
        // Create a date filter QueryParams if it exists
        QueryParams dateParams = filter.stream()
                .filter(f -> "date".equals(f.type()))
                .findFirst()
                .map(Filter::dateFilter)
                .orElse(new QueryParams(List.of()));


        return filter.stream()
                .filter(f -> "token".equals(f.type())) // Only process filters of type "token"
                .map(Filter::codeFilter)
                .flatMap(code -> code.params().stream())
                .map(param -> {
                    QueryParams invidividualCodeParams = new QueryParams(List.of(param));
                    return invidividualCodeParams.appendParams(dateParams);
                })
                .collect(Collectors.toList());
    }


    public String resourceType() {
        return attributes.getFirst().attributeRef().split("\\.")[0];
    }

    public String groupReferenceURL() {
        try {
            return URLEncoder.encode(groupReference, StandardCharsets.UTF_8);
        } catch (Exception e) {
            logger.error("Group Reference Error", e);
            return "";
        }
    }

    public boolean hasMustHave() {
        return attributes.stream().anyMatch(Attribute::mustHave);
    }
}
