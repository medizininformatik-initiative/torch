package de.medizininformatikinitiative.torch.management;

import ca.uhn.fhir.context.FhirContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttribute;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttributeGroup;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedCrtdl;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedDataExtraction;
import de.medizininformatikinitiative.torch.model.management.GroupsToProcess;
import de.medizininformatikinitiative.torch.service.FilterService;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ProcessedGroupFactoryTest {
    JsonNode node = JsonNodeFactory.instance.objectNode();
    static FilterService filterService = new FilterService(FhirContext.forR4(), "search-parameters.json");

    ProcessedGroupFactory processor = new ProcessedGroupFactory(new CompartmentManager("compartmentdefinition-patient.json"), filterService);
    AnnotatedAttributeGroup group = new AnnotatedAttributeGroup("Test", "12345", "Patient", "patient", List.of(new AnnotatedAttribute("Patient.test", "", false)), List.of(), null, false);
    AnnotatedAttributeGroup group2 = new AnnotatedAttributeGroup("Test2", "1234567", "Medication", "medication", List.of(new AnnotatedAttribute("Medication.test", "", false)), List.of(), null, false);
    AnnotatedAttributeGroup group3 = new AnnotatedAttributeGroup("Test3", "1235678", "Medication", "medication2", List.of(new AnnotatedAttribute("Medication.test", "", false)), List.of(), null, true);
    Map<String, AnnotatedAttributeGroup> resultMap = new HashMap<>();


    ProcessedGroupFactoryTest() throws IOException {
        resultMap.put("12345", group);
        resultMap.put("1234567", group2);
        resultMap.put("1235678", group3);
    }

    @Test
    void create() {
        AnnotatedCrtdl crtdl = new AnnotatedCrtdl(
                node, new AnnotatedDataExtraction(List.of(group, group2, group3)), Optional.empty());

        GroupsToProcess result = processor.create(crtdl);

        Map<String, AnnotatedAttributeGroup> expected = Map.of(
                "12345", group.rebuild(filterService),
                "1234567", group2.rebuild(filterService),
                "1235678", group3.rebuild(filterService)
        );

        assertThat(result.allGroups()).isEqualTo(expected);

        assertThat(result.directPatientCompartmentGroups())
                .containsExactly(group.rebuild(filterService));

        assertThat(result.directNoPatientGroups())
                .containsExactly(group2.rebuild(filterService));
    }


}
