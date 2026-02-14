# 프론트엔드 분석

URR(우르르) 티켓팅 플랫폼 프론트엔드 기술 분석 문서.

> 기준 경로: `apps/web/`

---

## 1. 기술 스택

| 라이브러리 | 버전 | 용도 |
|---|---|---|
| Next.js | `16.1.6` | App Router 기반 프레임워크 |
| React | `19.2.3` | UI 라이브러리 |
| React DOM | `19.2.3` | DOM 렌더링 |
| TypeScript | `5.9.3` | 정적 타입 시스템 |
| Tailwind CSS | `^4` | 유틸리티 CSS 프레임워크 |
| Axios | `^1.13.5` | HTTP 클라이언트 |
| Recharts | `^3.7.0` | 차트/데이터 시각화 |
| @tosspayments/payment-sdk | `^1.9.2` | 토스페이먼츠 결제 연동 |
| Vitest | `^4.0.18` | 단위 테스트 러너 |
| @testing-library/react | `^16.3.2` | React 컴포넌트 테스트 유틸 |
| @playwright/test | `^1.58.2` | E2E 테스트 프레임워크 |
| happy-dom | `^20.6.1` | Vitest용 DOM 환경 |
| ESLint | `^9` | 린트 도구 (eslint-config-next) |

> 출처: `apps/web/package.json:14-37`

---

## 2. 앱 라우터 페이지 구조

### 인증

| 경로 | 파일 |
|---|---|
| `/login` | `apps/web/src/app/login/page.tsx` |
| `/register` | `apps/web/src/app/register/page.tsx` |

### 이벤트 / 예매

| 경로 | 파일 |
|---|---|
| `/` (홈 - 이벤트 목록) | `apps/web/src/app/page.tsx` |
| `/events/[id]` (이벤트 상세) | `apps/web/src/app/events/[id]/page.tsx` |
| `/events/[id]/book` (티켓 예매) | `apps/web/src/app/events/[id]/book/page.tsx` |
| `/events/[id]/seats` (좌석 선택) | `apps/web/src/app/events/[id]/seats/page.tsx` |
| `/search` (검색 결과) | `apps/web/src/app/search/page.tsx` |
| `/queue/[eventId]` (대기열) | `apps/web/src/app/queue/[eventId]/page.tsx` |

### 결제

| 경로 | 파일 |
|---|---|
| `/payment/[reservationId]` | `apps/web/src/app/payment/[reservationId]/page.tsx` |
| `/payment/success` | `apps/web/src/app/payment/success/page.tsx` |
| `/payment/fail` | `apps/web/src/app/payment/fail/page.tsx` |
| `/payment-success/[reservationId]` | `apps/web/src/app/payment-success/[reservationId]/page.tsx` |
| `/membership-payment/[membershipId]` | `apps/web/src/app/membership-payment/[membershipId]/page.tsx` |
| `/transfer-payment/[transferId]` | `apps/web/src/app/transfer-payment/[transferId]/page.tsx` |

### 사용자

| 경로 | 파일 |
|---|---|
| `/my-reservations` | `apps/web/src/app/my-reservations/page.tsx` |
| `/my-memberships` | `apps/web/src/app/my-memberships/page.tsx` |
| `/reservations/[id]` (예매 상세) | `apps/web/src/app/reservations/[id]/page.tsx` |
| `/transfers` (양도 마켓) | `apps/web/src/app/transfers/page.tsx` |
| `/transfers/my` (내 양도) | `apps/web/src/app/transfers/my/page.tsx` |

### 아티스트

| 경로 | 파일 |
|---|---|
| `/artists` | `apps/web/src/app/artists/page.tsx` |
| `/artists/[id]` | `apps/web/src/app/artists/[id]/page.tsx` |

### 커뮤니티

| 경로 | 파일 |
|---|---|
| `/community` | `apps/web/src/app/community/page.tsx` |
| `/community/write` | `apps/web/src/app/community/write/page.tsx` |
| `/community/[postId]` | `apps/web/src/app/community/[postId]/page.tsx` |
| `/community/[postId]/edit` | `apps/web/src/app/community/[postId]/edit/page.tsx` |

### 뉴스

| 경로 | 파일 |
|---|---|
| `/news` | `apps/web/src/app/news/page.tsx` |
| `/news/create` | `apps/web/src/app/news/create/page.tsx` |
| `/news/[id]` | `apps/web/src/app/news/[id]/page.tsx` |
| `/news/[id]/edit` | `apps/web/src/app/news/[id]/edit/page.tsx` |

### 관리자

| 경로 | 파일 |
|---|---|
| `/admin` (대시보드) | `apps/web/src/app/admin/page.tsx` |
| `/admin/events` (이벤트 관리) | `apps/web/src/app/admin/events/page.tsx` |
| `/admin/events/new` | `apps/web/src/app/admin/events/new/page.tsx` |
| `/admin/events/edit/[id]` | `apps/web/src/app/admin/events/edit/[id]/page.tsx` |
| `/admin/reservations` | `apps/web/src/app/admin/reservations/page.tsx` |
| `/admin/statistics` | `apps/web/src/app/admin/statistics/page.tsx` |

총 **35개** 페이지.

---

## 3. API 클라이언트

> 출처: `apps/web/src/lib/api-client.ts`

### 3.1 Base URL 결정 로직

`resolveBaseUrl()` 함수가 우선순위에 따라 API 서버 주소를 결정한다 (라인 8-36).

| 순서 | 조건 | 결과 |
|---|---|---|
| 1 | `NEXT_PUBLIC_API_URL` 환경변수 존재 | 해당 값 사용 |
| 2 | SSR (`typeof window === "undefined"`) | `http://localhost:3001` |
| 3 | `window.__API_URL` 주입됨 (K8s) | 해당 값 사용 |
| 4 | localhost / 127.0.0.1 | `http://localhost:3001` |
| 5 | 사설 IP (172.x, 192.168.x, 10.x) | `http://{hostname}:3001` |
| 6 | 프로덕션 (그 외) | `""` (same-origin) |

### 3.2 Axios 인스턴스 설정

```typescript
// api-client.ts:38-43
const http = axios.create({
  baseURL: `${resolveBaseUrl()}/api/v1`,
  headers: { "Content-Type": "application/json" },
  withCredentials: true,   // 쿠키 기반 인증
  timeout: 15000,          // 15초 타임아웃
});
```

### 3.3 요청 인터셉터

대기열 진입 토큰(`tiketi-entry-token` 쿠키)을 `x-queue-entry-token` 헤더에 첨부한다. Lambda@Edge와 Gateway에서 VWR(가상 대기실) 검증에 사용된다.

```typescript
// api-client.ts:70-77
http.interceptors.request.use((config) => {
  const entryToken = getCookie("tiketi-entry-token");
  if (entryToken) {
    config.headers["x-queue-entry-token"] = entryToken;
  }
  return config;
});
```

> 출처: `apps/web/src/lib/api-client.ts:70-77`

### 3.4 응답 인터셉터

**401 Unauthorized - 자동 토큰 갱신** (라인 85-117):

1. 인증 엔드포인트(`/auth/login`, `/auth/register`, `/auth/refresh`)에 대한 401은 그대로 reject한다.
2. 이미 갱신 중이면 요청을 `failedQueue`에 추가하고 대기한다.
3. `POST /auth/refresh`를 호출하여 쿠키 기반으로 갱신한다.
4. 성공 시 대기열의 모든 요청을 재시도한다.
5. 실패 시 `clearAuth()`로 로컬 데이터를 지우고 `/login`으로 리다이렉트한다.

**429 Too Many Requests - 지수 백오프** (라인 120-128):

- 최대 2회 재시도한다.
- 지연 시간: `min(1000 * 2^retryCount, 4000)` ms.
- 첫 번째 재시도: 1초, 두 번째: 2초.

### 3.5 API 네임스페이스

#### `authApi` (라인 134-141)
| 메서드 | 시그니처 |
|---|---|
| `register` | `(payload: { email, password, name, phone? }) => POST /auth/register` |
| `login` | `(payload: { email, password }) => POST /auth/login` |
| `me` | `() => GET /auth/me` |
| `refresh` | `() => POST /auth/refresh` |
| `logout` | `() => POST /auth/logout` |

#### `eventsApi` (라인 143-146)
| 메서드 | 시그니처 |
|---|---|
| `list` | `(params?) => GET /events` |
| `detail` | `(id) => GET /events/{id}` |

#### `queueApi` (라인 148-153)
| 메서드 | 시그니처 |
|---|---|
| `check` | `(eventId) => POST /queue/check/{eventId}` |
| `status` | `(eventId) => GET /queue/status/{eventId}` |
| `heartbeat` | `(eventId) => POST /queue/heartbeat/{eventId}` |
| `leave` | `(eventId) => POST /queue/leave/{eventId}` |

#### `reservationsApi` (라인 155-164)
| 메서드 | 시그니처 |
|---|---|
| `createTicketOnly` | `(payload: { eventId, items[] }) => POST /reservations` |
| `mine` | `() => GET /reservations/my` |
| `byId` | `(id) => GET /reservations/{id}` |
| `cancel` | `(id) => POST /reservations/{id}/cancel` |

#### `seatsApi` (라인 166-170)
| 메서드 | 시그니처 |
|---|---|
| `byEvent` | `(eventId) => GET /seats/events/{eventId}` |
| `reserve` | `(payload: { eventId, seatIds[] }) => POST /seats/reserve` |

#### `paymentsApi` (라인 172-176)
| 메서드 | 시그니처 |
|---|---|
| `prepare` | `(payload) => POST /payments/prepare` |
| `confirm` | `(payload) => POST /payments/confirm` |
| `process` | `(payload) => POST /payments/process` |

#### `statsApi` (라인 178-193)
| 메서드 | 시그니처 |
|---|---|
| `overview` | `() => GET /stats/overview` |
| `daily` | `(days?) => GET /stats/daily` |
| `events` | `(params?) => GET /stats/events` |
| `eventDetail` | `(eventId) => GET /stats/events/{eventId}` |
| `payments` | `() => GET /stats/payments` |
| `revenue` | `(params?) => GET /stats/revenue` |
| `users` | `(days?) => GET /stats/users` |
| `hourlyTraffic` | `(days?) => GET /stats/hourly-traffic` |
| `conversion` | `(days?) => GET /stats/conversion` |
| `cancellations` | `(days?) => GET /stats/cancellations` |
| `realtime` | `() => GET /stats/realtime` |
| `performance` | `() => GET /stats/performance` |
| `seatPreferences` | `(eventId?) => GET /stats/seat-preferences` |
| `userBehavior` | `(days?) => GET /stats/user-behavior` |

#### `communityApi` (라인 195-209)
| 메서드 | 시그니처 |
|---|---|
| `posts` | `(params?) => GET /community/posts` |
| `postDetail` | `(id) => GET /community/posts/{id}` |
| `createPost` | `(payload: { title, content, artist_id }) => POST /community/posts` |
| `updatePost` | `(id, payload: { title, content }) => PUT /community/posts/{id}` |
| `deletePost` | `(id) => DELETE /community/posts/{id}` |
| `comments` | `(postId, params?) => GET /community/posts/{postId}/comments` |
| `createComment` | `(postId, payload: { content }) => POST /community/posts/{postId}/comments` |
| `deleteComment` | `(postId, commentId) => DELETE /community/posts/{postId}/comments/{commentId}` |

#### `newsApi` (라인 211-219)
| 메서드 | 시그니처 |
|---|---|
| `list` | `() => GET /news` |
| `byId` | `(id) => GET /news/{id}` |
| `create` | `(payload: { title, content, author, author_id?, is_pinned? }) => POST /news` |
| `update` | `(id, payload: { title, content, is_pinned? }) => PUT /news/{id}` |
| `delete` | `(id) => DELETE /news/{id}` |

#### `artistsApi` (라인 221-224)
| 메서드 | 시그니처 |
|---|---|
| `list` | `(params?) => GET /artists` |
| `detail` | `(id) => GET /artists/{id}` |

#### `membershipsApi` (라인 226-231)
| 메서드 | 시그니처 |
|---|---|
| `subscribe` | `(artistId) => POST /memberships/subscribe` |
| `my` | `() => GET /memberships/my` |
| `myForArtist` | `(artistId) => GET /memberships/my/{artistId}` |
| `benefits` | `(artistId) => GET /memberships/benefits/{artistId}` |

#### `transfersApi` (라인 233-239)
| 메서드 | 시그니처 |
|---|---|
| `list` | `(params?) => GET /transfers` |
| `my` | `() => GET /transfers/my` |
| `detail` | `(id) => GET /transfers/{id}` |
| `create` | `(reservationId) => POST /transfers` |
| `cancel` | `(id) => POST /transfers/{id}/cancel` |

#### `adminApi` (라인 241-263)
| 메서드 | 시그니처 |
|---|---|
| `dashboard` | `() => GET /admin/dashboard` |
| `seatLayouts` | `() => GET /admin/seat-layouts` |
| `events.create` | `(payload) => POST /admin/events` |
| `events.update` | `(id, payload) => PUT /admin/events/{id}` |
| `events.cancel` | `(id) => POST /admin/events/{id}/cancel` |
| `events.remove` | `(id) => DELETE /admin/events/{id}` |
| `events.generateSeats` | `(id) => POST /admin/events/{id}/generate-seats` |
| `events.clearSeats` | `(id) => DELETE /admin/events/{id}/seats` |
| `tickets.create` | `(eventId, payload) => POST /admin/events/{eventId}/tickets` |
| `tickets.update` | `(id, payload) => PUT /admin/tickets/{id}` |
| `reservations.list` | `(params?) => GET /admin/reservations` |
| `reservations.updateStatus` | `(id, status) => PATCH /admin/reservations/{id}/status` |

---

## 4. 타입 시스템

> 출처: `apps/web/src/lib/types.ts`

### `UserRole` (라인 1)
`"user" | "admin"` 유니온 타입.

### `AuthUser` (라인 3-9)
| 필드 | 타입 | 설명 |
|---|---|---|
| `id` | `string` | 사용자 고유 ID |
| `userId` | `string?` | 대체 ID 필드 |
| `email` | `string` | 이메일 주소 |
| `name` | `string` | 사용자 이름 |
| `role` | `UserRole` | 권한 (`user` / `admin`) |

### `EventSummary` (라인 11-23)
| 필드 | 타입 | 설명 |
|---|---|---|
| `id` | `string` | 이벤트 ID |
| `title` | `string` | 이벤트 제목 |
| `venue` | `string?` | 공연장 |
| `event_date` | `string?` | 공연 일시 |
| `status` | `string?` | 상태 |
| `artist_name` | `string?` | 아티스트명 |
| `min_price` / `max_price` | `number?` | 가격 범위 |
| `poster_image_url` | `string?` | 포스터 이미지 |
| `sale_start_date` / `sale_end_date` | `string?` | 판매 기간 |

### `EventDetail` (라인 26-40) - `EventSummary` 확장
snake_case와 camelCase 양쪽 별칭을 모두 제공한다. 추가 필드:

| 필드 | 타입 | 설명 |
|---|---|---|
| `description` | `string?` | 상세 설명 |
| `address` | `string?` | 주소 |
| `seat_layout_id` / `seatLayoutId` | `string \| null?` | `null` = 스탠딩, UUID = 좌석 배치 존재 |
| `ticket_types` / `ticketTypes` | `TicketType[]?` | 티켓 유형 목록 |

### `TicketType` (라인 42-50)
| 필드 | 타입 | 설명 |
|---|---|---|
| `id` | `string` | 티켓 유형 ID |
| `name` | `string` | 이름 (VIP, 일반 등) |
| `price` | `number` | 가격 |
| `total_quantity` | `number` | 총 수량 |
| `available_quantity` | `number` | 잔여 수량 |

### `Seat` (라인 52-61)
| 필드 | 타입 | 설명 |
|---|---|---|
| `id` | `string` | 좌석 ID |
| `section` | `string` | 구역 |
| `row_number` / `seat_number` | `number` | 열/번호 |
| `seat_label` | `string` | 표시 라벨 |
| `price` | `number` | 가격 |
| `status` | `"available" \| "reserved" \| "locked"` | 상태 |

### `SeatLayout` / `SeatLayoutSection` (라인 63-77)
좌석 배치도 설정. `layout_config.sections` 배열로 구역별 행 수, 열 수, 가격을 정의한다.

### `Reservation` (라인 94-108)
| 필드 | 타입 | 설명 |
|---|---|---|
| `id` | `string` | 예매 ID |
| `reservation_number` | `string` | 예매 번호 |
| `total_amount` | `number` | 총 결제 금액 |
| `status` | `string` | 예매 상태 |
| `payment_status` | `string?` | 결제 상태 |
| `expires_at` | `string?` | 만료 시각 |
| `items` | `ReservationItem[]?` | 예매 항목 |
| `event` | `EventSummary?` | 연결된 이벤트 정보 |

### `ReservationItem` (라인 79-92)
좌석 또는 티켓 유형 기반 예매 항목. `seat_info`로 좌석 정보를 포함할 수 있다.

### `QueueStatus` (라인 120-136)
| 필드 | 타입 | 설명 |
|---|---|---|
| `status` | `"queued" \| "active" \| "not_in_queue"?` | 대기 상태 |
| `queued` | `boolean` | 대기열 진입 여부 |
| `position` / `peopleAhead` / `peopleBehind` | `number?` | 순서 정보 |
| `estimatedWait` | `number?` | 예상 대기 시간 |
| `nextPoll` | `number?` | 다음 폴링 간격(초) |
| `entryToken` | `string?` | 진입 토큰 |

### `MembershipTier` (라인 138)
`"BRONZE" | "SILVER" | "GOLD" | "DIAMOND"` 등급 체계.

### `Artist` (라인 140-149)
아티스트 정보. `membership_price`, `event_count`, `member_count` 포함.

### `ArtistMembership` (라인 151-164)
| 필드 | 타입 | 설명 |
|---|---|---|
| `tier` | `MembershipTier` | 현재 등급 |
| `effective_tier` | `MembershipTier?` | 유효 등급 (프로모션 등 반영) |
| `points` | `number` | 적립 포인트 |
| `status` | `string` | 상태 |
| `expires_at` | `string?` | 만료일 |

### `MembershipBenefits` (라인 174-182)
등급별 혜택 정보. 선예매 단계(`preSalePhase`), 예매 수수료(`bookingFeeSurcharge`), 양도 접근 여부(`transferAccess`) 등.

### `TicketTransfer` (라인 184-203)
양도 거래 정보. `original_price`, `transfer_fee`, `transfer_fee_percent`, `total_price` 등 금액 관련 필드 포함.

### `StatsOverview` / `DailyStats` / `EventStats` (라인 205-228)
관리자 통계용 인터페이스. 총 이벤트 수, 매출, 일별 통계 등.

### `CommunityPost` / `CommunityComment` (라인 230-251)
커뮤니티 게시글/댓글. 조회수(`views`), 댓글 수(`comment_count`), 고정 여부(`is_pinned`) 포함.

---

## 5. 상태 관리

URR 프론트엔드는 Redux, Zustand 등 전역 상태 관리 라이브러리를 사용하지 않는다. 상태 관리 전략은 다음과 같다.

### localStorage
- **키**: `"user"`
- **내용**: `AuthUser` 객체 (JSON 직렬화)
- **관리**: `storage.ts`의 `getUser()`, `setUser()`, `clearUser()` 함수
- **용도**: 페이지 새로고침 후에도 로그인 상태 유지

> 출처: `apps/web/src/lib/storage.ts:5-30`

### sessionStorage
- **키**: `"auth-me-ts"`
- **내용**: 타임스탬프 (밀리초)
- **TTL**: 30초
- **용도**: `AuthGuard`에서 `/auth/me` API 호출 빈도 제한. 30초 이내 재검증 시 서버 호출을 건너뛴다.

> 출처: `apps/web/src/components/auth-guard.tsx:32-37`

### Cookie
- **키**: `tiketi-entry-token`
- **설정 위치**: `useQueuePolling` 훅에서 서버 응답의 `entryToken`을 쿠키로 저장
- **속성**: `path=/; max-age=600; SameSite=Strict` (HTTPS 시 `Secure` 추가)
- **용도**: Lambda@Edge 및 Gateway에서 대기열 진입 검증

> 출처: `apps/web/src/hooks/use-queue-polling.ts:48-51`

### 컴포넌트 수준 상태
- `useState`와 `useEffect`를 활용한 로컬 상태 관리
- 페이지별 데이터 패칭은 `useEffect` 내 API 호출로 처리

---

## 6. 커스텀 훅

### `useServerTime` / `getServerNow`

> 출처: `apps/web/src/hooks/use-server-time.ts`

서버와 클라이언트 간 시간 오프셋을 계산하여 정확한 서버 시간을 추정한다.

**동작 방식**:
1. `GET /api/time`으로 서버 시간을 조회한다 (라인 24).
2. RTT(Round-Trip Time)를 측정하고 중간점을 계산한다: `clientMidpoint = before + rtt / 2` (라인 29).
3. 오프셋을 산출한다: `offset = serverTime - clientMidpoint` (라인 30).
4. 오프셋은 모듈 수준 변수 `cachedOffset`에 캐싱되어 중복 요청을 방지한다 (라인 14).
5. 동시 호출 시 `fetchPromise`로 단일 요청만 실행한다 (라인 15, 19).

```typescript
// use-server-time.ts:55-57
export function getServerNow(offset: number): number {
  return Date.now() + offset;
}
```

**반환값**: `{ offset: number, ready: boolean }`

### `useCountdown`

> 출처: `apps/web/src/hooks/use-countdown.ts`

서버 시간 기반 카운트다운 타이머.

**인터페이스**:
```typescript
// use-countdown.ts:6-14
export interface CountdownTime {
  months: number;
  days: number;
  hours: number;
  minutes: number;
  seconds: number;
  totalDays: number;
  isExpired: boolean;
}
```

**주요 설계 결정**:
- `useServerTime()`의 오프셋을 반영하여 클라이언트 시계 오차를 보정한다 (라인 44).
- `expiredRef`로 만료 콜백(`onExpire`)의 중복 실행을 방지한다 (라인 45, 79-81).
- 컴포넌트 리마운트 시 이미 만료된 상태면 `onExpire`를 호출하지 않는다 (라인 66-74). 이는 `onExpire -> fetchEvents -> 리마운트 -> onExpire`의 무한 루프를 방지한다.
- 1초 간격 `setInterval`로 갱신한다 (라인 86).

**유틸리티 함수**:
- `formatCountdown(t, showMonths?)`: `"3일 2시간 15분 30초"` 형식 (라인 93-104)
- `formatCountdownShort(t)`: `"MM:SS"` 형식 (라인 106-110)

### `useQueuePolling`

> 출처: `apps/web/src/hooks/use-queue-polling.ts`

대기열 상태를 적응형 폴링으로 조회한다.

**폴링 간격 제어**:
- 기본값: 3초 (라인 7)
- 최솟값: 1초, 최댓값: 60초 (라인 8-9)
- 서버 응답의 `nextPoll` 값으로 동적 조절한다 (라인 44-46).

**동작 흐름**:
1. `GET /queue/status/{eventId}`를 호출한다 (라인 39).
2. 응답의 `entryToken`을 쿠키에 저장한다 (라인 48-51).
3. `setTimeout`으로 다음 폴링을 예약한다 (라인 60).
4. `mountedRef`로 언마운트 후 상태 업데이트를 방지한다 (라인 19-27).

```typescript
// use-queue-polling.ts:15
export function useQueuePolling(eventId: string, enabled = true)
```

**반환값**: `{ status: QueueStatus | null, loading: boolean, error: string | null }`

---

## 7. 컴포넌트

### `SiteHeader`

> 출처: `apps/web/src/components/site-header.tsx`

글로벌 상단 내비게이션 바. `"use client"` 지시어로 클라이언트 컴포넌트이다.

**구성 요소**:
- **로고**: `<Link href="/">` URR 브랜드 (라인 27-30)
- **검색 바**: `<form role="search">` 검색어 입력 후 `/search?q=...`로 이동 (라인 33-48)
- **내비게이션**: 현재 경로(`usePathname`)에 따라 활성 스타일 적용 (라인 51-142)

**조건부 렌더링**:
- 로그인 상태(`getUser()` 존재): 아티스트, 커뮤니티, 내 예매, 내 멤버십, 양도 마켓, 로그아웃 버튼
- `user.role === "admin"`: 관리자 링크 추가 표시 (라인 104-115)
- 비로그인 상태: 로그인, 회원가입 링크만 표시 (라인 129-140)

**로그아웃 처리** (라인 120-124):
```typescript
onClick={async () => {
  try { await authApi.logout(); } catch {}
  clearAuth();
  router.push("/login");
}}
```

### `AuthGuard`

> 출처: `apps/web/src/components/auth-guard.tsx`

인증 필요 페이지를 감싸는 래퍼 컴포넌트.

**Props**: `{ children: ReactNode, adminOnly?: boolean }`

**검증 흐름**:
1. `getUser()`로 localStorage에서 사용자 데이터 확인 (라인 19).
2. 없으면 `/login`으로 리다이렉트 (라인 21-24).
3. `adminOnly`이고 `role !== "admin"`이면 `/`로 리다이렉트 (라인 26-29).
4. sessionStorage의 `"auth-me-ts"` 캐시가 30초 이내면 서버 호출 생략 (라인 32-37).
5. `authApi.me()`로 서버 측 검증 실행 (라인 40-48).
6. 네트워크 오류 시 로컬 데이터를 신뢰하여 폴백 처리 (라인 51-63).

**로딩 UI**: 검증 완료 전까지 스피너와 "인증 확인 중..." 메시지 표시 (라인 67-75).

### `PageCard`

> 출처: `apps/web/src/components/page-card.tsx`

페이지 제목과 설명을 포함하는 카드 레이아웃 컴포넌트.

```typescript
// page-card.tsx:1-5
interface Props {
  title: string;
  description?: string;
  children?: React.ReactNode;
}
```

`rounded-2xl border bg-white p-6 shadow-sm` 스타일의 섹션 래퍼이다.

---

## 8. 인증 흐름

### 전체 흐름

```
사용자 접근
  |
  v
AuthGuard (localStorage 확인)
  |-- 사용자 없음 --> /login 리다이렉트
  |-- adminOnly && role != admin --> / 리다이렉트
  |-- sessionStorage 캐시 유효 (30초 이내) --> 통과
  |-- authApi.me() 서버 검증
       |-- 성공 --> sessionStorage 캐시 갱신, 통과
       |-- 401 --> 응답 인터셉터에서 자동 갱신 시도
       |       |-- refresh 성공 --> me() 재시도
       |       |-- refresh 실패 --> clearAuth(), /login 리다이렉트
       |-- 기타 에러 (네트워크, 429, 500)
            --> 로컬 데이터 폴백, sessionStorage 캐시 갱신
```

### 토큰 갱신 인터셉터

> 출처: `apps/web/src/lib/api-client.ts:51-117`

쿠키 기반 인증을 사용하므로 토큰을 명시적으로 저장하지 않는다. `withCredentials: true` 설정으로 Axios가 자동으로 쿠키를 포함한다.

갱신 중 동시 요청 처리를 위한 대기열 패턴:
```typescript
// api-client.ts:52-57
let isRefreshing = false;
let failedQueue: Array<{
  resolve: (value: unknown) => void;
  reject: (reason: unknown) => void;
  config: AxiosRequestConfig;
}> = [];
```

### 스토리지 헬퍼

> 출처: `apps/web/src/lib/storage.ts`

| 함수 | 동작 |
|---|---|
| `getUser()` | localStorage에서 `"user"` 키의 JSON 파싱. SSR 안전 (`typeof window` 체크) (라인 7-16) |
| `setUser(user)` | `AuthUser` 객체를 JSON 직렬화하여 저장 (라인 18-21) |
| `clearUser()` | `"user"` 키 삭제 (라인 23-26) |
| `clearAuth()` | `clearUser()` 호출. 향후 추가 정리 로직 확장 가능 (라인 28-30) |

---

## 9. 미들웨어 및 보안

> 출처: `apps/web/src/middleware.ts`

Next.js Edge Middleware로 모든 페이지 요청에 보안 헤더를 주입한다.

### CSP (Content-Security-Policy)

```typescript
// middleware.ts:5
const nonce = Buffer.from(crypto.randomUUID()).toString("base64");
```

요청마다 고유 nonce를 생성하고 `x-nonce` 헤더에 주입한다 (라인 19-20).

| 지시어 | 값 | 비고 |
|---|---|---|
| `default-src` | `'self'` | 동일 출처만 허용 |
| `script-src` | `'self' 'unsafe-inline'` | Next.js RSC 인라인 스크립트 호환 |
| `style-src` | `'self' 'unsafe-inline' 'nonce-{nonce}'` | nonce 기반 스타일 허용 |
| `img-src` | `'self' data: https:` | 외부 HTTPS 이미지 허용 |
| `connect-src` | `'self' http://localhost:* https://*.tiketi.com` | API 연결 허용 |
| `frame-ancestors` | `'none'` | iframe 삽입 차단 |

### 추가 보안 헤더

| 헤더 | 값 |
|---|---|
| `X-Frame-Options` | `DENY` |
| `X-Content-Type-Options` | `nosniff` |
| `Referrer-Policy` | `strict-origin-when-cross-origin` |

### Matcher 설정

```typescript
// middleware.ts:31-34
export const config = {
  matcher: [
    { source: "/((?!_next/static|_next/image|favicon.ico).*)",
      missing: [{ type: "header", key: "next-router-prefetch" }] },
  ],
};
```

정적 리소스와 프리페치 요청은 제외한다.

---

## 10. 스타일링

### CSS 변수 및 테마

> 출처: `apps/web/src/app/globals.css`

```css
/* globals.css:3-14 */
:root {
  --bg: #ffffff;
  --panel: #f8fafc;
  --line: #e2e8f0;
  --text: #1e293b;
  --muted: #475569;
  --accent: #0ea5e9;
  --accent-hover: #0284c7;
  --warning: #fbbf24;
  --danger: #ef4444;
  --success: #22c55e;
}
```

| 변수 | 색상 | 용도 |
|---|---|---|
| `--bg` | `#ffffff` | 배경 |
| `--panel` | `#f8fafc` | 패널/카드 배경 |
| `--line` | `#e2e8f0` | 구분선 |
| `--text` | `#1e293b` | 기본 텍스트 |
| `--muted` | `#475569` | 보조 텍스트 |
| `--accent` | `#0ea5e9` | 강조색 (sky-500) |
| `--danger` | `#ef4444` | 에러/위험 |
| `--success` | `#22c55e` | 성공 |

Tailwind v4의 `@theme inline` 블록으로 CSS 변수를 Tailwind 토큰에 매핑한다 (라인 16-21).

### 폰트 설정

> 출처: `apps/web/src/app/layout.tsx:6-15`

| 폰트 | 변수 | 용도 |
|---|---|---|
| Space Grotesk | `--font-heading` | 본문 및 제목 |
| IBM Plex Mono | `--font-mono` | 코드/모노스페이스 (weight: 400, 500) |

### 레이아웃

> 출처: `apps/web/src/app/layout.tsx:17-37`

- `<html lang="ko">`: 한국어 문서 선언
- 메타데이터: `title: "URR - 우르르"`, `description: "URR - 가장 빠른 티켓팅 플랫폼"` (라인 17-20)
- Skip navigation 링크: `<a href="#main-content">본문으로 건너뛰기</a>` (접근성) (라인 26-31)
- `<main>` 태그에 `role="main"` 속성 적용 (라인 33)
- 최대 너비: `max-w-7xl` (1280px), 좌우 패딩 `px-6`

---

## 11. 유틸리티

### 날짜/가격 포맷

> 출처: `apps/web/src/lib/format.ts`

| 함수 | 설명 | 출력 예시 |
|---|---|---|
| `formatEventDate(d)` | 한국어 요일 포함 공연 날짜 (라인 3-18) | `"2026년 3월 15일 (일) 19:00"` |
| `formatPrice(price)` | `Intl.NumberFormat("ko-KR")` (라인 20-22) | `"50,000"` |
| `formatDate(d)` | `toLocaleDateString("ko-KR")` (라인 24-27) | `"2026. 3. 15."` |
| `formatDateTime(d)` | `toLocaleString("ko-KR")` medium/short (라인 29-32) | `"2026. 3. 15. 오후 7:00"` |

요일 배열: `["일", "월", "화", "수", "목", "금", "토"]` (라인 1)

### 스토리지 헬퍼

> 출처: `apps/web/src/lib/storage.ts`

| 함수 | 시그니처 | 설명 |
|---|---|---|
| `getUser` | `() => AuthUser \| null` | localStorage에서 사용자 조회. SSR 안전 (라인 7-16) |
| `setUser` | `(user: AuthUser) => void` | 사용자 데이터 저장 (라인 18-21) |
| `clearUser` | `() => void` | 사용자 데이터 삭제 (라인 23-26) |
| `clearAuth` | `() => void` | `clearUser()` 호출. 확장 가능한 정리 함수 (라인 28-30) |

모든 함수에 `typeof window === "undefined"` 가드가 적용되어 SSR 환경에서 안전하게 동작한다.
