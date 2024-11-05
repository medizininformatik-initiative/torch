package de.medizininformatikinitiative.torch.model.crtdl;

import de.medizininformatikinitiative.torch.model.fhir.Query;
import de.medizininformatikinitiative.torch.model.fhir.QueryParams;
import de.medizininformatikinitiative.torch.model.mapping.DseMappingTreeBase;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;

import static de.medizininformatikinitiative.torch.model.fhir.QueryParams.*;
import static de.medizininformatikinitiative.torch.model.sq.Comparator.GREATER_EQUAL;
import static de.medizininformatikinitiative.torch.model.sq.Comparator.LESS_EQUAL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AttributeGroupTest {

    static final LocalDate DATE_START = LocalDate.parse("2023-01-01");
    static final LocalDate DATE_END = LocalDate.parse("2023-12-31");
    static final Code CODE1 = new Code("system1", "code1");
    static final Code CODE2 = new Code("system2", "code2");

    @Test
    void testDuplicateDateFiltersThrowsException() {
        var dateFilter1 = new Filter("date", "dateField1", DATE_START, DATE_END);
        var dateFilter2 = new Filter("date", "dateField2", DATE_START, DATE_END);

        assertThatThrownBy(() -> new AttributeGroup("groupRef", List.of(), List.of(dateFilter1, dateFilter2)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Duplicate date type filter found");
    }

    @Nested
    class Queries {

        @Mock
        DseMappingTreeBase mappingTreeBase;

        @Test
        void oneCode() {
            when(mappingTreeBase.expand("system1", "code1")).thenReturn(Stream.of("code1"));
            var tokenFilter = new Filter("token", "code", List.of(CODE1));
            var attributeGroup = new AttributeGroup("groupRef", List.of(new Attribute("Patient.name", false)), List.of(tokenFilter));

            var result = attributeGroup.queries(mappingTreeBase);

            assertThat(result).containsExactly(
                    new Query("Patient", QueryParams.of("code", codeValue(CODE1)).appendParam("_profile", stringValue("groupRef")))
            );
        }

        @Test
        void twoCodes() {
            when(mappingTreeBase.expand("system1", "code1")).thenReturn(Stream.of("code1"));
            when(mappingTreeBase.expand("system2", "code2")).thenReturn(Stream.of("code2"));
            var tokenFilter = new Filter("token", "code", List.of(CODE1, CODE2));
            var attributeGroup = new AttributeGroup("groupRef", List.of(new Attribute("Patient.name", false)), List.of(tokenFilter));

            var result = attributeGroup.queries(mappingTreeBase);

            assertThat(result).containsExactly(
                    new Query("Patient", QueryParams.of("code", codeValue(CODE1)).appendParam("_profile", stringValue("groupRef"))),
                    new Query("Patient", QueryParams.of("code", codeValue(CODE2)).appendParam("_profile", stringValue("groupRef")))
            );
        }

        @Test
        void dateFilter() {
            var dateFilter = new Filter("date", "date", DATE_START, DATE_END);
            var attributeGroup = new AttributeGroup("groupRef", List.of(new Attribute("Patient.name", false)), List.of(dateFilter));

            var result = attributeGroup.queries(mappingTreeBase);

            assertThat(result).containsExactly(
                    new Query("Patient", QueryParams.of("date", dateValue(GREATER_EQUAL, DATE_START)).appendParam("date", dateValue(LESS_EQUAL, DATE_END)).appendParam("_profile", stringValue("groupRef")))
            );
        }
    }

    @Nested
    class HasMustHave {

        @Test
        void testTrue() {
            var attributeGroup = new AttributeGroup("groupRef", List.of(new Attribute("Patient.name", true)), List.of());

            assertThat(attributeGroup.hasMustHave()).isTrue();
        }

        @Test
        void testFalse() {
            var attributeGroup = new AttributeGroup("groupRef", List.of(new Attribute("Patient.name", false)), List.of());

            assertThat(attributeGroup.hasMustHave()).isFalse();
        }
    }
}
