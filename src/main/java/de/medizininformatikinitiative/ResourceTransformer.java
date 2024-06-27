package de.medizininformatikinitiative;

import ca.uhn.fhir.context.FhirContext;
import de.medizininformatikinitiative.util.ElementCopier;
import de.medizininformatikinitiative.util.Exceptions.mustHaveViolatedException;
import de.medizininformatikinitiative.util.Redaction;
import org.hl7.fhir.r4.model.DomainResource;
import org.hl7.fhir.r4.model.Resource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import reactor.core.publisher.Flux;

import java.lang.reflect.InvocationTargetException;

@Component
public class ResourceTransformer {


    private final DataStore dataStore;

    private final ElementCopier copier;
    private final Redaction redaction;


    @Autowired
    public ResourceTransformer(DataStore dataStore, ElementCopier copier, Redaction redaction, CDSStructureDefinitionHandler cds, FhirContext ctx) {
        this.dataStore = dataStore;
        this.copier = copier;
        this.redaction = redaction;
    }


    public Flux<Resource> transformResources(String FHIRSearchQuery, de.medizininformatikinitiative.util.model.CRTDL CRTDL) {
        Flux<Resource> resources = dataStore.getResources(FHIRSearchQuery);
        return resources.map(resource -> {

            try {
                return transform((DomainResource) resource, CRTDL);
            } catch (NoSuchMethodException e) {
                throw new RuntimeException(e);
            } catch (InvocationTargetException e) {
                throw new RuntimeException(e);
            } catch (InstantiationException e) {
                throw new RuntimeException(e);
            } catch (IllegalAccessException | mustHaveViolatedException e) {
                throw new RuntimeException(e);
            }
        });

    }


    public Resource transform(DomainResource resourcesrc,de.medizininformatikinitiative.util.model.CRTDL CRTDL) throws NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException, de.medizininformatikinitiative.util.Exceptions.mustHaveViolatedException {
        Class<? extends DomainResource> resourceClass = resourcesrc.getClass().asSubclass(DomainResource.class);
        DomainResource tgt = resourceClass.getDeclaredConstructor().newInstance();
       CRTDL.getCohortDefinition().getDataExtraction().getAttributeGroups().get(0).getAttributes().forEach(attribute -> {
            try {
                copier.copy( resourcesrc, tgt, attribute);
            } catch (de.medizininformatikinitiative.util.Exceptions.mustHaveViolatedException e) {
                throw new RuntimeException(e);
            }
        });
        //TODO define technically required in all Ressources
        copier.copy(resourcesrc, tgt, new de.medizininformatikinitiative.util.model.Attribute("meta.profile", true));
        copier.copy(resourcesrc, tgt, new de.medizininformatikinitiative.util.model.Attribute("id", true));
        //TODO Handle Custom ENUM Types like Status, since it has its Error in the valuesystem.
        if(resourcesrc.getClass()==org.hl7.fhir.r4.model.Observation.class){
            copier.copy(resourcesrc, tgt, new de.medizininformatikinitiative.util.model.Attribute("status", true));
        }
        redaction.redact(tgt, "", 1);

        return tgt;
    }


}
