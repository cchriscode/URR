# 프로젝트 종합 분석 인덱스 (워킹트리 기준)

## 1) 분석 기준
- 기준 시점: 현재 로컬 워킹트리(커밋 여부와 무관)
- 분석 관점: 프론트엔드, 백엔드(구조/DB/MSA 통신/대기열/동시성), 보안, 인프라, 모니터링, 사용자 플로우
- 출처 표기: `파일경로:라인`

## 2) 문서 구성
- `docs/gpt/01-frontend-analysis.md`: 프론트엔드 아키텍처/구현/테스트/개선점
- `docs/gpt/02-backend-analysis.md`: 백엔드 서비스 구조/통신/동시성/대기열/정합성
- `docs/gpt/03-security-analysis.md`: 인증/인가/경계보안/취약 포인트
- `docs/gpt/04-infrastructure-analysis.md`: K8s 오버레이/Terraform/런타임 인프라
- `docs/gpt/05-monitoring-analysis.md`: 메트릭/로그/트레이싱/대시보드/알림
- `docs/gpt/06-user-flow-analysis.md`: 실제 사용자 여정 기준 E2E 흐름
- `docs/gpt/07-strengths-and-gaps.md`: 강점/미흡점 종합 및 우선순위 액션

## 3) 현재 시스템 개요
- 프론트는 Next.js 기반이며 API는 게이트웨이의 `/api/v1`를 기준으로 호출한다.  
  출처: `apps/web/src/lib/api-client.ts:38`, `apps/web/src/lib/api-client.ts:39`
- 게이트웨이는 `/api/v1/**`와 레거시 `/api/**`를 함께 라우팅하며, 필터에서 v1 prefix를 제거한다.  
  출처: `services-spring/gateway-service/src/main/resources/application.yml:15`, `services-spring/gateway-service/src/main/resources/application.yml:71`, `services-spring/gateway-service/src/main/java/com/tiketi/gatewayservice/filter/ApiVersionFilter.java:31`, `services-spring/gateway-service/src/main/java/com/tiketi/gatewayservice/filter/ApiVersionFilter.java:33`
- 백엔드는 `auth`, `gateway`, `ticket`, `payment`, `stats`, `queue`, `catalog`, `community`로 분리되어 있으며 K8s base에 모두 등록되어 있다.  
  출처: `k8s/spring/base/kustomization.yaml:5`, `k8s/spring/base/kustomization.yaml:20`
- 비동기 이벤트 버스는 Kafka(`payment-events`, `reservation-events`, `transfer-events`, `membership-events`)로 구성되어 있다.  
  출처: `services-spring/ticket-service/src/main/java/com/tiketi/ticketservice/shared/config/KafkaConfig.java:17`, `services-spring/ticket-service/src/main/java/com/tiketi/ticketservice/shared/config/KafkaConfig.java:22`, `services-spring/ticket-service/src/main/java/com/tiketi/ticketservice/shared/config/KafkaConfig.java:27`, `services-spring/ticket-service/src/main/java/com/tiketi/ticketservice/shared/config/KafkaConfig.java:32`
- 대기열은 queue-service + Redis ZSET/Lua + 선택적 SQS 퍼블리시로 동작한다.  
  출처: `services-spring/queue-service/src/main/java/com/tiketi/queueservice/service/QueueService.java:334`, `services-spring/queue-service/src/main/resources/redis/admission_control.lua:31`, `services-spring/queue-service/src/main/java/com/tiketi/queueservice/service/SqsPublisher.java:38`

## 4) 주요 기술 스택 요약
- 프론트: Next.js 16, React 19, Axios, Toss SDK, Vitest, Playwright  
  출처: `apps/web/package.json:17`, `apps/web/package.json:18`, `apps/web/package.json:23`, `apps/web/package.json:36`
- 백엔드: Spring Boot 3, JDBC/JPA, Redis, Kafka, Resilience4j, Flyway, JWT  
  출처: `services-spring/ticket-service/build.gradle:23`, `services-spring/ticket-service/build.gradle:24`, `services-spring/ticket-service/build.gradle:35`, `services-spring/ticket-service/build.gradle:29`, `services-spring/ticket-service/build.gradle:31`
- 인프라: Kubernetes(kustomize), Terraform(AWS), Lambda@Edge, SQS FIFO  
  출처: `k8s/spring/overlays/prod/kustomization.yaml:1`, `terraform/environments/prod/main.tf:50`, `terraform/environments/prod/main.tf:64`, `terraform/environments/prod/main.tf:87`
- 관측성: Prometheus + Grafana + Loki/Promtail + Zipkin  
  출처: `k8s/spring/overlays/kind/kustomization.yaml:13`, `k8s/spring/overlays/kind/kustomization.yaml:17`, `k8s/spring/overlays/kind/zipkin.yaml:17`

## 5) 즉시 확인된 핵심 이슈(요약)
- Catalog가 ticket DB를 직접 공유하고 Flyway를 비활성화한 구조라 서비스 경계가 느슨하다.  
  출처: `services-spring/catalog-service/src/main/resources/application.yml:5`, `services-spring/catalog-service/src/main/resources/application.yml:9`
- Ticket 서비스의 만료 예약 정리 스케줄러가 2개 경로로 중복 존재한다.  
  출처: `services-spring/ticket-service/src/main/java/com/tiketi/ticketservice/scheduling/ReservationCleanupScheduler.java:31`, `services-spring/ticket-service/src/main/java/com/tiketi/ticketservice/scheduling/MaintenanceService.java:29`
- Stats 조회 서비스 일부 값이 합성/하드코딩이다(정확도 리스크).  
  출처: `services-spring/stats-service/src/main/java/com/tiketi/statsservice/service/StatsQueryService.java:66`, `services-spring/stats-service/src/main/java/com/tiketi/statsservice/service/StatsQueryService.java:228`, `services-spring/stats-service/src/main/java/com/tiketi/statsservice/service/StatsQueryService.java:549`
- CloudFront 커스텀 헤더명과 Gateway 우회 검증 헤더명이 불일치한다.  
  출처: `terraform/modules/cloudfront/main.tf:79`, `services-spring/gateway-service/src/main/java/com/tiketi/gatewayservice/filter/VwrEntryTokenFilter.java:32`

---
아래 문서들에서 영역별로 상세 분석과 근거를 제공합니다.
