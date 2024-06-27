package de.medizininformatikinitiative.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import de.medizininformatikinitiative.model.Code;

import java.lang.reflect.Array;
import java.util.LinkedList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Filter {

    // No-argument constructor
    public Filter() {
    }

    @JsonProperty("type")
    private String type;

    @JsonProperty("name")
    private String name;

    @JsonProperty("codes")
    private List<Code> codes;

    @JsonProperty("start")
    private String start;

    @JsonProperty("end")
    private String end;

    // Getters and Setters

    String getType() {
        return type;
    }

    String getDateFilter() {
        String filterString = "";
        if (type.equals("date")) {

            if (start != null && !start.trim().isEmpty()) {
                filterString += name + "=ge" + start;
            }
            if (end != null && !end.trim().isEmpty()) {
                if (!filterString.isEmpty()) {
                    filterString += "&";
                }
                filterString += name + "=le" + end;
            }

        }
        return filterString;
    }

    List<String> getCodeFilter() {
        List<String> searchClauses = new LinkedList<String>();
        if (type.equals("token")) {
            for (Code code : codes) {
                searchClauses.add(name + "=" + code.getCodeURL());
            }

        }
        return searchClauses;
    }


}
