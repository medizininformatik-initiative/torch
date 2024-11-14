package de.medizininformatikinitiative.torch.util;

import ca.uhn.fhir.context.FhirContext;
import org.hl7.fhir.r4.model.Base;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.ElementDefinition;
import org.hl7.fhir.r4.model.StringType;
import org.hl7.fhir.r4.model.StructureDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SlicingTest {
    private Slicing slicing;

    @BeforeEach
    void setUp() {
        FhirContext context = FhirContext.forR4();
        slicing = new Slicing(context);
    }


    @Test
    void testCheckSlicing_NoSlicingElement() {
        StructureDefinition structureDefinition = new StructureDefinition();
        StructureDefinition.StructureDefinitionSnapshotComponent snapshot = structureDefinition.getSnapshot();
        ElementDefinition elementDefinition = new ElementDefinition();
        elementDefinition.setPath("Patient.contact");
        snapshot.addElement(elementDefinition);
        Base base = Mockito.mock(Base.class);

        ElementDefinition result = slicing.checkSlicing(base, "Patient.contact", structureDefinition);

        assertThat(result).isNull();
    }

    @Test
    void testGenerateConditionsForFHIRPath_WithValueDiscriminator() {
        StructureDefinition structureDefinition = new StructureDefinition();
        StructureDefinition.StructureDefinitionSnapshotComponent snapshot = structureDefinition.getSnapshot();
        ElementDefinition parentElement = new ElementDefinition();
        parentElement.setPath("Patient.contact");
        parentElement.setId("Patient.contact");
        parentElement.getSlicing().addDiscriminator().setPath("relationship").setType(ElementDefinition.DiscriminatorType.VALUE);
        snapshot.addElement(parentElement);
        ElementDefinition subElementDefinition = new ElementDefinition();
        subElementDefinition.setId("Patient.contact.relationship");
        subElementDefinition.setPath("Patient.contact.relationship");
        Coding coding = new Coding("System", "code1", "Display");
        subElementDefinition.setPattern(new CodeableConcept().setText("Test").setCoding(Collections.singletonList(coding)));
        ElementDefinition.ElementDefinitionSlicingComponent slicingComponent = new ElementDefinition.ElementDefinitionSlicingComponent();
        subElementDefinition.setSlicing(slicingComponent);
        snapshot.addElement(subElementDefinition);

        List<String> result = slicing.generateConditionsForFHIRPath("Patient.contact", snapshot);

        assertThat(result).containsExactly(
                "relationship.coding.system='System'",
                "relationship.coding.code='code1'",
                "relationship.coding.display='Display'",
                "relationship.text='Test'"
        );

    }

    @Test
    void testGenerateConditionsForFHIRPath_NoDiscriminator() {
        StructureDefinition structureDefinition = new StructureDefinition();
        StructureDefinition.StructureDefinitionSnapshotComponent snapshot = structureDefinition.getSnapshot();
        ElementDefinition elementDefinition = new ElementDefinition(new StringType("Patient.contact"));
        elementDefinition.setId("Patient.contact");
        snapshot.addElement(elementDefinition);

        List<String> result = slicing.generateConditionsForFHIRPath("Patient.contact", snapshot);

        assertThat(result).containsExactly();
    }

    @Test
    void testGenerateConditionsForFHIRPath_WithUnsupportedDiscriminatorType() {
        StructureDefinition structureDefinition = new StructureDefinition();
        StructureDefinition.StructureDefinitionSnapshotComponent snapshot = structureDefinition.getSnapshot();
        ElementDefinition elementDefinition = new ElementDefinition();
        elementDefinition.setPath("Patient.contact");
        elementDefinition.setId("Patient.contact");
        elementDefinition.getSlicing().addDiscriminator().setPath("unknown").setType(ElementDefinition.DiscriminatorType.PROFILE);
        snapshot.addElement(elementDefinition);


        List<String> result = slicing.generateConditionsForFHIRPath("Patient.contact", snapshot);

        assertThat(result).containsExactly("unknown.conformsTo({profile})");
    }

    @Test
    void testCollectConditionsFromPattern_WithValidPattern() {
        StructureDefinition.StructureDefinitionSnapshotComponent snapshot = new StructureDefinition.StructureDefinitionSnapshotComponent();
        ElementDefinition elementDefinition = new ElementDefinition();
        elementDefinition.setId("Patient.contact");
        elementDefinition.setPath("Patient.contact");
        snapshot.addElement(elementDefinition);
        ElementDefinition subElementDefinition = new ElementDefinition();
        subElementDefinition.setId("Patient.contact.relationship");
        subElementDefinition.setPath("Patient.contact.relationship");
        Coding coding = new Coding("System", "code1", "Display");
        subElementDefinition.setPattern(new CodeableConcept().setText("Test").setCoding(Collections.singletonList(coding)));
        ElementDefinition.ElementDefinitionSlicingComponent slicingComponent = new ElementDefinition.ElementDefinitionSlicingComponent();
        subElementDefinition.setSlicing(slicingComponent);
        snapshot.addElement(subElementDefinition);


        List<String> result = slicing.collectConditionsfromPattern("Patient.contact", snapshot, "relationship");

        assertThat(result).containsExactly(
                "relationship.coding.system='System'",
                "relationship.coding.code='code1'",
                "relationship.coding.display='Display'",
                "relationship.text='Test'"
        );
    }

    @Test
    void testTraverseValueRec_WithPrimitivePattern() {
        CodeableConcept pattern = new CodeableConcept();
        pattern.setCoding(Collections.singletonList(new Coding("System", "code1", "Display")));

        List<String> result = slicing.traverseValueRec("relationship", pattern);

        assertThat(result).containsOnly(
                "relationship.coding.system='System'",
                "relationship.coding.code='code1'",
                "relationship.coding.display='Display'");
    }
}
