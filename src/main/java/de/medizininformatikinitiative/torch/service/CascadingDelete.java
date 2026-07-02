package de.medizininformatikinitiative.torch.service;

import de.medizininformatikinitiative.torch.model.consent.PatientBatchWithConsent;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttributeGroup;
import de.medizininformatikinitiative.torch.model.management.ResourceAttribute;
import de.medizininformatikinitiative.torch.model.management.ResourceBundle;
import de.medizininformatikinitiative.torch.model.management.ResourceGroup;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Propagates the invalidation of {@link ResourceGroup}s through a {@link ResourceBundle}'s reference
 * graph.
 *
 * <p>{@link #handleBundle} runs in two phases. First, {@link #handleChildren} and {@link #handleParents}
 * are applied breadth-first starting from the initially invalid groups: an attribute is invalidated once
 * its last remaining reference disappears, a referenceOnly group is invalidated once its last remaining
 * parent attribute disappears, and a must-have attribute losing its last reference escalates the
 * invalidation to its parent groups. This phase is a greatest-fixpoint computation: it only invalidates a
 * group once it can prove that group has zero remaining live references, so a pair of referenceOnly groups
 * that reference each other can keep one another alive even after their only external anchor is gone,
 * since neither individually ever reaches zero. {@link #sweepUnfoundedCycles} then runs a mark-and-sweep
 * pass to catch exactly that case, invalidating any referenceOnly group left standing that is not actually
 * reachable from a directly loaded group.
 */
@Component
public class CascadingDelete {

    PatientBatchWithConsent handlePatientBatch(PatientBatchWithConsent patientBatch, Map<String, AnnotatedAttributeGroup> groupMap) {
        patientBatch.bundles().values().parallelStream().forEach(bundle -> handleBundle(bundle.bundle(), groupMap));
        return patientBatch;
    }

    /**
     * Invalidates the bundle's initially invalid {@link ResourceGroup}s and propagates that invalidation
     * through the reference graph, then removes any referenceOnly reference cycle left behind with no live
     * anchor. See the class Javadoc for the two-phase algorithm.
     */
    void handleBundle(ResourceBundle resourceBundle, Map<String, AnnotatedAttributeGroup> groupMap) {
        Set<ResourceGroup> invalidResourceGroups = resourceBundle.getInvalid().keySet();
        Queue<ResourceGroup> processingQueue = new LinkedList<>(invalidResourceGroups);
        while (!processingQueue.isEmpty()) {
            ResourceGroup invalidResourceGroup = processingQueue.poll();
            processingQueue.addAll(handleChildren(resourceBundle, groupMap, invalidResourceGroup));
            processingQueue.addAll(handleParents(resourceBundle, invalidResourceGroup));
        }
        sweepUnfoundedCycles(resourceBundle, groupMap);
    }

    /**
     * Mark-and-sweep pass catching referenceOnly {@link ResourceGroup}s that survive the main BFS only
     * because they form a mutually-supporting reference cycle with no live anchor (e.g. A references B and
     * B references A; once their only external referrer is invalidated, neither A nor B alone ever reaches
     * zero remaining parents, so {@link #handleChildren} never invalidates either). Starting from the
     * directly loaded (non-referenceOnly) groups, a forward BFS marks every group reachable through a valid
     * attribute as grounded. Any referenceOnly group left unmarked is unfounded and is invalidated, with the
     * invalidation propagated through {@link #handleParents} to catch must-have violations.
     */
    private void sweepUnfoundedCycles(ResourceBundle resourceBundle, Map<String, AnnotatedAttributeGroup> groupMap) {
        Set<ResourceGroup> validGroups = resourceBundle.getValidResourceGroups();
        Set<ResourceGroup> grounded = new LinkedHashSet<>();
        Queue<ResourceGroup> bfsQueue = new LinkedList<>();

        for (ResourceGroup rg : validGroups) {
            if (!groupMap.get(rg.groupId()).includeReferenceOnly()) {
                grounded.add(rg);
                bfsQueue.add(rg);
            }
        }

        while (!bfsQueue.isEmpty()) {
            ResourceGroup rg = bfsQueue.poll();
            for (ResourceAttribute attr : resourceBundle.parentResourceGroupToResourceAttributesMap().getOrDefault(rg, Set.of())) {
                if (!resourceBundle.resourceAttributeValid(attr)) continue;
                for (ResourceGroup target : resourceBundle.resourceAttributeToChildResourceGroup().getOrDefault(attr, Set.of())) {
                    if (grounded.add(target)) {
                        bfsQueue.add(target);
                    }
                }
            }
        }

        Queue<ResourceGroup> invalidationQueue = validGroups.stream()
                .filter(rg -> groupMap.get(rg.groupId()).includeReferenceOnly() && !grounded.contains(rg))
                .collect(Collectors.toCollection(LinkedList::new));

        invalidationQueue.forEach(rg -> resourceBundle.addResourceGroupValidity(rg, false));
        while (!invalidationQueue.isEmpty()) {
            invalidationQueue.addAll(handleParents(resourceBundle, invalidationQueue.poll()));
        }
    }

    /**
     * given a parent ResourceGroup it invalidates the connection from children down.
     *
     * @param resourceBundle coreResourcebundle to be handled
     * @param groupMap       known attributeGroups
     * @param parentRG       parentRG that triggered the deletion process.
     * @return children to be deleted
     */
    Set<ResourceGroup> handleChildren(ResourceBundle resourceBundle, Map<String, AnnotatedAttributeGroup> groupMap, ResourceGroup parentRG) {
        Set<ResourceGroup> resourceGroups = new LinkedHashSet<>();
        Set<ResourceAttribute> resourceAttributes = resourceBundle.parentResourceGroupToResourceAttributesMap().get(parentRG);

        if (resourceAttributes == null) {
            return Set.of();
        }

        for (ResourceAttribute resourceAttribute : resourceAttributes) {
            if (resourceBundle.resourceAttributeValid(resourceAttribute)) {
                if (resourceBundle.removeParentRGFromAttribute(parentRG, resourceAttribute)) {
                    resourceBundle.removeAttributefromParentRG(parentRG, resourceAttribute);
                    resourceBundle.setResourceAttributeInValid(resourceAttribute);
                    Set<ResourceGroup> childrenResourceGroups = resourceBundle.resourceAttributeToChildResourceGroup().get(resourceAttribute);

                    for (ResourceGroup group : childrenResourceGroups) {
                        resourceBundle.removeChildRGFromAttribute(group, resourceAttribute);
                        if (resourceBundle.removeParentAttributeFromChildRG(group, resourceAttribute)) {

                            AnnotatedAttributeGroup attributeGroup = groupMap.get(group.groupId());
                            if (attributeGroup.includeReferenceOnly()) {
                                resourceGroups.add(group);
                                resourceBundle.addResourceGroupValidity(group, false);
                            }
                        }
                    }
                    resourceBundle.resourceAttributeToChildResourceGroup().remove(resourceAttribute);
                }
            }
        }

        return resourceGroups;
    }

    /**
     * @param resourceBundle resourceBundle to be handled
     * @param childRG        rg whose parents have to be handled
     * @return removes the connection of a invalid ResourceGroup to its parents propagates up if must have attribute.
     */
    Set<ResourceGroup> handleParents(ResourceBundle resourceBundle, ResourceGroup childRG) {
        Set<ResourceGroup> resourceGroups = new LinkedHashSet<>();
        Set<ResourceAttribute> resourceAttributes = resourceBundle.childResourceGroupToResourceAttributesMap().getOrDefault(childRG, Set.of());

        for (ResourceAttribute resourceAttribute : resourceAttributes) {
            boolean wasLastChild = resourceBundle.removeParentAttributeFromChildRG(childRG, resourceAttribute);
            resourceBundle.removeChildRGFromAttribute(childRG, resourceAttribute);

            if (wasLastChild) {
                resourceBundle.setResourceAttributeInValid(resourceAttribute);

                // If it's a must-have attribute, escalate to all parent groups of the attribute
                if (resourceAttribute.annotatedAttribute().mustHave()) {
                    Set<ResourceGroup> parentGroups = resourceBundle.resourceAttributeToParentResourceGroup()
                            .getOrDefault(resourceAttribute, Set.of());

                    // Add all parent groups to be invalidated
                    resourceGroups.addAll(parentGroups);

                    // Ensure invalidation is applied recursively
                    for (ResourceGroup parentGroup : parentGroups) {
                        resourceBundle.removeAttributefromParentRG(parentGroup, resourceAttribute);
                        resourceBundle.addResourceGroupValidity(parentGroup, false);
                    }
                }
            }
        }

        return resourceGroups;
    }
}
