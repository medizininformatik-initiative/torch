package de.medizininformatikinitiative.torch.model.management;

import de.medizininformatikinitiative.torch.model.consent.NonContinuousPeriod;
import org.hl7.fhir.r4.model.Resource;

import java.util.Optional;

import static java.util.Objects.requireNonNull;

/**
 * Generic bundle that handles Resources for a single patient.
 * Has a underlying ResourceBundle to handle the Resources and their associated groups.
 *
 * @param patientId      ID String of the Patient
 * @param consentPeriods ConsentProvisions to be applied to all Resources within the patient compartment
 * @param bundle         ResourceBundle handling the resource wrappers for the patient
 */
public record PatientResourceBundle(String patientId, NonContinuousPeriod consentPeriods, ResourceBundle bundle) {

    public PatientResourceBundle {
        requireNonNull(patientId);
        requireNonNull(consentPeriods);
        requireNonNull(bundle);
    }

    public PatientResourceBundle(String patientID) {
        this(patientID, new ResourceBundle());
    }

    public PatientResourceBundle(String patientID, ResourceBundle bundle) {
        this(patientID, NonContinuousPeriod.of(), bundle);
    }

    public PatientResourceBundle(String patientID, CachelessResourceBundle cachelessResourceBundle) {
        this(patientID, NonContinuousPeriod.of(), cachelessResourceBundle.toCaching());
    }

    public PatientResourceBundle(String patientID, NonContinuousPeriod consentPeriods) {
        this(patientID, consentPeriods, new ResourceBundle());
    }

    public Optional<Resource> get(String id) {
        return bundle.get(id);
    }

    public boolean put(ResourceGroupWrapper wrapper) {
        return bundle.put(wrapper);
    }

    public void remove(String id) {
        bundle.remove(id);
    }

    public Boolean isEmpty() {
        return bundle.isEmpty();
    }

    public boolean contains(String ref) {
        return bundle.contains(ref);
    }

    public void put(Resource resource) {
        bundle.put(resource);
    }

    public Boolean put(Resource resource, String groupId, boolean b) {
        return bundle().put(resource, groupId, b);
    }

    /**
     * Puts an empty Optional resource for an resource id
     *
     * @param resourceReference the referenceString of the resource that could not be fetched.
     */
    public void put(String resourceReference) {
        bundle.put(resourceReference);
    }

    public void addStaticInfo(CachelessResourceBundle staticInfo) {
        bundle.merge(staticInfo);
    }
}
