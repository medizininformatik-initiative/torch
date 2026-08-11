package de.medizininformatikinitiative.torch.util;

import ca.uhn.fhir.context.FhirContext;
import de.medizininformatikinitiative.torch.model.management.ReferenceResolutionContext;
import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.r4.model.Base;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Composition;
import org.hl7.fhir.r4.model.ElementDefinition;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.StringType;
import org.hl7.fhir.r4.model.StructureDefinition;
import org.hl7.fhir.r4.model.UriType;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class SlicingTest {


    @Test
    void testCheckSlicing_NoSlicingElement() {
        StructureDefinition structureDefinition = new StructureDefinition();
        StructureDefinition.StructureDefinitionSnapshotComponent snapshot = structureDefinition.getSnapshot();
        ElementDefinition elementDefinition = new ElementDefinition();
        elementDefinition.setPath("Patient.contact");
        snapshot.addElement(elementDefinition);
        Base base = Mockito.mock(Base.class);

        Optional<ElementDefinition> result = Slicing.resolveSlicing(base, "Patient.contact", CompiledStructureDefinition.fromStructureDefinition(structureDefinition), ReferenceResolutionContext.EMPTY);

        assertThat(result).isEmpty();
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

        List<String> result = Slicing.generateConditionsForFHIRPath("Patient.contact", CompiledStructureDefinition.fromStructureDefinition(structureDefinition));

        assertThat(result).containsExactly(
                "relationship.coding.system='System'",
                "relationship.coding.code='code1'",
                "relationship.coding.display='Display'",
                "relationship.text='Test'"
        );

    }

    @Test
    void testGenerateConditionsForFHIRPath_ExtensionSlicedByUrl_WithoutUrlChild() {
        StructureDefinition structureDefinition = new StructureDefinition();
        StructureDefinition.StructureDefinitionSnapshotComponent snapshot = structureDefinition.getSnapshot();
        ElementDefinition parentElement = new ElementDefinition();
        parentElement.setId("Condition.extension");
        parentElement.setPath("Condition.extension");
        parentElement.getSlicing().addDiscriminator().setPath("url").setType(ElementDefinition.DiscriminatorType.VALUE);
        snapshot.addElement(parentElement);
        ElementDefinition sliceElement = new ElementDefinition();
        sliceElement.setId("Condition.extension:Feststellungsdatum");
        sliceElement.setPath("Condition.extension");
        sliceElement.setSliceName("Feststellungsdatum");
        sliceElement.addType().setCode("Extension").addProfile("https://example.org/fhir/StructureDefinition/feststellungsdatum");
        snapshot.addElement(sliceElement);

        List<String> result = Slicing.generateConditionsForFHIRPath("Condition.extension:Feststellungsdatum",
                CompiledStructureDefinition.fromStructureDefinition(structureDefinition));

        assertThat(result).containsExactly("url='https://example.org/fhir/StructureDefinition/feststellungsdatum'");
    }

    @Test
    void testGenerateConditionsForFHIRPath_ExtensionSlicedByUrl_WithUrlChild() {
        StructureDefinition structureDefinition = new StructureDefinition();
        StructureDefinition.StructureDefinitionSnapshotComponent snapshot = structureDefinition.getSnapshot();
        ElementDefinition parentElement = new ElementDefinition();
        parentElement.setId("Condition.extension");
        parentElement.setPath("Condition.extension");
        parentElement.getSlicing().addDiscriminator().setPath("url").setType(ElementDefinition.DiscriminatorType.VALUE);
        snapshot.addElement(parentElement);
        ElementDefinition sliceElement = new ElementDefinition();
        sliceElement.setId("Condition.extension:Gesamtdosis");
        sliceElement.setPath("Condition.extension");
        sliceElement.setSliceName("Gesamtdosis");
        sliceElement.addType().setCode("Extension").addProfile("https://example.org/fhir/StructureDefinition/gesamtdosis");
        snapshot.addElement(sliceElement);
        ElementDefinition urlElement = new ElementDefinition();
        urlElement.setId("Condition.extension:Gesamtdosis.url");
        urlElement.setPath("Condition.extension.url");
        urlElement.setFixed(new UriType("https://example.org/fhir/StructureDefinition/gesamtdosis"));
        snapshot.addElement(urlElement);

        List<String> result = Slicing.generateConditionsForFHIRPath("Condition.extension:Gesamtdosis",
                CompiledStructureDefinition.fromStructureDefinition(structureDefinition));

        assertThat(result).containsExactly("url='https://example.org/fhir/StructureDefinition/gesamtdosis'");
    }

    @Test
    void testGenerateConditionsForFHIRPath_NoDiscriminator() {
        StructureDefinition structureDefinition = new StructureDefinition();
        StructureDefinition.StructureDefinitionSnapshotComponent snapshot = structureDefinition.getSnapshot();
        ElementDefinition elementDefinition = new ElementDefinition(new StringType("Patient.contact"));
        elementDefinition.setId("Patient.contact");
        snapshot.addElement(elementDefinition);

        List<String> result = Slicing.generateConditionsForFHIRPath("Patient.contact", CompiledStructureDefinition.fromStructureDefinition(structureDefinition));

        assertThat(result).containsExactly();
    }

    @Test
    void testGenerateConditionsForFHIRPath_ProfileDiscriminator_NoTypeInfo_ProducesNoCondition() {
        StructureDefinition structureDefinition = new StructureDefinition();
        StructureDefinition.StructureDefinitionSnapshotComponent snapshot = structureDefinition.getSnapshot();
        ElementDefinition elementDefinition = new ElementDefinition();
        elementDefinition.setPath("Patient.contact");
        elementDefinition.setId("Patient.contact");
        elementDefinition.getSlicing().addDiscriminator().setPath("unknown").setType(ElementDefinition.DiscriminatorType.PROFILE);
        snapshot.addElement(elementDefinition);


        List<String> result = Slicing.generateConditionsForFHIRPath("Patient.contact", CompiledStructureDefinition.fromStructureDefinition(structureDefinition));

        assertThat(result).isEmpty();
    }

    /**
     * Mirrors {@code Observation.derivedFrom:variant} in the real MII molecular-genetics ontology: a profile
     * discriminator with a reference-resolving path, where the target profile is declared on the slice's own
     * {@code Reference} type rather than resolvable via the path itself.
     */
    @Test
    void testGenerateConditionsForFHIRPath_ProfileDiscriminator_SubstitutesTargetProfile() {
        StructureDefinition structureDefinition = new StructureDefinition();
        StructureDefinition.StructureDefinitionSnapshotComponent snapshot = structureDefinition.getSnapshot();
        ElementDefinition parentElement = new ElementDefinition();
        parentElement.setId("Observation.derivedFrom");
        parentElement.setPath("Observation.derivedFrom");
        parentElement.getSlicing().addDiscriminator().setPath("$this.resolve()").setType(ElementDefinition.DiscriminatorType.PROFILE);
        snapshot.addElement(parentElement);
        ElementDefinition sliceElement = new ElementDefinition();
        sliceElement.setId("Observation.derivedFrom:variant");
        sliceElement.setPath("Observation.derivedFrom");
        sliceElement.setSliceName("variant");
        sliceElement.addType().setCode("Reference").addTargetProfile("https://www.medizininformatik-initiative.de/fhir/ext/modul-molgen/StructureDefinition/variante");
        snapshot.addElement(sliceElement);

        List<String> result = Slicing.generateConditionsForFHIRPath("Observation.derivedFrom:variant",
                CompiledStructureDefinition.fromStructureDefinition(structureDefinition));

        assertThat(result).containsExactly("$this.resolve().conformsTo('https://www.medizininformatik-initiative.de/fhir/ext/modul-molgen/StructureDefinition/variante')");
    }

    /**
     * Mirrors {@code Bundle.entry:Composition} in the real ISiKBerichtBundle ontology: a type discriminator whose
     * path names a real child element ({@code resource}), whose own type ({@code Composition}) - not the sliced
     * element's own type ({@code BackboneElement}) - is what the generated condition must check.
     */
    @Test
    void testGenerateConditionsForFHIRPath_TypeDiscriminator_ResolvesChildElementType() {
        StructureDefinition structureDefinition = new StructureDefinition();
        StructureDefinition.StructureDefinitionSnapshotComponent snapshot = structureDefinition.getSnapshot();
        ElementDefinition parentElement = new ElementDefinition();
        parentElement.setId("Bundle.entry");
        parentElement.setPath("Bundle.entry");
        parentElement.getSlicing().addDiscriminator().setPath("resource").setType(ElementDefinition.DiscriminatorType.TYPE);
        snapshot.addElement(parentElement);
        ElementDefinition sliceElement = new ElementDefinition();
        sliceElement.setId("Bundle.entry:Composition");
        sliceElement.setPath("Bundle.entry");
        sliceElement.setSliceName("Composition");
        sliceElement.addType().setCode("BackboneElement");
        snapshot.addElement(sliceElement);
        ElementDefinition resourceChild = new ElementDefinition();
        resourceChild.setId("Bundle.entry:Composition.resource");
        resourceChild.setPath("Bundle.entry.resource");
        resourceChild.addType().setCode("Composition");
        snapshot.addElement(resourceChild);

        List<String> result = Slicing.generateConditionsForFHIRPath("Bundle.entry:Composition",
                CompiledStructureDefinition.fromStructureDefinition(structureDefinition));

        assertThat(result).containsExactly("resource.ofType(Composition)");
    }

    /**
     * A type discriminator with path {@code $this} constrains the sliced element's own type directly (e.g. a
     * choice-type element like {@code value[x]} sliced by its runtime type), so no child element lookup is
     * needed.
     */
    @Test
    void testGenerateConditionsForFHIRPath_TypeDiscriminator_ThisPath_UsesSlicedElementOwnType() {
        StructureDefinition structureDefinition = new StructureDefinition();
        StructureDefinition.StructureDefinitionSnapshotComponent snapshot = structureDefinition.getSnapshot();
        ElementDefinition parentElement = new ElementDefinition();
        parentElement.setId("Observation.value[x]");
        parentElement.setPath("Observation.value[x]");
        parentElement.getSlicing().addDiscriminator().setPath("$this").setType(ElementDefinition.DiscriminatorType.TYPE);
        snapshot.addElement(parentElement);
        ElementDefinition sliceElement = new ElementDefinition();
        sliceElement.setId("Observation.value[x]:valueQuantity");
        sliceElement.setPath("Observation.value[x]");
        sliceElement.setSliceName("valueQuantity");
        sliceElement.addType().setCode("Quantity");
        snapshot.addElement(sliceElement);

        List<String> result = Slicing.generateConditionsForFHIRPath("Observation.value[x]:valueQuantity",
                CompiledStructureDefinition.fromStructureDefinition(structureDefinition));

        assertThat(result).containsExactly("$this.ofType(Quantity)");
    }

    /**
     * A type discriminator whose path resolves to a real child element that itself has no type info produces no
     * condition, the same as an unresolvable (reference-resolving) path.
     */
    @Test
    void testGenerateConditionsForFHIRPath_TypeDiscriminator_ChildFound_NoTypeInfo_ProducesNoCondition() {
        StructureDefinition structureDefinition = new StructureDefinition();
        StructureDefinition.StructureDefinitionSnapshotComponent snapshot = structureDefinition.getSnapshot();
        ElementDefinition parentElement = new ElementDefinition();
        parentElement.setId("Bundle.entry");
        parentElement.setPath("Bundle.entry");
        parentElement.getSlicing().addDiscriminator().setPath("resource").setType(ElementDefinition.DiscriminatorType.TYPE);
        snapshot.addElement(parentElement);
        ElementDefinition sliceElement = new ElementDefinition();
        sliceElement.setId("Bundle.entry:Untyped");
        sliceElement.setPath("Bundle.entry");
        sliceElement.setSliceName("Untyped");
        snapshot.addElement(sliceElement);
        ElementDefinition resourceChild = new ElementDefinition();
        resourceChild.setId("Bundle.entry:Untyped.resource");
        resourceChild.setPath("Bundle.entry.resource");
        snapshot.addElement(resourceChild);

        List<String> result = Slicing.generateConditionsForFHIRPath("Bundle.entry:Untyped",
                CompiledStructureDefinition.fromStructureDefinition(structureDefinition));

        assertThat(result).isEmpty();
    }

    /**
     * Confirms the fix's core motivation: the generated {@code .where(...)} clause for a type discriminator is
     * valid FHIRPath that evaluates without throwing (unlike the unsubstituted {@code {type}} placeholder), and
     * actually filters by the resolved child type.
     */
    @Test
    void testHandleSlicingForFhirPath_TypeDiscriminator_EvaluatesWithoutThrowing() throws FHIRException {
        StructureDefinition structureDefinition = new StructureDefinition();
        StructureDefinition.StructureDefinitionSnapshotComponent snapshot = structureDefinition.getSnapshot();
        ElementDefinition parentElement = new ElementDefinition();
        parentElement.setId("Bundle.entry");
        parentElement.setPath("Bundle.entry");
        parentElement.getSlicing().addDiscriminator().setPath("resource").setType(ElementDefinition.DiscriminatorType.TYPE);
        snapshot.addElement(parentElement);
        ElementDefinition sliceElement = new ElementDefinition();
        sliceElement.setId("Bundle.entry:Composition");
        sliceElement.setPath("Bundle.entry");
        sliceElement.setSliceName("Composition");
        sliceElement.addType().setCode("BackboneElement");
        snapshot.addElement(sliceElement);
        ElementDefinition resourceChild = new ElementDefinition();
        resourceChild.setId("Bundle.entry:Composition.resource");
        resourceChild.setPath("Bundle.entry.resource");
        resourceChild.addType().setCode("Composition");
        snapshot.addElement(resourceChild);

        String[] fhirPath = FhirPathBuilder.handleSlicingForFhirPath("Bundle.entry:Composition",
                CompiledStructureDefinition.fromStructureDefinition(structureDefinition));

        Bundle bundle = new Bundle();
        bundle.addEntry().setResource(new Composition());
        bundle.addEntry().setResource(new Patient());

        List<Base> matches = FhirContext.forR4().newFhirPath().evaluate(bundle, fhirPath[0], Base.class);

        assertThat(matches).hasSize(1);
    }

    /**
     * A type discriminator whose path involves reference resolution (e.g. {@code $this.resolve()}) has no child
     * element to look up statically - Torch cannot yet resolve references at this point (#1173) - so no condition
     * should be generated rather than a fabricated, always-false one.
     */
    @Test
    void testGenerateConditionsForFHIRPath_TypeDiscriminator_UnresolvablePath_ProducesNoCondition() {
        StructureDefinition structureDefinition = new StructureDefinition();
        StructureDefinition.StructureDefinitionSnapshotComponent snapshot = structureDefinition.getSnapshot();
        ElementDefinition parentElement = new ElementDefinition();
        parentElement.setId("Observation.derivedFrom");
        parentElement.setPath("Observation.derivedFrom");
        parentElement.getSlicing().addDiscriminator().setPath("$this.resolve()").setType(ElementDefinition.DiscriminatorType.TYPE);
        snapshot.addElement(parentElement);
        ElementDefinition sliceElement = new ElementDefinition();
        sliceElement.setId("Observation.derivedFrom:attachedImage");
        sliceElement.setPath("Observation.derivedFrom");
        sliceElement.setSliceName("attachedImage");
        sliceElement.addType().setCode("Reference");
        snapshot.addElement(sliceElement);

        List<String> result = Slicing.generateConditionsForFHIRPath("Observation.derivedFrom:attachedImage",
                CompiledStructureDefinition.fromStructureDefinition(structureDefinition));

        assertThat(result).isEmpty();
    }

    @Test
    void testCollectConditionsFromPattern_WithValidPattern() {
        StructureDefinition structureDefinition = new StructureDefinition();
        StructureDefinition.StructureDefinitionSnapshotComponent snapshot = structureDefinition.getSnapshot();
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


        List<String> result = Slicing.collectConditionsfromPattern("Patient.contact", CompiledStructureDefinition.fromStructureDefinition(structureDefinition), "relationship");

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

        List<String> result = Slicing.traverseValueRec("relationship", pattern);

        assertThat(result).containsOnly(
                "relationship.coding.system='System'",
                "relationship.coding.code='code1'",
                "relationship.coding.display='Display'");
    }
}
