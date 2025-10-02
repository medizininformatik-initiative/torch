## Getting Started

### Quickstart

Torch can be started with a single command using docker and by providing a configuration file e.g. .env.
See [Configuration](configuration.md) for details on the configuration options.
```sh
docker run -d --name torch -p 8080:8080 ghcr.io/medizininformatik-initiative/torch:1.0.0-alpha.7 --env-file [/path/to/.env]
```

### Install prerequisites

TORCH interacts with the following components:

- a CQL ready FHIR Server like [Blaze](https://github.com/samply/blaze)
  or [FLARE](https://github.com/medizininformatik-initiative/flare) for Cohort
- A FHIR Server / FHIR Search API
- Reverse Proxy (NGINX) (set by configuration)

The reverse proxy allows for integration into a site's multi-server infrastructure and provides a means of serving
the extracted data. In practice, it acts as a **sidecar container** for TORCH, handling the delivery of generated files.

### Feasibility Deploy

For simplicity torch is integrated in
the [feasibility-triangle](https://github.com/medizininformatik-initiative/feasibility-deploy/tree/main/feasibility-triangle)
of the feasibility-deploy repository, but can also be installed without it.
