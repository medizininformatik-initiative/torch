package de.medizininformatikinitiative.torch.util;

import de.medizininformatikinitiative.torch.exceptions.PatientIdNotFoundException;
import org.hl7.fhir.r4.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;

/**
 * Resource Utils to extract References and IDs from Resources
 */
public class ResourceUtils {

    private static final Logger logger = LoggerFactory.getLogger(ResourceUtils.class);


    public static String patientId(DomainResource resource) throws PatientIdNotFoundException {


        // Check if the resource is an instance of Patient
        if (resource instanceof Patient patient) {
            return patient.getIdPart();
        }
        try {
            //TODO Check all Base Resources
            if (resource instanceof Consent consent) {
                if (consent.hasPatient()) {
                    return getPatientReference(consent.getPatient().getReference());
                }
            } else {
                Method hasSubjectMethod = resource.getClass().getMethod("hasSubject");
                boolean hasSubject = (Boolean) hasSubjectMethod.invoke(resource);

                if (hasSubject) {
                    Method getSubjectMethod = resource.getClass().getMethod("getSubject");
                    Object subject = getSubjectMethod.invoke(resource);

                    Method hasReferenceMethod = subject.getClass().getMethod("hasReference");
                    boolean hasReference = (Boolean) hasReferenceMethod.invoke(subject);

                    if (hasReference) {
                        Method getReferenceMethod = subject.getClass().getMethod("getReference");
                        String reference = (String) getReferenceMethod.invoke(subject);
                        return getPatientReference(reference);
                    }

                }
            }
            throw new PatientIdNotFoundException("Patient Reference not found ");
        } catch (Exception e) {
            // Handle reflection exceptions
            logger.error("Patient ID not Found ", e);
        }

        // Throw an error if no patient ID is found
        throw new PatientIdNotFoundException("Patient ID not found in the given resource");
    }

    public static String getPatientReference(String reference) throws PatientIdNotFoundException {
        if (reference != null && reference.startsWith("Patient/")) {
            return reference.substring("Patient/".length());
        } else {
            throw new PatientIdNotFoundException("Reference does not start with 'Patient/': " + reference);
        }
    }

    public static String getPatientIdFromBundle(Bundle bundle) throws PatientIdNotFoundException {
        if (bundle == null || bundle.getEntry().isEmpty()) {
            throw new PatientIdNotFoundException("Bundle is isEmpty or null");
        }
        Resource resource = bundle.getEntryFirstRep().getResource();
        if (resource instanceof DomainResource) {
            return patientId((DomainResource) resource);
        }
        throw new PatientIdNotFoundException("First entry in bundle is not a DomainResource");
    }


}
