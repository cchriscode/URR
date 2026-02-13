# URR (우르르) 백엔드 마이크로서비스 아키텍처 분석

## 목차

1. [전체 아키텍처 개요](#1-전체-아키텍처-개요)
2. [기술 스택](#2-기술-스택)
3. [Gateway Service](#3-gateway-service)
4. [Auth Service](#4-auth-service)
5. [Ticket Service](#5-ticket-service)
6. [Payment Service](#6-payment-service)
7. [Queue Service (VWR)](#7-queue-service-vwr)
8. [Stats Service](#8-stats-service)
9. [Community Service](#9-community-service)
10. [서비스간 통신](#10-서비스간-통신)
11. [데이터베이스 아키텍처](#11-데이터베이스-아키텍처)
12. [관측성 (Observability)](#12-관측성-observability)

---

## 1. 전체 아키텍처 개요

URR 플랫폼은 7개의 독립 마이크로서비스로 구성된 이벤트 기반 티켓 예매 시스템이다. 각 서비스는 단일 책임 원칙에 따라 분리되어 있으며, 동기(REST) 및 비동기(Kafka) 통신을 조합하여 동작한다.

### 시스템 구성도

```
                           +------------------+
                           |   Client (Web)   |
                           |   localhost:3000  |
                           +--------+---------+
                                    |
                                    | HTTP
                                    v
                     +------------------------------+
                     |      Gateway Service         |
                     |      (port 3001)             |
                     |  - Rate Limiting (Redis)     |
                     |  - VWR Token 검증             |
                     |  - CORS                      |
                     +-----+----+----+----+----+----+
                           |    |    |    |    |
              +------------+    |    |    |    +------------+
              |                 |    |    |                 |
              v                 v    |    v                 v
    +---------+---+  +---------+-+  |  +-+----------+  +--+----------+
    |Auth Service |  |  Ticket   |  |  |  Payment   |  | Community   |
    | (port 3005) |  |  Service  |  |  |  Service   |  |  Service    |
    | - JWT 발급   |  |(port 3002)|  |  |(port 3003) |  | (port 3008) |
    | - OAuth     |  | - 이벤트   |  |  | - Toss 연동 |  | - 뉴스       |
    | - 사용자관리  |  | - 좌석     |  |  | - 결제 처리  |  +-------------+
    +-------------+  | - 예매     |  |  +------+-----+
                     | - 양도     |  |         |
                     | - 멤버십   |  |         | Kafka
                     +-----+-----+  |    +----+----+
                           |        |    |         |
                           | Kafka  |    v         v
                           |        |  +-+---------+-+
                           +------->+  | Stats Service|
                                    |  | (port 3004)  |
                                    |  +--------------+
                                    |
                                    v
                           +--------+---------+
                           |  Queue Service   |
                           |  (port 3007)     |
                           |  - 가상대기열(VWR)|
                           |  - Redis ZSET    |
                           +------------------+

    [인프라 계층]
    +----------+  +----------+  +----------+
    |PostgreSQL|  |  Redis/  |  |  Kafka   |
    |  16 (x5) |  | Dragonfly|  |  3.7.0   |
    +----------+  +----------+  +----------+
```

### 서비스 요약

| 서비스 | 포트 | 역할 | 데이터 저장소 |
|--------|------|------|-------------|
| Gateway Service | 3001 | API 라우팅, 레이트 리밋, VWR 토큰 검증 | Redis |
| Auth Service | 3005 | 인증/인가, JWT, Google OAuth | auth_db (PostgreSQL) |
| Ticket Service | 3002 | 이벤트, 좌석, 예매, 양도, 멤버십 | ticket_db (PostgreSQL), Redis |
| Payment Service | 3003 | 결제 처리 (Toss Payments 연동) | payment_db (PostgreSQL) |
| Stats Service | 3004 | 통계/분석, 이벤트 소비 | stats_db (PostgreSQL) |
| Queue Service | 3007 | 가상대기열 (VWR), 입장 제어 | Redis |
| Community Service | 3008 | 뉴스/커뮤니티 콘텐츠 | community_db (PostgreSQL) |

---

## 2. 기술 스택

### 공통 프레임워크

| 항목 | 버전/기술 | 비고 |
|------|----------|------|
| Runtime | Java 21 | LTS, Virtual Threads 지원 |
| Framework | Spring Boot 3.5.0 | Spring Cloud Gateway MVC |
| Database | PostgreSQL 16 | 서비스별 독립 인스턴스 |
| Cache/Queue Storage | Redis 7 / Dragonfly | 좌석 락, 대기열, 레이트 리밋 |
| Message Broker | Apache Kafka 3.7.0 | KRaft 모드 (Zookeeper 미사용) |
| Schema Migration | Flyway | 서비스별 독립 마이그레이션 |
| Tracing | Zipkin 3 | 분산 추적 |
| Container | Docker (postgres:16, redis:7, apache/kafka:3.7.0) | `docker-compose.databases.yml` |

### Kafka 구성

설정 파일: `services-spring/docker-compose.databases.yml` (lines 57-73)

```
KAFKA_PROCESS_ROLES: broker,controller   (KRaft 모드 - 단일 노드)
KAFKA_LISTENERS: PLAINTEXT://0.0.0.0:9092, CONTROLLER://0.0.0.0:9093
KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
```

---

## 3. Gateway Service

설정 파일: `services-spring/gateway-service/src/main/resources/application.yml` (109 lines)

Gateway Service는 모든 클라이언트 요청의 단일 진입점(Single Entry Point)으로, Spring Cloud Gateway MVC 기반의 리버스 프록시, 레이트 리밋, VWR 토큰 검증을 수행한다.

### 3.1 라우트 설정

설정 위치: `application.yml` lines 10-71

총 15개 라우트가 정의되어 있으며, 4개 백엔드 서비스로 분배된다.

| 라우트 ID | Path Pattern | 대상 서비스 | 기본 URL |
|-----------|-------------|------------|---------|
| auth | `/api/auth/**` | Auth Service | `http://localhost:3005` |
| payment | `/api/payments/**` | Payment Service | `http://localhost:3003` |
| stats | `/api/stats/**` | Stats Service | `http://localhost:3004` |
| events | `/api/events/**` | Ticket Service | `http://localhost:3002` |
| tickets | `/api/tickets/**` | Ticket Service | `http://localhost:3002` |
| seats | `/api/seats/**` | Ticket Service | `http://localhost:3002` |
| reservations | `/api/reservations/**` | Ticket Service | `http://localhost:3002` |
| admin | `/api/admin/**` | Ticket Service | `http://localhost:3002` |
| artists | `/api/artists/**` | Ticket Service | `http://localhost:3002` |
| memberships | `/api/memberships/**` | Ticket Service | `http://localhost:3002` |
| transfers | `/api/transfers/**` | Ticket Service | `http://localhost:3002` |
| image | `/api/image/**` | Ticket Service | `http://localhost:3002` |
| time | `/api/time/**` | Ticket Service | `http://localhost:3002` |
| queue | `/api/queue/**` | Queue Service | `http://localhost:3007` |
| news | `/api/news/**` | Community Service | `http://localhost:3008` |

모든 서비스 URL은 환경변수로 오버라이드 가능하다 (예: `${TICKET_SERVICE_URL:http://localhost:3002}`).

### 3.2 RateLimitFilter

파일: `services-spring/gateway-service/src/main/java/com/tiketi/gatewayservice/filter/RateLimitFilter.java` (183 lines)

#### 필터 동작 방식

- **실행 순서**: `@Order(0)` -- 가장 먼저 실행
- **상속**: `OncePerRequestFilter` (요청당 1회 실행 보장)
- **시간 윈도우**: 60초 (line 32: `WINDOW_MS = 60_000L`)
- **실패 정책**: **Fail-closed** -- Redis 장애 시 요청 거부 (lines 103-107)

#### 카테고리별 제한

| 카테고리 | RPM 기본값 | 매칭 경로 | 환경변수 |
|----------|-----------|----------|---------|
| AUTH | 20 | `/api/auth/**` | `RATE_LIMIT_AUTH_RPM` |
| QUEUE | 60 | `/api/queue/**` | `RATE_LIMIT_QUEUE_RPM` |
| BOOKING | 10 | `/api/seats/reserve`, `/api/reservations`, `/api/reservations/*/cancel` | `RATE_LIMIT_BOOKING_RPM` |
| GENERAL | 100 | 그 외 모든 경로 | `RATE_LIMIT_GENERAL_RPM` |

설정 위치: `application.yml` lines 104-108

카테고리 분류 로직: `resolveCategory()` (lines 142-157)
- BOOKING 카테고리는 쓰기 작업(reserve, create, cancel)만 포함
- 좌석/예매 조회는 GENERAL 카테고리로 분류

#### 클라이언트 식별 (lines 112-125)

```
resolveClientId(request):
  1. Authorization 헤더에서 JWT subject 추출 → "user:{userId}" 반환
  2. JWT 없으면 request.getRemoteAddr() → "ip:{addr}" 반환
  (X-Forwarded-For 헤더는 의도적으로 무시 - 스푸핑 방지)
```

#### Redis Lua Script

파일: `services-spring/gateway-service/src/main/resources/redis/rate_limit.lua` (15 lines)

Sliding Window 알고리즘을 ZSET으로 구현한다.
- KEY: `rate:{category}:{clientId}`
- 동작: 윈도우 외 항목 제거(ZREMRANGEBYSCORE) -> 현재 수 확인(ZCARD) -> 제한 초과 시 0 반환 / 미만이면 ZADD 후 1 반환

#### 응답 형식 (429 Too Many Requests)

```json
{"error": "Rate limit exceeded", "retryAfter": 60}
```

### 3.3 VwrEntryTokenFilter

파일: `services-spring/gateway-service/src/main/java/com/tiketi/gatewayservice/filter/VwrEntryTokenFilter.java` (115 lines)

#### 필터 동작 방식

- **실행 순서**: `@Order(1)` -- RateLimitFilter 다음에 실행
- **대상 메서드**: POST, PUT, PATCH만 (line 33: `PROTECTED_METHODS = Set.of("POST", "PUT", "PATCH")`)
- **대상 경로**: `/api/seats/**`, `/api/reservations**` (line 94-96)
- **토큰 헤더**: `x-queue-entry-token` (line 30)

#### 검증 흐름

```
요청 수신
  |
  +-- GET 요청? --> 필터 건너뜀 (shouldNotFilter)
  |
  +-- POST/PUT/PATCH
       |
       +-- 경로가 /api/seats/** 또는 /api/reservations**?
       |    |
       |    +-- No --> 필터 건너뜀
       |    |
       |    +-- Yes
       |         |
       |         +-- CloudFront bypass 확인 (X-CloudFront-Verified 헤더)
       |         |    |
       |         |    +-- 일치 --> 통과 (lines 62-69)
       |         |
       |         +-- x-queue-entry-token 헤더 검증
       |              |
       |              +-- 없음 --> 403 Forbidden
       |              +-- JWT 파싱 실패 --> 403 Forbidden
       |              +-- 유효 --> 통과
```

CloudFront bypass는 CDN 에지에서 Lambda@Edge가 이미 토큰을 검증한 경우에 사용된다. `MessageDigest.isEqual()`로 타이밍 공격을 방지한다 (line 64).

#### 거부 응답 (403 Forbidden)

```json
{"error": "Queue entry token required", "redirectTo": "/queue"}
```

### 3.4 CORS 설정

파일: `services-spring/gateway-service/src/main/java/com/tiketi/gatewayservice/config/CorsConfig.java` (33 lines)

| 항목 | 값 | 설정 위치 |
|------|---|----------|
| Allowed Origins | `${CORS_ALLOWED_ORIGINS:http://localhost:3000}` | `application.yml` line 102 |
| Allowed Methods | GET, POST, PUT, DELETE, PATCH, OPTIONS | CorsConfig.java |
| Allowed Headers | Authorization, Content-Type, x-queue-entry-token | CorsConfig.java |

### 3.5 Redis 설정

파일: `services-spring/gateway-service/src/main/java/com/tiketi/gatewayservice/config/RedisConfig.java` (26 lines)

- 호스트: `${REDIS_HOST:localhost}` (application.yml line 6)
- 포트: `${REDIS_PORT:6379}` (application.yml line 7)

---

## 4. Auth Service

설정 파일: `services-spring/auth-service/src/main/resources/application.yml` (46 lines)

Auth Service는 사용자 등록, 로그인, JWT 토큰 관리, Google OAuth 연동을 담당한다. JPA + Flyway로 스키마를 관리하며 `validate` 모드로 운영한다 (line 10).

### 4.1 도메인 모델

#### UserEntity

파일: `services-spring/auth-service/src/main/java/com/tiketi/authservice/domain/UserEntity.java` (63 lines)

테이블: `users`

| 필드 | 타입 | 제약조건 | 비고 |
|------|------|---------|------|
| id | UUID | PK, auto-generated | `@GeneratedValue(strategy = AUTO)` |
| email | String | UNIQUE, NOT NULL | 로그인 식별자 |
| password_hash | String | | BCrypt 12라운드 해시 |
| name | String | NOT NULL | |
| phone | String | | 선택 |
| google_id | String | | Google OAuth 연동용 |
| role | UserRole (enum) | DEFAULT 'user' | user / admin |
| created_at | Timestamp | | 자동 생성 |
| updated_at | Timestamp | | 자동 갱신 |

#### UserRole

파일: `services-spring/auth-service/src/main/java/com/tiketi/authservice/domain/UserRole.java`

```java
enum UserRole { user, admin }
```

### 4.2 JWT 토큰 관리

파일: `services-spring/auth-service/src/main/java/com/tiketi/authservice/security/JwtService.java` (94 lines)

#### 토큰 명세

| 토큰 유형 | 만료 시간 | Claims | 설정 위치 |
|-----------|----------|--------|----------|
| Access Token | 30분 (1800초) | userId, email, role, type="access" | JwtService.java line 28 |
| Refresh Token | 7일 (604800초) | userId, type="refresh" | JwtService.java line 45 |

#### 서명 방식

- 알고리즘: HMAC-SHA256
- 키 길이: 최소 32바이트
- 설정: `${JWT_SECRET}` 환경변수 (application.yml line 43)

#### JwtProperties

파일: `services-spring/auth-service/src/main/java/com/tiketi/authservice/config/JwtProperties.java`

```
app.security.jwt.secret              = ${JWT_SECRET}
app.security.jwt.expiration-seconds  = ${JWT_EXPIRATION_SECONDS:1800}
app.security.jwt.refresh-token-expiration-seconds = ${JWT_REFRESH_EXPIRATION_SECONDS:604800}
```

설정 위치: `application.yml` lines 40-45

### 4.3 보안 설정

파일: `services-spring/auth-service/src/main/java/com/tiketi/authservice/config/SecurityConfig.java`

#### 접근 제어

| 경로 | 정책 | 비고 |
|------|------|------|
| `/api/auth/register` | permitAll | 회원가입 |
| `/api/auth/login` | permitAll | 로그인 |
| `/api/auth/verify-token` | permitAll | 토큰 검증 |
| `/api/auth/google` | permitAll | Google OAuth |
| `/api/auth/refresh` | permitAll | 토큰 갱신 |
| `/internal/**` | permitAll | 내부 서비스 통신 |
| 그 외 전체 | authenticated | JWT 인증 필요 |

#### 필터 체인

```
요청 --> JwtAuthenticationFilter --> InternalApiAuthFilter --> SecurityFilterChain
```

- `JwtAuthenticationFilter`: Bearer 토큰 검증, SecurityContext 설정
- `InternalApiAuthFilter`: `INTERNAL_API_TOKEN`으로 서비스간 인증

### 4.4 AuthService (핵심 비즈니스 로직)

파일: `services-spring/auth-service/src/main/java/com/tiketi/authservice/service/AuthService.java` (263 lines)

#### register (lines 50-67)

```
요청(email, password, name, phone)
  |
  +-- 이메일 중복 확인 (findByEmail)
  |    +-- 존재 --> "Email already exists" 예외
  |
  +-- BCrypt 12라운드 해싱 (passwordEncoder.encode)
  +-- UserEntity 생성 및 저장
  +-- Access Token + Refresh Token 발급
  +-- AuthResponse 반환
```

#### login (lines 69-92)

```
요청(email, password)
  |
  +-- 사용자 조회 (findByEmail)
  |    +-- 미존재 --> "Invalid email or password"
  |
  +-- 비밀번호 해시가 비어있는지 확인 (OAuth 사용자 방어)
  +-- passwordEncoder.matches() 검증
  |    +-- 불일치 --> "Invalid email or password"
  |
  +-- Access Token + Refresh Token 발급
```

에러 메시지가 이메일 존재 여부를 노출하지 않도록 동일한 메시지를 사용한다 (line 72, 86).

#### refreshToken (lines 94-114)

```
요청(refreshToken)
  |
  +-- jwtService.validateRefreshToken() 검증
  |    +-- 실패 --> "Invalid or expired refresh token"
  |
  +-- Claims에서 userId 추출
  +-- 사용자 조회 (findById)
  +-- 새 Access Token + Refresh Token 쌍 발급
```

#### googleLogin (lines 157-230)

```
요청(credential: Google ID token)
  |
  +-- Google tokeninfo API 호출 (https://oauth2.googleapis.com/tokeninfo)
  +-- audience(aud) 검증 (GOOGLE_CLIENT_ID 일치 확인)
  +-- sub(googleId), email, name, picture 추출
  |
  +-- 이메일로 기존 사용자 조회
  |    +-- 미존재 --> 새 계정 생성 (passwordHash = "OAUTH_USER_NO_PASSWORD")
  |    +-- 존재, googleId 미연동 --> googleId 연동
  |    +-- 존재, 이미 연동 --> 기존 계정 사용
  |
  +-- JWT 토큰 쌍 발급
```

### 4.5 API 엔드포인트

파일: `services-spring/auth-service/src/main/java/com/tiketi/authservice/controller/AuthController.java` (66 lines)

| Method | Path | 설명 | 인증 |
|--------|------|------|------|
| POST | `/api/auth/register` | 회원가입 | 불필요 |
| POST | `/api/auth/login` | 로그인 | 불필요 |
| POST | `/api/auth/verify-token` | 토큰 검증 | 불필요 |
| POST | `/api/auth/refresh` | 토큰 갱신 | 불필요 |
| POST | `/api/auth/google` | Google OAuth 로그인 | 불필요 |
| GET | `/api/auth/me` | 내 프로필 조회 | 필요 |

### 4.6 데이터베이스

- DB명: `auth_db`
- 포트: 5438
- Flyway 마이그레이션: V1(users 테이블), V2(google_id 컬럼), V3(admin seed 데이터)
- 연결 설정: `application.yml` lines 4-7

---

## 5. Ticket Service

설정 파일: `services-spring/ticket-service/src/main/resources/application.yml` (72 lines)

Ticket Service는 플랫폼의 핵심 서비스로, 이벤트 관리, 좌석 예매, 양도, 멤버십 등 주요 비즈니스 도메인을 담당한다. DDD(Domain-Driven Design) 스타일의 패키지 구조를 채택하고 있다.

### 5.1 패키지 구조

```
com.tiketi.ticketservice/
  +-- domain/
  |     +-- admin/         AdminController, AdminService, ImageUploadService, MaintenanceService
  |     +-- artist/        ArtistController, ArtistService, SpotifyService
  |     +-- event/         EventController (36 lines), EventReadService
  |     +-- membership/    MembershipController, MembershipService
  |     +-- reservation/   ReservationController (62 lines), ReservationService
  |     +-- seat/          SeatController (60 lines), SeatGeneratorService, SeatLockService
  |     +-- ticket/        TicketController
  |     +-- transfer/      TransferController, TransferService
  |
  +-- shared/
  |     +-- security/      JwtTokenParser, AuthUser, InternalTokenValidator
  |     +-- client/        PaymentInternalClient
  |
  +-- messaging/
  |     +-- TicketEventProducer
  |     +-- PaymentEventConsumer (207 lines)
  |
  +-- scheduling/
        +-- ReservationCleanupScheduler
        +-- PaymentReconciliationScheduler
```

### 5.2 API 엔드포인트

#### Reservation (예매)

파일: `services-spring/ticket-service/src/main/java/com/tiketi/ticketservice/domain/reservation/controller/ReservationController.java` (62 lines)

| Method | Path | 설명 |
|--------|------|------|
| POST | `/api/reservations` | 예매 생성 |
| GET | `/api/reservations/my` | 내 예매 목록 |
| GET | `/api/reservations/{id}` | 예매 상세 조회 |
| POST | `/api/reservations/{id}/cancel` | 예매 취소 |

#### Seat (좌석)

파일: `services-spring/ticket-service/src/main/java/com/tiketi/ticketservice/domain/seat/controller/SeatController.java` (60 lines)

| Method | Path | 설명 |
|--------|------|------|
| GET | `/api/seats/layouts` | 좌석 배치도 목록 |
| GET | `/api/seats/events/{eventId}` | 이벤트별 좌석 현황 |
| POST | `/api/seats/reserve` | 좌석 잠금(예약) |
| GET | `/api/seats/reservation/{id}` | 예약된 좌석 조회 |

#### Admin (관리자)

파일: `services-spring/ticket-service/src/main/java/com/tiketi/ticketservice/domain/admin/controller/AdminController.java`

12개 엔드포인트:

| Method | Path | 설명 |
|--------|------|------|
| GET | `/api/admin/dashboard` | 대시보드 통계 |
| GET | `/api/admin/seatLayouts` | 좌석 배치도 관리 |
| GET | `/api/admin/events` | 이벤트 목록 |
| POST | `/api/admin/events` | 이벤트 생성 |
| PUT | `/api/admin/events/{id}` | 이벤트 수정 |
| DELETE | `/api/admin/events/{id}` | 이벤트 삭제 |
| POST | `/api/admin/events/{id}/seats` | 좌석 생성 |
| GET | `/api/admin/tickets` | 티켓 목록 |
| PUT | `/api/admin/tickets/{id}` | 티켓 수정 |
| DELETE | `/api/admin/tickets/{id}` | 티켓 삭제 |
| GET | `/api/admin/reservations` | 예매 목록 |
| PUT | `/api/admin/reservations/{id}/status` | 예매 상태 변경 |

#### Event (이벤트)

파일: `services-spring/ticket-service/src/main/java/com/tiketi/ticketservice/domain/event/controller/EventController.java` (36 lines)

| Method | Path | 설명 |
|--------|------|------|
| GET | `/api/events` | 이벤트 목록 |
| GET | `/api/events/{id}` | 이벤트 상세 |

### 5.3 좌석 잠금 (Seat Locking)

Redis Lua 스크립트를 사용한 분산 락 메커니즘으로, 동시 예매 시 좌석 충돌을 방지한다.

#### seat_lock_acquire.lua (36 lines)

```
KEY 구조: seat:{eventId}:{seatId}
VALUE 구조: HMSET (userId, status, fencingToken, ttl)

동작 흐름:
  1. 기존 락 상태 확인 (HGET status)
  2. HELD 또는 CONFIRMED 상태인 경우:
     a. 같은 사용자 → TTL 연장 (PEXPIRE), 기존 token 반환
     b. 다른 사용자 → 거부 (0 반환)
  3. 락 없음 → fencing token 증가 (INCR token_seq)
     → HMSET {userId, status=HELD, fencingToken, createdAt}
     → PEXPIRE 설정
     → 새 token 반환
```

- TOKEN_SEQ KEY: `token_seq` (KEYS[2]) -- 전역 시퀀스로 fencing token 생성
- Fencing Token: 분산 환경에서의 stale lock 방지

#### seat_lock_release.lua

```
동작: userId + fencing token 검증 후 DEL
```

#### SeatLockService

파일: `services-spring/ticket-service/src/main/java/com/tiketi/ticketservice/domain/seat/service/SeatLockService.java` (113 lines)

| 메서드 | 역할 |
|--------|------|
| `acquireLock(eventId, seatId, userId)` | 좌석 잠금 획득, fencing token 반환 |
| `releaseLock(eventId, seatId, userId, token)` | 좌석 잠금 해제 |
| `verifyForPayment(eventId, seatId, userId)` | 결제 전 잠금 상태 검증 |
| `cleanupLock(eventId, seatId)` | 좌석 잠금 강제 해제 (만료/관리자) |

### 5.4 Kafka 메시징

#### Producer

파일: `services-spring/ticket-service/src/main/java/com/tiketi/ticketservice/messaging/TicketEventProducer.java`

| 토픽 | 이벤트 | 발행 시점 |
|------|--------|----------|
| `reservation-events` | ReservationCreatedEvent | 예매 생성 |
| `reservation-events` | ReservationConfirmedEvent | 예매 확정 |
| `reservation-events` | ReservationCancelledEvent | 예매 취소 |
| `membership-events` | MembershipActivatedEvent | 멤버십 활성화 |
| `transfer-events` | TransferCompletedEvent | 양도 완료 |

Kafka Producer 설정 (`application.yml` lines 6-11):
- `key-serializer`: StringSerializer
- `value-serializer`: JsonSerializer
- `acks`: all (모든 복제본 확인)
- `spring.json.add.type.headers`: false

#### Consumer

파일: `services-spring/ticket-service/src/main/java/com/tiketi/ticketservice/messaging/PaymentEventConsumer.java` (207 lines)

| 토픽 | 이벤트 | 처리 내용 |
|------|--------|----------|
| `payment-events` | PAYMENT_CONFIRMED | 예매/멤버십/양도 확정 처리 |
| `payment-events` | PAYMENT_REFUNDED | 환불 처리 (예매 상태 복원) |

Consumer 설정 (`application.yml` lines 12-20):
- `group-id`: ticket-service-group
- `auto-offset-reset`: earliest
- 신뢰 패키지: `com.tiketi.paymentservice.messaging.event`, `com.tiketi.ticketservice.messaging.event`

### 5.5 스케줄러

| 스케줄러 | 간격 | 역할 | 설정 |
|----------|------|------|------|
| ReservationCleanupScheduler | 30초 | 만료된 예매 정리 (미결제 예매 취소) | `reservation.cleanup.interval-ms` (line 60) |
| PaymentReconciliationScheduler | 5분 | 결제-예매 상태 대조 및 불일치 보정 | `reservation.reconciliation.interval-ms` (line 62) |
| Event Status Scheduler | 1분 | 이벤트 상태 자동 업데이트 (오픈/마감) | `event.status.interval-ms` (line 65) |

설정 위치: `application.yml` lines 58-65

### 5.6 데이터베이스

- DB명: `ticket_db`
- 포트: 5434
- Flyway 마이그레이션: V1 ~ V11

| 버전 | 내용 |
|------|------|
| V1 | events, seats, reservations, reservation_items, ticket_types, seat_layouts |
| V3 | artists 테이블 |
| V4 | memberships 테이블 |
| V5 | transfers 테이블 |
| V8 | seats 동시성 제어 (낙관적 잠금) |
| V9 | standing events (입석 지원) |
| V11 | 인덱스 추가 (성능 최적화) |

추가 설정:
- Redis: `${REDIS_HOST:localhost}:${REDIS_PORT:6379}` (lines 33-35)
- Auth Service URL: `${AUTH_SERVICE_URL:http://localhost:3005}` (line 67)
- AWS S3: 이미지 업로드용 (lines 69-72)

---

## 6. Payment Service

설정 파일: `services-spring/payment-service/src/main/resources/application.yml` (45 lines)

Payment Service는 Toss Payments 연동을 통한 결제 처리를 담당한다. JdbcTemplate 기반의 직접 SQL 실행 방식을 사용하며, Kafka를 통해 결제 이벤트를 발행한다.

### 6.1 API 엔드포인트

파일: `services-spring/payment-service/src/main/java/com/tiketi/paymentservice/controller/PaymentController.java` (90 lines)

| Method | Path | 설명 | 인증 |
|--------|------|------|------|
| POST | `/api/payments/prepare` | 결제 준비 (orderId 생성) | 필요 |
| POST | `/api/payments/confirm` | 결제 승인 | 필요 |
| POST | `/api/payments/{paymentKey}/cancel` | 결제 취소 (환불) | 필요 |
| GET | `/api/payments/order/{orderId}` | 주문 ID로 결제 조회 | 필요 |
| GET | `/api/payments/user/me` | 내 결제 내역 | 필요 |

#### Internal API

파일: `services-spring/payment-service/src/main/java/com/tiketi/paymentservice/controller/InternalPaymentController.java`

서비스간 결제 상태 조회를 위한 내부 엔드포인트 (INTERNAL_API_TOKEN 인증).

### 6.2 결제 흐름

파일: `services-spring/payment-service/src/main/java/com/tiketi/paymentservice/service/PaymentService.java` (370 lines)

#### prepare (lines 44-114)

```
요청(reservationId/referenceId, amount, paymentType)
  |
  +-- paymentType 분기:
  |    +-- "reservation" --> ticketInternalClient.validateReservation()
  |    +-- "transfer"    --> ticketInternalClient.validateTransfer()
  |    +-- "membership"  --> ticketInternalClient.validateMembership()
  |
  +-- 서버측 금액 검증 (validatedAmount != request.amount() --> 거부)
  |
  +-- 기존 결제 확인:
  |    +-- 이미 confirmed --> "Payment already confirmed" 예외
  |    +-- pending 상태 --> 기존 orderId 반환 (멱등성)
  |
  +-- orderId 생성: "ORD_{timestamp}_{uuid8자}" (line 104)
  +-- payments 테이블 INSERT (status = 'pending')
  +-- 반환: { orderId, amount, clientKey }
```

주문 ID 형식: `ORD_1707836400000_A1B2C3D4`

#### confirm (lines 116-169)

```
요청(orderId, paymentKey, amount)
  |
  +-- SELECT ... FOR UPDATE (비관적 잠금, line 122)
  +-- 사용자 ID 검증 (line 130)
  +-- 금액 일치 검증 (line 133)
  +-- 이미 confirmed 상태 확인 (line 136)
  |
  +-- reservation 타입이면 추가 검증:
  |    ticketInternalClient.validateReservation()
  |
  +-- payments 테이블 UPDATE:
  |    payment_key, method='toss', status='confirmed'
  |    toss_status='DONE', toss_approved_at
  |
  +-- completeByType() --> Kafka 이벤트 발행
```

`FOR UPDATE` 잠금(line 122)으로 동시 결제 승인을 방지한다.

#### cancel (lines 190-224)

```
요청(paymentKey, cancelReason)
  |
  +-- SELECT ... FOR UPDATE
  +-- 소유자 검증
  +-- confirmed 상태만 취소 가능
  |
  +-- status='refunded', refund_amount=amount 업데이트
  +-- PaymentRefundedEvent Kafka 발행
```

### 6.3 Kafka 메시징

| 토픽 | 이벤트 | 데이터 |
|------|--------|--------|
| `payment-events` | PaymentConfirmedEvent | paymentId, orderId, userId, reservationId, referenceId, paymentType, amount, paymentMethod, timestamp |
| `payment-events` | PaymentRefundedEvent | paymentId, orderId, userId, reservationId, referenceId, paymentType, amount, reason, timestamp |

Producer 설정 (`application.yml` lines 6-11):
- `acks`: all
- `retries`: 3 (line 10)

### 6.4 서비스간 통신

파일: `services-spring/payment-service/src/main/java/com/tiketi/paymentservice/client/TicketInternalClient.java`

Payment Service --> Ticket Service (동기 REST):
- `validateReservation(reservationId, userId)`: 예매 상태/금액 검증
- `validateTransfer(transferId, userId)`: 양도 상태/금액 검증
- `validateMembership(membershipId, userId)`: 멤버십 상태/금액 검증

인증: `Authorization: Bearer {INTERNAL_API_TOKEN}`

### 6.5 데이터베이스

- DB명: `payment_db`
- 포트: 5435
- Flyway 마이그레이션: V1 ~ V3

payments 테이블 주요 컬럼:

| 컬럼 | 설명 |
|------|------|
| id | UUID, PK |
| order_id | 주문 ID (ORD_...) |
| payment_key | Toss 결제 키 |
| user_id | 결제 사용자 |
| reservation_id | 예매 ID (nullable) |
| reference_id | 범용 참조 ID (양도/멤버십) |
| payment_type | reservation / transfer / membership |
| amount | 결제 금액 |
| method | toss 등 |
| status | pending / confirmed / refunded |
| toss_status | Toss API 상태 |
| toss_approved_at | Toss 승인 시각 |
| refund_amount | 환불 금액 |
| refund_reason | 환불 사유 |
| refunded_at | 환불 시각 |

---

## 7. Queue Service (VWR)

설정 파일: `services-spring/queue-service/src/main/resources/application.yml` (48 lines)

Queue Service는 가상대기열(Virtual Waiting Room, VWR)을 구현하여 대규모 트래픽을 제어한다. Redis ZSET을 활용한 2-tier 구조로 대기열과 활성 사용자를 분리 관리한다.

### 7.1 아키텍처 개요

```
             사용자 요청
                  |
                  v
          +-------+-------+
          |  check(eventId)|
          +-------+-------+
                  |
      +-----------+-----------+
      |           |           |
      v           v           v
  이미 대기열?  이미 active?  신규 사용자
  (queue ZSET) (active ZSET)    |
      |           |           |
  heartbeat   토큰 반환    threshold 확인
  (TTL 갱신)   (JWT)      (현재 active 수)
                           |
                     +-----+-----+
                     |           |
                     v           v
               active < 1000  active >= 1000
               (즉시 입장)    (대기열 추가)
```

### 7.2 Redis 데이터 구조

| KEY 패턴 | 타입 | Score 의미 | 용도 |
|----------|------|-----------|------|
| `active:{eventId}` | ZSET | 만료 시간 (epoch ms) | 현재 입장 사용자 |
| `queue:{eventId}` | ZSET | 입장 시간 (epoch ms) | 대기열 |

### 7.3 QueueService

파일: `services-spring/queue-service/src/main/java/com/tiketi/queueservice/service/QueueService.java` (417 lines)

#### check (lines 63-92)

핵심 진입점: 사용자의 대기열 상태를 확인하고 적절한 응답을 반환한다.

```
check(eventId, userId):
  1. 이벤트 정보 조회 (ticketInternalClient.getEventQueueInfo)
  2. 이미 대기열에 있는가? --> heartbeat 갱신 + 대기 응답
  3. 이미 active인가? --> TTL 갱신 + 입장 토큰 반환
  4. currentUsers >= threshold 또는 queueSize > 0?
     --> 대기열 추가 + 대기 응답
  5. 그 외 --> active 추가 + 즉시 입장 토큰 반환
```

#### Entry Token 생성

```
JWT 구조:
  - sub: eventId (문자열)
  - claim "userId": userId
  - 서명: HMAC-SHA256 (QUEUE_ENTRY_TOKEN_SECRET)
  - TTL: 600초 (10분)
```

설정 위치: `application.yml` lines 36-38

#### 동적 폴링 간격

대기열 크기에 따라 클라이언트 폴링 간격을 동적으로 조절한다.

| 대기열 크기 | 폴링 간격 |
|------------|----------|
| 1,000 이하 | 1초 |
| 5,000 이하 | 5초 |
| 10,000 이하 | 10초 |
| 100,000 이하 | 30초 |
| 100,000 초과 | 60초 |

#### 대기 시간 추정

```
estimatedWaitSeconds = position / throughputPerSecond
```

처리량 측정: 최근 60초간(THROUGHPUT_WINDOW_MS) admission 횟수를 추적하여 초당 처리량을 계산한다 (lines 41-43).

#### Fallback 메커니즘

Redis 장애 시 in-memory ConcurrentHashMap으로 대체 동작한다 (lines 37-38):

```java
private final ConcurrentMap<String, LinkedHashSet<String>> fallbackQueue;
private final ConcurrentMap<String, Set<String>> fallbackActive;
```

### 7.4 AdmissionWorkerService

파일: `services-spring/queue-service/src/main/java/com/tiketi/queueservice/service/AdmissionWorkerService.java` (184 lines)

#### admitUsers (1초 간격)

```
admitUsers():
  1. 활성 이벤트 목록 조회
  2. 이벤트별 분산 락 획득: admission:lock:{eventId}
  3. Lua script (admission_control.lua) 실행:
     a. ZREMRANGEBYSCORE active:{eventId} -inf {now} --> 만료 사용자 제거
     b. ZPOPMIN queue:{eventId} {batchSize} --> 대기열에서 최대 100명 추출
     c. ZADD active:{eventId} {expireAt} {userId} --> active로 이동
  4. 분산 락 해제
```

설정:
- 간격: `${QUEUE_ADMISSION_INTERVAL_MS:1000}` (application.yml line 32)
- 배치 크기: `${QUEUE_ADMISSION_BATCH_SIZE:100}` (application.yml line 33)

#### cleanupStaleUsers (30초 간격)

```
cleanupStaleUsers():
  1. stale_cleanup.lua 실행
  2. heartbeat 없는 사용자를 active set에서 제거
  3. 빈 슬롯만큼 추가 입장 허용
```

설정: `${QUEUE_STALE_CLEANUP_INTERVAL_MS:30000}` (application.yml line 35)

### 7.5 Redis Lua Scripts

#### admission_control.lua (46 lines)

```lua
-- 핵심 로직 (원자적 실행)
ZREMRANGEBYSCORE active:{eventId} -inf {now}    -- 만료 제거
ZPOPMIN queue:{eventId} {batchSize}             -- 대기열에서 추출
ZADD active:{eventId} {expireAt} {userId}       -- active로 이동
```

#### stale_cleanup.lua (21 lines)

```lua
-- 비활성 사용자 정리
-- heartbeat가 갱신되지 않은 사용자를 active set에서 제거
```

### 7.6 API 엔드포인트

파일: `services-spring/queue-service/src/main/java/com/tiketi/queueservice/controller/QueueController.java` (80 lines)

| Method | Path | 설명 |
|--------|------|------|
| POST | `/api/queue/check/{eventId}` | 대기열 진입/상태 확인 |
| GET | `/api/queue/status/{eventId}` | 대기열 현황 조회 |
| POST | `/api/queue/heartbeat/{eventId}` | 활성 상태 유지 (heartbeat) |
| POST | `/api/queue/leave/{eventId}` | 대기열/active 이탈 |

### 7.7 설정 요약

| 설정 | 기본값 | 환경변수 | 설정 위치 |
|------|--------|---------|----------|
| Threshold | 1,000 | `QUEUE_THRESHOLD` | QueueService 생성자 line 49 |
| Active TTL | 600초 | `QUEUE_ACTIVE_TTL_SECONDS` | QueueService 생성자 line 50 |
| Entry Token TTL | 600초 | `QUEUE_ENTRY_TOKEN_TTL_SECONDS` | application.yml line 38 |
| Admission 간격 | 1,000ms | `QUEUE_ADMISSION_INTERVAL_MS` | application.yml line 32 |
| Batch 크기 | 100 | `QUEUE_ADMISSION_BATCH_SIZE` | application.yml line 33 |
| Stale Cleanup 간격 | 30,000ms | `QUEUE_STALE_CLEANUP_INTERVAL_MS` | application.yml line 35 |
| SQS 연동 | 비활성 | `SQS_ENABLED` | application.yml line 47 |

---

## 8. Stats Service

설정 파일: `services-spring/stats-service/src/main/resources/application.yml` (47 lines)

Stats Service는 Kafka 이벤트를 소비하여 통계 데이터를 집계하는 읽기 전용(write-behind) 서비스이다.

### 8.1 이벤트 소비

파일: `services-spring/stats-service/src/main/java/com/tiketi/statsservice/messaging/StatsEventConsumer.java` (192 lines)

| 토픽 | 이벤트 | 처리 |
|------|--------|------|
| `payment-events` | PAYMENT_CONFIRMED | 매출 기록 |
| `payment-events` | PAYMENT_REFUNDED | 환불 기록 |
| `reservation-events` | RESERVATION_CREATED | 예매 생성 집계 |
| `reservation-events` | RESERVATION_CONFIRMED | 예매 확정 집계 |
| `reservation-events` | RESERVATION_CANCELLED | 예매 취소 집계 |
| `membership-events` | MEMBERSHIP_ACTIVATED | 멤버십 활성화 집계 |

#### 중복 방지

```
processed_events 테이블:
  eventKey = {eventType}_{id}_{timestamp}

소비 전 eventKey 존재 여부 확인 --> 이미 처리된 이벤트면 건너뜀
```

Consumer 설정 (`application.yml` lines 6-14):
- `group-id`: stats-service-group
- `auto-offset-reset`: earliest
- 신뢰 패키지: payment/ticket 서비스 이벤트 패키지

### 8.2 통계 기록

파일: `services-spring/stats-service/src/main/java/com/tiketi/statsservice/service/StatsWriteService.java`

| 메서드 | 트리거 이벤트 |
|--------|-------------|
| `recordReservationCreated()` | RESERVATION_CREATED |
| `recordReservationConfirmed()` | RESERVATION_CONFIRMED |
| `recordReservationCancelled()` | RESERVATION_CANCELLED |
| `recordPaymentRefunded()` | PAYMENT_REFUNDED |
| `recordTransferCompleted()` | TRANSFER_COMPLETED |
| `recordMembershipActivated()` | MEMBERSHIP_ACTIVATED |

### 8.3 데이터베이스

- DB명: `stats_db`
- 포트: 5436
- Flyway 마이그레이션: V1 ~ V2

| 테이블 | 용도 |
|--------|------|
| `daily_stats` | 일별 집계 (예매 수, 매출, 환불 등) |
| `event_stats` | 이벤트별 집계 |
| `processed_events` | 중복 이벤트 방지 (V2에서 추가) |

---

## 9. Community Service

설정 파일: `services-spring/community-service/src/main/resources/application.yml` (36 lines)

Community Service는 뉴스/커뮤니티 콘텐츠를 관리하는 경량 서비스이다. JPA를 사용하지 않고 Spring JDBC(JdbcTemplate)를 직접 사용한다.

### 9.1 API 엔드포인트

파일: `services-spring/community-service/src/main/java/.../controller/NewsController.java` (76 lines)

| Method | Path | 설명 |
|--------|------|------|
| GET | `/api/news` | 뉴스 목록 조회 |
| GET | `/api/news/{id}` | 뉴스 상세 조회 |
| POST | `/api/news` | 뉴스 작성 |
| PUT | `/api/news/{id}` | 뉴스 수정 |
| DELETE | `/api/news/{id}` | 뉴스 삭제 |

### 9.2 데이터베이스

- DB명: `community_db`
- 포트: 5437
- Flyway 마이그레이션: V1 ~ V2

news 테이블:

| 컬럼 | 설명 |
|------|------|
| id | PK |
| title | 제목 |
| content | 본문 |
| author | 작성자 |
| views | 조회수 |
| is_pinned | 상단 고정 여부 |

### 9.3 서비스간 통신

```yaml
# application.yml lines 33-35
internal:
  ticket-service-url: ${TICKET_SERVICE_URL:http://localhost:3002}
  api-token: ${INTERNAL_API_TOKEN}
```

Ticket Service와의 내부 통신을 위한 설정이 포함되어 있다.

---

## 10. 서비스간 통신

### 10.1 통신 패턴 개요

```
[동기 통신 (REST)]

Client --> Gateway --> Auth Service
                  --> Ticket Service
                  --> Payment Service
                  --> Queue Service
                  --> Stats Service
                  --> Community Service

Ticket Service <--internal--> Auth Service    (사용자 정보 조회)
Payment Service --internal--> Ticket Service  (예매/양도/멤버십 검증)
Queue Service --internal--> Ticket Service    (이벤트 정보 조회)
Community Service --internal--> Ticket Service


[비동기 통신 (Kafka)]

Payment Service  --[payment-events]--> Ticket Service
                                   --> Stats Service

Ticket Service   --[reservation-events]--> Stats Service
Ticket Service   --[membership-events]--> Stats Service
Ticket Service   --[transfer-events]--> Stats Service
```

### 10.2 Kafka 토픽 상세

| 토픽 | Producer | Consumer(s) | 이벤트 |
|------|----------|-------------|--------|
| `payment-events` | Payment Service | Ticket Service, Stats Service | PAYMENT_CONFIRMED, PAYMENT_REFUNDED |
| `reservation-events` | Ticket Service | Stats Service | RESERVATION_CREATED, RESERVATION_CONFIRMED, RESERVATION_CANCELLED |
| `membership-events` | Ticket Service | Stats Service | MEMBERSHIP_ACTIVATED |
| `transfer-events` | Ticket Service | Stats Service | TRANSFER_COMPLETED |

### 10.3 내부 API 인증

서비스간 REST 통신은 공유 비밀 토큰(`INTERNAL_API_TOKEN`)으로 인증한다.

```
요청 헤더: Authorization: Bearer {INTERNAL_API_TOKEN}
검증 클래스: InternalTokenValidator
```

각 서비스의 InternalTokenValidator 위치:

| 서비스 | 파일 |
|--------|------|
| Auth Service | `auth-service/.../security/InternalTokenValidator.java` |
| Ticket Service | `ticket-service/.../shared/security/InternalTokenValidator.java` |
| Payment Service | `payment-service/.../security/InternalTokenValidator.java` |

### 10.4 핵심 흐름: 예매-결제 사이클

```
[1] 사용자 --> Gateway --> Queue Service (대기열 진입)
                          |
                          v
                     대기열 통과 --> entry token 발급 (JWT)

[2] 사용자 --> Gateway [VwrEntryTokenFilter: 토큰 검증]
                  |
                  v
              Ticket Service (POST /api/seats/reserve)
                  |
                  v
              Redis Lua: seat_lock_acquire
              (좌석 잠금, fencing token 반환)

[3] 사용자 --> Gateway --> Ticket Service (POST /api/reservations)
                  |
                  v
              예매 레코드 생성 (status: pending)
              Kafka --> reservation-events (RESERVATION_CREATED)

[4] 사용자 --> Gateway --> Payment Service (POST /api/payments/prepare)
                  |
                  v
              Payment --> Ticket Service (internal: 예매 검증)
              orderId 생성, payments 레코드 (status: pending)

[5] 사용자 --> Toss Payments --> 결제 승인

[6] 사용자 --> Gateway --> Payment Service (POST /api/payments/confirm)
                  |
                  v
              FOR UPDATE 잠금 --> 검증 --> status='confirmed'
              Kafka --> payment-events (PAYMENT_CONFIRMED)

[7] Ticket Service (Consumer: payment-events)
              |
              v
          예매 확정 (status: confirmed)
          좌석 잠금 해제
          Kafka --> reservation-events (RESERVATION_CONFIRMED)

[8] Stats Service (Consumer: payment-events + reservation-events)
              |
              v
          통계 집계 (daily_stats, event_stats)
```

---

## 11. 데이터베이스 아키텍처

### 11.1 인프라 구성

설정 파일: `services-spring/docker-compose.databases.yml` (74 lines)

| 서비스 | 이미지 | DB명 | 외부 포트 | 내부 포트 |
|--------|--------|------|----------|----------|
| auth-db | postgres:16 | auth_db | 5438 | 5432 |
| ticket-db | postgres:16 | ticket_db | 5434 | 5432 |
| payment-db | postgres:16 | payment_db | 5435 | 5432 |
| stats-db | postgres:16 | stats_db | 5436 | 5432 |
| community-db | postgres:16 | community_db | 5437 | 5432 |
| redis | redis:7 | - | 6379 | 6379 |
| kafka | apache/kafka:3.7.0 | - | 9092 | 9092 |
| zipkin | openzipkin/zipkin:3 | - | 9411 | 9411 |

공통 인증정보: `tiketi_user` / `tiketi_password`

### 11.2 Database-per-Service 패턴

각 서비스는 독립된 PostgreSQL 인스턴스를 사용한다. 서비스 간 직접적인 DB 접근은 없으며, 데이터 조회가 필요한 경우 REST API 또는 Kafka 이벤트를 통해 통신한다.

```
Auth Service ------> auth_db (port 5438)
                     V1: users
                     V2: google_id
                     V3: admin seed

Ticket Service ----> ticket_db (port 5434)
                     V1: events, seats, reservations,
                         reservation_items, ticket_types, seat_layouts
                     V3: artists
                     V4: memberships
                     V5: transfers
                     V8: seats concurrency
                     V9: standing events
                     V11: indexes

Payment Service ---> payment_db (port 5435)
                     V1: payments
                     V3: indexes

Stats Service -----> stats_db (port 5436)
                     V1: daily_stats, event_stats
                     V2: processed_events

Community Service -> community_db (port 5437)
                     V1: news
                     V2: (스키마 업데이트)
```

### 11.3 Redis 사용 현황

| 서비스 | 용도 | KEY 패턴 | 자료 구조 |
|--------|------|----------|----------|
| Gateway Service | 레이트 리밋 | `rate:{category}:{clientId}` | ZSET (Sliding Window) |
| Ticket Service | 좌석 잠금 | `seat:{eventId}:{seatId}` | HASH |
| Ticket Service | Fencing Token | `token_seq` | STRING (INCR) |
| Queue Service | 활성 사용자 | `active:{eventId}` | ZSET |
| Queue Service | 대기열 | `queue:{eventId}` | ZSET |
| Queue Service | 분산 락 | `admission:lock:{eventId}` | STRING |

---

## 12. 관측성 (Observability)

### 12.1 분산 추적 (Distributed Tracing)

모든 서비스는 Zipkin 기반 분산 추적을 지원한다. 각 서비스의 `application.yml`에 동일한 설정이 포함되어 있다.

```yaml
management:
  tracing:
    sampling:
      probability: ${TRACING_SAMPLING_PROBABILITY:1.0}  # 100% 샘플링 (개발 환경)
  zipkin:
    tracing:
      endpoint: ${ZIPKIN_ENDPOINT:http://localhost:9411/api/v2/spans}
```

### 12.2 로깅 패턴

모든 서비스에서 traceId/spanId를 포함하는 통일된 로깅 패턴을 사용한다.

```yaml
logging:
  pattern:
    level: "%5p [${spring.application.name:},%X{traceId:-},%X{spanId:-}]"
```

출력 예시: `INFO [gateway-service,abc123def456,789xyz...]`

### 12.3 헬스체크 및 메트릭

모든 서비스에서 Actuator 엔드포인트를 노출한다.

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus
```

| 엔드포인트 | 용도 |
|-----------|------|
| `/actuator/health` | 서비스 상태 확인 (K8s liveness/readiness probe) |
| `/actuator/info` | 서비스 정보 |
| `/actuator/prometheus` | Prometheus 메트릭 수집 |

Management 포트는 `${MANAGEMENT_PORT:}` 환경변수로 메인 포트와 분리 가능하다.

---

## 부록: 환경변수 참조

### 필수 환경변수

| 변수 | 사용 서비스 | 용도 |
|------|-----------|------|
| `JWT_SECRET` | Auth, Gateway | JWT 서명 키 (32바이트 이상) |
| `QUEUE_ENTRY_TOKEN_SECRET` | Gateway, Queue | VWR 토큰 서명 키 (32바이트 이상) |
| `INTERNAL_API_TOKEN` | 전체 | 서비스간 인증 토큰 |

### 선택 환경변수 (기본값 있음)

| 변수 | 기본값 | 서비스 |
|------|--------|--------|
| `CORS_ALLOWED_ORIGINS` | `http://localhost:3000` | Gateway |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Ticket, Payment, Stats |
| `REDIS_HOST` | `localhost` | Gateway, Ticket, Queue |
| `REDIS_PORT` | `6379` | Gateway, Ticket, Queue |
| `GOOGLE_CLIENT_ID` | (없음) | Auth |
| `TOSS_CLIENT_KEY` | `test_ck_dummy` | Payment |
| `TRACING_SAMPLING_PROBABILITY` | `1.0` | 전체 |
| `QUEUE_THRESHOLD` | `1000` | Queue |
| `QUEUE_ACTIVE_TTL_SECONDS` | `600` | Queue |
| `SQS_ENABLED` | `false` | Queue |
