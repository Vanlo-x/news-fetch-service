# news-fetch-service PRD

## 1. 服务定位

`news-fetch-service` 是新闻推送系统中的数据获取服务。

它负责从可配置的新闻来源获取新闻，统一转换为标准结构，完成基础去重、失败兜底和缓存返回，并通过 HTTP API 提供给下游服务调用。

当前服务只负责新闻数据获取，不负责 AI 摘要、个性化推荐、用户系统、推送通知或前端展示。

## 2. 当前阶段目标

第一阶段目标是创建一个可运行、可测试、结构清晰的 Java Spring Boot + Maven 服务骨架。

第一阶段不实现真实新闻获取。

## 3. 服务边界

### 负责

* 加载可配置新闻来源
* 拉取新闻原始数据
* 解析 RSS / API / HTML 来源
* 标准化为统一 NewsItem
* 基础去重
* 失败重试
* 来源级 fallback
* 缓存 fallback
* 暴露 HTTP API
* 记录来源状态和错误信息

### 不负责

* AI 摘要
* 个性化推荐
* 用户偏好
* 推送通知
* 前端页面
* 新闻最终排版
* 绕过反爬、验证码、登录或付费墙

## 4. 技术栈

* Java 21
* Spring Boot
* Maven
* JUnit 5
* Jackson
* Jakarta Validation
* Spring Boot Actuator

后续阶段可按需加入：

* Rome RSS
* Caffeine
* Redis
* PostgreSQL
* springdoc-openapi

## 5. 核心模块

```text
api/
  对外 HTTP Controller 和 DTO

domain/
  领域模型，如 NewsItem、SourceConfig、FetchError

application/
  业务编排，如 FetchOrchestrator、FallbackManager、Deduplicator

adapters/
  不同来源适配器，如 RSS、JSON API、HTML

infrastructure/
  HTTP Client、缓存、配置存储、日志、监控

config/
  Spring 配置和来源配置加载
```

## 6. 核心数据模型

### NewsItem

字段规划：

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

### SourceConfig

字段规划：

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
retryCount
fallbackSourceIds
parserConfig
rateLimit
cacheTtlSeconds
```

### FetchError

字段规划：

```text
sourceId
stage
code
message
retryable
occurredAt
```

## 7. API 初始规划

### GET /health

返回服务健康状态。

第一阶段需要实现。

### POST /v1/news/fetch

获取新闻数据。

第一阶段只定义 DTO，不实现真实获取。

### GET /v1/sources/status

获取来源健康状态。

后续阶段实现。

### POST /v1/sources/validate

校验来源配置。

后续阶段实现。

## 8. 兜底策略

后续实现顺序：

1. 单来源失败不影响整体请求。
2. 单来源失败后执行 retry。
3. retry 后失败则尝试 fallbackSourceIds。
4. 仍失败则读取未过期缓存。
5. 调用方允许时可返回过期缓存。
6. 所有降级结果必须明确标记。
7. 不允许伪造新闻数据。

## 9. 安全要求

* 来源 URL 只允许 http / https。
* 禁止请求 localhost、内网 IP、link-local、metadata service。
* 请求必须有超时。
* 响应体大小必须限制。
* 日志不得记录敏感 header。
* 单元测试不得请求真实互联网。
* 不实现绕过反爬、登录、验证码或付费墙的逻辑。

## 10. 第一阶段验收标准

第一阶段只做项目骨架。

完成标准：

* `./mvnw test` 或 `mvn test` 通过。
* `./mvnw spring-boot:run` 或 `mvn spring-boot:run` 可以启动服务。
* `GET /health` 返回 ok。
* 项目目录结构清晰。
* 存在基础 domain model。
* 存在基础 DTO。
* 存在 README 本地启动说明。
* 不包含真实 RSS 拉取、缓存、fallback 或复杂业务逻辑。
