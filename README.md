# news-fetch-service

Spring Boot service for fetching and normalizing news data from configurable sources.

The current implementation supports real RSS fetching through configured sources. It does not implement JSON API sources, HTML sources, retry, fallback, caching, deduplication, source status, or final ranking.

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

Current configuration loading binds and validates source metadata. RSS sources are fetched only when `/v1/news/fetch` is called.

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

The infrastructure layer includes a restricted source HTTP client used by the RSS adapter.

Current behavior:

* Re-validates source URLs before each request.
* Supports `GET` and empty-body `POST`.
* Applies per-request timeout.
* Enforces `max-response-bytes` while reading the response.
* Does not follow redirects.
* Drops sensitive outbound headers: `Authorization`, `Cookie`, and `Proxy-Authorization`.
* Does not retry, fallback, cache, or deduplicate.

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

The RSS news fetch API is also available:

```bash
curl -X POST http://localhost:8080/v1/news/fetch \
  -H "Content-Type: application/json" \
  -d '{"sourceIds":["tech-rss"],"category":"technology","language":"zh","region":"CN","limit":10}'
```

Response shape:

```json
{"status":"OK","items":[...],"errors":[]}
```

Current `/v1/news/fetch` request contract:

* Request body is required.
* All filters are optional.
* `sourceIds` may contain up to 50 non-blank IDs using letters, numbers, underscores, and hyphens. Missing or empty means all enabled RSS sources.
* `category` may contain letters, numbers, underscores, and hyphens.
* `language` must be a lowercase two-letter language code, such as `zh` or `en`.
* `region` must be an uppercase two-letter region code, such as `CN` or `US`.
* `limit` must be between 1 and 100 when provided. Missing `limit` defaults to `20`.
* Current implementation supports only `RSS` sources. Explicitly requested non-RSS sources are returned as fetch errors.

## Run Tests

On Windows:

```powershell
.\mvnw.cmd test
```

On macOS or Linux:

```bash
./mvnw test
```
