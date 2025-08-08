# Architecture

This project uses a Spring Boot backend and a TypeScript/JavaScript frontend. The backend exposes REST APIs and
interacts with a FHIR server. The frontend communicates with the backend via HTTP.

## Components

- **Spring Boot Application**: Handles business logic and API endpoints.
- **FHIR Server**: Stores and retrieves healthcare data.
- **Reverse Proxy (NGINX)**: Routes requests and serves static content.
- **Frontend (TypeScript/JavaScript)**: User interface for interacting with the system.

## Data Flow

[Consent](../implementation/consent.md).
