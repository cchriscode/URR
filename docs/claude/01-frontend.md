# 01. 프론트엔드 분석

---

## 1. 기술 스택

이 프로젝트의 프론트엔드는 Next.js 기반의 단일 페이지 애플리케이션(SPA)으로, React 19와 TypeScript를 사용한다.

| 범주 | 라이브러리 | 버전 | 용도 |
|------|-----------|------|------|
| 프레임워크 | Next.js | 16.1.6 | App Router 기반 SSR/CSR 하이브리드 |
| UI | React | 19.2.3 | 컴포넌트 렌더링 |
| 언어 | TypeScript | 5.9.3 | 정적 타입 체크 |
| 스타일 | TailwindCSS | ^4 | 유틸리티 우선 CSS |
| 서버 상태 | @tanstack/react-query | ^5.90.21 | API 캐싱, 자동 재요청 |
| HTTP | Axios | ^1.13.5 | API 클라이언트, 인터셉터 |
| 차트 | Recharts | ^3.7.0 | 관리자 통계 시각화 |
| 결제 | @tosspayments/payment-sdk | ^1.9.2 | TossPayments 결제 연동 |
| E2E 테스트 | @playwright/test | ^1.58.2 | 브라우저 기반 E2E 테스트 |
| 단위 테스트 | Vitest | ^4.0.18 | 컴포넌트/유틸 단위 테스트 |
| 테스트 유틸 | @testing-library/react | ^16.3.2 | React 컴포넌트 테스트 헬퍼 |
| 테스트 DOM | happy-dom | ^20.6.1 | 경량 DOM 시뮬레이션 |

> 참조: `apps/web/package.json:14-38`

---

## 2. 프로젝트 구조

### 2.1 디렉토리 트리

```
apps/web/src/
├── app/                          # Next.js App Router 페이지
│   ├── layout.tsx                # 루트 레이아웃 (폰트, Providers, SiteHeader)
│   ├── providers.tsx             # QueryClient + AuthProvider 래핑
│   ├── globals.css               # TailwindCSS import + CSS 변수
│   ├── page.tsx                  # 홈 (이벤트 목록)
│   ├── login/page.tsx            # 로그인
│   ├── register/page.tsx         # 회원가입
│   ├── search/page.tsx           # 검색 결과
│   ├── not-found.tsx             # 404 페이지
│   ├── error.tsx                 # 전역 에러 바운더리
│   ├── loading.tsx               # 전역 로딩 상태
│   ├── events/
│   │   ├── [id]/
│   │   │   ├── page.tsx          # 이벤트 상세
│   │   │   ├── event-detail-client.tsx  # 상세 클라이언트 컴포넌트
│   │   │   ├── seats/page.tsx    # 좌석 선택
│   │   │   ├── book/page.tsx     # 스탠딩 예매
│   │   │   └── loading.tsx
│   │   ├── error.tsx
│   │   └── loading.tsx
│   ├── queue/[eventId]/page.tsx  # 대기열
│   ├── payment/
│   │   ├── [reservationId]/page.tsx  # 결제
│   │   ├── success/page.tsx      # 결제 성공
│   │   └── fail/page.tsx         # 결제 실패
│   ├── payment-success/[reservationId]/page.tsx
│   ├── my-reservations/
│   │   ├── page.tsx              # 내 예매 목록
│   │   └── loading.tsx
│   ├── reservations/[id]/page.tsx  # 예매 상세
│   ├── transfers/
│   │   ├── page.tsx              # 양도 마켓
│   │   └── my/page.tsx           # 내 양도 목록
│   ├── transfer-payment/[transferId]/page.tsx  # 양도 결제
│   ├── my-memberships/page.tsx   # 내 멤버십
│   ├── membership-payment/[membershipId]/page.tsx  # 멤버십 결제
│   ├── artists/
│   │   ├── page.tsx              # 아티스트 목록
│   │   └── [id]/page.tsx         # 아티스트 상세
│   ├── community/
│   │   ├── page.tsx              # 커뮤니티 게시판
│   │   ├── write/page.tsx        # 글쓰기
│   │   ├── [postId]/page.tsx     # 게시글 상세
│   │   ├── [postId]/edit/page.tsx # 게시글 수정
│   │   ├── error.tsx
│   │   └── loading.tsx
│   ├── news/
│   │   ├── page.tsx              # 뉴스 목록
│   │   ├── create/page.tsx       # 뉴스 작성
│   │   ├── [id]/page.tsx         # 뉴스 상세
│   │   └── [id]/edit/page.tsx    # 뉴스 수정
│   └── admin/
│       ├── page.tsx              # 관리자 대시보드
│       ├── events/
│       │   ├── page.tsx          # 이벤트 관리
│       │   ├── new/page.tsx      # 이벤트 생성
│       │   └── edit/[id]/page.tsx # 이벤트 수정
│       ├── reservations/page.tsx # 예매 관리
│       ├── statistics/page.tsx   # 통계
│       ├── loading.tsx
│       └── error.tsx
├── components/
│   ├── site-header.tsx           # 글로벌 헤더 (검색, 내비게이션)
│   ├── auth-guard.tsx            # 인증 보호 컴포넌트
│   └── page-card.tsx             # 재사용 카드 컨테이너
├── hooks/
│   ├── use-events.ts             # useEvents, useEventDetail
│   ├── use-reservations.ts       # useMyReservations
│   ├── use-queue-polling.ts      # useQueuePolling (대기열 상태 폴링)
│   ├── use-server-time.ts        # useServerTime (서버 시간 동기화)
│   └── use-countdown.ts          # useCountdown (카운트다운 타이머)
├── lib/
│   ├── api-client.ts             # Axios 인스턴스 + 13개 API 모듈
│   ├── auth-context.tsx          # AuthContext/AuthProvider
│   ├── types.ts                  # 전체 TypeScript 타입 정의
│   ├── format.ts                 # 날짜/가격 포맷 유틸리티
│   ├── storage.ts                # localStorage 유틸 (레거시)
│   └── server-api.ts             # 서버 사이드 fetch 유틸리티
├── middleware.ts                 # 보안 헤더 미들웨어
└── test/
    ├── setup.tsx                 # 테스트 초기 설정
    ├── components/site-header.test.tsx
    ├── hooks/use-queue-polling.test.ts
    └── lib/format.test.ts
```

### 2.2 라우트 정리

| 라우트 패턴 | 페이지 파일 | 설명 |
|-------------|-----------|------|
| `/` | `app/page.tsx` | 이벤트 목록 (홈) |
| `/login` | `app/login/page.tsx` | 로그인 |
| `/register` | `app/register/page.tsx` | 회원가입 |
| `/search?q=` | `app/search/page.tsx` | 검색 결과 |
| `/events/[id]` | `app/events/[id]/page.tsx` | 이벤트 상세 |
| `/events/[id]/seats` | `app/events/[id]/seats/page.tsx` | 좌석 선택 |
| `/events/[id]/book` | `app/events/[id]/book/page.tsx` | 스탠딩 예매 |
| `/queue/[eventId]` | `app/queue/[eventId]/page.tsx` | 대기열 |
| `/payment/[reservationId]` | `app/payment/[reservationId]/page.tsx` | 결제 |
| `/payment/success` | `app/payment/success/page.tsx` | 결제 성공 |
| `/payment/fail` | `app/payment/fail/page.tsx` | 결제 실패 |
| `/my-reservations` | `app/my-reservations/page.tsx` | 내 예매 목록 |
| `/reservations/[id]` | `app/reservations/[id]/page.tsx` | 예매 상세 |
| `/transfers` | `app/transfers/page.tsx` | 양도 마켓 |
| `/transfers/my` | `app/transfers/my/page.tsx` | 내 양도 목록 |
| `/transfer-payment/[transferId]` | `app/transfer-payment/[transferId]/page.tsx` | 양도 결제 |
| `/my-memberships` | `app/my-memberships/page.tsx` | 내 멤버십 목록 |
| `/membership-payment/[membershipId]` | `app/membership-payment/[membershipId]/page.tsx` | 멤버십 결제 |
| `/artists` | `app/artists/page.tsx` | 아티스트 목록 |
| `/artists/[id]` | `app/artists/[id]/page.tsx` | 아티스트 상세 |
| `/community` | `app/community/page.tsx` | 커뮤니티 게시판 |
| `/community/write` | `app/community/write/page.tsx` | 게시글 작성 |
| `/community/[postId]` | `app/community/[postId]/page.tsx` | 게시글 상세 |
| `/community/[postId]/edit` | `app/community/[postId]/edit/page.tsx` | 게시글 수정 |
| `/news` | `app/news/page.tsx` | 뉴스 목록 |
| `/news/create` | `app/news/create/page.tsx` | 뉴스 작성 |
| `/news/[id]` | `app/news/[id]/page.tsx` | 뉴스 상세 |
| `/news/[id]/edit` | `app/news/[id]/edit/page.tsx` | 뉴스 수정 |
| `/admin` | `app/admin/page.tsx` | 관리자 대시보드 |
| `/admin/events` | `app/admin/events/page.tsx` | 이벤트 관리 |
| `/admin/events/new` | `app/admin/events/new/page.tsx` | 이벤트 생성 |
| `/admin/events/edit/[id]` | `app/admin/events/edit/[id]/page.tsx` | 이벤트 수정 |
| `/admin/reservations` | `app/admin/reservations/page.tsx` | 예매 관리 |
| `/admin/statistics` | `app/admin/statistics/page.tsx` | 통계 대시보드 |

---

## 3. 주요 페이지별 기능

### 3.1 홈 페이지 (`/`)

이벤트 목록을 필터링 가능한 그리드 형태로 표시한다.

**핵심 기능:**
- 상태 기반 필터 탭: `on_sale`(예매 중), `upcoming`(오픈 예정), `ended`(예매 종료), `cancelled`(취소됨), 전체
- 각 이벤트 카드에 실시간 카운트다운 표시 (판매 종료까지 / 오픈까지)
- 카운트다운 만료 시 자동 필터 전환 (`upcoming` 만료 시 `on_sale`로 전환) 또는 데이터 재요청

```tsx
// 필터 탭과 이벤트 쿼리
const [filter, setFilter] = useState("on_sale");
const params: Record<string, string | number> = { page: 1, limit: 12 };
if (filter) params.status = filter;
const { data: events = [], isLoading, error, refetch } = useEvents(params);
```

> 참조: `apps/web/src/app/page.tsx:10-16` (필터 정의), `apps/web/src/app/page.tsx:114-120` (상태 관리 및 쿼리)

**EventCard 컴포넌트:**
- 포스터 이미지 표시 (없으면 이모지 폴백)
- 상태 배지 (예매 중, 오픈 예정, 종료, 취소, 매진)
- 카운트다운 바: `on_sale`이면 `sale_end_date` 기준, `upcoming`이면 `sale_start_date` 기준
- 가격 범위, 장소, 일시, 아티스트명 표시

> 참조: `apps/web/src/app/page.tsx:43-111` (EventCard 컴포넌트)

### 3.2 이벤트 상세 (`/events/[id]`)

이벤트 상세 정보와 티켓 종류를 표시하고, 예매 진입점을 제공한다.

**핵심 기능:**
- `useEventDetail` 훅으로 이벤트 데이터 조회
- 포스터, 아티스트명, 장소/주소, 공연일시, 판매 기간 표시
- 티켓 종류별 가격과 잔여 수량 표시
- `seat_layout_id` 존재 여부에 따라 예매 버튼 텍스트 결정: "좌석 선택 예매하기" 또는 "바로 예매하기"
- 예매 버튼 클릭 시 `/queue/{eventId}`로 이동 (대기열 진입)
- `upcoming` 상태에서 카운트다운이 만료되면 자동으로 예매 가능 상태로 전환

```tsx
const canBook =
  event.status === "on_sale" ||
  (event.status === "upcoming" && timeLeft.isExpired);
```

> 참조: `apps/web/src/app/events/[id]/event-detail-client.tsx:25-48` (상태 결정 로직), `apps/web/src/app/events/[id]/event-detail-client.tsx:136-143` (예매 버튼)

### 3.3 대기열 (`/queue/[eventId]`)

VWR(Virtual Waiting Room) 대기열 UI를 제공한다.

**핵심 기능:**
- `AuthGuard`로 보호
- `queueApi.check()` 호출로 대기열 진입
- `entryToken`을 쿠키(`urr-entry-token`)에 저장
- 실제 대기 상태(`queued`)인 경우에만 폴링 시작
- 대기 순번, 내 앞/뒤 인원 수, 예상 대기 시간 표시
- `active` 상태 또는 비대기 상태 시 자동 리다이렉트
  - `seatLayoutId`가 있으면 `/events/{id}/seats`로
  - 없으면 `/events/{id}/book`으로
- 리다이렉트 판단을 위해 `eventsApi.detail()`로 이벤트 데이터를 별도 조회하여 클라이언트 측 조작 방지
- "대기열 나가기" 버튼

> 참조: `apps/web/src/app/queue/[eventId]/page.tsx:19-98` (대기열 로직), `apps/web/src/app/queue/[eventId]/page.tsx:76-86` (자동 리다이렉트)

### 3.4 좌석 선택 (`/events/[id]/seats`)

좌석 배치도(시트맵)를 그래픽으로 표시하고 좌석을 선택할 수 있다.

**핵심 기능:**
- `AuthGuard`로 보호
- 진입 시 대기열 상태 확인 (여전히 `queued`면 대기열 페이지로 리다이렉트)
- `seatsApi.byEvent()`로 좌석 목록 조회
- 구역(section)별, 열(row)별 좌석 시각화
- 좌석 상태별 색상 표시:
  - `available`: 선택 가능 (연한 회색)
  - `reserved`: 예매됨 (짙은 회색, 비활성)
  - `locked`: 다른 사용자가 선택 중 (노란색, 비활성)
  - 선택됨: 하늘색 배경
- 1석 단일 선택 방식 (이전 선택 해제 후 새 좌석 선택)
- 좌석 선택 시 하단 sticky 패널에 선택 정보 및 가격 표시
- "예매하기" 클릭 시 `seatsApi.reserve()` 호출, 성공 시 `/payment/{reservationId}`로 이동
- 409 충돌 시 좌석 목록 자동 새로고침

```tsx
const handleBook = async () => {
  // ...
  const res = await seatsApi.reserve({ eventId, seatIds: [selected] });
  const reservationId = res.data?.id ?? res.data?.reservation?.id;
  if (reservationId) {
    router.push(`/payment/${reservationId}`);
  }
};
```

> 참조: `apps/web/src/app/events/[id]/seats/page.tsx:59-69` (좌석 색상 로직), `apps/web/src/app/events/[id]/seats/page.tsx:120-156` (예매 처리)

### 3.5 스탠딩 예매 (`/events/[id]/book`)

좌석 배치도가 없는 이벤트(스탠딩 공연)를 위한 티켓 선택 화면이다.

**핵심 기능:**
- `AuthGuard`로 보호
- `eventsApi.detail()`로 이벤트 및 티켓 종류 조회
- 티켓 종류를 라디오 버튼 형태로 표시 (매진 시 비활성)
- 1매 단일 선택 방식
- 선택 시 하단 sticky 패널에 선택 티켓 정보 및 가격 표시
- `reservationsApi.createTicketOnly()` 호출, `idempotencyKey`(멱등성 키)를 자동 생성하여 중복 요청 방지
- 409 매진 시 이벤트 데이터 재조회 및 선택 초기화

> 참조: `apps/web/src/app/events/[id]/book/page.tsx:55-87` (예매 처리), `apps/web/src/lib/api-client.ts:157-164` (createTicketOnly + 멱등성 키)

### 3.6 결제 (`/payment/[reservationId]`)

예매 건에 대한 결제를 처리한다.

**핵심 기능:**
- `AuthGuard`로 보호
- `reservationsApi.byId()`로 예매 정보 조회 (이벤트명, 예매번호, 좌석, 금액)
- 만료 카운트다운: `expires_at` 기준, 만료 시 `/my-reservations`로 자동 이동
- 1분 미만 남으면 빨간색 긴급 표시 + 펄스 애니메이션
- 결제 수단 4가지: 네이버페이, 카카오페이, 계좌이체, 토스페이먼츠
- 결제 흐름:
  - **토스페이먼츠**: `paymentsApi.prepare()` -> TossPayments SDK 동적 import -> `requestPayment()` -> 성공/실패 콜백 URL로 리다이렉트
  - **기타(네이버페이/카카오페이/계좌이체)**: `paymentsApi.process()`로 서버 측 mock 즉시 처리 -> `/payment/success`로 이동

```tsx
if (method === "toss") {
  const prepRes = await paymentsApi.prepare({ reservationId, amount });
  const { loadTossPayments } = await import("@tosspayments/payment-sdk");
  const tossPayments = await loadTossPayments(clientKey);
  await tossPayments.requestPayment("카드", { amount, orderId, ... });
} else {
  await paymentsApi.process({ reservationId, paymentMethod: method });
  queryClient.invalidateQueries({ queryKey: ["reservations"] });
  router.push("/payment/success");
}
```

> 참조: `apps/web/src/app/payment/[reservationId]/page.tsx:22-27` (결제 수단 정의), `apps/web/src/app/payment/[reservationId]/page.tsx:57-93` (결제 처리 로직)

### 3.7 결제 성공 (`/payment/success`)

결제 완료 후 사용자에게 성공 메시지를 표시한다.

**핵심 기능:**
- TossPayments 콜백 파라미터(`paymentKey`, `orderId`, `amount`)가 있으면 `paymentsApi.confirm()` 호출
- 이미 confirm된 결제(`already confirmed`)는 성공으로 처리 (페이지 새로고침 대응)
- 예매 캐시 무효화: `queryClient.invalidateQueries({ queryKey: ["reservations"] })`
- "내 예매 확인", "홈으로 돌아가기" 링크 제공

> 참조: `apps/web/src/app/payment/success/page.tsx:9-40` (결제 확인 로직)

### 3.8 결제 실패 (`/payment/fail`)

결제 실패 시 에러 메시지와 에러 코드를 표시한다.

- URL 파라미터에서 `code`, `message` 추출하여 표시

> 참조: `apps/web/src/app/payment/fail/page.tsx:7-10`

### 3.9 내 예매 (`/my-reservations`)

현재 사용자의 예매 내역 목록을 표시한다.

**핵심 기능:**
- `AuthGuard`로 보호
- `useMyReservations` 훅 사용
- 예매별 상태 배지: 확정(`confirmed`), 대기(`pending`), 취소(`cancelled`)
- `pending` 상태 예매에 만료 카운트다운 표시, 만료 시 목록 재조회
- 행동 버튼:
  - `pending` + 미만료: "결제하기" (결제 페이지로 이동)
  - `confirmed`: "양도 등록" (양도 마켓에 등록)
  - `pending` 또는 `confirmed`: "취소하기"

> 참조: `apps/web/src/app/my-reservations/page.tsx:36-117` (ReservationRow 컴포넌트), `apps/web/src/app/my-reservations/page.tsx:119-186` (페이지)

### 3.10 양도 마켓 (`/transfers`)

티켓 양도 마켓을 표시하고, 본인 티켓을 양도 등록하거나 다른 사용자의 티켓을 구매할 수 있다.

**핵심 기능:**
- `AuthGuard`로 보호
- 아티스트별 필터링
- "내 티켓 양도하기" 버튼: 확정 예매 목록에서 양도할 티켓 선택 가능
- 양도 목록에 원가, 수수료(%), 총 가격 표시
- 본인 양도 건은 "내 양도" 비활성 표시, 타인 양도 건은 "구매하기" 버튼 (구매 결제 페이지로 이동)

> 참조: `apps/web/src/app/transfers/page.tsx:20-227` (전체 페이지)

### 3.11 멤버십 (`/my-memberships`)

현재 사용자가 가입한 아티스트 멤버십 목록을 표시한다.

**핵심 기능:**
- `AuthGuard`로 보호
- 티어 배지: BRONZE, SILVER, GOLD, DIAMOND (각각 고유 스타일)
- 포인트, 만료일, 활성/만료 상태 표시
- 아티스트 이미지 또는 이니셜 아바타

> 참조: `apps/web/src/app/my-memberships/page.tsx:10-15` (티어 설정), `apps/web/src/app/my-memberships/page.tsx:17-95` (페이지)

### 3.12 멤버십 결제 (`/membership-payment/[membershipId]`)

아티스트 멤버십 구독 결제를 처리한다.

**핵심 기능:**
- `AuthGuard`로 보호
- 가격을 백엔드(`membershipsApi.benefits()`)에서 조회하여 URL 파라미터 조작 방지
- 결제 수단 선택 UI (티켓 결제와 동일한 4가지)
- `paymentType: "membership"` 구분으로 서버에 결제 요청
- 구독 시 Silver(Lv.2) 등급, 선예매 3 접근, 양도 기능, 가입 보너스 200pt 혜택 안내

> 참조: `apps/web/src/app/membership-payment/[membershipId]/page.tsx:36-67` (가격 서버 검증), `apps/web/src/app/membership-payment/[membershipId]/page.tsx:69-110` (결제 처리)

### 3.13 커뮤니티 (`/community`)

아티스트별 커뮤니티 게시판이다.

**핵심 기능:**
- 아티스트 카테고리 바로 필터링 (전체 + 각 아티스트)
- 게시글 목록: 공지 배지, 아티스트 배지, 제목, 댓글 수, 작성자, 조회수, 작성일
- 페이지네이션 (현재 페이지 기준 +-2 페이지 표시)
- 로그인 사용자에게 "글쓰기" 버튼 표시

> 참조: `apps/web/src/app/community/page.tsx:10-194` (전체 페이지)

### 3.14 아티스트 (`/artists`)

등록된 아티스트 목록을 카드 그리드로 표시한다.

**핵심 기능:**
- 아티스트 이미지 (hover 시 scale-up 효과), 이름, 설명, 공연 수, 멤버 수 표시
- 아티스트 상세 페이지로 링크

> 참조: `apps/web/src/app/artists/page.tsx:8-80` (전체 페이지)

### 3.15 검색 (`/search`)

헤더 검색바에서 입력한 키워드로 이벤트를 검색한다.

**핵심 기능:**
- URL 파라미터 `q`에서 검색어 추출
- `eventsApi.list({ q, page: 1, limit: 30 })`으로 서버 측 검색
- Suspense 래핑으로 `useSearchParams()` SSR 호환

> 참조: `apps/web/src/app/search/page.tsx:14-98` (전체 페이지)

### 3.16 관리자 대시보드 (`/admin`)

관리자 전용 대시보드로, 이벤트 관리/예매 관리/통계 페이지로의 진입점을 제공한다.

**핵심 기능:**
- `AuthGuard adminOnly` 속성으로 관리자만 접근 가능
- `adminApi.dashboard()`로 총 이벤트 수, 총 예매 수, 총 매출 조회
- 이벤트 관리, 예매 관리, 통계 카드

> 참조: `apps/web/src/app/admin/page.tsx:8-75` (전체 페이지)

### 3.17 관리자 통계 (`/admin/statistics`)

12개 섹션으로 구성된 심층 분석 대시보드이다.

**핵심 기능:**
- 실시간 현황 (30초 주기 갱신): 잠긴 좌석, 활성 결제, 활성 사용자, 최근 1시간 매출, 인기 이벤트
- 개요: 총 사용자, 진행 중 이벤트, 확정 예매, 총 매출
- 전환 퍼널: 비례 가로 바 (완료/대기/취소), 전환율/취소율/대기율
- 시간대별 트래픽: Recharts BarChart
- 일별 예매 추이 (30일): LineChart (예매/확정)
- 일별 매출 추이 (30일): AreaChart (그라디언트)
- 취소/환불 분석: 총 취소, 평균 취소 시간, 환불 금액, 이벤트별 테이블
- 좌석 선호도: 구역별 PieChart, 가격대별 수평 바
- 사용자 행동: 유형별 PieChart (신규/재방문/충성), 지출 분포, 평균 메트릭스
- 시스템 성능: DB 크기, 성공률, 테이블 카운트
- 이벤트별 통계 테이블: 매출 순 정렬
- 결제 수단: PieChart + 상세 테이블

> 참조: `apps/web/src/app/admin/statistics/page.tsx:62-1116` (전체 페이지), `apps/web/src/app/admin/statistics/page.tsx:6-22` (Recharts 컴포넌트 import)

---

## 4. API 통신 구조

### 4.1 Axios 인스턴스 설정

`http` 인스턴스는 `resolveBaseUrl()` 함수로 환경에 따라 baseURL을 동적으로 결정한다.

```tsx
const http = axios.create({
  baseURL: `${resolveBaseUrl()}/api/v1`,
  headers: { "Content-Type": "application/json" },
  withCredentials: true,
  timeout: 15000,
});
```

> 참조: `apps/web/src/lib/api-client.ts:38-43`

**baseURL 결정 우선순위:**

1. `NEXT_PUBLIC_API_URL` 환경 변수 (빌드 타임 주입)
2. SSR 폴백: `http://localhost:3001`
3. `window.__API_URL` (K8s 런타임 주입)
4. 로컬 개발: `localhost`/`127.0.0.1`이면 `http://localhost:3001`, 사설 IP면 해당 IP의 3001 포트
5. 프로덕션: 빈 문자열 (same-origin, CloudFront/ALB 리버스 프록시)

> 참조: `apps/web/src/lib/api-client.ts:8-36`

### 4.2 요청 인터셉터

모든 요청에 대기열 진입 토큰을 자동으로 헤더에 첨부한다.

```tsx
http.interceptors.request.use((config) => {
  const entryToken = getCookie("urr-entry-token");
  if (entryToken) {
    config.headers["x-queue-entry-token"] = entryToken;
  }
  return config;
});
```

> 참조: `apps/web/src/lib/api-client.ts:70-77`

### 4.3 응답 인터셉터

**401 Silent Refresh (큐잉 방식):**

1. 401 응답 수신 시, 인증 관련 엔드포인트(`/auth/login`, `/auth/register`, `/auth/refresh`)는 즉시 reject
2. 이미 refresh 진행 중이면 요청을 큐(`failedQueue`)에 추가하고 대기
3. refresh 미진행 중이면 `POST /auth/refresh` 호출
4. refresh 성공: 큐의 모든 대기 요청을 재시도, 원래 요청도 재시도
5. refresh 실패: `clearAuth()` 호출 (localStorage 정리), 큐의 모든 대기 요청 reject
6. 리다이렉트는 여기서 하지 않음 (AuthGuard가 담당, 무한 리로드 루프 방지)

> 참조: `apps/web/src/lib/api-client.ts:52-68` (큐잉 구조), `apps/web/src/lib/api-client.ts:79-116` (401 처리)

**429 지수 백오프 재시도:**

최대 2회 재시도, 지수 백오프(`1s -> 2s -> 4s`, 최대 4초).

```tsx
if (error.response?.status === 429) {
  const retryCount = originalRequest._retryCount ?? 0;
  if (retryCount < 2) {
    originalRequest._retryCount = retryCount + 1;
    const delay = Math.min(1000 * Math.pow(2, retryCount), 4000);
    await new Promise((r) => setTimeout(r, delay));
    return http(originalRequest);
  }
}
```

> 참조: `apps/web/src/lib/api-client.ts:118-127`

### 4.4 모듈별 API

| 모듈 | 엔드포인트 예시 | 라인 참조 |
|------|---------------|-----------|
| `authApi` | `/auth/register`, `/auth/login`, `/auth/me`, `/auth/refresh`, `/auth/logout`, `/auth/google` | `api-client.ts:133-141` |
| `eventsApi` | `/events`, `/events/:id` | `api-client.ts:143-146` |
| `queueApi` | `/queue/check/:eventId`, `/queue/status/:eventId`, `/queue/heartbeat/:eventId`, `/queue/leave/:eventId` | `api-client.ts:148-153` |
| `reservationsApi` | `/reservations`, `/reservations/my`, `/reservations/:id`, `/reservations/:id/cancel` | `api-client.ts:155-168` |
| `seatsApi` | `/seats/events/:eventId`, `/seats/reserve` | `api-client.ts:170-177` |
| `paymentsApi` | `/payments/prepare`, `/payments/confirm`, `/payments/process` | `api-client.ts:179-183` |
| `statsApi` | `/stats/overview`, `/stats/daily`, `/stats/events`, `/stats/realtime` 외 12개 | `api-client.ts:185-200` |
| `communityApi` | `/community/posts`, `/community/posts/:id`, `/community/posts/:postId/comments` | `api-client.ts:202-216` |
| `newsApi` | `/news`, `/news/:id` | `api-client.ts:218-226` |
| `artistsApi` | `/artists`, `/artists/:id` | `api-client.ts:228-231` |
| `membershipsApi` | `/memberships/subscribe`, `/memberships/my`, `/memberships/benefits/:artistId` | `api-client.ts:233-238` |
| `transfersApi` | `/transfers`, `/transfers/my`, `/transfers/:id`, `/transfers/:id/cancel` | `api-client.ts:240-246` |
| `adminApi` | `/admin/dashboard`, `/admin/events`, `/admin/reservations` 외 | `api-client.ts:248-270` |

### 4.5 멱등성 키

`reservationsApi.createTicketOnly()`과 `seatsApi.reserve()`에서 `crypto.randomUUID()`를 사용하여 멱등성 키를 자동 생성한다. 클라이언트가 키를 직접 제공할 수도 있지만, 미제공 시 자동 생성된다.

```tsx
// createTicketOnly
idempotencyKey: payload.idempotencyKey ?? crypto.randomUUID(),

// seatsApi.reserve
idempotencyKey: payload.idempotencyKey ?? crypto.randomUUID(),
```

> 참조: `apps/web/src/lib/api-client.ts:163` (createTicketOnly), `apps/web/src/lib/api-client.ts:175` (seatsApi.reserve)

### 4.6 서버 사이드 Fetch 유틸리티

SSR용 서버 측 API 호출 유틸리티. Next.js의 `fetch` `next.revalidate` 옵션으로 캐시 주기를 설정한다 (기본 60초).

```tsx
export async function serverFetch<T>(path: string, options?: { revalidate?: number }): Promise<T | null> {
  const res = await fetch(`${BASE_URL}/api/v1${path}`, {
    next: { revalidate: options?.revalidate ?? 60 },
  });
  // ...
}
```

> 참조: `apps/web/src/lib/server-api.ts:1-14`

---

## 5. 인증 구현

### 5.1 AuthContext / AuthProvider

`AuthProvider`는 React Context를 사용하여 인증 상태를 전역으로 관리한다.

**컨텍스트 값:**

```tsx
interface AuthContextValue {
  user: AuthUser | null;     // 현재 사용자 정보 (null이면 미인증)
  isLoading: boolean;        // 인증 확인 중 여부
  isAuthenticated: boolean;  // user !== null
  refresh: () => void;       // 사용자 정보 재조회
  logout: () => Promise<void>;  // 로그아웃
}
```

> 참조: `apps/web/src/lib/auth-context.tsx:7-13`

**AuthUser 타입:**

```tsx
export type UserRole = "user" | "admin";

export interface AuthUser {
  id: string;
  userId?: string;
  email: string;
  name: string;
  role: UserRole;
}
```

> 참조: `apps/web/src/lib/types.ts:1-9`

**초기화 흐름:**
1. 마운트 시 `authApi.me()` (`GET /auth/me`) 호출
2. 응답에서 사용자 정보 추출 (`res.data.user` 또는 `res.data`)
3. `role`이 "admin"이면 admin, 그 외 "user"
4. 실패 시 `user`를 `null`로 설정 (미인증 상태)

> 참조: `apps/web/src/lib/auth-context.tsx:27-51` (fetchUser)

**로그아웃 흐름:**
1. `authApi.logout()` 호출 (best effort, 실패해도 무시)
2. `user`를 `null`로 설정

> 참조: `apps/web/src/lib/auth-context.tsx:57-64` (logout)

**Provider 트리:**
`Providers` 컴포넌트에서 `QueryClientProvider` > `AuthProvider` 순으로 래핑.

> 참조: `apps/web/src/app/providers.tsx:22-26`

### 5.2 AuthGuard

보호 라우트를 위한 인증 가드 컴포넌트.

```tsx
interface Props {
  children: React.ReactNode;
  adminOnly?: boolean;
}
```

**동작:**
1. `isLoading` 중이거나 `user`가 null이면 로딩 스피너 + "인증 확인 중..." 표시
2. `user`가 null이면 `/login`으로 리다이렉트 (`router.replace`)
3. `adminOnly`가 true이고 `user.role`이 admin이 아니면 `/`로 리다이렉트

> 참조: `apps/web/src/components/auth-guard.tsx:12-43`

### 5.3 로그인

**이메일/패스워드 로그인:**
- `authApi.login({ email, password })` 호출
- 성공 시 `refreshAuth()` (사용자 정보 재조회) + `/`로 이동
- 401 에러: "이메일 또는 비밀번호가 올바르지 않습니다." 메시지

> 참조: `apps/web/src/app/login/page.tsx:19-39` (로그인 폼 제출)

**Google OAuth:**
- `NEXT_PUBLIC_GOOGLE_CLIENT_ID` 환경 변수가 설정되어 있으면 Google Sign-In 버튼 렌더링
- Google GSI 스크립트를 동적 로드하여 `google.accounts.id.initialize()` 및 `renderButton()` 호출
- 콜백에서 `authApi.google(credential)` 호출

> 참조: `apps/web/src/app/login/page.tsx:41-91` (Google OAuth)

### 5.4 회원가입

```tsx
const [form, setForm] = useState({ email: "", password: "", name: "", phone: "" });
```

- 필수: 이름, 이메일, 비밀번호 (8자 이상, `minLength={8}`)
- 선택: 전화번호
- `authApi.register(form)` 호출
- 409: "이미 등록된 이메일입니다."
- 400: "입력 정보를 확인해주세요. (비밀번호 8자 이상)"

> 참조: `apps/web/src/app/register/page.tsx:9-38` (회원가입 로직), `apps/web/src/app/register/page.tsx:77-79` (비밀번호 minLength)

---

## 6. 상태 관리

### 6.1 React Query 설정

`QueryClient`는 `Providers` 컴포넌트에서 단일 인스턴스로 생성한다.

```tsx
const [queryClient] = useState(
  () =>
    new QueryClient({
      defaultOptions: {
        queries: {
          staleTime: 60 * 1000,       // 60초 (1분)
          gcTime: 5 * 60 * 1000,      // 5분 (garbage collection)
          retry: 1,                    // 실패 시 1회 재시도
          refetchOnWindowFocus: false, // 윈도우 포커스 시 재조회 비활성
        },
      },
    }),
);
```

> 참조: `apps/web/src/app/providers.tsx:8-20`

### 6.2 커스텀 훅

**useEvents** -- 이벤트 목록 조회

```tsx
export function useEvents(params?: Record<string, string | number>) {
  return useQuery({
    queryKey: ["events", params],
    queryFn: async () => {
      const res = await eventsApi.list(params);
      return res.data.events ?? res.data.data ?? [];
    },
  });
}
```

응답 데이터에서 `events` 또는 `data` 키를 유연하게 추출한다.

> 참조: `apps/web/src/hooks/use-events.ts:6-14`

**useEventDetail** -- 단일 이벤트 상세 조회

```tsx
export function useEventDetail(id: string | undefined) {
  return useQuery({
    queryKey: ["event", id],
    queryFn: async () => {
      const res = await eventsApi.detail(id!);
      const ev = res.data.event ?? res.data.data ?? res.data ?? null;
      if (ev && !ev.ticketTypes && !ev.ticket_types && res.data.ticketTypes) {
        ev.ticketTypes = res.data.ticketTypes;
      }
      return ev;
    },
    enabled: !!id,
  });
}
```

`enabled: !!id`로 id가 없으면 쿼리를 비활성화한다. `ticketTypes` 필드가 중첩 응답 구조에 있을 경우를 대비하여 수동 병합한다.

> 참조: `apps/web/src/hooks/use-events.ts:16-29`

**useMyReservations** -- 내 예매 목록 조회

```tsx
export function useMyReservations() {
  return useQuery({
    queryKey: ["reservations", "mine"],
    queryFn: async () => {
      const res = await reservationsApi.mine();
      return res.data.reservations ?? res.data.data ?? [];
    },
  });
}
```

> 참조: `apps/web/src/hooks/use-reservations.ts:6-14`

**useQueuePolling** -- 대기열 상태 폴링 (아래 섹션 7에서 상세 설명)

> 참조: `apps/web/src/hooks/use-queue-polling.ts`

**useServerTime** -- 서버 시간 동기화 (아래 섹션 8에서 상세 설명)

> 참조: `apps/web/src/hooks/use-server-time.ts`

**useCountdown** -- 카운트다운 타이머 (아래 섹션 8에서 상세 설명)

> 참조: `apps/web/src/hooks/use-countdown.ts`

---

## 7. VWR (Virtual Waiting Room) 프론트엔드 구현

### 7.1 useQueuePolling 훅

대기열 상태를 주기적으로 폴링하는 커스텀 훅이다.

**폴링 간격:**
- 기본 간격: 3초 (`DEFAULT_POLL_SECONDS`)
- 최소/최대: 1초 ~ 60초 (`MIN_POLL_SECONDS`, `MAX_POLL_SECONDS`)
- 서버 응답의 `nextPoll` 필드로 간격 동적 제어

```tsx
const DEFAULT_POLL_SECONDS = 3;
const MIN_POLL_SECONDS = 1;
const MAX_POLL_SECONDS = 60;

function clampPoll(seconds: number): number {
  return Math.max(MIN_POLL_SECONDS, Math.min(MAX_POLL_SECONDS, seconds));
}
```

> 참조: `apps/web/src/hooks/use-queue-polling.ts:7-13`

**폴링 동작:**
1. `queueApi.status(eventId)` 호출 (`GET /queue/status/:eventId`)
2. 성공: 상태 업데이트, `nextPoll`로 다음 간격 설정
3. `entryToken`이 응답에 포함되면 쿠키에 저장
4. 실패: "Queue status polling failed" 에러 상태 설정
5. 언마운트 시 타이머 정리

```tsx
// entryToken 쿠키 저장
if (data.entryToken) {
  const isSecure = window.location.protocol === 'https:';
  document.cookie = `urr-entry-token=${data.entryToken}; path=/; max-age=600; SameSite=Strict${isSecure ? '; Secure' : ''}`;
}
```

> 참조: `apps/web/src/hooks/use-queue-polling.ts:36-62` (폴링 로직), `apps/web/src/hooks/use-queue-polling.ts:48-51` (쿠키 저장)

**반환값:**

```tsx
return { status, loading, error };
// status: QueueStatus | null
```

> 참조: `apps/web/src/hooks/use-queue-polling.ts:72`

### 7.2 entryToken 쿠키 관리

대기열을 통과한 사용자에게 발급되는 진입 토큰을 쿠키로 관리한다.

| 속성 | 값 |
|------|---|
| 쿠키 이름 | `urr-entry-token` |
| 경로 | `/` |
| 유효 기간 | 600초 (10분) |
| SameSite | `Strict` |
| Secure | HTTPS에서만 설정 |

이 토큰은 요청 인터셉터에서 `x-queue-entry-token` 헤더로 자동 첨부된다.

> 참조: `apps/web/src/hooks/use-queue-polling.ts:48-51` (쿠키 설정), `apps/web/src/lib/api-client.ts:70-77` (인터셉터 첨부)

### 7.3 QueueStatus 인터페이스

```tsx
export interface QueueStatus {
  status?: "queued" | "active" | "not_in_queue";
  queued: boolean;
  position?: number;
  peopleAhead?: number;
  peopleBehind?: number;
  estimatedWait?: number;
  nextPoll?: number;
  currentUsers?: number;
  threshold?: number;
  queueSize?: number;
  entryToken?: string;
  eventInfo?: {
    title?: string;
    artist?: string;
  };
}
```

> 참조: `apps/web/src/lib/types.ts:120-136`

### 7.4 대기열 페이지 UI

**표시 정보:**
- 현재 대기 순번 (큰 숫자)
- 내 앞/뒤 인원 수, 예상 대기 시간 (3칸 그리드)
- 현재 접속자 수 / 최대 수용 인원 (조건부 표시)
- 이벤트 제목, 아티스트명
- 자동 리다이렉트 안내 메시지: "이 페이지를 닫지 마세요. 순서가 되면 자동으로 좌석 선택 페이지/티켓 선택 페이지로 이동합니다."
- "대기열 나가기" 버튼 (`queueApi.leave()` 호출 후 이벤트 상세로 이동)

**자동 리다이렉트 로직:**
- `status === "active"` 또는 `(!queued && status !== "queued")`인 경우
- `seatLayoutId` 유무에 따라 좌석 선택(`/events/{id}/seats`) 또는 스탠딩 예매(`/events/{id}/book`)로 분기

> 참조: `apps/web/src/app/queue/[eventId]/page.tsx:76-86` (리다이렉트 로직), `apps/web/src/app/queue/[eventId]/page.tsx:120-153` (UI 렌더링)

---

## 8. 서버 시간 동기화 & 카운트다운

### 8.1 useServerTime

클라이언트와 서버 간의 시간 차이(offset)를 계산하여 서버 기준 시간을 사용할 수 있게 한다.

**offset 계산 알고리즘:**

```
서버 시간 = serverTime
RTT = after - before
클라이언트 중간점 = before + RTT / 2
offset = serverTime - clientMidpoint
```

1. `GET /api/time` 호출 전후의 클라이언트 시간 측정
2. RTT(Round-Trip Time) 계산
3. 서버 응답 시각에서 RTT/2만큼 보정한 offset 산출
4. offset 캐싱: 한 번 계산되면 이후 호출에서는 캐시된 값 반환
5. 실패 시 offset = 0 (클라이언트 시간 그대로 사용)

```tsx
const before = Date.now();
const res = await fetch(`${resolveBaseUrl()}/api/time`);
const after = Date.now();
const data = await res.json();
const rtt = after - before;
const serverTime = new Date(data.time).getTime();
const clientMidpoint = before + rtt / 2;
cachedOffset = serverTime - clientMidpoint;
```

> 참조: `apps/web/src/hooks/use-server-time.ts:17-39` (fetchOffset)

**캐싱 전략:**
- 모듈 수준 변수 `cachedOffset`에 단일 값 저장
- `fetchPromise`로 동시 호출 방지 (Promise 공유)
- 최초 한 번만 서버 호출, 이후 캐시 반환

> 참조: `apps/web/src/hooks/use-server-time.ts:14-15`

**`getServerNow` 헬퍼:**

```tsx
export function getServerNow(offset: number): number {
  return Date.now() + offset;
}
```

> 참조: `apps/web/src/hooks/use-server-time.ts:55-57`

### 8.2 useCountdown

서버 시간 기준의 카운트다운 타이머 훅이다.

**인터페이스:**

```tsx
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

> 참조: `apps/web/src/hooks/use-countdown.ts:6-14`

**동작:**
1. `useServerTime()`으로 offset 획득
2. 목표 시각(`targetDate`)과 서버 현재 시각의 차이 계산
3. 1초 간격 `setInterval`로 업데이트
4. 만료 감지:
   - 마운트 시 이미 만료 상태면 `onExpire`를 호출하지 않음 (무한 루프 방지)
   - 활성 상태에서 만료로 전환될 때만 `onExpire` 콜백 실행 (100ms 딜레이)
5. `targetDate` 변경 시 만료 상태 초기화

```tsx
if (tl.isExpired && !expiredRef.current) {
  expiredRef.current = true;
  setTimeout(() => onExpireRef.current?.(), 100);
}
```

> 참조: `apps/web/src/hooks/use-countdown.ts:40-91` (useCountdown 훅), `apps/web/src/hooks/use-countdown.ts:62-74` (초기 만료 감지 로직)

**포맷 유틸리티:**

- `formatCountdown(t, showMonths)`: "1개월 2일 3시간 4분 5초" 형식
- `formatCountdownShort(t)`: "MM:SS" 형식 (결제 만료 타이머용)

> 참조: `apps/web/src/hooks/use-countdown.ts:93-104` (formatCountdown), `apps/web/src/hooks/use-countdown.ts:106-110` (formatCountdownShort)

**사용처:**
- 홈 페이지 이벤트 카드: 판매 종료/오픈 카운트다운 (`apps/web/src/app/page.tsx:52`)
- 이벤트 상세: 판매 시작/종료 카운트다운 (`apps/web/src/app/events/[id]/event-detail-client.tsx:44`)
- 결제 페이지: 예매 만료 타이머 (`apps/web/src/app/payment/[reservationId]/page.tsx:53-55`)
- 내 예매 목록: 각 예매의 만료 타이머 (`apps/web/src/app/my-reservations/page.tsx:49`)

---

## 9. 결제 통합

### 9.1 TossPayments SDK 통합

TossPayments 결제는 3단계로 진행된다.

**1단계 - prepare:**
서버에 결제 준비 요청을 보내고 `orderId`, `clientKey`를 받는다.

```tsx
const prepRes = await paymentsApi.prepare({
  reservationId: params.reservationId,
  amount: info?.total_amount,
});
const orderId = prepRes.data?.orderId ?? prepRes.data?.order_id;
const clientKey = prepRes.data?.clientKey ?? prepRes.data?.client_key;
```

> 참조: `apps/web/src/app/payment/[reservationId]/page.tsx:65-70`

**2단계 - requestPayment:**
TossPayments SDK를 동적 import하여 결제 요청.

```tsx
const { loadTossPayments } = await import("@tosspayments/payment-sdk");
const tossPayments = await loadTossPayments(clientKey);
await tossPayments.requestPayment("카드", {
  amount: info?.total_amount ?? 0,
  orderId,
  orderName: info?.event_title ?? "티켓 결제",
  successUrl: `${window.location.origin}/payment/success`,
  failUrl: `${window.location.origin}/payment/fail`,
});
```

> 참조: `apps/web/src/app/payment/[reservationId]/page.tsx:72-80`

**3단계 - confirm:**
결제 성공 페이지에서 Toss 콜백 파라미터를 서버에 전달하여 최종 확인.

```tsx
paymentsApi.confirm({ paymentKey, orderId, amount: Number(amount) })
```

> 참조: `apps/web/src/app/payment/success/page.tsx:27-28`

### 9.2 Mock 결제 (네이버페이, 카카오페이, 계좌이체)

Toss 이외의 결제 수단은 `paymentsApi.process()`로 서버에서 즉시 성공 처리한다.

```tsx
await paymentsApi.process({
  reservationId: params.reservationId,
  paymentMethod: method,  // "naver_pay" | "kakao_pay" | "bank_transfer"
});
queryClient.invalidateQueries({ queryKey: ["reservations"] });
router.push("/payment/success");
```

> 참조: `apps/web/src/app/payment/[reservationId]/page.tsx:82-88`

### 9.3 멤버십 결제

멤버십 결제도 동일한 결제 수단을 사용하지만, 요청 페이로드에 `paymentType: "membership"`과 `referenceId`를 포함한다.

```tsx
// 토스
await paymentsApi.prepare({
  amount: price,
  paymentType: "membership",
  referenceId: params.membershipId,
});

// 기타
await paymentsApi.process({
  paymentMethod: method,
  paymentType: "membership",
  referenceId: params.membershipId,
});
```

> 참조: `apps/web/src/app/membership-payment/[membershipId]/page.tsx:76-98`

---

## 10. 보안 헤더 (미들웨어)

`middleware.ts`는 모든 요청에 보안 헤더를 설정한다 (정적 자산 및 프리페치 제외).

### 10.1 Content Security Policy (CSP)

```
default-src 'self';
script-src 'self' 'unsafe-inline' https://accounts.google.com https://apis.google.com;
style-src 'self' 'unsafe-inline' 'nonce-{nonce}' https://accounts.google.com;
img-src 'self' data: https:;
connect-src 'self' http://localhost:* https://*.urr.guru https://accounts.google.com;
frame-src https://accounts.google.com;
frame-ancestors 'none';
```

- 각 요청마다 `crypto.randomUUID()` 기반 nonce를 생성하여 인라인 스타일을 허용
- `script-src`에 `'unsafe-inline'` 허용 (Next.js RSC payload가 빌드 타임에 생성되어 nonce를 적용할 수 없으므로)
- Google OAuth를 위해 `accounts.google.com` 도메인 허용

> 참조: `apps/web/src/middleware.ts:5-18` (CSP 헤더 구성)

### 10.2 기타 보안 헤더

| 헤더 | 값 | 설명 |
|------|---|------|
| `X-Frame-Options` | `DENY` | 클릭재킹 방지 |
| `X-Content-Type-Options` | `nosniff` | MIME 타입 스니핑 방지 |
| `Referrer-Policy` | `strict-origin-when-cross-origin` | 리퍼러 누출 최소화 |
| `Permissions-Policy` | `camera=(), microphone=(), geolocation=(), payment=()` | 불필요한 브라우저 API 비활성 |

> 참조: `apps/web/src/middleware.ts:24-28`

### 10.3 미들웨어 적용 범위

```tsx
export const config = {
  matcher: [
    {
      source: "/((?!_next/static|_next/image|favicon.ico).*)",
      missing: [{ type: "header", key: "next-router-prefetch" }]
    },
  ],
};
```

- `_next/static`, `_next/image`, `favicon.ico`는 제외
- Next.js 라우터 프리페치 요청도 제외 (`next-router-prefetch` 헤더가 없는 요청에만 적용)

> 참조: `apps/web/src/middleware.ts:33-37`

---

## 11. 컴포넌트 구조

### 11.1 SiteHeader

글로벌 헤더 컴포넌트로, 모든 페이지 상단에 표시된다.

**구성 요소:**
- 로고: "URR" (sky-500 컬러, 번개 아이콘)
- 검색바: `form[role="search"]`로 접근성 마크업, 검색 시 `/search?q=` 라우트로 이동
- 네비게이션 링크:
  - 아티스트 (`/artists`)
  - 커뮤니티 (`/community`)
  - (로그인 시) 내 예매, 내 멤버십, 양도 마켓
  - (관리자 시) 관리자 링크
  - (비로그인 시) 로그인, 회원가입 버튼
- 현재 경로에 따른 활성 링크 하이라이트 (`bg-sky-50 text-sky-600`)
- 로그아웃 버튼

**역할 기반 렌더링:**
```tsx
{user ? (
  <>
    {/* 내 예매, 내 멤버십, 양도 마켓 */}
    {user.role === "admin" && (
      <Link href="/admin">관리자</Link>
    )}
    <span>{user.name}</span>
    <button onClick={logout}>로그아웃</button>
  </>
) : (
  <>
    <Link href="/login">로그인</Link>
    <Link href="/register">회원가입</Link>
  </>
)}
```

> 참조: `apps/web/src/components/site-header.tsx:8-144` (전체 컴포넌트), `apps/web/src/components/site-header.tsx:71-139` (역할 기반 렌더링)

### 11.2 AuthGuard

인증 보호 및 역할 기반 접근 제어 컴포넌트. 섹션 5.2에서 상세히 다루었다.

- `adminOnly` prop으로 관리자 전용 페이지 보호
- 로딩 중 스피너 표시 (접근성: `role="status"`, `aria-label="인증 확인 중"`)

> 참조: `apps/web/src/components/auth-guard.tsx:12-43`

### 11.3 PageCard

재사용 가능한 카드 컨테이너 컴포넌트.

```tsx
interface Props {
  title: string;
  description?: string;
  children?: React.ReactNode;
}

export function PageCard({ title, description, children }: Props) {
  return (
    <section className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
      <h1 className="text-2xl font-semibold tracking-tight text-slate-900">{title}</h1>
      {description ? <p className="mt-2 text-sm text-slate-500">{description}</p> : null}
      {children ? <div className="mt-6">{children}</div> : null}
    </section>
  );
}
```

> 참조: `apps/web/src/components/page-card.tsx:1-15`

---

## 12. 스타일링 & 디자인 시스템

### 12.1 TailwindCSS 유틸리티 우선

모든 스타일링은 TailwindCSS v4 유틸리티 클래스로 처리한다. 별도의 컴포넌트 CSS 파일이나 CSS-in-JS는 사용하지 않는다.

```css
@import "tailwindcss";
```

> 참조: `apps/web/src/app/globals.css:1`

### 12.2 CSS 변수

`globals.css`에서 전역 CSS 변수를 정의하여 일관된 색상 체계를 유지한다.

```css
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

> 참조: `apps/web/src/app/globals.css:3-14`

TailwindCSS의 `@theme inline` 디렉티브로 CSS 변수를 Tailwind 테마에 매핑한다.

```css
@theme inline {
  --color-background: var(--bg);
  --color-foreground: var(--text);
  --font-sans: var(--font-heading);
  --font-mono: var(--font-mono);
}
```

> 참조: `apps/web/src/app/globals.css:16-21`

### 12.3 폰트

| 폰트 | CSS 변수 | 용도 |
|------|---------|------|
| Space Grotesk | `--font-heading` | 제목, 본문 (기본 sans-serif) |
| IBM Plex Mono | `--font-mono` | 코드, 숫자, 예매번호 |

```tsx
const heading = Space_Grotesk({
  variable: "--font-heading",
  subsets: ["latin"],
});

const mono = IBM_Plex_Mono({
  variable: "--font-mono",
  subsets: ["latin"],
  weight: ["400", "500"],
});
```

두 폰트 변수를 `<body>` 태그의 className에 적용하여 전역으로 사용한다.

```tsx
<body className={`${heading.variable} ${mono.variable} bg-slate-50`}>
```

> 참조: `apps/web/src/app/layout.tsx:7-16` (폰트 설정), `apps/web/src/app/layout.tsx:26` (body 적용)

### 12.4 색상 체계

프로젝트 전반에 걸쳐 사용되는 Tailwind 색상 팔레트.

| 역할 | Tailwind 색상 | 주요 사용처 |
|------|-------------|-----------|
| 주 색상 (Primary) | `sky-500` / `sky-600` | 버튼, 링크, 활성 상태, 가격, 배지 |
| 경고 (Warning) | `amber-500` / `amber-600` | 카운트다운, 오픈 예정 배지, 아티스트 태그, 수수료 |
| 중립 (Neutral) | `slate-*` | 배경(`slate-50`), 텍스트(`slate-900`), 테두리(`slate-200`), 뮤트(`slate-400`) |
| 에러 (Error) | `red-500` / `red-50` | 에러 메시지, 취소 배지, 매진 배지, 긴급 타이머 |
| 성공 (Success) | `green-500` / `emerald-500` | 활성 멤버십 상태, 통계 성공 지표 |

**상태 배지 예시 (이벤트):**
```tsx
case "on_sale":   return { text: "예매 중", cls: "bg-sky-50 text-sky-600" };
case "upcoming":  return { text: "오픈 예정", cls: "bg-amber-50 text-amber-600" };
case "ended":     return { text: "종료", cls: "bg-slate-100 text-slate-500" };
case "cancelled": return { text: "취소", cls: "bg-red-50 text-red-500" };
case "sold_out":  return { text: "매진", cls: "bg-red-50 text-red-500" };
```

> 참조: `apps/web/src/app/page.tsx:18-33` (이벤트 상태 배지), `apps/web/src/app/my-reservations/page.tsx:21-34` (예매 상태 배지)

### 12.5 공통 UI 패턴

프로젝트 전반에 반복적으로 사용되는 Tailwind 유틸리티 패턴.

**카드 컨테이너:**
```
rounded-2xl border border-slate-200 bg-white p-6 shadow-sm
```

**스피너 (로딩):**
```
inline-block h-6 w-6 animate-spin rounded-full border-2 border-sky-500 border-t-transparent
```

**입력 필드:**
```
w-full rounded-lg border border-slate-200 bg-slate-50 px-3 py-2.5 text-sm text-slate-900
placeholder:text-slate-400 focus:border-sky-400 focus:outline-none focus:ring-1 focus:ring-sky-400
```

**주 버튼 (Primary):**
```
rounded-lg bg-sky-500 px-4 py-2.5 text-sm font-medium text-white
hover:bg-sky-600 disabled:opacity-50 transition-colors
```

### 12.6 다크 모드

현재 다크 모드는 구현되어 있지 않다. `globals.css`에 다크 모드 관련 미디어 쿼리나 `dark:` 변형은 정의되어 있지 않으며, 모든 색상은 라이트 모드 기준으로만 설정되어 있다.

> 참조: `apps/web/src/app/globals.css:1-45` (다크 모드 관련 정의 부재)

### 12.7 접근성

- `<html lang="ko">`: 한국어 문서 선언 (`apps/web/src/app/layout.tsx:25`)
- 스킵 링크: "본문으로 건너뛰기" (`apps/web/src/app/layout.tsx:28-33`)
- `<main id="main-content" role="main">`: 주 콘텐츠 랜드마크 (`apps/web/src/app/layout.tsx:35`)
- 검색바: `role="search"`, `aria-label="공연 및 아티스트 검색"` (`apps/web/src/components/site-header.tsx:32`)
- 대기열: `role="status"`, `aria-live="polite"` (`apps/web/src/app/queue/[eventId]/page.tsx:121`)
- AuthGuard 로딩: `role="status"`, `aria-label="인증 확인 중"` (`apps/web/src/components/auth-guard.tsx:29`)

### 12.8 포맷 유틸리티

`format.ts`에서 날짜와 가격 관련 유틸리티 함수를 제공한다.

```tsx
// "2025년 3월 15일 (토) 19:00" 형식
export function formatEventDate(d?: string | null): string

// Intl.NumberFormat("ko-KR")으로 쉼표 포맷
export function formatPrice(price: number): string

// toLocaleDateString("ko-KR")
export function formatDate(d?: string): string

// toLocaleString("ko-KR", { dateStyle: "medium", timeStyle: "short" })
export function formatDateTime(d?: string): string
```

> 참조: `apps/web/src/lib/format.ts:1-32`
