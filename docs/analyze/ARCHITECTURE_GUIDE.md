# URR 티켓팅 시스템 — 아키텍처 완전 가이드

이 문서는 URR 티켓팅 시스템의 **모든 AWS 서비스, 연결 방식, 동작 원리**를 처음 보는 사람도 이해할 수 있도록 설명한다.

---

## 목차

1. [시스템 한 줄 요약](#1-시스템-한-줄-요약)
2. [사용자 요청이 처리되는 전체 흐름](#2-사용자-요청이-처리되는-전체-흐름)
3. [Edge Layer — 사용자와 가장 가까운 계층](#3-edge-layer--사용자와-가장-가까운-계층)
4. [VWR (Virtual Waiting Room) — 대기열 시스템](#4-vwr-virtual-waiting-room--대기열-시스템)
5. [VPC — 전체 네트워크 구조](#5-vpc--전체-네트워크-구조)
6. [EKS — 애플리케이션 실행 환경](#6-eks--애플리케이션-실행-환경)
7. [마이크로서비스 — 8개 백엔드 서비스](#7-마이크로서비스--8개-백엔드-서비스)
8. [데이터 계층 — DB, 캐시, 메시징](#8-데이터-계층--db-캐시-메시징)
9. [모니터링 — 시스템 감시](#9-모니터링--시스템-감시)
10. [CI/CD — 코드에서 배포까지](#10-cicd--코드에서-배포까지)
11. [보안 — 6단계 방어](#11-보안--6단계-방어)
12. [전체 연결 요약표](#12-전체-연결-요약표)

---

## 1. 시스템 한 줄 요약

**URR**은 공연/이벤트 티켓을 예매하는 웹 서비스다. 인기 공연 오픈 시 수만 명이 동시 접속하기 때문에, 2단계 대기열(VWR)로 트래픽을 제어하고, AWS 위에서 마이크로서비스 아키텍처로 운영한다.

---

## 2. 사용자 요청이 처리되는 전체 흐름

사용자가 `urr.guru`에 접속해서 티켓을 예매하기까지의 경로:

```
사용자 브라우저
  ↓
① Route 53 (DNS)        — urr.guru → CloudFront IP로 변환
  ↓
② CloudFront (CDN)      — 전 세계 엣지 서버에서 응답
  ↓ (분기)
  ├─ 프론트엔드 요청   → ALB → Frontend Pod (Next.js SSR/CSR)
  ├─ 정적 파일 요청   → S3 Assets 버킷에서 이미지/파일 반환
  ├─ VWR API 요청     → API Gateway → Lambda → DynamoDB
  └─ 백엔드 API 요청  → Lambda@Edge (토큰 검증) → Internet Gateway → ALB
                                                                      ↓
③ ALB (로드 밸런서)     — 요청을 EKS 내 gateway Pod로 전달
  ↓
④ gateway-service       — JWT 인증, Rate Limiting, 요청 라우팅
  ↓
⑤ 개별 서비스           — ticket, payment, auth 등 담당 서비스가 처리
  ↓
⑥ 데이터 계층           — PostgreSQL(영구 저장), Redis(캐시), Kafka(이벤트)
```

---

## 3. Edge Layer — 사용자와 가장 가까운 계층

사용자의 브라우저에서 VPC(내부 네트워크)에 도달하기 전, 가장 먼저 만나는 서비스들이다.

### 3.1 Route 53 (DNS)

| 항목 | 설명 |
|------|------|
| **정체** | AWS의 DNS 서비스 |
| **하는 일** | `urr.guru` 같은 도메인을 입력하면, 실제 서버 주소(CloudFront IP)로 변환해준다 |
| **연결** | 사용자 → Route 53 → CloudFront |
| **비유** | 전화번호부. "김철수"를 검색하면 "010-xxxx-xxxx"를 알려주는 역할 |

### 3.2 CloudFront (CDN)

| 항목 | 설명 |
|------|------|
| **정체** | AWS의 CDN (Content Delivery Network) |
| **하는 일** | 전 세계 수백 개 엣지 서버에 콘텐츠를 캐시해두고, 사용자와 가장 가까운 서버에서 응답한다 |
| **왜 필요** | 서울 리전 서버까지 갈 필요 없이, 가까운 곳에서 빠르게 응답. DDoS 방어 효과도 있음 |

**CloudFront가 요청을 분류하는 방법:**

| 요청 경로 | 보내는 곳 | 설명 |
|-----------|-----------|------|
| `/*` (기본) | **ALB** (→ Frontend Pod) | Next.js 앱 (standalone 모드, SSR + CSR) |
| `/api/*` | **ALB** (→ EKS) | 백엔드 API 호출. Lambda@Edge가 먼저 토큰 검증 |
| `/static/*`, `/_next/static/*` | **S3 Assets** | 이미지, CSS, JS 번들 파일 (장기 캐시) |
| `/vwr-api/*` | **API Gateway** | VWR 대기열 API |
| `/vwr/*` | **S3** | VWR 정적 대기 페이지 (Lambda@Edge 폴백용, 주 경로는 모달) |

**연결 관계:**
- Route 53 → **CloudFront** → ALB → Frontend Pod (Next.js 앱)
- Route 53 → **CloudFront** → S3 Assets (정적 파일)
- Route 53 → **CloudFront** → Lambda@Edge → Internet Gateway → ALB (API)
- Route 53 → **CloudFront** → API Gateway (VWR API)

### 3.3 WAF (Web Application Firewall)

| 항목 | 설명 |
|------|------|
| **정체** | AWS의 웹 방화벽 |
| **하는 일** | CloudFront에 붙어서 악성 요청(SQL Injection, XSS 등)을 차단한다 |
| **연결** | CloudFront에 **부착**(Attached)되어 동작. 별도 서버가 아님 |
| **비유** | 건물 입구 보안 검색대. 위험물을 소지한 사람은 들어오지 못하게 함 |

**적용 규칙 4개** (우선순위 순):

| 규칙 | 하는 일 |
|------|---------|
| Rate Limit | 같은 IP에서 5분 안에 2,000건 넘으면 차단 (DDoS/봇 방어) |
| Common Rule Set | 웹 공격 공통 패턴 차단 (OWASP Top 10) |
| Known Bad Inputs | 알려진 악성 입력 차단 (예: Log4j 취약점) |
| SQL Injection | SQL 삽입 공격 탐지 및 차단 |

- 규칙에 걸리지 않으면 정상 통과 (기본 Allow)
- 모든 차단/허용 기록이 CloudWatch에 남음

### 3.4 Lambda@Edge (토큰 검증 — 대기열 우회 방지)

| 항목 | 설명 |
|------|------|
| **정체** | CloudFront 엣지 서버에서 실행되는 작은 함수 |
| **하는 일** | `/api/*` 요청이 들어오면, 사용자가 대기열을 정상적으로 통과했는지 **토큰(입장 증표)**을 확인한다 |
| **왜 필요** | 대기열을 건너뛰고 직접 API를 호출하는 것을 방지 |
| **연결** | CloudFront → **Lambda@Edge** → Internet Gateway |
| **비유** | 놀이공원 입구에서 줄을 서서 받은 팔찌를 확인하는 직원 |

> **토큰이란?** 사용자가 대기열을 정상적으로 통과했다는 "디지털 입장 증표"다. 쿠키(브라우저에 자동 저장되는 작은 데이터)에 담겨 있으며, 위조 방지를 위해 암호화 서명이 되어 있다.

**2단계 토큰 검증:**

Lambda@Edge는 `/api/*` 요청이 오면, 순서대로 두 가지 팔찌를 확인한다:

```
요청 도착 (예: /api/v1/seats/reserve)
  ↓
① Tier 1 검증: "사이트에 입장했나?"
   → urr-vwr-token 쿠키 확인
   ├─ 없거나 위조됨 → /vwr/{eventId}로 이동시킴 (S3 정적 대기 페이지)
   └─ 유효함 → 다음 단계로 ↓
  ↓
② Tier 2 검증: "예매 차례가 됐나?" (좌석/예매/결제 API만 해당)
   → urr-entry-token 쿠키 확인
   ├─ 없거나 위조됨 → /queue/{eventId}로 이동시킴 (대기열 페이지)
   └─ 유효함 → 요청 통과 ✓ (ALB로 전달)
```

> **리다이렉트(redirect)란?** 브라우저가 A 페이지에 접속했는데, 서버가 "B 페이지로 가세요"라고 자동으로 돌려보내는 것이다. 사용자는 화면이 잠깐 전환되면서 다른 페이지로 이동한다.

**검증 면제 경로** — 아래 경로들은 토큰 없이 누구나 접근 가능:

| 경로 | 이유 |
|------|------|
| `/api/v1/events/*` | 이벤트 목록/상세 조회 — 대기 전에 볼 수 있어야 하므로 |
| `/api/v1/auth/*` | 로그인/회원가입 — 대기 전에 인증할 수 있어야 하므로 |
| `/api/v1/queue/*` | 대기열 자체 API — 대기 중에 호출하는 것이므로 |
| `/api/v1/stats/*` | 통계 조회 — 누구나 볼 수 있으므로 |
| `/vwr/*`, `/vwr-api/*` | VWR 대기 페이지/API — 대기열에 진입하기 위한 것이므로 |

**비유로 정리:**
```
놀이공원 비유:
  → 입장 줄(VWR)을 서서 팔찌(urr-vwr-token)를 받아야 공원에 들어감
  → 인기 놀이기구 줄(Queue)을 서서 탑승권(urr-entry-token)을 받아야 탈 수 있음
  → 팔찌 없이 놀이기구에 타려고 하면? → 입장 줄로 돌려보냄 (리다이렉트)
  → 하지만 매점이나 화장실(events, auth, stats)은 팔찌 없이도 이용 가능
```

### 3.5 Internet Gateway (IGW)

| 항목 | 설명 |
|------|------|
| **정체** | VPC와 인터넷을 연결하는 관문 |
| **하는 일** | VPC 밖에서 들어오는 트래픽을 VPC 안으로 전달하고, VPC 안에서 나가는 트래픽을 인터넷으로 내보낸다 |
| **연결** | Lambda@Edge → **IGW** → ALB |
| **비유** | 아파트 단지 정문. 외부 차량이 들어오려면 반드시 정문을 통과해야 함 |

### 3.6 Frontend Pod (Next.js)

| 항목 | 설명 |
|------|------|
| **정체** | Next.js 앱을 standalone 모드로 빌드하여 실행하는 EKS Pod |
| **하는 일** | 사용자가 페이지에 접속하면, Next.js 서버가 HTML을 만들어 내려준다 (SSR 3개 + CSR 31개 페이지) |
| **연결** | CloudFront → ALB → **Frontend Pod** (:3000) |
| **포트** | 3000 |
| **레플리카** | 2개 (동일한 앱이 2개 동시에 실행, 하나가 죽어도 나머지가 서비스) |

> **SSR과 CSR이란?**
> - **SSR(Server-Side Rendering)**: 서버에서 HTML을 완성해서 보내주는 방식. 사용자가 페이지를 열면 바로 내용이 보인다. 검색엔진에도 유리.
> - **CSR(Client-Side Rendering)**: 빈 HTML을 보내고, 브라우저의 JavaScript가 화면을 그리는 방식. 첫 로딩 후에는 페이지 전환이 빠르다.
> - URR에서는 메인/이벤트 상세 등 3개 페이지가 SSR, 나머지 31개(예매/결제/마이페이지 등)는 CSR이다.

### 3.7 S3 Assets (정적 파일)

| 항목 | 설명 |
|------|------|
| **정체** | 이미지, CSS, JS 번들 등 정적 리소스를 저장하는 S3 버킷 |
| **하는 일** | 공연 포스터 이미지, 폰트, 아이콘 등을 제공한다 |
| **연결** | CloudFront → **S3 Assets** (OAC 인증) |
| **Frontend Pod와의 차이** | Frontend Pod는 Next.js 앱 서버 (동적 렌더링 가능), Assets는 정적 미디어/리소스 파일 |

> **OAC(Origin Access Control)란?** S3 버킷은 기본적으로 비공개다. OAC는 "CloudFront만 이 버킷에 접근할 수 있다"라고 설정하는 것이다. 사용자가 S3 URL을 직접 입력해도 접근할 수 없고, 반드시 CloudFront를 거쳐야 한다.

**S3에 저장되는 파일 종류:**

| 경로 | 내용 | 설명 |
|------|------|------|
| `/_next/static/*` | JS/CSS 번들 | Next.js가 빌드할 때 생성하는 파일. 1년 캐시 |
| `/vwr/index.html` | VWR 정적 대기 페이지 | Lambda@Edge 폴백용 (자세한 설명은 4.4절 참고) |
| 기타 | 이미지, 폰트, 아이콘 | 공연 포스터 등 정적 미디어 파일 |

---

## 4. VWR (Virtual Waiting Room) — 대기열 시스템

인기 공연 오픈 시 수만 명이 동시에 몰리면 서버가 다운될 수 있다. VWR은 **줄을 세워서 순서대로 입장**시키는 시스템이다. 2단계로 구성된다.

### 4.0 사용자가 경험하는 전체 예매 흐름

실제 사용자 입장에서 티켓을 예매하는 과정:

```
① 이벤트 페이지 접속
   사용자가 보고 싶은 공연의 상세 페이지에 들어간다.
   공연 정보, 티켓 종류, 가격 등을 확인한다.

② "예매하기" 버튼 클릭
   버튼을 누르는 순간, 시스템이 "지금 사람이 많은지" 확인한다.
   ├─ 사람이 별로 없으면 → ③으로 바로 이동 (대기 없음)
   └─ 사람이 많으면 (VWR 활성) → 모달(팝업) 대기 화면이 뜬다

   [VWR 모달 대기 화면]
   ┌─────────────────────────────────┐
   │        접속 대기 중              │
   │  현재 접속자가 많아 순서대로     │
   │  안내하고 있습니다               │
   │                                 │
   │     현재 대기 순번               │
   │        2,341번                  │
   │                                 │
   │  ████████░░░░  내 앞 1,841명    │
   │  예상 대기: 약 37초              │
   │                                 │
   │  이 창을 닫지 마세요.            │
   │  순서가 되면 자동으로 이동합니다. │
   │                                 │
   │      [ 대기열 나가기 ]           │
   └─────────────────────────────────┘

   → 순서가 오면 자동으로 모달이 닫히고 다음 단계로 이동

③ Tier 2 대기열 (예매 순서 대기)
   서버에 접속은 했지만, 좌석 선택 페이지에 동시에 들어갈 수 있는 인원이
   제한되어 있다. 여기서 한 번 더 줄을 선다.
   ┌─────────────────────────────────┐
   │         대기열 안내               │
   │  현재 대기 순번: 47번            │
   │  내 앞: 46명  내 뒤: 128명       │
   │  예상 대기: 약 1분               │
   └─────────────────────────────────┘

   → 순서가 오면 자동으로 다음 페이지로 이동

④ 좌석 선택 또는 티켓 선택
   ├─ 좌석 지정 공연 → 좌석 배치도에서 원하는 좌석을 선택
   └─ 스탠딩 공연 → 원하는 티켓 종류와 수량을 선택

⑤ 결제
   선택한 좌석/티켓의 결제를 진행한다.

⑥ 예매 완료
```

### 4.1 Tier 1 — VWR 모달 대기열 (서버리스)

사용자가 "예매하기"를 누르면, 이벤트 페이지 위에 **모달(팝업)**이 뜨면서 대기열에 자동 진입한다. 이 대기열은 서버(Pod)에 부하를 주지 않고, **서버리스**(API Gateway + Lambda + DynamoDB)로 동작한다.

> **서버리스란?** 별도의 서버를 관리하지 않아도 요청이 올 때마다 AWS가 자동으로 실행해주는 방식. 수만 명이 동시에 몰려도 자동으로 확장되며, 비용은 사용한 만큼만 발생한다.

**구성 요소:**

| 서비스 | 역할 | 비유 |
|--------|------|------|
| **API Gateway (VWR API)** | 대기열 API 입구 | 번호표 발급 창구 |
| **Lambda VWR API** | 대기 번호 발급/조회 실행 | 번호표 발급 직원 |
| **Lambda Counter Advancer** | 10초마다 "500명 더 입장 허용" (1분 6사이클) | 줄 앞에서 "다음 500분 들어오세요!" 하는 안내 직원 |
| **DynamoDB (counters)** | 이벤트별 현재 입장 허용 번호 저장 | 전광판 ("현재 3,000번까지 입장") |
| **DynamoDB (positions)** | 각 사용자의 대기 번호 저장 | 번호표 장부 |

**모달에서 일어나는 일 (사용자는 대기 화면만 보면 된다):**
```
① "예매하기" 클릭 → 모달이 뜸
   → 브라우저가 VWR API에 "줄 서기" 요청
   → DynamoDB에서 대기 번호 발급 (예: 2,341번)
   → 모달에 "2,341번" 표시

② 모달이 자동으로 "내 순서 확인" 반복 (2~15초마다)
   → VWR API에 "내 번호 왔나요?" 확인
   → "현재 입장 허용: 3,000번" vs "내 번호: 2,341번"
   → 아직이면 → 대기 계속
   → 내 번호가 됐으면 → 입장! (urr-vwr-token 쿠키 발급)

③ 입장 허가 → 모달이 닫히고 Tier 2 대기열 페이지로 자동 이동

(백그라운드에서 1분마다)
   → Lambda가 자동으로 "입장 허용 번호"를 10초마다 500씩 올림 (1분에 6사이클)
   → 1분에 3,000명, 1시간에 180,000명 처리 가능
```

**왜 모달인가?**
- 사용자가 이미 이벤트 페이지를 보고 있으므로, 별도 페이지 이동 없이 모달(팝업)로 대기 → UX가 매끄러움
- 모달 안의 폴링은 브라우저 JS가 서버리스 API를 직접 호출 → **백엔드 Pod에 부하 0**
- S3 정적 대기 페이지(`/vwr/*`)는 Lambda@Edge 폴백용으로 인프라에 남아 있음

**연결:**
- 브라우저 모달 → CloudFront `/vwr-api/*` → API Gateway → Lambda VWR API → DynamoDB
- EventBridge(1분 타이머) → Lambda Counter Advancer (내부 6사이클, 10초 간격) → DynamoDB
- Lambda와 DynamoDB 모두 VPC 밖 AWS 리전 서비스이므로, VPC Endpoint 없이 AWS 네트워크 내에서 직접 통신

### 4.2 Tier 2 — 서비스 레벨 대기열 (Redis)

Tier 1을 통과한 사용자가 실제 좌석 선택/예매 페이지에 들어가기 전 한 번 더 줄을 세운다. 이번에는 Redis를 사용.

> **왜 Tier 1을 통과했는데 또 줄을 서나?** Tier 1은 "사이트에 들어올 수 있는 사람"을 제한하는 것이고, Tier 2는 "좌석 선택 페이지에 동시에 있을 수 있는 사람"을 제한하는 것이다. 놀이공원에 비유하면, Tier 1은 놀이공원 입장 줄, Tier 2는 인기 놀이기구 줄이다.

**구성 요소:**

| 서비스 | 역할 | 비유 |
|--------|------|------|
| **queue-service** (EKS Pod) | Redis 기반 대기열 관리 | 놀이기구 앞 줄 관리 직원 |
| **Redis** | 대기열 상태 저장 | 줄 서 있는 사람 명단 |
| **gateway-service** | entry token 검증 | 놀이기구 팔찌 확인 직원 |

**흐름:**
```
Tier 1 통과한 사용자 (urr-vwr-token 쿠키 보유)
  → /queue/{eventId} 페이지에서 대기
  → queue-service가 Redis에서 대기 순서 관리
  → 내 순서가 오면 → x-queue-entry-token 발급 (입장 팔찌)
  → 이 토큰으로 좌석 선택/예매 API 호출 가능
```

### 4.3 폴백 경로 — S3 정적 대기 페이지

위 4.0~4.1에서 설명한 **모달(팝업) 방식**이 정상 경로다. 하지만 모달은 이벤트 상세 페이지 안에서만 동작하기 때문에, 사용자가 이벤트 페이지를 거치지 않고 직접 API에 접근하면 모달이 뜰 수 없다. 이런 상황을 위해 **S3 정적 대기 페이지**가 폴백(비상용 대안)으로 존재한다.

> **폴백(fallback)이란?** "주 경로가 안 될 때 사용하는 대안 경로"를 뜻한다. 엘리베이터가 고장났을 때 비상 계단을 사용하는 것과 같다.

**정상 경로 vs 폴백 경로 비교:**

| | 정상 경로 (대부분의 경우) | 폴백 경로 (예외 상황) |
|---|---|---|
| **시작** | "예매하기" 버튼 클릭 | URL 직접 입력, 북마크, 쿠키 만료 등 |
| **대기 화면** | 이벤트 페이지 위에 **모달(팝업)** | 별도의 **S3 정적 HTML 페이지** |
| **대기 방식** | 동일 — 브라우저가 VWR API를 반복 호출 | 동일 — 페이지 JS가 VWR API를 반복 호출 |
| **입장 후** | 모달 닫힘 → `/queue/` 자동 이동 | 쿠키 저장 → 원래 가려던 페이지로 자동 이동 |
| **사용자 경험** | 매끄러움 (페이지 전환 없음) | 페이지 전환 2회 발생 |

**폴백이 발동되는 구체적 상황:**

사용자가 정상적으로 "예매하기" 버튼을 눌러서 VWR 모달을 거치면 `urr-vwr-token` 쿠키를 받는다. 하지만 아래 상황에서는 쿠키 없이 API에 접근하게 된다:

- 예매 URL(`/api/v1/seats/...`)을 **주소창에 직접 복사해서 붙여넣기**한 경우
- 예매 도중 **브라우저를 완전히 껐다가 다시 접속**한 경우 (쿠키가 만료됨)
- 친구에게 받은 예매 **링크를 다른 기기에서 열었을** 경우
- SNS나 외부 사이트에서 예매 페이지 **링크를 타고 직접 접속**한 경우

**폴백이 동작하는 과정:**

```
① 사용자가 쿠키 없이 /api/v1/seats/abc-123 같은 API에 접근
  ↓
② Lambda@Edge가 urr-vwr-token 쿠키가 없는 것을 감지
  ↓
③ 302 리다이렉트: 브라우저를 /vwr/abc-123 으로 자동 이동시킴
  ↓
④ CloudFront가 /vwr/abc-123 요청을 받음
   → CloudFront Function이 경로를 /vwr/index.html로 변환
   → S3에서 정적 HTML 파일을 가져와 사용자에게 전달
  ↓
⑤ S3 정적 대기 페이지가 브라우저에 표시됨
   → 화면에 대기 순번, 예상 대기 시간 등 표시 (모달과 동일한 UI)
   → 자동으로 /vwr-api/vwr/assign/{eventId} 호출 → 대기 번호 발급
   → 2~15초마다 /vwr-api/vwr/check/ 호출 → 내 순서 확인
  ↓
⑥ 순서가 오면 urr-vwr-token 쿠키 저장 → 원래 가려던 페이지로 자동 이동
```

> **CloudFront Function이란?** Lambda@Edge보다 더 가볍고 빠른 함수다. 여기서는 `/vwr/abc-123`처럼 이벤트 ID가 포함된 URL을 `/vwr/index.html`(실제 파일)로 변환하는 단순한 역할만 한다. S3에는 `abc-123`이라는 파일이 없고 `index.html`만 있기 때문에 이 변환이 필요하다.

**비유:**
정상 경로(모달)는 정문에서 안내를 받으며 줄 서는 것이고, 폴백 경로(S3 페이지)는 뒷문으로 갔더니 "정문으로 가세요"라고 안내받아 거기서 줄을 서는 것이다. 어디로 가든 결국 줄을 서야 입장 가능하다.

### 4.4 왜 2단계인가?

| 단계 | 비유 | 목적 | 기술 |
|------|------|------|------|
| **Tier 1 (VWR)** | 놀이공원 입장 줄 | 서버에 트래픽이 몰리는 것 자체를 방지. 수만 명을 서버 없이 대기시킴 | DynamoDB + Lambda (서버리스, 무제한 확장) |
| **Tier 2 (Queue)** | 인기 놀이기구 줄 | 서버에 들어온 사람 중 좌석 선택 페이지 동시 접속자를 제한 | Redis (빠른 읽기/쓰기) |

**왜 토큰도 2개인가?**

각 단계를 통과할 때마다 별도의 JWT 토큰이 발급된다 (`urr-vwr-token`, `urr-entry-token`). 하나의 토큰에 tier 값을 넣어 업그레이드하는 방식도 가능하지만, 2개를 분리한 이유:

- **만료 독립성**: VWR 토큰은 10분, 예매 토큰도 10분이지만 독립적으로 설정 가능하다. 예매 시간이 초과되면 예매 대기열(Tier 2)만 다시 서면 되고, VWR(Tier 1)은 다시 안 서도 된다. 토큰이 1개면 만료 시 VWR부터 다시 서야 한다.
- **시스템 분리**: VWR(서버리스)과 Queue(Redis)는 완전히 독립된 시스템이다. 토큰도 분리하면 시크릿, 발급 로직, 장애 범위가 서로 영향을 주지 않는다.
- **보안**: 하나가 탈취되어도 나머지는 안전하다. 시크릿 키를 다르게 설정할 수 있다.

**토큰 & TTL 타임라인:**

| 항목 | TTL | 설정 위치 |
|------|-----|-----------|
| Tier 1 VWR 토큰 (`urr-vwr-token`) | 10분 | `lambda/vwr-api/lib/token.js` (`exp: now + 600`) |
| Tier 2 Entry 토큰 (`urr-entry-token`) | 10분 | `queue-service` (`queue.entry-token.ttl-seconds:600`) |
| Redis 활성 유저 TTL | 10분 | `queue-service` (`QUEUE_ACTIVE_TTL_SECONDS:600`) |
| Redis 좌석 락 | 7분 | `ticket-service` (`seat-lock.ttl-seconds:420`) |
| DynamoDB 대기열 레코드 | 24시간 | `lambda/vwr-api/lib/dynamo.js` (`ttl: now + 86400`) |
| 쿠키 max-age (프론트) | 10분 | `vwr-modal.tsx`, `queue/page.tsx` (`max-age=600`) |

```
0분   VWR 입장 → Tier 1 토큰 발급 (10분 유효)
~1분  Queue 입장 → Tier 2 Entry 토큰 발급 (10분 유효)
~2분  좌석 선택 → Redis 좌석 락 (7분 유효)
~5분  결제 완료
10분  토큰 만료 → 이후 보호 API 접근 불가
```

---

## 5. VPC — 전체 네트워크 구조

VPC(Virtual Private Cloud)는 AWS 안에 만든 **가상의 사설 네트워크**다. 모든 서버와 데이터베이스가 이 안에 들어간다.

### 5.1 VPC 기본 정보

| 항목 | 값 |
|------|-----|
| CIDR | `10.0.0.0/16` (IP 65,536개) |
| 리전 | ap-northeast-2 (서울) |
| 가용 영역 | 2개 (AZ-A, AZ-B) |

**왜 2개 AZ?**: 한쪽 데이터센터에 장애가 나도 반대쪽에서 서비스를 유지하기 위해.

### 5.2 서브넷 구성 (5종류 × 2AZ = 10개)

서브넷은 VPC를 더 작은 네트워크로 나눈 것이다. 보안 수준에 따라 5종류로 나뉜다.

#### Public Subnet (공개)

| 항목 | 값 |
|------|-----|
| AZ-A | `10.0.0.0/24` |
| AZ-B | `10.0.1.0/24` |
| 인터넷 | **직접 연결** (IGW 경유) |
| 배치된 것 | NAT Gateway, ALB ENI |

인터넷에서 직접 접근 가능한 유일한 서브넷. ALB가 여기에 ENI(네트워크 인터페이스)를 두고 외부 트래픽을 받는다.

**NAT Gateway:**

| 항목 | 설명 |
|------|------|
| **정체** | Network Address Translation 게이트웨이 |
| **하는 일** | Private 서브넷의 서버가 인터넷에 **나가는 것만** 허용한다. 외부에서 들어오는 것은 차단 |
| **왜 필요** | EKS 워커 노드가 ECR에서 이미지를 다운로드하거나, 외부 API를 호출할 때 필요 |
| **배치** | AZ마다 1개씩, 총 2개 (AZ-A 장애 시 AZ-B의 NAT는 정상 동작) |
| **비유** | 아파트 단지의 후문. 주민(서버)은 밖에 나갈 수 있지만, 외부인은 후문으로 들어올 수 없음 |

**ALB ENI:**

| 항목 | 설명 |
|------|------|
| **정체** | ALB가 Public 서브넷에 만드는 네트워크 인터페이스 |
| **하는 일** | ALB는 관리형 서비스라 직접 서브넷에 "서버"로 존재하지 않지만, ENI를 통해 각 AZ의 Public 서브넷에 발을 걸쳐 놓는다 |
| **왜 필요** | ALB가 양쪽 AZ에서 트래픽을 받아서 Private 서브넷의 Pod로 전달하기 위해 |

#### Private App Subnet (NAT 경유)

| 항목 | 값 |
|------|-----|
| AZ-A | `10.0.10.0/24` |
| AZ-B | `10.0.11.0/24` |
| 인터넷 | NAT Gateway 경유 (아웃바운드만) |
| 배치된 것 | EKS Worker Node, 모든 서비스 Pod, 모니터링 Pod, RDS Proxy ENI, VPC Endpoint(Interface) |

**핵심 서브넷.** 모든 백엔드 서비스가 여기서 실행된다.

#### Private DB Subnet (격리)

| 항목 | 값 |
|------|-----|
| AZ-A | `10.0.20.0/24` |
| AZ-B | `10.0.21.0/24` |
| 인터넷 | **완전 차단** (인바운드/아웃바운드 모두) |
| 배치된 것 | RDS Primary, RDS Standby, RDS Read Replica |

인터넷 경로가 아예 없다. 해커가 DB 서버를 장악해도 외부로 데이터를 빼낼 수 없다.

#### Private Cache Subnet (격리)

| 항목 | 값 |
|------|-----|
| AZ-A | `10.0.30.0/24` |
| AZ-B | `10.0.31.0/24` |
| 인터넷 | **완전 차단** |
| 배치된 것 | ElastiCache Redis Primary, Redis Replica |

DB 서브넷과 마찬가지로 인터넷 경로 없음. Redis에 저장된 세션/대기열 데이터 보호.

#### Private Streaming Subnet (NAT 경유)

| 항목 | 값 |
|------|-----|
| AZ-A | `10.0.40.0/24` |
| AZ-B | `10.0.41.0/24` |
| 인터넷 | NAT Gateway 경유 (아웃바운드만) |
| 배치된 것 | MSK Broker ENI (양쪽 AZ), SQS FIFO, Lambda Ticket Worker |

Kafka 브로커의 네트워크 인터페이스가 양쪽 AZ에 배치된다. SQS와 Lambda는 리전 서비스이지만, Lambda가 VPC 내 RDS에 접근하기 위해 이 서브넷에 ENI를 생성한다.

### 5.3 라우트 테이블 (네트워크 경로)

서브넷 유형별로 트래픽이 어디로 가는지 정해진 규칙표가 있다.

**Public 서브넷:**

| 목적지 | 대상 | 의미 |
|--------|------|------|
| `10.0.0.0/16` | local | VPC 내부 통신은 직접 |
| `0.0.0.0/0` | Internet Gateway | 그 외 모든 트래픽은 인터넷으로 |

**Private (NAT) 서브넷** (App, Streaming):

| 목적지 | 대상 | 의미 |
|--------|------|------|
| `10.0.0.0/16` | local | VPC 내부 통신은 직접 |
| `0.0.0.0/0` | NAT Gateway | 외부로 나갈 때는 NAT 경유 (응답은 돌아옴) |

**Private (격리) 서브넷** (DB, Cache):

| 목적지 | 대상 | 의미 |
|--------|------|------|
| `10.0.0.0/16` | local | VPC 내부 통신만 가능 |
| *(0.0.0.0/0 없음)* | - | 외부 통신 경로 자체가 없음 |

### 5.4 VPC Endpoints — AWS 서비스에 대한 전용 통로

EKS에서 ECR(컨테이너 이미지 저장소)이나 CloudWatch(로그)에 접근할 때, 인터넷(NAT Gateway)을 경유하면 비용이 발생하고 느리다. VPC Endpoint는 AWS 서비스에 대한 **사설 전용 통로**를 만들어 NAT를 거치지 않고 직접 연결한다.

**종류 2가지:**

| 종류 | 개수 | 비용 | 위치 |
|------|------|------|------|
| **Gateway Endpoint** | 1개 | **무료** | 라우트 테이블에 경로 추가 |
| **Interface Endpoint** | 9개 | 유료 (시간당) | App 서브넷에 ENI 생성 |

**Gateway Endpoint (1개, 무료):**

| 서비스 | 왜 필요 |
|--------|---------|
| **S3** | Pod에서 S3 파일 접근 (로그 저장, 파일 업로드 등) |

**Interface Endpoints (9개):**

| 서비스 | 왜 필요 |
|--------|---------|
| **ECR API** | 컨테이너 이미지 메타데이터 조회 |
| **ECR DKR** | 컨테이너 이미지 다운로드 (Docker pull) |
| **EC2** | EKS 노드 관리 (Karpenter가 노드 생성/삭제 시) |
| **EKS** | Worker Node ↔ EKS Control Plane API 통신 |
| **STS** | IRSA 토큰 발급 (아래 설명 참고) |
| **CloudWatch Logs** | 컨테이너 로그 전송 |
| **Secrets Manager** | RDS Proxy 인증 정보, 각종 시크릿 조회 |
| **ELB** | ALB 제어 (Target Group 업데이트 등) |
| **Auto Scaling** | EKS 노드 그룹 크기 조정 |

> **IRSA(IAM Roles for Service Accounts)란?** "이 Pod는 S3에만 접근 가능하고, 저 Pod는 DynamoDB에만 접근 가능하다"처럼, Pod별로 AWS 서비스 접근 권한을 다르게 부여하는 방식이다. STS를 통해 임시 자격증명을 받아 사용한다.

---

## 6. EKS — 애플리케이션 실행 환경

EKS(Elastic Kubernetes Service)는 컨테이너화된 애플리케이션을 실행하고 관리하는 플랫폼이다.

> **컨테이너와 Pod란?**
> - **컨테이너**: 앱과 실행에 필요한 모든 것(코드, 라이브러리, 설정)을 하나의 패키지로 묶은 것. "이 컨테이너만 있으면 어떤 컴퓨터에서든 동일하게 실행된다"는 것이 핵심.
> - **Pod**: Kubernetes에서 컨테이너를 실행하는 가장 작은 단위. 보통 Pod 1개 = 컨테이너 1개. "Pod가 3개"라는 것은 같은 앱이 3개의 복사본으로 동시에 실행된다는 뜻이다.

### 6.1 EKS Control Plane (컨트롤 플레인)

| 항목 | 설명 |
|------|------|
| **정체** | Kubernetes의 "두뇌". Pod 스케줄링, 상태 관리, API 서버 등을 담당 |
| **하는 일** | "이 Pod를 어느 노드에 배치할까?", "Pod가 죽었으니 새로 만들자" 등의 결정 |
| **위치** | AWS 관리형 (VPC 밖, 하지만 VPC Endpoint로 프라이빗 접근) |
| **접근 제한** | **Public + Private** — VPC 내부에서 VPC Endpoint로 접근하고, 운영자는 인터넷에서도 접근 가능 (IAM 인증 필수) |

**왜 Public + Private인가?**

EKS API 서버 엔드포인트에 접근하는 경로가 2가지다:

| 접근 주체 | 경로 | 이유 |
|-----------|------|------|
| Worker Node | **Private** (VPC Endpoint) | VPC 내부에서 직접 통신. NAT 비용 없음, 빠름 |
| 운영자 kubectl | **Public** (인터넷) | VPN/Bastion 없이 어디서든 장애 대응 가능 |
| GitHub Actions CI/CD | **Public** (인터넷) | 별도 VPN 설정 없이 클러스터 접근 가능 |

Private Only로 설정하면 `kubectl`을 치기 위해 VPN 서버나 Bastion EC2를 별도로 운영해야 한다. 소규모 팀에서는 관리 부담이 크고, 장애 시 VPN/Bastion이 같이 죽으면 클러스터 접근 자체가 불가능해진다.

Public Endpoint가 열려 있어도 **IAM 인증 + K8s RBAC**이 적용되므로 AWS 자격증명 없이는 API 호출 자체가 거부된다. 필요 시 `public_access_cidrs`로 운영팀 IP만 허용하여 추가 강화할 수 있다.

**연결:**
- Worker Node ↔ EKS Control Plane (VPC Endpoint 경유, Private)
- 운영자 kubectl → EKS Control Plane (인터넷 경유, Public, IAM 인증)
- Karpenter → EKS Control Plane (노드 부족 시 알림 수신)
- ArgoCD → EKS Control Plane (배포 매니페스트 적용)

### 6.2 Worker Nodes (워커 노드)

| 항목 | 설명 |
|------|------|
| **정체** | 실제로 Pod(컨테이너)가 실행되는 EC2 인스턴스 |
| **위치** | Private App Subnet (양쪽 AZ에 분산) |
| **초기 노드** | t3.medium × 2~5대 (ON_DEMAND) |
| **추가 노드** | Karpenter가 필요 시 자동 생성 (t3/m5 계열, on-demand + spot 혼합) |

**다이어그램에서**: 양쪽 AZ에 "EKS Worker Nodes" 박스가 있고, 그 안에 각 서비스 Pod가 배치되어 있다.

### 6.3 Karpenter (노드 자동확장)

| 항목 | 설명 |
|------|------|
| **정체** | Kubernetes 노드 오토스케일러 |
| **하는 일** | Pod를 배치할 노드가 부족하면 자동으로 EC2를 생성하고, Pod가 줄어들면 빈 노드를 삭제한다 |
| **위치** | EKS Worker Node 안에서 Pod로 실행 (양쪽 AZ) |

**동작 원리:**
```
1. HPA가 트래픽 증가 감지 → Pod 수를 3→6으로 늘리려 함
2. 기존 노드에 자리가 없음 → Pod가 "Pending" 상태
3. Karpenter가 EKS API Server에서 Pending Pod 감지
4. Karpenter → EC2 API 호출 → 새 노드(t3.large) 자동 생성
5. 새 노드에 Pod 배치 → 서비스 정상화
6. 트래픽 감소 → Pod 수 줄어듦 → 빈 노드 60초 후 자동 삭제
```

**연결:** Karpenter Pod → EKS Control Plane (API Server 감시)

### 6.4 ALB (Application Load Balancer)

| 항목 | 설명 |
|------|------|
| **정체** | L7(HTTP) 로드 밸런서 |
| **하는 일** | 외부에서 들어온 요청을 경로에 따라 gateway-service 또는 Frontend Pod로 분배한다 |
| **위치** | Internet-facing (Public 서브넷에 ENI) |

**Target Group 2개:**

| Target Group | 포트 | 대상 | 라우팅 경로 |
|-------------|------|------|------------|
| gateway-service | :3001 | API 라우터 Pod | CloudFront `/api/*` 경유 |
| frontend | :3000 | Next.js Pod | CloudFront `/*` 기본 경유 |

**연결:**
- Internet Gateway → **ALB** → gateway-service Pod (`:3001`) — API 요청
- Internet Gateway → **ALB** → Frontend Pod (`:3000`) — 페이지 요청
- CloudFront에서만 접근 허용 (Prefix List로 제한)

**동작:**
```
HTTPS :443 요청 수신
  → SSL 종료 (TLS 1.2/1.3)
  → Health Check로 정상 Pod만 선택
  → 경로별 Target Group으로 전달 (IP 타겟 모드)
```

---

## 7. 마이크로서비스 — 8개 백엔드 서비스

모든 서비스는 Spring Boot로 작성되어 EKS Pod로 실행된다. gateway-service가 모든 요청을 받아서 적절한 서비스로 라우팅한다.

### 7.1 gateway-service (API 게이트웨이)

| 항목 | 값 |
|------|-----|
| **포트** | 3001 |
| **레플리카** | 3개 (HPA: 3~10) |
| **역할** | 모든 외부 요청의 **단일 진입점**. API 라우팅, 인증, 보안 필터 |

> **JWT(JSON Web Token)란?** 로그인하면 서버가 발급해주는 "신분증" 같은 것이다. 이후 API를 호출할 때마다 이 JWT를 함께 보내면, 서버가 "이 사람은 로그인한 김철수다"라고 확인한다. 쿠키에 저장되어 브라우저가 자동으로 보낸다.

> **HPA(Horizontal Pod Autoscaler)란?** 트래픽이 많아지면 Pod 수를 자동으로 늘리고, 적어지면 줄이는 장치. 예: "평소 3개 → 트래픽 폭주 시 10개까지 자동 증가". "3~10"은 최소 3개, 최대 10개라는 뜻이다.

> **Rate Limiting이란?** "같은 사용자가 너무 빠르게 요청을 보내면 잠시 막는 것". 예: 로그인 API는 1분에 60번까지만 허용. 자동화 봇이나 악의적인 반복 요청을 방지한다.

**하는 일 (필터 체인, 실행 순서):**
1. API 버전 헤더를 추가한다 (ApiVersionFilter, Order -10)
2. Cookie에서 JWT 토큰을 추출한다 (CookieAuthFilter, Order -2)
3. JWT를 검증하고 사용자 정보를 헤더에 추가한다 (JwtAuthFilter, Order -1)
4. IP/사용자별 요청 횟수를 제한한다 (RateLimitFilter, Order 0, Redis 사용)
5. 좌석/예매 API는 Entry Token을 검증한다 (VwrEntryTokenFilter, Order 1)
6. 적절한 백엔드 서비스로 요청을 전달한다

**Rate Limit 설정 (분당 요청 수):**

| 카테고리 | RPM | 대상 경로 |
|----------|-----|-----------|
| AUTH | 60 | `/api/auth/**` |
| QUEUE | 120 | `/api/queue/**` |
| BOOKING | 30 | `/api/v1/seats/reserve`, `/api/v1/reservations`, `/api/reservations/*/cancel` |
| GENERAL | 3,000 | 그 외 모든 API |

**연결:**
- ALB → **gateway** → auth, ticket, payment, stats, queue, catalog, community
- **gateway** ↔ Redis (Rate Limiting, 토큰 검증용)

### 7.2 auth-service (인증)

| 항목 | 값 |
|------|-----|
| **포트** | 3005 |
| **레플리카** | 2개 (HPA: 2~6) |
| **역할** | 회원가입, 로그인, OAuth, JWT 토큰 발급/검증 |

**하는 일:**
- 이메일/비밀번호 로그인 처리
- OAuth (소셜 로그인) 처리
- JWT Access/Refresh 토큰 발급
- 사용자 정보 관리

**연결:**
- gateway → **auth**
- catalog → **auth** (이벤트 생성자 정보 조회)
- **auth** → PostgreSQL (auth_db)

### 7.3 ticket-service (티켓/예매)

| 항목 | 값 |
|------|-----|
| **포트** | 3002 |
| **레플리카** | 3개 (HPA: 3~10) |
| **역할** | 이벤트 관리, 좌석 관리, 예매, 양도 |

**하는 일:**
- 이벤트/공연 CRUD
- 좌석 잠금 (Redis로 동시 선택 방지, 420초 TTL)
- 예매 생성/취소
- 티켓 양도 처리

**연결:**
- gateway → **ticket**
- **ticket** → payment (REST, PaymentInternalClient — 결제 관련 내부 조회)
- **ticket** → PostgreSQL (ticket_db, RDS Proxy 경유)
- **ticket** → Redis (좌석 잠금: `seat:{eventId}:{seatId}`, 420초 TTL)
- **ticket** → Kafka (reservation-events, transfer-events, membership-events 발행)
- Kafka → **ticket** (payment-events 수신 — 결제 완료/실패 시 예매 확정/취소)

### 7.4 payment-service (결제)

| 항목 | 값 |
|------|-----|
| **포트** | 3003 |
| **레플리카** | 2개 (HPA: 2~6) |
| **역할** | 결제 처리, 결제 상태 관리 |

**하는 일:**
- 결제 요청 처리 (PG 연동)
- 결제 완료/실패 처리
- 환불 처리

**연결:**
- gateway → **payment**
- **payment** → PostgreSQL (payment_db, RDS Proxy 경유)
- **payment** → Kafka (payment-events 발행 — 결제 완료/실패 결과 알림)
- *(Kafka 수신 없음 — payment-service는 Consumer가 없다)*

### 7.5 stats-service (통계)

| 항목 | 값 |
|------|-----|
| **포트** | 3004 |
| **레플리카** | 1개 (HPA: 1~4) |
| **역할** | 이벤트 통계 집계, Kafka 이벤트 소비 |

**하는 일:**
- 예매/결제/양도 이벤트를 Kafka에서 수신하여 통계 저장
- 인기 이벤트, 매출 집계 등

**연결:**
- gateway → **stats**
- Kafka → **stats** (payment-events, reservation-events, transfer-events, membership-events — 4개 토픽 모두 수신)
- **stats** → PostgreSQL (stats_db, RDS Proxy 경유)

### 7.6 queue-service (Tier 2 대기열)

| 항목 | 값 |
|------|-----|
| **포트** | 3007 |
| **레플리카** | 3개 (HPA: 3~8) |
| **역할** | Tier 2 Redis 기반 대기열 관리 |

**하는 일:**
- 사용자의 대기열 진입/퇴장 처리
- 현재 대기 상태 조회 (몇 번째인지)
- 입장 허용 시 entry-token 발급

**연결:**
- gateway → **queue**
- **queue** → Redis (9개 키: queue, active, seen, counter, maxUsers, status, active-seen, threshold + queue:active-events)
- **queue** → SQS FIFO (티켓 이벤트 발행)

### 7.7 catalog-service (카탈로그)

| 항목 | 값 |
|------|-----|
| **포트** | 3009 |
| **레플리카** | 2개 (HPA: 2~6) |
| **역할** | 이벤트/아티스트 **읽기 전용** 조회 |

**하는 일:**
- 이벤트 목록 조회 (검색, 필터링)
- 아티스트 정보 조회
- 전용 catalog_db 사용

**연결:**
- gateway → **catalog**
- queue → **catalog** (대기열에서 이벤트 정보 필요 시)
- **catalog** → auth (이벤트 생성자 정보)
- **catalog** → PostgreSQL (catalog_db, RDS Proxy 경유)

### 7.8 community-service (커뮤니티)

| 항목 | 값 |
|------|-----|
| **포트** | 3008 |
| **레플리카** | 1개 (HPA: 1~4) |
| **역할** | 커뮤니티 게시판, 리뷰 |

**하는 일:**
- 게시글 CRUD
- 댓글, 리뷰 관리

**연결:**
- gateway → **community**
- **community** → ticket (REST, TicketInternalClient — 이벤트/티켓 정보 조회)
- **community** → PostgreSQL (community_db, RDS Proxy 경유)

### 7.9 서비스 간 호출 요약

```
ALB → gateway (유일한 외부 진입점)
         ├→ auth        (인증)
         ├→ ticket      (예매/좌석)
         ├→ payment     (결제)
         ├→ stats       (통계)
         ├→ queue       (대기열)
         ├→ catalog     (카탈로그)
         └→ community   (커뮤니티)

서비스 간 REST 호출:
  catalog   → auth   (이벤트 생성자 정보 조회)
  ticket    → payment (PaymentInternalClient — 결제 내부 조회)
  community → ticket  (TicketInternalClient — 이벤트/티켓 정보 조회)

Kafka 비동기 메시징:
  ticket  → reservation-events, transfer-events, membership-events 발행
  payment → payment-events 발행
  ticket  ← payment-events 수신 (결제 결과로 예매 확정/취소)
  stats   ← 4개 토픽 모두 수신 (통계 집계)

queue → SQS → Lambda (티켓 이벤트 비동기 처리)
```

### urr-common 공유 라이브러리

`services-spring/urr-common/` — 6개 서비스(catalog, community, payment, queue, stats, ticket)가 Gradle Composite Build(`includeBuild '../urr-common'`)로 의존하는 공유 라이브러리.

**제공 컴포넌트:**
- `JwtTokenParser` (`@Component`) — X-User-* 헤더에서 사용자 컨텍스트 추출, Admin 권한 검증
- `GlobalExceptionHandler` (`@RestControllerAdvice`) — 범용 예외 처리 (ResponseStatusException, MethodArgumentNotValidException, 기타)
- `InternalTokenValidatorAutoConfiguration` (`@ConditionalOnProperty(name = "INTERNAL_API_TOKEN")`) — 서비스 간 내부 토큰 검증 자동 등록
- `DataSourceRoutingConfig` (`@ConditionalOnProperty(name = "spring.datasource.url")`) — Primary/Replica 읽기/쓰기 분리 라우팅
- `AuthUser` (record) — 인증된 사용자 정보 값 객체
- `PreSaleSchedule` — 멤버십 티어별 선예매 일정 계산 유틸리티

**미사용 서비스:**
- auth-service: 자체 InternalTokenValidator, GlobalExceptionHandler 보유 (API가 다름)
- gateway-service: Spring Cloud Gateway 아키텍처, JWT 생성이 아닌 헤더 주입 역할

---

## 8. 데이터 계층 — DB, 캐시, 메시징

### 8.1 RDS PostgreSQL (관계형 데이터베이스)

**모든 영구 데이터**가 여기에 저장된다.

| 항목 | 값 |
|------|-----|
| **엔진** | PostgreSQL 16.4 |
| **인스턴스** | db.t3.medium |
| **스토리지** | 50GB (gp3) |
| **위치** | Private DB Subnet (격리) |

**단일 RDS 인스턴스에 6개 데이터베이스:**

> **왜 RDS 1대인가?** MSA에서는 서비스별 DB를 물리적으로 다른 서버에 두는 경우도 있지만, 현재 구조에서는 **VWR(Tier 1)이 백엔드로 들어오는 트래픽 자체를 제어**하기 때문에 RDS에 급격한 부하가 발생하지 않는다. Multi-AZ Failover(장애 시 자동 전환)와 RDS Proxy(커넥션 관리)도 적용되어 있어 1대로 충분하다. 각 서비스의 DB는 논리적으로 완전히 분리되어 있으므로, 규모가 커지거나 결제 데이터 격리 같은 규제 요건이 생기면 payment_db 등을 별도 RDS 인스턴스로 분리할 수 있다.

| DB 이름 | 사용 서비스 | 저장 데이터 |
|---------|------------|-------------|
| `ticket_db` | ticket | 이벤트, 공연, 좌석, 예매 |
| `catalog_db` | catalog | 카탈로그 전용 (이벤트/아티스트 읽기 최적화) |
| `auth_db` | auth | 사용자, 인증 정보 |
| `payment_db` | payment | 결제 기록 |
| `stats_db` | stats | 통계 집계 데이터 |
| `community_db` | community | 게시글, 리뷰 |

**3개의 RDS 인스턴스:**

| 인스턴스 | 위치 | 역할 |
|----------|------|------|
| **Primary** | AZ-A (db-subnet-a) | 읽기 + 쓰기. 모든 서비스가 여기에 쿼리 |
| **Standby** | AZ-B (db-subnet-c) | Primary의 **동기 복제본**. 평소에는 쿼리 불가. Primary 장애 시 자동으로 Primary 승격 (Multi-AZ Failover) |
| **Read Replica** | AZ-A (db-subnet-a) | Primary의 **비동기 복제본**. 읽기 전용 쿼리 가능. stats, catalog 같은 읽기 위주 서비스가 여기에 쿼리하면 Primary 부하 감소 |

**연결:**
- Primary → Standby : **동기 복제** (Sync Replication) — 데이터 손실 없음
- Primary → Read Replica : **비동기 복제** (Async Replication) — 약간의 지연 허용

#### RDS Proxy (커넥션 풀링)

| 항목 | 설명 |
|------|------|
| **정체** | RDS 앞에 위치하는 커넥션 풀 관리자 |
| **하는 일** | Pod가 수십 개인데 각각 DB 연결을 열면 커넥션이 폭발한다. RDS Proxy가 커넥션을 모아서 재사용한다 |
| **위치** | VPC 내부 관리형 서비스 (App Subnet 양쪽 AZ에 ENI 배치) |
| **인증** | Secrets Manager에서 DB 인증 정보를 가져옴 |

**연결:**
- 모든 서비스 Pod → **RDS Proxy** → RDS Primary
- Lambda Ticket Worker → **RDS Proxy** → RDS Primary

### 8.2 ElastiCache Redis (인메모리 캐시)

**빠른 읽기/쓰기가 필요한 임시 데이터**를 저장한다.

| 항목 | 값 |
|------|-----|
| **엔진** | Redis 7.1 |
| **노드 타입** | cache.t4g.medium |
| **위치** | Private Cache Subnet (격리) |
| **보안** | TLS 암호화 + AUTH 토큰 |

**2개의 Redis 노드:**

| 노드 | 위치 | 역할 |
|------|------|------|
| **Primary** | AZ-A (cache-subnet-a) | 읽기 + 쓰기 |
| **Replica** | AZ-B (cache-subnet-c) | Primary의 복제본. 읽기 가능. Primary 장애 시 자동 승격 (Auto-Failover) |

**연결:**
- Primary → Replica : **복제** (Replication)

**Redis에 저장되는 데이터:**

| 키 패턴 | 용도 | 사용 서비스 |
|---------|------|------------|
| `{eventId}:queue` | Tier 2 대기열 목록 | queue |
| `{eventId}:active` | 현재 활성 사용자 목록 | queue |
| `{eventId}:seen` | 이미 들어온 사용자 추적 | queue |
| `seat:{eventId}:{seatId}` | 좌석 잠금 (420초 TTL) | ticket |
| `seat:{eventId}:{seatId}:token_seq` | 좌석 잠금 펜싱 토큰 카운터 | ticket |
| `rate:{category}:{clientId}` | 요청 제한 카운터 | gateway |
| `{eventId}:counter` | 대기열 카운터 | queue |
| `{eventId}:maxUsers` | 최대 동시 입장 수 | queue |
| `{eventId}:status` | 대기열 상태 | queue |
| `{eventId}:active-seen` | 활성 사용자 확인 추적 | queue |
| `{eventId}:threshold` | 대기열 임계값 | queue |
| `queue:active-events` | 현재 활성 이벤트 목록 | queue |

### 8.3 Amazon MSK (Kafka — 이벤트 스트리밍)

서비스 간 **비동기 메시지**를 전달하는 이벤트 브로커.

| 항목 | 값 |
|------|-----|
| **정체** | AWS 관리형 Apache Kafka |
| **Kafka 버전** | 3.6.0 |
| **브로커** | 2대 (AZ-A, AZ-B에 각 1대) |
| **인스턴스** | kafka.t3.small |
| **보안** | TLS + IAM 인증 |
| **위치** | Private Streaming Subnet |

**왜 Kafka인가?**: payment-service가 "결제 완료/실패"를 발행하면, ticket-service가 이를 수신해서 예매를 확정/취소하는 구조. 서비스가 직접 HTTP로 호출하면 한쪽이 다운됐을 때 메시지가 유실된다. Kafka는 메시지를 디스크에 저장하므로 수신자가 잠시 다운되어도 메시지가 보존된다.

> **비동기 메시지란?** 일반적인 API 호출(동기)은 "전화"와 같다 — 상대방이 받을 때까지 기다려야 한다. 비동기 메시지는 "문자 메시지"와 같다 — 보내놓으면 상대방이 나중에 읽어도 된다. Kafka가 문자 메시지를 보관하는 통신사 역할을 한다.

**Kafka 토픽 4개:**

| 토픽 | 발행자 | 수신자 | 내용 |
|------|--------|--------|------|
| `payment-events` | payment | ticket, stats | "결제 완료/실패됐습니다" |
| `reservation-events` | ticket | stats | "예매가 생성/변경됐습니다" |
| `transfer-events` | ticket | stats | "양도가 발생했습니다" |
| `membership-events` | ticket | stats | "멤버십 변경됐습니다" |

**MSK Broker ENI:**
Kafka 브로커는 관리형 서비스이지만, VPC 내 Streaming Subnet에 ENI(네트워크 인터페이스)를 배치하여 서비스 Pod가 접근할 수 있게 한다. 양쪽 AZ에 각 1개씩 ENI가 있다.

**연결:**
- ticket-service → **MSK** (reservation-events, transfer-events, membership-events 발행)
- payment-service → **MSK** (payment-events 발행)
- **MSK** → ticket-service (payment-events 수신)
- **MSK** → stats-service (4개 토픽 모두 수신)

### 8.4 SQS FIFO (메시지 큐)

| 항목 | 설명 |
|------|------|
| **정체** | AWS 관리형 메시지 큐 (FIFO = 순서 보장) |
| **큐 이름** | `urr-prod-ticket-events.fifo` |
| **하는 일** | queue-service가 보낸 티켓 이벤트를 순서대로 처리 |
| **위치** | 리전 서비스 (특정 서브넷에 속하지 않음. 다이어그램에서는 시각적으로 Streaming Subnet에 표시) |

**DLQ (Dead Letter Queue):**
- 이름: `urr-prod-ticket-events-dlq.fifo`
- 처리 실패한 메시지가 3회 재시도 후에도 실패하면 DLQ로 이동
- 14일간 보관 (나중에 수동으로 확인/재처리)

**연결:**
- queue-service → **SQS FIFO** → Lambda Ticket Worker → RDS Proxy → RDS

### 8.5 Lambda Ticket Worker (SQS 처리기)

| 항목 | 설명 |
|------|------|
| **정체** | SQS에서 메시지를 꺼내 처리하는 Lambda 함수 |
| **런타임** | Node.js 20.x |
| **하는 일** | SQS에서 티켓 이벤트를 꺼내서 DB에 반영 |
| **위치** | 리전 서비스이지만, RDS에 접근하기 위해 Streaming Subnet에 VPC ENI 생성 |

**연결:**
- SQS FIFO → **Lambda Ticket Worker** → RDS Proxy → RDS Primary

### 8.6 Secrets Manager

| 항목 | 설명 |
|------|------|
| **정체** | AWS의 비밀 정보 저장소 |
| **하는 일** | DB 비밀번호, API 키, 인증 토큰 등을 안전하게 저장하고, 필요한 서비스에만 제공 |
| **연결** | RDS Proxy → **Secrets Manager** (DB 인증 정보 조회) |
| **접근 방식** | VPC Endpoint(Interface)를 통해 접근 |

### 8.7 DynamoDB (VWR 전용)

| 항목 | 설명 |
|------|------|
| **정체** | AWS의 NoSQL 데이터베이스 (서버리스) |
| **하는 일** | VWR Tier 1 대기열의 번호표 데이터 저장 |
| **위치** | 리전 서비스 (VPC 밖). Lambda도 VPC 밖에서 직접 접근 (VPC Endpoint 불필요) |

**2개 테이블:**

| 테이블 | 역할 |
|--------|------|
| **counters** | 이벤트별 현재 입장 허용 번호 (servingCounter)와 다음 발급 번호 (nextPosition) |
| **positions** | 각 사용자의 대기 번호. 24시간 후 자동 삭제 |

**연결:**
- Lambda VWR API → **DynamoDB** (Lambda는 VPC 밖이므로 AWS 네트워크 내에서 직접 접근)
- Lambda Counter Advancer → **DynamoDB**
- API Gateway → Lambda → **DynamoDB**

---

## 9. 모니터링 — 시스템 감시

3개의 모니터링 계층으로 구성된다.

### 9.1 EKS 내부 모니터링 (Monitoring Namespace)

EKS 클러스터 안에서 Pod로 실행되는 모니터링 스택. 양쪽 AZ의 App Subnet에 배치된다.

| Pod | 역할 |
|-----|------|
| **Prometheus** | 메트릭 수집기. 모든 Pod에서 CPU/메모리/요청수 등을 주기적으로 수집한다 |
| **Grafana** | 대시보드. Prometheus에서 수집한 메트릭을 시각화한다 |
| **Loki** | 로그 수집기. 모든 Pod의 로그를 수집하여 검색 가능하게 한다 |
| **Zipkin** | 분산 추적. 요청이 gateway→ticket→payment 순서로 흐르는 경로를 추적한다 |
| **AlertManager** | 알림 관리. Prometheus에서 임계값 초과 시 Slack/이메일로 알림을 보낸다 |

### 9.2 AMP + AMG (외부 모니터링)

EKS가 장애나면 EKS 내부 Prometheus/Grafana도 함께 중단된다. 이를 대비해 메트릭을 외부로 복제한다.

| 서비스 | 역할 |
|--------|------|
| **AMP (Amazon Managed Prometheus)** | EKS 내부 Prometheus가 `remote_write`로 메트릭을 전송하는 외부 저장소 |
| **AMG (Amazon Managed Grafana)** | AMP에 저장된 메트릭을 시각화하는 외부 대시보드 |

**연결:**
- EKS Prometheus → (remote_write, SigV4 인증) → **AMP** → **AMG**

**핵심 가치**: EKS가 완전히 다운되어도, 장애 발생 직전까지의 메트릭이 AMP에 보존되어 AMG에서 확인 가능.

### 9.3 CloudWatch (AWS 서비스 모니터링)

| 항목 | 설명 |
|------|------|
| **정체** | AWS 자체 모니터링 서비스 |
| **감시 대상** | RDS, ElastiCache, MSK, Lambda, SQS 등 **AWS 관리형 서비스** |
| **AMP/AMG와의 차이** | AMP/AMG는 EKS Pod 메트릭, CloudWatch는 AWS 인프라 메트릭 |

**CloudWatch가 감시하는 것들:**

| 대상 | 감시 항목 |
|------|-----------|
| RDS | CPU, 커넥션 수, 슬로우 쿼리 |
| ElastiCache | 메모리, 히트율, 복제 지연 |
| MSK | 컨트롤러 상태, 오프라인 파티션 |
| Lambda | 에러 횟수, 실행 시간, 스로틀링 |
| SQS DLQ | 실패 메시지 수 |
| EKS Control Plane | API 서버 로그 (audit, authenticator 등) |

**알림 경로:**
- CloudWatch Alarm → SNS Topic → 이메일/Slack

### 9.4 AMG에서 CloudWatch도 조회

AMG는 AMP의 메트릭뿐만 아니라 CloudWatch의 AWS 서비스 메트릭도 데이터소스로 추가할 수 있다. 따라서 AMG 하나에서 Pod 메트릭 + AWS 인프라 메트릭을 **통합 조회** 가능.

```
EKS Prometheus ──(remote_write)──→ AMP ──→ AMG (Pod 메트릭)
CloudWatch ────────────────────────────────→ AMG (AWS 인프라 메트릭)
```

---

## 10. CI/CD — 코드에서 배포까지

### 10.1 GitHub Actions (CI — 빌드 & 테스트)

| 항목 | 설명 |
|------|------|
| **정체** | GitHub의 CI/CD 파이프라인 |
| **위치** | GitHub 클라우드 (AWS 밖) |
| **하는 일** | 코드가 main에 머지되면, 자동으로 빌드/테스트/이미지 빌드를 수행 |

**파이프라인 흐름:**
```
① 개발자가 코드를 main 브랜치에 머지
  ↓
② GitHub Actions 트리거
  ├─ Unit Test (JDK 21 + Gradle)
  ├─ Integration Test
  ├─ Docker Build (arm64 이미지)
  ├─ Trivy 보안 스캔 (CRITICAL/HIGH 취약점)
  └─ ECR에 이미지 Push (3개 태그: {SHA}-{날짜시간}, latest, {환경명})
  ↓
③ K8s 매니페스트의 이미지 태그를 업데이트 → Git에 커밋
  ↓
④ ArgoCD가 Git 변경 감지 → 배포 시작
```

**연결:**
- GitHub Actions → **ECR** (CI Build & Push — 빌드된 이미지 저장)

### 10.2 ECR (Elastic Container Registry)

| 항목 | 설명 |
|------|------|
| **정체** | AWS의 Docker 이미지 저장소 |
| **하는 일** | 서비스별 Docker 이미지를 버전별로 저장한다 |
| **위치** | 리전 서비스 (VPC 밖) |

**연결:**
- GitHub Actions → **ECR** (이미지 Push)
- EKS Worker Nodes ← **ECR** (이미지 Pull — Pod 시작 시 이미지 다운로드)

Worker Node가 ECR에서 이미지를 Pull하는 경로:
```
Worker Node → VPC Endpoint (ECR API + ECR DKR) → ECR
```
VPC Endpoint를 통해 인터넷(NAT)을 거치지 않고 직접 접근.

### 10.3 ArgoCD (CD — GitOps 배포)

| 항목 | 설명 |
|------|------|
| **정체** | GitOps 기반 CD(Continuous Deployment) 도구 |
| **하는 일** | Git 저장소의 K8s 매니페스트를 감시하다가, 변경이 감지되면 EKS에 자동 배포한다 |
| **위치** | EKS 밖 (별도 관리) |

**핵심 개념 — GitOps:**
- "Git이 진실의 원천(Single Source of Truth)"
- 배포하고 싶은 상태를 Git의 YAML 파일로 정의
- ArgoCD가 Git의 YAML과 실제 EKS 상태를 비교
- 차이가 있으면 자동으로 EKS를 Git 상태로 맞춤

**ArgoCD는 ECR과 직접 연결되지 않는다.** ArgoCD는 Git의 매니페스트만 감시한다. CI가 ECR에 이미지를 Push한 후, Git의 매니페스트에 새 이미지 태그를 커밋하면, ArgoCD가 그 변경을 감지하여 배포한다.

**연결:**
- **ArgoCD** → EKS Control Plane (GitOps Deploy — 매니페스트 적용)
- ArgoCD ← Git 저장소 (매니페스트 변경 감시)

**전체 CI/CD 흐름 요약:**
```
① GitHub: 코드 머지
  ↓
② GitHub Actions: 빌드 → 테스트 → Docker 이미지 빌드
  ↓
③ ECR: 이미지 저장
  ↓
④ GitHub Actions: K8s 매니페스트의 이미지 태그 업데이트 → Git 커밋
  ↓
⑤ ArgoCD: Git 변경 감지 → EKS Control Plane에 매니페스트 적용
  ↓
⑥ EKS: 새 Pod 생성 → ECR에서 새 이미지 Pull → 이전 Pod 종료
```

### 10.4 Blue-Green 배포 (Argo Rollouts)

4개 핵심 서비스(gateway, ticket, payment, queue)는 Blue-Green 배포를 사용한다.

```
현재 실행 중 (Blue)     새 버전 (Green)
┌─────────────────┐   ┌─────────────────┐
│  gateway v1.0   │   │  gateway v1.1   │
│  (트래픽 받는 중) │   │  (대기 중)       │
└─────────────────┘   └─────────────────┘

① Green Pod 생성 → Health Check (5회 × 10초)
② Health Check 통과 → 수동 승인 대기
③ 운영자 승인 → 트래픽을 Green으로 전환
④ 30초 후 Blue 종료
⑤ 문제 발생 시 → 자동 롤백 (다시 Blue로)
```

---

## 11. 보안 — 6단계 방어

외부에서 데이터까지 총 6단계 보안이 적용된다.

| 계층 | 위치 | 역할 |
|------|------|------|
| **Layer 1** | CloudFront + WAF | TLS 암호화(데이터를 도청 불가능하게 함), 보안 헤더, WAF (Rate Limit, SQLi, XSS, Bad Input 차단) |
| **Layer 2** | Lambda@Edge | VWR 토큰 검증 (대기열 우회 방지) |
| **Layer 3** | ALB | CloudFront에서만 접근 허용 (Prefix List), HTTPS |
| **Layer 4** | gateway-service | JWT 인증, Rate Limiting, Entry Token 검증 |
| **Layer 5** | NetworkPolicy | 서비스별 통신 규칙 (화이트리스트 방식, 허용되지 않은 서비스 간 통신 차단) |
| **Layer 6** | VPC | 서브넷 격리 (DB/Cache는 인터넷 차단), 보안 그룹 |

---

## 12. 전체 연결 요약표

### 12.1 외부 → 내부 (인바운드)

| 출발 | 도착 | 프로토콜 | 용도 |
|------|------|----------|------|
| 사용자 | Route 53 | DNS | 도메인 → IP 변환 |
| Route 53 | CloudFront | HTTPS | CDN으로 라우팅 |
| CloudFront | ALB → Frontend Pod | HTTPS | Next.js 앱 제공 |
| CloudFront | S3 Assets | HTTPS (OAC) | 이미지/파일 제공 |
| CloudFront | API Gateway | HTTPS | VWR 대기열 API |
| CloudFront | Lambda@Edge | - | API 요청 시 토큰 검증 |
| Lambda@Edge | IGW | HTTPS | 검증 후 VPC로 진입 |
| IGW | ALB | HTTPS | VPC 진입 |
| ALB | gateway Pod | HTTP :3001 | API 요청 전달 |

### 12.2 서비스 간 (내부)

| 출발 | 도착 | 프로토콜 | 용도 |
|------|------|----------|------|
| gateway | auth/ticket/payment/stats/queue/catalog/community | HTTP | API 라우팅 |
| catalog | auth | HTTP | 이벤트 생성자 정보 |
| ticket | payment | HTTP | REST 내부 조회 (PaymentInternalClient) |
| community | ticket | HTTP | REST 내부 조회 (TicketInternalClient) |
| queue | catalog | HTTP | 이벤트 정보 조회 |
| ticket | Kafka | TCP :9094 | reservation/transfer/membership-events 발행 |
| payment | Kafka | TCP :9094 | payment-events 발행 |
| Kafka | ticket/stats | TCP :9094 | 이벤트 수신 |
| queue | SQS | HTTPS | 티켓 이벤트 발행 |
| SQS | Lambda Worker | - | 이벤트 트리거 |

### 12.3 서비스 → 데이터 계층

| 출발 | 도착 | 프로토콜 | 용도 |
|------|------|----------|------|
| 서비스 Pod | RDS Proxy | TCP :5432 | DB 쿼리 |
| RDS Proxy | RDS Primary | TCP :5432 | 커넥션 풀링 |
| RDS Primary | RDS Standby | - | 동기 복제 (자동) |
| RDS Primary | Read Replica | - | 비동기 복제 (자동) |
| 서비스 Pod | Redis Primary | TCP :6379 | 캐시/대기열 |
| Redis Primary | Redis Replica | - | 복제 (자동) |
| Lambda Worker | RDS Proxy | TCP :5432 | SQS 메시지 처리 결과 저장 |
| Lambda VWR | DynamoDB | HTTPS | 대기열 데이터 |

### 12.4 모니터링

| 출발 | 도착 | 프로토콜 | 용도 |
|------|------|----------|------|
| Prometheus | 모든 Pod | HTTP /metrics | 메트릭 수집 |
| Prometheus | AMP | HTTPS (SigV4) | 메트릭 외부 전송 |
| AMP | AMG | - | 대시보드 조회 |
| AWS 서비스들 | CloudWatch | - | 인프라 메트릭 자동 수집 |
| CloudWatch | AMG | - | 통합 대시보드 |
| CloudWatch Alarm | SNS | - | 알림 발송 |

### 12.5 CI/CD

| 출발 | 도착 | 용도 |
|------|------|------|
| GitHub Actions | ECR | 이미지 빌드 & Push |
| GitHub Actions | Git (매니페스트) | 이미지 태그 업데이트 |
| ArgoCD | Git (매니페스트) | 변경 감시 |
| ArgoCD | EKS Control Plane | GitOps 배포 |
| EKS Worker Nodes | ECR | 이미지 Pull (VPC Endpoint 경유) |

### 12.6 VPC Endpoint 경유 접근

| Pod/서비스 | VPC Endpoint | AWS 서비스 | 용도 |
|-----------|-------------|-----------|------|
| Worker Node | Interface (ECR) | ECR | 이미지 Pull |
| Worker Node | Interface (EKS) | EKS CP | API 통신 |
| Worker Node | Interface (STS) | STS | IRSA 토큰 |
| Worker Node | Interface (Logs) | CloudWatch | 로그 전송 |
| Worker Node | Interface (SM) | Secrets Manager | 시크릿 조회 |
| Karpenter | Interface (EC2) | EC2 | 노드 생성/삭제 |
| 서비스 Pod | Gateway (S3) | S3 | 파일 접근 |
| Lambda VWR | *(VPC 밖 직접)* | DynamoDB | VWR 데이터 |
