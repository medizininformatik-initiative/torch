package de.medizininformatikinitiative.torch.model.sq;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static de.medizininformatikinitiative.torch.model.sq.Comparator.GREATER_THAN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


public class FilterPartTest {

    static final TermCode TERM_CODE = TermCode.of("sys-153728", "code-153757", "display-153815");
    static final TermCode UNIT = TermCode.of("sys-155523", "km/h", "km/h");
    static final TermCode UCUM_UNIT = TermCode.of("http://unitsofmeasure.org", "km/h", "km/h");
    static final BigDecimal VALUE = BigDecimal.valueOf(100);
    static final BigDecimal MIN_VALUE = BigDecimal.valueOf(50);
    static final BigDecimal MAX_VALUE = BigDecimal.valueOf(100);

    @Nested
    class Concept {

        @Test
        void fromJson() throws Exception {
            var result = parse("""
                    {
                        "type": "concept",
                        "selectedConcepts": [
                            {
                                "system": "sys-153728",
                                "code": "code-153757",
                                "display": "display-153815"
                            }
                        ]
                    }
                    """);

            assertThat(result).isEqualTo(ConceptFilterPart.of(TERM_CODE));
        }

        @Test
        void fromJson_withEmptyConcepts() {
            assertThatThrownBy(() -> parse("""
                    {
                        "type": "concept",
                        "selectedConcepts": []
                    }
                    """))
                    .hasMessage("empty selectedConcepts");
        }

        @Test
        void fromJson_noConcepts() {
            assertThatThrownBy(() -> parse("""
                    {
                        "type": "concept"
                    }
                    """))
                    .hasMessage("empty selectedConcepts");
        }
    }

    @Nested
    class QuantityComparator {

        @Test
        void fromJson() throws JsonProcessingException {
            var result = parse("""
                    {
                        "type": "quantity-comparator",
                        "comparator": "gt",
                        "value": 100,
                        "unit": {
                            "code": "km/h",
                            "display": "km/h",
                            "system": "sys-155523"
                        }
                    }
                    """);

            assertThat(result).isEqualTo(QuantityComparatorFilterPart.of(GREATER_THAN, Quantity.of(VALUE, UNIT)));
        }

        @Test
        void fromJson_Comparator_noUnit() throws JsonProcessingException {
            var result = parse("""
                    {
                        "type": "quantity-comparator",
                        "comparator": "gt",
                        "value": 100
                    }
                    """);

            assertThat(result).isEqualTo(QuantityComparatorFilterPart.of(GREATER_THAN, Quantity.of(VALUE)));
        }

        @Test
        void fromJson_Comparator_UnitWithoutSystem() throws JsonProcessingException {
            var result = parse("""
                    {
                        "type": "quantity-comparator",
                        "comparator": "gt",
                        "value": 100,
                        "unit": {
                            "code": "km/h",
                            "display": "km/h"
                        }
                    }
                    """);

            assertThat(result).isEqualTo(QuantityComparatorFilterPart.of(GREATER_THAN, Quantity.of(VALUE, UCUM_UNIT)));
        }
    }

    @Nested
    class QuantityRange {

        @Test
        void fromJson() throws JsonProcessingException {
            var result = parse("""
                    {
                        "type": "quantity-range",
                        "minValue": 50,
                        "maxValue": 100,
                        "unit": {
                            "code": "km/h",
                            "display": "km/h",
                            "system": "sys-155523"
                        }
                    }
                    """);

            assertThat(result).isEqualTo(QuantityRangeFilterPart.of(Quantity.of(MIN_VALUE, UNIT),
                    Quantity.of(MAX_VALUE, UNIT)));
        }

        @Test
        void fromJson_UnitWithoutSystem() throws JsonProcessingException {
            var result = parse("""
                    {
                        "type": "quantity-range",
                        "minValue": 50,
                        "maxValue": 100,
                        "unit": {
                            "code": "km/h",
                            "display": "km/h"
                        }
                    }
                    """);

            assertThat(result).isEqualTo(QuantityRangeFilterPart.of(Quantity.of(MIN_VALUE, UCUM_UNIT),
                    Quantity.of(MAX_VALUE, UCUM_UNIT)));
        }

        @Test
        void fromJson_withoutUnit() throws JsonProcessingException {
            var result = parse("""
                    {
                        "type": "quantity-range",
                        "minValue": 50,
                        "maxValue": 100
                    }
                    """);

            assertThat(result).isEqualTo(QuantityRangeFilterPart.of(Quantity.of(MIN_VALUE), Quantity.of(MAX_VALUE)));
        }
    }

    @Test
    void fromJson_referenceWithEmptyCriteria() {
        assertThatThrownBy(() -> parse("""
                {
                    "type": "reference",
                    "criteria": []
                }
                """))
                .hasMessage("empty criteria");
    }

    @Test
    void fromJson_unknownType() {
        assertThatThrownBy(() -> parse("""
                {
                    "type": "foobar"
                }
                """))
                .hasMessage("unknown filterPart type: foobar");
    }

    static FilterPart parse(String s) throws JsonProcessingException {
        return FilterPart.fromJsonNode(new ObjectMapper().readTree(s));
    }
}
