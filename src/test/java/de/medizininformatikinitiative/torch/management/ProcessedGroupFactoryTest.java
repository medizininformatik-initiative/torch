package de.medizininformatikinitiative.torch.management;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import de.medizininformatikinitiative.torch.model.GroupsToProcess;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttribute;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttributeGroup;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedCrtdl;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedDataExtraction;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ProcessedGroupFactoryTest {
    JsonNode node = JsonNodeFactory.instance.objectNode();

    ProcessedGroupFactory processor = new ProcessedGroupFactory(new CompartmentManager("compartmentdefinition-patient.json"));
    AnnotatedAttributeGroup group = new AnnotatedAttributeGroup("Test", "12345", "patient", List.of(new AnnotatedAttribute("Patient.test", "", "", false)), List.of(), false);
    AnnotatedAttributeGroup group2 = new AnnotatedAttributeGroup("Test2", "1234567", "medication", List.of(new AnnotatedAttribute("Medication.test", "", "", false)), List.of(), false);
    AnnotatedAttributeGroup group3 = new AnnotatedAttributeGroup("Test3", "1235678", "medication2", List.of(new AnnotatedAttribute("Medication.test", "", "", false)), List.of(), true);
    Map<String, AnnotatedAttributeGroup> resultMap = new HashMap<>();


    ProcessedGroupFactoryTest() throws IOException {
        resultMap.put("12345", group);
        resultMap.put("1234567", group2);
        resultMap.put("1235678", group3);
    }

    @Test
    void create() {
        AnnotatedCrtdl crtdl = new AnnotatedCrtdl(node, new AnnotatedDataExtraction(List.of(group, group2, group3)));

        GroupsToProcess result = processor.create(crtdl);

        assertThat(result.allGroups()).isEqualTo(resultMap);
        assertThat(result.directPatientCompartmentGroups()).containsExactly(group);
        assertThat(result.directNoPatientGroups()).containsExactly(group2);
    }


}