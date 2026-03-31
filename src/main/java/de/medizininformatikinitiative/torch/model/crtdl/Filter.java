package de.medizininformatikinitiative.torch.model.crtdl;

import com.fasterxml.jackson.annotation.JsonProperty;
import de.medizininformatikinitiative.torch.model.fhir.QueryParams;
import de.medizininformatikinitiative.torch.model.mapping.DseMappingTreeBase;
import de.medizininformatikinitiative.torch.model.sq.Comparator;

import java.time.LocalDate;
import java.util.List;

import static java.util.Objects.requireNonNull;

public record Filter(
        @JsonProperty(required = true) String type,
        @JsonProperty(required = true) String name,
        @JsonProperty List<Code> codes,
        @JsonProperty LocalDate start,
        @JsonProperty LocalDate end
) {

    public Filter {
        requireNonNull(type);
        requireNonNull(name);
        codes = codes == null ? List.of() : List.copyOf(codes);
    }

    public Filter(String type, String name, List<Code> codes) {
        this(type, name, codes, null, null);
    }

    public Filter(String type, String name, LocalDate start, LocalDate end) {
        this(type, name, List.of(), start, end);
    }

    public QueryParams dateFilter() {
        QueryParams dateParams = QueryParams.EMPTY;
        if ("date".equals(type)) {
            if (start != null) {
                dateParams = dateParams.appendParam(name, QueryParams.dateValue(Comparator.GREATER_EQUAL, start));
            }
            if (end != null) {
                dateParams = dateParams.appendParam(name, QueryParams.dateValue(Comparator.LESS_EQUAL, end));
            }

        }
        return dateParams;
    }


    public QueryParams codeFilter(DseMappingTreeBase mappingBase) {
        QueryParams codeParams = QueryParams.EMPTY;

        if ("token".equals(type)) {
            codeParams = codes.stream()
                    .flatMap(code -> java.util.stream.Stream.concat(
                            java.util.stream.Stream.of(code),
                            mappingBase.expand(code.system(), code.code())
                                    .map(c -> new Code(code.system(), c))
                    ))
                    .distinct()
                    .reduce(codeParams,
                            (params, resolvedCode) -> params.appendParam(name, QueryParams.codeValue(resolvedCode)),
                            QueryParams::appendParams
                    );
        }

        return codeParams;
    }
}
