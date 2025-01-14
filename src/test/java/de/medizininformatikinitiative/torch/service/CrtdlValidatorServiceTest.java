package de.medizininformatikinitiative.torch.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import de.medizininformatikinitiative.torch.model.crtdl.Attribute;
import de.medizininformatikinitiative.torch.model.crtdl.AttributeGroup;
import de.medizininformatikinitiative.torch.model.crtdl.Crtdl;
import de.medizininformatikinitiative.torch.model.crtdl.DataExtraction;
import de.medizininformatikinitiative.torch.setup.IntegrationTestSetup;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;


class CrtdlValidatorServiceTest {
    private final IntegrationTestSetup itSetup = new IntegrationTestSetup();
    private final CrtdlValidatorService validatorService = new CrtdlValidatorService(itSetup.structureDefinitionHandler());
    JsonNode node = JsonNodeFactory.instance.objectNode();

    CrtdlValidatorServiceTest() throws IOException {
    }

    @Test
    void unknownProfile() throws IOException {
        Crtdl crtdl = new Crtdl(node, new DataExtraction(List.of(new AttributeGroup("unknown.test", List.of(), List.of()))));

        assertThatThrownBy(() -> {
            validatorService.validate(crtdl);
        }).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown Profile: unknown.test");
    }

    @Test
    void unknownAttribute() throws IOException {
        Crtdl crtdl = new Crtdl(node, new DataExtraction(List.of(new AttributeGroup("https://www.medizininformatik-initiative.de/fhir/core/modul-labor/StructureDefinition/ObservationLab", List.of(new Attribute("Condition.unknown", false)), List.of()))));

        assertThatThrownBy(() -> {
            validatorService.validate(crtdl);
        }).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown Attributes in https://www.medizininformatik-initiative.de/fhir/core/modul-labor/StructureDefinition/ObservationLab");
    }
    

}