package de.medizininformatikinitiative.torch.service;

import ca.uhn.fhir.context.FhirContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import de.medizininformatikinitiative.torch.exceptions.ValidationException;
import de.medizininformatikinitiative.torch.management.CompartmentManager;
import de.medizininformatikinitiative.torch.model.crtdl.*;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttribute;
import de.medizininformatikinitiative.torch.setup.IntegrationTestSetup;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


class CrtdlValidatorServiceTest {
    private final IntegrationTestSetup itSetup = new IntegrationTestSetup();
    private static final FilterService filterService = new FilterService(FhirContext.forR4(), "search-parameters.json");
    private final CrtdlValidatorService validatorService = new CrtdlValidatorService(itSetup.structureDefinitionHandler(),
            new StandardAttributeGenerator(new CompartmentManager("compartmentdefinition-patient.json"), itSetup.structureDefinitionHandler()), filterService);
    JsonNode node = JsonNodeFactory.instance.objectNode();

    AttributeGroup patientGroup = new AttributeGroup("patientGroupId", "https://www.medizininformatik-initiative.de/fhir/core/modul-person/StructureDefinition/PatientPseudonymisiert", List.of(), List.of());

    CrtdlValidatorServiceTest() throws IOException {
    }

    @BeforeAll
    static void init() {
        filterService.init();
    }

    @Test
    void unknownProfile() throws ValidationException {
        Crtdl crtdl = new Crtdl(node, new DataExtraction(List.of(patientGroup, new AttributeGroup("test", "unknown.test", List.of(), List.of()))));

        assertThatThrownBy(() -> {
            validatorService.validate(crtdl);
        }).isInstanceOf(ValidationException.class)
                .hasMessageContaining("Unknown Profile: unknown.test");

    }


    @Test
    void unknownAttribute() throws ValidationException {
        Crtdl crtdl = new Crtdl(node, new DataExtraction(List.of(patientGroup, new AttributeGroup("test", "https://www.medizininformatik-initiative.de/fhir/core/modul-labor/StructureDefinition/ObservationLab", List.of(new Attribute("Condition.unknown", false)), List.of()))));

        assertThatThrownBy(() -> {
            validatorService.validate(crtdl);
        }).isInstanceOf(ValidationException.class)
                .hasMessageContaining("Unknown Attribute Condition.unknown in group test");

    }

    @Test
    void referenceWithoutLinkedGroups() throws ValidationException {
        Crtdl crtdl = new Crtdl(node, new DataExtraction(List.of(patientGroup, new AttributeGroup("test", "https://www.medizininformatik-initiative.de/fhir/core/modul-labor/StructureDefinition/ObservationLab", List.of(new Attribute("Observation.subject", false)), List.of()))));

        assertThatThrownBy(() -> {
            validatorService.validate(crtdl);
        }).isInstanceOf(ValidationException.class)
                .hasMessageContaining("Reference Attribute Observation.subject without linked Groups in group test");

    }

    @Test
    void validInput_withoutFilter() throws ValidationException {
        Crtdl crtdl = new Crtdl(node, new DataExtraction(List.of(patientGroup)));

        var validatedCrtdl = validatorService.validate(crtdl);

        assertThat(validatedCrtdl).isNotNull();
        assertThat(validatedCrtdl.dataExtraction().attributeGroups().getFirst().compiledFilter()).isNull();
        assertThat(validatedCrtdl.dataExtraction().attributeGroups().getFirst().attributes()).isEqualTo(
                List.of(
                        new AnnotatedAttribute("Patient.id", "Patient.id", "Patient.id", false),
                        new AnnotatedAttribute("Patient.meta.profile", "Patient.meta.profile", "Patient.meta.profile", false)
                ));
    }

    @Test
    void validInput_withFilter() throws ValidationException {
        Crtdl crtdl = new Crtdl(node, new DataExtraction(List.of(patientGroup,
                new AttributeGroup("test", "https://www.medizininformatik-initiative.de/fhir/core/modul-labor/StructureDefinition/ObservationLab",
                        List.of(), List.of(new Filter("token", "code", List.of(new Code("some-system", "some-code"))))))));

        var validatedCrtdl = validatorService.validate(crtdl);

        assertThat(validatedCrtdl).isNotNull();
        assertThat(validatedCrtdl.dataExtraction().attributeGroups().get(1).compiledFilter()).isNotNull();
        assertThat(validatorService.validate(crtdl).dataExtraction().attributeGroups().get(1).attributes()).isEqualTo(
                List.of(
                        new AnnotatedAttribute("Observation.id", "Observation.id", "Observation.id", false),
                        new AnnotatedAttribute("Observation.meta.profile", "Observation.meta.profile", "Observation.meta.profile", false),
                        new AnnotatedAttribute("Observation.subject", "Observation.subject", "Observation.subject", false, List.of("patientGroupId"))
                ));
    }



}