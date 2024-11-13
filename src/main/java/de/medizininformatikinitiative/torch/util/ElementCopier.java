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
                            logger.warn("Element not recognized and cast unsupported currently  {} {} ", terserFHIRPATH, elementDefinition.getType().getFirst().getWorkingCode());
                            logger.warn("Caster Exception: ", casterException);
                        }
                    } else {
                        logger.warn("Element has no known type {}", terserFHIRPATH);
                    }
                }

            } else {

                logger.trace("terserFHIRPATH {} ", terserFHIRPATH);
                String[] elementParts = terserFHIRPATH.split("\\.");

                if (elementParts.length > 2) {

                    int endIndex = attribute.attributeRef().lastIndexOf(".");

                    if (endIndex != -1) {
                        String ParentPath = attribute.attributeRef().substring(0, endIndex);
                        logger.trace("ParentPath {}", ParentPath);
                        logger.trace("Elemente {}", snapshot.getElementByPath(ParentPath));
                        //String type = snapshot.getElementByPath(ParentPath).getType().getFirst().getWorkingCode();
                        elements.forEach(element -> helper.setField(ParentPath, element.fhirType(), element));
                    }
                } else {
                    logger.trace("Base Field to be Set {} ", elementParts.length);
                    // Convert the list to an array
                    IBase[] elementsArray = elements.toArray(new IBase[0]);
                    logger.trace("elementsArray {} ", elementsArray.length);
                    // Now pass the array as varargs
                    TerserUtil.setField(ctx, elementParts[1], tgt, elementsArray);
                }


            }
        }


    }

}

