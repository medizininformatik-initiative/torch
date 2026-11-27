package de.medizininformatikinitiative.torch.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import de.medizininformatikinitiative.torch.model.crtdl.Code;
import de.medizininformatikinitiative.torch.model.fhir.Query;
import de.medizininformatikinitiative.torch.model.fhir.QueryParams;
import de.medizininformatikinitiative.torch.model.sq.TermCode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static de.medizininformatikinitiative.torch.model.fhir.QueryParams.codeValue;
import static de.medizininformatikinitiative.torch.model.fhir.QueryParams.conceptValue;
import static de.medizininformatikinitiative.torch.model.fhir.QueryParams.orValue;
import static de.medizininformatikinitiative.torch.model.fhir.QueryParams.stringValue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class OrQueryTest {

    static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new Jdk8Module());

    static List<List<OrQuery>> roundTrip(List<List<OrQuery>> orGroups) throws Exception {
        return MAPPER.readValue(MAPPER.writeValueAsString(orGroups), new TypeReference<>() {
        });
    }

    @Test
    void orJoinedTokenValuesRoundTripExactly() throws Exception {
        var query = Query.of("Condition", QueryParams
                .of("code", orValue(List.of(
                        conceptValue(TermCode.of("http://fhir.de/icd-10-gm", "E11.9 #1", "x")),
                        conceptValue(TermCode.of("icd10", "a,b$c", "y")),
                        codeValue(new Code("sys", "c d")))))
                .appendParam("code", stringValue("100%+&"))
                .appendParam("medication.code", conceptValue(TermCode.of("atc", "A10", "A10"))));
        var orGroups = List.of(List.of(OrQuery.of(query, Optional.of("recorded-date"))));

        var restored = roundTrip(orGroups);

        assertThat(restored).isEqualTo(orGroups);
        assertThat(restored.getFirst().getFirst().query().toString()).isEqualTo(query.toString());
        assertThat(restored.getFirst().getFirst().query().params().params())
                .extracting(QueryParams.Param::name, param -> param.value().toString())
                .containsExactlyElementsOf(query.params().params().stream()
                        .map(param -> tuple(param.name(), param.value().toString()))
                        .toList());
    }

    @Test
    void missingDateParamRoundTripsAsEmpty() throws Exception {
        var orGroups = List.of(List.of(
                OrQuery.of(Query.of("Patient", QueryParams.of("gender", stringValue("female"))), Optional.empty())));

        var restored = roundTrip(orGroups);

        assertThat(restored).isEqualTo(orGroups);
        assertThat(restored.getFirst().getFirst().dateParam()).isEmpty();
    }
}
