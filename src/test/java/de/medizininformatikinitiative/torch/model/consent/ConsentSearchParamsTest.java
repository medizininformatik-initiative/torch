package de.medizininformatikinitiative.torch.model.consent;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.api.RestSearchParameterTypeEnum;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.medizininformatikinitiative.torch.model.mapping.MappingException;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConsentSearchParamsTest {

    static final FhirContext CTX = FhirContext.forR4();

    static final Map<String, String> DATE_FIELDS = Map.of(
            "Condition", "Condition.recordedDate",
            "Patient", "");

    /**
     * Search params that only search part of their date field: R4 has no search param for
     * {@code RiskAssessment.occurrencePeriod}, so the check misses risk assessments dated by a period.
     */
    static final Map<String, String> PARTIAL_PATHS = Map.of("RiskAssessment", "(RiskAssessment.occurrence as dateTime)");

    static Map<String, String> read(String file) throws IOException {
        return new ObjectMapper().readValue(new File(file), new TypeReference<>() {
        });
    }

    @Test
    void returnsTheSearchParamOfATypeWithDate() throws Exception {
        var searchParams = ConsentSearchParams.of(DATE_FIELDS, Map.of("Condition", "recorded-date"));

        assertThat(searchParams.dateSearchParam("Condition")).contains("recorded-date");
    }

    @Test
    void returnsEmptyForATypeWithoutDate() throws Exception {
        var searchParams = ConsentSearchParams.of(DATE_FIELDS, Map.of("Condition", "recorded-date"));

        assertThat(searchParams.dateSearchParam("Patient")).isEmpty();
    }

    @Test
    void rejectsLookupOfUnknownType() {
        var searchParams = ConsentSearchParams.of(DATE_FIELDS, Map.of("Condition", "recorded-date"));

        assertThatThrownBy(() -> searchParams.dateSearchParam("Observation"))
                .isInstanceOf(MappingException.class)
                .hasMessage("No consent date mapping for resource type Observation");
    }

    @Test
    void rejectsMissingSearchParam() {
        assertThatThrownBy(() -> ConsentSearchParams.of(DATE_FIELDS, Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("[Condition]");
    }

    @Test
    void rejectsBlankSearchParam() {
        assertThatThrownBy(() -> ConsentSearchParams.of(DATE_FIELDS, Map.of("Condition", " ")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("[Condition]");
    }

    @Test
    void rejectsSuperfluousSearchParam() {
        assertThatThrownBy(() -> ConsentSearchParams.of(DATE_FIELDS,
                Map.of("Condition", "recorded-date", "Patient", "birthdate", "Observation", "date")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("[Observation, Patient]");
    }

    @Test
    void shippedFilesAgree() throws Exception {
        var searchParams = ConsentSearchParams.of(read("mappings/type_to_consent.json"),
                read("mappings/type_to_consent_search_param.json"));

        assertThat(searchParams.dateSearchParam("Condition")).contains("recorded-date");
    }

    /**
     * The consent check on the FHIR server only matches the consent check during extraction if each search param
     * searches exactly the configured date field.
     */
    @Test
    void shippedSearchParamsSearchTheirDateField() throws Exception {
        var dateFields = read("mappings/type_to_consent.json");
        var searchParams = read("mappings/type_to_consent_search_param.json");

        assertThat(searchParams).isNotEmpty().allSatisfy((type, searchParamName) -> {
            var searchParam = CTX.getResourceDefinition(type).getSearchParam(searchParamName);
            assertThat(searchParam).as("%s search param %s", type, searchParamName).isNotNull();
            assertThat(searchParam.getParamType()).isEqualTo(RestSearchParameterTypeEnum.DATE);
            assertThat(Arrays.stream(searchParam.getPath().split("\\|")).map(String::trim))
                    .as("paths of %s search param %s", type, searchParamName)
                    .contains(PARTIAL_PATHS.getOrDefault(type, dateFields.get(type)));
        });
    }
}
