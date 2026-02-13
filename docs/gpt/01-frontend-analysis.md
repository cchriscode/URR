# 프론트엔드 상세 분석

## 1) 기술 스택 및 빌드/테스트 체계
- 런타임은 Next.js 16 + React 19 조합이다.  
  출처: `apps/web/package.json:17`, `apps/web/package.json:18`, `apps/web/package.json:19`
- 테스트 스크립트가 `vitest`(유닛)와 `playwright`(E2E)로 분리되어 있다.  
  출처: `apps/web/package.json:10`, `apps/web/package.json:12`
- Vitest는 `happy-dom` 기반으로 설정되어 브라우저 API 의존 컴포넌트를 빠르게 검증한다.  
  출처: `apps/web/vitest.config.ts:8`
- Playwright는 `e2e/` 디렉터리 기반, CI 재시도 정책(2회)을 갖는다.  
  출처: `apps/web/playwright.config.ts:4`, `apps/web/playwright.config.ts:7`

## 2) 프론트 구조 및 라우팅
- 홈에서 이벤트 목록을 불러오고(`eventsApi.list`) 상세 페이지로 연결된다.  
  출처: `apps/web/src/app/page.tsx:126`
- 이벤트 상세에서 예매 가능 시 대기열(`/queue/{eventId}`)로 진입시키는 구조다.  
  출처: `apps/web/src/app/events/[id]/page.tsx:132`, `apps/web/src/app/events/[id]/page.tsx:229`
- 좌석형/스탠딩형 예매 페이지를 분리했다.  
  출처: `apps/web/src/app/queue/[eventId]/page.tsx:77`, `apps/web/src/app/queue/[eventId]/page.tsx:80`

## 3) 인증/세션 처리 방식
- API 클라이언트는 쿠키 기반 인증(`withCredentials`)을 기본 사용한다.  
  출처: `apps/web/src/lib/api-client.ts:41`
- 액세스 토큰 만료 시 Axios 인터셉터가 `/auth/refresh`를 호출하고 실패 시 로그아웃/리다이렉트 처리한다.  
  출처: `apps/web/src/lib/api-client.ts:84`, `apps/web/src/lib/api-client.ts:102`, `apps/web/src/lib/api-client.ts:108`, `apps/web/src/lib/api-client.ts:110`
- `AuthGuard`는 로컬 사용자 정보 + 서버 `/auth/me` 검증을 결합한다.  
  출처: `apps/web/src/components/auth-guard.tsx:19`, `apps/web/src/components/auth-guard.tsx:35`, `apps/web/src/components/auth-guard.tsx:36`
- 사용자 기본 정보는 `localStorage`(`user` 키)에 저장한다.  
  출처: `apps/web/src/lib/storage.ts:5`, `apps/web/src/lib/storage.ts:20`

## 4) API 통신 방식
- API 베이스는 `/api/v1`로 고정돼 게이트웨이 버저닝에 맞춘다.  
  출처: `apps/web/src/lib/api-client.ts:39`
- 로컬/사설망/프로덕션 도메인별 베이스 URL 해석 로직이 있다.  
  출처: `apps/web/src/lib/api-client.ts:25`, `apps/web/src/lib/api-client.ts:31`, `apps/web/src/lib/api-client.ts:35`
- 큐 입장 토큰을 `x-queue-entry-token` 헤더로 자동 주입한다.  
  출처: `apps/web/src/lib/api-client.ts:69`, `apps/web/src/lib/api-client.ts:73`

## 5) 대기열 UX 구현 방식
- 대기열 페이지 마운트 시 `queueApi.check`로 입장/즉시입장 여부를 확정한다.  
  출처: `apps/web/src/app/queue/[eventId]/page.tsx:50`, `apps/web/src/app/queue/[eventId]/page.tsx:52`
- 입장 토큰을 `tiketi-entry-token` 쿠키로 저장해 Edge/Gateway 검증에 사용한다.  
  출처: `apps/web/src/app/queue/[eventId]/page.tsx:53`, `apps/web/src/app/queue/[eventId]/page.tsx:56`
- 큐 상태 폴링은 서버가 내려준 `nextPoll`로 동적으로 주기를 조절한다.  
  출처: `apps/web/src/hooks/use-queue-polling.ts:44`, `apps/web/src/hooks/use-queue-polling.ts:45`
- 입장 승인 시 좌석형/스탠딩형 예매 페이지로 자동 분기한다.  
  출처: `apps/web/src/app/queue/[eventId]/page.tsx:76`, `apps/web/src/app/queue/[eventId]/page.tsx:78`, `apps/web/src/app/queue/[eventId]/page.tsx:80`

## 6) 예매/결제 플로우 구현
- 좌석형은 `seatsApi.reserve`로 1석 단위 예약 후 결제 페이지로 이동한다.  
  출처: `apps/web/src/app/events/[id]/seats/page.tsx:120`, `apps/web/src/app/events/[id]/seats/page.tsx:122`, `apps/web/src/app/events/[id]/seats/page.tsx:126`
- 스탠딩형은 `reservationsApi.createTicketOnly`로 수량 기반 예약 후 결제로 이동한다.  
  출처: `apps/web/src/app/events/[id]/book/page.tsx:55`, `apps/web/src/app/events/[id]/book/page.tsx:57`, `apps/web/src/app/events/[id]/book/page.tsx:62`
- 일반 결제는 `prepare`(Toss SDK)와 `process`(간편 mock 성공) 두 경로를 지원한다.  
  출처: `apps/web/src/app/payment/[reservationId]/page.tsx:63`, `apps/web/src/app/payment/[reservationId]/page.tsx:70`, `apps/web/src/app/payment/[reservationId]/page.tsx:81`
- 양도 결제/멤버십 결제도 동일하게 `paymentType + referenceId`로 처리한다.  
  출처: `apps/web/src/app/transfer-payment/[transferId]/page.tsx:62`, `apps/web/src/app/transfer-payment/[transferId]/page.tsx:80`, `apps/web/src/app/membership-payment/[membershipId]/page.tsx:78`, `apps/web/src/app/membership-payment/[membershipId]/page.tsx:96`
- 내 예약 화면에서 예약 취소와 양도 등록 액션을 동시에 제공한다.  
  출처: `apps/web/src/app/my-reservations/page.tsx:138`, `apps/web/src/app/my-reservations/page.tsx:148`

## 7) 보안 관련 프론트 구현
- 미들웨어에서 CSP, XFO, nosniff, referrer-policy를 설정한다.  
  출처: `apps/web/src/middleware.ts:23`, `apps/web/src/middleware.ts:24`, `apps/web/src/middleware.ts:25`, `apps/web/src/middleware.ts:26`
- 다만 `script-src 'unsafe-inline'`을 허용하고 있다.  
  출처: `apps/web/src/middleware.ts:12`
- 큐 엔트리 토큰 쿠키를 JS에서 작성하는 구조라 `HttpOnly` 보호는 적용되지 않는다.  
  출처: `apps/web/src/app/queue/[eventId]/page.tsx:56`, `apps/web/src/hooks/use-queue-polling.ts:50`

## 8) 테스트 커버리지 현황
- E2E는 smoke 테스트 위주(홈/로그인/회원가입/아티스트/뉴스/네비게이션)다.  
  출처: `apps/web/e2e/smoke.spec.ts:4`, `apps/web/e2e/smoke.spec.ts:12`, `apps/web/e2e/smoke.spec.ts:18`, `apps/web/e2e/smoke.spec.ts:24`, `apps/web/e2e/smoke.spec.ts:29`, `apps/web/e2e/smoke.spec.ts:34`
- 관리자/결제/대기열 경쟁상황 등 핵심 비즈니스 시나리오 E2E는 아직 제한적이다(스모크 중심).

## 9) 좋은 점
- API 버저닝(`/api/v1`)을 프론트 클라이언트에서 일관되게 적용했다.  
  출처: `apps/web/src/lib/api-client.ts:39`
- 토큰 만료 시 동시요청 큐잉(`failedQueue`)으로 refresh 폭주를 방지한다.  
  출처: `apps/web/src/lib/api-client.ts:52`, `apps/web/src/lib/api-client.ts:91`, `apps/web/src/lib/api-client.ts:94`
- 대기열 상태의 `nextPoll`을 반영하는 적응형 폴링으로 불필요한 호출을 줄인다.  
  출처: `apps/web/src/hooks/use-queue-polling.ts:44`, `apps/web/src/hooks/use-queue-polling.ts:60`
- 좌석형/스탠딩형 플로우 분리가 명확해 사용자 혼동이 적다.  
  출처: `apps/web/src/app/queue/[eventId]/page.tsx:78`, `apps/web/src/app/queue/[eventId]/page.tsx:80`

## 10) 미흡한 점
- CSP에서 `unsafe-inline`을 허용하여 XSS 방어 강도가 낮아질 수 있다.  
  출처: `apps/web/src/middleware.ts:12`
- 사용자 권한 보조 상태를 `localStorage`에 보관하고 네트워크 오류 시 이를 fallback으로 신뢰한다. 보안 민감 페이지에서 오탐 허용 가능성이 있다.  
  출처: `apps/web/src/lib/storage.ts:20`, `apps/web/src/components/auth-guard.tsx:45`, `apps/web/src/components/auth-guard.tsx:49`, `apps/web/src/components/auth-guard.tsx:55`
- 화면 문자열 일부가 깨진 형태로 확인되어 인코딩/국제화 품질 점검이 필요하다.  
  출처: `apps/web/src/app/events/[id]/seats/page.tsx:137`, `apps/web/src/app/register/page.tsx:43`, `apps/web/src/app/payment/[reservationId]/page.tsx:72`
- E2E가 스모크 중심이라 결제 실패, 대기열 우회, 동시 좌석 경쟁 등의 회귀 방어가 약하다.  
  출처: `apps/web/e2e/smoke.spec.ts:3`

## 11) 개선 제안
1. `script-src`를 nonce/hash 중심으로 전환하고 `unsafe-inline` 제거를 단계적으로 진행.
2. `AuthGuard`의 네트워크 에러 fallback 정책을 페이지 위험도별로 분리(관리자/결제는 strict).
3. 대기열/예매/결제/관리자 플로우 E2E를 추가(성공+실패+경합 시나리오).
4. 프론트 문자열 인코딩 정책(UTF-8, lint 규칙)과 i18n 리소스 분리 적용.
