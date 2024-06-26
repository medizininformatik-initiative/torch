package de.medizininformatikinitiative.util;

import org.hl7.fhir.r4.model.*;

import java.util.concurrent.atomic.AtomicBoolean;

public class DiscriminatorResolver {


    /**
     * Resolves the discriminator for a given slice
     *
     * @param base          Element to be sliced
     * @param slice         ElementDefinition of the slice
     * @param discriminator Discriminator to be resolved
     * @param path          path to the element
     * @param snapshot      Snapshot of the StructureDefinition
     * @return
     */
    public static Boolean resolveDiscriminator(Base base, ElementDefinition slice, String discriminator, String path, StructureDefinition.StructureDefinitionSnapshotComponent snapshot) {
        //System.out.println("Discriminator "+discriminator);
        return switch (discriminator) {
            case "exists" -> false;
            case "null" -> false;
            case "pattern" -> resolvePattern(base, slice, path, snapshot);
            case "profile" -> false;
            case "type" -> resolveType(base, slice, path, snapshot);
            case "value" -> false;
            default -> false;
        };


    }

    /**
     * Resolves the Pattern for a given slice
     *
     * @param base     Element to be sliced
     * @param slice    ElementDefinition of the slice
     * @param snapshot Snapshot of the StructureDefinition
     * @return
     */
    private static Boolean resolvePattern(Base base, ElementDefinition slice, String path, StructureDefinition.StructureDefinitionSnapshotComponent snapshot) {
        //System.out.println("Resolving Pattern " + path);
        if (path.equalsIgnoreCase("$this")) {
            //no wandering needed
            //System.out.println("Slice Name " + slice.getName() + " Slicename " + slice.getSliceName());

            Property patternChild = slice.getChildByName("pattern[x]");
            for (Property child : patternChild.getValues().get(0).children()) {
                if (child.hasValues()) {

                    String BasePatternString = String.valueOf(base.getChildByName(child.getName()).getValues().get(0));
                    String childPAtternString = String.valueOf(child.getValues().get(0));
                    if (childPAtternString.equalsIgnoreCase(BasePatternString)) {
                        //System.out.println("Pattern Matched");

                    } else {
                        return false;
                    }
                }
            }
            return true;


        } else {
            //TODO resolving subpath

        }
        return false;


    }


    /**
     * Resolves the Type for a given slice
     *
     * @param base     Element to be sliced
     * @param slice    ElementDefinition of the slice
     * @param snapshot Snapshot of the StructureDefinition
     * @return
     */
    private static Boolean resolveType(Base base, ElementDefinition slice, String path, StructureDefinition.StructureDefinitionSnapshotComponent snapshot) {
        //System.out.println("Resolving Type"+path);
        if (path.equalsIgnoreCase("$this")) {
            //no wandering needed
              if (base.fhirType().equalsIgnoreCase(slice.getType().get(0).getWorkingCode())) {
                return true;
            }


        } else {
            //TODO resolving subpath
        }
        return false;
    }

}
