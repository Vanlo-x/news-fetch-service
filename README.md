# news-fetch-service

Spring Boot service for fetching and normalizing news data from configurable sources.

The current implementation supports real RSS fetching through configured sources, request-level normalization, basic deduplication, deterministic result sorting, per-source retry for retryable failures, one-level fallback sources, source-level in-memory caching, source status, and basic fetch logs. It does not implement JSON API sources, HTML sources, persistent cache storage, persistent status storage, persistent deduplication, backoff, recursive fallback chains, or AI/quality ranking.

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
      cache-ttl-seconds: 60
```

Current configuration loading binds and validates source metadata. RSS sources are fetched only when `/v1/news/fetch` is called.

## Fetch Orchestration

`FetchNewsUseCase` maps the HTTP request contract into an application command. `FetchOrchestrator` owns the current fetch pipeline:

* Select configured sources from request filters.
* Resolve a source adapter by `SourceType`.
* Fetch source items through the adapter.
* Retry retryable source failures according to `retry-count`.
* Try configured fallback sources when a source still fails without items.
* Read and write source-level cache when `cache-ttl-seconds` is configured.
* Record source status and basic fetch logs.
* Merge source-level errors without failing the whole request.
* Normalize fetched items.
* Deduplicate request-local items.
* Sort results by published time and source order.
* Apply the response limit and calculate the response status.

Current adapter support is limited to `RSS`. Future JSON API and HTML sources should be added as new `NewsSourceAdapter` implementations.

Current retry behavior is intentionally basic:

* `retry-count` means additional attempts after the first attempt.
* Only retryable source failures are retried.
* A successful retry suppresses the earlier transient error from the API response.
* Exhausted retries return the final failure.
* No backoff, jitter, scheduling, or source status logging is implemented yet.

Current fallback behavior is also intentionally basic:

* `fallback-source-ids` are tried in configured order after the primary source has no items and has errors.
* Fallback sources must be configured, enabled, and supported by an adapter.
* The first fallback source that returns items stops the fallback chain.
* Primary source errors are preserved, so successful fallback usually returns `PARTIAL`.
* Fallback is one level only; fallback sources do not recursively invoke their own fallback lists.

Current cache behavior is in-memory and source-scoped:

* `cache-ttl-seconds` enables cache for that source when greater than `0`.
* Cache key is the source id.
* Only successful source results with items and no errors are cached.
* Failed source results are not cached.
* Cache hit skips HTTP fetch, retry, and fallback for that source.
* Fallback sources use their own cache entries.
* Cached items are still normalized, deduplicated, limited, and status-calculated per request.

Current source status behavior is in-memory:

* `GET /v1/news/sources/status` returns all configured source statuses.
* `GET /v1/news/sources/{sourceId}/status` returns one configured source status.
* A source starts as `UNKNOWN` when enabled and not fetched yet.
* Disabled sources are reported as `DISABLED`.
* Fetches update last health, fetch time, success/failure time, item count, duration, cache hit, fallback use, resolved source id, and last error.
* Successful fallback records the primary source as `DEGRADED` and the fallback source according to its own fetch result.
* Status is process-local and is lost on restart.

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
* `cache-ttl-seconds` is optional; missing or `0` disables source-level cache.

## Restricted Source HTTP Client

The infrastructure layer includes a restricted source HTTP client used by the RSS adapter.

Current behavior:

* Re-validates source URLs before each request.
* Supports `GET` and empty-body `POST`.
* Applies per-request timeout.
* Enforces `max-response-bytes` while reading the response.
* Does not follow redirects.
* Drops sensitive outbound headers: `Authorization`, `Cookie`, and `Proxy-Authorization`.
* Does not own retry, fallback, cache, or deduplication behavior.

## Run Locally

On Windows:

```powershell
.\mvnw.cmd spring-boot:run
```

On macOS or Linux:

```bash
./mvnw spring-boot:run
```

To run with verified real RSS sources:

```powershell
.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=local
```

The `local` profile configures these RSS sources:

* `hacker-news-rss` - `https://news.ycombinator.com/rss`
* `techcrunch-rss` - `https://techcrunch.com/feed/`
* `the-verge-rss` - `https://www.theverge.com/rss/index.xml`
* `ars-technica-rss` - `https://feeds.arstechnica.com/arstechnica/index`
* `bbc-business-rss` - `https://feeds.bbci.co.uk/news/business/rss.xml`

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
  -d '{"sourceIds":["hacker-news-rss","techcrunch-rss","the-verge-rss","ars-technica-rss","bbc-business-rss"],"limit":10}'
```

Response shape:

```json
{"status":"OK","items":[...],"errors":[]}
```

Source status is available at:

```bash
curl http://localhost:8080/v1/news/sources/status
curl http://localhost:8080/v1/news/sources/tech-rss/status
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

## Normalization, Deduplication, And Sorting

Fetched RSS items are normalized before the response is limited:

* Text fields are trimmed and internal whitespace is collapsed.
* URLs are normalized by lowercasing scheme/host, removing fragments, and normalizing paths.
* `id` and `fingerprint` are regenerated after normalization.
* If a normalized URL exists, fingerprint is based on that URL.
* If URL is missing, fingerprint is based on normalized title and published time.
* Duplicate fingerprints are removed while preserving the first item encountered in source order.

Current deduplication is in-memory and request-scoped only. It is not persistent and is separate from source-level fetch caching.

Current sorting is deterministic:

* Items with newer `publishedAt` values are returned first.
* Items without `publishedAt` are returned after items with timestamps.
* When timestamps match, configured source order is used.
* Remaining ties preserve original collection order.

## Run Tests

On Windows:

```powershell
.\mvnw.cmd test
```

On macOS or Linux:

```bash
./mvnw test
```
