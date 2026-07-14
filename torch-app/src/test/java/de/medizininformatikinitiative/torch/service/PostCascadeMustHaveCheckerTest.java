package de.medizininformatikinitiative.torch.service;

import de.medizininformatikinitiative.torch.exceptions.MustHaveViolatedException;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttributeGroup;
import de.medizininformatikinitiative.torch.model.extraction.ExtractionId;
import de.medizininformatikinitiative.torch.model.management.ResourceBundle;
import de.medizininformatikinitiative.torch.model.management.ResourceGroup;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostCascadeMustHaveCheckerTest {

    private final PostCascadeMustHaveChecker checker = new PostCascadeMustHaveChecker();

    @Mock
    AnnotatedAttributeGroup requiredA;

    @Mock
    AnnotatedAttributeGroup requiredB;

    @Mock
    AnnotatedAttributeGroup optionalC;

    @Test
    void validate_whenAllRequiredGroupsSurvive_returnsBundle() throws Exception {
        ResourceBundle bundle = new ResourceBundle();
        bundle.addResourceGroupValidity(new ResourceGroup(new ExtractionId("Observation", "1"), "group-a"), true);
        bundle.addResourceGroupValidity(new ResourceGroup(new ExtractionId("Condition", "2"), "group-b"), true);

        when(requiredA.hasMustHave()).thenReturn(true);
        when(requiredA.id()).thenReturn("group-a");
        when(requiredB.hasMustHave()).thenReturn(true);
        when(requiredB.id()).thenReturn("group-b");

        ResourceBundle result = checker.validate(bundle, List.of(requiredA, requiredB));

        assertThat(result).isSameAs(bundle);
    }

    @Test
    void validate_whenRequiredGroupMissing_throws() {
        ResourceBundle bundle = new ResourceBundle();
        bundle.addResourceGroupValidity(new ResourceGroup(new ExtractionId("Observation", "1"), "group-a"), true);

        when(requiredA.hasMustHave()).thenReturn(true);
        when(requiredA.id()).thenReturn("group-a");
        when(requiredB.hasMustHave()).thenReturn(true);
        when(requiredB.id()).thenReturn("group-b");

        assertThatThrownBy(() -> checker.validate(bundle, List.of(requiredA, requiredB)))
                .isInstanceOf(MustHaveViolatedException.class)
                .hasMessageContaining("group-b");
    }

    @Test
    void validate_whenNoRequiredDirectGroups_returnsBundle() throws Exception {
        ResourceBundle bundle = new ResourceBundle();

        when(optionalC.hasMustHave()).thenReturn(false);

        ResourceBundle result = checker.validate(bundle, List.of(optionalC));

        assertThat(result).isSameAs(bundle);
    }
}
