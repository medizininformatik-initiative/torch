package de.medizininformatikinitiative.torch.assertions;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.hl7.fhir.r4.model.CodeType;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.Reference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static de.medizininformatikinitiative.torch.TestUtils.nodeFromTreeString;
import static de.medizininformatikinitiative.torch.TestUtils.nodeFromValueString;
import static de.medizininformatikinitiative.torch.assertions.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ResourceAssertTest {
    private static final String CODE_1 = "code-123";
    private static final String CODE_2 = "code-456";
    private static final String SYSTEM = "system-123";
    private static final String DISPLAY = "display";

    @Nested
    class TestExtractElementsAt {
        Extension DATA_ABSENT_REASON_EXTENSION = new Extension().setUrl("http://hl7.org/fhir/StructureDefinition/data-absent-reason").setValue(new CodeType("masked"));
        String DATA_ABSENT_REASON_EXTENSION_STRING = """ 
                {
                    "extension": [
                        {
                            "url": "http://hl7.org/fhir/StructureDefinition/data-absent-reason",
                            "valueCode": "masked"
                        }
                    ]
                }  
                """;
        String DATA_ABSENT_REASON_STRING = """
                {
                    "url": "http://hl7.org/fhir/StructureDefinition/data-absent-reason",
                    "valueCode": "masked"
                }
                """;

        @Test
        void testExtractNothingFound() {
            Condition condition = new Condition();

            assertThat(condition).extractElementsAt("code.coding").isEmpty();
        }

        @Test
        void testExtractSingleCoding() {
            Condition condition = new Condition().setCode(new CodeableConcept(new Coding(SYSTEM, CODE_1, DISPLAY)));

            assertThat(condition).extractElementsAt("code.coding")
                    .satisfiesExactly(c -> assertThat(c.get("code")).isEqualTo(nodeFromValueString(CODE_1)));
            assertThat(condition).extractElementsAt("code.coding.code")
                    .satisfiesExactly(c -> assertThat(c).isEqualTo(nodeFromValueString(CODE_1)));
        }

        @Test
        void testExtractTwoCodings() {
            Condition condition = new Condition().setCode(new CodeableConcept().setCoding(List.of(
                    new Coding(SYSTEM, CODE_1, DISPLAY), new Coding(SYSTEM, CODE_2, DISPLAY))));

            assertThat(condition).extractElementsAt("code.coding")
                    .satisfiesExactlyInAnyOrder(
                            c -> assertThat(c.get("code")).isEqualTo(nodeFromValueString(CODE_1)),
                            c -> assertThat(c.get("code")).isEqualTo(nodeFromValueString(CODE_2)));
            assertThat(condition).extractElementsAt("code.coding.code")
                    .satisfiesExactlyInAnyOrder(
                            c -> assertThat(c).isEqualTo(nodeFromValueString(CODE_1)),
                            c -> assertThat(c).isEqualTo(nodeFromValueString(CODE_2))
                    );
        }

        @Test
        @DisplayName("Test to validate behavior when there is a node like an extension at a field that is usually a single primitive value")
        void testExtractAtPrimitiveField() throws JsonProcessingException {
            Condition condition = new Condition().setRecordedDateElement((DateTimeType) new DateTimeType().setExtension(List.of(DATA_ABSENT_REASON_EXTENSION)));

            assertThat(condition).extractElementsAt("recordedDate").containsExactly(nodeFromTreeString("\"\""));
            assertThat(condition).extractElementsAt("recordedDate.extension").containsExactly(nodeFromTreeString(DATA_ABSENT_REASON_STRING));
        }

        @Test
        void testExtract_foundExtensionOnly() throws JsonProcessingException {
            Condition condition = new Condition().setSubject((Reference) new Reference().setExtension(List.of(DATA_ABSENT_REASON_EXTENSION)));

            assertThat(condition).extractElementsAt("subject").containsExactly(nodeFromTreeString(DATA_ABSENT_REASON_EXTENSION_STRING));
            assertThat(condition).extractElementsAt("subject.reference").isEmpty();
        }
    }

    @Nested
    class ExtractTopElementNamesTest {

        @Test
        void testExtractEmptyResource() {
            Condition condition = new Condition();

            assertThat(condition).extractTopElementNames().containsExactly("resourceType");
        }

        @Test
        void testExtractCodeAndSubject() {
            Condition condition = new Condition().setCode(new CodeableConcept(new Coding(SYSTEM, CODE_1, DISPLAY))).setSubject(new Reference("some/ref"));

            assertThat(condition).extractTopElementNames()
                    .containsExactlyInAnyOrder("resourceType", "code", "subject");
        }
    }

    @Nested
    class TestExtractChildrenStringsAt {

        @Test
        void testExtractSimple() {
            var condition = new Condition().setCode(new CodeableConcept().setCoding(List.of(new Coding(SYSTEM, CODE_1, DISPLAY))));

            assertThat(condition).extractChildrenStringsAt("code.coding.code")
                    .hasSize(1)
                    .first().isEqualTo(CODE_1);
        }

        @Test
        void testExtract_multipleChildren() {
            var condition = new Condition().setCode(new CodeableConcept().setCoding(List.of(
                    new Coding(SYSTEM, CODE_1, DISPLAY),
                    new Coding(SYSTEM, CODE_2, DISPLAY))));

            assertThat(condition).extractChildrenStringsAt("code.coding.code")
                    .hasSize(2)
                    .containsExactlyInAnyOrder(CODE_1, CODE_2);
        }
    }

    @Nested
    class TestHasDataAbsentReasonAt {
        private static final Extension DATA_ABSENT_REASON_EXTENSION = new Extension()
                .setUrl("http://hl7.org/fhir/StructureDefinition/data-absent-reason")
                .setValue(new CodeType("masked"));
        private static final String DATA_ABSENT_REASON_STRING = """
                                                    {
                                                    "extension": [
                                                            {
                                                                "url": "http://hl7.org/fhir/StructureDefinition/data-absent-reason",
                                                                "valueCode": "masked"
                                                            }
                                                        ]
                                                    }
                                                    """;

        @Test
        void test_atNonPrimitiveField() throws JsonProcessingException {
            Condition condition = new Condition().setSubject((Reference) new Reference().setExtension(List.of(DATA_ABSENT_REASON_EXTENSION)));

            assertThat(condition).hasDataAbsentReasonAt("subject", "masked");
        }

        @Test
        void test_atNonPrimitiveField_noneFound() {
            Condition condition = new Condition().setSubject((Reference) new Reference().setExtension(List.of()));

            assertThatThrownBy(() -> assertThat(condition).hasDataAbsentReasonAt("subject", "masked"))
                    .hasMessage("Expected data absent reason at 'subject', but found: '{}'");
        }

        @Test
        void test_atPrimitiveField() throws JsonProcessingException {
            Condition condition = new Condition().setRecordedDateElement((DateTimeType) new DateTimeType().setExtension(List.of(DATA_ABSENT_REASON_EXTENSION)));

            assertThat(condition).hasDataAbsentReasonAt("recordedDate", "masked");
        }

        @Test
        void test_atPrimitiveField_noneFound() {
            Condition condition = new Condition().setRecordedDateElement((DateTimeType) new DateTimeType().setExtension(List.of()));

            assertThatThrownBy(() -> assertThat(condition).hasDataAbsentReasonAt("recordedDate", "masked"))
                    .hasMessage("Expected extension at 'recordedDate', but did not find one");
        }
    }
}
