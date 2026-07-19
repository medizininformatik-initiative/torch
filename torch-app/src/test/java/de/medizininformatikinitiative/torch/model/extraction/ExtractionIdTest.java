package de.medizininformatikinitiative.torch.model.extraction;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExtractionIdTest {

    private final ObjectMapper om = new ObjectMapper();

    @Nested
    class FromRelativeUrl {

        @Test
        void throwsWhenSlashIsFirstCharacter() {
            assertThatThrownBy(() -> ExtractionId.fromRelativeUrl("/123"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("leading slash not allowed");
        }

        @Test
        void throwsWhenSlashIsLastCharacter() {
            assertThatThrownBy(() -> ExtractionId.fromRelativeUrl("Patient/"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Invalid relative reference: expected 'ResourceType/id' but got 'Patient/'");
        }


        @Test
        void throwsOnAbsoluteUrl() {
            assertThatThrownBy(() -> ExtractionId.fromRelativeUrl("http://example.org/fhir/Patient/123"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Absolute references are not supported: 'http://example.org/fhir/Patient/123'");
        }

        @Test
        void throwsOnUrn() {
            assertThatThrownBy(() -> ExtractionId.fromRelativeUrl("urn:uuid:1234"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("URN references are not supported: 'urn:uuid:1234'");
        }

        @Test
        void throwsOnLeadingSlash() {
            assertThatThrownBy(() -> ExtractionId.fromRelativeUrl("/Patient/123"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Invalid relative reference (leading slash not allowed): '/Patient/123'");
        }


        @Test
        void parsesValid() {
            ExtractionId id = ExtractionId.fromRelativeUrl("Patient/123");

            assertThat(id.resourceType()).isEqualTo("Patient");
            assertThat(id.id()).isEqualTo("123");
            assertThat(id.toRelativeUrl()).isEqualTo("Patient/123");
            assertThat(id).hasToString("Patient/123");
        }

        @Test
        void splitsOnlyOnceKeepRestInId() {
            assertThatThrownBy(() -> ExtractionId.fromRelativeUrl("DocumentReference/abc/def"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Invalid relative reference: too many path segments in 'DocumentReference/abc/def'");
        }

        @Test
        void throwsOnNullInput() {
            assertThatThrownBy(() -> ExtractionId.fromRelativeUrl(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("relativeUrl must not be null");
        }

        @Test
        void throwsWhenNoSlash() {
            assertThatThrownBy(() -> ExtractionId.fromRelativeUrl("Patient"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid relative reference");
        }

        @Test
        void throwsWhenBlankType() {
            assertThatThrownBy(() -> ExtractionId.fromRelativeUrl(" /123"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid relative reference");
        }

        @Test
        void throwsWhenBlankId() {
            assertThatThrownBy(() -> ExtractionId.fromRelativeUrl("Patient/  "))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid relative reference");
        }


    }

    @Nested
    class Constructor {
        @Test
        void throwsOnNullResourceType() {
            assertThatThrownBy(() -> new ExtractionId(null, "1"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("resourceType must not be null");
        }

        @Test
        void throwsOnNullId() {
            assertThatThrownBy(() -> new ExtractionId("Patient", null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("id must not be null");
        }

        @Test
        void throwsOnBlankResourceType() {
            assertThatThrownBy(() -> new ExtractionId("  ", "1"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("resourceType must not be blank");
        }


        @Test
        void throwsOnBlankId() {
            assertThatThrownBy(() -> new ExtractionId("Patient", " \t"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("id must not be blank");
        }
    }

    @Nested
    class Of {
        @Test
        void throwsOnNullVarargsArray() {
            assertThatThrownBy(() -> ExtractionId.of((String[]) null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("relativeUrls must not be null");
        }

        @Test
        void throwsOnNullElement() {
            assertThatThrownBy(() -> ExtractionId.of("Patient/1", null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("relativeUrl must not be null");
        }

        @Test
        void preservesInsertionOrder_andRemovesDuplicates() {
            Set<ExtractionId> ids = ExtractionId.of("Patient/1", "Encounter/2", "Patient/1");

            assertThat(ids).containsExactly(
                    new ExtractionId("Patient", "1"),
                    new ExtractionId("Encounter", "2")
            );
        }

        @Test
        void propagatesInvalidRelativeUrl() {
            assertThatThrownBy(() -> ExtractionId.of("Patient/1", "nope"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid relative reference");
        }
    }


    @Nested
    class Serialization {
        @Test
        void jackson_deserializeFromJsonString_viaJsonCreator() throws Exception {
            ExtractionId id = om.readValue("\"Patient/42\"", ExtractionId.class);

            assertThat(id).isEqualTo(new ExtractionId("Patient", "42"));
            assertThat(id.toRelativeUrl()).isEqualTo("Patient/42");
        }

        @Test
        void jackson_serializeAsJsonString_viaJsonValue() throws Exception {
            String json = om.writeValueAsString(new ExtractionId("Patient", "42"));

            assertThat(json).isEqualTo("\"Patient/42\"");
        }
    }


    @Nested
    class Sorting {
        @Test
        void sorting_shouldOrderByResourceTypeThenId() {
            var ids = java.util.List.of(
                    new ExtractionId("Patient", "2"),
                    new ExtractionId("Observation", "1"),
                    new ExtractionId("Patient", "1"),
                    new ExtractionId("Encounter", "9"),
                    new ExtractionId("Encounter", "10")
            );

            var sorted = ids.stream().sorted().toList();

            assertThat(sorted).containsExactly(
                    // resourceType lexicographic: Encounter < Observation < Patient
                    new ExtractionId("Encounter", "10"),
                    new ExtractionId("Encounter", "9"),
                    new ExtractionId("Observation", "1"),
                    // same type -> id lexicographic
                    new ExtractionId("Patient", "1"),
                    new ExtractionId("Patient", "2")
            );
        }

        @Test
        void sorting_shouldWorkInTreeSet_usingNaturalOrder() {
            var set = new java.util.TreeSet<ExtractionId>();
            set.add(new ExtractionId("Patient", "2"));
            set.add(new ExtractionId("Patient", "1"));
            set.add(new ExtractionId("Observation", "1"));

            assertThat(set).containsExactly(
                    new ExtractionId("Observation", "1"),
                    new ExtractionId("Patient", "1"),
                    new ExtractionId("Patient", "2")
            );
        }

        @Test
        @SuppressWarnings("ConstantConditions")
        void compareTo_shouldThrowOnNull() {
            ExtractionId id = new ExtractionId("Patient", "1");

            assertThatThrownBy(() -> id.compareTo(null))
                    .isInstanceOf(NullPointerException.class);
        }
    }


}
