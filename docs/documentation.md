# Documentation Overview

### Configuration

TORCH is configured using environment variables that control its behavior and integration with FHIR servers.

👉 Visit the [**Configuration**](./configuration.md) for a detailed list of environment variables and their descriptions.

### API

TORCH provides a FHIR REST API for extracting data based on the CRTDL.
It implements the FHIR [Asynchronous Bulk Data Request Pattern][1].

👉 Visit the [**API Documentation**](./api/api.md) for details on endpoints, request/response formats, and usage
examples.

### Task API

Jobs can be listed, inspected, reprioritized, paused, resumed, cancelled, and deleted through a FHIR
[`Task`](https://www.hl7.org/fhir/task.html)-based control API.

👉 Visit the [**Task API Documentation**](./api/task-api.md) for endpoint details and status/priority mappings.

### CRTDL

The Clinical Resource Transfer Definition Language (CRTDL) is a JSON format used to describe cohorts and data extraction
rules.

👉 Visit the [**CRTDL Documentation**](./crtdl/crtdl.md) for a comprehensive guide on how to create and use CRTDL
definitions.


[1]: <http://hl7.org/fhir/R5/async-bulk.html>


