package de.medizininformatikinitiative;

import ca.uhn.fhir.context.FhirContext;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class Torch {

    @Bean
    FhirContext context() {
        return FhirContext.forR4();
    }
}
