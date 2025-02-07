package de.medizininformatikinitiative.torch.management;

import org.hl7.fhir.r4.model.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ResourceCache {
    private static final Logger logger = LoggerFactory.getLogger(ResourceCache.class);

    private final Map<String, Resource> resourceCache = new ConcurrentHashMap<>();

    /**
     * Retrieves a resource from the cache if available.
     *
     * @param id The identifier of the resource.
     * @return A {@link Mono} containing the cached resource, or empty if not found.
     */
    public Mono<Resource> get(String id) {
        Resource cached = resourceCache.get(id);
        if (cached != null) {
            logger.debug("Fetching resource from cache: {}", id);
            return Mono.just(cached);
        }
        return Mono.empty();
    }

    /**
     * Stores a resource in the cache.
     *
     * @param resource The FHIR resource to cache.
     */
    public void put(Resource resource) {
        if (resource != null) {
            resourceCache.put(resource.getId(), resource);
        }
    }

    /**
     * Invalidates a resource from the cache.
     *
     * @param fullUrl The full URL of the resource to remove.
     */
    public void invalidate(String fullUrl) {
        logger.debug("Invalidating resource from cache: {}", fullUrl);
        resourceCache.remove(fullUrl);
    }

    /**
     * Clears the entire cache.
     */
    public void clear() {
        logger.debug("Clearing entire resource cache.");
        resourceCache.clear();
    }
}
