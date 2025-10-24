package de.medizininformatikinitiative.torch.model.crtdl.annotated;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.medizininformatikinitiative.torch.model.consent.ConsentCode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class AnnotatedCrtdlTest {

    public static final Set<ConsentCode> CONSENT_CONTEXT = Set.of(new ConsentCode("fdpg.mii.cds", "Einwilligung"));
    private final ObjectMapper mapper = new ObjectMapper();

    private AnnotatedDataExtraction emptyDataExtraction() {
        return new AnnotatedDataExtraction(List.of());
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

        JsonNode cohortDefinition = mapper.readTree(json);
        AnnotatedCrtdl crtdl = new AnnotatedCrtdl(cohortDefinition, emptyDataExtraction());

        Set<ConsentCode> codes = crtdl.consentKey(CONSENT_CONTEXT);


        assertThat(codes).containsExactlyInAnyOrder(
                new ConsentCode("s1", "A1")
                , new ConsentCode("s1", "A2")
                , new ConsentCode("s1", "B1"));
    }

    @Test
    void returnsEmptyIfInclusionCriteriaIsNotArray() throws Exception {
        String json = """
                { "inclusionCriteria": { "context": { "code": "Einwilligung"} } }
                """;
        JsonNode cohortDefinition = mapper.readTree(json);
        AnnotatedCrtdl crtdl = new AnnotatedCrtdl(cohortDefinition, emptyDataExtraction());

        assertThat(crtdl.consentKey(CONSENT_CONTEXT)).isEmpty();
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
        JsonNode cohortDefinition = mapper.readTree(json);
        AnnotatedCrtdl crtdl = new AnnotatedCrtdl(cohortDefinition, emptyDataExtraction());

        Set<ConsentCode> codes = crtdl.consentKey(CONSENT_CONTEXT);

        // Only the array element contributes (X2)
        assertThat(codes).containsExactly(new ConsentCode("s1", "X2"));
    }

    @Test
    void ignoresCodeNotSetInContext() throws Exception {
        String json = """
                {
                  "inclusionCriteria": [
                    [
                      {
                        "context": { "system":"fdpg.mii.cds"  }
                      }
                    ]
                  ]
                }
                """;
        JsonNode cohortDefinition = mapper.readTree(json);
        AnnotatedCrtdl crtdl = new AnnotatedCrtdl(cohortDefinition, emptyDataExtraction());

        Set<ConsentCode> codes = crtdl.consentKey(CONSENT_CONTEXT);

        // Only the last one should remain
        assertThat(codes).isEmpty();
    }

    @Test
    void skipsInvalidTermCodes() throws Exception {
        String json = """
                {
                  "inclusionCriteria": [
                    [
                      {
                        "context": { "code": "Einwilligung", "system":"fdpg.mii.cds"  },
                        "termCodes": { "system": "s1", "code": "INVALID" }
                      },
                      {
                        "context": { "code": "Einwilligung", "system":"fdpg.mii.cds"  },
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
        JsonNode cohortDefinition = mapper.readTree(json);
        AnnotatedCrtdl crtdl = new AnnotatedCrtdl(cohortDefinition, emptyDataExtraction());

        Set<ConsentCode> codes = crtdl.consentKey(CONSENT_CONTEXT);

        // Only the last one should remain
        assertThat(codes).containsExactly(new ConsentCode("s1", "VALID"));
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

        JsonNode cohortDefinition = mapper.readTree(json);
        AnnotatedCrtdl crtdl = new AnnotatedCrtdl(cohortDefinition, emptyDataExtraction());

        Set<ConsentCode> codes = crtdl.consentKey(CONSENT_CONTEXT);

        assertThat(codes).containsExactly(new ConsentCode("s1", "TOP1"));
    }

    @Test
    void returnsEmptyIfNoInclusionCriteria() throws Exception {
        String json = "{ }";
        JsonNode cohortDefinition = mapper.readTree(json);
        AnnotatedCrtdl crtdl = new AnnotatedCrtdl(cohortDefinition, emptyDataExtraction());

        Set<ConsentCode> codes = crtdl.consentKey(CONSENT_CONTEXT);

        assertThat(codes).isEmpty();
    }

    @Test
    void returnsEmptyIfNoContext() throws Exception {
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

        JsonNode cohortDefinition = mapper.readTree(json);
        AnnotatedCrtdl crtdl = new AnnotatedCrtdl(cohortDefinition, emptyDataExtraction());

        Set<ConsentCode> codes = crtdl.consentKey(CONSENT_CONTEXT);

        assertThat(codes).isEmpty();
    }
}
