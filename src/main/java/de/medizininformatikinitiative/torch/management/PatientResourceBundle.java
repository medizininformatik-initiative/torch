package de.medizininformatikinitiative.torch.management;

import de.medizininformatikinitiative.torch.model.ResourceGroupWrapper;
import de.medizininformatikinitiative.torch.model.consent.Provisions;
import org.hl7.fhir.r4.model.Encounter;

import java.util.Collection;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;


/**
 * Generic bundle that handles Resources for a single patient.
 * Has a underlying ResourceBundle to handle the Resources and their associated groups.
 *
 * @param patientId  ID String of the Patient
 * @param provisions ConsentProvisions to be applied to all Resources within the patient compartment
 * @param bundle     Resourcebundle handling the resource wrappers for the patient
 */
public record PatientResourceBundle(String patientId, Provisions provisions,
                                    ConcurrentHashMap<String, ResourceGroupWrapper> resourceCache) {

    public PatientResourceBundle {
        patientId = Objects.requireNonNull(patientId);
        provisions = Objects.requireNonNull(provisions);
    }

    public PatientResourceBundle(String patientID) {
        this(patientID, Provisions.of(), new ConcurrentHashMap<>());
    }

    public PatientResourceBundle(String patientID, Provisions provisions) {
        this(patientID, provisions, new ConcurrentHashMap<>());
    }

    public PatientResourceBundle updateConsentPeriodsByPatientEncounters(Collection<Encounter> encounters) {
        return new PatientResourceBundle(patientId, provisions.updateConsentPeriodsByPatientEncounters(encounters), resourceCache);
    }

    public ResourceGroupWrapper get(String id) {
        return resourceCache.get(id);
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

    public ResourceBundle toResourceBundle() {
        return new ResourceBundle(resourceCache);
    }

}
