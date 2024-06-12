package de.medizininformatikinitiative.util;

import org.hl7.fhir.r4.model.*;

import java.util.HashMap;
import java.util.List;

public class ExtensionsSafeCopy {


    private final StructureDefinition.StructureDefinitionSnapshotComponent snapshot;

    ExtensionsSafeCopy(StructureDefinition cds) {
         snapshot = cds.getSnapshot();
    }

    ;

    public void copy(String elementID, Element src, Element dst) {
        ElementDefinition definition = snapshot.getElementByPath(elementID);
        List<Extension> defExtension = definition.getExtension();
        if (src.hasExtension()) {
            for (Extension extension : src.getExtension()) {
                Element finalDst = dst;
                defExtension.stream()
                        .filter(e -> e.getUrl().equals(extension.getUrl()))
                        .findFirst()
                        .ifPresent(e -> finalDst.addExtension(extension));
            }
        }

        dst = src.copy();

    }

        /*
        Extension dataAbsentReasonExtension = src.getExtensionByUrl("http://hl7.org/fhir/StructureDefinition/data-absent-reason");
        if(dataAbsentReasonExtension!=null){
            dst.addExtension(dataAbsentReasonExtension);
        }*/


}


