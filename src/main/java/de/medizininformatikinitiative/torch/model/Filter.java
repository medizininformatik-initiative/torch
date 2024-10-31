package de.medizininformatikinitiative.torch.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import de.medizininformatikinitiative.torch.model.mapping.DseMappingTreeBase;

import java.util.ArrayList;
import java.util.List;

public record Filter(
        @JsonProperty("type") String type,
        @JsonProperty("name") String name,
        @JsonProperty("codes") List<Code> codes,
        @JsonProperty("start") String start,
        @JsonProperty("end") String end
) {

    public String getDateFilter() {
        StringBuilder filterString = new StringBuilder();
        if ("date".equals(type)) {
            if (start != null && !start.trim().isEmpty()) {
                filterString.append(name).append("=ge").append(start);
            }
            if (end != null && !end.trim().isEmpty()) {
                if (filterString.length() > 0) {
                    filterString.append("&");
                }
                filterString.append(name).append("=le").append(end);
            }
        }
        return filterString.toString();
    }

    public String getCodeFilter(DseMappingTreeBase mappingBase) {
        if (!"token".equals(type)) {
            return "";
        }

        List<String> codeUrls = new ArrayList<>();
        for (Code code : codes) {
            String system = code.system();
            var expandedCodes = mappingBase.expand(system, code.code()).map(c -> new Code(system, c));

            codeUrls.addAll(expandedCodes.map(Code::getCodeURL).toList());
        }

        return name + "=" + String.join(",", codeUrls);
    }
}
