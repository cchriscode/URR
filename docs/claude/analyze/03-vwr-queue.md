# URR 2-Tier VWR (Virtual Waiting Room) + Queue 시스템 분석

## 목차

1. [시스템 개요](#1-시스템-개요)
2. [Tier 1 -- CDN 레벨 VWR](#2-tier-1----cdn-레벨-vwr)
3. [Tier 2 -- 뒷단 대기열 (queue-service + Redis)](#3-tier-2----뒷단-대기열)
4. [2계층 토큰 플로우](#4-2계층-토큰-플로우)
5. [경로 보호 매트릭스](#5-경로-보호-매트릭스)
6. [유량 제어 전략](#6-유량-제어-전략)
7. [Tier 1 vs Tier 2 비교](#7-tier-1-vs-tier-2-비교)
8. [강점 및 약점 분석](#8-강점-및-약점-분석)

---

## 1. 시스템 개요

URR은 티켓팅 시스템의 트래픽 폭주를 2단계로 흡수하는 아키텍처를 채택하고 있다.

```
사용자 브라우저
     |
     v
+--------------------+
| CloudFront (CDN)   |    <-- Lambda@Edge: viewer-request
|  + Lambda@Edge     |        Tier 1 VWR 토큰 검증
+--------------------+        Tier 2 entryToken 검증
     |
     | Tier 1 VWR 활성 시 --> 302 /vwr/{eventId}
     |                        S3 정적 대기 페이지
     |                        API Gateway --> Lambda --> DynamoDB
     |
     v (Tier 1 통과)
+--------------------+
| ALB / Gateway      |    <-- VwrEntryTokenFilter
| (gateway-service)  |        Tier 2 entryToken 검증
+--------------------+
     |
     | entryToken 없으면 --> /queue/{eventId}
     |                       Next.js 대기 페이지
     |                       queue-service --> Redis
     |
     v (Tier 2 통과)
+--------------------+
| Backend Services   |    예약/좌석선택/결제 등
| (ticket, payment)  |
+--------------------+
```

**핵심 설계 원리**: Tier 1은 서버 인프라에 도달하기 전에 트래픽을 CDN 에지에서 차단하고, Tier 2는 서버에 도달한 인증된 사용자를 Redis 기반으로 세밀하게 유량 제어한다.

---

## 2. Tier 1 -- CDN 레벨 VWR

### 2.1 전체 구조

```
사용자 -----> CloudFront -----> Lambda@Edge (viewer-request)
                                     |
                         +-----------+-----------+
                         |                       |
                    VWR 비활성              VWR 활성
                    (통과)              urr-vwr-token 쿠키 확인
                                             |
                                   +---------+---------+
                                   |                   |
                              토큰 유효            토큰 없음/만료
                              (통과)              302 --> /vwr/{eventId}
                                                        |
                                                   S3 정적 페이지
                                                   (index.html)
                                                        |
                                              API Gateway --> Lambda
                                                        |
                                                   DynamoDB
                                              (순번 발급/확인)
```

### 2.2 정적 대기 페이지

**파일**: `apps/vwr/index.html`

S3에 호스팅되는 단일 HTML 파일로, 외부 의존성 없이 순수 JavaScript로 구현되어 있다.

**주요 메커니즘**:

| 기능 | 구현 방식 | 위치 (줄번호) |
|------|----------|-------------|
| eventId 추출 | URL 경로 파싱 (`/vwr/{eventId}`) | L154-155 |
| 세션 유지 | `localStorage` (`urr-vwr-{eventId}` 키) | L165, L171-181 |
| 익명 사용자 ID | `localStorage` (`urr-anon-id`) | L297-305 |
| 순번 요청 | `POST /vwr-api/vwr/assign/{eventId}` | L189 |
| 순번 확인 폴링 | `GET /vwr-api/vwr/check/{eventId}/{requestId}` | L228 |
| 입장 시 리다이렉트 | 쿠키 설정 후 `/events/{eventId}`로 이동 | L247-249 |
| 폴링 간격 | 서버 응답의 `nextPoll` 값 사용 (기본 5초) | L254, L262 |

**세션 복구 플로우**:
```
페이지 로드
  |
  v
localStorage에 requestId 존재?
  |               |
  Yes             No
  |               |
  v               v
이전 position으로  POST /vwr/assign/{eventId}
UI 표시 후        새 순번 발급 받기
폴링 재개
```

**입장 판정 시 동작** (L245-249):
```javascript
if (data.admitted && data.token) {
    setCookie('urr-vwr-token', data.token, 600);  // 10분 TTL
    localStorage.removeItem(STORAGE_KEY);
    window.location.href = '/events/' + eventId;
}
```

### 2.3 VWR API Lambda

**파일**: `lambda/vwr-api/index.js`

단일 Lambda 함수가 API Gateway의 모든 VWR 엔드포인트를 라우팅한다. 콜드 스타트 최소화를 위한 설계이다.

**라우팅 테이블**:

| 리소스 경로 | 메서드 | 핸들러 | 설명 |
|------------|--------|--------|------|
| `/vwr/assign/{eventId}` | POST | `handlers/assign.js` | 순번 발급 |
| `/vwr/check/{eventId}/{requestId}` | GET | `handlers/check.js` | 순번 확인 + 입장 판정 |
| `/vwr/status/{eventId}` | GET | `handlers/status.js` | 이벤트 VWR 현황 조회 |

#### 2.3.1 순번 발급 (assign)

**파일**: `lambda/vwr-api/handlers/assign.js`

```
POST /vwr/assign/{eventId}  { userId: "anon-xxx" }
     |
     v
getEventStatus(eventId)  -- DynamoDB counters 테이블 조회
     |
     v
isActive == false? --> 404 (VWR 비활성, 프론트에서 /events/{eventId}로 리다이렉트)
     |
     v
requestId = crypto.randomUUID()
     |
     v
assignPosition(eventId, requestId, userId)
     |
     +-- DynamoDB UpdateCommand: ADD nextPosition :one (원자적 카운터)
     +-- DynamoDB PutCommand: positions 테이블에 레코드 저장
     |
     v
응답: { requestId, position, estimatedWait, servingCounter }
```

**대기 시간 추정** (L28-30):
```javascript
const totalAhead = position - (status.servingCounter || 0);
// 500명/10초 배치 = 초당 50명 처리 기준
const estimatedWaitSeconds = Math.max(0, Math.ceil(totalAhead / 50));
```

#### 2.3.2 순번 확인 (check)

**파일**: `lambda/vwr-api/handlers/check.js`

```
GET /vwr/check/{eventId}/{requestId}?userId=xxx
     |
     v
getPositionAndCounter(eventId, requestId)
     -- positions + counters 테이블 동시 조회 (Promise.all)
     |
     v
position == null? --> 404 (만료됨, 재발급 필요)
     |
     v
isActive == false? --> admitted: true + JWT 발급 (VWR 비활성화 시 전원 통과)
     |
     v
position <= servingCounter?
     |               |
     Yes              No
     |               |
  admitted: true   admitted: false
  + JWT 토큰       + estimatedWait
                   + nextPoll (적응형)
```

**적응형 폴링 간격** (L47-51):
```
대기 인원 (ahead)     폴링 간격
<= 500               2초
<= 2,000             5초
<= 10,000            10초
> 10,000             15초
```

#### 2.3.3 현황 조회 (status)

**파일**: `lambda/vwr-api/handlers/status.js`

공개 API로 인증 없이 접근 가능하다. 이벤트의 전체 대기열 현황을 반환한다.

응답:
```json
{
    "eventId": "uuid",
    "isActive": true,
    "totalInQueue": 15000,
    "serving": 3000,
    "waitingCount": 12000
}
```

### 2.4 DynamoDB 테이블 설계

**파일**: `terraform/modules/dynamodb-vwr/main.tf`

#### counters 테이블 (`{prefix}-vwr-counters`)

| 속성 | 타입 | 역할 |
|------|------|------|
| `eventId` (PK) | S | 이벤트 식별자 |
| `nextPosition` | N | 다음 발급 순번 (원자적 카운터) |
| `servingCounter` | N | 현재 서비스 중인 순번까지 |
| `isActive` | BOOL | VWR 활성 여부 |
| `updatedAt` | N | 마지막 갱신 시각 (epoch ms) |

```
빌링 모드: PAY_PER_REQUEST (온디맨드)
PITR: 환경변수로 제어
```

#### positions 테이블 (`{prefix}-vwr-positions`)

| 속성 | 타입 | 역할 |
|------|------|------|
| `eventId` (PK) | S | 이벤트 식별자 |
| `requestId` (SK) | S | 요청별 고유 ID |
| `position` | N | 발급된 순번 |
| `userId` | S | 사용자 익명 ID |
| `createdAt` | N | 생성 시각 (epoch ms) |
| `ttl` | N | TTL (24시간 후 자동 삭제, epoch sec) |

```
GSI: eventId-position-index (eventId=Hash, position=Range, ALL 프로젝션)
TTL: ttl 속성 활성화 (24시간)
빌링 모드: PAY_PER_REQUEST
```

**원자적 순번 발급 메커니즘** (`lambda/vwr-api/lib/dynamo.js` L17-29):
```javascript
// DynamoDB의 ADD 연산은 원자적(atomic) — 동시 요청에도 순번 충돌 없음
await ddb.send(new UpdateCommand({
    TableName: TABLE_COUNTERS,
    Key: { eventId },
    UpdateExpression: 'ADD nextPosition :one SET updatedAt = :now',
    ConditionExpression: 'isActive = :true',  // VWR 활성 시에만
    ExpressionAttributeValues: {
        ':one': 1,
        ':true': true,
        ':now': Date.now(),
    },
    ReturnValues: 'UPDATED_NEW',
}));
```

### 2.5 서빙 카운터 자동 전진 (Counter Advancer)

**파일**: `lambda/vwr-counter-advancer/index.js`

EventBridge에 의해 1분마다 트리거되는 Lambda로, 내부적으로 6회 사이클을 돌며 10초 간격으로 서빙 카운터를 전진시킨다.

```
EventBridge (1분 주기)
     |
     v
+------------------------------------+
| Lambda 실행 (최대 60초)             |
|                                    |
|  Cycle 1 (0s):  scan + advance     |
|  Cycle 2 (10s): scan + advance     |
|  Cycle 3 (20s): scan + advance     |
|  Cycle 4 (30s): scan + advance     |
|  Cycle 5 (40s): scan + advance     |
|  Cycle 6 (50s): scan + advance     |
+------------------------------------+
```

**각 사이클의 동작**:

1. `ScanCommand`로 `isActive = true`인 모든 이벤트 조회
2. 각 이벤트에 대해 `advanceCounter()` 병렬 실행

**advanceCounter 동작** (L63-90):
```javascript
UpdateCommand({
    Key: { eventId },
    UpdateExpression: 'ADD servingCounter :batch SET updatedAt = :now',
    ConditionExpression: 'isActive = :true AND servingCounter < nextPosition',
    ExpressionAttributeValues: {
        ':batch': BATCH_SIZE,  // 기본값: 500
    },
});
```

**처리량 계산**:
```
BATCH_SIZE = 500명
CYCLE_INTERVAL = 10초
CYCLES_PER_INVOCATION = 6

=> 초당 처리량: 500 / 10 = 50명/초
=> 분당 처리량: 500 x 6 = 3,000명/분
=> 시간당 처리량: 180,000명/시
```

**안전장치**:
- `ConditionExpression`: `servingCounter < nextPosition` -- 대기열보다 앞서가지 않음
- `ConditionalCheckFailedException` 발생 시 `caught_up_or_inactive` 반환

### 2.6 Lambda@Edge (viewer-request)

**파일**: `lambda/edge-queue-check/index.js`

CloudFront의 viewer-request 이벤트에 연결되어 모든 요청을 가로챈다.

**판단 흐름**:

```
요청 수신
  |
  v
BYPASS_PATHS 매칭? (/api/v1/queue, /api/v1/auth, /vwr/, /vwr-api/ 등)
  |           |
  Yes         No
  |           |
  통과         |
              v
        경로에서 eventId 추출 가능?
              |           |
              Yes         No
              |           |
              v           통과
        vwr-active.json에서
        해당 event VWR 활성?
              |           |
              Yes         No
              |           |
              v           통과
        urr-vwr-token 쿠키 검증
        (tier==1, sub==eventId)
              |           |
           유효          무효/없음
              |           |
              v           v
        Tier 2 검사   302 --> /vwr/{eventId}
              |
              v
        PROTECTED_PATHS 매칭?
        (/api/v1/reservations, /api/v1/tickets 등)
              |           |
              Yes         No
              |           |
              v           통과
        urr-entry-token 검증
        (쿠키 또는 x-queue-entry-token 헤더)
              |           |
           유효          무효/없음
              |           |
           통과         302 --> /queue/{eventId}
```

**VWR 설정 로드** (`lambda/edge-queue-check/vwr-config.js`):
- `vwr-active.json` 파일을 Lambda 배포 패키지에서 로드 (빌드 타임 번들링)
- 5분 메모리 캐싱 적용
- 파일 없으면 `{ activeEvents: [] }` 반환 (VWR 비활성 상태)

**JWT 검증** (L147-181):
- HMAC-SHA256 서명 검증 (timing-safe comparison)
- 만료 시간 검증
- 별도 라이브러리 없이 Node.js `crypto` 모듈만 사용 (Lambda@Edge 번들 크기 제한)

**두 개의 Secret 키**:
- `VWR_SECRET`: Tier 1 VWR 토큰 검증용 (L32)
- `SECRET`: Tier 2 entryToken 검증용 (L31)
- `config.json`에서 빌드 타임에 주입 (Lambda@Edge는 환경 변수 미지원)

### 2.7 관리자 API (VwrAdminController)

**파일**: `services-spring/queue-service/src/main/java/guru/urr/queueservice/controller/VwrAdminController.java`

`vwr.dynamodb.enabled=true`일 때만 활성화되는 조건부 컨트롤러이다.

| 엔드포인트 | 메서드 | 설명 |
|-----------|--------|------|
| `/api/admin/vwr/activate/{eventId}` | POST | VWR 활성화 (DynamoDB에 카운터 초기화) |
| `/api/admin/vwr/deactivate/{eventId}` | POST | VWR 비활성화 (`isActive=false`) |
| `/api/admin/vwr/status/{eventId}` | GET | VWR 현황 조회 |
| `/api/admin/vwr/advance/{eventId}` | POST | 수동 서빙 카운터 전진 |

**activate 시 초기화 값** (L59-68):
```
nextPosition: 0
servingCounter: 0
isActive: true
updatedAt: 현재시각
```

**주의점** (L73): activate 후 `vwr-active.json` 파일을 업데이트하고 Lambda@Edge를 재배포해야 한다. 이 과정은 수동이다.

---

## 3. Tier 2 -- 뒷단 대기열

### 3.1 queue-service 구조

**디렉토리**: `services-spring/queue-service/`

```
queue-service/
  src/main/java/guru/urr/queueservice/
    QueueServiceApplication.java
    config/
      RedisConfig.java          -- Lua 스크립트 빈 등록
      SqsConfig.java            -- SQS 클라이언트 (조건부)
      DynamoDbConfig.java       -- DynamoDB 클라이언트 (조건부)
    controller/
      QueueController.java      -- 대기열 API (check, status, heartbeat, leave)
      VwrAdminController.java   -- VWR 관리 API
      OpsController.java        -- /health 엔드포인트
    service/
      QueueService.java         -- 핵심 대기열 로직
      AdmissionWorkerService.java -- 주기적 입장 처리
      SqsPublisher.java         -- SQS FIFO 발행
    shared/
      client/TicketInternalClient.java  -- 이벤트 정보 조회
      metrics/QueueMetrics.java         -- Micrometer 메트릭
      security/JwtTokenParser.java      -- JWT 파싱
      security/AuthUser.java            -- 인증 사용자 레코드
  src/main/resources/
    application.yml
    redis/
      admission_control.lua     -- 입장 제어 Lua 스크립트
      stale_cleanup.lua         -- 좀비 정리 Lua 스크립트
```

### 3.2 Redis 키 설계

```
queue:{eventId}          -- ZSET: 대기열 (score=입장시각 timestamp)
active:{eventId}         -- ZSET: 활성 사용자 (score=만료시각 timestamp)
queue:seen:{eventId}     -- ZSET: 하트비트 추적 (score=마지막 seen timestamp)
active:seen:{eventId}    -- ZSET: 활성 사용자 하트비트 (score=마지막 seen timestamp)
queue:active-events      -- SET: 현재 활성 이벤트 목록 (KEYS 명령 회피)
admission:lock:{eventId} -- STRING: 분산 락 (TTL 4초)
```

#### Redis ZSet 활용 상세

**대기열 (queue:{eventId})**:
- `ZADD`: `score = System.currentTimeMillis()` -- 진입 시각 기준 정렬 (FIFO)
- `ZRANK`: 현재 대기 순번 조회 (0-based, +1하여 반환)
- `ZCARD`: 대기열 크기
- `ZPOPMIN`: 가장 오래된 사용자부터 꺼냄 (Lua 스크립트에서 사용)
- `ZREM`: 대기열 이탈

**활성 사용자 (active:{eventId})**:
- `ZADD`: `score = currentTime + activeTtlMs` -- 만료 시각을 score로 사용
- `ZCOUNT`: `range(현재시각, +inf)` -- 만료되지 않은 활성 사용자 수
- `ZREMRANGEBYSCORE`: `range(-inf, 현재시각)` -- 만료된 사용자 일괄 제거

이 설계의 장점은 별도의 TTL 관리 없이 `ZCOUNT`와 `ZREMRANGEBYSCORE`만으로 시간 기반 만료를 처리한다는 것이다.

### 3.3 대기열 진입 플로우 (check)

**파일**: `services-spring/queue-service/src/main/java/guru/urr/queueservice/service/QueueService.java` L60-91

```
POST /api/queue/check/{eventId}
     |
     v
JWT에서 userId 추출 (X-User-Id 헤더)
     |
     v
이미 대기열에 있는가? (ZSCORE queue:{eventId})
     |           |
     Yes         No
     |           |
 touch +         |
 position       v
 반환       이미 active 사용자인가? (ZSCORE active:{eventId})
                 |           |
                 Yes         No
                 |           |
              touch +         |
              entryToken     v
              반환       현재 active 수 >= threshold?
                         또는 대기열 비어있지 않음?
                              |           |
                              Yes         No
                              |           |
                           대기열에       active에
                           ZADD          ZADD
                           (score=now)   (score=만료시각)
                              |           |
                              v           v
                           queued       entryToken
                           응답          발급 + 응답
```

**threshold**: 환경변수 `QUEUE_THRESHOLD` (기본값: 1000)

### 3.4 입장 처리 (AdmissionWorkerService)

**파일**: `services-spring/queue-service/src/main/java/guru/urr/queueservice/service/AdmissionWorkerService.java`

1초 주기(`@Scheduled(fixedDelay=1000ms)`)로 실행되며, Redis Lua 스크립트를 사용하여 원자적으로 입장을 처리한다.

```
@Scheduled (1초마다)
     |
     v
queue:active-events에서 활성 이벤트 목록 조회
     |
     v
각 이벤트에 대해:
     |
     v
분산 락 획득 시도 (admission:lock:{eventId}, TTL 4초)
     |           |
  획득 실패      획득 성공
  (skip)          |
                  v
           Lua: admission_control.lua 실행
                  |
                  v
           queueService.recordAdmissions(admitted)
                  |
                  v
           대기열+활성 모두 비었으면 active-events에서 제거
                  |
                  v
           락 해제
```

#### admission_control.lua 상세

**파일**: `services-spring/queue-service/src/main/resources/redis/admission_control.lua`

```lua
-- 1. 만료된 활성 사용자 제거
ZREMRANGEBYSCORE(active:{eventId}, -inf, 현재시각)

-- 2. 현재 활성 수 확인
activeCount = ZCARD(active:{eventId})

-- 3. 가용 슬롯 계산
available = maxActive - activeCount
if available <= 0 then return {0, activeCount} end

-- 4. 대기열에서 ZPOPMIN (가장 오래 대기한 사용자부터)
toAdmit = min(available, admitBatchSize)
popped = ZPOPMIN(queue:{eventId}, toAdmit)

-- 5. active로 이동 (만료시각 score로)
for each userId in popped:
    ZADD(active:{eventId}, now + activeTtlMs, userId)
    ZREM(queue:seen:{eventId}, userId)
```

이 Lua 스크립트는 Redis에서 원자적으로 실행되므로, 여러 워커 인스턴스가 동시에 실행되어도 이중 입장이 발생하지 않는다. 다만 분산 락도 추가로 적용하여 불필요한 Lua 실행을 방지한다.

### 3.5 entryToken (Tier 2 JWT)

**파일**: `services-spring/queue-service/src/main/java/guru/urr/queueservice/service/QueueService.java` L215-227

```java
Jwts.builder()
    .subject(eventId)       // 이벤트 ID
    .claim("uid", userId)   // 사용자 ID
    .issuedAt(issuedAt)
    .expiration(expiration) // 기본 600초 (10분)
    .signWith(entryTokenKey) // HMAC-SHA256
    .compact();
```

**발급 시점**: active 사용자로 전환될 때 (`buildActiveResponse`)
**전달 경로**: JSON 응답 -> 프론트엔드가 쿠키(`urr-entry-token`)와 헤더(`x-queue-entry-token`)로 양쪽에 전달

### 3.6 프론트엔드 폴링

#### queue 페이지 (Tier 2)

**파일**: `apps/web/src/app/queue/[eventId]/page.tsx`

```
페이지 마운트
     |
     v
POST /api/queue/check/{eventId}  -- 대기열 진입 시도
     |
     v
data.queued == true?
     |           |
     Yes         No (즉시 입장)
     |           |
  setJoined   entryToken 쿠키 설정
  (true)      --> /events/{eventId}/seats 또는 /book
     |
     v
useQueuePolling(eventId, joined=true) 시작
     |
     v
GET /api/queue/status/{eventId}  -- 주기적 폴링
     |
     v
status == "active"?
     |           |
     Yes         No
     |           |
  리다이렉트    계속 폴링
  (좌석/예약)   (nextPoll 간격)
```

#### useQueuePolling 훅

**파일**: `apps/web/src/hooks/use-queue-polling.ts`

| 설정 | 값 | 설명 |
|------|-----|------|
| DEFAULT_POLL_SECONDS | 3 | 기본 폴링 간격 |
| MIN_POLL_SECONDS | 1 | 최소 폴링 간격 |
| MAX_POLL_SECONDS | 60 | 최대 폴링 간격 |

**적응형 폴링**: 서버 응답의 `nextPoll` 값을 `clampPoll`으로 [1, 60] 범위로 제한하여 사용한다.

**entryToken 쿠키 설정** (L47-51):
```typescript
if (data.entryToken) {
    document.cookie = `urr-entry-token=${data.entryToken}; path=/; max-age=600; SameSite=Strict`;
}
```

### 3.7 좀비 정리 (Stale Cleanup)

**파일**: `services-spring/queue-service/src/main/java/guru/urr/queueservice/service/AdmissionWorkerService.java` L120-183

30초 주기(`@Scheduled(fixedDelay=30000ms)`)로 실행된다.

```
@Scheduled (30초마다)
     |
     v
각 활성 이벤트에 대해:
     |
     v
Lua: stale_cleanup.lua 실행
     -- queue:seen에서 cutoff 이전 사용자 찾기
     -- 해당 사용자를 queue:seen과 queue에서 모두 제거
     -- 배치 크기: 1000 (초과 시 100ms 대기 후 반복)
     |
     v
active:seen에서도 만료된 항목 제거
     -- ZREMRANGEBYSCORE(active:seen, -inf, activeSeenCutoff)
```

**stale_cleanup.lua** (`services-spring/queue-service/src/main/resources/redis/stale_cleanup.lua`):
```lua
-- heartbeat ZSET에서 stale 사용자 조회
staleUsers = ZRANGEBYSCORE(queue:seen:{eventId}, -inf, cutoff, LIMIT 0 batchSize)

-- heartbeat와 queue 양쪽에서 제거
ZREM(queue:seen:{eventId}, ...staleUsers)
ZREM(queue:{eventId}, ...staleUsers)
```

**cutoff 계산**:
- 대기열 좀비: `현재시각 - QUEUE_SEEN_TTL_SECONDS(기본 600초)`
- 활성 사용자 좀비: `현재시각 - QUEUE_ACTIVE_TTL_SECONDS(기본 600초)`

### 3.8 SQS FIFO 발행

**파일**: `services-spring/queue-service/src/main/java/guru/urr/queueservice/service/SqsPublisher.java`

입장 허가 시 SQS FIFO 큐에 메시지를 fire-and-forget으로 발행한다. 실패해도 입장 자체는 차단하지 않는다.

```java
// MessageGroupId: eventId (이벤트별 순서 보장)
// DeduplicationId: userId:eventId (5분 중복 방지)
sqsClient.sendMessage(SendMessageRequest.builder()
    .queueUrl(queueUrl)
    .messageBody(messageBody)
    .messageGroupId(eventId.toString())
    .messageDeduplicationId(deduplicationId)
    .build());
```

활성화 조건: `aws.sqs.enabled=true` 이고 `SqsClient` 빈과 `queue-url`이 모두 설정되어 있을 때.

---

## 4. 2계층 토큰 플로우

```
[사용자]
   |
   | (1) VWR 대기 페이지에서 순번 대기
   v
[VWR API Lambda]
   |
   | (2) position <= servingCounter 도달
   |     Tier 1 JWT 발급: urr-vwr-token
   |     {sub: eventId, uid: userId, tier: 1, exp: +10min}
   v
[사용자 브라우저]
   |
   | (3) urr-vwr-token 쿠키 설정 (maxAge=600)
   |     /events/{eventId}로 리다이렉트
   v
[CloudFront Lambda@Edge]
   |
   | (4) urr-vwr-token 검증 통과
   |     요청을 ALB/backend로 전달
   v
[queue 페이지 (/queue/{eventId})]
   |
   | (5) POST /api/queue/check/{eventId}
   |     대기열 진입 또는 즉시 active
   v
[queue-service]
   |
   | (6) active 전환 시 Tier 2 JWT 발급: entryToken
   |     {sub: eventId, uid: userId, exp: +10min}
   v
[사용자 브라우저]
   |
   | (7) urr-entry-token 쿠키 설정 (maxAge=600)
   |     x-queue-entry-token 헤더로도 전송
   v
[CloudFront Lambda@Edge]
   |
   | (8) PROTECTED_PATHS 접근 시 urr-entry-token 검증
   v
[Gateway VwrEntryTokenFilter]
   |
   | (9) POST/PUT/PATCH + 보호 경로 시 entryToken 재검증
   |     uid와 X-User-Id 일치 확인
   v
[Backend Services] -- 좌석 선택, 예약, 결제
```

### 토큰 비교

| 속성 | Tier 1 (urr-vwr-token) | Tier 2 (urr-entry-token) |
|------|----------------------|------------------------|
| 발급자 | VWR API Lambda | queue-service (Spring) |
| 서명 방식 | HMAC-SHA256 (수동 구현) | HMAC-SHA256 (jjwt 라이브러리) |
| Secret | `VWR_TOKEN_SECRET` | `QUEUE_ENTRY_TOKEN_SECRET` |
| TTL | 600초 (10분) | 600초 (10분) |
| Claims - sub | eventId | eventId |
| Claims - uid | userId (익명) | userId (인증됨) |
| Claims - tier | 1 | 없음 |
| 전달 방식 | 쿠키 (`urr-vwr-token`) | 쿠키 + 헤더 (양쪽) |
| 검증 위치 | Lambda@Edge | Lambda@Edge + Gateway Filter |
| 사용자 인증 | 불필요 (익명 ID) | 필요 (JWT 인증 사용자) |

---

## 5. 경로 보호 매트릭스

### Lambda@Edge 레벨 (`lambda/edge-queue-check/index.js`)

| 경로 패턴 | Tier 1 검사 | Tier 2 검사 | 비고 |
|----------|------------|------------|------|
| `/vwr/**` | BYPASS | BYPASS | VWR 정적 페이지 |
| `/vwr-api/**` | BYPASS | BYPASS | VWR API |
| `/api/v1/queue/**` | BYPASS | BYPASS | 대기열 API |
| `/api/v1/auth/**` | BYPASS | BYPASS | 인증 API |
| `/api/v1/events/**` | BYPASS | BYPASS | 이벤트 목록 |
| `/api/v1/stats/**` | BYPASS | BYPASS | 통계 |
| `/health`, `/actuator` | BYPASS | BYPASS | 헬스체크 |
| `/events/{uuid}` | VWR 토큰 필요 | -- | 이벤트 상세 페이지 |
| `/api/v1/reservations/**` | VWR 토큰 필요 | entryToken 필요 | 예약 API |
| `/api/v1/tickets/**` | VWR 토큰 필요 | entryToken 필요 | 티켓 API |
| `/api/v1/seats/**` | VWR 토큰 필요 | entryToken 필요 | 좌석 API |
| `/api/v1/admin/**` | VWR 토큰 필요 | entryToken 필요 | 관리자 API |

### Gateway 레벨 (`VwrEntryTokenFilter`)

**파일**: `services-spring/gateway-service/src/main/java/guru/urr/gatewayservice/filter/VwrEntryTokenFilter.java`

| 조건 | 동작 |
|------|------|
| GET 요청 | 필터 건너뜀 (L49) |
| POST/PUT/PATCH + 비보호 경로 | 필터 건너뜀 |
| POST/PUT/PATCH + `/api/seats/**` 또는 `/api/reservations**` | entryToken 검증 |
| `X-CloudFront-Verified` 헤더 일치 | 필터 건너뜀 (CDN 검증 신뢰) |
| entryToken의 uid != X-User-Id | 403 Forbidden |

---

## 6. 유량 제어 전략

### 6.1 Tier 1 유량 제어

```
servingCounter 전진 속도 제어
     |
     +-- BATCH_SIZE (기본 500) x 10초 간격 = 50명/초
     +-- EventBridge 1분 주기 x 6사이클
     +-- ConditionExpression으로 오버슈팅 방지
     +-- 관리자 수동 advance 가능 (batchSize 파라미터)

                    시간 -->
servingCounter:  0 -- 500 -- 1000 -- 1500 -- 2000 -- ...
                 |    |      |       |       |
nextPosition:    |    |      |  사용자 계속 진입 -->
                 |    |      |
                 500명이 Tier 1 통과 가능
```

### 6.2 Tier 2 유량 제어

```
threshold 기반 동시 접속 제한
     |
     +-- QUEUE_THRESHOLD (기본 1000)
     +-- 매 1초마다 AdmissionWorker 실행
     +-- admitBatchSize (기본 100)
     +-- 만료된 active 사용자 자동 정리 (Lua 스크립트)
     +-- 좀비 대기자 30초마다 정리

                    시간 -->
active 수:     1000 -- 980 (일부 만료) -- 1000 (100명 입장) -- ...
                |      |                    |
threshold:      1000   1000                 1000
                |      |                    |
available:      0      20                   0
```

### 6.3 적응형 폴링 간격 비교

| 대기 위치 | Tier 1 (VWR) | Tier 2 (queue-service) |
|----------|-------------|----------------------|
| 0 (즉시 입장) | -- | 3초 |
| 1 - 500 | 2초 | -- |
| 1 - 1,000 | -- | 1초 |
| 501 - 2,000 | 5초 | -- |
| 1,001 - 5,000 | -- | 5초 |
| 2,001 - 10,000 | 10초 | 10초 |
| 10,001 - 100,000 | 15초 | 30초 |
| 100,001+ | -- | 60초 |

---

## 7. Tier 1 vs Tier 2 비교

| 항목 | Tier 1 (CDN VWR) | Tier 2 (queue-service) |
|------|-----------------|----------------------|
| **위치** | CloudFront Edge + S3 + Lambda | EKS 클러스터 내 |
| **저장소** | DynamoDB | Redis (ZSet) |
| **대기열 구현** | 원자적 카운터 (순차 번호) | ZSet ZADD (timestamp 점수) |
| **순서 보장** | DynamoDB ADD 연산 (글로벌 순서) | Redis ZADD timestamp (밀리초 정밀도) |
| **입장 판정** | position <= servingCounter | active 수 < threshold |
| **입장 제어** | Counter Advancer Lambda (배치) | AdmissionWorker (Lua 스크립트) |
| **입장 간격** | 10초마다 500명 | 1초마다 최대 100명 |
| **사용자 인증** | 불필요 (익명 ID) | 필요 (JWT 인증) |
| **상태 유지** | 클라이언트 localStorage | 서버 Redis |
| **만료 처리** | DynamoDB TTL (24시간) | Redis score 기반 (10분) |
| **좀비 처리** | TTL 자동 삭제 | 하트비트 + 30초 주기 정리 |
| **대기 페이지** | 정적 HTML (S3) | Next.js SSR |
| **확장성** | CDN 에지 분산 (글로벌) | Redis 클러스터 + 수평 확장 |
| **비용 모델** | 요청당 과금 (DynamoDB+Lambda) | 인프라 상시 비용 (EKS+Redis) |
| **토큰** | urr-vwr-token (tier:1) | urr-entry-token |
| **메트릭** | CloudWatch | Micrometer/Prometheus |
| **복원력** | 없음 (서버리스) | Resilience4j (CircuitBreaker, Retry) |

---

## 8. 강점 및 약점 분석

### 8.1 강점

**1. 2단계 트래픽 흡수 구조**

Tier 1이 CDN 에지에서 대량 트래픽을 차단하므로 백엔드 인프라에 부하가 전달되기 전에 유량이 제어된다. Tier 2는 인증된 사용자만을 대상으로 세밀한 동시 접속 관리를 한다.

**2. DynamoDB 원자적 카운터**

`ADD nextPosition :one`은 DynamoDB가 보장하는 원자적 연산으로, 동시 요청이 수만 건이어도 순번 충돌이 발생하지 않는다. 별도의 분산 락이 필요 없다.

**3. Redis Lua 스크립트 원자성**

`admission_control.lua`는 만료 정리, 가용 슬롯 계산, 대기열 pop, active 이동을 하나의 원자적 트랜잭션으로 처리한다. 이중 입장이나 레이스 컨디션이 구조적으로 불가능하다.

**4. 적응형 폴링**

대기 순번에 따라 폴링 간격을 동적으로 조정하여, 가까운 사용자는 빠르게 반응하고 먼 사용자는 서버 부하를 줄인다.

**5. Graceful Degradation**

- Tier 1 VWR 비활성화 시 `isActive=false` -> 전원 즉시 통과
- SQS 발행 실패 시에도 입장 처리는 정상 동작 (fire-and-forget)
- 이벤트 정보 조회 실패 시 CircuitBreaker fallback

**6. 정적 대기 페이지의 효율성**

S3 + CloudFront 정적 호스팅으로 대기 페이지 자체는 거의 무한한 동시접속을 처리할 수 있다. CDN 캐싱 효과가 극대화된다.

**7. 보안 다층 검증**

Lambda@Edge -> Gateway Filter -> Backend Service 세 단계에서 토큰을 검증하며, uid 바인딩 검증과 timing-safe 비교를 적용한다.

### 8.2 약점

**1. Lambda@Edge VWR 설정 배포 지연**

`vwr-active.json`이 Lambda 배포 패키지에 번들되어 있어, VWR 활성화/비활성화 시 Lambda@Edge 재배포가 필요하다. CloudFront 배포 전파에 수 분이 소요되므로 즉각적인 VWR 활성화가 어렵다.

**2. Counter Advancer의 Scan 연산**

`lambda/vwr-counter-advancer/index.js` L28에서 `ScanCommand`로 활성 이벤트를 조회한다. DynamoDB Scan은 전체 테이블을 읽는 연산으로, 이벤트 수가 많아지면 비효율적이다. 다만 counters 테이블은 이벤트당 1행이므로 현실적 규모에서는 문제없다.

**3. Tier 2 토큰이 폴링 응답으로만 전달됨**

active로 전환된 사용자가 entryToken을 받으려면 다음 폴링 응답을 기다려야 한다. `check` 엔드포인트 응답과 `status` 폴링 응답 간에 entryToken 포함 여부가 다르다 -- `buildActiveResponse`(check 시)는 entryToken을 포함하지만, `status` 메서드(L93-128)의 active 분기는 포함하지 않는다.

**4. 단일 threshold의 한계**

`QUEUE_THRESHOLD`가 전역 설정(환경변수)으로 되어 있어, 이벤트별로 다른 동시접속 한도를 설정할 수 없다. 대형/소형 이벤트 혼재 시 비효율이 발생할 수 있다.

**5. Tier 1 익명 ID의 취약성**

Tier 1의 사용자 식별이 `localStorage`의 `urr-anon-id`에 의존한다. 시크릿 모드나 다른 브라우저에서 새 순번을 무제한 발급받을 수 있어, 악의적 사용자가 대기열을 오염시킬 가능성이 있다.

**6. Tier 1과 Tier 2 간 사용자 식별 불연속**

Tier 1은 익명 ID(`anon-xxx`)를 사용하고, Tier 2는 JWT 인증 사용자 ID를 사용한다. 동일 사용자임을 연결하는 메커니즘이 없어, Tier 1을 통과한 토큰을 다른 사용자가 사용할 수 있는 이론적 위험이 있다 (다만 Tier 1 토큰에 eventId가 바인딩되어 있고 10분 TTL이 있어 실질적 위험은 낮다).

**7. Redis 단일 장애점 (개발 환경)**

개발 환경에서는 단일 Redis 인스턴스를 사용한다. 프로덕션에서는 Redis Cluster(3노드)를 사용하지만 (`application.yml` L92-108), Lua 스크립트가 여러 키를 조작하므로 Redis Cluster의 해시 슬롯 제약에 주의가 필요하다 (같은 이벤트의 키들이 다른 슬롯에 배치될 수 있음).

**8. 대기 시간 추정 정확도**

Tier 1은 고정 비율(50명/초)로 추정하고, Tier 2는 최근 1분간 처리량 윈도우 기반으로 추정한다. 트래픽 패턴 변화가 급격할 때 추정이 부정확해질 수 있다. 특히 Tier 2에서 초기 5초간(`elapsed < 5000`)에는 `position * 30`으로 계산하여 과도하게 높은 대기 시간을 표시한다.
