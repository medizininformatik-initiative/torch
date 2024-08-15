package de.medizininformatikinitiative.torch;

import ca.uhn.fhir.context.BaseRuntimeChildDefinition;
import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.context.RuntimeResourceDefinition;
import org.hl7.fhir.instance.model.api.IBase;
import org.hl7.fhir.r4.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component


public class BundleCreator {
    private static final Logger logger = LoggerFactory.getLogger(BundleCreator.class);


    @Autowired
    FhirContext context;

    org.hl7.fhir.r4.model.Bundle.HTTPVerb method=Bundle.HTTPVerb.PUT;

    public BundleCreator() {

    }

    private void extractReferences(DomainResource resource, Set<String> patientReferences, Set<String> encounterReferences) {
        RuntimeResourceDefinition def = context.getResourceDefinition(resource);

        // Iterate over all child elements
        for (BaseRuntimeChildDefinition childDef : def.getChildren()) {
            List<IBase> values = childDef.getAccessor().getValues(resource);
            for (IBase value : values) {
                if (value instanceof Reference) {
                    String refString = ((Reference) value).getReference();
                    if (refString != null) {
                        if (refString.startsWith("Patient/")) {
                            patientReferences.add(refString);
                        } else if (refString.startsWith("Encounter/")) {
                            encounterReferences.add(refString);
                        }
                    }
                } else if (value instanceof DomainResource) {
                    // Recursively extract references from contained resources
                    extractReferences((DomainResource) value, patientReferences, encounterReferences);
                }
            }
        }
    }

    public Map<String, Bundle> createBundles(Map<String, Collection<Resource>> resourcesByPatientId) {
        return resourcesByPatientId.entrySet().stream().collect(Collectors.toMap(
                Map.Entry::getKey,
                entry -> {
                    String patientId = entry.getKey();
                    Collection<Resource> patientResources = entry.getValue();

                    // Extract references from resources
                    Set<String> patientReferences = new HashSet<>();
                    Set<String> encounterReferences = new HashSet<>();

                    for (Resource resource : patientResources) {
                        extractReferences((DomainResource) resource, patientReferences, encounterReferences);
                    }

                    // Create the bundle
                    Bundle bundle = new Bundle();
                    bundle.setType(Bundle.BundleType.TRANSACTION);
                    bundle.setId(patientId);

                    boolean patientAdded = false;

                    for (Resource resource : patientResources) {
                        try {
                            Bundle.BundleEntryComponent entryComponent = new Bundle.BundleEntryComponent();
                            entryComponent.setResource(resource);
                            Bundle.BundleEntryRequestComponent request = new Bundle.BundleEntryRequestComponent();
                            request.setUrl(resource.getResourceType() + "/" + resource.getId());
                            request.setMethod(method);
                            entryComponent.setRequest(request);

                            bundle.addEntry(entryComponent);

                            // Add the main patient resource to the bundle if it exists and hasn't been added yet
                            if (!patientAdded && resource instanceof Patient) {
                                patientAdded = true;
                            }
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    }

                    // Add dummy patient resource if the main patient resource wasn't included
                    if (!patientAdded) {
                        Patient dummyPatient = new Patient();
                        Meta meta = new Meta();
                        meta.addProfile("https://www.medizininformatik-initiative.de/fhir/core/modul-person/StructureDefinition/Patient");
                        dummyPatient.setMeta(meta);
                        dummyPatient.setId(patientId);
                        Bundle.BundleEntryComponent patientEntry = new Bundle.BundleEntryComponent();
                        patientEntry.setResource(dummyPatient);
                        Bundle.BundleEntryRequestComponent request = new Bundle.BundleEntryRequestComponent();
                        request.setUrl("Patient/" + patientId);
                        request.setMethod(method);
                        patientEntry.setRequest(request);
                        bundle.addEntry(patientEntry);
                    }

                    // Add dummy encounter resources based on extracted references
                    for (String encounterRef : encounterReferences) {
                        Encounter dummyEncounter = new Encounter();
                        dummyEncounter.setStatus(Encounter.EncounterStatus.UNKNOWN);
                        dummyEncounter.setId(encounterRef.split("/")[1]); // Set only the ID part
                        Bundle.BundleEntryComponent encounterEntry = new Bundle.BundleEntryComponent();
                        encounterEntry.setResource(dummyEncounter);
                        Bundle.BundleEntryRequestComponent request = new Bundle.BundleEntryRequestComponent();
                        request.setUrl("Encounter/" + dummyEncounter.getId());
                        request.setMethod(method);
                        encounterEntry.setRequest(request);
                        bundle.addEntry(encounterEntry);
                    }

                    return bundle;
                }
        ));
    }
}