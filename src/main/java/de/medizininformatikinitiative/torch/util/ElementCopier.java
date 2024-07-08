package de.medizininformatikinitiative.torch.util;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import ca.uhn.fhir.util.TerserUtil;
import ca.uhn.fhir.util.TerserUtilHelper;
import de.medizininformatikinitiative.torch.CdsStructureDefinitionHandler;
import de.medizininformatikinitiative.torch.model.Attribute;
import de.medizininformatikinitiative.torch.exceptions.MustHaveViolatedException;
import org.hl7.fhir.r4.model.*;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

import static de.medizininformatikinitiative.torch.util.CopyUtils.*;
import static de.medizininformatikinitiative.torch.util.FhirPathBuilder.cleanFhirPath;
import static de.medizininformatikinitiative.torch.util.FhirPathBuilder.handleSlicingForFhirPath;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ElementCopier {
    private static final Logger logger = LoggerFactory.getLogger(ElementCopier.class);

    private final FhirContext ctx;
    private final IParser parser;

    private static final ElementFactory factory = new ElementFactory();

    CdsStructureDefinitionHandler handler;

    Slicing slicing;


    /**
     * Constructor
     *
     * @param handler, contains all structuredefinition and FHIR ctx
     */
    public ElementCopier(CdsStructureDefinitionHandler handler) {
        this.handler = handler;
        this.ctx = handler.ctx;
        this.parser = ctx.newJsonParser().setPrettyPrint(true);
        this.slicing = new Slicing(handler);

    }


    /**
     * @param src       Source Resource to copy from
     * @param tgt       Target Resource to copy to
     * @param attribute Attribute to copy containing FHIR Path and if it is a mandatory element.
     * @return Target Resource with copied elements
     * @throws MustHaveViolatedException if mandatory element is missing
     */
    public void copy(DomainResource src, DomainResource tgt, Attribute attribute) throws MustHaveViolatedException {
        CanonicalType profileurl = src.getMeta().getProfile().get(0);
        StructureDefinition structureDefinition = handler.getDefinition(String.valueOf(profileurl.getValue()));
        logger.debug("Empty Structuredefinition? {}", structureDefinition.isEmpty());
        List<String> legalExtensions = new LinkedList<>();
        ctx.newFhirPath().evaluate(structureDefinition, "StructureDefinition.snapshot.element.select(type.profile +'') ", StringType.class).forEach(stringType -> legalExtensions.add(stringType.getValue()));
        StructureDefinition.StructureDefinitionSnapshotComponent snapshot = structureDefinition.getSnapshot();

        TerserUtilHelper helper = TerserUtilHelper.newHelper(ctx, tgt);
        logger.debug("TGT set {}", tgt.getClass());
        logger.debug("Attribute FHIR PATH {}", attribute.getAttributeRef());

        String fhirPath = cleanFhirPath(handleSlicingForFhirPath(attribute.getAttributeRef(),false));
        logger.debug("FHIR PATH {}", fhirPath);
        List<Base> elements = ctx.newFhirPath().evaluate(src,fhirPath , Base.class);
        //TODO Check Extensions on Element Level
        elements.forEach(element -> {
            if (element instanceof Element) {
                checkExtensions(attribute.getAttributeRef(), legalExtensions, (Element) element, structureDefinition);

            }
        });
        if (elements.isEmpty()) {
            //System.out.println("Elements Empty");
            if (attribute.isMustHave()) {
                throw new MustHaveViolatedException("Attribute " + attribute.getAttributeRef() + " must have a value");
            }
        } else {
            String shorthandFHIRPATH = handleSlicingForFhirPath(attribute.getAttributeRef(),true);

            DomainResource finalTgt = tgt;
            if (elements.size() == 1) {

                if(shorthandFHIRPATH.endsWith("[x]")){
                    String type = capitalizeFirstLetter(elements.get(0).fhirType());
                    shorthandFHIRPATH = shorthandFHIRPATH.replace("[x]", type);
                }
                TerserUtil.setFieldByFhirPath(ctx.newTerser(), shorthandFHIRPATH, finalTgt, elements.get(0));
            } else {
                //Assume branching before element

                int endIndex = attribute.getAttributeRef().lastIndexOf(".");

                if (endIndex != -1) {
                    String ParentPath = attribute.getAttributeRef().substring(0, endIndex); // not forgot to put check if(endIndex != -1)
                    logger.debug("ParentPath {}", ParentPath);
                    logger.debug("Elemente {}", snapshot.getElementByPath(ParentPath));
                    String type = snapshot.getElementByPath(ParentPath).getType().get(0).getWorkingCode();
                    elements.forEach(element -> {
                        helper.setField(ParentPath, type, element);
                    });
                }


            }


/*
            try {
                copyInit(attribute, tgt, elements, snapshot);
            } catch (InvocationTargetException e) {
                throw new RuntimeException(e);
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
*/
        }

    }

    /**
     * @param path
     * @param legalExtensions     all extensions that are allowed within the resource TODO: Make them elementspecific
     * @param element             Element to check for extensions
     * @param structureDefinition StructureDefinition of the resource TODO: Needed to get the allowed extensions on element level
     */
    public void checkExtensions(String path, List<String> legalExtensions, Element element, StructureDefinition structureDefinition) {

        if (element.hasExtension()) {
            //TODO get Slicing and handle it properly
            List<Extension> extensions = element.getExtension();
            List urlToBeRemoved = new LinkedList();
            extensions.forEach(extension -> {
                        String extensionURL = extension.getUrl();
                        //System.out.println("Extension " +extensionURL );
                        if (!Objects.equals(extensionURL, "http://hl7.org/fhir/StructureDefinition/data-absent-reason")) {
                            if (!legalExtensions.contains(extensionURL)) {
                                System.out.println("Illegal Extensions Found " + extension.getUrl());
                                urlToBeRemoved.add(extension.getUrl());
                            }
                        }
                    }
            );

            urlToBeRemoved.forEach(url -> element.removeExtension((String) url));
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
     * @param attribute
     * @param tgt
     * @param elements
     * @param snapshot
     * @return
     * @throws InvocationTargetException
     * @throws IllegalAccessException
     */
    public DomainResource copyInit(Attribute attribute, Resource tgt, List<Element> elements, StructureDefinition.StructureDefinitionSnapshotComponent snapshot) throws InvocationTargetException, IllegalAccessException {
        String[] IDparts = attribute.getAttributeRef().split("\\.");
        if (IDparts.length < 2) {
            return (DomainResource) tgt;
        }
        //Initiate resourcepath
        String path = IDparts[0];
        DomainResource result = (DomainResource) recursiveCopy(path, tgt, elements, snapshot, IDparts, 1);
        return result;
    }

    /**
     * Copy elements from source to target resource recursively.
     * TODO: Maybe needed for more involved copying
     *
     * @param path
     * @param parent
     * @param elements
     * @param snapshot
     * @param IDparts
     * @param index
     * @return
     * @throws InvocationTargetException
     * @throws IllegalAccessException
     */
    private Base recursiveCopy(String path, Base parent, List<Element> elements, StructureDefinition.StructureDefinitionSnapshotComponent snapshot, String[] IDparts, int index) throws InvocationTargetException, IllegalAccessException {
        String childname = IDparts[index];
        boolean list = false;
        if (parent.getNamedProperty(childname).isList()) {
            list = true;
        }
        if (index == IDparts.length - 1) {

            //Reached Element
            if (list) {
                Method listMethod = reflectListSetter(parent.getClass(), childname);
                listMethod.invoke(parent, elements);
            }

            parent.setProperty(childname, elements.get(0));

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
                    } catch (InvocationTargetException e) {
                        throw new RuntimeException(e);
                    } catch (IllegalAccessException e) {
                        throw new RuntimeException(e);
                    }
                });
                return parent;
            } else {
                //Create new element
                //TODO Handle slicing
                Base newElement = factory.createElement(snapshot.getElementByPath(path).getType().get(0).getWorkingCode());
                parent.setProperty(getElementName(path), newElement);
                recursiveCopy(path, newElement, elements, snapshot, IDparts, index);
                return parent;
            }

        }


    }
}

