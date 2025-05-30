package de.medizininformatikinitiative.torch.util;

import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttributeGroup;
import de.medizininformatikinitiative.torch.model.management.ReferenceWrapper;
import de.medizininformatikinitiative.torch.model.management.ResourceBundle;
import de.medizininformatikinitiative.torch.model.management.ResourceGroup;
import org.hl7.fhir.r4.model.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public class ResourceGroupValidator {
    private static final Logger logger = LoggerFactory.getLogger(ResourceGroupValidator.class);

    private final ProfileMustHaveChecker profileMustHaveChecker;

    public ResourceGroupValidator(ProfileMustHaveChecker profileMustHaveChecker) {
        this.profileMustHaveChecker = profileMustHaveChecker;
    }

    /**
     * Collects all valid resourceGroups for the currently processed ResourceBundle.
     * <p> For a given reference and resource checks if already a valid group in processingBundle.
     * If resourceGroups not assigned yet, executes filter, musthave (Without References) and profile checks.
     *
     * @param groupMap         known attribute groups
     * @param resource         Resource to be checked
     * @param processingBundle bundle that is currently processed
     * @return ResourceGroup if previously unknown and assignable to the group.
     */
    public List<ResourceGroup> collectValidGroups(ReferenceWrapper referenceWrapper, Map<String, AnnotatedAttributeGroup> groupMap, Resource resource, ResourceBundle processingBundle) {
        return referenceWrapper.refAttribute().linkedGroups().stream()
                .map(groupId -> {
                    ResourceGroup resourceGroup = new ResourceGroup(ResourceUtils.getRelativeURL(resource), groupId);
                    Boolean isValid = processingBundle.isValidResourceGroup(resourceGroup);
                    if (isValid == null) {
                        AnnotatedAttributeGroup group = groupMap.get(groupId);
                        boolean fulfilled = profileMustHaveChecker.fulfilled(resource, group);
                        if (group.compiledFilter() != null) {
                            fulfilled = fulfilled && group.compiledFilter().test(resource);
                        }
                        logger.trace("Group {} for Reference: {}", groupId, fulfilled);
                        isValid = fulfilled;
                        processingBundle.addResourceGroupValidity(resourceGroup, isValid);
                    }
                    return isValid ? resourceGroup : null;
                })
                .filter(Objects::nonNull)
                .toList();
    }


}
