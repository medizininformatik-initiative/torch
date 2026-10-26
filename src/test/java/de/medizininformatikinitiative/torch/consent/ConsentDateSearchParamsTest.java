package de.medizininformatikinitiative.torch.consent;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.context.RuntimeResourceDefinition;
import ca.uhn.fhir.context.RuntimeSearchParam;
import ca.uhn.fhir.rest.api.RestSearchParameterTypeEnum;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static java.util.Map.entry;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConsentDateSearchParamsTest {

    @Test
    void derivesOneParamPerShippedDateFieldExceptRiskAssessment() throws Exception {
        var dateFields = new LinkedHashMap<String, String>();
        new ObjectMapper().readTree(new File("mappings/type_to_consent.json")).properties()
                .forEach(entry -> dateFields.put(entry.getKey(), entry.getValue().asText()));

        var params = new ConsentDateSearchParams(FhirContext.forR4(), dateFields);

        assertThat(params.searchParams()).containsOnly(
                entry("Condition", "recorded-date"),
                entry("Consent", "date"),
                entry("DiagnosticReport", "date"),
                entry("DocumentReference", "date"),
                entry("Encounter", "date"),
                entry("FamilyMemberHistory", "date"),
                entry("List", "date"),
                entry("MedicationAdministration", "effective-time"),
                entry("MedicationRequest", "authoredon"),
                entry("MedicationStatement", "effective"),
                entry("Observation", "date"),
                entry("Procedure", "date"),
                entry("Provenance", "recorded"),
                entry("ServiceRequest", "authored"),
                entry("Specimen", "collected"),
                entry("Task", "authored-on"));
        assertThat(dateFields).containsEntry("RiskAssessment", "RiskAssessment.occurrence");
        assertThat(params.searchParam("RiskAssessment")).isEmpty();
    }

    static RuntimeSearchParam param(String name, RestSearchParameterTypeEnum type, String type_, String... paths) {
        var param = mock(RuntimeSearchParam.class);
        when(param.getName()).thenReturn(name);
        when(param.getParamType()).thenReturn(type);
        when(param.getPathsSplitForResourceType(type_)).thenReturn(List.of(paths));
        return param;
    }

    static FhirContext context(String type, RuntimeSearchParam... params) {
        var ctx = mock(FhirContext.class);
        var definition = mock(RuntimeResourceDefinition.class);
        when(ctx.getResourceDefinition(type)).thenReturn(definition);
        when(definition.getSearchParams()).thenReturn(List.of(params));
        return ctx;
    }

    @Test
    void singleExactMatchIsTheSearchParam() {
        var ctx = context("Condition",
                param("recorded-date", RestSearchParameterTypeEnum.DATE, "Condition", "Condition.recordedDate"),
                param("onset-date", RestSearchParameterTypeEnum.DATE, "Condition", "Condition.onset.as(dateTime)"));

        var params = new ConsentDateSearchParams(ctx, Map.of("Condition", "Condition.recordedDate"));

        assertThat(params.searchParam("Condition")).contains("recorded-date");
    }

    @Test
    void pathsAreNotNormalized() {
        var ctx = context("RiskAssessment",
                param("date", RestSearchParameterTypeEnum.DATE, "RiskAssessment", "(RiskAssessment.occurrence as dateTime)"));

        var params = new ConsentDateSearchParams(ctx, Map.of("RiskAssessment", "RiskAssessment.occurrence"));

        assertThat(params.searchParam("RiskAssessment")).isEmpty();
        assertThat(params.searchParams()).isEmpty();
    }

    @Test
    void onlyDateParamsMatch() {
        var ctx = context("Task",
                param("authored-on", RestSearchParameterTypeEnum.STRING, "Task", "Task.authoredOn"));

        var params = new ConsentDateSearchParams(ctx, Map.of("Task", "Task.authoredOn"));

        assertThat(params.searchParam("Task")).isEmpty();
    }

    @Test
    void multipleMatchesFail() {
        var ctx = context("Encounter",
                param("date", RestSearchParameterTypeEnum.DATE, "Encounter", "Encounter.period"),
                param("period", RestSearchParameterTypeEnum.DATE, "Encounter", "Encounter.period"));

        assertThatThrownBy(() -> new ConsentDateSearchParams(ctx, Map.of("Encounter", "Encounter.period")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Encounter.period")
                .hasMessageContaining("[date, period]");
    }

    @Test
    void typesWithoutDateFieldNeedNoSearchParam() {
        var ctx = mock(FhirContext.class);

        var params = new ConsentDateSearchParams(ctx, Map.of("Patient", ""));

        assertThat(params.searchParams()).isEmpty();
        assertThat(params.searchParam("Patient")).isEmpty();
    }
}
