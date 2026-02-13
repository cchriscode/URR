# URR (우르르) 프론트엔드 기술 분석서

> **문서 버전**: 1.0
> **작성일**: 2026-02-13
> **대상 시스템**: URR 티켓팅 플랫폼 프론트엔드
> **소스 경로**: `apps/web/`

---

## 목차

1. [기술 스택](#1-기술-스택)
2. [프로젝트 구조](#2-프로젝트-구조)
3. [라우팅 구조](#3-라우팅-구조)
4. [API 통신 계층](#4-api-통신-계층)
5. [인증 흐름](#5-인증-흐름)
6. [상태 관리](#6-상태-관리)
7. [VWR (가상대기열) 구현](#7-vwr-가상대기열-구현)
8. [결제 흐름](#8-결제-흐름)
9. [UI/UX 패턴](#9-uiux-패턴)
10. [Docker 및 배포](#10-docker-및-배포)

---

## 1. 기술 스택

### 1.1 핵심 의존성

| 구분 | 패키지 | 버전 | 용도 |
|------|--------|------|------|
| 프레임워크 | Next.js | 16.1.6 | App Router 기반 SSR/CSR |
| UI 라이브러리 | React | 19.2.3 | 컴포넌트 렌더링 |
| 언어 | TypeScript | 5.9.3 | 정적 타입 검사 |
| CSS | Tailwind CSS | ^4 | 유틸리티 기반 스타일링 |
| HTTP | axios | ^1.13.5 | API 통신 |
| 결제 | @tosspayments/payment-sdk | ^1.9.2 | 토스페이먼츠 결제 연동 |
| 차트 | recharts | ^3.7.0 | 관리자 통계 시각화 |

**참조 파일**: `apps/web/package.json` (lines 1-29)

### 1.2 TypeScript 컴파일 설정

```
Target: ES2017
Module: ESNext (Bundler resolution)
Strict mode: 활성
Path alias: @/* -> ./src/*
JSX: react-jsx
Incremental: 활성
```

**참조 파일**: `apps/web/tsconfig.json` (lines 1-34)

### 1.3 Next.js 설정

`apps/web/next.config.ts` (lines 1-42)에서 다음 항목을 구성한다.

- **Turbopack**: `turbopack.root`를 현재 디렉토리로 설정 (line 6)
- **보안 헤더** (lines 8-39):

| 헤더 | 값 | 목적 |
|------|----|------|
| Content-Security-Policy | `default-src 'self'; script-src 'self' 'unsafe-inline' 'unsafe-eval'; ...` | XSS 방어 |
| X-Frame-Options | `DENY` | 클릭재킹 방지 |
| X-Content-Type-Options | `nosniff` | MIME 스니핑 방지 |
| Referrer-Policy | `strict-origin-when-cross-origin` | 리퍼러 제한 |

CSP의 `connect-src` 지시어는 `http://localhost:*`와 `https://*.tiketi.com`을 허용하여 로컬 개발 및 프로덕션 API 호출을 모두 지원한다.

---

## 2. 프로젝트 구조

### 2.1 디렉토리 레이아웃

```
apps/web/src/
  app/                          # Next.js App Router 페이지
    layout.tsx                  # 루트 레이아웃 (lines 1-31)
    page.tsx                    # 홈 - 이벤트 목록
    globals.css                 # 전역 CSS
    error.tsx                   # 전역 에러 바운더리
    not-found.tsx               # 404 페이지
    login/page.tsx              # 로그인
    register/page.tsx           # 회원가입
    search/page.tsx             # 검색 결과
    events/[id]/page.tsx        # 이벤트 상세
    events/[id]/seats/page.tsx  # 좌석 선택
    events/[id]/book/page.tsx   # 스탠딩 예매
    queue/[eventId]/page.tsx    # 가상대기열
    payment/[reservationId]/page.tsx  # 결제
    payment/success/page.tsx    # 결제 성공
    payment/fail/page.tsx       # 결제 실패
    my-reservations/page.tsx    # 내 예매
    my-memberships/page.tsx     # 내 멤버십
    artists/page.tsx            # 아티스트 목록
    artists/[id]/page.tsx       # 아티스트 상세
    transfers/page.tsx          # 양도 마켓
    news/page.tsx               # 뉴스
    admin/page.tsx              # 관리자 대시보드
    admin/events/page.tsx       # 이벤트 관리
    admin/reservations/page.tsx # 예매 관리
    admin/statistics/page.tsx   # 통계
    membership-payment/[membershipId]/page.tsx  # 멤버십 결제
  components/                   # 공유 컴포넌트
    auth-guard.tsx              # 인증 가드 (63 lines)
    site-header.tsx             # 글로벌 헤더 (144 lines)
    page-card.tsx               # 페이지 카드 래퍼 (15 lines)
  hooks/                        # 커스텀 훅
    use-countdown.ts            # 카운트다운 타이머 (99 lines)
    use-queue-polling.ts        # 대기열 폴링 (73 lines)
    use-server-time.ts          # 서버 시간 동기화 (57 lines)
  lib/                          # 유틸리티/설정
    api-client.ts               # API 클라이언트 (185 lines)
    storage.ts                  # localStorage 래퍼 (47 lines)
    types.ts                    # TypeScript 타입 정의 (229 lines)
    format.ts                   # 날짜/가격 포맷터 (32 lines)
  middleware.ts                 # Next.js 미들웨어 (13 lines)
```

### 2.2 루트 레이아웃

**파일**: `apps/web/src/app/layout.tsx` (lines 1-31)

```typescript
// line 24: <html lang="ko">
// line 25: <body className={`${heading.variable} ${mono.variable} bg-slate-50`}>
```

- **HTML 언어**: `ko` (한국어)
- **폰트**: Space Grotesk (제목, `--font-heading`) + IBM Plex Mono (코드/숫자, `--font-mono`, weight 400/500)
- **메타데이터**: title "URR - 우르르", description "URR - 가장 빠른 티켓팅 플랫폼" (lines 17-20)
- **레이아웃 구성**: `<SiteHeader />` + `<main>` (max-w-7xl, px-6, py-8) (lines 26-27)

### 2.3 미들웨어

**파일**: `apps/web/src/middleware.ts` (lines 1-13)

미들웨어는 현재 패스스루(pass-through) 상태이다. 인증 토큰이 `localStorage`에 저장되므로 서버 사이드 미들웨어에서 접근할 수 없으며, 인증 검사는 클라이언트 사이드 `AuthGuard` 컴포넌트에 위임된다 (lines 5-7 주석 참조).

```typescript
export const config = {
  matcher: [],  // 빈 매처 - 어떤 라우트에도 적용되지 않음
};
```

---

## 3. 라우팅 구조

### 3.1 전체 페이지 맵

| 경로 | 파일 | 라인 수 | 설명 | 인증 |
|------|------|---------|------|------|
| `/` | `app/page.tsx` | 195 | 이벤트 목록 - 상태 필터, 카운트다운, 그리드 레이아웃 | 불필요 |
| `/login` | `app/login/page.tsx` | 89 | 로그인 - email/password 입력, setToken/setUser 저장 | 불필요 |
| `/register` | `app/register/page.tsx` | 110 | 회원가입 - name, email, password, phone 입력 | 불필요 |
| `/search?q=` | `app/search/page.tsx` | - | 이벤트 검색 결과 | 불필요 |
| `/events/:id` | `app/events/[id]/page.tsx` | 242 | 이벤트 상세 - 포스터, 공연 정보, 티켓 타입 목록 | 불필요 |
| `/events/:id/seats` | `app/events/[id]/seats/page.tsx` | 307 | 좌석 선택 - 좌석맵, 섹션/열 기반 UI | AuthGuard |
| `/events/:id/book` | `app/events/[id]/book/page.tsx` | 245 | 스탠딩 예매 - 티켓 타입별 수량 선택 | AuthGuard |
| `/queue/:eventId` | `app/queue/[eventId]/page.tsx` | 175 | 가상대기열(VWR) - 대기 순번, 자동 리다이렉트 | AuthGuard |
| `/payment/:reservationId` | `app/payment/[reservationId]/page.tsx` | 216 | 결제 - 4가지 결제수단, 만료 타이머 | AuthGuard |
| `/payment/success` | `app/payment/success/page.tsx` | 109 | 결제 성공 - Toss 콜백 처리 | 불필요 |
| `/payment/fail` | `app/payment/fail/page.tsx` | - | 결제 실패 안내 | 불필요 |
| `/my-reservations` | `app/my-reservations/page.tsx` | 190 | 내 예매 목록 - 상태별 필터링 | AuthGuard |
| `/my-memberships` | `app/my-memberships/page.tsx` | 95 | 내 멤버십 목록 - 아티스트별 티어/포인트 | AuthGuard |
| `/artists` | `app/artists/page.tsx` | 81 | 아티스트 목록 | 불필요 |
| `/artists/:id` | `app/artists/[id]/page.tsx` | 387 | 아티스트 상세 - 티어, 포인트, 혜택 정보 | 불필요 |
| `/transfers` | `app/transfers/page.tsx` | 227 | 양도 마켓 - 티켓 양도 목록 및 거래 | AuthGuard |
| `/news` | `app/news/page.tsx` | 85 | 뉴스 목록 | 불필요 |
| `/admin` | `app/admin/page.tsx` | 75 | 관리자 대시보드 | AuthGuard (adminOnly) |
| `/admin/events` | `app/admin/events/page.tsx` | 142 | 이벤트 관리 - CRUD | AuthGuard (adminOnly) |
| `/admin/reservations` | `app/admin/reservations/page.tsx` | 138 | 예매 관리 - 상태 변경 | AuthGuard (adminOnly) |
| `/admin/statistics` | `app/admin/statistics/page.tsx` | - | 통계 대시보드 - recharts 차트 | AuthGuard (adminOnly) |
| `/membership-payment/:membershipId` | `app/membership-payment/[membershipId]/page.tsx` | - | 멤버십 결제 | AuthGuard |

### 3.2 네비게이션 구조

**파일**: `apps/web/src/components/site-header.tsx` (lines 1-144)

글로벌 헤더는 sticky 포지셔닝으로 상단에 고정되며 다음 요소를 포함한다.

- **로고**: "URR" 텍스트 + 번개 아이콘 (line 27-29)
- **검색바**: 폼 submit 시 `/search?q=` 경로로 이동 (lines 15-21, 33-47)
- **네비게이션 링크**: 현재 경로(pathname)에 따라 활성 스타일 적용 (lines 50-140)

비로그인 상태와 로그인 상태에서 표시되는 메뉴가 분기된다.

| 상태 | 표시 항목 |
|------|-----------|
| 비로그인 | 아티스트, News, 로그인, 회원가입 |
| 로그인 (일반) | 아티스트, News, 내 예매, 내 멤버십, 양도 마켓, 사용자명, 로그아웃 |
| 로그인 (관리자) | 위 항목 + 관리자 |

---

## 4. API 통신 계층

### 4.1 클라이언트 구성

**파일**: `apps/web/src/lib/api-client.ts` (lines 1-185)

#### Base URL 결정 로직 (lines 7-35)

`resolveBaseUrl()` 함수는 다음 우선순위로 API 서버 주소를 결정한다.

```
1. process.env.NEXT_PUBLIC_API_URL      (빌드 타임 환경변수)
2. typeof window === "undefined"         -> http://localhost:3001 (SSR 폴백)
3. window.__API_URL                      (K8s 런타임 주입)
4. localhost / 127.0.0.1 감지            -> http://localhost:3001
5. 사설 IP 대역 (172.x, 192.168.x, 10.x) -> http://{hostname}:3001
6. 그 외 (프로덕션)                       -> "" (same-origin, CloudFront/ALB 리버스 프록시)
```

#### Axios 인스턴스 (lines 37-40)

```typescript
const http = axios.create({
  baseURL: `${resolveBaseUrl()}/api`,
  headers: { "Content-Type": "application/json" },
});
```

#### 요청 인터셉터 (lines 48-59)

모든 요청에 두 가지 헤더를 자동 부착한다.

1. **Authorization**: `Bearer {token}` - localStorage에서 JWT 토큰 조회 (line 49-51)
2. **x-queue-entry-token**: 쿠키 `tiketi-entry-token` 값 - Lambda@Edge 및 Gateway VWR 검증용 (lines 54-56)

#### 응답 인터셉터 (lines 61-72)

HTTP 401 응답 시 `clearAuth()` 호출 후 `/login`으로 리다이렉트한다 (lines 64-68).

### 4.2 API 모듈 목록

| 모듈 | 파일 위치 (lines) | 엔드포인트 | 주요 메서드 |
|------|-------------------|------------|-------------|
| `authApi` | lines 74-79 | `/api/auth/*` | register, login, me |
| `eventsApi` | lines 81-84 | `/api/events/*` | list, detail |
| `queueApi` | lines 86-91 | `/api/queue/*` | check, status, heartbeat, leave |
| `reservationsApi` | lines 93-102 | `/api/reservations/*` | createTicketOnly, mine, byId, cancel |
| `seatsApi` | lines 104-108 | `/api/seats/*` | byEvent, reserve |
| `paymentsApi` | lines 110-114 | `/api/payments/*` | prepare, confirm, process |
| `statsApi` | lines 116-131 | `/api/stats/*` | overview, daily, events, eventDetail, payments, revenue, users, hourlyTraffic, conversion, cancellations, realtime, performance, seatPreferences, userBehavior |
| `newsApi` | lines 133-141 | `/api/news/*` | list, byId, create, update, delete |
| `artistsApi` | lines 143-146 | `/api/artists/*` | list, detail |
| `membershipsApi` | lines 148-153 | `/api/memberships/*` | subscribe, my, myForArtist, benefits |
| `transfersApi` | lines 155-161 | `/api/transfers/*` | list, my, detail, create, cancel |
| `adminApi` | lines 163-185 | `/api/admin/*` | dashboard, seatLayouts, events.*, tickets.*, reservations.* |

---

## 5. 인증 흐름

### 5.1 토큰 저장 방식

**파일**: `apps/web/src/lib/storage.ts` (lines 1-47)

| 키 | 저장소 | 타입 | 용도 |
|----|--------|------|------|
| `token` | localStorage | string | JWT 액세스 토큰 |
| `user` | localStorage | JSON (AuthUser) | 사용자 정보 (id, email, name, role) |

SSR 환경(`typeof window === "undefined"`)에서 안전하게 `null`을 반환하도록 모든 함수에 가드가 포함되어 있다 (lines 9, 13, 18, 23, 34, 39, 44).

`clearAuth()` 함수(line 44-47)는 `clearToken()`과 `clearUser()`를 순차 호출하여 모든 인증 정보를 제거한다.

### 5.2 AuthGuard 컴포넌트

**파일**: `apps/web/src/components/auth-guard.tsx` (lines 1-63)

```
Props: { children: ReactNode, adminOnly?: boolean }
```

인증 검증은 3단계로 수행된다.

```
1단계: 토큰 존재 확인 (line 20-23)
  - 토큰 없음 -> /login 리다이렉트

2단계: 로컬 역할 검증 (line 25-28, adminOnly 전용)
  - user.role !== "admin" -> / 리다이렉트

3단계: 서버 역할 검증 (lines 32-46, adminOnly 전용)
  - authApi.me() 호출 -> res.data.user ?? res.data에서 role 확인
  - 서버 role !== "admin" -> / 리다이렉트
  - 성공 시 setServerVerified(true)
```

3단계 서버 검증은 `localStorage` 조작을 통한 무단 관리자 접근을 방지한다 (line 30-31 주석 참조).

검증 진행 중에는 스피너 + "Authorizing..." 텍스트를 표시한다 (lines 52-59).

### 5.3 로그아웃

**파일**: `apps/web/src/components/site-header.tsx` (lines 116-125)

로그아웃 버튼 클릭 시 `clearAuth()` 호출 후 `router.push("/login")`으로 이동한다.

---

## 6. 상태 관리

### 6.1 설계 방식

이 프로젝트는 Redux, Zustand 등 외부 상태 관리 라이브러리를 사용하지 않는다. 모든 상태는 React의 `useState` 훅과 `localStorage`로 관리된다.

| 상태 유형 | 관리 방식 | 범위 |
|-----------|-----------|------|
| 컴포넌트 로컬 상태 | `useState` | 각 페이지 컴포넌트 |
| 인증 정보 | `localStorage` (storage.ts) | 전역 (브라우저 탭 간 공유) |
| 서버 시간 오프셋 | 모듈 레벨 캐시 (use-server-time.ts) | 전역 (싱글턴) |
| 대기열 토큰 | 쿠키 `tiketi-entry-token` | 전역 (max-age 600초) |

### 6.2 서버 시간 동기화

**파일**: `apps/web/src/hooks/use-server-time.ts` (lines 1-57)

클라이언트와 서버 간 시간 차이를 보정하기 위한 커스텀 훅이다.

**동작 원리** (lines 17-38):

```
1. 요청 전 시각 기록: before = Date.now()
2. GET /api/time 호출
3. 응답 후 시각 기록: after = Date.now()
4. RTT 계산: rtt = after - before
5. 서버 시각 파싱: serverTime = new Date(data.time).getTime()
6. 클라이언트 중간점: clientMidpoint = before + rtt / 2
7. 오프셋 계산: offset = serverTime - clientMidpoint
```

**최적화**:
- 모듈 레벨 `cachedOffset` 변수로 싱글턴 캐싱 (line 14)
- `fetchPromise`로 중복 요청 방지 (line 15)
- 오류 발생 시 offset을 0으로 폴백 (lines 32-34)

### 6.3 카운트다운 타이머

**파일**: `apps/web/src/hooks/use-countdown.ts` (lines 1-99)

`useCountdown(targetDate, onExpire?)` 훅은 서버 시간 동기화된 카운트다운을 제공한다.

- `useServerTime()` 훅과 연동하여 서버 보정 시간 기준 계산 (line 44)
- 1초 간격 `setInterval`로 갱신 (line 75)
- 만료 시 `onExpire` 콜백 실행 (lines 68-71)
- 포맷 함수 `formatCountdown()`: `"N개월 N일 N시간 N분 N초"` (lines 82-93)
- 포맷 함수 `formatCountdownShort()`: `"MM:SS"` (lines 95-99)
- 만료 텍스트: `"만료됨"` (line 83)

---

## 7. VWR (가상대기열) 구현

### 7.1 전체 흐름

```
사용자 -> /queue/:eventId 진입
    |
    +-> queueApi.check() 호출 (POST /api/queue/check/:eventId)
    |     |
    |     +-> 즉시 입장 가능 (queued=false, status="active")
    |     |     -> /events/:eventId/seats 또는 /book 리다이렉트
    |     |
    |     +-> 대기열 진입 (queued=true, status="queued")
    |           -> 엔트리 토큰 쿠키 저장
    |           -> useQueuePolling 시작
    |
    +-> useQueuePolling (GET /api/queue/status/:eventId, 3초 기본 간격)
    |     |
    |     +-> 서버 응답의 nextPoll로 동적 간격 조정 (1~60초)
    |     +-> 엔트리 토큰 갱신 (응답에 포함 시)
    |     +-> status="active" 감지 시 자동 리다이렉트
    |
    +-> 좌석 유무 판단: eventsApi.detail() -> seat_layout_id 확인
          |
          +-> seat_layout_id 존재 -> /events/:eventId/seats
          +-> seat_layout_id 없음 -> /events/:eventId/book
```

### 7.2 대기열 페이지

**파일**: `apps/web/src/app/queue/[eventId]/page.tsx` (lines 1-175)

#### 초기 진입 (lines 50-64)

```typescript
// line 52: queueApi.check(eventId)로 대기열 진입/즉시 입장 판단
// lines 54-56: 엔트리 토큰 쿠키 설정
//   tiketi-entry-token, max-age=600(10분), SameSite=Strict, HTTPS시 Secure
// line 60-61: queued=true일 때만 폴링 시작 (setJoined(true))
```

#### 좌석 유무 판단 (lines 30-44)

이벤트 상세 정보를 조회하여 `seat_layout_id` 또는 `seatLayoutId` 필드로 좌석제(seated) vs 스탠딩(standing) 공연을 구분한다. 이 값은 URL 파라미터가 아닌 서버 데이터에서 파생되므로 클라이언트 측 우회가 불가능하다 (line 26 주석 참조).

#### 자동 리다이렉트 (lines 73-83)

```typescript
// line 76: status === "active" 또는 (queued=false && status !== "queued")
// line 77-78: seatLayoutId 존재 -> /events/${eventId}/seats
// line 79-80: seatLayoutId 없음 -> /events/${eventId}/book
```

#### UI 표시 요소

| 요소 | 위치 | 설명 |
|------|------|------|
| 대기 순번 | line 122 | `position` 값, sky-50 배경 강조 |
| 내 앞/뒤 인원 | lines 127-134 | `peopleAhead`, `peopleBehind` 3열 그리드 |
| 예상 대기시간 | lines 135-138 | `estimatedWait`를 분/시간 단위로 포맷 |
| 접속자 현황 | lines 142-146 | `currentUsers` / `threshold` 표시 |
| 안내 문구 | lines 154-159 | amber 배경, "이 페이지를 닫지 마세요" |
| 대기열 나가기 | lines 162-170 | `queueApi.leave()` 호출 후 이벤트 상세로 이동 |

### 7.3 폴링 훅

**파일**: `apps/web/src/hooks/use-queue-polling.ts` (lines 1-73)

| 설정값 | 상수 | 값 |
|--------|------|----|
| 기본 폴링 간격 | `DEFAULT_POLL_SECONDS` | 3초 (line 7) |
| 최소 폴링 간격 | `MIN_POLL_SECONDS` | 1초 (line 8) |
| 최대 폴링 간격 | `MAX_POLL_SECONDS` | 60초 (line 9) |

- 서버 응답의 `nextPoll` 필드로 폴링 간격을 동적 조정한다 (lines 44-46)
- `clampPoll()` 함수로 1~60초 범위를 벗어나지 않도록 제한한다 (lines 11-13)
- 엔트리 토큰이 응답에 포함되면 쿠키를 갱신한다 (lines 48-51)
- `mountedRef`로 언마운트 후 상태 업데이트를 방지한다 (lines 19-27)
- `setTimeout` 기반 재귀 폴링으로, 요청 완료 후 다음 폴링을 예약한다 (line 60)

### 7.4 QueueStatus 타입

**파일**: `apps/web/src/lib/types.ts` (lines 120-136)

```typescript
interface QueueStatus {
  status?: "queued" | "active" | "not_in_queue";
  queued: boolean;
  position?: number;
  peopleAhead?: number;
  peopleBehind?: number;
  estimatedWait?: number;       // 예상 대기시간 (초)
  nextPoll?: number;            // 서버 권장 폴링 간격 (초)
  currentUsers?: number;        // 현재 접속자 수
  threshold?: number;           // 최대 동시 접속자 수
  queueSize?: number;           // 전체 대기열 크기
  entryToken?: string;          // Lambda@Edge 검증용 토큰
  eventInfo?: {
    title?: string;
    artist?: string;
  };
}
```

---

## 8. 결제 흐름

### 8.1 전체 시퀀스

```
예매 완료 -> /payment/:reservationId
    |
    +-> reservationsApi.byId()로 예매 정보 조회
    +-> useCountdown(expires_at)로 만료 타이머 시작
    +-> 결제수단 선택 (4가지)
    |
    +-> [토스페이먼츠]
    |     1. paymentsApi.prepare() -> orderId, clientKey 수신
    |     2. loadTossPayments(clientKey) -> SDK 로드
    |     3. tossPayments.requestPayment("카드", {...})
    |        successUrl: /payment/success
    |        failUrl: /payment/fail
    |     4. Toss 리다이렉트 -> /payment/success?paymentKey=...&orderId=...&amount=...
    |     5. paymentsApi.confirm({ paymentKey, orderId, amount })
    |
    +-> [네이버페이 / 카카오페이 / 계좌이체]
          1. paymentsApi.process({ reservationId, paymentMethod })
          2. 즉시 /payment/success로 이동
```

### 8.2 결제 페이지

**파일**: `apps/web/src/app/payment/[reservationId]/page.tsx` (lines 1-216)

#### 결제수단 (lines 21-26)

| ID | 라벨 | 아이콘 | 구현 방식 |
|----|------|--------|-----------|
| `naver_pay` | 네이버페이 | N (green-500) | mock 즉시 성공 |
| `kakao_pay` | 카카오페이 | K (yellow-400) | mock 즉시 성공 |
| `bank_transfer` | 계좌이체 | 은행 (slate-600) | mock 즉시 성공 |
| `toss` | 토스페이먼츠 | T (blue-500) | Toss SDK 연동 |

#### Toss SDK 연동 (lines 61-78)

```typescript
// line 63-66: paymentsApi.prepare()로 orderId, clientKey 수신
// line 70: dynamic import로 @tosspayments/payment-sdk 로드
// line 71: loadTossPayments(clientKey)
// line 72-78: requestPayment("카드", {amount, orderId, orderName, successUrl, failUrl})
```

#### Mock 결제 (lines 80-85)

토스 외 결제수단은 `paymentsApi.process()`를 호출한 뒤 즉시 성공 페이지로 이동한다.

#### 만료 타이머 (lines 51-53, 93-97, 146-162)

- `useCountdown(info?.expires_at, () => router.push("/my-reservations"))` (line 51-53)
- 만료 1분 미만(`isUrgent`): 빨간색 + `animate-pulse` 효과 (lines 93-97, 149-151)
- 1분 이상 남음: 황색(amber) 배경 (line 152)
- 완전 만료: "예매 시간이 만료되었습니다" 메시지 + 결제 버튼 비활성화 (lines 158-162, 191)

### 8.3 결제 성공 페이지

**파일**: `apps/web/src/app/payment/success/page.tsx` (lines 1-109)

- `useSearchParams()`로 Toss 콜백 파라미터 확인 (lines 9-13)
- Toss 콜백인 경우: `paymentsApi.confirm({ paymentKey, orderId, amount })` 호출 (lines 18-27)
- 비-Toss(mock) 결제: 확인 과정 없이 바로 성공 화면 표시
- `Suspense` 래퍼로 `useSearchParams()` SSR 호환성 확보 (lines 97-108)

---

## 9. UI/UX 패턴

### 9.1 디자인 시스템

#### 색상 체계 (Tailwind CSS 4)

| 용도 | 색상 클래스 | 사용 예시 |
|------|-------------|-----------|
| Primary | `sky-500`, `sky-600` | 버튼, 링크, 활성 상태 |
| Primary Light | `sky-50`, `sky-100` | 배경 강조, 선택 상태 |
| Neutral | `slate-50` ~ `slate-900` | 텍스트, 배경, 테두리 |
| Warning | `amber-50`, `amber-200`, `amber-700` | 타이머 경고, 안내 문구 |
| Error | `red-50`, `red-200`, `red-600` | 에러 메시지, 긴급 타이머 |
| Success (Nav) | `green-500` | 네이버페이 아이콘 |

#### 타이포그래피

| 요소 | 폰트 | 클래스 |
|------|-------|--------|
| 제목/로고 | Space Grotesk | `var(--font-heading)` |
| 코드/숫자 | IBM Plex Mono | `var(--font-mono)`, `font-mono` |
| 본문 | 시스템 기본 | Tailwind 기본값 |

### 9.2 반응형 레이아웃

이벤트 목록 등 그리드 레이아웃에 반응형 열 구성을 적용한다.

```
모바일:  grid-cols-1
태블릿:  sm:grid-cols-2
데스크톱: lg:grid-cols-3
```

최대 너비는 루트 레이아웃에서 `max-w-7xl` (1280px)로 제한한다.

### 9.3 공통 UI 패턴

| 패턴 | 구현 | 사용 위치 |
|------|------|-----------|
| 로딩 스피너 | `animate-spin rounded-full border-2 border-sky-500 border-t-transparent` | AuthGuard, 결제, 대기열 |
| 에러 카드 | `bg-red-50 border border-red-200 text-red-600` | 결제 실패, API 에러 |
| 상태 뱃지 | `rounded-full` pill 형태 | 예매 상태, 멤버십 티어 |
| 카드 컨테이너 | `rounded-2xl border border-slate-200 bg-white p-6 shadow-sm` | PageCard 컴포넌트 |
| 안내 배너 | `rounded-xl bg-amber-50 border border-amber-200` | 대기열, 결제 타이머 |
| 긴급 펄스 | `animate-pulse bg-red-50 border-red-200` | 결제 만료 임박 |

### 9.4 한국어 로컬라이제이션

**파일**: `apps/web/src/lib/format.ts` (lines 1-32)

| 함수 | 위치 | 출력 형식 |
|------|------|-----------|
| `formatEventDate()` | lines 3-18 | `"2026년 2월 13일 (금) 19:00"` |
| `formatPrice()` | lines 20-22 | `Intl.NumberFormat("ko-KR")` -> `"150,000"` |
| `formatDate()` | lines 24-27 | `toLocaleDateString("ko-KR")` |
| `formatDateTime()` | lines 29-32 | `toLocaleString("ko-KR", {dateStyle:"medium", timeStyle:"short"})` |

요일 이름은 한국어 배열로 직접 정의한다: `["일", "월", "화", "수", "목", "금", "토"]` (line 1).

---

## 10. Docker 및 배포

### 10.1 Dockerfile

**파일**: `apps/web/Dockerfile` (lines 1-34)

#### 멀티스테이지 빌드

| 스테이지 | 베이스 이미지 | 역할 |
|----------|--------------|------|
| `builder` | `node:20-alpine` | 의존성 설치 + Next.js 빌드 |
| `runner` | `node:20-alpine` | 프로덕션 서버 실행 |

#### 빌드 설정

```dockerfile
ARG NEXT_PUBLIC_API_URL=http://localhost:3001    # line 6
ENV NEXT_PUBLIC_API_URL=$NEXT_PUBLIC_API_URL     # line 8
```

`NEXT_PUBLIC_API_URL`은 빌드 인수(ARG)로 전달되며, 빌드 시점에 Next.js 번들에 임베드된다. 기본값은 `http://localhost:3001`이다.

#### 보안 설정 (lines 28-30)

```dockerfile
RUN addgroup --system --gid 1001 app && adduser --system --uid 1001 --ingroup app app
RUN chown -R app:app /app
USER app
```

- 시스템 그룹 `app` (GID 1001) 및 사용자 `app` (UID 1001) 생성
- 비-root 사용자로 컨테이너 실행
- 포트 3000 노출 (line 32)

#### 복사 대상 (lines 23-26)

```
package*.json   -> 의존성 메타데이터
.next/          -> 빌드 산출물
public/         -> 정적 리소스
node_modules/   -> 런타임 의존성
```

---

## 부록: 타입 정의 요약

**파일**: `apps/web/src/lib/types.ts` (lines 1-229)

### 핵심 도메인 타입

| 타입 | 위치 (lines) | 설명 |
|------|-------------|------|
| `AuthUser` | 3-9 | 사용자 정보 (id, email, name, role) |
| `UserRole` | 1 | `"user" \| "admin"` |
| `EventSummary` | 11-23 | 이벤트 요약 (목록용) |
| `EventDetail` | 26-40 | 이벤트 상세 (camelCase 별칭 포함) |
| `TicketType` | 42-50 | 티켓 유형 (가격, 수량) |
| `Seat` | 52-61 | 좌석 정보 (섹션, 열, 번호, 상태) |
| `SeatLayout` | 71-77 | 좌석 배치 레이아웃 |
| `Reservation` | 94-108 | 예매 정보 (상태, 금액, 만료시각) |
| `QueueStatus` | 120-136 | 대기열 상태 |
| `MembershipTier` | 138 | `"BRONZE" \| "SILVER" \| "GOLD" \| "DIAMOND"` |
| `Artist` | 140-149 | 아티스트 정보 |
| `ArtistMembership` | 151-164 | 아티스트 멤버십 (티어, 포인트) |
| `MembershipBenefits` | 174-182 | 멤버십 혜택 (선예매, 수수료, 양도 접근) |
| `TicketTransfer` | 184-203 | 티켓 양도 거래 |
| `StatsOverview` | 205-213 | 통계 개요 |
| `DailyStats` | 215-220 | 일별 통계 |
| `EventStats` | 222-228 | 이벤트별 통계 |

### 좌석 상태 값

`Seat.status` 필드는 3가지 상태를 가진다 (line 60):

| 값 | 의미 |
|----|------|
| `available` | 선택 가능 |
| `reserved` | 예매 완료 |
| `locked` | 임시 잠금 (결제 진행 중) |
