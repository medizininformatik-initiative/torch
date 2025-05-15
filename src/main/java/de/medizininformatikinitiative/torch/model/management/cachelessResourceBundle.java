package de.medizininformatikinitiative.torch.model.management;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reference Parent-Child relation
 * ResourceGroup<->ResourceAttribute<->ResourceGroup
 */
public record cachelessResourceBundle(
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
    public cachelessResourceBundle(cachingResourceBundle bundle) {
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
     * Converts this cacheless Bundle back into a cachingResourceBundle with a new fresh concurrent cache.
     */
    public cachingResourceBundle toCaching() {
        return new cachingResourceBundle(
                new HashMap<>(resourceAttributeToParentResourceGroup),
                new HashMap<>(resourceAttributeToChildResourceGroup),
                new HashMap<>(resourceGroupValidity),
                new HashMap<>(resourceAttributeValidity),
                new HashMap<>(parentResourceGroupToResourceAttributesMap),
                new HashMap<>(childResourceGroupToResourceAttributesMap),
                new ConcurrentHashMap<>()
        );
    }
}
