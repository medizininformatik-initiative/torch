package de.medizininformatikinitiative;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import de.medizininformatikinitiative.util.CRTDL.AttributeGroup;
import org.hl7.fhir.r4.model.DomainResource;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;

public class CopyRedactExecutor {

    public FhirContext ctx= FhirContext.forR4();;

    public IParser jsonParser = ctx.newJsonParser();

    //CDS Structure Definitions
    private CDSStructureDefinitionHandler CDS;

    private HashMap<String, AttributeGroup> AttributeGroups = new HashMap<>();

    public CopyRedactExecutor(CDSStructureDefinitionHandler CDS, HashMap<String, AttributeGroup> AttributeGroups) {
        this.CDS = CDS;
        this.AttributeGroups = AttributeGroups;
    }

    /*

     Class<? extends DomainResource> resourceClass = (Class<? extends DomainResource>) ctx.getResourceDefinition(definition.getType()).getImplementingClass();
     */
    public DomainResource copyRedact(String FilePath) throws IOException {
        DomainResource source = readResource(FilePath);
        DomainResource target = source.copy();
        return target;
    }

    public DomainResource readResource(String filePath) throws IOException {
        FileInputStream fis;
        try {
            fis = new FileInputStream(filePath);
        } catch (FileNotFoundException e) {
            throw new IOException(e);
        }

        DomainResource resource = (DomainResource) jsonParser.parseResource(fis);
        return resource;

    }







}
