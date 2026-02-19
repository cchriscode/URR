# URR Spring Backend

Spring Boot 마이크로서비스 기반 티켓팅/예약 플랫폼 백엔드.

## Services

| 서비스 | 포트 | 설명 |
|--------|------|------|
| `gateway-service` | 3001 | API 게이트웨이 (JWT 검증, VWR 필터, Rate Limiting) |
| `ticket-service` | 3002 | 이벤트/좌석/예매/양도/멤버십 |
| `payment-service` | 3003 | 결제 처리 (Kafka Producer) |
| `stats-service` | 3004 | 통계/대시보드 (Kafka Consumer) |
| `auth-service` | 3005 | 인증/회원 (JWT, Google OAuth, Refresh Token Rotation) |
| `queue-service` | 3007 | 대기열/VWR (Redis ZSET, Lua Script) |
| `community-service` | 3008 | 뉴스/게시판/댓글 |
| `catalog-service` | 3009 | 이벤트 카탈로그/아티스트/관리자 (Spotify API, S3) |

## Shared Library

`urr-common/` — 6개 서비스(catalog, community, payment, queue, stats, ticket)가 Gradle Composite Build로 공유.

| 컴포넌트 | 역할 |
|----------|------|
| `JwtTokenParser` | X-User-* 헤더에서 사용자 컨텍스트 추출 |
| `GlobalExceptionHandler` | 공통 예외 처리 (@RestControllerAdvice) |
| `InternalTokenValidatorAutoConfiguration` | 서비스 간 내부 토큰 검증 (@ConditionalOnProperty) |
| `DataSourceRoutingConfig` | Primary/Replica 읽기/쓰기 분리 라우팅 |
| `AuthUser` | userId, email, role 값 객체 (record) |
| `PreSaleSchedule` | 멤버십 티어별 선예매 일정 계산 |

auth-service와 gateway-service는 urr-common을 사용하지 않음 (자체 인증/게이트웨이 로직).

## Local DB

`docker-compose.databases.yml`로 서비스별 분리된 DB 실행:

| DB | 포트 | 데이터베이스 |
|----|------|-------------|
| auth-db | 5433 | auth_db |
| ticket-db | 5434 | ticket_db |
| payment-db | 5435 | payment_db |
| stats-db | 5436 | stats_db |
| community-db | 5437 | community_db |

catalog-service는 `localhost:5432/catalog_db` (기본 PostgreSQL 포트) 사용.

## Run

### Kind (권장)

```powershell
.\scripts\spring-kind-dev.ps1
```

상세 가이드: [`KIND_QUICK_START.md`](KIND_QUICK_START.md)

### 로컬 개발

```powershell
# 1. DB/Redis/Kafka 실행
docker compose -f services-spring/docker-compose.databases.yml up -d

# 2. 전체 서비스 실행
.\scripts\start-all.ps1 -Build -WithFrontend
```

## Tech Stack

- Spring Boot 3.5.0, Java 21
- Spring Cloud Gateway (WebMVC)
- Spring Data JPA / JdbcTemplate
- Spring Kafka (payment-events, reservation-events, transfer-events, membership-events)
- Redis (대기열 ZSET, Rate Limiting, 좌석 분산 잠금)
- PostgreSQL + Flyway 마이그레이션
- Resilience4j (CircuitBreaker, Retry)
- Micrometer + Prometheus + Zipkin (분산 추적)
