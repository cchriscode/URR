# 08. 종합 평가

## 1. 프로젝트 개요

### 1.1 프로젝트 목적

Tiketi는 이벤트 및 공연 티켓 예매 플랫폼으로, 대규모 동시 접속 환경에서 안정적인 좌석 예약, 결제, 대기열 관리를 목표로 설계된 마이크로서비스 아키텍처(MSA) 기반 시스템이다.

### 1.2 기술 스택 요약

| 계층 | 기술 |
|------|------|
| **프론트엔드** | Next.js 16.1.6, React 19.2.3, TypeScript 5.9.3, TailwindCSS 4, React Query 5 (`apps/web/package.json:18-19`) |
| **백엔드** | Spring Boot 3.5, Java 21 (eclipse-temurin:21), Spring Cloud Gateway MVC (`services-spring/gateway-service/Dockerfile:1`) |
| **메시징** | Apache Kafka 3.7.0, KRaft 모드 (`k8s/spring/overlays/kind/kafka.yaml:19`) |
| **캐시/락** | Dragonfly (Redis 호환) (`k8s/spring/overlays/kind/dragonfly.yaml:19`) |
| **데이터베이스** | PostgreSQL, 서비스별 독립 DB (`services-spring/ticket-service/src/main/resources/application.yml:22-24`) |
| **인프라** | Kubernetes (Kind 로컬, AWS EKS 대상), Kustomize (`k8s/spring/overlays/kind/kustomization.yaml:1-4`) |
| **모니터링** | Prometheus v2.51.0, Grafana 10.2.3, Loki 2.9.3, Zipkin 3 (`k8s/spring/overlays/kind/prometheus.yaml:68`, `k8s/spring/overlays/kind/grafana.yaml:62`, `k8s/spring/overlays/kind/loki.yaml:63`, `k8s/spring/overlays/kind/zipkin.yaml:17`) |
| **IaC** | Terraform (`terraform/`) |

### 1.3 MSA 구성: 9개 서비스

`k8s/spring/base/kustomization.yaml:4-22` 에서 확인되는 서비스 목록:

| 서비스 | 포트 | 역할 |
|--------|------|------|
| **gateway-service** | 3001 | API 게이트웨이, JWT 검증, Rate Limiting, VWR 토큰 검증 |
| **auth-service** | 3005 | 사용자 인증/인가, Google OAuth, JWT 발급 |
| **ticket-service** | 3002 | 좌석 관리, 예약, 멤버십, 양도 |
| **payment-service** | 3003 | 결제 처리, TossPayments 연동 |
| **queue-service** | 3007 | 가상 대기열(VWR), 입장 제어 |
| **catalog-service** | 3009 | 이벤트 카탈로그, 관리자 기능 |
| **stats-service** | 3004 | 통계 집계, 대시보드 데이터 |
| **community-service** | 3008 | 커뮤니티 게시판, 뉴스 |
| **frontend** | 3000 | Next.js 웹 애플리케이션 |

---

## 2. 강점 분석

### 2.1 아키텍처

**MSA 서비스 분리**

각 서비스가 독립 데이터베이스를 보유하여 서비스 간 데이터 결합도가 낮다. ticket-service는 `ticket_db`, stats-service는 별도 DB, auth-service는 `users` 테이블 전용 DB를 사용한다.

- ticket-service DB 연결: `services-spring/ticket-service/src/main/resources/application.yml:22-24`
- auth-service DB 마이그레이션: `services-spring/auth-service/src/main/resources/db/migration/V1__create_users_table.sql`
- payment-service DB 마이그레이션: `services-spring/payment-service/src/main/resources/db/migration/V1__payment_schema.sql`

**이벤트 드리븐 설계**

Kafka를 중심으로 서비스 간 비동기 통신이 구현되어 있다. 결제 완료 이벤트는 `payment-events` 토픽을 통해 ticket-service와 stats-service가 독립적으로 소비한다.

- ticket-service Kafka 소비: `services-spring/ticket-service/src/main/java/com/tiketi/ticketservice/messaging/PaymentEventConsumer.java:49` -- `@KafkaListener(topics = "payment-events", groupId = "ticket-service-group")`
- stats-service Kafka 소비: `services-spring/stats-service/src/main/java/com/tiketi/statsservice/messaging/StatsEventConsumer.java:25` -- `@KafkaListener(topics = "payment-events", groupId = "stats-service-group")`
- 이벤트 발행 토픽: `reservation-events`, `transfer-events`, `membership-events` (`services-spring/ticket-service/src/main/java/com/tiketi/ticketservice/messaging/TicketEventProducer.java:25-77`)

**Saga 패턴**

결제 확인 후 예매 확정 그리고 포인트 적립으로 이어지는 흐름이 Kafka 이벤트 체인으로 구현되어 있다.

1. 결제 완료 이벤트 수신: `PaymentEventConsumer.java:49-93`
2. 예약 확정 처리: `PaymentEventConsumer.java:107` -- `reservationService.confirmReservationPayment(reservationId, paymentMethod)`
3. 포인트 적립: `services-spring/ticket-service/src/main/java/com/tiketi/ticketservice/domain/reservation/service/ReservationService.java:454` -- `membershipService.awardPointsForArtist(userId, artistId, "TICKET_PURCHASE", 100, ...)`
4. 확인 이벤트 재발행: `PaymentEventConsumer.java:114-115` -- `ticketEventProducer.publishReservationConfirmed(...)`

**API 게이트웨이 중앙화**

gateway-service가 모든 외부 요청의 진입점으로, 인증(JWT), Rate Limiting, VWR 토큰 검증을 한 곳에서 처리한다.

- 라우팅 규칙 (15개 경로): `services-spring/gateway-service/src/main/resources/application.yml:10-75`
- JWT 인증 필터: `services-spring/gateway-service/src/main/java/com/tiketi/gatewayservice/filter/JwtAuthFilter.java:35`
- Rate Limit 필터: `services-spring/gateway-service/src/main/java/com/tiketi/gatewayservice/filter/RateLimitFilter.java:22`
- VWR 토큰 필터: `services-spring/gateway-service/src/main/java/com/tiketi/gatewayservice/filter/VwrEntryTokenFilter.java:26`

---

### 2.2 동시성 제어

이 프로젝트의 가장 두드러진 기술적 강점은 3계층 동시성 제어 메커니즘이다.

**Layer 1: Redis Lua 원자적 좌석 잠금**

Redis Lua 스크립트로 좌석 상태 확인과 잠금 획득을 단일 원자적 연산으로 수행하여 경쟁 조건을 원천적으로 방지한다.

- Lua 스크립트: `services-spring/ticket-service/src/main/resources/redis/seat_lock_acquire.lua:1-36`
- 상태 확인 + 잠금을 하나의 스크립트에서 수행: `seat_lock_acquire.lua:12-22` -- HELD/CONFIRMED 상태 확인 후 동일 사용자 재선택 시 TTL 연장
- 단조 증가 펜싱 토큰 생성: `seat_lock_acquire.lua:25` -- `local token = redis.call('INCR', tokenSeqKey)`
- 원자적 상태 전이: `seat_lock_acquire.lua:28-34` -- HMSET으로 status, userId, token, heldAt를 한 번에 설정

**Layer 2: DB FOR UPDATE 비관적 잠금**

Redis 잠금 성공 후 PostgreSQL SELECT FOR UPDATE로 DB 수준의 2차 검증을 수행한다.

- FOR UPDATE 쿼리: `services-spring/ticket-service/src/main/java/com/tiketi/ticketservice/domain/reservation/service/ReservationService.java:92-99` -- `SELECT id, seat_label, price, status, version FROM seats WHERE id IN (:seatIds) AND event_id = :eventId FOR UPDATE`
- 낙관적 잠금(version): `ReservationService.java:119-132` -- `UPDATE seats SET status = 'locked', version = version + 1, fencing_token = ? ... WHERE id = ? AND version = ?`
- DB 수준 펜싱 토큰 저장: `services-spring/ticket-service/src/main/resources/db/migration/V8__seats_concurrency_columns.sql:2-5` -- version, fencing_token, locked_by 컬럼

**Layer 3: 멱등성 보장**

Kafka 이벤트 중복 처리를 방지하기 위한 processed_events 테이블 기반 멱등성 메커니즘이 구현되어 있다.

- 멱등성 테이블 스키마: `services-spring/ticket-service/src/main/resources/db/migration/V14__processed_events.sql:2-6`
- 중복 체크: `services-spring/ticket-service/src/main/java/com/tiketi/ticketservice/messaging/PaymentEventConsumer.java:209-218` -- `isAlreadyProcessed(eventKey)`
- 처리 완료 기록: `PaymentEventConsumer.java:221-231` -- `markProcessed(eventKey)`
- 예약 요청 멱등성 키: `ReservationService.java:62-69` -- 동일 `idempotencyKey`에 대해 기존 예약 반환

**펜싱 토큰을 통한 유령 잠금 방지**

결제 시 Redis 펜싱 토큰을 재검증하여, TTL 만료 후 다른 사용자가 획득한 잠금에 대해 이전 사용자의 결제가 통과되는 것을 방지한다.

- 결제 검증 Lua 스크립트: `services-spring/ticket-service/src/main/resources/redis/payment_verify.lua:1-19` -- userId와 token 동시 검증 후 CONFIRMED 상태로 전이
- Java 검증 호출: `services-spring/ticket-service/src/main/java/com/tiketi/ticketservice/domain/seat/service/SeatLockService.java:81-98` -- `verifyForPayment(eventId, seatId, userId, token)`
- 결제 확정 시 펜싱 토큰 DB 교차 검증: `ReservationService.java:402-417` -- seats 테이블의 fencing_token으로 Redis 검증

---

### 2.3 VWR (Virtual Waiting Room)

**Redis ZSET 기반 대기열**

Redis Sorted Set을 활용하여 대기열 순서(입장 시간 기준 score)와 활성 사용자 관리(만료 시간 기준 score)를 효율적으로 처리한다.

- 대기열 추가: `services-spring/queue-service/src/main/java/com/tiketi/queueservice/service/QueueService.java:330` -- `redisTemplate.opsForZSet().add(queueKey(eventId), userId, System.currentTimeMillis())`
- 활성 사용자 관리: `QueueService.java:301-304` -- expiryScore 기반 TTL 관리
- 위치 조회: `QueueService.java:319-321` -- `redisTemplate.opsForZSet().rank()` O(log N) 연산
- 활성 사용자 수 조회: `QueueService.java:296-298` -- 현재 시간 이후 만료 score만 카운트

**동적 폴링 간격**

대기열 위치에 따라 클라이언트 폴링 간격을 1초~60초로 차등 적용하여 서버 부하를 최소화한다.

- 위치 기반 폴링: `QueueService.java:231-238` -- position 1000 이하: 1초, 5000 이하: 5초, 10000 이하: 10초, 100000 이하: 30초, 그 이상: 60초

**처리량 기반 대기 시간 추정**

슬라이딩 윈도우(1분) 동안의 실제 입장 처리량을 기반으로 대기 시간을 추정한다.

- 처리량 추적: `QueueService.java:36-38` -- AtomicLong 기반 1분 윈도우
- 대기 시간 계산: `QueueService.java:242-258` -- `position / throughputPerSecond`
- 입장 기록: `QueueService.java:168-177` -- `recordAdmissions(count)`

**JWT 입장 토큰 + userId 바인딩**

대기열 통과 시 발급되는 JWT 입장 토큰에 userId를 바인딩하여 토큰 탈취를 방지한다.

- 토큰 생성: `QueueService.java:215-227` -- `.claim("uid", userId)` 포함 JWT 생성
- 게이트웨이 검증: `services-spring/gateway-service/src/main/java/com/tiketi/gatewayservice/filter/VwrEntryTokenFilter.java:88-97` -- VWR 토큰의 uid와 Auth JWT의 userId 일치 여부 확인
- Timing-safe 비교: `VwrEntryTokenFilter.java:64-66` -- `MessageDigest.isEqual()` 사용

**SQS FIFO 통합**

AWS 환경 확장을 위한 SQS FIFO 큐 발행 기능이 구현되어 있다.

- SQS 발행: `services-spring/queue-service/src/main/java/com/tiketi/queueservice/service/SqsPublisher.java:38-68` -- `messageGroupId(eventId)`, `messageDeduplicationId(userId + ":" + eventId)`
- 활성화 플래그: `SqsPublisher.java:27` -- `@Value("${aws.sqs.enabled:false}")`
- 입장 시 발행: `QueueService.java:210` -- `sqsPublisher.publishAdmission(eventId, userId, entryToken)`

**원자적 입장 제어**

Lua 스크립트로 만료된 활성 사용자 제거, 가용 슬롯 계산, 대기열 팝, 활성 추가를 원자적으로 수행한다.

- 입장 제어 Lua: `services-spring/queue-service/src/main/resources/redis/admission_control.lua:1-46`
- ZPOPMIN 원자적 팝: `admission_control.lua:32` -- 경쟁 조건 없는 대기열 추출
- 배치 입장: `services-spring/queue-service/src/main/java/com/tiketi/queueservice/service/AdmissionWorkerService.java:47-118` -- 1초 간격 스케줄러, 분산 락으로 중복 실행 방지

---

### 2.4 보안

**게이트웨이 중앙 JWT 검증 + 헤더 주입**

JWT 검증을 게이트웨이에서 중앙 처리하여 하위 서비스에 JWT 시크릿을 배포하지 않는다.

- JWT 파싱 + 헤더 주입: `services-spring/gateway-service/src/main/java/com/tiketi/gatewayservice/filter/JwtAuthFilter.java:60-77` -- Claims에서 userId, email, role 추출 후 `X-User-Id`, `X-User-Email`, `X-User-Role` 헤더 주입

**외부 헤더 스트리핑 (스푸핑 방지)**

외부에서 주입된 X-User-* 헤더를 필터 최우선 순위로 제거하여 헤더 위조 공격을 방지한다.

- 스트리핑 Wrapper: `JwtAuthFilter.java:104-137` -- `UserHeaderStrippingWrapper` 클래스가 `x-user-id`, `x-user-email`, `x-user-role` 헤더를 차단
- 필터 우선순위: `JwtAuthFilter.java:34` -- `@Order(-1)` (가장 먼저 실행)

**Timing-safe 토큰 비교**

VWR 토큰 검증 시 `MessageDigest.isEqual()` 을 사용하여 타이밍 사이드 채널 공격을 방지한다.

- `services-spring/gateway-service/src/main/java/com/tiketi/gatewayservice/filter/VwrEntryTokenFilter.java:64-66` -- `MessageDigest.isEqual(cloudFrontSecret.getBytes(...), cfHeader.getBytes(...))`

**Rate Limiting: Redis Lua 슬라이딩 윈도우**

Redis ZSET 기반 슬라이딩 윈도우 Rate Limiting을 Lua 스크립트로 원자적으로 수행한다.

- Lua 스크립트: `services-spring/gateway-service/src/main/resources/redis/rate_limit.lua:1-15` -- ZREMRANGEBYSCORE + ZADD + ZCARD를 단일 스크립트로 수행
- 카테고리별 제한: `services-spring/gateway-service/src/main/java/com/tiketi/gatewayservice/filter/RateLimitFilter.java:113-128` -- AUTH(60rpm), QUEUE(120rpm), BOOKING(30rpm), GENERAL(3000rpm)
- 설정: `services-spring/gateway-service/src/main/resources/application.yml:115-119`
- Fail-open 정책: `RateLimitFilter.java:94-98` -- Redis 장애 시 요청 통과 (가용성 우선)

**K8s NetworkPolicy: 최소 권한 원칙**

기본적으로 모든 트래픽을 차단(default-deny-all)하고, 서비스별로 필요한 통신만 허용한다.

- 기본 거부: `k8s/spring/base/network-policies.yaml:1-9` -- `default-deny-all` 정책
- 게이트웨이만 백엔드 접근: `network-policies.yaml:39-59` -- auth-service는 gateway-service와 catalog-service에서만 접근 가능
- 서비스 간 필요 통신만 허용: `network-policies.yaml:61-83` -- ticket-service는 gateway, payment, catalog에서만 접근 가능
- DNS 이그레스 허용: `network-policies.yaml:178-201` -- kube-dns만 허용

**프론트엔드 보안 헤더**

Next.js 미들웨어에서 CSP, X-Frame-Options, Permissions-Policy 등 보안 헤더를 설정한다.

- CSP 헤더: `apps/web/src/middleware.ts:10-18` -- default-src 'self', frame-ancestors 'none'
- X-Frame-Options: `middleware.ts:25` -- `"DENY"`
- Permissions-Policy: `middleware.ts:28` -- `camera=(), microphone=(), geolocation=(), payment=()`
- X-Content-Type-Options: `middleware.ts:26` -- `"nosniff"`

---

### 2.5 옵저버빌리티

**4-Pillar 모니터링**

Metrics, Logs, Traces, Health 네 가지 축으로 옵저버빌리티가 구성되어 있다.

| 축 | 도구 | 설정 |
|----|------|------|
| Metrics | Prometheus | `k8s/spring/overlays/kind/prometheus.yaml:6-47` -- 8개 서비스 스크래핑 (10초 간격) |
| Logs | Loki + Promtail | `k8s/spring/overlays/kind/loki.yaml:1-123` |
| Traces | Zipkin | `k8s/spring/overlays/kind/zipkin.yaml:1-40`, 샘플링: `services-spring/gateway-service/src/main/resources/application.yml:96` |
| Dashboard | Grafana | `k8s/spring/overlays/kind/grafana.yaml:1-134`, Prometheus + Loki 데이터소스 연동 |

**구조화 로그: traceId/spanId 포함**

모든 서비스에서 로그 패턴에 traceId와 spanId를 포함하여 분산 추적이 가능하다.

- 로그 패턴: `services-spring/ticket-service/src/main/resources/application.yml:64-65` -- `"%5p [${spring.application.name:},%X{traceId:-},%X{spanId:-}]"`
- 게이트웨이도 동일 패턴: `services-spring/gateway-service/src/main/resources/application.yml:102-103`

**비즈니스 메트릭 Prometheus 연동**

Micrometer Counter/Timer를 통해 비즈니스 지표를 Prometheus에 노출한다.

- 8개 비즈니스 카운터 + 1개 타이머: `services-spring/ticket-service/src/main/java/com/tiketi/ticketservice/shared/metrics/BusinessMetrics.java:11-60`
- 예: `business.reservation.created.total`, `business.payment.processed.total`, `business.transfer.completed.total`
- Actuator Prometheus 엔드포인트 노출: `services-spring/ticket-service/src/main/resources/application.yml:53-55` -- `include: health,info,prometheus`

**Resilience4j: Circuit Breaker + Retry**

서비스 간 통신에 Circuit Breaker와 Retry 패턴이 적용되어 있다.

- Circuit Breaker 설정: `services-spring/ticket-service/src/main/resources/application.yml:87-96` -- sliding-window-size: 10, failure-rate-threshold: 50%, slow-call-duration-threshold: 3s
- Retry 설정: `services-spring/ticket-service/src/main/resources/application.yml:97-105` -- max-attempts: 3, 지수 백오프 (500ms * 2배)

---

### 2.6 인프라 & DevOps

**Kustomize 기반 환경 분리**

base/kind/dev/prod 4단계 환경을 Kustomize overlay로 관리한다.

- Base 설정: `k8s/spring/base/kustomization.yaml:1-24` -- 공통 리소스 정의
- Kind 오버레이: `k8s/spring/overlays/kind/kustomization.yaml:1-73` -- 로컬 개발용 (인프라 컴포넌트 포함)
- Dev 오버레이: `k8s/spring/overlays/dev/kustomization.yaml`
- Prod 오버레이: `k8s/spring/overlays/prod/kustomization.yaml:1-28` -- PDB, HPA 포함

**프로덕션 안정성 설정**

Prod 환경에 HPA(Auto Scaling)와 PDB(Pod Disruption Budget)가 구성되어 있다.

- HPA: `k8s/spring/overlays/prod/hpa.yaml:1-76` -- gateway(3-10), ticket(3-10), queue(3-8), payment(2-6) 레플리카, CPU 70% 기준
- PDB: `k8s/spring/overlays/prod/pdb.yaml:1-70` -- 7개 서비스 모두 minAvailable: 1

**멀티스테이지 Docker 빌드**

빌드 이미지와 런타임 이미지를 분리하여 최종 이미지 크기를 최적화하고 보안을 강화한다.

- 빌드 스테이지: `services-spring/gateway-service/Dockerfile:1-11` -- eclipse-temurin:21-jdk로 빌드
- 런타임 스테이지: `services-spring/gateway-service/Dockerfile:13-22` -- eclipse-temurin:21-jre만 포함
- 비루트 실행: `services-spring/gateway-service/Dockerfile:18-19` -- UID/GID 1001 전용 사용자 생성 후 `USER app`

**Flyway 마이그레이션**

모든 서비스에서 Flyway를 사용하여 DB 스키마를 버전 관리한다.

- ticket-service: 14개 마이그레이션 (V1~V14) -- `services-spring/ticket-service/src/main/resources/db/migration/`
- auth-service: 4개 마이그레이션 (V1~V4) -- `services-spring/auth-service/src/main/resources/db/migration/`
- payment-service: 3개 마이그레이션 -- `services-spring/payment-service/src/main/resources/db/migration/`
- Flyway 활성화: `services-spring/ticket-service/src/main/resources/application.yml:29-31` -- `flyway.enabled: true`

**자동화 스크립트 (PS1/Bash 동시 지원)**

Windows(PowerShell)와 Linux/Mac(Bash) 양쪽 환경에서 동일한 작업을 수행할 수 있는 스크립트가 쌍으로 제공된다.

- `scripts/spring-kind-up.ps1` / `scripts/spring-kind-up.sh` -- 클러스터 시작
- `scripts/spring-kind-build-load.ps1` / `scripts/spring-kind-build-load.sh` -- 이미지 빌드 및 로드
- `scripts/spring-kind-dev.ps1` / `scripts/spring-kind-dev.sh` -- 개발 환경 기동
- `scripts/cleanup.ps1` / `scripts/cleanup.sh` -- 리소스 정리

---

### 2.7 프론트엔드

**React Query 기반 서버 상태 관리**

`@tanstack/react-query` 5를 사용하여 서버 상태의 캐싱, 재검증, 에러 처리를 체계적으로 관리한다.

- 의존성: `apps/web/package.json:15` -- `"@tanstack/react-query": "^5.90.21"`

**서버 시간 동기화 (RTT 보정)**

클라이언트-서버 간 시계 차이를 RTT(Round Trip Time) 보정으로 해결한다.

- RTT 보정 로직: `apps/web/src/hooks/use-server-time.ts:22-31` -- `clientMidpoint = before + rtt / 2`, `offset = serverTime - clientMidpoint`
- 캐싱: `use-server-time.ts:14-15` -- 한 번 측정 후 캐싱하여 중복 요청 방지

**401 Silent Refresh + 요청 큐잉**

토큰 만료 시 자동으로 리프레시를 시도하고, 리프레시 중 발생하는 요청은 큐에 저장했다가 일괄 재시도한다.

- Silent Refresh: `apps/web/src/lib/api-client.ts:85-116` -- 401 응답 시 `/auth/refresh` 호출 후 원본 요청 재시도
- 요청 큐잉: `api-client.ts:92-97` -- `isRefreshing` 상태에서 후속 요청을 `failedQueue`에 저장
- 큐 일괄 처리: `api-client.ts:59-68` -- `processQueue(success)` 로 성공/실패에 따라 일괄 resolve/reject
- 429 재시도: `api-client.ts:118-127` -- 지수 백오프 (최대 2회, 1~4초)

**TossPayments SDK 동적 로딩**

결제 SDK를 의존성으로 포함하여 필요 시 로딩한다.

- 의존성: `apps/web/package.json:16` -- `"@tosspayments/payment-sdk": "^1.9.2"`

**보안 미들웨어**

- CSP nonce 기반: `apps/web/src/middleware.ts:5` -- `Buffer.from(crypto.randomUUID()).toString("base64")`
- Permissions-Policy: `middleware.ts:28` -- 카메라, 마이크, 위치, 결제 기능 전부 비활성화

---

## 3. 미흡한 부분 + AWS 보완 방안

### 3.1 실시간 통신 부재

**현재 상태**: 모든 실시간성이 폴링(polling)으로 구현되어 있다. 대기열 상태 폴링(`queueApi.status`), 좌석 상태는 페이지 로드 시 조회한다.

- 폴링 기반 대기열: `apps/web/src/lib/api-client.ts:148-153` -- queueApi의 check/status/heartbeat 모두 HTTP 요청
- 서버측 동적 폴링 간격: `services-spring/queue-service/src/main/java/com/tiketi/queueservice/service/QueueService.java:231-238` -- nextPoll 값 반환

**문제점**: 대기열 위치 변경, 좌석 실시간 점유 상태 등이 지연 반영된다. 최소 폴링 간격이 1초이므로 순간적인 좌석 경합 상태를 사용자에게 즉시 전달할 수 없다.

**AWS 보완 방안**:
- **API Gateway WebSocket API**: 대기열 위치 업데이트를 서버 푸시로 전환. Lambda 백엔드에서 DynamoDB Streams와 연동하여 위치 변경 시 자동 푸시.
- **AWS AppSync (GraphQL Subscriptions)**: 좌석 상태 변경을 구독(subscription) 방식으로 전달. DynamoDB 테이블 변경 감지를 통한 자동 알림.
- **대안**: 현재 아키텍처에 Spring WebSocket을 추가하고, ALB의 WebSocket 지원을 활용하는 방법도 가능하다.

---

### 3.2 알림 시스템 미구성

**현재 상태**: Prometheus가 메트릭을 수집하지만, PrometheusRule이나 AlertManager 설정이 없다.

- Prometheus 설정에 alerting 규칙 없음: `k8s/spring/overlays/kind/prometheus.yaml:6-47` -- `scrape_configs`만 존재하고 `alerting`이나 `rule_files` 섹션이 없음

**문제점**: 서비스 장애, 에러율 급증, Circuit Breaker 트립, Kafka consumer lag 증가 등 운영 이슈 발생 시 자동 알림이 불가하다.

**AWS 보완 방안**:
- **CloudWatch Alarms + SNS**: EKS 메트릭(CPU, 메모리, Pod 재시작), RDS 메트릭(Connection count, Replication lag), MSK 메트릭(Consumer lag) 임계치 기반 알림
- **PagerDuty/OpsGenie 연동**: SNS 토픽을 통한 on-call 엔지니어 자동 호출
- **권장 알림 규칙**: 에러율 >5%, Circuit Breaker OPEN 상태, Pod CrashLoopBackOff, Kafka consumer lag >10000, DB 커넥션 풀 90% 초과

---

### 3.3 Kafka 단일 노드

**현재 상태**: Kind 환경에서 단일 Kafka 브로커로 운영된다.

- 단일 레플리카: `k8s/spring/overlays/kind/kafka.yaml:8` -- `replicas: 1`
- 단일 노드 KRaft: `k8s/spring/overlays/kind/kafka.yaml:37` -- `KAFKA_CONTROLLER_QUORUM_VOTERS: "1@localhost:9093"`
- Replication factor 1: `k8s/spring/overlays/kind/kafka.yaml:39-41` -- `KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: "1"`, `KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR: "1"`
- 서비스 설정도 replication-factor: 1: `services-spring/ticket-service/src/main/resources/application.yml:80` -- `replication-factor: ${KAFKA_TOPIC_REPLICATION_FACTOR:1}`

**문제점**: 브로커 장애 시 전체 메시징 시스템이 중단된다. 결제 확인 이벤트, 통계 이벤트 등이 유실될 수 있다.

**AWS 보완 방안**:
- **Amazon MSK (Managed Streaming for Apache Kafka)**: 멀티 AZ 배포로 브로커 장애 시 자동 복구. 최소 3개 브로커 권장 (3 AZ).
- 토픽 replication-factor를 3으로 설정, min.insync.replicas를 2로 설정하여 데이터 내구성 확보.
- MSK Connect를 통한 커넥터 관리, MSK Serverless 옵션으로 운영 부담 최소화.

---

### 3.4 Redis 단일 노드

**현재 상태**: Dragonfly 단일 인스턴스로 운영된다.

- 단일 레플리카: `k8s/spring/overlays/kind/dragonfly.yaml:8` -- `replicas: 1`
- 메모리 제한: `k8s/spring/overlays/kind/dragonfly.yaml:21` -- `--maxmemory=512mb`
- 스냅샷만 설정: `k8s/spring/overlays/kind/dragonfly.yaml:23-25` -- 매분 스냅샷, 별도 복제 없음

**문제점**: Dragonfly 장애 시 좌석 잠금(seat lock), 대기열(queue ZSET), Rate Limiting, 펜싱 토큰 등 모든 Redis 의존 기능이 동시에 중단된다. 이는 전체 티켓팅 프로세스의 마비를 의미한다.

**AWS 보완 방안**:
- **Amazon ElastiCache for Redis (클러스터 모드)**: 멀티 AZ 자동 장애 조치, Read Replica로 읽기 분산.
- Prod 프로파일에 이미 클러스터 설정이 준비되어 있음: `services-spring/gateway-service/src/main/resources/application.yml:122-138` -- `spring.data.redis.cluster.nodes` 3노드 설정
- 최소 구성: 1 primary + 1 replica (2 AZ), 권장: 3 primary + 3 replica (3 AZ).

---

### 3.5 DB 백업/복구 전략 없음

**현재 상태**: Flyway 마이그레이션으로 스키마 버전 관리만 존재하고, 데이터 백업/복구 전략이 없다.

- Flyway 설정: `services-spring/ticket-service/src/main/resources/application.yml:29-31`
- Kind 환경 PVC 기반 저장소만 사용: `k8s/spring/overlays/kind/pvc.yaml`

**문제점**: 데이터 손실(PVC 유실, 잘못된 마이그레이션, 운영 실수) 시 복구 방안이 없다. 예매 데이터, 결제 기록 등 비즈니스 크리티컬 데이터의 내구성이 보장되지 않는다.

**AWS 보완 방안**:
- **Amazon RDS**: 자동 백업(일 1회 + 트랜잭션 로그), Point-in-Time Recovery(최대 35일), 멀티 AZ 배포
- **Read Replica**: 통계 조회(stats-service)를 Read Replica로 분리하여 운영 DB 부하 감소
- **수동 스냅샷**: 마이그레이션 실행 전 수동 스냅샷 생성 절차 수립

---

### 3.6 CI/CD 파이프라인 없음

**현재 상태**: 빌드 및 배포가 수동 스크립트로 수행된다.

- 빌드 스크립트: `scripts/spring-kind-build-load.ps1`, `scripts/spring-kind-build-load.sh`
- 배포 스크립트: `scripts/spring-kind-up.ps1`, `scripts/spring-kind-up.sh`
- Kustomize 이미지 태그 수동 관리: `k8s/spring/overlays/kind/kustomization.yaml:45-72` -- `newTag: local`

**문제점**: 코드 품질 게이트(린트, 테스트, 보안 스캔)가 자동화되어 있지 않아, 결함이 있는 코드가 배포될 수 있다. 배포 롤백 절차도 수동이다.

**AWS 보완 방안**:
- **GitHub Actions + Amazon ECR**: PR 시 자동 테스트/빌드, 머지 시 ECR 이미지 푸시
- **ArgoCD (GitOps)**: ArgoCD 디렉토리가 이미 존재하며(`argocd/`), Kustomize 오버레이와 연동하여 Git 기반 자동 배포 가능
- **파이프라인 구성**: lint/test -> build/push -> staging 배포 -> smoke test -> production 배포
- **CodePipeline 대안**: CodePipeline + CodeBuild를 사용한 AWS 네이티브 CI/CD도 가능

---

### 3.7 Secret Rotation 미구현

**현재 상태**: 시크릿을 환경 변수 파일(.env)로 정적 관리한다. AWS Secrets Manager 통합 가이드는 문서로 존재하지만 실제 구현은 되어 있지 않다.

- Kind 환경 시크릿 생성: `k8s/spring/overlays/kind/kustomization.yaml:25-27` -- `secrets.env` 파일 기반 SecretGenerator
- AWS 통합 가이드: `k8s/AWS_SECRETS_INTEGRATION.md:1-349` -- External Secrets Operator, CSI Driver 방법 문서화

**문제점**: 시크릿 교체 시 모든 서비스를 재배포해야 한다. JWT 시크릿, DB 비밀번호, Redis 인증 토큰 등의 주기적 교체가 자동화되어 있지 않다.

**AWS 보완 방안**:
- **AWS Secrets Manager + Lambda Rotation**: 자동 시크릿 교체 함수로 30/60/90일 주기 교체 자동화
- **External Secrets Operator**: `k8s/AWS_SECRETS_INTEGRATION.md:28-105`에 이미 가이드가 작성되어 있으므로, 이를 그대로 구현하면 된다
- **IRSA (IAM Roles for Service Accounts)**: Pod가 IAM 역할로 시크릿에 접근, 정적 자격 증명 불필요

---

### 3.8 로드 테스트 프레임워크 존재하나 미완성

**현재 상태**: k6 기반 로드 테스트 시나리오가 존재하나, 실행 결과나 성능 기준선이 확인되지 않는다.

- 테스트 시나리오: `tests/load/scenarios/booking-flow.js`, `tests/load/scenarios/queue-rush.js`, `tests/load/scenarios/mixed-traffic.js`, `tests/load/scenarios/browse-events.js`

**문제점**: 동시 접속 처리 능력(특히 티켓 오픈 시 순간 트래픽)의 실제 검증이 이루어지지 않았다. 3계층 동시성 제어의 실제 한계치를 파악할 수 없다.

**보완 방안**:
- k6 시나리오 실행 및 결과 기록 (성능 기준선 확립)
- 동시 좌석 예약 스트레스 테스트: Redis Lua 잠금의 실패율, DB 잠금 대기 시간 측정
- **AWS Distributed Load Testing**: CloudFormation 기반 분산 부하 테스트로 실제 AWS 환경에서 검증
- CI/CD 파이프라인에 성능 회귀 테스트 통합

---

### 3.9 Grafana 기본 비밀번호

**현재 상태**: Grafana가 기본 비밀번호(admin/admin)로 설정되어 있다.

- `k8s/spring/overlays/kind/grafana.yaml:68-69` -- `GF_SECURITY_ADMIN_USER: "admin"`, `GF_SECURITY_ADMIN_PASSWORD: "admin"`

**문제점**: 모니터링 대시보드에 무단 접근이 가능하다. Kind 로컬 환경에서는 허용 가능하나, 프로덕션 배포 시 반드시 변경해야 한다.

**AWS 보완 방안**:
- **Amazon Managed Grafana**: AWS SSO/IAM Identity Center 통합으로 중앙 인증
- 또는 Grafana 비밀번호를 AWS Secrets Manager로 관리하고, 환경변수로 주입
- LDAP/OAuth 연동으로 조직 계정 기반 접근 제어

---

### 3.10 다크 모드 & 접근성

**현재 상태**: 다크 모드가 구현되어 있지 않다. TailwindCSS 4를 사용하고 있으나 `dark:` variant 활용이 확인되지 않는다.

- TailwindCSS 4 사용: `apps/web/package.json:35` -- `"tailwindcss": "^4"`

**보완 방안**:
- TailwindCSS `dark:` 클래스 활용: `dark:bg-gray-900`, `dark:text-white` 등
- `prefers-color-scheme` 미디어 쿼리 자동 감지 또는 수동 토글 버튼
- WCAG 2.1 AA 수준 색상 대비 검증 (4.5:1 이상)
- 키보드 네비게이션, 스크린 리더 호환성 테스트

---

### 3.11 API 문서화

**현재 상태**: Swagger/OpenAPI 스펙이 생성되지 않는다. API 엔드포인트 정보는 프론트엔드 API 클라이언트 코드에서만 유추 가능하다.

- API 엔드포인트 목록: `apps/web/src/lib/api-client.ts:133-270` -- 15개 이상의 API 모듈

**보완 방안**:
- **SpringDoc OpenAPI**: 각 Spring Boot 서비스에 `springdoc-openapi-starter-webmvc-ui` 의존성 추가
- Swagger UI를 gateway-service를 통해 통합 노출
- 게이트웨이에서 각 서비스의 OpenAPI 스펙을 집계하는 방식 구현

---

### 3.12 E2E 테스트 커버리지

**현재 상태**: Playwright 의존성은 설치되어 있고 스크립트도 정의되어 있으나, 실제 테스트 케이스 작성 여부가 불분명하다.

- Playwright 의존성: `apps/web/package.json:24` -- `"@playwright/test": "^1.58.2"`
- 테스트 스크립트: `apps/web/package.json:12` -- `"test:e2e": "playwright test"`

**보완 방안**:
- 핵심 사용자 플로우 E2E 테스트 작성: 회원가입 -> 로그인 -> 이벤트 조회 -> 대기열 진입 -> 좌석 선택 -> 결제 -> 예매 확인
- CI/CD 파이프라인에 Playwright 테스트 단계 추가
- Visual regression testing으로 UI 변경 감지

---

## 4. AWS 프로덕션 아키텍처 권장안

```
                           +-----------+
                           | Route 53  |
                           |  (DNS)    |
                           +-----+-----+
                                 |
                           +-----+-----+
                           | CloudFront|
                           | + ACM SSL |
                           | + Lambda  |
                           |  @Edge    |
                           +-----+-----+
                                 |
                    +------------+------------+
                    |                         |
              +-----+-----+           +------+------+
              |    ALB     |           |     S3      |
              | (Internal) |           | (Static     |
              +-----+-----+           |  Assets)    |
                    |                  +-------------+
              +-----+-----+
              |    EKS     |
              |  Cluster   |
              +-----+-----+
                    |
    +---------------+----------------+
    |               |                |
+---+---+    +-----+-----+   +------+------+
|Gateway|    |  Services  |   | Monitoring  |
|Service|    +-----+------+   +------+------+
+---+---+          |                |
    |         +----+----+     +-----+-----+
    |         |         |     |Prometheus |
    +--> auth-service   |     |Grafana    |
    +--> ticket-service |     |Loki       |
    +--> payment-service|     +-----------+
    +--> queue-service  |
    +--> catalog-service|
    +--> stats-service  |
    +--> community-svc  |
         +----+---------+
              |
    +---------+---------+---------+
    |         |         |         |
+---+---+ +--+---+ +---+---+ +---+---+
|  RDS  | |Elasti| | MSK   | | SQS   |
|Multi  | |Cache | |Multi  | | FIFO  |
| AZ    | |Redis | | AZ    | |       |
+-------+ |Clust.| +-------+ +-------+
           +------+

    +-------------+  +-------------+  +-------------+
    |  Secrets    |  | CloudWatch  |  |   X-Ray     |
    |  Manager    |  |  + Alarms   |  | (Tracing)   |
    +-------------+  |  + SNS      |  +-------------+
                     +-------------+
```

**구성 요소별 AWS 매핑**:

| 현재 구성 | AWS 프로덕션 |
|-----------|-------------|
| Kind 클러스터 | Amazon EKS (관리형 K8s) |
| PostgreSQL Pod | Amazon RDS PostgreSQL (멀티 AZ) |
| Dragonfly Pod | Amazon ElastiCache Redis (클러스터 모드) |
| Kafka Pod | Amazon MSK (3 브로커, 3 AZ) |
| 수동 시크릿 | AWS Secrets Manager + External Secrets Operator |
| Docker 로컬 빌드 | Amazon ECR + GitHub Actions |
| 포트포워딩 | ALB + Route 53 + ACM |
| 로컬 모니터링 | CloudWatch + Amazon Managed Grafana + X-Ray |
| 없음 (CDN) | CloudFront + Lambda@Edge (VWR 토큰 검증) |
| 없음 (WAF) | AWS WAF (DDoS, SQL Injection 방어) |

---

## 5. 우선순위별 개선 로드맵

### P0: 프로덕션 필수 (배포 전 반드시 완료)

| 항목 | 현재 상태 | 목표 | 관련 파일 |
|------|-----------|------|-----------|
| **CI/CD 파이프라인** | 수동 스크립트 | GitHub Actions + ArgoCD | `scripts/spring-kind-build-load.sh`, `argocd/` |
| **DB 백업/복구** | 없음 | RDS 자동 백업 + PITR | `services-spring/ticket-service/src/main/resources/application.yml:22-24` |
| **Secret Rotation** | 정적 env 파일 | Secrets Manager + ESO | `k8s/AWS_SECRETS_INTEGRATION.md:1-349` |
| **알림 시스템** | 없음 | CloudWatch Alarms + SNS | `k8s/spring/overlays/kind/prometheus.yaml:6-47` |
| **Kafka 고가용성** | 단일 브로커 | MSK 3-브로커 | `k8s/spring/overlays/kind/kafka.yaml:8` |
| **Redis 고가용성** | 단일 인스턴스 | ElastiCache 클러스터 | `k8s/spring/overlays/kind/dragonfly.yaml:8` |
| **Grafana 보안** | admin/admin | Managed Grafana 또는 SSO 연동 | `k8s/spring/overlays/kind/grafana.yaml:68-69` |

### P1: 성능/안정성 (프로덕션 운영 초기)

| 항목 | 현재 상태 | 목표 | 관련 파일 |
|------|-----------|------|-----------|
| **실시간 통신** | 폴링 | WebSocket/SSE | `services-spring/queue-service/src/main/java/com/tiketi/queueservice/service/QueueService.java:231-238` |
| **로드 테스트** | 시나리오만 존재 | 성능 기준선 확립 | `tests/load/scenarios/` |
| **API 문서화** | 없음 | SpringDoc OpenAPI | `apps/web/src/lib/api-client.ts:133-270` |
| **WAF 도입** | 없음 | AWS WAF 규칙 적용 | - |

### P2: 사용자 경험 (안정화 후)

| 항목 | 현재 상태 | 목표 | 관련 파일 |
|------|-----------|------|-----------|
| **다크 모드** | 미구현 | TailwindCSS dark variant | `apps/web/package.json:35` |
| **E2E 테스트** | 프레임워크만 설정 | 핵심 플로우 커버리지 | `apps/web/package.json:24` |
| **접근성 개선** | 미검증 | WCAG 2.1 AA 준수 | `apps/web/src/middleware.ts` |
| **국제화(i18n)** | 한국어 단일 | 다국어 지원 | - |

---

## 부록: 소스 참조 인덱스

본 문서에서 참조한 주요 파일 경로 목록:

| 약칭 | 전체 경로 |
|------|-----------|
| `SeatLockService.java` | `services-spring/ticket-service/src/main/java/com/tiketi/ticketservice/domain/seat/service/SeatLockService.java` |
| `seat_lock_acquire.lua` | `services-spring/ticket-service/src/main/resources/redis/seat_lock_acquire.lua` |
| `payment_verify.lua` | `services-spring/ticket-service/src/main/resources/redis/payment_verify.lua` |
| `PaymentEventConsumer.java` | `services-spring/ticket-service/src/main/java/com/tiketi/ticketservice/messaging/PaymentEventConsumer.java` |
| `TicketEventProducer.java` | `services-spring/ticket-service/src/main/java/com/tiketi/ticketservice/messaging/TicketEventProducer.java` |
| `ReservationService.java` | `services-spring/ticket-service/src/main/java/com/tiketi/ticketservice/domain/reservation/service/ReservationService.java` |
| `BusinessMetrics.java` | `services-spring/ticket-service/src/main/java/com/tiketi/ticketservice/shared/metrics/BusinessMetrics.java` |
| `QueueService.java` | `services-spring/queue-service/src/main/java/com/tiketi/queueservice/service/QueueService.java` |
| `SqsPublisher.java` | `services-spring/queue-service/src/main/java/com/tiketi/queueservice/service/SqsPublisher.java` |
| `AdmissionWorkerService.java` | `services-spring/queue-service/src/main/java/com/tiketi/queueservice/service/AdmissionWorkerService.java` |
| `admission_control.lua` | `services-spring/queue-service/src/main/resources/redis/admission_control.lua` |
| `JwtAuthFilter.java` | `services-spring/gateway-service/src/main/java/com/tiketi/gatewayservice/filter/JwtAuthFilter.java` |
| `RateLimitFilter.java` | `services-spring/gateway-service/src/main/java/com/tiketi/gatewayservice/filter/RateLimitFilter.java` |
| `VwrEntryTokenFilter.java` | `services-spring/gateway-service/src/main/java/com/tiketi/gatewayservice/filter/VwrEntryTokenFilter.java` |
| `rate_limit.lua` | `services-spring/gateway-service/src/main/resources/redis/rate_limit.lua` |
| `StatsEventConsumer.java` | `services-spring/stats-service/src/main/java/com/tiketi/statsservice/messaging/StatsEventConsumer.java` |
| `network-policies.yaml` | `k8s/spring/base/network-policies.yaml` |
| `middleware.ts` | `apps/web/src/middleware.ts` |
| `api-client.ts` | `apps/web/src/lib/api-client.ts` |
| `use-server-time.ts` | `apps/web/src/hooks/use-server-time.ts` |
