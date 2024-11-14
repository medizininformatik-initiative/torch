package de.medizininformatikinitiative.torch.util;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.fhirpath.IFhirPath;
import ca.uhn.fhir.util.TerserUtil;
import ca.uhn.fhir.util.TerserUtilHelper;
import de.medizininformatikinitiative.torch.CdsStructureDefinitionHandler;
import de.medizininformatikinitiative.torch.exceptions.MustHaveViolatedException;
import de.medizininformatikinitiative.torch.model.crtdl.Attribute;
import org.hl7.fhir.instance.model.api.IBase;
import org.hl7.fhir.r4.model.Base;
import org.hl7.fhir.r4.model.CanonicalType;
import org.hl7.fhir.r4.model.DomainResource;
import org.hl7.fhir.r4.model.ElementDefinition;
import org.hl7.fhir.r4.model.StructureDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.List;

import static de.medizininformatikinitiative.torch.util.CopyUtils.capitalizeFirstLetter;

/**
 * Copying class using FHIR Path and Terser from Hapi
 */
public class ElementCopier {
    private static final Logger logger = LoggerFactory.getLogger(ElementCopier.class);

    private final FhirContext ctx;

    private final CdsStructureDefinitionHandler handler;

    private final FhirPathBuilder pathBuilder;
    private final IFhirPath fhirPathEngine;


    /**
     * Constructor
     *
     * @param handler, contains all structuredefinition and FHIR ctx
     */
    public ElementCopier(CdsStructureDefinitionHandler handler, FhirContext ctx, FhirPathBuilder fhirPathBuilder) {
        this.handler = handler;
        this.ctx = ctx;
        this.pathBuilder = fhirPathBuilder;
        this.fhirPathEngine = ctx.newFhirPath();

    }


    /**
     * @param src       Source Resource to copy from
     * @param tgt       Target Resource to copy to
     * @param attribute Attribute to copy containing ElementID and if it is a mandatory element.
     * @throws MustHaveViolatedException if mandatory element is missing
     */
    public <T extends DomainResource> void copy(T src, T tgt, Attribute attribute) throws MustHaveViolatedException {
        List<CanonicalType> profileurl = src.getMeta().getProfile();
        logger.trace("ProfileURL {}", profileurl.getFirst());
        StructureDefinition structureDefinition = handler.getDefinition(profileurl);
        logger.trace("Empty Structuredefinition? {} {}", structureDefinition.isEmpty(), profileurl.getFirst().getValue());


        StructureDefinition.StructureDefinitionSnapshotComponent snapshot = structureDefinition.getSnapshot();
        ElementDefinition elementDefinition = snapshot.getElementById(attribute.attributeRef());

        TerserUtilHelper helper = TerserUtilHelper.newHelper(ctx, tgt);


        logger.trace("Attribute Path {}", attribute.attributeRef());

        String[] fhirPaths = pathBuilder.handleSlicingForFhirPath(attribute.attributeRef(), snapshot);
        String fhirPath = fhirPaths[0];
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

            String terserFHIRPATH = fhirPaths[1];
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
                    if (elementDefinition.hasType()) {
                        elementDefinition.getType().getFirst().getWorkingCode();
                        logger.trace("Element not recognized {} {}", terserFHIRPATH, elementDefinition.getType().getFirst().getWorkingCode());
                        try {
                            Base casted = ElementFactory.stringtoPrimitive(elements.getFirst().toString(), elementDefinition.getType().getFirst().getWorkingCode());
                            logger.trace("Casted {}", casted.fhirType());
                            TerserUtil.setFieldByFhirPath(ctx.newTerser(), terserFHIRPATH, tgt, casted);
                        } catch (Exception casterException) {
                            logger.debug("Element not recognized and cast unsupported currently  {} {} ", terserFHIRPATH, elementDefinition.getType().getFirst().getWorkingCode());
                            logger.trace("Caster Exception: ", casterException);
                        }
                    } else {
                        logger.warn("Element has no known type {}", terserFHIRPATH);
                    }
                }

            } else {

                logger.trace("terserFHIRPATH {} ", terserFHIRPATH);
                String[] elementParts = terserFHIRPATH.split("\\.");
                int endIndex = terserFHIRPATH.lastIndexOf(".");

                if (elementParts.length > 2) {


                    if (endIndex != -1) {
                        String parentPath = terserFHIRPATH.substring(0, endIndex);
                        logger.trace("ParentPath {}", parentPath);
                        logger.trace("Elemente {}", snapshot.getElementByPath(parentPath));

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

