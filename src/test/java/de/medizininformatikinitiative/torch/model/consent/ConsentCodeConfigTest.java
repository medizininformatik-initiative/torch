package de.medizininformatikinitiative.torch.model.consent;

import de.medizininformatikinitiative.torch.exceptions.ConsentFormatException;
import de.medizininformatikinitiative.torch.model.management.TermCode;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class ConsentCodeConfigTest {

    static final TermCode GATE = new TermCode("sys", "gate");
    static final TermCode DATA = new TermCode("sys", "data");
    static final TermCode RETRO = new TermCode("sys", "retro");
    static final LocalDate LOOKBACK = LocalDate.of(1900, 1, 1);

    static ConsentCodeConfig fullConfig() {
        return new ConsentCodeConfig(List.of(
                new ProspectiveEntry(GATE, true, List.of(DATA), List.of(), null),
                new ProspectiveEntry(DATA, false, List.of(GATE), List.of(new RetroModifier(RETRO)), LOOKBACK)
        ));
    }

    @Nested
    class ValidateCodeCoOccurrence {

        @Test
        void passesWhenAllRequiredPresent() {
            assertDoesNotThrow(() -> fullConfig().validateCodeCoOccurrence(Set.of(GATE, DATA)));
        }

        @Test
        void passesWhenCodeAbsentFromSet() {
            assertDoesNotThrow(() -> fullConfig().validateCodeCoOccurrence(Set.of(new TermCode("other", "x"))));
        }

        @Test
        void throwsWhenRequiredCodeMissing() {
            assertThatThrownBy(() -> fullConfig().validateCodeCoOccurrence(Set.of(DATA)))
                    .isInstanceOf(ConsentFormatException.class)
                    .hasMessageContaining(DATA.code());
        }
    }

    @Nested
    class WithRetroModifiers {

        @Test
        void addsRetroWhenPresentInRequested() {
            Set<TermCode> result = fullConfig().withRetroModifiers(Set.of(GATE, DATA), Set.of(GATE, DATA, RETRO));

            assertThat(result).contains(RETRO);
        }

        @Test
        void doesNotAddRetroWhenAbsentFromRequested() {
            Set<TermCode> result = fullConfig().withRetroModifiers(Set.of(GATE, DATA), Set.of(GATE, DATA));

            assertThat(result).isNotEmpty().doesNotContain(RETRO);
        }

        @Test
        void doesNotAddRetroWhenProspectiveNotInSupported() {
            Set<TermCode> result = fullConfig().withRetroModifiers(Set.of(GATE), Set.of(GATE, DATA, RETRO));
            assertThat(result).isNotEmpty().doesNotContain(RETRO);
        }

        @Test
        void entryWithNoRetroModifiersDoesNotAddAnything() {
            Set<TermCode> result = fullConfig().withRetroModifiers(Set.of(GATE), Set.of(GATE));

            assertThat(result).containsExactly(GATE);
        }
    }

    @Nested
    class WithMiiConsentCodes {

        static final String SYS = "urn:oid:2.16.840.1.113883.3.1937.777.24.5.3";
        static final TermCode MDAT_NUTZEN = new TermCode(SYS, "2.16.840.1.113883.3.1937.777.24.5.3.8");
        static final TermCode MDAT_ERHEBEN = new TermCode(SYS, "2.16.840.1.113883.3.1937.777.24.5.3.6");
        static final TermCode MDAT_RETRO_NUTZEN = new TermCode(SYS, "2.16.840.1.113883.3.1937.777.24.5.3.46");
        static final TermCode MDAT_RETRO_SPEICH = new TermCode(SYS, "2.16.840.1.113883.3.1937.777.24.5.3.45");

        ConsentCodeConfig miiConfig() {
            return new ConsentCodeConfig(List.of(
                    new ProspectiveEntry(MDAT_NUTZEN, true, List.of(MDAT_ERHEBEN), List.of(), null),
                    new ProspectiveEntry(MDAT_ERHEBEN, false, List.of(MDAT_NUTZEN), List.of(
                            new RetroModifier(MDAT_RETRO_NUTZEN),
                            new RetroModifier(MDAT_RETRO_SPEICH)
                    ), LOOKBACK)
            ));
        }

        @Test
        void passesForNoRetroExampleCodes() {
            assertDoesNotThrow(() -> miiConfig().validateCodeCoOccurrence(Set.of(MDAT_NUTZEN, MDAT_ERHEBEN)));
        }

        @Test
        void passesForRetroExampleCodes() {
            assertDoesNotThrow(() -> miiConfig().validateCodeCoOccurrence(Set.of(MDAT_NUTZEN, MDAT_ERHEBEN, MDAT_RETRO_NUTZEN, MDAT_RETRO_SPEICH)));
        }

        @Test
        void throwsWhen8PresentWithout6() {
            assertThatThrownBy(() -> miiConfig().validateCodeCoOccurrence(Set.of(MDAT_NUTZEN)))
                    .isInstanceOf(ConsentFormatException.class)
                    .hasMessageContaining(MDAT_NUTZEN.code());
        }

        @Test
        void throwsWhen6PresentWithout8() {
            assertThatThrownBy(() -> miiConfig().validateCodeCoOccurrence(Set.of(MDAT_ERHEBEN)))
                    .isInstanceOf(ConsentFormatException.class)
                    .hasMessageContaining(MDAT_ERHEBEN.code());
        }
    }

    @Nested
    class Filters {

        @Test
        void prospectiveCodes_returnsAllEntries() {
            assertThat(fullConfig().prospectiveCodes()).containsExactlyInAnyOrder(GATE, DATA);
        }

        @Test
        void extractRequestedProspectiveCodes_keepsKnownCodesOnly() {
            Set<TermCode> result = fullConfig().extractRequestedProspectiveCodes(Set.of(GATE, DATA, RETRO, new TermCode("x", "y")));

            assertThat(result).containsExactlyInAnyOrder(GATE, DATA);
        }

        @Test
        void gateCodes_returnsOnlyGateEntries() {
            assertThat(fullConfig().gateCodes(Set.of(GATE, DATA))).containsExactly(GATE);
        }

        @Test
        void nonGateCodes_returnsOnlyDataEntries() {
            assertThat(fullConfig().nonGateCodes(Set.of(GATE, DATA))).containsExactly(DATA);
        }

        @Test
        void retroToProspective_mapsRetroCodeToEntry() {
            var result = fullConfig().retroToProspective();

            assertThat(result).containsKey(RETRO);
            assertThat(result.get(RETRO).code()).isEqualTo(DATA);
        }
    }
}
