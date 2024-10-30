package de.medizininformatikinitiative.torch.model.crtdl;

import com.fasterxml.jackson.annotation.JsonProperty;
import de.medizininformatikinitiative.torch.config.SpringContext;
import de.medizininformatikinitiative.torch.model.fhir.QueryParams;
import de.medizininformatikinitiative.torch.model.sq.Comparator;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public record Filter(
        @JsonProperty(value = "type", required = true) String type,
        @JsonProperty(value = "name", required = true) String name,
        @JsonProperty("codes") List<Code> codes,
        @JsonProperty("start") String start,
        @JsonProperty("end") String end
) {


    public QueryParams dateFilter() {
      QueryParams dateParams =  QueryParams.EMPTY;
        if("date".equals(type)){
            if (start != null ) {
                dateParams=dateParams.appendParam(name,QueryParams.dateValue(Comparator.GREATER_EQUAL, parseLocalDate(start)));
            }
            if (end != null ) {
                dateParams=dateParams.appendParam(name,QueryParams.dateValue(Comparator.LESS_EQUAL, parseLocalDate(end)));
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

    private LocalDate parseLocalDate(String s) {
        try {
            return LocalDate.parse(s);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid value `%s` in time restriction property `%s`.".formatted(s, name));
        }
    }


}
