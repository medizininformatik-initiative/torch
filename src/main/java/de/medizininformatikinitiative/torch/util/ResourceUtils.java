package de.medizininformatikinitiative.torch.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.medizininformatikinitiative.torch.exceptions.PatientIdNotFoundException;
import org.hl7.fhir.r4.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;

/**
 * Resource Utils to extract References and IDs from Resources
 */
public class ResourceUtils {

    private static final Logger logger = LoggerFactory.getLogger(ResourceUtils.class);


    public static String patientId(DomainResource resource) throws PatientIdNotFoundException {

        return switch (resource) {
            case Patient p -> p.getIdPart();
            case Consent c -> {
                if (c.hasPatient()) {
                    yield getPatientReference(c.getPatient().getReference());
                } else {
                    throw new PatientIdNotFoundException("Patient ID not found in the given Consent resource");
                }
            }
            default -> getPatientIdViaReflection(resource);
        };

    }

    //TODO: FHIRPath?
    private static String getPatientIdViaReflection(DomainResource resource) throws PatientIdNotFoundException {

        try {
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
                } else {
                    throw new PatientIdNotFoundException("Patient Reference not found for Resource of Type " + resource.getResourceType());
                }

            } else {
                throw new PatientIdNotFoundException("Patient Reference not found for Resource of Type " + resource.getResourceType());
            }

        } catch (NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException |
                 InvocationTargetException e) {
            throw new PatientIdNotFoundException("Patient Reference not found for Resource of Type " + resource.getResourceType());
        }
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


    public static List<ElementDefinition> getElementsByPath(String path, StructureDefinition.StructureDefinitionSnapshotComponent snapshot) {
        if (path == null) {
            return Collections.emptyList();
        }
        List<ElementDefinition> matchingElements = new ArrayList<>();
        for (ElementDefinition ed : snapshot.getElement()) {
            if (path.equals(ed.getPath()) || (path + "[x]").equals(ed.getPath())) {
                matchingElements.add(ed);
            }
        }
        return List.copyOf(matchingElements);
    }

    public static List<ElementDefinition> getElementById(String id, StructureDefinition.StructureDefinitionSnapshotComponent snapshot) {
        if (id == null) {
            return Collections.emptyList();
        }
        List<ElementDefinition> matchingElements = new ArrayList<>();
        for (ElementDefinition ed : snapshot.getElement()) {
            if (id.equals(ed.getId())) {
                matchingElements.add(ed);
            }
        }
        return List.copyOf(matchingElements);
    }


    /**
     * Reads a JSON file from the resources folder, extracts codes in the resource fields, and returns them as a list.
     * Used to read the compartment definition.
     *
     * @param fileName The name of the JSON file in the resources' folder.
     * @return A Set of extracted codes.
     * @throws IOException If the file cannot be read.
     */
    public static Set<String> extractCodes(String fileName) throws IOException {
        // Load the file from resources
        File file = new File(Objects.requireNonNull(ResourceUtils.class.getClassLoader().getResource(fileName)).getFile());

        // Parse JSON using Jackson
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode rootNode = objectMapper.readTree(file);

        // Extract codes from the "resource" array
        Set<String> codes = new HashSet<>();
        if (rootNode.has("resource")) {
            for (JsonNode resourceNode : rootNode.get("resource")) {
                if (resourceNode.has("code")) {
                    codes.add(resourceNode.get("code").asText());
                }
            }
        }
        return codes;
    }


}
