package de.medizininformatikinitiative.torch.util;

import de.medizininformatikinitiative.torch.TargetClassCreationException;
import de.medizininformatikinitiative.torch.exceptions.PatientIdNotFoundException;
import de.medizininformatikinitiative.torch.model.management.ResourceGroupWrapper;
import org.hl7.fhir.r4.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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

    public static String getRelativeURL(Resource resource) {
        return resource.fhirType() + "/" + resource.getIdPart();

    }

    public static String getRelativeURL(ResourceGroupWrapper resourceWrapper) {
        return getRelativeURL(resourceWrapper.resource());

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
     * @param base base to be casted to its fhirtype
     * @param <T>  fhirType e.g. Medication
     * @return cast of base
     */
    @SuppressWarnings("unchecked")
    public static <T> T castBaseToItsFhirType(Base base) {
        String typeName = base.fhirType(); // e.g., "Patient", "Observation"
        String basePackage = DomainResource.class.getPackage().getName(); // dynamically resolves package

        try {
            Class<?> clazz = Class.forName(basePackage + "." + typeName);
            if (clazz.isInstance(base)) {
                return (T) clazz.cast(base);
            } else {
                throw new IllegalArgumentException("Base is not instance of " + typeName);
            }
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Unknown FHIR type: " + typeName, e);
        }
    }

    public static Method getSetterMethod(Object obj, String setterName) {
        for (Method method : obj.getClass().getMethods()) {
            if (method.getName().equals(setterName) && method.getParameterCount() == 1) {
                return method;
            }
        }
        return null; // or throw exception
    }

    public static Base setField(Base base, String fieldName, Extension extension) {
        try {
            String capitalized = fieldName.substring(0, 1).toUpperCase() + fieldName.substring(1);
            String setterName = "set" + capitalized;
            System.out.println("Looking for setter: " + setterName + " on class: " + base.getClass().getSimpleName());

            Method setter = ResourceUtils.getSetterMethod(base, setterName);

            if (setter != null) {
                System.out.println("Found setter: " + setter.getName());
                Type[] genericParameterTypes = setter.getGenericParameterTypes();

                Object valueToSet;

                if (genericParameterTypes.length == 1 && genericParameterTypes[0] instanceof ParameterizedType) {
                    // Handle List<T>
                    ParameterizedType paramType = (ParameterizedType) genericParameterTypes[0];
                    Type actualType = paramType.getActualTypeArguments()[0];
                    System.out.println("Setter takes a generic List parameter: " + actualType.getTypeName());

                    Class<?> genericClass = Class.forName(actualType.getTypeName());
                    Object instance = genericClass.getDeclaredConstructor().newInstance();
                    System.out.println("Instantiated element of list: " + instance.getClass().getSimpleName());

                    List<Object> list = new ArrayList<>();
                    list.add(instance);
                    valueToSet = list;

                } else {
                    // Handle single object
                    Class<?> paramClass = setter.getParameterTypes()[0];
                    System.out.println("Setter takes a single parameter of type: " + paramClass.getSimpleName());

                    Object instance = paramClass.getDeclaredConstructor().newInstance();
                    System.out.println("Instantiated parameter object: " + instance.getClass().getSimpleName());

                    valueToSet = instance;
                }

                // Call the setter
                System.out.println("Invoking setter with value: " + valueToSet);
                setter.invoke(base, valueToSet);
                System.out.println("Setter invoked successfully!");

            } else {
                System.out.println("No setter found for field: " + fieldName);
            }

        } catch (Exception e) {
            System.out.println("Error while setting field: " + fieldName);
            e.printStackTrace();
        }

        return base;
    }


}
