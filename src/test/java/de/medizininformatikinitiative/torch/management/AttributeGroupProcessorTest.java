package de.medizininformatikinitiative.torch.management;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import de.medizininformatikinitiative.torch.model.ProcessedGroups;
import de.medizininformatikinitiative.torch.model.crtdl.Attribute;
import de.medizininformatikinitiative.torch.model.crtdl.AttributeGroup;
import de.medizininformatikinitiative.torch.model.crtdl.Crtdl;
import de.medizininformatikinitiative.torch.model.crtdl.DataExtraction;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AttributeGroupProcessorTest {
    JsonNode node = JsonNodeFactory.instance.objectNode();

    AttributeGroupProcessor processor = new AttributeGroupProcessor(new CompartmentManager("compartmentdefinition-patient.json"));
    AttributeGroup group = new AttributeGroup("Test", "patient", List.of(new Attribute("Patient.test", false)), List.of(), false);
    AttributeGroup group2 = new AttributeGroup("Test2", "medication", List.of(new Attribute("Medication.test", false)), List.of(), false);
    AttributeGroup group3 = new AttributeGroup("Test3", "medication2", List.of(new Attribute("Medication.test", false)), List.of(), true);
    Map<String, AttributeGroup> resultMap = new HashMap<>();


    AttributeGroupProcessorTest() throws IOException {
        resultMap.put("patient", group);
        resultMap.put("medication", group2);
        resultMap.put("medication2", group3);
    }

    @Test
    void process() {
        Crtdl crtdl = new Crtdl(node, new DataExtraction(List.of(group, group2, group3)));
        ProcessedGroups result = processor.process(crtdl);

        assertThat(result.groups()).isEqualTo(resultMap);
        assertThat(result.firstPass()).containsExactly(group);
        assertThat(result.secondPass()).containsExactly(group2);
    }


}