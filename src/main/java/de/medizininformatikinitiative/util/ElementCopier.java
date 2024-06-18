package de.medizininformatikinitiative.util;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import ca.uhn.fhir.util.TerserUtil;
import ca.uhn.fhir.util.TerserUtilHelper;
import de.medizininformatikinitiative.CDSStructureDefinitionHandler;
import de.medizininformatikinitiative.util.CRTDL.Attribute;
import de.medizininformatikinitiative.util.Exceptions.mustHaveViolatedException;
import org.hl7.fhir.instance.model.api.IBase;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.*;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

import static de.medizininformatikinitiative.util.CopyUtils.getElementName;
import static de.medizininformatikinitiative.util.CopyUtils.reflectListSetter;

public class ElementCopier {

    private static final FhirContext ctx = FhirContext.forR4();
    private static final IParser parser = ctx.newJsonParser().setPrettyPrint(true);

    private static final ElementFactory factory = new ElementFactory();

    CDSStructureDefinitionHandler handler;


    public ElementCopier(CDSStructureDefinitionHandler handler) {
        this.handler = handler;
    }


    public DomainResource copy(DomainResource src, DomainResource tgt, Attribute attribute) throws mustHaveViolatedException {
        CanonicalType profileurl = src.getMeta().getProfile().get(0);
        StructureDefinition structureDefinition = handler.getDefinition(String.valueOf(profileurl.getValue()));
        StructureDefinition.StructureDefinitionSnapshotComponent snapshot = structureDefinition.getSnapshot();
        TerserUtilHelper helper = TerserUtilHelper.newHelper(ctx, tgt);

        System.out.println("TGT set " + tgt.getClass());
        List<Element> elements = ctx.newFhirPath().evaluate(src, attribute.AttributeRef, Element.class);
        //TODO: Check for Extensions
        if (elements.isEmpty()) {
            System.out.println("Elements Empty");
            if (attribute.mustHave) {
                throw new mustHaveViolatedException("Attribute " + attribute.AttributeRef + " must have a value");

            }
        } else {
            DomainResource finalTgt = tgt;
            if (elements.size() == 1) {
                System.out.println("1 Element" + elements.get(0).fhirType());
                TerserUtil.setFieldByFhirPath(ctx.newTerser(), attribute.AttributeRef, finalTgt, elements.get(0));
            } else {
                //Assume branching before element
                System.out.println("Multiple Elements " + elements.size());
                int endIndex = attribute.AttributeRef.lastIndexOf(".");
                System.out.println("Endindex "+endIndex);
                if (endIndex != -1) {
                    String ParentPath = attribute.AttributeRef.substring(0, endIndex); // not forgot to put check if(endIndex != -1)
                    System.out.println("ParentPATH "+ParentPath);
                    String type = snapshot.getElementByPath(ParentPath).getType().get(0).getWorkingCode();
                    elements.forEach(element -> {
                        helper.setField(ParentPath, type, element);
                    });
                }





            }

            return tgt;
        }

        return null;

    }

    public Element checkExtensions(Attribute attribute, Element element, StructureDefinition.StructureDefinitionSnapshotComponent snapshot) {

        if (element.hasExtension()) {
            List<Extension> extensions = element.getExtension();

            for (Extension extension : extensions) {
                if (extension.getUrl().equals(attribute.AttributeRef)) {
                    return element;
                }
            }
        }
        return element;
    }



    public DomainResource copyInit(Attribute attribute, Resource tgt, List<Element> elements, StructureDefinition.StructureDefinitionSnapshotComponent snapshot) throws InvocationTargetException, IllegalAccessException {
        String[] IDparts = attribute.AttributeRef.split("\\.");
        if (IDparts.length < 2) {
            return (DomainResource) tgt;
        }
        //Initiate resourcepath
        String path = IDparts[0];
        DomainResource result = (DomainResource) recursiveCopy(path, tgt, elements, snapshot, IDparts, 1);
        return result;
    }

    private Base recursiveCopy(String path, Base parent, List<Element> elements, StructureDefinition.StructureDefinitionSnapshotComponent snapshot, String[] IDparts, int index) throws InvocationTargetException, IllegalAccessException {
        String childname = IDparts[index];
        boolean list = false;
        if (parent.getNamedProperty(childname).isList()) {
            list = true;
        }
        if (index == IDparts.length - 1) {
            //System.out.println("Parent "+parent.getClass());
            //Reached Element
            if (list) {
                Method listMethod = reflectListSetter(parent.getClass(), childname);
                listMethod.invoke(parent, elements);
            }

            Base returnvalue = parent.setProperty(childname, elements.get(0));
            //System.out.println("Returnvalue "+returnvalue.getClass());
            return parent;
        } else {
            System.out.println("Path " + path + " Childname " + childname + " Index " + index);
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

                //Base newElement = factory.create(snapshot.getElementByPath(path).getType().get(0).getWorkingCode(), path,parent);
                Base newElement = factory.createElement(snapshot.getElementByPath(path).getType().get(0).getWorkingCode());
                parent.setProperty(getElementName(path), newElement);
                recursiveCopy(path, newElement, elements, snapshot, IDparts, index);
                return parent;
            }

        }


    }
}

