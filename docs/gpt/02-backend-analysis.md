# 백엔드 상세 분석 (구조/DB/MSA/대기열/동시성)

## 1) 서비스 구성과 책임
- 서비스는 `gateway`, `auth`, `ticket`, `payment`, `stats`, `queue`, `catalog`, `community`로 분리되어 있다.  
  출처: `k8s/spring/base/kustomization.yaml:5`, `k8s/spring/base/kustomization.yaml:20`
- 게이트웨이가 외부 API 라우팅을 중앙집중 처리한다.  
  출처: `services-spring/gateway-service/src/main/resources/application.yml:12`, `services-spring/gateway-service/src/main/resources/application.yml:69`
- Ticket 서비스는 도메인 패키지를 `reservation/seat/transfer/membership`로 분리한 구조다.  
  출처: `services-spring/ticket-service/src/main/java/com/tiketi/ticketservice/domain/reservation/controller/ReservationController.java:1`, `services-spring/ticket-service/src/main/java/com/tiketi/ticketservice/domain/seat/controller/SeatController.java:1`, `services-spring/ticket-service/src/main/java/com/tiketi/ticketservice/domain/transfer/controller/TransferController.java:1`, `services-spring/ticket-service/src/main/java/com/tiketi/ticketservice/domain/membership/controller/MembershipController.java:1`

## 2) 기술 스택
- Ticket: JPA + Redis + Web + Flyway + Kafka + JWT + AOP  
  출처: `services-spring/ticket-service/build.gradle:23`, `services-spring/ticket-service/build.gradle:24`, `services-spring/ticket-service/build.gradle:26`, `services-spring/ticket-service/build.gradle:29`, `services-spring/ticket-service/build.gradle:35`
- Queue: Redis + Web + JWT + AOP  
  출처: `services-spring/queue-service/build.gradle:23`, `services-spring/queue-service/build.gradle:24`, `services-spring/queue-service/build.gradle:27`
- Payment: JPA + Flyway + Kafka + JWT  
  출처: `services-spring/payment-service/build.gradle:23`, `services-spring/payment-service/build.gradle:28`, `services-spring/payment-service/build.gradle:34`, `services-spring/payment-service/build.gradle:30`
- Stats: JPA + Flyway + Kafka + JWT  
  출처: `services-spring/stats-service/build.gradle:23`, `services-spring/stats-service/build.gradle:26`, `services-spring/stats-service/build.gradle:32`, `services-spring/stats-service/build.gradle:28`
- Auth: Spring Security + JPA + Flyway + JWT  
  출처: `services-spring/auth-service/build.gradle:24`, `services-spring/auth-service/build.gradle:23`, `services-spring/auth-service/build.gradle:27`, `services-spring/auth-service/build.gradle:30`

## 3) DB 토폴로지 및 데이터 경계
- Auth DB 분리: `auth_db`  
  출처: `services-spring/auth-service/src/main/resources/application.yml:5`
- Ticket DB 분리: `ticket_db`  
  출처: `services-spring/ticket-service/src/main/resources/application.yml:22`
- Payment DB 분리: `payment_db`  
  출처: `services-spring/payment-service/src/main/resources/application.yml:14`
- Stats DB 분리: `stats_db`  
  출처: `services-spring/stats-service/src/main/resources/application.yml:16`
- Community DB 분리: `community_db`  
  출처: `services-spring/community-service/src/main/resources/application.yml:5`
- Catalog는 현재 `ticket_db`를 공유하며 flyway를 끈 상태다.  
  출처: `services-spring/catalog-service/src/main/resources/application.yml:5`, `services-spring/catalog-service/src/main/resources/application.yml:9`
- 로컬 데이터베이스 compose도 위 분리를 반영한다.  
  출처: `services-spring/docker-compose.databases.yml:2`, `services-spring/docker-compose.databases.yml:11`, `services-spring/docker-compose.databases.yml:20`, `services-spring/docker-compose.databases.yml:29`, `services-spring/docker-compose.databases.yml:38`

## 4) API 계층과 MSA 통신 방식

### 4.1 외부 트래픽: Gateway 라우팅
- 게이트웨이는 `auth/payments/stats/events/seats/reservations/queue/admin/news/artists/memberships/transfers`를 각 서비스로 라우팅한다.  
  출처: `services-spring/gateway-service/src/main/resources/application.yml:12`, `services-spring/gateway-service/src/main/resources/application.yml:63`
- `/api/v1`와 레거시 `/api`를 동시에 지원한다.  
  출처: `services-spring/gateway-service/src/main/resources/application.yml:15`, `services-spring/gateway-service/src/main/resources/application.yml:71`
- `ApiVersionFilter`가 `/api/v1/...`를 `/api/...`로 rewrite 한다.  
  출처: `services-spring/gateway-service/src/main/java/com/tiketi/gatewayservice/filter/ApiVersionFilter.java:22`, `services-spring/gateway-service/src/main/java/com/tiketi/gatewayservice/filter/ApiVersionFilter.java:31`, `services-spring/gateway-service/src/main/java/com/tiketi/gatewayservice/filter/ApiVersionFilter.java:33`

### 4.2 동기 내부 통신: Internal API + 토큰
- Queue -> Catalog: 이벤트 큐 정보 조회(`/internal/events/{eventId}/queue-info`)  
  출처: `services-spring/queue-service/src/main/java/com/tiketi/queueservice/shared/client/TicketInternalClient.java:36`, `services-spring/catalog-service/src/main/java/com/tiketi/catalogservice/internal/controller/InternalEventController.java:22`
- Payment -> Ticket: 예약/양도/멤버십 검증 internal 호출  
  출처: `services-spring/payment-service/src/main/java/com/tiketi/paymentservice/client/TicketInternalClient.java:35`, `services-spring/payment-service/src/main/java/com/tiketi/paymentservice/client/TicketInternalClient.java:45`, `services-spring/payment-service/src/main/java/com/tiketi/paymentservice/client/TicketInternalClient.java:55`
- Ticket internal API는 validate/confirm/refund/complete/activate 패턴으로 공개된다.  
  출처: `services-spring/ticket-service/src/main/java/com/tiketi/ticketservice/internal/controller/InternalReservationController.java:41`, `services-spring/ticket-service/src/main/java/com/tiketi/ticketservice/internal/controller/InternalReservationController.java:51`, `services-spring/ticket-service/src/main/java/com/tiketi/ticketservice/internal/controller/InternalReservationController.java:63`, `services-spring/ticket-service/src/main/java/com/tiketi/ticketservice/internal/controller/InternalReservationController.java:85`, `services-spring/ticket-service/src/main/java/com/tiketi/ticketservice/internal/controller/InternalReservationController.java:110`
- Internal 호출은 대부분 `Authorization: Bearer INTERNAL_API_TOKEN` 검증을 사용한다.  
  출처: `services-spring/ticket-service/src/main/java/com/tiketi/ticketservice/shared/security/InternalTokenValidator.java:20`, `services-spring/payment-service/src/main/java/com/tiketi/paymentservice/security/InternalTokenValidator.java:20`, `services-spring/catalog-service/src/main/resources/application.yml:42`, `services-spring/queue-service/src/main/resources/application.yml:50`
- 내부 HTTP 클라이언트는 Resilience4j `CircuitBreaker + Retry`를 붙인 형태다.  
  출처: `services-spring/payment-service/src/main/java/com/tiketi/paymentservice/client/TicketInternalClient.java:31`, `services-spring/queue-service/src/main/java/com/tiketi/queueservice/shared/client/TicketInternalClient.java:31`, `services-spring/catalog-service/src/main/java/com/tiketi/catalogservice/shared/client/AuthInternalClient.java:34`

### 4.3 비동기 통신: Kafka 이벤트
- Payment 서비스가 `payment-events`를 발행한다.  
  출처: `services-spring/payment-service/src/main/java/com/tiketi/paymentservice/messaging/PaymentEventProducer.java:14`, `services-spring/payment-service/src/main/java/com/tiketi/paymentservice/messaging/PaymentEventProducer.java:23`
- Ticket 서비스는 `payment-events`를 consume하여 예약 확정/양도 완료/멤버십 활성화를 후처리한다.  
  출처: `services-spring/ticket-service/src/main/java/com/tiketi/ticketservice/messaging/PaymentEventConsumer.java:43`, `services-spring/ticket-service/src/main/java/com/tiketi/ticketservice/messaging/PaymentEventConsumer.java:55`, `services-spring/ticket-service/src/main/java/com/tiketi/ticketservice/messaging/PaymentEventConsumer.java:56`
- Ticket 서비스는 결과 이벤트를 `reservation-events`, `transfer-events`, `membership-events`로 발행한다.  
  출처: `services-spring/ticket-service/src/main/java/com/tiketi/ticketservice/messaging/TicketEventProducer.java:25`, `services-spring/ticket-service/src/main/java/com/tiketi/ticketservice/messaging/TicketEventProducer.java:58`, `services-spring/ticket-service/src/main/java/com/tiketi/ticketservice/messaging/TicketEventProducer.java:69`
- Stats 서비스는 위 이벤트를 수신해 집계 테이블을 갱신한다.  
  출처: `services-spring/stats-service/src/main/java/com/tiketi/statsservice/messaging/StatsEventConsumer.java:70`, `services-spring/stats-service/src/main/java/com/tiketi/statsservice/messaging/StatsEventConsumer.java:116`

## 5) 대기열 구현 방식
- Queue API는 `check/status/heartbeat/leave`로 구성된다.  
  출처: `services-spring/queue-service/src/main/java/com/tiketi/queueservice/controller/QueueController.java:27`, `services-spring/queue-service/src/main/java/com/tiketi/queueservice/controller/QueueController.java:36`, `services-spring/queue-service/src/main/java/com/tiketi/queueservice/controller/QueueController.java:45`, `services-spring/queue-service/src/main/java/com/tiketi/queueservice/controller/QueueController.java:54`
- 큐/활성유저는 Redis ZSET, heartbeat는 별도 ZSET으로 관리한다.  
  출처: `services-spring/queue-service/src/main/java/com/tiketi/queueservice/service/QueueService.java:271`, `services-spring/queue-service/src/main/java/com/tiketi/queueservice/service/QueueService.java:291`, `services-spring/queue-service/src/main/java/com/tiketi/queueservice/service/QueueService.java:388`
- admission은 Lua에서 `ZPOPMIN`으로 원자적으로 승격한다.  
  출처: `services-spring/queue-service/src/main/resources/redis/admission_control.lua:31`, `services-spring/queue-service/src/main/resources/redis/admission_control.lua:41`
- stale 유저는 별도 Lua로 seen/queue 동시 정리한다.  
  출처: `services-spring/queue-service/src/main/resources/redis/stale_cleanup.lua:11`, `services-spring/queue-service/src/main/resources/redis/stale_cleanup.lua:18`, `services-spring/queue-service/src/main/resources/redis/stale_cleanup.lua:19`
- admission worker는 이벤트별 분산락(`admission:lock:{eventId}`)을 사용해 중복 승격을 방지한다.  
  출처: `services-spring/queue-service/src/main/java/com/tiketi/queueservice/service/AdmissionWorkerService.java:65`, `services-spring/queue-service/src/main/java/com/tiketi/queueservice/service/AdmissionWorkerService.java:69`
- 입장자에게는 eventId를 subject로 갖는 entry token JWT를 발급한다.  
  출처: `services-spring/queue-service/src/main/java/com/tiketi/queueservice/service/QueueService.java:215`, `services-spring/queue-service/src/main/java/com/tiketi/queueservice/service/QueueService.java:221`, `services-spring/queue-service/src/main/java/com/tiketi/queueservice/service/QueueService.java:222`
- Redis 장애 시 인메모리 fallback이 있으나 멀티 인스턴스 부적합 로그를 명시한다.  
  출처: `services-spring/queue-service/src/main/java/com/tiketi/queueservice/service/QueueService.java:295`, `services-spring/queue-service/src/main/java/com/tiketi/queueservice/service/QueueService.java:307`
- admission 이벤트를 SQS FIFO로도 발행 가능하다.  
  출처: `services-spring/queue-service/src/main/java/com/tiketi/queueservice/service/SqsPublisher.java:45`, `services-spring/queue-service/src/main/java/com/tiketi/queueservice/service/SqsPublisher.java:58`

## 6) 동시성 처리 방식 (좌석/예약)

### 6.1 좌석 예약 시 3단계 락
- 1단계: Redis Lua seat lock 획득  
  출처: `services-spring/ticket-service/src/main/java/com/tiketi/ticketservice/domain/reservation/service/ReservationService.java:61`, `services-spring/ticket-service/src/main/java/com/tiketi/ticketservice/domain/reservation/service/ReservationService.java:64`
- 2단계: DB `FOR UPDATE`로 좌석 행 잠금  
  출처: `services-spring/ticket-service/src/main/java/com/tiketi/ticketservice/domain/reservation/service/ReservationService.java:78`, `services-spring/ticket-service/src/main/java/com/tiketi/ticketservice/domain/reservation/service/ReservationService.java:83`
- 3단계: version 증가 + fencing_token 반영 업데이트  
  출처: `services-spring/ticket-service/src/main/java/com/tiketi/ticketservice/domain/reservation/service/ReservationService.java:103`, `services-spring/ticket-service/src/main/java/com/tiketi/ticketservice/domain/reservation/service/ReservationService.java:111`, `services-spring/ticket-service/src/main/java/com/tiketi/ticketservice/domain/reservation/service/ReservationService.java:112`
- 락 실패/충돌 시 이미 획득한 락을 롤백 해제한다.  
  출처: `services-spring/ticket-service/src/main/java/com/tiketi/ticketservice/domain/reservation/service/ReservationService.java:68`, `services-spring/ticket-service/src/main/java/com/tiketi/ticketservice/domain/reservation/service/ReservationService.java:69`

### 6.2 fencing token 보장
- Redis Lua에서 seat별 token 시퀀스를 `INCR`로 단조 증가시킨다.  
  출처: `services-spring/ticket-service/src/main/resources/redis/seat_lock_acquire.lua:24`, `services-spring/ticket-service/src/main/resources/redis/seat_lock_acquire.lua:25`
- 결제 확정 직전 `userId + token` 조합을 Lua로 재검증한다.  
  출처: `services-spring/ticket-service/src/main/resources/redis/payment_verify.lua:12`, `services-spring/ticket-service/src/main/resources/redis/payment_verify.lua:13`
- Ticket 서비스 결제확정 경로에서 seat별 verifyForPayment를 수행한다.  
  출처: `services-spring/ticket-service/src/main/java/com/tiketi/ticketservice/domain/reservation/service/ReservationService.java:375`, `services-spring/ticket-service/src/main/java/com/tiketi/ticketservice/domain/reservation/service/ReservationService.java:384`
- 스키마에도 version/fencing_token/locked_by 컬럼이 추가되어 있다.  
  출처: `services-spring/ticket-service/src/main/resources/db/migration/V8__seats_concurrency_columns.sql:2`, `services-spring/ticket-service/src/main/resources/db/migration/V8__seats_concurrency_columns.sql:5`, `services-spring/ticket-service/src/main/resources/db/migration/V8__seats_concurrency_columns.sql:8`

## 7) 정합성/운영성 처리
- 결제 누락 보정용 reconciliation 스케줄러가 존재한다(5분 이상 pending 조회 후 payment-service 확인).  
  출처: `services-spring/ticket-service/src/main/java/com/tiketi/ticketservice/scheduling/PaymentReconciliationScheduler.java:36`, `services-spring/ticket-service/src/main/java/com/tiketi/ticketservice/scheduling/PaymentReconciliationScheduler.java:42`, `services-spring/ticket-service/src/main/java/com/tiketi/ticketservice/scheduling/PaymentReconciliationScheduler.java:59`
- 예약 만료 정리는 `ReservationCleanupScheduler`와 `MaintenanceService` 두 경로가 유사 기능으로 공존한다.  
  출처: `services-spring/ticket-service/src/main/java/com/tiketi/ticketservice/scheduling/ReservationCleanupScheduler.java:31`, `services-spring/ticket-service/src/main/java/com/tiketi/ticketservice/scheduling/MaintenanceService.java:29`
- Stats는 Kafka 중복 수신 대비 `processed_events` dedup 테이블을 운영한다.  
  출처: `services-spring/stats-service/src/main/java/com/tiketi/statsservice/messaging/StatsEventConsumer.java:152`, `services-spring/stats-service/src/main/java/com/tiketi/statsservice/messaging/StatsEventConsumer.java:166`, `services-spring/stats-service/src/main/resources/db/migration/V2__processed_events_table.sql:2`

## 8) 비즈니스 도메인 구현 요약
- Reservation 도메인: 생성/조회/취소/결제확정/환불마킹 처리  
  출처: `services-spring/ticket-service/src/main/java/com/tiketi/ticketservice/domain/reservation/controller/ReservationController.java:30`, `services-spring/ticket-service/src/main/java/com/tiketi/ticketservice/domain/reservation/controller/ReservationController.java:54`, `services-spring/ticket-service/src/main/java/com/tiketi/ticketservice/domain/reservation/service/ReservationService.java:363`, `services-spring/ticket-service/src/main/java/com/tiketi/ticketservice/domain/reservation/service/ReservationService.java:434`
- Transfer 도메인: listing 생성/검증/완료(소유권 이전)  
  출처: `services-spring/ticket-service/src/main/java/com/tiketi/ticketservice/domain/transfer/service/TransferService.java:30`, `services-spring/ticket-service/src/main/java/com/tiketi/ticketservice/domain/transfer/service/TransferService.java:200`, `services-spring/ticket-service/src/main/java/com/tiketi/ticketservice/domain/transfer/service/TransferService.java:238`
- Membership 도메인: 가입 pending -> 결제 후 activate, 포인트/티어 계산  
  출처: `services-spring/ticket-service/src/main/java/com/tiketi/ticketservice/domain/membership/service/MembershipService.java:34`, `services-spring/ticket-service/src/main/java/com/tiketi/ticketservice/domain/membership/service/MembershipService.java:79`, `services-spring/ticket-service/src/main/java/com/tiketi/ticketservice/domain/membership/service/MembershipService.java:203`, `services-spring/ticket-service/src/main/java/com/tiketi/ticketservice/domain/membership/service/MembershipService.java:246`

## 9) 확인된 미흡점
- Catalog가 ticket DB를 공유하여 완전한 독립 서비스 경계가 아니다.  
  출처: `services-spring/catalog-service/src/main/resources/application.yml:5`
- Ticket 만료정리 스케줄러 중복은 운영 중 충돌/중복 처리 리스크가 있다.  
  출처: `services-spring/ticket-service/src/main/java/com/tiketi/ticketservice/scheduling/ReservationCleanupScheduler.java:31`, `services-spring/ticket-service/src/main/java/com/tiketi/ticketservice/scheduling/MaintenanceService.java:29`
- Queue의 Redis 장애 fallback은 단일 인스턴스 기준이어서 프로덕션 다중 인스턴스 환경에 적합하지 않다.  
  출처: `services-spring/queue-service/src/main/java/com/tiketi/queueservice/service/QueueService.java:295`
- Stats 조회 일부는 실제 운영지표가 아닌 합성/0값을 반환한다.  
  출처: `services-spring/stats-service/src/main/java/com/tiketi/statsservice/service/StatsQueryService.java:66`, `services-spring/stats-service/src/main/java/com/tiketi/statsservice/service/StatsQueryService.java:228`, `services-spring/stats-service/src/main/java/com/tiketi/statsservice/service/StatsQueryService.java:549`, `services-spring/stats-service/src/main/java/com/tiketi/statsservice/service/StatsQueryService.java:550`
- Lambda worker의 `reservation_create` 액션은 현재 Ticket internal controller에 대응 엔드포인트가 없다.  
  출처: `lambda/ticket-worker/index.js:72`, `lambda/ticket-worker/index.js:76`, `services-spring/ticket-service/src/main/java/com/tiketi/ticketservice/internal/controller/InternalReservationController.java:41`, `services-spring/ticket-service/src/main/java/com/tiketi/ticketservice/internal/controller/InternalReservationController.java:51`

## 10) 총평
- 동시성/이벤트 기반 처리(좌석 락 + Kafka + 재조정 스케줄러)는 설계 의도가 명확하고 실제 운영 이슈를 고려한 구조다.
- 다만 서비스 경계(DB 공유), 일부 운영성 코드(중복 스케줄러/합성 지표), 대기열 fallback 전략은 단기 정리가 필요하다.
