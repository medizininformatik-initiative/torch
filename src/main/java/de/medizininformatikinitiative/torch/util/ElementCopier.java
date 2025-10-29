package de.medizininformatikinitiative.torch.util;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.fhirpath.IFhirPath;
import ca.uhn.fhir.util.TerserUtil;
import de.medizininformatikinitiative.torch.exceptions.MustHaveViolatedException;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttribute;
import de.medizininformatikinitiative.torch.model.management.CopyTreeNode;
import org.hl7.fhir.instance.model.api.IBase;
import org.hl7.fhir.r4.model.Base;
import org.hl7.fhir.r4.model.DomainResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static de.medizininformatikinitiative.torch.util.CopyUtils.capitalizeFirstLetter;

/**
 * Copying class using FHIR Path and Terser from Hapi
 */
public class ElementCopier {
    private static final Logger logger = LoggerFactory.getLogger(ElementCopier.class);

    private final FhirContext ctx;

    private final IFhirPath fhirPathEngine;


    /**
     * Constructor
     *
     * @param ctx the FHIRContext to use for creating the FhirPathEngine
     */
    public ElementCopier(FhirContext ctx) {
        this.ctx = ctx;
        this.fhirPathEngine = ctx.newFhirPath();
    }


    /**
     * Copies elements from the source resource to the target resource according to the copy tree.
     *
     * @param src      Source Resource to copy from
     * @param tgt      Target Resource to copy to
     * @param copyTree Attribute tree describing which elements to copy
     */
    public void copy(Base src, Base tgt, CopyTreeNode copyTree) {
        Map<String, List<CopyTreeNode>> childMap = copyTree.children().stream()
                .collect(Collectors.groupingBy(CopyTreeNode::fieldName));

        for (var entry : new ArrayList<>(childMap.entrySet())) {
            String fieldName = entry.getKey();
            Map<Base, Base> processed = new LinkedHashMap<>();
            for (var child : entry.getValue()) {

                // Evaluate the elements in the source resource for this subpath
                List<Base> elements = fhirPathEngine.evaluate(src, child.fhirPath(), Base.class);
                if (elements.isEmpty()) continue;


                for (Base element : elements) {
                    Base targetElement = processed.get(element);
                    // Create a new instance or extract a child target
                    if (targetElement == null) {
                        targetElement = createEmptyElement(element.getClass());
                    }
                    if (targetElement.isPrimitive() || child.children().isEmpty()) {
                        // Leaf node or primitives are copied directly
                        targetElement = element.copy();
                    } else {
                        copy(element, targetElement, child);
                    }
                    processed.put(element, targetElement);
                }
            }

            // Set the field on the target reflectively
            CopyUtils.setFieldReflectively(tgt, fieldName, processed.values().stream().toList());
        }
    }


    public <T extends Base> T createEmptyElement(Class<T> clazz) {
        try {
            Constructor<T> ctor = clazz.getDeclaredConstructor();
            ctor.setAccessible(true); // allow instantiation even if private
            return ctor.newInstance();
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Cannot create empty instance of " + clazz, e);
        }
    }


    /**
     * @param src       Source Resource to copy from
     * @param tgt       Target Resource to copy to
     * @param attribute Attribute to copy containing ElementID and if it is a mandatory element.
     * @throws MustHaveViolatedException if mandatory element is missing
     */
    public <T extends DomainResource> void copy(T src, T tgt, AnnotatedAttribute attribute) throws MustHaveViolatedException {

        String fhirPath = attribute.fhirPath();
        logger.trace("FHIR PATH {}", fhirPath);

        List<Base> elements;
        elements = fhirPathEngine.evaluate(src, fhirPath, Base.class);

        logger.trace("Elements received {}", fhirPath);
        if (elements.isEmpty()) {
            logger.trace("Elements empty {}", fhirPath);
            if (attribute.mustHave()) {
                throw new MustHaveViolatedException("Attribute " + attribute.attributeRef() + " must have a value");
            }
        } else {

            String terserFHIRPATH = attribute.fhirPath();
            logger.trace("Terser FhirPath {}", terserFHIRPATH);
            if (elements.size() == 1) {

                if (terserFHIRPATH.endsWith("[x]")) {
                    logger.trace("Tersertobehandled {}", terserFHIRPATH);
                    String type = capitalizeFirstLetter(elements.getFirst().fhirType());
                    terserFHIRPATH = terserFHIRPATH.replace("[x]", type);
                }
                logger.trace("Setting {} {}", terserFHIRPATH, elements.getFirst().fhirType());
                try {
                    TerserUtil.setFieldByFhirPath(ctx.newTerser(), terserFHIRPATH, tgt, elements.getFirst());
                } catch (Exception e) {
                    logger.warn("Element has no known type {}", terserFHIRPATH);
                }

            } else {

                logger.trace("terserFHIRPATH {} ", terserFHIRPATH);
                String[] elementParts = terserFHIRPATH.split("\\.");
                int endIndex = terserFHIRPATH.lastIndexOf(".");

                if (elementParts.length > 2) {


                    if (endIndex != -1) {
                        String parentPath = terserFHIRPATH.substring(0, endIndex);
                        logger.trace("ParentPath {}", parentPath);

                        IBase parentElement = TerserUtil.getFirstFieldByFhirPath(ctx, parentPath, tgt);

                        if (parentElement == null) {
                            TerserUtil.setFieldByFhirPath(ctx.newTerser(), terserFHIRPATH, tgt, elements.getFirst());
                            parentElement = TerserUtil.getFirstFieldByFhirPath(ctx, parentPath, tgt);
                        }
                        logger.trace("parentElement Type {}", parentElement.fhirType());

                        setListOnParentField(parentElement, terserFHIRPATH.substring(endIndex + 1), elements);
                    }
                } else {

                    logger.trace("Base Field to be Set {} ", elementParts.length);

                    IBase[] elementsArray = elements.toArray(new IBase[0]);
                    logger.trace("elementsArray {} ", elementsArray.length);
                    // Now pass the array as varargs
                    TerserUtil.setField(ctx, elementParts[1], tgt, elementsArray);
                }


            }
        }


    }

    public void setListOnParentField(IBase parentField, String childPath, List<?> list) {
        try {
            String setterName = "set" + Character.toUpperCase(childPath.charAt(0)) + childPath.substring(1);
            Method setterMethod = null;
            for (Method method : parentField.getClass().getMethods()) {
                if (method.getName().equals(setterName) && method.getParameterCount() == 1 &&
                        List.class.isAssignableFrom(method.getParameterTypes()[0])) {
                    setterMethod = method;
                    break;
                }
            }

            if (setterMethod == null) {
                throw new NoSuchMethodException("No setter method found for child path " + childPath + " with a List parameter.");
            }
            setterMethod.invoke(parentField, list);
            logger.trace("Successfully set the list on parentField {} using setter {}", parentField.fhirType(), setterName);

        } catch (Exception e) {
            logger.error("Failed to set list on parent field {} for child path {}", parentField.fhirType(), childPath, e);
        }
    }


}

