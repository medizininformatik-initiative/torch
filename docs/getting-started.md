## Getting Started

### Quickstart

Torch can be started with a single command using docker:

```sh
docker run -d --name torch -p 8080:8080 ghcr.io/medizininformatik-initiative/torch:1.0.0-alpha.7
```

### Install prerequisites

TORCH interacts with the following components directly:

- a CQL ready FHIR Server like [Blaze](https://github.com/samply/blaze)
  or [FLARE](https://github.com/medizininformatik-initiative/flare) for Cohort
- A FHIR Server / FHIR Search API
- Reverse Proxy (NGINX)

The reverse proxy allows for integration into a site's multi-server infrastructure and provides a means of serving
the extracted data.

### Feasibility Deploy

For simplicity torch is integrated in
the [feasibility-triangle](https://github.com/medizininformatik-initiative/feasibility-deploy/tree/main/feasibility-triangle)
of the feasibility-deploy repository, but can also be installed without it.
