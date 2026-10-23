package de.medizininformatikinitiative.torch.model.mapping;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.medizininformatikinitiative.torch.model.sq.TermCode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static de.medizininformatikinitiative.torch.model.mapping.FixedCriterionType.CODE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FixedCriterionTest {

    static final TermCode CONCEPT = new TermCode("http://hl7.org/fhir/consent-state-codes", "active", "Active");
    static final TermCode COMPOSITE_CODE = new TermCode("615098system", "164923code", "501602display");

    @Test
    void fromJson() throws Exception {
        var result = parse("""
                {
                    "type": "code",
                    "fhirPath": "status",
                    "searchParameter": "status",
                    "value": [
                        {
                            "code": "active",
                            "display": "Active",
                            "system": "http://hl7.org/fhir/consent-state-codes"
                        }
                    ]
                }
                """);

        assertThat(result).isEqualTo(new FixedCriterion(CODE, "status", List.of(CONCEPT), null));
    }

    @Test
    void fromJson_withCompositeCode() throws Exception {
        var result = parse("""
                {
                    "type": "code",
                    "fhirPath": "status",
                    "searchParameter": "status",
                    "compositeCode": {
                        "code": "164923code",
                        "display": "501602display",
                        "system": "615098system"
                    },
                    "value": [
                        {
                            "code": "active",
                            "display": "Active",
                            "system": "http://hl7.org/fhir/consent-state-codes"
                        }
                    ]
                }
                """);

        assertThat(result).isEqualTo(new FixedCriterion(CODE, "status", List.of(CONCEPT), COMPOSITE_CODE));
    }

    @Test
    void fromJson_fixedCriterion_withMissingCompositeCode() {
        assertThatThrownBy(() -> parse("""
                {
                    "type": "composite-concept",
                    "fhirPath": "status",
                    "searchParameter": "status",
                    "value": [
                        {
                            "code": "active",
                            "display": "Active",
                            "system": "http://hl7.org/fhir/consent-state-codes"
                        }
                    ]
                }
                """))
                .hasRootCauseExactlyInstanceOf(CompositeCodeNotFoundException.class)
                .hasRootCauseMessage("Expected mapping to have a `compositeCode` property because the type is `composite-concept`.");
    }

    static FixedCriterion parse(String s) throws JsonProcessingException {
        return new ObjectMapper().readValue(s, FixedCriterion.class);
    }
}
