# 02. 백엔드 아키텍처 분석

> URR 티켓팅 플랫폼 백엔드의 마이크로서비스 아키텍처, 서비스 간 통신 패턴, 데이터베이스 구조 및 복원력 전략에 대한 기술 분석 문서이다.

---

## 1. 기술 스택

### 1.1 핵심 프레임워크

| 기술 | 버전 | 용도 | 참조 |
|------|------|------|------|
| Spring Boot | 3.5.0 | 애플리케이션 프레임워크 | `services-spring/gateway-service/build.gradle:3` |
| Java | 21 | 런타임 (toolchain) | `services-spring/gateway-service/build.gradle:13` |
| Gradle | - | 빌드 도구 (dependency-management 1.1.7) | `services-spring/gateway-service/build.gradle:4` |
| Spring Cloud | 2025.0.1 | Gateway MVC (게이트웨이 전용) | `services-spring/gateway-service/build.gradle:22` |

모든 9개 서비스가 동일한 Spring Boot 3.5.0 / Java 21 조합을 사용한다. 이는 각 서비스의 `build.gradle` 파일에서 확인할 수 있다.

- `services-spring/auth-service/build.gradle:3` (Spring Boot 3.5.0)
- `services-spring/ticket-service/build.gradle:3` (Spring Boot 3.5.0)
- `services-spring/payment-service/build.gradle:3` (Spring Boot 3.5.0)
- `services-spring/stats-service/build.gradle:3` (Spring Boot 3.5.0)
- `services-spring/queue-service/build.gradle:3` (Spring Boot 3.5.0)
- `services-spring/community-service/build.gradle:3` (Spring Boot 3.5.0)
- `services-spring/catalog-service/build.gradle:3` (Spring Boot 3.5.0)

### 1.2 데이터 액세스

| 라이브러리 | 사용 서비스 | 참조 |
|------------|------------|------|
| Spring Data JPA | auth, ticket, payment, stats | `services-spring/ticket-service/build.gradle:23` |
| Spring Data Redis | gateway, ticket, queue | `services-spring/ticket-service/build.gradle:24` |
| Spring JDBC | catalog, community | `services-spring/catalog-service/build.gradle:23` |
| Flyway (PostgreSQL) | auth, ticket, payment, stats, community | `services-spring/ticket-service/build.gradle:29-30` |
| PostgreSQL Driver | auth, ticket, payment, stats, community, catalog | `services-spring/ticket-service/build.gradle:42` |

catalog-service와 community-service는 JPA 대신 Spring JDBC(`spring-boot-starter-jdbc`)를 사용하여 직접 SQL 쿼리를 실행하는 경량 데이터 접근 방식을 채택하고 있다.

- `services-spring/catalog-service/build.gradle:23` (`spring-boot-starter-jdbc`)
- `services-spring/community-service/build.gradle:23` (`spring-boot-starter-jdbc`)

### 1.3 메시징

| 라이브러리 | 사용 서비스 | 참조 |
|------------|------------|------|
| Spring Kafka | ticket, payment, stats | `services-spring/ticket-service/build.gradle:35` |
| AWS SQS SDK 2.29.0 | queue | `services-spring/queue-service/build.gradle:28` |
| AWS STS SDK 2.29.0 | queue | `services-spring/queue-service/build.gradle:29` |

### 1.4 복원력 및 관측성

| 라이브러리 | 버전 | 사용 서비스 | 참조 |
|------------|------|------------|------|
| Resilience4j | 2.2.0 | ticket, payment, catalog, community, queue | `services-spring/ticket-service/build.gradle:28` |
| Micrometer Prometheus | - | 전체 | `services-spring/gateway-service/build.gradle:28` |
| Micrometer Brave (Zipkin) | - | 전체 | `services-spring/gateway-service/build.gradle:29-30` |

### 1.5 인증 및 보안

| 라이브러리 | 버전 | 사용 서비스 | 참조 |
|------------|------|------------|------|
| JJWT API | 0.12.6 | 전체 | `services-spring/gateway-service/build.gradle:32` |
| JJWT Impl (runtime) | 0.12.6 | 전체 | `services-spring/gateway-service/build.gradle:33` |
| Spring Security | - | auth 전용 | `services-spring/auth-service/build.gradle:24` |
| Google API Client | 2.7.2 | auth 전용 (OAuth) | `services-spring/auth-service/build.gradle:29` |

auth-service만 Spring Security를 사용하며, 나머지 서비스는 게이트웨이에서 주입하는 `X-User-*` 헤더에 의존한다.

### 1.6 유틸리티 및 외부 서비스

| 라이브러리 | 버전 | 사용 서비스 | 참조 |
|------------|------|------------|------|
| Lombok | 1.18.34 | auth, ticket | `services-spring/auth-service/build.gradle:34` |
| AWS S3 SDK | 2.31.68 | ticket, catalog | `services-spring/ticket-service/build.gradle:36` |
| Spotify Web API | 8.4.1 | ticket, catalog | `services-spring/ticket-service/build.gradle:37` |

---

## 2. MSA 서비스 구성

### 2.1 서비스 구성 종합표

| 서비스 | 포트 | DB | Redis | Kafka 역할 | AWS | 핵심 목적 |
|--------|------|----|----- |------------|-----|-----------|
| gateway-service | 3001 | -- | O (Rate Limit) | -- | -- | API 라우팅, JWT 검증, Rate Limiting |
| auth-service | 3005 | auth_db (5438) | -- | -- | -- | JWT 발급, 사용자 관리, Google OAuth |
| ticket-service | 3002 | ticket_db (5434) | O (좌석 잠금) | Producer + Consumer | S3 | 예매, 좌석, 양도, 멤버십 |
| catalog-service | 3009 | ticket_db (5434) | -- | -- | S3 | 이벤트, 아티스트, 관리자 CRUD |
| payment-service | 3003 | payment_db (5435) | -- | Producer | -- | Toss 결제 처리, 환불 |
| queue-service | 3007 | -- | O (대기열) | -- | SQS | VWR 대기열 관리 |
| community-service | 3008 | community_db (5437) | -- | -- | -- | 커뮤니티 게시판, 뉴스 |
| stats-service | 3004 | stats_db (5436) | -- | Consumer | -- | 분석/통계 집계 |

참조:
- 포트: `services-spring/gateway-service/src/main/resources/application.yml:78`, `services-spring/auth-service/src/main/resources/application.yml:20`, `services-spring/ticket-service/src/main/resources/application.yml:38`, `services-spring/catalog-service/src/main/resources/application.yml:12`, `services-spring/payment-service/src/main/resources/application.yml:26`, `services-spring/queue-service/src/main/resources/application.yml:10`, `services-spring/community-service/src/main/resources/application.yml:13`, `services-spring/stats-service/src/main/resources/application.yml:28`
- DB URL: `services-spring/auth-service/src/main/resources/application.yml:5`, `services-spring/ticket-service/src/main/resources/application.yml:22`, `services-spring/catalog-service/src/main/resources/application.yml:5`, `services-spring/payment-service/src/main/resources/application.yml:14`, `services-spring/community-service/src/main/resources/application.yml:5`, `services-spring/stats-service/src/main/resources/application.yml:16`

### 2.2 서비스별 상세 설명

**gateway-service (3001)**: Spring Cloud Gateway MVC 기반의 API 게이트웨이이다. 모든 외부 트래픽의 단일 진입점으로, JWT 검증 후 사용자 정보를 헤더로 주입하고, Redis Lua 스크립트 기반 슬라이딩 윈도우 Rate Limiting을 수행한다. VWR 입장 토큰 검증도 처리한다.
- 참조: `services-spring/gateway-service/build.gradle:27`

**auth-service (3005)**: Spring Security + JJWT 기반 인증 서비스이다. 이메일/비밀번호 및 Google OAuth2 로그인을 지원하며, JWT Access Token과 Refresh Token을 발급한다.
- 참조: `services-spring/auth-service/build.gradle:24,29`, `services-spring/auth-service/src/main/resources/application.yml:48-52`

**ticket-service (3002)**: 시스템의 핵심 도메인 서비스이다. 좌석 예매, 티켓 양도, 아티스트 멤버십, 예약 관리를 담당하며, Redis 분산 잠금으로 좌석 동시성을 제어하고, Kafka를 통해 예매/양도/멤버십 이벤트를 발행/소비한다.
- 참조: `services-spring/ticket-service/build.gradle:22-37`

**catalog-service (3009)**: 이벤트, 아티스트, 좌석 레이아웃의 조회 및 관리자 CRUD를 담당한다. ticket_db를 공유하되 Flyway는 비활성화(`flyway.enabled: false`)하여 스키마 관리를 ticket-service에 위임한다.
- 참조: `services-spring/catalog-service/src/main/resources/application.yml:5,9`

**payment-service (3003)**: Toss Payments API 연동 결제 처리 서비스이다. 결제 확인/환불 후 Kafka `payment-events` 토픽으로 이벤트를 발행한다.
- 참조: `services-spring/payment-service/src/main/resources/application.yml:1-23`

**queue-service (3007)**: Redis ZSET 기반의 가상 대기실(VWR) 서비스이다. DB 없이 Redis만으로 대기열, 활성 사용자, 입장 토큰을 관리한다. AWS SQS FIFO를 통한 입장 이벤트 발행도 지원한다.
- 참조: `services-spring/queue-service/build.gradle:22-29`, `services-spring/queue-service/src/main/resources/application.yml:1-8`

**community-service (3008)**: 아티스트별 커뮤니티 게시판과 뉴스 관리 서비스이다. 게시글 작성 시 ticket-service의 내부 API를 호출하여 멤버십 포인트를 적립한다.
- 참조: `services-spring/community-service/src/main/resources/application.yml:1-11`

**stats-service (3004)**: Kafka 이벤트를 소비하여 일별 통계와 이벤트별 통계를 집계하는 분석 서비스이다. payment-events, reservation-events, membership-events 세 개 토픽을 구독한다.
- 참조: `services-spring/stats-service/src/main/resources/application.yml:1-14`

---

## 3. API 게이트웨이

### 3.1 라우트 설정

게이트웨이는 Spring Cloud Gateway MVC의 선언적 라우트 설정을 사용한다. 모든 라우트는 `/api/v1/` 및 `/api/` 이중 경로 패턴을 지원하며, `ApiVersionFilter`가 런타임에 `/api/v1/` 프리픽스를 제거한다.

| 라우트 ID | 경로 패턴 | 대상 서비스 (기본 URL) | 참조 |
|-----------|----------|----------------------|------|
| auth | `/api/v1/auth/**`, `/api/auth/**` | auth-service (3005) | `services-spring/gateway-service/src/main/resources/application.yml:12-15` |
| payment | `/api/v1/payments/**`, `/api/payments/**` | payment-service (3003) | `services-spring/gateway-service/src/main/resources/application.yml:16-19` |
| stats | `/api/v1/stats/**`, `/api/stats/**` | stats-service (3004) | `services-spring/gateway-service/src/main/resources/application.yml:20-23` |
| events | `/api/v1/events/**`, `/api/events/**` | catalog-service (3009) | `services-spring/gateway-service/src/main/resources/application.yml:24-27` |
| tickets | `/api/v1/tickets/**`, `/api/tickets/**` | ticket-service (3002) | `services-spring/gateway-service/src/main/resources/application.yml:28-31` |
| seats | `/api/v1/seats/**`, `/api/seats/**` | ticket-service (3002) | `services-spring/gateway-service/src/main/resources/application.yml:32-35` |
| reservations | `/api/v1/reservations/**`, `/api/reservations/**` | ticket-service (3002) | `services-spring/gateway-service/src/main/resources/application.yml:36-39` |
| queue | `/api/v1/queue/**`, `/api/queue/**` | queue-service (3007) | `services-spring/gateway-service/src/main/resources/application.yml:40-43` |
| admin | `/api/v1/admin/**`, `/api/admin/**` | catalog-service (3009) | `services-spring/gateway-service/src/main/resources/application.yml:44-47` |
| community | `/api/v1/community/**`, `/api/community/**` | community-service (3008) | `services-spring/gateway-service/src/main/resources/application.yml:48-51` |
| news | `/api/v1/news/**`, `/api/news/**` | community-service (3008) | `services-spring/gateway-service/src/main/resources/application.yml:52-55` |
| artists | `/api/v1/artists/**`, `/api/artists/**` | catalog-service (3009) | `services-spring/gateway-service/src/main/resources/application.yml:56-59` |
| memberships | `/api/v1/memberships/**`, `/api/memberships/**` | ticket-service (3002) | `services-spring/gateway-service/src/main/resources/application.yml:60-63` |
| transfers | `/api/v1/transfers/**`, `/api/transfers/**` | ticket-service (3002) | `services-spring/gateway-service/src/main/resources/application.yml:64-67` |
| image | `/api/v1/image/**`, `/api/image/**` | catalog-service (3009) | `services-spring/gateway-service/src/main/resources/application.yml:68-71` |
| time | `/api/v1/time/**`, `/api/time/**` | ticket-service (3002) | `services-spring/gateway-service/src/main/resources/application.yml:72-75` |

ticket-service가 가장 많은 라우트(tickets, seats, reservations, memberships, transfers, time)를 처리하며, catalog-service가 그 다음(events, admin, artists, image)으로 많은 라우트를 담당한다.

### 3.2 필터 체인

게이트웨이의 요청 처리 파이프라인은 5개의 `OncePerRequestFilter` 구현체로 구성되며, `@Order` 어노테이션에 의해 실행 순서가 결정된다. 숫자가 작을수록 먼저 실행된다.

#### 3.2.1 ApiVersionFilter (`@Order(-10)`)

**파일**: `services-spring/gateway-service/src/main/java/guru/urr/gatewayservice/filter/ApiVersionFilter.java`

가장 먼저 실행되는 필터로, API 버전 경로 세그먼트를 제거하여 하위 서비스가 버전 없는 경로를 처리할 수 있게 한다.

- **변환 규칙**: `/api/v1/events/123` -> `/api/events/123` (라인 22-23, 31-32)
- **구현 방식**: `HttpServletRequestWrapper`를 확장한 `RewrittenPathRequest`로 `getRequestURI()`, `getServletPath()`, `getRequestURL()`을 오버라이드한다 (라인 39-68)
- `/api/v1/`로 시작하지 않는 요청은 변환 없이 통과한다 (라인 34-35)

#### 3.2.2 CookieAuthFilter (`@Order(-2)`)

**파일**: `services-spring/gateway-service/src/main/java/guru/urr/gatewayservice/filter/CookieAuthFilter.java`

HTTP-only 쿠키에 저장된 Access Token을 `Authorization` 헤더로 변환한다. 브라우저 기반 클라이언트의 CSRF 방어와 토큰 관리를 투명하게 처리한다.

- **쿠키 이름**: `access_token` (라인 21)
- **동작**: `Authorization` 헤더가 이미 존재하면 스킵한다 (라인 28-31)
- 쿠키에서 토큰을 추출하여 `Bearer {token}` 형태의 Authorization 헤더를 주입한다 (라인 34-38)
- `AuthHeaderRequestWrapper` 내부 클래스로 `getHeader()`, `getHeaders()`, `getHeaderNames()`를 오버라이드한다 (라인 55-87)

#### 3.2.3 JwtAuthFilter (`@Order(-1)`)

**파일**: `services-spring/gateway-service/src/main/java/guru/urr/gatewayservice/filter/JwtAuthFilter.java`

게이트웨이 수준에서 JWT를 검증하고, 인증된 사용자 정보를 하위 서비스로 전달하는 핵심 보안 필터이다. 이 필터 덕분에 하위 서비스는 JWT_SECRET을 알 필요가 없다 (라인 27-31 주석).

**보안 처리 절차**:

1. **외부 헤더 스트리핑**: 모든 요청에서 외부에서 주입된 `X-User-Id`, `X-User-Email`, `X-User-Role` 헤더를 제거하여 스푸핑을 방지한다 (라인 53-54, 104-137)
2. **JWT 파싱**: `Authorization: Bearer {token}`에서 토큰을 추출하고, JJWT 라이브러리로 서명을 검증한다 (라인 57-64)
3. **클레임 추출**: `userId`, `email`, `role` 클레임을 추출한다 (라인 66-68)
4. **헤더 주입**: 유효한 JWT인 경우 `X-User-Id`, `X-User-Email`, `X-User-Role` 헤더를 주입한다 (라인 71-77)
5. **실패 처리**: JWT가 없거나 유효하지 않으면 X-User-* 헤더 없이 요청을 통과시킨다 (라인 84-85). 인증 실패 시 403을 반환하지 않고, 하위 서비스의 인증 요구사항에 위임한다.

**키 빌드**: Base64 디코딩을 먼저 시도하고, 실패 시 UTF-8 바이트를 사용한다. 최소 32바이트 미만이면 null을 반환하여 JWT 검증을 비활성화한다 (라인 88-98).

주입 헤더 상수:
- `X-User-Id` (라인 39)
- `X-User-Email` (라인 40)
- `X-User-Role` (라인 41)

#### 3.2.4 RateLimitFilter (`@Order(0)`)

**파일**: `services-spring/gateway-service/src/main/java/guru/urr/gatewayservice/filter/RateLimitFilter.java`

Redis Lua 스크립트 기반의 슬라이딩 윈도우 Rate Limiting 필터이다.

**카테고리별 제한** (라인 130-137):

| 카테고리 | 경로 패턴 | 기본 RPM | 참조 |
|----------|----------|----------|------|
| AUTH | `/api/auth/**` | 60 | `services-spring/gateway-service/src/main/resources/application.yml:116` |
| QUEUE | `/api/queue/**` | 120 | `services-spring/gateway-service/src/main/resources/application.yml:117` |
| BOOKING | `/api/seats/reserve`, `/api/reservations` | 30 | `services-spring/gateway-service/src/main/resources/application.yml:118` |
| GENERAL | 나머지 전체 | 3000 | `services-spring/gateway-service/src/main/resources/application.yml:119` |

**클라이언트 식별** (라인 103-111):
- 인증된 사용자: `JwtAuthFilter`가 주입한 `X-User-Id` 헤더 사용 -> `user:{userId}`
- 미인증 사용자: IP 주소 -> `ip:{remoteAddr}`

**Redis Lua 스크립트** (`services-spring/gateway-service/src/main/resources/redis/rate_limit.lua`):
```
1. ZREMRANGEBYSCORE로 윈도우 밖의 오래된 항목을 제거 (라인 7)
2. ZADD로 현재 요청을 타임스탬프 점수와 함께 추가 (라인 8)
3. ZCARD로 현재 윈도우 내 요청 수를 확인 (라인 9)
4. EXPIRE 설정 (윈도우 ms / 1000 + 1초) (라인 10)
5. 제한 초과 시 ZREM으로 방금 추가한 항목 제거 후 0 반환 (라인 11-13)
6. 허용 시 1 반환 (라인 15)
```

- Redis 키 패턴: `rate:{category}:{clientId}` (라인 74)
- 윈도우 크기: 60,000ms (1분) (라인 26)
- Fail-open 정책: Redis 연결 실패 시 요청을 허용한다 (라인 94-98)
- 제외 경로: `/api/v1/auth/me`, `/api/auth/me`, `/health`, `/actuator` (라인 52-55)

**응답**: 429 상태 코드와 `retryAfter: 60` JSON 응답 (라인 139-146)

**RedisConfig**: Lua 스크립트를 `DefaultRedisScript<Long>` 빈으로 등록한다.
- 참조: `services-spring/gateway-service/src/main/java/guru/urr/gatewayservice/config/RedisConfig.java:19-25`

#### 3.2.5 VwrEntryTokenFilter (`@Order(1)`)

**파일**: `services-spring/gateway-service/src/main/java/guru/urr/gatewayservice/filter/VwrEntryTokenFilter.java`

VWR(가상 대기실)을 통과한 사용자만 좌석 예약/예매 API에 접근할 수 있도록 입장 토큰을 검증하는 필터이다.

**보호 대상** (라인 33, 113-115):
- HTTP 메소드: POST, PUT, PATCH만 (GET은 보호하지 않음)
- 경로: `/api/seats/**`, `/api/reservations/**`

**검증 절차**:
1. **CloudFront 바이패스**: `X-CloudFront-Verified` 헤더가 설정된 CloudFront Secret과 일치하면 CDN 엣지에서 이미 검증된 것으로 간주하고 토큰 검증을 건너뛴다 (라인 62-69). `MessageDigest.isEqual()`로 타이밍 안전 비교를 수행한다 (라인 64).
2. **토큰 추출**: `x-queue-entry-token` 헤더에서 JWT를 추출한다 (라인 30, 72)
3. **JWT 검증**: HMAC-SHA256 서명 검증 (최소 키 길이 32바이트) (라인 80-85, 130-138)
4. **userId 바인딩 검증**: VWR 토큰의 `uid` 클레임과 `JwtAuthFilter`가 주입한 `X-User-Id`가 일치하는지 확인한다. 불일치 시 403을 반환하여 토큰 공유/도용을 방지한다 (라인 88-96)
5. **eventId 전달**: VWR 토큰의 subject(eventId)를 request attribute `vwr.eventId`로 전달한다 (라인 100-103)

**실패 응답**: HTTP 403과 `{"error":"Queue entry token required","redirectTo":"/queue"}` (라인 117-121)

---

## 4. MSA 간 통신

### 4.1 REST 내부 클라이언트

서비스 간 동기 통신은 Spring `RestClient`를 사용하며, Resilience4j의 `@CircuitBreaker`와 `@Retry` 어노테이션으로 보호된다.

#### 공통 설정

모든 내부 클라이언트는 동일한 HTTP 타임아웃과 인증 패턴을 따른다:

| 설정 | 값 | 참조 (대표) |
|------|------|------|
| Connect Timeout | 5초 | `services-spring/payment-service/src/main/java/guru/urr/paymentservice/client/TicketInternalClient.java:31` |
| Read Timeout | 10초 | `services-spring/payment-service/src/main/java/guru/urr/paymentservice/client/TicketInternalClient.java:32` |
| 인증 방식 | `Authorization: Bearer {INTERNAL_API_TOKEN}` | `services-spring/payment-service/src/main/java/guru/urr/paymentservice/client/TicketInternalClient.java:42` |

#### 서비스간 내부 클라이언트 목록

**payment-service -> ticket-service**: `TicketInternalClient`
- 참조: `services-spring/payment-service/src/main/java/guru/urr/paymentservice/client/TicketInternalClient.java`

| 메소드 | HTTP | 엔드포인트 | CircuitBreaker | Retry | 라인 |
|--------|------|-----------|----------------|-------|------|
| `validateReservation(reservationId, userId)` | GET | `/internal/reservations/{id}/validate?userId=` | O | O | 39-44 |
| `validateTransfer(transferId, userId)` | GET | `/internal/transfers/{id}/validate?userId=` | O | O | 49-54 |
| `validateMembership(membershipId, userId)` | GET | `/internal/memberships/{id}/validate?userId=` | O | O | 59-64 |
| `confirmReservation(reservationId, paymentMethod)` | POST | `/internal/reservations/{id}/confirm` | O | O | 69-76 |
| `confirmTransfer(transferId, buyerId, paymentMethod)` | POST | `/internal/transfers/{id}/complete` | -- | -- | 78-85 |
| `activateMembership(membershipId)` | POST | `/internal/memberships/{id}/activate` | -- | -- | 87-93 |

`confirmReservation`의 fallback은 예외를 던지지 않고 로그만 남긴다(라인 96-99). Kafka 이벤트가 결과적 일관성(eventual consistency)을 보장하기 때문이다.

**ticket-service -> payment-service**: `PaymentInternalClient`
- 참조: `services-spring/ticket-service/src/main/java/guru/urr/ticketservice/shared/client/PaymentInternalClient.java`

| 메소드 | HTTP | 엔드포인트 | CircuitBreaker | Retry | 라인 |
|--------|------|-----------|----------------|-------|------|
| `getPaymentByReservation(reservationId)` | GET | `/internal/payments/by-reservation/{reservationId}` | O | O | 38-43 |

fallback은 null을 반환한다 (라인 47-49).

**queue-service -> catalog-service**: `TicketInternalClient`
- 참조: `services-spring/queue-service/src/main/java/guru/urr/queueservice/shared/client/TicketInternalClient.java`

| 메소드 | HTTP | 엔드포인트 | CircuitBreaker | Retry | 라인 |
|--------|------|-----------|----------------|-------|------|
| `getEventQueueInfo(eventId)` | GET | `/internal/events/{eventId}/queue-info` | O | O | 41-47 |

클라이언트 이름은 `TicketInternalClient`이지만 실제로는 catalog-service(3009)에 연결된다 (라인 25).
fallback은 `Map.of("title", "Unknown")`을 반환한다 (라인 51-53).

**community-service -> ticket-service**: `TicketInternalClient`
- 참조: `services-spring/community-service/src/main/java/guru/urr/communityservice/shared/client/TicketInternalClient.java`

| 메소드 | HTTP | 엔드포인트 | CircuitBreaker | Retry | 라인 |
|--------|------|-----------|----------------|-------|------|
| `awardMembershipPoints(userId, actionType, points, description, referenceId)` | POST | `/internal/memberships/award-points` | O | -- | 34-37 |
| `awardMembershipPoints(userId, artistId, actionType, points, description, referenceId)` | POST | `/internal/memberships/award-points` | O | -- | 42-58 |

POST가 멱등하지 않으므로 `@Retry`를 의도적으로 사용하지 않는다 (라인 41 주석). 중복 포인트 적립을 방지하기 위한 설계 결정이다.

**catalog-service -> auth-service**: `AuthInternalClient`
- 참조: `services-spring/catalog-service/src/main/java/guru/urr/catalogservice/shared/client/AuthInternalClient.java`

| 메소드 | HTTP | 엔드포인트 | CircuitBreaker | Retry | 라인 |
|--------|------|-----------|----------------|-------|------|
| `findUsersByIds(ids)` | POST | `/internal/users/batch` | O | O | 42-67 |

사용자 ID 목록을 배치로 조회하여 관리자 대시보드에서 사용자 정보를 표시한다.
fallback은 빈 Map을 반환한다 (라인 70-72).

**catalog-service -> ticket-service**: `TicketInternalClient`
- 참조: `services-spring/catalog-service/src/main/java/guru/urr/catalogservice/shared/client/TicketInternalClient.java`

| 메소드 | HTTP | 엔드포인트 | CircuitBreaker | Retry | 라인 |
|--------|------|-----------|----------------|-------|------|
| `generateSeats(eventId, layoutId)` | POST | `/internal/seats/generate/{eventId}/{layoutId}` | O | -- | 40-51 |
| `countSeats(eventId)` | GET | `/internal/seats/count/{eventId}` | O | O | 55-66 |
| `deleteSeats(eventId)` | DELETE | `/internal/seats/{eventId}` | O | O | 70-81 |
| `getTicketTypesByEvent(eventId)` | GET | `/internal/ticket-types?eventId=` | O | O | 108-118 |
| `getTicketTypeAvailability(ticketTypeId)` | GET | `/internal/ticket-types/{id}/availability` | -- | O | 121-127 |
| `createTicketType(...)` | POST | `/internal/ticket-types` | -- | -- | 129-139 |
| `updateTicketType(...)` | PUT | `/internal/ticket-types/{id}` | -- | -- | 141-151 |
| `getReservationStats()` | GET | `/internal/admin/reservation-stats` | O | O | 157-163 |
| `getRecentReservations()` | GET | `/internal/admin/recent-reservations` | -- | O | 167-177 |
| `listReservations(page, limit, status)` | GET | `/internal/admin/reservations` | -- | O | 180-189 |
| `updateReservationStatus(id, status, paymentStatus)` | PATCH | `/internal/admin/reservations/{id}/status` | -- | -- | 191-201 |
| `cancelReservationsByEvent(eventId)` | POST | `/internal/admin/reservations/cancel-by-event/{eventId}` | -- | -- | 203-211 |
| `cancelAllReservationsByEvent(eventId)` | POST | `/internal/admin/reservations/cancel-all-by-event/{eventId}` | -- | -- | 213-221 |
| `getSeatLayouts()` | GET | `/internal/admin/seat-layouts` | -- | O | 224-230 |
| `getActiveSeatReservationCount(eventId)` | GET | `/internal/admin/active-seat-reservation-count?eventId=` | -- | O | 233-241 |

`generateSeats`는 POST 비멱등 연산이므로 `@Retry`를 사용하지 않는다 (라인 39 주석).

### 4.2 Kafka 이벤트 스트리밍

#### 토픽 구성

Kafka 토픽은 ticket-service의 `KafkaConfig`에서 선언적으로 생성된다. 모든 토픽은 3개 파티션으로 구성된다.

| 토픽 | 파티션 | Replication Factor | 참조 |
|------|--------|-------------------|------|
| `payment-events` | 3 | 설정값 (기본 1) | `services-spring/ticket-service/src/main/java/guru/urr/ticketservice/shared/config/KafkaConfig.java:17` |
| `reservation-events` | 3 | 설정값 (기본 1) | `services-spring/ticket-service/src/main/java/guru/urr/ticketservice/shared/config/KafkaConfig.java:22` |
| `transfer-events` | 3 | 설정값 (기본 1) | `services-spring/ticket-service/src/main/java/guru/urr/ticketservice/shared/config/KafkaConfig.java:27` |
| `membership-events` | 3 | 설정값 (기본 1) | `services-spring/ticket-service/src/main/java/guru/urr/ticketservice/shared/config/KafkaConfig.java:32` |

Replication Factor는 `kafka.topic.replication-factor` 프로퍼티로 제어되며, 기본값은 1이다.
- 참조: `services-spring/ticket-service/src/main/resources/application.yml:79-80`

#### 이벤트 레코드 정의

**payment-events 토픽의 이벤트**:

`PaymentConfirmedEvent` (Java record):
- 참조: `services-spring/payment-service/src/main/java/guru/urr/paymentservice/messaging/event/PaymentConfirmedEvent.java:6-24`
- 필드: `type("PAYMENT_CONFIRMED")`, `paymentId`, `orderId`, `userId`, `reservationId`, `referenceId`, `paymentType`, `amount`, `paymentMethod`, `timestamp`

`PaymentRefundedEvent` (Java record):
- 참조: `services-spring/payment-service/src/main/java/guru/urr/paymentservice/messaging/event/PaymentRefundedEvent.java:6-24`
- 필드: `type("PAYMENT_REFUNDED")`, `paymentId`, `orderId`, `userId`, `reservationId`, `referenceId`, `paymentType`, `amount`, `reason`, `timestamp`

**reservation-events 토픽의 이벤트**:

`ReservationCreatedEvent`:
- 참조: `services-spring/ticket-service/src/main/java/guru/urr/ticketservice/messaging/event/ReservationCreatedEvent.java:6-19`
- 필드: `type("RESERVATION_CREATED")`, `sagaId`, `reservationId`, `userId`, `eventId`, `totalAmount`, `timestamp`

`ReservationConfirmedEvent`:
- 참조: `services-spring/ticket-service/src/main/java/guru/urr/ticketservice/messaging/event/ReservationConfirmedEvent.java:6-22`
- 필드: `type("RESERVATION_CONFIRMED")`, `sagaId`, `reservationId`, `userId`, `eventId`, `totalAmount`, `paymentMethod`, `timestamp`

`ReservationCancelledEvent`:
- 참조: `services-spring/ticket-service/src/main/java/guru/urr/ticketservice/messaging/event/ReservationCancelledEvent.java` (동일 패턴)

**transfer-events 토픽의 이벤트**:

`TransferCompletedEvent`:
- 참조: `services-spring/ticket-service/src/main/java/guru/urr/ticketservice/messaging/event/TransferCompletedEvent.java:6-21`
- 필드: `type("TRANSFER_COMPLETED")`, `sagaId`, `transferId`, `reservationId`, `sellerId`, `buyerId`, `totalPrice`, `timestamp`

**membership-events 토픽의 이벤트**:

`MembershipActivatedEvent`:
- 참조: `services-spring/ticket-service/src/main/java/guru/urr/ticketservice/messaging/event/MembershipActivatedEvent.java:6-18`
- 필드: `type("MEMBERSHIP_ACTIVATED")`, `sagaId`, `membershipId`, `userId`, `artistId`, `timestamp`

#### Producer (이벤트 발행자)

**PaymentEventProducer** (payment-service):
- 참조: `services-spring/payment-service/src/main/java/guru/urr/paymentservice/messaging/PaymentEventProducer.java`
- 토픽: `payment-events` (라인 14)
- 메소드:
  - `publish(PaymentConfirmedEvent)`: Kafka 키로 `orderId`를 사용한다 (라인 22-30)
  - `publishRefund(PaymentRefundedEvent)`: Kafka 키로 `orderId`를 사용한다 (라인 33-42)
- 비동기 콜백(`whenComplete`)으로 발행 성공/실패를 로깅한다

Producer 설정:
- `acks: all` - 모든 ISR 복제본의 확인을 기다린다 (라인 9)
- `retries: 3` (payment-service 전용)
- `spring.json.add.type.headers: false` - 타입 헤더를 추가하지 않는다
- 참조: `services-spring/payment-service/src/main/resources/application.yml:5-12`

**TicketEventProducer** (ticket-service):
- 참조: `services-spring/ticket-service/src/main/java/guru/urr/ticketservice/messaging/TicketEventProducer.java`
- 메소드:
  - `publishReservationCreated(ReservationCreatedEvent)` -> `reservation-events` 토픽 (라인 24-33)
  - `publishReservationConfirmed(ReservationConfirmedEvent)` -> `reservation-events` 토픽 (라인 35-44)
  - `publishReservationCancelled(ReservationCancelledEvent)` -> `reservation-events` 토픽 (라인 46-55)
  - `publishTransferCompleted(TransferCompletedEvent)` -> `transfer-events` 토픽 (라인 57-66)
  - `publishMembershipActivated(MembershipActivatedEvent)` -> `membership-events` 토픽 (라인 68-77)
- 모든 메소드에서 해당 엔티티의 ID를 Kafka 메시지 키로 사용하여 동일 엔티티의 이벤트가 동일 파티션에 저장되도록 보장한다

Producer 설정 (ticket-service):
- `acks: all`
- `spring.json.add.type.headers: false`
- 참조: `services-spring/ticket-service/src/main/resources/application.yml:6-11`

#### Consumer (이벤트 소비자)

**PaymentEventConsumer** (ticket-service):
- 참조: `services-spring/ticket-service/src/main/java/guru/urr/ticketservice/messaging/PaymentEventConsumer.java`
- 토픽: `payment-events`, Consumer Group: `ticket-service-group` (라인 49)
- `@KafkaListener` 어노테이션으로 메시지를 `Map<String, Object>`로 수신한다 (라인 50)

이벤트 타입별 처리 (라인 60-84):

| type 필드 | paymentType | 처리 메소드 | 동작 |
|-----------|------------|------------|------|
| `PAYMENT_CONFIRMED` | `reservation` (기본) | `handleReservationPayment` | 예약 결제 확인, ReservationConfirmedEvent 발행 |
| `PAYMENT_CONFIRMED` | `transfer` | `handleTransferPayment` | 양도 구매 완료, TransferCompletedEvent 발행 |
| `PAYMENT_CONFIRMED` | `membership` | `handleMembershipPayment` | 멤버십 활성화, MembershipActivatedEvent 발행 |
| `PAYMENT_REFUNDED` | - | `handleRefund` | 예약 환불 처리, ReservationCancelledEvent 발행 |

하위 호환성을 위해 `type` 필드가 없는 레거시 이벤트도 duck-typing 방식으로 처리한다 (라인 71-83). `reason` 필드 존재 여부로 환불 이벤트를 구분한다.

Consumer 설정 (ticket-service):
- `group-id: ticket-service-group` (라인 13)
- `auto-offset-reset: earliest` (라인 14)
- `spring.json.trusted.packages`: payment-service와 ticket-service의 이벤트 패키지를 신뢰한다 (라인 18)
- `spring.json.use.type.headers: false` / `spring.json.value.default.type: java.util.LinkedHashMap` - 타입 헤더 대신 Map으로 역직렬화한다 (라인 19-20)
- 참조: `services-spring/ticket-service/src/main/resources/application.yml:12-20`

**StatsEventConsumer** (stats-service):
- 참조: `services-spring/stats-service/src/main/java/guru/urr/statsservice/messaging/StatsEventConsumer.java`
- Consumer Group: `stats-service-group`
- 3개 토픽 구독:

| 토픽 | 메소드 | Consumer Group | 라인 |
|------|--------|---------------|------|
| `payment-events` | `handlePaymentEvent` | `stats-service-group` | 25-68 |
| `reservation-events` | `handleReservationEvent` | `stats-service-group` | 70-114 |
| `membership-events` | `handleMembershipEvent` | `stats-service-group` | 116-134 |

`payment-events`와 `reservation-events`에 대해 서로 다른 Consumer Group(`ticket-service-group`, `stats-service-group`)이 독립적으로 소비하므로, 동일 메시지를 두 서비스가 각각 처리한다 (Kafka의 Consumer Group 기반 메시지 분배).

Consumer 설정 (stats-service):
- `group-id: stats-service-group` (라인 7)
- 동일한 역직렬화 설정 사용
- 참조: `services-spring/stats-service/src/main/resources/application.yml:6-14`

### 4.3 내부 API 인증

서비스 간 내부 API 호출은 사전 공유 토큰(`INTERNAL_API_TOKEN` 환경변수) 기반의 Bearer 인증으로 보호된다.

**InternalTokenValidator**:
- 참조: `services-spring/ticket-service/src/main/java/guru/urr/ticketservice/shared/security/InternalTokenValidator.java`
- 동일한 구현이 4개 서비스에 존재한다:
  - `services-spring/ticket-service/src/main/java/guru/urr/ticketservice/shared/security/InternalTokenValidator.java`
  - `services-spring/payment-service/src/main/java/guru/urr/paymentservice/security/InternalTokenValidator.java`
  - `services-spring/catalog-service/src/main/java/guru/urr/catalogservice/shared/security/InternalTokenValidator.java`
  - `services-spring/auth-service/src/main/java/guru/urr/authservice/security/InternalTokenValidator.java`

**검증 로직** (ticket-service의 구현):
1. `Authorization` 헤더가 없거나 `Bearer `로 시작하지 않으면 401 반환 (라인 20-22)
2. 토큰을 추출하여 `MessageDigest.isEqual()`로 타이밍 안전(timing-safe) 비교 수행 (라인 23-26, 29-32)
3. 불일치 시 403 반환 (라인 25-26)

**보호 대상 경로**: `/internal/**` 패턴 하의 모든 컨트롤러이다. 예를 들어 `InternalMembershipController`는 `/internal/memberships` 경로에 매핑되며, 모든 핸들러 메소드에서 `internalTokenValidator.requireValidToken(authorization)`을 호출한다.
- 참조: `services-spring/ticket-service/src/main/java/guru/urr/ticketservice/internal/controller/InternalMembershipController.java:12,30`

### 4.4 멱등성 처리

Kafka 메시지의 at-least-once 전달 보장 특성상 중복 메시지가 발생할 수 있으므로, 두 가지 수준의 멱등성 메커니즘이 구현되어 있다.

#### 4.4.1 Kafka 이벤트 멱등성 (processed_events 테이블)

**ticket-service의 processed_events**:
- 스키마: `services-spring/ticket-service/src/main/resources/db/migration/V14__processed_events.sql`
- 컬럼: `event_key` (VARCHAR(255) PK), `consumer_group` (VARCHAR(100) NOT NULL), `processed_at` (TIMESTAMPTZ) (라인 2-6)
- 인덱스: `idx_processed_events_consumer(consumer_group, processed_at)` (라인 8)

**PaymentEventConsumer의 중복 제거 로직**:
- 참조: `services-spring/ticket-service/src/main/java/guru/urr/ticketservice/messaging/PaymentEventConsumer.java`

1. **이벤트 키 생성** (라인 193-207): `sagaId`가 있으면 우선 사용하고, 없으면 `type:referenceId` 조합을 키로 사용한다
2. **중복 확인** (라인 209-219): `SELECT COUNT(*) FROM processed_events WHERE event_key = ? AND consumer_group = ?` 쿼리로 이미 처리된 이벤트인지 확인한다. consumer_group 컬럼으로 서비스별 독립적 멱등성을 보장한다
3. **처리 완료 기록** (라인 221-231): `INSERT INTO processed_events (event_key, consumer_group) VALUES (?, ?)` - `DuplicateKeyException`을 무시하여 동시 소비자 간 경쟁 조건을 안전하게 처리한다 (라인 226)

**stats-service의 processed_events**:
- 스키마: `services-spring/stats-service/src/main/resources/db/migration/V2__processed_events_table.sql`
- 컬럼: `event_key` (VARCHAR(255) PK), `processed_at` (TIMESTAMPTZ) (라인 2-5)
- ticket-service와 달리 `consumer_group` 컬럼이 없다 (stats-service 단일 서비스 전용)

**StatsEventConsumer의 중복 제거 로직**:
- 참조: `services-spring/stats-service/src/main/java/guru/urr/statsservice/messaging/StatsEventConsumer.java`
- 이벤트 키 생성: `type:id:timestamp` 조합 (라인 138-150)
- 중복 확인: `SELECT COUNT(*) FROM processed_events WHERE event_key = ?` (라인 152-160)
- 처리 완료 기록: `INSERT ... ON CONFLICT (event_key) DO NOTHING` (라인 163-170)

#### 4.4.2 예약 멱등성 (idempotency_key)

네트워크 재시도로 인한 중복 예약을 방지하기 위해 `reservations` 테이블에 `idempotency_key` 컬럼이 추가되어 있다.

- 스키마: `services-spring/ticket-service/src/main/resources/db/migration/V12__reservation_idempotency.sql`
- 컬럼: `idempotency_key` (VARCHAR(64)) (라인 2)
- 유니크 인덱스: `idx_reservations_idempotency_key` (WHERE idempotency_key IS NOT NULL 조건부 인덱스) (라인 4-5)

클라이언트가 동일한 `idempotency_key`로 예약을 재시도하면 유니크 제약 조건에 의해 중복 생성이 방지된다.

---

## 5. 데이터베이스 구조

### 5.1 서비스별 DB 분리

MSA 원칙에 따라 각 서비스는 독립된 PostgreSQL 16 데이터베이스를 사용한다. 유일한 예외는 catalog-service가 ticket-service와 ticket_db를 공유하는 것이다.

| 서비스 | 데이터베이스 | 호스트 포트 | Flyway | 참조 |
|--------|------------|-----------|--------|------|
| auth-service | auth_db | 5438 | 활성 (4 migrations) | `services-spring/docker-compose.databases.yml:2-9` |
| ticket-service | ticket_db | 5434 | 활성 (14 migrations) | `services-spring/docker-compose.databases.yml:11-18` |
| catalog-service | ticket_db (공유) | 5434 | **비활성** | `services-spring/catalog-service/src/main/resources/application.yml:9` |
| payment-service | payment_db | 5435 | 활성 (3 migrations) | `services-spring/docker-compose.databases.yml:20-27` |
| stats-service | stats_db | 5436 | 활성 (2 migrations) | `services-spring/docker-compose.databases.yml:29-36` |
| community-service | community_db | 5437 | 활성 (3 migrations) | `services-spring/docker-compose.databases.yml:38-45` |
| queue-service | -- | -- | -- | (Redis 전용) |
| gateway-service | -- | -- | -- | (Redis 전용) |

공통 인프라:
- Redis 7: 포트 6379 (`services-spring/docker-compose.databases.yml:47-50`)
- Zipkin 3: 포트 9411 (`services-spring/docker-compose.databases.yml:52-55`)
- Apache Kafka 3.7.0 (KRaft 모드): 포트 9092 (`services-spring/docker-compose.databases.yml:57-73`)

catalog-service는 ticket_db를 공유하지만 Flyway를 비활성화(`flyway.enabled: false`)하여 스키마 마이그레이션을 ticket-service에 위임한다.
- 참조: `services-spring/catalog-service/src/main/resources/application.yml:5,9`

### 5.2 Flyway 마이그레이션

#### ticket-service: 14개 마이그레이션 (V1 ~ V14)

| 버전 | 파일명 | 설명 | 주요 테이블/변경 | 참조 |
|------|--------|------|----------------|------|
| V1 | `V1__ticket_core_schema.sql` | 핵심 스키마 생성 | seat_layouts, events, ticket_types, seats, reservations, reservation_items, keyword_mappings, news | `services-spring/ticket-service/src/main/resources/db/migration/V1__ticket_core_schema.sql:1-103` |
| V2 | `V2__seed_data.sql` | 초기 데이터 시드 | 좌석 레이아웃, 이벤트 시드 | `services-spring/ticket-service/src/main/resources/db/migration/V2__seed_data.sql` |
| V3 | `V3__artists_table.sql` | 아티스트 테이블 생성 | artists, events.artist_id FK 추가 | `services-spring/ticket-service/src/main/resources/db/migration/V3__artists_table.sql:1-30` |
| V4 | `V4__membership_tables.sql` | 멤버십 시스템 | artist_memberships, membership_point_logs | `services-spring/ticket-service/src/main/resources/db/migration/V4__membership_tables.sql:1-31` |
| V5 | `V5__ticket_transfers.sql` | 티켓 양도 시스템 | ticket_transfers | `services-spring/ticket-service/src/main/resources/db/migration/V5__ticket_transfers.sql:1-20` |
| V6 | `V6__membership_pending_status.sql` | 멤버십 결제 추적 | artist_memberships.payment_reference_id 추가 | `services-spring/ticket-service/src/main/resources/db/migration/V6__membership_pending_status.sql:2` |
| V7 | `V7__transfer_nullable_artist.sql` | 양도 아티스트 선택적 | ticket_transfers.artist_id NOT NULL 제거 | `services-spring/ticket-service/src/main/resources/db/migration/V7__transfer_nullable_artist.sql:1-2` |
| V8 | `V8__seats_concurrency_columns.sql` | 동시성 제어 컬럼 | seats.version (낙관적 잠금), seats.fencing_token, seats.locked_by | `services-spring/ticket-service/src/main/resources/db/migration/V8__seats_concurrency_columns.sql:1-8` |
| V9 | `V9__standing_events.sql` | 스탠딩 공연 지원 | 스키마 변경 없음 (기존 nullable seat_layout_id 활용) | `services-spring/ticket-service/src/main/resources/db/migration/V9__standing_events.sql:1-10` |
| V10 | `V10__generate_seats_for_seeded_events.sql` | 시드 이벤트 좌석 생성 | PL/pgSQL로 layout_config JSON 파싱 후 좌석 일괄 생성 | `services-spring/ticket-service/src/main/resources/db/migration/V10__generate_seats_for_seeded_events.sql:1-46` |
| V11 | `V11__add_indexes.sql` | 성능 인덱스 추가 | reservations, reservation_items, seats 인덱스 | `services-spring/ticket-service/src/main/resources/db/migration/V11__add_indexes.sql:1-11` |
| V12 | `V12__reservation_idempotency.sql` | 예약 멱등성 | reservations.idempotency_key (조건부 유니크 인덱스) | `services-spring/ticket-service/src/main/resources/db/migration/V12__reservation_idempotency.sql:1-5` |
| V13 | `V13__admin_audit_logs.sql` | 관리자 감사 로그 | admin_audit_logs | `services-spring/ticket-service/src/main/resources/db/migration/V13__admin_audit_logs.sql:1-16` |
| V14 | `V14__processed_events.sql` | Kafka 멱등성 | processed_events (event_key + consumer_group) | `services-spring/ticket-service/src/main/resources/db/migration/V14__processed_events.sql:1-8` |

ticket-service의 주요 테이블 관계:

```
seat_layouts --< events >-- artists
                  |
                  +--< ticket_types
                  +--< seats
                  +--< reservations --< reservation_items
                                    +--< ticket_transfers
                  +--< artist_memberships --< membership_point_logs
```

#### auth-service: 4개 마이그레이션 (V1 ~ V4)

| 버전 | 설명 | 주요 테이블 | 참조 |
|------|------|------------|------|
| V1 | 사용자 테이블 | users (email, password_hash, name, role) | `services-spring/auth-service/src/main/resources/db/migration/V1__create_users_table.sql:1-29` |
| V2 | Google OAuth | users.google_id 추가 | `services-spring/auth-service/src/main/resources/db/migration/V2__add_google_id_to_users.sql` |
| V3 | 관리자 시드 | admin 사용자 초기 데이터 | `services-spring/auth-service/src/main/resources/db/migration/V3__seed_admin.sql` |
| V4 | Refresh Token | refresh_tokens 테이블 | `services-spring/auth-service/src/main/resources/db/migration/V4__refresh_tokens_table.sql` |

users 테이블 구조 (라인 3-12):
- `id` (UUID PK), `email` (UNIQUE NOT NULL), `password_hash`, `name`, `phone`
- `role` (CHECK: 'user' | 'admin'), `created_at`, `updated_at`
- `update_updated_at_column()` 트리거로 자동 갱신 (라인 17-29)

#### payment-service: 3개 마이그레이션 (V1 ~ V3)

| 버전 | 설명 | 주요 테이블 | 참조 |
|------|------|------------|------|
| V1 | 결제 스키마 | payments, payment_logs (Toss 연동) | `services-spring/payment-service/src/main/resources/db/migration/V1__payment_schema.sql:1-40` |
| V2 | 결제 유형 확장 | payments.payment_type, reference_id 추가 | `services-spring/payment-service/src/main/resources/db/migration/V2__payment_types.sql:1-7` |
| V3 | 인덱스 추가 | user_id, reservation_id 인덱스 | `services-spring/payment-service/src/main/resources/db/migration/V3__add_indexes.sql:1-2` |

payments 테이블 구조 (V1 라인 3-25):
- 핵심: `id` (UUID PK), `reservation_id`, `user_id`, `event_id`, `order_id` (UNIQUE), `payment_key` (UNIQUE), `amount`, `method`, `status`
- Toss 연동: `toss_order_name`, `toss_status`, `toss_requested_at`, `toss_approved_at`, `toss_receipt_url`, `toss_checkout_url`, `toss_response` (JSONB)
- 환불: `refund_amount`, `refund_reason`, `refunded_at`

V2 변경 (라인 2-4): `payment_type` 컬럼(기본값 'reservation')과 `reference_id` 컬럼을 추가하여 양도/멤버십 결제를 지원하고, `reservation_id`의 NOT NULL 제약을 해제했다.

#### stats-service: 2개 마이그레이션 (V1 ~ V2)

| 버전 | 설명 | 주요 테이블 | 참조 |
|------|------|------------|------|
| V1 | 통계 스키마 | daily_stats, event_stats | `services-spring/stats-service/src/main/resources/db/migration/V1__stats_schema.sql:1-33` |
| V2 | 이벤트 중복 방지 | processed_events | `services-spring/stats-service/src/main/resources/db/migration/V2__processed_events_table.sql:1-7` |

daily_stats (라인 3-17): `date` (UNIQUE), `total_reservations`, `confirmed_reservations`, `cancelled_reservations`, `total_revenue`, `payment_revenue`, `new_users`, `active_users`, `new_events`, `active_events`

event_stats (라인 19-33): `event_id` (UNIQUE), `total_seats`, `reserved_seats`, `available_seats`, `total_reservations`, `confirmed_reservations`, `total_revenue`, `average_ticket_price`, `view_count`

#### community-service: 3개 마이그레이션 (V1 ~ V3)

| 버전 | 설명 | 주요 테이블 | 참조 |
|------|------|------------|------|
| V1 | 뉴스 스키마 | news | `services-spring/community-service/src/main/resources/db/migration/V1__community_schema.sql:1-13` |
| V2 | 인덱스 추가 | news 인덱스 | `services-spring/community-service/src/main/resources/db/migration/V2__add_indexes.sql:1-2` |
| V3 | 커뮤니티 게시판 | community_posts, community_comments | `services-spring/community-service/src/main/resources/db/migration/V3__community_posts_comments.sql:1-30` |

community_posts (V3 라인 2-14): `artist_id`, `author_id`, `author_name`, `title`, `content`, `views`, `comment_count`, `is_pinned`
community_comments (V3 라인 17-24): `post_id` (FK -> community_posts), `author_id`, `author_name`, `content`

### 5.3 Redis 데이터 구조

Redis는 3개 서비스(gateway, ticket, queue)에서 서로 다른 목적으로 사용된다.

#### 좌석 잠금 (ticket-service)

분산 잠금과 펜싱 토큰을 사용하여 좌석의 동시 예약을 방지한다.

| 키 패턴 | 타입 | 용도 | 참조 |
|---------|------|------|------|
| `seat:{eventId}:{seatId}` | HASH | 좌석 잠금 상태 (userId, fencingToken 등) | `services-spring/ticket-service/src/main/java/guru/urr/ticketservice/domain/seat/service/SeatLockService.java:110-112` |
| `seat:{eventId}:{seatId}:token_seq` | COUNTER (INCR) | 펜싱 토큰 시퀀스 번호 | `services-spring/ticket-service/src/main/java/guru/urr/ticketservice/domain/seat/service/SeatLockService.java:41` |

`SeatLockService` 동작:
- `acquireLock(eventId, seatId, userId)`: Lua 스크립트로 원자적으로 잠금 획득 + 펜싱 토큰 발급 (라인 39-62)
- `releaseLock(eventId, seatId, userId, token)`: Lua 스크립트로 소유자/토큰 검증 후 해제 (라인 65-78)
- `verifyForPayment(eventId, seatId, userId, token)`: 결제 진행 전 잠금 소유권 검증 (라인 81-98)
- `cleanupLock(eventId, seatId)`: 키 삭제 (라인 101-107)
- TTL: `SEAT_LOCK_TTL_SECONDS` (기본 300초 = 5분) (라인 30)

펜싱 토큰은 DB의 `seats.fencing_token` 컬럼(V8 마이그레이션)과 함께 사용되어, Redis 잠금 만료 후 지연된 쓰기 요청이 최신 잠금의 데이터를 덮어쓰는 것을 방지한다.
- 참조: `services-spring/ticket-service/src/main/resources/db/migration/V8__seats_concurrency_columns.sql:2-5`

#### VWR 대기열 (queue-service)

| 키 패턴 | 타입 | 용도 | 참조 |
|---------|------|------|------|
| `queue:{eventId}` | ZSET (score=입장시각 ms) | 대기열 (FIFO 순서) | `services-spring/queue-service/src/main/java/guru/urr/queueservice/service/QueueService.java:271-273` |
| `active:{eventId}` | ZSET (score=만료시각 ms) | 활성 사용자 (TTL 기반) | `services-spring/queue-service/src/main/java/guru/urr/queueservice/service/QueueService.java:275-277` |
| `queue:seen:{eventId}` | ZSET (score=마지막 활동 ms) | 대기열 하트비트 추적 | `services-spring/queue-service/src/main/java/guru/urr/queueservice/service/QueueService.java:279-281` |
| `active:seen:{eventId}` | ZSET (score=마지막 활동 ms) | 활성 사용자 하트비트 추적 | `services-spring/queue-service/src/main/java/guru/urr/queueservice/service/QueueService.java:283-285` |
| `queue:active-events` | SET | 현재 활성 대기열이 있는 이벤트 목록 | `services-spring/queue-service/src/main/java/guru/urr/queueservice/service/QueueService.java:264` |

대기열 동작 흐름 (라인 60-91):
1. 이미 대기열에 있는지 확인 -> 위치 반환
2. 이미 활성 사용자인지 확인 -> 입장 토큰 반환
3. 현재 활성 사용자 수가 threshold 이상이거나 대기열이 비어있지 않으면 -> 대기열에 추가
4. threshold 미만이면 -> 활성 사용자로 즉시 등록, 입장 토큰 발급

활성 사용자 만료: score에 `현재시각 + activeTtlSeconds * 1000` (기본 600초)을 저장하여, ZRANGEBYSCORE로 만료된 사용자를 제거할 수 있다 (라인 289-293, 301-303).

#### Rate Limiting (gateway-service)

| 키 패턴 | 타입 | 용도 | 참조 |
|---------|------|------|------|
| `rate:{category}:{clientId}` | ZSET (score=요청시각 ms) | 슬라이딩 윈도우 요청 카운트 | `services-spring/gateway-service/src/main/java/guru/urr/gatewayservice/filter/RateLimitFilter.java:74` |

Lua 스크립트(`rate_limit.lua`)가 원자적으로 윈도우 정리, 요청 추가, 카운트 확인, 제한 초과 시 롤백을 수행한다.
- 참조: `services-spring/gateway-service/src/main/resources/redis/rate_limit.lua:1-15`

#### 프로덕션 Redis 클러스터 설정

프로덕션 환경(`prod` 프로파일)에서는 3노드 Redis 클러스터를 사용한다:
- 노드: `redis-cluster-0.redis-cluster:6379`, `redis-cluster-1.redis-cluster:6379`, `redis-cluster-2.redis-cluster:6379`
- max-redirects: 3
- Lettuce 클러스터 토폴로지 adaptive refresh (30초 주기)
- 참조: `services-spring/gateway-service/src/main/resources/application.yml:122-138`, `services-spring/queue-service/src/main/resources/application.yml:86-102`

---

## 6. Resilience4j 설정

5개 서비스(ticket, payment, catalog, community, queue)가 Resilience4j를 사용하여 서비스 간 통신의 복원력을 확보한다. 모든 서비스가 동일한 `internalService` 인스턴스 설정을 공유한다.

### 6.1 Circuit Breaker 설정

`internalService` Circuit Breaker 인스턴스:

| 설정 | 값 | 설명 | 참조 |
|------|------|------|------|
| `sliding-window-size` | 10 | 최근 10개 호출 기반 상태 판단 | `services-spring/ticket-service/src/main/resources/application.yml:91` |
| `failure-rate-threshold` | 50% | 실패율 50% 초과 시 OPEN 상태 전환 | `services-spring/ticket-service/src/main/resources/application.yml:92` |
| `wait-duration-in-open-state` | 10초 | OPEN 상태 유지 시간 후 HALF-OPEN 전환 | `services-spring/ticket-service/src/main/resources/application.yml:93` |
| `permitted-number-of-calls-in-half-open-state` | 3 | HALF-OPEN에서 허용하는 시험 호출 수 | `services-spring/ticket-service/src/main/resources/application.yml:94` |
| `slow-call-duration-threshold` | 3초 | 3초 이상 소요 시 느린 호출로 분류 | `services-spring/ticket-service/src/main/resources/application.yml:95` |
| `slow-call-rate-threshold` | 80% | 느린 호출 비율 80% 초과 시 OPEN 전환 | `services-spring/ticket-service/src/main/resources/application.yml:96` |

동일 설정이 다음 서비스에서도 확인된다:
- `services-spring/payment-service/src/main/resources/application.yml:55-62`
- `services-spring/catalog-service/src/main/resources/application.yml:51-58`
- `services-spring/community-service/src/main/resources/application.yml:47-53`
- `services-spring/queue-service/src/main/resources/application.yml:60-67`

**queue-service 전용 추가 인스턴스: `redisQueue`**

| 설정 | 값 | 설명 | 참조 |
|------|------|------|------|
| `sliding-window-size` | 10 | - | `services-spring/queue-service/src/main/resources/application.yml:69` |
| `failure-rate-threshold` | 50% | - | `services-spring/queue-service/src/main/resources/application.yml:70` |
| `wait-duration-in-open-state` | **30초** | internalService(10초)보다 길다 | `services-spring/queue-service/src/main/resources/application.yml:71` |
| `record-exceptions` | `RedisConnectionFailureException` | Redis 연결 실패만 기록 | `services-spring/queue-service/src/main/resources/application.yml:73-74` |

### 6.2 Retry 설정

`internalService` Retry 인스턴스:

| 설정 | 값 | 설명 | 참조 |
|------|------|------|------|
| `max-attempts` | 3 | 최대 3회 시도 (원본 + 2회 재시도) | `services-spring/ticket-service/src/main/resources/application.yml:100` |
| `wait-duration` | 500ms | 첫 번째 재시도 대기 시간 | `services-spring/ticket-service/src/main/resources/application.yml:101` |
| `exponential-backoff-multiplier` | 2 | 지수 백오프 (500ms -> 1000ms -> 2000ms) | `services-spring/ticket-service/src/main/resources/application.yml:102` |
| `retry-exceptions` | `ResourceAccessException`, `ConnectException` | 네트워크 오류만 재시도 | `services-spring/ticket-service/src/main/resources/application.yml:103-105` |

재시도 대상 예외는 네트워크 수준 오류(`ResourceAccessException`, `ConnectException`)로 한정되어 있어, 4xx/5xx HTTP 응답에 대해서는 재시도하지 않는다. 이는 비멱등 연산의 부작용을 방지하는 의도적 설계이다.

### 6.3 Fallback 전략

내부 클라이언트별로 서로 다른 fallback 전략이 적용된다:

| 클라이언트 | 메소드 | Fallback 동작 | 참조 |
|------------|--------|--------------|------|
| payment -> ticket `validateReservation` | 검증 실패 | 502 BAD_GATEWAY 예외 발생 | `services-spring/payment-service/src/main/java/guru/urr/paymentservice/client/TicketInternalClient.java:102-104` |
| payment -> ticket `confirmReservation` | 확인 실패 | 로그만 남기고 무시 (Kafka가 보상) | `services-spring/payment-service/src/main/java/guru/urr/paymentservice/client/TicketInternalClient.java:96-99` |
| ticket -> payment `getPaymentByReservation` | 조회 실패 | null 반환 | `services-spring/ticket-service/src/main/java/guru/urr/ticketservice/shared/client/PaymentInternalClient.java:47-49` |
| queue -> catalog `getEventQueueInfo` | 조회 실패 | `Map.of("title", "Unknown")` 반환 | `services-spring/queue-service/src/main/java/guru/urr/queueservice/shared/client/TicketInternalClient.java:51-53` |
| community -> ticket `awardMembershipPoints` | 적립 실패 | 로그만 남기고 무시 | `services-spring/community-service/src/main/java/guru/urr/communityservice/shared/client/TicketInternalClient.java:62-65` |
| catalog -> auth `findUsersByIds` | 조회 실패 | 빈 Map 반환 | `services-spring/catalog-service/src/main/java/guru/urr/catalogservice/shared/client/AuthInternalClient.java:70-72` |
| catalog -> ticket `generateSeats` | 생성 실패 | 503 SERVICE_UNAVAILABLE 예외 발생 | `services-spring/catalog-service/src/main/java/guru/urr/catalogservice/shared/client/TicketInternalClient.java:84-88` |
| catalog -> ticket `getReservationStats` | 통계 조회 실패 | 기본값 Map(모든 값 0) 반환 | `services-spring/catalog-service/src/main/java/guru/urr/catalogservice/shared/client/TicketInternalClient.java:252-254` |

fallback 전략은 세 가지 패턴으로 분류된다:
1. **예외 전파**: 검증/생성처럼 실패가 치명적인 경우 502/503 예외를 발생시킨다
2. **기본값 반환**: 조회성 호출이 실패해도 서비스가 부분적으로 동작할 수 있도록 기본값을 반환한다
3. **무시(fire-and-forget)**: Kafka 이벤트 등 다른 메커니즘이 결과적 일관성을 보장하는 경우 실패를 무시한다
