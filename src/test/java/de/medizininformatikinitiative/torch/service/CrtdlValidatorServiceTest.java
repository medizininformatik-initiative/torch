package de.medizininformatikinitiative.torch.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import de.medizininformatikinitiative.torch.model.crtdl.AttributeGroup;
import de.medizininformatikinitiative.torch.model.crtdl.Crtdl;
import de.medizininformatikinitiative.torch.model.crtdl.DataExtraction;
import de.medizininformatikinitiative.torch.setup.IntegrationTestSetup;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


class CrtdlValidatorServiceTest {
    private final IntegrationTestSetup itSetup = new IntegrationTestSetup();


    @Test
    void invalidProfile() {
        CrtdlValidatorService validatorService = new CrtdlValidatorService(itSetup.getCds());
        JsonNode node = JsonNodeFactory.instance.objectNode();
        Crtdl crtdl = new Crtdl(node, new DataExtraction(List.of(new AttributeGroup("invalid", List.of(), List.of()))));

        assertThatThrownBy(() -> {
            validatorService.validate(crtdl);
        }).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown Profile: invalid");
    }

    @Test
    void validProfile() {
        CrtdlValidatorService validatorService = new CrtdlValidatorService(itSetup.getCds());
        JsonNode node = JsonNodeFactory.instance.objectNode();
        Crtdl crtdl = new Crtdl(node, new DataExtraction(List.of(new AttributeGroup("https://www.medizininformatik-initiative.de/fhir/core/modul-labor/StructureDefinition/ObservationLab", List.of(), List.of()))));

        Crtdl validatedCrtdl = validatorService.validate(crtdl);

        assertThat(validatedCrtdl.dataExtraction().attributeGroups()).hasSize(10);
    }

}