package de.medizininformatikinitiative;

import ca.uhn.fhir.context.FhirContext;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.SpringApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = "de.medizininformatikinitiative")
public class Torch {


    public static void main(String[] args) {
        SpringApplication.run(Torch.class, args);
    }


}
