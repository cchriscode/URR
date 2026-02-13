# 보안 상세 분석

## 1) 인증/인가 모델

### 1.1 인증 주체
- 인증의 중심은 auth-service이며 `/api/auth/*` 엔드포인트에서 로그인/재발급/로그아웃을 처리한다.  
  출처: `services-spring/auth-service/src/main/java/com/tiketi/authservice/controller/AuthController.java:28`, `services-spring/auth-service/src/main/java/com/tiketi/authservice/controller/AuthController.java:48`, `services-spring/auth-service/src/main/java/com/tiketi/authservice/controller/AuthController.java:67`, `services-spring/auth-service/src/main/java/com/tiketi/authservice/controller/AuthController.java:103`
- access/refresh 토큰은 HttpOnly 쿠키로 발급된다.  
  출처: `services-spring/auth-service/src/main/java/com/tiketi/authservice/util/CookieHelper.java:30`, `services-spring/auth-service/src/main/java/com/tiketi/authservice/util/CookieHelper.java:40`
- refresh는 쿠키 우선, body fallback(`RefreshTokenRequest`)를 지원한다.  
  출처: `services-spring/auth-service/src/main/java/com/tiketi/authservice/controller/AuthController.java:69`, `services-spring/auth-service/src/main/java/com/tiketi/authservice/controller/AuthController.java:73`

### 1.2 권한 체크
- auth-service는 SecurityFilterChain 기반으로 `permitAll`/`authenticated` 정책을 명시한다.  
  출처: `services-spring/auth-service/src/main/java/com/tiketi/authservice/config/SecurityConfig.java:32`, `services-spring/auth-service/src/main/java/com/tiketi/authservice/config/SecurityConfig.java:37`, `services-spring/auth-service/src/main/java/com/tiketi/authservice/config/SecurityConfig.java:40`
- stats-service는 모든 통계 API에 `requireAdmin`을 적용한다.  
  출처: `services-spring/stats-service/src/main/java/com/tiketi/statsservice/controller/StatsController.java:28`, `services-spring/stats-service/src/main/java/com/tiketi/statsservice/controller/StatsController.java:139`
- queue-service admin API도 `requireAdmin`으로 보호된다.  
  출처: `services-spring/queue-service/src/main/java/com/tiketi/queueservice/controller/QueueController.java:68`, `services-spring/queue-service/src/main/java/com/tiketi/queueservice/controller/QueueController.java:77`

## 2) 토큰/쿠키 세부 구현

### 2.1 JWT 내용
- access/refresh는 `type` claim으로 구분한다.  
  출처: `services-spring/auth-service/src/main/java/com/tiketi/authservice/security/JwtService.java:37`, `services-spring/auth-service/src/main/java/com/tiketi/authservice/security/JwtService.java:52`, `services-spring/auth-service/src/main/java/com/tiketi/authservice/security/JwtService.java:74`
- refresh 검증 시 `type=refresh` 강제 검사를 수행한다.  
  출처: `services-spring/auth-service/src/main/java/com/tiketi/authservice/security/JwtService.java:71`, `services-spring/auth-service/src/main/java/com/tiketi/authservice/security/JwtService.java:74`

### 2.2 쿠키 속성
- access cookie: `HttpOnly`, `Secure`(환경 의존), `SameSite=Lax`, path `/`  
  출처: `services-spring/auth-service/src/main/java/com/tiketi/authservice/util/CookieHelper.java:30`, `services-spring/auth-service/src/main/java/com/tiketi/authservice/util/CookieHelper.java:31`, `services-spring/auth-service/src/main/java/com/tiketi/authservice/util/CookieHelper.java:34`
- refresh cookie: `HttpOnly`, `SameSite=Lax`, path `/api/auth`  
  출처: `services-spring/auth-service/src/main/java/com/tiketi/authservice/util/CookieHelper.java:40`, `services-spring/auth-service/src/main/java/com/tiketi/authservice/util/CookieHelper.java:42`, `services-spring/auth-service/src/main/java/com/tiketi/authservice/util/CookieHelper.java:44`

### 2.3 게이트웨이의 쿠키 브리지
- 게이트웨이는 `access_token` 쿠키를 `Authorization: Bearer ...`로 승격한다.  
  출처: `services-spring/gateway-service/src/main/java/com/tiketi/gatewayservice/filter/CookieAuthFilter.java:21`, `services-spring/gateway-service/src/main/java/com/tiketi/gatewayservice/filter/CookieAuthFilter.java:34`, `services-spring/gateway-service/src/main/java/com/tiketi/gatewayservice/filter/CookieAuthFilter.java:37`

## 3) 내부 통신 보안
- 내부 API는 고정 shared secret(`INTERNAL_API_TOKEN`) 비교 방식이 기본이다.  
  출처: `services-spring/ticket-service/src/main/java/com/tiketi/ticketservice/shared/security/InternalTokenValidator.java:15`, `services-spring/payment-service/src/main/java/com/tiketi/paymentservice/security/InternalTokenValidator.java:15`, `services-spring/queue-service/src/main/resources/application.yml:50`
- 비교는 timing-safe 비교(`MessageDigest.isEqual`)를 사용한다.  
  출처: `services-spring/ticket-service/src/main/java/com/tiketi/ticketservice/shared/security/InternalTokenValidator.java:24`, `services-spring/auth-service/src/main/java/com/tiketi/authservice/security/InternalTokenValidator.java:29`
- 서비스 간 호출에 Resilience4j를 붙여 장애 전파를 완화한다(보안은 아니지만 가용성 측면에서 중요).  
  출처: `services-spring/payment-service/src/main/java/com/tiketi/paymentservice/client/TicketInternalClient.java:31`, `services-spring/queue-service/src/main/java/com/tiketi/queueservice/shared/client/TicketInternalClient.java:31`

## 4) Gateway 보안 필터

### 4.1 Rate Limit
- 인증/큐/예매/일반 카테고리별 제한을 분리한다.  
  출처: `services-spring/gateway-service/src/main/java/com/tiketi/gatewayservice/filter/RateLimitFilter.java:39`, `services-spring/gateway-service/src/main/java/com/tiketi/gatewayservice/filter/RateLimitFilter.java:152`, `services-spring/gateway-service/src/main/resources/application.yml:111`
- Redis Lua 기반 윈도우 카운팅으로 초과 시 429를 반환한다.  
  출처: `services-spring/gateway-service/src/main/java/com/tiketi/gatewayservice/filter/RateLimitFilter.java:98`, `services-spring/gateway-service/src/main/resources/redis/rate_limit.lua:11`, `services-spring/gateway-service/src/main/java/com/tiketi/gatewayservice/filter/RateLimitFilter.java:181`
- Redis 장애 시 fail-open 정책으로 요청을 통과시킨다.  
  출처: `services-spring/gateway-service/src/main/java/com/tiketi/gatewayservice/filter/RateLimitFilter.java:114`, `services-spring/gateway-service/src/main/java/com/tiketi/gatewayservice/filter/RateLimitFilter.java:115`

### 4.2 대기열 입장 토큰(VWR) 검증
- 보호 메서드는 POST/PUT/PATCH로 제한하고 예약/좌석 경로를 보호한다.  
  출처: `services-spring/gateway-service/src/main/java/com/tiketi/gatewayservice/filter/VwrEntryTokenFilter.java:34`, `services-spring/gateway-service/src/main/java/com/tiketi/gatewayservice/filter/VwrEntryTokenFilter.java:117`
- `x-queue-entry-token` JWT를 검증하고 uid와 auth subject를 비교한다.  
  출처: `services-spring/gateway-service/src/main/java/com/tiketi/gatewayservice/filter/VwrEntryTokenFilter.java:31`, `services-spring/gateway-service/src/main/java/com/tiketi/gatewayservice/filter/VwrEntryTokenFilter.java:85`, `services-spring/gateway-service/src/main/java/com/tiketi/gatewayservice/filter/VwrEntryTokenFilter.java:92`, `services-spring/gateway-service/src/main/java/com/tiketi/gatewayservice/filter/VwrEntryTokenFilter.java:95`
- 검증 성공 시 eventId를 request attribute로 전달한다.  
  출처: `services-spring/gateway-service/src/main/java/com/tiketi/gatewayservice/filter/VwrEntryTokenFilter.java:103`, `services-spring/gateway-service/src/main/java/com/tiketi/gatewayservice/filter/VwrEntryTokenFilter.java:106`

## 5) Frontend 보안 헤더/클라이언트 보안
- Next middleware에서 CSP/XFO/nosniff/referrer-policy를 설정한다.  
  출처: `apps/web/src/middleware.ts:23`, `apps/web/src/middleware.ts:24`, `apps/web/src/middleware.ts:25`, `apps/web/src/middleware.ts:26`
- `script-src 'unsafe-inline'` 허용 상태다.  
  출처: `apps/web/src/middleware.ts:12`
- 사용자 정보는 `localStorage`에 저장된다(토큰은 아니지만 역할/식별자 노출 경로).  
  출처: `apps/web/src/lib/storage.ts:9`, `apps/web/src/lib/storage.ts:20`
- 큐 entry token 쿠키는 프론트 JS에서 생성하며 `SameSite=Strict`를 사용한다.  
  출처: `apps/web/src/app/queue/[eventId]/page.tsx:56`, `apps/web/src/hooks/use-queue-polling.ts:50`

## 6) 인프라 보안 연계
- K8s 기본 네트워크 정책은 `default-deny-all`로 시작한다.  
  출처: `k8s/spring/base/network-policies.yaml:4`, `k8s/spring/base/network-policies.yaml:6`
- 서비스별 ingress 허용 대상을 분리해 east-west 트래픽을 제한한다.  
  출처: `k8s/spring/base/network-policies.yaml:64`, `k8s/spring/base/network-policies.yaml:88`, `k8s/spring/base/network-policies.yaml:145`
- Terraform ALB 모듈은 CloudFront prefix list 기반 인바운드 제한을 지원한다.  
  출처: `terraform/modules/alb/main.tf:20`, `terraform/modules/alb/main.tf:27`
- CloudFront 응답 헤더 정책에 HSTS/frame-options/referrer-policy가 포함되어 있다.  
  출처: `terraform/modules/cloudfront/main.tf:257`, `terraform/modules/cloudfront/main.tf:259`, `terraform/modules/cloudfront/main.tf:269`, `terraform/modules/cloudfront/main.tf:280`

## 7) 보안 리스크 및 개선 우선순위

### P0 (즉시)
- CloudFront가 origin에 넣는 커스텀 헤더(`X-Custom-Header`)와 Gateway 우회 필터가 기대하는 헤더(`X-CloudFront-Verified`)가 다르다. 우회 로직이 의도대로 동작하지 않거나 운영 중 혼선을 만든다.  
  출처: `terraform/modules/cloudfront/main.tf:79`, `services-spring/gateway-service/src/main/java/com/tiketi/gatewayservice/filter/VwrEntryTokenFilter.java:32`
- Lambda@Edge 리다이렉트의 eventId 추출 정규식이 `/event/...` 기준이라 실제 `/events/...` 경로와 불일치 가능성이 있다.  
  출처: `lambda/edge-queue-check/index.js:163`

### P1 (단기)
- CSP에 `unsafe-inline`이 포함되어 XSS 방어면에서 보수적이지 않다.  
  출처: `apps/web/src/middleware.ts:12`
- kind 환경 시크릿 파일이 레포에 평문 형태로 존재한다(개발환경이라도 관리정책 필요).  
  출처: `k8s/spring/overlays/kind/secrets.env:2`, `k8s/spring/overlays/kind/secrets.env:11`, `k8s/spring/overlays/kind/secrets.env:12`
- Grafana admin 계정이 `admin/admin`으로 고정되어 있다.  
  출처: `k8s/spring/overlays/kind/grafana.yaml:67`, `k8s/spring/overlays/kind/grafana.yaml:70`

### P2 (중기)
- Internal API 인증이 단일 shared token 중심이라 서비스별 세분 권한/키 롤링/감사 추적이 제한적이다.  
  출처: `services-spring/ticket-service/src/main/java/com/tiketi/ticketservice/shared/security/InternalTokenValidator.java:15`, `services-spring/payment-service/src/main/java/com/tiketi/paymentservice/security/InternalTokenValidator.java:15`
- Rate limit이 fail-open이라 Redis 장애 시 보호가 약화된다.  
  출처: `services-spring/gateway-service/src/main/java/com/tiketi/gatewayservice/filter/RateLimitFilter.java:114`

## 8) 총평
- 현재 구조는 기본적인 인증/내부통신 보호/네트워크 경계가 잡혀 있다.
- 다만 엣지-게이트웨이 헤더 정합성과 CSP 강화, 시크릿/운영계정 정책은 보안 완성도를 위해 우선 정리해야 한다.
