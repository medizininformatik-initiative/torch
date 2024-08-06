package de.medizininformatikinitiative.torch.util;

import de.medizininformatikinitiative.torch.exceptions.PatientIdNotFoundException;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.DomainResource;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;

public class ResourceUtils {

    private static final Logger logger = LoggerFactory.getLogger(ResourceUtils.class);



    public static String getPatientId(DomainResource resource) throws PatientIdNotFoundException {
        // Check if the resource is an instance of Patient
        if (resource instanceof Patient patient) {
            return patient.getId();
        }

        try {
            // Use reflection to check if the method 'hasSubject' exists
            Method hasSubjectMethod = resource.getClass().getMethod("hasSubject");
            boolean hasSubject = (Boolean) hasSubjectMethod.invoke(resource);

            if (hasSubject) {
                // Use reflection to check if the method 'getSubject' exists
                Method getSubjectMethod = resource.getClass().getMethod("getSubject");
                Object subject = getSubjectMethod.invoke(resource);

                // Use reflection to check if the 'subject' has the method 'hasReference'
                Method hasReferenceMethod = subject.getClass().getMethod("hasReference");
                boolean hasReference = (Boolean) hasReferenceMethod.invoke(subject);

                if (hasReference) {
                    // Use reflection to get the 'getReference' method from 'subject'
                    Method getReferenceMethod = subject.getClass().getMethod("getReference");
                    String reference = (String) getReferenceMethod.invoke(subject);
                    if (reference != null && reference.startsWith("Patient/")) {
                        return reference.substring("Patient/".length());
                    }
                    else{
                        throw new PatientIdNotFoundException("Reference does not start with 'Patient/': " + reference);
                    }
                }

            }
            throw new PatientIdNotFoundException("Patient Reference not found ");
        } catch (Exception e) {
            // Handle reflection exceptions
            logger.error("Patient ID not Found ",e);
        }

        // Throw an error if no patient ID is found
        throw new PatientIdNotFoundException("Patient ID not found in the given resource");
    }

    public static String getPatientIdFromBundle(Bundle bundle) throws PatientIdNotFoundException {
        if (bundle == null || bundle.getEntry().isEmpty()) {
            throw new PatientIdNotFoundException("Bundle is empty or null");
        }
        Resource resource = bundle.getEntryFirstRep().getResource();
        if (resource instanceof DomainResource) {
            return getPatientId((DomainResource) resource);
        }
        throw new PatientIdNotFoundException("First entry in bundle is not a DomainResource");
    }


}
