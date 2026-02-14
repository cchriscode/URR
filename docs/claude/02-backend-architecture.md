# 백엔드 MSA 구조 분석

> URR 티켓팅 플랫폼의 Spring Boot 기반 마이크로서비스 아키텍처 분석 문서.
> 기준 디렉토리: `C:\Users\USER\project-ticketing-copy\services-spring\`

---

## 1. 서비스 개요

플랫폼은 8개의 독립적인 Spring Boot 서비스로 구성되어 있다. 각 서비스는 단일 책임 원칙에 따라 분리되어 있으며, 개별 포트에서 실행된다.

| 서비스 | 포트 | 데이터베이스 | 주요 역할 | Application 클래스 경로 |
|--------|------|-------------|-----------|------------------------|
| Gateway | 3001 | - (Redis만 사용) | API 라우팅, 인증 필터, Rate Limiting | `gateway-service/.../GatewayServiceApplication.java` |
| Auth | 3005 | auth_db (5438) | 사용자 인증, JWT 발급, OAuth | `auth-service/.../AuthServiceApplication.java` |
| Ticket | 3002 | ticket_db (5434) | 예매, 좌석, 양도, 멤버십 | `ticket-service/.../TicketServiceApplication.java` |
| Payment | 3003 | payment_db (5435) | 결제 처리, 환불 | `payment-service/.../PaymentServiceApplication.java` |
| Stats | 3004 | stats_db (5436) | 통계 집계, 이벤트 소비 | `stats-service/.../StatsServiceApplication.java` |
| Queue | 3007 | - (Redis만 사용) | 대기열 관리, 입장 토큰 | `queue-service/.../QueueServiceApplication.java` |
| Community | 3008 | community_db (5437) | 커뮤니티 게시글, 댓글, 뉴스 | `community-service/.../CommunityServiceApplication.java` |
| Catalog | 3009 | ticket_db (5434) 공유 | 이벤트/아티스트 카탈로그, 관리자 기능 | `catalog-service/.../CatalogServiceApplication.java` |

**출처:**
- Gateway 포트: `gateway-service/src/main/resources/application.yml:78`
- Auth 포트: `auth-service/src/main/resources/application.yml:20`
- Ticket 포트: `ticket-service/src/main/resources/application.yml:38`
- Payment 포트: `payment-service/src/main/resources/application.yml:26`
- Stats 포트: `stats-service/src/main/resources/application.yml:28`
- Queue 포트: `queue-service/src/main/resources/application.yml:10`
- Community 포트: `community-service/src/main/resources/application.yml:13`
- Catalog 포트: `catalog-service/src/main/resources/application.yml:12`

### 참고: Catalog-service와 Ticket-service의 DB 공유

Catalog-service는 독립 DB 없이 `ticket_db`를 공유한다. Flyway는 비활성화(`enabled: false`)되어 있으며, 스키마 관리는 ticket-service의 마이그레이션에 의존한다.

```yaml
# catalog-service/src/main/resources/application.yml:4-9
spring:
  datasource:
    url: ${TICKET_DB_URL:jdbc:postgresql://localhost:5434/ticket_db}
  flyway:
    enabled: false
```

**출처:** `catalog-service/src/main/resources/application.yml:4-9`

---

## 2. 게이트웨이 라우팅

Gateway 서비스는 Spring Cloud Gateway MVC를 사용하여 모든 외부 요청을 적절한 서비스로 라우팅한다. 총 16개의 라우트가 정의되어 있다.

| ID | Path Predicate | 대상 서비스 | 대상 URL (기본값) |
|----|---------------|------------|-------------------|
| `auth` | `/api/v1/auth/**`, `/api/auth/**` | Auth Service | `http://localhost:3005` |
| `payment` | `/api/v1/payments/**`, `/api/payments/**` | Payment Service | `http://localhost:3003` |
| `stats` | `/api/v1/stats/**`, `/api/stats/**` | Stats Service | `http://localhost:3004` |
| `events` | `/api/v1/events/**`, `/api/events/**` | Catalog Service | `http://localhost:3009` |
| `tickets` | `/api/v1/tickets/**`, `/api/tickets/**` | Ticket Service | `http://localhost:3002` |
| `seats` | `/api/v1/seats/**`, `/api/seats/**` | Ticket Service | `http://localhost:3002` |
| `reservations` | `/api/v1/reservations/**`, `/api/reservations/**` | Ticket Service | `http://localhost:3002` |
| `queue` | `/api/v1/queue/**`, `/api/queue/**` | Queue Service | `http://localhost:3007` |
| `admin` | `/api/v1/admin/**`, `/api/admin/**` | Catalog Service | `http://localhost:3009` |
| `community` | `/api/v1/community/**`, `/api/community/**` | Community Service | `http://localhost:3008` |
| `news` | `/api/v1/news/**`, `/api/news/**` | Community Service | `http://localhost:3008` |
| `artists` | `/api/v1/artists/**`, `/api/artists/**` | Catalog Service | `http://localhost:3009` |
| `memberships` | `/api/v1/memberships/**`, `/api/memberships/**` | Ticket Service | `http://localhost:3002` |
| `transfers` | `/api/v1/transfers/**`, `/api/transfers/**` | Ticket Service | `http://localhost:3002` |
| `image` | `/api/v1/image/**`, `/api/image/**` | Catalog Service | `http://localhost:3009` |
| `time` | `/api/v1/time/**`, `/api/time/**` | Ticket Service | `http://localhost:3002` |

**출처:** `gateway-service/src/main/resources/application.yml:10-75`

### 라우트별 서비스 분포

- **Ticket Service (5개 라우트):** tickets, seats, reservations, memberships, transfers, time
- **Catalog Service (4개 라우트):** events, admin, artists, image
- **Community Service (2개 라우트):** community, news
- **Auth Service (1개 라우트):** auth
- **Payment Service (1개 라우트):** payment
- **Stats Service (1개 라우트):** stats
- **Queue Service (1개 라우트):** queue

### Rate Limiting 설정

Gateway에서 엔드포인트 유형별 Rate Limit을 적용한다.

```yaml
# gateway-service/src/main/resources/application.yml:115-119
rate-limit:
  auth-rpm: ${RATE_LIMIT_AUTH_RPM:60}
  queue-rpm: ${RATE_LIMIT_QUEUE_RPM:120}
  booking-rpm: ${RATE_LIMIT_BOOKING_RPM:30}
  general-rpm: ${RATE_LIMIT_GENERAL_RPM:3000}
```

**출처:** `gateway-service/src/main/resources/application.yml:115-119`

---

## 3. 서비스 간 통신 패턴

서비스 간 통신은 두 가지 패턴으로 구분된다: 동기 REST 호출과 비동기 Kafka 이벤트.

### 3.1 동기 통신 (REST Internal API)

서비스 간 동기 통신은 `/internal/` 접두사를 가진 REST API를 통해 이루어진다. 각 호출 서비스는 `TicketInternalClient` 클래스를 통해 대상 서비스의 Internal API를 호출한다.

#### Internal API 엔드포인트 목록

**Ticket Service (`/internal/`)**

| 엔드포인트 | 메서드 | 설명 | 호출 서비스 | 컨트롤러 |
|-----------|--------|------|-----------|----------|
| `/internal/reservations/{id}/validate` | GET | 예매 검증 | Payment | `InternalReservationController:41` |
| `/internal/reservations/{id}/confirm` | POST | 예매 확인 | - | `InternalReservationController:51` |
| `/internal/reservations/{id}/refund` | POST | 환불 처리 | - | `InternalReservationController:64` |
| `/internal/transfers/{id}/validate` | GET | 양도 검증 | Payment | `InternalReservationController:76` |
| `/internal/transfers/{id}/complete` | POST | 양도 완료 | - | `InternalReservationController:85` |
| `/internal/memberships/{id}/validate` | GET | 멤버십 검증 | Payment | `InternalReservationController:100` |
| `/internal/memberships/{id}/activate` | POST | 멤버십 활성화 | - | `InternalReservationController:110` |
| `/internal/memberships/award-points` | POST | 포인트 적립 | Community | `InternalMembershipController:26` |
| `/internal/seats/reserve` | POST | 좌석 예약(잠금) | - | `InternalSeatController:32` |
| `/internal/seats/generate/{eventId}/{layoutId}` | POST | 좌석 생성 | Catalog | `InternalSeatController:66` |
| `/internal/seats/count/{eventId}` | GET | 좌석 수 조회 | Catalog | `InternalSeatController:76` |
| `/internal/seats/{eventId}` | DELETE | 좌석 삭제 | Catalog | `InternalSeatController:85` |
| `/internal/reschedule-event-status` | POST | 이벤트 상태 재스케줄 | - | `InternalController:23` |

**출처:**
- `ticket-service/src/main/java/com/tiketi/ticketservice/internal/controller/InternalReservationController.java:41-118`
- `ticket-service/src/main/java/com/tiketi/ticketservice/internal/controller/InternalMembershipController.java:26-46`
- `ticket-service/src/main/java/com/tiketi/ticketservice/internal/controller/InternalSeatController.java:32-92`
- `ticket-service/src/main/java/com/tiketi/ticketservice/internal/controller/InternalController.java:23-28`

**Catalog Service (`/internal/`)**

| 엔드포인트 | 메서드 | 설명 | 호출 서비스 | 컨트롤러 |
|-----------|--------|------|-----------|----------|
| `/internal/events/{eventId}/queue-info` | GET | 이벤트 대기열 정보 | Queue | `InternalEventController:22` |

**출처:** `catalog-service/src/main/java/com/tiketi/catalogservice/internal/controller/InternalEventController.java:22-28`

**Auth Service (`/internal/`)**

| 엔드포인트 | 메서드 | 설명 | 호출 서비스 | 컨트롤러 |
|-----------|--------|------|-----------|----------|
| `/internal/users/{id}` | GET | 사용자 조회 | Ticket, Catalog | `InternalUserController:33` |
| `/internal/users/batch` | POST | 사용자 일괄 조회 | Ticket, Catalog | `InternalUserController:44` |

**출처:** `auth-service/src/main/java/com/tiketi/authservice/controller/InternalUserController.java:33-61`

**Payment Service (`/internal/`)**

| 엔드포인트 | 메서드 | 설명 | 호출 서비스 | 컨트롤러 |
|-----------|--------|------|-----------|----------|
| `/internal/payments/by-reservation/{reservationId}` | GET | 예매별 결제 조회 | Ticket | `InternalPaymentController:31` |

**출처:** `payment-service/src/main/java/com/tiketi/paymentservice/controller/InternalPaymentController.java:31-57`

#### Internal API 클라이언트 구현

각 서비스의 `TicketInternalClient`는 Spring의 `RestClient`를 사용하며, 연결 타임아웃 5초, 읽기 타임아웃 10초로 설정되어 있다.

```java
// community-service/.../shared/client/TicketInternalClient.java:27-30
var requestFactory = ClientHttpRequestFactories.get(ClientHttpRequestFactorySettings.DEFAULTS
        .withConnectTimeout(Duration.ofSeconds(5))
        .withReadTimeout(Duration.ofSeconds(10)));
this.restClient = RestClient.builder().baseUrl(ticketServiceUrl).requestFactory(requestFactory).build();
```

**Internal API 클라이언트 소재와 호출 대상:**

| 호출 서비스 | 클라이언트 위치 | 호출 대상 | 주요 메서드 |
|-----------|--------------|----------|-----------|
| Payment | `payment-service/.../client/TicketInternalClient.java` | Ticket Service | `validateReservation`, `validateTransfer`, `validateMembership` |
| Community | `community-service/.../shared/client/TicketInternalClient.java` | Ticket Service | `awardMembershipPoints` |
| Queue | `queue-service/.../shared/client/TicketInternalClient.java` | Catalog Service | `getEventQueueInfo` |
| Catalog | `catalog-service/.../shared/client/TicketInternalClient.java` | Ticket Service | `generateSeats`, `countSeats`, `deleteSeats` |

**출처:**
- `payment-service/src/main/java/com/tiketi/paymentservice/client/TicketInternalClient.java:1-84`
- `community-service/src/main/java/com/tiketi/communityservice/shared/client/TicketInternalClient.java:1-74`
- `queue-service/src/main/java/com/tiketi/queueservice/shared/client/TicketInternalClient.java:1-55`
- `catalog-service/src/main/java/com/tiketi/catalogservice/shared/client/TicketInternalClient.java:1-100`

#### Internal API 인증: `x-internal-token` 헤더 검증

서비스 간 Internal API는 공유 토큰(`INTERNAL_API_TOKEN` 환경변수)을 사용하여 인증한다. 모든 검증에는 **timing-safe comparison**을 사용하여 타이밍 공격을 방지한다.

**Ticket Service의 InternalTokenValidator:**

```java
// ticket-service/.../shared/security/InternalTokenValidator.java:19-33
public void requireValidToken(String authorization) {
    if (authorization == null || !authorization.startsWith("Bearer ")) {
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Internal token required");
    }
    String token = authorization.substring(7);
    if (!timingSafeEquals(internalToken, token)) {
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid internal token");
    }
}

private static boolean timingSafeEquals(String a, String b) {
    return MessageDigest.isEqual(
            a.getBytes(StandardCharsets.UTF_8),
            b.getBytes(StandardCharsets.UTF_8));
}
```

**출처:** `ticket-service/src/main/java/com/tiketi/ticketservice/shared/security/InternalTokenValidator.java:19-33`

**Auth Service의 InternalApiAuthFilter:**

Auth Service는 서블릿 필터(`OncePerRequestFilter`)를 사용하여 `/internal/` 경로의 모든 요청을 자동으로 검증한다. `x-internal-token` 헤더와 `Authorization: Bearer` 헤더 모두 지원한다.

```java
// auth-service/.../security/InternalApiAuthFilter.java:28-34
String token = request.getHeader("x-internal-token");
if (token == null || token.isBlank()) {
    String authHeader = request.getHeader("Authorization");
    if (authHeader != null && authHeader.startsWith("Bearer ")) {
        token = authHeader.substring(7);
    }
}
```

**출처:** `auth-service/src/main/java/com/tiketi/authservice/security/InternalApiAuthFilter.java:25-52`

필터 적용 조건: URI가 `/internal/`로 시작하는 요청에만 적용(`shouldNotFilter` 메서드, 라인 50-52).

---

### 3.2 비동기 통신 (Kafka)

서비스 간 비동기 통신은 Apache Kafka를 통해 이루어진다. 이벤트는 4개의 토픽으로 분류된다.

#### Kafka 토픽 목록

| 토픽 | Producer | Consumer | 설명 |
|------|----------|----------|------|
| `reservation-events` | Ticket Service | Stats Service | 예매 생성/확인/취소 이벤트 |
| `payment-events` | Payment Service | Ticket Service, Stats Service | 결제 확인/환불 이벤트 |
| `membership-events` | Ticket Service | Stats Service | 멤버십 활성화 이벤트 |
| `transfer-events` | Ticket Service | Stats Service | 양도 완료 이벤트 |

**출처:**
- `ticket-service/src/main/java/com/tiketi/ticketservice/messaging/TicketEventProducer.java:25-77`
- `payment-service/src/main/java/com/tiketi/paymentservice/messaging/PaymentEventProducer.java:14-42`
- `stats-service/src/main/java/com/tiketi/statsservice/messaging/StatsEventConsumer.java:25-134`
- `ticket-service/src/main/java/com/tiketi/ticketservice/messaging/PaymentEventConsumer.java:43-79`

#### 이벤트 타입 및 필드 정의

**`reservation-events` 토픽**

| 이벤트 타입 | Record 클래스 | 필드 |
|-----------|--------------|------|
| `RESERVATION_CREATED` | `ReservationCreatedEvent` | `type`, `reservationId` (UUID), `userId` (String), `eventId` (UUID), `totalAmount` (int), `timestamp` (Instant) |
| `RESERVATION_CONFIRMED` | `ReservationConfirmedEvent` | `type`, `reservationId` (UUID), `userId` (String), `eventId` (UUID), `totalAmount` (int), `paymentMethod` (String), `timestamp` (Instant) |
| `RESERVATION_CANCELLED` | `ReservationCancelledEvent` | `type`, `reservationId` (UUID), `userId` (String), `eventId` (UUID), `reason` (String), `timestamp` (Instant) |

**출처:**
- `ticket-service/src/main/java/com/tiketi/ticketservice/messaging/event/ReservationCreatedEvent.java:6-18`
- `ticket-service/src/main/java/com/tiketi/ticketservice/messaging/event/ReservationConfirmedEvent.java:6-20`
- `ticket-service/src/main/java/com/tiketi/ticketservice/messaging/event/ReservationCancelledEvent.java:6-18`

**`payment-events` 토픽**

| 이벤트 타입 | Record 클래스 | 필드 |
|-----------|--------------|------|
| `PAYMENT_CONFIRMED` | `PaymentConfirmedEvent` | `type`, `paymentId` (UUID), `orderId` (String), `userId` (String), `reservationId` (UUID), `referenceId` (UUID), `paymentType` (String), `amount` (int), `paymentMethod` (String), `timestamp` (Instant) |
| `PAYMENT_REFUNDED` | `PaymentRefundedEvent` | `type`, `paymentId` (UUID), `orderId` (String), `userId` (String), `reservationId` (UUID), `referenceId` (UUID), `paymentType` (String), `amount` (int), `reason` (String), `timestamp` (Instant) |

**출처:**
- `payment-service/src/main/java/com/tiketi/paymentservice/messaging/event/PaymentConfirmedEvent.java:6-24`
- `payment-service/src/main/java/com/tiketi/paymentservice/messaging/event/PaymentRefundedEvent.java:6-24`

**`membership-events` 토픽**

| 이벤트 타입 | Record 클래스 | 필드 |
|-----------|--------------|------|
| `MEMBERSHIP_ACTIVATED` | `MembershipActivatedEvent` | `type`, `membershipId` (UUID), `userId` (String), `artistId` (UUID), `timestamp` (Instant) |

**출처:** `ticket-service/src/main/java/com/tiketi/ticketservice/messaging/event/MembershipActivatedEvent.java:6-17`

**`transfer-events` 토픽**

| 이벤트 타입 | Record 클래스 | 필드 |
|-----------|--------------|------|
| `TRANSFER_COMPLETED` | `TransferCompletedEvent` | `type`, `transferId` (UUID), `reservationId` (UUID), `sellerId` (String), `buyerId` (String), `totalPrice` (int), `timestamp` (Instant) |

**출처:** `ticket-service/src/main/java/com/tiketi/ticketservice/messaging/event/TransferCompletedEvent.java:6-19`

#### 이벤트 흐름도

```
┌──────────────────────────────────────────────────────────────────────────┐
│                          Kafka Event Flow                                │
├──────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│  ┌─────────────┐    payment-events     ┌─────────────────┐              │
│  │   Payment    │─────────────────────>│  Ticket Service  │              │
│  │   Service    │                      │ (PaymentEvent    │              │
│  │              │                      │  Consumer)       │              │
│  └──────┬───────┘                      └────────┬─────────┘              │
│         │                                       │                        │
│         │ payment-events                        │ reservation-events     │
│         │                                       │ transfer-events        │
│         │                                       │ membership-events      │
│         ▼                                       ▼                        │
│  ┌──────────────────────────────────────────────────────┐               │
│  │                    Stats Service                      │               │
│  │               (StatsEventConsumer)                    │               │
│  │                                                       │               │
│  │  Listens to:                                          │               │
│  │  - payment-events    -> recordPaymentRefunded,        │               │
│  │                         recordTransferCompleted        │               │
│  │  - reservation-events -> recordReservationCreated,    │               │
│  │                          recordReservationConfirmed,   │               │
│  │                          recordReservationCancelled    │               │
│  │  - membership-events -> recordMembershipActivated     │               │
│  └───────────────────────────────────────────────────────┘               │
│                                                                          │
│  Flow Summary:                                                           │
│  1. Payment 완료 -> PaymentEventProducer -> "payment-events"             │
│  2. Ticket의 PaymentEventConsumer가 수신                                 │
│     -> 예매 확인/양도 완료/멤버십 활성화 처리                            │
│     -> TicketEventProducer가 후속 이벤트 발행                            │
│        ("reservation-events", "transfer-events", "membership-events")    │
│  3. Stats의 StatsEventConsumer가 모든 토픽을 수신하여 통계 집계          │
│                                                                          │
└──────────────────────────────────────────────────────────────────────────┘
```

#### 이벤트 중복 처리 (Deduplication)

Stats Service는 `processed_events` 테이블을 사용하여 이벤트 중복 소비를 방지한다. 이벤트 키는 `type:id:timestamp` 형식으로 구성된다.

```java
// stats-service/.../messaging/StatsEventConsumer.java:138-149
private String buildEventKey(Map<String, Object> event) {
    String type = str(event.get("type"));
    String timestamp = str(event.get("timestamp"));
    String id = str(event.get("reservationId"));
    if (id == null) id = str(event.get("paymentId"));
    if (id == null) id = str(event.get("membershipId"));
    if (id == null) id = str(event.get("transferId"));

    if (type != null && id != null && timestamp != null) {
        return type + ":" + id + ":" + timestamp;
    }
    return null;
}
```

**출처:** `stats-service/src/main/java/com/tiketi/statsservice/messaging/StatsEventConsumer.java:138-170`

#### Kafka 설정

Producer는 `acks=all`로 설정되어 모든 replica에 기록이 완료되어야 전송 성공으로 처리한다. Consumer는 `auto-offset-reset: earliest`로 설정되어 있다.

```yaml
# ticket-service/src/main/resources/application.yml:4-20
spring:
  kafka:
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
      acks: all
    consumer:
      group-id: ticket-service-group
      auto-offset-reset: earliest
```

**출처:**
- `ticket-service/src/main/resources/application.yml:4-20`
- `payment-service/src/main/resources/application.yml:4-12`
- `stats-service/src/main/resources/application.yml:4-14`

#### Backward Compatibility: Duck-typing 폴백

Consumer에서 이벤트의 `type` 필드가 없는 경우를 대비하여 duck-typing 기반 폴백 로직이 구현되어 있다. 예를 들어 `reason` 필드가 있으면 환불 이벤트로, `paymentMethod` 필드가 있으면 결제 확인 이벤트로 판단한다.

**출처:**
- `ticket-service/src/main/java/com/tiketi/ticketservice/messaging/PaymentEventConsumer.java:46-75`
- `stats-service/src/main/java/com/tiketi/statsservice/messaging/StatsEventConsumer.java:34-60`

---

## 4. Database per Service 패턴

각 서비스는 독립적인 PostgreSQL 인스턴스를 사용한다. 5개의 PostgreSQL 컨테이너가 docker-compose로 관리된다.

### 데이터베이스 인스턴스 목록

| 데이터베이스 | 호스트 포트 | 사용 서비스 | Docker 서비스명 |
|------------|-----------|-----------|----------------|
| `auth_db` | 5438 | Auth Service | `auth-db` |
| `ticket_db` | 5434 | Ticket Service, Catalog Service | `ticket-db` |
| `payment_db` | 5435 | Payment Service | `payment-db` |
| `stats_db` | 5436 | Stats Service | `stats-db` |
| `community_db` | 5437 | Community Service | `community-db` |

모든 PostgreSQL 인스턴스는 `postgres:16` 이미지를 사용하며, 기본 사용자는 `tiketi_user`이다.

**출처:** `docker-compose.databases.yml:1-45`

### 인프라 서비스

데이터베이스 외에 다음 인프라 컴포넌트가 docker-compose에 포함되어 있다.

| 서비스 | 이미지 | 포트 | 역할 |
|--------|-------|------|------|
| Redis | `redis:7` | 6379 | Gateway/Queue/Ticket 세션 및 캐시 |
| Zipkin | `openzipkin/zipkin:3` | 9411 | 분산 트레이싱 |
| Kafka | `apache/kafka:3.7.0` | 9092 | 이벤트 브로커 |

Kafka는 KRaft 모드(Zookeeper 없음)로 단일 노드 구성이다.

```yaml
# docker-compose.databases.yml:59-71
kafka:
  image: apache/kafka:3.7.0
  environment:
    KAFKA_NODE_ID: 1
    KAFKA_PROCESS_ROLES: broker,controller
    KAFKA_LISTENERS: PLAINTEXT://0.0.0.0:9092,CONTROLLER://0.0.0.0:9093
    KAFKA_CONTROLLER_QUORUM_VOTERS: 1@kafka:9093
```

**출처:** `docker-compose.databases.yml:58-73`

### Flyway 마이그레이션 전략

각 서비스는 Flyway를 사용하여 데이터베이스 스키마를 관리한다. `ddl-auto: validate`로 설정되어 JPA 엔티티와 스키마의 일치 여부만 검증하고, 스키마 변경은 Flyway 마이그레이션 파일로만 수행한다.

| 서비스 | Flyway 활성화 | 마이그레이션 수 | 마이그레이션 경로 |
|--------|-------------|---------------|------------------|
| Auth | true | 3 (V1~V3) | `auth-service/src/main/resources/db/migration/` |
| Ticket | true | 11 (V1~V11) | `ticket-service/src/main/resources/db/migration/` |
| Payment | true | 3 (V1~V3) | `payment-service/src/main/resources/db/migration/` |
| Stats | true | 2 (V1~V2) | `stats-service/src/main/resources/db/migration/` |
| Community | true | 3 (V1~V3) | `community-service/src/main/resources/db/migration/` |
| Catalog | false | - | ticket_db 공유, 마이그레이션 없음 |

모든 서비스는 `baseline-on-migrate: true`로 설정되어 기존 데이터베이스에도 마이그레이션을 적용할 수 있다.

**출처:**
- `ticket-service/src/main/resources/application.yml:29-31`
- `auth-service/src/main/resources/application.yml:15-17`
- `catalog-service/src/main/resources/application.yml:8-9`

---

## 5. Circuit Breaker & Resilience

서비스 간 동기 통신의 장애 전파를 방지하기 위해 Resilience4j의 Circuit Breaker와 Retry 패턴을 적용한다. Ticket, Payment, Community, Queue, Catalog 서비스에서 동일한 `internalService` 인스턴스 설정을 공유한다.

### Circuit Breaker 설정

```yaml
# ticket-service/src/main/resources/application.yml:87-96
resilience4j:
  circuitbreaker:
    instances:
      internalService:
        sliding-window-size: 10
        failure-rate-threshold: 50
        wait-duration-in-open-state: 10s
        permitted-number-of-calls-in-half-open-state: 3
        slow-call-duration-threshold: 3s
        slow-call-rate-threshold: 80
```

| 설정 항목 | 값 | 설명 |
|----------|---|------|
| `sliding-window-size` | 10 | 최근 10개 호출 기준으로 실패율 계산 |
| `failure-rate-threshold` | 50% | 실패율 50% 초과 시 Circuit Open |
| `wait-duration-in-open-state` | 10초 | Open 상태에서 Half-Open으로 전환까지 대기 시간 |
| `permitted-number-of-calls-in-half-open-state` | 3 | Half-Open 상태에서 허용되는 테스트 호출 수 |
| `slow-call-duration-threshold` | 3초 | 3초 이상 걸리면 느린 호출로 분류 |
| `slow-call-rate-threshold` | 80% | 느린 호출 비율 80% 초과 시 Circuit Open |

**출처:**
- `ticket-service/src/main/resources/application.yml:87-96`
- `payment-service/src/main/resources/application.yml:53-62`
- `community-service/src/main/resources/application.yml:44-53`
- `queue-service/src/main/resources/application.yml:58-67`
- `catalog-service/src/main/resources/application.yml:49-58`

### Retry 설정

```yaml
# ticket-service/src/main/resources/application.yml:97-106
resilience4j:
  retry:
    instances:
      internalService:
        max-attempts: 3
        wait-duration: 500ms
        exponential-backoff-multiplier: 2
        retry-exceptions:
          - org.springframework.web.client.ResourceAccessException
          - java.net.ConnectException
```

| 설정 항목 | 값 | 설명 |
|----------|---|------|
| `max-attempts` | 3 | 최대 3회 재시도 |
| `wait-duration` | 500ms | 초기 대기 시간 |
| `exponential-backoff-multiplier` | 2 | 지수 백오프 배수 (500ms -> 1s -> 2s) |
| `retry-exceptions` | `ResourceAccessException`, `ConnectException` | 네트워크 오류에만 재시도 |

**출처:** `ticket-service/src/main/resources/application.yml:97-106`

### 멱등성 고려사항

POST 요청(비멱등 작업)에는 `@Retry`를 적용하지 않는다. Community Service의 포인트 적립과 Catalog Service의 좌석 생성이 해당된다.

```java
// community-service/.../shared/client/TicketInternalClient.java:41
// No @Retry: POST is not idempotent -- retry could cause duplicate point awards

// catalog-service/.../shared/client/TicketInternalClient.java:37
// No @Retry: POST is not idempotent -- retry could cause duplicate seat creation
```

반면, GET 요청(멱등 작업)에는 `@CircuitBreaker`와 `@Retry`를 함께 적용한다.

```java
// payment-service/.../client/TicketInternalClient.java:37-39
@CircuitBreaker(name = "internalService", fallbackMethod = "validateReservationFallback")
@Retry(name = "internalService")
public Map<String, Object> validateReservation(UUID reservationId, String userId) {
```

**출처:**
- `community-service/src/main/java/com/tiketi/communityservice/shared/client/TicketInternalClient.java:40-41`
- `catalog-service/src/main/java/com/tiketi/catalogservice/shared/client/TicketInternalClient.java:36-37`
- `payment-service/src/main/java/com/tiketi/paymentservice/client/TicketInternalClient.java:37-39`

### Fallback 전략

각 클라이언트는 Circuit Breaker가 Open되었을 때 실행되는 fallback 메서드를 정의한다. 서비스별로 전략이 다르다.

| 서비스 | Fallback 전략 | 설명 |
|--------|-------------|------|
| Payment | 예외 발생 (`ResponseStatusException 502`) | 결제 검증 실패 시 사용자에게 오류 전달 |
| Community | 로그 경고만 기록 | 포인트 적립 실패는 비핵심 기능이므로 무시 |
| Queue | 기본값 반환 (`Map.of("title", "Unknown")`) | 이벤트 정보 조회 실패 시 기본값 사용 |
| Catalog | 예외 발생 (`ResponseStatusException 503`) | 좌석 생성 실패는 치명적이므로 오류 전달 |

**출처:**
- `payment-service/src/main/java/com/tiketi/paymentservice/client/TicketInternalClient.java:68-83`
- `community-service/src/main/java/com/tiketi/communityservice/shared/client/TicketInternalClient.java:61-73`
- `queue-service/src/main/java/com/tiketi/queueservice/shared/client/TicketInternalClient.java:50-54`
- `catalog-service/src/main/java/com/tiketi/catalogservice/shared/client/TicketInternalClient.java:81-99`

---

## 6. 공통 기술 스택

### 핵심 프레임워크

| 기술 | 버전 | 설명 |
|------|------|------|
| Java | 21 | LTS, `toolchain { languageVersion = JavaLanguageVersion.of(21) }` |
| Spring Boot | 3.5.0 | 모든 서비스 동일 버전 |
| Spring Cloud | 2025.0.1 | Gateway 서비스에서 사용 |
| Gradle | - | 빌드 도구, 각 서비스별 독립 `build.gradle` |

**출처:** `gateway-service/build.gradle:3-4`, `ticket-service/build.gradle:3-4`

### Gateway 기술

Gateway는 `spring-cloud-starter-gateway-server-webmvc`를 사용한다. 이는 reactive가 아닌 서블릿 기반 Gateway 구현이다.

```groovy
// gateway-service/build.gradle:27
implementation 'org.springframework.cloud:spring-cloud-starter-gateway-server-webmvc'
```

**출처:** `gateway-service/build.gradle:27`

### 데이터 접근 패턴

서비스별로 두 가지 데이터 접근 패턴이 사용된다.

**JPA (Spring Data JPA)**
- 사용 서비스: Auth, Ticket, Payment, Stats
- `ddl-auto: validate` + Flyway 조합
- `open-in-view: false`로 설정하여 LazyLoading 문제 방지

```yaml
# ticket-service/src/main/resources/application.yml:25-28
spring:
  jpa:
    hibernate:
      ddl-auto: validate
    open-in-view: false
```

**JdbcTemplate (JDBC 직접 사용)**
- 사용 서비스: Community Service
- JPA 의존성 없이 `spring.datasource`만 설정
- Flyway로 스키마 관리

Community Service의 `application.yml`에는 JPA 설정이 없으며, datasource와 Flyway만 구성되어 있다.

**출처:**
- `ticket-service/src/main/resources/application.yml:25-28`
- `community-service/src/main/resources/application.yml:3-10`

### Actuator 및 모니터링

모든 서비스에서 동일한 Actuator 설정을 사용한다.

```yaml
# 공통 설정 (모든 서비스 동일)
management:
  endpoint:
    health:
      show-details: always
      show-components: always
  endpoints:
    web:
      exposure:
        include: health,info,prometheus
  tracing:
    sampling:
      probability: ${TRACING_SAMPLING_PROBABILITY:1.0}
  zipkin:
    tracing:
      endpoint: ${ZIPKIN_ENDPOINT:http://localhost:9411/api/v2/spans}
```

| 엔드포인트 | 용도 |
|-----------|------|
| `/actuator/health` | 헬스체크 (DB, Redis 포함) |
| `/actuator/info` | 서비스 정보 |
| `/actuator/prometheus` | Prometheus 메트릭 수집 |

분산 트레이싱은 Zipkin + Micrometer Tracing (Brave bridge)을 사용하며, 로그 패턴에 `traceId`와 `spanId`가 포함된다.

```yaml
# 공통 로깅 패턴
logging:
  pattern:
    level: "%5p [${spring.application.name:},%X{traceId:-},%X{spanId:-}]"
```

**출처:**
- `ticket-service/src/main/resources/application.yml:40-65`
- `gateway-service/src/main/resources/application.yml:80-103`

### 주요 의존성 요약

| 의존성 | 사용 서비스 | 용도 |
|--------|-----------|------|
| `spring-boot-starter-data-jpa` | Auth, Ticket, Payment, Stats | ORM |
| `spring-boot-starter-data-redis` | Gateway, Ticket, Queue | 캐시/세션/대기열 |
| `spring-kafka` | Ticket, Payment, Stats | 이벤트 메시징 |
| `resilience4j-spring-boot3` | Ticket, Payment, Community, Queue, Catalog | Circuit Breaker |
| `flyway-core` + `flyway-database-postgresql` | Auth, Ticket, Payment, Stats, Community | DB 마이그레이션 |
| `jjwt-api` | Gateway, Auth, Ticket | JWT 토큰 처리 |
| `micrometer-registry-prometheus` | 전체 | 메트릭 수집 |
| `micrometer-tracing-bridge-brave` | 전체 | 분산 트레이싱 |
| `software.amazon.awssdk:s3` | Ticket | S3 이미지 업로드 |

**출처:** `ticket-service/build.gradle:21-47`, `gateway-service/build.gradle:25-37`
