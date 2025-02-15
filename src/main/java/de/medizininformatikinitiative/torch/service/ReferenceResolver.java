package de.medizininformatikinitiative.torch.service;

import de.medizininformatikinitiative.torch.exceptions.MustHaveViolatedException;
import de.medizininformatikinitiative.torch.management.CompartmentManager;
import de.medizininformatikinitiative.torch.management.ConsentHandler;
import de.medizininformatikinitiative.torch.model.PatientResourceBundle;
import de.medizininformatikinitiative.torch.model.ReferenceWrapper;
import de.medizininformatikinitiative.torch.model.ResourceBundle;
import de.medizininformatikinitiative.torch.model.ResourceGroupWrapper;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttributeGroup;
import de.medizininformatikinitiative.torch.util.ProfileMustHaveChecker;
import de.medizininformatikinitiative.torch.util.ReferenceExtractor;
import org.hl7.fhir.r4.model.DomainResource;
import org.hl7.fhir.r4.model.Resource;
import reactor.core.publisher.Mono;

import java.util.*;

public class ReferenceResolver {

    ReferenceExtractor referenceExtractor;
    DataStore dataStore;
    ProfileMustHaveChecker profileMustHaveChecker;
    CompartmentManager compartmentManager;
    ConsentHandler consentHandler;


    ReferenceResolver(ReferenceExtractor referenceExtractor, DataStore store, ProfileMustHaveChecker checker, CompartmentManager compartmentManager, ConsentHandler handler) {
        this.referenceExtractor = referenceExtractor;
        this.dataStore = store;
        this.profileMustHaveChecker = checker;
        this.compartmentManager = compartmentManager;
        this.consentHandler = handler;
    }

    PatientResourceBundle resolvePatient(PatientResourceBundle patientBundle, ResourceBundle coreBundle) throws MustHaveViolatedException {
        //load patient bundle into stack
        Deque<ResourceGroupWrapper> resourceStack = new LinkedList<>(patientBundle.values());
        boolean newResource;

        while (!resourceStack.isEmpty()) {
            ResourceGroupWrapper wrapper = resourceStack.pop();
            List<ReferenceWrapper> referenceWrappers = referenceExtractor.extract(wrapper);

            for (ReferenceWrapper ref : referenceWrappers) {
                for (String reference : ref.references()) {
                    newResource = false;
                    Mono<ResourceGroupWrapper> referenceResource;
                    if (patientBundle.contains(reference)) {
                        referenceResource = patientBundle.get(reference);
                    } else if (coreBundle.contains(reference)) {
                        referenceResource = coreBundle.get(reference);
                    } else {
                        newResource = true;
                        referenceResource = dataStore.fetchResourceByReference(reference)
                                .map(resource -> new ResourceGroupWrapper(resource, Set.of()));
                    }
                    //Over all referenced resources
                    referenceResource.flatMap(resourceWrapper -> {
                        Set<AnnotatedAttributeGroup> groups = new HashSet<>();
                        //Groupset from linked list
                        resourceWrapper.groupSet().stream()
                                .filter(annotatedAttributeGroup ->
                                        profileMustHaveChecker.fulfilled((DomainResource) resourceWrapper.resource(), annotatedAttributeGroup))
                                .forEach(groups::add);
                        if (ref.refAttribute().mustHave() && groups.isEmpty()) {
                            //Must Have Violation
                            //collect must have violation over reference
                            ref.GroupId();
                        }
                        resourceWrapper.addGroups(groups);

                        return Mono.just(resourceWrapper);
                    }).subscribe();
                }


            }

        }


        //extract references from patient bundle
        // check if resource in patientbundle or coreBundle if not build queries and fetch resources
        // check which attribute groups are applicable
        // update groups
        // add new resources to patient or corebundle (if in compartment)


        //Add successful Resource IDs to Reference and manage it in bundle
        //Manage for which groups the must have has been violated
        return patientBundle;

    }

    Boolean validateResourceAgainstLinkedGroups(Resource resource, ReferenceWrapper referenceWrapper) {
        /*
        for all linked groups:
        Load Group

           //Groupset from linked list
                        linked.stream()
                                .filter(annotatedAttributeGroup ->
                                        profileMustHaveChecker.fulfilled((DomainResource) resourceWrapper.resource(), annotatedAttributeGroup))
                                .forEach(groups::add);
                        if (ref.refAttribute().mustHave() && groups.isEmpty()) {
                            //Must Have Violation
                            //collect must have violation over reference
                        }

        save if reference not resolvable

        safe reference to ResourceWrapper

         */
        return true;
    }


}
