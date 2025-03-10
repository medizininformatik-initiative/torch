package de.medizininformatikinitiative.torch.model.management;

import de.medizininformatikinitiative.torch.util.ResourceUtils;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.DomainResource;
import org.hl7.fhir.r4.model.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;


/**
 * Generic bundle that handles Resources
 *
 * @param resourceCache                    Cache that contains the Resources keyed by their FullURL
 * @param resourceAGtoParentReferenceGroup Bundle level map managing a resource group combination pointing to a ReferenceGroup calling it i.e. which context created this reference
 * @param resourceAGtoChildReferenceGroup  Bundle level map managing a resource group combination pointing to a ReferenceGroup it calls i.e. which resources are called because of that reference
 * @param referenceValidity                Is this reference valid i.e. has this reference
 * @param resourceAGToReferenceWrapper
 * @param ReferenceToResourceId            Manages the references pointing to a unique resource e.g. loaded by absolute url and pointing at something.
 */
public record ResourceBundle(ConcurrentHashMap<String, ResourceGroupWrapper> resourceCache,
                             ConcurrentHashMap<ResourceAttributeGroup, java.util.Set<ResourceIdGroup>> resourceAGtoParentReferenceGroup,
                             ConcurrentHashMap<ResourceAttributeGroup, java.util.Set<ResourceIdGroup>> resourceAGtoChildReferenceGroup,
                             ConcurrentHashMap<ResourceIdGroup, Boolean> referenceValidity,
                             ConcurrentHashMap<ResourceAttributeGroup, List<ReferenceWrapper>> resourceAGToReferenceWrapper,
                             ConcurrentHashMap<String, String> ReferenceToResourceId) {
    private static final Logger logger = LoggerFactory.getLogger(ResourceBundle.class);

    static final org.hl7.fhir.r4.model.Bundle.HTTPVerb method = Bundle.HTTPVerb.PUT;


    public ResourceBundle() {
        this(new ConcurrentHashMap<>(), new ConcurrentHashMap<>(), new ConcurrentHashMap<>(), new ConcurrentHashMap<>(), new ConcurrentHashMap<>(), new ConcurrentHashMap<>());
    }


    public Mono<ResourceGroupWrapper> get(String id) {
        ResourceGroupWrapper cached = resourceCache.get(id);
        if (cached != null) {
            return Mono.just(cached);
        }
        return Mono.empty();
    }


    /**
     * Adds the wrapper into the underlying concurrent hashmap.
     * Generates from IDPart and ResourceType of the resource the relative url as key for the cache
     *
     * @param wrapper wrapper to be added to the resourcebundle
     * @return boolean containing info if the wrapper is new or updated.
     */
    public boolean put(ResourceGroupWrapper wrapper) {
        return put(wrapper, List.of(ResourceUtils.getRelativeURL(wrapper.resource())));
    }

    public boolean put(ResourceGroupWrapper wrapper, List<String> references) {
        AtomicReference<Boolean> result = new AtomicReference<>(false);
        if (wrapper == null) {
            return result.get();
        }
        String relativeURL = ResourceUtils.getRelativeURL(wrapper.resource());
        references.forEach(ref -> ReferenceToResourceId.putIfAbsent(ref, relativeURL));
        //set Cache Key to relative URL
        DomainResource resource = wrapper.resource();
        resourceCache.compute(ResourceUtils.getRelativeURL(resource), (id, existingWrapper) -> {
            if (existingWrapper == null) {
                // No existing wrapper, add the new one
                result.set(true);
                return wrapper;
            } else {
                // Merge attribute groups into a new mutable set
                HashSet<String> mergedGroups = new HashSet<>(existingWrapper.groupSet());
                mergedGroups.addAll(wrapper.groupSet());
                if (mergedGroups.equals(existingWrapper.groupSet())) {
                    result.set(false);
                } else {
                    result.set(true);
                }
                return new ResourceGroupWrapper(existingWrapper.resource(), mergedGroups);
            }
        });

        return result.get();
    }


    public Bundle toFhirBundle() {
        Bundle bundle = new Bundle();
        bundle.setType(Bundle.BundleType.TRANSACTION);
        bundle.setId(UUID.randomUUID().toString());

        resourceCache.values().forEach(resourceGroupWrapper -> {
            bundle.addEntry(createBundleEntry(resourceGroupWrapper.resource()));
        });
        return bundle;
    }

    private Bundle.BundleEntryComponent createBundleEntry(Resource resource) {
        Bundle.BundleEntryComponent entryComponent = new Bundle.BundleEntryComponent();
        entryComponent.setResource(resource);
        Bundle.BundleEntryRequestComponent request = new Bundle.BundleEntryRequestComponent();
        request.setUrl(resource.getId());
        request.setMethod(method);
        entryComponent.setRequest(request);
        return entryComponent;
    }

    public Boolean isValidReference(ResourceIdGroup group) {
        return referenceValidity.get(group);
    }

    public Boolean knownReference(String Reference) {
        return ReferenceToResourceId.contains(Reference);
    }


    public void remove(String id) {
        resourceCache.remove(id);
    }

    public Boolean isEmpty() {
        return resourceCache.isEmpty();
    }

    public Collection<String> keySet() {
        return resourceCache.keySet();
    }

    public Collection<ResourceGroupWrapper> values() {
        return resourceCache.values();
    }

    public boolean contains(String ref) {
        return resourceCache.containsKey(ref);
    }

    public String getResourceIDFromReferenceString(String reference) {
        return ReferenceToResourceId.get(reference);
    }
}
