# news-fetch-service PRD

## 1. 服务定位

`news-fetch-service` 是新闻推送系统中的数据获取服务。

它负责从可配置的新闻来源获取新闻，统一转换为标准结构，完成基础去重、失败兜底和缓存返回，并通过 HTTP API 提供给下游服务调用。

当前服务只负责新闻数据获取，不负责 AI 摘要、个性化推荐、用户系统、推送通知或前端展示。

## 2. 当前阶段目标

项目包根：

```text
com.vanlo.newsfetch
```

当前阶段已经具备：

* Spring Boot + Maven 项目骨架。
* `/health` 健康检查。
* `/v1/news/fetch` API 契约和基础参数校验。
* 来源配置加载和校验。
* Source URL 安全校验。
* 受限 HTTP client。
* RSS 真实拉取和 Rome RSS/Atom 解析。
* 请求内标准化和基础去重。
* FetchOrchestrator 基础编排。
* retryable 来源失败的基础重试。
* 一层顺序 fallback。
* source 级内存 cache。
* source status 和基础日志。
* 基础确定性排序。

当前阶段仍不实现：

* JSON API 来源。
* HTML 来源。
* 持久化 cache。
* 持久化 source status。
* retry backoff / jitter。
* 递归 fallback 链。
* AI/质量评分排序。

## 3. 技术栈

* Java 21
* Spring Boot
* Maven
* JUnit 5
* Jackson
* Jakarta Validation
* Spring Boot Actuator
* Rome RSS

后续阶段可按需加入：

* Caffeine
* Redis
* PostgreSQL
* springdoc-openapi

## 4. 核心模块

```text
api/
  对外 HTTP Controller 和 DTO

domain/
  领域模型，如 NewsItem、SourceConfig、FetchError

application/
  业务入口、FetchOrchestrator、标准化、去重和后续编排

adapters/
  不同来源适配器。当前实现 RSS adapter

infrastructure/
  受限 HTTP client、URL 安全校验等基础设施

config/
  Spring 配置和来源配置加载
```

## 5. 核心数据模型

### NewsItem

当前字段：

```text
id
title
url
sourceId
sourceName
publishedAt
fetchedAt
summary
content
author
imageUrl
category
language
region
tags
fingerprint
qualityScore
raw
```

当前 RSS 映射规则：

* `title` 来自 RSS/Atom entry title。
* `url` 来自 entry link。
* `publishedAt` 优先 published date，其次 updated date。
* `fetchedAt` 为服务拉取时间。
* `sourceId`、`sourceName`、`category`、`language`、`region` 来自 `SourceConfig`。
* `summary` 来自 description。
* `author` 尽量读取 RSS/Atom author。
* RSS adapter 会先生成临时 ID。
* application 层标准化后重新生成 `id` 和 `fingerprint`。
* 有 URL 时，`fingerprint` 基于规范化 URL。
* 没有 URL 时，`fingerprint` 基于规范化 title 和 published time。
* `raw` 只保存少量元数据，不保存完整原文。

### SourceConfig

当前字段：

```text
id
name
type
enabled
priority
category
language
region
url
method
headers
params
timeoutMs
maxResponseBytes
retryCount
fallbackSourceIds
parserConfig
rateLimit
cacheTtlSeconds
```

当前配置加载规则：

* 来源配置位于 `news-fetch.sources`。
* `id`、`name`、`type`、`url` 必填。
* `id` 必须唯一，且只允许字母、数字、下划线和连字符。
* `url` 必须使用 `http://` 或 `https://`。
* `url` 不允许包含 user info。
* `url` 不允许指向 localhost、loopback、内网 IP、link-local、unspecified address 或 metadata service。
* 当前阶段不做 DNS 解析，只校验 URL 字面量和 IP 字面量。

### FetchError

当前字段：

```text
sourceId
stage
code
message
retryable
occurredAt
```

当前常见错误：

* `SOURCE_NOT_FOUND`
* `SOURCE_DISABLED`
* `UNSUPPORTED_SOURCE_TYPE`
* `HTTP_CLIENT_ERROR`
* `HTTP_STATUS`
* `RSS_PARSE_ERROR`

## 6. API

### GET /health

返回服务健康状态。

### POST /v1/news/fetch

从配置的 RSS 来源拉取新闻。

请求规则：

* 请求体必需。
* 所有过滤字段可选。
* `sourceIds` 缺省或为空时使用所有 enabled RSS 来源。
* 指定 `sourceIds` 时，只拉取匹配的 enabled RSS 来源。
* `category`、`language`、`region` 用于匹配来源配置。
* `limit` 缺省为 `20`，最大为 `100`。

响应状态：

* 有 items 且无 errors：`OK`
* 有 items 且有 errors：`PARTIAL`
* 无 items 且有 errors：`FAILED`
* 无 matched RSS 来源且无 errors：`OK`

当前标准化和去重规则：

* 文本字段会 trim，并折叠连续空白字符。
* URL 会小写 scheme/host、移除 fragment、规范化 path。
* 相同 fingerprint 的新闻只保留第一条。
* 去重只在单次请求内生效，不持久化，不使用缓存。

当前排序规则：

* 标准化和去重之后、limit 之前排序。
* `publishedAt` 越新越靠前。
* `publishedAt` 缺失的新闻排在有发布时间的新闻之后。
* 发布时间相同或都缺失时，按来源配置顺序排序。
* 仍相同则保持原始收集顺序。

当前 retry 规则：

* `retryCount` 表示首次请求失败后的额外尝试次数。
* 只有无 items 且所有错误均为 `retryable=true` 时才重试。
* 重试成功时，不在 API 响应中暴露中间失败。
* 重试耗尽时，返回最后一次失败。
* 当前不实现 backoff、jitter、异步调度或 source status 记录。

当前 fallback 规则：

* 主来源经过 retry 后仍无 items 且有 errors 时，才尝试 `fallbackSourceIds`。
* 兜底来源按配置顺序尝试。
* 兜底来源必须已配置、enabled，且有可用 adapter。
* 第一个返回 items 的兜底来源会终止后续兜底尝试。
* 主来源错误会保留，因此兜底成功通常返回 `PARTIAL`。
* 当前只支持一层 fallback，不递归执行兜底来源自己的 `fallbackSourceIds`。

当前 cache 规则：

* `cacheTtlSeconds` 大于 `0` 时启用该来源的内存缓存。
* cache key 为 source id。
* 只缓存有 items 且无 errors 的成功来源结果。
* 失败结果不缓存。
* cache 命中时跳过该来源的 HTTP fetch、retry 和 fallback。
* fallback 来源使用自己的 cache entry。
* cache 中的 items 仍会在每次请求中经过标准化、去重、limit 和状态计算。
* 当前不实现持久化缓存、容量淘汰、分布式缓存或缓存指标。

当前 source status 和日志规则：

* `GET /v1/news/sources/status` 返回所有已配置来源的最近状态。
* `GET /v1/news/sources/{sourceId}/status` 返回单个来源的最近状态。
* enabled 但尚未拉取的来源状态为 `UNKNOWN`。
* disabled 来源状态为 `DISABLED`。
* 每次来源执行后记录 health、lastFetchAt、lastSuccessAt、lastFailureAt、lastErrorCode、lastItemCount、lastDurationMs、lastCacheHit、lastFallbackUsed 和 lastResolvedSourceId。
* 兜底成功时，主来源记录为 `DEGRADED`，兜底来源记录自己的执行状态。
* 基础日志记录 sourceId、health、items、errors、durationMs、cacheHit、fallbackUsed、resolvedSourceId 和 errorCode。
* 日志不记录 URL、headers、Authorization、Cookie、API keys、响应体或原文内容。
* 当前状态只保存在进程内存中，服务重启后丢失。

## 7. 安全要求

* 来源 URL 只允许 http / https。
* 禁止请求 localhost、内网 IP、link-local、metadata service。
* 请求必须有超时。
* 响应体大小必须限制。
* 不跟随重定向。
* 请求前必须复检 URL。
* 日志不得记录 Authorization、Cookie、API keys 或 secrets。
* 单元测试不得请求真实互联网。
* 不实现绕过反爬、登录、验证码或付费墙的逻辑。

## 8. 后续实现顺序

建议后续阶段：

1. retry backoff / jitter。
2. recursive fallback / fallback policy。
3. persistent/distributed cache。
4. persistent source status / metrics。
5. DNS 解析后的安全校验。
6. JSON API source adapter。
7. HTML source adapter。
8. 部署和运行文档。
