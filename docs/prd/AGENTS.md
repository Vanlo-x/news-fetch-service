# AGENTS.md

## Project

This repository implements `news-fetch-service`, a Java Spring Boot service for fetching news from configurable sources.

The service will eventually support source configuration, RSS/API/HTML adapters, normalization, deduplication, fallback, caching, source status, and HTTP APIs.

Current development must proceed milestone by milestone. Do not implement future milestones unless the task explicitly asks for them.

## Tech Stack

* Java 21
* Spring Boot
* Maven
* JUnit 5
* Jackson
* Jakarta Validation
* Spring Boot Actuator

## Package Root

Use:

```text
com.example.newsfetch
```

If the generated project currently uses another root package, either refactor it to `com.example.newsfetch` before continuing or keep all new code consistent with the existing package root.

## Architecture Rules

Use this package structure under the package root:

```text
api
application
domain
adapters
infrastructure
config
```

Rules:

* Keep domain models independent from Spring MVC.
* Keep source adapters isolated from orchestration logic.
* Adapters must not perform global fallback, caching, deduplication, or final sorting.
* Fetch orchestration belongs in the application layer.
* Infrastructure implementations must be replaceable behind interfaces.
* Do not hardcode news sources in Java code.
* Do not broaden task scope without explicit instruction.
* Prefer readable Java over clever abstractions.
* Prefer Java records for immutable DTOs and simple value objects where appropriate.

## Security Rules

* Validate source URLs before external requests.
* Allow only http and https.
* Block localhost, private IP ranges, link-local addresses, and metadata service addresses.
* Enforce request timeout.
* Enforce response size limit.
* Do not log Authorization, Cookie, API keys, or secrets.
* Do not implement scraping that bypasses login, captcha, robots restrictions, or paywalls.

## Testing Rules

* Use JUnit 5.
* Do not call real external news sources in unit tests.
* Use fixtures, fake adapters, or mocked HTTP clients.
* Add or update tests for every behavior change.
* Do not skip failing tests.
* Do not weaken assertions just to make tests pass.

Before completing a task, run:

```bash
./mvnw test
```

On Windows, this may be:

```powershell
.\mvnw.cmd test
```

## Documentation Rules

If public API behavior changes, update:

```text
docs/api/openapi.yaml
```

If local developer workflow changes, update:

```text
README.md
```

## Done Definition

A task is done only when:

* Code compiles.
* Relevant tests are added or updated.
* `./mvnw test` or `.\mvnw.cmd test` passes.
* Public API changes are documented.
* The implementation stays within the requested scope.
