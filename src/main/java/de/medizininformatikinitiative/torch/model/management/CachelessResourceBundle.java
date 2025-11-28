package de.medizininformatikinitiative.torch.model.management;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reference Parent-Child relation
 * ResourceGroup<->ResourceAttribute<->ResourceGroup
 */
public record CachelessResourceBundle(
        Map<ResourceAttribute, Set<ResourceGroupRelation>> resourceAttributeToParentResourceGroup,
        Map<ResourceAttribute, Set<ResourceGroupRelation>> resourceAttributeToChildResourceGroup,
        Map<ResourceGroupRelation, Boolean> resourceGroupValidity,
        Map<ResourceAttribute, Boolean> resourceAttributeValidity,
        Map<ResourceGroupRelation, Set<ResourceAttribute>> parentResourceGroupToResourceAttributesMap,
        Map<ResourceGroupRelation, Set<ResourceAttribute>> childResourceGroupToResourceAttributesMap
) {
    public CachelessResourceBundle {
        resourceAttributeToParentResourceGroup = Map.copyOf(resourceAttributeToParentResourceGroup);
        resourceGroupValidity = Map.copyOf(resourceGroupValidity);
        resourceAttributeValidity = Map.copyOf(resourceAttributeValidity);
        parentResourceGroupToResourceAttributesMap = Map.copyOf(parentResourceGroupToResourceAttributesMap);
        childResourceGroupToResourceAttributesMap = Map.copyOf(childResourceGroupToResourceAttributesMap);
    }

    /**
     * Creates an immutable version of a given ResourceBundle.
     */
    public CachelessResourceBundle(ResourceBundle bundle) {
        this(
                bundle.resourceAttributeToParentResourceGroup(),
                bundle.resourceAttributeToChildResourceGroup(),
                bundle.resourceGroupValidity(),
                bundle.resourceAttributeValidity(),
                bundle.parentResourceGroupToResourceAttributesMap(),
                bundle.childResourceGroupToResourceAttributesMap()
        );
    }

    /**
     * Converts this cacheless Bundle back into a cachingResourceBundle with a new fresh concurrent cache.
     */
    public ResourceBundle toCaching() {
        return new ResourceBundle(
                new ConcurrentHashMap<>(resourceAttributeToParentResourceGroup),
                new ConcurrentHashMap<>(resourceAttributeToChildResourceGroup),
                new ConcurrentHashMap<>(resourceGroupValidity),
                new ConcurrentHashMap<>(resourceAttributeValidity),
                new ConcurrentHashMap<>(parentResourceGroupToResourceAttributesMap),
                new ConcurrentHashMap<>(childResourceGroupToResourceAttributesMap),
                new ConcurrentHashMap<>()
        );
    }
}
