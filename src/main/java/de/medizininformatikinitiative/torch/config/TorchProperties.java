package de.medizininformatikinitiative.torch.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "torch")
public record TorchProperties(
        Base base,
        Output output,
        Profile profile,
        Mapping mapping,
        Fhir fhir,
        Flare flare,
        Results results,
        int batchsize,
        int maxConcurrency,
        int bufferSize,
        String mappingsFile,
        String conceptTreeFile,
        String dseMappingTreeFile,
        boolean useCql
) {

    public record Base(String url) {

    }

    public record Output(File file) {
        public record File(Server server) {
            public record Server(String url) {

            }
        }

    }


    public record Profile(String dir) {
    }

    public record Mapping(String consent, String typeToConsent) {
    }

    public record Fhir(String url, int pageCount, TestPopulation testPopulation, Oauth oauth) {
        public record TestPopulation(String path) {
        }

        public record Oauth(Issuer issuer, Client client) {
            public record Issuer(String uri) {
            }

            public record Client(String id, String secret) {
            }
        }
    }

    public record Flare(String url) {
    }

    public record Results(String dir, String persistence) {
    }
}
