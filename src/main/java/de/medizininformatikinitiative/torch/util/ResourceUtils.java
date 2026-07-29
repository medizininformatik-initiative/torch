package de.medizininformatikinitiative.torch.util;

import de.medizininformatikinitiative.torch.TargetClassCreationException;
import de.medizininformatikinitiative.torch.exceptions.PatientIdNotFoundException;
import de.medizininformatikinitiative.torch.model.extraction.ExtractionId;
import de.medizininformatikinitiative.torch.model.extraction.IdentifierReference;
import org.hl7.fhir.r4.model.Base;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Consent;
import org.hl7.fhir.r4.model.DomainResource;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Resource Utils to extract referenceValidity and IDs from Resources
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

    /**
     * Strips version information from a FHIR canonical URL.
     *
     * @param url the potentially versioned URL
     * @return the URL with version information removed
     */
    public static String stripVersion(String url) {
        int pipeIndex = url.indexOf('|');
        return pipeIndex == -1 ? url : url.substring(0, pipeIndex);
    }


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
        if (resource instanceof DomainResource domainResource) {
            return patientId(domainResource);
        }
        throw new PatientIdNotFoundException("First entry in bundle is not a DomainResource");
    }

    public static ExtractionId getRelativeURL(Resource resource) {
        try {
            return new ExtractionId(resource.fhirType(), resource.getIdPart());
        } catch (NullPointerException e) {
            throw new IllegalArgumentException("Resource cannot be null");
        }
    }

    /**
     * Reads the {@code identifier} values of a resource generically, without depending on a resource-type-specific
     * {@code getIdentifier()} accessor.
     *
     * @param resource resource to read identifiers from
     * @return the resource's identifier values, or an empty list if it has none
     */
    public static List<Identifier> getIdentifiers(Resource resource) {
        return resource.children().stream()
                .filter(child -> "identifier".equals(child.getName()))
                .flatMap(child -> child.getValues().stream())
                .filter(Identifier.class::isInstance)
                .map(Identifier.class::cast)
                .toList();
    }

    /**
     * Indexes resources by their {@code identifier} values, so a {@code Reference.identifier}-only value can be
     * resolved back to the resource(s) it refers to. A given identifier may index more than one resource if the
     * same identifier value happens to be shared across resources in the collection.
     *
     * @param resources resources to index, e.g. all resources currently held by a bundle
     * @return identifier to the {@link ExtractionId}s of resources carrying it
     */
    public static Map<IdentifierReference, Set<ExtractionId>> indexByIdentifier(Collection<Resource> resources) {
        Map<IdentifierReference, Set<ExtractionId>> index = new HashMap<>();
        for (Resource resource : resources) {
            ExtractionId id = getRelativeURL(resource);
            for (Identifier identifier : getIdentifiers(resource)) {
                if (!identifier.hasValue()) {
                    continue;
                }
                IdentifierReference key = new IdentifierReference(identifier.getSystem(), identifier.getValue());
                index.computeIfAbsent(key, k -> new HashSet<>()).add(id);
            }
        }
        return index;
    }

    /**
     * Creates a new instance of a FHIR DomainResource subclass.
     *
     * @param resourceClass Class of the FHIR resource
     * @return New instance of the specified FHIR resource class
     * @throws TargetClassCreationException If instantiation fails
     */
    public static <T extends DomainResource> T createTargetResource(Class<T> resourceClass) throws TargetClassCreationException {
        try {
            return resourceClass.getDeclaredConstructor().newInstance();
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException |
                 NoSuchMethodException e) {
            throw new TargetClassCreationException(resourceClass);
        }
    }

    /**
     * Reflects the method with one param for a given object used for setter and add extension methods
     *
     * @param obj        object to be reflected
     * @param methodName method to be found
     * @return Method
     */
    public static Method getMethodWithOneParam(Object obj, String methodName) throws NoSuchMethodException {
        for (Method method : obj.getClass().getMethods()) {
            if (method.getName().equals(methodName) && method.getParameterCount() == 1) {
                return method;
            }
        }
        throw new NoSuchMethodException("No such method with one parameter: " + methodName);
    }

    public static void setField(Base base, String fieldName, Extension extension) {
        try {
            String capitalized = fieldName.substring(0, 1).toUpperCase() + fieldName.substring(1);
            String setterName = "set" + capitalized;


            Method setter = ResourceUtils.getMethodWithOneParam(base, setterName);


            Type[] genericParameterTypes = setter.getGenericParameterTypes();

            Object valueToSet;

            if (genericParameterTypes.length == 1 && genericParameterTypes[0] instanceof ParameterizedType paramType) {
                // Handle List<T>
                Type actualType = paramType.getActualTypeArguments()[0];

                Class<?> genericClass = Class.forName(actualType.getTypeName());
                Object instance = genericClass.getDeclaredConstructor().newInstance();
                Method addExtension = ResourceUtils.getMethodWithOneParam(instance, "addExtension");


                List<Object> list = new ArrayList<>();
                addExtension.invoke(instance, extension);
                list.add(instance);
                valueToSet = list;

            } else {
                // Handle single object
                Class<?> paramClass = setter.getParameterTypes()[0];


                Object instance = paramClass.getDeclaredConstructor().newInstance();
                Method addExtension = ResourceUtils.getMethodWithOneParam(instance, "addExtension");


                addExtension.invoke(instance, extension);
                valueToSet = instance;
            }

            // Call the setter
            setter.invoke(base, valueToSet);


        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException |
                 InstantiationException e) {
            logger.error("RESOURCE_REFLECTION_01 Could not set field: {} in class {} due to: {}", fieldName, base.getClass().getSimpleName(), e.getMessage());
        } catch (ClassNotFoundException e) {
            logger.error("RESOURCE_REFLECTION_02 Class not Found for {} {}", fieldName, base.getClass().getSimpleName());
            throw new RuntimeException(e);
        }
    }


}
