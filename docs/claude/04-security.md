# 04. 보안 분석

본 문서는 URR 티켓팅 플랫폼의 보안 아키텍처를 분석한다. 인증 체계, 서비스 간 통신 보안, 네트워크 정책, 입력 검증 등 전 계층에 걸친 보안 메커니즘을 코드 수준에서 검토한다.

---

## 1. 인증 체계

### 1.1 JWT 토큰 구조

Access Token은 auth-service의 `JwtService`에서 발급된다. 토큰 생성 시 사용자 엔티티로부터 다음 Claims를 포함한다.

| Claim | 값 | 설명 |
|-------|-----|------|
| `sub` | `user.getId()` | JWT 표준 subject (사용자 ID) |
| `userId` | `user.getId()` | 커스텀 클레임 (다운스트림 서비스 참조용) |
| `email` | `user.getEmail()` | 사용자 이메일 |
| `role` | `user.getRole().name()` | 사용자 역할 (USER / ADMIN) |
| `type` | `"access"` | 토큰 유형 구분자 |
| `iat` | 발급 시각 | JWT 표준 issued-at |
| `exp` | 발급 시각 + 만료 시간 | JWT 표준 expiration |

> 참조: `services-spring/auth-service/src/main/java/guru/urr/authservice/security/JwtService.java:31-46`

**서명 알고리즘**: HMAC-SHA256. `signingKey()` 메서드에서 `JWT_SECRET` 환경 변수를 Base64 디코딩하여 키를 생성한다. Base64 디코딩 실패 시 원시 바이트로 폴백하며, 키 길이가 32바이트 미만이면 `IllegalArgumentException`을 발생시킨다.

> 참조: `services-spring/auth-service/src/main/java/guru/urr/authservice/security/JwtService.java:104-116`

**만료 시간**: `JwtProperties` 레코드에서 `expirationSeconds` 값을 읽어 밀리초로 변환한다. 기본값은 `application.yml`에서 1800초(30분)로 설정되어 있다.

> 참조: `services-spring/auth-service/src/main/java/guru/urr/authservice/config/JwtProperties.java:5-7`
> 참조: `services-spring/auth-service/src/main/resources/application.yml:51`

### 1.2 Refresh Token

Refresh Token은 Access Token과 동일한 서명 키를 사용하되, 별도의 Claims 구조를 갖는다.

| Claim | 값 | 설명 |
|-------|-----|------|
| `sub` | `user.getId()` | 사용자 ID |
| `userId` | `user.getId()` | 사용자 ID (커스텀) |
| `type` | `"refresh"` | 토큰 유형 (access와 구분) |
| `familyId` | UUID | 토큰 패밀리 ID (회전 추적용) |
| `jti` | UUID | 고유 토큰 ID |

> 참조: `services-spring/auth-service/src/main/java/guru/urr/authservice/security/JwtService.java:52-67`

**만료 시간**: `refreshTokenExpirationSeconds` 값을 사용하며, 기본값은 604800초(7일)이다.

> 참조: `services-spring/auth-service/src/main/resources/application.yml:52`

**쿠키 전송**: Refresh Token은 `CookieHelper`를 통해 httpOnly 쿠키로 전송된다. 쿠키 속성은 다음과 같다.

| 속성 | 값 | 설명 |
|------|-----|------|
| `httpOnly` | `true` | JavaScript 접근 차단 (XSS 방어) |
| `secure` | `COOKIE_SECURE` 환경 변수 | HTTPS 전용 전송 (프로덕션에서 true) |
| `path` | `/api/auth` | 인증 엔드포인트에서만 전송 |
| `maxAge` | 604800 | 7일 |
| `SameSite` | `Lax` | 크로스 사이트 요청 제한 |

> 참조: `services-spring/auth-service/src/main/java/guru/urr/authservice/util/CookieHelper.java:38-46`

Access Token 쿠키도 동일한 패턴으로 설정되며, `path`는 `"/"`로 전체 경로에 전송된다.

> 참조: `services-spring/auth-service/src/main/java/guru/urr/authservice/util/CookieHelper.java:28-36`

`AuthController`의 `refresh()` 엔드포인트는 `@CookieValue`로 쿠키에서 Refresh Token을 읽거나, 요청 본문의 `refreshToken` 필드를 폴백으로 사용한다. 갱신 성공 시 새로운 Access Token과 Refresh Token 쿠키를 재설정한다.

> 참조: `services-spring/auth-service/src/main/java/guru/urr/authservice/controller/AuthController.java:67-83`

**Refresh Token 검증**: `validateRefreshToken()` 메서드에서 `type` 클레임이 `"refresh"`인지 확인하여, Access Token이 Refresh Token으로 오용되는 것을 방지한다.

> 참조: `services-spring/auth-service/src/main/java/guru/urr/authservice/security/JwtService.java:95-102`

**토큰 해싱**: Refresh Token은 데이터베이스 저장 시 SHA-256으로 해싱된다. 원본 토큰은 서버에 저장되지 않으므로, 데이터베이스가 유출되어도 토큰을 재사용할 수 없다.

> 참조: `services-spring/auth-service/src/main/java/guru/urr/authservice/security/JwtService.java:73-81`

### 1.3 게이트웨이 중앙 인증

게이트웨이의 `JwtAuthFilter`는 `@Order(-1)`로 필터 체인에서 가장 먼저 실행되어, 모든 요청에 대해 JWT 검증과 사용자 정보 주입을 수행한다. 이 중앙 집중식 설계 덕분에 다운스트림 서비스는 JWT_SECRET을 알 필요가 없다.

> 참조: `services-spring/gateway-service/src/main/java/guru/urr/gatewayservice/filter/JwtAuthFilter.java:33-35`

**스푸핑 방지**: 필터는 항상 `UserHeaderStrippingWrapper`를 통해 외부에서 주입된 `X-User-Id`, `X-User-Email`, `X-User-Role` 헤더를 제거한다. 이는 JWT 검증 이전 단계에서 수행되므로, 악의적 클라이언트가 헤더를 위조하여 다른 사용자로 위장하는 것을 원천 차단한다.

```java
// 항상 외부 X-User-* 헤더 제거 (스푸핑 방지)
HttpServletRequest sanitized = new UserHeaderStrippingWrapper(request);
```

> 참조: `services-spring/gateway-service/src/main/java/guru/urr/gatewayservice/filter/JwtAuthFilter.java:53-54`

**헤더 스트리핑 구현**: `UserHeaderStrippingWrapper`는 `HttpServletRequestWrapper`를 상속하여 `getHeader()`, `getHeaders()`, `getHeaderNames()` 메서드를 오버라이드한다. 대소문자 무관 비교로 세 개의 보호 헤더를 차단한다.

> 참조: `services-spring/gateway-service/src/main/java/guru/urr/gatewayservice/filter/JwtAuthFilter.java:104-137`

**JWT 파싱 및 헤더 주입**: Authorization 헤더에서 Bearer 토큰을 추출하고, `Jwts.parser()`로 서명을 검증한 후 Claims에서 `userId`, `email`, `role`을 추출한다. `userId`가 유효하면 `UserHeaderInjectionWrapper`를 통해 `X-User-Id`, `X-User-Email`, `X-User-Role` 헤더를 요청에 주입한다.

> 참조: `services-spring/gateway-service/src/main/java/guru/urr/gatewayservice/filter/JwtAuthFilter.java:56-86`

**키 생성**: `buildKey()` 메서드는 Base64 디코딩을 먼저 시도하고, 실패 시 UTF-8 원시 바이트로 폴백한다. 키 길이가 32바이트 미만이면 `null`을 반환하여 JWT 검증을 비활성화한다(개발 환경 지원).

> 참조: `services-spring/gateway-service/src/main/java/guru/urr/gatewayservice/filter/JwtAuthFilter.java:88-98`

### 1.4 다운스트림 사용자 추출

각 다운스트림 서비스는 `JwtTokenParser` 컴포넌트를 통해 게이트웨이가 주입한 `X-User-*` 헤더에서 사용자 정보를 추출한다. 이 클래스는 JWT를 직접 파싱하지 않으며, 게이트웨이의 검증 결과만 신뢰한다.

**requireUser()**: `X-User-Id` 헤더가 없거나 비어 있으면 `401 UNAUTHORIZED`를 반환한다. 유효한 경우 `AuthUser` 레코드를 생성하여 반환한다.

> 참조: `services-spring/ticket-service/src/main/java/guru/urr/ticketservice/shared/security/JwtTokenParser.java:11-18`

**requireAdmin()**: `requireUser()`를 먼저 호출한 후, `AuthUser.isAdmin()`으로 역할을 검증한다. admin이 아니면 `403 FORBIDDEN`을 반환한다.

> 참조: `services-spring/ticket-service/src/main/java/guru/urr/ticketservice/shared/security/JwtTokenParser.java:20-27`

**AuthUser 레코드**: `isAdmin()` 메서드는 `"admin".equalsIgnoreCase(role)`로 대소문자 무관 비교를 수행한다.

> 참조: `services-spring/ticket-service/src/main/java/guru/urr/ticketservice/shared/security/AuthUser.java:3-7`

이 패턴은 모든 다운스트림 서비스(ticket-service, payment-service, stats-service 등)에서 동일하게 사용된다.

### 1.5 프론트엔드 인증

**세션 복원**: `AuthProvider`는 마운트 시 `authApi.me()` 호출로 서버에서 현재 사용자 정보를 가져온다. httpOnly 쿠키가 자동 전송되므로, 페이지 새로고침 후에도 세션이 유지된다.

> 참조: `apps/web/src/lib/auth-context.tsx:27-51`

**Silent Refresh**: Axios 응답 인터셉터에서 401 응답을 감지하면 자동으로 `/auth/refresh` 요청을 보내 토큰을 갱신한다. 갱신 성공 시 원래 요청을 재시도한다.

```typescript
if (error.response?.status === 401 && !originalRequest._retry) {
    originalRequest._retry = true;
    isRefreshing = true;
    try {
        await http.post("/auth/refresh");
        processQueue(true);
        return http(originalRequest);
    } catch { ... }
}
```

> 참조: `apps/web/src/lib/api-client.ts:79-116`

**요청 큐잉**: Refresh 진행 중 발생하는 추가 401 응답은 `failedQueue` 배열에 대기시킨다. Refresh 완료 후 `processQueue(true)`로 대기 중인 모든 요청을 재시도한다. 이 패턴은 동시 다발적 API 호출 시 Refresh Token이 중복 사용되는 것을 방지한다.

> 참조: `apps/web/src/lib/api-client.ts:52-68`

**인증 엔드포인트 예외**: login, register, refresh 엔드포인트 자체의 401 응답에 대해서는 refresh를 시도하지 않아 무한 루프를 방지한다.

> 참조: `apps/web/src/lib/api-client.ts:87-90`

**429 자동 재시도**: Rate Limit 응답(429)에 대해 최대 2회까지 지수 백오프(1초, 2초, 4초)로 자동 재시도한다.

> 참조: `apps/web/src/lib/api-client.ts:119-127`

---

## 2. 내부 API 보안

### 2.1 INTERNAL_API_TOKEN

서비스 간 내부 API 호출은 공유 시크릿(`INTERNAL_API_TOKEN`) 기반으로 인증된다. 이 메커니즘은 외부 사용자가 내부 전용 엔드포인트에 접근하는 것을 차단한다.

**ticket-service의 InternalTokenValidator**:

`requireValidToken()` 메서드는 `Authorization: Bearer {token}` 형식의 헤더를 검증한다. 헤더가 없거나 Bearer 접두사가 없으면 `401 UNAUTHORIZED`, 토큰 불일치 시 `403 FORBIDDEN`을 반환한다.

> 참조: `services-spring/ticket-service/src/main/java/guru/urr/ticketservice/shared/security/InternalTokenValidator.java:19-27`

**타이밍 공격 방지**: 문자열 비교에 `MessageDigest.isEqual()`을 사용한다. 이 메서드는 입력 길이에 관계없이 일정한 시간에 비교를 수행하므로, 공격자가 응답 시간 차이를 관찰하여 토큰을 추론하는 타이밍 공격(timing attack)을 방지한다.

```java
private static boolean timingSafeEquals(String a, String b) {
    return MessageDigest.isEqual(
            a.getBytes(StandardCharsets.UTF_8),
            b.getBytes(StandardCharsets.UTF_8));
}
```

> 참조: `services-spring/ticket-service/src/main/java/guru/urr/ticketservice/shared/security/InternalTokenValidator.java:29-33`

**payment-service의 InternalTokenValidator**: ticket-service와 동일한 구현이다. 동일한 `INTERNAL_API_TOKEN` 환경 변수를 사용하며, 동일한 timing-safe 비교 로직을 적용한다.

> 참조: `services-spring/payment-service/src/main/java/guru/urr/paymentservice/security/InternalTokenValidator.java:29-33`

**auth-service의 InternalApiAuthFilter**: auth-service는 필터 기반으로 내부 API를 보호한다. `/internal/` 경로에만 적용되며, `x-internal-token` 헤더 또는 `Authorization: Bearer` 헤더에서 토큰을 추출한다. 동일하게 `MessageDigest.isEqual()`로 timing-safe 비교를 수행한다.

> 참조: `services-spring/auth-service/src/main/java/guru/urr/authservice/security/InternalApiAuthFilter.java:25-47`
> 참조: `services-spring/auth-service/src/main/java/guru/urr/authservice/security/InternalApiAuthFilter.java:50-52`

### 2.2 VWR 입장 토큰

VWR(Virtual Waiting Room) 입장 토큰은 대기열을 통과한 사용자만 좌석 예약/예매 API에 접근할 수 있도록 보장하는 메커니즘이다.

**토큰 생성 (queue-service)**: `QueueService.generateEntryToken()` 메서드에서 HMAC-SHA256 JWT를 생성한다.

| 속성 | 값 | 설명 |
|------|-----|------|
| `subject` | eventId | 대상 이벤트 ID |
| `uid` | userId | 토큰 소유자 사용자 ID |
| `iat` | 현재 시각 | 발급 시각 |
| `exp` | 현재 + TTL | 만료 시각 |

> 참조: `services-spring/queue-service/src/main/java/guru/urr/queueservice/service/QueueService.java:215-227`

**TTL**: `queue.entry-token.ttl-seconds` 설정값이며, 기본값은 600초(10분)이다.

> 참조: `services-spring/queue-service/src/main/java/guru/urr/queueservice/service/QueueService.java:48`

**서명 키**: `queue.entry-token.secret` 환경 변수를 UTF-8 바이트로 변환하여 HMAC-SHA256 키를 생성한다. 최소 32바이트 이상이어야 한다.

> 참조: `services-spring/queue-service/src/main/java/guru/urr/queueservice/service/QueueService.java:56`

**토큰 검증 (gateway VwrEntryTokenFilter)**: 게이트웨이의 `VwrEntryTokenFilter`(`@Order(1)`)에서 좌석/예매 관련 POST/PUT/PATCH 요청을 가로채어 입장 토큰을 검증한다.

**보호 경로**: `/api/seats/`로 시작하거나 `/api/reservations`로 시작하는 경로의 POST, PUT, PATCH 메서드만 보호한다.

> 참조: `services-spring/gateway-service/src/main/java/guru/urr/gatewayservice/filter/VwrEntryTokenFilter.java:33`
> 참조: `services-spring/gateway-service/src/main/java/guru/urr/gatewayservice/filter/VwrEntryTokenFilter.java:113-115`

**userId 바인딩 검증**: VWR 토큰의 `uid` 클레임과 `JwtAuthFilter`가 주입한 `X-User-Id` 헤더를 비교한다. 불일치 시 403 응답을 반환하여, 다른 사용자의 입장 토큰을 도용하는 것을 방지한다.

```java
String vwrUserId = vwrClaims.get("uid", String.class);
if (vwrUserId != null) {
    String authUserId = extractAuthUserId(request);
    if (authUserId != null && !vwrUserId.equals(authUserId)) {
        sendForbiddenResponse(response);
        return;
    }
}
```

> 참조: `services-spring/gateway-service/src/main/java/guru/urr/gatewayservice/filter/VwrEntryTokenFilter.java:87-97`

**CloudFront 바이패스**: 프로덕션 환경에서 CloudFront Lambda@Edge가 CDN 레벨에서 이미 토큰을 검증한 경우, `X-CloudFront-Verified` 헤더의 값이 `cloudfront.secret`과 일치하면 게이트웨이에서의 중복 검증을 건너뛴다. 이때도 `MessageDigest.isEqual()`로 timing-safe 비교를 수행한다.

> 참조: `services-spring/gateway-service/src/main/java/guru/urr/gatewayservice/filter/VwrEntryTokenFilter.java:62-70`

**키 생성 시 길이 검증**: `buildSigningKey()` 메서드에서 시크릿이 32바이트 미만이면 `IllegalArgumentException`을 발생시켜 애플리케이션 시작을 차단한다.

> 참조: `services-spring/gateway-service/src/main/java/guru/urr/gatewayservice/filter/VwrEntryTokenFilter.java:130-139`

---

## 3. CORS 정책

게이트웨이의 `CorsConfig`에서 CORS 정책을 중앙 관리한다. 모든 다운스트림 서비스에 대한 CORS 규칙이 게이트웨이 한 곳에서 적용된다.

| 설정 | 값 | 설명 |
|------|-----|------|
| 허용 오리진 | `${cors.allowed-origins}` (기본: `http://localhost:3000`) | 쉼표 구분 복수 오리진 지원 |
| 허용 메서드 | GET, POST, PUT, DELETE, PATCH, OPTIONS | 모든 REST 메서드 |
| 허용 헤더 | Authorization, Content-Type, x-queue-entry-token | 인증, 콘텐츠 타입, VWR 토큰 |
| Credentials | `true` | 쿠키 전송 허용 |
| Max-Age | 3600초 (1시간) | 프리플라이트 캐시 시간 |

> 참조: `services-spring/gateway-service/src/main/java/guru/urr/gatewayservice/config/CorsConfig.java:16-32`

**오리진 설정**: `cors.allowed-origins` 환경 변수(`CORS_ALLOWED_ORIGINS`)로 외부에서 주입하며, 쉼표로 구분된 복수 오리진을 `Arrays.asList()`로 파싱한다.

> 참조: `services-spring/gateway-service/src/main/java/guru/urr/gatewayservice/config/CorsConfig.java:22`
> 참조: `services-spring/gateway-service/src/main/resources/application.yml:113`

**경로 적용**: `/**` 패턴으로 모든 경로에 동일한 CORS 정책을 적용한다.

> 참조: `services-spring/gateway-service/src/main/java/guru/urr/gatewayservice/config/CorsConfig.java:30`

---

## 4. Rate Limiting

### 4.1 구현

게이트웨이의 `RateLimitFilter`(`@Order(0)`)는 Redis 기반 슬라이딩 윈도우 알고리즘으로 요청 속도를 제한한다. `JwtAuthFilter`(`@Order(-1)`) 이후, `VwrEntryTokenFilter`(`@Order(1)`) 이전에 실행된다.

> 참조: `services-spring/gateway-service/src/main/java/guru/urr/gatewayservice/filter/RateLimitFilter.java:21-22`

**Lua 스크립트**: 원자적(atomic) 실행을 보장하는 Redis Lua 스크립트를 사용한다.

```lua
local key = KEYS[1]
local now = tonumber(ARGV[1])
local window = tonumber(ARGV[2])
local limit = tonumber(ARGV[3])
local req_id = ARGV[4]
local cutoff = now - window
redis.call('ZREMRANGEBYSCORE', key, '-inf', cutoff)  -- 만료된 항목 제거
redis.call('ZADD', key, now, req_id)                  -- 현재 요청 추가
local count = redis.call('ZCARD', key)                 -- 윈도우 내 요청 수
redis.call('EXPIRE', key, math.ceil(window / 1000) + 1)
if count > limit then
    redis.call('ZREM', key, req_id)  -- 초과 시 롤백
    return 0                          -- 거부
end
return 1                              -- 허용
```

> 참조: `services-spring/gateway-service/src/main/resources/redis/rate_limit.lua:1-15`

**윈도우 크기**: 60,000ms (60초).

> 참조: `services-spring/gateway-service/src/main/java/guru/urr/gatewayservice/filter/RateLimitFilter.java:26`

**Fail-open 정책**: Redis 연결 실패 시 요청을 차단하지 않고 허용한다. 가용성을 보안보다 우선시하는 설계이다.

```java
} catch (Exception e) {
    log.warn("Rate limit check failed (fail-open), allowing request: {}", e.getMessage());
    filterChain.doFilter(request, response);
    return;
}
```

> 참조: `services-spring/gateway-service/src/main/java/guru/urr/gatewayservice/filter/RateLimitFilter.java:94-98`

### 4.2 카테고리별 제한

`application.yml`의 환경 변수로 카테고리별 RPM(Requests Per Minute)을 설정한다.

| 카테고리 | RPM | 대상 경로 | 환경 변수 |
|----------|-----|-----------|-----------|
| AUTH | 60 | `/api/v1/auth/*`, `/api/auth/*` | `RATE_LIMIT_AUTH_RPM` |
| QUEUE | 120 | `/api/v1/queue/*`, `/api/queue/*` | `RATE_LIMIT_QUEUE_RPM` |
| BOOKING | 30 | `/api/v1/seats/reserve`, `/api/v1/reservations`, `*/cancel` | `RATE_LIMIT_BOOKING_RPM` |
| GENERAL | 3000 | 위에 해당하지 않는 모든 경로 | `RATE_LIMIT_GENERAL_RPM` |

> 참조: `services-spring/gateway-service/src/main/resources/application.yml:115-119`

**카테고리 분류 로직**: `resolveCategory()` 메서드에서 요청 경로 접두사로 카테고리를 결정한다. BOOKING 카테고리는 정확한 경로 일치와 정규식 패턴을 조합하여 예약/취소 엔드포인트만 선택적으로 보호한다.

> 참조: `services-spring/gateway-service/src/main/java/guru/urr/gatewayservice/filter/RateLimitFilter.java:113-128`

### 4.3 클라이언트 식별

`resolveClientId()` 메서드에서 클라이언트를 식별한다.

- **인증 사용자**: `JwtAuthFilter`가 주입한 `X-User-Id` 헤더를 사용하여 `user:{userId}` 형식의 키를 생성한다.
- **비인증 사용자**: `request.getRemoteAddr()`로 IP 주소를 가져와 `ip:{remote_addr}` 형식의 키를 생성한다.

> 참조: `services-spring/gateway-service/src/main/java/guru/urr/gatewayservice/filter/RateLimitFilter.java:103-111`

### 4.4 면제 경로

다음 경로는 Rate Limiting에서 제외된다.

- `/api/v1/auth/me`, `/api/auth/me` -- 세션 확인 (페이지 로드마다 호출)
- `/health` -- 헬스체크
- `/actuator/*` -- 모니터링

> 참조: `services-spring/gateway-service/src/main/java/guru/urr/gatewayservice/filter/RateLimitFilter.java:52-56`

### 4.5 응답

Rate Limit 초과 시 다음 JSON 응답을 반환한다.

- **HTTP 상태**: `429 Too Many Requests`
- **응답 본문**: `{"error":"Rate limit exceeded","retryAfter":60}`
- **Retry-After**: 60초

> 참조: `services-spring/gateway-service/src/main/java/guru/urr/gatewayservice/filter/RateLimitFilter.java:139-146`

---

## 5. 입력 검증

### 5.1 Bean Validation (Jakarta Validation)

Spring의 `@Valid` 어노테이션과 Jakarta Bean Validation 제약 조건을 사용하여 요청 데이터를 검증한다.

**RegisterRequest 예시**:

```java
public record RegisterRequest(
    @NotBlank @Email String email,
    @NotBlank @Size(min = 8, message = "Password must be at least 8 characters") String password,
    @NotBlank String name,
    String phone
) {}
```

> 참조: `services-spring/auth-service/src/main/java/guru/urr/authservice/dto/RegisterRequest.java:7-13`

**LoginRequest 예시**:

```java
public record LoginRequest(
    @NotBlank @Email String email,
    @NotBlank String password
) {}
```

> 참조: `services-spring/auth-service/src/main/java/guru/urr/authservice/dto/LoginRequest.java:6-10`

**SeatReserveRequest 예시**:

```java
public record SeatReserveRequest(
    @NotNull UUID eventId,
    @NotNull List<UUID> seatIds,
    String idempotencyKey
) {}
```

> 참조: `services-spring/ticket-service/src/main/java/guru/urr/ticketservice/domain/reservation/dto/SeatReserveRequest.java:7-12`

**CreateReservationRequest 예시**: `@Valid`를 중첩 객체 목록에도 적용하여 하위 항목까지 검증한다.

```java
public record CreateReservationRequest(
    @NotNull UUID eventId,
    @NotNull @Valid List<ReservationItemRequest> items,
    String idempotencyKey
) {}
```

> 참조: `services-spring/ticket-service/src/main/java/guru/urr/ticketservice/domain/reservation/dto/CreateReservationRequest.java:8-13`

컨트롤러에서는 `@Valid @RequestBody`로 검증을 트리거한다.

> 참조: `services-spring/auth-service/src/main/java/guru/urr/authservice/controller/AuthController.java:40`

### 5.2 SQL 인젝션 방지

JPA/Hibernate를 사용하여 모든 데이터베이스 쿼리가 파라미터화된 PreparedStatement로 실행된다. 사용자 입력이 SQL 문자열에 직접 결합되지 않으므로 SQL 인젝션이 구조적으로 방지된다.

### 5.3 DDL 검증 모드

Hibernate의 `ddl-auto` 설정이 `validate`로 되어 있어, 애플리케이션 시작 시 엔티티와 데이터베이스 스키마의 일치 여부만 검증한다. 자동 스키마 변경(create, update)이 비활성화되어 있으므로, 악의적이거나 의도치 않은 스키마 변경이 방지된다. 스키마 마이그레이션은 Flyway로만 수행된다.

> 참조: `services-spring/auth-service/src/main/resources/application.yml:10`
> 참조: `services-spring/ticket-service/src/main/resources/application.yml:27`

---

## 6. XSS 방지

### 6.1 React JSX 자동 이스케이프

프론트엔드는 Next.js(React) 기반이며, React의 JSX는 렌더링 시 문자열 값을 자동으로 HTML 이스케이프한다. `<`, `>`, `&`, `"`, `'` 등의 특수 문자가 엔티티로 변환되므로, 사용자 입력이 HTML/JavaScript로 해석되지 않는다.

### 6.2 dangerouslySetInnerHTML 미사용

코드베이스에서 `dangerouslySetInnerHTML`을 사용하지 않는다. 이 속성은 원시 HTML을 직접 렌더링하여 XSS 취약점을 유발할 수 있으나, 본 프로젝트에서는 사용하지 않음으로써 해당 위험을 원천 차단한다.

### 6.3 쿠키 설정 방식

인증 토큰은 서버 측 `Set-Cookie` HTTP 헤더를 통해 설정된다. `httpOnly: true` 속성으로 JavaScript에서 `document.cookie`로 접근할 수 없으므로, XSS 공격으로 토큰이 탈취되는 경로가 차단된다.

> 참조: `services-spring/auth-service/src/main/java/guru/urr/authservice/util/CookieHelper.java:30`
> 참조: `services-spring/auth-service/src/main/java/guru/urr/authservice/util/CookieHelper.java:40`

---

## 7. CSRF

### 7.1 비활성화 근거

Spring Security의 CSRF 보호는 auth-service에서 명시적으로 비활성화되어 있다.

```java
http
    .csrf(csrf -> csrf.disable())
    .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
```

> 참조: `services-spring/auth-service/src/main/java/guru/urr/authservice/config/SecurityConfig.java:33-35`

**비활성화 사유**:

1. **무상태 인증**: `SessionCreationPolicy.STATELESS`로 서버 측 세션을 사용하지 않는다. CSRF 공격은 브라우저가 자동 전송하는 세션 쿠키를 악용하는 것이 핵심인데, JWT 기반 무상태 인증에서는 세션 쿠키가 존재하지 않는다.
2. **쿠키 SameSite 속성**: 인증 쿠키에 `SameSite=Lax` 속성이 설정되어 있어, 크로스 사이트 POST/PUT/DELETE 요청에서 쿠키가 전송되지 않는다.

> 참조: `services-spring/auth-service/src/main/java/guru/urr/authservice/util/CookieHelper.java:34`
> 참조: `services-spring/auth-service/src/main/java/guru/urr/authservice/util/CookieHelper.java:44`

3. **CORS 정책**: 허용된 오리진에서만 요청이 가능하므로, 임의의 악성 사이트에서의 요청이 차단된다.

---

## 8. 보안 헤더

Next.js 미들웨어에서 모든 페이지 응답에 보안 헤더를 주입한다.

### 8.1 Content Security Policy (CSP)

```
default-src 'self';
script-src 'self' 'unsafe-inline' https://accounts.google.com https://apis.google.com;
style-src 'self' 'unsafe-inline' 'nonce-{random}' https://accounts.google.com;
img-src 'self' data: https:;
connect-src 'self' http://localhost:* https://*.urr.guru https://accounts.google.com;
frame-src https://accounts.google.com;
frame-ancestors 'none';
```

> 참조: `apps/web/src/middleware.ts:10-18`

**script-src 'unsafe-inline' 사용 사유**: Next.js RSC(React Server Components)가 빌드/프리렌더 시 인라인 스크립트를 생성하는데, 이 스크립트들은 nonce를 부여할 수 없다. 주석에 해당 사유가 명시되어 있다.

> 참조: `apps/web/src/middleware.ts:7-9`

**nonce 기반 style-src**: `crypto.randomUUID()`로 요청마다 고유 nonce를 생성하여 스타일 태그에 적용한다.

> 참조: `apps/web/src/middleware.ts:5`

### 8.2 기타 보안 헤더

| 헤더 | 값 | 설명 |
|------|-----|------|
| `X-Frame-Options` | `DENY` | 모든 iframe 삽입 차단 (클릭재킹 방지) |
| `X-Content-Type-Options` | `nosniff` | MIME 타입 스니핑 차단 |
| `Referrer-Policy` | `strict-origin-when-cross-origin` | 크로스 오리진 시 오리진만 전송 |
| `Permissions-Policy` | `camera=(), microphone=(), geolocation=(), payment=()` | 카메라, 마이크, 위치, 결제 API 비활성화 |

> 참조: `apps/web/src/middleware.ts:25-28`

### 8.3 적용 범위

미들웨어는 `_next/static`, `_next/image`, `favicon.ico`를 제외한 모든 경로에 적용되며, `next-router-prefetch` 헤더가 없는 요청에만 실행된다.

> 참조: `apps/web/src/middleware.ts:33-37`

---

## 9. 시크릿 관리

### 9.1 개발 환경

개발/Kind 클러스터 환경에서는 `secrets.env` 파일로 시크릿을 관리한다. 예시 파일에서 관리되는 시크릿 목록은 다음과 같다.

| 시크릿 | 용도 |
|--------|------|
| `POSTGRES_USER`, `POSTGRES_PASSWORD` | PostgreSQL 공통 인증 |
| `AUTH_DB_USERNAME`, `AUTH_DB_PASSWORD` | auth-service DB 인증 |
| `TICKET_DB_USERNAME`, `TICKET_DB_PASSWORD` | ticket-service DB 인증 |
| `PAYMENT_DB_USERNAME`, `PAYMENT_DB_PASSWORD` | payment-service DB 인증 |
| `STATS_DB_USERNAME`, `STATS_DB_PASSWORD` | stats-service DB 인증 |
| `COMMUNITY_DB_USERNAME`, `COMMUNITY_DB_PASSWORD` | community-service DB 인증 |
| `JWT_SECRET` | JWT 서명 키 (Base64, 최소 32바이트) |
| `INTERNAL_API_TOKEN` | 서비스 간 인증 토큰 |
| `QUEUE_ENTRY_TOKEN_SECRET` | VWR 입장 토큰 서명 키 (최소 32자) |
| `TOSS_CLIENT_KEY` | 결제 서비스 API 키 |

> 참조: `k8s/spring/overlays/kind/secrets.env.example:1-39`

시크릿은 Kubernetes Secret으로 생성된 후 환경 변수로 Pod에 주입된다.

### 9.2 프로덕션 권장 사항

프로덕션 환경에서는 External Secrets Operator와 AWS Secrets Manager 연동을 권장한다.

**External Secrets Operator (권장 방법)**:

1. Helm으로 External Secrets Operator 설치
2. IRSA(IAM Roles for Service Accounts)로 Secrets Manager 접근 권한 부여
3. `SecretStore` 리소스로 AWS Secrets Manager 연결
4. `ExternalSecret` 리소스로 AWS 시크릿을 Kubernetes Secret에 매핑
5. Pod에서 환경 변수로 참조

> 참조: `k8s/AWS_SECRETS_INTEGRATION.md:26-155`

**IRSA 구성**: Terraform IAM 모듈에서 `secretsmanager:GetSecretValue`와 `secretsmanager:DescribeSecret` 권한을 특정 시크릿 ARN에 대해서만 부여한다. 최소 권한 원칙을 따른다.

> 참조: `k8s/AWS_SECRETS_INTEGRATION.md:66-84`

**Secret Rotation 지원**: `ExternalSecret`의 `refreshInterval: 1h` 설정으로 AWS Secrets Manager의 시크릿 변경을 1시간마다 동기화한다.

> 참조: `k8s/AWS_SECRETS_INTEGRATION.md:117`

**대안: Secrets Store CSI Driver**: 파일 시스템 마운트 방식으로 시크릿을 주입하는 대안도 문서화되어 있다.

> 참조: `k8s/AWS_SECRETS_INTEGRATION.md:158-230`

**보안 모범 사례** (문서 기재):
- IRSA 사용 (정적 AWS 자격 증명 금지)
- 정기적 시크릿 로테이션
- Pod별 최소 권한 시크릿 접근
- Redis TLS 전송 암호화
- CloudTrail 감사 로깅
- YAML에 시크릿 하드코딩 금지

> 참조: `k8s/AWS_SECRETS_INTEGRATION.md:301-308`

---

## 10. 네트워크 정책

Kubernetes NetworkPolicy를 통해 Pod 간 트래픽을 제한한다.

### 10.1 기본 정책: 전면 거부

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

네임스페이스 내 모든 Pod에 대해 인그레스와 이그레스를 기본 거부한다. 이후 명시적으로 허용하는 화이트리스트 방식을 적용한다.

> 참조: `k8s/spring/base/network-policies.yaml:1-9`

### 10.2 인그레스 정책

| 서비스 | 허용 출발지 | 포트 | 정책 이름 |
|--------|-------------|------|-----------|
| gateway-service | 외부 전체 (제한 없음) | 3001 | `allow-gateway-ingress` |
| frontend | 외부 전체 (제한 없음) | 3000 | `allow-frontend-ingress` |
| auth-service | gateway-service, catalog-service | 3005 | `allow-auth-service-ingress` |
| ticket-service | gateway-service, payment-service, catalog-service | 3002 | `allow-ticket-service-ingress` |
| catalog-service | gateway-service, queue-service | 3009 | `allow-catalog-service-ingress` |
| payment-service | gateway-service | 3003 | `allow-payment-service-ingress` |
| stats-service | gateway-service | 3004 | `allow-stats-service-ingress` |
| queue-service | gateway-service | 3007 | `allow-queue-service-ingress` |
| community-service | gateway-service | 3008 | `allow-community-service-ingress` |

> 참조: `k8s/spring/base/network-policies.yaml:11-177`

**핵심 설계**: 게이트웨이와 프론트엔드만 외부 접근이 가능하고, 백엔드 서비스는 게이트웨이 또는 특정 내부 서비스에서만 접근할 수 있다. 예를 들어 ticket-service는 gateway-service(사용자 요청 라우팅), payment-service(결제 완료 후 상태 갱신), catalog-service(이벤트 정보 조회)에서만 접근 가능하다.

> 참조: `k8s/spring/base/network-policies.yaml:61-83`

### 10.3 이그레스 정책

**백엔드 이그레스**: `tier: backend` 라벨이 있는 Pod은 다음을 허용한다.

1. 동일 네임스페이스 내 모든 Pod 간 통신 (서비스 간, DB, Redis, Kafka 포함)
2. DNS 쿼리 (kube-dns, UDP/TCP 53번 포트)

> 참조: `k8s/spring/base/network-policies.yaml:179-201`

**게이트웨이 이그레스**: 게이트웨이는 `tier: backend` 라벨이 있는 Pod과 DNS 서비스에 대한 이그레스만 허용된다.

> 참조: `k8s/spring/base/network-policies.yaml:203-226`

---

## 보안 아키텍처 요약

```
클라이언트 요청
    |
    v
[Next.js Middleware] -- CSP, X-Frame-Options, nosniff 헤더 주입
    |
    v
[Gateway - JwtAuthFilter @Order(-1)] -- X-User-* 헤더 스트리핑 + JWT 파싱 + 헤더 주입
    |
    v
[Gateway - RateLimitFilter @Order(0)] -- Redis Lua 슬라이딩 윈도우 Rate Limiting
    |
    v
[Gateway - VwrEntryTokenFilter @Order(1)] -- VWR 입장 토큰 검증 (좌석/예매 경로만)
    |
    v
[Gateway - CorsFilter] -- CORS 정책 적용
    |
    v
[다운스트림 서비스] -- JwtTokenParser (X-User-Id 헤더 기반 인증)
                    -- InternalTokenValidator (/internal/** 경로 보호)
                    -- @Valid Bean Validation (입력 검증)
                    -- JPA 파라미터화 쿼리 (SQL 인젝션 방지)
```

**보안 계층 정리**:

| 계층 | 메커니즘 | 방어 대상 |
|------|----------|-----------|
| 네트워크 | Kubernetes NetworkPolicy | 무단 Pod 간 접근 |
| 전송 | CORS, SameSite 쿠키 | 크로스 사이트 요청 |
| 인증 | JWT (Access + Refresh), httpOnly 쿠키 | 신원 위조, 토큰 탈취 |
| 인가 | requireAdmin(), 역할 기반 접근 제어 | 권한 상승 |
| 속도 제한 | Redis Lua 슬라이딩 윈도우 | DDoS, 브루트포스 |
| 대기열 | VWR 입장 토큰 (userId 바인딩) | 대기열 우회, 토큰 공유 |
| 서비스 간 | INTERNAL_API_TOKEN (timing-safe 비교) | 내부 API 무단 접근 |
| 입력 | Bean Validation, 파라미터화 쿼리 | SQL 인젝션, 잘못된 입력 |
| 출력 | React 자동 이스케이프, CSP | XSS |
| 헤더 | X-Frame-Options, nosniff, Permissions-Policy | 클릭재킹, MIME 스니핑 |
| 시크릿 | 환경 변수 주입, External Secrets Operator | 시크릿 유출 |
