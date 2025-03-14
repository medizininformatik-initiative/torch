package de.medizininformatikinitiative.torch.service;

import de.medizininformatikinitiative.torch.management.CompartmentManager;
import de.medizininformatikinitiative.torch.model.consent.PatientBatchWithConsent;
import de.medizininformatikinitiative.torch.model.management.ImmutableResourceBundle;
import de.medizininformatikinitiative.torch.model.management.ResourceAttribute;
import de.medizininformatikinitiative.torch.model.management.ResourceBundle;
import de.medizininformatikinitiative.torch.model.management.ResourceGroup;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class PatientBatchToCoreBundleWriter {

    private final CompartmentManager compartmentManager;


    /**
     * Processes a batch of patient bundles by extracting individual ImmutableResourceBundles
     * and merging them into a single consolidated ImmutableResourceBundle.
     * Then merges that into the coreBundle.
     *
     * @param patientBundles List of patient-specific ResourceBundles.
     */
    public void updateCore(PatientBatchWithConsent batch, ResourceBundle coreBundle) {
        coreBundle.merge(processPatientBatch(batch));
    }


    /**
     * Processes a batch of patient bundles by extracting individual ImmutableResourceBundles
     * and merging them into a single consolidated ImmutableResourceBundle.
     *
     * @param patientBundles List of patient-specific ResourceBundles.
     * @return A single merged ImmutableResourceBundle.
     */
    public ImmutableResourceBundle processPatientBatch(PatientBatchWithConsent batch) {
        List<ImmutableResourceBundle> extractedBundles = batch.bundles().values().stream().map(bundle -> new ImmutableResourceBundle(bundle.bundle())).map(this::extractRelevantPatientData) // Step 1: Extract Immutable Bundles
                .collect(Collectors.toList());

        return mergeImmutableBundles(extractedBundles); // Step 2: Merge extracted bundles
    }


    public PatientBatchToCoreBundleWriter(CompartmentManager compartmentManager) {
        this.compartmentManager = compartmentManager;
    }


    /**
     * Extracts an ImmutableResourceBundle from a single ResourceBundle.
     */
    public ImmutableResourceBundle extractRelevantPatientData(ImmutableResourceBundle patientResourceBundle) {
        // Step 1: Identify valid ResourceGroups that are NOT in the compartment
        Map<ResourceGroup, Boolean> validGroups = patientResourceBundle.resourceGroupValidity().entrySet().stream()
                .filter(entry -> entry.getValue() && !compartmentManager.isInCompartment(entry.getKey())) // Exclude patient groups
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        System.out.println("validGroups = " + validGroups);

        // Step 2: Collect attributes linked to valid parent groups
        Map<ResourceAttribute, Set<ResourceGroup>> parentMap = new HashMap<>();
        Map<ResourceAttribute, Set<ResourceGroup>> childMap = new HashMap<>();
        Map<ResourceAttribute, Boolean> attributeValidity = new HashMap<>();

        // **Collect attributes that link to any valid parent resource**
        for (Map.Entry<ResourceAttribute, Set<ResourceGroup>> entry : patientResourceBundle.resourceAttributeToParentResourceGroup().entrySet()) {
            ResourceAttribute attribute = entry.getKey();
            Set<ResourceGroup> parents = entry.getValue();


            // If any parent is valid, keep this attribute
            if (parents.stream().anyMatch(validGroups::containsKey)) {
                parentMap.put(attribute, new HashSet<>(parents));
                attributeValidity.put(attribute, true); // Mark attribute as valid
            }
        }
        System.out.println(" ParentMap = " + parentMap);

        for (Map.Entry<ResourceAttribute, Set<ResourceGroup>> entry : patientResourceBundle.resourceAttributeToChildResourceGroup().entrySet()) {
            ResourceAttribute attribute = entry.getKey();
            Set<ResourceGroup> children = entry.getValue();

            // If this attribute was already validated via parent links, add children
            if (parentMap.containsKey(attribute)) {
                childMap.put(attribute, new HashSet<>(children));

                // Mark child resource groups as valid if they weren't already
                for (ResourceGroup child : children) {
                    validGroups.putIfAbsent(child, true);
                }
            }
        }

        // Step 3: Build the resource group to attributes mapping
        Map<ResourceGroup, Set<ResourceAttribute>> parentResourceGroupToAttributesMap = new HashMap<>();
        Map<ResourceGroup, Set<ResourceAttribute>> childResourceGroupToAttributesMap = new HashMap<>();

        parentMap.forEach((attribute, parents) -> {
            for (ResourceGroup parent : parents) {
                parentResourceGroupToAttributesMap.computeIfAbsent(parent, k -> new HashSet<>()).add(attribute);
            }
        });

        childMap.forEach((attribute, children) -> {
            for (ResourceGroup child : children) {
                childResourceGroupToAttributesMap.computeIfAbsent(child, k -> new HashSet<>()).add(attribute);
            }
        });

        return new ImmutableResourceBundle(parentMap, childMap, validGroups, attributeValidity, parentResourceGroupToAttributesMap, childResourceGroupToAttributesMap);
    }


    /**
     * Merges multiple ImmutableResourceBundles into a single ImmutableResourceBundle.
     *
     * @param bundles List of ImmutableResourceBundles.
     * @return A merged ImmutableResourceBundle.
     */
    public ImmutableResourceBundle mergeImmutableBundles(List<ImmutableResourceBundle> bundles) {
        Map<ResourceAttribute, Set<ResourceGroup>> mergedParentMap = new ConcurrentHashMap<>();
        Map<ResourceAttribute, Set<ResourceGroup>> mergedChildMap = new ConcurrentHashMap<>();
        Map<ResourceGroup, Boolean> mergedValidGroups = new ConcurrentHashMap<>();
        Map<ResourceAttribute, Boolean> mergedAttributeValidity = new ConcurrentHashMap<>();
        Map<ResourceGroup, Set<ResourceAttribute>> mergedParentGroupToAttributes = new ConcurrentHashMap<>();
        Map<ResourceGroup, Set<ResourceAttribute>> mergedChildGroupToAttributes = new ConcurrentHashMap<>();

        for (ImmutableResourceBundle bundle : bundles) {
            // Merge parent relationships
            bundle.resourceAttributeToParentResourceGroup().forEach((attribute, parents) -> mergedParentMap.computeIfAbsent(attribute, k -> ConcurrentHashMap.newKeySet()).addAll(parents));

            // Merge child relationships
            bundle.resourceAttributeToChildResourceGroup().forEach((attribute, children) -> mergedChildMap.computeIfAbsent(attribute, k -> ConcurrentHashMap.newKeySet()).addAll(children));

            // Merge valid resource groups
            mergedValidGroups.putAll(bundle.resourceGroupValidity());

            // Merge attribute validity
            bundle.resourceAttributeValidity().forEach((attribute, isValid) -> mergedAttributeValidity.putIfAbsent(attribute, isValid));

            // Merge parent group to attributes
            bundle.parentResourceGroupToResourceAttributesMap().forEach((resourceGroup, attributes) -> mergedParentGroupToAttributes.computeIfAbsent(resourceGroup, k -> ConcurrentHashMap.newKeySet()).addAll(attributes));

            // Merge child group to attributes
            bundle.childResourceGroupToResourceAttributesMap().forEach((resourceGroup, attributes) -> mergedChildGroupToAttributes.computeIfAbsent(resourceGroup, k -> ConcurrentHashMap.newKeySet()).addAll(attributes));
        }

        return new ImmutableResourceBundle(mergedParentMap, mergedChildMap, mergedValidGroups, mergedAttributeValidity, mergedParentGroupToAttributes, mergedChildGroupToAttributes);
    }
}



