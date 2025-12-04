## Getting Started

### Quickstart

Torch can be started with a single command using docker and by providing a configuration file (e.g. .env).
See [Configuration](configuration.md) for details on the configuration options.
```sh
docker run -d --name torch --env-file [/path/to/.env] -p 8080:8080 ghcr.io/medizininformatik-initiative/torch:1.0.0-alpha.12 
```

### Install prerequisites

TORCH interacts with the following components:

- a CQL ready FHIR Server like [Blaze](https://github.com/samply/blaze)
  or [FLARE](https://github.com/medizininformatik-initiative/flare) for cohort retrieval
- A FHIR Server / FHIR Search API
- Reverse Proxy (NGINX) (set by configuration)

The reverse proxy allows for integration into a site's multi-server infrastructure and provides a means of serving
the extracted data. In practice, it acts as a **sidecar container** for TORCH, handling the delivery of generated files.

### Feasibility Deploy

For simplicity torch is integrated in
the [feasibility-triangle](https://github.com/medizininformatik-initiative/feasibility-deploy/tree/main/feasibility-triangle)
of the feasibility-deploy repository, but can also be installed without it.

## Transfer Script

TORCH provides a companion **transfer script** designed to automate the workflow of submitting a data extraction
request, polling the status, and transferring the resulting files to a target FHIR server.

The transfer script will:

1. Take the **CRTDL** and generate a FHIR parameters resource to send to TORCH.
2. Execute the $extract-data operation on the TORCH server using the parameters resource as input.
3. Poll the TORCH status endpoint until the export is complete.
4. Download the resulting patient-oriented FHIR bundles into a temp dir.
5. Upload these files to a configured target FHIR server using the `blazectl` tool.
6. Provide progress feedback and error handling at each step.

## Verification

For container images, we use cosign to sign images. This allows users to confirm the image was built by the expected CI
pipeline and has not been modified after publication.

```
cosign verify "ghcr.io/medizininformatik-initiative/torch:v1.0.0" \
--certificate-identity-regexp "https://github.com/medizininformatik-initiative/torch.*" \
--certificate-oidc-issuer "https://token.actions.githubusercontent.com" \
--certificate-github-workflow-ref="refs/tags/v1.0.0" \
-o text
```

The expected output is:

```
Verification for ghcr.io/medizininformatik-initiative/torch:v1.0.0 --
The following checks were performed on each of these signatures:
- The cosign claims were validated
- Existence of the claims in the transparency log was verified offline
- The code-signing certificate was verified using trusted certificate authority certificates
```

This output ensures that the image was build on the GitHub workflow on the repository
`medizininformatik-initiative/torch` and tag `v1.0.0`.

