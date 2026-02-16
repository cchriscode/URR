# URR 프론트엔드 아키텍처 분석

> 분석 일자: 2026-02-16
> 대상: `apps/web/` (Next.js), `apps/vwr/` (정적 VWR 페이지)

---

## 목차

1. [기술 스택](#1-기술-스택)
2. [프로젝트 구조](#2-프로젝트-구조)
3. [주요 페이지](#3-주요-페이지)
4. [API 통신](#4-api-통신)
5. [인증 흐름](#5-인증-흐름)
6. [대기열 (Queue) UI](#6-대기열-queue-ui)
7. [VWR 정적 페이지](#7-vwr-정적-페이지)
8. [상태 관리](#8-상태-관리)
9. [빌드 및 배포](#9-빌드-및-배포)
10. [좋은 점과 미흡한 점](#10-좋은-점과-미흡한-점)

---

## 1. 기술 스택

### 핵심 의존성 요약

| 카테고리 | 기술 | 버전 | 비고 |
|----------|------|------|------|
| 프레임워크 | Next.js | 16.1.6 | App Router, Turbopack 사용 |
| UI 라이브러리 | React | 19.2.3 | React 19 (최신) |
| 언어 | TypeScript | 5.9.3 | strict 모드 활성화 |
| 스타일링 | Tailwind CSS | v4 | `@tailwindcss/postcss` 플러그인 방식 |
| HTTP 클라이언트 | Axios | 1.13.5 | interceptors 기반 인증/재시도 |
| 서버 상태 관리 | TanStack React Query | 5.90.21 | staleTime 60초, gcTime 5분 |
| 결제 | TossPayments SDK | 1.9.2 | 동적 import로 lazy-load |
| 차트 | Recharts | 3.7.0 | 관리자 통계 페이지 전용 |
| 단위 테스트 | Vitest | 4.0.18 | happy-dom 환경 |
| E2E 테스트 | Playwright | 1.58.2 | Chromium 프로젝트 단일 구성 |
| 린트 | ESLint | 9.x | next/core-web-vitals + next/typescript |
| 폰트 | Space Grotesk, IBM Plex Mono | Google Fonts | CSS 변수로 주입 |

**참고 파일:** `apps/web/package.json:1-39`

### Tailwind CSS v4 구성

Tailwind CSS v4는 PostCSS 플러그인으로 설정되어 있다. `globals.css`에서 `@import "tailwindcss"` 한 줄로 통합하고, `@theme inline` 블록에서 디자인 토큰을 CSS 변수로 재정의한다.

```css
/* apps/web/src/app/globals.css:1-21 */
@import "tailwindcss";

:root {
  --bg: #ffffff;
  --panel: #f8fafc;
  --line: #e2e8f0;
  --text: #1e293b;
  --muted: #475569;
  --accent: #0ea5e9;      /* sky-500 계열 */
  --accent-hover: #0284c7;
  --warning: #fbbf24;
  --danger: #ef4444;
  --success: #22c55e;
}

@theme inline {
  --color-background: var(--bg);
  --color-foreground: var(--text);
  --font-sans: var(--font-heading);
  --font-mono: var(--font-mono);
}
```

다크 모드는 구현되어 있지 않다. 전체 색상 팔레트는 `slate` 기반의 밝은 테마 단일 구성이다.

---

## 2. 프로젝트 구조

### 디렉토리 레이아웃

```
apps/web/
+-- src/
|   +-- app/                      # Next.js App Router (페이지)
|   |   +-- layout.tsx            # 루트 레이아웃 (SiteHeader, Providers)
|   |   +-- page.tsx              # 홈페이지 (이벤트 목록)
|   |   +-- providers.tsx         # QueryClient + AuthProvider
|   |   +-- globals.css           # Tailwind + 디자인 토큰
|   |   +-- loading.tsx           # 루트 스켈레톤 UI
|   |   +-- error.tsx             # 루트 에러 바운더리
|   |   +-- not-found.tsx         # 404 페이지
|   |   +-- login/                # 로그인
|   |   +-- register/             # 회원가입
|   |   +-- events/[id]/          # 이벤트 상세/좌석선택/예매
|   |   +-- queue/[eventId]/      # 대기열
|   |   +-- payment/              # 결제 (결제, 성공, 실패)
|   |   +-- my-reservations/      # 내 예매 목록
|   |   +-- reservations/[id]/    # 예매 상세
|   |   +-- artists/              # 아티스트 목록/상세
|   |   +-- community/            # 커뮤니티 (글목록/상세/작성/수정)
|   |   +-- news/                 # 뉴스 (목록/상세/작성/수정)
|   |   +-- transfers/            # 양도 마켓
|   |   +-- my-memberships/       # 내 멤버십
|   |   +-- membership-payment/   # 멤버십 결제
|   |   +-- transfer-payment/     # 양도 결제
|   |   +-- search/               # 검색 결과
|   |   +-- admin/                # 관리자 (대시보드/이벤트/예매/통계)
|   +-- components/               # 공유 컴포넌트 (3개)
|   |   +-- auth-guard.tsx
|   |   +-- site-header.tsx
|   |   +-- page-card.tsx
|   +-- hooks/                    # 커스텀 훅 (5개)
|   |   +-- use-countdown.ts
|   |   +-- use-events.ts
|   |   +-- use-queue-polling.ts
|   |   +-- use-reservations.ts
|   |   +-- use-server-time.ts
|   +-- lib/                      # 유틸리티 및 API 클라이언트
|   |   +-- api-client.ts         # Axios 인스턴스 + 모든 API 함수
|   |   +-- auth-context.tsx      # React Context 기반 인증
|   |   +-- types.ts              # 공유 타입 정의
|   |   +-- format.ts             # 날짜/가격 포매터
|   |   +-- server-api.ts         # SSR용 fetch 래퍼
|   |   +-- storage.ts            # localStorage 유틸리티
|   +-- middleware.ts             # CSP, 보안 헤더
|   +-- test/                     # 단위 테스트
+-- e2e/                          # E2E 테스트 (Playwright)
+-- public/                       # 정적 파일
+-- Dockerfile                    # 멀티스테이지 빌드
+-- next.config.ts
+-- vitest.config.ts
+-- playwright.config.ts
```

### 라우팅 방식: App Router

Next.js 16의 **App Router**를 사용한다. 모든 페이지가 `src/app/` 하위에 파일시스템 기반으로 구성되어 있으며, 동적 라우트는 `[id]`, `[eventId]`, `[reservationId]` 등의 대괄호 디렉토리로 정의한다.

Pages Router는 사용하지 않는다.

### 컴포넌트 조직

공유 컴포넌트는 `src/components/`에 3개만 존재한다:

| 컴포넌트 | 파일 | 역할 |
|----------|------|------|
| `AuthGuard` | `components/auth-guard.tsx` | 인증 필수 페이지 래퍼, `adminOnly` 프롭 지원 |
| `SiteHeader` | `components/site-header.tsx` | 상단 GNB (로고, 검색, 네비게이션, 인증 상태) |
| `PageCard` | `components/page-card.tsx` | 카드 형태 섹션 래퍼 (범용) |

대부분의 UI는 각 페이지 컴포넌트 내부에 인라인으로 작성되어 있다. 별도의 `Button`, `Input`, `Card` 등의 디자인 시스템 컴포넌트는 없다.

---

## 3. 주요 페이지

### 사용자 흐름 다이어그램

```
 [홈페이지]          [로그인/회원가입]        [관리자]
     |                      |                    |
     v                      v                    v
 이벤트 목록 -----> 로그인 필수 ---------> 대시보드
     |               (AuthGuard)           |   |   |
     v                                   이벤트 예매 통계
 이벤트 상세                              관리  관리  대시보드
     |
     v
 대기열 (Queue)
     |
     +--------+--------+
     |                  |
     v                  v
 좌석 선택           티켓 선택
 (seated)            (standing)
     |                  |
     +--------+---------+
              |
              v
          결제 페이지
              |
     +--------+---------+
     |                   |
     v                   v
 결제 성공           결제 실패
     |
     v
 내 예매 목록
     |
     v
 양도 등록 --> 양도 마켓
```

### 페이지별 상세

| 경로 | 파일 | 인증 | 기능 |
|------|------|------|------|
| `/` | `app/page.tsx` | 불필요 | 이벤트 목록 (필터: 예매중/오픈예정/종료/취소/전체), 카운트다운 표시 |
| `/login` | `app/login/page.tsx` | 불필요 | 이메일/비밀번호 로그인, Google OAuth 지원 |
| `/register` | `app/register/page.tsx` | 불필요 | 이메일/비밀번호/이름/전화번호 회원가입 |
| `/events/[id]` | `app/events/[id]/page.tsx` | 불필요 | 이벤트 상세 (포스터, 장소, 일시, 티켓종류, 예매 버튼) |
| `/queue/[eventId]` | `app/queue/[eventId]/page.tsx` | 필수 | 대기열 UI (순번, 앞/뒤 인원, 예상대기, 자동 리다이렉트) |
| `/events/[id]/seats` | `app/events/[id]/seats/page.tsx` | 필수 | 좌석 선택 (섹션별 좌석 맵, 1석 선택 후 예매) |
| `/events/[id]/book` | `app/events/[id]/book/page.tsx` | 필수 | 스탠딩 티켓 선택 (티켓 타입 1매 선택 후 예매) |
| `/payment/[reservationId]` | `app/payment/[reservationId]/page.tsx` | 필수 | 결제 (네이버/카카오/계좌이체/토스, 만료 타이머) |
| `/payment/success` | `app/payment/success/page.tsx` | 불필요 | 결제 완료 (토스 콜백 처리 포함) |
| `/payment/fail` | `app/payment/fail/page.tsx` | 불필요 | 결제 실패 |
| `/my-reservations` | `app/my-reservations/page.tsx` | 필수 | 내 예매 목록 (상태배지, 만료타이머, 취소/양도) |
| `/reservations/[id]` | `app/reservations/[id]/page.tsx` | 필수 | 예매 상세 |
| `/artists` | `app/artists/page.tsx` | 불필요 | 아티스트 목록 (카드 그리드) |
| `/artists/[id]` | `app/artists/[id]/page.tsx` | 불필요 | 아티스트 상세 (멤버십 가입, 등급 혜택, 포인트, 공연일정) |
| `/community` | `app/community/page.tsx` | 불필요 | 커뮤니티 게시판 (아티스트별 필터, 페이지네이션) |
| `/community/[postId]` | `app/community/[postId]/page.tsx` | 불필요 | 게시글 상세 + 댓글 |
| `/community/write` | `app/community/write/page.tsx` | 필수 | 글 작성 |
| `/news` | `app/news/page.tsx` | 불필요 | 뉴스 목록 |
| `/transfers` | `app/transfers/page.tsx` | 필수 | 양도 마켓 (양도 등록, 구매) |
| `/transfers/my` | `app/transfers/my/page.tsx` | 필수 | 내 양도 목록 |
| `/search` | `app/search/page.tsx` | 불필요 | 검색 결과 |
| `/my-memberships` | `app/my-memberships/page.tsx` | 필수 | 내 멤버십 목록 |
| `/admin` | `app/admin/page.tsx` | admin | 관리자 대시보드 |
| `/admin/events` | `app/admin/events/page.tsx` | admin | 이벤트 CRUD + 좌석 생성 |
| `/admin/events/new` | `app/admin/events/new/page.tsx` | admin | 새 이벤트 생성 폼 |
| `/admin/events/edit/[id]` | `app/admin/events/edit/[id]/page.tsx` | admin | 이벤트 수정 |
| `/admin/reservations` | `app/admin/reservations/page.tsx` | admin | 전체 예매 관리 (상태 변경) |
| `/admin/statistics` | `app/admin/statistics/page.tsx` | admin | 통계 대시보드 (12개 섹션, Recharts) |

### Error/Loading Boundary 구조

```
app/
  layout.tsx          (RootLayout)
  error.tsx           (범용 에러 바운더리)
  loading.tsx         (범용 스켈레톤 UI)
  not-found.tsx       (404)
  events/
    error.tsx         (이벤트 전용 에러)
    loading.tsx       (이벤트 전용 스켈레톤)
    [id]/
      loading.tsx     (이벤트 상세 로딩)
  community/
    error.tsx
    loading.tsx
  admin/
    error.tsx
    loading.tsx
```

---

## 4. API 통신

### 아키텍처 개요

```
+-------------------+        +-------------------+
|  브라우저 (Client) |        | Next.js SSR       |
|                   |        |                   |
| api-client.ts     |        | server-api.ts     |
| (Axios, "use     |        | (fetch, ISR       |
|  client")         |        |  revalidate)      |
+--------+----------+        +--------+----------+
         |                            |
         v                            v
+---------------------------------------------+
|         API Gateway (port 3001)              |
|         /api/v1/*                            |
+---------------------------------------------+
```

### API 클라이언트 설계 (`apps/web/src/lib/api-client.ts`)

모든 API 호출은 단일 Axios 인스턴스 `http`를 통해 이루어진다. Base URL 결정 로직은 다음 우선순위를 따른다:

```
1. NEXT_PUBLIC_API_URL 환경 변수 (빌드 시 bake)
2. window.__API_URL (K8s 런타임 주입)
3. localhost:3001 (로컬 개발)
4. 동일 IP:3001 (사설 네트워크)
5. "" (프로덕션: 같은 오리진, CloudFront/ALB 리버스 프록시)
```

**참고:** `api-client.ts:8-36`

### API 네임스페이스

`api-client.ts`는 도메인별로 10개의 API 네임스페이스를 export한다:

| 네임스페이스 | 라인 | 엔드포인트 | 메서드 수 |
|-------------|------|-----------|----------|
| `authApi` | 133-141 | `/auth/*` | 6 (register, login, me, refresh, logout, google) |
| `eventsApi` | 143-146 | `/events/*` | 2 (list, detail) |
| `queueApi` | 148-153 | `/queue/*` | 4 (check, status, heartbeat, leave) |
| `reservationsApi` | 155-168 | `/reservations/*` | 4 (createTicketOnly, mine, byId, cancel) |
| `seatsApi` | 170-177 | `/seats/*` | 2 (byEvent, reserve) |
| `paymentsApi` | 179-183 | `/payments/*` | 3 (prepare, confirm, process) |
| `statsApi` | 185-200 | `/stats/*` | 13 |
| `communityApi` | 202-216 | `/community/*` | 7 |
| `newsApi` | 218-226 | `/news/*` | 5 |
| `artistsApi` | 228-231 | `/artists/*` | 2 |
| `membershipsApi` | 233-238 | `/memberships/*` | 4 |
| `transfersApi` | 240-246 | `/transfers/*` | 5 |
| `adminApi` | 248-270 | `/admin/*` | 9 (중첩 객체 구조) |

### Interceptors

**Request Interceptor** (`api-client.ts:70-77`):
- 쿠키에서 `urr-entry-token`을 읽어 `x-queue-entry-token` 헤더로 첨부
- Lambda@Edge와 Gateway VWR 검증에 사용

**Response Interceptor** (`api-client.ts:79-131`):
- **401 처리**: Silent token refresh (`POST /auth/refresh`), 실패 큐 관리, 중복 refresh 방지
- **429 처리**: 지수 백오프 재시도 (최대 2회, 최대 4초 대기)
- Auth 엔드포인트 자체에 대해서는 refresh를 시도하지 않음

```
401 응답 수신
    |
    +-- 이미 retry? --> reject
    |
    +-- auth 엔드포인트? --> reject
    |
    +-- refresh 진행 중? --> failedQueue에 추가
    |
    +-- refresh 시도
         |
         +-- 성공: 큐 flush + 원본 요청 재시도
         |
         +-- 실패: 큐 reject + clearAuth() + reject
```

### SSR용 API (`apps/web/src/lib/server-api.ts`)

서버 컴포넌트를 위한 별도의 `serverFetch` 함수가 있으나, 현재 실제 사용되는 페이지는 확인되지 않는다. 모든 페이지가 `"use client"` 지시어를 사용하고 있어 클라이언트 사이드에서만 데이터를 페칭한다.

```typescript
// apps/web/src/lib/server-api.ts:3-14
export async function serverFetch<T>(path: string, options?: { revalidate?: number }): Promise<T | null> {
  const res = await fetch(`${BASE_URL}/api/v1${path}`, {
    next: { revalidate: options?.revalidate ?? 60 },
  });
  // ...
}
```

### Idempotency Key

예매/좌석 예약 요청에 `crypto.randomUUID()`를 사용한 멱등성 키를 자동 생성한다 (`api-client.ts:162-163`, `175-176`). 이는 네트워크 재시도 시 중복 예매를 방지한다.

---

## 5. 인증 흐름

### 인증 아키텍처

```
+---------------------+
| AuthProvider         |  (React Context)
| +-- user: AuthUser  |
| +-- isLoading       |
| +-- isAuthenticated |
| +-- refresh()       |
| +-- logout()        |
+----------+----------+
           |
           v
+---------------------+
| authApi.me()        |  GET /auth/me
| (마운트 시 호출)     |  HttpOnly 쿠키로 인증
+---------------------+
           |
           +------+------+
           |             |
        200 OK       401 Error
           |             |
     setUser(u)    setUser(null)
```

**참고:** `apps/web/src/lib/auth-context.tsx:1-83`

### 인증 수단

| 방식 | 구현 | 비고 |
|------|------|------|
| 이메일/비밀번호 | `authApi.login()` | POST /auth/login |
| Google OAuth | `authApi.google(credential)` | Google Identity Services SDK 동적 로드 |
| 토큰 갱신 | `authApi.refresh()` | POST /auth/refresh, HttpOnly 쿠키 |

### 토큰 저장

- **Access Token / Refresh Token**: 서버가 **HttpOnly 쿠키**로 설정 (`withCredentials: true`)
- **Entry Token (대기열)**: `document.cookie`에 `urr-entry-token`으로 직접 저장 (max-age: 600초)
- **Legacy**: `localStorage.removeItem("user")` 호출이 `clearAuth()`에 남아 있음 (호환성)

### AuthGuard 컴포넌트 (`apps/web/src/components/auth-guard.tsx`)

```typescript
// auth-guard.tsx:12-43
export function AuthGuard({ children, adminOnly = false }: Props) {
  // isLoading 중 = 스피너 표시
  // !user = /login으로 리다이렉트
  // adminOnly && role !== "admin" = / 으로 리다이렉트
}
```

- 로딩 중에는 접근성을 고려한 스피너(`role="status"`, `aria-label="인증 확인 중"`)를 표시
- 리다이렉트는 `router.replace()`로 히스토리에 남기지 않음
- `adminOnly` 프롭으로 관리자 전용 페이지 보호

### 사용자 역할

`UserRole`은 `"user" | "admin"` 두 가지만 존재한다 (`types.ts:1`). 서버 응답의 `role`이 대소문자 혼용 가능하여 `toLowerCase() === "admin"` 비교를 수행한다 (`auth-context.tsx:39`).

---

## 6. 대기열 (Queue) UI

### 전체 흐름

```
이벤트 상세 --[예매하기 클릭]--> /queue/[eventId]
                                     |
                                queueApi.check()
                                     |
                          +----------+-----------+
                          |                      |
                      queued=true            queued=false
                          |                  (즉시 입장)
                    setJoined(true)              |
                          |              seatLayoutId 확인
                  useQueuePolling()               |
                     (폴링 시작)          +-------+-------+
                          |              |               |
                    status 변경       좌석 있음       좌석 없음
                          |              |               |
                    active/admitted  /events/[id]/    /events/[id]/
                          |           seats              book
                          +-------+-------+
                                  |
                          seatLayoutId 확인
                                  |
                          좌석/티켓 페이지로 리다이렉트
```

### 대기열 페이지 (`apps/web/src/app/queue/[eventId]/page.tsx`)

핵심 동작:

1. **마운트 시**: `queueApi.check(eventId)` 호출 -- 대기열 진입 또는 즉시 입장 판단
2. **`eventsApi.detail(eventId)` 병렬 호출**: `seatLayoutId` 여부를 서버에서 가져와 좌석/스탠딩 분기 결정
3. **폴링**: `useQueuePolling` 훅으로 주기적으로 상태 확인
4. **입장 시**: `seatLayoutId` 유무에 따라 `/events/[id]/seats` 또는 `/events/[id]/book`으로 자동 리다이렉트

**보안 고려사항**: `seatLayoutId`를 URL 파라미터가 아닌 서버 데이터에서 가져와 클라이언트 사이드 우회를 방지한다 (`page.tsx:26-27` 주석 참조).

### Queue Polling 훅 (`apps/web/src/hooks/use-queue-polling.ts`)

```
폴링 주기 결정:
  서버 응답의 nextPoll 필드 사용 (없으면 기본 3초)
  최소: 1초, 최대: 60초 (clampPoll)

Entry Token 처리:
  서버가 entryToken 반환 시 쿠키에 저장
  urr-entry-token 쿠키 (max-age: 600초)
  https 환경에서 Secure 플래그 추가
```

**참고:** `use-queue-polling.ts:7-13`, `48-51`

### 대기열 UI 구성

| 표시 항목 | 소스 필드 | 위치 |
|----------|----------|------|
| 대기 순번 | `position` | 대형 숫자 (4xl bold) |
| 내 앞 인원 | `peopleAhead` | 3열 그리드 |
| 내 뒤 인원 | `peopleBehind` | 3열 그리드 |
| 예상 대기 시간 | `estimatedWait` | 3열 그리드 |
| 현재 접속자/최대 | `currentUsers`/`threshold` | 하단 텍스트 |
| 이벤트 정보 | `eventInfo.title`/`artist` | 헤더 하단 |

접근성: `role="status"`, `aria-live="polite"`, `aria-label="대기열 상태"` 적용 (`page.tsx:121`).

---

## 7. VWR 정적 페이지

### 개요

`apps/vwr/index.html`은 Next.js와 완전히 분리된 **정적 HTML/CSS/JS** 파일이다. CloudFront + Lambda@Edge 환경에서 VWR(Virtual Waiting Room)으로 제공되도록 설계되었다.

### 기술 특성

| 항목 | 내용 |
|------|------|
| 번들러 | 없음 (바닐라 JS) |
| 프레임워크 | 없음 |
| 스타일 | 인라인 `<style>` |
| API 통신 | `fetch()` 직접 사용 |
| 상태 관리 | `localStorage` |
| 테마 | 다크 (#0a0a0a 배경) |

### VWR 흐름

```
+--[사용자 접속]--+
|                |
| /vwr/{eventId} |
+-------+--------+
        |
  localStorage 확인
  (urr-vwr-{eventId})
        |
   +----+----+
   |         |
 있음       없음
   |         |
 이전 세션   POST /vwr/assign/{eventId}
 requestId    |
   |         +-- 404: VWR 비활성 --> /events/{eventId}로 리다이렉트
   |         +-- 성공: requestId + position 저장
   |
   +---------+
             |
       폴링 시작
 GET /vwr/check/{eventId}/{requestId}
             |
        +----+----+
        |         |
   admitted    대기 중
   + token        |
        |    updateDisplay()
  setCookie()  schedulePoll()
  urr-vwr-token   |
        |    (nextPoll 또는 5초)
  /events/{eventId}
  로 리다이렉트
```

### VWR vs Next.js Queue 비교

| 측면 | VWR (`apps/vwr/`) | Queue (`apps/web/queue/`) |
|------|-------------------|--------------------------|
| 렌더링 | 정적 HTML (CDN) | Next.js CSR |
| 인증 | 익명 (anon-id) | HttpOnly 쿠키 인증 필수 |
| API | `/vwr-api/vwr/*` | `/api/v1/queue/*` |
| 용도 | CDN 레벨 트래픽 차단 | 애플리케이션 레벨 대기열 |
| 토큰 | `urr-vwr-token` 쿠키 | `urr-entry-token` 쿠키 |
| 세션 유지 | localStorage | React state |
| 오프라인 | `error.html` 폴백 | Next.js error boundary |

### VWR error.html

`apps/vwr/error.html`은 S3/CloudFront에서 오리진 오류 시 보여줄 에러 페이지이다. "다시 시도" 버튼으로 `window.location.reload()`를 호출한다. 동일한 다크 테마를 사용한다.

---

## 8. 상태 관리

### 상태 관리 전략

이 프로젝트는 중앙 집중식 전역 상태 관리 라이브러리(Redux, Zustand 등)를 사용하지 않는다. 대신 다음 조합을 사용한다:

```
+------------------------------------------+
| 상태 유형         | 관리 방식              |
+------------------------------------------+
| 서버 데이터 캐시   | TanStack React Query  |
| 인증 상태          | React Context         |
| 페이지 로컬 상태   | useState              |
| 시간 동기화        | 모듈 레벨 싱글턴       |
| 엔트리 토큰        | document.cookie       |
+------------------------------------------+
```

### React Query 설정 (`apps/web/src/app/providers.tsx:8-19`)

```typescript
new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 60 * 1000,          // 1분간 fresh 유지
      gcTime: 5 * 60 * 1000,         // 5분간 가비지 컬렉션 유예
      retry: 1,                       // 1회 재시도
      refetchOnWindowFocus: false,    // 포커스 시 자동 refetch 비활성화
    },
  },
})
```

React Query를 사용하는 훅:
- `useEvents(params)` -- `queryKey: ["events", params]`
- `useEventDetail(id)` -- `queryKey: ["event", id]`
- `useMyReservations()` -- `queryKey: ["reservations", "mine"]`

### 서버 시간 동기화 (`apps/web/src/hooks/use-server-time.ts`)

클라이언트-서버 시간 차이를 보정하는 모듈 레벨 싱글턴 패턴:

```
1. GET /api/time 호출
2. RTT 측정 (before/after)
3. offset = serverTime - (clientBefore + rtt/2)
4. cachedOffset에 저장 (모듈 스코프)
5. 이후 모든 useCountdown에서 offset 적용
```

이 패턴은 앱 전체에서 한 번만 서버 시간을 가져온 후 캐시하여, 카운트다운 타이머가 서버 시간 기준으로 정확하게 동작하도록 한다.

### 카운트다운 훅 (`apps/web/src/hooks/use-countdown.ts`)

`useCountdown`은 다음 시나리오에서 사용된다:

| 용도 | 대상 | 만료 시 동작 |
|------|------|-------------|
| 이벤트 판매 종료 | `sale_end_date` | 목록 refetch 또는 필터 변경 |
| 이벤트 오픈 카운트다운 | `sale_start_date` | 필터를 "예매 중"으로 전환 |
| 결제 만료 타이머 | `expires_at` | `/my-reservations`로 리다이렉트 |
| 예매 만료 표시 | `expires_at` | 목록 refetch |

**무한 루프 방지**: 이미 만료된 상태로 마운트된 경우 `onExpire` 콜백을 호출하지 않는다 (`use-countdown.ts:65-73` 주석 참조). 이는 `onExpire -> refetch -> remount -> onExpire -> ...` 무한 루프를 차단한다.

---

## 9. 빌드 및 배포

### Dockerfile (`apps/web/Dockerfile`)

멀티스테이지 빌드:

```
Stage 1: builder (node:20-alpine)
  +-- npm ci (의존성 설치, 캐시 레이어)
  +-- npm run build (Next.js 빌드, NEXT_PUBLIC_API_URL ARG로 주입)

Stage 2: runner (node:20-alpine)
  +-- package*.json 복사
  +-- .next/ 복사 (빌드 결과물)
  +-- public/ 복사
  +-- npm ci --omit=dev (프로덕션 의존성만)
  +-- 비루트 사용자 (app:app, uid/gid 1001)
  +-- EXPOSE 3000
  +-- CMD ["npm", "run", "start"]
```

**주의사항**: `NEXT_PUBLIC_API_URL`이 빌드 시 ARG로 주입되어 번들에 베이크된다. 런타임에 변경하려면 `window.__API_URL` 주입 방식을 사용해야 한다.

### next.config.ts (`apps/web/next.config.ts`)

```typescript
const nextConfig: NextConfig = {
  turbopack: {
    root: path.resolve(__dirname),
  },
  images: {
    remotePatterns: [
      { protocol: "https", hostname: "**" },  // 모든 HTTPS 이미지 허용
    ],
  },
};
```

- **Turbopack** 활성화 (개발 빌드 속도 향상)
- 이미지 최적화: 모든 HTTPS 호스트 허용 (보안 관점에서 과도하게 열려 있음)
- **Standalone 출력 미사용**: `output: "standalone"` 미설정

### 환경 변수

| 변수 | 용도 | 주입 시점 |
|------|------|----------|
| `NEXT_PUBLIC_API_URL` | 백엔드 API Gateway URL | 빌드 시 (ARG) |
| `NEXT_PUBLIC_GOOGLE_CLIENT_ID` | Google OAuth 클라이언트 ID | 빌드 시 |
| `NODE_ENV` | 환경 구분 | Dockerfile에서 `production` 설정 |

### 보안 미들웨어 (`apps/web/src/middleware.ts`)

모든 요청에 대해 다음 보안 헤더를 동적으로 설정한다:

| 헤더 | 값 | 목적 |
|------|-----|------|
| `Content-Security-Policy` | nonce 기반, `unsafe-inline` (script) | XSS 방어 |
| `X-Frame-Options` | `DENY` | 클릭재킹 방어 |
| `X-Content-Type-Options` | `nosniff` | MIME 스니핑 방어 |
| `Referrer-Policy` | `strict-origin-when-cross-origin` | 리퍼러 제어 |
| `Permissions-Policy` | camera/mic/geo/payment 모두 비활성화 | 권한 제어 |

CSP의 `connect-src`에 `https://*.urr.guru`가 포함되어 있어 프로덕션 도메인이 `urr.guru`임을 알 수 있다.

### 테스트 구성

**단위 테스트** (Vitest):
- `src/test/setup.tsx`: `next/navigation`과 `next/link` 모킹
- `src/test/components/site-header.test.tsx`
- `src/test/hooks/use-queue-polling.test.ts`
- `src/test/lib/format.test.ts`
- 환경: happy-dom

**E2E 테스트** (Playwright):
- `e2e/smoke.spec.ts`: 기본 접근성 검증
- `e2e/auth.spec.ts`: 로그인/회원가입 플로우
- `e2e/booking-flow.spec.ts`: 예매 플로우
- 브라우저: Chromium만
- 실패 시 스크린샷, 첫 재시도 시 trace

---

## 10. 좋은 점과 미흡한 점

### 좋은 점

**1. 체계적인 API 클라이언트 설계**

`api-client.ts`의 인터셉터 설계가 견고하다. Silent token refresh, 실패 큐(failedQueue) 관리, auth 엔드포인트 예외 처리, 429 지수 백오프 등 실전 환경에서 필요한 패턴이 빠짐없이 구현되어 있다. 멱등성 키 자동 생성도 중복 예매 방지에 효과적이다.

**2. 서버 시간 동기화**

`useServerTime` 훅이 RTT 보정까지 수행하는 NTP 유사 방식으로 시간 차이를 계산한다. 티켓팅 플랫폼에서 카운트다운 정확도는 사용자 경험에 직결되며, 이를 모듈 레벨 싱글턴으로 효율적으로 관리한다.

**3. 무한 루프 방지 로직**

`useCountdown`의 `onExpire` 콜백이 이미 만료된 상태에서 재마운트 시 호출되지 않도록 방어한다. 이는 `onExpire -> refetch -> remount` 순환을 차단하는 중요한 엣지 케이스 처리이다.

**4. 접근성 기본 준수**

- Skip navigation 링크 (`layout.tsx:28-33`)
- `role="main"`, `role="status"`, `role="search"` 시멘틱 마크업
- `aria-label`, `aria-live="polite"` 속성
- `lang="ko"` 설정
- AuthGuard 로딩 시 `role="status"` + `aria-label`

**5. 보안 미들웨어**

CSP nonce, X-Frame-Options DENY, Permissions-Policy 비활성화 등 프로덕션 수준의 보안 헤더가 per-request nonce 방식으로 적용되어 있다.

**6. 이중 대기열 아키텍처**

VWR(CDN 레벨 정적 대기열) + Queue(앱 레벨 인증된 대기열) 이중 구조는 트래픽 급증 시나리오에 대한 방어 깊이를 제공한다. VWR에서 익명 트래픽을 걸러내고, Queue에서 인증된 사용자를 순서대로 처리한다.

**7. 관리자 통계 대시보드**

12개 섹션(실시간, 개요, 전환 퍼널, 시간대별, 일별 예매/매출, 취소 분석, 좌석 선호도, 사용자 행동, 시스템 성능, 이벤트별, 결제 수단)을 갖춘 종합적인 통계 뷰가 Recharts로 시각화되어 있다. 30초 주기의 실시간 갱신도 포함한다.

---

### 미흡한 점

**1. 거의 모든 페이지가 `"use client"` -- SSR 미활용**

현재 모든 페이지가 클라이언트 컴포넌트이다. 이벤트 목록, 이벤트 상세, 아티스트 목록 등은 서버 컴포넌트로 초기 데이터를 렌더링하면 SEO와 FCP(First Contentful Paint)가 크게 개선된다. `server-api.ts`가 이미 존재하지만 사용되고 있지 않다.

> **AWS 배포 시 개선 방안**: CloudFront 캐싱과 결합하여 서버 컴포넌트가 생성한 HTML을 CDN에서 캐시하면, API 서버 부하 감소와 동시에 빠른 초기 렌더링을 달성할 수 있다.

**2. 디자인 시스템 부재 -- 컴포넌트 중복**

공유 컴포넌트가 3개(`AuthGuard`, `SiteHeader`, `PageCard`)뿐이다. 다음 UI 패턴이 각 페이지에서 반복 구현되어 있다:

- **statusBadge 함수**: `page.tsx`, `event-detail-client.tsx`, `admin/events/page.tsx`, `my-reservations/page.tsx`, `admin/reservations/page.tsx` 등 5곳 이상에서 거의 동일한 함수가 중복 정의
- **스피너**: `animate-spin rounded-full border-2 border-sky-500 border-t-transparent` 패턴이 20+ 곳에서 반복
- **입력 필드 스타일**: `rounded-lg border border-slate-200 bg-slate-50 px-3 py-2.5 ...` 클래스 문자열이 각 폼에서 반복

> **개선 방안**: `Button`, `Spinner`, `Badge`, `Input`, `Card` 등 기본 컴포넌트를 `components/ui/`에 추출하여 디자인 일관성과 유지보수성을 확보할 수 있다.

**3. 타입 안전성 부족 -- `any` 다수 사용**

`admin/statistics/page.tsx`에서 `eslint-disable @typescript-eslint/no-explicit-any` 주석과 함께 12개 상태 변수가 모두 `any`로 선언되어 있다 (`page.tsx:65-76`). 서버 응답 구조에 대한 타입 정의를 추가하면 런타임 오류를 사전에 방지할 수 있다.

**4. Next.js Image 컴포넌트 불일치**

`next/image`의 `Image` 컴포넌트는 아티스트 상세 페이지(`artists/[id]/page.tsx:174`)에서만 사용되고, 홈페이지 이벤트 카드(`page.tsx:63`)와 이벤트 상세(`event-detail-client.tsx:54`) 등에서는 `<img>` 태그를 직접 사용한다. Next.js `Image`는 자동 리사이징, WebP 변환, lazy loading을 제공하므로 모든 이미지에 적용해야 한다.

또한, `next.config.ts`의 `images.remotePatterns`이 `hostname: "**"`로 모든 호스트를 허용하고 있어 보안 위험이 있다.

> **AWS 배포 시 개선 방안**: S3 + CloudFront를 이미지 오리진으로 설정하고, `remotePatterns`을 특정 S3 버킷 도메인으로 제한한다.

**5. Standalone 출력 미설정**

Dockerfile에서 `.next` 폴더 전체와 `node_modules`(프로덕션)를 복사하고 있다. `next.config.ts`에 `output: "standalone"`을 설정하면 필요한 파일만 추출되어 Docker 이미지 크기를 50-80% 줄일 수 있다.

> **AWS 배포 시 개선 방안**: ECS/EKS에서 standalone 출력을 사용하면 컨테이너 시작 시간이 단축되고, ECR 저장 비용도 감소한다.

**6. 모바일 반응형 미흡**

`SiteHeader`의 네비게이션이 수평 레이아웃만 지원한다. 모바일 뷰포트에서 햄버거 메뉴나 bottom navigation이 없어 많은 링크가 화면 밖으로 넘칠 수 있다. 좌석 선택 페이지(`seats/page.tsx`)의 좌석 그리드도 모바일에서 가로 스크롤이 필요한 구조이다.

> **개선 방안**: 768px 이하에서 햄버거 메뉴 토글, bottom tab navigation, 좌석 맵의 pinch-to-zoom 등을 구현한다.

**7. 에러 처리 일관성 부족**

`alert()`와 `confirm()`이 관리자 페이지와 양도/취소 기능에서 사용되고 있다 (`admin/events/page.tsx:59`, `my-reservations/page.tsx:125`, `transfers/page.tsx:77`). 이는 모바일 웹에서 UX가 좋지 않으며, 스크린 리더 사용자에게 접근성 문제를 유발할 수 있다.

> **개선 방안**: 확인/취소 다이얼로그를 커스텀 모달 컴포넌트로 교체하고, 오류 메시지를 인라인 토스트/알림으로 표시한다.

**8. 다크 모드 미지원**

`globals.css`에 CSS 변수가 정의되어 있으나 다크 모드 변형이 없다. Tailwind CSS v4의 `@media (prefers-color-scheme: dark)` 또는 `dark:` variant를 활용하여 OS 설정에 따른 자동 전환을 구현할 수 있다.

**9. 번들 최적화 미흡**

- `recharts` (차트 라이브러리)가 관리자 통계 페이지에서만 사용되지만, 동적 임포트되지 않아 메인 번들에 포함될 가능성이 있다
- Google OAuth SDK가 `<script>` 태그 직접 삽입 방식으로 로드됨

> **AWS 배포 시 개선 방안**: `next/dynamic`으로 관리자 통계 차트를 지연 로드하고, CloudFront의 HTTP/3 + Brotli 압축으로 전송 크기를 최소화한다.

**10. 404 페이지 한국어 미적용**

`not-found.tsx`의 텍스트가 영어("Page not found", "Go home")로 되어 있다. 나머지 UI가 모두 한국어인데 404만 영어이다.

---

### 개선 우선순위 요약

| 우선순위 | 항목 | 영향도 | 난이도 |
|----------|------|--------|--------|
| 높음 | SSR 도입 (서버 컴포넌트 전환) | SEO + 성능 | 중간 |
| 높음 | Standalone Docker 출력 | 배포 크기/속도 | 낮음 |
| 높음 | 모바일 네비게이션 | UX | 중간 |
| 중간 | 디자인 시스템 컴포넌트 추출 | 유지보수 | 중간 |
| 중간 | `any` 타입 제거 | 안정성 | 중간 |
| 중간 | `<img>` -> `next/image` 통일 | 성능 | 낮음 |
| 중간 | alert/confirm 교체 | UX + 접근성 | 중간 |
| 낮음 | 다크 모드 | UX | 중간 |
| 낮음 | 번들 최적화 (recharts dynamic import) | 성능 | 낮음 |
| 낮음 | 404 페이지 한국어화 | UX 일관성 | 낮음 |
