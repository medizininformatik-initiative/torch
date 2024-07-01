package de.medizininformatikinitiative;

import ca.uhn.fhir.context.FhirContext;
import de.medizininformatikinitiative.util.ElementCopier;
import de.medizininformatikinitiative.util.Redaction;
import de.medizininformatikinitiative.model.*;
import org.hl7.fhir.r4.model.DomainResource;
import org.hl7.fhir.r4.model.Resource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.springframework.util.MultiValueMap;
import reactor.core.publisher.Flux;

import java.lang.reflect.InvocationTargetException;

@Component
public class ResourceTransformer {


    private final DataStore dataStore;

    private final ElementCopier copier;
    private final Redaction redaction;


    @Autowired
    public ResourceTransformer(DataStore dataStore, ElementCopier copier, Redaction redaction, CdsStructureDefinitionHandler cds, FhirContext ctx) {
        this.dataStore = dataStore;
        this.copier = copier;
        this.redaction = redaction;
    }


    public Flux<Resource> transformResources(MultiValueMap<String, String> parameters, AttributeGroup group, String resourceType) {
        Flux<Resource> resources = dataStore.getResources(resourceType, parameters);
        return resources.map(resource -> {

            try {
                return transform((DomainResource) resource, group);
            } catch (NoSuchMethodException e) {
                throw new RuntimeException(e);
            } catch (InvocationTargetException e) {
                throw new RuntimeException(e);
            } catch (InstantiationException e) {
                throw new RuntimeException(e);
            } catch (IllegalAccessException | de.medizininformatikinitiative.util.Exceptions.MustHaveViolatedException e) {
                throw new RuntimeException(e);
            }
        });

    }


    public Resource transform(DomainResource resourcesrc, AttributeGroup group) throws NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException, de.medizininformatikinitiative.util.Exceptions.MustHaveViolatedException {
        Class<? extends DomainResource> resourceClass = resourcesrc.getClass().asSubclass(DomainResource.class);
        DomainResource tgt = resourceClass.getDeclaredConstructor().newInstance();
       group.getAttributes().forEach(attribute -> {
            try {
                copier.copy( resourcesrc, tgt, attribute);
            } catch (de.medizininformatikinitiative.util.Exceptions.MustHaveViolatedException e) {
                throw new RuntimeException(e);
            }
        });
        //TODO define technically required in all Ressources
        copier.copy(resourcesrc, tgt, new Attribute("meta.profile", true));
        copier.copy(resourcesrc, tgt, new Attribute("id", true));
        //TODO Handle Custom ENUM Types like Status, since it has its Error in the valuesystem.
        if(resourcesrc.getClass()==org.hl7.fhir.r4.model.Observation.class){
            copier.copy(resourcesrc, tgt, new Attribute("status", true));
        }
        redaction.redact(tgt, "", 1);

        return tgt;
    }


}
