package de.medizininformatikinitiative.util;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import de.medizininformatikinitiative.CDSStructureDefinitionHandler;
import de.medizininformatikinitiative.util.CRTDL.Attribute;
import de.medizininformatikinitiative.util.Exceptions.elementNotDefinedException;
import de.medizininformatikinitiative.util.Exceptions.mustHaveViolatedException;
import org.hl7.fhir.r4.model.*;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

import static de.medizininformatikinitiative.util.CopyUtils.getElementName;
import static de.medizininformatikinitiative.util.CopyUtils.reflectListSetter;

public class ElementCopier {

    private static final FhirContext ctx = FhirContext.forR4();
    private static final IParser parser = ctx.newJsonParser().setPrettyPrint(true);

    private static final Factory factory = new Factory();

    CDSStructureDefinitionHandler handler;


    public ElementCopier(CDSStructureDefinitionHandler handler) {
        this.handler = handler;
    }


    public DomainResource copy(DomainResource src, Attribute attribute) throws mustHaveViolatedException, elementNotDefinedException {
        CanonicalType profileurl = src.getMeta().getProfile().get(0);
        StructureDefinition structureDefinition = handler.getDefinition(String.valueOf(profileurl.getValue()));
        StructureDefinition.StructureDefinitionSnapshotComponent snapshot = structureDefinition.getSnapshot();
        try {
            Class<? extends DomainResource> resourceClass = src.getClass().asSubclass(DomainResource.class);
            DomainResource tgt = resourceClass.getDeclaredConstructor().newInstance();
            System.out.println("TGT set "+tgt.getClass());
            List<Element> elements = ctx.newFhirPath().evaluate(src, attribute.AttributeRef, Element.class);
            if (elements.isEmpty()) {
                System.out.println("Elements Empty");
                if (attribute.mustHave) {
                    throw new mustHaveViolatedException("Attribute " + attribute.AttributeRef + " must have a value");

                }
            } else {
                tgt = copyRecursively(attribute, tgt, elements, snapshot);

                return tgt;
            }

        } catch (InstantiationException | IllegalAccessException e) {
            e.printStackTrace();
            // Handle exceptions accordingly
        } catch (InvocationTargetException | NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
        return  null;

    }

    public DomainResource copyRecursively(Attribute attribute, Resource tgt, List<Element> elements, StructureDefinition.StructureDefinitionSnapshotComponent snapshot) throws InvocationTargetException, IllegalAccessException {
        String[] IDparts = attribute.AttributeRef.split("\\.");
        if(IDparts.length<2){
            return (DomainResource) tgt;
        }
        //Initiate resourcepath
        String path = IDparts[0];
        DomainResource result = (DomainResource) recursiveWanderer(path, tgt, elements, snapshot, IDparts, 1);
        return result;
    }

    private Base recursiveWanderer(String path, Base parent,List<Element> elements, StructureDefinition.StructureDefinitionSnapshotComponent snapshot, String[] IDparts, int index) throws InvocationTargetException, IllegalAccessException {
        String childname = IDparts[index];
        boolean list=false;
        if(parent.getNamedProperty(childname).isList()){
            list=true;
        }
        if (index==IDparts.length-1) {
            System.out.println("Parent "+parent.getClass());
            if(list){
                Method listMethod = reflectListSetter(parent.getClass(), childname);
                listMethod.invoke(parent, elements);
            }
            Base returnvalue = parent.setProperty(childname, elements.get(0));
            System.out.println("Returnvalue "+returnvalue.getClass());
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
                        recursiveWanderer(finalPath, value, elements, snapshot, IDparts, finalIndex);
                    } catch (InvocationTargetException e) {
                        throw new RuntimeException(e);
                    } catch (IllegalAccessException e) {
                        throw new RuntimeException(e);
                    }
                });
                return parent;
            } else {
                //Create new element

                Base newElement = factory.create(child.getTypeCode());
                parent.setProperty(getElementName(path), newElement);
                recursiveWanderer(path, newElement, elements, snapshot, IDparts, index);
                return parent;
            }

        }


    }
}

