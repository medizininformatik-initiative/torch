package de.medizininformatikinitiative.torch.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import de.medizininformatikinitiative.torch.config.SpringContext;

import java.util.*;
import java.util.stream.Stream;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Filter {

    // No-argument constructor
    public Filter() {
    }

    // Constructor for direct instantiation (for testing)
    public Filter(String type, String name, List<Code> codes) {
        this.type = type;
        this.name = name;
        this.codes = codes;
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

    public String getName() {
        return name;
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

    public String getCodeFilter() {
        String result="";
        if (type.equals("token")) {
            result+=name+"=";
            List<String> codeUrls = new ArrayList<>();
            for (Code code : codes) {
                codeUrls.addAll(List.of(code.getCodeURL()));
            }
            result += String.join(",", codeUrls);
        }
        return result;
    }

    public Stream<Filter> expandFilter() {

        if (! type.equals("token")){
            Filter singleFilter = this;
            return List.of(singleFilter).stream();
        }

        List<Filter> expandedFilter = new ArrayList<>();

        for (Code code : codes) {
            String s = code.getSystem();
            var tempExpandedFilter = SpringContext.getDseMappingTreeBase().expand(s, code.getCode()).map(c ->
                    new Filter("token", "code", List.of(new Code(s, c))));



            expandedFilter.addAll(tempExpandedFilter.toList());
        }

       return expandedFilter.stream();

    }


}
