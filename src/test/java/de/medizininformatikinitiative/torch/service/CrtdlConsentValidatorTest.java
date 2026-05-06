package de.medizininformatikinitiative.torch.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.medizininformatikinitiative.torch.exceptions.ConsentFormatException;
import de.medizininformatikinitiative.torch.model.crtdl.Crtdl;
import de.medizininformatikinitiative.torch.model.crtdl.DataExtraction;
import de.medizininformatikinitiative.torch.model.management.TermCode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CrtdlConsentValidatorTest {

    private static final String MII_OID_SYSTEM = "urn:oid:2.16.840.1.113883.3.1937.777.24.5.3";
    private static final TermCode MDAT_NUTZEN       = new TermCode(MII_OID_SYSTEM, "2.16.840.1.113883.3.1937.777.24.5.3.8");
    private static final TermCode MDAT_ERHEBEN      = new TermCode(MII_OID_SYSTEM, "2.16.840.1.113883.3.1937.777.24.5.3.6");
    private static final TermCode MDAT_RETRO_NUTZEN = new TermCode(MII_OID_SYSTEM, "2.16.840.1.113883.3.1937.777.24.5.3.46");
    private static final TermCode MDAT_RETRO_SPEICH = new TermCode(MII_OID_SYSTEM, "2.16.840.1.113883.3.1937.777.24.5.3.45");

    private final ObjectMapper mapper = new ObjectMapper();
    private final CrtdlConsentValidator crtdlConsentValidator = new CrtdlConsentValidator();

    private DataExtraction emptyDataExtraction() {
        return new DataExtraction(List.of());
    }

    @Test
    void acceptsNonConsentAsExClusionCriteria() throws Exception {
        String json = """
                {
                  "exclusionCriteria": [
                    [
                      {
                        "context": {
                          "code": "Not-Einwilligung",
                          "system": "fdpg.mii.cds"
                        },
                        "termCodes": [
                          { "system": "s1", "code": "A2" }
                        ]
                      }
                    ]
                  ]
                }""";
        Optional<Set<TermCode>> codes = getTermCodes(json);

        assertThat(codes).isEmpty();
    }

    @Test
    void failsConsentAsExclusionCriteria() {
        String json = """
                {
                  "exclusionCriteria": [
                    [
                      {
                        "context": {
                          "code": "Einwilligung",
                          "system": "fdpg.mii.cds"
                        },
                        "termCodes": [
                          { "system": "s1", "code": "A2" }
                        ]
                      }
                    ]
                  ]
                }""";

        assertThatThrownBy(() -> getTermCodes(json))
                .isInstanceOf(ConsentFormatException.class)
                .hasMessageContaining("Exclusion criteria must not contain Einwilligung consent codes.");
    }

    @Test
    void collectsAllTopLevelCodes() throws Exception {
        String json = """
                {
                  "inclusionCriteria": [
                    [
                      {
                        "context": {
                          "code": "Einwilligung",
                          "system": "fdpg.mii.cds"
                        },
                        "termCodes": [
                          { "system": "s1", "code": "A1" },
                          { "system": "s1", "code": "A2" }
                        ]
                      }
                    ],
                    [
                      {
                        "context": {
                          "code": "Einwilligung",
                          "system": "fdpg.mii.cds"
                        },
                        "termCodes": [
                          { "system": "s1", "code": "B1" }
                        ]
                      }
                    ]
                  ]
                }""";

        Optional<Set<TermCode>> codes = getTermCodes(json);

        assertThat(codes).isPresent();
        assertThat(codes.get()).containsExactlyInAnyOrder(
                new TermCode("s1", "A1")
                , new TermCode("s1", "A2")
                , new TermCode("s1", "B1"));
    }

    @Test
    void returnsEmptyIfInclusionCriteriaIsNotArray() throws Exception {
        String json = """
                { "inclusionCriteria": { "context": { "code": "Einwilligung"} } }
                """;
        Optional<Set<TermCode>> codes = getTermCodes(json);

        assertThat(codes).isEmpty();
    }

    @Test
    void ignoresNonArrayExclusionCriteriaElements() throws Exception {
        String json = """
                {
                  "exclusionCriteria": {
                    "context": { "code": "Other", "system": "fdpg.mii.cds" },
                    "termCodes": [
                      { "system": "s1", "code": "A1" }
                    ]
                  }
                }
                """;

        Optional<Set<TermCode>> codes = getTermCodes(json);

        // The validator should skip non-array exclusionCriteria without throwing
        assertThat(codes).isEmpty();
    }

    @Test
    void ignoresNonArrayInclusionCriteriaElements() throws Exception {
        String json = """
                {
                  "inclusionCriteria": [
                    { "context": { "code": "Einwilligung","system":"fdpg.mii.cds"  }, "termCodes": [ { "system": "s1", "code": "X1" } ] },
                    [ { "context": { "code": "Einwilligung","system":"fdpg.mii.cds" }, "termCodes": [ { "system": "s1", "code": "X2" } ] } ]
                  ]
                }
                """;
        Optional<Set<TermCode>> codes = getTermCodes(json);

        // Only the array element contributes (X2)
        assertThat(codes).isPresent();
        assertThat(codes.get()).containsExactly(new TermCode("s1", "X2"));
    }

    @Test
    void collectsMultipleEinwilligungBlocksInOneGroup() throws Exception {
        String json = """
                {
                  "inclusionCriteria": [
                    [
                      {
                        "context": { "code": "Einwilligung", "system": "fdpg.mii.cds" },
                        "termCodes": [
                          { "system": "urn:oid:2.16.840.1.113883.3.1937.777.24.5.3", "code": "2.16.840.1.113883.3.1937.777.24.5.3.8" }
                        ]
                      },
                      {
                        "context": { "code": "Einwilligung", "system": "fdpg.mii.cds" },
                        "termCodes": [
                          { "system": "urn:oid:2.16.840.1.113883.3.1937.777.24.5.3", "code": "2.16.840.1.113883.3.1937.777.24.5.3.46" }
                        ]
                      }
                    ]
                  ]
                }
                """;

        Optional<Set<TermCode>> codes = getTermCodes(json);

        assertThat(codes).isPresent();
        assertThat(codes.get()).containsExactlyInAnyOrder(
                new TermCode("urn:oid:2.16.840.1.113883.3.1937.777.24.5.3", "2.16.840.1.113883.3.1937.777.24.5.3.8"),
                new TermCode("urn:oid:2.16.840.1.113883.3.1937.777.24.5.3", "2.16.840.1.113883.3.1937.777.24.5.3.46")
        );
    }

    @Test
    void skipsInvalidTermCodes() throws Exception {
        String json = """
                {
                  "inclusionCriteria": [
                    [
                      {
                        "context": { "code": "Einwilligung" },
                        "termCodes": { "system": "s1", "code": "INVALID" }
                      },
                      {
                        "context": { "code": "Einwilligung" },
                        "termCodes": [
                          { "system": "s1" },
                          { "code": "C2" },
                          { "system": "s1", "code": "VALID" }
                        ]
                      }
                    ]
                  ]
                }
                """;

        Optional<Set<TermCode>> codes = getTermCodes(json);

        assertThat(codes).isPresent();
        assertThat(codes.get()).containsExactly(new TermCode("s1", "VALID"));
    }

    @Test
    void ignoresNestedCriteria() throws Exception {
        String json = """
                {
                  "inclusionCriteria": [
                    [
                      {
                        "context": { "code": "Einwilligung", "system":"fdpg.mii.cds" },
                        "termCodes": [ {
                        "system": "s1",
                        "code": "TOP1" } ],
                        "nested": [
                          {
                            "context": { "code": "Einwilligung" },
                            "termCodes": [ {
                            "system": "s1",
                            "code": "IGNORE_ME" } ]
                          }
                        ]
                      }
                    ]
                  ]
                }
                """;

        Optional<Set<TermCode>> codes = getTermCodes(json);

        assertThat(codes).isPresent();
        assertThat(codes.get()).containsExactly(new TermCode("s1", "TOP1"));
    }

    @Test
    void skipsTermCodesWithMissingFields() throws Exception {
        String json = """
                {
                  "inclusionCriteria": [
                    [
                      {
                        "context": { "code": "Einwilligung" },
                        "termCodes": [
                          { "system": "s1" },
                          { "code": "C2" },
                          { "system": "s1", "code": "VALID" }
                        ]
                      }
                    ]
                  ]
                }
                """;

        Optional<Set<TermCode>> codes = getTermCodes(json);

        assertThat(codes).isPresent();
        assertThat(codes.get()).containsExactly(new TermCode("s1", "VALID"));
    }

    @Test
    void skipsNonArrayTermCodes() throws Exception {
        String json = """
                {
                  "inclusionCriteria": [
                    [
                      {
                        "context": { "code": "Einwilligung" },
                        "termCodes": { "system": "s1", "code": "INVALID" }
                      }
                    ]
                  ]
                }
                """;

        Optional<Set<TermCode>> codes = getTermCodes(json);

        assertThat(codes).isEmpty(); // triggers `return Stream.empty()`
    }

    @Test
    void returnsEmptyIfNoInclusionCriteria() throws Exception {
        String json = "{ }";
        Optional<Set<TermCode>> codes = getTermCodes(json);

        assertThat(codes).isEmpty();
    }

    @Test
    void returnsEmptyIfNoEinwilligung() throws Exception {
        String json = """
                {
                  "inclusionCriteria": [
                    [
                      {
                        "context": { "code": "Other" },
                        "termCodes": [ { "code": "X1" } ]
                      }
                    ]
                  ]
                }
                """;

        Optional<Set<TermCode>> codes = getTermCodes(json);

        assertThat(codes).isEmpty();
    }

    // --- issue #851 examples ---

    @Test
    void noRetroExampleExtractsBothMiiCodes() throws Exception {
        // mirrors cohort-consent-no-retro.json: .8 and .6 in separate inclusion groups
        String json = """
                {
                  "inclusionCriteria": [
                    [
                      {
                        "context": { "code": "Einwilligung", "system": "fdpg.mii.cds", "version": "1.0.0" },
                        "termCodes": [
                          { "system": "urn:oid:2.16.840.1.113883.3.1937.777.24.5.3",
                            "code": "2.16.840.1.113883.3.1937.777.24.5.3.8",
                            "display": "MDAT wissenschaftlich nutzen EU DSGVO NIVEAU" }
                        ]
                      }
                    ],
                    [
                      {
                        "context": { "code": "Einwilligung", "system": "fdpg.mii.cds", "version": "1.0.0" },
                        "termCodes": [
                          { "system": "urn:oid:2.16.840.1.113883.3.1937.777.24.5.3",
                            "code": "2.16.840.1.113883.3.1937.777.24.5.3.6",
                            "display": "MDAT erheben" }
                        ]
                      }
                    ]
                  ]
                }""";

        Optional<Set<TermCode>> codes = getTermCodes(json);

        assertThat(codes).isPresent();
        assertThat(codes.get()).containsExactlyInAnyOrder(MDAT_NUTZEN, MDAT_ERHEBEN);
    }

    @Test
    void retroExampleExtractsAllFourMiiCodes() throws Exception {
        // mirrors cohort-consent-retro.json: .46/.45/.6 in group 1, .8 in group 2, .6 again in group 3
        // .6 is duplicated across groups but deduplicates in the returned Set
        String json = """
                {
                  "inclusionCriteria": [
                    [
                      {
                        "context": { "code": "Einwilligung", "system": "fdpg.mii.cds", "version": "1.0.0" },
                        "termCodes": [
                          { "system": "urn:oid:2.16.840.1.113883.3.1937.777.24.5.3",
                            "code": "2.16.840.1.113883.3.1937.777.24.5.3.46",
                            "display": "MDAT retrospektiv wissenschaftlich nutzen EU DSGVO NIVEAU" }
                        ]
                      },
                      {
                        "context": { "code": "Einwilligung", "system": "fdpg.mii.cds", "version": "1.0.0" },
                        "termCodes": [
                          { "system": "urn:oid:2.16.840.1.113883.3.1937.777.24.5.3",
                            "code": "2.16.840.1.113883.3.1937.777.24.5.3.45",
                            "display": "MDAT retrospektiv speichern verarbeiten" }
                        ]
                      },
                      {
                        "context": { "code": "Einwilligung", "system": "fdpg.mii.cds", "version": "1.0.0" },
                        "termCodes": [
                          { "system": "urn:oid:2.16.840.1.113883.3.1937.777.24.5.3",
                            "code": "2.16.840.1.113883.3.1937.777.24.5.3.6",
                            "display": "MDAT erheben" }
                        ]
                      }
                    ],
                    [
                      {
                        "context": { "code": "Einwilligung", "system": "fdpg.mii.cds", "version": "1.0.0" },
                        "termCodes": [
                          { "system": "urn:oid:2.16.840.1.113883.3.1937.777.24.5.3",
                            "code": "2.16.840.1.113883.3.1937.777.24.5.3.8",
                            "display": "MDAT wissenschaftlich nutzen EU DSGVO NIVEAU" }
                        ]
                      }
                    ],
                    [
                      {
                        "context": { "code": "Einwilligung", "system": "fdpg.mii.cds", "version": "1.0.0" },
                        "termCodes": [
                          { "system": "urn:oid:2.16.840.1.113883.3.1937.777.24.5.3",
                            "code": "2.16.840.1.113883.3.1937.777.24.5.3.6",
                            "display": "MDAT erheben" }
                        ]
                      }
                    ]
                  ]
                }""";

        Optional<Set<TermCode>> codes = getTermCodes(json);

        assertThat(codes).isPresent();
        assertThat(codes.get()).containsExactlyInAnyOrder(
                MDAT_NUTZEN, MDAT_ERHEBEN, MDAT_RETRO_NUTZEN, MDAT_RETRO_SPEICH);
    }

    @Test
    void failsWhenGateCodeUsedAsExclusionCriterion() {
        // placing .8 (MDAT wissenschaftlich nutzen) in exclusion criteria is not allowed
        String json = """
                {
                  "exclusionCriteria": [
                    [
                      {
                        "context": { "code": "Einwilligung", "system": "fdpg.mii.cds", "version": "1.0.0" },
                        "termCodes": [
                          { "system": "urn:oid:2.16.840.1.113883.3.1937.777.24.5.3",
                            "code": "2.16.840.1.113883.3.1937.777.24.5.3.8",
                            "display": "MDAT wissenschaftlich nutzen EU DSGVO NIVEAU" }
                        ]
                      }
                    ]
                  ]
                }""";

        assertThatThrownBy(() -> getTermCodes(json))
                .isInstanceOf(ConsentFormatException.class)
                .hasMessageContaining("Exclusion criteria must not contain Einwilligung consent codes.");
    }

    @Test
    void failsWhenRetroCodeUsedAsExclusionCriterion() {
        // placing .46 (MDAT retrospektiv) in exclusion criteria is not allowed
        String json = """
                {
                  "exclusionCriteria": [
                    [
                      {
                        "context": { "code": "Einwilligung", "system": "fdpg.mii.cds", "version": "1.0.0" },
                        "termCodes": [
                          { "system": "urn:oid:2.16.840.1.113883.3.1937.777.24.5.3",
                            "code": "2.16.840.1.113883.3.1937.777.24.5.3.46",
                            "display": "MDAT retrospektiv wissenschaftlich nutzen EU DSGVO NIVEAU" }
                        ]
                      }
                    ]
                  ]
                }""";

        assertThatThrownBy(() -> getTermCodes(json))
                .isInstanceOf(ConsentFormatException.class)
                .hasMessageContaining("Exclusion criteria must not contain Einwilligung consent codes.");
    }

    private Optional<Set<TermCode>> getTermCodes(String json) throws JsonProcessingException, ConsentFormatException {
        return crtdlConsentValidator.extractConsentCodes(new Crtdl(mapper.readTree(json), emptyDataExtraction()));
    }


}
