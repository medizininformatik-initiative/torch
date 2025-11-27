package de.medizininformatikinitiative.torch.model.management;

import de.medizininformatikinitiative.torch.util.ResourceUtils;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.DomainResource;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Period;
import org.hl7.fhir.r4.model.Provenance;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.hl7.fhir.r4.model.Bundle.BundleType.TRANSACTION;
import static org.hl7.fhir.r4.model.Bundle.HTTPVerb.PUT;

/**
 * Generic bundle that handles Resources
 *
 * @param resourceAttributeToParentResourceGroup     Bundle level map managing a resource group combination pointing to a ReferenceGroup calling it i.e. which context created this reference
 * @param resourceAttributeToChildResourceGroup      Bundle level map managing a resource group combination pointing to a ReferenceGroup it calls i.e. which resources are called because of that reference
 * @param resourceGroupValidity                      Is this reference valid i.e. has this reference
 * @param resourceAttributeValidity                  Manages the references pointing to a unique resource e.g. loaded by absolute url and pointing at something.
 * @param parentResourceGroupToResourceAttributesMap
 * @param childResourceGroupToResourceAttributesMap
 * @param cache
 */
public record ResourceBundle(
        ConcurrentHashMap<ResourceAttribute, Set<ResourceGroup>> resourceAttributeToParentResourceGroup,
        ConcurrentHashMap<ResourceAttribute, Set<ResourceGroup>> resourceAttributeToChildResourceGroup,
        ConcurrentHashMap<ResourceGroup, Boolean> resourceGroupValidity,
        ConcurrentHashMap<ResourceAttribute, Boolean> resourceAttributeValidity,
        ConcurrentHashMap<ResourceGroup, Set<ResourceAttribute>> parentResourceGroupToResourceAttributesMap,
        ConcurrentHashMap<ResourceGroup, Set<ResourceAttribute>> childResourceGroupToResourceAttributesMap,
        ConcurrentHashMap<String, Optional<Resource>> cache) {

    public static final String PROGRAM_URL = "https://www.medizininformatik-initiative.de/fhir/fdpg/NamingSystem/program";
    public static final String ATTRIBUTE_GROUP_URL = "https://www.medizininformatik-initiative.de/fhir/fdpg/NamingSystem/attribute_group";
    public static final String EXTRACTION_ID_URL = "https://www.medizininformatik-initiative.de/fhir/fdpg/NamingSystem/extraction_id";
    public static final String TORCH = "torch";
    public static final String PROVENANCE_PREFIX = "Provenance/torch-";

    public ResourceBundle() {
        this(new ConcurrentHashMap<>(), new ConcurrentHashMap<>(), new ConcurrentHashMap<>(), new ConcurrentHashMap<>(), new ConcurrentHashMap<>(), new ConcurrentHashMap<>(), new ConcurrentHashMap<>());
    }

    /**
     * Gets the resource from cache.
     *
     * @param reference reference of the cached fhir resource
     * @return resource when in cache, optional.empty() when fetch was attempted but not successful
     * or null when reference is not known to cache.
     */
    public Optional<Resource> get(String reference) {
        return cache.get(reference);
    }

    public void addAttributeToChild(ResourceAttribute attribute, ResourceGroup child) {
        // Link the child to the attribute in resourceAttributeToChildResourceGroup
        resourceAttributeToChildResourceGroup
                .computeIfAbsent(attribute, k -> ConcurrentHashMap.newKeySet())
                .add(child);

        // Ensure child-to-attribute mapping is updated
        childResourceGroupToResourceAttributesMap
                .computeIfAbsent(child, k -> ConcurrentHashMap.newKeySet())
                .add(attribute);
    }


    public void addAttributeToParent(ResourceAttribute attribute, ResourceGroup parent) {
        // Link the parent to the attribute in resourceAttributeToParentResourceGroup
        resourceAttributeToParentResourceGroup
                .computeIfAbsent(attribute, k -> ConcurrentHashMap.newKeySet())
                .add(parent);

        // Ensure parent-to-attribute mapping is updated
        parentResourceGroupToResourceAttributesMap
                .computeIfAbsent(parent, k -> ConcurrentHashMap.newKeySet())
                .add(attribute);
    }

    /**
     * Removes a parent resourceGroup for the given attribute.
     * If the resourceGroup is the last one in the set, the entire group is removed from the map.
     *
     * @param group     The resource group from which the attribute should be removed.
     * @param attribute The attribute to remove from the group.
     * @return true if the attribute was removed and the group became empty (or was absent); false otherwise.
     */
    public boolean removeParentRGFromAttribute(ResourceGroup group, ResourceAttribute attribute) {
        AtomicBoolean isEmpty = new AtomicBoolean(false);

        resourceAttributeToParentResourceGroup.computeIfPresent(attribute, (key, set) -> {
            set.remove(group);
            if (set.isEmpty()) {
                isEmpty.set(true);
                return null; // Remove key if set is empty
            }
            return set;
        });

        return isEmpty.get();
    }

    /**
     * Removes a attribute for the given parent resourceGroup.
     * If the resourceGroup is the last one in the set, the entire group is removed from the map.
     *
     * @param group     The resource group from which the attribute should be removed.
     * @param attribute The attribute to remove from the group.
     * @return true if the attribute was removed and the group became empty (or was absent); false otherwise.
     */
    public boolean removeAttributefromParentRG(ResourceGroup group, ResourceAttribute attribute) {
        return removeFromMap(group, attribute, parentResourceGroupToResourceAttributesMap);
    }

    public Boolean setResourceAttributeValid(ResourceAttribute attribute) {
        return resourceAttributeValidity.put(attribute, true);
    }

    public Boolean setResourceAttributeInValid(ResourceAttribute attribute) {
        return resourceAttributeValidity.put(attribute, false);
    }

    public boolean resourceAttributeValid(ResourceAttribute attribute) {
        return resourceAttributeValidity.getOrDefault(attribute, false);
    }

    /**
     * Removes a parent attribute for the given resource group.
     * If the attribute is the last one in the set, the entire group is removed from the map.
     *
     * @param group     The resource group from which the attribute should be removed.
     * @param attribute The attribute to remove from the group.
     * @return true if the attribute was removed and the group became empty (or was absent); false otherwise.
     */
    public boolean removeParentAttributeFromChildRG(ResourceGroup group, ResourceAttribute attribute) {
        return removeFromMap(group, attribute, childResourceGroupToResourceAttributesMap);
    }

    private boolean removeFromMap(ResourceGroup group, ResourceAttribute attribute, ConcurrentHashMap<ResourceGroup, Set<ResourceAttribute>> childResourceGroupToResourceAttributesMap) {
        AtomicBoolean isEmpty = new AtomicBoolean(false);


        childResourceGroupToResourceAttributesMap.computeIfPresent(group, (key, set) -> {
            set.remove(attribute);
            if (set.isEmpty()) {
                isEmpty.set(true);
                return null; // Remove key if set is empty
            }
            return set;
        });

        return isEmpty.get();
    }

    /**
     * Removes a child resourceGroup for an attribute calling it.
     * If the child resourceGroup is the last one in the set, the entire group is removed from the map.
     * An attribute without children is automatically invalid.
     *
     * @param group     The resource group from which the attribute should be removed.
     * @param attribute The attribute to remove from the group.
     * @return true if the attribute was removed and the group became empty (or was absent); false otherwise.
     */
    public boolean removeChildRGFromAttribute(ResourceGroup group, ResourceAttribute attribute) {
        AtomicBoolean isEmpty = new AtomicBoolean(false);

        resourceAttributeToChildResourceGroup.computeIfPresent(attribute, (key, set) -> {
            set.remove(group);
            if (set.isEmpty()) {
                setResourceAttributeInValid(attribute);
                isEmpty.set(true);
                return null; // Remove key if set is empty
            }
            return set;
        });

        return isEmpty.get();
    }

    public boolean put(Resource resource, String groupId, boolean valid) {
        String resourceUrl = ResourceUtils.getRelativeURL(resource);
        ResourceGroup group = new ResourceGroup(resourceUrl, groupId);
        addResourceGroupValidity(group, valid);
        return cache.putIfAbsent(resourceUrl, Optional.of(resource)) == null;
    }

    /**
     * Adds the wrapper into the underlying concurrent hashmap.
     * Generates from IDPart and ResourceType of the resource the relative url as key for the cache
     *
     * @param wrapper wrapper to be added to the resourcebundle
     * @return boolean containing info if the wrapper is new or updated.
     */
    public boolean put(ResourceGroupWrapper wrapper) {
        AtomicReference<Boolean> result = new AtomicReference<>(false);
        if (wrapper == null) {
            return result.get();
        }
        //set Cache Key to relative URL
        DomainResource resource = wrapper.resource();

        String resourceUrl = ResourceUtils.getRelativeURL(resource);
        wrapper.groupSet().forEach(group -> addResourceGroupValidity(new ResourceGroup(resourceUrl, group), true));
        cache.put(resourceUrl, Optional.of(resource));

        return result.get();
    }

    public ConcurrentMap<ResourceGroup, Boolean> getInvalid() {
        ConcurrentHashMap<ResourceGroup, Boolean> invalidEntries = new ConcurrentHashMap<>();

        resourceGroupValidity.forEach((key, value) -> {
            if (Boolean.FALSE.equals(value)) { // If value is false, add to the new map
                invalidEntries.put(key, false);
            }
        });

        return invalidEntries;
    }

    public Set<ResourceGroup> getKnownResourceGroups() {
        return Set.copyOf(resourceGroupValidity.keySet());
    }

    public Bundle toFhirBundle(String extractionId) {
        Bundle bundle = new Bundle();
        bundle.setType(TRANSACTION);
        bundle.setId(UUID.randomUUID().toString());

        cache.values().forEach(resource -> resource.ifPresent(value -> bundle.addEntry(createBundleEntry(value))));
        getAttributeGroupProvenance(extractionId).forEach(provenance -> bundle.addEntry(createBundleEntry(provenance)));
        return bundle;
    }

    private static Provenance createProvenance(String extractionId, String groupId, List<ResourceGroup> groupResources) {
        Provenance provenance = new Provenance();
        provenance.setId(PROVENANCE_PREFIX + groupId);
        provenance.setRecorded(new Date());

        // Add all resources under the same group as targets
        groupResources.forEach(rg ->
                provenance.addTarget(new Reference(rg.resourceId()))
        );

        // Set occurredPeriod (constant or dynamic)
        Period occurred = new Period();
        occurred.setStartElement(new DateTimeType(new Date()));
        provenance.setOccurred(occurred);

        // Add the agent (torch)
        Provenance.ProvenanceAgentComponent agent = new Provenance.ProvenanceAgentComponent();
        Reference whoReference = new Reference();
        whoReference.setIdentifier(new Identifier()
                .setSystem(PROGRAM_URL)
                .setValue(TORCH));
        agent.setWho(whoReference);
        provenance.addAgent(agent);

        // Add entity: attribute_group
        Provenance.ProvenanceEntityComponent entityGroup = new Provenance.ProvenanceEntityComponent();
        entityGroup.setRole(Provenance.ProvenanceEntityRole.SOURCE);
        entityGroup.setWhat(new Reference().setIdentifier(new Identifier()
                .setSystem(ATTRIBUTE_GROUP_URL)
                .setValue(groupId)));
        provenance.addEntity(entityGroup);

        // Add entity: extraction_id
        Provenance.ProvenanceEntityComponent entityExtraction = new Provenance.ProvenanceEntityComponent();
        entityExtraction.setRole(Provenance.ProvenanceEntityRole.SOURCE);
        entityExtraction.setWhat(new Reference().setIdentifier(new Identifier()
                .setSystem(EXTRACTION_ID_URL)
                .setValue(extractionId)));
        provenance.addEntity(entityExtraction);

        return provenance;
    }

    public List<Provenance> getAttributeGroupProvenance(String extractionId) {
        // Group all resources by groupId
        Map<String, List<ResourceGroup>> groupedByGroupId = getValidResourceGroups().stream()
                .collect(Collectors.groupingBy(ResourceGroup::groupId));

        // Create one Provenance per group
        return groupedByGroupId.entrySet().stream()
                .map(entry -> {
                    String groupId = entry.getKey();
                    List<ResourceGroup> groupResources = entry.getValue();
                    return createProvenance(extractionId, groupId, groupResources);
                })
                .toList();
    }


    private Bundle.BundleEntryComponent createBundleEntry(Resource resource) {
        Bundle.BundleEntryComponent entryComponent = new Bundle.BundleEntryComponent();
        entryComponent.setResource(resource);
        Bundle.BundleEntryRequestComponent request = new Bundle.BundleEntryRequestComponent();
        request.setUrl(ResourceUtils.getRelativeURL(resource));
        request.setMethod(PUT);
        entryComponent.setRequest(request);
        return entryComponent;
    }

    public Boolean isValidResourceGroup(ResourceGroup group) {
        return resourceGroupValidity.get(group);
    }

    public Boolean addResourceGroupValidity(ResourceGroup group, boolean valid) {
        return resourceGroupValidity.put(group, valid);
    }

    public void remove(String id) {
        cache.remove(id);
    }

    public Boolean isEmpty() {
        return cache.isEmpty();
    }

    public boolean contains(ResourceGroup ref) {
        return resourceGroupValidity.containsKey(ref);
    }

    public boolean contains(String ref) {
        return cache.containsKey(ref);
    }

    public void put(Resource resource) {
        cache.put(ResourceUtils.getRelativeURL(resource), Optional.of(resource));
    }


    public void put(String resourceReference) {
        cache.put(resourceReference, Optional.empty());
    }

    public Set<ResourceGroup> getValidResourceGroups() {
        return resourceGroupValidity().entrySet().stream()
                .filter(entry -> Boolean.TRUE.equals(entry.getValue()))
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }
}
