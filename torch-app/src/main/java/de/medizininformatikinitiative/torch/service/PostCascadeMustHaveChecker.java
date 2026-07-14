package de.medizininformatikinitiative.torch.service;

import de.medizininformatikinitiative.torch.exceptions.MustHaveViolatedException;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttributeGroup;
import de.medizininformatikinitiative.torch.model.management.ResourceBundle;
import de.medizininformatikinitiative.torch.model.management.ResourceGroup;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class PostCascadeMustHaveChecker {

    public ResourceBundle validate(
            ResourceBundle bundle,
            Collection<AnnotatedAttributeGroup> directGroups
    ) throws MustHaveViolatedException {
        Set<String> requiredGroupIds = directGroups.stream()
                .filter(AnnotatedAttributeGroup::hasMustHave)
                .map(AnnotatedAttributeGroup::id)
                .collect(Collectors.toSet());

        if (requiredGroupIds.isEmpty()) {
            return bundle;
        }

        Set<String> survivingGroupIds = bundle.getValidResourceGroups().stream()
                .map(ResourceGroup::groupId)
                .collect(Collectors.toSet());

        Set<String> missing = requiredGroupIds.stream()
                .filter(groupId -> !survivingGroupIds.contains(groupId))
                .collect(Collectors.toSet());

        if (!missing.isEmpty()) {
            throw new MustHaveViolatedException(
                    "Required direct groups missing after cascading delete: " + missing
            );
        }

        return bundle;
    }
}
