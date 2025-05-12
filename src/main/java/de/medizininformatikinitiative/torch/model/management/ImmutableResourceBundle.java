package de.medizininformatikinitiative.torch.model.management;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reference Parent-Child relation
 * ResourceGroup<->ResourceAttribute<->ResourceGroup
 */
public record ImmutableResourceBundle(
        Map<ResourceAttribute, Set<ResourceGroup>> resourceAttributeToParentResourceGroup,
        Map<ResourceAttribute, Set<ResourceGroup>> resourceAttributeToChildResourceGroup,
        Map<ResourceGroup, Boolean> resourceGroupValidity,
        Map<ResourceAttribute, Boolean> resourceAttributeValidity,
        Map<ResourceGroup, Set<ResourceAttribute>> parentResourceGroupToResourceAttributesMap,
        Map<ResourceGroup, Set<ResourceAttribute>> childResourceGroupToResourceAttributesMap
) {
    /**
     * Creates an immutable version of a given ResourceBundle.
     */
    public ImmutableResourceBundle(ResourceBundle bundle) {
        this(
                Map.copyOf(bundle.resourceAttributeToParentResourceGroup()),
                Map.copyOf(bundle.resourceAttributeToChildResourceGroup()),
                Map.copyOf(bundle.resourceGroupValidity()),
                Map.copyOf(bundle.resourceAttributeValidity()),
                Map.copyOf(bundle.parentResourceGroupToResourceAttributesMap()),
                Map.copyOf(bundle.childResourceGroupToResourceAttributesMap())
        );
    }

    /**
     * Converts this immutable bundle back into a mutable ResourceBundle.
     * A new mutable cache is created, ensuring no shared mutations.
     */
    public ResourceBundle toMutable() {
        return new ResourceBundle(
                new ConcurrentHashMap<>(resourceAttributeToParentResourceGroup),
                new ConcurrentHashMap<>(resourceAttributeToChildResourceGroup),
                new ConcurrentHashMap<>(resourceGroupValidity),
                new ConcurrentHashMap<>(resourceAttributeValidity),
                new ConcurrentHashMap<>(parentResourceGroupToResourceAttributesMap),
                new ConcurrentHashMap<>(childResourceGroupToResourceAttributesMap),
                new ConcurrentHashMap<>() // Fresh mutable cache
        );
    }
}
