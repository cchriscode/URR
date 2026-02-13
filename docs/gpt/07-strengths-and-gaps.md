# 종합 평가: 좋은 부분과 미흡한 부분

## 1) 한 줄 결론
- 현재 코드베이스는 “대기열 + 좌석 동시성 + 결제 이벤트 연계”의 핵심 구조가 잘 잡혀 있고, 운영에 필요한 관측성/인프라 뼈대도 구축되어 있다.
- 반면 “서비스 경계(DB 공유), 운영 정합성(중복 스케줄러/합성 통계), 보안 정합성(CloudFront-Gateway 헤더 계약)”은 기술부채로 남아 있다.

## 2) 좋은 부분 (Strengths)

### S1. 좌석 동시성 처리 설계가 실전형
- Redis 락 -> DB `FOR UPDATE` -> fencing token 검증의 3중 보호 구조를 사용한다.  
  출처: `services-spring/ticket-service/src/main/java/com/tiketi/ticketservice/domain/reservation/service/ReservationService.java:61`, `services-spring/ticket-service/src/main/java/com/tiketi/ticketservice/domain/reservation/service/ReservationService.java:83`, `services-spring/ticket-service/src/main/java/com/tiketi/ticketservice/domain/reservation/service/ReservationService.java:384`
- 스키마 레벨에서도 version/fencing_token/locked_by 컬럼으로 보강했다.  
  출처: `services-spring/ticket-service/src/main/resources/db/migration/V8__seats_concurrency_columns.sql:2`, `services-spring/ticket-service/src/main/resources/db/migration/V8__seats_concurrency_columns.sql:5`, `services-spring/ticket-service/src/main/resources/db/migration/V8__seats_concurrency_columns.sql:8`

### S2. 대기열 아키텍처가 유연함
- Redis Lua admission + stale cleanup + entry token 발급이 분리되어 있다.  
  출처: `services-spring/queue-service/src/main/resources/redis/admission_control.lua:31`, `services-spring/queue-service/src/main/resources/redis/stale_cleanup.lua:11`, `services-spring/queue-service/src/main/java/com/tiketi/queueservice/service/QueueService.java:215`
- 프론트는 `nextPoll` 기반 적응형 폴링으로 불필요 트래픽을 줄인다.  
  출처: `apps/web/src/hooks/use-queue-polling.ts:44`, `apps/web/src/hooks/use-queue-polling.ts:60`

### S3. 이벤트 기반 후처리 흐름이 명확함
- Payment -> Ticket -> Stats로 이어지는 이벤트 체인이 분리되어 확장성이 좋다.  
  출처: `services-spring/payment-service/src/main/java/com/tiketi/paymentservice/messaging/PaymentEventProducer.java:23`, `services-spring/ticket-service/src/main/java/com/tiketi/ticketservice/messaging/PaymentEventConsumer.java:43`, `services-spring/stats-service/src/main/java/com/tiketi/statsservice/messaging/StatsEventConsumer.java:70`

### S4. 운영 기본기(관측성/배포)가 갖춰짐
- Prometheus/Grafana/Loki/Zipkin이 kind 오버레이에 통합돼 로컬 운영검증이 빠르다.  
  출처: `k8s/spring/overlays/kind/kustomization.yaml:13`, `k8s/spring/overlays/kind/kustomization.yaml:17`
- prod 오버레이에 HPA/PDB/Kafka3노드/Redis6노드 기반이 준비되어 있다.  
  출처: `k8s/spring/overlays/prod/hpa.yaml:10`, `k8s/spring/overlays/prod/pdb.yaml:6`, `k8s/spring/overlays/prod/kafka.yaml:40`, `k8s/spring/overlays/prod/redis.yaml:70`

### S5. API 버저닝 전환이 진행됨
- 프론트는 `/api/v1`를 기본 사용하고, 게이트웨이는 v1/legacy를 동시 수용한다.  
  출처: `apps/web/src/lib/api-client.ts:39`, `services-spring/gateway-service/src/main/resources/application.yml:15`, `services-spring/gateway-service/src/main/resources/application.yml:71`

## 3) 미흡한 부분 (Gaps)

### G1. 서비스 경계가 완전하지 않음
- catalog-service가 ticket DB를 공유하고 flyway를 비활성화했다. 도메인 독립성/릴리즈 독립성이 낮아진다.  
  출처: `services-spring/catalog-service/src/main/resources/application.yml:5`, `services-spring/catalog-service/src/main/resources/application.yml:9`

### G2. 운영 정합성 코드 일부가 중복/임시 상태
- 만료 예약 정리 스케줄러가 2개 경로로 중복되어 운영 충돌 가능성이 있다.  
  출처: `services-spring/ticket-service/src/main/java/com/tiketi/ticketservice/scheduling/ReservationCleanupScheduler.java:31`, `services-spring/ticket-service/src/main/java/com/tiketi/ticketservice/scheduling/MaintenanceService.java:29`
- stats 조회 일부가 합성 문자열/0값/`n/a`를 반환한다.  
  출처: `services-spring/stats-service/src/main/java/com/tiketi/statsservice/service/StatsQueryService.java:66`, `services-spring/stats-service/src/main/java/com/tiketi/statsservice/service/StatsQueryService.java:228`, `services-spring/stats-service/src/main/java/com/tiketi/statsservice/service/StatsQueryService.java:534`, `services-spring/stats-service/src/main/java/com/tiketi/statsservice/service/StatsQueryService.java:549`

### G3. 보안 정합성 이슈
- CloudFront custom header와 Gateway 우회검증 헤더명이 불일치한다.  
  출처: `terraform/modules/cloudfront/main.tf:79`, `services-spring/gateway-service/src/main/java/com/tiketi/gatewayservice/filter/VwrEntryTokenFilter.java:32`
- CSP에 `unsafe-inline`이 포함되어 XSS 방어 여지가 남아 있다.  
  출처: `apps/web/src/middleware.ts:12`
- 개발용 시크릿/기본계정이 코드에 가시적으로 남아 있다(kind).  
  출처: `k8s/spring/overlays/kind/secrets.env:12`, `k8s/spring/overlays/kind/grafana.yaml:67`, `k8s/spring/overlays/kind/grafana.yaml:70`

### G4. 계약 불일치/운영 리스크
- Lambda worker의 `reservation_create` 액션이 현재 ticket internal controller 엔드포인트와 맞지 않는다.  
  출처: `lambda/ticket-worker/index.js:72`, `lambda/ticket-worker/index.js:76`, `services-spring/ticket-service/src/main/java/com/tiketi/ticketservice/internal/controller/InternalReservationController.java:41`
- Queue의 Redis fallback은 다중 인스턴스 생산환경에 부적합하다고 코드 자체가 명시한다.  
  출처: `services-spring/queue-service/src/main/java/com/tiketi/queueservice/service/QueueService.java:295`
- prod config env의 Kafka bootstrap 중복 선언은 운영 혼동을 유발한다.  
  출처: `k8s/spring/overlays/prod/config.env:15`, `k8s/spring/overlays/prod/config.env:22`

### G5. 테스트 깊이 부족
- 프론트 E2E는 smoke 중심이며 핵심 실패 시나리오(결제 실패/경합/우회)에 대한 자동 회귀 방어가 약하다.  
  출처: `apps/web/e2e/smoke.spec.ts:3`

## 4) 우선순위 실행안

### P0 (즉시, 1~2주)
1. CloudFront-Gateway 헤더 계약 통일(`X-CloudFront-Verified` 또는 단일 명칭으로 정식화) 및 통합테스트 추가.  
   출처: `terraform/modules/cloudfront/main.tf:79`, `services-spring/gateway-service/src/main/java/com/tiketi/gatewayservice/filter/VwrEntryTokenFilter.java:32`
2. Ticket 만료 정리 스케줄러 단일화(소유 책임 1곳으로 통합).  
   출처: `services-spring/ticket-service/src/main/java/com/tiketi/ticketservice/scheduling/ReservationCleanupScheduler.java:31`, `services-spring/ticket-service/src/main/java/com/tiketi/ticketservice/scheduling/MaintenanceService.java:29`
3. Lambda worker action-API 계약 정리(미사용 액션 제거 또는 endpoint 구현).  
   출처: `lambda/ticket-worker/index.js:72`, `services-spring/ticket-service/src/main/java/com/tiketi/ticketservice/internal/controller/InternalReservationController.java:41`

### P1 (단기, 1~2개월)
1. Catalog DB 분리(읽기 모델/API 분리 포함) 및 독립 migration 체계 복구.  
   출처: `services-spring/catalog-service/src/main/resources/application.yml:5`, `services-spring/catalog-service/src/main/resources/application.yml:9`
2. StatsQueryService 합성값 제거, 실제 집계로 교체.  
   출처: `services-spring/stats-service/src/main/java/com/tiketi/statsservice/service/StatsQueryService.java:66`, `services-spring/stats-service/src/main/java/com/tiketi/statsservice/service/StatsQueryService.java:549`
3. 프론트 CSP 강화(`unsafe-inline` 단계적 제거) + 고위험 경로 AuthGuard strict 모드 분리.  
   출처: `apps/web/src/middleware.ts:12`, `apps/web/src/components/auth-guard.tsx:45`
4. Grafana 알림 채널(Slack/PagerDuty) IaC 명시 및 온콜 라우팅 구성.  
   출처: `k8s/spring/overlays/kind/grafana.yaml:29`

### P2 (중기)
1. Queue fallback 정책 개선(멀티 인스턴스 안전 모드 혹은 fail-fast 전략).  
   출처: `services-spring/queue-service/src/main/java/com/tiketi/queueservice/service/QueueService.java:295`
2. 시크릿/운영계정 관리 표준화(예제 분리, 회전 자동화).  
   출처: `k8s/spring/overlays/kind/secrets.env:1`, `k8s/spring/overlays/kind/grafana.yaml:67`
3. E2E 확장: 대기열/결제/동시좌석 경쟁/환불 경로를 CI 필수 시나리오로 승격.  
   출처: `apps/web/e2e/smoke.spec.ts:3`

## 5) 기술사 소개용 메시지(요약)
- 이 프로젝트는 “대규모 티켓팅의 핵심 난제(대기열/동시성/결제 정합성)”에 대한 코드 레벨 해법을 이미 갖추고 있다.
- 다음 단계는 “경계 분리 완성도와 운영 신뢰성”을 끌어올리는 작업이며, 위 P0/P1 항목을 처리하면 엔터프라이즈 품질에 크게 근접한다.
