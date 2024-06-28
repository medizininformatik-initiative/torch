package de.medizininformatikinitiative.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import de.medizininformatikinitiative.model.Filter;
import de.medizininformatikinitiative.model.Attribute;

import java.net.URLEncoder;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@JsonIgnoreProperties(ignoreUnknown = true)
public class AttributeGroup {


    // No-argument constructor
    public AttributeGroup() {
    }

    @JsonProperty("groupReference")
    private String groupReference;

    @JsonProperty("attributes")
    private List<Attribute> attributes;

    @JsonProperty("filter")
    private List<Filter> filter;

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


    public Map<String, String> getFiltersAsMap() {
        Map<String, String> filterMap = new HashMap<>();
        for (Filter filter : filter) {
            if ("date".equals(filter.getType())) {
                filterMap.put("date", filter.getDateFilter());
            } else {
                filterMap.putAll(filter.getCodeFilter());
            }
        }
        return filterMap;
    }


}
