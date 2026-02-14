# 보안 분석

URR(tiketi) 티켓팅 플랫폼의 보안 아키텍처를 분석한 문서이다. 모든 출처는 실제 소스 코드의 파일 경로와 라인 번호를 기반으로 한다.

---

## 1. JWT 토큰 관리

JWT 기반 무상태(stateless) 인증 체계를 사용한다. Access Token과 Refresh Token을 분리하여 발급하며, HMAC-SHA256 알고리즘으로 서명한다.

### 1.1 서명 키 생성

서명 키는 Base64 디코딩을 우선 시도하고, 실패 시 원시 바이트로 폴백한다. 최소 32바이트 키 길이를 강제한다.

```java
private Key signingKey() {
    byte[] keyBytes;
    try {
        keyBytes = Decoders.BASE64.decode(jwtProperties.secret());
    } catch (IllegalArgumentException ex) {
        log.warn("JWT_SECRET is not valid Base64, using raw bytes");
        keyBytes = jwtProperties.secret().getBytes(StandardCharsets.UTF_8);
    }
    if (keyBytes.length < 32) {
        throw new IllegalArgumentException("JWT_SECRET must be at least 32 bytes");
    }
    return Keys.hmacShaKeyFor(keyBytes);
}
```

**출처:** `services-spring/auth-service/src/main/java/com/tiketi/authservice/security/JwtService.java:80-92`

### 1.2 Access Token

- 만료 시간: `expirationSeconds` 속성 기반 (설정값에 따름)
- Claims 구성: `userId`, `email`, `role`, `type=access`
- Subject: `user.getId().toString()`

```java
return Jwts.builder()
    .subject(user.getId().toString())
    .claim("userId", user.getId().toString())
    .claim("email", user.getEmail())
    .claim("role", user.getRole().name())
    .claim("type", "access")
    .issuedAt(now)
    .expiration(expiry)
    .signWith(signingKey())
    .compact();
```

**출처:** `services-spring/auth-service/src/main/java/com/tiketi/authservice/security/JwtService.java:27-42`

### 1.3 Refresh Token

- 만료 시간: `refreshTokenExpirationSeconds` 속성 기반
- Claims 구성: `userId`, `type=refresh` (email, role 미포함)
- 검증 시 `type` 클레임이 `refresh`가 아니면 예외 발생

```java
public Claims validateRefreshToken(String token) {
    Claims claims = parse(token);
    String type = claims.get("type", String.class);
    if (!"refresh".equals(type)) {
        throw new io.jsonwebtoken.JwtException("Token is not a refresh token");
    }
    return claims;
}
```

**출처:** `services-spring/auth-service/src/main/java/com/tiketi/authservice/security/JwtService.java:44-78`

### 1.4 설정 속성

JWT 관련 속성은 `JwtProperties` 레코드를 통해 `app.security.jwt` 접두사로 바인딩된다.

```java
@ConfigurationProperties(prefix = "app.security.jwt")
public record JwtProperties(String secret, long expirationSeconds, long refreshTokenExpirationSeconds) {
}
```

**출처:** `services-spring/auth-service/src/main/java/com/tiketi/authservice/config/JwtProperties.java:5-7`

---

## 2. 인증 필터 체인

Spring Security 필터 체인은 무상태 세션 정책과 CSRF 비활성화를 기반으로 구성된다.

### 2.1 필터 순서

`SecurityConfig`에서 두 개의 커스텀 필터를 `UsernamePasswordAuthenticationFilter` 이전에 등록한다.

```java
http
    .csrf(csrf -> csrf.disable())
    .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
    .authorizeHttpRequests(auth -> auth
        .requestMatchers("/health", "/actuator/health", "/actuator/info").permitAll()
        .requestMatchers("/api/auth/register", "/api/auth/login", "/api/auth/verify-token",
                         "/api/auth/google", "/api/auth/refresh", "/api/auth/logout").permitAll()
        .requestMatchers("/internal/**").permitAll()
        .anyRequest().authenticated())
    .addFilterBefore(internalApiAuthFilter, UsernamePasswordAuthenticationFilter.class)
    .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
```

**출처:** `services-spring/auth-service/src/main/java/com/tiketi/authservice/config/SecurityConfig.java:32-50`

### 2.2 필터 실행 순서

1. `InternalApiAuthFilter` -- `/internal/**` 경로에만 적용
2. `JwtAuthenticationFilter` -- 모든 요청에 적용 (Bearer 토큰이 없으면 통과)

### 2.3 공개 엔드포인트 화이트리스트

| 경로 | 용도 |
|------|------|
| `/health`, `/actuator/health`, `/actuator/info` | 헬스체크 |
| `/api/auth/register` | 회원가입 |
| `/api/auth/login` | 로그인 |
| `/api/auth/verify-token` | 토큰 검증 |
| `/api/auth/google` | Google OAuth |
| `/api/auth/refresh` | 토큰 갱신 |
| `/api/auth/logout` | 로그아웃 |
| `/internal/**` | 내부 서비스 간 통신 |

### 2.4 JWT 인증 필터 동작

`Authorization: Bearer <token>` 헤더가 존재하면 토큰을 파싱하여 `SecurityContext`에 인증 정보를 설정한다. 토큰이 없으면 필터 체인을 그대로 통과시킨다. 검증 실패 시 `SecurityContext`를 초기화한다.

```java
String token = authHeader.substring(7);
try {
    Claims claims = jwtService.parse(token);
    String userId = claims.get("userId", String.class);
    UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
        userId, null, Collections.emptyList());
    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
    SecurityContextHolder.getContext().setAuthentication(authentication);
} catch (JwtException e) {
    log.debug("JWT validation failed: {}", e.getMessage());
    SecurityContextHolder.clearContext();
}
```

**출처:** `services-spring/auth-service/src/main/java/com/tiketi/authservice/security/JwtAuthenticationFilter.java:34-53`

---

## 3. 내부 API 인증

서비스 간(inter-service) 통신을 위한 정적 토큰 기반 인증 메커니즘이다.

### 3.1 InternalApiAuthFilter

`/internal/` 경로에만 적용되며, `x-internal-token` 헤더 또는 `Authorization: Bearer` 헤더에서 토큰을 추출한다.

```java
@Override
protected boolean shouldNotFilter(HttpServletRequest request) {
    return !request.getRequestURI().startsWith("/internal/");
}
```

토큰 추출 우선순위:
1. `x-internal-token` 헤더
2. `Authorization: Bearer` 헤더 (폴백)

```java
String token = request.getHeader("x-internal-token");
if (token == null || token.isBlank()) {
    String authHeader = request.getHeader("Authorization");
    if (authHeader != null && authHeader.startsWith("Bearer ")) {
        token = authHeader.substring(7);
    }
}
```

**출처:** `services-spring/auth-service/src/main/java/com/tiketi/authservice/security/InternalApiAuthFilter.java:28-34`

### 3.2 타이밍 세이프 비교

토큰 비교에 `MessageDigest.isEqual()`을 사용하여 타이밍 공격(timing attack)을 방지한다.

```java
if (token == null || !MessageDigest.isEqual(
        expectedToken.getBytes(StandardCharsets.UTF_8),
        token.getBytes(StandardCharsets.UTF_8))) {
    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.getWriter().write("{\"error\":\"Forbidden\"}");
    return;
}
```

**출처:** `services-spring/auth-service/src/main/java/com/tiketi/authservice/security/InternalApiAuthFilter.java:37-44`

### 3.3 InternalTokenValidator

별도의 유효성 검증 컴포넌트로, 다른 서비스에서 재사용 가능한 형태이다. 동일하게 타이밍 세이프 비교를 수행한다.

```java
public boolean isValid(String authorization, String xInternalToken) {
    if (xInternalToken != null && !xInternalToken.isBlank()) {
        return timingSafeEquals(internalToken, xInternalToken);
    }
    if (authorization == null || !authorization.startsWith("Bearer ")) {
        return false;
    }
    String token = authorization.substring(7);
    return timingSafeEquals(internalToken, token);
}

private static boolean timingSafeEquals(String a, String b) {
    return MessageDigest.isEqual(
            a.getBytes(StandardCharsets.UTF_8),
            b.getBytes(StandardCharsets.UTF_8));
}
```

**출처:** `services-spring/auth-service/src/main/java/com/tiketi/authservice/security/InternalTokenValidator.java:17-32`

---

## 4. API 게이트웨이 보안

게이트웨이 서비스는 모든 외부 요청의 단일 진입점으로, Rate Limiting, CORS, VWR 토큰 검증 필터를 순서대로 적용한다.

### 4.1 Rate Limiting

#### 4.1.1 필터 설정

`@Order(0)`으로 가장 먼저 실행되며, Redis Lua 스크립트를 통해 원자적(atomic) 속도 제한을 수행한다.

**출처:** `services-spring/gateway-service/src/main/java/com/tiketi/gatewayservice/filter/RateLimitFilter.java:27-28`

#### 4.1.2 카테고리별 제한

| 카테고리 | 기본값 (RPM) | 적용 경로 |
|----------|-------------|-----------|
| AUTH | 60 | `/api/v1/auth/**`, `/api/auth/**` |
| QUEUE | 120 | `/api/v1/queue/**`, `/api/queue/**` |
| BOOKING | 30 | `/api/v1/seats/reserve`, `/api/reservations` 등 쓰기 작업 |
| GENERAL | 3000 | 그 외 모든 경로 |

**출처:** `services-spring/gateway-service/src/main/resources/application.yml:115-119`

#### 4.1.3 클라이언트 식별

JWT에서 subject를 추출하여 `user:<userId>` 형태로 식별한다. JWT가 없거나 파싱 실패 시 `ip:<remoteAddr>`로 폴백한다. `X-Forwarded-For` 헤더는 스푸핑 가능성 때문에 의도적으로 사용하지 않는다.

```java
private String resolveClientId(HttpServletRequest request) {
    String authHeader = request.getHeader("Authorization");
    if (authHeader != null && authHeader.startsWith("Bearer ")) {
        String token = authHeader.substring(7);
        String subject = extractSubjectFromJwt(token);
        if (subject != null) {
            return "user:" + subject;
        }
    }
    return "ip:" + request.getRemoteAddr();
}
```

**출처:** `services-spring/gateway-service/src/main/java/com/tiketi/gatewayservice/filter/RateLimitFilter.java:134-147`

#### 4.1.4 Redis Lua 스크립트 (Sliding Window)

Sorted Set 기반 슬라이딩 윈도우 알고리즘을 사용한다. 60초 윈도우 내 요청 수를 원자적으로 확인한다.

```lua
local key = KEYS[1]
local now = tonumber(ARGV[1])
local window = tonumber(ARGV[2])
local limit = tonumber(ARGV[3])
local req_id = ARGV[4]
local cutoff = now - window
redis.call('ZREMRANGEBYSCORE', key, '-inf', cutoff)
redis.call('ZADD', key, now, req_id)
local count = redis.call('ZCARD', key)
redis.call('EXPIRE', key, math.ceil(window / 1000) + 1)
if count > limit then
    redis.call('ZREM', key, req_id)
    return 0
end
return 1
```

**출처:** `services-spring/gateway-service/src/main/resources/redis/rate_limit.lua:1-15`

#### 4.1.5 Fail-Open 정책

Redis 장애 시 요청을 차단하지 않고 통과시키는 fail-open 정책을 채택한다. 가용성을 보안보다 우선시하는 설계이다.

```java
} catch (Exception e) {
    log.warn("Rate limit check failed (fail-open), allowing request: {}", e.getMessage());
    filterChain.doFilter(request, response);
    return;
}
```

**출처:** `services-spring/gateway-service/src/main/java/com/tiketi/gatewayservice/filter/RateLimitFilter.java:125-129`

#### 4.1.6 /auth/me 면제

`/auth/me` 엔드포인트는 페이지 네비게이션마다 호출되는 읽기 전용 본인 확인 API이므로 rate limiting에서 면제한다. `shouldNotFilter`와 `doFilterInternal` 양쪽에서 중복 확인한다 (Spring Cloud Gateway MVC의 `shouldNotFilter` 호출 불안정성 대응).

**출처:** `services-spring/gateway-service/src/main/java/com/tiketi/gatewayservice/filter/RateLimitFilter.java:76-99`

### 4.2 CORS 설정

```java
CorsConfiguration config = new CorsConfiguration();
List<String> origins = Arrays.asList(allowedOrigins.split(","));
config.setAllowedOrigins(origins);
config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
config.setAllowedHeaders(List.of("Authorization", "Content-Type", "x-queue-entry-token"));
config.setAllowCredentials(true);
config.setMaxAge(3600L);
```

| 항목 | 값 |
|------|-----|
| Allowed Origins | 환경변수 `CORS_ALLOWED_ORIGINS` (기본: `http://localhost:3000`) |
| Allowed Methods | GET, POST, PUT, DELETE, PATCH, OPTIONS |
| Allowed Headers | Authorization, Content-Type, x-queue-entry-token |
| Allow Credentials | true |
| Max Age | 3600초 (1시간) |

**출처:** `services-spring/gateway-service/src/main/java/com/tiketi/gatewayservice/config/CorsConfig.java:20-32`

### 4.3 VWR(Virtual Waiting Room) 토큰 검증

`@Order(1)`로 Rate Limit 필터 다음에 실행된다. 좌석 예약 및 결제 관련 쓰기 작업에 대해 대기열 진입 토큰을 검증한다.

#### 4.3.1 적용 범위

POST, PUT, PATCH 메서드에 대해 `/api/seats/` 또는 `/api/reservations` 경로만 보호한다.

```java
private static final Set<String> PROTECTED_METHODS = Set.of("POST", "PUT", "PATCH");

private boolean isProtectedPath(String path) {
    return path.startsWith("/api/seats/") || path.startsWith("/api/reservations");
}
```

**출처:** `services-spring/gateway-service/src/main/java/com/tiketi/gatewayservice/filter/VwrEntryTokenFilter.java:34, 117-119`

#### 4.3.2 토큰 검증 로직

`x-queue-entry-token` 헤더에서 JWT를 추출하고, 서명을 검증한 뒤 사용자 ID 바인딩을 확인한다.

```java
Claims vwrClaims = Jwts.parser()
        .verifyWith(signingKey)
        .build()
        .parseSignedClaims(token)
        .getPayload();

// VWR 토큰의 uid와 Auth JWT의 subject가 일치하는지 확인
String vwrUserId = vwrClaims.get("uid", String.class);
if (vwrUserId != null && jwtKey != null) {
    String authUserId = extractAuthUserId(request);
    if (authUserId != null && !vwrUserId.equals(authUserId)) {
        log.warn("VWR token userId mismatch: token uid={} auth sub={} path={}",
                vwrUserId, authUserId, request.getRequestURI());
        sendForbiddenResponse(response);
        return;
    }
}
```

**출처:** `services-spring/gateway-service/src/main/java/com/tiketi/gatewayservice/filter/VwrEntryTokenFilter.java:85-101`

#### 4.3.3 CloudFront 바이패스

CDN 에지에서 이미 검증된 요청은 `X-CloudFront-Verified` 헤더의 비밀값을 타이밍 세이프 비교하여 필터를 건너뛸 수 있다.

```java
if (cloudFrontSecret != null) {
    String cfHeader = request.getHeader(CF_VERIFIED_HEADER);
    if (cfHeader != null && MessageDigest.isEqual(
            cloudFrontSecret.getBytes(StandardCharsets.UTF_8),
            cfHeader.getBytes(StandardCharsets.UTF_8))) {
        filterChain.doFilter(request, response);
        return;
    }
}
```

**출처:** `services-spring/gateway-service/src/main/java/com/tiketi/gatewayservice/filter/VwrEntryTokenFilter.java:66-74`

#### 4.3.4 서명 키 요구사항

VWR 토큰 서명 키는 최소 32바이트를 요구한다.

```java
private static SecretKey buildSigningKey(String secret) {
    byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
    if (secretBytes.length < HMAC_MIN_KEY_LENGTH) {
        throw new IllegalArgumentException(
                "QUEUE_ENTRY_TOKEN_SECRET must be at least " + HMAC_MIN_KEY_LENGTH + " bytes");
    }
    return new SecretKeySpec(secretBytes, "HmacSHA256");
}
```

**출처:** `services-spring/gateway-service/src/main/java/com/tiketi/gatewayservice/filter/VwrEntryTokenFilter.java:156-165`

---

## 5. 프론트엔드 보안

Next.js 미들웨어를 통해 모든 응답에 보안 헤더를 주입한다.

### 5.1 Content Security Policy (CSP)

요청별로 고유한 nonce를 생성하여 CSP 헤더에 포함한다.

```typescript
const nonce = Buffer.from(crypto.randomUUID()).toString("base64");

const cspHeader = [
  "default-src 'self'",
  "script-src 'self' 'unsafe-inline'",
  `style-src 'self' 'unsafe-inline' 'nonce-${nonce}'`,
  "img-src 'self' data: https:",
  "connect-src 'self' http://localhost:* https://*.tiketi.com",
  "frame-ancestors 'none'",
].join("; ");
```

| 지시어 | 값 | 비고 |
|--------|-----|------|
| `default-src` | `'self'` | 기본 동일 출처 제한 |
| `script-src` | `'self' 'unsafe-inline'` | Next.js RSC 인라인 스크립트 호환 |
| `style-src` | `'self' 'unsafe-inline' 'nonce-...'` | nonce 기반 스타일 허용 |
| `img-src` | `'self' data: https:` | HTTPS 이미지 허용 |
| `connect-src` | `'self' http://localhost:* https://*.tiketi.com` | API 연결 허용 |
| `frame-ancestors` | `'none'` | 프레임 삽입 차단 |

**출처:** `apps/web/src/middleware.ts:5-17`

### 5.2 추가 보안 헤더

```typescript
response.headers.set("X-Frame-Options", "DENY");
response.headers.set("X-Content-Type-Options", "nosniff");
response.headers.set("Referrer-Policy", "strict-origin-when-cross-origin");
```

| 헤더 | 값 | 목적 |
|------|-----|------|
| `X-Frame-Options` | `DENY` | 클릭재킹 방지 |
| `X-Content-Type-Options` | `nosniff` | MIME 타입 스니핑 방지 |
| `Referrer-Policy` | `strict-origin-when-cross-origin` | 리퍼러 정보 누출 제한 |

**출처:** `apps/web/src/middleware.ts:24-26`

### 5.3 미들웨어 적용 범위

정적 리소스(`_next/static`, `_next/image`, `favicon.ico`)와 프리페치 요청은 제외한다.

```typescript
export const config = {
  matcher: [
    { source: "/((?!_next/static|_next/image|favicon.ico).*)",
      missing: [{ type: "header", key: "next-router-prefetch" }] },
  ],
};
```

**출처:** `apps/web/src/middleware.ts:31-35`

---

## 6. 동시성 보안 (Seat Locking)

좌석 예약 시 발생할 수 있는 경쟁 조건(race condition)을 방지하기 위해 Redis 기반 분산 락과 Fencing Token 패턴을 사용한다.

### 6.1 Fencing Token 패턴

좌석 잠금 획득 시 단조 증가(monotonically increasing) fencing token을 발급하여, 이전 락 소유자의 지연된 요청이 현재 락을 침범하는 것을 방지한다.

### 6.2 좌석 잠금 획득 (seat_lock_acquire.lua)

```lua
-- KEYS[1] = seat:{eventId}:{seatId}      (HASH: status, userId, token, heldAt)
-- KEYS[2] = seat:{eventId}:{seatId}:token_seq  (fencing token counter)
-- ARGV[1] = userId
-- ARGV[2] = ttl (seconds)

local seatKey = KEYS[1]
local tokenSeqKey = KEYS[2]
local userId = ARGV[1]
local ttl = tonumber(ARGV[2])

-- 1. 현재 상태 확인
local status = redis.call('HGET', seatKey, 'status')
if status == 'HELD' or status == 'CONFIRMED' then
    local currentUser = redis.call('HGET', seatKey, 'userId')
    if currentUser == userId then
        -- 동일 사용자 재선택: TTL 연장, 기존 토큰 반환
        redis.call('EXPIRE', seatKey, ttl)
        local existingToken = redis.call('HGET', seatKey, 'token')
        return {1, existingToken}
    end
    return {0, '-1'}  -- 실패: 다른 사용자가 점유 중
end

-- 2. 단조 증가 fencing token 생성
local token = redis.call('INCR', tokenSeqKey)

-- 3. 원자적 상태 전이: AVAILABLE -> HELD
redis.call('HMSET', seatKey,
    'status', 'HELD',
    'userId', userId,
    'token', token,
    'heldAt', tostring(redis.call('TIME')[1])
)
redis.call('EXPIRE', seatKey, ttl)

return {1, token}
```

**출처:** `services-spring/ticket-service/src/main/resources/redis/seat_lock_acquire.lua:1-36`

### 6.3 좌석 잠금 해제 (seat_lock_release.lua)

동일 사용자이면서 동일 fencing token인 경우에만 잠금을 해제한다.

```lua
local currentUserId = redis.call('HGET', seatKey, 'userId')
local currentToken = redis.call('HGET', seatKey, 'token')

if currentUserId ~= userId or currentToken ~= token then
    return 0
end

redis.call('DEL', seatKey)
return 1
```

**출처:** `services-spring/ticket-service/src/main/resources/redis/seat_lock_release.lua:9-18`

### 6.4 결제 검증 (payment_verify.lua)

결제 처리 전 잠금 소유권과 fencing token을 검증하고, 상태를 `CONFIRMED`로 전이하여 결제 중 잠금 해제를 방지한다.

```lua
local currentUserId = redis.call('HGET', seatKey, 'userId')
local currentToken = redis.call('HGET', seatKey, 'token')

if currentUserId ~= userId or currentToken ~= token then
    return 0  -- 실패: 잠금 만료 또는 탈취
end

redis.call('HSET', seatKey, 'status', 'CONFIRMED')
return 1
```

**출처:** `services-spring/ticket-service/src/main/resources/redis/payment_verify.lua:9-19`

### 6.5 Java 서비스 계층

`SeatLockService`는 Redis Lua 스크립트를 호출하는 래퍼로, 잠금 획득/해제/결제 검증/정리를 담당한다. 기본 TTL은 300초(5분)이다.

```java
public record SeatLockResult(boolean success, long fencingToken) {}

public SeatLockResult acquireLock(UUID eventId, UUID seatId, String userId) {
    String seatKey = seatKey(eventId, seatId);
    String tokenSeqKey = seatKey + ":token_seq";
    // ...Redis Lua 스크립트 실행...
}
```

`fencingToken`이 `-1`인 경우 결제 검증을 차단한다:

```java
public boolean verifyForPayment(UUID eventId, UUID seatId, String userId, long token) {
    if (token == -1) {
        log.error("Cannot verify payment: seat was locked without Redis (fencingToken=-1)");
        return false;
    }
    // ...
}
```

**출처:** `services-spring/ticket-service/src/main/java/com/tiketi/ticketservice/domain/seat/service/SeatLockService.java:23, 39-63, 81-85`

---

## 7. Pod 보안

모든 백엔드 서비스 Deployment에 Kubernetes Pod 보안 정책이 적용된다.

### 7.1 Pod SecurityContext

```yaml
spec:
  securityContext:
    runAsNonRoot: true
    runAsUser: 1000
    fsGroup: 1000
  containers:
    - name: auth-service
      securityContext:
        allowPrivilegeEscalation: false
        readOnlyRootFilesystem: false
        capabilities:
          drop:
            - ALL
```

| 설정 | 값 | 목적 |
|------|-----|------|
| `runAsNonRoot` | `true` | root 실행 방지 |
| `runAsUser` | `1000` | 비특권 사용자로 실행 |
| `fsGroup` | `1000` | 파일 시스템 그룹 제한 |
| `allowPrivilegeEscalation` | `false` | 권한 상승 차단 |
| `capabilities.drop` | `ALL` | 모든 Linux 커널 capability 제거 |

**출처:** `k8s/spring/base/auth-service/deployment.yaml:18-31`
**출처:** `k8s/spring/base/ticket-service/deployment.yaml:18-31`

### 7.2 Dockerfile 비루트 사용자

컨테이너 이미지 빌드 시 UID 1001의 전용 `app` 사용자를 생성하여 실행한다.

```dockerfile
FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=build /workspace/build/libs/*.jar app.jar

RUN addgroup --system --gid 1001 app && adduser --system --uid 1001 --ingroup app app
USER app

EXPOSE 3005
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
```

**출처:** `services-spring/auth-service/Dockerfile:13-22`

---

## 8. 네트워크 정책

Kubernetes NetworkPolicy를 통해 마이크로서비스 간 네트워크 통신을 제한한다.

### 8.1 기본 정책: 모든 트래픽 거부

```yaml
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

모든 Pod에 대해 인그레스와 이그레스를 기본적으로 차단한다.

**출처:** `k8s/spring/base/network-policies.yaml:1-9`

### 8.2 서비스별 인그레스 허용 목록

| 서비스 | 포트 | 허용된 출발지 |
|--------|------|--------------|
| gateway-service | 3001 | 모든 출처 (외부 진입점) |
| frontend | 3000 | 모든 출처 (외부 진입점) |
| auth-service | 3005 | gateway-service, catalog-service |
| ticket-service | 3002 | gateway-service, payment-service, catalog-service |
| catalog-service | 3009 | gateway-service, queue-service |
| payment-service | 3003 | gateway-service |
| stats-service | 3004 | gateway-service |
| queue-service | 3007 | gateway-service |
| community-service | 3008 | gateway-service |

**출처:** `k8s/spring/base/network-policies.yaml:11-176`

### 8.3 이그레스 정책

백엔드 서비스(`tier: backend` 라벨)는 동일 네임스페이스 내 Pod와 DNS(kube-dns, UDP/TCP 53)로의 이그레스만 허용된다.

```yaml
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: allow-backend-egress
spec:
  podSelector:
    matchLabels:
      tier: backend
  policyTypes:
    - Egress
  egress:
    - to:
        - podSelector: {}
    - to:
        - namespaceSelector: {}
          podSelector:
            matchLabels:
              k8s-app: kube-dns
      ports:
        - port: 53
          protocol: UDP
        - port: 53
          protocol: TCP
```

**출처:** `k8s/spring/base/network-policies.yaml:179-201`

### 8.4 게이트웨이 이그레스

게이트웨이 서비스는 `tier: backend` 라벨의 Pod와 DNS로만 이그레스가 허용된다.

**출처:** `k8s/spring/base/network-policies.yaml:203-226`

---

## 9. 시크릿 관리

Kubernetes Secret을 Kustomize `secretGenerator`를 통해 관리한다.

### 9.1 시크릿 생성 방식

`kustomization.yaml`에서 `secrets.env` 파일을 기반으로 Kubernetes Secret 리소스를 생성한다. `disableNameSuffixHash: true`로 해시 접미사를 비활성화한다.

```yaml
secretGenerator:
  - name: spring-kind-secret
    envs:
      - secrets.env

generatorOptions:
  disableNameSuffixHash: true
```

**출처:** `k8s/spring/overlays/kind/kustomization.yaml:24-30`

### 9.2 관리 대상 시크릿

| 시크릿 키 | 용도 |
|----------|------|
| `POSTGRES_USER` / `POSTGRES_PASSWORD` | PostgreSQL 접속 정보 |
| `AUTH_DB_USERNAME` / `AUTH_DB_PASSWORD` | Auth 서비스 DB 접속 |
| `TICKET_DB_USERNAME` / `TICKET_DB_PASSWORD` | Ticket 서비스 DB 접속 |
| `PAYMENT_DB_USERNAME` / `PAYMENT_DB_PASSWORD` | Payment 서비스 DB 접속 |
| `STATS_DB_USERNAME` / `STATS_DB_PASSWORD` | Stats 서비스 DB 접속 |
| `JWT_SECRET` | JWT 서명 키 (Base64 인코딩) |
| `INTERNAL_API_TOKEN` | 내부 서비스 간 인증 토큰 |
| `QUEUE_ENTRY_TOKEN_SECRET` | VWR 대기열 진입 토큰 서명 키 |
| `TOSS_CLIENT_KEY` | 결제 서비스 (Toss Payments) 클라이언트 키 |

**출처:** `k8s/spring/overlays/kind/secrets.env:1-14`

### 9.3 환경변수 주입

각 서비스의 Deployment 패치에서 Secret 리소스를 `envFrom`으로 참조하여 환경변수로 주입한다. 서비스 코드에서는 `@Value("${JWT_SECRET}")`, `@Value("${INTERNAL_API_TOKEN}")` 등으로 접근한다.

---

## 보안 아키텍처 요약

```
[클라이언트]
    |
    | CSP, X-Frame-Options, nosniff (Next.js 미들웨어)
    v
[프론트엔드 (Next.js)]
    |
    | CORS 검증
    v
[API 게이트웨이]
    |-- RateLimitFilter (@Order 0): Redis Lua 슬라이딩 윈도우
    |-- VwrEntryTokenFilter (@Order 1): 대기열 토큰 검증 (쓰기 작업)
    |-- CookieAuthFilter: 쿠키 기반 인증 전달
    v
[백엔드 서비스]
    |-- InternalApiAuthFilter: /internal/** 경로 보호
    |-- JwtAuthenticationFilter: Bearer 토큰 인증
    |-- NetworkPolicy: 서비스 간 통신 제한
    |-- Pod SecurityContext: 비루트 실행, capability 제거
    v
[데이터 계층]
    |-- Redis: 좌석 잠금 (Fencing Token), Rate Limiting
    |-- PostgreSQL: SELECT FOR UPDATE (비관적 락)
    |-- K8s Secret: 시크릿 환경변수 주입
```
