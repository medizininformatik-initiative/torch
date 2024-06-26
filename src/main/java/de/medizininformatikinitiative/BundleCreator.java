package de.medizininformatikinitiative;

import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class BundleCreator {
    private static final Logger logger = LoggerFactory.getLogger(BundleCreator.class);


    public BundleCreator() {

    }

    public Map<String, Bundle> createBundles(Map<String, Collection<Resource>> resourcesByPatientId) {
        return resourcesByPatientId.entrySet().stream().collect(Collectors.toMap(
                Map.Entry::getKey,
                entry -> {
                    String patientId = entry.getKey();
                    Collection<Resource> patientResources = entry.getValue();

                    Bundle bundle = new Bundle();
                    bundle.setType(Bundle.BundleType.COLLECTION);
                    bundle.setId(patientId );

                    patientResources.forEach(resource -> {
                        try {
                            Bundle.BundleEntryComponent entryComponent = new Bundle.BundleEntryComponent();
                            entryComponent.setResource(resource);
                            bundle.addEntry(entryComponent);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    });
                    return bundle;
                }
        ));
    }
}