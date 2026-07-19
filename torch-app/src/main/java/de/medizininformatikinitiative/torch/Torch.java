package de.medizininformatikinitiative.torch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;


@SpringBootApplication
@EnableScheduling
public class Torch {

    public static void main(String[] args) {
        SpringApplication.run(Torch.class, args);
    }
}
