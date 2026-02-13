# URR (우르르) 티켓팅 플랫폼 종합 평가 보고서

**평가 대상**: URR 티켓팅 시스템 전체 코드베이스
**평가 일자**: 2026-02-13
**평가 범위**: Frontend, Backend, Security, Infrastructure
**플랫폼 목표**: 15,000명 동시 접속 대규모 공연 티켓팅 시스템

---

## 1. 종합 평가 개요

### 1.1 평가 요약

| 도메인 | 등급 | 점수 | 요약 |
|--------|------|------|------|
| 아키텍처 | A- | 88/100 | DDD 기반 마이크로서비스 설계가 우수하나, Ticket Service의 비대함이 과제 |
| 보안 | B+ | 85/100 | Fail-closed rate limiting, timing-safe 비교 등 고급 보안 패턴 적용. localStorage 토큰 저장이 주요 약점 |
| 프론트엔드 | B | 78/100 | 최신 스택(Next.js 16 + React 19) 활용. 테스트 부재와 접근성 미흡이 과제 |
| 인프라 | A- | 86/100 | Terraform 모듈화, K8s overlay, HPA 등 운영 수준 구성. 시크릿 관리와 HA 보완 필요 |
| 결제/대기열 | A | 90/100 | Redis Lua 원자적 입장 제어, 멱등성 처리, fencing token 좌석 락 등 핵심 기능 견고 |
| **종합** | **B+** | **85/100** | **프로덕션 배포 가능 수준의 완성도. 보안 강화 및 운영 안정성 보완 필요** |

### 1.2 서비스 구성도

```
[CloudFront + Lambda@Edge] --> [API Gateway (3001)]
                                    |
                    +---------------+---------------+
                    |               |               |
             [Auth (3005)]   [Ticket (3002)]  [Queue (3007)]
                    |               |               |
             [Payment (3003)] [Stats (3004)]  [Community (3008)]
                    |               |               |
               [Kafka] <-----------+---------------+
                    |
          [PostgreSQL x5]  [Dragonfly/Redis]  [Zipkin]
```

---

## 2. 우수한 점 (강점)

### 2.1 아키텍처 설계

#### 2.1.1 DDD 기반 도메인 구조

Ticket Service 내부가 도메인 주도 설계(DDD) 원칙에 따라 8개 하위 도메인으로 분리되어 있다.

**파일 위치**: `services-spring/ticket-service/src/main/java/com/tiketi/ticketservice/domain/`

```
domain/
  admin/        # 관리자 기능
  artist/       # 아티스트 관리
  event/        # 이벤트(공연) 도메인
  membership/   # 멤버십 가입/관리
  reservation/  # 예매 처리
  seat/         # 좌석 선택/잠금
  ticket/       # 티켓 발급
  transfer/     # 양도 처리
```

각 도메인이 자체 컨트롤러, 서비스, DTO를 보유하며 도메인 간 결합도가 낮다. Bounded Context를 패키지 단위로 명확하게 분리한 점이 유지보수성을 높인다.

#### 2.1.2 7개 독립 마이크로서비스

서비스별 책임이 명확하게 분리되어 있다.

| 서비스 | 포트 | 책임 |
|--------|------|------|
| Gateway Service | 3001 | API 라우팅, Rate Limiting, VWR 토큰 검증 |
| Ticket Service | 3002 | 이벤트/좌석/예매/양도/멤버십/아티스트 |
| Payment Service | 3003 | 결제 준비/확인/환불, Toss SDK 연동 |
| Stats Service | 3004 | 통계 집계, Kafka 이벤트 소비 |
| Auth Service | 3005 | 인증/인가, JWT 발급, Google OAuth |
| Queue Service | 3007 | 대기열 관리, Redis 기반 입장 제어 |
| Community Service | 3008 | 뉴스/커뮤니티 기능 |

**근거**: Gateway 라우팅 설정에서 각 서비스로의 경로 분배를 확인할 수 있다.

- `services-spring/gateway-service/src/main/resources/application.yml` lines 10-71: 서비스별 라우트 정의

#### 2.1.3 이벤트 기반 비동기 통신 (Kafka)

서비스 간 결합을 줄이기 위해 Apache Kafka를 사용한 이벤트 기반 비동기 통신을 구현했다.

**사용 토픽**:

| 토픽명 | 생산자 | 소비자 |
|--------|--------|--------|
| `payment-events` | Payment Service | Ticket Service, Stats Service |
| `reservation-events` | Ticket Service | Stats Service |
| `membership-events` | Ticket Service | Stats Service |

**근거**:
- `services-spring/payment-service/src/main/java/com/tiketi/paymentservice/messaging/PaymentEventProducer.java` line 14: `TOPIC = "payment-events"`
- `services-spring/ticket-service/src/main/java/com/tiketi/ticketservice/messaging/PaymentEventConsumer.java` line 43: `@KafkaListener(topics = "payment-events")`
- `services-spring/stats-service/src/main/java/com/tiketi/statsservice/messaging/StatsEventConsumer.java` line 25: `@KafkaListener(topics = "payment-events")`
- `services-spring/stats-service/src/main/java/com/tiketi/statsservice/messaging/StatsEventConsumer.java` line 70: `@KafkaListener(topics = "reservation-events")`
- `services-spring/stats-service/src/main/java/com/tiketi/statsservice/messaging/StatsEventConsumer.java` line 116: `@KafkaListener(topics = "membership-events")`

#### 2.1.4 API Gateway 패턴

단일 진입점을 통해 모든 클라이언트 요청을 처리하며, 필터 체인을 통한 다계층 보안을 제공한다.

**필터 실행 순서**:

| Order | 필터 | 책임 |
|-------|------|------|
| 0 | `RateLimitFilter` | 카테고리별 요청 속도 제한 |
| 1 | `VwrEntryTokenFilter` | 대기열 입장 토큰 검증 |

- `services-spring/gateway-service/src/main/java/com/tiketi/gatewayservice/filter/RateLimitFilter.java` line 27: `@Order(0)`
- `services-spring/gateway-service/src/main/java/com/tiketi/gatewayservice/filter/VwrEntryTokenFilter.java` line 25: `@Order(1)`

#### 2.1.5 DB per Service 패턴

각 서비스가 독립적인 PostgreSQL 데이터베이스를 사용하여 데이터 격리를 보장한다.

**근거**: `services-spring/docker-compose.databases.yml`

| 서비스 | 데이터베이스 | 포트 |
|--------|-------------|------|
| Auth Service | `auth_db` | 5438 (line 5) |
| Ticket Service | `ticket_db` | 5434 (line 14) |
| Payment Service | `payment_db` | 5435 (line 23) |
| Stats Service | `stats_db` | 5436 (line 32) |
| Community Service | `community_db` | 5437 (line 41) |

---

### 2.2 보안

#### 2.2.1 Rate Limiting Fail-Closed 정책

Redis 장애 시 요청을 허용하지 않고 거부(fail-closed)하는 보안 중심 설계를 적용했다. 대부분의 시스템이 fail-open으로 구현하여 Redis 장애 시 rate limiting이 무력화되는 것과 대비된다.

**파일**: `services-spring/gateway-service/src/main/java/com/tiketi/gatewayservice/filter/RateLimitFilter.java` lines 103-106

```java
} catch (Exception e) {
    log.error("Rate limit check failed (fail-closed), rejecting request: {}", e.getMessage());
    sendRateLimitResponse(response);
    return;
}
```

또한 카테고리별로 세분화된 Rate Limit을 적용한다.

| 카테고리 | 기본값 (RPM) | 대상 경로 |
|----------|-------------|-----------|
| AUTH | 20 | `/api/auth/**` |
| QUEUE | 60 | `/api/queue/**` |
| BOOKING | 10 | `/api/seats/reserve`, `/api/reservations` |
| GENERAL | 100 | 기타 모든 경로 |

**근거**: `RateLimitFilter.java` lines 46-48, lines 142-157

#### 2.2.2 VWR (Virtual Waiting Room) 2중 검증

대기열 입장 토큰을 2개 레이어에서 검증하여 우회 공격을 방지한다.

**1단계 - Lambda@Edge (CDN 레벨)**:
- `lambda/edge-queue-check/index.js` lines 45-100: CloudFront viewer-request에서 JWT 서명 검증
- 보호 경로: `/api/reservations`, `/api/tickets`, `/api/seats`, `/api/admin` (lines 28-33)
- 우회 경로: `/api/queue`, `/api/auth`, `/api/events`, `/api/stats` (lines 36-43)

**2단계 - Gateway VwrEntryTokenFilter**:
- `services-spring/gateway-service/src/main/java/com/tiketi/gatewayservice/filter/VwrEntryTokenFilter.java` lines 58-91
- POST/PUT/PATCH 메서드에 대해서만 검증 (line 49: `PROTECTED_METHODS = Set.of("POST", "PUT", "PATCH")`)
- CloudFront 통과 시 bypass 헤더 확인 (lines 62-69)

#### 2.2.3 Fencing Token 기반 좌석 동시성 제어

좌석 잠금 시 단조 증가하는 fencing token을 사용하여 분산 환경에서의 동시성 문제를 해결한다.

**파일**: `services-spring/ticket-service/src/main/resources/redis/seat_lock_acquire.lua` lines 24-25

```lua
-- 2. Generate monotonically increasing fencing token
local token = redis.call('INCR', tokenSeqKey)
```

전체 Lua 스크립트가 원자적으로 실행되며:
1. 현재 좌석 상태 확인 (line 12-21)
2. Fencing token 생성 (line 25)
3. `AVAILABLE -> HELD` 상태 전이 (lines 28-33)
4. TTL 설정으로 자동 만료 (line 34)

#### 2.2.4 Timing-Safe 비교

내부 API 토큰 검증에서 타이밍 공격을 방지하기 위해 상수 시간 비교를 사용한다.

**Java 측 (MessageDigest.isEqual)**:
- `services-spring/auth-service/src/main/java/com/tiketi/authservice/security/InternalTokenValidator.java` lines 28-31:
```java
private static boolean timingSafeEquals(String a, String b) {
    return MessageDigest.isEqual(
            a.getBytes(StandardCharsets.UTF_8),
            b.getBytes(StandardCharsets.UTF_8));
}
```

- `services-spring/gateway-service/src/main/java/com/tiketi/gatewayservice/filter/VwrEntryTokenFilter.java` lines 64-66: CloudFront 시크릿 비교에도 `MessageDigest.isEqual()` 사용

**JavaScript 측 (crypto.timingSafeEqual)**:
- `lambda/edge-queue-check/index.js` lines 121-122:
```javascript
if (signatureB64.length !== expectedSignature.length ||
    !crypto.timingSafeEqual(Buffer.from(signatureB64), Buffer.from(expectedSignature))) {
```

#### 2.2.5 Non-Root 컨테이너 실행

모든 서비스 Dockerfile에서 UID 1001 비루트 사용자로 실행한다.

| 서비스 | 파일 | 설정 위치 |
|--------|------|-----------|
| Auth Service | `services-spring/auth-service/Dockerfile` | line 18-19 |
| Frontend | `apps/web/Dockerfile` | line 28-30 |

```dockerfile
RUN addgroup --system --gid 1001 app && adduser --system --uid 1001 --ingroup app app
USER app
```

#### 2.2.6 Kubernetes 보안 컨텍스트

Pod 수준에서 runAsNonRoot 및 모든 Linux capabilities 드롭을 적용한다.

**파일**: `k8s/spring/base/ticket-service/deployment.yaml` lines 18-31

```yaml
spec:
  securityContext:
    runAsNonRoot: true
    runAsUser: 1000
    fsGroup: 1000
  containers:
    - name: ticket-service
      securityContext:
        allowPrivilegeEscalation: false
        readOnlyRootFilesystem: false
        capabilities:
          drop:
            - ALL
```

#### 2.2.7 NetworkPolicy Default Deny

K8s NetworkPolicy에서 기본 거부(default-deny) 정책을 적용한 후 필요한 통신만 화이트리스트로 허용한다.

**파일**: `k8s/spring/base/network-policies.yaml`

- lines 1-9: `default-deny-all` - 모든 Ingress/Egress 기본 차단
- lines 11-23: `allow-gateway-ingress` - Gateway 서비스 인바운드 허용
- lines 39-67: `allow-internal-communication` - backend tier 간 상호 통신 허용
- lines 69-92: `allow-gateway-to-services` - Gateway에서 backend로의 아웃바운드 허용

#### 2.2.8 BCrypt 12 라운드

비밀번호 해싱에 BCrypt 12 라운드를 사용하여 무차별 대입 공격에 대한 충분한 연산 비용을 보장한다.

**파일**: `services-spring/auth-service/src/main/java/com/tiketi/authservice/config/AppConfig.java` line 13

```java
return new BCryptPasswordEncoder(12);
```

#### 2.2.9 CloudFront 보안 헤더

TLSv1.2_2021 최소 프로토콜, HSTS preload, X-Frame-Options DENY 등 포괄적인 보안 헤더를 설정한다.

**파일**: `terraform/modules/cloudfront/main.tf`

- line 170: `minimum_protocol_version = "TLSv1.2_2021"`
- lines 258-263: HSTS 설정 (max-age 1년, includeSubdomains, preload)
- lines 269-272: X-Frame-Options DENY
- lines 274-278: XSS Protection
- lines 280-283: Referrer-Policy strict-origin-when-cross-origin

---

### 2.3 프론트엔드

#### 2.3.1 최신 스택 활용

Next.js 16 App Router와 React 19를 사용하여 서버 컴포넌트, 스트리밍 SSR 등 최신 웹 기술을 활용한다.

#### 2.3.2 CSP 헤더 설정

Content-Security-Policy를 설정하여 기본적인 XSS 방어를 제공한다.

**파일**: `apps/web/next.config.ts` lines 14-22

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

`frame-ancestors 'none'` 설정으로 클릭재킹 공격을 방지하며, `default-src 'self'`로 기본 소스를 제한한다.

#### 2.3.3 서버 시간 동기화

클라이언트-서버 시간 차이를 RTT 기반으로 보정하여 정확한 카운트다운을 제공한다.

**파일**: `apps/web/src/hooks/use-server-time.ts` lines 17-39

```typescript
const before = Date.now();
const res = await fetch(`${resolveBaseUrl()}/api/time`);
const after = Date.now();
const data = await res.json();
const rtt = after - before;
const serverTime = new Date(data.time).getTime();
const clientMidpoint = before + rtt / 2;
cachedOffset = serverTime - clientMidpoint;
```

NTP와 유사한 RTT/2 보정 방식으로 네트워크 지연을 감안한 정확한 시간 동기화를 구현했다.

#### 2.3.4 동적 Polling 간격 최적화

대기열 위치에 따라 polling 간격을 1초~60초로 동적 조정하여 불필요한 서버 부하를 줄인다.

**파일**: `apps/web/src/hooks/use-queue-polling.ts`

- line 8: `MIN_POLL_SECONDS = 1`
- line 9: `MAX_POLL_SECONDS = 60`
- lines 44-46: 서버 응답의 `nextPoll` 값으로 간격 조정

```typescript
if (data.nextPoll != null) {
    pollIntervalRef.current = clampPoll(data.nextPoll);
}
```

#### 2.3.5 한국어 완전 지원

날짜, 가격, UI 텍스트 전반에 한국어 로케일을 적용했다.

**파일**: `apps/web/src/lib/format.ts`

- line 1: 한국어 요일명 `["일", "월", "화", "수", "목", "금", "토"]`
- line 14: `${y}년 ${m}월 ${day}일 (${dow}) ${hh}:${mm}` 형식
- line 21: `Intl.NumberFormat("ko-KR")` 가격 포맷
- line 26-27: `toLocaleDateString("ko-KR")` 날짜 포맷

---

### 2.4 인프라

#### 2.4.1 Kind + Prod 분리된 K8s Overlay

Kustomize overlay를 통해 개발(Kind)과 프로덕션 환경을 완전히 분리한다.

```
k8s/spring/
  base/                    # 공통 매니페스트
  overlays/
    kind/                  # 로컬 개발 환경
    prod/                  # 프로덕션 환경
```

프로덕션 overlay에서 추가 리소스를 적용한다.

**파일**: `k8s/spring/overlays/prod/kustomization.yaml` lines 5-8

```yaml
resources:
  - ../../base
  - pdb.yaml
  - hpa.yaml
```

#### 2.4.2 HPA 자동 스케일링

프로덕션 환경에서 CPU 사용률 70% 기준으로 자동 스케일링을 설정했다.

**파일**: `k8s/spring/overlays/prod/hpa.yaml`

| 서비스 | Min | Max | 근거 |
|--------|-----|-----|------|
| Gateway Service | 3 | 10 | lines 10-11 |
| Ticket Service | 3 | 10 | lines 29-30 |
| Queue Service | 3 | 8 | lines 48-49 |
| Payment Service | 2 | 6 | lines 67-68 |

#### 2.4.3 PDB (PodDisruptionBudget)

모든 핵심 서비스에 `minAvailable: 1`을 설정하여 노드 유지보수 시에도 최소 1개 Pod가 항상 가동된다.

**파일**: `k8s/spring/overlays/prod/pdb.yaml`

적용 서비스: gateway-service (line 3), ticket-service (line 13), queue-service (line 23), payment-service (line 33), auth-service (line 43), stats-service (line 53), community-service (line 63)

#### 2.4.4 Terraform 모듈화

인프라를 12개 Terraform 모듈로 분리하여 재사용성과 관리 용이성을 확보했다.

**디렉터리**: `terraform/modules/`

```
alb/            # Application Load Balancer
cloudfront/     # CDN + Lambda@Edge
eks/            # Kubernetes 클러스터
elasticache/    # Redis 캐시
iam/            # IAM 역할/정책
lambda-worker/  # 백그라운드 워커
rds/            # PostgreSQL 데이터베이스
s3/             # 오브젝트 스토리지
secrets/        # Secrets Manager
sqs/            # 메시지 큐
vpc/            # 네트워크
vpc-endpoints/  # VPC 엔드포인트
```

#### 2.4.5 RDS Proxy + TLS 강제

RDS Proxy를 통한 커넥션 풀링과 TLS 암호화를 강제한다.

**파일**: `terraform/modules/rds/main.tf`

- line 159: `require_tls = true` - TLS 연결 강제
- lines 174-178: 커넥션 풀 설정 (`connection_borrow_timeout = 120`, `max_connections_percent = 100`)
- lines 47-105: Multi-AZ, 암호화 스토리지(`storage_encrypted = true`), Performance Insights

#### 2.4.6 CI/CD with Trivy 보안 스캔

GitHub Actions 파이프라인에서 Trivy를 사용한 컨테이너 이미지 취약점 스캔을 수행한다.

**파일**: `.github/workflows/auth-service-ci-cd.yml` lines 105-110

```yaml
- name: Run security scan (Trivy)
  uses: aquasecurity/trivy-action@master
```

#### 2.4.7 풀스택 옵저버빌리티

로깅, 메트릭, 분산 추적을 포함하는 포괄적인 관측 가능성 스택을 구축했다.

| 컴포넌트 | 역할 | 파일 |
|----------|------|------|
| Grafana | 대시보드/시각화 | `k8s/spring/overlays/kind/grafana.yaml` |
| Loki | 로그 수집/검색 | `k8s/spring/overlays/kind/loki.yaml` |
| Promtail | 로그 전송 에이전트 | `k8s/spring/overlays/kind/promtail.yaml` |
| Zipkin | 분산 추적 | `k8s/spring/overlays/kind/zipkin.yaml` |

분산 추적을 위한 로그 패턴:

**파일**: `services-spring/gateway-service/src/main/resources/application.yml` line 92

```yaml
level: "%5p [${spring.application.name:},%X{traceId:-},%X{spanId:-}]"
```

---

### 2.5 결제 시스템

#### 2.5.1 Toss Payments SDK 연동

Toss Payments SDK를 사용한 실제 결제 플로우를 구현했다.

**파일**: `apps/web/src/app/payment/[reservationId]/page.tsx` lines 61-78

```typescript
if (method === "toss") {
    const prepRes = await paymentsApi.prepare({
        reservationId: params.reservationId,
        amount: info?.total_amount,
    });
    const { loadTossPayments } = await import("@tosspayments/payment-sdk");
    const tossPayments = await loadTossPayments(clientKey);
    await tossPayments.requestPayment("카드", {
        amount: info?.total_amount ?? 0,
        orderId,
        orderName: info?.event_title ?? "티켓 결제",
        successUrl: `${window.location.origin}/payment/success`,
        failUrl: `${window.location.origin}/payment/fail`,
    });
}
```

#### 2.5.2 멱등성 보장

orderId 기반으로 중복 결제를 방지하며, 기존 결제가 있을 경우 새로 생성하지 않고 기존 값을 반환한다.

**파일**: `services-spring/payment-service/src/main/java/com/tiketi/paymentservice/service/PaymentService.java`

- lines 80-102: 기존 결제 조회 및 중복 방지
- line 97-98: 이미 확인된 결제인 경우 예외 발생

```java
if ("confirmed".equals(String.valueOf(payment.get("status")))) {
    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Payment already confirmed");
}
```

#### 2.5.3 Pessimistic Lock (FOR UPDATE)

결제 확인(confirm) 시 `SELECT ... FOR UPDATE`로 비관적 잠금을 적용하여 동시 결제 확인을 방지한다.

**파일**: `services-spring/payment-service/src/main/java/com/tiketi/paymentservice/service/PaymentService.java` lines 118-123

```java
List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
    SELECT id, reservation_id, user_id, amount, status, payment_type, reference_id
    FROM payments
    WHERE order_id = ?
    FOR UPDATE
    """, request.orderId());
```

---

### 2.6 대기열 시스템

#### 2.6.1 Redis Lua 원자적 입장 제어

대기열에서 활성 사용자로의 전환을 단일 Lua 스크립트로 원자적으로 처리하여 중복 입장을 방지한다.

**파일**: `services-spring/queue-service/src/main/resources/redis/admission_control.lua`

핵심 로직:
- line 18: 만료된 활성 사용자 제거 (`ZREMRANGEBYSCORE`)
- line 21: 현재 활성 사용자 수 계산 (`ZCARD`)
- line 32: 원자적 대기열 팝 (`ZPOPMIN`) - 중복 불가능
- lines 39-43: 활성 목록에 추가 및 seen 목록에서 제거

```lua
-- 4. ZPOPMIN - atomic pop from queue (no duplicates)
local popped = redis.call('ZPOPMIN', queueKey, toAdmit)
```

`ZPOPMIN`은 sorted set에서 가장 낮은 점수의 멤버를 원자적으로 제거하므로, 두 worker가 동시에 실행되더라도 같은 사용자가 두 번 입장하는 것이 불가능하다.

#### 2.6.2 Kafka 이벤트 중복 처리 방지

Stats Service에서 `processed_events` 테이블을 사용한 idempotent consumer 패턴을 구현했다.

**파일**: `services-spring/stats-service/src/main/resources/db/migration/V2__processed_events_table.sql` lines 1-5

```sql
-- H7: Deduplication table to prevent double-counting on Kafka redelivery
CREATE TABLE IF NOT EXISTS processed_events (
    event_key VARCHAR(255) PRIMARY KEY,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```

Kafka 재전달(redelivery) 시에도 이벤트가 중복 처리되지 않도록 보장한다.

---

## 3. 미흡한 점 (개선 필요)

### 3.1 보안 취약점

#### 3.1.1 [위험도: 높음] localStorage 토큰 저장

JWT 토큰을 `localStorage`에 저장하여 XSS 공격 시 토큰 탈취가 가능하다.

**파일**: `apps/web/src/lib/storage.ts`

- line 10: `return localStorage.getItem(TOKEN_KEY);`
- line 15: `localStorage.setItem(TOKEN_KEY, token);`

`localStorage`는 JavaScript에서 접근 가능하므로, XSS 취약점이 존재할 경우 공격자가 토큰을 탈취할 수 있다. `httpOnly` 쿠키는 JavaScript에서 접근이 불가능하여 XSS에 대한 근본적 방어를 제공한다.

#### 3.1.2 [위험도: 높음] CSP unsafe-inline/unsafe-eval

CSP에 `unsafe-inline`과 `unsafe-eval`을 허용하여 XSS 방어가 약화된다.

**파일**: `apps/web/next.config.ts` line 17

```typescript
"script-src 'self' 'unsafe-inline' 'unsafe-eval'",
```

`unsafe-inline`은 인라인 스크립트 실행을 허용하고, `unsafe-eval`은 `eval()` 함수 사용을 허용하므로 CSP의 핵심 보호 기능이 무력화된다.

#### 3.1.3 [위험도: 높음] Refresh Token 미활용 (프론트엔드)

백엔드에서 refresh token을 발급하고 갱신 API도 구현되어 있으나, 프론트엔드에서 활용하지 않는다.

**백엔드 구현 존재**:
- `services-spring/auth-service/src/main/java/com/tiketi/authservice/service/AuthService.java` lines 95-113: `refreshToken()` 메서드 구현 완료

**프론트엔드 미사용**:
- `apps/web/src/lib/api-client.ts` lines 64-68: 401 에러 시 silent refresh 없이 즉시 로그아웃

```typescript
if (error.response?.status === 401) {
    clearAuth();
    if (typeof window !== "undefined") {
        window.location.href = "/login";
    }
}
```

사용자 경험 측면에서 access token 만료 시 자동 갱신이 이루어지지 않아 빈번한 재로그인이 발생할 수 있다.

#### 3.1.4 [위험도: 중간] Google OAuth 토큰 검증 방식

Google OAuth 토큰을 서명 검증이 아닌 `tokeninfo` 엔드포인트 호출로 검증한다.

**파일**: `services-spring/auth-service/src/main/java/com/tiketi/authservice/service/AuthService.java` lines 168-175

```java
payload = googleRestClient.get()
    .uri(uriBuilder -> uriBuilder.path("/tokeninfo").queryParam("id_token", credential).build())
    .retrieve()
    ...
```

`tokeninfo` 엔드포인트는 매 요청마다 Google 서버에 HTTP 호출을 수행하므로:
1. Google API 장애 시 로그인 불가
2. 네트워크 레이턴시 추가
3. Google 서버 의존성 증가

Google 공개키를 캐싱하여 로컬에서 JWT 서명을 검증하는 방식이 권장된다.

#### 3.1.5 [위험도: 중간] 내부 API 토큰 단순 문자열

서비스 간 인증에 단순 문자열 토큰을 사용한다.

**파일**: `k8s/spring/overlays/kind/secrets.env` line 12

```
INTERNAL_API_TOKEN=dev-internal-token-change-me
```

프로덕션에서도 단순 문자열 비교 방식을 사용할 경우, 토큰 탈취 시 모든 내부 API에 무제한 접근이 가능하다.

#### 3.1.6 [위험도: 낮음] CORS credentials: true

CORS에서 `credentials: true`를 설정하여 origin 설정 실수 시 CSRF 가능성이 존재한다.

**파일**: `services-spring/gateway-service/src/main/java/com/tiketi/gatewayservice/config/CorsConfig.java` line 26

```java
config.setAllowCredentials(true);
```

현재 `allowedOrigins`를 환경변수로 설정하므로 와일드카드 사용은 방지되나, 잘못된 origin 추가 시 위험하다.

---

### 3.2 아키텍처 문제

#### 3.2.1 [위험도: 높음] Ticket Service 비대

단일 Ticket Service에 8개 도메인(이벤트, 좌석, 예매, 양도, 멤버십, 아티스트, 관리자, 티켓)이 모두 포함되어 있다.

**디렉터리**: `services-spring/ticket-service/src/main/java/com/tiketi/ticketservice/domain/`

```
admin/  artist/  event/  membership/  reservation/  seat/  ticket/  transfer/
```

다른 서비스(Auth, Payment, Stats, Queue, Community)가 각각 하나의 책임만 가지는 것과 대비된다. Ticket Service의 장애가 이벤트 조회, 좌석 선택, 예매, 양도, 멤버십 등 시스템의 핵심 기능 전체에 영향을 준다.

#### 3.2.2 [위험도: 높음] 동기 서비스 간 호출 + Circuit Breaker 부재

Payment Service에서 Ticket Service로의 동기 REST 호출에 Circuit Breaker가 없다.

**파일**: `services-spring/payment-service/src/main/java/com/tiketi/paymentservice/service/PaymentService.java`

- line 67: `ticketInternalClient.validateReservation(request.reservationId(), userId)` - 동기 호출
- line 56: `ticketInternalClient.validateTransfer(referenceId, userId)` - 동기 호출
- line 61: `ticketInternalClient.validateMembership(referenceId, userId)` - 동기 호출

Ticket Service 장애 시 Payment Service도 연쇄 장애가 발생한다. Resilience4j 등의 Circuit Breaker가 없으며, retry/backoff도 미구현이다.

#### 3.2.3 [위험도: 중간] API 버저닝 부재

모든 API 경로가 버전 없이 구성되어 있다.

**파일**: `services-spring/gateway-service/src/main/resources/application.yml`

```yaml
- Path=/api/events/**     # /api/v1/events/** 아님
- Path=/api/auth/**       # /api/v1/auth/** 아님
```

API 변경 시 하위 호환성 유지가 어려우며, 클라이언트 강제 업데이트가 필요하다.

---

### 3.3 프론트엔드 문제

#### 3.3.1 [위험도: 중간] 전역 상태 관리 부재

`useState`만 사용하며 전역 상태 관리 라이브러리(Zustand, Jotai 등)가 없다.

**파일**: `apps/web/src/lib/storage.ts` - localStorage 직접 접근으로 상태 공유

복잡한 상태 공유 시 prop drilling이 발생하며, 서버 상태와 클라이언트 상태의 분리가 명확하지 않다.

#### 3.3.2 [위험도: 중간] 에러 바운더리 미비

전역 `error.tsx`가 존재하나 단일 페이지이며, 세분화된 에러 처리가 부족하다.

**파일**: `apps/web/src/app/error.tsx` lines 1-33

영문 메시지("Something went wrong")만 제공하며, 에러 유형별 분기가 없다. 네트워크 에러, 서버 에러, 클라이언트 에러에 대한 구분된 UI가 필요하다.

#### 3.3.3 [위험도: 중간] 접근성(a11y) 부족

`aria-label`, `role`, `keyboard navigation` 관련 코드가 전반적으로 미흡하다.

**파일**: `apps/web/src/app/error.tsx` - 에러 페이지에도 `role="alert"`, `aria-live` 속성 없음

#### 3.3.4 [위험도: 높음] 프론트엔드 테스트 부재

프론트엔드 유닛 테스트, 통합 테스트, E2E 테스트가 전혀 존재하지 않는다. `apps/web/` 내에 테스트 파일이 없다.

#### 3.3.5 [위험도: 낮음] 이미지 최적화 미적용

Next.js의 `next/image` 컴포넌트를 활용하지 않아 이미지 자동 최적화(WebP 변환, 지연 로딩, 크기 조절)가 적용되지 않는다.

---

### 3.4 인프라 문제

#### 3.4.1 [위험도: 높음] Secrets 평문 저장

K8s 시크릿이 평문 파일로 관리된다.

**파일**: `k8s/spring/overlays/kind/secrets.env`

- line 2: `POSTGRES_PASSWORD=tiketi_password`
- line 11: `JWT_SECRET=c3ByaW5nLWtpbmQtdGVzdC1qd3Qtc2VjcmV0LTIwMjYtMDItMTA=`
- line 12: `INTERNAL_API_TOKEN=dev-internal-token-change-me`
- line 14: `TOSS_CLIENT_KEY=test_ck_dummy`

`secrets.env.example`이 존재하여 패턴은 올바르나, 실제 시크릿 파일이 리포지토리에 포함되어 있다.

#### 3.4.2 [위험도: 높음] Kafka 단일 노드

Kind 환경에서 Kafka가 단일 노드로 구성되며, replication factor가 1이다.

**파일**: `k8s/spring/overlays/kind/kafka.yaml`

- line 8: `replicas: 1`
- line 39: `KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: "1"`
- line 41: `KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR: "1"`

프로덕션에서 브로커 장애 시 모든 이벤트 기반 통신이 중단된다.

#### 3.4.3 [위험도: 중간] Dragonfly/Redis 단일 인스턴스

Redis(Dragonfly)가 단일 인스턴스로 운영되며, Sentinel이나 Cluster 모드가 없다.

Rate limiting, 좌석 잠금, 대기열 관리 등 핵심 기능이 Redis에 의존하므로, Redis 장애 시 시스템 전체에 영향을 준다.

#### 3.4.4 [위험도: 중간] Grafana 기본 비밀번호

Grafana에 기본 비밀번호(admin/admin)가 설정되어 있다.

**파일**: `k8s/spring/overlays/kind/grafana.yaml` lines 41-44

```yaml
- name: GF_SECURITY_ADMIN_USER
  value: "admin"
- name: GF_SECURITY_ADMIN_PASSWORD
  value: "admin"
```

#### 3.4.5 [위험도: 중간] Health Check 미비

서비스 health 엔드포인트가 단순 상태 반환만 수행하며, 데이터베이스나 Redis 연결 상태를 확인하지 않는다.

**파일**: `services-spring/ticket-service/src/main/java/com/tiketi/ticketservice/controller/OpsController.java` lines 10-13

```java
@GetMapping("/health")
public Map<String, String> health() {
    return Map.of("status", "ok", "service", "ticket-service");
}
```

실제 의존성(DB, Redis, Kafka)이 장애 상태여도 "ok"를 반환하여, K8s 헬스체크가 장애를 감지하지 못한다.

#### 3.4.6 [위험도: 낮음] 로그 보존 정책 미설정

Loki의 로그 보존 기간이 설정되지 않아 디스크 공간이 무한히 증가할 수 있다.

---

### 3.5 테스트

#### 3.5.1 [위험도: 높음] 통합 테스트 부족

서비스 간 통신(Gateway -> Ticket, Payment -> Ticket 등)에 대한 통합 테스트가 존재하지 않는다.

#### 3.5.2 [위험도: 높음] 부하 테스트 미구성

15,000명 동시 접속 목표를 설정했으나, 이를 검증하기 위한 부하 테스트 도구(k6, Gatling 등)나 시나리오가 없다.

#### 3.5.3 [위험도: 낮음] Chaos Engineering 미도입

장애 복구(Redis 장애, Kafka 장애, 네트워크 파티션 등)를 검증하기 위한 chaos engineering 프레임워크가 없다.

---

## 4. 개선 권장사항

### 4.1 우선순위 P0 (즉시 - 1~2주)

| 번호 | 항목 | 위험도 | 관련 파일 | 설명 |
|------|------|--------|-----------|------|
| P0-1 | httpOnly cookie 전환 | 높음 | `apps/web/src/lib/storage.ts` | localStorage 대신 httpOnly/Secure/SameSite=Strict 쿠키 사용. 서버 측에서 Set-Cookie 헤더 발급 |
| P0-2 | Refresh token silent renewal | 높음 | `apps/web/src/lib/api-client.ts` lines 64-68 | 401 응답 시 refresh token으로 자동 갱신 후 원래 요청 재시도 |
| P0-3 | Circuit breaker 추가 | 높음 | `services-spring/payment-service/` | Resilience4j 도입, 서비스 간 동기 호출에 circuit breaker + retry + timeout 적용 |
| P0-4 | Deep health check | 중간 | `OpsController.java` (모든 서비스) | Spring Boot Actuator health indicator로 DB, Redis, Kafka 연결 상태 포함 |
| P0-5 | Secrets 관리 강화 | 높음 | `k8s/spring/overlays/kind/secrets.env` | .gitignore에 secrets.env 추가, 프로덕션은 AWS Secrets Manager 또는 Sealed Secrets 사용 |

### 4.2 우선순위 P1 (단기 - 1~2개월)

| 번호 | 항목 | 위험도 | 관련 파일 | 설명 |
|------|------|--------|-----------|------|
| P1-1 | Ticket Service 도메인 분리 | 높음 | `services-spring/ticket-service/domain/` | reservation, transfer, membership을 독립 서비스로 분리 검토 |
| P1-2 | API 버저닝 도입 | 중간 | `gateway-service/application.yml` | `/api/v1/` 경로 접두사 도입 |
| P1-3 | 프론트엔드 테스트 | 높음 | `apps/web/` | Jest + React Testing Library (유닛), Playwright (E2E) 도입 |
| P1-4 | 부하 테스트 시나리오 | 높음 | 신규 | k6 또는 Gatling으로 15,000명 동시 접속 시나리오 작성 |
| P1-5 | Grafana 대시보드 + 알림 | 중간 | `k8s/spring/overlays/kind/grafana.yaml` | 사전 구성된 대시보드 프로비저닝, Slack/PagerDuty 알림 설정 |
| P1-6 | Kafka 프로덕션 구성 | 높음 | `k8s/spring/overlays/kind/kafka.yaml` | 3 브로커, replication.factor=3, min.insync.replicas=2 |

### 4.3 우선순위 P2 (중기 - 3~6개월)

| 번호 | 항목 | 위험도 | 관련 파일 | 설명 |
|------|------|--------|-----------|------|
| P2-1 | CSP nonce 기반 전환 | 중간 | `apps/web/next.config.ts` lines 17-18 | unsafe-inline/unsafe-eval 제거, nonce 기반 CSP 적용 |
| P2-2 | mTLS 서비스 인증 | 중간 | `InternalTokenValidator.java` | Istio service mesh 또는 mTLS 기반 서비스 간 인증 |
| P2-3 | Google OAuth 서명 검증 | 중간 | `AuthService.java` lines 168-175 | google-auth-library로 공개키 캐싱 + 로컬 JWT 서명 검증 |
| P2-4 | 접근성(WCAG 2.1 AA) | 중간 | `apps/web/src/` 전체 | aria 속성, 키보드 네비게이션, 스크린 리더 호환성 개선 |
| P2-5 | Redis Cluster/Sentinel | 중간 | K8s Redis 설정 | HA 구성으로 Redis 단일 장애점 제거 |
| P2-6 | Chaos Engineering | 낮음 | 신규 | Litmus/Chaos Mesh 도입, 정기적 장애 복구 훈련 |

---

## 부록: 평가 기준

### 등급 기준

| 등급 | 점수 범위 | 의미 |
|------|-----------|------|
| A+ | 95-100 | 업계 선도 수준, 개선점 거의 없음 |
| A | 90-94 | 프로덕션 우수 수준, 경미한 개선만 필요 |
| A- | 85-89 | 프로덕션 양호 수준, 일부 보완 필요 |
| B+ | 80-84 | 프로덕션 배포 가능, 보완 항목 존재 |
| B | 75-79 | 기능적 완성, 품질 보완 필요 |
| B- | 70-74 | 기본 기능 동작, 상당한 보완 필요 |

### 위험도 기준

| 위험도 | 의미 | 조치 시한 |
|--------|------|-----------|
| 높음 | 보안 침해, 데이터 유실, 서비스 장애 가능 | 즉시~2주 |
| 중간 | 사용자 경험 저하, 운영 효율 감소 | 1~3개월 |
| 낮음 | 기술 부채, 장기적 유지보수 비용 증가 | 3~6개월 |
