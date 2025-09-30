package de.medizininformatikinitiative.torch.consent;

import ca.uhn.fhir.context.FhirContext;
import com.fasterxml.jackson.databind.JsonNode;
import de.medizininformatikinitiative.torch.exceptions.ConsentViolatedException;
import de.medizininformatikinitiative.torch.exceptions.PatientIdNotFoundException;
import de.medizininformatikinitiative.torch.exceptions.ReferenceToPatientException;
import de.medizininformatikinitiative.torch.model.consent.PatientBatchWithConsent;
import de.medizininformatikinitiative.torch.model.consent.Period;
import de.medizininformatikinitiative.torch.model.management.PatientResourceBundle;
import de.medizininformatikinitiative.torch.util.ResourceUtils;
import org.hl7.fhir.r4.model.Base;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.DomainResource;
import org.hl7.fhir.r4.model.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static java.util.Objects.requireNonNull;

public class ConsentValidator {

    private static final Logger logger = LoggerFactory.getLogger(ConsentValidator.class);

    private final FhirContext ctx;
    private final JsonNode resourceToField;

    public ConsentValidator(FhirContext ctx, JsonNode resourceToField) {
        this.ctx = requireNonNull(ctx);
        this.resourceToField = requireNonNull(resourceToField);
    }

    /**
     * Wrapper function that checks whether the provided {@link DomainResource} complies with the patient's consents.
     *
     * <p>This method extracts the relevant {@link PatientResourceBundle} for the patient associated with the resource
     * and delegates the consent compliance check to the existing {@code checkConsent} method.
     *
     * @param resource                The FHIR {@link DomainResource} to check for consent compliance.
     * @param patientBatchWithConsent A batch containing consent information structured by patient ID.
     * @return {@code true} if the resource complies with the consents; {@code false} otherwise.
     */
    public boolean checkConsent(DomainResource resource, PatientBatchWithConsent patientBatchWithConsent) {
        // Extract the patient ID from the resource
        String patientID;
        try {
            patientID = ResourceUtils.patientId(resource);
        } catch (PatientIdNotFoundException e) {
            logger.error("Patient ID not found in resource: {}", e.getMessage());
            return false;
        }

        // Retrieve the PatientResourceBundle for the given patient ID
        PatientResourceBundle patientResourceBundle = patientBatchWithConsent.bundles().get(patientID);

        if (patientResourceBundle == null) {
            logger.warn("No PatientResourceBundle found for patient ID: {}", patientID);
            return false;
        }

        // Delegate the consent check to the existing checkConsent method
        return checkConsent(resource, patientResourceBundle);
    }

    public boolean checkConsent(DomainResource resource, PatientResourceBundle patientResourceBundle) {
        JsonNode fieldValue = null;
        if (resourceToField.has(resource.getResourceType().toString())) {
            logger.trace("Handling the following Profile {}", resource.getResourceType());
            fieldValue = resourceToField.get(resource.getResourceType().toString());
        }

        if (fieldValue == null) {
            logger.warn("No matching ResourceType found for resource of type: {}", resource.getResourceType());
            return false;
        }
        if (fieldValue.asText().isEmpty()) {
            logger.trace("Field value is empty, consent is automatically granted if patient has consents in general.");
            return true;
        }

        List<Base> values = ctx.newFhirPath().evaluate(resource, fieldValue.asText(), Base.class);

        for (Base value : values) {
            Period period = switch (value.getClass().getSimpleName()) {
                case "Period" -> Period.fromHapi((org.hl7.fhir.r4.model.Period) value);
                case "DateTimeType" -> Period.fromHapi((DateTimeType) value);
                default -> throw new IllegalArgumentException("No valid Date Time Value found");
            };
            boolean hasValidConsent = patientResourceBundle.consentPeriods().within(period);
            if (hasValidConsent) {
                return true;
            }
        }
        return false;
    }

    /**
     * For a resource it checks if the resource is part of the bundle it claims to be.
     *
     * <p> When a resource is loaded it is checked if the resource is a patient or core Resource
     * (core Resources should not link to patient resources) and checks in case of patient resources
     * if it fits the consent and is assigned to the correct patient.
     *
     * @param patientBundle bundle to which the loaded resource should belong
     * @param applyConsent  flag if batch has a consent check
     * @param resource      resource to check
     * @return true if fitting otherwise it throws errors
     */
    public boolean checkPatientIdAndConsent(PatientResourceBundle patientBundle, boolean applyConsent, Resource resource) throws PatientIdNotFoundException, ConsentViolatedException, ReferenceToPatientException {
        String resourcePatientId = ResourceUtils.patientId((DomainResource) resource);
        if (!resourcePatientId.equals(patientBundle.patientId())) {
            throw new ReferenceToPatientException("Patient loaded reference belonging to another patient");
        }

        if (applyConsent && !checkConsent((DomainResource) resource, patientBundle)) {
            throw new ConsentViolatedException("Consent Violated in Patient Resource");
        }
        return true;
    }
}
