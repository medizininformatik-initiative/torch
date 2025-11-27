package de.medizininformatikinitiative.torch.model.extraction;

import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttribute;
import de.medizininformatikinitiative.torch.model.management.ResourceAttribute;
import de.medizininformatikinitiative.torch.model.management.ResourceBundle;
import de.medizininformatikinitiative.torch.model.management.ResourceGroup;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

class ResourceExtractionInfoTest {

    /**
     * Creates a minimal ResourceBundle where we can control all 7 maps explicitly.
     */
    private ResourceBundle bundle(
            Map<ResourceAttribute, Set<ResourceGroup>> parent,
            Map<ResourceAttribute, Set<ResourceGroup>> child,
            Map<ResourceGroup, Boolean> groupValidity,
            Map<ResourceAttribute, Boolean> attributeValidity
    ) {
        return new ResourceBundle(
                new ConcurrentHashMap<>(parent),
                new ConcurrentHashMap<>(child),
                new ConcurrentHashMap<>(groupValidity),
                new ConcurrentHashMap<>(attributeValidity),
                new ConcurrentHashMap<>(),
                new ConcurrentHashMap<>(),
                new ConcurrentHashMap<>()
        );
    }

    @Test
    void collectValidGroups_shouldReturnOnlyValidGroupsForResource() {
        String resourceId = "Patient/1";

        ResourceGroup g1 = new ResourceGroup(resourceId, "A");
        ResourceGroup g2 = new ResourceGroup(resourceId, "B");
        ResourceGroup g3 = new ResourceGroup("Other", "X");

        Map<ResourceGroup, Boolean> validity = Map.of(
                g1, true,
                g2, false,
                g3, true
        );

        ResourceBundle bundle = bundle(Map.of(), Map.of(), validity, Map.of());

        Set<String> result =
                ResourceExtractionInfo.of(bundle, resourceId).groups();

        assertThat(result)
                .containsExactly("A")               // only g1 is valid + matches resourceId
                .doesNotContain("B")
                .doesNotContain("X");
    }

    @Test
    void computeAttributeReferenceMap_shouldReturnValidReferencesOnly() {
        String resourceId = "Observation/123";

        // Create attributes
        ResourceAttribute attrA = new ResourceAttribute(resourceId, new AnnotatedAttribute("code", "p1", false));
        ResourceAttribute attrB = new ResourceAttribute(resourceId, new AnnotatedAttribute("subject", "p2", false));

        ResourceGroup rg1 = new ResourceGroup("Patient/9", "G1");
        ResourceGroup rg2 = new ResourceGroup("Patient/10", "G2");
        ResourceGroup rgInvalid = new ResourceGroup("Patient/99", "GX");

        Map<ResourceAttribute, Set<ResourceGroup>> child = Map.of(
                attrA, Set.of(rg1, rgInvalid),
                attrB, Set.of(rg2)
        );

        Map<ResourceGroup, Boolean> validity = Map.of(
                rg1, true,
                rg2, true,
                rgInvalid, false
        );

        Map<ResourceAttribute, Boolean> attrValidity = Map.of(
                attrA, true,
                attrB, true
        );

        ResourceBundle bundle = bundle(Map.of(), child, validity, attrValidity);

        Map<String, Set<String>> result =
                ResourceExtractionInfo.computeAttributeReferenceMap(bundle, resourceId);

        assertThat(result)
                .containsOnlyKeys("code", "subject");

        assertThat(result.get("code"))
                .containsExactly("Patient/9")              // valid only
                .doesNotContain("Patient/99");            // invalid → excluded

        assertThat(result.get("subject"))
                .containsExactly("Patient/10");
    }

    @Test
    void of_shouldCombineGroupsAndAttributeMapping() {
        String resourceId = "Condition/11";

        ResourceAttribute attr = new ResourceAttribute(resourceId,
                new AnnotatedAttribute("severity", "f1", false));

        ResourceGroup group = new ResourceGroup(resourceId, "GRP");
        ResourceGroup ref = new ResourceGroup("Patient/18", "GRPX");
        Map<ResourceAttribute, Boolean> attrValidity = Map.of(attr, true);

        Map<ResourceAttribute, Set<ResourceGroup>> child = Map.of(
                attr, Set.of(ref)
        );

        Map<ResourceGroup, Boolean> validityWithRef = new HashMap<>();
        validityWithRef.put(group, true);
        validityWithRef.put(ref, true);

        ResourceBundle bundle = bundle(
                Map.of(),
                child,
                validityWithRef,
                attrValidity
        );

        ResourceExtractionInfo info = ResourceExtractionInfo.of(bundle, resourceId);

        assertThat(info.groups())
                .containsExactly("GRP");

        assertThat(info.attributeToReferences())
                .containsOnlyKeys("severity");

        assertThat(info.attributeToReferences().get("severity"))
                .containsExactly("Patient/18");
    }

    @Test
    void merge_nullOther_shouldReturnThis() {
        ResourceExtractionInfo left = new ResourceExtractionInfo(
                Set.of("G1"),
                Map.of("code", Set.of("P1"))
        );

        assertThat(left.merge(null)).isSameAs(left);
    }

    @Test
    void merge_shouldUnionGroupsAndDeepMergeAttributes() {
        ResourceExtractionInfo left = new ResourceExtractionInfo(
                Set.of("G1", "G2"),
                Map.of(
                        "code", Set.of("Patient/1", "Patient/2"),
                        "subject", Set.of("Patient/3")
                )
        );

        ResourceExtractionInfo right = new ResourceExtractionInfo(
                Set.of("G2", "G3"),
                Map.of(
                        "code", Set.of("Patient/99"),
                        "author", Set.of("Practitioner/7")
                )
        );

        ResourceExtractionInfo merged = left.merge(right);

        ResourceExtractionInfo expected = new ResourceExtractionInfo(
                Set.of("G1", "G2", "G3"),
                Map.of(
                        "code", Set.of("Patient/1", "Patient/2", "Patient/99"),
                        "subject", Set.of("Patient/3"),
                        "author", Set.of("Practitioner/7")
                )
        );

        // ✔ one clean assertion for the entire object
        assertThat(merged)
                .usingRecursiveComparison()
                .ignoringCollectionOrder()   // sets → ignore order
                .isEqualTo(expected);
    }

}

