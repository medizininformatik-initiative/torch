package de.medizininformatikinitiative.torch.model.consent;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.api.RestSearchParameterTypeEnum;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.Arrays;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConsentDateMappingTest {

    static final FhirContext CTX = FhirContext.forR4();

    /**
     * Search params that only search part of their date field: R4 has no search param for
     * {@code RiskAssessment.occurrencePeriod}, so the check misses risk assessments dated by a period.
     */
    static final Map<String, String> PARTIAL_PATHS = Map.of("RiskAssessment", "(RiskAssessment.occurrence as dateTime)");

    @Test
    void dateFieldAndSearchParamHaveToBeSetTogether() {
        assertThatThrownBy(() -> new ConsentDateMapping("Condition.recordedDate", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ConsentDateMapping(null, "recorded-date"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(new ConsentDateMapping(null, null).hasDate()).isFalse();
    }

    /**
     * The consent check on the FHIR server only matches the consent check during extraction if each search param
     * searches exactly the configured date field.
     */
    @Test
    void shippedSearchParamsSearchTheirDateField() throws Exception {
        Map<String, ConsentDateMapping> mappings = new ObjectMapper().readValue(new File("mappings/type_to_consent.json"),
                new TypeReference<>() {
                });

        assertThat(mappings).allSatisfy((type, mapping) -> {
            if (mapping.hasDate()) {
                var searchParam = CTX.getResourceDefinition(type).getSearchParam(mapping.dateSearchParam());
                assertThat(searchParam).as("%s search param %s", type, mapping.dateSearchParam()).isNotNull();
                assertThat(searchParam.getParamType()).isEqualTo(RestSearchParameterTypeEnum.DATE);
                assertThat(Arrays.stream(searchParam.getPath().split("\\|")).map(String::trim))
                        .as("paths of %s search param %s", type, mapping.dateSearchParam())
                        .contains(PARTIAL_PATHS.getOrDefault(type, mapping.dateField()));
            }
        });
    }
}
