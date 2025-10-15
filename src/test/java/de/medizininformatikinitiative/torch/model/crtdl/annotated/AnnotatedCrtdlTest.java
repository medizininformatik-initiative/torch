package de.medizininformatikinitiative.torch.model.crtdl.annotated;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.medizininformatikinitiative.torch.model.consent.ConsentCode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class AnnotatedCrtdlTest {

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
                         "context": { "code": "Einwilligung" },
                         "termCodes": [
                           {
                           "system": "s1",
                           "code": "A1"
                           },
                           {
                           "system": "s1",
                           "code": "A2"
                           }
                         ]
                       }
                     ],
                     [
                       {
                         "context": { "code": "Einwilligung" },
                         "termCodes": [
                           {
                           "system": "s1",
                           "code": "B1" }
                         ]
                       }
                     ]
                   ]
                 }
                \s""";

        JsonNode cohortDefinition = mapper.readTree(json);
        AnnotatedCrtdl crtdl = new AnnotatedCrtdl(cohortDefinition, emptyDataExtraction());

        Optional<Set<ConsentCode>> codes = crtdl.consentKey();

        assertThat(codes).isPresent();
        assertThat(codes.get()).containsExactlyInAnyOrder(
                new ConsentCode("s1", "A1")
                , new ConsentCode("s1", "A2")
                , new ConsentCode("s1", "B1"));
    }

    @Test
    void returnsEmptyIfInclusionCriteriaIsNotArray() throws Exception {
        String json = """
                { "inclusionCriteria": { "context": { "code": "Einwilligung" } } }
                """;
        JsonNode cohortDefinition = mapper.readTree(json);
        AnnotatedCrtdl crtdl = new AnnotatedCrtdl(cohortDefinition, emptyDataExtraction());

        assertThat(crtdl.consentKey()).isEmpty();
    }

    @Test
    void ignoresNonArrayInclusionCriteriaElements() throws Exception {
        String json = """
                {
                  "inclusionCriteria": [
                    { "context": { "code": "Einwilligung" }, "termCodes": [ { "system": "s1", "code": "X1" } ] },
                    [ { "context": { "code": "Einwilligung" }, "termCodes": [ { "system": "s1", "code": "X2" } ] } ]
                  ]
                }
                """;
        JsonNode cohortDefinition = mapper.readTree(json);
        AnnotatedCrtdl crtdl = new AnnotatedCrtdl(cohortDefinition, emptyDataExtraction());

        Optional<Set<ConsentCode>> codes = crtdl.consentKey();

        // Only the array element contributes (X2)
        assertThat(codes).isPresent();
        assertThat(codes.get()).containsExactly(new ConsentCode("s1", "X2"));
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
        JsonNode cohortDefinition = mapper.readTree(json);
        AnnotatedCrtdl crtdl = new AnnotatedCrtdl(cohortDefinition, emptyDataExtraction());

        Optional<Set<ConsentCode>> codes = crtdl.consentKey();

        // Only the last one should remain
        assertThat(codes).isPresent();
        assertThat(codes.get()).containsExactly(new ConsentCode("s1", "VALID"));
    }

    @Test
    void ignoresNestedCriteria() throws Exception {
        String json = """
                {
                  "inclusionCriteria": [
                    [
                      {
                        "context": { "code": "Einwilligung" },
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

        Optional<Set<ConsentCode>> codes = crtdl.consentKey();

        assertThat(codes).isPresent();
        assertThat(codes.get()).containsExactly(new ConsentCode("s1", "TOP1"));
    }

    @Test
    void returnsEmptyIfNoInclusionCriteria() throws Exception {
        String json = "{ }";
        JsonNode cohortDefinition = mapper.readTree(json);
        AnnotatedCrtdl crtdl = new AnnotatedCrtdl(cohortDefinition, emptyDataExtraction());

        Optional<Set<ConsentCode>> codes = crtdl.consentKey();

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

        JsonNode cohortDefinition = mapper.readTree(json);
        AnnotatedCrtdl crtdl = new AnnotatedCrtdl(cohortDefinition, emptyDataExtraction());

        Optional<Set<ConsentCode>> codes = crtdl.consentKey();

        assertThat(codes).isEmpty();
    }
}
