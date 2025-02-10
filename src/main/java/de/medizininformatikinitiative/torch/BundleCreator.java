package de.medizininformatikinitiative.torch;

import ca.uhn.fhir.context.FhirContext;
import de.medizininformatikinitiative.torch.exceptions.PatientIdNotFoundException;
import de.medizininformatikinitiative.torch.management.CompartmentManager;
import de.medizininformatikinitiative.torch.management.ResourceStore;
import de.medizininformatikinitiative.torch.model.ResourceGroupWrapper;
import de.medizininformatikinitiative.torch.util.ResourceUtils;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.DomainResource;
import org.hl7.fhir.r4.model.Resource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Component

/*
 * Bundle creator for collecting Resources by patient into bundles for export
 */
public class BundleCreator {


    @Autowired
    FhirContext context;

    @Autowired
    CompartmentManager compartmentManager;

    final org.hl7.fhir.r4.model.Bundle.HTTPVerb method = Bundle.HTTPVerb.PUT;

    public BundleCreator() {

    }

    public Map<String, Bundle> createBundles(ResourceStore resourceStore) throws PatientIdNotFoundException {
        if (resourceStore.isEmpty()) {
            throw new IllegalArgumentException("ResourceStore is empty; cannot create bundle.");
        }

        HashMap<String, Bundle> bundleMap = new HashMap<String, Bundle>();


        for (ResourceGroupWrapper wrapper : resourceStore.values()) {
            DomainResource resource = (DomainResource) wrapper.resource();

            String id = "core";
            if (compartmentManager.isInCompartment(resource.getResourceType().toString())) {
                id = ResourceUtils.patientId(resource);
            }
            Bundle bundle;
            bundle = bundleMap.get(id);
            if (bundle == null) {
                bundle = new Bundle();
                bundle.setType(Bundle.BundleType.TRANSACTION);
                bundle.setId(UUID.randomUUID().toString());
            }
            bundle.addEntry(createBundleEntry(resource));
            bundleMap.put(id, bundle);
        }
        return bundleMap;
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

}