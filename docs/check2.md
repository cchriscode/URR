유저 로그인

API Gateway가 auth-service로 라우팅.

auth-service에서 access token + refresh token을 발급.

access token은 짧게(10-15분), refresh token은 길게(7-14일) 설정

access token은 서비스 접근용, refresh token은 비밀번호 없이 access token을 재발급받는 수단

이후 매 요청마다 Lambda에서 access token을 자체 검증, X-User-Id 헤더를 주입해서 하위 서비스에 전달.

ticket-service, payment-service 등은 JWT_SECRET을 모르고 Lambda가 넣어준 X-User-Id 헤더만 받음. refresh token의 존재도 모름. 오직 auth-service만 refresh token을 검증하고 발급함.

외부에서 가짜 헤더를 넣으면? Lambda에서 외부 X-User- 헤더를 전부 삭제, JWT 검증 성공한 경우에만 새로 주입. 

access token이 만료되면? Lambda가 401을 반환.

클라이언트가 refresh를 호출하고(이 경로는 Lambda 검증 넘김),

auth-service가 refresh token을 DB에서 확인한 뒤 새 access token + 새 refresh token을 발급.

클라이언트는 새 access token으로 다시 요청.

이 갱신은 유저가 사이트를 사용하는 동안 자동으로 반복됨

refresh token이 만료되면 그때 재로그인.

refresh token만 넣고 일반 API를 요청하면? Lambda에서 차단

refresh token은 1회용. 재사용되면 즉시 revoke 처리되고 새 refresh token이 발급

만약 탈취된 refresh token이 재사용되면? 이미 revoke된 토큰이 다시 들어온 것이므로 탈취로 판단, 해당 세션 전체의 refresh token을 무효화. 

해커와 유저 모두 강제 로그아웃되며, 유저만 재로그인

---

# 봇 탐지 + 토큰 인증 통합 흐름

## 전체 아키텍처

```
클라이언트 (브라우저)
    │
    ▼
CloudFront + WAF ─────────── 1차: 네트워크 레벨 봇 차단
    │
    ▼
API Gateway ──────────────── 2차: Lambda Authorizer (봇 행동 분석 + JWT 검증)
    │
    ▼
ALB ──────────────────────── K8s 서비스로 라우팅
    │
    ▼
K8s 서비스 (ticket, payment, queue, ...)
```

## 각 레이어별 역할

### 1차: CloudFront + WAF (신원 불필요)

WAF가 처리하는 것:
- 알려진 봇 IP/User-Agent 차단 (AWS Bot Control Managed Rule)
- 초당 요청 수 제한 (Rate-based Rule, 예: 같은 IP에서 5초 내 100회 → 차단)
- SQL Injection, XSS 패턴 차단 (OWASP Rule Set)
- TLS fingerprint(JA3) 기반 자동화 도구 시그니처 차단

WAF가 못 잡는 것:
- 정상 브라우저(Chrome)를 쓰는 매크로
- IP를 계속 바꾸는 VPN 매크로
- 사람처럼 행동하는 고급 매크로

WAF에서 차단되면 → 403 즉시 반환. API Gateway까지 안 감.

### 2차: API Gateway + Lambda Authorizer

Lambda Authorizer 하나가 봇 탐지와 JWT 검증을 순서대로 처리함.

```
Lambda Authorizer 내부 흐름:

  요청 들어옴 (cookie, headers, IP 정보)
      │
      ▼
  ┌─ 봇 차단 확인 (Redis) ─┐
  │  key: "blocked:ip:{ip}" │
  │  존재하면 → Deny 반환    │  ← JWT 검증 안 함. 바로 차단.
  └─────────────────────────┘
      │ (차단 아님)
      ▼
  ┌─ 서버 자체 행동 분석 ───┐
  │  같은 IP 요청 빈도 체크  │
  │  요청 간격 균일성 체크   │
  │  비정상 패턴 → Deny     │
  └─────────────────────────┘
      │ (정상)
      ▼
  ┌─ JWT 검증 ──────────────┐
  │  cookie에서 access_token │
  │  추출 → 서명 검증        │
  │  만료 → Deny (401)       │
  │  유효 → userId 추출      │
  └─────────────────────────┘
      │ (userId 확보)
      ▼
  ┌─ 유저 행동 분석 (Redis) ────────┐
  │  key: "bot:score:user:{userId}" │
  │  LLM 서버가 산출한 점수 조회     │
  │  점수 > 임계값 → Deny           │
  └─────────────────────────────────┘
      │ (통과)
      ▼
  Allow 반환 + context:
    { userId, userEmail, userRole }
```

API Gateway가 context를 헤더로 매핑:
- X-User-Id ← context.userId
- X-User-Email ← context.userEmail
- X-User-Role ← context.userRole

외부에서 들어온 X-User-* 헤더는 API Gateway Integration Request에서 덮어쓰기되므로 위조 불가.

### 3차: ALB → K8s 서비스

하위 서비스(ticket, payment, queue 등)는 JWT_SECRET을 모름.
Lambda가 넣어준 X-User-Id 헤더만 신뢰하고 사용.

## 경로별 흐름

### 일반 API 요청 (예: GET /api/tickets)

```
브라우저: GET /api/tickets (cookie: access_token=eyJ...)
    │
    ▼
WAF: IP/rate 정상 → 통과
    │
    ▼
API Gateway → Lambda Authorizer:
    1. blocked:ip 확인 → 없음
    2. 요청 패턴 분석 → 정상
    3. cookie에서 access_token 추출 → JWT 검증 성공 → userId=123
    4. bot:score:user:123 조회 → 0.2 (정상)
    → Allow + { userId: 123, role: USER }
    │
    ▼
API Gateway → ALB → ticket-service
    헤더: X-User-Id: 123, X-User-Role: USER
    │
    ▼
ticket-service: X-User-Id=123으로 티켓 목록 반환
```

### access_token 만료 시

```
브라우저: GET /api/tickets (cookie: access_token=만료됨)
    │
    ▼
WAF → 통과
    │
    ▼
Lambda Authorizer:
    1. blocked:ip → 없음
    2. 패턴 → 정상
    3. JWT 검증 → 만료됨
    → Deny (401)
    │
    ▼
브라우저: 401 받음 → refresh 호출

브라우저: POST /api/auth/refresh (cookie: refresh_token=eyJ...)
    │
    ▼
WAF → 통과
    │
    ▼
API Gateway: /api/auth/refresh 경로는 Authorizer 없음 (Authorization: NONE)
    │
    ▼
ALB → auth-service:
    refresh_token 검증 (DB에서 확인, 1회용 체크)
    → 새 access_token + 새 refresh_token 발급 (Set-Cookie)
    │
    ▼
브라우저: 새 access_token으로 원래 요청 재시도
```

refresh 경로에 Authorizer가 없는 이유: access_token이 만료된 상태에서 호출하는 것이므로 JWT 검증을 걸면 영원히 갱신 불가.
단, WAF rate limit은 적용됨. refresh 남용(무한 호출)은 WAF에서 차단.

### 매크로가 티켓 예매하는 경우

```
매크로: POST /api/queue/join (cookie: access_token=유효, 정상 계정 사용)
    │
    ▼
WAF: IP 정상, rate 정상 → 통과 (VPN이라 매번 IP 다름)
    │
    ▼
Lambda Authorizer:
    1. blocked:ip → 없음 (IP 계속 바뀜)
    2. 요청 패턴 → 정상 (rate 안 넘음)
    3. JWT 검증 → 유효 → userId=456
    4. bot:score:user:456 조회 → 0.85 (LLM 서버가 마우스 궤적 분석 후 높은 점수 부여)
    → Deny (403, BOT_DETECTED)

매크로 차단됨.
```

이 점수는 어디서 왔는가:

```
[별도 흐름 - 비동기]

프론트엔드: 마우스 궤적 raw 데이터 수집
    { points: [{x:100,y:200,t:1000}, {x:102,y:200,t:1010}, ...] }
    │
    ▼
POST /api/behavior/report (Authorizer 적용됨 → userId 확보)
    │
    ▼
LLM 매크로 탐지 서버:
    마우스 궤적 분석 (직선 이동, 균일 간격, 베지어 곡선 부재 등)
    → 점수 산출 → Redis에 저장
    │
    ▼
Redis: SET bot:score:user:456 0.85 EX 3600
```

LLM 서버가 점수를 쓰고, Lambda Authorizer가 점수를 읽음. 비동기 분리.

### 로그인

```
브라우저: POST /api/auth/login { email, password }
    │
    ▼
WAF → 통과
    │
    ▼
API Gateway: /api/auth/login 경로는 Authorizer 없음
    (로그인 전이라 access_token이 없음)
    │
    ▼
ALB → auth-service:
    email/password 확인 → access_token + refresh_token 발급 (Set-Cookie)
    │
    ▼
브라우저: 이후 요청부터 cookie에 access_token 포함 → Authorizer 통과
```

## Authorizer 적용/미적용 경로 정리

| 경로 | Authorizer | 이유 |
|------|-----------|------|
| POST /api/auth/login | 없음 | 로그인 전, 토큰 없음 |
| POST /api/auth/register | 없음 | 회원가입, 토큰 없음 |
| POST /api/auth/refresh | 없음 | access_token 만료 상태에서 호출 |
| GET /api/auth/oauth2/* | 없음 | 소셜 로그인 콜백 |
| GET /api/tickets | 있음 | 인증 필요 |
| POST /api/queue/join | 있음 | 인증 + 봇탐지 필요 |
| POST /api/payments/* | 있음 | 인증 + 봇탐지 필요 |
| POST /api/behavior/report | 있음 | userId 필요 (마우스 데이터 연결용) |
| GET /health, /actuator/* | 없음 | 내부 헬스체크 |

## 봇 탐지 데이터 흐름 요약

```
프론트엔드 ──마우스 데이터──→ LLM 서버 ──점수──→ Redis
                                                  ↑ 쓰기
                                                  │
Lambda Authorizer ──점수 조회──────────────────────┘ 읽기
    + 자체 패턴 분석 (IP 빈도, 요청 간격)
    + JWT 검증 (userId 확보)
    → 종합 판단 → Allow/Deny
```

세 가지 독립된 판단 소스:
1. WAF: 네트워크 레벨 (IP, rate, 시그니처)
2. Lambda 자체: 요청 패턴 (서버 측 분석, 클라이언트 위조 불가)
3. LLM 서버: 마우스 궤적 (행동 분석, 비동기)

이 세 겹이 각각 다른 수준의 봇을 잡아냄:
- WAF → 저급 봇 (스크립트, curl, 알려진 봇)
- Lambda 패턴 → 중급 봇 (자동화 도구, 비정상 속도)
- LLM 분석 → 고급 봇 (사람처럼 행동하지만 마우스 궤적이 기계적)

토큰 시스템과 봇 탐지는 서로 간섭하지 않음.
봇 탐지는 "차단할까?"를 결정하고, 토큰은 "누구인가?"를 확인함.
같은 파이프라인에서 순서대로 실행될 뿐, 각자의 역할만 수행.
