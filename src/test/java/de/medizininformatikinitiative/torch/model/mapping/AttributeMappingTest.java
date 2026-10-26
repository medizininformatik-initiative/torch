package de.medizininformatikinitiative.torch.model.mapping;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.medizininformatikinitiative.torch.model.sq.TermCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AttributeMappingTest {

    static final TermCode KEY = new TermCode("sys-162102", "code-162115", "display-162129");
    static final TermCode COMPOSITE_CODE = new TermCode("sys-162549", "code-162555", "display-162610");
    static final String SEARCH_PARAM = "searchParam-293925";

    @Test
    void fromJson() throws JsonProcessingException {
        var result = parse("""
                {
                    "attributeType": "code",
                    "attributeKey": {
                        "system": "sys-162102",
                        "code": "code-162115",
                        "display": "display-162129"
                    },
                    "attributeSearchParameter": "searchParam-293925"
                }
                """);

        assertThat(result).isEqualTo(AttributeMapping.code(KEY, SEARCH_PARAM));
    }

    @Test
    void fromJson_withCompositeCode() throws JsonProcessingException {
        var result = parse("""
                {
                    "attributeType": "composite-quantity",
                    "attributeKey": {
                        "system": "sys-162102",
                        "code": "code-162115",
                        "display": "display-162129"
                    },
                    "attributeSearchParameter": "searchParam-293925",
                    "compositeCode": {
                        "system": "sys-162549",
                        "code": "code-162555",
                        "display": "display-162610"
                    }
                }
                """);

        assertThat(result).isEqualTo(AttributeMapping.compositeComparator(KEY, SEARCH_PARAM, COMPOSITE_CODE));
    }

    @Test
    void fromJson_compositeType_withoutCompositeCode() {
        assertThatThrownBy(() -> parse("""
                {
                    "attributeType": "composite-quantity",
                    "attributeKey": {
                        "code": "8480-6",
                        "display": "Systolischer Blutdruck",
                        "system": "http://loing.org"
                    },
                    "attributeSearchParameter": "component-code-value-quantity"
                }
                """))
                .hasCauseExactlyInstanceOf(CompositeCodeNotFoundException.class);
    }

    static AttributeMapping parse(String s) throws JsonProcessingException {
        return new ObjectMapper().readValue(s, AttributeMapping.class);
    }
}
