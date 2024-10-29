package de.medizininformatikinitiative.torch.model.crtdl;

import com.fasterxml.jackson.annotation.JsonProperty;
import de.medizininformatikinitiative.torch.config.SpringContext;
import de.medizininformatikinitiative.torch.model.fhir.QueryParams;
import de.medizininformatikinitiative.torch.model.sq.Comparator;

import java.time.LocalDate;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public record Filter(
        @JsonProperty("type") String type,
        @JsonProperty("name") String name,
        @JsonProperty("codes") List<Code> codes,
        @JsonProperty("start") String start,
        @JsonProperty("end") String end
) {


    public QueryParams dateFilter() {
      QueryParams dateParams =  QueryParams.EMPTY;
        if("date".equals(type)){
            if (start != null && !start.trim().isEmpty()) {
                dateParams=dateParams.appendParam(name,QueryParams.dateValue(Comparator.GREATER_EQUAL, LocalDate.parse(start)));
            }
            if (end != null && !end.trim().isEmpty()) {
                dateParams=dateParams.appendParam(name,QueryParams.dateValue(Comparator.LESS_EQUAL, LocalDate.parse(end)));
            }

        }
        return  dateParams;
    }


    public QueryParams codeFilter() {
        QueryParams codeParams = QueryParams.EMPTY;

        if ("token".equals(type)) {
            codeParams = codes.stream()
                    .flatMap(code -> SpringContext.getDseMappingTreeBase()
                            .expand(code.system(), code.code())
                            .map(c -> new Code(code.system(), c))
                    )
                    .reduce(codeParams,
                            (params, expandedCode) -> params.appendParam(name, QueryParams.codeValue(expandedCode)),
                            QueryParams::appendParams
                    );
        }

        return codeParams;
    }


}
