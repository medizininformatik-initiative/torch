package de.medizininformatikinitiative.torch;

import ca.uhn.fhir.context.FhirContext;
import de.medizininformatikinitiative.torch.exceptions.MustHaveViolatedException;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttribute;
import de.medizininformatikinitiative.torch.setup.IntegrationTestSetup;
import de.medizininformatikinitiative.torch.util.ElementCopier;
import org.hl7.fhir.r4.model.CanonicalType;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Meta;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.PrimitiveType;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class ElementCopierTest {

    private final IntegrationTestSetup itSetup = new IntegrationTestSetup();
    private final FhirContext fhirContext = FhirContext.forR4();
    private final ElementCopier copier = new ElementCopier(fhirContext);

    public ElementCopierTest() throws IOException {
    }

    @Test
    void singleProfile() throws MustHaveViolatedException {
        Observation source = new Observation();
        Meta meta = new Meta();
        meta.setProfile(List.of(new CanonicalType("https://www.medizininformatik-initiative.de/fhir/core/modul-diagnose/StructureDefinition/Diagnose")));
        source.setMeta(meta);
        Observation tgt = new Observation();

        copier.copy(source, tgt, new AnnotatedAttribute("Observation.meta.profile", "Observation.meta.profile", "Observation.meta.profile", false));

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

        copier.copy(source, tgt, new AnnotatedAttribute("Observation.meta.profile", "Observation.meta.profile", "Observation.meta.profile", false));

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
        copier.copy(source, tgt, new AnnotatedAttribute("Observation.meta.profile", "Observation.meta.profile", "Observation.meta.profile", false));

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

        copier.copy(source, tgt, new AnnotatedAttribute("Observation.category", "Observation.category", "Observation.category", false));

        assertThat(tgt.getCategory().stream().map(CodeableConcept::getCoding).map(List::getFirst).map(Coding::getCode))
                .containsExactly("Test", "Test2");
    }

    private static Meta defaultMeta() {
        return new Meta().setProfile(List.of(new CanonicalType("https://www.medizininformatik-initiative.de/fhir/core/modul-diagnose/StructureDefinition/Diagnose")));
    }
}
