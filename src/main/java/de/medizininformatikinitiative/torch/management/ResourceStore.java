package de.medizininformatikinitiative.torch.management;

import de.medizininformatikinitiative.torch.model.ResourceGroupWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/*
1 core Store
1 store per patient

 */

@Component
public class ResourceStore {
    private static final Logger logger = LoggerFactory.getLogger(ResourceStore.class);

    private final Map<String, ResourceGroupWrapper> resourceCache = new ConcurrentHashMap<>();


    public Mono<ResourceGroupWrapper> get(String id) {
        ResourceGroupWrapper cached = resourceCache.get(id);
        if (cached != null) {
            logger.debug("Fetching resource from cache: {}", id);
            return Mono.just(cached);
        }
        return Mono.empty();
    }


    public void put(ResourceGroupWrapper wrapper) {
        if (wrapper != null) {
            resourceCache.compute(wrapper.resource().getId(), (id, existingWrapper) -> {
                if (existingWrapper != null) {
                    return existingWrapper.addGroups(wrapper.groupSet());
                }
                return wrapper;
            });
        }
    }


    public void delete(String fullUrl) {
        logger.debug("Invalidating resource from cache: {}", fullUrl);
        resourceCache.remove(fullUrl);
    }


}
