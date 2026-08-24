package de.medizininformatikinitiative.torch.util;

import de.medizininformatikinitiative.torch.model.extraction.ExtractionId;
import de.medizininformatikinitiative.torch.model.management.ReferenceResolutionContext;
import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.r4.model.*;
import org.hl7.fhir.r4.model.ElementDefinition.DiscriminatorType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the DiscriminatorResolver class with specific discriminator paths.
 */
@ExtendWith(MockitoExtension.class)
class DiscriminatorResolverWithPathTest {

    @Mock
    ElementDefinition.ElementDefinitionSlicingDiscriminatorComponent discriminatorMock;

    @Mock
    StructureDefinition.StructureDefinitionSnapshotComponent snapshotMock;

    /**
     * Helper method to create an ElementDefinition with a fixed value at a specific path.
     *
     * @param id         The ID of the ElementDefinition.
     * @param path       The path of the ElementDefinition.
     * @param fixedValue The fixed value to set.
     * @return A configured ElementDefinition instance.
     */
    private ElementDefinition createElementWithFixedValue(String id, String path, String fixedValue) {
        ElementDefinition element = new ElementDefinition();
        element.setId(id);
        element.setPath(path);
        element.setFixed(new StringType(fixedValue));
        return element;
    }

    /**
     * Helper method to create an ElementDefinition with a TypeRefComponent at a specific path.
     *
     * @param id   The ID of the ElementDefinition.
     * @param path The path of the ElementDefinition.
     * @param type The type code to set.
     * @return A configured ElementDefinition instance.
     */
    private ElementDefinition createElementWithType(String id, String path, String type) {
        ElementDefinition element = new ElementDefinition();
        element.setId(id);
        element.setPath(path);
        ElementDefinition.TypeRefComponent typeRef = new ElementDefinition.TypeRefComponent();
        typeRef.setCode(type);
        element.addType(typeRef);
        return element;
    }

    @Test
    void testResolveDiscriminator_TypeValue_ResolvePatternTrue_WithPatientResource() throws FHIRException {
        when(discriminatorMock.getType()).thenReturn(DiscriminatorType.VALUE);
        when(discriminatorMock.getPath()).thenReturn("name.family"); // Specific path to navigate in the Patient resource

        ElementDefinition slice = new ElementDefinition();
        slice.setId("Patient.name.family");  // Ensures path matches how it's constructed in resolveSlicePath
        slice.setPath("Patient.name.family");
        StringType fixedFamilyName = new StringType("Doe");
        slice.setFixed(fixedFamilyName);
        when(snapshotMock.getElementById("Patient.name.family")).thenReturn(slice);
        ElementDefinition baseElement = new ElementDefinition();
        baseElement.setId("Patient");  // Ensures path matches how it's constructed in resolveSlicePath
        baseElement.setPath("Patient");

        Patient basePatient = new Patient();
        HumanName patientName = new HumanName();
        patientName.setFamily("Doe"); // Family name matches the fixed value
        basePatient.addName(patientName);

        Boolean result = DiscriminatorResolver.resolveDiscriminator(basePatient, baseElement, discriminatorMock, snapshotMock, ReferenceResolutionContext.EMPTY);

        assertTrue(result, "Should return true when discriminator type is 'value' and family name matches the fixed value");
    }


    /**
     * Test when discriminator type is 'VALUE' but the fixed value does not match at a specific path.
     * TODO
     */
    @Test
    void testResolveDiscriminator_TypeValue_ResolvePatternFalse_WithSpecificPath() {

    }

    /**
     * Test when discriminator path does not exist in the snapshot.
     */
    @Test
    void testResolveDiscriminator_PathDoesNotExist() {
        when(discriminatorMock.getType()).thenReturn(DiscriminatorType.VALUE);
        when(discriminatorMock.getPath()).thenReturn("nonexistent.path"); // Path does not exist
        ElementDefinition slice = new ElementDefinition();
        slice.setId("sliceId.slicePath");
        slice.setPath("nonexistent.path"); // Set the path that does not exist
        when(snapshotMock.getElementById("sliceId.slicePath.nonexistent.path")).thenReturn(null);
        Patient basePatient = new Patient();

        Boolean result = DiscriminatorResolver.resolveDiscriminator(basePatient, slice, discriminatorMock, snapshotMock, ReferenceResolutionContext.EMPTY);

        assertFalse(result, "Should return false when discriminator path does not exist in the snapshot");
    }

    /**
     * Test when discriminator type is 'TYPE' with a reference-resolving path like {@code $this.resolve()}, and the
     * referenced resource is not resolvable (e.g. not present in the batch). Must return false rather than throw.
     */
    @Test
    void testResolveDiscriminator_TypeType_ReferenceNotResolvable() {
        when(discriminatorMock.getType()).thenReturn(DiscriminatorType.TYPE);
        when(discriminatorMock.getPath()).thenReturn("$this.resolve()");
        ElementDefinition slice = new ElementDefinition();
        slice.setId("Observation.derivedFrom:attached-image");

        Reference reference = new Reference("DocumentReference/123");

        Boolean result = DiscriminatorResolver.resolveDiscriminator(reference, slice, discriminatorMock, snapshotMock, ReferenceResolutionContext.EMPTY);

        assertFalse(result, "Should return false, not throw, when a type discriminator's reference does not resolve");
    }

    /**
     * Same shape as above, but its snapshot path (not a {@code resolve()} path) is missing instead - the case
     * {@code testResolveDiscriminator_TypeType_ReferenceNotResolvable} covered before it was repurposed for the
     * reference-resolving path.
     */
    @Test
    void testResolveDiscriminator_TypeType_NonResolvePathDoesNotExist() {
        when(discriminatorMock.getType()).thenReturn(DiscriminatorType.TYPE);
        when(discriminatorMock.getPath()).thenReturn("unknownChild");
        ElementDefinition slice = new ElementDefinition();
        slice.setId("Patient");
        when(snapshotMock.getElementById("Patient.unknownChild")).thenReturn(null);

        Patient basePatient = new Patient();

        Boolean result = DiscriminatorResolver.resolveDiscriminator(basePatient, slice, discriminatorMock, snapshotMock, ReferenceResolutionContext.EMPTY);

        assertFalse(result, "Should return false when a non-resolve() type discriminator path is missing from the snapshot");
    }

    /**
     * A {@code resolve()} path applied to a {@link Reference} with no {@code .reference} value set can never
     * resolve, regardless of what the resolution context could otherwise provide.
     */
    @Test
    void testResolveDiscriminator_TypeProfile_ReferenceWithoutReferenceValue() {
        when(discriminatorMock.getType()).thenReturn(DiscriminatorType.PROFILE);
        when(discriminatorMock.getPath()).thenReturn("$this.resolve()");
        ElementDefinition slice = new ElementDefinition();
        slice.setId("Observation.derivedFrom:variant");
        slice.addType().setCode("Reference").addTargetProfile("https://www.medizininformatik-initiative.de/fhir/ext/modul-molgen/StructureDefinition/variante");

        Reference reference = new Reference();
        reference.setDisplay("no reference value set");

        Boolean result = DiscriminatorResolver.resolveDiscriminator(reference, slice, discriminatorMock, snapshotMock, ReferenceResolutionContext.EMPTY);

        assertFalse(result, "Should return false when the Reference has no reference value to resolve");
    }

    /**
     * A {@code resolve()} path applied to a reference string that {@link ExtractionId#fromRelativeUrl} rejects
     * (e.g. a {@code #contained} reference) must return false rather than throw.
     */
    @Test
    void testResolveDiscriminator_TypeProfile_UnparsableReference() {
        when(discriminatorMock.getType()).thenReturn(DiscriminatorType.PROFILE);
        when(discriminatorMock.getPath()).thenReturn("$this.resolve()");
        ElementDefinition slice = new ElementDefinition();
        slice.setId("Observation.derivedFrom:variant");
        slice.addType().setCode("Reference").addTargetProfile("https://www.medizininformatik-initiative.de/fhir/ext/modul-molgen/StructureDefinition/variante");

        Reference reference = new Reference("#contained1");

        Boolean result = DiscriminatorResolver.resolveDiscriminator(reference, slice, discriminatorMock, snapshotMock, ReferenceResolutionContext.EMPTY);

        assertFalse(result, "Should return false, not throw, when the reference cannot be parsed into an ExtractionId");
    }

    /**
     * Test when discriminator type is 'TYPE' and its path resolves to a child of the sliced element (e.g.
     * {@code Bundle.entry} sliced by type at path {@code resource}, as done by ISiKBerichtBundle). The type
     * comparison must use the resolved child's type, not the outer sliced element's own type.
     */
    @Test
    void testResolveDiscriminator_TypeType_PathResolvesToChildResource() {
        when(discriminatorMock.getType()).thenReturn(DiscriminatorType.TYPE);
        when(discriminatorMock.getPath()).thenReturn("resource");

        ElementDefinition slice = new ElementDefinition();
        slice.setId("Bundle.entry:Composition");

        ElementDefinition resourceElement = createElementWithType("Bundle.entry:Composition.resource", "Bundle.entry.resource", "Composition");
        when(snapshotMock.getElementById("Bundle.entry:Composition.resource")).thenReturn(resourceElement);

        Bundle.BundleEntryComponent entry = new Bundle.BundleEntryComponent();
        entry.setResource(new Composition());

        Boolean result = DiscriminatorResolver.resolveDiscriminator(entry, slice, discriminatorMock, snapshotMock, ReferenceResolutionContext.EMPTY);

        assertTrue(result, "Should match against the resolved child at the discriminator path, not the outer sliced element itself");
    }

    /**
     * Same shape as above, but the child's actual type does not match the slice's declared type.
     */
    @Test
    void testResolveDiscriminator_TypeType_PathResolvesToChildResource_TypeMismatch() {
        when(discriminatorMock.getType()).thenReturn(DiscriminatorType.TYPE);
        when(discriminatorMock.getPath()).thenReturn("resource");

        ElementDefinition slice = new ElementDefinition();
        slice.setId("Bundle.entry:Composition");

        ElementDefinition resourceElement = createElementWithType("Bundle.entry:Composition.resource", "Bundle.entry.resource", "Composition");
        when(snapshotMock.getElementById("Bundle.entry:Composition.resource")).thenReturn(resourceElement);

        Bundle.BundleEntryComponent entry = new Bundle.BundleEntryComponent();
        entry.setResource(new Patient());

        Boolean result = DiscriminatorResolver.resolveDiscriminator(entry, slice, discriminatorMock, snapshotMock, ReferenceResolutionContext.EMPTY);

        assertFalse(result, "Should not match when the resolved child's type differs from the slice's declared type");
    }

    /**
     * Same shape again, but the discriminator path does not resolve on the actual base instance (e.g. the entry's
     * {@code resource} is unset). {@code resolveElementPath} then returns null and resolveType must return false
     * rather than throw a NullPointerException.
     */
    @Test
    void testResolveDiscriminator_TypeType_PathResolvesToChildResource_ChildUnset() {
        when(discriminatorMock.getType()).thenReturn(DiscriminatorType.TYPE);
        when(discriminatorMock.getPath()).thenReturn("resource");

        ElementDefinition slice = new ElementDefinition();
        slice.setId("Bundle.entry:Composition");

        ElementDefinition resourceElement = createElementWithType("Bundle.entry:Composition.resource", "Bundle.entry.resource", "Composition");
        when(snapshotMock.getElementById("Bundle.entry:Composition.resource")).thenReturn(resourceElement);

        Bundle.BundleEntryComponent entry = new Bundle.BundleEntryComponent();

        Boolean result = DiscriminatorResolver.resolveDiscriminator(entry, slice, discriminatorMock, snapshotMock, ReferenceResolutionContext.EMPTY);

        assertFalse(result, "Should return false, not throw, when the discriminator path does not resolve on the actual base instance");
    }

    /**
     * A repeating element (e.g. {@code CodeableConcept.coding}) must match a pattern discriminator if
     * <i>any</i> of its values satisfies the pattern, not only its first value.
     */
    @Test
    void testResolveDiscriminator_TypeValue_PatternMatchesCodingNotFirstInArray() {
        when(discriminatorMock.getType()).thenReturn(DiscriminatorType.VALUE);
        when(discriminatorMock.getPath()).thenReturn("code");

        ElementDefinition slice = new ElementDefinition();
        slice.setId("Observation.component:SystolicBP");

        ElementDefinition codeElement = new ElementDefinition();
        CodeableConcept pattern = new CodeableConcept();
        pattern.addCoding(new Coding("http://loinc.org", "8480-6", null));
        codeElement.setPattern(pattern);
        when(snapshotMock.getElementById("Observation.component:SystolicBP.code")).thenReturn(codeElement);

        Observation.ObservationComponentComponent component = new Observation.ObservationComponentComponent();
        CodeableConcept code = new CodeableConcept();
        code.addCoding(new Coding("http://snomed.info/sct", "271649006", "Systolic blood pressure (observable entity)"));
        code.addCoding(new Coding("http://loinc.org", "8480-6", "Blood pressure panel with all children optional"));
        code.addCoding(new Coding("urn:iso:std:iso:11073:10101", "150017", "Systolic blood pressure"));
        component.setCode(code);

        Boolean result = DiscriminatorResolver.resolveDiscriminator(component, slice, discriminatorMock, snapshotMock, ReferenceResolutionContext.EMPTY);

        assertTrue(result, "Should match when any coding in the array matches the pattern, not just the first");
    }

    /**
     * Test when discriminator type is 'VALUE' but the slice does not have a fixed value at the specified path.
     */
    @Test
    void testResolveDiscriminator_TypeValue_NoFixedValueAtPath() {
        when(discriminatorMock.getType()).thenReturn(ElementDefinition.DiscriminatorType.VALUE);
        when(discriminatorMock.getPath()).thenReturn("name.code"); // Setting specific path to navigate
        ElementDefinition slice = new ElementDefinition();
        slice.setId("Patient"); // Setting the ID
        slice.setPath("name.code");    // Ensure the path is set properly
        ElementDefinition childElement = new ElementDefinition();
        childElement.setId("Patient.name.code");
        childElement.setPath("name.code");
        when(snapshotMock.getElementById("Patient.name.code")).thenReturn(childElement);
        Patient basePatient = new Patient();
        Extension parentExtension = new Extension("name", new Extension("code", new StringType("someValue")));
        basePatient.addExtension(parentExtension);

        Boolean result = DiscriminatorResolver.resolveDiscriminator(basePatient, slice, discriminatorMock, snapshotMock, ReferenceResolutionContext.EMPTY);

        assertFalse(result, "Should return false when discriminator type is 'value' but no fixed value is set at the specified path");
    }

    /**
     * Reproduces #1173 (base-URL case): a 'TYPE' discriminator at {@code $this.resolve()}, as used e.g. by
     * {@code Observation.derivedFrom:dicom-image} in mii-pr-patho-finding, whose {@code targetProfile} is a base
     * HL7 canonical URL not loaded as an ontology profile - the resource type falls back to the URL's own
     * trailing segment.
     */
    @Test
    void testResolveDiscriminator_TypeType_ResolvesReferenceAgainstBaseTargetProfile() {
        when(discriminatorMock.getType()).thenReturn(DiscriminatorType.TYPE);
        when(discriminatorMock.getPath()).thenReturn("$this.resolve()");

        ElementDefinition slice = new ElementDefinition();
        slice.setId("Observation.derivedFrom:dicom-image");
        slice.addType().setCode("Reference").addTargetProfile("http://hl7.org/fhir/StructureDefinition/ImagingStudy");

        Reference reference = new Reference("ImagingStudy/img1");
        ImagingStudy imagingStudy = new ImagingStudy();
        imagingStudy.setId("img1");

        ReferenceResolutionContext resolutionContext = new ReferenceResolutionContext(
                id -> id.equals(ExtractionId.fromRelativeUrl("ImagingStudy/img1")) ? Optional.of(imagingStudy) : Optional.empty(),
                url -> Optional.empty());

        Boolean result = DiscriminatorResolver.resolveDiscriminator(reference, slice, discriminatorMock, snapshotMock, resolutionContext);

        assertTrue(result, "Should match by resource type when the target profile is a base HL7 canonical URL");
    }

    /**
     * Reproduces #1173 (custom-profile case): same shape, but {@code targetProfile} is a custom MII profile whose
     * own base resource type ("Media", as for mii-pr-patho-attached-image) differs from its URL's trailing
     * segment ("mii-pr-patho-attached-image") - the type must come from the resolved profile, not the URL.
     */
    @Test
    void testResolveDiscriminator_TypeType_ResolvesReferenceAgainstCustomTargetProfile() {
        when(discriminatorMock.getType()).thenReturn(DiscriminatorType.TYPE);
        when(discriminatorMock.getPath()).thenReturn("$this.resolve()");

        String profileUrl = "https://www.medizininformatik-initiative.de/fhir/ext/modul-patho/StructureDefinition/mii-pr-patho-attached-image";
        ElementDefinition slice = new ElementDefinition();
        slice.setId("Observation.derivedFrom:attached-image");
        slice.addType().setCode("Reference").addTargetProfile(profileUrl);

        Reference reference = new Reference("Media/media1");
        Media media = new Media();
        media.setId("media1");

        StructureDefinition attachedImageProfile = new StructureDefinition();
        attachedImageProfile.setUrl(profileUrl);
        attachedImageProfile.setType("Media");
        CompiledStructureDefinition compiled = CompiledStructureDefinition.fromStructureDefinition(attachedImageProfile);

        ReferenceResolutionContext resolutionContext = new ReferenceResolutionContext(
                id -> id.equals(ExtractionId.fromRelativeUrl("Media/media1")) ? Optional.of(media) : Optional.empty(),
                url -> url.equals(profileUrl) ? Optional.of(compiled) : Optional.empty());

        Boolean result = DiscriminatorResolver.resolveDiscriminator(reference, slice, discriminatorMock, snapshotMock, resolutionContext);

        assertTrue(result, "Should match by the target profile's own declared base type, not its URL's trailing segment");
    }

    /**
     * Same shape as above, but the resolved resource's type does not match any of the slice's target profiles.
     */
    @Test
    void testResolveDiscriminator_TypeType_ResolvedReferenceTypeMismatch() {
        when(discriminatorMock.getType()).thenReturn(DiscriminatorType.TYPE);
        when(discriminatorMock.getPath()).thenReturn("$this.resolve()");

        ElementDefinition slice = new ElementDefinition();
        slice.setId("Observation.derivedFrom:dicom-image");
        slice.addType().setCode("Reference").addTargetProfile("http://hl7.org/fhir/StructureDefinition/ImagingStudy");

        Reference reference = new Reference("Patient/p1");
        Patient patient = new Patient();
        patient.setId("p1");

        ReferenceResolutionContext resolutionContext = new ReferenceResolutionContext(
                id -> id.equals(ExtractionId.fromRelativeUrl("Patient/p1")) ? Optional.of(patient) : Optional.empty(),
                url -> Optional.empty());

        Boolean result = DiscriminatorResolver.resolveDiscriminator(reference, slice, discriminatorMock, snapshotMock, resolutionContext);

        assertFalse(result, "Should not match when the resolved resource's type differs from every target profile's type");
    }

    /**
     * Reproduces #1173's 'PROFILE' case, as used e.g. by {@code Observation.derivedFrom:variant} in
     * mii-pr-molgen-diagnostische-implikation: the resolved resource must declare, in its own {@code meta.profile},
     * conformance to one of the slice's target profiles.
     */
    @Test
    void testResolveDiscriminator_TypeProfile_ResolvedReferenceDeclaresMatchingProfile() {
        when(discriminatorMock.getType()).thenReturn(DiscriminatorType.PROFILE);
        when(discriminatorMock.getPath()).thenReturn("resolve()");

        String profileUrl = "https://www.medizininformatik-initiative.de/fhir/ext/modul-molgen/StructureDefinition/variante";
        ElementDefinition slice = new ElementDefinition();
        slice.setId("Observation.derivedFrom:variant");
        slice.addType().setCode("Reference").addTargetProfile(profileUrl);

        Reference reference = new Reference("Observation/variant1");
        Observation variant = new Observation();
        variant.setId("variant1");
        variant.getMeta().addProfile(profileUrl + "|1.0.0");

        ReferenceResolutionContext resolutionContext = new ReferenceResolutionContext(
                id -> id.equals(ExtractionId.fromRelativeUrl("Observation/variant1")) ? Optional.of(variant) : Optional.empty(),
                url -> Optional.empty());

        Boolean result = DiscriminatorResolver.resolveDiscriminator(reference, slice, discriminatorMock, snapshotMock, resolutionContext);

        assertTrue(result, "Should match, ignoring the version suffix, when the resolved resource declares the target profile");
    }

    /**
     * Same shape as above, but the resolved resource declares a different profile.
     */
    @Test
    void testResolveDiscriminator_TypeProfile_ResolvedReferenceDeclaresDifferentProfile() {
        when(discriminatorMock.getType()).thenReturn(DiscriminatorType.PROFILE);
        when(discriminatorMock.getPath()).thenReturn("resolve()");

        ElementDefinition slice = new ElementDefinition();
        slice.setId("Observation.derivedFrom:variant");
        slice.addType().setCode("Reference").addTargetProfile("https://www.medizininformatik-initiative.de/fhir/ext/modul-molgen/StructureDefinition/variante");

        Reference reference = new Reference("Observation/other1");
        Observation other = new Observation();
        other.setId("other1");
        other.getMeta().addProfile("https://www.medizininformatik-initiative.de/fhir/ext/modul-molgen/StructureDefinition/haplotyp");

        ReferenceResolutionContext resolutionContext = new ReferenceResolutionContext(
                id -> id.equals(ExtractionId.fromRelativeUrl("Observation/other1")) ? Optional.of(other) : Optional.empty(),
                url -> Optional.empty());

        Boolean result = DiscriminatorResolver.resolveDiscriminator(reference, slice, discriminatorMock, snapshotMock, resolutionContext);

        assertFalse(result, "Should not match when the resolved resource declares a different profile");
    }
}
