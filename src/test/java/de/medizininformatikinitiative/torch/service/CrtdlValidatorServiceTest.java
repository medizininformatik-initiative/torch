package de.medizininformatikinitiative.torch.service;

import ca.uhn.fhir.context.FhirContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import de.medizininformatikinitiative.torch.exceptions.ConsentFormatException;
import de.medizininformatikinitiative.torch.exceptions.ValidationException;
import de.medizininformatikinitiative.torch.management.CompartmentManager;
import de.medizininformatikinitiative.torch.model.crtdl.Attribute;
import de.medizininformatikinitiative.torch.model.crtdl.AttributeGroup;
import de.medizininformatikinitiative.torch.model.crtdl.Code;
import de.medizininformatikinitiative.torch.model.crtdl.Crtdl;
import de.medizininformatikinitiative.torch.model.crtdl.DataExtraction;
import de.medizininformatikinitiative.torch.model.crtdl.Filter;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttribute;
import de.medizininformatikinitiative.torch.setup.IntegrationTestSetup;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


class CrtdlValidatorServiceTest {
    private final IntegrationTestSetup itSetup = new IntegrationTestSetup();
    private final CrtdlValidatorService validatorService = new CrtdlValidatorService(itSetup.structureDefinitionHandler(),
            new StandardAttributeGenerator(new CompartmentManager("compartmentdefinition-patient.json"), itSetup.structureDefinitionHandler()));
    JsonNode node = JsonNodeFactory.instance.objectNode();

    AttributeGroup patientGroup = new AttributeGroup("patientGroupId", "https://www.medizininformatik-initiative.de/fhir/core/modul-person/StructureDefinition/PatientPseudonymisiert", List.of(), List.of());

    CrtdlValidatorServiceTest() throws IOException {
    }


    @Test
    void consentViolated() throws JsonProcessingException {
        String json = """
                {
                  "exclusionCriteria": [
                    [
                      {
                        "context": {
                          "code": "Einwilligung",
                          "system": "fdpg.mii.cds"
                        },
                        "termCodes": [
                          { "system": "s1", "code": "A2" }
                        ]
                      }
                    ]
                  ]
                }""";
        ObjectMapper mapper = new ObjectMapper();
        JsonNode cohortDefinition = mapper.readTree(json);
        Crtdl crtdl = new Crtdl(cohortDefinition, new DataExtraction(List.of(patientGroup, new AttributeGroup("test", "unknown.test", List.of(), List.of()))));

        assertThatThrownBy(() -> validatorService.validateAndAnnotate(crtdl)).isInstanceOf(ConsentFormatException.class)
                .hasMessageContaining("Exclusion criteria must not contain Einwilligung consent codes.");
    }

    @Test
    void unknownProfile() {
        Crtdl crtdl = new Crtdl(node, new DataExtraction(List.of(patientGroup, new AttributeGroup("test", "unknown.test", List.of(), List.of()))));

        assertThatThrownBy(() -> validatorService.validateAndAnnotate(crtdl)).isInstanceOf(ValidationException.class)
                .hasMessageContaining("Unknown Profile: unknown.test");

    }


    @Test
    void unknownAttribute() {
        Crtdl crtdl = new Crtdl(node, new DataExtraction(List.of(patientGroup, new AttributeGroup("test", "https://www.medizininformatik-initiative.de/fhir/core/modul-labor/StructureDefinition/ObservationLab", List.of(new Attribute("Condition.unknown", false)), List.of()))));

        assertThatThrownBy(() -> validatorService.validateAndAnnotate(crtdl)).isInstanceOf(ValidationException.class)
                .hasMessageContaining("Unknown Attribute Condition.unknown in group test");

    }

    @Test
    void referenceWithoutLinkedGroups() {
        Crtdl crtdl = new Crtdl(node, new DataExtraction(List.of(patientGroup, new AttributeGroup("test", "https://www.medizininformatik-initiative.de/fhir/core/modul-labor/StructureDefinition/ObservationLab", List.of(new Attribute("Observation.subject", false)), List.of()))));

        assertThatThrownBy(() -> validatorService.validateAndAnnotate(crtdl)).isInstanceOf(ValidationException.class)
                .hasMessageContaining("Reference Attribute Observation.subject without linked Groups in group test");

    }

    @Test
    void BackBoneElementWithReference() throws ValidationException, ConsentFormatException {
        Crtdl crtdl = new Crtdl(node, new DataExtraction(List.of(patientGroup, new AttributeGroup("test", "https://www.medizininformatik-initiative.de/fhir/core/modul-fall/StructureDefinition/KontaktGesundheitseinrichtung", List.of(new Attribute("Encounter.diagnosis", false, List.of("patientGroupId"))), List.of()))));

        var validatedCrtdl = validatorService.validateAndAnnotate(crtdl);

        assertThat(validatedCrtdl).isNotNull();
        assertThat(validatedCrtdl.dataExtraction().attributeGroups().get(1).attributes()).isEqualTo(
                List.of(
                        new AnnotatedAttribute("Encounter.id", "Encounter.id", false),
                        new AnnotatedAttribute("Encounter.meta.profile", "Encounter.meta.profile", false),
                        new AnnotatedAttribute("Encounter.subject", "Encounter.subject", false, List.of("patientGroupId")),
                        new AnnotatedAttribute("Encounter.diagnosis", "Encounter.diagnosis", false, List.of("patientGroupId"))
                ));

    }

    @Test
    void validInput_withoutFilter() throws ValidationException, ConsentFormatException {
        Crtdl crtdl = new Crtdl(node, new DataExtraction(List.of(patientGroup)));

        var validatedCrtdl = validatorService.validateAndAnnotate(crtdl);

        assertThat(validatedCrtdl).isNotNull();
        assertThat(validatedCrtdl.dataExtraction().attributeGroups().getFirst().attributes()).isEqualTo(
                List.of(
                        new AnnotatedAttribute("Patient.id", "Patient.id", false),
                        new AnnotatedAttribute("Patient.meta.profile", "Patient.meta.profile", false)
                ));
    }

    @Test
    void validInput_withFilter() throws ValidationException, ConsentFormatException {
        Crtdl crtdl = new Crtdl(node, new DataExtraction(List.of(patientGroup,
                new AttributeGroup("test", "https://www.medizininformatik-initiative.de/fhir/core/modul-labor/StructureDefinition/ObservationLab",
                        List.of(), List.of(new Filter("token", "code", List.of(new Code("some-system", "some-code"))))))));

        var validatedCrtdl = validatorService.validateAndAnnotate(crtdl);

        assertThat(validatedCrtdl).isNotNull();
        assertThat(validatedCrtdl.dataExtraction().attributeGroups().get(1).attributes()).isEqualTo(
                List.of(
                        new AnnotatedAttribute("Observation.id", "Observation.id", false),
                        new AnnotatedAttribute("Observation.meta.profile", "Observation.meta.profile", false),
                        new AnnotatedAttribute("Observation.subject", "Observation.subject", false, List.of("patientGroupId"))
                ));
    }


}
