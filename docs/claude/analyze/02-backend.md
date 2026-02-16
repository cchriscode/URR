# URR 백엔드 아키텍처 종합 분석

> 분석 일자: 2026-02-16
> 대상 경로: `C:\Users\USER\URR\services-spring\`
> 분석 범위: 8개 Spring Boot 마이크로서비스 전체

---

## 목차

1. [기술 스택](#1-기술-스택)
2. [MSA 구조](#2-msa-구조)
3. [DB 구조](#3-db-구조)
4. [서비스 간 통신](#4-서비스-간-통신)
5. [동시성 처리](#5-동시성-처리)
6. [데이터 모델](#6-데이터-모델)
7. [API 설계](#7-api-설계)
8. [에러 처리](#8-에러-처리)
9. [좋은 점과 미흡한 점](#9-좋은-점과-미흡한-점)

---

## 1. 기술 스택

### 핵심 프레임워크

| 항목 | 버전 / 상세 |
|------|-------------|
| **Spring Boot** | 3.5.0 |
| **Spring Cloud** | 2025.0.1 (gateway-service만 사용) |
| **Java** | 21 (toolchain) |
| **빌드 도구** | Gradle (각 서비스 독립 build.gradle) |
| **DB** | PostgreSQL 16 |
| **메시지 브로커** | Apache Kafka 3.7.0 |
| **캐시/락** | Redis 7 |
| **분산 추적** | Zipkin + Micrometer Tracing (Brave) |
| **메트릭** | Micrometer + Prometheus |
| **마이그레이션** | Flyway |

### 서비스별 핵심 의존성 매트릭스

| 서비스 | JPA | Redis | Kafka | Resilience4j | AWS SDK | Spotify API |
|--------|-----|-------|-------|--------------|---------|-------------|
| gateway-service | - | O | - | - | - | - |
| auth-service | O | - | - | - | - | - |
| ticket-service | O | O | O (P/C) | O | S3 | O |
| payment-service | O | - | O (P) | O | - | - |
| stats-service | O | - | O (C) | - | - | - |
| queue-service | - | O | - | O | SQS, DynamoDB | - |
| community-service | - (JDBC) | - | - | O | - | - |
| catalog-service | - (JDBC) | - | - | O | S3 | O |

> P = Producer, C = Consumer, P/C = 양방향

**참조 파일:**
- `services-spring/auth-service/build.gradle` (라인 1-61)
- `services-spring/gateway-service/build.gradle` (라인 1-48)
- `services-spring/ticket-service/build.gradle` (라인 1-65)
- `services-spring/queue-service/build.gradle` (라인 1-44)

---

## 2. MSA 구조

### 전체 아키텍처 다이어그램

```
                        [Client / Next.js Frontend]
                                    |
                                    v
                     +------------------------------+
                     |      gateway-service         |
                     |        (port 3001)           |
                     |  - JWT 검증 & 헤더 주입       |
                     |  - Rate Limiting (Redis Lua)  |
                     |  - VWR Entry Token 검증       |
                     |  - CORS / API 버전 처리       |
                     +------------------------------+
                                    |
            +-----------+-----------+-----------+-----------+-----------+-----------+
            |           |           |           |           |           |           |
            v           v           v           v           v           v           v
     +-----------+ +-----------+ +-----------+ +-----------+ +-----------+ +-----------+
     |   auth    | |  ticket   | | payment   | |  stats    | |  queue    | | catalog   |
     | (3005)    | | (3002)    | | (3003)    | | (3004)    | | (3007)    | | (3009)    |
     +-----------+ +-----------+ +-----------+ +-----------+ +-----------+ +-----------+
     | users     | | events    | | payments  | | daily_    | | Redis     | | events    |
     | refresh_  | | seats     | | payment_  | |   stats   | | SQS FIFO  | | artists   |
     | tokens    | | reserv..  | |   logs    | | event_    | | DynamoDB  | | (읽기전용) |
     |           | | ticket_   | |           | |   stats   | |           | |           |
     |           | |   types   | |           | | processed | |           | |           |
     |           | | artists   | |           | |  _events  | |           | |           |
     |           | | member..  | |           | |           | |           | |           |
     |           | | transfers | |           | |           | |           | |           |
     +-----------+ +-----------+ +-----------+ +-----------+ +-----------+ +-----------+
           |             |              |                                       |
        auth_db       ticket_db     payment_db     stats_db                 ticket_db
        (5438)        (5434)        (5435)         (5436)                   (공유)
                                                                community_db
                                                                (5437)
                                              +----------------+
                                              | community      |
                                              | (3008)         |
                                              +----------------+
```

### 서비스별 상세 책임

#### 2.1 gateway-service (포트 3001)

**역할:** API 게이트웨이 - 모든 외부 요청의 단일 진입점

**핵심 기능:**
- Spring Cloud Gateway MVC 기반 라우팅 (15개 라우트 정의)
- JWT 검증 후 `X-User-Id`, `X-User-Email`, `X-User-Role` 헤더 주입
- 외부 `X-User-*` 헤더 스푸핑 방지 (자동 스트리핑)
- Redis Lua 스크립트 기반 Rate Limiting (4가지 카테고리)
- VWR(Virtual Waiting Room) Entry Token 검증
- CloudFront 바이패스 지원

**Rate Limit 카테고리:**

| 카테고리 | 기본값 | 대상 경로 |
|----------|--------|-----------|
| AUTH | 60 RPM | `/api/v1/auth/**` |
| QUEUE | 120 RPM | `/api/v1/queue/**` |
| BOOKING | 30 RPM | `/api/v1/seats/reserve`, `/api/v1/reservations` |
| GENERAL | 3000 RPM | 기타 모든 경로 |

**참조 파일:**
- `services-spring/gateway-service/src/main/resources/application.yml` (라인 1-139)
- `services-spring/gateway-service/src/main/java/guru/urr/gatewayservice/filter/JwtAuthFilter.java` (라인 1-182)
- `services-spring/gateway-service/src/main/java/guru/urr/gatewayservice/filter/RateLimitFilter.java` (라인 1-155)
- `services-spring/gateway-service/src/main/java/guru/urr/gatewayservice/filter/VwrEntryTokenFilter.java` (라인 1-141)

#### 2.2 auth-service (포트 3005)

**역할:** 인증/인가 서비스

**핵심 기능:**
- 이메일/비밀번호 기반 회원가입 및 로그인
- Google OAuth 2.0 소셜 로그인
- JWT 발급 (Access Token + Refresh Token)
- Refresh Token Rotation 및 재사용 탐지 (family-based revocation)
- HttpOnly 쿠키 기반 토큰 전달
- 내부 서비스용 사용자 조회 API

**인증 흐름:**
```
[Client] --POST /api/auth/login--> [auth-service]
    1. 비밀번호 검증 (BCrypt)
    2. JWT Access Token 생성 (userId, email, role 포함)
    3. Refresh Token 생성 (familyId 포함)
    4. refresh_tokens 테이블에 token_hash 저장
    5. Set-Cookie: access_token, refresh_token (HttpOnly)
    6. Response body에도 token/refreshToken 포함
```

**Refresh Token Rotation:**
```
[Client] --POST /api/auth/refresh--> [auth-service]
    1. Refresh Token의 token_hash로 DB 조회
    2. 이미 revoked된 경우 -> familyId 전체 무효화 (도난 감지)
    3. 현재 토큰 revoke 처리 (단일 사용)
    4. 동일 familyId로 새 Refresh Token 발급
```

**참조 파일:**
- `services-spring/auth-service/src/main/java/guru/urr/authservice/service/AuthService.java` (라인 1-309)
- `services-spring/auth-service/src/main/java/guru/urr/authservice/controller/AuthController.java` (라인 1-118)

#### 2.3 ticket-service (포트 3002)

**역할:** 티켓팅 핵심 서비스 - 좌석, 예약, 멤버십, 양도

**핵심 기능:**
- 좌석 예약 (Redis 분산 락 + DB 낙관적 잠금의 2-Phase 프로토콜)
- 일반 예약 (티켓 유형별 수량 기반)
- 예약 만료 자동 정리 (30초 주기 스케줄러)
- 결제 조정 스케줄러 (5분 주기)
- 아티스트 멤버십 시스템 (SILVER -> GOLD -> DIAMOND 티어)
- 티켓 양도 마켓플레이스
- Kafka 이벤트 발행 (reservation-events, transfer-events, membership-events)
- Kafka 이벤트 소비 (payment-events)
- Micrometer 기반 비즈니스 메트릭 수집

**참조 파일:**
- `services-spring/ticket-service/src/main/java/guru/urr/ticketservice/domain/reservation/service/ReservationService.java` (라인 1-536)
- `services-spring/ticket-service/src/main/java/guru/urr/ticketservice/domain/seat/service/SeatLockService.java` (라인 1-113)
- `services-spring/ticket-service/src/main/java/guru/urr/ticketservice/domain/membership/service/MembershipService.java` (라인 1-291)
- `services-spring/ticket-service/src/main/java/guru/urr/ticketservice/domain/transfer/service/TransferService.java` (라인 1-270)

#### 2.4 payment-service (포트 3003)

**역할:** 결제 처리 서비스

**핵심 기능:**
- Toss Payments 연동 (prepare -> confirm 2단계 결제)
- 즉시 결제 (process) 지원
- 결제 취소 및 환불 처리
- 3가지 결제 유형 지원: reservation, transfer, membership
- Kafka를 통한 결제 완료/환불 이벤트 발행
- 동기적 내부 API 호출 (primary) + Kafka 이벤트 (secondary fallback)

**결제 흐름:**
```
[Client] --POST /api/payments/prepare--> [payment-service]
    1. ticket-service 내부 API로 예약 유효성 검증
    2. orderId 생성 (ORD_timestamp_uuid8)
    3. payments 테이블에 'pending' 상태로 저장
    4. 응답: { orderId, amount, clientKey }

[Client] --POST /api/payments/confirm--> [payment-service]
    1. orderId로 결제 레코드 조회 (FOR UPDATE)
    2. 소유자/금액 검증
    3. 결제 상태 'confirmed'로 업데이트
    4. 동기: ticket-service 내부 API로 예약 확정 시도
    5. 비동기: Kafka payment-events 토픽에 PaymentConfirmedEvent 발행
```

**참조 파일:**
- `services-spring/payment-service/src/main/java/guru/urr/paymentservice/service/PaymentService.java` (라인 1-425)
- `services-spring/payment-service/src/main/java/guru/urr/paymentservice/controller/PaymentController.java` (라인 1-90)
- `services-spring/payment-service/src/main/java/guru/urr/paymentservice/messaging/PaymentEventProducer.java` (라인 1-43)

#### 2.5 stats-service (포트 3004)

**역할:** 통계 및 분석 서비스

**핵심 기능:**
- Kafka 이벤트 소비 (payment-events, reservation-events, membership-events)
- 일별 통계 집계 (daily_stats)
- 이벤트별 통계 관리 (event_stats)
- 관리자 전용 대시보드 API (overview, daily, revenue, conversion 등)
- 이벤트 중복 처리 방지 (processed_events 테이블)

**API 목록 (모두 admin 권한 필요):**
- `GET /api/stats/overview` - 종합 대시보드
- `GET /api/stats/daily?days=30` - 일별 통계
- `GET /api/stats/events?limit=10&sortBy=revenue` - 이벤트별 통계
- `GET /api/stats/revenue?period=daily&days=30` - 매출 통계
- `GET /api/stats/realtime` - 실시간 지표
- `GET /api/stats/conversion?days=30` - 전환율 분석
- `GET /api/stats/seat-preferences` - 좌석 선호도
- `GET /api/stats/user-behavior?days=30` - 사용자 행동 분석
- `GET /api/stats/performance` - 성능 지표

**참조 파일:**
- `services-spring/stats-service/src/main/java/guru/urr/statsservice/messaging/StatsEventConsumer.java` (라인 1-192)
- `services-spring/stats-service/src/main/java/guru/urr/statsservice/controller/StatsController.java` (라인 1-145)

#### 2.6 queue-service (포트 3007)

**역할:** Virtual Waiting Room (가상 대기열) 서비스

**핵심 기능:**
- Redis ZSET 기반 대기열 관리 (position tracking)
- 활성 사용자 관리 (threshold 기반 입장 제어)
- 배치 입장 처리 (AdmissionWorkerService, 1초 주기)
- Stale 사용자 자동 정리 (30초 주기)
- 처리량 기반 대기시간 추정
- 동적 폴링 간격 계산 (position 기반)
- SQS FIFO를 통한 입장 이벤트 발행
- DynamoDB 카운터 (VWR 통계)
- Entry Token 발급 (JWT 기반, eventId + userId 바인딩)

**대기열 상태 머신:**
```
[check] --> [currentUsers < threshold && queueSize == 0]
                    --> ACTIVE (즉시 입장, entryToken 발급)
            [currentUsers >= threshold || queueSize > 0]
                    --> QUEUED (대기열에 추가)

[AdmissionWorker] (1초 주기)
    --> Redis Lua 스크립트로 atomic batch admission
    --> threshold까지 대기열에서 사용자 이동

[StaleCleanup] (30초 주기)
    --> heartbeat 없는 사용자 제거
```

**Redis 키 구조:**

| 키 패턴 | 타입 | 용도 |
|---------|------|------|
| `queue:{eventId}` | ZSET | 대기열 (score=timestamp) |
| `active:{eventId}` | ZSET | 활성 사용자 (score=만료시간) |
| `queue:seen:{eventId}` | ZSET | 대기열 heartbeat 추적 |
| `active:seen:{eventId}` | ZSET | 활성 사용자 heartbeat 추적 |
| `queue:active-events` | SET | 활성 대기열이 있는 이벤트 목록 |
| `admission:lock:{eventId}` | STRING | 배치 입장 분산 락 |

**참조 파일:**
- `services-spring/queue-service/src/main/java/guru/urr/queueservice/service/QueueService.java` (라인 1-365)
- `services-spring/queue-service/src/main/java/guru/urr/queueservice/service/AdmissionWorkerService.java` (라인 1-184)
- `services-spring/queue-service/src/main/java/guru/urr/queueservice/service/SqsPublisher.java` (라인 1-69)

#### 2.7 community-service (포트 3008)

**역할:** 커뮤니티 서비스 - 뉴스, 게시판, 댓글

**핵심 기능:**
- 뉴스(공지) 관리 (admin CRUD)
- 아티스트별 커뮤니티 게시판
- 게시글 및 댓글 CRUD
- ticket-service 내부 API를 통한 멤버십 검증

**참조 파일:**
- `services-spring/community-service/src/main/java/guru/urr/communityservice/controller/NewsController.java`
- `services-spring/community-service/src/main/java/guru/urr/communityservice/controller/CommunityPostController.java`
- `services-spring/community-service/src/main/java/guru/urr/communityservice/controller/CommentController.java`

#### 2.8 catalog-service (포트 3009)

**역할:** 카탈로그/관리자 서비스 - 이벤트 조회, 아티스트, 관리자 기능

**핵심 기능:**
- 이벤트 목록 및 상세 조회 (공개 API)
- 아티스트 관리 (Spotify API 연동)
- 관리자 이벤트 CRUD (생성, 수정, 취소, 삭제)
- 관리자 좌석/티켓유형 관리
- 관리자 예약 관리 (목록 조회, 상태 변경)
- S3 이미지 업로드
- Admin Audit Log (AOP 기반)
- ticket-service DB(ticket_db)를 읽기 전용으로 공유

**참조 파일:**
- `services-spring/catalog-service/src/main/java/guru/urr/catalogservice/domain/event/controller/EventController.java` (라인 1-37)
- `services-spring/catalog-service/src/main/java/guru/urr/catalogservice/domain/admin/service/AdminService.java` (라인 1-309)
- `services-spring/catalog-service/src/main/java/guru/urr/catalogservice/domain/artist/controller/ArtistController.java`

---

## 3. DB 구조

### 3.1 데이터베이스 인스턴스 분리

```
+-------------------+     +-------------------+     +-------------------+
|    auth_db        |     |    ticket_db      |     |   payment_db      |
|   (port 5438)     |     |   (port 5434)     |     |   (port 5435)     |
|                   |     |                   |     |                   |
| - users           |     | - events          |     | - payments        |
| - refresh_tokens  |     | - seats           |     | - payment_logs    |
|                   |     | - seat_layouts    |     |                   |
|                   |     | - reservations    |     +-------------------+
|                   |     | - reservation_    |
+-------------------+     |   items           |     +-------------------+
                          | - ticket_types    |     |   stats_db        |
                          | - artists         |     |   (port 5436)     |
                          | - artist_         |     |                   |
                          |   memberships     |     | - daily_stats     |
                          | - membership_     |     | - event_stats     |
                          |   point_logs      |     | - processed_      |
                          | - ticket_         |     |   events          |
                          |   transfers       |     +-------------------+
                          | - keyword_        |
                          |   mappings        |     +-------------------+
                          | - news            |     |  community_db     |
                          | - admin_audit_    |     |   (port 5437)     |
                          |   logs            |     |                   |
                          | - processed_      |     | - news            |
                          |   events          |     | - community_posts |
                          +-------------------+     | - community_      |
                                 ^                  |   comments        |
                                 |                  +-------------------+
                          catalog-service
                          (읽기 전용 공유)
```

**참조 파일:** `services-spring/docker-compose.databases.yml` (라인 1-73)

### 3.2 서비스-DB 매핑

| 서비스 | DB | 접속 포트 | Flyway | JPA/JDBC | 비고 |
|--------|-----|-----------|--------|----------|------|
| auth-service | auth_db | 5438 | O | JPA | 독립 DB |
| ticket-service | ticket_db | 5434 | O | JPA + JdbcTemplate | 핵심 DB, 14개 마이그레이션 |
| catalog-service | ticket_db | 5434 | X (비활성) | JdbcTemplate | ticket_db 읽기 전용 공유 |
| payment-service | payment_db | 5435 | O | JPA + JdbcTemplate | 독립 DB |
| stats-service | stats_db | 5436 | O | JPA + JdbcTemplate | 독립 DB |
| community-service | community_db | 5437 | O | JdbcTemplate | 독립 DB |
| queue-service | - | - | - | - | DB 미사용 (Redis + SQS + DynamoDB) |
| gateway-service | - | - | - | - | DB 미사용 (Redis만) |

### 3.3 DB 연결 설정 패턴

모든 서비스가 동일한 패턴으로 DB 연결을 구성한다:

```yaml
spring:
  datasource:
    url: ${서비스_DB_URL:jdbc:postgresql://localhost:PORT/DB_NAME}
    username: ${서비스_DB_USERNAME:urr_user}
    password: ${서비스_DB_PASSWORD:urr_password}
  jpa:
    hibernate:
      ddl-auto: validate     # Flyway가 스키마 관리
    open-in-view: false       # OSIV 비활성화 (올바른 설정)
  flyway:
    enabled: true
    baseline-on-migrate: true
```

**핵심 관찰:**
- 모든 서비스에서 `ddl-auto: validate`를 사용하여 Flyway가 스키마를 관리한다.
- `open-in-view: false`로 설정하여 LazyInitializationException 리스크를 제거했다.
- catalog-service는 `flyway.enabled: false`로 설정하여 ticket_db의 스키마를 건드리지 않는다.

### 3.4 Flyway 마이그레이션 현황

| 서비스 | 마이그레이션 수 | 핵심 내용 |
|--------|----------------|-----------|
| auth-service | V1~V4 | users, google_id, seed_admin, refresh_tokens |
| ticket-service | V1~V14 | core schema, seed data, artists, memberships, transfers, concurrency, standing, seats gen, indexes, idempotency, audit_logs, processed_events |
| payment-service | V1~V3 | payment schema, payment_types, indexes |
| stats-service | V1~V2 | stats schema, processed_events |
| community-service | V1~V3 | news, indexes, posts_comments |

---

## 4. 서비스 간 통신

### 4.1 동기 통신 (REST - Internal API)

서비스 간 동기 호출은 `RestClient` (Spring 6.1+)를 사용하며, `INTERNAL_API_TOKEN`으로 인증한다.

```
+------------------+                   +------------------+
| payment-service  | --RestClient-->   | ticket-service   |
| (TicketInternal  |   /internal/...   | (Internal*       |
|  Client)         |                   |  Controller)     |
+------------------+                   +------------------+
       |                                       ^
       | validateReservation                   |
       | validateTransfer                      |
       | validateMembership                    |
       | confirmReservation                    |
       | confirmTransfer                       |
       | activateMembership                    |
       v                                       |
+------------------+                   +------------------+
| catalog-service  | --RestClient-->   | auth-service     |
| (AuthInternal    |   /internal/...   | (InternalUser    |
|  Client)         |                   |  Controller)     |
| (TicketInternal  |                   +------------------+
|  Client)         |
+------------------+
       |
       | ticket-service 좌석/예약 관리
       | auth-service 사용자 정보 조회
       v
+------------------+
| queue-service    | --RestClient-->   ticket-service
| (TicketInternal  |                   catalog-service
|  Client)         |
+------------------+
       |
       | getEventQueueInfo
       v
+------------------+
| community-service| --RestClient-->   ticket-service
| (TicketInternal  |
|  Client)         |
+------------------+
       |
       | 멤버십 검증
```

**Internal API 호출 목록:**

| 호출자 | 대상 | 엔드포인트 | 용도 |
|--------|------|-----------|------|
| payment -> ticket | GET `/internal/reservations/{id}/validate` | 예약 유효성 검증 |
| payment -> ticket | POST `/internal/reservations/{id}/confirm` | 예약 확정 |
| payment -> ticket | GET `/internal/transfers/{id}/validate` | 양도 유효성 검증 |
| payment -> ticket | POST `/internal/transfers/{id}/complete` | 양도 완료 |
| payment -> ticket | GET `/internal/memberships/{id}/validate` | 멤버십 유효성 검증 |
| payment -> ticket | POST `/internal/memberships/{id}/activate` | 멤버십 활성화 |
| catalog -> auth | POST `/internal/users/batch` | 사용자 정보 일괄 조회 |
| catalog -> ticket | 다수 엔드포인트 | 좌석/예약/티켓유형 관리 |
| queue -> ticket | GET `/internal/events/{id}/queue-info` | 이벤트 큐 정보 조회 |
| queue -> catalog | 이벤트 정보 조회 | 이벤트 상세 정보 |
| community -> ticket | 멤버십 검증 | 커뮤니티 접근 권한 |

**RestClient 설정 (모든 InternalClient 공통):**

```java
// 참조: services-spring/payment-service/src/main/java/guru/urr/paymentservice/client/TicketInternalClient.java (라인 26-33)
var requestFactory = ClientHttpRequestFactories.get(
    ClientHttpRequestFactorySettings.DEFAULTS
        .withConnectTimeout(Duration.ofSeconds(5))
        .withReadTimeout(Duration.ofSeconds(10)));
this.restClient = RestClient.builder()
    .baseUrl(ticketServiceUrl)
    .requestFactory(requestFactory)
    .build();
```

### 4.2 비동기 통신 (Kafka)

#### Kafka 토픽 구조

| 토픽 | 파티션 | 생산자 | 소비자 | 이벤트 유형 |
|------|--------|--------|--------|-------------|
| `payment-events` | 3 | payment-service | ticket-service (group: ticket-service-group), stats-service (group: stats-service-group) | PaymentConfirmedEvent, PaymentRefundedEvent |
| `reservation-events` | 3 | ticket-service | stats-service (group: stats-service-group) | ReservationCreatedEvent, ReservationConfirmedEvent, ReservationCancelledEvent |
| `transfer-events` | 3 | ticket-service | stats-service (group: stats-service-group) | TransferCompletedEvent |
| `membership-events` | 3 | ticket-service | stats-service (group: stats-service-group) | MembershipActivatedEvent |

**참조 파일:** `services-spring/ticket-service/src/main/java/guru/urr/ticketservice/shared/config/KafkaConfig.java` (라인 1-34)

#### Kafka 이벤트 플로우 다이어그램

```
[payment-service]
    |
    |-- PaymentConfirmedEvent --> [payment-events] --> [ticket-service] 예약확정/양도완료/멤버십활성화
    |                                              --> [stats-service]  통계 집계
    |
    |-- PaymentRefundedEvent  --> [payment-events] --> [ticket-service] 예약환불처리
                                                   --> [stats-service]  환불 통계

[ticket-service]
    |
    |-- ReservationCreatedEvent    --> [reservation-events] --> [stats-service]
    |-- ReservationConfirmedEvent  --> [reservation-events] --> [stats-service]
    |-- ReservationCancelledEvent  --> [reservation-events] --> [stats-service]
    |-- TransferCompletedEvent     --> [transfer-events]    --> [stats-service]
    |-- MembershipActivatedEvent   --> [membership-events]  --> [stats-service]
```

#### Kafka 직렬화 설정

- **Producer:** `StringSerializer` (key) + `JsonSerializer` (value), `acks=all`
- **Consumer:** `StringDeserializer` (key) + `JsonDeserializer` (value), `auto-offset-reset=earliest`
- **Trusted packages:** `com.urr.paymentservice.messaging.event, com.urr.ticketservice.messaging.event`
- **Type headers 비활성화:** `spring.json.use.type.headers=false`, `spring.json.value.default.type=java.util.LinkedHashMap`
  - duck-typing + explicit type field 방식으로 이벤트 구분

#### 이벤트 멱등성(Idempotency) 보장

**ticket-service (PaymentEventConsumer):**
- `processed_events` 테이블에 `(event_key, consumer_group)` 저장
- event_key 생성: sagaId 우선, 없으면 `type:referenceId`
- `DuplicateKeyException` 무시하여 동시 처리 안전

**stats-service (StatsEventConsumer):**
- `processed_events` 테이블에 `event_key` 저장
- event_key 생성: `type:id:timestamp`
- `ON CONFLICT (event_key) DO NOTHING` 활용

**참조 파일:**
- `services-spring/ticket-service/src/main/java/guru/urr/ticketservice/messaging/PaymentEventConsumer.java` (라인 191-231)
- `services-spring/stats-service/src/main/java/guru/urr/statsservice/messaging/StatsEventConsumer.java` (라인 136-171)

### 4.3 SQS FIFO

queue-service에서 사용자 입장 이벤트를 AWS SQS FIFO 큐에 발행한다.

```
[queue-service] --SQS FIFO--> [외부 시스템 / 모니터링]
    messageGroupId: eventId
    messageDeduplicationId: userId:eventId (5분 중복 방지 윈도우)
```

**특징:**
- `aws.sqs.enabled=false`로 기본 비활성화 (로컬/KIND 환경)
- SQS 실패 시 Redis-only로 fallback (fire-and-forget 패턴)

**참조 파일:** `services-spring/queue-service/src/main/java/guru/urr/queueservice/service/SqsPublisher.java` (라인 1-69)

---

## 5. 동시성 처리

### 5.1 좌석 예약 - 2-Phase Locking Protocol

좌석 예약은 동시성 충돌을 방지하기 위해 Redis 분산 락과 DB 낙관적 잠금을 결합한 2-Phase 프로토콜을 사용한다.

```
=== Phase 1: Redis Lua 분산 락 ===

[Client] --POST /api/seats/reserve--> [ticket-service]

    for each seatId:
        1. Redis Lua 스크립트 실행 (atomic)
           - 좌석 상태 확인 (HELD/CONFIRMED이면 실패)
           - fencing_token 카운터 INCR (단조 증가)
           - HMSET {status: HELD, userId, token, heldAt}
           - TTL 설정 (기본 300초)

        2. 실패 시: 이전에 획득한 모든 락 rollback

=== Phase 2: DB 낙관적 잠금 ===

    3. SELECT ... FOR UPDATE (비관적 잠금으로 행 잠금)
    4. status='available' 확인
    5. UPDATE seats SET status='locked', version=version+1,
       fencing_token=?, locked_by=? WHERE id=? AND version=?
       (version 불일치 시 CONFLICT)

    6. 예약 레코드 생성 (5분 만료)
```

**Redis Lua 스크립트 (seat_lock_acquire.lua):**

```lua
-- KEYS[1] = seat:{eventId}:{seatId}
-- KEYS[2] = seat:{eventId}:{seatId}:token_seq
-- ARGV[1] = userId, ARGV[2] = ttl

local status = redis.call('HGET', seatKey, 'status')
if status == 'HELD' or status == 'CONFIRMED' then
    local currentUser = redis.call('HGET', seatKey, 'userId')
    if currentUser == userId then
        -- 동일 사용자 재선택: TTL 연장
        redis.call('EXPIRE', seatKey, ttl)
        return {1, existingToken}
    end
    return {0, '-1'}  -- 다른 사용자가 점유
end
-- fencing token 생성 (단조 증가)
local token = redis.call('INCR', tokenSeqKey)
-- HELD 상태로 전환
redis.call('HMSET', seatKey, 'status', 'HELD', 'userId', userId, 'token', token, ...)
redis.call('EXPIRE', seatKey, ttl)
return {1, token}
```

**참조 파일:**
- `services-spring/ticket-service/src/main/resources/redis/seat_lock_acquire.lua` (라인 1-36)
- `services-spring/ticket-service/src/main/java/guru/urr/ticketservice/domain/reservation/service/ReservationService.java` (라인 55-167)

### 5.2 Fencing Token 검증

결제 확인 시점에 fencing token을 검증하여, 락이 만료되어 다른 사용자에게 재할당된 좌석에 대한 결제를 방지한다.

```
[결제 확인 시]
    1. 좌석의 fencing_token을 DB에서 조회
    2. Redis에서 해당 좌석의 현재 token/userId 확인
    3. token 불일치 시 -> 409 CONFLICT ("Seat lock expired or stolen")
```

**참조 파일:** `services-spring/ticket-service/src/main/java/guru/urr/ticketservice/domain/reservation/service/ReservationService.java` (라인 391-418)

### 5.3 예약 만료 자동 정리

```java
@Scheduled(fixedRateString = "${reservation.cleanup.interval-ms:30000}")
@Transactional
public void cleanupExpiredReservations() {
    // FOR UPDATE SKIP LOCKED: 다중 인스턴스 환경에서 안전
    SELECT ... WHERE status = 'pending' AND expires_at < NOW()
    FOR UPDATE SKIP LOCKED

    // 각 만료 예약에 대해:
    // 1. 좌석 상태 복구 (available, version+1, fencing_token=0)
    // 2. Redis 좌석 락 정리
    // 3. 티켓유형 수량 복구
    // 4. 예약 상태 'expired'로 변경
}
```

**핵심: `FOR UPDATE SKIP LOCKED`** - 이미 다른 트랜잭션이 처리 중인 행은 건너뛴다. 다중 인스턴스 환경에서 동일 예약을 중복 처리하지 않는다.

**참조 파일:** `services-spring/ticket-service/src/main/java/guru/urr/ticketservice/scheduling/ReservationCleanupScheduler.java` (라인 1-101)

### 5.4 대기열 입장 제어 (Redis 분산 락)

```java
// AdmissionWorkerService: 1초 주기 실행
String lockKey = "admission:lock:" + eventId;
Boolean acquired = redisTemplate.opsForValue()
    .setIfAbsent(lockKey, "1", Duration.ofSeconds(4));

if (!acquired) continue;  // 다른 워커가 처리 중

// Redis Lua 스크립트로 atomic batch admission
redisTemplate.execute(admissionScript, ...);
```

**참조 파일:** `services-spring/queue-service/src/main/java/guru/urr/queueservice/service/AdmissionWorkerService.java` (라인 47-118)

### 5.5 DB 레벨 동시성 제어 요약

| 기법 | 적용 위치 | 설명 |
|------|-----------|------|
| `FOR UPDATE` | 좌석 예약, 결제 확인, 양도 완료 | 비관적 잠금 - 행 레벨 |
| `FOR UPDATE SKIP LOCKED` | 예약 만료 정리 | 다중 인스턴스 안전한 배치 처리 |
| `version` 컬럼 | seats 테이블 | 낙관적 잠금 - WHERE version=? |
| `fencing_token` 컬럼 | seats 테이블 | Redis 락 토큰 검증 |
| `idempotency_key` 컬럼 | reservations 테이블 | 중복 예약 방지 |
| `processed_events` 테이블 | ticket-service, stats-service | Kafka 이벤트 멱등성 |

---

## 6. 데이터 모델

### 6.1 핵심 엔티티 관계도 (ERD)

```
[users] (auth_db)
    id UUID PK
    email VARCHAR(255) UNIQUE
    password_hash VARCHAR(255)
    name VARCHAR(100)
    phone VARCHAR(20)
    role VARCHAR(20) CHECK ('user','admin')
    google_id VARCHAR(255)
        |
        | 1:N
        v
[refresh_tokens] (auth_db)
    id UUID PK
    user_id UUID FK
    token_hash VARCHAR(255) UNIQUE
    family_id UUID
    expires_at TIMESTAMPTZ
    revoked_at TIMESTAMPTZ

=====================================

[seat_layouts] (ticket_db)
    id UUID PK
    name VARCHAR(100) UNIQUE
    layout_config JSONB
        |
        | 1:N
        v
[events] (ticket_db)
    id UUID PK
    title VARCHAR(255)
    venue VARCHAR(255)
    event_date TIMESTAMPTZ
    sale_start_date TIMESTAMPTZ
    sale_end_date TIMESTAMPTZ
    status VARCHAR(20) -- upcoming/on_sale/ended/cancelled
    seat_layout_id UUID FK -> seat_layouts
    artist_id UUID FK -> artists
    poster_image_url TEXT
        |
        +-- 1:N --> [seats]
        |               id UUID PK
        |               event_id UUID FK
        |               section VARCHAR(50)
        |               row_number INTEGER
        |               seat_number INTEGER
        |               seat_label VARCHAR(20)
        |               price INTEGER
        |               status VARCHAR(20) -- available/locked/reserved
        |               version INTEGER (낙관적 잠금)
        |               fencing_token BIGINT (Redis 락 검증)
        |               locked_by UUID
        |
        +-- 1:N --> [ticket_types]
        |               id UUID PK
        |               event_id UUID FK
        |               name VARCHAR(100)
        |               price INTEGER
        |               total_quantity INTEGER
        |               available_quantity INTEGER
        |
        +-- 1:N --> [reservations]
                        id UUID PK
                        user_id UUID
                        event_id UUID FK
                        reservation_number VARCHAR(50) UNIQUE
                        total_amount INTEGER
                        status VARCHAR(20) -- pending/confirmed/cancelled/expired
                        payment_status VARCHAR(20) -- pending/completed/refunded/refund_requested
                        payment_method VARCHAR(50)
                        expires_at TIMESTAMPTZ
                        idempotency_key VARCHAR(255)
                            |
                            +-- 1:N --> [reservation_items]
                                            id UUID PK
                                            reservation_id UUID FK
                                            ticket_type_id UUID FK (nullable)
                                            seat_id UUID FK (nullable)
                                            quantity INTEGER
                                            unit_price INTEGER
                                            subtotal INTEGER

[artists] (ticket_db)
    id UUID PK
    name VARCHAR(255) UNIQUE
    image_url TEXT
    membership_price INTEGER DEFAULT 30000
        |
        +-- 1:N --> [artist_memberships]
        |               id UUID PK
        |               user_id UUID
        |               artist_id UUID FK
        |               tier VARCHAR(20) -- SILVER/GOLD/DIAMOND
        |               points INTEGER DEFAULT 0
        |               status VARCHAR(20) -- pending/active/expired
        |               expires_at TIMESTAMPTZ
        |               UNIQUE(user_id, artist_id)
        |                   |
        |                   +-- 1:N --> [membership_point_logs]
        |                                   id UUID PK
        |                                   membership_id UUID FK
        |                                   action_type VARCHAR(50)
        |                                   points INTEGER
        |                                   description TEXT
        |
        +-- 1:N --> [ticket_transfers]
                        id UUID PK
                        reservation_id UUID FK
                        seller_id UUID
                        buyer_id UUID (nullable)
                        artist_id UUID FK
                        original_price INTEGER
                        transfer_fee INTEGER
                        transfer_fee_percent INTEGER
                        total_price INTEGER
                        status VARCHAR(20) -- listed/completed/cancelled

=====================================

[payments] (payment_db)
    id UUID PK
    reservation_id UUID (nullable)
    reference_id UUID (nullable) -- transfer/membership용
    user_id UUID
    event_id UUID
    order_id VARCHAR(64) UNIQUE
    payment_key VARCHAR(200) UNIQUE
    amount INTEGER
    method VARCHAR(50)
    status VARCHAR(20) -- pending/confirmed/refunded
    payment_type VARCHAR(20) -- reservation/transfer/membership
    toss_status/toss_approved_at/toss_response -- Toss Payments 연동 필드
        |
        +-- 1:N --> [payment_logs]
                        id UUID PK
                        payment_id UUID FK
                        action VARCHAR(50)
                        request_body JSONB
                        response_body JSONB

=====================================

[daily_stats] (stats_db)
    id UUID PK
    date DATE UNIQUE
    total_reservations INTEGER
    confirmed_reservations INTEGER
    total_revenue INTEGER
    ...

[event_stats] (stats_db)
    id UUID PK
    event_id UUID UNIQUE
    total_seats INTEGER
    reserved_seats INTEGER
    total_revenue INTEGER
    ...

=====================================

[community_posts] (community_db)
    id UUID PK
    artist_id UUID
    author_id UUID
    title VARCHAR(255)
    content TEXT
    views INTEGER
    comment_count INTEGER
        |
        +-- 1:N --> [community_comments]
                        id UUID PK
                        post_id UUID FK
                        author_id UUID
                        content TEXT
```

### 6.2 멤버십 티어 시스템

| 티어 | 포인트 기준 | 선예매 단계 | 수수료 할증 | 양도 수수료 |
|------|------------|------------|------------|------------|
| BRONZE (비회원) | - | 일반예매 | 0원 | 양도 불가 |
| SILVER | 0~499 | 선예매 3 | 3,000원 | 10% |
| GOLD | 500~1,499 | 선예매 2 | 2,000원 | 5% |
| DIAMOND | 1,500+ | 선예매 1 | 1,000원 | 5% |

**포인트 획득:**
- 멤버십 가입: 200 포인트
- 멤버십 갱신: 200 포인트
- 티켓 구매: 100 포인트

**참조 파일:** `services-spring/ticket-service/src/main/java/guru/urr/ticketservice/domain/membership/service/MembershipService.java` (라인 252-289)

---

## 7. API 설계

### 7.1 라우팅 구조

게이트웨이는 `/api/v1/` 접두사와 `/api/` 접두사를 모두 지원한다. 내부 서비스는 `/api/` 접두사만 사용한다. 게이트웨이의 `ApiVersionFilter`가 `/api/v1/`을 `/api/`로 변환한다.

### 7.2 서비스별 API 엔드포인트

#### auth-service

| 메서드 | 경로 | 인증 | 설명 |
|--------|------|------|------|
| POST | `/api/auth/register` | - | 회원가입 |
| POST | `/api/auth/login` | - | 로그인 |
| POST | `/api/auth/google` | - | Google 소셜 로그인 |
| GET | `/api/auth/me` | JWT | 내 정보 조회 |
| POST | `/api/auth/refresh` | Cookie/Body | 토큰 갱신 |
| POST | `/api/auth/verify-token` | - | 토큰 검증 |
| POST | `/api/auth/logout` | JWT | 로그아웃 (전체 세션 무효화) |

#### ticket-service

| 메서드 | 경로 | 인증 | 설명 |
|--------|------|------|------|
| GET | `/api/seats/layouts` | - | 좌석 배치도 목록 |
| GET | `/api/seats/events/{eventId}` | - | 이벤트별 좌석 목록 |
| POST | `/api/seats/reserve` | JWT + VWR | 좌석 예약 |
| GET | `/api/seats/reservation/{id}` | JWT | 좌석 예약 상세 |
| POST | `/api/reservations` | JWT | 일반 예약 생성 |
| GET | `/api/reservations/my` | JWT | 내 예약 목록 |
| GET | `/api/reservations/{id}` | JWT | 예약 상세 |
| POST | `/api/reservations/{id}/cancel` | JWT | 예약 취소 |
| GET | `/api/tickets/my` | JWT | 내 티켓 목록 |
| POST | `/api/memberships/subscribe` | JWT | 멤버십 가입 |
| GET | `/api/memberships/my` | JWT | 내 멤버십 목록 |
| GET | `/api/memberships/artist/{id}` | JWT | 아티스트별 멤버십 |
| GET | `/api/transfers` | JWT | 양도 목록 |
| POST | `/api/transfers/create` | JWT | 양도 등록 |
| GET | `/api/transfers/{id}` | JWT | 양도 상세 |
| POST | `/api/transfers/{id}/cancel` | JWT | 양도 취소 |

#### payment-service

| 메서드 | 경로 | 인증 | 설명 |
|--------|------|------|------|
| POST | `/api/payments/prepare` | JWT | 결제 준비 |
| POST | `/api/payments/confirm` | JWT | 결제 확인 (Toss) |
| POST | `/api/payments/process` | JWT | 즉시 결제 |
| GET | `/api/payments/order/{orderId}` | JWT | 주문별 결제 조회 |
| POST | `/api/payments/{paymentKey}/cancel` | JWT | 결제 취소/환불 |
| GET | `/api/payments/user/me` | JWT | 내 결제 내역 |

#### queue-service

| 메서드 | 경로 | 인증 | 설명 |
|--------|------|------|------|
| POST | `/api/queue/check/{eventId}` | JWT | 대기열 진입/상태 확인 |
| GET | `/api/queue/status/{eventId}` | JWT | 대기열 상태 조회 |
| POST | `/api/queue/heartbeat/{eventId}` | JWT | 하트비트 (활성 유지) |
| POST | `/api/queue/leave/{eventId}` | JWT | 대기열 이탈 |
| GET | `/api/queue/admin/{eventId}` | Admin | 대기열 관리 현황 |
| POST | `/api/queue/admin/clear/{eventId}` | Admin | 대기열 초기화 |

#### catalog-service

| 메서드 | 경로 | 인증 | 설명 |
|--------|------|------|------|
| GET | `/api/events` | - | 이벤트 목록 (공개) |
| GET | `/api/events/{id}` | - | 이벤트 상세 (공개) |
| GET | `/api/artists` | - | 아티스트 목록 |
| GET | `/api/artists/{id}` | - | 아티스트 상세 |
| POST | `/api/admin/events` | Admin | 이벤트 생성 |
| PUT | `/api/admin/events/{id}` | Admin | 이벤트 수정 |
| DELETE | `/api/admin/events/{id}` | Admin | 이벤트 삭제 |
| POST | `/api/admin/events/{id}/cancel` | Admin | 이벤트 취소 |
| POST | `/api/image/upload` | Admin | 이미지 업로드 (S3) |

### 7.3 요청/응답 패턴

**표준 응답 형태:**

```json
// 성공 (서비스마다 약간 다름)
{
  "message": "Reservation created",
  "reservation": { ... }
}

// stats-service
{
  "success": true,
  "data": { ... }
}

// 에러
{
  "error": "Seat already reserved: A1-1"
}
```

**인증 패턴:**
- 게이트웨이에서 JWT를 검증하고 `X-User-Id`, `X-User-Email`, `X-User-Role` 헤더를 주입한다.
- 각 서비스의 `JwtTokenParser`는 이 헤더를 읽어 `AuthUser` 레코드를 생성한다.
- Admin 확인은 `X-User-Role: admin` 헤더로 수행한다.

### 7.4 Idempotency Key 지원

좌석 예약과 일반 예약 모두 `idempotencyKey` 필드를 지원한다. 동일한 키가 이미 존재하면 기존 예약을 반환한다.

```java
// services-spring/ticket-service/src/main/java/guru/urr/ticketservice/domain/reservation/service/ReservationService.java (라인 62-68)
if (request.idempotencyKey() != null && !request.idempotencyKey().isBlank()) {
    List<Map<String, Object>> existing = jdbcTemplate.queryForList(
        "SELECT ... FROM reservations WHERE idempotency_key = ?",
        request.idempotencyKey());
    if (!existing.isEmpty()) {
        return Map.of("message", "Seat reserved temporarily", "reservation", existing.getFirst());
    }
}
```

---

## 8. 에러 처리

### 8.1 Circuit Breaker (Resilience4j)

ticket-service, payment-service, queue-service, community-service, catalog-service에서 서비스 간 통신에 Circuit Breaker를 적용한다.

**공통 설정:**

```yaml
# 참조: services-spring/ticket-service/src/main/resources/application.yml (라인 87-105)
resilience4j:
  circuitbreaker:
    instances:
      internalService:
        sliding-window-size: 10           # 최근 10건 기준
        failure-rate-threshold: 50        # 50% 실패 시 OPEN
        wait-duration-in-open-state: 10s  # 10초 후 HALF_OPEN
        permitted-number-of-calls-in-half-open-state: 3
        slow-call-duration-threshold: 3s  # 3초 초과 시 slow call
        slow-call-rate-threshold: 80      # 80% slow call 시 OPEN
  retry:
    instances:
      internalService:
        max-attempts: 3                   # 최대 3회 재시도
        wait-duration: 500ms              # 초기 대기 500ms
        exponential-backoff-multiplier: 2 # 지수적 백오프
        retry-exceptions:
          - ResourceAccessException
          - ConnectException
```

**queue-service 추가 Circuit Breaker:**

```yaml
# 참조: services-spring/queue-service/src/main/resources/application.yml (라인 74-80)
redisQueue:
  sliding-window-size: 10
  failure-rate-threshold: 50
  wait-duration-in-open-state: 30s    # Redis 장애 시 30초 대기
  record-exceptions:
    - RedisConnectionFailureException
```

### 8.2 Fallback 패턴

**payment-service -> ticket-service 호출 실패 시:**

```java
// services-spring/payment-service/src/main/java/guru/urr/paymentservice/client/TicketInternalClient.java (라인 96-99)

// confirmReservation 실패: Kafka 이벤트로 eventual consistency 보장
private void confirmReservationFallback(UUID reservationId, String paymentMethod, Throwable t) {
    log.error("Circuit breaker: confirmReservation failed ...");
    // Don't throw -- Kafka event will handle eventual consistency
}

// validateReservation 실패: 502 BAD_GATEWAY 즉시 반환 (fail-fast)
private Map<String, Object> validateReservationFallback(UUID reservationId, String userId, Throwable t) {
    throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Ticket service unavailable");
}
```

**이중 경로(Dual-Path) 패턴:**

```java
// services-spring/payment-service/src/main/java/guru/urr/paymentservice/service/PaymentService.java (라인 354-376)

// 1차: 동기적 내부 API 호출 (primary path)
try {
    ticketInternalClient.confirmReservation(reservationId, paymentMethod);
} catch (Exception e) {
    log.warn("Synchronous confirmation failed, falling back to Kafka");
}

// 2차: Kafka 이벤트 발행 (secondary: stats, notifications, eventual consistency fallback)
paymentEventProducer.publish(new PaymentConfirmedEvent(...));
```

### 8.3 Global Exception Handler

모든 서비스에 `@RestControllerAdvice` 기반 전역 예외 처리기가 있다:

```java
// services-spring/ticket-service/src/main/java/guru/urr/ticketservice/shared/exception/GlobalExceptionHandler.java (라인 1-34)

@ExceptionHandler(ResponseStatusException.class)
-> { "error": "reason" } with matching HTTP status

@ExceptionHandler(MethodArgumentNotValidException.class)
-> { "error": "validation message" } with 400

@ExceptionHandler(Exception.class)
-> { "error": "Internal server error" } with 500 (로그 기록)
```

### 8.4 Rate Limiting 실패 시 동작

```java
// services-spring/gateway-service/src/main/java/guru/urr/gatewayservice/filter/RateLimitFilter.java (라인 93-98)
} catch (Exception e) {
    log.warn("Rate limit check failed (fail-open), allowing request: {}", e.getMessage());
    filterChain.doFilter(request, response);  // Redis 장애 시 요청 허용
}
```

Redis 장애 시 **fail-open** 정책을 적용하여 서비스 가용성을 우선시한다.

---

## 9. 좋은 점과 미흡한 점

### 좋은 점

#### 1. 좌석 동시성 처리가 우수하다

Redis Lua 스크립트를 통한 원자적 분산 락 + DB 낙관적 잠금(version 컬럼) + fencing token의 3중 보호 메커니즘은 티켓팅 서비스의 핵심 요구사항인 좌석 이중 예약 방지를 체계적으로 해결한다. 특히 fencing token을 통해 락 만료 후 결제가 진행되는 edge case까지 처리한다.

#### 2. 이벤트 멱등성 보장이 철저하다

`processed_events` 테이블과 `idempotency_key` 필드를 통해 Kafka 이벤트 중복 처리와 예약 중복 생성을 체계적으로 방지한다. Kafka consumer에서 sagaId 기반 deduplication과 DB 레벨의 `DuplicateKeyException` 처리를 병행한다.

#### 3. 이중 경로(Dual-Path) 패턴으로 안정성을 확보했다

payment-service에서 ticket-service로의 확인 요청이 동기적 REST 호출(primary)과 Kafka 이벤트(secondary)를 병행한다. 동기 호출이 실패해도 Kafka를 통한 eventual consistency가 보장된다.

#### 4. 게이트웨이 보안 아키텍처가 잘 설계되었다

- JWT 검증을 게이트웨이에서 중앙 집중화하여 downstream 서비스가 JWT_SECRET을 알 필요가 없다.
- 외부 `X-User-*` 헤더 스푸핑을 자동 스트리핑으로 방지한다.
- VWR Entry Token으로 좌석 예약 엔드포인트에 대한 추가 보안 계층을 제공한다.
- Redis Lua 기반 Rate Limiting이 카테고리별로 세분화되어 있다.

#### 5. Observability 기반이 잘 갖춰져 있다

- 모든 서비스에 Micrometer + Prometheus 메트릭 엔드포인트가 있다.
- Zipkin 분산 추적이 전체 서비스에 걸쳐 설정되어 있다.
- 구조화된 로그 패턴에 traceId/spanId가 포함되어 있다.
- ticket-service에 비즈니스 메트릭(예약 생성/확인/취소/만료, 결제 처리 등)이 Micrometer Counter/Timer로 수집된다.

#### 6. DB 분리 원칙을 잘 지키고 있다

서비스별 독립 DB를 사용하며, Flyway로 스키마를 관리한다. catalog-service의 ticket_db 공유도 Flyway를 비활성화하여 읽기 전용 접근임을 명확히 한다.

#### 7. 대기열 시스템이 현실적으로 설계되었다

Redis ZSET 기반의 대기열은 position 추적이 O(log N)으로 효율적이고, 배치 입장(admission script), stale 사용자 정리, 처리량 기반 대기시간 추정, 동적 폴링 간격 등 실무적인 기능이 구현되어 있다.

---

### 미흡한 점

#### 1. catalog-service와 ticket-service의 DB 공유는 MSA 원칙 위반이다

catalog-service가 ticket_db에 직접 접근하여 events 테이블을 읽는다. 이는 "Database per Service" 원칙에 어긋나며, 스키마 변경 시 두 서비스가 동시에 영향받는다.

**개선 방향:**
- catalog-service용 별도 DB(catalog_db)를 생성하고, ticket-service에서 이벤트 변경 시 Kafka 이벤트로 동기화한다.
- 또는 CQRS 패턴을 적용하여 catalog-service가 읽기 전용 프로젝션을 자체 DB에 유지한다.

#### 2. JPA Entity 대신 JdbcTemplate + Map<String, Object>를 남용한다

대부분의 서비스 로직이 `JdbcTemplate.queryForList()`로 `Map<String, Object>`를 반환한다. 타입 안전성이 없고, 컬럼명 오타가 컴파일 타임에 잡히지 않으며, 코드 가독성이 떨어진다. auth-service만 JPA Entity를 제대로 사용한다.

**개선 방향:**
- 최소한 핵심 도메인 엔티티(Reservation, Seat, Payment 등)는 JPA Entity로 정의한다.
- 복잡한 조회 쿼리는 JdbcTemplate을 유지하되, RowMapper를 사용하여 DTO로 매핑한다.

#### 3. 서비스 간 공유 코드가 복제되어 있다

`JwtTokenParser`, `AuthUser`, `GlobalExceptionHandler`, `InternalTokenValidator` 등이 각 서비스에 거의 동일한 코드로 복사되어 있다. 변경 시 모든 서비스를 동시에 수정해야 한다.

**개선 방향:**
- 공유 라이브러리 모듈(`urr-common`)을 만들어 Gradle multi-project 또는 내부 Maven repository로 관리한다.

#### 4. Kafka Consumer가 Map<String, Object>로 duck-typing을 한다

Kafka Consumer가 `JsonDeserializer`로 `LinkedHashMap`을 받아서 `type` 필드나 `reason` 필드 존재 여부로 이벤트 유형을 판단한다. 이벤트 스키마 변경에 취약하고, 디버깅이 어렵다.

**개선 방향:**
- 이벤트 클래스를 공유 모듈로 추출하고, type header 기반 역직렬화를 사용한다.
- 또는 Schema Registry(Avro/Protobuf)를 도입하여 이벤트 스키마를 중앙 관리한다.

#### 5. 테스트 인프라가 부족하다

- 통합 테스트용 Testcontainers 설정이 보이지 않는다.
- payment-service와 stats-service에 integration test 태그 분리가 없다.
- Kafka consumer/producer에 대한 테스트가 미흡하다.

**개선 방향:**
- Testcontainers를 활용한 통합 테스트 환경을 구축한다.
- Kafka 테스트에 `spring-kafka-test`의 `EmbeddedKafkaBroker`를 활용한다.

#### 6. 금액 처리가 INTEGER 타입이다

모든 금액 필드가 `INTEGER`로 정의되어 있다. 현재 원화(KRW) 기준으로 문제없지만, 향후 외화 결제나 소수점 금액이 필요한 경우 대응하기 어렵다.

**개선 방향:** 현재 KRW 전용이라면 INTEGER가 적절하나, 필드 단위(원/전)를 명시적으로 문서화해야 한다.

#### 7. 결제 조정(Reconciliation) 메커니즘이 불완전하다

`PaymentReconciliationScheduler`가 존재하지만, payment-service와 ticket-service 간의 상태 불일치를 탐지하고 자동 복구하는 로직이 한정적이다. Kafka 메시지 유실 시 수동 개입이 필요할 수 있다.

**개선 방향:**
- Dead Letter Queue(DLQ)를 Kafka consumer에 설정한다.
- 주기적 batch reconciliation job으로 양쪽 DB 상태를 대조한다.
- 불일치 발견 시 알림 발송 및 자동/반자동 보정 로직을 추가한다.

#### 8. 프로덕션 Redis 설정이 제한적이다

gateway-service와 queue-service에 prod 프로파일로 Redis Cluster 설정이 있으나, ticket-service에는 Redis Cluster 설정이 없다. 좌석 락이 Redis에 의존하므로 고가용성 구성이 필요하다.

**개선 방향:**
- ticket-service에도 prod 프로파일에 Redis Cluster 또는 Sentinel 설정을 추가한다.
- Redis 장애 시 좌석 예약의 graceful degradation 전략을 정의한다.

#### 9. API 버전 관리 전략이 명확하지 않다

게이트웨이에서 `/api/v1/`을 `/api/`로 변환하는 방식으로 버전 처리를 하지만, 실제 v2 도입 시의 전략이 불분명하다. 현재는 단일 버전(v1)만 존재한다.

#### 10. AWS 배포 시 추가 고려사항

| 항목 | 현재 상태 | 권장 사항 |
|------|-----------|-----------|
| DB | 각 서비스별 PostgreSQL 컨테이너 | Amazon RDS for PostgreSQL (Multi-AZ) |
| Redis | 단일 Redis 인스턴스 | Amazon ElastiCache for Redis (Cluster Mode) |
| Kafka | 단일 KRaft 인스턴스 | Amazon MSK (이미 Terraform 모듈 존재) |
| SQS | 선택적 사용 | SQS FIFO 활성화 |
| DynamoDB | 선택적 VWR 카운터 | 활성화 권장 |
| Secret 관리 | 환경 변수 | AWS Secrets Manager / Parameter Store |
| Connection Pool | 기본값 | HikariCP 설정 튜닝 필요 (max-pool-size, minimum-idle) |
| Health Check | `/actuator/health` 노출 | ALB Target Group Health Check 연동 |

---

## 부록: 핵심 파일 경로 요약

```
services-spring/
  gateway-service/
    src/main/resources/application.yml                          # 라우팅, CORS, Rate Limit 설정
    src/main/java/.../filter/JwtAuthFilter.java                 # JWT 검증 + 헤더 주입
    src/main/java/.../filter/RateLimitFilter.java               # Redis Rate Limiting
    src/main/java/.../filter/VwrEntryTokenFilter.java           # VWR Token 검증
    src/main/resources/redis/rate_limit.lua                     # Rate Limit Lua 스크립트

  auth-service/
    src/main/java/.../service/AuthService.java                  # 인증 핵심 로직
    src/main/java/.../controller/AuthController.java            # Auth API 엔드포인트
    src/main/java/.../security/JwtService.java                  # JWT 발급/검증
    src/main/resources/db/migration/V1~V4                       # 스키마 마이그레이션

  ticket-service/
    src/main/java/.../reservation/service/ReservationService.java # 예약 핵심 로직
    src/main/java/.../seat/service/SeatLockService.java          # Redis 분산 락
    src/main/java/.../membership/service/MembershipService.java  # 멤버십 시스템
    src/main/java/.../transfer/service/TransferService.java      # 양도 시스템
    src/main/java/.../messaging/TicketEventProducer.java         # Kafka Producer
    src/main/java/.../messaging/PaymentEventConsumer.java        # Kafka Consumer
    src/main/java/.../scheduling/ReservationCleanupScheduler.java # 만료 예약 정리
    src/main/java/.../shared/config/KafkaConfig.java             # Kafka 토픽 정의
    src/main/java/.../shared/config/RedisConfig.java             # Redis Lua 스크립트 등록
    src/main/java/.../shared/metrics/BusinessMetrics.java        # 비즈니스 메트릭
    src/main/resources/redis/seat_lock_acquire.lua               # 좌석 락 Lua
    src/main/resources/db/migration/V1~V14                       # 스키마 마이그레이션

  payment-service/
    src/main/java/.../service/PaymentService.java                # 결제 핵심 로직
    src/main/java/.../client/TicketInternalClient.java           # ticket-service 호출
    src/main/java/.../messaging/PaymentEventProducer.java        # Kafka Producer

  stats-service/
    src/main/java/.../messaging/StatsEventConsumer.java          # Kafka Consumer (3 토픽)
    src/main/java/.../controller/StatsController.java            # 통계 API

  queue-service/
    src/main/java/.../service/QueueService.java                  # 대기열 핵심 로직
    src/main/java/.../service/AdmissionWorkerService.java        # 배치 입장 워커
    src/main/java/.../service/SqsPublisher.java                  # SQS FIFO 발행
    src/main/resources/redis/admission_control.lua               # 입장 제어 Lua

  catalog-service/
    src/main/java/.../admin/service/AdminService.java            # 관리자 기능
    src/main/java/.../event/controller/EventController.java      # 이벤트 조회 API

  community-service/
    src/main/java/.../controller/NewsController.java             # 뉴스 API
    src/main/java/.../controller/CommunityPostController.java    # 게시판 API

  docker-compose.databases.yml                                   # 로컬 인프라 구성
```
