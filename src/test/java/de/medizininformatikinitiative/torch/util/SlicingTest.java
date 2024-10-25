package de.medizininformatikinitiative.torch.util;

import ca.uhn.fhir.context.FhirContext;
import de.medizininformatikinitiative.torch.CdsStructureDefinitionHandler;
import de.medizininformatikinitiative.torch.setup.BaseTestSetup;
import org.hl7.fhir.r4.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SlicingTest {
    private static final Logger logger = LoggerFactory.getLogger(SlicingTest.class);

    private FhirContext context;
    private CdsStructureDefinitionHandler handler;
    private Slicing slicing;

    @BeforeEach
    void setUp() {
        context = FhirContext.forR4();
        handler = Mockito.mock(CdsStructureDefinitionHandler.class);
        slicing = new Slicing(handler, context);
    }

    @Test
    void testCheckSlicing_WithSlicedElement() {
        StructureDefinition structureDefinition = new StructureDefinition();
        StructureDefinition.StructureDefinitionSnapshotComponent snapshot = structureDefinition.getSnapshot();

        // Mock an ElementDefinition with slicing information
        ElementDefinition elementDefinition = new ElementDefinition();
        elementDefinition.setPath("Patient.contact");
        elementDefinition.getSlicing().addDiscriminator().setPath("relationship").setType(ElementDefinition.DiscriminatorType.VALUE);
        snapshot.addElement(elementDefinition);

        Base base = Mockito.mock(Base.class);
        ElementDefinition result = slicing.checkSlicing(base, "Patient.contact", structureDefinition);

        assertNotNull(result);
        assertEquals("Patient.contact", result.getPath());
    }

    @Test
    void testCheckSlicing_NoSlicingElement() {
        StructureDefinition structureDefinition = new StructureDefinition();
        StructureDefinition.StructureDefinitionSnapshotComponent snapshot = structureDefinition.getSnapshot();

        // ElementDefinition without slicing information
        ElementDefinition elementDefinition = new ElementDefinition();
        elementDefinition.setPath("Patient.contact");
        snapshot.addElement(elementDefinition);

        Base base = Mockito.mock(Base.class);
        ElementDefinition result = slicing.checkSlicing(base, "Patient.contact", structureDefinition);

        assertNull(result);
    }

    @Test
    void testGenerateConditionsForFHIRPath_WithValueDiscriminator() {
        StructureDefinition structureDefinition = new StructureDefinition();
        StructureDefinition.StructureDefinitionSnapshotComponent snapshot = structureDefinition.getSnapshot();

        // Sliced element with VALUE discriminator
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

        List<String> conditions = slicing.generateConditionsForFHIRPath("Patient.contact", snapshot);

        assertFalse(conditions.isEmpty());
        assertTrue(conditions.get(0).contains("Patient.contact.relationship"));
    }

    @Test
    void testGenerateConditionsForFHIRPath_NoDiscriminator() {
        StructureDefinition structureDefinition = new StructureDefinition();
        StructureDefinition.StructureDefinitionSnapshotComponent snapshot = structureDefinition.getSnapshot();

        // Element without any discriminator
        ElementDefinition elementDefinition = new ElementDefinition(new StringType("Patient.contact"));
        elementDefinition.setId("Patient.contact");
        snapshot.addElement(elementDefinition);

        List<String> conditions = slicing.generateConditionsForFHIRPath("Patient.contact", snapshot);

        assertTrue(conditions.isEmpty());
    }

    @Test
    void testGenerateConditionsForFHIRPath_WithUnsupportedDiscriminatorType() {
        StructureDefinition structureDefinition = new StructureDefinition();
        StructureDefinition.StructureDefinitionSnapshotComponent snapshot = structureDefinition.getSnapshot();

        // Mock unsupported discriminator type
        ElementDefinition elementDefinition = new ElementDefinition();
        elementDefinition.setPath("Patient.contact");
        elementDefinition.setId("Patient.contact");
        elementDefinition.getSlicing().addDiscriminator().setPath("unknown").setType(ElementDefinition.DiscriminatorType.PROFILE);
        snapshot.addElement(elementDefinition);


        List<String> conditions = slicing.generateConditionsForFHIRPath("Patient.contact", snapshot);

        assertFalse(conditions.isEmpty());
        assertTrue(conditions.get(0).contains("conformsTo"));
    }

    @Test
    void testCollectConditionsFromPattern_WithValidPattern() {
        StructureDefinition.StructureDefinitionSnapshotComponent snapshot = new StructureDefinition.StructureDefinitionSnapshotComponent();


        ElementDefinition elementDefinition = new ElementDefinition();
        elementDefinition.setId("Patient.contact");
        elementDefinition.setPath("Patient.contact");
        snapshot.addElement(elementDefinition);

        // Mock ElementDefinition with fixed pattern
        ElementDefinition subElementDefinition = new ElementDefinition();
        subElementDefinition.setId("Patient.contact.relationship");
        subElementDefinition.setPath("Patient.contact.relationship");
        Coding coding = new Coding("System", "code1", "Display");
        subElementDefinition.setPattern(new CodeableConcept().setText("Test").setCoding(Collections.singletonList(coding)));
        ElementDefinition.ElementDefinitionSlicingComponent slicingComponent = new ElementDefinition.ElementDefinitionSlicingComponent();
        subElementDefinition.setSlicing(slicingComponent);
        snapshot.addElement(subElementDefinition);


        List<String> conditions = slicing.collectConditionsfromPattern("Patient.contact", snapshot, "relationship");

        assertFalse(conditions.isEmpty());
        logger.info("{}", conditions.getFirst());
        assertTrue(conditions.get(0).contains("Patient.contact.relationship.coding.system"));
    }

    @Test
    void testTraverseValueRec_WithPrimitivePattern() {
        // Set up a CodeableConcept pattern with text and a coding
        CodeableConcept pattern = new CodeableConcept();
        pattern.setText("testValue");
        pattern.setCoding(Collections.singletonList(new Coding("System", "code1", "Display")));


        List<String> conditions = slicing.traverseValueRec("Patient.contact.relationship", pattern);

        // Ensure conditions are not empty and log each condition
        assertFalse(conditions.isEmpty());
        //conditions.forEach(condition -> logger.info("Condition: {}", condition));

        // Verify expected output conditions
        assertTrue(conditions.contains("Patient.contact.relationship.text='testValue'"));
        assertTrue(conditions.contains("Patient.contact.relationship.coding.system='System'"));
        assertTrue(conditions.contains("Patient.contact.relationship.coding.code='code1'"));
        assertTrue(conditions.contains("Patient.contact.relationship.coding.display='Display'"));
    }
}
