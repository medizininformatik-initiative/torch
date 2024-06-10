package de.medizininformatikinitiative;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import ca.uhn.fhir.context.FhirContext;

@SpringBootApplication
public class StructureDefinitionParserApplication {

    public static void main(String[] args) {
        SpringApplication.run(StructureDefinitionParserApplication.class, args);
    }

    @Bean
    public FhirContext fhirContext() {
        return FhirContext.forR4();
    }

    @Bean
    public CommandLineRunner commandLineRunner(CDSStructureDefinitionHandler parser) {
        return args -> parser.run();
    }
}
