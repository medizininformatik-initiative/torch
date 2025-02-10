package de.medizininformatikinitiative.torch.management;

import de.medizininformatikinitiative.torch.model.ResourceGroupWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.Collection;

public class PatientResourceStore {

    private static final Logger logger = LoggerFactory.getLogger(PatientResourceStore.class);

    private ResourceStore resourceStore;
    private final String patientID;

    PatientResourceStore(ResourceStore resourceStore, String patientID) {
        this.resourceStore = resourceStore;
        this.patientID = patientID;
    }

    public Mono<ResourceGroupWrapper> get(String id) {
        return resourceStore.get(id);
    }


    public void put(ResourceGroupWrapper wrapper) {
        resourceStore.put(wrapper);
    }


    public void delete(String fullUrl) {
        resourceStore.delete(fullUrl);
    }

    public Boolean isEmpty() {
        return resourceStore.isEmpty();
    }

    public Collection<String> keySet() {
        return resourceStore.keySet();
    }

    public Collection<ResourceGroupWrapper> values() {
        return resourceStore.values();
    }

    public String getID() {
        return patientID;
    }
}
