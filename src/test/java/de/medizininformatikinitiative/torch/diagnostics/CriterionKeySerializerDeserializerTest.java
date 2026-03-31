package de.medizininformatikinitiative.torch.diagnostics;

import com.fasterxml.jackson.core.JsonGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CriterionKeySerializerDeserializerTest {

    @Nested
    class Serialize {

        @Mock
        JsonGenerator gen;

        @Test
        void encode_withAllNonNull_joinsWithPipes() {
            var key = new CriterionKey(ExclusionKind.MUST_HAVE, "name", "groupRef", "attrRef");

            var encoded = CriterionKeySerializer.encode(key);

            assertThat(encoded).isEqualTo("MUST_HAVE|name|groupRef|attrRef");
        }

        @Test
        void encode_withNullName_usesEmptySegment() {
            var key = new CriterionKey(ExclusionKind.CONSENT, null, "groupRef", "attrRef");

            var encoded = CriterionKeySerializer.encode(key);

            assertThat(encoded).isEqualTo("CONSENT||groupRef|attrRef");
        }

        @Test
        void encode_withNullGroupRef_usesEmptySegment() {
            var key = new CriterionKey(ExclusionKind.REFERENCE_NOT_FOUND, "name", null, "attrRef");

            var encoded = CriterionKeySerializer.encode(key);

            assertThat(encoded).isEqualTo("REFERENCE_NOT_FOUND|name||attrRef");
        }

        @Test
        void encode_withNullAttributeRef_usesEmptySegment() {
            var key = new CriterionKey(ExclusionKind.REFERENCE_INVALID, "name", "groupRef", null);

            var encoded = CriterionKeySerializer.encode(key);

            assertThat(encoded).isEqualTo("REFERENCE_INVALID|name|groupRef|");
        }

        @Test
        void serialize_writesEncodedKeyAsFieldName() throws IOException {
            var key = new CriterionKey(ExclusionKind.MUST_HAVE, "name", "groupRef", "attrRef");

            new CriterionKeySerializer().serialize(key, gen, null);

            verify(gen).writeFieldName("MUST_HAVE|name|groupRef|attrRef");
        }
    }

    @Nested
    class Deserialize {

        @Test
        void deserializeKey_withAllFields_parsesCorrectly() throws IOException {
            var result = (CriterionKey) new CriterionKeyDeserializer()
                    .deserializeKey("MUST_HAVE|name|groupRef|attrRef", null);

            assertThat(result.kind()).isEqualTo(ExclusionKind.MUST_HAVE);
            assertThat(result.name()).isEqualTo("name");
            assertThat(result.groupRef()).isEqualTo("groupRef");
            assertThat(result.attributeRef()).isEqualTo("attrRef");
        }

        @Test
        void deserializeKey_withEmptySegments_returnsNullFields() throws IOException {
            var result = (CriterionKey) new CriterionKeyDeserializer()
                    .deserializeKey("CONSENT|||", null);

            assertThat(result.kind()).isEqualTo(ExclusionKind.CONSENT);
            assertThat(result.name()).isNull();
            assertThat(result.groupRef()).isNull();
            assertThat(result.attributeRef()).isNull();
        }

        @Test
        void deserializeKey_withTooFewParts_throwsIOException() {
            assertThatThrownBy(() -> new CriterionKeyDeserializer()
                    .deserializeKey("MUST_HAVE|only|three", null))
                    .isInstanceOf(IOException.class)
                    .hasMessageContaining("Invalid CriterionKey map key");
        }

        @Test
        void deserializeKey_withTooManyParts_throwsIOException() {
            assertThatThrownBy(() -> new CriterionKeyDeserializer()
                    .deserializeKey("MUST_HAVE|a|b|c|extra", null))
                    .isInstanceOf(IOException.class)
                    .hasMessageContaining("Invalid CriterionKey map key");
        }

        @Test
        void deserializeKey_withInvalidKind_throwsIOException() {
            assertThatThrownBy(() -> new CriterionKeyDeserializer()
                    .deserializeKey("INVALID_KIND|name|group|attr", null))
                    .isInstanceOf(IOException.class)
                    .hasMessageContaining("Unknown ExclusionKind");
        }
    }

    @Nested
    class RoundTrip {

        @Test
        void withAllFields_preservesAllValues() throws IOException {
            var original = new CriterionKey(ExclusionKind.REFERENCE_OUTSIDE_BATCH, "name", "groupRef", "attrRef");

            var encoded = CriterionKeySerializer.encode(original);
            var result = (CriterionKey) new CriterionKeyDeserializer().deserializeKey(encoded, null);

            assertThat(result).isEqualTo(original);
        }

        @Test
        void withNullOptionalFields_preservesNulls() throws IOException {
            var original = new CriterionKey(ExclusionKind.REFERENCE_NOT_FOUND, "name", null, null);

            var encoded = CriterionKeySerializer.encode(original);
            var result = (CriterionKey) new CriterionKeyDeserializer().deserializeKey(encoded, null);

            assertThat(result.kind()).isEqualTo(original.kind());
            assertThat(result.name()).isEqualTo(original.name());
            assertThat(result.groupRef()).isNull();
            assertThat(result.attributeRef()).isNull();
        }
    }
}
