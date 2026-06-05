# news-fetch-service

Spring Boot service skeleton for fetching and normalizing news data from configurable sources.

This milestone only provides the initial project structure, basic domain and DTO skeletons, and a simple health endpoint. It does not implement real news fetching, RSS parsing, fallback, caching, source status, or external network access.

## Tech Stack

* Java 21
* Spring Boot
* Maven
* JUnit 5

## Package Structure

Code is organized under `com.vanlo.newsfetch`:

```text
api
application
domain
adapters
infrastructure
config
```

## Run Locally

On Windows:

```powershell
.\mvnw.cmd spring-boot:run
```

On macOS or Linux:

```bash
./mvnw spring-boot:run
```

After startup, verify the service:

```bash
curl http://localhost:8080/health
```

Expected response:

```json
{"status":"ok"}
```

## Run Tests

On Windows:

```powershell
.\mvnw.cmd test
```

On macOS or Linux:

```bash
./mvnw test
```
