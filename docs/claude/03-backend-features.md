# 백엔드 핵심 기능 상세

URR 티켓팅 플랫폼의 백엔드 핵심 기능을 서비스별로 정리한 기술 문서이다. 모든 코드 참조는 실제 소스 파일의 정확한 경로와 라인 번호를 기준으로 한다.

---

## 1. 인증 시스템 (Auth Service - port 3005)

인증 서비스는 사용자 등록, 로그인, JWT 토큰 관리, Google OAuth를 담당한다.

### 1.1 API 엔드포인트

| 메서드 | 경로 | 설명 |
|--------|------|------|
| POST | `/api/auth/register` | 회원가입 |
| POST | `/api/auth/login` | 로그인 |
| GET | `/api/auth/me` | 현재 사용자 정보 조회 |
| POST | `/api/auth/verify-token` | 토큰 유효성 검증 |
| POST | `/api/auth/refresh` | 토큰 갱신 |
| POST | `/api/auth/google` | Google OAuth 로그인 |
| POST | `/api/auth/logout` | 로그아웃 (쿠키 제거) |

출처: `services-spring/auth-service/src/main/java/com/tiketi/authservice/controller/AuthController.java:27-108`

### 1.2 회원가입 흐름

1. 이메일 중복 검사 수행
2. 비밀번호를 `PasswordEncoder`로 해시 처리
3. `UserEntity` 생성 및 저장
4. Access Token + Refresh Token 생성
5. 쿠키에 토큰 설정 후 `AuthResponse` 반환 (HTTP 201)

```java
// 이메일 중복 검사
userRepository.findByEmail(request.email()).ifPresent(user -> {
    throw new ApiException("Email already exists");
});

// 비밀번호 해시 처리
user.setPasswordHash(passwordEncoder.encode(request.password()));

// 토큰 발급
String token = jwtService.generateToken(saved);
String refreshToken = jwtService.generateRefreshToken(saved);
```

출처: `services-spring/auth-service/src/main/java/com/tiketi/authservice/service/AuthService.java:60-77`

### 1.3 로그인 흐름

1. 이메일로 사용자 조회 (실패 시 "Invalid email or password")
2. 비밀번호 해시 비교 (`passwordEncoder.matches`)
3. Access Token + Refresh Token 생성
4. 쿠키에 토큰 설정 후 응답

출처: `services-spring/auth-service/src/main/java/com/tiketi/authservice/service/AuthService.java:79-102`

### 1.4 JWT 토큰 구조

**설정값:**
- Access Token 만료: `1800`초 (30분, 기본값)
- Refresh Token 만료: `604800`초 (7일, 기본값)
- 서명 알고리즘: HMAC-SHA (최소 32바이트 키 필수)

출처: `services-spring/auth-service/src/main/resources/application.yml:47-52`

**Access Token 클레임:**

```java
Jwts.builder()
    .subject(user.getId().toString())
    .claim("userId", user.getId().toString())
    .claim("email", user.getEmail())
    .claim("role", user.getRole().name())
    .claim("type", "access")
    .issuedAt(now)
    .expiration(expiry)
    .signWith(signingKey())
    .compact();
```

출처: `services-spring/auth-service/src/main/java/com/tiketi/authservice/security/JwtService.java:27-42`

**Refresh Token 클레임:**

```java
Jwts.builder()
    .subject(user.getId().toString())
    .claim("userId", user.getId().toString())
    .claim("type", "refresh")
    .issuedAt(now)
    .expiration(expiry)
    .signWith(signingKey())
    .compact();
```

출처: `services-spring/auth-service/src/main/java/com/tiketi/authservice/security/JwtService.java:44-57`

**서명 키 생성:** Base64 디코딩을 시도하고, 실패 시 원시 바이트로 폴백한다. 32바이트 미만이면 예외를 발생시킨다.

출처: `services-spring/auth-service/src/main/java/com/tiketi/authservice/security/JwtService.java:80-92`

### 1.5 토큰 갱신 흐름

1. `refresh_token` 쿠키 또는 요청 본문에서 Refresh Token 추출
2. `validateRefreshToken`으로 토큰 파싱 및 `type=refresh` 검증
3. `userId` 클레임으로 사용자 조회
4. 새로운 Access Token + Refresh Token 발급 (토큰 로테이션)

```java
Claims claims = jwtService.validateRefreshToken(refreshTokenValue);
String userId = claims.get("userId", String.class);
UserEntity user = userRepository.findById(UUID.fromString(userId))
    .orElseThrow(() -> new ApiException("User not found"));
String newAccessToken = jwtService.generateToken(user);
String newRefreshToken = jwtService.generateRefreshToken(user);
```

출처: `services-spring/auth-service/src/main/java/com/tiketi/authservice/service/AuthService.java:104-124`

### 1.6 Google OAuth 흐름

1. 클라이언트에서 Google ID Token (`credential`) 전달
2. `GoogleIdTokenVerifier`로 토큰 검증 (audience: `GOOGLE_CLIENT_ID`)
3. Google 프로필에서 이메일, 이름, 프로필 사진 추출
4. 이메일로 기존 사용자 조회:
   - 없으면 신규 생성 (`passwordHash = "OAUTH_USER_NO_PASSWORD"`)
   - 있으면 `googleId` 연결
5. JWT 토큰 발급 및 쿠키 설정

출처: `services-spring/auth-service/src/main/java/com/tiketi/authservice/service/AuthService.java:167-228`

---

## 2. 예약 시스템 (Ticket Service - port 3002)

좌석 예약은 동시성 문제를 해결하기 위해 3단계 잠금(Three-Phase Locking) 전략을 사용한다.

### 2.1 3단계 좌석 잠금 메커니즘

#### Phase 1: Redis Lua Script - 분산 잠금 획득 (Fencing Token 포함)

Redis에서 원자적으로 좌석 상태를 확인하고 잠금을 획득한다. Fencing Token은 단조 증가(monotonically increasing) 카운터로, ABA 문제를 방지한다.

```lua
-- KEYS[1] = seat:{eventId}:{seatId}      (HASH: status, userId, token, heldAt)
-- KEYS[2] = seat:{eventId}:{seatId}:token_seq  (fencing token counter)
-- ARGV[1] = userId
-- ARGV[2] = ttl (seconds)

local seatKey = KEYS[1]
local tokenSeqKey = KEYS[2]
local userId = ARGV[1]
local ttl = tonumber(ARGV[2])

-- 1. Check current status
local status = redis.call('HGET', seatKey, 'status')
if status == 'HELD' or status == 'CONFIRMED' then
    local currentUser = redis.call('HGET', seatKey, 'userId')
    if currentUser == userId then
        -- Same user re-selecting: extend TTL and return existing token
        redis.call('EXPIRE', seatKey, ttl)
        local existingToken = redis.call('HGET', seatKey, 'token')
        return {1, existingToken}
    end
    return {0, '-1'}  -- Failure: seat taken by another user
end

-- 2. Generate monotonically increasing fencing token
local token = redis.call('INCR', tokenSeqKey)

-- 3. Atomic state transition: AVAILABLE -> HELD
redis.call('HMSET', seatKey,
    'status', 'HELD',
    'userId', userId,
    'token', token,
    'heldAt', tostring(redis.call('TIME')[1])
)
redis.call('EXPIRE', seatKey, ttl)

return {1, token}
```

출처: `services-spring/ticket-service/src/main/resources/redis/seat_lock_acquire.lua:1-36`

**Redis 키 구조:**
- 좌석 잠금 키: `seat:{eventId}:{seatId}` (Hash: status, userId, token, heldAt)
- 토큰 시퀀스 키: `seat:{eventId}:{seatId}:token_seq` (정수 카운터)
- 잠금 TTL: 기본 300초 (`SEAT_LOCK_TTL_SECONDS`)

출처: `services-spring/ticket-service/src/main/java/com/tiketi/ticketservice/domain/seat/service/SeatLockService.java:30-31, 110-112`

#### Phase 2: SELECT FOR UPDATE - 데이터베이스 비관적 잠금

Redis 잠금 획득 후, DB에서 `SELECT FOR UPDATE`로 행 수준 잠금을 건다. 좌석 상태가 `available`인지 재확인한다.

```java
// Phase 2: DB lock with optimistic locking (version check)
List<Map<String, Object>> seats = namedParameterJdbcTemplate.queryForList("""
    SELECT id, seat_label, price, status, version
    FROM seats
    WHERE id IN (:seatIds) AND event_id = :eventId
    FOR UPDATE
    """, new MapSqlParameterSource()
    .addValue("seatIds", request.seatIds())
    .addValue("eventId", request.eventId()));
```

출처: `services-spring/ticket-service/src/main/java/com/tiketi/ticketservice/domain/reservation/service/ReservationService.java:79-86`

#### Phase 3: Optimistic Lock - 버전 기반 동시 수정 감지

`version` 컬럼을 사용하여 동시 수정을 감지한다. `WHERE version = ?` 조건이 실패하면 (updated == 0) 동시성 충돌로 판단한다.

```java
// Phase 3: Update seats with version increment and fencing token
int updated = jdbcTemplate.update("""
    UPDATE seats
    SET status = 'locked', version = version + 1,
        fencing_token = ?, locked_by = CAST(? AS UUID), updated_at = NOW()
    WHERE id = ? AND version = ?
    """, fencingToken, userId, seat.get("id"), currentVersion);

if (updated == 0) {
    releaseLocks(request.eventId(), request.seatIds(), userId, lockResults);
    throw new ResponseStatusException(HttpStatus.CONFLICT, "Seat modified concurrently");
}
```

출처: `services-spring/ticket-service/src/main/java/com/tiketi/ticketservice/domain/reservation/service/ReservationService.java:104-120`

### 2.2 잠금 해제 (Release) Lua Script

사용자 ID와 Fencing Token이 모두 일치해야만 잠금을 해제한다.

```lua
-- KEYS[1] = seat:{eventId}:{seatId}
-- ARGV[1] = userId
-- ARGV[2] = token

local currentUserId = redis.call('HGET', seatKey, 'userId')
local currentToken = redis.call('HGET', seatKey, 'token')

-- Only release if same user and same token
if currentUserId ~= userId or currentToken ~= token then
    return 0
end

redis.call('DEL', seatKey)
return 1
```

출처: `services-spring/ticket-service/src/main/resources/redis/seat_lock_release.lua:1-18`

### 2.3 결제 검증 (Payment Verify) Lua Script

결제 처리 시 좌석 잠금의 유효성을 확인하고, 상태를 `CONFIRMED`로 전환하여 결제 중 잠금 해제를 방지한다.

```lua
-- KEYS[1] = seat:{eventId}:{seatId}
-- ARGV[1] = userId
-- ARGV[2] = token

local currentUserId = redis.call('HGET', seatKey, 'userId')
local currentToken = redis.call('HGET', seatKey, 'token')

-- Verify both user and fencing token
if currentUserId ~= userId or currentToken ~= token then
    return 0  -- Failed: lock expired or stolen
end

-- Mark as CONFIRMED (prevents release while payment processes)
redis.call('HSET', seatKey, 'status', 'CONFIRMED')
return 1
```

출처: `services-spring/ticket-service/src/main/resources/redis/payment_verify.lua:1-19`

### 2.4 예약 생성 흐름

1. 좌석 수 검증 (최대 1석: `MAX_SEATS_PER_RESERVATION = 1`)
2. Phase 1~3 좌석 잠금 수행
3. 총 금액 계산
4. 예약 번호 생성 (`TK{timestamp}-{uuid8}`)
5. `reservations` 테이블에 INSERT (상태: `pending`, 만료: 5분)
6. `reservation_items` 테이블에 좌석 정보 INSERT
7. Fencing Token 포함하여 응답 반환

출처: `services-spring/ticket-service/src/main/java/com/tiketi/ticketservice/domain/reservation/service/ReservationService.java:52-151`

### 2.5 예약 확정 (결제 완료 후)

`confirmReservationPayment` 메서드에서 다음을 수행한다:

1. 예약의 좌석별 Redis Fencing Token 검증 (`verifyForPayment`)
2. 예약 상태를 `confirmed`, 결제 상태를 `completed`로 변경
3. 좌석 상태를 `reserved`로 변경
4. Redis 좌석 잠금 정리 (`cleanupLock`)
5. 아티스트 멤버십 포인트 적립 (100포인트, `TICKET_PURCHASE`)

출처: `services-spring/ticket-service/src/main/java/com/tiketi/ticketservice/domain/reservation/service/ReservationService.java:362-431`

### 2.6 예약 취소 흐름

1. 예약 조회 및 `FOR UPDATE` 잠금
2. 이미 취소된 예약인지 확인
3. 티켓 타입의 `available_quantity` 복원
4. 좌석 상태를 `available`로 변경 + Redis 잠금 정리
5. 예약 상태를 `cancelled`, 결제 상태를 `refund_requested`로 변경
6. `ReservationCancelledEvent` Kafka 이벤트 발행 (결제 서비스에서 실제 환불 처리)

출처: `services-spring/ticket-service/src/main/java/com/tiketi/ticketservice/domain/reservation/service/ReservationService.java:453-504`

---

## 3. 결제 시스템 (Payment Service - port 3003)

결제 서비스는 Toss Payments 연동을 통한 결제 처리와 환불을 담당한다. 예약, 양도, 멤버십 세 가지 결제 유형을 지원한다.

### 3.1 API 엔드포인트

| 메서드 | 경로 | 설명 |
|--------|------|------|
| POST | `/api/payments/prepare` | 결제 준비 |
| POST | `/api/payments/confirm` | 결제 승인 (Toss) |
| POST | `/api/payments/process` | 즉시 결제 처리 |
| GET | `/api/payments/order/{orderId}` | 주문별 결제 조회 |
| POST | `/api/payments/{paymentKey}/cancel` | 결제 취소/환불 |
| GET | `/api/payments/user/me` | 내 결제 내역 조회 |

출처: `services-spring/payment-service/src/main/java/com/tiketi/paymentservice/controller/PaymentController.java:22-90`

### 3.2 결제 흐름: Prepare -> Confirm -> Complete

#### 3.2.1 Prepare (결제 준비)

1. `paymentType`에 따라 검증 대상 결정:
   - `reservation`: Ticket Service에 예약 검증 요청
   - `transfer`: Ticket Service에 양도 검증 요청
   - `membership`: Ticket Service에 멤버십 검증 요청
2. 요청 금액과 검증된 금액 비교 (불일치 시 400 에러)
3. 기존 결제 중복 확인 (이미 `confirmed`면 에러, `pending`이면 기존 `orderId` 반환)
4. 주문 번호 생성 (`ORD_{timestamp}_{uuid8}`)
5. `payments` 테이블에 INSERT (상태: `pending`)
6. Toss 클라이언트 키와 함께 응답 반환

```java
String orderId = "ORD_" + System.currentTimeMillis() + "_"
    + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
```

출처: `services-spring/payment-service/src/main/java/com/tiketi/paymentservice/service/PaymentService.java:44-114`

#### 3.2.2 Confirm (결제 승인)

1. `orderId`로 결제 조회 (`FOR UPDATE`)
2. 사용자 소유권 및 금액 일치 검증
3. 결제 유형이 `reservation`이면 예약 재검증
4. 결제 상태를 `confirmed`로, `toss_status`를 `DONE`으로 변경
5. `completeByType`으로 유형별 완료 처리
6. `PaymentConfirmedEvent` Kafka 이벤트 발행

출처: `services-spring/payment-service/src/main/java/com/tiketi/paymentservice/service/PaymentService.java:116-169`

#### 3.2.3 Process (즉시 결제)

Toss Payments를 거치지 않는 간편 결제 처리 경로이다. Prepare + Confirm을 하나의 요청으로 수행한다.

1. 유형별 금액 검증
2. 멱등성 확인 (이미 `confirmed`된 결제가 있으면 그대로 반환)
3. 결제 레코드 생성 (상태: 바로 `confirmed`)
4. `completeByType`으로 유형별 완료 처리 + Kafka 이벤트 발행

출처: `services-spring/payment-service/src/main/java/com/tiketi/paymentservice/service/PaymentService.java:246-337`

### 3.3 환불 흐름

1. `paymentKey`로 결제 조회 (`FOR UPDATE`)
2. 사용자 소유권 확인
3. 상태가 `confirmed`인 경우에만 환불 가능
4. 결제 상태를 `refunded`로 변경, 환불 금액/사유/시간 기록
5. `PaymentRefundedEvent` Kafka 이벤트 발행

```java
jdbcTemplate.update("""
    UPDATE payments
    SET status = 'refunded', refund_amount = amount,
        refund_reason = ?, refunded_at = NOW(), updated_at = NOW()
    WHERE id = ?
    """, reason, payment.get("id"));
```

출처: `services-spring/payment-service/src/main/java/com/tiketi/paymentservice/service/PaymentService.java:190-224`

### 3.4 Kafka 이벤트

- **`PaymentConfirmedEvent`**: 결제 승인 시 발행. 포함 필드: paymentId, orderId, userId, reservationId, referenceId, paymentType, amount, paymentMethod, timestamp
- **`PaymentRefundedEvent`**: 환불 시 발행. 포함 필드: paymentId, orderId, userId, reservationId, transferId, paymentType, amount, reason, timestamp

출처: `services-spring/payment-service/src/main/java/com/tiketi/paymentservice/service/PaymentService.java:350-353, 219-221`

---

## 4. 대기열 시스템 (Queue Service - port 3007) -- Virtual Waiting Room

대기열 서비스는 이벤트별 가상 대기실을 운영하며, Redis ZSET 기반의 순서 관리와 활성 사용자 추적을 수행한다.

### 4.1 API 엔드포인트

| 메서드 | 경로 | 설명 |
|--------|------|------|
| POST | `/api/queue/check/{eventId}` | 대기열 진입/상태 확인 |
| GET | `/api/queue/status/{eventId}` | 대기 상태 조회 |
| POST | `/api/queue/heartbeat/{eventId}` | 하트비트 (활성 유지) |
| POST | `/api/queue/leave/{eventId}` | 대기열 이탈 |
| GET | `/api/queue/admin/{eventId}` | 관리자 대기열 정보 (관리자 전용) |
| POST | `/api/queue/admin/clear/{eventId}` | 대기열 초기화 (관리자 전용) |

출처: `services-spring/queue-service/src/main/java/com/tiketi/queueservice/controller/QueueController.java:17-80`

### 4.2 Redis ZSET 대기열 구조

**키 설계:**

| 키 패턴 | 타입 | 용도 | Score |
|---------|------|------|-------|
| `queue:{eventId}` | ZSET | 대기열 (순서 관리) | `System.currentTimeMillis()` (진입 시각) |
| `active:{eventId}` | ZSET | 활성 사용자 | 만료 타임스탬프 (currentTime + TTL) |
| `queue:seen:{eventId}` | ZSET | 대기열 하트비트 | 최근 확인 시각 |
| `active:seen:{eventId}` | ZSET | 활성 사용자 하트비트 | 최근 확인 시각 |
| `queue:active-events` | SET | 활성 이벤트 목록 | - |

출처: `services-spring/queue-service/src/main/java/com/tiketi/queueservice/service/QueueService.java:271-285`

### 4.3 대기열 진입 로직 (`check`)

```
사용자 요청
  |
  +-- 이미 대기열에 있는가? --> YES --> 터치 + 대기 응답 반환
  |
  +-- 활성 사용자인가? --> YES --> 터치 + 활성 응답 반환 (entryToken 포함)
  |
  +-- 대기열 크기 > 0 OR 현재 활성 사용자 >= threshold?
  |     |
  |     +-- YES --> 대기열에 추가 --> 대기 응답 반환
  |     |
  |     +-- NO --> 활성 사용자로 추가 --> 활성 응답 반환 (entryToken 포함)
```

출처: `services-spring/queue-service/src/main/java/com/tiketi/queueservice/service/QueueService.java:63-92`

**활성 사용자 판정:** ZSET의 score(만료 타임스탬프)가 현재 시간보다 큰 경우에만 활성으로 판단한다.

```java
Double score = redisTemplate.opsForZSet().score(activeKey(eventId), userId);
if (score == null) return false;
return score > System.currentTimeMillis();
```

출처: `services-spring/queue-service/src/main/java/com/tiketi/queueservice/service/QueueService.java:289-298`

**활성 사용자 수 조회:** `ZCOUNT`를 사용하여 현재 시간 이후의 score를 가진 멤버 수를 집계한다.

```java
Long count = redisTemplate.opsForZSet().count(activeKey(eventId),
    System.currentTimeMillis(), Double.POSITIVE_INFINITY);
```

출처: `services-spring/queue-service/src/main/java/com/tiketi/queueservice/service/QueueService.java:300-309`

### 4.4 Entry Token (진입 토큰) 생성

활성 사용자에게 JWT 기반 진입 토큰을 발급한다. 이 토큰은 실제 예약 페이지 접근 권한으로 사용된다.

```java
private String generateEntryToken(String eventId, String userId) {
    long nowMs = System.currentTimeMillis();
    Date issuedAt = new Date(nowMs);
    Date expiration = new Date(nowMs + (entryTokenTtlSeconds * 1000L));

    return Jwts.builder()
        .subject(eventId)
        .claim("uid", userId)
        .issuedAt(issuedAt)
        .expiration(expiration)
        .signWith(entryTokenKey)
        .compact();
}
```

출처: `services-spring/queue-service/src/main/java/com/tiketi/queueservice/service/QueueService.java:215-227`

### 4.5 동적 폴링 간격 (Position 기반)

대기열 위치에 따라 클라이언트의 폴링 주기를 동적으로 조정한다. 앞에 있을수록 자주, 뒤에 있을수록 느리게 폴링한다.

| 대기열 위치 | 폴링 간격 (초) |
|------------|--------------|
| 0 이하 | 3 |
| 1 - 1,000 | 1 |
| 1,001 - 5,000 | 5 |
| 5,001 - 10,000 | 10 |
| 10,001 - 100,000 | 30 |
| 100,001 이상 | 60 |

```java
private int calculateNextPoll(int position) {
    if (position <= 0) return 3;
    if (position <= 1000) return 1;
    if (position <= 5000) return 5;
    if (position <= 10000) return 10;
    if (position <= 100000) return 30;
    return 60;
}
```

출처: `services-spring/queue-service/src/main/java/com/tiketi/queueservice/service/QueueService.java:231-238`

### 4.6 대기 시간 추정 알고리즘 (Throughput 기반)

최근 1분간의 입장 처리 속도(throughput)를 기반으로 예상 대기 시간을 계산한다.

```java
private int estimateWait(int position) {
    if (position <= 0) return 0;

    long now = System.currentTimeMillis();
    long elapsed = now - throughputWindowStart.get();
    long admissions = recentAdmissions.get();

    // 데이터 부족 시 기본값: position * 30초
    if (elapsed < 5000 || admissions <= 0) {
        return Math.max(position * 30, 0);
    }

    // throughput = 최근 입장 수 / 경과 시간(초)
    double throughputPerSecond = (admissions * 1000.0) / elapsed;
    if (throughputPerSecond <= 0) {
        return Math.max(position * 30, 0);
    }
    return (int) Math.ceil(position / throughputPerSecond);
}
```

- **스루풋 윈도우**: 1분 (`THROUGHPUT_WINDOW_MS = 60_000`)
- **입장 기록**: `AdmissionWorkerService`에서 `recordAdmissions(count)` 호출 시 갱신
- **윈도우 리셋**: 1분 경과 시 카운터 초기화

출처: `services-spring/queue-service/src/main/java/com/tiketi/queueservice/service/QueueService.java:242-258`

### 4.7 인메모리 폴백 (ConcurrentHashMap)

Redis 연결 실패 시 `ConcurrentHashMap` 기반의 인메모리 대기열로 폴백한다. 멀티 인스턴스 환경에서는 사용할 수 없다.

```java
private final ConcurrentMap<String, LinkedHashSet<String>> fallbackQueue = new ConcurrentHashMap<>();
private final ConcurrentMap<String, Set<String>> fallbackActive = new ConcurrentHashMap<>();
```

모든 Redis 연산에서 `catch (Exception ex)` 블록으로 폴백이 구현되어 있으며, 경고 로그를 남긴다:

> "Redis unavailable - falling back to in-memory queue. This mode is NOT suitable for multi-instance deployment."

출처: `services-spring/queue-service/src/main/java/com/tiketi/queueservice/service/QueueService.java:37-38, 294-297`

### 4.8 설정값

| 설정 | 환경변수 | 기본값 | 설명 |
|------|---------|--------|------|
| 임계치 | `QUEUE_THRESHOLD` | 1000 | 활성 사용자 최대 수 |
| 활성 TTL | `QUEUE_ACTIVE_TTL_SECONDS` | 600 | 활성 사용자 만료 시간 (초) |
| 입장 주기 | `QUEUE_ADMISSION_INTERVAL_MS` | 1000 | 입장 배치 실행 주기 (ms) |
| 입장 배치 크기 | `QUEUE_ADMISSION_BATCH_SIZE` | 100 | 1회 입장 처리 수 |
| 비활성 정리 주기 | `QUEUE_STALE_CLEANUP_INTERVAL_MS` | 30000 | 비활성 사용자 정리 주기 (ms) |
| 진입 토큰 비밀키 | `QUEUE_ENTRY_TOKEN_SECRET` | (필수) | Entry Token 서명 키 |
| 진입 토큰 TTL | `QUEUE_ENTRY_TOKEN_TTL_SECONDS` | 600 | Entry Token 만료 시간 (초) |

출처: `services-spring/queue-service/src/main/resources/application.yml:37-45`

### 4.9 프로덕션 Redis 클러스터 설정

프로덕션 프로필(`prod`)에서는 Redis 클러스터 모드를 사용한다.

```yaml
spring:
  config:
    activate:
      on-profile: prod
  data:
    redis:
      cluster:
        nodes:
          - redis-cluster-0.redis-cluster:6379
          - redis-cluster-1.redis-cluster:6379
          - redis-cluster-2.redis-cluster:6379
        max-redirects: 3
      lettuce:
        cluster:
          refresh:
            adaptive: true
            period: 30s
```

출처: `services-spring/queue-service/src/main/resources/application.yml:78-95`

---

## 5. 멤버십 시스템

멤버십 시스템은 아티스트별 구독 관리, 포인트 적립, 티어 산정을 담당한다. Ticket Service(port 3002) 내에 포함되어 있다.

### 5.1 API 엔드포인트

| 메서드 | 경로 | 설명 |
|--------|------|------|
| POST | `/api/memberships/subscribe` | 멤버십 구독 신청 |
| GET | `/api/memberships/my` | 내 멤버십 목록 |
| GET | `/api/memberships/my/{artistId}` | 특정 아티스트 멤버십 상세 |
| GET | `/api/memberships/benefits/{artistId}` | 아티스트별 혜택 조회 |

출처: `services-spring/ticket-service/src/main/java/com/tiketi/ticketservice/domain/membership/controller/MembershipController.java:22-67`

### 5.2 구독 흐름

1. 아티스트 존재 및 `membership_price` 확인
2. 기존 멤버십 확인:
   - `active` 상태 -> 충돌 에러
   - `pending` 상태 -> 기존 멤버십 ID 반환
   - 만료/취소 -> 상태를 `pending`으로 변경
3. 신규 멤버십 생성 (티어: `SILVER`, 포인트: 0, 상태: `pending`)
4. 결제 완료 후 `activateMembership` 호출

출처: `services-spring/ticket-service/src/main/java/com/tiketi/ticketservice/domain/membership/service/MembershipService.java:33-76`

### 5.3 멤버십 활성화

결제 완료 후 호출된다:

1. 만료일 설정 (현재 + 1년)
2. 상태를 `active`로, `joined_at` 기록
3. 가입 보너스 포인트 적립 (`JOIN_BONUS_POINTS = 200`)
   - 신규 가입: `MEMBERSHIP_JOIN` + "Welcome bonus for membership join"
   - 갱신: `MEMBERSHIP_RENEW` + "Membership renewed"

```java
OffsetDateTime expiresAt = OffsetDateTime.now().plusYears(1);
jdbcTemplate.update("""
    UPDATE artist_memberships
    SET status = 'active', expires_at = ?, joined_at = NOW(), updated_at = NOW()
    WHERE id = ?
    """, Timestamp.from(expiresAt.toInstant()), membershipId);
```

출처: `services-spring/ticket-service/src/main/java/com/tiketi/ticketservice/domain/membership/service/MembershipService.java:78-101`

### 5.4 티어 시스템

포인트 기반으로 유효 티어(effective tier)를 산정한다. 별도의 BRONZE 가입 티어는 없으며, 비회원을 BRONZE로 취급한다.

| 티어 | 포인트 임계치 | 선예매 단계 | 예매 수수료 | 양도 수수료 | 양도 접근 |
|------|-------------|-----------|-----------|-----------|---------|
| BRONZE (비회원) | - | 일반예매 | 0원 | - | 불가 |
| SILVER | 0 이상 | 선예매 3 | 3,000원 | 10% | 가능 |
| GOLD | 500 이상 | 선예매 2 | 2,000원 | 5% | 가능 |
| DIAMOND | 1,500 이상 | 선예매 1 | 1,000원 | 5% | 가능 |

```java
private static final int GOLD_THRESHOLD = 500;
private static final int DIAMOND_THRESHOLD = 1500;

private String computeEffectiveTier(int points) {
    if (points >= DIAMOND_THRESHOLD) return "DIAMOND";
    if (points >= GOLD_THRESHOLD) return "GOLD";
    return "SILVER";
}
```

출처: `services-spring/ticket-service/src/main/java/com/tiketi/ticketservice/domain/membership/service/MembershipService.java:23-25, 246-250`

### 5.5 티어별 혜택 상세

```java
case "DIAMOND" -> {
    benefits.put("preSalePhase", 1);
    benefits.put("preSaleLabel", "선예매 1");
    benefits.put("bookingFeeSurcharge", 1000);
    benefits.put("transferAccess", true);
    benefits.put("transferFeePercent", 5);
}
case "GOLD" -> {
    benefits.put("preSalePhase", 2);
    benefits.put("preSaleLabel", "선예매 2");
    benefits.put("bookingFeeSurcharge", 2000);
    benefits.put("transferAccess", true);
    benefits.put("transferFeePercent", 5);
}
case "SILVER" -> {
    benefits.put("preSalePhase", 3);
    benefits.put("preSaleLabel", "선예매 3");
    benefits.put("bookingFeeSurcharge", 3000);
    benefits.put("transferAccess", true);
    benefits.put("transferFeePercent", 10);
}
```

출처: `services-spring/ticket-service/src/main/java/com/tiketi/ticketservice/domain/membership/service/MembershipService.java:252-289`

### 5.6 포인트 적립 시스템

포인트는 `membership_point_logs` 테이블에 기록되며, `artist_memberships.points`를 누적 갱신한다. 포인트 변경 시 자동으로 티어를 재산정한다.

```java
public void addPoints(UUID membershipId, String actionType, int points,
                      String description, UUID referenceId) {
    // 1. 포인트 로그 INSERT
    jdbcTemplate.update("""
        INSERT INTO membership_point_logs
        (membership_id, action_type, points, description, reference_id)
        VALUES (?, ?, ?, ?, ?)
        """, membershipId, actionType, points, description, referenceId);

    // 2. 멤버십 포인트 누적
    jdbcTemplate.update(
        "UPDATE artist_memberships SET points = points + ?, updated_at = NOW() WHERE id = ?",
        points, membershipId);

    // 3. 티어 재산정
    Integer totalPoints = jdbcTemplate.queryForObject(
        "SELECT points FROM artist_memberships WHERE id = ?", Integer.class, membershipId);
    String newTier = computeEffectiveTier(totalPoints);
    jdbcTemplate.update(
        "UPDATE artist_memberships SET tier = ?, updated_at = NOW() WHERE id = ?",
        newTier, membershipId);
}
```

**포인트 적립 액션 유형:**

| 액션 | 포인트 | 설명 |
|------|--------|------|
| `MEMBERSHIP_JOIN` | 200 | 멤버십 가입 보너스 |
| `MEMBERSHIP_RENEW` | 200 | 멤버십 갱신 보너스 |
| `TICKET_PURCHASE` | 100 | 티켓 구매 |
| `COMMUNITY_POST` | 30 | 커뮤니티 글 작성 |
| `COMMUNITY_COMMENT` | 10 | 커뮤니티 댓글 작성 |

출처: `services-spring/ticket-service/src/main/java/com/tiketi/ticketservice/domain/membership/service/MembershipService.java:202-223`

---

## 6. 양도 시스템

양도 시스템은 확정된 예약 티켓의 마켓플레이스 등록, 구매, 소유권 이전을 처리한다. Ticket Service(port 3002) 내에 포함되어 있다.

### 6.1 API 엔드포인트

| 메서드 | 경로 | 설명 |
|--------|------|------|
| POST | `/api/transfers` | 양도 등록 |
| GET | `/api/transfers` | 양도 가능 목록 조회 (artistId 필터 가능) |
| GET | `/api/transfers/my` | 내 양도 목록 |
| GET | `/api/transfers/{id}` | 양도 상세 |
| POST | `/api/transfers/{id}/cancel` | 양도 취소 |

출처: `services-spring/ticket-service/src/main/java/com/tiketi/ticketservice/domain/transfer/controller/TransferController.java:19-76`

### 6.2 양도 등록 흐름 (`createListing`)

1. 예약 검증: `confirmed` 상태이고 본인 소유인지 확인
2. 중복 등록 확인: 동일 예약에 `listed` 상태의 양도가 있는지 검사
3. 멤버십 검증 및 수수료 계산:
   - 아티스트가 있는 경우 활성 멤버십 필수
   - BRONZE 티어는 양도 불가
   - SILVER 티어: 수수료 10%
   - GOLD/DIAMOND 티어: 수수료 5%
4. 총 금액 산정: `원가 + (원가 * 수수료율 / 100)`
5. `ticket_transfers` 테이블에 INSERT (상태: `listed`)

```java
int feePercent = 10;
if (artistId != null) {
    // ...멤버십 조회...
    if ("BRONZE".equals(effectiveTier)) {
        throw new ResponseStatusException(HttpStatus.FORBIDDEN,
            "Bronze tier cannot transfer tickets");
    }
    feePercent = "SILVER".equals(effectiveTier) ? 10 : 5;
}

int originalPrice = ((Number) res.get("total_amount")).intValue();
int transferFee = originalPrice * feePercent / 100;
int totalPrice = originalPrice + transferFee;
```

출처: `services-spring/ticket-service/src/main/java/com/tiketi/ticketservice/domain/transfer/service/TransferService.java:29-97`

### 6.3 양도 구매 검증 (`validateForPurchase`)

1. 양도 상태가 `listed`인지 확인
2. 자기 양도 구매 방지 (seller_id != buyerId)
3. 구매자도 해당 아티스트의 활성 멤버십 필요
4. 결제 서비스에 `total_amount` 반환

출처: `services-spring/ticket-service/src/main/java/com/tiketi/ticketservice/domain/transfer/service/TransferService.java:200-235`

### 6.4 양도 완료 흐름 (`completePurchase`)

결제 완료 후 호출된다:

1. 양도 레코드 조회 (`FOR UPDATE`)
2. 상태가 `listed`인지 재확인
3. 예약의 `user_id`를 구매자로 변경 (소유권 이전)
4. 양도 상태를 `completed`로 변경, `buyer_id`와 `completed_at` 기록

```java
jdbcTemplate.update(
    "UPDATE reservations SET user_id = CAST(? AS UUID), updated_at = NOW() WHERE id = ?",
    buyerId, reservationId);

jdbcTemplate.update("""
    UPDATE ticket_transfers
    SET status = 'completed', buyer_id = CAST(? AS UUID),
        completed_at = NOW(), updated_at = NOW()
    WHERE id = ?
    """, buyerId, transferId);
```

출처: `services-spring/ticket-service/src/main/java/com/tiketi/ticketservice/domain/transfer/service/TransferService.java:237-263`

---

## 7. 커뮤니티 시스템 (Community Service - port 3006)

커뮤니티 서비스는 아티스트별 게시판의 게시글/댓글 CRUD와 멤버십 포인트 연동을 처리한다.

### 7.1 게시글 (Post) 서비스

**CRUD 작업:**

| 작업 | 메서드 | 설명 |
|------|--------|------|
| 목록 조회 | `list(artistId, page, limit)` | 아티스트별 필터, 고정글 우선 정렬, 페이지네이션 |
| 상세 조회 | `detail(id)` | 조회수 증가 + 게시글 반환 |
| 작성 | `create(request, user)` | 게시글 생성 + 30포인트 적립 |
| 수정 | `update(id, request, user)` | 작성자만 수정 가능 |
| 삭제 | `delete(id, user)` | 작성자 또는 관리자만 삭제 가능 |

출처: `services-spring/community-service/src/main/java/com/tiketi/communityservice/service/PostService.java:20-163`

**게시글 작성 시 포인트 적립:**

```java
private static final int POST_POINTS = 30;

// 게시글 생성 후
ticketInternalClient.awardMembershipPoints(
    user.userId(), request.artistId(),
    "COMMUNITY_POST", POST_POINTS,
    "커뮤니티 글 작성", postId);
```

출처: `services-spring/community-service/src/main/java/com/tiketi/communityservice/service/PostService.java:23, 112-117`

**정렬 방식:** 고정글(`is_pinned DESC`) 우선, 그다음 최신순(`created_at DESC`)

출처: `services-spring/community-service/src/main/java/com/tiketi/communityservice/service/PostService.java:48`

### 7.2 댓글 (Comment) 서비스

**CRUD 작업:**

| 작업 | 메서드 | 설명 |
|------|--------|------|
| 목록 조회 | `listByPost(postId, page, limit)` | 게시글별 댓글, 작성순 정렬, 페이지네이션 |
| 작성 | `create(postId, request, user)` | 댓글 생성 + comment_count 증가 + 10포인트 적립 |
| 삭제 | `delete(commentId, user)` | 작성자/관리자 삭제 + comment_count 감소 |

출처: `services-spring/community-service/src/main/java/com/tiketi/communityservice/service/CommentService.java:19-123`

**댓글 작성 시 포인트 적립:**

```java
private static final int COMMENT_POINTS = 10;

// 댓글 생성 후
ticketInternalClient.awardMembershipPoints(
    user.userId(), artistId,
    "COMMUNITY_COMMENT", COMMENT_POINTS,
    "커뮤니티 댓글 작성", commentId);
```

출처: `services-spring/community-service/src/main/java/com/tiketi/communityservice/service/CommentService.java:22, 91-96`

**댓글 수 관리:** 댓글 생성 시 `comment_count + 1`, 삭제 시 `GREATEST(comment_count - 1, 0)`으로 음수 방지

출처: `services-spring/community-service/src/main/java/com/tiketi/communityservice/service/CommentService.java:85-86, 118-119`

---

## 8. 통계 시스템 (Stats Service - port 3004)

통계 서비스는 Kafka 이벤트를 소비하여 통계 데이터를 집계하고, 관리자 대시보드를 위한 분석 API를 제공한다.

### 8.1 분석 엔드포인트 (14개, 모두 관리자 전용)

| 메서드 | 경로 | 파라미터 | 설명 |
|--------|------|---------|------|
| GET | `/api/stats/overview` | - | 전체 개요 |
| GET | `/api/stats/daily` | `days=30` | 일별 통계 |
| GET | `/api/stats/events` | `limit=10, sortBy=revenue` | 이벤트별 통계 |
| GET | `/api/stats/events/{eventId}` | - | 개별 이벤트 통계 |
| GET | `/api/stats/payments` | - | 결제 통계 |
| GET | `/api/stats/revenue` | `period=daily, days=30` | 매출 통계 |
| GET | `/api/stats/users` | `days=30` | 사용자 통계 |
| GET | `/api/stats/hourly-traffic` | `days=7` | 시간대별 트래픽 |
| GET | `/api/stats/conversion` | `days=30` | 전환율 통계 |
| GET | `/api/stats/cancellations` | `days=30` | 취소 통계 |
| GET | `/api/stats/realtime` | - | 실시간 통계 |
| GET | `/api/stats/seat-preferences` | `eventId` (선택) | 좌석 선호도 |
| GET | `/api/stats/user-behavior` | `days=30` | 사용자 행동 분석 |
| GET | `/api/stats/performance` | - | 시스템 성능 통계 |

모든 엔드포인트는 `jwtTokenParser.requireAdmin(authorization)`으로 관리자 권한을 검증한다.

출처: `services-spring/stats-service/src/main/java/com/tiketi/statsservice/controller/StatsController.java:16-144`

### 8.2 Kafka 이벤트 소비

세 개의 Kafka 토픽을 소비한다. Consumer Group: `stats-service-group`

#### 8.2.1 `payment-events` 토픽

이벤트 유형 판별 순서:
1. `type` 필드로 명시적 판별 (`PAYMENT_REFUNDED`, `PAYMENT_CONFIRMED`)
2. 폴백: duck-typing (`reason` 필드 존재 시 환불로 판단)

처리 내용:
- `PAYMENT_REFUNDED`: 환불 금액 기록
- `PAYMENT_CONFIRMED` + `paymentType=transfer`: 양도 결제 기록

출처: `services-spring/stats-service/src/main/java/com/tiketi/statsservice/messaging/StatsEventConsumer.java:25-68`

#### 8.2.2 `reservation-events` 토픽

이벤트 유형:
- `RESERVATION_CONFIRMED`: 예약 확정 (eventId, amount 기록)
- `RESERVATION_CANCELLED`: 예약 취소 (eventId 기록)
- `RESERVATION_CREATED`: 예약 생성 (eventId 기록)

폴백: `paymentMethod` 필드 존재 + `reason` 미존재 시 확정, `reason` 존재 시 취소, 그 외 생성

출처: `services-spring/stats-service/src/main/java/com/tiketi/statsservice/messaging/StatsEventConsumer.java:70-114`

#### 8.2.3 `membership-events` 토픽

멤버십 활성화 이벤트를 기록한다.

출처: `services-spring/stats-service/src/main/java/com/tiketi/statsservice/messaging/StatsEventConsumer.java:116-134`

### 8.3 이벤트 중복 제거 전략

`processed_events` 테이블을 사용하여 이벤트 처리 중복을 방지한다.

**이벤트 키 생성:**

```java
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
    return null;  // 키 생성 불가 시 중복 검사 건너뜀
}
```

출처: `services-spring/stats-service/src/main/java/com/tiketi/statsservice/messaging/StatsEventConsumer.java:138-150`

**중복 확인 및 기록:**

```java
// 중복 확인
Integer count = jdbcTemplate.queryForObject(
    "SELECT COUNT(*) FROM processed_events WHERE event_key = ?",
    Integer.class, eventKey);

// 처리 완료 기록 (UPSERT)
jdbcTemplate.update(
    "INSERT INTO processed_events (event_key, processed_at) "
    + "VALUES (?, NOW()) ON CONFLICT (event_key) DO NOTHING",
    eventKey);
```

키 형식: `{type}:{entityId}:{timestamp}` (예: `PAYMENT_CONFIRMED:uuid:2026-02-14T10:00:00Z`)

출처: `services-spring/stats-service/src/main/java/com/tiketi/statsservice/messaging/StatsEventConsumer.java:152-170`
