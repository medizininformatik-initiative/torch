package de.medizininformatikinitiative.torch.model.crtdl.annotated;

import de.medizininformatikinitiative.torch.model.crtdl.Code;
import de.medizininformatikinitiative.torch.model.crtdl.FieldCondition;
import de.medizininformatikinitiative.torch.model.crtdl.Filter;
import de.medizininformatikinitiative.torch.model.fhir.Query;
import de.medizininformatikinitiative.torch.model.fhir.QueryParams;
import de.medizininformatikinitiative.torch.model.management.CopyTreeNode;
import de.medizininformatikinitiative.torch.model.mapping.DseMappingTreeBase;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;

import static de.medizininformatikinitiative.torch.model.fhir.QueryParams.codeValue;
import static de.medizininformatikinitiative.torch.model.fhir.QueryParams.dateValue;
import static de.medizininformatikinitiative.torch.model.fhir.QueryParams.stringValue;
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
            var attributeGroup = new AnnotatedAttributeGroup(
                    "test", "Observation", "groupRef", List.of(), List.of(tokenFilter));

            var result = attributeGroup.queries(mappingTreeBase, "Observation");

            assertThat(result).containsExactly(
                    Query.of("Observation",
                            QueryParams.of("code", codeValue(CODE1))
                                    .appendParam("_profile:below", stringValue("groupRef")))
            );
        }

        @Test
        void twoCodes() {
            when(mappingTreeBase.expand("system1", "code1")).thenReturn(Stream.of("code1"));
            when(mappingTreeBase.expand("system2", "code2")).thenReturn(Stream.of("code2"));
            var tokenFilter = new Filter("token", "code", List.of(CODE1, CODE2));
            var attributeGroup = new AnnotatedAttributeGroup(
                    "test", "Observation", "groupRef", List.of(), List.of(tokenFilter));

            var result = attributeGroup.queries(mappingTreeBase, "Observation");

            assertThat(result).containsExactly(
                    Query.of("Observation",
                            QueryParams.of("code", codeValue(CODE1))
                                    .appendParam("_profile:below", stringValue("groupRef"))),
                    Query.of("Observation",
                            QueryParams.of("code", codeValue(CODE2))
                                    .appendParam("_profile:below", stringValue("groupRef")))
            );
        }

        @Test
        void dateFilter() {
            var dateFilter = new Filter("date", "date", DATE_START, DATE_END);
            var attributeGroup = new AnnotatedAttributeGroup(
                    "test", "Observation", "groupRef", List.of(), List.of(dateFilter));

            var result = attributeGroup.queries(mappingTreeBase, "Observation");

            assertThat(result).containsExactly(
                    Query.of("Observation",
                            QueryParams.of("date", dateValue(GREATER_EQUAL, DATE_START))
                                    .appendParam("date", dateValue(LESS_EQUAL, DATE_END))
                                    .appendParam("_profile:below", stringValue("groupRef")))
            );
        }

        @Test
        void filtersIgnoredForPatient() {
            var dateFilter = new Filter("date", "date", DATE_START, DATE_END);
            var attributeGroup = new AnnotatedAttributeGroup(
                    "test", "Observation", "groupRef", List.of(), List.of(dateFilter));

            var result = attributeGroup.queries(mappingTreeBase, "Patient");

            assertThat(result).containsExactly(Query.ofType("Patient"));
        }

        @Test
        void buildTree() {
            List<AnnotatedAttribute> attrs = List.of(
                    new AnnotatedAttribute("", "Patient.identifier.system", false),
                    new AnnotatedAttribute("", "Patient.identifier.where(type='official').value", true)
            );

            CopyTreeNode root = AnnotatedAttributeGroup.buildTree(attrs, "Patient");

            assertThat(root.fhirPath()).isEqualTo("Patient");
            assertThat(root.getChild(new FieldCondition("identifier", "")).get().children())
                    .isEqualTo(List.of(new CopyTreeNode("system")));
            assertThat(root.getChild(new FieldCondition("identifier", ".where(type='official')")).get().children())
                    .isEqualTo(List.of(new CopyTreeNode("value")));
        }

        @Test
        void addAttributes_newAttributeIsAppended() {
            var group = new AnnotatedAttributeGroup("g", "Observation", "ref",
                    List.of(new AnnotatedAttribute("Observation.id", "Observation.id", false)),
                    List.of());

            var result = group.addAttributes(List.of(
                    new AnnotatedAttribute("Observation.status", "Observation.status", false)));

            assertThat(result.attributes()).containsExactly(
                    new AnnotatedAttribute("Observation.id", "Observation.id", false),
                    new AnnotatedAttribute("Observation.status", "Observation.status", false));
        }

        @Test
        void addAttributes_sameAttributeRefWithoutLinkedGroupsMergesWithMustHaveTrue() {
            var group = new AnnotatedAttributeGroup("g", "Observation", "ref",
                    List.of(new AnnotatedAttribute("Observation.subject", "Observation.subject", false, List.of())),
                    List.of());

            var result = group.addAttributes(List.of(
                    new AnnotatedAttribute("Observation.subject", "Observation.subject", true, List.of())));

            assertThat(result.attributes()).containsExactly(
                    new AnnotatedAttribute("Observation.subject", "Observation.subject", true, List.of()));
        }

        @Test
        void addAttributes_sameAttributeRefWithSameLinkedGroupsMergesWithMustHaveTrue() {
            var group = new AnnotatedAttributeGroup("g", "Observation", "ref",
                    List.of(new AnnotatedAttribute("Observation.subject", "Observation.subject", false, List.of("groupA"))),
                    List.of());

            var result = group.addAttributes(List.of(
                    new AnnotatedAttribute("Observation.subject", "Observation.subject", true, List.of("groupA"))));

            assertThat(result.attributes()).containsExactly(
                    new AnnotatedAttribute("Observation.subject", "Observation.subject", true, List.of("groupA")));
        }

        @Test
        void addAttributes_sameAttributeRefWithDifferentLinkedGroupsKeepsBothEntries() {
            var group = new AnnotatedAttributeGroup("g", "Observation", "ref",
                    List.of(new AnnotatedAttribute("Observation.subject", "Observation.subject", false, List.of("groupA"))),
                    List.of());

            var result = group.addAttributes(List.of(
                    new AnnotatedAttribute("Observation.subject", "Observation.subject", false, List.of("groupB"))));

            assertThat(result.attributes()).containsExactly(
                    new AnnotatedAttribute("Observation.subject", "Observation.subject", false, List.of("groupA")),
                    new AnnotatedAttribute("Observation.subject", "Observation.subject", false, List.of("groupB")));
        }

        @Test
        void addAttributes_sameAttributeRefWithoutAndWithLinkedGroupsKeepsBothEntriesAndMustHaveSeparate() {
            var group = new AnnotatedAttributeGroup("g", "Observation", "ref",
                    List.of(new AnnotatedAttribute("Observation.subject", "Observation.subject", true, List.of())),
                    List.of());

            var result = group.addAttributes(List.of(
                    new AnnotatedAttribute("Observation.subject", "Observation.subject", false, List.of("groupA"))));

            assertThat(result.attributes()).containsExactly(
                    new AnnotatedAttribute("Observation.subject", "Observation.subject", true, List.of()),
                    new AnnotatedAttribute("Observation.subject", "Observation.subject", false, List.of("groupA")));
        }

        @Test
        void addAttributes_sameAttributeRefWithOverlappingButDifferentLinkedGroupsKeepsBothEntries() {
            var group = new AnnotatedAttributeGroup("g", "Observation", "ref",
                    List.of(new AnnotatedAttribute("Observation.subject", "Observation.subject", true,
                            List.of("groupA", "groupB", "groupC"))),
                    List.of());

            var result = group.addAttributes(List.of(
                    new AnnotatedAttribute("Observation.subject", "Observation.subject", false,
                            List.of("groupB", "groupC"))));

            assertThat(result.attributes()).containsExactly(
                    new AnnotatedAttribute("Observation.subject", "Observation.subject", true,
                            List.of("groupA", "groupB", "groupC")),
                    new AnnotatedAttribute("Observation.subject", "Observation.subject", false,
                            List.of("groupB", "groupC")));
        }

        @Test
        void addAttributes_sameAttributeRefWithSameLinkedGroupsInDifferentOrderMergesWithoutUnion() {
            var group = new AnnotatedAttributeGroup("g", "Observation", "ref",
                    List.of(new AnnotatedAttribute("Observation.subject", "Observation.subject", false,
                            List.of("groupA", "groupB"))),
                    List.of());

            var result = group.addAttributes(List.of(
                    new AnnotatedAttribute("Observation.subject", "Observation.subject", true,
                            List.of("groupB", "groupA"))));

            assertThat(result.attributes()).containsExactly(
                    new AnnotatedAttribute("Observation.subject", "Observation.subject", true,
                            List.of("groupA", "groupB")));
        }

        @Test
        void buildTree_encounterTypeKontaktebene() {
            var condition = ".where($this.coding.system='http://fhir.de/CodeSystem/Kontaktebene')";
            var attrs = List.of(
                    new AnnotatedAttribute(
                            "Encounter.type:Kontaktebene",
                            "Encounter.type.where($this.coding.system='http://fhir.de/CodeSystem/Kontaktebene').coding.code",
                            false)
            );

            var root = AnnotatedAttributeGroup.buildTree(attrs, "Encounter");

            var typeNode = root.getChild(new FieldCondition("type", condition)).orElseThrow();
            assertThat(typeNode.fieldCondition().condition()).isEqualTo(condition);
        }
    }
}
