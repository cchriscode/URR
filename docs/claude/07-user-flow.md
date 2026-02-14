# 07. 사용자 플로우 분석

> **문서 목적**: 티케팅 플랫폼(URR)의 주요 사용자 플로우를 프론트엔드 경로 -> API 호출 -> 게이트웨이 라우팅 -> 백엔드 로직 -> 응답 순서로 추적하여 기술한다. 각 단계에는 실제 소스 파일과 라인 번호를 명시한다.

---

## 1. 회원가입 & 로그인 플로우

### 1.1 이메일 회원가입

사용자가 URR 플랫폼에 신규 계정을 생성하는 플로우이다.

**프론트엔드 진입**

사용자가 SiteHeader의 "회원가입" 링크를 클릭하면 `/register` 경로로 이동한다 (`apps/web/src/components/site-header.tsx:132-137`). `RegisterPage` 컴포넌트가 마운트되며, 이름/이메일/비밀번호/전화번호 입력 폼을 렌더링한다 (`apps/web/src/app/register/page.tsx:9-111`).

- 상태 관리: `useState`로 `{ email, password, name, phone }` 폼 데이터를 관리한다 (`apps/web/src/app/register/page.tsx:12`).
- 비밀번호 최소 길이: HTML `minLength={8}` 속성으로 클라이언트 검증을 수행한다 (`apps/web/src/app/register/page.tsx:79`).

**API 호출**

폼 제출 시 `authApi.register(form)` 을 호출한다 (`apps/web/src/app/register/page.tsx:21`).

```
authApi.register = (payload) => http.post("/auth/register", payload)
```
(`apps/web/src/lib/api-client.ts:134-135`)

HTTP 클라이언트는 `axios`를 사용하며, `baseURL`이 `${resolveBaseUrl()}/api/v1`로 설정되어 있으므로 최종 요청 URL은 `POST /api/v1/auth/register`가 된다 (`apps/web/src/lib/api-client.ts:38-43`).

**게이트웨이 라우팅**

Spring Cloud Gateway가 `/api/v1/auth/**` 패턴을 매칭하여 auth-service(`${AUTH_SERVICE_URL:http://localhost:3005}`)로 프록시한다 (`services-spring/gateway-service/src/main/resources/application.yml:12-15`).

**백엔드 처리**

1. `AuthController.register()` 메서드가 `RegisterRequest`를 받아 `AuthService.register()`를 호출한다 (`services-spring/auth-service/src/main/java/com/tiketi/authservice/controller/AuthController.java:39-46`).
2. `AuthService.register()` 내부 처리 순서 (`services-spring/auth-service/src/main/java/com/tiketi/authservice/service/AuthService.java:66-84`):
   - 이메일 중복 확인: `userRepository.findByEmail()` -- 중복 시 `ApiException("Email already exists")` 발생 (라인 67-69)
   - `UserEntity` 생성: 이메일, 이름, 전화번호 설정 (라인 71-75)
   - 비밀번호 해싱: `passwordEncoder.encode(request.password())` -- BCrypt 사용 (라인 73)
   - DB 저장: `userRepository.save(user)` (라인 77)
   - Access Token 생성: `jwtService.generateToken(saved)` (라인 78)
   - Refresh Token 생성: `jwtService.generateRefreshToken(saved, familyId)` -- 토큰 패밀리 UUID를 부여 (라인 79-80)
   - Refresh Token DB 저장: `storeRefreshToken()` -- 토큰 해시, familyId, 만료시각 저장 (라인 81, 289-297)

**응답 처리**

3. `AuthController`에서 `CookieHelper`를 통해 쿠키를 설정한다 (`services-spring/auth-service/src/main/java/com/tiketi/authservice/controller/AuthController.java:43-44`):
   - `access_token`: 쿠키에 JWT 토큰 저장
   - `refresh_token`: httpOnly 쿠키에 리프레시 토큰 저장
4. 응답 본문: `AuthResponse` (message, token, refreshToken, user) -- HTTP 201 Created (라인 45)

**프론트엔드 후처리**

- 성공 시: `refreshAuth()` 호출로 `AuthProvider` 상태를 갱신한 후 `router.push("/")` 로 홈으로 리다이렉트한다 (`apps/web/src/app/register/page.tsx:22-23`).
- 실패 시: HTTP 409(이메일 중복) 또는 400(입력 오류) 에 따른 에러 메시지를 표시한다 (라인 25-34).

---

### 1.2 이메일 로그인

기존 계정으로 로그인하는 플로우이다.

**프론트엔드 진입**

SiteHeader의 "로그인" 링크(`apps/web/src/components/site-header.tsx:129`) 클릭 시 `/login` 경로로 이동한다. `LoginPage` 컴포넌트가 이메일/비밀번호 입력 폼을 렌더링한다 (`apps/web/src/app/login/page.tsx:11-156`).

**API 호출**

폼 제출 시 `authApi.login({ email, password })` 를 호출한다 (`apps/web/src/app/login/page.tsx:24`).

```
authApi.login = (payload) => http.post("/auth/login", payload)
```
(`apps/web/src/lib/api-client.ts:136`)

**게이트웨이 라우팅**

`/api/v1/auth/**` -> auth-service (`services-spring/gateway-service/src/main/resources/application.yml:12-15`).

**백엔드 처리**

1. `AuthController.login()` 메서드 (`services-spring/auth-service/src/main/java/com/tiketi/authservice/controller/AuthController.java:48-55`).
2. `AuthService.login()` 처리 순서 (`services-spring/auth-service/src/main/java/com/tiketi/authservice/service/AuthService.java:87-111`):
   - 이메일로 사용자 조회: `userRepository.findByEmail()` -- 없으면 `ApiException("Invalid email or password")` (라인 88-89)
   - 비밀번호 해시 존재 확인: OAuth 사용자의 경우 비밀번호가 없으므로 별도 검증 (라인 91-93)
   - 비밀번호 검증: `passwordEncoder.matches(request.password(), user.getPasswordHash())` (라인 97)
   - 불일치 시: `ApiException("Invalid email or password")` (라인 102-104)
   - JWT 토큰 생성: access token + refresh token (라인 106-109)

**응답**

- `CookieHelper`를 통해 `access_token`, `refresh_token` 쿠키 설정 (라인 52-53)
- 응답 본문: `AuthResponse` (message, token, refreshToken, user)

**프론트엔드 후처리**

성공 시 `refreshAuth()` -> `router.push("/")` (`apps/web/src/app/login/page.tsx:25-26`). 실패 시 HTTP 401 에러 메시지 "이메일 또는 비밀번호가 올바르지 않습니다." 표시 (라인 32-33).

---

### 1.3 Google OAuth 로그인

Google 소셜 로그인을 통한 인증 플로우이다.

**프론트엔드 진입**

`LoginPage`의 `useEffect`에서 Google Sign-In SDK 스크립트를 동적 로드한다 (`apps/web/src/app/login/page.tsx:41-76`):
- `NEXT_PUBLIC_GOOGLE_CLIENT_ID` 환경변수가 설정된 경우에만 활성화 (라인 9, 42)
- `google.accounts.id.initialize()` 로 클라이언트 초기화 (라인 56-59)
- `google.accounts.id.renderButton()` 으로 Google 로그인 버튼 렌더링 (라인 62-68)

**API 호출**

Google 콜백에서 `credential`(ID 토큰)을 받아 `authApi.google(credential)` 을 호출한다 (`apps/web/src/app/login/page.tsx:78-91`).

```
authApi.google = (credential) => http.post("/auth/google", { credential })
```
(`apps/web/src/lib/api-client.ts:140`)

**게이트웨이 라우팅**

`/api/v1/auth/**` -> auth-service (`services-spring/gateway-service/src/main/resources/application.yml:12-15`).

**백엔드 처리**

1. `AuthController.google()` 메서드 (`services-spring/auth-service/src/main/java/com/tiketi/authservice/controller/AuthController.java:85-101`).
2. `AuthService.googleLogin()` 처리 순서 (`services-spring/auth-service/src/main/java/com/tiketi/authservice/service/AuthService.java:197-259`):
   - `GoogleIdTokenVerifier.verify(credential)` 로 ID 토큰 검증 (라인 207)
   - 토큰 payload에서 `subject`(Google ID), `email`, `name`, `picture` 추출 (라인 218-221)
   - 이메일로 기존 사용자 조회: `userRepository.findByEmail(email)` (라인 227)
   - 신규 사용자인 경우: `UserEntity` 생성, `googleId` 설정, `passwordHash`를 `"OAUTH_USER_NO_PASSWORD"`로 설정하여 저장 (라인 228-234)
   - 기존 사용자이나 `googleId`가 없는 경우: Google ID를 연동 (라인 235-238)
   - JWT access token + refresh token 생성 (라인 240-243)

**응답**

- `CookieHelper`를 통해 쿠키 설정 (라인 94-99)
- 응답 본문: `{ message, token, refreshToken, user: { id, userId, email, name, role, picture } }`

**프론트엔드 후처리**

성공 시 `refreshAuth()` -> `router.push("/")` (`apps/web/src/app/login/page.tsx:83-84`).

---

### 1.4 자동 세션 복원

앱 마운트 시 기존 세션을 자동으로 복원하는 플로우이다.

**AuthProvider 초기화**

`AuthProvider` 컴포넌트의 `useEffect`에서 `fetchUser()` 를 호출한다 (`apps/web/src/lib/auth-context.tsx:53-55`).

```
fetchUser -> authApi.me() -> http.get("/auth/me")
```
(`apps/web/src/lib/auth-context.tsx:27-51`, `apps/web/src/lib/api-client.ts:137`)

**정상 응답 처리**

`authApi.me()` 응답에서 사용자 정보를 추출하여 `AuthContext` 상태를 갱신한다 (`apps/web/src/lib/auth-context.tsx:31-43`):
- `res.data.user` 또는 `res.data`에서 id, email, name, role 추출
- role이 "admin"인 경우 admin으로, 그 외 "user"로 설정 (라인 39)

**401 에러 시 자동 갱신**

axios 인터셉터에서 401 응답을 감지하면 자동으로 토큰 갱신을 시도한다 (`apps/web/src/lib/api-client.ts:79-131`):

1. 401 응답 수신 시 `_retry` 플래그를 확인하여 중복 시도 방지 (라인 85)
2. `/auth/login`, `/auth/register`, `/auth/refresh` 엔드포인트는 리프레시 대상에서 제외 (라인 88)
3. 이미 리프레시 중이면 `failedQueue`에 요청을 추가하여 대기 (라인 92-97)
4. `http.post("/auth/refresh")` 호출 (라인 103)
5. 성공 시: 큐에 대기 중인 모든 요청을 재시도 (`processQueue(true)`) (라인 104)
6. 실패 시: `clearAuth()` 호출, 큐 요청 모두 실패 처리 (라인 108-109)

**백엔드 리프레시 처리**

`AuthController.refresh()` 메서드 (`services-spring/auth-service/src/main/java/com/tiketi/authservice/controller/AuthController.java:67-83`):
- `refresh_token` 쿠키 또는 request body에서 리프레시 토큰 추출 (라인 69-76)
- `AuthService.refreshToken()` 내부 처리 (`services-spring/auth-service/src/main/java/com/tiketi/authservice/service/AuthService.java:114-153`):
  - JWT 검증 (라인 117)
  - DB에서 토큰 해시로 조회 (라인 128-129)
  - 이미 폐기된 토큰이면 해당 family 전체 무효화 (토큰 재사용 공격 탐지) (라인 132-137)
  - 현재 토큰을 폐기 처리 (라인 139-140)
  - 새 access token + refresh token 발급 (라인 148-150)

**429 자동 재시도**

추가로, 429(Too Many Requests) 응답 시 최대 2회까지 지수 백오프로 재시도한다 (`apps/web/src/lib/api-client.ts:119-127`):
- 지연 시간: `min(1000 * 2^retryCount, 4000)ms`

---

## 2. 이벤트 탐색 & 예매 플로우

### 2.1 이벤트 목록 조회

사용자가 홈 페이지에서 이벤트 목록을 탐색하는 플로우이다.

**프론트엔드 진입**

루트 경로 `/`에서 `HomePage` 컴포넌트가 마운트된다 (`apps/web/src/app/page.tsx:114-178`).

- 필터 탭: "예매 중", "오픈 예정", "예매 종료", "취소됨", "전체" (라인 10-16)
- 기본 필터: `"on_sale"` (`apps/web/src/app/page.tsx:115`)
- `useEvents(params)` 훅을 사용하여 데이터를 로드한다 (라인 120)

**API 호출**

`useEvents` 훅은 `@tanstack/react-query`의 `useQuery`를 사용한다 (`apps/web/src/hooks/use-events.ts:1-15`):

```typescript
eventsApi.list(params) -> http.get("/events", { params })
```
(`apps/web/src/lib/api-client.ts:144`)

최종 요청: `GET /api/v1/events?status=on_sale&page=1&limit=12`

**게이트웨이 라우팅**

`/api/v1/events/**` -> catalog-service(`${CATALOG_SERVICE_URL:http://localhost:3009}`) (`services-spring/gateway-service/src/main/resources/application.yml:24-27`).

**백엔드 처리**

`EventController.getEvents()` 메서드 (`services-spring/catalog-service/src/main/java/com/tiketi/catalogservice/domain/event/controller/EventController.java:22-30`):
- 파라미터: `status`, `q`(검색 쿼리), `page`(기본값 1), `limit`(기본값 10)
- `EventReadService.listEvents(status, searchQuery, page, limit)` 호출 (라인 29)

**UI 렌더링**

`EventCard` 컴포넌트가 각 이벤트를 렌더링한다 (`apps/web/src/app/page.tsx:43-112`):
- 포스터 이미지 표시 (라인 62-66)
- 상태 배지: on_sale="예매 중"(sky), upcoming="오픈 예정"(amber), ended="종료"(slate), sold_out="매진"(red) (라인 18-33)
- 카운트다운: `useCountdown`을 사용하여 판매 시작/종료까지 남은 시간 표시 (라인 44-52)
- 이벤트 정보: 장소, 일자, 가격대, 아티스트명 (라인 94-108)
- 카운트다운 만료 시: upcoming인 경우 필터를 "on_sale"로 자동 전환 (라인 167-171)

---

### 2.2 이벤트 상세 조회

특정 이벤트의 상세 정보를 조회하는 플로우이다.

**프론트엔드 진입**

이벤트 카드 클릭 시 `/events/[id]` 경로로 이동한다 (`apps/web/src/app/page.tsx:56-57`).

**API 호출**

```
eventsApi.detail(id) -> http.get(`/events/${id}`)
```
(`apps/web/src/lib/api-client.ts:145`)

**게이트웨이 라우팅**

`/api/v1/events/**` -> catalog-service (`services-spring/gateway-service/src/main/resources/application.yml:24-27`).

**백엔드 처리**

`EventController.getEvent(id)` 메서드가 `eventReadService.getEventDetail(id)` 를 호출한다 (`services-spring/catalog-service/src/main/java/com/tiketi/catalogservice/domain/event/controller/EventController.java:32-35`).

**UI 렌더링**

이벤트 상세 페이지에서 다음 정보를 표시한다 (`apps/web/src/app/events/[id]/page.tsx`):
- 이벤트 기본 정보: 제목, 설명, 장소, 일자
- 판매 시작 카운트다운: `useCountdown(salesStartAt)` 사용
- 좌석 배치 여부에 따른 분기:
  - `seat_layout_id` 존재 시: "좌석 선택" 버튼 -> `/events/[id]/seats`
  - `seat_layout_id` 없음(스탠딩): "예매하기" 버튼 -> `/events/[id]/book`
- 예매 버튼 활성화 조건: 판매 시작일이 지났고 판매 종료일 이전

**타입 정의**

이벤트 상세 응답의 타입은 `EventDetail` 인터페이스로 정의된다 (`apps/web/src/lib/types.ts:26-40`):
- `seat_layout_id?: string | null`: null이면 스탠딩 이벤트
- `ticket_types?: TicketType[]`: 티켓 유형 목록 (id, name, price, total_quantity, available_quantity)

---

### 2.3 대기열 진입 (Virtual Waiting Room)

트래픽이 임계값을 초과할 때 사용자를 대기열에 진입시키는 플로우이다.

**프론트엔드 진입**

이벤트 상세 페이지에서 예매 버튼 클릭 시 `/queue/[eventId]` 경로로 이동한다.

**초기 대기열 확인**

`queueApi.check(eventId)` 를 호출한다 (`apps/web/src/lib/api-client.ts:149`):

```
queueApi.check = (eventId) => http.post<QueueStatus>(`/queue/check/${eventId}`)
```

**게이트웨이 라우팅**

`/api/v1/queue/**` -> queue-service(`${QUEUE_SERVICE_URL:http://localhost:3007}`) (`services-spring/gateway-service/src/main/resources/application.yml:40-43`).

**백엔드 처리 -- QueueController**

`QueueController.check()` 메서드 (`services-spring/queue-service/src/main/java/com/tiketi/queueservice/controller/QueueController.java:27-34`):
- JWT에서 사용자 인증: `jwtTokenParser.requireUser(request)` (라인 32)
- `QueueService.check(eventId, userId)` 호출 (라인 33)

**백엔드 처리 -- QueueService.check()**

`QueueService.check()` 메서드의 의사결정 로직 (`services-spring/queue-service/src/main/java/com/tiketi/queueservice/service/QueueService.java:60-91`):

1. **이미 대기열에 있는 경우**: 대기열 위치를 갱신하고 `buildQueuedResponse` 반환 (라인 63-67)
2. **이미 활성 사용자인 경우**: 활성 상태를 갱신하고 `buildActiveResponse` 반환 (라인 70-72)
3. **새 사용자**:
   - 현재 활성 사용자 수 및 대기열 크기 조회 (라인 75-76)
   - 대기열이 비어있지 않거나 활성 사용자가 임계값(`QUEUE_THRESHOLD`, 기본 1000명)을 초과하면: 대기열에 추가 (라인 78-84)
   - 그렇지 않으면: 즉시 활성 사용자로 등록 (라인 87-90)

**Redis 데이터 구조**

대기열과 활성 사용자 관리에 Redis ZSET을 사용한다:
- 대기열 키: `queue:{eventId}` -- score는 등록 타임스탬프 (`services-spring/queue-service/src/main/java/com/tiketi/queueservice/service/QueueService.java:271-273, 329-331`)
- 활성 사용자 키: `active:{eventId}` -- score는 만료 타임스탬프 (라인 275-276, 301-304)
- 활성 이벤트 추적: `queue:active-events` SET (라인 262-267)

**대기열 응답 (queued 상태)**

`buildQueuedResponse()` 메서드 (`services-spring/queue-service/src/main/java/com/tiketi/queueservice/service/QueueService.java:181-195`):

```json
{
  "queued": true,
  "status": "queued",
  "position": 42,
  "peopleAhead": 41,
  "peopleBehind": 158,
  "estimatedWait": 1260,
  "nextPoll": 1,
  "threshold": 1000,
  "currentUsers": 1000,
  "eventInfo": { "title": "...", "artist": "..." }
}
```

- `estimatedWait`: 처리량 기반 대기 시간 추정 -- 최근 1분간 입장 처리량을 기반으로 계산 (`services-spring/queue-service/src/main/java/com/tiketi/queueservice/service/QueueService.java:242-258`)
- `nextPoll`: 위치에 따른 동적 폴링 간격 -- 1000위 이내 1초, 5000위 이내 5초, 10만위 이내 30초, 그 이상 60초 (라인 231-238)

**활성 응답 (active 상태)**

`buildActiveResponse()` 메서드 (`services-spring/queue-service/src/main/java/com/tiketi/queueservice/service/QueueService.java:197-213`):
- `entryToken`: JWT 형식의 진입 토큰 생성 (라인 198) -- subject에 eventId, claim에 userId, TTL은 `QUEUE_ACTIVE_TTL_SECONDS`(기본 600초) (라인 215-227)
- SQS FIFO로 입장 이벤트 발행 (라인 210)

**프론트엔드 -- 대기열 폴링**

`useQueuePolling` 훅이 폴링을 관리한다 (`apps/web/src/hooks/use-queue-polling.ts`):
- `queueApi.status(eventId)` 를 `nextPoll` 간격으로 반복 호출 (`apps/web/src/lib/api-client.ts:150`)
- `heartbeat`: `queueApi.heartbeat(eventId)` 로 연결 유지 (라인 151)

**프론트엔드 -- 진입 토큰 처리**

`entryToken`을 수신하면 쿠키에 저장한다:
- 쿠키명: `tiketi-entry-token`
- TTL: 10분
- 이후 모든 API 요청에 `x-queue-entry-token` 헤더로 자동 첨부 (`apps/web/src/lib/api-client.ts:70-77`)

**프론트엔드 -- 리다이렉트**

`status == "active"` 수신 시:
- 좌석 지정 이벤트: `/events/[id]/seats`
- 스탠딩 이벤트: `/events/[id]/book`

**QueueStatus 타입 정의** (`apps/web/src/lib/types.ts:120-136`)

---

### 2.4 좌석 선택 (좌석 지정 이벤트)

좌석이 지정된 이벤트에서 사용자가 좌석을 선택하고 예약하는 플로우이다.

**프론트엔드 진입**

대기열 통과 후 `/events/[id]/seats` 경로로 이동한다 (`apps/web/src/app/events/[id]/seats/page.tsx`).

**좌석 목록 조회**

```
seatsApi.byEvent(eventId) -> http.get(`/seats/events/${eventId}`)
```
(`apps/web/src/lib/api-client.ts:171`)

**게이트웨이 라우팅**

`/api/v1/seats/**` -> ticket-service(`${TICKET_SERVICE_URL:http://localhost:3002}`) (`services-spring/gateway-service/src/main/resources/application.yml:32-35`).

**백엔드 처리**

`SeatController.byEvent(eventId)` 메서드 (`services-spring/ticket-service/src/main/java/com/tiketi/ticketservice/domain/seat/controller/SeatController.java:38-41`):
- `catalogReadService.getSeatsByEvent(eventId)` 호출 (라인 40)

**좌석 상태 타입** (`apps/web/src/lib/types.ts:52-61`):
- `available`: 선택 가능
- `reserved`: 다른 사용자가 예약 완료
- `locked`: 다른 사용자가 임시 잠금 중

**좌석 예약 요청**

사용자가 좌석을 선택한 후 예약 요청:

```
seatsApi.reserve({ eventId, seatIds, idempotencyKey }) -> http.post("/seats/reserve", payload)
```
(`apps/web/src/lib/api-client.ts:172-176`)

- `idempotencyKey`: `crypto.randomUUID()`로 자동 생성하여 중복 예약 방지 (라인 175)

**게이트웨이 VWR 검증**

게이트웨이에서 `x-queue-entry-token` 헤더의 진입 토큰을 검증한다. 유효하지 않으면 요청을 거부한다. 이 토큰은 axios 요청 인터셉터에서 쿠키 `tiketi-entry-token` 값을 자동 첨부한다 (`apps/web/src/lib/api-client.ts:70-77`).

**백엔드 처리**

`SeatController.reserve()` 메서드 (`services-spring/ticket-service/src/main/java/com/tiketi/ticketservice/domain/seat/controller/SeatController.java:43-50`):
- JWT 인증: `jwtTokenParser.requireUser(request)` (라인 48)
- `reservationService.reserveSeats(userId, body)` 호출 (라인 49)
- 내부 처리: Redis Lua 스크립트로 좌석 원자적 잠금 -> 예약 레코드 DB 저장

---

### 2.5 스탠딩 예매 (좌석 없는 이벤트)

좌석 배치가 없는 스탠딩 이벤트의 예매 플로우이다.

**프론트엔드 진입**

대기열 통과 후 `/events/[id]/book` 경로로 이동한다 (`apps/web/src/app/events/[id]/book/page.tsx`).

**API 호출**

사용자가 티켓 유형별 수량을 선택한 후:

```
reservationsApi.createTicketOnly({ eventId, items, idempotencyKey })
  -> http.post("/reservations", payload)
```
(`apps/web/src/lib/api-client.ts:157-164`)

- `items`: `Array<{ ticketTypeId: string; quantity: number }>` 형태
- `idempotencyKey`: `crypto.randomUUID()`로 자동 생성 (라인 163)

**게이트웨이 라우팅**

`/api/v1/reservations/**` -> ticket-service (`services-spring/gateway-service/src/main/resources/application.yml:36-39`).

**백엔드 처리**

`ReservationController.create()` 메서드 (`services-spring/ticket-service/src/main/java/com/tiketi/ticketservice/domain/reservation/controller/ReservationController.java:30-37`):
- JWT 인증 (라인 35)
- `reservationService.createReservation(userId, body)` 호출 (라인 36)
- 내부 처리: 티켓 유형별 재고 확인 -> 예약 레코드 생성 -> 만료 시간 설정

---

### 2.6 결제

예약이 생성된 후 결제를 진행하는 플로우이다.

**프론트엔드 진입**

예약 성공 후 `/payment/[reservationId]` 경로로 이동한다 (`apps/web/src/app/payment/[reservationId]/page.tsx`).

**예약 정보 로드**

```
reservationsApi.byId(reservationId) -> http.get(`/reservations/${reservationId}`)
```
(`apps/web/src/lib/api-client.ts:166`)

**결제 수단 분기**

두 가지 결제 경로가 존재한다:

#### Toss Payments 결제 (카드결제)

1. **결제 준비**: `paymentsApi.prepare()` 호출 (`apps/web/src/lib/api-client.ts:180`)

   게이트웨이: `/api/v1/payments/**` -> payment-service (`services-spring/gateway-service/src/main/resources/application.yml:16-19`)

   백엔드 `PaymentController.prepare()` (`services-spring/payment-service/src/main/java/com/tiketi/paymentservice/controller/PaymentController.java:33-40`):
   - `PaymentService.prepare()` 호출 (`services-spring/payment-service/src/main/java/com/tiketi/paymentservice/service/PaymentService.java:49-118`):
     - 예약 검증: `ticketInternalClient.validateReservation()` (라인 71)
     - 금액 검증: 서버측 금액과 요청 금액 비교 (라인 79-81)
     - 기존 결제 중복 확인 (라인 86-106)
     - 주문 ID 생성: `ORD_{timestamp}_{uuid8}` 형식 (라인 108)
     - DB에 pending 상태로 결제 레코드 삽입 (라인 110-115)
     - 응답: `{ orderId, amount, clientKey }` (라인 117)

2. **Toss SDK 호출**: 프론트엔드에서 `requestPayment()` 실행
3. **리다이렉트**: Toss 결제 완료 후 `/payment/success?paymentKey=...&orderId=...&amount=...` 로 리다이렉트

#### 기타 결제 (네이버페이, 카카오페이, 계좌이체)

```
paymentsApi.process(payload) -> http.post("/payments/process", payload)
```
(`apps/web/src/lib/api-client.ts:182`)

백엔드 `PaymentController.process()` (`services-spring/payment-service/src/main/java/com/tiketi/paymentservice/controller/PaymentController.java:82-89`):
- `PaymentService.process()` 호출 (`services-spring/payment-service/src/main/java/com/tiketi/paymentservice/service/PaymentService.java:251-341`):
  - paymentType에 따라 분기: "reservation", "transfer", "membership" (라인 259-278)
  - 기존 확정 결제 중복 체크 (멱등성 보장) (라인 280-311)
  - DB에 즉시 confirmed 상태로 결제 레코드 삽입 (라인 315-321)
  - `completeByType()`: 예약 확정/양도 완료/멤버십 활성화 처리 (라인 329)
  - Kafka 이벤트 발행: `PaymentConfirmedEvent` (라인 373-375)

**만료 타이머**

결제 페이지에서 `useCountdown(expires_at)` 으로 예약 만료까지 남은 시간을 표시한다. 1분 미만이 되면 긴급 UI를 렌더링한다.

---

### 2.7 결제 확인 (Toss 콜백)

Toss Payments에서 리다이렉트로 돌아온 후 결제를 확정하는 플로우이다.

**프론트엔드 진입**

Toss 결제 완료 후 `/payment/success?paymentKey=...&orderId=...&amount=...` 경로로 리다이렉트된다 (`apps/web/src/app/payment/success/page.tsx`).

**API 호출**

URL 쿼리에서 `paymentKey`, `orderId`, `amount`를 추출하여:

```
paymentsApi.confirm({ paymentKey, orderId, amount }) -> http.post("/payments/confirm", payload)
```
(`apps/web/src/lib/api-client.ts:181`)

**게이트웨이 라우팅**

`/api/v1/payments/**` -> payment-service (`services-spring/gateway-service/src/main/resources/application.yml:16-19`).

**백엔드 처리**

`PaymentController.confirm()` (`services-spring/payment-service/src/main/java/com/tiketi/paymentservice/controller/PaymentController.java:42-49`):
- `PaymentService.confirm()` 호출 (`services-spring/payment-service/src/main/java/com/tiketi/paymentservice/service/PaymentService.java:121-173`):
  - `order_id`로 결제 레코드 조회 (`SELECT ... FOR UPDATE`로 행 잠금) (라인 122-127)
  - 사용자 소유권 확인 (라인 134-136)
  - 금액 일치 확인 (라인 137-139)
  - 중복 확정 방지 (라인 140-142)
  - 결제 상태를 `confirmed`로 변경, `payment_key` 및 `toss_approved_at` 저장 (라인 153-158)
  - `completeByType()` 호출 (`services-spring/payment-service/src/main/java/com/tiketi/paymentservice/service/PaymentService.java:343-376`):
    - reservation: `ticketInternalClient.confirmReservation()` (라인 364)
    - transfer: `ticketInternalClient.confirmTransfer()` (라인 358)
    - membership: `ticketInternalClient.activateMembership()` (라인 361)
  - Kafka 이벤트 발행: `PaymentConfirmedEvent` (라인 373-375)

**프론트엔드 후처리**

결제 확인 성공 후:
- `queryClient.invalidateQueries(["reservations"])` 로 캐시를 무효화
- 성공 UI 표시 후 `/my-reservations`로 이동 가능

---

## 3. 내 예매 관리

### 3.1 예매 목록 조회

사용자가 자신의 예매 내역을 확인하는 플로우이다.

**프론트엔드 진입**

SiteHeader의 "내 예매" 링크 클릭 (`apps/web/src/components/site-header.tsx:73-81`) -> `/my-reservations` 경로.

**API 호출**

`useMyReservations()` 훅이 데이터를 로드한다 (`apps/web/src/hooks/use-reservations.ts:6-14`):

```typescript
reservationsApi.mine() -> http.get("/reservations/my")
```
(`apps/web/src/lib/api-client.ts:165`)

- queryKey: `["reservations", "mine"]` (라인 8)
- 응답 파싱: `res.data.reservations ?? res.data.data ?? []` (라인 11)

**게이트웨이 라우팅**

`/api/v1/reservations/**` -> ticket-service (`services-spring/gateway-service/src/main/resources/application.yml:36-39`).

**백엔드 처리**

`ReservationController.my()` (`services-spring/ticket-service/src/main/java/com/tiketi/ticketservice/domain/reservation/controller/ReservationController.java:39-43`):
- `reservationService.getMyReservations(userId)` 호출 (라인 42)

**UI 렌더링** (`apps/web/src/app/my-reservations/page.tsx`)

예매별 카드를 렌더링하며, 각 카드에 다음 정보를 표시한다:
- 이벤트명, 예매 번호, 날짜, 금액
- 상태 배지: confirmed="확정"(sky), pending="대기"(amber), cancelled="취소"(red)
- pending 상태 예매에는 `useCountdown(expires_at)` 으로 만료 카운트다운 표시
- 액션 버튼: "취소하기", "양도 등록"

**Reservation 타입 정의** (`apps/web/src/lib/types.ts:94-108`):
- `expires_at?: string`: 예약 만료 시간
- `items?: ReservationItem[]`: 예매 항목 (좌석 또는 티켓 유형)
- `event?: EventSummary`: 이벤트 요약 정보

---

### 3.2 예매 취소

확정 전 예매를 취소하는 플로우이다.

**프론트엔드**

내 예매 페이지에서 "취소하기" 버튼 클릭 -> `confirm()` 대화상자 -> 확인:

```
reservationsApi.cancel(id) -> http.post(`/reservations/${id}/cancel`)
```
(`apps/web/src/lib/api-client.ts:167`)

**게이트웨이 라우팅**

`/api/v1/reservations/**` -> ticket-service (`services-spring/gateway-service/src/main/resources/application.yml:36-39`).

**백엔드 처리**

`ReservationController.cancel(id)` (`services-spring/ticket-service/src/main/java/com/tiketi/ticketservice/domain/reservation/controller/ReservationController.java:54-61`):
- `reservationService.cancelReservation(userId, id)` 호출 (라인 60)
- 내부 처리: 예약 상태를 `cancelled`로 변경, 좌석 잠금 해제(available로 복원)

---

### 3.3 양도 등록

확정된 예매를 양도 마켓에 등록하는 플로우이다.

**프론트엔드**

내 예매 페이지에서 `confirmed` 상태인 예매의 "양도 등록" 버튼 클릭 (`apps/web/src/app/my-reservations/page.tsx`):

```
transfersApi.create(reservationId) -> http.post("/transfers", { reservationId })
```
(`apps/web/src/lib/api-client.ts:244`)

**게이트웨이 라우팅**

`/api/v1/transfers/**` -> ticket-service (`services-spring/gateway-service/src/main/resources/application.yml:64-67`).

**백엔드 처리**

`TransferController.createListing()` (`services-spring/ticket-service/src/main/java/com/tiketi/ticketservice/domain/transfer/controller/TransferController.java:29-37`):
- JWT 인증 (라인 34)
- `reservationId` 추출 (라인 35)
- `transferService.createListing(userId, reservationId)` 호출 (라인 36)
- 내부 처리: 멤버십 등급 확인 -> 양도 수수료 계산 -> 양도 레코드 생성

---

## 4. 양도 마켓 플로우

### 4.1 양도 목록 조회

양도 가능한 티켓 목록을 탐색하는 플로우이다.

**프론트엔드 진입**

SiteHeader의 "양도 마켓" 링크 (`apps/web/src/components/site-header.tsx:93-102`) -> `/transfers` 경로.

**API 호출**

```
transfersApi.list(params) -> http.get("/transfers", { params })
```
(`apps/web/src/lib/api-client.ts:241`)

**게이트웨이 라우팅**

`/api/v1/transfers/**` -> ticket-service (`services-spring/gateway-service/src/main/resources/application.yml:64-67`).

**백엔드 처리**

`TransferController.listAvailable()` (`services-spring/ticket-service/src/main/java/com/tiketi/ticketservice/domain/transfer/controller/TransferController.java:39-49`):
- 선택적 `artistId` 필터 (라인 42)
- 페이지네이션: `page`(기본 1), `limit`(기본 20) (라인 43-44)
- `transferService.getAvailableTransfers(artistId, page, limit)` 호출 (라인 48)

**UI 렌더링** (`apps/web/src/app/transfers/page.tsx`)

양도 티켓 카드를 렌더링하며, 각 카드에 다음 정보를 표시한다:
- 이벤트명, 공연 날짜, 장소
- 원가, 양도 수수료, 총 가격
- 판매자 정보
- "구매하기" 버튼 -> `/transfer-payment/[transferId]`

**TicketTransfer 타입 정의** (`apps/web/src/lib/types.ts:184-203`):
- `original_price`: 원래 티켓 가격
- `transfer_fee`: 양도 수수료
- `total_price`: 구매자가 지불할 총 금액

---

### 4.2 양도 구매

양도 티켓을 구매하는 플로우이다.

**프론트엔드 진입**

양도 목록에서 "구매하기" 클릭 -> `/transfer-payment/[transferId]` (`apps/web/src/app/transfer-payment/[transferId]/page.tsx`).

**양도 상세 조회**

```
transfersApi.detail(id) -> http.get(`/transfers/${id}`)
```
(`apps/web/src/lib/api-client.ts:243`)

**백엔드 처리**

`TransferController.detail(id)` (`services-spring/ticket-service/src/main/java/com/tiketi/ticketservice/domain/transfer/controller/TransferController.java:57-64`):
- `transferService.getTransferDetail(id)` 호출 (라인 63)

**결제 처리**

```
paymentsApi.process({ paymentType: "transfer", referenceId, paymentMethod, amount })
```
(`apps/web/src/lib/api-client.ts:182`)

**백엔드 -- PaymentService.process() (transfer 분기)**

(`services-spring/payment-service/src/main/java/com/tiketi/paymentservice/service/PaymentService.java:260-261`):
- `ticketInternalClient.validateTransfer(referenceId, userId)` 로 양도 유효성 검증 (라인 262)
- 결제 확정 후 `completeByType("transfer")` 호출 (라인 329)
- `ticketInternalClient.confirmTransfer(referenceId, userId, paymentMethod)` 로 소유권 이전 처리 (`services-spring/payment-service/src/main/java/com/tiketi/paymentservice/service/PaymentService.java:358`)
- Kafka `PaymentConfirmedEvent` 발행 (라인 373-375)

---

## 5. 멤버십 플로우

### 5.1 멤버십 구독

아티스트 멤버십에 가입하는 플로우이다.

**프론트엔드 진입**

`/artists/[id]` 페이지에서 "멤버십 가입" 버튼 클릭 (`apps/web/src/app/artists/[id]/page.tsx`).

**멤버십 구독 API**

```
membershipsApi.subscribe(artistId) -> http.post("/memberships/subscribe", { artistId })
```
(`apps/web/src/lib/api-client.ts:234`)

**게이트웨이 라우팅**

`/api/v1/memberships/**` -> ticket-service (`services-spring/gateway-service/src/main/resources/application.yml:60-63`).

**백엔드 처리**

`MembershipController.subscribe()` (`services-spring/ticket-service/src/main/java/com/tiketi/ticketservice/domain/membership/controller/MembershipController.java:32-40`):
- JWT 인증 (라인 37)
- `membershipService.subscribe(userId, artistId)` 호출 (라인 39)
- 내부 처리: 멤버십을 `pending` 상태로 생성

**결제 후 활성화**

`/membership-payment/[membershipId]` 페이지에서 결제를 진행한다 (`apps/web/src/app/membership-payment/[membershipId]/page.tsx`).

```
paymentsApi.process({ paymentType: "membership", referenceId, paymentMethod, amount })
```

백엔드 `PaymentService.process()` -- membership 분기 (`services-spring/payment-service/src/main/java/com/tiketi/paymentservice/service/PaymentService.java:265-268`):
- `ticketInternalClient.validateMembership(referenceId, userId)` 로 검증 (라인 267)
- 결제 확정 후 `ticketInternalClient.activateMembership(referenceId)` 호출 (라인 361)
- 멤버십 상태가 `pending` -> `active`로 변경

---

### 5.2 포인트 적립

커뮤니티 활동 시 멤버십 포인트가 적립되는 플로우이다.

**게시글 작성 시 적립**

`PostService.create()` 메서드 (`services-spring/community-service/src/main/java/com/tiketi/communityservice/service/PostService.java:91-122`):
- 게시글 DB 삽입 후 (라인 93-103)
- `ticketInternalClient.awardMembershipPoints()` 호출 (라인 113-116):
  - `userId`, `artistId`, `actionType="COMMUNITY_POST"`, `points=30`, `description="커뮤니티 글 작성"`
  - 실패 시 로그만 남기고 게시글 작성은 유지 (라인 117-118)

**댓글 작성 시 적립**

`CommentService.create()` 메서드 (`services-spring/community-service/src/main/java/com/tiketi/communityservice/service/CommentService.java:58-101`):
- 댓글 DB 삽입 후 (라인 69-78)
- `ticketInternalClient.awardMembershipPoints()` 호출 (라인 92-95):
  - `actionType="COMMUNITY_COMMENT"`, `points=10`, `description="커뮤니티 댓글 작성"`
  - 실패 시 로그만 남기고 댓글 작성은 유지 (라인 96-98)

**내부 API 경로**

community-service -> ticket-service 간 내부 호출:
- `POST /internal/memberships/award-points`
- `InternalMembershipController.awardPoints()` (`services-spring/ticket-service/src/main/java/com/tiketi/ticketservice/internal/controller/InternalMembershipController.java:26-46`):
  - 내부 토큰 검증: `internalTokenValidator.requireValidToken(authorization)` (라인 30)
  - `artistId`가 있으면: 해당 아티스트 멤버십에만 포인트 적립 (라인 32-35)
  - `artistId`가 없으면: 사용자의 모든 멤버십에 포인트 적립 (라인 37-39)

---

### 5.3 멤버십 등급 확인

사용자가 자신의 멤버십 현황을 확인하는 플로우이다.

**프론트엔드 진입**

SiteHeader의 "내 멤버십" 링크 (`apps/web/src/components/site-header.tsx:83-91`) -> `/my-memberships` 경로 (`apps/web/src/app/my-memberships/page.tsx`).

**API 호출**

```
membershipsApi.my() -> http.get("/memberships/my")
```
(`apps/web/src/lib/api-client.ts:235`)

**백엔드 처리**

`MembershipController.myMemberships()` (`services-spring/ticket-service/src/main/java/com/tiketi/ticketservice/domain/membership/controller/MembershipController.java:42-46`):
- `membershipService.getMyMemberships(userId)` 호출 (라인 45)

**혜택 조회**

```
membershipsApi.benefits(artistId) -> http.get(`/memberships/benefits/${artistId}`)
```
(`apps/web/src/lib/api-client.ts:237`)

`MembershipController.benefits()` (`services-spring/ticket-service/src/main/java/com/tiketi/ticketservice/domain/membership/controller/MembershipController.java:57-64`):
- `membershipService.getUserBenefitsForArtist(userId, artistId)` 호출 (라인 63)

**멤버십 등급 체계** (`apps/web/src/lib/types.ts:138`):
- `MembershipTier`: "BRONZE" | "SILVER" | "GOLD" | "DIAMOND"

**ArtistMembership 타입** (`apps/web/src/lib/types.ts:151-164`):
- `tier`: 현재 등급
- `effective_tier`: 혜택 적용 등급 (결제 상태에 따라 다를 수 있음)
- `points`: 누적 포인트
- `status`: 멤버십 상태

**MembershipBenefits 타입** (`apps/web/src/lib/types.ts:174-183`):
- `preSalePhase`: 선예매 단계 번호
- `bookingFeeSurcharge`: 예매 수수료 할증
- `transferAccess`: 양도 기능 접근 가능 여부
- `transferFeePercent`: 양도 수수료율
- `nextTierThreshold`: 다음 등급까지 필요 포인트

---

## 6. 커뮤니티 플로우

### 6.1 게시글 관리

커뮤니티 게시글의 조회/작성/수정/삭제 플로우이다.

**프론트엔드 진입**

SiteHeader의 "커뮤니티" 링크 (`apps/web/src/components/site-header.tsx:61-69`) -> `/community` 경로.

#### 게시글 목록 조회

```
communityApi.posts(params) -> http.get("/community/posts", { params })
```
(`apps/web/src/lib/api-client.ts:203`)

**게이트웨이 라우팅**

`/api/v1/community/**` -> community-service(`${COMMUNITY_SERVICE_URL:http://localhost:3008}`) (`services-spring/gateway-service/src/main/resources/application.yml:48-51`).

**백엔드 처리**

`CommunityPostController.list()` (`services-spring/community-service/src/main/java/com/tiketi/communityservice/controller/CommunityPostController.java:28-34`):
- 선택적 `artistId` 필터, `page`, `limit` 파라미터 (라인 30-32)
- `postService.list(artistId, page, limit)` 호출 (라인 33)

`PostService.list()` (`services-spring/community-service/src/main/java/com/tiketi/communityservice/service/PostService.java:33-77`):
- 페이지네이션 안전 처리: 최소 1페이지, 최대 100개 (라인 34-35)
- 정렬: 고정글(`is_pinned`) 우선, 생성일 내림차순 (라인 48)
- 응답: `{ posts, pagination: { page, limit, total, totalPages } }` (라인 64-76)

**UI 렌더링** (`apps/web/src/app/community/page.tsx`)

게시글 카드 목록을 렌더링하며, 각 카드에 제목, 작성자, 작성일, 조회수, 댓글 수를 표시한다.

#### 게시글 상세 조회

```
communityApi.postDetail(id) -> http.get(`/community/posts/${id}`)
```
(`apps/web/src/lib/api-client.ts:204`)

`CommunityPostController.detail(id)` (`services-spring/community-service/src/main/java/com/tiketi/communityservice/controller/CommunityPostController.java:36-39`):
- `postService.detail(id)` 호출 (라인 38)
- 조회수 1 증가 처리 (`services-spring/community-service/src/main/java/com/tiketi/communityservice/service/PostService.java:80-81`)

**게시글 상세 UI** (`apps/web/src/app/community/[postId]/page.tsx`):
- 게시글 내용 표시
- 댓글 목록 로드 및 표시
- 수정/삭제 버튼 (작성자 본인만)

#### 게시글 작성

```
communityApi.createPost({ title, content, artist_id }) -> http.post("/community/posts", payload)
```
(`apps/web/src/lib/api-client.ts:205-206`)

`CommunityPostController.create()` (`services-spring/community-service/src/main/java/com/tiketi/communityservice/controller/CommunityPostController.java:41-47`):
- JWT 인증 (라인 45)
- `postService.create(request, user)` 호출 (라인 46)

`PostService.create()` (`services-spring/community-service/src/main/java/com/tiketi/communityservice/service/PostService.java:91-122`):
- DB에 게시글 삽입 (라인 93-103)
- 멤버십 포인트 30점 적립 (라인 112-118)
- HTTP 201 Created 응답

**작성 UI** (`apps/web/src/app/community/write/page.tsx`):
- 제목, 내용 입력 폼
- 아티스트 선택

#### 게시글 수정

```
communityApi.updatePost(id, { title, content }) -> http.put(`/community/posts/${id}`, payload)
```
(`apps/web/src/lib/api-client.ts:207-208`)

`CommunityPostController.update()` (`services-spring/community-service/src/main/java/com/tiketi/communityservice/controller/CommunityPostController.java:49-56`):
- 소유권 확인: 작성자 본인만 수정 가능 (`services-spring/community-service/src/main/java/com/tiketi/communityservice/service/PostService.java:126-135`)

#### 게시글 삭제

```
communityApi.deletePost(id) -> http.delete(`/community/posts/${id}`)
```
(`apps/web/src/lib/api-client.ts:209`)

`CommunityPostController.delete()` (`services-spring/community-service/src/main/java/com/tiketi/communityservice/controller/CommunityPostController.java:58-64`):
- 소유권 확인: 작성자 본인 또는 관리자만 삭제 가능 (`services-spring/community-service/src/main/java/com/tiketi/communityservice/service/PostService.java:148-158`)

---

### 6.2 댓글 관리

게시글에 대한 댓글 조회/작성/삭제 플로우이다.

#### 댓글 목록 조회

```
communityApi.comments(postId, params) -> http.get(`/community/posts/${postId}/comments`, { params })
```
(`apps/web/src/lib/api-client.ts:210-211`)

`CommentController.list()` (`services-spring/community-service/src/main/java/com/tiketi/communityservice/controller/CommentController.java:27-33`):
- `commentService.listByPost(postId, page, limit)` 호출 (라인 32)

`CommentService.listByPost()` (`services-spring/community-service/src/main/java/com/tiketi/communityservice/service/CommentService.java:32-56`):
- 생성일 오름차순 정렬 (라인 43)
- 응답: `{ comments, pagination: { page, limit, total } }` (라인 47-55)

#### 댓글 작성

```
communityApi.createComment(postId, { content }) -> http.post(`/community/posts/${postId}/comments`, payload)
```
(`apps/web/src/lib/api-client.ts:212-213`)

`CommentController.create()` (`services-spring/community-service/src/main/java/com/tiketi/communityservice/controller/CommentController.java:35-42`):
- JWT 인증 (라인 40)
- `commentService.create(postId, request, user)` 호출 (라인 41)

`CommentService.create()` (`services-spring/community-service/src/main/java/com/tiketi/communityservice/service/CommentService.java:58-101`):
- 게시글 존재 확인 및 `artist_id` 조회 (라인 61-67)
- DB에 댓글 삽입 (라인 69-78)
- 게시글의 `comment_count` 1 증가 (라인 86)
- 멤버십 포인트 10점 적립 (라인 92-95)

#### 댓글 삭제

```
communityApi.deleteComment(postId, commentId) -> http.delete(`/community/posts/${postId}/comments/${commentId}`)
```
(`apps/web/src/lib/api-client.ts:214-215`)

`CommentController.delete()` (`services-spring/community-service/src/main/java/com/tiketi/communityservice/controller/CommentController.java:44-52`):
- `commentService.delete(commentId, user)` 호출 (라인 50)

`CommentService.delete()` (`services-spring/community-service/src/main/java/com/tiketi/communityservice/service/CommentService.java:103-123`):
- 소유권 확인: 작성자 본인 또는 관리자 (라인 112-113)
- 댓글 삭제 후 `comment_count` 1 감소 (라인 117-119)

**CommunityPost 타입** (`apps/web/src/lib/types.ts:230-242`):
- `views`: 조회수
- `comment_count`: 댓글 수
- `is_pinned`: 고정글 여부

**CommunityComment 타입** (`apps/web/src/lib/types.ts:244-251`)

---

## 7. 관리자 플로우

### 7.1 이벤트 관리

관리자가 이벤트를 생성/수정/삭제하는 플로우이다.

**프론트엔드 진입**

SiteHeader에서 `user.role === "admin"` 인 경우에만 "관리자" 링크가 표시된다 (`apps/web/src/components/site-header.tsx:103-113`). 클릭 시 `/admin` -> `/admin/events` 경로로 이동한다 (`apps/web/src/app/admin/events/page.tsx`).

**이벤트 목록 조회**

관리자 이벤트 목록은 일반 이벤트 API를 사용하여 전체 이벤트를 조회한다:

```
eventsApi.list(params) -> http.get("/events", { params })
```
(`apps/web/src/lib/api-client.ts:144`)

#### 이벤트 생성

```
adminApi.events.create(payload) -> http.post("/admin/events", payload)
```
(`apps/web/src/lib/api-client.ts:252`)

**게이트웨이 라우팅**

`/api/v1/admin/**` -> catalog-service (`services-spring/gateway-service/src/main/resources/application.yml:44-47`).

**백엔드 처리**

`AdminController.createEvent()` (`services-spring/catalog-service/src/main/java/com/tiketi/catalogservice/domain/admin/controller/AdminController.java:52-60`):
- `@AuditLog(action = "CREATE_EVENT")` 어노테이션으로 감사 로그 기록 (라인 52)
- 관리자 권한 확인: `jwtTokenParser.requireAdmin(request)` (라인 58)
- `adminService.createEvent(body, admin.userId())` 호출 (라인 59)
- HTTP 201 Created 응답

#### 이벤트 수정

```
adminApi.events.update(id, payload) -> http.put(`/admin/events/${id}`, payload)
```
(`apps/web/src/lib/api-client.ts:253`)

`AdminController.updateEvent()` (`services-spring/catalog-service/src/main/java/com/tiketi/catalogservice/domain/admin/controller/AdminController.java:62-71`)

#### 이벤트 취소

```
adminApi.events.cancel(id) -> http.post(`/admin/events/${id}/cancel`)
```
(`apps/web/src/lib/api-client.ts:254`)

`AdminController.cancelEvent()` (`services-spring/catalog-service/src/main/java/com/tiketi/catalogservice/domain/admin/controller/AdminController.java:73-81`)

#### 좌석 생성

```
adminApi.events.generateSeats(id) -> http.post(`/admin/events/${id}/generate-seats`)
```
(`apps/web/src/lib/api-client.ts:256`)

`AdminController.generateSeats()` (`services-spring/catalog-service/src/main/java/com/tiketi/catalogservice/domain/admin/controller/AdminController.java:93-101`):
- `@AuditLog(action = "GENERATE_SEATS")` (라인 93)
- `adminService.generateSeats(id)` 호출 (라인 100)

#### 티켓 유형 생성

```
adminApi.tickets.create(eventId, payload) -> http.post(`/admin/events/${eventId}/tickets`, payload)
```
(`apps/web/src/lib/api-client.ts:260-261`)

`AdminController.createTicket()` (`services-spring/catalog-service/src/main/java/com/tiketi/catalogservice/domain/admin/controller/AdminController.java:113-122`)

---

### 7.2 예매 관리

관리자가 예매 목록을 조회하고 상태를 변경하는 플로우이다.

**프론트엔드 진입**

`/admin/reservations` 경로 (`apps/web/src/app/admin/reservations/page.tsx:36-138`).

**API 호출**

```
adminApi.reservations.list() -> http.get("/admin/reservations", { params })
```
(`apps/web/src/lib/api-client.ts:266`)

데이터 파싱: `res.data.reservations ?? res.data.data ?? []` (`apps/web/src/app/admin/reservations/page.tsx:44`)

**백엔드 처리**

`AdminController.reservations()` (`services-spring/catalog-service/src/main/java/com/tiketi/catalogservice/domain/admin/controller/AdminController.java:135-144`):
- 관리자 권한 확인 (라인 142)
- `adminService.listReservations(page, limit, status)` 호출 (라인 143)

#### 예매 상태 변경

드롭다운에서 상태를 선택하면:

```
adminApi.reservations.updateStatus(id, status) -> http.patch(`/admin/reservations/${id}/status`, { status })
```
(`apps/web/src/lib/api-client.ts:267-268`)

`handleStatusChange` 함수 (`apps/web/src/app/admin/reservations/page.tsx:53-61`):
- `confirm()` 대화상자로 확인 (라인 54)
- 성공 시 목록 새로고침 (라인 57)

`AdminController.updateReservationStatus()` (`services-spring/catalog-service/src/main/java/com/tiketi/catalogservice/domain/admin/controller/AdminController.java:146-155`):
- `@AuditLog(action = "UPDATE_RESERVATION_STATUS")` (라인 146)
- `adminService.updateReservationStatus(id, body)` 호출 (라인 154)

**상태 배지 매핑** (`apps/web/src/app/admin/reservations/page.tsx:19-34`):
- confirmed/completed: "확정"(sky)
- pending/waiting: "대기"(amber)
- cancelled: "취소"(red)
- refunded: "환불"(slate)

---

### 7.3 통계 대시보드

관리자가 플랫폼 전체 통계를 조회하는 플로우이다.

**프론트엔드 진입**

`/admin/statistics` 경로 (`apps/web/src/app/admin/statistics/page.tsx:62-1116`).

`AuthGuard` 컴포넌트로 관리자 전용 접근 제어: `<AuthGuard adminOnly>` (라인 136).

**API 호출 -- 병렬 초기 로드**

12개의 통계 API를 `Promise.all`로 병렬 호출한다 (`apps/web/src/app/admin/statistics/page.tsx:87-111`):

| 순서 | API 호출 | 상태 변수 |
|------|---------|-----------|
| 1 | `statsApi.realtime()` | `realtime` |
| 2 | `statsApi.overview()` | `overview` |
| 3 | `statsApi.conversion(30)` | `conversion` |
| 4 | `statsApi.hourlyTraffic(7)` | `hourly` |
| 5 | `statsApi.daily(30)` | `daily` |
| 6 | `statsApi.revenue({ period: "daily", days: 30 })` | `revenue` |
| 7 | `statsApi.cancellations(30)` | `cancellations` |
| 8 | `statsApi.seatPreferences()` | `seatPrefs` |
| 9 | `statsApi.userBehavior(30)` | `userBehavior` |
| 10 | `statsApi.performance()` | `performance` |
| 11 | `statsApi.events({ limit: 20, sortBy: "revenue" })` | `eventStats` |
| 12 | `statsApi.payments()` | `payments` |

statsApi 정의: `apps/web/src/lib/api-client.ts:185-200`

**게이트웨이 라우팅**

`/api/v1/stats/**` -> stats-service(`${STATS_SERVICE_URL:http://localhost:3004}`) (`services-spring/gateway-service/src/main/resources/application.yml:20-23`).

**실시간 자동 갱신**

30초 간격으로 `statsApi.realtime()` 을 재호출한다 (`apps/web/src/app/admin/statistics/page.tsx:114-117`).

**대시보드 섹션 구성**

12개 섹션으로 구성되며, Recharts 라이브러리를 사용하여 시각화한다:

1. **실시간 현황** (라인 155-227): 잠긴 좌석, 활성 결제, 활성 사용자, 최근 1시간 매출, 인기 이벤트
2. **개요** (라인 232-277): 총 사용자, 진행 중 이벤트, 확정 예매, 총 매출
3. **전환 퍼널** (라인 282-362): 완료/대기/취소 비율 비율 막대, 전환율/취소율/대기율 카드
4. **시간대별 트래픽** (라인 367-412): BarChart -- 시간대별 예매 건수, 피크 시간대 표시
5. **일별 예매 추이** (라인 417-473): LineChart -- 예매/확정 추이 (30일)
6. **일별 매출 추이** (라인 478-545): AreaChart -- 매출 추이 (30일), 그래디언트 영역
7. **취소/환불 분석** (라인 550-628): 총 취소, 평균 취소 소요 시간, 총 환불 금액, 이벤트별 취소 현황 테이블
8. **좌석 선호도** (라인 633-728): PieChart -- 구역별 예매 비율, 가격대별 선호도 수평 막대
9. **사용자 행동** (라인 733-867): PieChart -- 사용자 유형(신규/재방문/충성), 지출 분포, 평균 예매 수, 평균 지출
10. **시스템 성능** (라인 872-933): DB 크기, 성공률, 테이블 수 및 행 수
11. **이벤트별 통계** (라인 938-999): 테이블 -- 이벤트명, 예매 수, 매출, 좌석 활용률
12. **결제 수단** (라인 1004-1110): PieChart -- 결제 수단 비율, 테이블 -- 수단별 건수/총액/평균

---

## 8. 검색 플로우

사용자가 공연이나 아티스트를 검색하는 플로우이다.

**프론트엔드 -- 검색바**

SiteHeader에 검색 폼이 위치한다 (`apps/web/src/components/site-header.tsx:32-47`):
- `<form onSubmit={handleSearch}>` (라인 32)
- 입력 필드: `placeholder="공연, 아티스트 검색"` (라인 37)
- 검색 실행 시: `router.push(\`/search?q=${encodeURIComponent(q)}\`)` (라인 18)

**검색 결과 페이지**

`/search?q=query` 경로에서 `SearchResults` 컴포넌트가 마운트된다 (`apps/web/src/app/search/page.tsx:14-86`).

- URL 쿼리 파라미터에서 `q` 추출: `useSearchParams().get("q")` (라인 16)
- `Suspense`로 감싸 비동기 렌더링 지원 (라인 90-96)

**API 호출**

```
eventsApi.list({ q: query, page: 1, limit: 30 }) -> http.get("/events", { params })
```
(`apps/web/src/app/search/page.tsx:27`, `apps/web/src/lib/api-client.ts:144`)

**게이트웨이 라우팅**

`/api/v1/events/**` -> catalog-service (`services-spring/gateway-service/src/main/resources/application.yml:24-27`).

**백엔드 처리**

`EventController.getEvents()` 메서드가 `q` 파라미터를 `searchQuery`로 받아 검색을 수행한다 (`services-spring/catalog-service/src/main/java/com/tiketi/catalogservice/domain/event/controller/EventController.java:22-30`):
- `eventReadService.listEvents(status, searchQuery, page, limit)` (라인 29)

**UI 렌더링** (`apps/web/src/app/search/page.tsx:40-86`):

- 검색 결과 수 표시: `"{q}" 검색 결과 {results.length}건` (라인 45)
- 검색어가 비어있으면: "헤더에서 검색어를 입력하세요" 안내 (라인 48)
- 결과가 없으면: "검색 결과가 없습니다" 표시 (라인 56-58)
- 결과 카드: 이벤트명, 장소, 일자, 아티스트명, 가격대 (라인 61-82)
- 카드 클릭 시: `/events/${event.id}` 로 이동 (라인 63)
