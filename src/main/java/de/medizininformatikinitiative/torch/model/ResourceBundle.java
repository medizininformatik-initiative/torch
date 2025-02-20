package de.medizininformatikinitiative.torch.model;

import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Resource;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.HashSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;


/**
 * Generic bundle that handles Resources
 *
 * @param resourceCache Cache that contains the Resources keyed by their FullURL
 */
public record ResourceBundle(ConcurrentHashMap<String, ResourceGroupWrapper> resourceCache) {

    static final org.hl7.fhir.r4.model.Bundle.HTTPVerb method = Bundle.HTTPVerb.PUT;

    public ResourceBundle() {
        this(new ConcurrentHashMap<>());
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
     * Generates from ID and
     *
     * @param wrapper wrapper to be added to the resourcebundle
     * @return boolean containing info if the wrapper is new or updated.
     */
    public boolean put(ResourceGroupWrapper wrapper) {
        AtomicReference<Boolean> result = new AtomicReference<>(false);
        if (wrapper == null) {
            return result.get();
        }

        String resourceId = wrapper.resource().getIdBase();
        resourceCache.compute(resourceId, (id, existingWrapper) -> {
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
    
}
