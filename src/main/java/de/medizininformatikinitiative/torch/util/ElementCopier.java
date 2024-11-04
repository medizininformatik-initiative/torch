package de.medizininformatikinitiative.torch.util;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.util.TerserUtil;
import ca.uhn.fhir.util.TerserUtilHelper;
import de.medizininformatikinitiative.torch.CdsStructureDefinitionHandler;
import de.medizininformatikinitiative.torch.exceptions.MustHaveViolatedException;
import de.medizininformatikinitiative.torch.exceptions.PatientIdNotFoundException;
import de.medizininformatikinitiative.torch.model.crtdl.Attribute;
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

/**
 * Copying class using FHIR Path and Terser from Hapi
 */
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
    public ElementCopier(CdsStructureDefinitionHandler handler, FhirContext ctx, FhirPathBuilder fhirPathBuilder) {
        this.handler = handler;
        this.ctx = ctx;
        this.pathBuilder = fhirPathBuilder;

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
        logger.trace("ProfileURL {}", profileurl.getFirst());
        StructureDefinition structureDefinition = handler.getDefinition(profileurl);
        logger.trace("Empty Structuredefinition? {} {}", structureDefinition.isEmpty(), profileurl.getFirst().getValue());
        List<String> legalExtensions = new LinkedList<>();


        ctx.newFhirPath().evaluate(structureDefinition, "StructureDefinition.snapshot.element.select(type.profile +'') ", StringType.class).forEach(stringType -> legalExtensions.add(stringType.getValue()));
        StructureDefinition.StructureDefinitionSnapshotComponent snapshot = structureDefinition.getSnapshot();
        ElementDefinition elementDefinition = snapshot.getElementById(attribute.attributeRef());

        TerserUtilHelper helper = TerserUtilHelper.newHelper(ctx, tgt);
        logger.trace("{} TGT set {}", id, tgt.getClass());
        logger.trace("{} Attribute FHIR PATH {}", id, attribute.attributeRef());


        try {
            logger.trace("Attribute Path {}", attribute.attributeRef());

            String fhirPath = pathBuilder.handleSlicingForFhirPath(attribute.attributeRef(), snapshot);
            logger.trace("FHIR PATH {}", fhirPath);

            List<Base> elements = ctx.newFhirPath().evaluate(src, fhirPath, Base.class);
            logger.trace("Elements received {}", fhirPath);
            if (elements.isEmpty()) {
                if (attribute.mustHave()) {
                    logger.debug("Must Have Violated Thrown in Copier for {} {}", attribute.attributeRef(), ResourceUtils.getPatientId(src));
                    throw new MustHaveViolatedException("Attribute " + attribute.attributeRef() + " must have a value");
                }
            } else {
                String terserFHIRPATH = pathBuilder.handleSlicingForTerser(attribute.attributeRef());

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
                                Base casted = factory.stringtoPrimitive(elements.getFirst().toString(), elementDefinition.getType().getFirst().getWorkingCode());
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
                    //check if fieldname or deeper in the branch
                    if (elementParts.length > 2) {


                        //Assume branching before element
                        //TODO Go back in branching

                        int endIndex = attribute.attributeRef().lastIndexOf(".");

                        if (endIndex != -1) {
                            String ParentPath = attribute.attributeRef().substring(0, endIndex);
                            logger.trace("ParentPath {}", ParentPath);
                            logger.trace("Elemente {}", snapshot.getElementByPath(ParentPath));
                            String type = snapshot.getElementByPath(ParentPath).getType().getFirst().getWorkingCode();
                            elements.forEach(element -> helper.setField(ParentPath, type, element));
                        }
                    } else {
                        logger.trace("Base Field to be Set {} ", elementParts.length);
                        // Convert the list to an array
                        IBase[] elementsArray = elements.toArray(new IBase[0]);
                        logger.trace("elementsArray {} ", elementsArray.length);
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
     * @param path                Path of the Element to be checked
     * @param legalExtensions     all extensions that are allowed within the resource TODO: Make them elementspecific
     * @param element             Element to check for extensions
     * @param structureDefinition StructureDefinition of the resource TODO: Needed to get the allowed extensions on element level
     */
    public void checkExtensions(String path, List<String> legalExtensions, Element element, StructureDefinition structureDefinition) {

        if (element.hasExtension()) {
            //TODO get Slicing and handle it properly
            List<Extension> extensions = element.getExtension();
            List<String> urlToBeRemoved = new LinkedList<>();
            extensions.forEach(extension -> {
                        String extensionURL = extension.getUrl();
                        if (!Objects.equals(extensionURL, "http://hl7.org/fhir/StructureDefinition/data-absent-reason")) {
                            if (!legalExtensions.contains(extensionURL)) {
                                logger.warn("Illegal Extensions Found " + extension.getUrl());
                                urlToBeRemoved.add(extension.getUrl());
                            }
                        }
                    }
            );

            urlToBeRemoved.forEach(element::removeExtension);
        }
        element.children().
                forEach(child ->
                        {
                            String childpath = path + "." + child.getName();
                            child.getValues().forEach(value -> {
                                if (value instanceof Element) {
                                    checkExtensions(childpath, legalExtensions, (Element) value, structureDefinition);
                                }
                            });
                        }
                );


    }


    /**
     * Setup for recursive element copy.
     * TODO: Maybe needed for more involved copying
     *
     * @param attribute Attribute to be handled
     * @param tgt       tgt Resource
     * @param elements  Elements to be handled
     * @param snapshot  Structure Definition Snapshot
     * @return Resulting Resource
     * @throws InvocationTargetException Recursive Copy error
     * @throws IllegalAccessException    Recursive Copy error
     */
    public DomainResource copyInit(Attribute attribute, Resource tgt, List<Element> elements, StructureDefinition.StructureDefinitionSnapshotComponent snapshot) throws InvocationTargetException, IllegalAccessException {
        String[] IDparts = attribute.attributeRef().split("\\.");
        if (IDparts.length < 2) {
            return (DomainResource) tgt;
        }
        //Initiate resource path
        String path = IDparts[0];
        return (DomainResource) recursiveCopy(path, tgt, elements, snapshot, IDparts, 1);
    }

    /**
     * Copy elements from source to target resource recursively.
     * TODO: Maybe needed for more involved copying
     *
     * @param path     Element Path currently handled
     * @param parent   Parent Element
     * @param elements Elements to be copied
     * @param snapshot Structure Def snapshot
     * @param IDparts  Parts of ElementID
     * @param index    recursion index
     * @return Resulting Base
     * @throws InvocationTargetException Recursive Copy error
     * @throws IllegalAccessException    Recursive Copy error
     */
    private Base recursiveCopy(String path, Base parent, List<Element> elements, StructureDefinition.StructureDefinitionSnapshotComponent snapshot, String[] IDparts, int index) throws InvocationTargetException, IllegalAccessException {
        String childname = IDparts[index];
        boolean list = parent.getNamedProperty(childname).isList();
        if (index == IDparts.length - 1) {

            //Reached Element
            if (list) {
                Method listMethod = reflectListSetter(parent.getClass(), childname);
                assert listMethod != null;
                listMethod.invoke(parent, elements);
            }

            parent.setProperty(childname, elements.getFirst());

            return parent;
        } else {
            //System.out.println("Path " + path + " Childname " + childname + " Index " + index);
            path += "." + childname;
            index++;

            Property child = parent.getChildByName(childname);
            //TODO Handle lists?
            if (child.hasValues()) {
                String finalPath = path;
                int finalIndex = index;
                child.getValues().forEach(value -> {
                    try {
                        recursiveCopy(finalPath, value, elements, snapshot, IDparts, finalIndex);
                    } catch (InvocationTargetException | IllegalAccessException e) {
                        throw new RuntimeException(e);
                    }
                });
                return parent;
            } else {
                Base newElement = factory.createElement(snapshot.getElementByPath(path).getType().getFirst().getWorkingCode());
                parent.setProperty(getElementName(path), newElement);
                recursiveCopy(path, newElement, elements, snapshot, IDparts, index);
                return parent;
            }

        }


    }
}

