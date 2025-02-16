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
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class ReferenceResolver {

    ReferenceExtractor referenceExtractor;
    DataStore dataStore;
    ProfileMustHaveChecker profileMustHaveChecker;
    CompartmentManager compartmentManager;
    ConsentHandler consentHandler;
    Map<String, AnnotatedAttributeGroup> attributeGroupMap;
    ResourceBundle coreBundle;

    ReferenceResolver(ReferenceExtractor referenceExtractor, DataStore store, ProfileMustHaveChecker checker, CompartmentManager compartmentManager, ConsentHandler handler, Map<String, AnnotatedAttributeGroup> attributeGroupMap, ResourceBundle coreBundle) {
        this.referenceExtractor = referenceExtractor;
        this.dataStore = store;
        this.profileMustHaveChecker = checker;
        this.compartmentManager = compartmentManager;
        this.consentHandler = handler;
        this.attributeGroupMap = attributeGroupMap;
        this.coreBundle = coreBundle;
    }

/*
    Mono<PatientResourceBundle> resolvePatient(PatientResourceBundle patientBundle) {
        return Flux.fromIterable(patientBundle.values())
                .flatMap(wrapper -> {
                    List<ReferenceWrapper> referenceWrappers;
                    try {
                        referenceWrappers = referenceExtractor.extract(wrapper);
                    } catch (MustHaveViolatedException e) {
                        //start deleting groups from ParentResource
                    }

                    return Flux.fromIterable(referenceWrappers)
                            .flatMap(ref -> handleReference(ref, Optional.of(patientBundle)))
                            .flatMap(resourceWrappers -> Flux.fromIterable(resourceWrappers))
                            .doOnNext(resourceWrapper -> {
                                if (compartmentManager.isInCompartment(resourceWrapper.resource())) {
                                    patientBundle.put(resourceWrapper);
                                } else {
                                    coreBundle.put(resourceWrapper);
                                }
                            })
                            .then();
                })
                .thenReturn(patientBundle);
    }

*/

    /**
     * @param referenceWrapper reference to be handled
     * @param patientBundle    patient bundle from which the reference was loaded
     * @return Mono<List < ResourceGroupWrapper>> - all known and newly fetched resources.
     */
    Mono<List<ResourceGroupWrapper>> handleReference(ReferenceWrapper referenceWrapper, Optional<PatientResourceBundle> patientBundle) {
        return Flux.fromIterable(referenceWrapper.references())
                .flatMap(reference -> {
                    Mono<ResourceGroupWrapper> referenceResource;

                    if (patientBundle.isPresent() && patientBundle.get().contains(reference)) {
                        referenceResource = patientBundle.get().get(reference);
                    } else if (coreBundle.contains(reference)) {
                        referenceResource = coreBundle.get(reference);
                    } else {
                        referenceResource = dataStore.fetchResourceByReference(reference)
                                .map(resource -> new ResourceGroupWrapper(resource, Set.of()));
                    }

                    return referenceResource.flatMap(resourceWrapper -> {
                        Set<AnnotatedAttributeGroup> groups = referenceWrapper.refAttribute().linkedGroups().stream()
                                .map(attributeGroupMap::get)
                                .filter(group -> profileMustHaveChecker.fulfilled((DomainResource) resourceWrapper.resource(), group))
                                .collect(Collectors.toSet());

                        if (referenceWrapper.refAttribute().mustHave() && groups.isEmpty()) {
                            return Mono.error(new MustHaveViolatedException("MustHave condition violated for " + reference));
                        }

                        resourceWrapper.addGroups(groups);
                        return Mono.just(resourceWrapper);
                    }).onErrorResume(MustHaveViolatedException.class, e -> Mono.empty());
                })
                .collectList();
    }


}
