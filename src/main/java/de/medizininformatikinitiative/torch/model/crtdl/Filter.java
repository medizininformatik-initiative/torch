package de.medizininformatikinitiative.torch.model.crtdl;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import de.medizininformatikinitiative.torch.config.SpringContext;
import de.medizininformatikinitiative.torch.model.fhir.QueryParams;
import de.medizininformatikinitiative.torch.model.sq.Comparator;
import net.sf.saxon.trans.SymbolicName;
import net.sf.saxon.value.DateValue;
import org.springframework.cglib.core.Local;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.Objects.requireNonNull;

public record Filter(
        @JsonProperty(required = true) String type,
        @JsonProperty(required = true) String name,
        List<Code> codes,
        LocalDate start,
        LocalDate end
) {

    public Filter {
        requireNonNull(type);
        requireNonNull(name);
        codes = codes == null ? List.of() : List.copyOf(codes);
    }

    public Filter(String type, String name, List<Code> codes) {
        this(type, name, codes, null,null);
    }

    public Filter(String type, String name, LocalDate start, LocalDate end) {
        this(type, name, List.of(), start, end);
    }

    public QueryParams dateFilter() {
      QueryParams dateParams =  QueryParams.EMPTY;
        if("date".equals(type)){
            if (start != null ) {
                dateParams=dateParams.appendParam(name,QueryParams.dateValue(Comparator.GREATER_EQUAL, start));
            }
            if (end != null ) {
                dateParams=dateParams.appendParam(name,QueryParams.dateValue(Comparator.LESS_EQUAL, end));
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
