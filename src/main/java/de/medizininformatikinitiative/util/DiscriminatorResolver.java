package de.medizininformatikinitiative.util;

import org.hl7.fhir.r4.model.*;

import java.util.concurrent.atomic.AtomicBoolean;

public class DiscriminatorResolver {


    public static Boolean resolveDiscriminator(Base base, ElementDefinition slice,String discriminator,String Path,StructureDefinition.StructureDefinitionSnapshotComponent snapshot){
        //System.out.println("Discriminator "+discriminator);
        return switch (discriminator) {
            case "exists" -> false;
            case "null" -> false;
            case "pattern" -> resolvePattern(base, slice, Path,snapshot);
            case "profile" -> false;
            case "type" -> resolveType(base, slice, Path,snapshot);
            case "value" -> false;
            default -> false;
        };


    }

    private static Boolean resolvePattern(Base base, ElementDefinition slice, String path,StructureDefinition.StructureDefinitionSnapshotComponent snapshot) {
        //System.out.println("Resolving Pattern " + path);
        if (path.equalsIgnoreCase("$this")) {
            //no wandering needed
            //System.out.println("Slice Name " + slice.getName() + " Slicename " + slice.getSliceName());

            Property patternChild = slice.getChildByName("pattern[x]");
            for(Property child : patternChild.getValues().get(0).children()){
                if (child.hasValues()) {

                    String BasePatternString = String.valueOf(base.getChildByName(child.getName()).getValues().get(0));
                    String childPAtternString = String.valueOf(child.getValues().get(0));
                    //System.out.println("Value to be checked " + child.getName());
                    //System.out.println(BasePatternString + " against " + childPAtternString);
                    if (childPAtternString.equalsIgnoreCase(BasePatternString)) {
                        //System.out.println("Pattern Matched");

                    } else {
                        //System.out.println("Pattern Not Matched");
                        return false;
                    }
                }
            }
            return true;



        }else{
            //TODO resolving subpath

        }
        return false;


    }

    private static Boolean resolveType(Base base, ElementDefinition slice, String path,StructureDefinition.StructureDefinitionSnapshotComponent snapshot){
        //System.out.println("Resolving Type"+path);
        if(path.equalsIgnoreCase("$this")){
            //no wandering needed
            //System.out.println("Base FHIR TYPE"+base.fhirType());

            //System.out.println("Slice Type "+slice.getType().get(0).getWorkingCode());
            if(base.fhirType().equalsIgnoreCase(slice.getType().get(0).getWorkingCode())) {
                //System.out.println("Type  Matched"+slice.getType().get(0).getWorkingCode()+" "+base.fhirType());
                return true;
            }


        }else{
            //TODO resolving subpath
        }
        return false;
    }

}
