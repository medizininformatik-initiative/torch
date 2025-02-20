package de.medizininformatikinitiative.torch.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import de.medizininformatikinitiative.torch.exceptions.ValidationException;
import de.medizininformatikinitiative.torch.management.CompartmentManager;
import de.medizininformatikinitiative.torch.model.crtdl.Attribute;
import de.medizininformatikinitiative.torch.model.crtdl.AttributeGroup;
import de.medizininformatikinitiative.torch.model.crtdl.Crtdl;
import de.medizininformatikinitiative.torch.model.crtdl.DataExtraction;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttribute;
import de.medizininformatikinitiative.torch.setup.IntegrationTestSetup;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


class CrtdlValidatorServiceTest {
    private final IntegrationTestSetup itSetup = new IntegrationTestSetup();
    private final CrtdlValidatorService validatorService = new CrtdlValidatorService(itSetup.structureDefinitionHandler(), new CompartmentManager("compartmentdefinition-patient.json"));
    JsonNode node = JsonNodeFactory.instance.objectNode();

    CrtdlValidatorServiceTest() throws IOException {
    }

    @Test
    void unknownProfile() throws ValidationException {
        Crtdl crtdl = new Crtdl(node, new DataExtraction(List.of(new AttributeGroup("test", "unknown.test", List.of(), List.of()))));

        assertThatThrownBy(() -> {
            validatorService.validate(crtdl);
        }).isInstanceOf(ValidationException.class)
                .hasMessageContaining("Unknown Profile: unknown.test");

    }


    @Test
    void unknownAttribute() throws ValidationException {
        Crtdl crtdl = new Crtdl(node, new DataExtraction(List.of(new AttributeGroup("test", "https://www.medizininformatik-initiative.de/fhir/core/modul-labor/StructureDefinition/ObservationLab", List.of(new Attribute("Condition.unknown", false)), List.of()))));

        assertThatThrownBy(() -> {
            validatorService.validate(crtdl);
        }).isInstanceOf(ValidationException.class)
                .hasMessageContaining("Unknown Attribute Condition.unknown in https://www.medizininformatik-initiative.de/fhir/core/modul-labor/StructureDefinition/ObservationLab");

    }

    @Test
    void validInput() throws ValidationException {
        Crtdl crtdl = new Crtdl(node, new DataExtraction(List.of(new AttributeGroup("test", "https://www.medizininformatik-initiative.de/fhir/core/modul-labor/StructureDefinition/ObservationLab", List.of(), List.of()))));

        assertThat(validatorService.validate(crtdl).dataExtraction().attributeGroups().get(0).attributes()).isEqualTo(
                List.of(
                        new AnnotatedAttribute("Observation.id", "Observation.id", "Observation.id", true),
                        new AnnotatedAttribute("Observation.meta.profile", "Observation.meta.profile", "Observation.meta.profile", true),
                        new AnnotatedAttribute("Observation.subject", "Observation.subject", "Observation.subject", true)
                ));

    }


}