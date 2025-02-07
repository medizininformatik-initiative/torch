package de.medizininformatikinitiative.torch;

import ca.uhn.fhir.context.FhirContext;
import de.medizininformatikinitiative.torch.exceptions.MustHaveViolatedException;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttribute;
import de.medizininformatikinitiative.torch.setup.IntegrationTestSetup;
import de.medizininformatikinitiative.torch.util.ElementCopier;
import org.hl7.fhir.r4.model.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class ElementCopierTest {

    private final IntegrationTestSetup itSetup = new IntegrationTestSetup();
    private final FhirContext fhirContext = FhirContext.forR4();
    private final ElementCopier copier = new ElementCopier(itSetup.structureDefinitionHandler(), fhirContext, itSetup.fhirPathBuilder());

    @Test
    void singleProfile() throws MustHaveViolatedException {
        Observation source = new Observation();
        Meta meta = new Meta();
        meta.setProfile(List.of(new CanonicalType("https://www.medizininformatik-initiative.de/fhir/core/modul-diagnose/StructureDefinition/Diagnose")));
        source.setMeta(meta);
        Observation tgt = new Observation();
        String profile = source.getMeta().getProfile().getFirst().getValue();

        copier.copy(source, tgt, new AnnotatedAttribute("Observation.meta.profile", "", "", false), profile);

        assertThat(tgt.getMeta().getProfile().stream().map(PrimitiveType::getValue))
                .containsExactly("https://www.medizininformatik-initiative.de/fhir/core/modul-diagnose/StructureDefinition/Diagnose");
    }

    @Test
    void multiProfilePrePopulatedTarget() throws MustHaveViolatedException {
        Observation source = new Observation();
        Meta meta = new Meta();
        meta.setProfile(List.of(new CanonicalType("Test"),
                new CanonicalType("https://www.medizininformatik-initiative.de/fhir/core/modul-diagnose/StructureDefinition/Diagnose")));
        source.setMeta(meta);
        Observation tgt = new Observation();
        tgt.setMeta(new Meta());
        String profile = source.getMeta().getProfile().get(1).getValue();

        copier.copy(source, tgt, new AnnotatedAttribute("Observation.meta.profile", "", "", false), profile);

        assertThat(tgt.getMeta().getProfile().stream().map(PrimitiveType::getValue))
                .containsExactly("Test", "https://www.medizininformatik-initiative.de/fhir/core/modul-diagnose/StructureDefinition/Diagnose");
    }

    @Test
    void multiProfileUnPopulatedTarget() throws MustHaveViolatedException {
        Observation source = new Observation();
        Meta meta = new Meta();
        meta.setProfile(List.of(new CanonicalType("Test"),
                new CanonicalType("https://www.medizininformatik-initiative.de/fhir/core/modul-diagnose/StructureDefinition/Diagnose")));
        source.setMeta(meta);
        Observation tgt = new Observation();
        String profile = source.getMeta().getProfile().get(1).getValue();

        copier.copy(source, tgt, new AnnotatedAttribute("Observation.meta.profile", "", "", false), profile);

        assertThat(tgt.getMeta().getProfile().stream().map(PrimitiveType::getValue))
                .containsExactly("Test", "https://www.medizininformatik-initiative.de/fhir/core/modul-diagnose/StructureDefinition/Diagnose");
    }

    @Test
    void multiCategory() throws MustHaveViolatedException {
        Observation source = new Observation();
        List<CodeableConcept> categories = List.of(
                new CodeableConcept().addCoding(new Coding().setCode("Test")),
                new CodeableConcept().addCoding(new Coding().setCode("Test2"))
        );
        source.setMeta(defaultMeta());
        source.setCategory(categories);
        Observation tgt = new Observation();
        String profile = source.getMeta().getProfile().getFirst().getValue();

        copier.copy(source, tgt, new AnnotatedAttribute("Observation.category", "", "", false), profile);

        assertThat(tgt.getCategory().stream().map(CodeableConcept::getCoding).map(List::getFirst).map(Coding::getCode))
                .containsExactly("Test", "Test2");
    }

    private static Meta defaultMeta() {
        return new Meta().setProfile(List.of(new CanonicalType("https://www.medizininformatik-initiative.de/fhir/core/modul-diagnose/StructureDefinition/Diagnose")));
    }
}
