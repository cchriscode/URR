# Tiketi 티켓팅 시스템 보안 분석 보고서

**문서 버전**: 1.0
**분석 일자**: 2026-02-13
**대상 시스템**: Tiketi Ticketing Platform (Spring Boot 마이크로서비스)
**분석 범위**: 인증, 인가, API 보안, 데이터 보안, 인프라 보안, 프론트엔드 보안

---

## 목차

1. [심각도 등급 정의](#1-심각도-등급-정의)
2. [시스템 보안 아키텍처 개요](#2-시스템-보안-아키텍처-개요)
3. [인증 시스템 분석](#3-인증-시스템-분석)
4. [인가 시스템 분석](#4-인가-시스템-분석)
5. [API 보안 분석](#5-api-보안-분석)
6. [데이터 보안 분석](#6-데이터-보안-분석)
7. [인프라 보안 분석](#7-인프라-보안-분석)
8. [프론트엔드 보안 분석](#8-프론트엔드-보안-분석)
9. [Redis/Lua 보안 분석](#9-redislua-보안-분석)
10. [Lambda@Edge 보안 분석](#10-lambdaedge-보안-분석)
11. [발견 사항 요약](#11-발견-사항-요약)
12. [권장 조치 사항](#12-권장-조치-사항)

---

## 1. 심각도 등급 정의

| 등급 | 표기 | 설명 | 대응 기한 |
|------|------|------|-----------|
| **치명** | `CRITICAL` | 즉시 악용 가능, 데이터 유출 또는 시스템 탈취 위험 | 24시간 이내 |
| **높음** | `HIGH` | 조건부 악용 가능, 권한 상승 또는 서비스 장애 위험 | 1주 이내 |
| **중간** | `MEDIUM` | 제한적 영향, 정보 노출 또는 보안 우회 가능성 | 1개월 이내 |
| **낮음** | `LOW` | 최소 영향, 보안 모범 사례 미준수 | 다음 릴리스 |
| **정보** | `INFO` | 참고 사항, 개선 권고 | 선택적 적용 |

---

## 2. 시스템 보안 아키텍처 개요

### 2.1 트래픽 흐름

```
Client
  |
  v
CloudFront (TLSv1.2_2021, HSTS, Lambda@Edge 토큰 검증)
  |
  v
ALB (CloudFront prefix list 제한, drop_invalid_header_fields)
  |
  v
Gateway Service (Rate Limiting -> VWR Entry Token -> CORS -> Routing)
  |
  v
Backend Services (auth, ticket, payment, stats, queue, community)
  |
  v
PostgreSQL (RDS Proxy, TLS, 암호화 스토리지)
```

### 2.2 보안 계층 요약

| 계층 | 구현 위치 | 핵심 메커니즘 |
|------|-----------|---------------|
| Edge | CloudFront + Lambda@Edge | TLS 종단, HSTS, 큐 토큰 사전 검증 |
| 네트워크 | ALB + Security Groups | CloudFront 전용 접근, 잘못된 헤더 드롭 |
| 게이트웨이 | Gateway Service | Rate Limiting, VWR 토큰, CORS |
| 애플리케이션 | 각 마이크로서비스 | JWT 검증, 역할 기반 접근 제어 |
| 데이터 | RDS + Secrets | 스토리지 암호화, TLS 전송, 환경변수 비밀 관리 |
| 컨테이너 | K8s + Docker | non-root 실행, 권한 상승 차단, NetworkPolicy |

---

## 3. 인증 시스템 분석

### 3.1 JWT 토큰 생성

**파일**: `services-spring/auth-service/src/main/java/com/tiketi/authservice/security/JwtService.java`

#### Access Token (lines 27-42)

```java
public String generateToken(UserEntity user) {
    long expirationMillis = jwtProperties.expirationSeconds() * 1000;
    Date now = new Date();
    Date expiry = new Date(now.getTime() + expirationMillis);

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
}
```

| 항목 | 값 | 평가 |
|------|-----|------|
| 알고리즘 | HMAC-SHA256 | 적정 (대칭키, 단일 발급자 구조에 적합) |
| 만료 시간 | 30분 (`expirationSeconds` 설정) | 적정 |
| Claims | userId, email, role, type | `email` 포함은 토큰 크기 증가 요인 |
| Subject | userId (UUID) | 적정 |

#### Refresh Token (lines 44-57)

```java
public String generateRefreshToken(UserEntity user) {
    long expirationMillis = jwtProperties.refreshTokenExpirationSeconds() * 1000;
    // ...
    return Jwts.builder()
        .subject(user.getId().toString())
        .claim("userId", user.getId().toString())
        .claim("type", "refresh")
        .signWith(signingKey())
        .compact();
}
```

| 항목 | 값 | 평가 |
|------|-----|------|
| 만료 시간 | 7일 | 적정 (장기 세션 유지) |
| Claims | userId, type 만 포함 | 적정 (최소 정보 원칙) |
| 저장 방식 | 클라이언트 측 | `HIGH` - 서버 측 폐기(revocation) 메커니즘 부재 |

#### 서명 키 관리 (lines 80-92)

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

| 점검 항목 | 결과 | 심각도 |
|-----------|------|--------|
| 최소 키 길이 검증 (32바이트) | 통과 | - |
| Base64 디코딩 실패 시 fallback | 경고 로그 후 raw bytes 사용 | `LOW` |
| 키 로테이션 메커니즘 | 미구현 | `MEDIUM` |

**설정 파일**: `services-spring/auth-service/src/main/resources/application.yml` (lines 43-45)

### 3.2 비밀번호 해싱

**파일**: `services-spring/auth-service/src/main/java/com/tiketi/authservice/config/AppConfig.java` (line 13)

| 항목 | 값 | 평가 |
|------|-----|------|
| 알고리즘 | BCrypt | 업계 표준, 적정 |
| 라운드 | 12 | 적정 (기본값 10보다 높음, 약 250ms 해싱 시간) |

### 3.3 토큰 검증 (서비스별)

각 마이크로서비스가 독립적으로 JWT를 검증한다. 동일한 `JWT_SECRET`을 공유하여 서명을 확인한다.

| 서비스 | 파일 | 검증 메서드 | 비고 |
|--------|------|-------------|------|
| **Ticket** | `ticket-service/.../shared/security/JwtTokenParser.java` (lines 24-41) | `requireUser()` + `requireAdmin()` | 일반/관리자 분리 |
| **Payment** | `payment-service/.../security/JwtTokenParser.java` (lines 24-41) | `requireUser()` only | 사용자 전용 |
| **Stats** | `stats-service/.../security/JwtTokenParser.java` (lines 24-44) | `requireAdmin()` only | 관리자 전용 |
| **Queue** | `queue-service/.../shared/security/JwtTokenParser.java` | `requireUser()` + `requireAdmin()` | 일반/관리자 분리 |
| **Auth** | `auth-service/.../security/JwtAuthenticationFilter.java` (lines 31-56) | Spring Security Context 설정 | 필터 체인 통합 |

#### Auth Service JWT 필터 상세 (lines 30-56)

```java
protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
    FilterChain filterChain) throws ServletException, IOException {

    String authHeader = request.getHeader("Authorization");
    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
        filterChain.doFilter(request, response);
        return;
    }

    String token = authHeader.substring(7);
    try {
        Claims claims = jwtService.parse(token);
        String userId = claims.get("userId", String.class);
        UsernamePasswordAuthenticationToken authentication =
            new UsernamePasswordAuthenticationToken(userId, null, Collections.emptyList());
        authentication.setDetails(
            new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    } catch (JwtException e) {
        log.debug("JWT validation failed: {}", e.getMessage());
        SecurityContextHolder.clearContext();
    }

    filterChain.doFilter(request, response);
}
```

| 점검 항목 | 결과 | 심각도 |
|-----------|------|--------|
| 유효하지 않은 토큰 시 SecurityContext 초기화 | 통과 (line 52) | - |
| GrantedAuthority 설정 | `Collections.emptyList()` - 역할 미포함 | `INFO` |
| Bearer 접두사 검증 | 통과 (line 35) | - |

### 3.4 Spring Security 설정

**파일**: `services-spring/auth-service/src/main/java/com/tiketi/authservice/config/SecurityConfig.java`

```java
http
    .csrf(csrf -> csrf.disable())
    .sessionManagement(session ->
        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
    .authorizeHttpRequests(auth -> auth
        .requestMatchers("/health", "/actuator/health", "/actuator/info").permitAll()
        .requestMatchers("/api/auth/register", "/api/auth/login",
            "/api/auth/verify-token", "/api/auth/google", "/api/auth/refresh").permitAll()
        .requestMatchers("/internal/**").permitAll()
        .anyRequest().authenticated())
```

| 점검 항목 | 결과 | 심각도 | 설명 |
|-----------|------|--------|------|
| CSRF 비활성화 | 허용 | - | STATELESS JWT 기반이므로 적절 |
| 세션 정책 | STATELESS | 적정 | - |
| `/internal/**` permitAll | 주의 필요 | `INFO` | InternalApiAuthFilter가 별도 보호 (line 46) |
| 공개 엔드포인트 범위 | 적정 | - | 인증 관련 5개 경로만 허용 |
| Actuator 노출 | health, info만 | 적정 | - |

#### 필터 체인 순서

```
InternalApiAuthFilter → JwtAuthenticationFilter → Spring Security
```

- `InternalApiAuthFilter`는 `UsernamePasswordAuthenticationFilter` 앞에 등록 (line 46)
- `JwtAuthenticationFilter`는 `UsernamePasswordAuthenticationFilter` 앞에 등록 (line 47)

---

## 4. 인가 시스템 분석

### 4.1 역할 모델

| 역할 | 값 | 설명 |
|------|-----|------|
| 일반 사용자 | `user` | 티켓 구매, 대기열 참여, 멤버십 |
| 관리자 | `admin` | 이벤트/아티스트 관리, 통계 조회, 예약 관리 |

**역할 정의**: `auth-service/.../domain/UserRole.java` - enum으로 `user`, `admin` 정의

### 4.2 서버 측 인가 검증

**AuthUser record**: `ticket-service/.../shared/security/AuthUser.java`

- `isAdmin()` 메서드: `equalsIgnoreCase` 비교 사용
- AdminController: 12개 엔드포인트 전부 `requireAdmin()` 호출

| 점검 항목 | 결과 | 심각도 |
|-----------|------|--------|
| 관리자 엔드포인트 일관된 권한 검증 | 통과 (12/12) | - |
| 대소문자 무시 비교 (`equalsIgnoreCase`) | 유연하나 잠재적 불일치 | `LOW` |
| 역할 기반 접근 제어 (RBAC) | 2단계 (user/admin) | `INFO` - 세분화 가능 |

### 4.3 프론트엔드 인가 가드

**파일**: `apps/web/src/components/auth-guard.tsx`

```typescript
export function AuthGuard({ children, adminOnly = false }: Props) {
  // 1단계: 클라이언트 토큰 확인 (line 20)
  if (!token) {
    router.replace("/login");
    return;
  }

  // 2단계: 로컬 역할 검증 (line 25)
  if (adminOnly && user?.role !== "admin") {
    router.replace("/");
    return;
  }

  // 3단계: 서버 검증 - localStorage 조작 방지 (lines 32-45)
  if (adminOnly) {
    authApi.me().then((res) => {
      const serverUser = res.data?.user ?? res.data;
      if (serverUser.role !== "admin") {
        router.replace("/");
      } else {
        setServerVerified(true);
      }
    });
  }
}
```

| 점검 항목 | 결과 | 심각도 |
|-----------|------|--------|
| 서버 측 역할 재검증 | 통과 (`authApi.me()` 호출) | - |
| localStorage 조작 방지 | 통과 (서버 검증 후 렌더링) | - |
| 비관리자 경로 서버 검증 | 미수행 (`!adminOnly` 시 서버 호출 없음) | `LOW` |
| 검증 중 로딩 상태 표시 | 통과 ("Authorizing..." 스피너) | - |

---

## 5. API 보안 분석

### 5.1 Rate Limiting

**파일**: `services-spring/gateway-service/src/main/java/com/tiketi/gatewayservice/filter/RateLimitFilter.java` (183 lines)

#### 구성 요약

| 속성 | 값 | 설명 |
|------|-----|------|
| Filter Order | 0 (최우선) | 다른 필터보다 먼저 실행 |
| 윈도우 | 60초 (sliding window) | `WINDOW_MS = 60_000L` (line 32) |
| 실패 정책 | Fail-closed | Redis 오류 시 요청 거부 (lines 103-106) |
| 응답 코드 | 429 + retryAfter | `RETRY_AFTER_SECONDS = 60` (line 33) |

#### 카테고리별 제한

| 카테고리 | 분당 요청 수 (RPM) | 대상 경로 |
|----------|---------------------|-----------|
| AUTH | 20 | `/api/auth/**` |
| QUEUE | 60 | `/api/queue/**` |
| BOOKING | 10 | `/api/seats/reserve`, `/api/reservations`, 취소 |
| GENERAL | 100 | 그 외 모든 경로 |

#### 클라이언트 식별 (lines 112-125)

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
    // Use remote address directly - more reliable than X-Forwarded-For
    // which can be spoofed by clients
    return "ip:" + request.getRemoteAddr();
}
```

| 점검 항목 | 결과 | 심각도 | 설명 |
|-----------|------|--------|------|
| X-Forwarded-For 스푸핑 방지 | 통과 | - | 의도적으로 무시 (line 122-124 주석) |
| JWT 기반 사용자 식별 | 통과 | - | 인증 사용자는 userId로 추적 |
| 비인증 사용자 IP 식별 | 주의 | `MEDIUM` | 프록시/NAT 뒤 사용자 공유 IP 문제 |
| Fail-closed 정책 | 통과 | - | Redis 장애 시 안전하게 차단 |

#### Lua 스크립트 (Sliding Window)

**파일**: `services-spring/gateway-service/src/main/resources/redis/rate_limit.lua` (15 lines)

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

| 점검 항목 | 결과 | 심각도 |
|-----------|------|--------|
| 원자적 실행 (Lua 스크립트) | 통과 | - |
| Sliding window ZSET 구현 | 통과 | - |
| 윈도우 외 데이터 정리 (`ZREMRANGEBYSCORE`) | 통과 | - |
| TTL 설정 (`EXPIRE`) | 통과 | - |
| 초과 시 요청 ID 제거 (`ZREM`) | 통과 - 정확한 카운트 유지 | - |

### 5.2 VWR Entry Token 검증

**파일**: `services-spring/gateway-service/src/main/java/com/tiketi/gatewayservice/filter/VwrEntryTokenFilter.java` (115 lines)

#### 구성 요약

| 속성 | 값 | 설명 |
|------|-----|------|
| Filter Order | 1 (Rate Limit 다음) | `@Order(1)` (line 25) |
| 보호 메서드 | POST, PUT, PATCH만 | `PROTECTED_METHODS` (line 33) |
| 보호 경로 | `/api/seats/**`, `/api/reservations**` | `isProtectedPath()` (line 94-96) |
| 실패 응답 | 403 + `redirectTo: /queue` | (lines 98-102) |

#### CloudFront Bypass 검증 (lines 61-69)

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

| 점검 항목 | 결과 | 심각도 |
|-----------|------|--------|
| Timing-safe 비교 (`MessageDigest.isEqual`) | 통과 | - |
| CloudFront secret 환경변수 관리 | 통과 | - |
| JWT 서명 검증 (lines 81-84) | 통과 (`Jwts.parser().verifyWith()`) | - |
| GET 요청 비보호 | 의도적 설계 | `INFO` - 읽기 전용은 토큰 불필요 |

### 5.3 CORS 설정

**파일**: `services-spring/gateway-service/src/main/java/com/tiketi/gatewayservice/config/CorsConfig.java` (33 lines)

```java
List<String> origins = Arrays.asList(allowedOrigins.split(","));
config.setAllowedOrigins(origins);
config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
config.setAllowedHeaders(List.of("Authorization", "Content-Type", "x-queue-entry-token"));
config.setAllowCredentials(true);
config.setMaxAge(3600L);
```

| 점검 항목 | 결과 | 심각도 |
|-----------|------|--------|
| 화이트리스트 기반 Origin | 통과 (환경변수 설정) | - |
| 와일드카드 Origin 사용 여부 | 미사용 | - |
| Credentials 허용 | true (쿠키 전송 필요) | `INFO` |
| 허용 헤더 제한 | 3개만 허용 | 적정 |
| preflight 캐시 | 3600초 (1시간) | 적정 |

### 5.4 내부 API 인증

서비스 간 통신은 공유 비밀 토큰(`INTERNAL_API_TOKEN`)으로 보호된다.

#### InternalTokenValidator 구현 비교

| 서비스 | 파일 | Timing-safe 비교 |
|--------|------|-------------------|
| **Auth** | `auth-service/.../security/InternalTokenValidator.java` (lines 17-31) | `MessageDigest.isEqual()` 사용 |
| **Payment** | `payment-service/.../security/InternalTokenValidator.java` (lines 19-32) | `MessageDigest.isEqual()` 사용 |
| **Ticket** | `ticket-service/.../shared/security/InternalTokenValidator.java` | `MessageDigest.isEqual()` 사용 |

#### InternalApiAuthFilter (Auth Service)

**파일**: `services-spring/auth-service/src/main/java/com/tiketi/authservice/security/InternalApiAuthFilter.java`

```java
// 경로 필터링 (lines 50-52)
protected boolean shouldNotFilter(HttpServletRequest request) {
    return !request.getRequestURI().startsWith("/internal/");
}

// 토큰 확인 (lines 28-34)
String token = request.getHeader("x-internal-token");
if (token == null || token.isBlank()) {
    String authHeader = request.getHeader("Authorization");
    if (authHeader != null && authHeader.startsWith("Bearer ")) {
        token = authHeader.substring(7);
    }
}
```

| 점검 항목 | 결과 | 심각도 |
|-----------|------|--------|
| `/internal/**` 경로 전용 | 통과 (line 51) | - |
| 이중 헤더 지원 (`x-internal-token` / `Authorization`) | 통과 | - |
| Timing-safe 비교 | 통과 (line 37-39) | - |
| 모든 서비스 일관된 구현 | 통과 | - |

---

## 6. 데이터 보안 분석

### 6.1 비밀 정보 관리

| 비밀 정보 | 저장 방식 | 주입 방식 | 심각도 |
|-----------|-----------|-----------|--------|
| JWT Secret | `JWT_SECRET` 환경변수 | K8s Secret / 환경변수 | - |
| 내부 API 토큰 | `INTERNAL_API_TOKEN` 환경변수 | K8s Secret / 환경변수 | - |
| 큐 토큰 시크릿 | `QUEUE_ENTRY_TOKEN_SECRET` 환경변수 | K8s Secret / 환경변수 | - |
| DB 비밀번호 | `AUTH_DB_PASSWORD` 등 환경변수 | K8s Secret / 환경변수 | - |
| CloudFront Secret | `cloudfront.secret` 환경변수 | K8s Secret / 환경변수 | - |

**K8s Secrets 관리**: `k8s/spring/overlays/kind/secrets.env` -> Kustomize `SecretGenerator`

| 점검 항목 | 결과 | 심각도 | 설명 |
|-----------|------|--------|------|
| 하드코딩된 비밀 정보 | 미발견 | - | 모든 비밀은 환경변수 참조 |
| secrets.env 버전 관리 | `secrets.env.example` 별도 제공 | `INFO` | .gitignore 확인 필요 |
| 비밀 정보 로테이션 | 미구현 | `MEDIUM` | JWT 키 로테이션 메커니즘 부재 |
| AWS Secrets Manager 연동 | RDS Proxy 인증에 사용 | 적정 | `db_credentials_secret_arn` 참조 |

### 6.2 비밀번호 보안

| 항목 | 구현 | 평가 |
|------|------|------|
| 해싱 알고리즘 | BCrypt | 업계 표준 |
| Cost Factor | 12 | 적정 (기본 10 대비 4배 강화) |
| Salt | BCrypt 자동 생성 | 적정 |
| 평문 저장 | 없음 | 통과 |

### 6.3 데이터 전송 보안

| 구간 | 암호화 | 프로토콜 |
|------|--------|----------|
| Client -> CloudFront | TLS 1.2+ | `TLSv1.2_2021` (minimum_protocol_version) |
| CloudFront -> ALB | HTTPS | `origin_protocol_policy = "https-only"` |
| ALB -> Gateway | HTTP (VPC 내부) | 클러스터 내부 통신 |
| Service -> RDS Proxy | TLS 필수 | `require_tls = true` |
| RDS Proxy -> RDS | TLS | AWS 관리형 |

### 6.4 데이터 저장 보안

| 항목 | 설정 | 파일 참조 |
|------|------|-----------|
| RDS 스토리지 암호화 | `storage_encrypted = true` | `terraform/modules/rds/main.tf` (line 57) |
| 퍼블릭 접근 차단 | `publicly_accessible = false` | `terraform/modules/rds/main.tf` (line 73) |
| Multi-AZ 배포 | `multi_az = var.multi_az` | `terraform/modules/rds/main.tf` (line 76) |
| 삭제 보호 | `deletion_protection = var.deletion_protection` | `terraform/modules/rds/main.tf` (line 100) |

---

## 7. 인프라 보안 분석

### 7.1 컨테이너 보안

모든 서비스 Dockerfile에서 non-root 사용자로 실행한다.

**예시** (`services-spring/auth-service/Dockerfile`, line 18-19):

```dockerfile
RUN addgroup --system --gid 1001 app && adduser --system --uid 1001 --ingroup app app
USER app
```

| 서비스 | Dockerfile 위치 | non-root UID | 확인 |
|--------|-----------------|--------------|------|
| auth-service | `services-spring/auth-service/Dockerfile` (line 18) | 1001 | 통과 |
| gateway-service | `services-spring/gateway-service/Dockerfile` (line 18) | 1001 | 통과 |
| frontend | `apps/web/Dockerfile` (line 28) | 1001 | 통과 |
| payment-service | `services-spring/payment-service/Dockerfile` | 1001 | 통과 |
| ticket-service | `services-spring/ticket-service/Dockerfile` | 1001 | 통과 |
| stats-service | `services-spring/stats-service/Dockerfile` | 1001 | 통과 |

### 7.2 Kubernetes Pod 보안

각 deployment.yaml에 적용된 보안 컨텍스트:

```yaml
securityContext:
  runAsNonRoot: true
  runAsUser: 1000
  allowPrivilegeEscalation: false
  capabilities:
    drop: [ALL]
```

| 점검 항목 | 결과 | 심각도 |
|-----------|------|--------|
| `runAsNonRoot: true` | 전체 서비스 적용 | - |
| `runAsUser: 1000` | 전체 서비스 적용 | - |
| `allowPrivilegeEscalation: false` | 전체 서비스 적용 | - |
| `capabilities.drop: [ALL]` | 전체 서비스 적용 | - |
| `readOnlyRootFilesystem` | 미설정 | `LOW` |
| `seccompProfile` | 미설정 | `LOW` |

> 참고: Dockerfile UID(1001)와 K8s `runAsUser`(1000)의 불일치가 있으나, `runAsNonRoot: true`가 실질적 보안을 담당하므로 기능적 영향은 없다. 일관성을 위해 통일 권장.

### 7.3 Network Policies

**파일**: `k8s/spring/base/network-policies.yaml` (92 lines)

#### 정책 구성

| 정책 이름 | 범위 | 설명 |
|-----------|------|------|
| `default-deny-all` (lines 1-9) | 전체 Pod | Ingress + Egress 기본 차단 |
| `allow-gateway-ingress` (lines 11-23) | gateway-service | 포트 3001 인바운드 허용 |
| `allow-frontend-ingress` (lines 25-37) | frontend | 포트 3000 인바운드 허용 |
| `allow-internal-communication` (lines 39-67) | tier=backend | 백엔드 간 통신 + DNS 허용 |
| `allow-gateway-to-services` (lines 69-92) | gateway-service | 백엔드 서비스 + DNS 아웃바운드 |

| 점검 항목 | 결과 | 심각도 |
|-----------|------|--------|
| Default deny 정책 | 통과 (Ingress + Egress 모두) | - |
| 게이트웨이 전용 외부 접근 | 통과 (포트 3001만 허용) | - |
| 백엔드 간 통신 제한 | `tier=backend` 레이블 기반 | `INFO` - 서비스 간 세분화 가능 |
| DNS 이그레스 허용 | 통과 (UDP/TCP 53, kube-dns) | - |
| 외부 이그레스 (인터넷) | 차단 (default deny) | `INFO` - 외부 API 호출 필요 시 별도 허용 필요 |

### 7.4 AWS ALB 보안

**파일**: `terraform/modules/alb/main.tf`

| 항목 | 설정 | 라인 |
|------|------|------|
| CloudFront 전용 접근 | `prefix_list_ids = [var.cloudfront_prefix_list_id]` | line 27 |
| 잘못된 헤더 드롭 | `drop_invalid_header_fields = true` | line 105 |
| TLS 정책 | `ELBSecurityPolicy-TLS13-1-2-2021-06` | line 161 |
| HTTP -> HTTPS 리다이렉트 | 301 리다이렉트 구성 | lines 184-194 |
| 삭제 보호 | `enable_deletion_protection = var.enable_deletion_protection` | line 101 |

| 점검 항목 | 결과 | 심각도 |
|-----------|------|--------|
| CloudFront prefix list 제한 | 통과 - 직접 ALB 접근 차단 | - |
| TLS 1.3 지원 | 통과 (`TLS13-1-2-2021-06`) | - |
| 잘못된 헤더 드롭 | 통과 - HTTP Request Smuggling 방지 | - |

### 7.5 AWS CloudFront 보안

**파일**: `terraform/modules/cloudfront/main.tf`

#### 보안 헤더 정책 (lines 253-284)

```hcl
security_headers_config {
    strict_transport_security {
        access_control_max_age_sec = 31536000  # 1년
        include_subdomains         = true
        preload                    = true
    }
    content_type_options { override = true }       # X-Content-Type-Options: nosniff
    frame_options { frame_option = "DENY" }        # X-Frame-Options: DENY
    xss_protection { mode_block = true; protection = true }
    referrer_policy { referrer_policy = "strict-origin-when-cross-origin" }
}
```

| 헤더 | 값 | 평가 |
|------|-----|------|
| HSTS | `max-age=31536000; includeSubDomains; preload` | 최적 |
| X-Frame-Options | DENY | 클릭재킹 방지 |
| X-Content-Type-Options | nosniff | MIME 스니핑 방지 |
| X-XSS-Protection | 1; mode=block | 레거시 브라우저 보호 |
| Referrer-Policy | strict-origin-when-cross-origin | 적정 |

#### TLS 설정

| 항목 | 값 | 평가 |
|------|-----|------|
| 최소 TLS 버전 | `TLSv1.2_2021` | 적정 (line 170) |
| viewer_protocol_policy | `redirect-to-https` | 통과 (line 99) |
| Origin 통신 | `https-only` | 통과 (line 74) |
| Origin TLS | `TLSv1.2` | 통과 (line 75) |

---

## 8. 프론트엔드 보안 분석

### 8.1 토큰 저장

| 항목 | 구현 | 심각도 | 설명 |
|------|------|--------|------|
| Access Token 저장 | `localStorage` | `HIGH` | XSS 공격 시 토큰 탈취 가능 |
| Refresh Token 저장 | `localStorage` | `HIGH` | 장기 토큰 노출 위험 |
| Entry Token 저장 | Cookie (`SameSite=Strict`, `Secure`) | 적정 | HTTPS 환경에서 보호 |

> **권장**: Access Token은 메모리(변수)에, Refresh Token은 `httpOnly` 쿠키에 저장하여 XSS 피해 최소화.

### 8.2 401 인터셉터

- 401 응답 수신 시 `clearAuth()` 호출 후 `/login`으로 리다이렉트
- 만료된 토큰으로 인한 무한 요청 방지

### 8.3 Content Security Policy

**파일**: `apps/web/next.config.ts` (lines 14-22)

```typescript
value: [
    "default-src 'self'",
    "script-src 'self' 'unsafe-inline' 'unsafe-eval'",
    "style-src 'self' 'unsafe-inline'",
    "img-src 'self' data: https:",
    "connect-src 'self' http://localhost:* https://*.tiketi.com",
    "frame-ancestors 'none'",
].join("; "),
```

| 지시어 | 값 | 심각도 | 설명 |
|--------|-----|--------|------|
| `default-src` | `'self'` | 적정 | 기본 제한 |
| `script-src` | `'self' 'unsafe-inline' 'unsafe-eval'` | `HIGH` | XSS 방어력 약화 |
| `style-src` | `'self' 'unsafe-inline'` | `MEDIUM` | 인라인 스타일 허용 |
| `img-src` | `'self' data: https:` | `LOW` | `https:` 와일드카드 |
| `connect-src` | `'self' http://localhost:* https://*.tiketi.com` | `MEDIUM` | localhost 와일드카드 포트 (개발용 잔재) |
| `frame-ancestors` | `'none'` | 적정 | iframe 삽입 차단 |

> **`unsafe-inline` + `unsafe-eval` 위험**: CSP의 XSS 방어 효과를 크게 저하시킨다. Next.js의 인라인 스크립트 요구사항 때문이나, nonce 기반 CSP로 전환 권장.

### 8.4 기타 보안 헤더 (next.config.ts)

| 헤더 | 값 | 평가 |
|------|-----|------|
| `X-Frame-Options` | DENY | 통과 |
| `X-Content-Type-Options` | nosniff | 통과 |
| `Referrer-Policy` | strict-origin-when-cross-origin | 통과 |

---

## 9. Redis/Lua 보안 분석

### 9.1 좌석 잠금 메커니즘

| 항목 | 구현 | 평가 |
|------|------|------|
| Fencing Token | 단조증가 토큰으로 stale 요청 방지 | 적정 - 분산 잠금 경합 방지 |
| 원자적 연산 | `HMSET + EXPIRE` 단일 Lua 스크립트 | 적정 - 경합 조건 방지 |
| Dragonfly 호환 | `KEYS[]` 명시적 선언 | 적정 - 호환성 보장 |

### 9.2 대기열 입장 제어

| 항목 | 구현 | 평가 |
|------|------|------|
| 입장 순서 | `ZPOPMIN` 원자적 연산 | 적정 - 순서 보장 |
| 중복 입장 방지 | 원자적 팝 후 삭제 | 적정 |

### 9.3 Rate Limiting (Lua)

위 5.1절에서 상세 분석 완료. Sliding window ZSET 기반, 원자적 실행 보장.

| 점검 항목 | 결과 | 심각도 |
|-----------|------|--------|
| Lua 스크립트 인젝션 | 해당 없음 (파라미터 바인딩) | - |
| Redis AUTH 설정 | 환경변수 기반 | `INFO` - K8s 내부 통신 |
| Redis TLS | 미확인 | `LOW` - 클러스터 내부 통신 |

---

## 10. Lambda@Edge 보안 분석

**파일**: `lambda/edge-queue-check/index.js` (175 lines)

### 10.1 경로 보호 정책

**보호 경로** (lines 28-32):
```javascript
const PROTECTED_PATHS = [
    '/api/reservations',
    '/api/tickets',
    '/api/seats',
    '/api/admin'
];
```

**우회 경로** (lines 36-43):
```javascript
const BYPASS_PATHS = [
    '/api/queue',
    '/api/auth',
    '/api/events',
    '/api/stats',
    '/health',
    '/actuator'
];
```

### 10.2 JWT 검증 (lines 105-139)

```javascript
function verifyJWT(token, secret) {
    const [headerB64, payloadB64, signatureB64] = parts;

    // Timing-safe 서명 비교
    const expectedSignature = crypto
        .createHmac('sha256', secret)
        .update(data)
        .digest('base64url');

    if (signatureB64.length !== expectedSignature.length ||
        !crypto.timingSafeEqual(
            Buffer.from(signatureB64), Buffer.from(expectedSignature))) {
        return null;
    }

    // 만료 검증
    if (payload.exp && payload.exp < now) {
        return null;
    }

    return payload;
}
```

| 점검 항목 | 결과 | 심각도 |
|-----------|------|--------|
| Timing-safe 비교 (`crypto.timingSafeEqual`) | 통과 (line 122) | - |
| 길이 사전 검증 | 통과 (line 121) | - |
| 만료 시간 검증 | 통과 (lines 130-132) | - |
| eventId 경로 매칭 검증 | 통과 (lines 92-96) | - |
| Algorithm confusion 방어 | 미검증 (header.alg 미확인) | `LOW` |

### 10.3 비밀 정보 관리

```javascript
// Lambda@Edge does not support environment variables.
// Secret is injected at build time via config.json generated by Terraform.
let SECRET;
try {
    const config = require('./config.json');
    SECRET = config.secret;
} catch (e) {
    SECRET = process.env.QUEUE_ENTRY_TOKEN_SECRET;
}
```

**Terraform 생성**: `terraform/modules/cloudfront/main.tf` (lines 7-9)

```hcl
resource "local_file" "edge_config" {
    content  = jsonencode({ secret = var.queue_entry_token_secret })
    filename = "${var.lambda_source_dir}/config.json"
}
```

| 점검 항목 | 결과 | 심각도 |
|-----------|------|--------|
| Lambda@Edge 환경변수 제약 대응 | config.json 빌드 타임 주입 | 적정 |
| config.json 버전 관리 제외 | 확인 필요 (.gitignore) | `MEDIUM` |
| 비밀 부재 시 시작 실패 | 통과 (`throw new Error`) | - |

---

## 11. 발견 사항 요약

### 11.1 심각도별 분류

#### CRITICAL - 없음

현재 치명적 수준의 보안 취약점은 발견되지 않았다.

#### HIGH - 2건

| ID | 항목 | 위치 | 설명 |
|----|------|------|------|
| H-1 | localStorage 토큰 저장 | 프론트엔드 전체 | XSS 공격 시 Access/Refresh Token 탈취 가능. `httpOnly` 쿠키 전환 권장 |
| H-2 | CSP `unsafe-inline` + `unsafe-eval` | `apps/web/next.config.ts` (line 17) | CSP의 XSS 방어력을 크게 약화. nonce 기반 CSP 전환 권장 |

#### MEDIUM - 4건

| ID | 항목 | 위치 | 설명 |
|----|------|------|------|
| M-1 | JWT 키 로테이션 미구현 | `auth-service/.../security/JwtService.java` | 키 유출 시 전체 토큰 무효화 불가 |
| M-2 | Refresh Token 서버 측 폐기 부재 | `auth-service/.../security/JwtService.java` | 탈취된 Refresh Token 즉시 폐기 불가 |
| M-3 | NAT/프록시 환경 IP 공유 | `gateway-service/.../filter/RateLimitFilter.java` (line 124) | 동일 IP 사용자 간 Rate Limit 공유 |
| M-4 | CSP connect-src localhost 와일드카드 | `apps/web/next.config.ts` (line 20) | 프로덕션 빌드에 개발용 설정 잔재 |

#### LOW - 5건

| ID | 항목 | 위치 | 설명 |
|----|------|------|------|
| L-1 | `readOnlyRootFilesystem` 미설정 | K8s deployment.yaml 전체 | 컨테이너 파일시스템 쓰기 차단 미적용 |
| L-2 | `seccompProfile` 미설정 | K8s deployment.yaml 전체 | syscall 필터링 미적용 |
| L-3 | Dockerfile/K8s UID 불일치 | Dockerfile(1001) vs K8s(1000) | 기능적 영향 없으나 일관성 부족 |
| L-4 | Lambda@Edge alg 미검증 | `lambda/edge-queue-check/index.js` | JWT 헤더의 알고리즘 필드 미확인 |
| L-5 | 비관리자 경로 서버 검증 부재 | `apps/web/src/components/auth-guard.tsx` | `adminOnly=false` 시 서버 역할 재검증 미수행 |

#### INFO - 5건

| ID | 항목 | 위치 | 설명 |
|----|------|------|------|
| I-1 | 2단계 역할 모델 | auth-service UserRole | user/admin만 존재, 세분화 가능 |
| I-2 | 백엔드 간 통신 세분화 | network-policies.yaml | `tier=backend` 단위, 서비스 간 세분화 가능 |
| I-3 | JwtAuthenticationFilter GrantedAuthority 비어 있음 | auth-service JwtAuthenticationFilter (line 47) | Spring Security 역할 기반 접근 제어 미활용 |
| I-4 | VWR 토큰 GET 요청 비보호 | VwrEntryTokenFilter (line 33) | 의도적 설계이나 읽기 작업 보호 고려 |
| I-5 | Redis TLS 미적용 (클러스터 내부) | 클러스터 내부 통신 | NetworkPolicy로 보완 |

### 11.2 전체 보안 점수

| 영역 | 점수 | 등급 | 비고 |
|------|------|------|------|
| 인증 | 8.5/10 | A | JWT 구현 견고, 키 로테이션 부재 감점 |
| 인가 | 8.0/10 | B+ | 서버 측 검증 충실, 역할 세분화 가능 |
| API 보안 | 9.0/10 | A | Rate Limiting + VWR + CORS 다층 방어 |
| 데이터 보안 | 8.5/10 | A | 암호화 전송/저장, 비밀 관리 양호 |
| 인프라 보안 | 9.0/10 | A | non-root, NetworkPolicy, 최소 권한 |
| 프론트엔드 보안 | 6.5/10 | C+ | localStorage 토큰 + CSP 완화 |
| Edge 보안 | 8.5/10 | A | Timing-safe 검증, 경로 보호 |
| **종합** | **8.3/10** | **B+** | 프론트엔드 보안 강화 필요 |

---

## 12. 권장 조치 사항

### 12.1 단기 조치 (1-2주)

| 우선순위 | 조치 | 대상 파일 | 효과 |
|----------|------|-----------|------|
| 1 | Refresh Token을 `httpOnly` 쿠키로 전환 | 프론트엔드 + auth-service | H-1 해결, XSS 토큰 탈취 방지 |
| 2 | CSP에서 `unsafe-eval` 제거, nonce 기반 전환 | `apps/web/next.config.ts` | H-2 해결, XSS 방어력 복원 |
| 3 | `connect-src`에서 `http://localhost:*` 제거 (프로덕션) | `apps/web/next.config.ts` (line 20) | M-4 해결 |

### 12.2 중기 조치 (1-3개월)

| 우선순위 | 조치 | 대상 | 효과 |
|----------|------|------|------|
| 4 | JWT 키 로테이션 메커니즘 구현 | auth-service JwtService | M-1 해결, 키 유출 대응력 확보 |
| 5 | Refresh Token 블랙리스트/화이트리스트 | auth-service + Redis | M-2 해결, 탈취 토큰 즉시 폐기 |
| 6 | K8s `readOnlyRootFilesystem` + `seccompProfile` 적용 | 전체 deployment.yaml | L-1, L-2 해결 |
| 7 | Dockerfile/K8s UID 통일 (1001) | K8s deployment.yaml | L-3 해결, 일관성 확보 |

### 12.3 장기 조치 (3-6개월)

| 우선순위 | 조치 | 대상 | 효과 |
|----------|------|------|------|
| 8 | RBAC 세분화 (operator, support 등) | UserRole + 전체 서비스 | I-1 개선 |
| 9 | 서비스 간 NetworkPolicy 세분화 | network-policies.yaml | I-2 개선, 최소 권한 네트워크 |
| 10 | AWS WAF 도입 (SQL injection, XSS 추가 방어) | CloudFront 앞단 | 심층 방어 강화 |
| 11 | 보안 감사 로그 중앙화 (SIEM 연동) | 전체 서비스 | 침해 탐지/대응 역량 확보 |

---

> **면책 조항**: 본 보안 분석은 소스 코드 정적 분석에 기반하며, 동적 침투 테스트 결과를 포함하지 않는다. 프로덕션 배포 전 전문 보안 업체의 침투 테스트를 권장한다.
