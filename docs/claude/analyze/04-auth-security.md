# URR 인증 및 보안 아키텍처 분석

> 분석 일자: 2026-02-16
> 분석 대상: URR (Ultimate Reservation & Retail) 플랫폼
> 분석 범위: 인증 시스템, API 보안, 네트워크 보안, 데이터 보안, 대기열 보안, OWASP Top 10 대응

---

## 목차

1. [인증 시스템 개요](#1-인증-시스템-개요)
2. [JWT 구조 및 토큰 관리](#2-jwt-구조-및-토큰-관리)
3. [3-Tier 토큰 아키텍처](#3-3-tier-토큰-아키텍처)
4. [관리자 인증](#4-관리자-인증)
5. [API 보안](#5-api-보안)
6. [네트워크 보안](#6-네트워크-보안)
7. [데이터 보안](#7-데이터-보안)
8. [Lambda@Edge 보안](#8-lambdaedge-보안)
9. [대기열 보안](#9-대기열-보안)
10. [OWASP Top 10 대응 평가](#10-owasp-top-10-대응-평가)
11. [위협 모델 (Threat Model)](#11-위협-모델-threat-model)
12. [보안 평가 요약](#12-보안-평가-요약)
13. [강점 및 약점](#13-강점-및-약점)
14. [AWS 환경 특화 권고사항](#14-aws-환경-특화-권고사항)

---

## 1. 인증 시스템 개요

### 아키텍처 구성

URR 플랫폼은 Spring Boot 기반의 마이크로서비스 아키텍처로, 인증 흐름이 다음과 같이 분리되어 있다:

```
[Browser] --> [CloudFront + Lambda@Edge] --> [ALB] --> [Gateway Service] --> [Auth Service]
                   |                                        |
           Tier 1 VWR token 검증                   JWT 검증 + X-User-* 헤더 주입
           Tier 2 Entry token 검증                  Rate Limiting (Redis)
                                                    VWR Entry Token 검증
```

### 핵심 컴포넌트

| 컴포넌트 | 위치 | 역할 |
|----------|------|------|
| auth-service | `services-spring/auth-service/` | 사용자 등록, 로그인, JWT 발급, 토큰 갱신 |
| gateway-service | `services-spring/gateway-service/` | JWT 검증, 헤더 주입, Rate Limiting, Entry Token 검증 |
| queue-service | `services-spring/queue-service/` | 대기열 관리, Entry Token 발급 |
| Lambda@Edge | `lambda/edge-queue-check/` | CDN 레벨 토큰 검증 (Tier 1 + Tier 2) |
| VWR API Lambda | `lambda/vwr-api/` | Tier 1 VWR 토큰 발급 |

### 인증 흐름 (Login Flow)

```
1. 사용자 → POST /api/auth/login (email, password)
2. auth-service: BCrypt 비밀번호 검증 → JWT access token + refresh token 생성
3. 응답: Set-Cookie(access_token, refresh_token) + JSON body에 토큰 포함
4. 이후 API 호출: Cookie에서 access_token 자동 전송 (withCredentials: true)
5. Gateway: Cookie → Authorization 헤더 변환 → JWT 검증 → X-User-* 헤더 주입
6. 다운스트림 서비스: X-User-* 헤더로 사용자 식별 (JWT_SECRET 불필요)
```

---

## 2. JWT 구조 및 토큰 관리

### 2.1 Access Token 구조

**발급 위치**: `services-spring/auth-service/src/main/java/guru/urr/authservice/security/JwtService.java` (라인 31-46)

```java
Jwts.builder()
    .subject(user.getId().toString())        // sub: UUID
    .claim("userId", user.getId().toString()) // 사용자 ID
    .claim("email", user.getEmail())          // 이메일
    .claim("role", user.getRole().name())     // "user" 또는 "admin"
    .claim("type", "access")                  // 토큰 타입 구분
    .issuedAt(now)                            // iat
    .expiration(expiry)                       // exp
    .signWith(signingKey())                   // HMAC-SHA 서명
    .compact();
```

| 항목 | 값 |
|------|-----|
| 서명 알고리즘 | HMAC-SHA (HS256/HS384/HS512, 키 크기에 따라 자동 결정) |
| 만료 시간 | 기본 1800초 (30분), 환경변수 `JWT_EXPIRATION_SECONDS`로 설정 |
| 키 최소 길이 | 32바이트 (라인 112-114에서 검증) |
| Claims | sub, userId, email, role, type, iat, exp |

### 2.2 Refresh Token 구조

**발급 위치**: `services-spring/auth-service/src/main/java/guru/urr/authservice/security/JwtService.java` (라인 52-67)

```java
Jwts.builder()
    .subject(user.getId().toString())
    .claim("userId", user.getId().toString())
    .claim("type", "refresh")                 // refresh 타입 명시
    .claim("familyId", familyId.toString())   // Token Family ID (회전 추적용)
    .id(UUID.randomUUID().toString())         // jti: 고유 ID
    .issuedAt(now)
    .expiration(expiry)
    .signWith(signingKey())
    .compact();
```

| 항목 | 값 |
|------|-----|
| 만료 시간 | 기본 604800초 (7일), 환경변수 `JWT_REFRESH_EXPIRATION_SECONDS`로 설정 |
| Family ID | 토큰 체인 추적을 위한 UUID (재사용 감지용) |
| JTI | 각 refresh token 고유 식별자 |

### 2.3 Refresh Token Rotation (토큰 회전)

**구현 위치**: `services-spring/auth-service/src/main/java/guru/urr/authservice/service/AuthService.java` (라인 113-153)

URR은 RFC 6749 권장사항에 따른 **Refresh Token Rotation with Reuse Detection**을 구현하고 있다:

1. **토큰 해싱**: refresh token은 SHA-256으로 해싱하여 DB에 저장 (`JwtService.hashToken`, 라인 73-81)
2. **단일 사용**: 사용된 refresh token은 즉시 `revokedAt` 타임스탬프로 폐기 (라인 139-140)
3. **재사용 감지**: 이미 폐기된 토큰이 재사용되면 해당 family의 모든 토큰을 폐기 (라인 132-136)
4. **Family 단위 폐기**: `refreshTokenRepository.revokeAllByFamilyId()` 호출로 전체 토큰 체인 무효화

```
Token Theft Scenario:
  공격자가 refresh_token_A 탈취 → 공격자 사용 → refresh_token_B 발급
  정상 사용자가 refresh_token_A 재시도 → 재사용 감지 → family 전체 폐기
  공격자의 refresh_token_B도 무효화됨
```

**DB 스키마**: `services-spring/auth-service/src/main/resources/db/migration/V4__refresh_tokens_table.sql`

```sql
CREATE TABLE refresh_tokens (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash VARCHAR(128) NOT NULL UNIQUE,  -- SHA-256 해시
    family_id UUID NOT NULL,                   -- 토큰 체인 그룹
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,                    -- NULL이면 유효
    created_at TIMESTAMPTZ
);
```

### 2.4 토큰 저장 방식

**구현 위치**: `services-spring/auth-service/src/main/java/guru/urr/authservice/util/CookieHelper.java`

| Cookie | HttpOnly | Secure | SameSite | Path | MaxAge |
|--------|----------|--------|----------|------|--------|
| `access_token` | Yes | 환경변수 `COOKIE_SECURE` (기본 false) | Lax | `/` | 1800초 (30분) |
| `refresh_token` | Yes | 환경변수 `COOKIE_SECURE` (기본 false) | Lax | `/api/auth` | 604800초 (7일) |

**보안 평가**:

- HttpOnly: **적절** -- JavaScript에서 토큰 접근 불가
- SameSite=Lax: **적절** -- 크로스사이트 POST 요청에서 쿠키 전송 차단
- Secure 기본값 false: **주의 필요** -- 프로덕션에서 반드시 true로 설정해야 함
- refresh_token Path 제한: **적절** -- `/api/auth` 경로에서만 전송

### 2.5 프론트엔드 토큰 처리

**구현 위치**: `apps/web/src/lib/api-client.ts`

```typescript
// 라인 38-43: axios 인스턴스 설정
const http = axios.create({
  baseURL: `${resolveBaseUrl()}/api/v1`,
  withCredentials: true,  // 쿠키 자동 전송
  timeout: 15000,
});

// 라인 70-77: 요청 인터셉터 - queue entry token 첨부
http.interceptors.request.use((config) => {
  const entryToken = getCookie("urr-entry-token");
  if (entryToken) {
    config.headers["x-queue-entry-token"] = entryToken;
  }
  return config;
});
```

Silent Refresh 메커니즘 (라인 79-131):
1. 401 응답 수신 시 자동으로 `/auth/refresh` 호출
2. 동시 다수 요청 실패 시 큐에 대기하여 단일 refresh 후 재시도
3. auth 엔드포인트 자체에서는 refresh 시도 안 함 (무한 루프 방지)
4. 429 응답에 대한 지수 백오프 재시도 (최대 2회)

---

## 3. 3-Tier 토큰 아키텍처

URR은 고부하 티켓팅 시스템의 특성을 반영한 3-Tier 토큰 구조를 가진다:

```
Tier 0: Auth JWT (사용자 인증)
   ↓
Tier 1: VWR Token (Virtual Waiting Room 통과 증명)
   ↓
Tier 2: Entry Token (대기열 통과 증명, API 접근 권한)
```

### Tier 0: Auth JWT

| 항목 | 내용 |
|------|------|
| 발급자 | auth-service |
| 검증자 | gateway-service (JwtAuthFilter) |
| 용도 | 사용자 신원 확인 |
| 저장 | `access_token` 쿠키 (HttpOnly) |
| 만료 | 30분 |

### Tier 1: VWR Token (`urr-vwr-token`)

**발급 위치**: `lambda/vwr-api/lib/token.js` (라인 11-27)

```javascript
const payload = {
  sub: eventId,           // 이벤트 ID
  uid: userId || 'anonymous',  // 사용자 ID
  tier: 1,                // Tier 구분
  iat: now,
  exp: now + 600,         // 10분 만료
};
```

| 항목 | 내용 |
|------|------|
| 발급자 | VWR API Lambda (`lambda/vwr-api/handlers/check.js`, 라인 24, 42) |
| 검증자 | Lambda@Edge (`lambda/edge-queue-check/index.js`, 라인 91-93) |
| 용도 | CDN 레벨 대기열(VWR) 통과 증명 |
| 저장 | `urr-vwr-token` 쿠키 |
| 만료 | 10분 |
| 서명 키 | `VWR_TOKEN_SECRET` 환경변수 |
| 활성 조건 | `vwr-active.json`에 해당 이벤트가 등록되어 있을 때만 검증 |

### Tier 2: Entry Token (`urr-entry-token` / `x-queue-entry-token`)

**발급 위치**: `services-spring/queue-service/src/main/java/guru/urr/queueservice/service/QueueService.java` (라인 215-227)

```java
Jwts.builder()
    .subject(eventId)              // 이벤트 ID
    .claim("uid", userId)          // 사용자 ID
    .issuedAt(issuedAt)
    .expiration(expiration)        // 기본 10분
    .signWith(entryTokenKey)       // QUEUE_ENTRY_TOKEN_SECRET
    .compact();
```

| 항목 | 내용 |
|------|------|
| 발급자 | queue-service |
| 검증자 | Lambda@Edge + gateway-service VwrEntryTokenFilter |
| 용도 | 대기열 통과 후 좌석/예약 API 접근 권한 |
| 저장 | `urr-entry-token` 쿠키 또는 `x-queue-entry-token` 헤더 |
| 만료 | 기본 600초 (10분), `QUEUE_ENTRY_TOKEN_TTL_SECONDS`로 설정 |
| 서명 키 | `QUEUE_ENTRY_TOKEN_SECRET` |
| 보호 대상 | `/api/seats/*`, `/api/reservations/*` (POST/PUT/PATCH만) |

### Tier 간 연계 검증

VwrEntryTokenFilter (라인 87-97)에서 **userId binding 검증** 수행:

```java
// VWR token의 uid와 Auth JWT의 userId가 일치하는지 확인
String vwrUserId = vwrClaims.get("uid", String.class);
String authUserId = extractAuthUserId(request);  // X-User-Id 헤더
if (authUserId != null && !vwrUserId.equals(authUserId)) {
    sendForbiddenResponse(response);  // 불일치 시 거부
}
```

Lambda@Edge (라인 124-126)에서 **eventId binding 검증** 수행:

```javascript
// Entry token의 sub(eventId)와 요청 경로의 eventId가 일치하는지 확인
if (eventIdFromPath && claims.sub && claims.sub !== eventIdFromPath) {
    return redirectToQueue(request);
}
```

---

## 4. 관리자 인증

### 4.1 역할 기반 접근 제어

**사용자 역할 정의**: `services-spring/auth-service/src/main/java/guru/urr/authservice/domain/UserRole.java`

```java
public enum UserRole {
    user,
    admin
}
```

**DB 제약**: `V1__create_users_table.sql` (라인 9)

```sql
role VARCHAR(20) NOT NULL DEFAULT 'user' CHECK (role IN ('user', 'admin'))
```

### 4.2 관리자 검증 메커니즘

**구현 위치**: `services-spring/queue-service/src/main/java/guru/urr/queueservice/shared/security/JwtTokenParser.java`

```java
public AuthUser requireAdmin(HttpServletRequest request) {
    AuthUser user = requireUser(request);
    if (!user.isAdmin()) {
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin role required");
    }
    return user;
}
```

다운스트림 서비스들은 gateway가 주입한 `X-User-Role` 헤더 값으로 관리자 여부를 판단한다.

**관리자 보호 엔드포인트 사용 예시**:

| 엔드포인트 | 위치 | 보호 방식 |
|------------|------|----------|
| `GET /api/queue/admin/{eventId}` | QueueController.java 라인 63-69 | `jwtTokenParser.requireAdmin(request)` |
| `POST /api/queue/admin/clear/{eventId}` | QueueController.java 라인 72-79 | `jwtTokenParser.requireAdmin(request)` |
| `POST /api/admin/vwr/activate/{eventId}` | VwrAdminController.java 라인 49-53 | `jwtTokenParser.requireAdmin(request)` |
| `POST /api/admin/vwr/deactivate/{eventId}` | VwrAdminController.java 라인 81-84 | `jwtTokenParser.requireAdmin(request)` |
| `POST /api/admin/vwr/advance/{eventId}` | VwrAdminController.java 라인 148 | `jwtTokenParser.requireAdmin(request)` |

### 4.3 관리자 시드 데이터

**위치**: `services-spring/auth-service/src/main/resources/db/migration/V3__seed_admin.sql`

```sql
INSERT INTO users (..., email, password_hash, ..., role, ...)
VALUES (..., 'admin@urr.com', '$2b$12$1RW...', ..., 'admin', ...)
ON CONFLICT (email) DO UPDATE SET role = 'admin';
```

**보안 문제 [심각도: HIGH]**: 하드코딩된 관리자 비밀번호가 Flyway 마이그레이션에 포함되어 있다. BCrypt 해시값이 소스 코드에 노출되므로, 오프라인 사전 공격 대상이 될 수 있다. `admin123`이라는 단순한 비밀번호가 사용된 것으로 추정된다.

---

## 5. API 보안

### 5.1 Gateway 필터 체인

**위치**: `services-spring/gateway-service/src/main/java/guru/urr/gatewayservice/filter/`

필터 실행 순서 (@Order 기준):

| 순서 | 필터 | Order | 역할 |
|------|------|-------|------|
| 1 | ApiVersionFilter | -10 | `/api/v1/` prefix를 `/api/`로 변환 |
| 2 | CookieAuthFilter | -2 | Cookie의 access_token을 Authorization 헤더로 변환 |
| 3 | JwtAuthFilter | -1 | JWT 검증, X-User-* 헤더 주입, 외부 X-User-* 헤더 제거 |
| 4 | RateLimitFilter | 0 | Redis 기반 Rate Limiting (Sliding Window) |
| 5 | VwrEntryTokenFilter | 1 | 보호 경로에 대한 Entry Token 검증 |

### 5.2 헤더 스푸핑 방지

**위치**: `services-spring/gateway-service/src/main/java/guru/urr/gatewayservice/filter/JwtAuthFilter.java` (라인 104-137)

JwtAuthFilter는 `UserHeaderStrippingWrapper`로 들어오는 모든 요청에서 다음 헤더를 제거한다:

```java
private static final List<String> STRIPPED = List.of(
    "x-user-id",
    "x-user-email",
    "x-user-role"
);
```

이는 외부 공격자가 X-User-* 헤더를 직접 설정하여 인증을 우회하는 것을 방지한다. JWT 검증이 성공한 경우에만 `UserHeaderInjectionWrapper`로 헤더를 주입한다.

### 5.3 CloudFront 시크릿 헤더 검증

**위치**: `services-spring/gateway-service/src/main/java/guru/urr/gatewayservice/filter/VwrEntryTokenFilter.java` (라인 62-69)

```java
if (cloudFrontSecret != null) {
    String cfHeader = request.getHeader(CF_VERIFIED_HEADER);
    if (cfHeader != null && MessageDigest.isEqual(
            cloudFrontSecret.getBytes(StandardCharsets.UTF_8),
            cfHeader.getBytes(StandardCharsets.UTF_8))) {
        filterChain.doFilter(request, response);  // Lambda@Edge가 이미 검증 완료
        return;
    }
}
```

`X-CloudFront-Verified` 헤더에 사전 공유 시크릿이 일치하면, Lambda@Edge에서 이미 토큰 검증을 완료한 것으로 간주하여 이중 검증을 건너뛴다. Timing-safe comparison (`MessageDigest.isEqual`) 사용.

### 5.4 CORS 설정

**위치**: `services-spring/gateway-service/src/main/java/guru/urr/gatewayservice/config/CorsConfig.java`

```java
config.setAllowedOrigins(origins);           // 환경변수로 설정 (기본: localhost:3000)
config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
config.setAllowedHeaders(List.of("Authorization", "Content-Type", "x-queue-entry-token"));
config.setAllowCredentials(true);
config.setMaxAge(3600L);
```

**보안 평가**:
- 허용 Origin이 환경변수로 외부화됨: **적절**
- `allowCredentials: true`와 특정 Origin 사용 (와일드카드 아님): **적절**
- 허용 헤더가 최소한으로 제한됨: **적절**

**VWR API Lambda의 CORS** (`lambda/vwr-api/index.js`, 라인 7-12):

```javascript
const CORS_HEADERS = {
  'Access-Control-Allow-Origin': process.env.CORS_ORIGIN || '*',
  ...
};
```

**보안 문제 [심각도: MEDIUM]**: VWR API Lambda의 CORS Origin이 환경변수 미설정 시 `*`로 폴백된다. 프로덕션에서 반드시 특정 Origin으로 설정해야 한다.

### 5.5 Rate Limiting

**위치**: `services-spring/gateway-service/src/main/java/guru/urr/gatewayservice/filter/RateLimitFilter.java`

Redis Sorted Set 기반의 Sliding Window 알고리즘 사용:

| 카테고리 | 기본 RPM | 대상 경로 |
|----------|---------|----------|
| AUTH | 60 | `/api/auth/*` |
| QUEUE | 120 | `/api/queue/*` |
| BOOKING | 30 | `/api/seats/reserve`, `/api/reservations` |
| GENERAL | 3000 | 기타 모든 경로 |

**Lua 스크립트**: `services-spring/gateway-service/src/main/resources/redis/rate_limit.lua`

```lua
redis.call('ZREMRANGEBYSCORE', key, '-inf', cutoff)  -- 윈도우 밖 제거
redis.call('ZADD', key, now, req_id)                 -- 현재 요청 추가
local count = redis.call('ZCARD', key)               -- 전체 카운트
if count > limit then
    redis.call('ZREM', key, req_id)                  -- 초과 시 제거
    return 0                                          -- 거부
end
return 1                                              -- 허용
```

**클라이언트 식별** (라인 103-111):
- 인증된 사용자: `user:{userId}` (X-User-Id 헤더 기반)
- 비인증 사용자: `ip:{remoteAddr}`

**보안 문제 [심각도: LOW]**: Rate limit 실패 시 fail-open 정책 적용 (라인 94-98). Redis 장애 시 모든 요청을 허용한다. 이는 가용성 우선 설계이나, DDoS 공격 시 보호가 해제될 수 있다.

### 5.6 서비스 간 내부 통신 보안

**위치**: `services-spring/auth-service/src/main/java/guru/urr/authservice/security/InternalApiAuthFilter.java`

`/internal/**` 경로는 `x-internal-token` 헤더 또는 Bearer 토큰으로 보호된다:

```java
if (token == null || !MessageDigest.isEqual(
        expectedToken.getBytes(StandardCharsets.UTF_8),
        token.getBytes(StandardCharsets.UTF_8))) {
    // 403 Forbidden
}
```

- Timing-safe comparison 사용: **적절**
- 토큰은 환경변수 `INTERNAL_API_TOKEN`으로 주입

---

## 6. 네트워크 보안

### 6.1 VPC 아키텍처

**위치**: `terraform/modules/vpc/main.tf`

5-Tier 서브넷 구조:

```
Internet
   |
[Public Subnets] ---- ALB, NAT Gateway
   |
[App Subnets] ------- EKS 노드 (Private)
   |
[DB Subnets] -------- RDS PostgreSQL (Private, 라우팅 없음)
   |
[Cache Subnets] ----- ElastiCache Redis (Private, 라우팅 없음)
   |
[Streaming Subnets] - Lambda Workers (Private, NAT 경유)
```

| 서브넷 계층 | CIDR 범위 | 인터넷 접근 | 용도 |
|-------------|-----------|------------|------|
| Public | `10.0.0.0/24`, `10.0.1.0/24` | 직접 접근 | ALB, NAT GW |
| App | `10.0.10.0/24`, `10.0.11.0/24` | NAT 경유 | EKS 노드 |
| DB | `10.0.20.0/24`, `10.0.21.0/24` | 없음 | RDS |
| Cache | `10.0.30.0/24`, `10.0.31.0/24` | 없음 | ElastiCache |
| Streaming | `10.0.40.0/24`, `10.0.41.0/24` | NAT 경유 | Lambda |

**보안 평가**:
- DB/Cache 서브넷은 인터넷 라우트 없음: **적절**
- AZ별 NAT Gateway로 고가용성 확보: **적절**
- Public 서브넷에 `map_public_ip_on_launch = true`: EKS 노드가 아닌 ALB 전용이므로 **적절**

### 6.2 Kubernetes 네트워크 정책

**위치**: `k8s/spring/base/network-policies.yaml`

```yaml
# 기본 정책: 모든 트래픽 차단
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: default-deny-all
spec:
  podSelector: {}
  policyTypes:
    - Ingress
    - Egress
```

**서비스별 Ingress 정책**:

| 서비스 | 허용 소스 | 포트 |
|--------|----------|------|
| gateway-service | 모든 소스 (ALB) | 3001 |
| frontend | 모든 소스 | 3000 |
| auth-service | gateway-service, catalog-service | 3005 |
| ticket-service | gateway-service, payment-service, catalog-service | 3002 |
| catalog-service | gateway-service, queue-service | 3009 |
| payment-service | gateway-service | 3003 |
| stats-service | gateway-service | 3004 |
| queue-service | gateway-service | 3007 |
| community-service | gateway-service | 3008 |

**Egress 정책** (라인 178-226):
- `tier: backend` 레이블 Pod: 같은 네임스페이스의 모든 Pod + DNS로 Egress 허용
- gateway-service: `tier: backend` Pod + DNS로 Egress 허용

**보안 평가**:
- Default deny-all 기본 정책: **적절**
- 서비스 간 최소 권한 원칙 적용: **적절**
- gateway-service Ingress에 소스 제한 없음: **주의 필요** -- ALB에서만 접근 가능하도록 CIDR 또는 레이블로 제한 권장

**보안 문제 [심각도: MEDIUM]**: `allow-backend-egress`에서 `podSelector: {}`로 같은 네임스페이스의 모든 Pod에 대한 Egress를 허용한다 (라인 190-191). 이는 침해된 Pod이 다른 모든 서비스에 직접 접근할 수 있게 한다. 필요한 대상만 명시적으로 허용하는 것이 바람직하다.

---

## 7. 데이터 보안

### 7.1 비밀 관리 (Secrets Management)

**위치**: `terraform/modules/secrets/main.tf`

AWS Secrets Manager를 통한 중앙화된 비밀 관리:

| 시크릿 | 생성 방식 | 길이 | 특수문자 |
|--------|----------|------|---------|
| RDS 비밀번호 | `random_password` | 32자 | 포함 |
| Redis 인증 토큰 | `random_password` | 32자 | 미포함 |
| Queue Entry Token Secret | `random_password` | 64자 | 미포함 |
| JWT Secret | `random_password` | 64자 | 미포함 |

**보안 평가**:
- 자동 생성된 고엔트로피 비밀: **적절**
- Secrets Manager 삭제 복구 기간 7일 (기본): **적절**
- Terraform output에 sensitive 마킹: **적절** (`terraform/modules/secrets/outputs.tf` 라인 9, 19, 30, 41)

### 7.2 비밀번호 해싱

**위치**: `services-spring/auth-service/src/main/java/guru/urr/authservice/config/AppConfig.java`

```java
@Bean
public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(12);  // cost factor 12
}
```

- BCrypt cost factor 12: **적절** (2^12 = 4096 라운드, 현재 권장 수준)
- 비밀번호 최소 길이 8자 제한: `RegisterRequest.java` 라인 9

### 7.3 OAuth 사용자 비밀번호 처리

**위치**: `services-spring/auth-service/src/main/java/guru/urr/authservice/service/AuthService.java` (라인 233)

```java
user.setPasswordHash("OAUTH_USER_NO_PASSWORD");
```

**보안 문제 [심각도: HIGH]**: Google OAuth 사용자의 password_hash에 평문 문자열 `"OAUTH_USER_NO_PASSWORD"`가 저장된다. 이 값은 BCrypt 해시 형식이 아니므로 `passwordEncoder.matches()`에서 항상 false를 반환하여 로그인은 실패하지만, password_hash 컬럼에 NOT NULL 제약이 있어 더미 값이 필요한 것으로 보인다.

문제점:
1. DB에서 OAuth 사용자와 일반 사용자 구분이 불명확하다
2. password_hash 컬럼에 의미 없는 평문 저장은 정책 위반 가능성이 있다
3. 향후 해싱 로직 변경 시 예외 발생 위험이 있다

권장: password_hash 컬럼을 nullable로 변경하거나, OAuth 전용 인증 플로우를 분리한다.

### 7.4 에러 메시지의 정보 노출

**위치**: `services-spring/auth-service/src/main/java/guru/urr/authservice/service/AuthService.java`

로그인 실패 시 동일한 에러 메시지 사용 (라인 89, 92):

```java
.orElseThrow(() -> new ApiException("Invalid email or password"));
// ...
throw new ApiException("Invalid email or password");
```

사용자 열거 공격(User Enumeration) 방지를 위해 동일 메시지 사용: **적절**

단, 등록 시 (라인 67-69):

```java
userRepository.findByEmail(request.email()).ifPresent(user -> {
    throw new ApiException("Email already exists");
});
```

**보안 문제 [심각도: LOW]**: 등록 API에서 이메일 존재 여부가 노출된다. 이는 사용자 열거 공격에 활용될 수 있으나, UX와 보안의 트레이드오프로 많은 서비스에서 허용하는 패턴이다. Rate limiting으로 대량 열거를 방지하는 것이 현실적이다.

---

## 8. Lambda@Edge 보안

### 8.1 시크릿 주입 방식

**위치**: `lambda/edge-queue-check/index.js` (라인 28-37)

```javascript
// Lambda@Edge는 환경변수를 지원하지 않음
// 빌드 타임에 Terraform이 config.json을 생성하여 주입
let SECRET, VWR_SECRET;
try {
  const config = require('./config.json');
  SECRET = config.secret;
  VWR_SECRET = config.vwrSecret || config.secret;
} catch (e) {
  SECRET = process.env.QUEUE_ENTRY_TOKEN_SECRET;  // 로컬 개발용 폴백
  VWR_SECRET = process.env.VWR_TOKEN_SECRET || SECRET;
}
```

**보안 평가**: Lambda@Edge의 환경변수 미지원 한계를 config.json 빌드 타임 주입으로 해결했다. 이 파일은 배포 패키지에 포함되므로 Lambda 함수 코드에 대한 접근 제어가 중요하다.

### 8.2 JWT 검증 구현

**위치**: `lambda/edge-queue-check/index.js` (라인 147-181)

```javascript
function verifyJWT(token, secret) {
  // ...
  const expectedSignature = crypto
    .createHmac('sha256', secret)
    .update(data)
    .digest('base64url');

  // Timing-safe comparison
  if (signatureB64.length !== expectedSignature.length ||
      !crypto.timingSafeEqual(Buffer.from(signatureB64), Buffer.from(expectedSignature))) {
    return null;
  }
  // ...
}
```

- 외부 라이브러리 없이 순수 Node.js crypto 모듈 사용: **적절** (Lambda@Edge 콜드스타트 최적화)
- `crypto.timingSafeEqual` 사용: **적절** (타이밍 사이드 채널 공격 방지)
- 길이 사전 검증 후 timingSafeEqual 호출: **적절** (timingSafeEqual은 동일 길이 입력 필요)
- 만료 시간(exp) 검증: **적절**

### 8.3 VWR Config 캐싱

**위치**: `lambda/edge-queue-check/vwr-config.js`

```javascript
const CACHE_TTL_MS = 5 * 60 * 1000; // 5분 캐시
```

정적 파일 기반 설정으로 런타임 설정 변경이 즉시 반영되지 않는다. 재배포가 필요하다.

---

## 9. 대기열 보안

### 9.1 토큰 위조 방지

Entry Token은 HMAC-SHA256으로 서명되며, 서명 키는 최소 32바이트 이상 (`VwrEntryTokenFilter.java` 라인 133-135):

```java
if (secretBytes.length < HMAC_MIN_KEY_LENGTH) {
    throw new IllegalArgumentException(
        "QUEUE_ENTRY_TOKEN_SECRET must be at least " + HMAC_MIN_KEY_LENGTH + " bytes");
}
```

Terraform에서 64바이트 랜덤 비밀이 자동 생성되므로 충분한 엔트로피를 가진다.

### 9.2 크로스 이벤트 토큰 재사용 방지

**Lambda@Edge** (`lambda/edge-queue-check/index.js` 라인 124-126):
```javascript
if (eventIdFromPath && claims.sub && claims.sub !== eventIdFromPath) {
    return redirectToQueue(request);  // eventId 불일치 시 거부
}
```

**Gateway VwrEntryTokenFilter** (`VwrEntryTokenFilter.java` 라인 99-103):
```java
String vwrEventId = vwrClaims.getSubject();
if (vwrEventId != null) {
    request.setAttribute("vwr.eventId", vwrEventId);  // 백엔드 추가 검증용
}
```

Entry Token의 `sub` claim에 eventId가 바인딩되어 있어, 다른 이벤트의 토큰으로는 접근 불가.

### 9.3 사용자 바인딩 검증

**Gateway VwrEntryTokenFilter** (라인 87-97):

Entry Token의 `uid` claim과 Auth JWT의 userId(X-User-Id 헤더)가 일치하는지 검증한다. 이는 사용자 A가 받은 Entry Token을 사용자 B가 사용하는 것을 방지한다.

### 9.4 대기열 우회 방지

보호 메커니즘이 2중으로 동작한다:

1. **Lambda@Edge**: CDN 레벨에서 Entry Token 없는 요청을 `/queue/{eventId}`로 리다이렉트
2. **VwrEntryTokenFilter**: Gateway 레벨에서 POST/PUT/PATCH 메서드의 보호 경로에 대해 Entry Token 검증

직접 ALB 접근 시에도 Gateway의 VwrEntryTokenFilter가 작동하므로, CDN 우회에 대한 방어가 있다.

**보안 문제 [심각도: MEDIUM]**: VwrEntryTokenFilter는 `POST`, `PUT`, `PATCH` 메서드만 보호한다 (라인 49). `GET` 요청으로 좌석 정보(`/api/seats/events/{eventId}`)를 조회하는 것은 Entry Token 없이 가능하다. 이는 의도된 설계일 수 있으나, 좌석 가용성 정보의 사전 노출이 우려된다면 GET도 보호 대상에 포함해야 한다.

### 9.5 Entry Token 만료 및 갱신

Entry Token의 TTL은 기본 10분이다. 활성 사용자의 heartbeat 메커니즘(`QueueController.heartbeat`)이 활성 상태를 유지하지만, Entry Token 자체의 갱신 메커니즘은 확인되지 않는다. 10분 후 토큰이 만료되면 사용자는 대기열을 다시 통과해야 할 수 있다.

---

## 10. OWASP Top 10 대응 평가

### A01:2021 -- Broken Access Control

| 점검 항목 | 상태 | 근거 |
|-----------|------|------|
| 헤더 스푸핑 방지 | PASS | JwtAuthFilter에서 X-User-* 헤더 제거 후 재주입 |
| 관리자 권한 검증 | PASS | requireAdmin()으로 role 기반 접근 제어 |
| 경로별 접근 제어 | PASS | SecurityConfig에서 엔드포인트별 인증 요구 |
| 수평적 권한 상승 | PARTIAL | Entry Token에 userId binding 있으나, 일부 API에서 리소스 소유권 검증이 gateway 레벨에서만 수행됨 |

### A02:2021 -- Cryptographic Failures

| 점검 항목 | 상태 | 근거 |
|-----------|------|------|
| 비밀번호 해싱 | PASS | BCrypt cost 12 |
| JWT 서명 | PASS | HMAC-SHA, 최소 32바이트 키 |
| Refresh Token 해싱 | PASS | SHA-256으로 DB 저장 |
| 전송 암호화 | PARTIAL | COOKIE_SECURE 기본값 false |

### A03:2021 -- Injection

| 점검 항목 | 상태 | 근거 |
|-----------|------|------|
| SQL Injection | PASS | JPA/Hibernate 파라미터 바인딩, Flyway 마이그레이션 |
| NoSQL Injection | PASS | DynamoDB SDK 사용 (파라미터화된 쿼리) |
| LDAP/OS Injection | N/A | 해당 기능 없음 |

### A04:2021 -- Insecure Design

| 점검 항목 | 상태 | 근거 |
|-----------|------|------|
| 위협 모델 | PASS | 3-Tier 토큰 아키텍처로 심층 방어 |
| 인증 흐름 | PASS | Refresh token rotation with reuse detection |
| Rate Limiting | PASS | 카테고리별 차등 제한 |
| 대기열 보안 | PASS | eventId/userId binding, 이중 검증 |

### A05:2021 -- Security Misconfiguration

| 점검 항목 | 상태 | 근거 |
|-----------|------|------|
| 기본 자격증명 | FAIL | 하드코딩된 관리자 비밀번호 (V3__seed_admin.sql) |
| CSRF 비활성화 | WARN | csrf.disable() -- SameSite=Lax 쿠키와 stateless JWT로 상쇄 |
| CORS 와일드카드 | WARN | VWR API Lambda에서 기본값 `*` 가능 |
| Actuator 노출 | PASS | health, info, prometheus만 노출 |

### A06:2021 -- Vulnerable and Outdated Components

| 점검 항목 | 상태 | 근거 |
|-----------|------|------|
| 의존성 관리 | N/A | 분석 범위 외 (pom.xml 미분석) |

### A07:2021 -- Identification and Authentication Failures

| 점검 항목 | 상태 | 근거 |
|-----------|------|------|
| 비밀번호 정책 | PARTIAL | 최소 8자만 요구, 복잡성 규칙 없음 |
| 무차별 대입 방지 | PASS | Rate limiting (AUTH: 60rpm) |
| 토큰 재사용 감지 | PASS | Refresh token family-based reuse detection |
| 세션 고정 | PASS | Stateless JWT, 세션 없음 |

### A08:2021 -- Software and Data Integrity Failures

| 점검 항목 | 상태 | 근거 |
|-----------|------|------|
| JWT 서명 검증 | PASS | 모든 계층에서 서명 검증 |
| 역직렬화 | PASS | Spring Boot 기본 직렬화, 사용자 정의 역직렬화 없음 |

### A09:2021 -- Security Logging and Monitoring Failures

| 점검 항목 | 상태 | 근거 |
|-----------|------|------|
| 인증 실패 로깅 | PARTIAL | JWT 검증 실패는 debug 레벨, 더 높은 레벨 권장 |
| Rate limit 초과 로깅 | PASS | warn 레벨로 기록 |
| 토큰 재사용 감지 로깅 | PASS | warn 레벨로 기록 (AuthService 라인 134) |
| 분산 추적 | PASS | Zipkin/Micrometer 통합 |

### A10:2021 -- Server-Side Request Forgery (SSRF)

| 점검 항목 | 상태 | 근거 |
|-----------|------|------|
| 외부 URL 접근 | LOW RISK | Google OAuth 토큰 검증 시 Google API 호출만 |
| 내부 서비스 호출 | PASS | 하드코딩된 서비스 URL, 사용자 입력 기반 URL 없음 |

---

## 11. 위협 모델 (Threat Model)

### 11.1 공격 표면 (Attack Surface)

```
                         [인터넷]
                            |
          +-----------------+------------------+
          |                                    |
   [CloudFront CDN]                   [VWR API Gateway]
   Lambda@Edge 검증                    Lambda 함수
          |                                    |
      [ALB]                              [DynamoDB]
          |
   [Gateway Service]
    JWT/Rate/Entry 검증
          |
   +------+------+------+------+------+------+
   |      |      |      |      |      |      |
 Auth  Ticket Payment Queue  Stats Catalog Community
   |                    |
[PostgreSQL]        [Redis]
```

### 11.2 위협 시나리오 및 대응

| ID | 위협 | 심각도 | 가능성 | 대응 현황 | 잔여 위험 |
|----|------|--------|--------|----------|----------|
| T1 | JWT 토큰 탈취 (XSS) | HIGH | LOW | HttpOnly 쿠키로 JS 접근 차단 | XSS 발생 시 쿠키 전송 가능 (SameSite=Lax가 상쇄) |
| T2 | Refresh Token 탈취 | HIGH | LOW | Token Rotation + Reuse Detection으로 탈취 감지 | 탈취 후 1회 사용 가능 (정상 사용자가 재사용 시 감지) |
| T3 | 관리자 계정 탈취 | CRITICAL | MEDIUM | 하드코딩 비밀번호 존재, Rate Limiting | 시드 비밀번호 변경 미강제 |
| T4 | 대기열 우회 | HIGH | LOW | Lambda@Edge + Gateway 이중 검증, userId/eventId binding | 직접 ALB 접근 시 CloudFront 시크릿 헤더 없이도 Gateway 검증 동작 |
| T5 | Entry Token 재사용 | MEDIUM | MEDIUM | eventId/userId binding, 10분 만료 | 만료 전 같은 이벤트에서 다른 세션의 재사용 가능 (일회성 토큰 아님) |
| T6 | Rate Limit 우회 | MEDIUM | LOW | Redis Sliding Window, IP/User 기반 | Redis 장애 시 fail-open |
| T7 | 서비스 간 통신 스푸핑 | HIGH | LOW | INTERNAL_API_TOKEN + timing-safe comparison | 네트워크 정책으로 격리, 단 백엔드 egress가 과도하게 열려 있음 |
| T8 | CloudFront 우회 (직접 ALB 접근) | MEDIUM | MEDIUM | VwrEntryTokenFilter에서 독립 검증 | CloudFront Secret 헤더 우회 시에도 Entry Token 자체 검증 수행 |
| T9 | 사용자 열거 | LOW | HIGH | 로그인: 통합 에러 메시지, 등록: 이메일 존재 노출 | Rate Limiting으로 대량 열거 방지 |
| T10 | DDoS | HIGH | MEDIUM | CloudFront Shield, Rate Limiting | Lambda@Edge에 Rate Limiting 없음, API GW 쓰로틀링은 별도 설정 필요 |

### 11.3 STRIDE 분석

| STRIDE | 위협 | 현재 대응 | 보완 필요 |
|--------|------|----------|----------|
| **S**poofing | X-User-* 헤더 위조 | Gateway에서 제거 후 JWT 기반 재주입 | - |
| **T**ampering | JWT 페이로드 변조 | HMAC-SHA 서명 검증 | - |
| **R**epudiation | 사용자 행위 부인 | 분산 추적(Zipkin), 로깅 | 감사 로그(Audit Log) 전용 시스템 권장 |
| **I**nformation Disclosure | 에러 메시지 정보 노출 | GlobalExceptionHandler에서 통제 | 프로덕션에서 stack trace 비노출 확인 필요 |
| **D**enial of Service | 대량 요청 | Rate Limiting, CloudFront | Lambda@Edge 자체 Rate Limiting 없음 |
| **E**levation of Privilege | 관리자 권한 상승 | X-User-Role 기반 검증, Gateway에서 주입 | 역할 변경 감사 로그 필요 |

---

## 12. 보안 평가 요약

### 종합 점수표

| 영역 | 등급 | 설명 |
|------|------|------|
| 인증 시스템 | A- | JWT + Refresh Token Rotation 잘 구현, 비밀번호 정책 보완 필요 |
| 토큰 관리 | A | 3-Tier 토큰 아키텍처, userId/eventId binding, timing-safe 검증 |
| API 보안 | B+ | Gateway 필터 체인 견고, Rate Limiting fail-open 정책 개선 필요 |
| 네트워크 보안 | A- | VPC 5계층 분리, K8s 네트워크 정책, 백엔드 egress 과도 |
| 데이터 보안 | B+ | Secrets Manager 활용, BCrypt 12, OAuth 비밀번호 처리 개선 필요 |
| 대기열 보안 | A | 이중 검증, 크로스 이벤트 방지, 사용자 바인딩 |
| OWASP 대응 | B+ | 대부분 대응, 기본 자격증명/비밀번호 정책 보완 필요 |

---

## 13. 강점 및 약점

### 강점

1. **3-Tier 토큰 아키텍처**: CDN(Tier 1) -> Gateway(Tier 2) -> Backend의 심층 방어 체계가 티켓팅 시스템의 핵심인 대기열 보안을 견고하게 보호한다.

2. **Refresh Token Rotation with Reuse Detection**: Family-based 토큰 회전과 재사용 감지는 RFC 권장사항을 충실히 따르며, 토큰 탈취 시 자동 감지 및 전체 세션 무효화가 가능하다.

3. **Gateway 레벨 헤더 스푸핑 방지**: JwtAuthFilter가 모든 외부 X-User-* 헤더를 제거한 후 JWT 검증 결과로만 재주입하여, 다운스트림 서비스가 JWT_SECRET 없이도 안전하게 사용자 정보를 받을 수 있다.

4. **Timing-safe 비교 전면 적용**: 내부 API 토큰(InternalApiAuthFilter), CloudFront 시크릿(VwrEntryTokenFilter), Lambda@Edge JWT 검증 모두에서 `MessageDigest.isEqual` 또는 `crypto.timingSafeEqual`을 사용한다.

5. **인프라 수준 비밀 관리**: Terraform Secrets Manager 모듈로 모든 비밀을 자동 생성하고, sensitive 마킹하여 Terraform state에서도 보호한다.

6. **카테고리별 차등 Rate Limiting**: AUTH(60rpm), BOOKING(30rpm), QUEUE(120rpm), GENERAL(3000rpm)으로 엔드포인트 민감도에 맞는 차등 제한을 적용한다.

7. **K8s Default Deny-All 네트워크 정책**: 기본 차단 후 필요한 트래픽만 허용하는 화이트리스트 방식으로, 서비스 간 불필요한 통신을 원천 차단한다.

### 약점

1. **[CRITICAL] 하드코딩된 관리자 비밀번호**: `V3__seed_admin.sql`에 BCrypt 해시가 소스 코드에 포함되어 있다. Git 이력에 영구 노출되며, 프로덕션 배포 후 비밀번호 변경을 강제하는 메커니즘이 없다.
   - **파일**: `services-spring/auth-service/src/main/resources/db/migration/V3__seed_admin.sql` (라인 3-13)
   - **권장**: 마이그레이션에서 관리자 시드를 제거하고, 별도 초기화 스크립트로 환경변수 기반 비밀번호 설정

2. **[HIGH] OAuth 사용자 더미 비밀번호**: Google OAuth 사용자의 password_hash에 평문 `"OAUTH_USER_NO_PASSWORD"`가 저장된다.
   - **파일**: `services-spring/auth-service/src/main/java/guru/urr/authservice/service/AuthService.java` (라인 233)
   - **권장**: password_hash 컬럼을 nullable로 변경하거나, OAuth 사용자 전용 마커 사용

3. **[MEDIUM] COOKIE_SECURE 기본값 false**: 프로덕션에서 HTTPS 강제와 함께 반드시 true로 설정해야 한다.
   - **파일**: `services-spring/auth-service/src/main/java/guru/urr/authservice/util/CookieHelper.java` (라인 19)
   - **권장**: 프로덕션 프로필에서 기본값을 true로 변경

4. **[MEDIUM] VWR API Lambda CORS 와일드카드 폴백**: `CORS_ORIGIN` 환경변수 미설정 시 `*`로 폴백된다.
   - **파일**: `lambda/vwr-api/index.js` (라인 8)
   - **권장**: 기본값 제거, 환경변수 미설정 시 에러 발생하도록 변경

5. **[MEDIUM] 백엔드 Egress 네트워크 정책 과도**: `tier: backend` Pod이 같은 네임스페이스의 모든 Pod에 접근 가능하다.
   - **파일**: `k8s/spring/base/network-policies.yaml` (라인 190-191)
   - **권장**: 서비스별 필요한 대상만 명시적으로 허용

6. **[MEDIUM] Entry Token이 일회성이 아님**: 만료 전까지 동일 토큰으로 여러 번 요청 가능하다. 좌석 예약 같은 민감한 작업에서 replay 위험이 있다.
   - **권장**: 중요 작업에 대해 Idempotency Key 기반 중복 방지 적용 (일부 구현됨: `reservationsApi.createTicketOnly`에서 `idempotencyKey` 사용)

7. **[LOW] Rate Limiting Fail-open**: Redis 장애 시 모든 요청을 허용한다.
   - **파일**: `services-spring/gateway-service/src/main/java/guru/urr/gatewayservice/filter/RateLimitFilter.java` (라인 94-98)
   - **권장**: 가용성과 보안의 트레이드오프를 명시적으로 문서화하고, 인메모리 폴백 카운터 도입 검토

8. **[LOW] 비밀번호 복잡성 규칙 부재**: 최소 8자만 요구하고 대/소문자, 숫자, 특수문자 요구가 없다.
   - **파일**: `services-spring/auth-service/src/main/java/guru/urr/authservice/dto/RegisterRequest.java` (라인 9)

---

## 14. AWS 환경 특화 권고사항

### 14.1 즉시 조치 필요 (P0)

| 항목 | 현재 상태 | 권고 |
|------|----------|------|
| 관리자 시드 비밀번호 | 소스 코드에 하드코딩 | 마이그레이션에서 제거, AWS Secrets Manager에서 초기 비밀번호 주입, 첫 로그인 시 변경 강제 |
| COOKIE_SECURE | 기본 false | 프로덕션 K8s ConfigMap에서 `COOKIE_SECURE=true` 설정 확인 |
| VWR API CORS | 기본 `*` | Terraform에서 `CORS_ORIGIN` 환경변수 필수 설정 |

### 14.2 단기 개선 (P1, 1-2주)

| 항목 | 권고 |
|------|------|
| ALB 보안 그룹 | CloudFront Managed Prefix List만 허용하여 직접 ALB 접근 차단 |
| WAF 적용 | AWS WAF를 CloudFront 배포에 연결하여 SQL Injection, XSS 필터링 |
| Secrets Rotation | Secrets Manager 자동 회전(Auto-Rotation) 활성화 (RDS, Redis 자격증명) |
| CloudTrail 감사 | Secrets Manager 접근, IAM 변경 등 보안 이벤트 감사 로깅 |
| Lambda@Edge Rate Limiting | CloudFront의 Custom 요청 제한 또는 Shield Advanced 활용 |

### 14.3 중기 개선 (P2, 1-3개월)

| 항목 | 권고 |
|------|------|
| Network Policy 세분화 | 백엔드 egress를 서비스별로 명시적 허용 (현재 `podSelector: {}` 제거) |
| RDS IAM 인증 | 정적 비밀번호 대신 IAM 역할 기반 DB 인증으로 전환 |
| VPC Endpoints | S3, DynamoDB, SQS, Secrets Manager에 대한 VPC Endpoint 추가로 NAT Gateway 경유 없는 프라이빗 접근 |
| ElastiCache TLS | Redis 연결에 in-transit 암호화(TLS) 활성화 |
| Audit Log 시스템 | 관리자 행위, 토큰 발급/폐기, 대기열 조작 등에 대한 전용 감사 로그 파이프라인 구축 (CloudWatch Logs -> S3 -> Athena) |
| OAuth 사용자 모델 개선 | password_hash nullable 변경, 인증 방식 컬럼 추가 |

### 14.4 장기 개선 (P3, 3-6개월)

| 항목 | 권고 |
|------|------|
| AWS Cognito 또는 OIDC 통합 | 자체 인증 서비스 대신 관리형 인증 서비스로 전환 검토 (MFA, 계정 복구, 비밀번호 정책 자동 관리) |
| Service Mesh (Istio) | K8s 서비스 간 mTLS 통신으로 INTERNAL_API_TOKEN 기반 인증 대체 |
| Security Hub 통합 | AWS Security Hub에서 전체 보안 상태 통합 모니터링 |
| Penetration Test | 외부 보안 전문 업체를 통한 침투 테스트 수행 |

---

> 분석 종료. 본 문서는 코드 리뷰 기반의 정적 분석 결과이며, 런타임 환경의 실제 설정값(환경변수, Terraform state 등)에 따라 보안 상태가 달라질 수 있다. 프로덕션 배포 전 동적 보안 테스트(DAST) 및 침투 테스트를 병행할 것을 권장한다.
