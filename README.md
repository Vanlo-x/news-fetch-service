# news-fetch-service

Spring Boot service skeleton for fetching and normalizing news data from configurable sources.

This milestone only provides the initial project structure, basic domain and DTO skeletons, a simple health endpoint, and a placeholder news fetch endpoint. It does not implement real news fetching, RSS parsing, fallback, caching, source status, or external network access.

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

The placeholder news fetch API is also available:

```bash
curl -X POST http://localhost:8080/v1/news/fetch \
  -H "Content-Type: application/json" \
  -d '{"sourceIds":["demo"],"category":"technology","language":"zh","region":"CN","limit":10}'
```

Expected placeholder response:

```json
{"status":"OK","items":[],"errors":[]}
```

Current `/v1/news/fetch` request contract:

* Request body is required.
* All filters are optional.
* `sourceIds` may contain up to 50 non-blank IDs. Missing or empty means all enabled sources in later milestones.
* `category` may contain letters, numbers, underscores, and hyphens.
* `language` must be a lowercase two-letter language code, such as `zh` or `en`.
* `region` must be an uppercase two-letter region code, such as `CN` or `US`.
* `limit` must be between 1 and 100 when provided.

## Run Tests

On Windows:

```powershell
.\mvnw.cmd test
```

On macOS or Linux:

```bash
./mvnw test
```
