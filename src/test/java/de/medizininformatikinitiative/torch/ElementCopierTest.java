package de.medizininformatikinitiative.torch;

import ca.uhn.fhir.context.FhirContext;
import de.medizininformatikinitiative.torch.exceptions.MustHaveViolatedException;
import de.medizininformatikinitiative.torch.model.crtdl.Attribute;
import de.medizininformatikinitiative.torch.setup.IntegrationTestSetup;
import de.medizininformatikinitiative.torch.util.ElementCopier;
import org.hl7.fhir.r4.model.CanonicalType;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Meta;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.PrimitiveType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class ElementCopierTest {

    private final IntegrationTestSetup itSetup = new IntegrationTestSetup();
    private final FhirContext fhirContext = FhirContext.forR4();
    private final ElementCopier copier = new ElementCopier(itSetup.getCds(), fhirContext, itSetup.fhirPathBuilder());

    @Test
    void singleProfile() throws MustHaveViolatedException {
        Observation src = new Observation();
        Observation tgt = new Observation();

        Meta meta = new Meta();
        meta.setProfile(List.of(new CanonicalType("https://www.medizininformatik-initiative.de/fhir/core/modul-diagnose/StructureDefinition/Diagnose")));
        src.setMeta(meta);

        copier.copy(src, tgt, new Attribute("Observation.meta.profile", false));

        assertThat(tgt.getMeta().getProfile().stream().map(PrimitiveType::getValue))
                .containsExactly("https://www.medizininformatik-initiative.de/fhir/core/modul-diagnose/StructureDefinition/Diagnose");
    }

    @Test
    void multiProfilePrePopulatedTarget() throws MustHaveViolatedException {
        Observation src = new Observation();
        Observation tgt = new Observation();

        Meta meta2 = new Meta();
        Meta meta = new Meta();
        meta.setProfile(List.of(new CanonicalType("Test"),
                new CanonicalType("https://www.medizininformatik-initiative.de/fhir/core/modul-diagnose/StructureDefinition/Diagnose")));
        src.setMeta(meta);
        tgt.setMeta(meta2);

        copier.copy(src, tgt, new Attribute("Observation.meta.profile", false));

        assertThat(tgt.getMeta().getProfile().stream().map(PrimitiveType::getValue))
                .containsExactly("Test", "https://www.medizininformatik-initiative.de/fhir/core/modul-diagnose/StructureDefinition/Diagnose");

    }

    @Test
    void multiCategory() throws MustHaveViolatedException {
        Observation src = new Observation();
        Observation tgt = new Observation();

        List<CodeableConcept> categories = List.of(
                new CodeableConcept().addCoding(new Coding().setCode("Test")),
                new CodeableConcept().addCoding(new Coding().setCode("Test2"))
        );
        Meta meta = new Meta();
        meta.setProfile(List.of(new CanonicalType("Test"),
                new CanonicalType("https://www.medizininformatik-initiative.de/fhir/core/modul-diagnose/StructureDefinition/Diagnose")));
        src.setMeta(meta);
        src.setCategory(categories);


        copier.copy(src, tgt, new Attribute("Observation.category", false));

        assertThat(tgt.getCategory().get(0).getCoding().getFirst().getCode())
                .isEqualTo("Test");
        assertThat(tgt.getCategory().get(1).getCoding().getFirst().getCode())
                .isEqualTo("Test2");

    }


}
