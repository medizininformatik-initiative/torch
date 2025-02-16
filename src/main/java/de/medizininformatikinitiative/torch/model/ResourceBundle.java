package de.medizininformatikinitiative.torch.model;

import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttributeGroup;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Resource;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;


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


    public boolean put(ResourceGroupWrapper wrapper) {
        if (wrapper == null) {
            return false;
        }

        String resourceId = wrapper.resource().getId();
        resourceCache.compute(resourceId, (id, existingWrapper) -> {
            if (existingWrapper == null) {
                // No existing wrapper, add the new one
                return wrapper;
            } else {
                // Merge attribute groups into a new mutable set
                Set<AnnotatedAttributeGroup> mergedGroups = new HashSet<>(existingWrapper.groupSet());
                mergedGroups.addAll(wrapper.groupSet());
                return new ResourceGroupWrapper(existingWrapper.resource(), mergedGroups);
            }
        });

        // Check if the wrapper was added or updated
        return resourceCache.get(resourceId).equals(wrapper);
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
        request.setUrl(resource.getResourceType() + "/" + resource.getId());
        request.setMethod(method);
        entryComponent.setRequest(request);
        return entryComponent;
    }


    public void delete(String fullUrl) {
        resourceCache.remove(fullUrl);
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
