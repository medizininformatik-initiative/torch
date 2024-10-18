package de.medizininformatikinitiative.torch.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import de.medizininformatikinitiative.torch.config.SpringContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public class AttributeGroup {

    private static final Logger logger = LoggerFactory.getLogger(AttributeGroup.class);

    @JsonProperty("groupReference")
    private String groupReference;

    @JsonProperty("attributes")
    private List<Attribute> attributes;

    @JsonProperty("filter")
    private List<Filter> filter;

    public UUID uuid;



    // No-argument constructor
    public AttributeGroup(){
        uuid = UUID.randomUUID();
    }

    // Getters and Setters
    public String getGroupReference() {
        return groupReference;
    }

    public void setGroupReference(String groupReference) {
        this.groupReference = groupReference;
    }

    public List<Attribute> getAttributes() {
        return attributes;
    }

    public void setAttributes(List<Attribute> attributes) {
        this.attributes = attributes;
    }

    public List<Filter> getFilter() {
        return filter;
    }

    public boolean hasFilter(){
        return filter != null && !filter.isEmpty();
    }

    public void setFilter(List<Filter> filter) {
        if(containsDuplicateDateType(filter)) {
            throw new IllegalArgumentException("Duplicate date type filter found");
        }
        this.filter = filter;
    }


    // Helper method to check for duplicate 'date' type filters
    private boolean containsDuplicateDateType(List<Filter> filterList) {
        boolean dateTypeFound = false;
        for (Filter filter : filterList) {
            if ("date".equals(filter.getType())) {
                if (dateTypeFound) {
                    return true;  // Duplicate found
                }
                dateTypeFound = true;
            }
        }
        return false;
    }

    public String getFilterString() {
        List<String> filterStrings = new ArrayList<>();
        for (Filter filter : filter) {
            if ("date".equals(filter.getType())) {
                // Add the appropriate string for date type filter
                filterStrings.add(filter.getDateFilter());
            } else {
                // Add the appropriate string for other types of filters
                filterStrings.add(filter.getCodeFilter());
            }
        }
        return String.join("&", filterStrings);
    }

    public String getResourceType() {
        return attributes.getFirst().getAttributeRef().split("\\.")[0];
    }

    public String getGroupReferenceURL() {
        String encodedString = "";
        try {
            encodedString = URLEncoder.encode(groupReference, StandardCharsets.UTF_8);
        } catch (Exception e) {
           logger.error("Group Reference Error",e);
        }
        return encodedString;
    }

    public boolean hasMustHave() {
        return attributes.stream().anyMatch(Attribute::isMustHave);
    }
}
