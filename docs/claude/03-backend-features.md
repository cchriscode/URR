# 03. 핵심 기능 구현 분석

---

## 1. VWR (Virtual Waiting Room) 대기열 시스템

### 1.1 아키텍처

VWR 시스템은 **queue-service**에 구현되어 있으며, 완전한 Redis 기반 아키텍처를 사용한다. 별도의 관계형 DB 없이 Redis의 Sorted Set(ZSET)을 핵심 자료구조로 활용하며, 선택적으로 AWS SQS FIFO 큐와 연동하여 입장 이벤트를 외부 시스템에 전달한다.

**핵심 구성 요소:**

| 구성 요소 | 역할 | 소스 참조 |
|-----------|------|-----------|
| `QueueService` | 대기열 입/퇴장, 상태 조회, 폴링 간격 계산 | `queue-service/.../service/QueueService.java:22` |
| `AdmissionWorkerService` | 스케줄러 기반 배치 입장 처리, stale 사용자 정리 | `queue-service/.../service/AdmissionWorkerService.java:14` |
| `SqsPublisher` | SQS FIFO 입장 이벤트 발행 | `queue-service/.../service/SqsPublisher.java:15` |
| `QueueController` | REST API 엔드포인트 | `queue-service/.../controller/QueueController.java:17` |

**임계값 기반 입장 제어:**

```java
// QueueService.java:45-46
@Value("${QUEUE_THRESHOLD:1000}") int threshold,
@Value("${QUEUE_ACTIVE_TTL_SECONDS:600}") int activeTtlSeconds,
```
> 참조: `queue-service/src/main/java/guru/urr/queueservice/service/QueueService.java:45-46`

`QUEUE_THRESHOLD`는 동시에 활성 상태로 허용하는 최대 사용자 수를 정의한다. 기본값은 1000이며, 이 임계값에 도달하면 이후 사용자는 대기열에 배치된다.

### 1.2 Redis 데이터 구조

시스템은 5가지 Redis 키 패턴을 사용한다.

**1) Active Users ZSET: `active:{eventId}`**

활성 사용자를 관리하며, score 값으로 **만료 타임스탬프**(expiry timestamp)를 사용한다.

```java
// QueueService.java:301-304
private void addActiveUser(UUID eventId, String userId) {
    long expiryScore = System.currentTimeMillis() + (activeTtlSeconds * 1000L);
    redisTemplate.opsForZSet().add(activeKey(eventId), userId, expiryScore);
    touchActiveUser(eventId, userId);
}
```
> 참조: `queue-service/src/main/java/guru/urr/queueservice/service/QueueService.java:301-304`

현재 활성 사용자 수를 조회할 때는 현재 시각 이후의 score만 카운트하여 만료된 항목을 자동 제외한다.

```java
// QueueService.java:295-298
private int getCurrentUsers(UUID eventId) {
    Long count = redisTemplate.opsForZSet().count(activeKey(eventId),
        System.currentTimeMillis(), Double.POSITIVE_INFINITY);
    return count == null ? 0 : count.intValue();
}
```
> 참조: `queue-service/src/main/java/guru/urr/queueservice/service/QueueService.java:295-298`

**2) Queue ZSET: `queue:{eventId}`**

대기열 사용자를 관리하며, score 값으로 **진입 타임스탬프**(join timestamp)를 사용한다. score가 낮을수록 먼저 입장한 사용자이므로 FIFO 순서가 보장된다.

```java
// QueueService.java:329-332
private void addToQueue(UUID eventId, String userId) {
    redisTemplate.opsForZSet().add(queueKey(eventId), userId, System.currentTimeMillis());
    touchQueueUser(eventId, userId);
}
```
> 참조: `queue-service/src/main/java/guru/urr/queueservice/service/QueueService.java:329-332`

**3) Heartbeat ZSET: `queue:seen:{eventId}` / `active:seen:{eventId}`**

사용자의 마지막 활동 시각을 추적한다. 일정 시간 이상 heartbeat가 없는 사용자는 stale로 판정하여 정리한다.

```java
// QueueService.java:341-346
private void touchQueueUser(UUID eventId, String userId) {
    try {
        redisTemplate.opsForZSet().add(queueSeenKey(eventId), userId, System.currentTimeMillis());
    } catch (Exception ignored) {
    }
}
```
> 참조: `queue-service/src/main/java/guru/urr/queueservice/service/QueueService.java:341-346`

```java
// QueueService.java:348-355
private void touchActiveUser(UUID eventId, String userId) {
    try {
        redisTemplate.opsForZSet().add(activeSeenKey(eventId), userId, System.currentTimeMillis());
        long newExpiry = System.currentTimeMillis() + (activeTtlSeconds * 1000L);
        redisTemplate.opsForZSet().add(activeKey(eventId), userId, newExpiry);
    } catch (Exception ignored) {
    }
}
```
> 참조: `queue-service/src/main/java/guru/urr/queueservice/service/QueueService.java:348-355`

**4) Active Events Set: `queue:active-events`**

Redis `KEYS` 명령어 사용을 회피하기 위한 SET 자료구조로, 현재 대기열이 활성화된 이벤트 ID를 저장한다.

```java
// QueueService.java:262-267
private void trackActiveEvent(UUID eventId) {
    try {
        redisTemplate.opsForSet().add("queue:active-events", eventId.toString());
    } catch (Exception ignored) {
    }
}
```
> 참조: `queue-service/src/main/java/guru/urr/queueservice/service/QueueService.java:262-267`

**5) TTL 설정**

| 설정 키 | 기본값 | 용도 |
|---------|--------|------|
| `QUEUE_ACTIVE_TTL_SECONDS` | 600초 (10분) | 활성 사용자 만료 시간 |
| `QUEUE_SEEN_TTL_SECONDS` | 600초 (10분) | Heartbeat stale 판정 기준 |
| `QUEUE_THRESHOLD` | 1000 | 최대 동시 활성 사용자 수 |

> 참조: `queue-service/src/main/java/guru/urr/queueservice/service/AdmissionWorkerService.java:32-34`

### 1.3 입장 흐름 (check 엔드포인트)

`POST /api/queue/check/{eventId}` 엔드포인트가 대기열 입장의 핵심 흐름을 처리한다.

> 참조: `queue-service/src/main/java/guru/urr/queueservice/controller/QueueController.java:27-34`

`QueueService.check()` 메서드의 전체 분기 로직은 다음과 같다.

```java
// QueueService.java:60-91
public Map<String, Object> check(UUID eventId, String userId) {
    Map<String, Object> eventInfo = ticketInternalClient.getEventQueueInfo(eventId);

    if (isInQueue(eventId, userId)) {
        touchQueueUser(eventId, userId);
        int position = getQueuePosition(eventId, userId);
        int queueSize = getQueueSize(eventId);
        return buildQueuedResponse(position, queueSize, eventInfo, eventId);
    }

    if (isActiveUser(eventId, userId)) {
        touchActiveUser(eventId, userId);
        return buildActiveResponse(eventInfo, eventId, userId);
    }

    int currentUsers = getCurrentUsers(eventId);
    int queueSize = getQueueSize(eventId);

    if (queueSize > 0 || currentUsers >= threshold) {
        addToQueue(eventId, userId);
        trackActiveEvent(eventId);
        queueMetrics.recordQueueJoined();
        int position = getQueuePosition(eventId, userId);
        queueSize = getQueueSize(eventId);
        return buildQueuedResponse(position, queueSize, eventInfo, eventId);
    }

    addActiveUser(eventId, userId);
    trackActiveEvent(eventId);
    queueMetrics.recordQueueAdmitted();
    return buildActiveResponse(eventInfo, eventId, userId);
}
```
> 참조: `queue-service/src/main/java/guru/urr/queueservice/service/QueueService.java:60-91`

**분기 흐름 상세:**

| 조건 | 처리 | 반환 상태 |
|------|------|-----------|
| 이미 대기열에 있는 경우 | heartbeat 갱신 + 현재 위치 반환 | `queued: true` |
| 이미 활성 사용자인 경우 | TTL 연장 + entryToken 발급 | `queued: false, status: active` |
| `queueSize > 0` 또는 `currentUsers >= threshold` | 대기열에 추가 | `queued: true` |
| 임계값 미만, 대기열 비어있음 | 즉시 입장 (active ZSET에 추가) | `queued: false, status: active` |

입장 허용 시 `buildActiveResponse()`에서 entryToken을 생성하고 SQS에 입장 이벤트를 발행한다.

```java
// QueueService.java:197-213
private Map<String, Object> buildActiveResponse(Map<String, Object> eventInfo, UUID eventId, String userId) {
    String entryToken = generateEntryToken(eventId.toString(), userId);
    // ...
    sqsPublisher.publishAdmission(eventId, userId, entryToken);
    return result;
}
```
> 참조: `queue-service/src/main/java/guru/urr/queueservice/service/QueueService.java:197-213`

### 1.4 동적 폴링 간격

클라이언트의 폴링 빈도를 대기열 위치에 따라 동적으로 조절하여 서버 부하를 최적화한다.

```java
// QueueService.java:231-238
private int calculateNextPoll(int position) {
    if (position <= 0) return 3;
    if (position <= 1000) return 1;
    if (position <= 5000) return 5;
    if (position <= 10000) return 10;
    if (position <= 100000) return 30;
    return 60;
}
```
> 참조: `queue-service/src/main/java/guru/urr/queueservice/service/QueueService.java:231-238`

| 대기열 위치 | 폴링 간격(초) | 설계 의도 |
|------------|--------------|-----------|
| <= 0 (활성) | 3 | 활성 상태 유지 확인 |
| <= 1,000 | 1 | 곧 입장할 사용자에게 실시간 업데이트 |
| <= 5,000 | 5 | 적절한 반응성 유지 |
| <= 10,000 | 10 | 부하 절감 시작 |
| <= 100,000 | 30 | 대규모 대기열 부하 관리 |
| > 100,000 | 60 | 최소 폴링으로 서버 보호 |

### 1.5 대기 시간 추정

처리량 슬라이딩 윈도우(1분 기반)를 활용하여 예상 대기 시간을 계산한다.

```java
// QueueService.java:36-38
private final AtomicLong recentAdmissions = new AtomicLong(0);
private final AtomicLong throughputWindowStart = new AtomicLong(System.currentTimeMillis());
private static final long THROUGHPUT_WINDOW_MS = 60_000; // 1-minute window
```
> 참조: `queue-service/src/main/java/guru/urr/queueservice/service/QueueService.java:36-38`

```java
// QueueService.java:242-258
private int estimateWait(int position) {
    if (position <= 0) return 0;

    long now = System.currentTimeMillis();
    long elapsed = now - throughputWindowStart.get();
    long admissions = recentAdmissions.get();

    if (elapsed < 5000 || admissions <= 0) {
        return Math.max(position * 30, 0);  // Fallback: position x 30초
    }

    double throughputPerSecond = (admissions * 1000.0) / elapsed;
    if (throughputPerSecond <= 0) {
        return Math.max(position * 30, 0);
    }
    return (int) Math.ceil(position / throughputPerSecond);
}
```
> 참조: `queue-service/src/main/java/guru/urr/queueservice/service/QueueService.java:242-258`

**알고리즘:**

1. 최근 1분간의 입장 허용 수(`recentAdmissions`)와 경과 시간(`elapsed`)으로 초당 처리량을 계산한다.
2. `estimatedWait = position / admissions_per_second`
3. 데이터가 부족할 경우(경과 시간 5초 미만 또는 입장 수 0) fallback으로 `position * 30초`를 반환한다.

`AdmissionWorkerService`에서 배치 입장 처리 시 `recordAdmissions()`를 호출하여 처리량 데이터를 갱신한다.

```java
// QueueService.java:168-177
public synchronized void recordAdmissions(int count) {
    long now = System.currentTimeMillis();
    long windowStart = throughputWindowStart.get();
    if (now - windowStart > THROUGHPUT_WINDOW_MS) {
        recentAdmissions.set(count);
        throughputWindowStart.set(now);
    } else {
        recentAdmissions.addAndGet(count);
    }
}
```
> 참조: `queue-service/src/main/java/guru/urr/queueservice/service/QueueService.java:168-177`

### 1.6 JWT 입장 토큰

활성 사용자에게 발급되는 입장 토큰은 HMAC-SHA256으로 서명된 JWT이다.

```java
// QueueService.java:215-227
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
> 참조: `queue-service/src/main/java/guru/urr/queueservice/service/QueueService.java:215-227`

**토큰 구성:**

| 필드 | 값 | 설명 |
|------|---|------|
| `subject` | eventId | 어떤 이벤트에 대한 입장인지 식별 |
| `uid` (claim) | userId | 입장 허용된 사용자 식별 |
| `iat` | 발급 시각 | 토큰 발급 시간 |
| `exp` | 발급 시각 + TTL | 토큰 만료 시간 |
| 서명 알고리즘 | HMAC-SHA256 | `entryTokenKey`로 서명 |

```java
// QueueService.java:47-48
@Value("${queue.entry-token.secret}") String entryTokenSecret,
@Value("${queue.entry-token.ttl-seconds:600}") int entryTokenTtlSeconds
```
> 참조: `queue-service/src/main/java/guru/urr/queueservice/service/QueueService.java:47-48`

TTL 기본값은 600초(10분)이며, `queue.entry-token.ttl-seconds` 설정으로 변경 가능하다. 게이트웨이에서도 동일한 secret을 사용하여 토큰을 검증한다.

> 참조: `gateway-service/src/main/resources/application.yml:106-107`

### 1.7 SQS FIFO 통합

`SqsPublisher`는 입장 이벤트를 AWS SQS FIFO 큐에 fire-and-forget 방식으로 발행한다.

```java
// SqsPublisher.java:38-68
public void publishAdmission(UUID eventId, String userId, String entryToken) {
    if (!enabled) {
        return;  // SQS 비활성 시 조용히 스킵
    }

    try {
        Map<String, Object> body = Map.of(
                "action", "admitted",
                "eventId", eventId.toString(),
                "userId", userId,
                "entryToken", entryToken,
                "timestamp", System.currentTimeMillis()
        );

        String messageBody = objectMapper.writeValueAsString(body);
        String deduplicationId = userId + ":" + eventId;

        sqsClient.sendMessage(SendMessageRequest.builder()
                .queueUrl(queueUrl)
                .messageBody(messageBody)
                .messageGroupId(eventId.toString())
                .messageDeduplicationId(deduplicationId)
                .build());
    } catch (Exception e) {
        log.error("SQS publish failed (fallback to Redis-only): user={} event={} error={}",
                userId, eventId, e.getMessage());
    }
}
```
> 참조: `queue-service/src/main/java/guru/urr/queueservice/service/SqsPublisher.java:38-68`

**FIFO 보장 메커니즘:**

| 속성 | 값 | 목적 |
|------|---|------|
| `MessageGroupId` | `eventId` | 동일 이벤트 내 메시지 순서 보장 |
| `MessageDeduplicationId` | `userId:eventId` | 5분 중복 방지 윈도우로 동일 사용자 중복 입장 방지 |

**Fallback 전략:** SQS 전송 실패 시 예외를 로깅하고 Redis-only 모드로 동작한다. `enabled` 플래그가 `false`이거나 `sqsClient`가 null이면 SQS 발행을 건너뛴다.

```java
// SqsPublisher.java:24-32
public SqsPublisher(
        @org.springframework.lang.Nullable SqsClient sqsClient,
        @Value("${aws.sqs.queue-url:}") String queueUrl,
        @Value("${aws.sqs.enabled:false}") boolean enabled) {
    this.sqsClient = sqsClient;
    this.queueUrl = queueUrl;
    this.objectMapper = new ObjectMapper();
    this.enabled = enabled && sqsClient != null && !queueUrl.isBlank();
}
```
> 참조: `queue-service/src/main/java/guru/urr/queueservice/service/SqsPublisher.java:24-32`

SQS 클라이언트 Bean은 `aws.sqs.enabled=true`일 때만 생성된다.

```java
// SqsConfig.java:14
@ConditionalOnProperty(name = "aws.sqs.enabled", havingValue = "true")
public class SqsConfig {
```
> 참조: `queue-service/src/main/java/guru/urr/queueservice/config/SqsConfig.java:14`

### 1.8 배치 입장 처리 (AdmissionWorkerService)

`AdmissionWorkerService`는 스케줄러 기반으로 대기열에서 활성 상태로 사용자를 배치 이동시킨다.

**입장 처리 (1초 주기):**

```java
// AdmissionWorkerService.java:47-48
@Scheduled(fixedDelayString = "${queue.admission.interval-ms:1000}")
public void admitUsers() {
```
> 참조: `queue-service/src/main/java/guru/urr/queueservice/service/AdmissionWorkerService.java:47-48`

Lua 스크립트(`admission_control.lua`)를 사용하여 원자적으로 배치 입장을 처리한다.

```lua
-- admission_control.lua:17-46
-- 1. 만료된 활성 사용자 제거
redis.call('ZREMRANGEBYSCORE', activeKey, '-inf', now)

-- 2. 현재 활성 사용자 수 확인
local activeCount = redis.call('ZCARD', activeKey)

-- 3. 사용 가능한 슬롯 계산
local available = maxActive - activeCount
if available <= 0 then
    return {0, activeCount}
end

local toAdmit = math.min(available, admitCount)

-- 4. ZPOPMIN - 대기열에서 원자적으로 꺼내기
local popped = redis.call('ZPOPMIN', queueKey, toAdmit)

-- 5. 활성 ZSET에 추가
for i = 1, #popped, 2 do
    local userId = popped[i]
    redis.call('ZADD', activeKey, now + activeTtlMs, userId)
    redis.call('ZREM', queueSeenKey, userId)
    admitted = admitted + 1
end
```
> 참조: `queue-service/src/main/resources/redis/admission_control.lua:17-46`

**Stale 사용자 정리 (30초 주기):**

```java
// AdmissionWorkerService.java:120-121
@Scheduled(fixedDelayString = "${queue.stale-cleanup.interval-ms:30000}")
public void cleanupStaleUsers() {
```
> 참조: `queue-service/src/main/java/guru/urr/queueservice/service/AdmissionWorkerService.java:120-121`

```lua
-- stale_cleanup.lua:11-19
local staleUsers = redis.call('ZRANGEBYSCORE', heartbeatKey,
    '-inf', cutoff, 'LIMIT', 0, batchSize)

if #staleUsers == 0 then
    return {0}
end

redis.call('ZREM', heartbeatKey, unpack(staleUsers))
redis.call('ZREM', queueKey, unpack(staleUsers))
```
> 참조: `queue-service/src/main/resources/redis/stale_cleanup.lua:11-19`

배치 크기(기본 1000)로 나누어 처리하며, 배치 간 100ms 대기하여 Redis 부하를 분산한다.

> 참조: `queue-service/src/main/java/guru/urr/queueservice/service/AdmissionWorkerService.java:140-159`

---

## 2. 좌석 동시성 제어 (3-Layer Locking)

### 2.1 Layer 1: Redis Lua 분산 잠금 (Fencing Token)

`SeatLockService`는 Redis Lua 스크립트를 사용하여 좌석별 분산 잠금을 관리한다.

> 참조: `ticket-service/src/main/java/guru/urr/ticketservice/domain/seat/service/SeatLockService.java:13`

**Lua 스크립트 빈 등록:**

```java
// RedisConfig.java:14-17
@Bean
public DefaultRedisScript<List> seatLockAcquireScript() {
    DefaultRedisScript<List> script = new DefaultRedisScript<>();
    script.setScriptSource(new ResourceScriptSource(new ClassPathResource("redis/seat_lock_acquire.lua")));
```
> 참조: `ticket-service/src/main/java/guru/urr/ticketservice/shared/config/RedisConfig.java:14-17`

**키 구조:**

```java
// SeatLockService.java:110-112
private String seatKey(UUID eventId, UUID seatId) {
    return "seat:" + eventId + ":" + seatId;
}
```
> 참조: `ticket-service/src/main/java/guru/urr/ticketservice/domain/seat/service/SeatLockService.java:110-112`

- **좌석 HASH**: `seat:{eventId}:{seatId}` -- 필드: `status`, `userId`, `token`, `heldAt`
- **펜싱 토큰 카운터**: `seat:{eventId}:{seatId}:token_seq` -- INCR로 단조 증가

#### seat_lock_acquire.lua

```lua
-- seat_lock_acquire.lua (전체)
-- KEYS[1] = seat:{eventId}:{seatId}      (HASH: status, userId, token, heldAt)
-- KEYS[2] = seat:{eventId}:{seatId}:token_seq  (fencing token counter)
-- ARGV[1] = userId
-- ARGV[2] = ttl (seconds)

local seatKey = KEYS[1]
local tokenSeqKey = KEYS[2]
local userId = ARGV[1]
local ttl = tonumber(ARGV[2])

-- 1. 현재 상태 확인
local status = redis.call('HGET', seatKey, 'status')
if status == 'HELD' or status == 'CONFIRMED' then
    local currentUser = redis.call('HGET', seatKey, 'userId')
    if currentUser == userId then
        -- 동일 사용자 재진입: TTL 연장 + 기존 토큰 반환
        redis.call('EXPIRE', seatKey, ttl)
        local existingToken = redis.call('HGET', seatKey, 'token')
        return {1, existingToken}
    end
    return {0, '-1'}  -- 실패: 타 사용자가 점유 중
end

-- 2. 단조 증가 펜싱 토큰 생성
local token = redis.call('INCR', tokenSeqKey)

-- 3. 원자적 상태 전이: AVAILABLE -> HELD
redis.call('HMSET', seatKey,
    'status', 'HELD',
    'userId', userId,
    'token', token,
    'heldAt', tostring(redis.call('TIME')[1])
)
redis.call('EXPIRE', seatKey, ttl)

return {1, token}
```
> 참조: `ticket-service/src/main/resources/redis/seat_lock_acquire.lua:1-36`

**핵심 동작:**

| 시나리오 | 동작 | 반환값 |
|---------|------|--------|
| 좌석 없음 (AVAILABLE) | HASH 생성, HELD 상태 설정, 펜싱 토큰 발급 | `{1, token}` |
| 동일 사용자 재진입 | TTL 연장, 기존 토큰 반환 | `{1, existingToken}` |
| 타 사용자 점유 중 | 거부 | `{0, -1}` |

**TTL 설정:**

```java
// SeatLockService.java:30
@Value("${SEAT_LOCK_TTL_SECONDS:300}") int seatLockTtlSeconds
```
> 참조: `ticket-service/src/main/java/guru/urr/ticketservice/domain/seat/service/SeatLockService.java:30`

기본 300초(5분)이며, Redis EXPIRE 명령으로 자동 해제가 보장된다.

**Java 측 잠금 획득:**

```java
// SeatLockService.java:39-63
public SeatLockResult acquireLock(UUID eventId, UUID seatId, String userId) {
    String seatKey = seatKey(eventId, seatId);
    String tokenSeqKey = seatKey + ":token_seq";
    try {
        @SuppressWarnings("unchecked")
        List<Object> result = redisTemplate.execute(
            seatLockAcquireScript,
            List.of(seatKey, tokenSeqKey),
            userId,
            String.valueOf(seatLockTtlSeconds)
        );

        if (result == null || result.size() < 2) {
            return new SeatLockResult(false, -1);
        }

        long success = Long.parseLong(result.get(0).toString());
        long token = Long.parseLong(result.get(1).toString());
        return new SeatLockResult(success == 1, token);
    } catch (Exception ex) {
        log.error("Redis seat lock failed for {}: {}", seatKey, ex.getMessage());
        return new SeatLockResult(false, -1);
    }
}
```
> 참조: `ticket-service/src/main/java/guru/urr/ticketservice/domain/seat/service/SeatLockService.java:39-63`

반환 타입은 record 패턴을 사용한다.

```java
// SeatLockService.java:23
public record SeatLockResult(boolean success, long fencingToken) {}
```
> 참조: `ticket-service/src/main/java/guru/urr/ticketservice/domain/seat/service/SeatLockService.java:23`

#### seat_lock_release.lua

```lua
-- seat_lock_release.lua (전체)
local currentUserId = redis.call('HGET', seatKey, 'userId')
local currentToken = redis.call('HGET', seatKey, 'token')

-- userId + token 동시 검증 후 해제
if currentUserId ~= userId or currentToken ~= token then
    return 0
end

redis.call('DEL', seatKey)
return 1
```
> 참조: `ticket-service/src/main/resources/redis/seat_lock_release.lua:1-18`

userId와 token을 모두 검증하여 잘못된 해제를 방지한다.

#### payment_verify.lua

```lua
-- payment_verify.lua (전체)
local currentUserId = redis.call('HGET', seatKey, 'userId')
local currentToken = redis.call('HGET', seatKey, 'token')

-- 사용자와 펜싱 토큰 동시 검증
if currentUserId ~= userId or currentToken ~= token then
    return 0  -- 실패: 잠금 만료 또는 탈취
end

-- CONFIRMED로 상태 변경 (결제 처리 중 해제 방지)
redis.call('HSET', seatKey, 'status', 'CONFIRMED')
return 1
```
> 참조: `ticket-service/src/main/resources/redis/payment_verify.lua:1-19`

결제 확인 시점에 잠금이 여전히 유효한지 검증하고, 상태를 `CONFIRMED`로 변경하여 결제 처리 중 다른 사용자의 접근을 차단한다.

### 2.2 Layer 2: DB 비관적 잠금 (FOR UPDATE)

Redis 잠금 획득 후 DB 수준에서도 `SELECT ... FOR UPDATE`로 비관적 잠금을 건다.

```java
// ReservationService.java:92-99
List<Map<String, Object>> seats = namedParameterJdbcTemplate.queryForList("""
    SELECT id, seat_label, price, status, version
    FROM seats
    WHERE id IN (:seatIds) AND event_id = :eventId
    FOR UPDATE
    """, new MapSqlParameterSource()
    .addValue("seatIds", request.seatIds())
    .addValue("eventId", request.eventId()));
```
> 참조: `ticket-service/src/main/java/guru/urr/ticketservice/domain/reservation/service/ReservationService.java:92-99`

DB 잠금 후 좌석 상태를 확인하고, `version` 필드를 사용한 낙관적 잠금을 추가로 적용한다.

```java
// ReservationService.java:117-133
for (int i = 0; i < seats.size(); i++) {
    Map<String, Object> seat = seats.get(i);
    int currentVersion = ((Number) seat.get("version")).intValue();
    long fencingToken = lockResults.get(i).fencingToken();

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
}
```
> 참조: `ticket-service/src/main/java/guru/urr/ticketservice/domain/reservation/service/ReservationService.java:117-133`

`version` 필드를 `WHERE` 조건에 포함하여 동시 수정 감지(낙관적 잠금 폴백)를 수행하며, 업데이트 성공 시 `version + 1`로 증가시킨다. 펜싱 토큰도 DB에 기록하여 결제 시 검증에 사용한다.

### 2.3 Layer 3: 멱등성 키

`reservations` 테이블의 `idempotency_key` 컬럼으로 중복 예매를 방지한다.

```java
// ReservationService.java:62-69
if (request.idempotencyKey() != null && !request.idempotencyKey().isBlank()) {
    List<Map<String, Object>> existing = jdbcTemplate.queryForList(
        "SELECT id, reservation_number, total_amount, status, payment_status, expires_at FROM reservations WHERE idempotency_key = ?",
        request.idempotencyKey());
    if (!existing.isEmpty()) {
        return Map.of("message", "Seat reserved temporarily", "reservation", existing.getFirst());
    }
}
```
> 참조: `ticket-service/src/main/java/guru/urr/ticketservice/domain/reservation/service/ReservationService.java:62-69`

동일한 `idempotency_key`로 재요청 시 기존 예매 정보를 그대로 반환하며, Redis 잠금이나 DB 잠금을 다시 시도하지 않는다. 네트워크 재시도, 중복 클릭 등으로 인한 이중 예매를 원천 차단한다.

### 2.4 전체 잠금 흐름

`reserveSeats()` 메서드의 전체 흐름은 다음과 같다.

> 참조: `ticket-service/src/main/java/guru/urr/ticketservice/domain/reservation/service/ReservationService.java:56-167`

```
1. 멱등성 확인 (idempotency_key 조회)
   └─ 기존 예매 존재 시 → 즉시 반환

2. Phase 1: Redis Lua 잠금 획득 (좌석별)
   ├─ seat_lock_acquire.lua 실행
   ├─ 실패 시 → 이미 획득한 잠금 모두 해제 + CONFLICT 응답
   └─ 성공 시 → fencingToken 수집

3. Phase 2: DB FOR UPDATE 잠금
   ├─ SELECT ... FOR UPDATE (비관적 잠금)
   ├─ 좌석 상태 확인 (available인지 검증)
   └─ UPDATE seats SET status='locked', version=version+1, fencing_token=?

4. Phase 3: 예매 + 아이템 INSERT
   ├─ INSERT INTO reservations (status='pending', expires_at=NOW()+5분)
   └─ INSERT INTO reservation_items (좌석별)

5. 응답 반환 (reservationId, fencingToken 포함)

6. 실패 시: 모든 Redis 잠금 해제
```

```java
// ReservationService.java:169-174 (잠금 해제 헬퍼)
private void releaseLocks(UUID eventId, List<UUID> seatIds, String userId,
                           List<SeatLockService.SeatLockResult> lockResults) {
    for (int i = 0; i < lockResults.size(); i++) {
        seatLockService.releaseLock(eventId, seatIds.get(i), userId, lockResults.get(i).fencingToken());
    }
}
```
> 참조: `ticket-service/src/main/java/guru/urr/ticketservice/domain/reservation/service/ReservationService.java:169-174`

---

## 3. 예매 플로우

### 3.1 전체 순서

```
클라이언트 → 게이트웨이(Rate Limit + VWR 토큰 검증) → ticket-service
```

게이트웨이에서 서비스별 Rate Limit을 적용한다.

```yaml
# gateway-service/src/main/resources/application.yml:115-119
rate-limit:
  auth-rpm: ${RATE_LIMIT_AUTH_RPM:60}
  queue-rpm: ${RATE_LIMIT_QUEUE_RPM:120}
  booking-rpm: ${RATE_LIMIT_BOOKING_RPM:30}
  general-rpm: ${RATE_LIMIT_GENERAL_RPM:3000}
```
> 참조: `gateway-service/src/main/resources/application.yml:115-119`

좌석 예매의 경우 `POST /api/seats/reserve` 엔드포인트를 통해 진입한다.

```java
// SeatController.java:43-49
@PostMapping("/reserve")
public Map<String, Object> reserve(
    HttpServletRequest request,
    @Valid @RequestBody SeatReserveRequest body
) {
    AuthUser user = jwtTokenParser.requireUser(request);
    return reservationService.reserveSeats(user.userId(), body);
}
```
> 참조: `ticket-service/src/main/java/guru/urr/ticketservice/domain/seat/controller/SeatController.java:43-49`

일반 예매의 경우 `POST /api/reservations` 엔드포인트를 사용한다.

```java
// ReservationController.java:30-37
@PostMapping
public Map<String, Object> create(
    HttpServletRequest request,
    @Valid @RequestBody CreateReservationRequest body
) {
    AuthUser user = jwtTokenParser.requireUser(request);
    return reservationService.createReservation(user.userId(), body);
}
```
> 참조: `ticket-service/src/main/java/guru/urr/ticketservice/domain/reservation/controller/ReservationController.java:30-37`

### 3.2 ReservationService.reserveSeats() 상세

`@Transactional`로 전체 과정을 단일 트랜잭션으로 처리한다. 앞서 2.4절에서 설명한 3-Layer Locking 흐름을 따르며, 최종적으로 예매 레코드가 `pending` 상태로 생성된다.

```java
// ReservationService.java:136-137
int totalAmount = seats.stream().mapToInt(s -> ((Number) s.get("price")).intValue()).sum();
OffsetDateTime expiresAt = OffsetDateTime.now().plusMinutes(5);
```
> 참조: `ticket-service/src/main/java/guru/urr/ticketservice/domain/reservation/service/ReservationService.java:136-137`

예매 만료 시간은 생성 시점으로부터 **5분**이다. 좌석별 1석 제한이 적용된다.

```java
// ReservationService.java:35
private static final int MAX_SEATS_PER_RESERVATION = 1;
```
> 참조: `ticket-service/src/main/java/guru/urr/ticketservice/domain/reservation/service/ReservationService.java:35`

### 3.3 만료 처리: ReservationCleanupScheduler

`ReservationCleanupScheduler`가 30초 주기로 만료된 pending 예매를 정리한다.

```java
// ReservationCleanupScheduler.java:35-36
@Scheduled(fixedRateString = "${reservation.cleanup.interval-ms:30000}")
@Transactional
public void cleanupExpiredReservations() {
```
> 참조: `ticket-service/src/main/java/guru/urr/ticketservice/scheduling/ReservationCleanupScheduler.java:35-36`

**처리 흐름:**

```java
// ReservationCleanupScheduler.java:39-45
List<Map<String, Object>> expired = jdbcTemplate.queryForList("""
    SELECT id, event_id
    FROM reservations
    WHERE status = 'pending'
      AND expires_at < NOW()
    FOR UPDATE SKIP LOCKED
    """);
```
> 참조: `ticket-service/src/main/java/guru/urr/ticketservice/scheduling/ReservationCleanupScheduler.java:39-45`

`FOR UPDATE SKIP LOCKED`를 사용하여 이미 다른 프로세스가 처리 중인 행은 건너뛰고 교착 상태를 방지한다.

만료된 예매마다 다음 처리를 수행한다.

```java
// ReservationCleanupScheduler.java:62-89
// 좌석: available로 복원, version 증가, fencing_token 초기화, Redis 잠금 해제
if (seatId != null) {
    jdbcTemplate.update("""
        UPDATE seats SET status = 'available', version = version + 1,
        fencing_token = 0, locked_by = NULL, updated_at = NOW()
        WHERE id = ?
        """, seatId);
    seatLockService.cleanupLock(eventId, (UUID) seatId);
}
// 티켓 타입: 수량 복원
if (ticketTypeId != null) {
    jdbcTemplate.update(
        "UPDATE ticket_types SET available_quantity = available_quantity + ? WHERE id = ?",
        quantity, ticketTypeId);
}
// 예매 상태: expired로 변경
jdbcTemplate.update(
    "UPDATE reservations SET status = 'expired', updated_at = NOW() WHERE id = ?",
    reservationId);
```
> 참조: `ticket-service/src/main/java/guru/urr/ticketservice/scheduling/ReservationCleanupScheduler.java:62-89`

### 3.4 예매 확인: PaymentEventConsumer

Kafka `payment-events` 토픽을 구독하여 결제 확인 이벤트를 처리한다.

```java
// PaymentEventConsumer.java:49-50
@KafkaListener(topics = "payment-events", groupId = "ticket-service-group")
public void handlePaymentEvent(Map<String, Object> event) {
```
> 참조: `ticket-service/src/main/java/guru/urr/ticketservice/messaging/PaymentEventConsumer.java:49-50`

이벤트 타입별 분기 처리를 수행한다.

```java
// PaymentEventConsumer.java:60-69
String type = str(event.get("type"));
if ("PAYMENT_REFUNDED".equals(type)) {
    handleRefund(event);
} else if ("PAYMENT_CONFIRMED".equals(type)) {
    String paymentType = str(event.get("paymentType"));
    switch (paymentType != null ? paymentType : "reservation") {
        case "transfer" -> handleTransferPayment(event);
        case "membership" -> handleMembershipPayment(event);
        default -> handleReservationPayment(event);
    }
}
```
> 참조: `ticket-service/src/main/java/guru/urr/ticketservice/messaging/PaymentEventConsumer.java:60-69`

일반 예매 결제 확인 시:

```java
// PaymentEventConsumer.java:95-116
private void handleReservationPayment(Map<String, Object> event) {
    UUID reservationId = uuid(event.get("reservationId"));
    String paymentMethod = str(event.get("paymentMethod"));
    // ...
    reservationService.confirmReservationPayment(reservationId, paymentMethod);
    metrics.recordReservationConfirmed();
    // ...
    ticketEventProducer.publishReservationConfirmed(new ReservationConfirmedEvent(...));
}
```
> 참조: `ticket-service/src/main/java/guru/urr/ticketservice/messaging/PaymentEventConsumer.java:95-116`

**멱등성 보장:** `processed_events` 테이블을 사용하여 중복 이벤트 처리를 방지한다.

```java
// PaymentEventConsumer.java:209-219
private boolean isAlreadyProcessed(String eventKey) {
    try {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM processed_events WHERE event_key = ? AND consumer_group = ?",
            Integer.class, eventKey, CONSUMER_GROUP);
        return count != null && count > 0;
    } catch (Exception e) {
        return false;
    }
}
```
> 참조: `ticket-service/src/main/java/guru/urr/ticketservice/messaging/PaymentEventConsumer.java:209-219`

### 3.5 결제 확인 시 잠금 검증

`confirmReservationPayment()`에서 결제 확인 전 Redis 펜싱 토큰을 재검증한다.

```java
// ReservationService.java:391-418
public void confirmReservationPayment(UUID reservationId, String paymentMethod) {
    // 예매 + 이벤트 정보 조회
    List<Map<String, Object>> resRows = jdbcTemplate.queryForList(
        "SELECT r.user_id, r.event_id, e.artist_id FROM reservations r JOIN events e ON r.event_id = e.id WHERE r.id = ?",
        reservationId);

    // 좌석별 펜싱 토큰 검증
    for (Map<String, Object> item : items) {
        if (seatIdObj != null && tokenObj != null) {
            UUID seatId = (UUID) seatIdObj;
            long token = ((Number) tokenObj).longValue();
            if (token > 0 && !seatLockService.verifyForPayment(eventId, seatId, userId, token)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Seat lock expired or stolen. Please try again.");
            }
        }
    }

    // 예매 상태 업데이트: pending -> confirmed
    int updated = jdbcTemplate.update("""
        UPDATE reservations
        SET status = 'confirmed', payment_status = 'completed', payment_method = ?, updated_at = NOW()
        WHERE id = ? AND status = 'pending'
        """, paymentMethod, reservationId);
}
```
> 참조: `ticket-service/src/main/java/guru/urr/ticketservice/domain/reservation/service/ReservationService.java:391-418`

---

## 4. 결제 시스템

### 4.1 결제 유형

`PaymentService`는 세 가지 결제 유형을 지원한다.

```java
// PaymentService.java:57-77
switch (paymentType) {
    case "transfer" -> {
        // 양도 구매 결제
        Map<String, Object> transfer = ticketInternalClient.validateTransfer(referenceId, userId);
        validatedAmount = requiredInt(transfer, "total_amount", "totalAmount");
    }
    case "membership" -> {
        // 멤버십 구독 결제
        Map<String, Object> membership = ticketInternalClient.validateMembership(referenceId, userId);
        validatedAmount = requiredInt(membership, "total_amount", "totalAmount");
    }
    default -> {
        // 일반 예매 결제 (reservation)
        Map<String, Object> reservation = ticketInternalClient.validateReservation(request.reservationId(), userId);
        validatedAmount = requiredInt(reservation, "total_amount", "totalAmount");
    }
}
```
> 참조: `payment-service/src/main/java/guru/urr/paymentservice/service/PaymentService.java:57-77`

| 유형 | `paymentType` 값 | 참조 ID 필드 | 검증 대상 |
|------|------------------|-------------|-----------|
| 일반 예매 | `reservation` (기본값) | `reservationId` | 예매 상태 + 금액 |
| 양도 구매 | `transfer` | `referenceId` | 양도 상태 + 금액 |
| 멤버십 구독 | `membership` | `referenceId` | 멤버십 상태 + 가격 |

### 4.2 결제 흐름

**Step 1: 결제 초기화 (prepare)**

```
POST /api/payments/prepare
```
> 참조: `payment-service/src/main/java/guru/urr/paymentservice/controller/PaymentController.java:33-40`

```java
// PaymentService.java:49-118
@Transactional
public Map<String, Object> prepare(String userId, PreparePaymentRequest request) {
    // 1. 결제 유형별 ticket-service 내부 API로 유효성 검증
    // 2. 금액 불일치 확인
    if (validatedAmount != request.amount()) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Amount mismatch");
    }
    // 3. 기존 결제 중복 확인
    // 4. 주문번호 생성 + payments 레코드 INSERT (status: pending)
    String orderId = "ORD_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    // 5. orderId, amount, clientKey 반환 (프론트엔드 Toss Payments SDK용)
    return Map.of("orderId", orderId, "amount", request.amount(), "clientKey", tossClientKey);
}
```
> 참조: `payment-service/src/main/java/guru/urr/paymentservice/service/PaymentService.java:49-118`

PreparePaymentRequest DTO:

```java
// PreparePaymentRequest.java:7-12
public record PreparePaymentRequest(
    UUID reservationId,
    @NotNull @Min(1) Integer amount,
    String paymentType,
    UUID referenceId
) {}
```
> 참조: `payment-service/src/main/java/guru/urr/paymentservice/dto/PreparePaymentRequest.java:7-12`

**Step 2: 결제 확인 (confirm)**

```
POST /api/payments/confirm
```
> 참조: `payment-service/src/main/java/guru/urr/paymentservice/controller/PaymentController.java:42-49`

```java
// PaymentService.java:120-173
@Transactional
public Map<String, Object> confirm(String userId, ConfirmPaymentRequest request) {
    // 1. order_id로 결제 조회 (FOR UPDATE)
    List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
        SELECT id, reservation_id, user_id, amount, status, payment_type, reference_id
        FROM payments
        WHERE order_id = ?
        FOR UPDATE
        """, request.orderId());

    // 2. 소유자 확인, 금액 확인, 이미 확인된 결제 방어
    // 3. payments 테이블 업데이트 (status: confirmed, toss_status: DONE)
    jdbcTemplate.update("""
        UPDATE payments
        SET payment_key = ?, method = 'toss', status = 'confirmed',
            toss_status = 'DONE', toss_approved_at = ?, updated_at = NOW()
        WHERE id = ?
        """, request.paymentKey(), now, payment.get("id"));

    // 4. 유형별 완료 처리
    completeByType(paymentType, payment, userId, "toss");
}
```
> 참조: `payment-service/src/main/java/guru/urr/paymentservice/service/PaymentService.java:120-173`

**Step 3: 유형별 완료 처리 (completeByType)**

동기 확인(RestClient)과 비동기 확인(Kafka)을 이중으로 수행한다.

```java
// PaymentService.java:343-376
private void completeByType(String paymentType, Map<String, Object> payment, String userId, String paymentMethod) {
    // 동기 확인: ticket-service 내부 API 직접 호출
    try {
        switch (paymentType) {
            case "transfer" -> {
                if (referenceId != null) ticketInternalClient.confirmTransfer(referenceId, userId, paymentMethod);
            }
            case "membership" -> {
                if (referenceId != null) ticketInternalClient.activateMembership(referenceId);
            }
            default -> {
                if (reservationId != null) ticketInternalClient.confirmReservation(reservationId, paymentMethod);
            }
        }
    } catch (Exception e) {
        log.warn("Synchronous confirmation failed for {} {}, falling back to Kafka: {}",
            paymentType, reservationId != null ? reservationId : referenceId, e.getMessage());
    }

    // 비동기 확인: Kafka payment-events 토픽 발행
    paymentEventProducer.publish(new PaymentConfirmedEvent(
        paymentId, orderId, userId, reservationId, referenceId,
        paymentType, amount, paymentMethod, Instant.now()));
}
```
> 참조: `payment-service/src/main/java/guru/urr/paymentservice/service/PaymentService.java:343-376`

동기 호출이 실패해도 Kafka 이벤트를 통해 eventual consistency를 보장한다. 또한 5분 주기의 `PaymentReconciliationScheduler`가 추가 안전망으로 동작한다.

```java
// PaymentReconciliationScheduler.java:36-37
@Scheduled(fixedRateString = "${reservation.reconciliation.interval-ms:300000}")
public void reconcilePendingReservations() {
```
> 참조: `ticket-service/src/main/java/guru/urr/ticketservice/scheduling/PaymentReconciliationScheduler.java:36-37`

### 4.3 결제 상태 관리

**payments 테이블 주요 필드:**

```java
// PaymentService.java:110-115
jdbcTemplate.update("""
    INSERT INTO payments (reservation_id, user_id, event_id, order_id, amount, status, payment_type, reference_id)
    VALUES (CAST(? AS UUID), CAST(? AS UUID), CAST(? AS UUID), ?, ?, 'pending', ?, CAST(? AS UUID))
    """,
    reservationIdText, userId, eventIdText, orderId, request.amount(),
    paymentType, referenceId != null ? referenceId.toString() : null);
```
> 참조: `payment-service/src/main/java/guru/urr/paymentservice/service/PaymentService.java:110-115`

| 필드 | 용도 |
|------|------|
| `order_id` | `ORD_` 접두사 + 타임스탬프 + UUID 조각 (고유 주문번호) |
| `payment_key` | Toss Payments에서 발급한 결제 키 |
| `status` | `pending` -> `confirmed` -> `refunded` |
| `toss_status` | Toss Payments API 응답 상태 (`DONE` 등) |
| `payment_type` | `reservation` / `transfer` / `membership` |
| `reference_id` | 양도 또는 멤버십 참조 ID |

**Kafka 이벤트 구조 (PaymentConfirmedEvent):**

```java
// PaymentConfirmedEvent.java:6-24
public record PaymentConfirmedEvent(
    String type,           // "PAYMENT_CONFIRMED"
    UUID paymentId,
    String orderId,
    String userId,
    UUID reservationId,
    UUID referenceId,
    String paymentType,    // reservation / transfer / membership
    int amount,
    String paymentMethod,
    Instant timestamp
) {
    public PaymentConfirmedEvent(UUID paymentId, String orderId, String userId,
                                  UUID reservationId, UUID referenceId, String paymentType,
                                  int amount, String paymentMethod, Instant timestamp) {
        this("PAYMENT_CONFIRMED", paymentId, orderId, userId, reservationId,
             referenceId, paymentType, amount, paymentMethod, timestamp);
    }
}
```
> 참조: `payment-service/src/main/java/guru/urr/paymentservice/messaging/event/PaymentConfirmedEvent.java:6-24`

**결제 취소/환불:**

```java
// PaymentService.java:194-228
@Transactional
public Map<String, Object> cancel(String userId, String paymentKey, CancelPaymentRequest request) {
    // payment_key로 조회 (FOR UPDATE)
    // confirmed 상태인지 확인
    // status: refunded, refund_amount, refund_reason 업데이트
    // Kafka PaymentRefundedEvent 발행
    paymentEventProducer.publishRefund(new PaymentRefundedEvent(...));
}
```
> 참조: `payment-service/src/main/java/guru/urr/paymentservice/service/PaymentService.java:194-228`

---

## 5. 양도 (Ticket Transfer) 시스템

### 5.1 양도 등록

`POST /api/transfers` 엔드포인트를 통해 양도 등록을 수행한다.

> 참조: `ticket-service/src/main/java/guru/urr/ticketservice/domain/transfer/controller/TransferController.java:29-37`

```java
// TransferService.java:30-97
@Transactional
public Map<String, Object> createListing(String userId, UUID reservationId) {
```
> 참조: `ticket-service/src/main/java/guru/urr/ticketservice/domain/transfer/service/TransferService.java:30-97`

**검증 단계:**

1. **예매 소유자 확인:** 예매의 `user_id`가 요청자와 일치해야 한다.

```java
// TransferService.java:45-46
if (!String.valueOf(res.get("user_id")).equals(userId)) {
    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not your reservation");
}
```
> 참조: `ticket-service/src/main/java/guru/urr/ticketservice/domain/transfer/service/TransferService.java:45-46`

2. **confirmed 상태 확인:** 결제가 완료된 예매만 양도 가능하다.

```java
// TransferService.java:48-49
if (!"confirmed".equals(String.valueOf(res.get("status")))) {
    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only confirmed reservations can be transferred");
}
```
> 참조: `ticket-service/src/main/java/guru/urr/ticketservice/domain/transfer/service/TransferService.java:48-49`

3. **기존 등록 중복 확인:**

```java
// TransferService.java:56-61
Integer existingCount = jdbcTemplate.queryForObject(
    "SELECT COUNT(*) FROM ticket_transfers WHERE reservation_id = ? AND status = 'listed'",
    Integer.class, reservationId);
if (existingCount != null && existingCount > 0) {
    throw new ResponseStatusException(HttpStatus.CONFLICT, "Transfer already listed for this reservation");
}
```
> 참조: `ticket-service/src/main/java/guru/urr/ticketservice/domain/transfer/service/TransferService.java:56-61`

4. **멤버십 등급별 수수료 계산:**

```java
// TransferService.java:64-79
int feePercent = 10;
if (artistId != null) {
    List<Map<String, Object>> memberRows = jdbcTemplate.queryForList(
        "SELECT id, tier, points, status FROM artist_memberships WHERE user_id = CAST(? AS UUID) AND artist_id = ? AND status = 'active'",
        userId, artistId);
    if (memberRows.isEmpty()) {
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Active membership required for this artist");
    }

    int points = ((Number) memberRows.getFirst().get("points")).intValue();
    String effectiveTier = computeEffectiveTier(points);

    if ("BRONZE".equals(effectiveTier)) {
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bronze tier cannot transfer tickets");
    }
    feePercent = "SILVER".equals(effectiveTier) ? 10 : 5;
}
```
> 참조: `ticket-service/src/main/java/guru/urr/ticketservice/domain/transfer/service/TransferService.java:64-79`

| 등급 | 양도 가능 여부 | 수수료 |
|------|-------------|--------|
| BRONZE | 불가 (403 Forbidden) | - |
| SILVER | 가능 | 10% |
| GOLD | 가능 | 5% |
| DIAMOND | 가능 | 5% |

**가격 산출:**

```java
// TransferService.java:82-84
int originalPrice = ((Number) res.get("total_amount")).intValue();
int transferFee = originalPrice * feePercent / 100;
int totalPrice = originalPrice + transferFee;
```
> 참조: `ticket-service/src/main/java/guru/urr/ticketservice/domain/transfer/service/TransferService.java:82-84`

### 5.2 양도 구매

양도 구매는 결제 시스템을 통해 처리되며, `PAYMENT_CONFIRMED` 이벤트의 `paymentType: "transfer"`로 분기된다.

**구매 검증:**

```java
// TransferService.java:200-235
public Map<String, Object> validateForPurchase(UUID transferId, String buyerId) {
    // listed 상태 확인
    if (!"listed".equals(String.valueOf(transfer.get("status")))) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Transfer is no longer available");
    }
    // 자기 양도 구매 방지
    if (String.valueOf(transfer.get("seller_id")).equals(buyerId)) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot buy your own transfer");
    }
    // 구매자 멤버십 확인
    if (artistId != null) {
        Integer memberCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM artist_memberships WHERE user_id = CAST(? AS UUID) AND artist_id = ? AND status = 'active'",
            Integer.class, buyerId, artistId);
        if (memberCount == null || memberCount == 0) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Buyer must have active membership for this artist");
        }
    }
}
```
> 참조: `ticket-service/src/main/java/guru/urr/ticketservice/domain/transfer/service/TransferService.java:200-235`

**구매 완료 (completePurchase):**

```java
// TransferService.java:237-263
@Transactional
public void completePurchase(UUID transferId, String buyerId, String paymentMethod) {
    // FOR UPDATE 잠금
    List<Map<String, Object>> rows = jdbcTemplate.queryForList(
        "SELECT id, reservation_id, seller_id, status FROM ticket_transfers WHERE id = ? FOR UPDATE", transferId);

    // listed 상태 확인
    UUID reservationId = (UUID) transfer.get("reservation_id");

    // 소유권 이전: reservation의 user_id를 구매자로 변경
    jdbcTemplate.update(
        "UPDATE reservations SET user_id = CAST(? AS UUID), updated_at = NOW() WHERE id = ?",
        buyerId, reservationId);

    // 양도 상태: completed로 변경
    jdbcTemplate.update("""
        UPDATE ticket_transfers
        SET status = 'completed', buyer_id = CAST(? AS UUID), completed_at = NOW(), updated_at = NOW()
        WHERE id = ?
        """, buyerId, transferId);
}
```
> 참조: `ticket-service/src/main/java/guru/urr/ticketservice/domain/transfer/service/TransferService.java:237-263`

**Kafka 이벤트 발행:**

양도 완료 시 `PaymentEventConsumer`에서 `TransferCompletedEvent`를 발행한다.

```java
// PaymentEventConsumer.java:149-150
ticketEventProducer.publishTransferCompleted(new TransferCompletedEvent(
    referenceId, reservationId, sellerId, userId, amount, Instant.now()));
```
> 참조: `ticket-service/src/main/java/guru/urr/ticketservice/messaging/PaymentEventConsumer.java:149-150`

---

## 6. 멤버십 & 포인트 시스템

### 6.1 멤버십 구조

멤버십은 **아티스트별 구독** 모델을 따른다. `artist_memberships` 테이블에 사용자-아티스트 관계가 저장된다.

```java
// MembershipService.java:65-71
Map<String, Object> row = jdbcTemplate.queryForList("""
    INSERT INTO artist_memberships (user_id, artist_id, tier, points, status, expires_at)
    VALUES (CAST(? AS UUID), ?, 'SILVER', 0, 'pending', NOW())
    RETURNING *
    """, userId, artistId)
    .stream().findFirst()
    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Failed to create membership"));
```
> 참조: `ticket-service/src/main/java/guru/urr/ticketservice/domain/membership/service/MembershipService.java:65-71`

신규 가입 시 `tier: SILVER`, `points: 0`, `status: pending`으로 생성된다.

**등급 체계:**

```java
// MembershipService.java:23-25
private static final int GOLD_THRESHOLD = 500;
private static final int DIAMOND_THRESHOLD = 1500;
private static final int JOIN_BONUS_POINTS = 200;
```
> 참조: `ticket-service/src/main/java/guru/urr/ticketservice/domain/membership/service/MembershipService.java:23-25`

```java
// MembershipService.java:246-250
private String computeEffectiveTier(int points) {
    if (points >= DIAMOND_THRESHOLD) return "DIAMOND";
    if (points >= GOLD_THRESHOLD) return "GOLD";
    return "SILVER";
}
```
> 참조: `ticket-service/src/main/java/guru/urr/ticketservice/domain/membership/service/MembershipService.java:246-250`

| 등급 | 포인트 범위 | 승급 임계값 |
|------|-----------|------------|
| SILVER | 0 ~ 499 | - (기본) |
| GOLD | 500 ~ 1,499 | 500점 |
| DIAMOND | 1,500+ | 1,500점 |

**상태 전이:** `pending` -> `active` (결제 완료 후)

```java
// MembershipService.java:78-101
@Transactional
public void activateMembership(UUID membershipId) {
    // pending 상태 확인
    // active로 변경, 만료일을 1년 후로 설정
    OffsetDateTime expiresAt = OffsetDateTime.now().plusYears(1);
    jdbcTemplate.update("""
        UPDATE artist_memberships
        SET status = 'active', expires_at = ?, joined_at = NOW(), updated_at = NOW()
        WHERE id = ?
        """, Timestamp.from(expiresAt.toInstant()), membershipId);

    // 가입 보너스 포인트 200점 지급
    addPoints(membershipId, actionType, JOIN_BONUS_POINTS, desc, null);
}
```
> 참조: `ticket-service/src/main/java/guru/urr/ticketservice/domain/membership/service/MembershipService.java:78-101`

### 6.2 포인트 적립

**포인트 적립 메커니즘:**

```java
// MembershipService.java:202-223
@Transactional
public void addPoints(UUID membershipId, String actionType, int points, String description, UUID referenceId) {
    // 1. membership_point_logs에 이력 기록
    jdbcTemplate.update("""
        INSERT INTO membership_point_logs (membership_id, action_type, points, description, reference_id)
        VALUES (?, ?, ?, ?, ?)
        """, membershipId, actionType, points, description, referenceId);

    // 2. 멤버십 포인트 누적
    jdbcTemplate.update(
        "UPDATE artist_memberships SET points = points + ?, updated_at = NOW() WHERE id = ?",
        points, membershipId);

    // 3. 등급 재계산 및 업데이트
    Integer totalPoints = jdbcTemplate.queryForObject(
        "SELECT points FROM artist_memberships WHERE id = ?", Integer.class, membershipId);
    String newTier = computeEffectiveTier(totalPoints);
    jdbcTemplate.update(
        "UPDATE artist_memberships SET tier = ?, updated_at = NOW() WHERE id = ?",
        newTier, membershipId);
}
```
> 참조: `ticket-service/src/main/java/guru/urr/ticketservice/domain/membership/service/MembershipService.java:202-223`

**특정 아티스트 멤버십에 포인트 부여:**

```java
// MembershipService.java:225-235
public void awardPointsForArtist(String userId, UUID artistId, String actionType, int points, String description, UUID referenceId) {
    List<Map<String, Object>> rows = jdbcTemplate.queryForList(
        "SELECT id FROM artist_memberships WHERE user_id = CAST(? AS UUID) AND artist_id = ? AND status = 'active'",
        userId, artistId);
    if (rows.isEmpty()) {
        log.debug("No active membership for user {} artist {}, skipping points", userId, artistId);
        return;
    }
    UUID membershipId = (UUID) rows.getFirst().get("id");
    addPoints(membershipId, actionType, points, description, referenceId);
}
```
> 참조: `ticket-service/src/main/java/guru/urr/ticketservice/domain/membership/service/MembershipService.java:225-235`

**전체 멤버십에 포인트 부여:**

```java
// MembershipService.java:237-244
public void awardPointsToAllMemberships(String userId, String actionType, int points, String description, UUID referenceId) {
    List<Map<String, Object>> rows = jdbcTemplate.queryForList(
        "SELECT id FROM artist_memberships WHERE user_id = CAST(? AS UUID) AND status = 'active'", userId);
    for (Map<String, Object> row : rows) {
        UUID membershipId = (UUID) row.get("id");
        addPoints(membershipId, actionType, points, description, referenceId);
    }
}
```
> 참조: `ticket-service/src/main/java/guru/urr/ticketservice/domain/membership/service/MembershipService.java:237-244`

**커뮤니티 활동 포인트:** community-service에서 ticket-service의 내부 API를 호출하여 포인트를 적립한다.

```java
// community-service/.../shared/client/TicketInternalClient.java:42-58
@CircuitBreaker(name = "internalService", fallbackMethod = "awardMembershipPointsArtistFallback")
public void awardMembershipPoints(String userId, UUID artistId, String actionType,
                                   int points, String description, UUID referenceId) {
    Map<String, Object> body = new java.util.HashMap<>();
    body.put("userId", userId);
    body.put("actionType", actionType);
    body.put("points", points);
    body.put("description", description);
    body.put("referenceId", referenceId);
    if (artistId != null) {
        body.put("artistId", artistId);
    }
    restClient.post()
        .uri("/internal/memberships/award-points")
        .header("Authorization", "Bearer " + internalApiToken)
        .body(body)
        .retrieve()
        .toBodilessEntity();
}
```
> 참조: `community-service/src/main/java/guru/urr/communityservice/shared/client/TicketInternalClient.java:42-58`

Circuit Breaker 패턴을 적용하여 ticket-service 장애 시 fallback으로 경고 로그만 남기고 실패를 허용한다. `@Retry` 어노테이션은 의도적으로 적용하지 않았다(POST는 멱등하지 않으므로 재시도 시 포인트 중복 적립 위험).

> 참조: `community-service/src/main/java/guru/urr/communityservice/shared/client/TicketInternalClient.java:41`

**내부 API 엔드포인트:**

```java
// InternalMembershipController.java:26-46
@PostMapping("/award-points")
public Map<String, Object> awardPoints(
        @RequestBody AwardPointsRequest request,
        @RequestHeader(value = "Authorization", required = false) String authorization) {
    internalTokenValidator.requireValidToken(authorization);
    try {
        if (request.artistId() != null) {
            membershipService.awardPointsForArtist(
                request.userId(), request.artistId(), request.actionType(),
                request.points(), request.description(), request.referenceId());
        } else {
            membershipService.awardPointsToAllMemberships(
                request.userId(), request.actionType(), request.points(),
                request.description(), request.referenceId());
        }
        return Map.of("ok", true);
    } catch (Exception e) {
        return Map.of("ok", false, "error", e.getMessage());
    }
}
```
> 참조: `ticket-service/src/main/java/guru/urr/ticketservice/internal/controller/InternalMembershipController.java:26-46`

요청 DTO:

```java
// AwardPointsRequest.java:5-12
public record AwardPointsRequest(
    String userId,
    String actionType,
    int points,
    String description,
    UUID referenceId,
    UUID artistId  // null이면 전체 멤버십에 포인트 부여
) {}
```
> 참조: `ticket-service/src/main/java/guru/urr/ticketservice/internal/dto/AwardPointsRequest.java:5-12`

**티켓 구매 시 포인트 적립:**

예매 확인 시 해당 아티스트 멤버십에 100포인트를 적립한다.

```java
// ReservationService.java:449-459
try {
    if (!resRows.isEmpty() && resRows.getFirst().get("artist_id") != null) {
        UUID artistId = (UUID) resRows.getFirst().get("artist_id");
        String userId = String.valueOf(resRows.getFirst().get("user_id"));
        membershipService.awardPointsForArtist(userId, artistId, "TICKET_PURCHASE", 100,
            "Points for ticket purchase", reservationId);
    }
} catch (Exception e) {
    log.warn("Failed to award membership points for reservation {}: {}", reservationId, e.getMessage());
}
```
> 참조: `ticket-service/src/main/java/guru/urr/ticketservice/domain/reservation/service/ReservationService.java:449-459`

### 6.3 멤버십 혜택

등급별 혜택은 `getBenefitsForTier()` 메서드에서 정의한다.

```java
// MembershipService.java:252-290
private Map<String, Object> getBenefitsForTier(String tier) {
    Map<String, Object> benefits = new LinkedHashMap<>();
    switch (tier) {
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
        default -> {  // BRONZE (비회원)
            benefits.put("preSalePhase", null);
            benefits.put("preSaleLabel", "일반예매");
            benefits.put("bookingFeeSurcharge", 0);
            benefits.put("transferAccess", false);
            benefits.put("transferFeePercent", null);
        }
    }
}
```
> 참조: `ticket-service/src/main/java/guru/urr/ticketservice/domain/membership/service/MembershipService.java:252-290`

| 등급 | 선예매 | 예매 수수료 | 양도 접근 | 양도 수수료 |
|------|--------|-----------|----------|------------|
| DIAMOND | 선예매 1 (최우선) | 1,000원 | 가능 | 5% |
| GOLD | 선예매 2 | 2,000원 | 가능 | 5% |
| SILVER | 선예매 3 | 3,000원 | 가능 | 10% |
| BRONZE (비회원) | 일반예매 | 0원 | 불가 | - |

---

## 7. 스케줄러 & 배치 처리

### 7.1 ReservationCleanupScheduler

**역할:** 만료된 pending 예매를 정리하여 좌석/티켓 수량을 복원한다.

**주기:** 30초 (`reservation.cleanup.interval-ms`)

```java
// ReservationCleanupScheduler.java:35
@Scheduled(fixedRateString = "${reservation.cleanup.interval-ms:30000}")
```
> 참조: `ticket-service/src/main/java/guru/urr/ticketservice/scheduling/ReservationCleanupScheduler.java:35`

**처리 대상:** `status = 'pending'` AND `expires_at < NOW()`

**처리 내용:**
1. 좌석: `available`로 복원, `version` 증가, `fencing_token` 초기화, Redis 잠금 해제
2. 티켓 타입: `available_quantity` 복원
3. 예매: `status = 'expired'`로 변경
4. 메트릭: `recordReservationExpired()` 기록

> 참조: `ticket-service/src/main/java/guru/urr/ticketservice/scheduling/ReservationCleanupScheduler.java:37-100`

`FOR UPDATE SKIP LOCKED`를 사용하여 다중 인스턴스 환경에서의 동시 처리 충돌을 방지한다.

### 7.2 PaymentReconciliationScheduler

**역할:** Kafka 이벤트 유실 시 결제 확인 누락을 보정한다.

**주기:** 5분 (`reservation.reconciliation.interval-ms`)

```java
// PaymentReconciliationScheduler.java:36
@Scheduled(fixedRateString = "${reservation.reconciliation.interval-ms:300000}")
```
> 참조: `ticket-service/src/main/java/guru/urr/ticketservice/scheduling/PaymentReconciliationScheduler.java:36`

**처리 대상:** `status = 'pending'` AND `payment_status = 'pending'` AND `created_at < NOW() - 5분` AND `expires_at > NOW()`

**처리 흐름:**
1. pending 예매 중 5분 이상 경과한 건을 최대 50건 조회
2. payment-service 내부 API로 해당 예매의 결제 상태 확인
3. 결제가 `confirmed` 상태이면 `confirmReservationPayment()` 호출

```java
// PaymentReconciliationScheduler.java:56-76
for (Map<String, Object> reservation : pendingReservations) {
    UUID reservationId = (UUID) reservation.get("id");
    try {
        Map<String, Object> paymentInfo = paymentInternalClient.getPaymentByReservation(reservationId);
        boolean found = Boolean.TRUE.equals(paymentInfo.get("found"));
        String status = String.valueOf(paymentInfo.get("status"));

        if (found && "confirmed".equals(status)) {
            reservationService.confirmReservationPayment(reservationId, method);
        }
    } catch (Exception e) {
        log.warn("Reconciliation: failed to check reservation {}: {}", reservationId, e.getMessage());
    }
}
```
> 참조: `ticket-service/src/main/java/guru/urr/ticketservice/scheduling/PaymentReconciliationScheduler.java:56-76`

### 7.3 AdmissionWorkerService (대기열 스케줄러)

**입장 배치 처리 주기:** 1초 (`queue.admission.interval-ms`)

> 참조: `queue-service/src/main/java/guru/urr/queueservice/service/AdmissionWorkerService.java:47`

**Stale 사용자 정리 주기:** 30초 (`queue.stale-cleanup.interval-ms`)

> 참조: `queue-service/src/main/java/guru/urr/queueservice/service/AdmissionWorkerService.java:120`

분산 환경에서 단일 이벤트에 대한 중복 처리를 방지하기 위해 Redis 기반 분산 잠금을 사용한다.

```java
// AdmissionWorkerService.java:65-75
String lockKey = "admission:lock:" + eventId;
Boolean acquired = redisTemplate.opsForValue().setIfAbsent(lockKey, "1",
    java.time.Duration.ofSeconds(4));

if (acquired == null || !acquired) {
    log.debug("Skipping event {} - lock held by another worker", eventId);
    continue;
}
```
> 참조: `queue-service/src/main/java/guru/urr/queueservice/service/AdmissionWorkerService.java:65-75`

### 7.4 스케줄러 구성 요약

| 스케줄러 | 서비스 | 주기 | 설정 키 |
|---------|--------|------|---------|
| `cleanupExpiredReservations` | ticket-service | 30초 | `reservation.cleanup.interval-ms` |
| `reconcilePendingReservations` | ticket-service | 5분 | `reservation.reconciliation.interval-ms` |
| `admitUsers` | queue-service | 1초 | `queue.admission.interval-ms` |
| `cleanupStaleUsers` | queue-service | 30초 | `queue.stale-cleanup.interval-ms` |
