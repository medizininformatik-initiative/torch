package de.medizininformatikinitiative.torch.model;

import de.medizininformatikinitiative.torch.model.management.ResourceGroupWrapper;
import org.hl7.fhir.r4.model.Patient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ResourceGroupRelationWrapperTest {

    Patient patient1 = new Patient();
    Patient patient2 = new Patient();
    Patient patient3 = new Patient();
    private ResourceGroupWrapper wrapper1;

    private ResourceGroupWrapper wrapper1Mod;
    private Set<String> attributeGroups1;
    private Set<String> attributeGroups2;


    @BeforeEach
    void setUp() {
        patient1.setId("patient1");
        patient2.setId("patient2");
        patient3.setId("patient3");
        attributeGroups1 = Set.of("group1", "group2");

        attributeGroups2 = Set.of("group2");

        Set<String> attributeGroups3 = Set.of("group1");

        wrapper1 = new ResourceGroupWrapper(patient1, attributeGroups1);
        wrapper1Mod = new ResourceGroupWrapper(patient1, attributeGroups3);
    }

    @Test
    void addGroups() {
        ResourceGroupWrapper wrapper = new ResourceGroupWrapper(patient1, Set.of());
        ResourceGroupWrapper result = wrapper.addGroups(attributeGroups1);

        assertThat(result).isEqualTo(wrapper1);
    }

    @Test
    void removeGroups() {
        ResourceGroupWrapper result = wrapper1.removeGroups(attributeGroups2);
        assertThat(result).isEqualTo(wrapper1Mod);

    }

    @Test
    void resource() {
        assertThat(wrapper1.resource()).isEqualTo(patient1);
    }

    @Test
    void groupSet() {
        assertThat(wrapper1.groupSet()).isEqualTo(attributeGroups1);
    }

}
