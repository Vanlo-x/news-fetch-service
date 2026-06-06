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

## Source Configuration

News sources are configured under `news-fetch.sources` in `application.yaml`. The default project configuration uses an empty source list, so the service starts without fetching anything.

Example:

```yaml
news-fetch:
  sources:
    - id: tech-rss
      name: Tech RSS
      type: RSS
      enabled: true
      priority: 100
      category: technology
      language: zh
      region: CN
      url: https://example.com/rss.xml
      method: GET
      timeout-ms: 5000
      max-response-bytes: 1048576
      retry-count: 0
```

Current configuration loading only binds and validates source metadata. It does not connect to the source URL.

Configuration rules:

* `id`, `name`, `type`, and `url` are required.
* `id` must be unique across all configured sources and may contain only letters, numbers, underscores, and hyphens.
* `url` must use `http://` or `https://`.
* `url` must not contain user info.
* `url` must not target `localhost`, loopback, private IP ranges, link-local addresses, unspecified addresses, or metadata service addresses.
* Hostname validation is parse-only in this milestone; DNS resolution is intentionally out of scope until the restricted HTTP client layer.
* `enabled` defaults to `true`.
* `priority` defaults to `100`; lower values are loaded first.
* `method` defaults to `GET`.
* `timeout-ms` defaults to `5000`.
* `max-response-bytes` defaults to `1048576`.
* `retry-count` defaults to `0`.

## Restricted Source HTTP Client

The infrastructure layer includes a restricted source HTTP client for future RSS/API adapters. It is not wired into `/v1/news/fetch` yet.

Current behavior:

* Re-validates source URLs before each request.
* Supports `GET` and empty-body `POST`.
* Applies per-request timeout.
* Enforces `max-response-bytes` while reading the response.
* Does not follow redirects.
* Drops sensitive outbound headers: `Authorization`, `Cookie`, and `Proxy-Authorization`.
* Does not parse RSS, normalize news, retry, fallback, or cache.

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
* `sourceIds` may contain up to 50 non-blank IDs using letters, numbers, underscores, and hyphens. Missing or empty means all enabled sources in later milestones.
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
