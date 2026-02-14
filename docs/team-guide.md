# URR 티켓팅 핵심 아키텍처 가이드

이 문서는 URR 프로젝트에서 구현한 5가지 핵심 패턴을 설명한다.
각 패턴은 독립적으로 다른 프로젝트에 적용할 수 있다.

---

## 목차

1. [MSA 게이트웨이 패턴](#1-msa-게이트웨이-패턴)
2. [VWR 가상 대기열](#2-vwr-가상-대기열)
3. [3단계 동시성 제어](#3-3단계-동시성-제어-좌석-잠금)
4. [멱등성 처리](#4-멱등성-idempotency)
5. [Kafka 비동기 결제 흐름](#5-kafka-비동기-결제-흐름)
6. [적용 가이드](#6-다른-프로젝트에-적용할-때)

---

## 1. MSA 게이트웨이 패턴

### 왜 필요한가

모든 서비스가 각자 JWT를 검증하면:
- JWT_SECRET이 8개 서비스에 분산 → 하나만 뚫려도 전체 탈취
- 키 교체(rotation) 시 8개 서비스 동시 재배포 필요
- 동일한 인증 코드가 6개 서비스에 중복

### 구조

```
[브라우저]
   │
   ▼
[Gateway :3001]
   ├─ CookieAuthFilter   (@Order -2)  쿠키 → Authorization 헤더 변환
   ├─ JwtAuthFilter       (@Order -1)  JWT 검증 → X-User-* 헤더 주입
   ├─ RateLimitFilter     (@Order  0)  Redis Lua 기반 요청 제한
   └─ VwrEntryTokenFilter (@Order  1)  대기열 토큰 검증
   │
   ├──→ Auth Service    :3005  (JWT_SECRET 보유 — 토큰 발급)
   ├──→ Ticket Service  :3002  (JWT_SECRET 없음 — 헤더만 읽음)
   ├──→ Payment Service :3003  (JWT_SECRET 없음)
   ├──→ Catalog Service :3009  (JWT_SECRET 없음)
   ├──→ Queue Service   :3007  (JWT_SECRET 없음)
   ├──→ Community Svc   :3008  (JWT_SECRET 없음)
   └──→ Stats Service   :3004  (JWT_SECRET 없음)
```

### 핵심 코드

#### 게이트웨이: JWT 검증 + 헤더 주입

> `services-spring/gateway-service/src/main/java/guru/urr/gatewayservice/filter/JwtAuthFilter.java`

```java
@Component
@Order(-1)
public class JwtAuthFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(...) {
        // 1. 외부에서 주입된 X-User-* 헤더 제거 (스푸핑 방지)
        HttpServletRequest sanitized = new UserHeaderStrippingWrapper(request);

        // 2. JWT가 있으면 검증
        String authHeader = sanitized.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ") && jwtKey != null) {
            Claims claims = Jwts.parser()
                .verifyWith(jwtKey).build()
                .parseSignedClaims(token).getPayload();

            // 3. 검증 성공 → 신뢰된 헤더 주입
            injectedHeaders.put("X-User-Id", claims.get("userId", String.class));
            injectedHeaders.put("X-User-Email", claims.get("email", String.class));
            injectedHeaders.put("X-User-Role", claims.get("role", String.class));

            filterChain.doFilter(new UserHeaderInjectionWrapper(sanitized, injectedHeaders), response);
            return;
        }

        // JWT 없으면 헤더 없이 통과 (public API 허용)
        filterChain.doFilter(sanitized, response);
    }
}
```

#### 다운스트림 서비스: 헤더만 읽기

> `services-spring/ticket-service/src/main/java/guru/urr/ticketservice/shared/security/JwtTokenParser.java`

```java
@Component
public class JwtTokenParser {

    // JWT 라이브러리 의존성 없음. 헤더만 읽는다.
    public AuthUser requireUser(HttpServletRequest request) {
        String userId = request.getHeader("X-User-Id");
        if (userId == null || userId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        return new AuthUser(userId,
            request.getHeader("X-User-Email"),
            request.getHeader("X-User-Role"));
    }
}
```

> 동일한 구조가 6개 서비스에 적용:
> - `ticket-service/.../shared/security/JwtTokenParser.java`
> - `catalog-service/.../shared/security/JwtTokenParser.java`
> - `payment-service/.../security/JwtTokenParser.java`
> - `queue-service/.../shared/security/JwtTokenParser.java`
> - `stats-service/.../security/JwtTokenParser.java`
> - `community-service/.../shared/security/JwtTokenParser.java`

### 이것이 안전한 이유

외부에서 `X-User-Id: admin123` 헤더를 직접 보내면?

1. **JwtAuthFilter가 제거함** — `UserHeaderStrippingWrapper`가 모든 X-User-* 헤더를 먼저 삭제
2. **네트워크 격리** — Kubernetes NetworkPolicy로 내부 서비스에 직접 접근 불가
3. **게이트웨이만 진입점** — Security Group / VPC로 보장

---

## 2. VWR 가상 대기열

### 왜 필요한가

인기 공연 티켓 오픈 시 10만 명이 동시 접속하면 DB와 서버가 죽는다.
대기열로 동시 접속자 수를 제한하고, 순서대로 입장시킨다.

### 구조

```
[사용자] → POST /queue/check/{eventId}
              │
              ▼
        ┌─────────────────────┐
        │  현재 active 수 확인  │
        │  (Redis ZSET count)  │
        └──────┬──────────────┘
               │
    ┌──────────┴──────────┐
    ▼                     ▼
[여유 있음]           [꽉 참 or 대기열 존재]
    │                     │
    ▼                     ▼
active에 추가       queue에 추가 (ZSET)
entry token 발급    position 반환
좌석 선택 가능       폴링으로 대기
                         │
                    [순서 도달]
                         │
                    ZPOPMIN으로
                    queue → active 이동
                    entry token 발급
```

### Redis 데이터 구조

```
queue:{eventId}         ZSET   score=진입시각(ms)    대기 순서
active:{eventId}        ZSET   score=만료시각(ms)    활성 유저 (TTL 관리)
queue:seen:{eventId}    ZSET   score=heartbeat시각   이탈 감지용
active:seen:{eventId}   ZSET   score=heartbeat시각   이탈 감지용
queue:active-events     SET                          활성 이벤트 목록
```

### 핵심 코드

#### 대기열 진입 판단

> `services-spring/queue-service/src/main/java/guru/urr/queueservice/service/QueueService.java:60-91`

```java
public Map<String, Object> check(UUID eventId, String userId) {
    // 1. 이미 대기 중이면 → 현재 위치 반환
    if (isInQueue(eventId, userId)) {
        int position = getQueuePosition(eventId, userId);
        return buildQueuedResponse(position, ...);
    }

    // 2. 이미 입장한 유저면 → 바로 진행
    if (isActiveUser(eventId, userId)) {
        return buildActiveResponse(eventInfo, eventId, userId);
    }

    // 3. 새로운 유저
    int currentUsers = getCurrentUsers(eventId);  // active ZSET에서 만료 안 된 유저 수
    int queueSize = getQueueSize(eventId);

    if (queueSize > 0 || currentUsers >= threshold) {
        // 대기열이 있거나 꽉 찬 경우 → 줄 서기
        addToQueue(eventId, userId);  // ZADD queue:{eventId} {timestamp} {userId}
        return buildQueuedResponse(...);
    }

    // 여유 있으면 → 바로 입장
    addActiveUser(eventId, userId);   // ZADD active:{eventId} {만료시각} {userId}
    return buildActiveResponse(...);  // entry token 포함
}
```

#### 대기열 → active 전환 (Lua 스크립트)

> `services-spring/queue-service/src/main/resources/redis/admission_control.lua`

```lua
-- 모든 연산이 원자적(atomic)으로 실행됨

-- 1. 만료된 active 유저 제거
redis.call('ZREMRANGEBYSCORE', activeKey, '-inf', now)

-- 2. 빈 슬롯 계산
local activeCount = redis.call('ZCARD', activeKey)
local available = maxActive - activeCount
if available <= 0 then return {0, activeCount} end

-- 3. 대기열에서 선착순으로 꺼내기
--    ZPOPMIN = score가 가장 낮은(= 가장 먼저 온) 유저부터 꺼냄
local popped = redis.call('ZPOPMIN', queueKey, toAdmit)

-- 4. active로 이동 (만료 시각을 score로 설정)
for i = 1, #popped, 2 do
    local userId = popped[i]
    redis.call('ZADD', activeKey, now + activeTtlMs, userId)
end

return {admitted, activeCount + admitted}
```

#### 동적 폴링 간격

> `services-spring/queue-service/src/main/java/guru/urr/queueservice/service/QueueService.java:231-238`

```java
// 대기열 앞쪽일수록 자주 폴링, 뒤쪽일수록 느리게
private int calculateNextPoll(int position) {
    if (position <= 1000)   return 1;   // 1초
    if (position <= 5000)   return 5;   // 5초
    if (position <= 10000)  return 10;  // 10초
    if (position <= 100000) return 30;  // 30초
    return 60;                          // 1분
}
```

#### Entry Token 발급

> `services-spring/queue-service/src/main/java/guru/urr/queueservice/service/QueueService.java:215-227`

```java
// active 유저에게만 발급되는 JWT (별도 시크릿)
private String generateEntryToken(String eventId, String userId) {
    return Jwts.builder()
        .subject(eventId)            // 이 이벤트에 대한 토큰
        .claim("uid", userId)        // 이 유저에게만 유효
        .expiration(new Date(nowMs + entryTokenTtlSeconds * 1000L))
        .signWith(entryTokenKey)     // queue.entry-token.secret
        .compact();
}
```

이 토큰 없이 좌석 예매/결제 API를 호출하면 게이트웨이의 `VwrEntryTokenFilter`가 403을 반환한다.

> `services-spring/gateway-service/src/main/java/guru/urr/gatewayservice/filter/VwrEntryTokenFilter.java:113-114`

```java
private boolean isProtectedPath(String path) {
    return path.startsWith("/api/seats/") || path.startsWith("/api/reservations");
}
```

---

## 3. 3단계 동시성 제어 (좌석 잠금)

### 왜 필요한가

100명이 같은 좌석을 동시에 클릭하면 1명만 성공해야 한다.
DB 락만으로는 모든 요청이 DB까지 도달하여 부하가 크다.

### 3단계 구조

```
100명 동시 클릭
   │
   ▼
Phase 1: Redis Lua 분산 락     ← 99명 여기서 탈락 (1ms 이내)
   │
   ▼ (1명만 통과)
Phase 2: SELECT FOR UPDATE     ← DB 수준 재검증
   │
   ▼
Phase 3: UPDATE WHERE version  ← 최종 무결성 보장
   │
   ▼
좌석 예약 완료
```

### 핵심 코드

> `services-spring/ticket-service/src/main/java/guru/urr/ticketservice/domain/reservation/service/ReservationService.java:56-133`

#### Phase 1: Redis Lua 분산 락 (라인 74-89)

```java
SeatLockResult lockResult = seatLockService.acquireLock(eventId, seatId, userId);
if (!lockResult.success()) {
    throw new ResponseStatusException(HttpStatus.CONFLICT, "Seat already selected");
}
```

> `services-spring/ticket-service/src/main/resources/redis/seat_lock_acquire.lua`

```lua
-- Redis에서 원자적(atomic) 실행

-- 1. 이미 잠긴 좌석인지 확인
local status = redis.call('HGET', seatKey, 'status')
if status == 'HELD' or status == 'CONFIRMED' then
    local currentUser = redis.call('HGET', seatKey, 'userId')
    if currentUser == userId then
        -- 같은 유저가 다시 선택 → TTL 연장
        redis.call('EXPIRE', seatKey, ttl)
        return {1, existingToken}
    end
    return {0, '-1'}  -- 다른 유저가 잡고 있음 → 실패
end

-- 2. Fencing Token 생성 (단조증가 카운터)
local token = redis.call('INCR', tokenSeqKey)

-- 3. 원자적 상태 전환: AVAILABLE → HELD
redis.call('HMSET', seatKey,
    'status', 'HELD',
    'userId', userId,
    'token', token,
    'heldAt', tostring(redis.call('TIME')[1]))
redis.call('EXPIRE', seatKey, ttl)  -- 5분 후 자동 해제

return {1, token}  -- 성공 + fencing token
```

#### Phase 2: DB 비관적 잠금 (라인 92-114)

```java
// SELECT FOR UPDATE = 행 수준 잠금
// 다른 트랜잭션이 이 행을 수정하려면 대기해야 함
List<Map<String, Object>> seats = namedParameterJdbcTemplate.queryForList("""
    SELECT id, seat_label, price, status, version
    FROM seats
    WHERE id IN (:seatIds) AND event_id = :eventId
    FOR UPDATE
    """, params);

// DB에서 다시 한번 상태 확인 (Redis와 DB 사이에 변경될 수 있으므로)
List<String> unavailable = seats.stream()
    .filter(seat -> !Objects.equals("available", seat.get("status")))
    .toList();
if (!unavailable.isEmpty()) {
    throw new ResponseStatusException(HttpStatus.CONFLICT, "Seat already reserved");
}
```

#### Phase 3: 낙관적 잠금 (라인 117-133)

```java
// version 컬럼으로 최종 검증
// UPDATE가 0행을 반환하면 = 누군가 사이에 변경한 것
int updated = jdbcTemplate.update("""
    UPDATE seats
    SET status = 'locked', version = version + 1,
        fencing_token = ?, locked_by = CAST(? AS UUID)
    WHERE id = ? AND version = ?
    """, fencingToken, userId, seatId, currentVersion);

if (updated == 0) {
    throw new ResponseStatusException(HttpStatus.CONFLICT, "Seat modified concurrently");
}
```

### Fencing Token이 필요한 이유

```
시간 ──────────────────────────────────────────→

유저A: [Redis 락 획득 token=5] ─── 네트워크 지연 ─── [결제 시도 token=5]
                                                          │
유저B:        [Redis 락 만료] → [새 락 획득 token=6] → [결제 완료]
                                                          │
                                                    token=5 ≠ token=6
                                                     → 거부! (유저B 보호)
```

분산 시스템에서 락의 TTL이 만료되면 새 소유자에게 넘어간다.
이때 지연된 이전 소유자의 요청을 fencing token으로 구별한다.

> `services-spring/ticket-service/src/main/resources/redis/payment_verify.lua`

```lua
local currentUserId = redis.call('HGET', seatKey, 'userId')
local currentToken = redis.call('HGET', seatKey, 'token')

-- 유저 ID + fencing token 둘 다 일치해야 통과
if currentUserId ~= userId or currentToken ~= token then
    return 0  -- 락이 만료/도난됨
end

redis.call('HSET', seatKey, 'status', 'CONFIRMED')
return 1
```

> `services-spring/ticket-service/src/main/java/guru/urr/ticketservice/domain/seat/service/SeatLockService.java:81-99`

```java
public boolean verifyForPayment(UUID eventId, UUID seatId, String userId, long token) {
    // Redis Lua로 원자적 검증
    Long result = redisTemplate.execute(paymentVerifyScript,
        List.of(seatKey), userId, String.valueOf(token));
    return result != null && result == 1;
}
```

---

## 4. 멱등성 (Idempotency)

### 왜 필요한가

- 네트워크 타임아웃 → 클라이언트가 재시도 → 동일 요청 2번 도달
- "결제" 버튼 더블클릭 → 2건의 결제 생성
- Kafka at-least-once → 같은 이벤트 2번 수신

### 구현: 프론트엔드

> `apps/web/src/lib/api-client.ts:158-165`

```typescript
// 요청마다 고유 UUID를 생성해서 보냄
createTicketOnly: (payload) => http.post("/reservations", {
    ...payload,
    idempotencyKey: payload.idempotencyKey ?? crypto.randomUUID(),
}),

// 좌석 예매도 동일
reserve: (payload) => http.post("/seats/reserve", {
    ...payload,
    idempotencyKey: payload.idempotencyKey ?? crypto.randomUUID(),
}),
```

### 구현: 백엔드 (API 레벨)

> `services-spring/ticket-service/src/main/java/guru/urr/ticketservice/domain/reservation/service/ReservationService.java:62-69`

```java
// 좌석 예약 시
if (request.idempotencyKey() != null && !request.idempotencyKey().isBlank()) {
    List<Map<String, Object>> existing = jdbcTemplate.queryForList(
        "SELECT id, reservation_number, total_amount, status, payment_status, expires_at " +
        "FROM reservations WHERE idempotency_key = ?",
        request.idempotencyKey());

    if (!existing.isEmpty()) {
        // 이미 같은 키로 생성된 예약이 있으면 → 기존 결과 반환 (새로 만들지 않음)
        return Map.of("message", "Seat reserved temporarily", "reservation", existing.getFirst());
    }
}
```

> 일반 예약도 동일한 패턴 (라인 183-190)

DB 스키마:
```sql
-- services-spring/ticket-service/src/main/resources/db/migration/V12__reservation_idempotency.sql
ALTER TABLE reservations ADD COLUMN idempotency_key VARCHAR(255);
CREATE UNIQUE INDEX idx_reservations_idempotency_key
    ON reservations (idempotency_key) WHERE idempotency_key IS NOT NULL;
```

### 구현: Kafka 컨슈머 레벨

> `services-spring/ticket-service/src/main/java/guru/urr/ticketservice/messaging/PaymentEventConsumer.java:49-93`

```java
@KafkaListener(topics = "payment-events", groupId = "ticket-service-group")
public void handlePaymentEvent(Map<String, Object> event) {
    // 1. 이벤트 고유 키 생성
    String eventKey = buildEventKey(event);  // "PAYMENT_CONFIRMED:{reservationId}"

    // 2. 이미 처리했으면 건너뜀
    if (eventKey != null && isAlreadyProcessed(eventKey)) {
        log.info("Skipping already-processed event: {}", eventKey);
        return;
    }

    // 3. 비즈니스 로직 실행
    handleReservationPayment(event);

    // 4. 처리 완료 기록
    if (eventKey != null) {
        markProcessed(eventKey);
    }
}
```

> `PaymentEventConsumer.java:209-231`

```java
private boolean isAlreadyProcessed(String eventKey) {
    Integer count = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM processed_events WHERE event_key = ? AND consumer_group = ?",
        Integer.class, eventKey, CONSUMER_GROUP);
    return count != null && count > 0;
}

private void markProcessed(String eventKey) {
    jdbcTemplate.update(
        "INSERT INTO processed_events (event_key, consumer_group) VALUES (?, ?)",
        eventKey, CONSUMER_GROUP);
}
```

DB 스키마:
```sql
-- services-spring/ticket-service/src/main/resources/db/migration/V14__processed_events.sql
CREATE TABLE processed_events (
    id          BIGSERIAL PRIMARY KEY,
    event_key   VARCHAR(255) NOT NULL,
    consumer_group VARCHAR(100) NOT NULL,
    processed_at TIMESTAMP DEFAULT NOW(),
    UNIQUE (event_key, consumer_group)
);
```

---

## 5. Kafka 비동기 결제 흐름

### 왜 필요한가

결제 서비스를 동기 HTTP로 호출하면:
- 결제 서비스가 1초 응답 지연 → 예약 API도 1초 지연
- 결제 서비스 장애 → 예약 자체가 불가능
- 환불 처리 중 타임아웃 → 돈은 빠졌는데 상태 미반영

Kafka로 비동기 처리하면:
- 예약과 결제가 독립적으로 동작
- 한쪽 서비스가 죽어도 메시지가 큐에 남아있음
- 재시작 시 밀린 메시지를 순서대로 처리

### 전체 흐름

```
[1] 사용자 → ticket-service: 좌석 예약 (status=pending, 5분 TTL)
         │
[2] 사용자 → payment-service: 결제 요청
         │
[3] payment-service ──→ Kafka [payment-events] ──→ ticket-service
         │                  "PAYMENT_CONFIRMED"         │
         │                                         예약 확정
         │                                    (status=confirmed)
         │
[4] 사용자 → ticket-service: 취소 요청
         │                (status=cancelled, payment_status=refund_requested)
         │
[5] ticket-service ──→ Kafka [reservation-events] ──→ payment-service
                        "ReservationCancelled"              │
                                                       환불 처리
                                                            │
[6] payment-service ──→ Kafka [payment-events] ──→ ticket-service
                        "PAYMENT_REFUNDED"              │
                                                   환불 완료 반영
                                              (payment_status=refunded)
```

### 핵심 코드

#### 이벤트 발행 (Producer)

> `services-spring/ticket-service/src/main/java/guru/urr/ticketservice/messaging/TicketEventProducer.java`

```java
@Component
public class TicketEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    // 예약 취소 이벤트 발행
    public void publishReservationCancelled(ReservationCancelledEvent event) {
        kafkaTemplate.send(
            "reservation-events",                    // 토픽
            event.reservationId().toString(),         // 키 → 같은 예약은 같은 파티션
            event)                                   // 이벤트 데이터
        .whenComplete((result, ex) -> {
            if (ex != null) log.error("Failed to publish: {}", ex.getMessage());
        });
    }

    // 결제 확정 이벤트 발행
    public void publishReservationConfirmed(ReservationConfirmedEvent event) { ... }

    // 양도 완료 이벤트 발행
    public void publishTransferCompleted(TransferCompletedEvent event) { ... }
}
```

#### 이벤트 수신 (Consumer)

> `services-spring/ticket-service/src/main/java/guru/urr/ticketservice/messaging/PaymentEventConsumer.java`

```java
@KafkaListener(topics = "payment-events", groupId = "ticket-service-group")
public void handlePaymentEvent(Map<String, Object> event) {
    // 멱등성 체크 (위 섹션 참고)
    String eventKey = buildEventKey(event);
    if (isAlreadyProcessed(eventKey)) return;

    String type = str(event.get("type"));
    if ("PAYMENT_CONFIRMED".equals(type)) {
        String paymentType = str(event.get("paymentType"));
        switch (paymentType != null ? paymentType : "reservation") {
            case "transfer"   -> handleTransferPayment(event);    // 양도 결제
            case "membership" -> handleMembershipPayment(event);  // 멤버십 결제
            default           -> handleReservationPayment(event); // 일반 예약 결제
        }
    } else if ("PAYMENT_REFUNDED".equals(type)) {
        handleRefund(event);
    }

    markProcessed(eventKey);
}
```

#### 취소 → 환불 흐름

> `services-spring/ticket-service/src/main/java/guru/urr/ticketservice/domain/reservation/service/ReservationService.java:482-535`

```java
public Map<String, Object> cancelReservation(String userId, UUID reservationId) {
    // 1. 예약 행 잠금 (FOR UPDATE)
    // 2. 좌석 상태 복원: locked → available
    // 3. Redis 좌석 락 해제

    // 4. payment_status = 'refund_requested' (아직 실제 환불 안 됨)
    //    'refunded'로 바로 바꾸지 않는 이유: 실제 돈 이동은 payment-service가 처리
    jdbcTemplate.update(
        "UPDATE reservations SET status='cancelled', payment_status='refund_requested' WHERE id=?",
        reservationId);

    // 5. Kafka로 취소 이벤트 발행 → payment-service가 실제 환불 처리
    ticketEventProducer.publishReservationCancelled(new ReservationCancelledEvent(
        reservationId, userId, eventId, "User requested cancellation", Instant.now()));
}
```

### Kafka 토픽 & 이벤트 타입 정리

| 토픽 | 이벤트 | 발행 서비스 | 수신 서비스 | 처리 내용 |
|------|--------|-------------|-------------|-----------|
| `payment-events` | PAYMENT_CONFIRMED | payment | ticket | 예약 확정, 좌석 reserved |
| `payment-events` | PAYMENT_REFUNDED | payment | ticket | 환불 완료 반영 |
| `reservation-events` | ReservationCancelled | ticket | payment | 실제 환불 처리 |
| `reservation-events` | ReservationConfirmed | ticket | stats | 통계 집계 |
| `transfer-events` | TransferCompleted | ticket | stats | 양도 통계 |
| `membership-events` | MembershipActivated | ticket | stats | 멤버십 통계 |

### Kafka 설정

> `services-spring/ticket-service/src/main/resources/application.yml`

```yaml
spring:
  kafka:
    producer:
      acks: all              # 모든 브로커가 확인해야 성공 (데이터 유실 방지)
      retries: 3
      properties:
        enable.idempotence: true  # Producer 멱등성
    consumer:
      auto-offset-reset: earliest
      enable-auto-commit: false   # 수동 커밋 (처리 완료 후 커밋)
```

---

## 6. 다른 프로젝트에 적용할 때

### 우선순위 추천

| 순서 | 패턴 | 난이도 | 효과 | 필요한 인프라 |
|------|------|--------|------|---------------|
| 1 | **게이트웨이 인증** | 중 | 보안 강화 + 코드 중복 제거 | Spring Cloud Gateway |
| 2 | **멱등성** | 하 | 중복 결제/예약 방지 | DB 컬럼 1개 |
| 3 | **VWR 대기열** | 상 | 서버 과부하 방지 | Redis |
| 4 | **3단계 잠금** | 상 | 동시성 100% 해결 | Redis + Lua |
| 5 | **Kafka 비동기** | 상 | 서비스 장애 격리 | Kafka 클러스터 |

### 각 패턴별 최소 도입 요건

**게이트웨이 인증**
- `JwtAuthFilter` 1개 (게이트웨이에 추가)
- 다운스트림 서비스의 JWT 파싱 코드를 헤더 읽기로 교체
- NetworkPolicy로 게이트웨이 우회 차단

**멱등성**
- `idempotency_key` 컬럼 추가 (UNIQUE INDEX)
- API 진입부에 `SELECT WHERE idempotency_key = ?` 추가
- 프론트에서 `crypto.randomUUID()` 전송

**VWR 대기열**
- Redis ZSET 2개 (queue, active)
- Lua 스크립트 1개 (admission_control)
- Entry Token JWT 발급/검증
- 프론트에서 폴링 로직

**3단계 잠금**
- Redis Lua 스크립트 2개 (acquire, verify)
- DB에 `version`, `fencing_token` 컬럼
- `SELECT FOR UPDATE` + `UPDATE WHERE version = ?`

**Kafka 비동기**
- Kafka 토픽 설계 (이벤트별 분류)
- Producer: `KafkaTemplate.send(토픽, 키, 이벤트)`
- Consumer: `@KafkaListener` + `processed_events` 테이블

### 파일 구조 참고

```
services-spring/
├── gateway-service/
│   └── filter/
│       ├── CookieAuthFilter.java      ← 쿠키 → 헤더
│       ├── JwtAuthFilter.java         ← JWT 검증 + 헤더 주입
│       ├── RateLimitFilter.java       ← Rate Limiting
│       └── VwrEntryTokenFilter.java   ← Entry Token 검증
│
├── ticket-service/
│   ├── domain/reservation/service/
│   │   └── ReservationService.java    ← 3단계 잠금 + 멱등성
│   ├── domain/seat/service/
│   │   └── SeatLockService.java       ← Redis Lua 락 관리
│   ├── messaging/
│   │   ├── TicketEventProducer.java   ← Kafka 이벤트 발행
│   │   └── PaymentEventConsumer.java  ← Kafka 이벤트 수신 + 멱등성
│   └── resources/redis/
│       ├── seat_lock_acquire.lua      ← 좌석 잠금 Lua
│       ├── seat_lock_release.lua      ← 좌석 해제 Lua
│       └── payment_verify.lua         ← Fencing Token 검증 Lua
│
├── queue-service/
│   ├── service/
│   │   └── QueueService.java          ← VWR 대기열 로직
│   └── resources/redis/
│       ├── admission_control.lua      ← 대기열→active 전환 Lua
│       └── stale_cleanup.lua          ← 이탈 유저 정리 Lua
│
└── payment-service/
    └── controller/
        └── PaymentController.java     ← 결제 API
```
