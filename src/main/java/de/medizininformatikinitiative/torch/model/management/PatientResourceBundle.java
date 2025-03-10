package de.medizininformatikinitiative.torch.model.management;

import de.medizininformatikinitiative.torch.model.consent.Provisions;
import org.hl7.fhir.r4.model.Encounter;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.Objects;


/**
 * Generic bundle that handles Resources for a single patient.
 * Has a underlying ResourceBundle to handle the Resources and their associated groups.
 *
 * @param patientId  ID String of the Patient
 * @param provisions ConsentProvisions to be applied to all Resources within the patient compartment
 * @param bundle     Resourcebundle handling the resource wrappers for the patient
 */
public record PatientResourceBundle(String patientId, Provisions provisions,
                                    ResourceBundle bundle) {

    public PatientResourceBundle {
        Objects.requireNonNull(patientId);
        Objects.requireNonNull(provisions);

    }

    public PatientResourceBundle(String patientID) {
        this(patientID, new ResourceBundle());
    }

    public PatientResourceBundle(String patientID, ResourceBundle bundle) {
        this(patientID, Provisions.of(), bundle);
    }


    public PatientResourceBundle(String patientID, Provisions provisions) {
        this(patientID, provisions, new ResourceBundle());
    }

    public PatientResourceBundle updateConsentPeriodsByPatientEncounters(Collection<Encounter> encounters) {
        return new PatientResourceBundle(patientId, provisions.updateConsentPeriodsByPatientEncounters(encounters), bundle);
    }

    public Mono<ResourceGroupWrapper> get(String id) {
        return bundle.get(id);
    }

    public boolean put(ResourceGroupWrapper wrapper) {
        return bundle.put(wrapper);
    }

    public Boolean isValidReference(ResourceIdGroup group) {
        return bundle.isValidReference(group);
    }

    public Boolean knownReference(String reference) {
        return bundle.knownReference(reference);
    }

    public String getResourceIDFromReferenceString(String reference) {
        return bundle.getResourceIDFromReferenceString(reference);
    }

    public void remove(String id) {
        bundle.remove(id);
    }


    public Boolean isEmpty() {
        return bundle.isEmpty();
    }

    public Collection<String> keySet() {
        return bundle.keySet();
    }

    public Collection<ResourceGroupWrapper> values() {
        return bundle.values();
    }

    public ResourceBundle toResourceBundle() {
        return bundle;
    }

    public boolean contains(String ref) {
        return bundle.contains(ref);
    }


}
