package de.medizininformatikinitiative.torch.model.management;

import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.StringType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MultiElementContextTest {

    @Test
    void shouldRedactExtension_returnsFalse_forCanonicalDataAbsentReasonUrl() {
        MultiElementContext ctx = new MultiElementContext(List.of());

        Extension dar = new Extension(
                "http://hl7.org/fhir/StructureDefinition/data-absent-reason",
                new StringType("unknown")
        );

        assertThat(ctx.shouldRedactExtension(dar)).isFalse();
    }

    @Test
    void shouldRedactExtension_returnsFalse_forCodeSystemDataAbsentReasonUrlVariant() {
        MultiElementContext ctx = new MultiElementContext(List.of());

        Extension darVariant = new Extension(
                "http://terminology.hl7.org/CodeSystem/data-absent-reason",
                new StringType("unknown")
        );

        assertThat(ctx.shouldRedactExtension(darVariant)).isFalse();
    }

    @Test
    void shouldRedactExtension_returnsTrue_forUnknownExtension_whenNoSliceMatches() {
        MultiElementContext ctx = new MultiElementContext(List.of());

        Extension unknown = new Extension(
                "http://example.org/fhir/StructureDefinition/some-extension",
                new StringType("x")
        );

        assertThat(ctx.shouldRedactExtension(unknown)).isTrue();
    }

    @Test
    void shouldRedactExtension_returnsTrue_whenExtensionIsNull() {
        MultiElementContext ctx = new MultiElementContext(List.of());

        assertThat(ctx.shouldRedactExtension(null)).isTrue();
    }

    @Test
    void shouldRedactExtension_returnsTrue_whenExtensionUrlIsNull() {
        MultiElementContext ctx = new MultiElementContext(List.of());

        Extension ext = new Extension();
        ext.setValue(new StringType("x")); // url remains null

        assertThat(ctx.shouldRedactExtension(ext)).isTrue();
    }
}
