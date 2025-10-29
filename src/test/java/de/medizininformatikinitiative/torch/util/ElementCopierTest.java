package de.medizininformatikinitiative.torch.util;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import de.medizininformatikinitiative.torch.model.management.CopyTreeNode;
import org.hl7.fhir.r4.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

class ElementCopierTest {

    private ElementCopier copyService;
    private IParser jsonParser;

    @BeforeEach
    void setup() {
        FhirContext context = FhirContext.forR4();
        copyService = new ElementCopier(context);
        jsonParser = context.newJsonParser();
    }

    @Test
    void nestedList() throws ReflectiveOperationException {
        // --- Given ---
        Patient src = new Patient();
        Identifier id1 = new Identifier()
                .setSystem("http://hospital.org")
                .setValue("12345")
                .setType(new CodeableConcept().addCoding(new Coding().setCode("official")));
        Identifier id2 = new Identifier()
                .setSystem("http://temp.org")
                .setValue("TEMP-999")
                .setType(new CodeableConcept().addCoding(new Coding().setCode("secondary")));
        src.addIdentifier(id1);
        src.addIdentifier(id2);

        Patient tgt = new Patient();

        // --- Copy Tree ---
        CopyTreeNode copyTree = new CopyTreeNode("Patient", "", List.of(
                new CopyTreeNode("identifier", "", List.of(
                        new CopyTreeNode("system"), // copy system for all identifiers
                        new CopyTreeNode("value"),  // copy value for all identifiers
                        new CopyTreeNode("type", "", List.of(
                                new CopyTreeNode("coding", ".where(code='official')", List.of(
                                        new CopyTreeNode("code")
                                ))
                        ))
                ))
        ));

        // --- When ---
        copyService.copy(src, tgt, copyTree);

        // --- Then ---
        Patient expected = new Patient();
        expected.addIdentifier(new Identifier()
                .setSystem("http://hospital.org")
                .setValue("12345")
                .setType(new CodeableConcept().addCoding(new Coding().setCode("official"))));
        expected.addIdentifier(new Identifier()
                .setSystem("http://temp.org")
                .setValue("TEMP-999")
                .setType(new CodeableConcept())); // coding filtered out

        assertThat(tgt.equalsDeep(expected)).isTrue();
    }

    @Test
    void choiceSlicingSuccess() throws ReflectiveOperationException {
        // --- Given ---
        Observation src = new Observation();
        src.setValue(new CodeableConcept().addCoding(new Coding().setCode("ABC"))); // valueCodeableConcept

        Observation tgt = new Observation();

        // --- Copy Tree ---
        CopyTreeNode copyTree = new CopyTreeNode("Observation", "", List.of(
                new CopyTreeNode("value", ".ofType(CodeableConcept)", List.of(
                        new CopyTreeNode("coding", "", List.of(
                                new CopyTreeNode("code")
                        ))
                ))
        ));

        // --- When ---
        copyService.copy(src, tgt, copyTree);

        // --- Then ---
        Observation expected = new Observation();
        expected.setValue(new CodeableConcept().addCoding(new Coding().setCode("ABC")));

        assertThat(tgt.equalsDeep(expected)).isTrue(); // success: CodeableConcept copied
    }

    @Test
    void choiceSlicingFails() throws ReflectiveOperationException {
        // --- Given ---
        Observation src = new Observation();
        src.setValue(new org.hl7.fhir.r4.model.Quantity().setValue(42)); // valueQuantity

        Observation tgt = new Observation();

        // --- Copy Tree ---
        CopyTreeNode copyTree = new CopyTreeNode("Observation", "", List.of(
                new CopyTreeNode("value", ".ofType(CodeableConcept)", List.of(
                        new CopyTreeNode("coding", "", List.of(
                                new CopyTreeNode("code")
                        ))
                ))
        ));

        // --- When ---
        copyService.copy(src, tgt, copyTree);

        // --- Then ---
        Observation expected = new Observation(); // nothing should be copied

        assertThat(tgt.equalsDeep(expected)).isTrue(); // fail case: Quantity ignored, target empty
    }

    @Test
    void nestedElementsIncludingConditionalBranches() throws ReflectiveOperationException {
        // --- Given ---
        // Patient with two identifiers: one official, one secondary
        Patient src = new Patient();
        Identifier official = new Identifier()
                .setSystem("http://hospital.org")
                .setValue("12345")
                .setType(new CodeableConcept().addCoding(new Coding().setCode("official")));
        Identifier secondary = new Identifier()
                .setSystem("http://temp.org")
                .setValue("TEMP-999")
                .setType(new CodeableConcept().addCoding(new Coding().setCode("secondary")));
        src.addIdentifier(official);
        src.addIdentifier(secondary);


        Patient tgt = new Patient();

        // --- Copy Tree ---
        CopyTreeNode copyTree = new CopyTreeNode("Patient", "", List.of(
                new CopyTreeNode("identifier", "", List.of(
                        new CopyTreeNode("system")
                )),
                new CopyTreeNode("identifier", ".where(type.coding.code='official')", List.of(
                        new CopyTreeNode("value")
                ))
        ));

        // --- When ---
        copyService.copy(src, tgt, copyTree);

        System.out.println(jsonParser.setPrettyPrint(true).encodeToString(tgt));

        // --- Then ---
        assertThat(tgt.getIdentifier()).hasSize(2);

        List<Identifier> expected = List.of(
                new Identifier().setSystem("http://hospital.org").setValue("12345"),
                new Identifier().setSystem("http://temp.org")
        );

        // Use HAPI equalsDeep for comparison
        assertThat(tgt.getIdentifier()).hasSize(2);
        for (Identifier expectedId : expected) {
            assertThat(tgt.getIdentifier().stream().anyMatch(i -> i.equalsDeep(expectedId))).isTrue();
        }
    }

    @Test
    void copyWithoutConditions() throws ReflectiveOperationException {
        // --- Given ---
        // Patient with two identifiers: one official, one secondary
        Patient src = new Patient();
        Identifier official = new Identifier()
                .setSystem("http://hospital.org")
                .setValue("12345")
                .setType(new CodeableConcept().addCoding(new Coding().setCode("official")));
        Identifier secondary = new Identifier()
                .setSystem("http://temp.org")
                .setValue("TEMP-999")
                .setType(new CodeableConcept().addCoding(new Coding().setCode("secondary")));
        src.addIdentifier(official);
        src.addIdentifier(secondary);

        Patient tgt = new Patient();

        // --- Copy Tree ---
        CopyTreeNode copyTree = new CopyTreeNode("Patient", "", List.of(
                new CopyTreeNode("identifier", "", List.of(
                        new CopyTreeNode("value")
                ))));

        // --- When ---
        copyService.copy(src, tgt, copyTree);
        System.out.println(jsonParser.setPrettyPrint(true).encodeToString(tgt));

        // --- Then ---
        assertThat(tgt.getIdentifier()).hasSize(2);

        List<Identifier> expected = List.of(
                new Identifier().setValue("12345"),
                new Identifier().setValue("TEMP-999")
        );

        // Use HAPI equalsDeep for comparison
        assertThat(tgt.getIdentifier()).hasSize(2);
        for (Identifier expectedId : expected) {
            assertThat(tgt.getIdentifier().stream().anyMatch(i -> i.equalsDeep(expectedId))).isTrue();
        }
    }

    @Nested
    class CreateEmptyElementTest {

        @Test
        void shouldThrowRuntimeExceptionWhenNoDefaultConstructorExists() {
            assertThatThrownBy(() -> copyService.createEmptyElement(NoDefaultConstructorResource.class))
                    .isInstanceOf(ReflectiveOperationException.class)
                    .hasMessageContaining("Cannot create empty instance of")
                    .hasCauseInstanceOf(ReflectiveOperationException.class);
        }

        // Minimal subclass with private constructor (to test setAccessible)
        static class NoDefaultConstructorResource extends Observation {
            String param;

            // arg  constructor
            public NoDefaultConstructorResource(String param) {
                this.param = param;
            }

        }
    }


}
