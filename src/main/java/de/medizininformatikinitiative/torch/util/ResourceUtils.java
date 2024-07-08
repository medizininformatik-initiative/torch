package de.medizininformatikinitiative.torch.util;

import de.medizininformatikinitiative.torch.exceptions.PatientIdNotFoundException;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.DomainResource;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Resource;

import java.lang.reflect.Method;

public class ResourceUtils {


    public static String getPatientId(DomainResource resource) throws PatientIdNotFoundException {
        // Check if the resource is an instance of Patient
        if (resource instanceof Patient) {
            Patient patient = (Patient) resource;
            return "Patient/"+patient.getId();
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
                    return (String) getReferenceMethod.invoke(subject);
                }
            }
        } catch (Exception e) {
            // Handle reflection exceptions
            e.printStackTrace();
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
