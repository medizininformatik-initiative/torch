package de.medizininformatikinitiative.torch.util;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.util.TerserUtil;
import ca.uhn.fhir.util.TerserUtilHelper;
import de.medizininformatikinitiative.torch.CdsStructureDefinitionHandler;
import de.medizininformatikinitiative.torch.exceptions.MustHaveViolatedException;
import de.medizininformatikinitiative.torch.exceptions.PatientIdNotFoundException;
import de.medizininformatikinitiative.torch.model.Attribute;
import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.instance.model.api.IBase;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

import static de.medizininformatikinitiative.torch.util.CopyUtils.*;

public class ElementCopier {
    private static final Logger logger = LoggerFactory.getLogger(ElementCopier.class);

    private final FhirContext ctx;

    private static final ElementFactory factory = new ElementFactory();

    CdsStructureDefinitionHandler handler;

    FhirPathBuilder pathBuilder;


    /**
     * Constructor
     *
     * @param handler, contains all structuredefinition and FHIR ctx
     */
    public ElementCopier(CdsStructureDefinitionHandler handler) {
        this.handler = handler;
        this.ctx = handler.ctx;
        this.pathBuilder = new FhirPathBuilder(handler);

    }

    /**
     * @param src       Source Resource to copy from
     * @param tgt       Target Resource to copy to
     * @param attribute Attribute to copy containing ElementID and if it is a mandatory element.
     * @throws MustHaveViolatedException if mandatory element is missing
     */
    public void copy(DomainResource src, DomainResource tgt, Attribute attribute) throws MustHaveViolatedException, PatientIdNotFoundException {
        String id = ResourceUtils.getPatientId(src);
        List<CanonicalType> profileurl = src.getMeta().getProfile();
        StructureDefinition structureDefinition = handler.getDefinition(profileurl);
        logger.debug("Empty Structuredefinition? {} {}", structureDefinition.isEmpty(), profileurl.getFirst().getValue());
        List<String> legalExtensions = new LinkedList<>();


        ctx.newFhirPath().evaluate(structureDefinition, "StructureDefinition.snapshot.element.select(type.profile +'') ", StringType.class).forEach(stringType -> legalExtensions.add(stringType.getValue()));
        StructureDefinition.StructureDefinitionSnapshotComponent snapshot = structureDefinition.getSnapshot();
        ElementDefinition elementDefinition = snapshot.getElementById(attribute.getAttributeRef());

        TerserUtilHelper helper = TerserUtilHelper.newHelper(ctx, tgt);
        logger.debug("{} TGT set {}", id, tgt.getClass());
        logger.debug("{} Attribute FHIR PATH {}", id, attribute.getAttributeRef());


        try {
            logger.debug("Attribute Path {}", attribute.getAttributeRef());

            String fhirPath = pathBuilder.handleSlicingForFhirPath(attribute.getAttributeRef(), snapshot);
            logger.debug("FHIR PATH {}", fhirPath);

            List<Base> elements = ctx.newFhirPath().evaluate(src, fhirPath, Base.class);
            logger.debug("Elements received {}", fhirPath);
            if (elements.isEmpty()) {
                if (attribute.isMustHave()) {
                    throw new MustHaveViolatedException("Attribute " + attribute.getAttributeRef() + " must have a value");
                }
            } else {
                String terserFHIRPATH = pathBuilder.handleSlicingForTerser(attribute.getAttributeRef());

                if (elements.size() == 1) {

                    if (terserFHIRPATH.endsWith("[x]")) {
                        logger.debug("Tersertobehandled {}", terserFHIRPATH);
                        String type = capitalizeFirstLetter(elements.getFirst().fhirType());
                        terserFHIRPATH = terserFHIRPATH.replace("[x]", type);
                    }
                    logger.debug("Setting {} {}", terserFHIRPATH, elements.getFirst().fhirType());
                    try {
                        TerserUtil.setFieldByFhirPath(ctx.newTerser(), terserFHIRPATH, tgt, elements.getFirst());
                    } catch (Exception e) {
                        if (elementDefinition.hasType()) {
                            elementDefinition.getType().getFirst().getWorkingCode();
                            //TODO
                            logger.debug("Element not recognized {} {}", terserFHIRPATH, elementDefinition.getType().getFirst().getWorkingCode());
                            try {
                                Base casted = factory.stringtoPrimitive(elements.getFirst().toString(), elementDefinition.getType().getFirst().getWorkingCode());
                                logger.debug("Casted {}", casted.fhirType());
                                TerserUtil.setFieldByFhirPath(ctx.newTerser(), terserFHIRPATH, tgt, casted);
                            } catch (Exception casterException) {
                                logger.warn("Element not recognized and cast unsupported currently  {} {} ", terserFHIRPATH, elementDefinition.getType().getFirst().getWorkingCode());
                                logger.warn("{} ", casterException);
                            }
                        } else {
                            logger.warn("Element has no known type ", terserFHIRPATH);
                        }


                    }

                } else {

                    logger.info("terserFHIRPATH {} ", terserFHIRPATH);
                    String[] elementParts = terserFHIRPATH.split("\\.");
                    //check if fieldname or deeper in the branch
                    if (elementParts.length > 2) {


                        //Assume branching before element
                        //TODO Go back in branching

                        int endIndex = attribute.getAttributeRef().lastIndexOf(".");

                        if (endIndex != -1) {
                            String ParentPath = attribute.getAttributeRef().substring(0, endIndex);
                            logger.debug("ParentPath {}", ParentPath);
                            logger.debug("Elemente {}", snapshot.getElementByPath(ParentPath));
                            String type = snapshot.getElementByPath(ParentPath).getType().getFirst().getWorkingCode();
                            elements.forEach(element -> helper.setField(ParentPath, type, element));
                        }
                    } else {
                        logger.debug("Base Field to be Set {} ", elementParts.length);
                        // Convert the list to an array
                        IBase[] elementsArray = elements.toArray(new IBase[0]);
                        logger.info("elementsArray {} ", elementsArray.length);
                        // Now pass the array as varargs
                        TerserUtil.setField(ctx, elementParts[1], (IBaseResource) tgt, elementsArray);
                    }


                }
            }
        } catch (NullPointerException e) {
            //FHIR Search Returns Null, if not result found
        } catch (FHIRException e) {
            logger.error("Unsupported Type", e);

        }

    }


    /**
     * @param src       Source Resource to copy from
     * @param tgt       Target Resource to copy to
     * @param attribute Attribute to copy containing ElementID and if it is a mandatory element.
     * @throws MustHaveViolatedException if mandatory element is missing
     */
    public void copyMod(DomainResource src, DomainResource tgt, Attribute attribute) throws MustHaveViolatedException, PatientIdNotFoundException {
        String id = ResourceUtils.getPatientId(src);
        List<CanonicalType> profileurl = src.getMeta().getProfile();
        StructureDefinition structureDefinition = handler.getDefinition(profileurl);
        logger.debug("Empty Structuredefinition? {} {}", structureDefinition.isEmpty(), profileurl.getFirst().getValue());
        List<String> legalExtensions = new LinkedList<>();


        ctx.newFhirPath().evaluate(structureDefinition, "StructureDefinition.snapshot.element.select(type.profile +'') ", StringType.class).forEach(stringType -> legalExtensions.add(stringType.getValue()));
        StructureDefinition.StructureDefinitionSnapshotComponent snapshot = structureDefinition.getSnapshot();
        ElementDefinition elementDefinition = snapshot.getElementById(attribute.getAttributeRef());

        TerserUtilHelper helper = TerserUtilHelper.newHelper(ctx, tgt);
        logger.debug("{} TGT set {}", id, tgt.getClass());
        logger.debug("{} Attribute FHIR PATH {}", id, attribute.getAttributeRef());


        try {
            logger.debug("Attribute Path {}", attribute.getAttributeRef());

            String fhirPath = pathBuilder.handleSlicingForFhirPath(attribute.getAttributeRef(), snapshot);
            logger.debug("FHIR PATH {}", fhirPath);

            List<Base> elements = ctx.newFhirPath().evaluate(src, fhirPath, Base.class);
            logger.debug("Elements received {}", fhirPath);
            if (elements.isEmpty()) {
                if (attribute.isMustHave()) {
                    throw new MustHaveViolatedException("Attribute " + attribute.getAttributeRef() + " must have a value");
                }
            } else {
                String terserFHIRPATH = pathBuilder.transformTerserPath(attribute.getAttributeRef());
                if (elements.size() == 1) {

                    logger.debug("Setting {} {}", terserFHIRPATH, elements.getFirst().fhirType());

                    try {

                        TerserUtil.setFieldByFhirPath(ctx.newTerser(), terserFHIRPATH, tgt, elements.getFirst());

                    } catch (Exception e) {
                        logger.error("Exception For setFieldbyFhirPath", e);
                        logger.warn("Element not recognized {} {}", terserFHIRPATH, elementDefinition.getType().getFirst().getWorkingCode());

                    }

                } else {
                    //Assume branching before element
                    //TODO Go back in branching
                    logger.info("terserFHIRPATH {} ", terserFHIRPATH);
                    String[] elementParts = terserFHIRPATH.split("\\.");
                    //check if fieldname or deeper in the branch
                    if (elementParts.length > 2) {
                        // Extract the parent path from the attribute reference
                        int endIndex = attribute.getAttributeRef().lastIndexOf(".");
                        String ParentPath = attribute.getAttributeRef().substring(0, endIndex);
                        logger.debug("ParentPath {}", ParentPath);
                        logger.debug("Elemente {}", snapshot.getElementByPath(ParentPath));
                        String type = snapshot.getElementByPath(ParentPath).getType().getFirst().getWorkingCode();
                        elements.forEach(element -> helper.setField(ParentPath, type, element));
                    } else {
                        logger.debug("Base Field to be Set {} ", elementParts.length);
                        // Convert the list to an array
                        IBase[] elementsArray = elements.toArray(new IBase[0]);

                        // Now pass the array as varargs
                        TerserUtil.setField(ctx, elementParts[1], (IBaseResource) tgt, elementsArray);
                    }


                }


            }

        } catch (MustHaveViolatedException mustEx) {
            throw mustEx;
        } catch (NullPointerException nullEx) {
            logger.debug("FHIR PATH returned nothing ", elementDefinition, nullEx);
            //Case FHIR PATH returns nothing
        } catch (Exception e) {
            logger.error("FHIR PATH for ID {} failed {}", elementDefinition, e);
            throw new RuntimeException(e);

        }
    }
}

