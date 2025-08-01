package de.medizininformatikinitiative.torch.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "torch")
public record TorchProperties(
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
    public record Max(int connections) {
    }

    public record Profile(String dir) {
    }

    public record Mapping(String consent, String typeToConsent) {
    }

    public record Fhir(String url, Max max, Page page, TestPopulation testPopulation, Oauth oauth, Disable disable) {
        public record TestPopulation(String path) {
        }

        public record Page(int count) {

        }

        public record Disable(boolean async) {
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
