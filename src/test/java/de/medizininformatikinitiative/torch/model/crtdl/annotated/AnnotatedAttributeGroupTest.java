package de.medizininformatikinitiative.torch.model.crtdl.annotated;

import de.medizininformatikinitiative.torch.model.crtdl.Code;
import de.medizininformatikinitiative.torch.model.crtdl.Filter;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnnotatedAttributeGroupTest {
    static final LocalDate DATE_START = LocalDate.parse("2023-01-01");
    static final LocalDate DATE_END = LocalDate.parse("2023-12-31");
    static final Code CODE1 = new Code("system1", "code1");
    static final Code CODE2 = new Code("system2", "code2");

    @Nested
    class Queries {

        @Mock
        DseMappingTreeBase mappingTreeBase;

        @Test
        void oneCode() {
            when(mappingTreeBase.expand("system1", "code1")).thenReturn(Stream.of("code1"));
            var tokenFilter = new Filter("token", "code", List.of(CODE1));
            var attributeGroup = new AnnotatedAttributeGroup("test", "groupRef", List.of(new AnnotatedAttribute("Observation.name", "Observation.name", "Observation.name", false)), List.of(tokenFilter), null);

            var result = attributeGroup.queries(mappingTreeBase, "Observation");

            assertThat(result).containsExactly(
                    new Query("Observation", QueryParams.of("code", codeValue(CODE1)).appendParam("_profile", stringValue("groupRef")))
            );
        }

        @Test
        void twoCodes() {
            when(mappingTreeBase.expand("system1", "code1")).thenReturn(Stream.of("code1"));
            when(mappingTreeBase.expand("system2", "code2")).thenReturn(Stream.of("code2"));
            var tokenFilter = new Filter("token", "code", List.of(CODE1, CODE2));
            var attributeGroup = new AnnotatedAttributeGroup("test", "groupRef", List.of(new AnnotatedAttribute("Observation.name", "Observation.name", "Observation.name", false)), List.of(tokenFilter), null);

            var result = attributeGroup.queries(mappingTreeBase, "Observation");

            assertThat(result).containsExactly(
                    new Query("Observation", QueryParams.of("code", codeValue(CODE1)).appendParam("_profile", stringValue("groupRef"))),
                    new Query("Observation", QueryParams.of("code", codeValue(CODE2)).appendParam("_profile", stringValue("groupRef")))
            );
        }

        @Test
        void dateFilter() {
            var dateFilter = new Filter("date", "date", DATE_START, DATE_END);
            var attributeGroup = new AnnotatedAttributeGroup("test", "groupRef", List.of(new AnnotatedAttribute("Observation.name", "Observation.name", "Observation.name", false)), List.of(dateFilter), null);

            var result = attributeGroup.queries(mappingTreeBase, "Observation");

            assertThat(result).containsExactly(
                    new Query("Observation", QueryParams.of("date", dateValue(GREATER_EQUAL, DATE_START)).appendParam("date", dateValue(LESS_EQUAL, DATE_END)).appendParam("_profile", stringValue("groupRef")))
            );
        }

        @Test
        void filtersIgnoredForPatient() {
            var dateFilter = new Filter("date", "date", DATE_START, DATE_END);
            var attributeGroup = new AnnotatedAttributeGroup("test", "groupRef", List.of(new AnnotatedAttribute("Patient.name", "Patient.name", "Patient.name", false)), List.of(dateFilter), null);

            var result = attributeGroup.queries(mappingTreeBase, "Patient");

            assertThat(result).containsExactly(
            );
        }
    }

}