package de.medizininformatikinitiative.torch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {
        "de.medizininformatikinitiative.torch.config",
        "de.medizininformatikinitiative.torch.rest"
})
public class Torch {

    public static void main(String[] args) {
        SpringApplication.run(Torch.class, args);
    }
}
