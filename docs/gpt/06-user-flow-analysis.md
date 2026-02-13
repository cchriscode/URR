# 사용자 플로우 분석 (프론트 + 백엔드 연계)

## 0) 공통 API 진입점
- 프론트는 기본적으로 게이트웨이 `/api/v1`를 호출한다.  
  출처: `apps/web/src/lib/api-client.ts:39`
- 게이트웨이는 `/api/v1/**`를 내부 `/api/**`로 rewrite 후 각 서비스로 라우팅한다.  
  출처: `services-spring/gateway-service/src/main/java/com/tiketi/gatewayservice/filter/ApiVersionFilter.java:31`, `services-spring/gateway-service/src/main/resources/application.yml:15`

## 1) 회원가입/로그인 플로우

### 1.1 회원가입
1. 프론트 `/register`에서 `authApi.register` 호출  
   출처: `apps/web/src/app/register/page.tsx:19`
2. auth-service `/api/auth/register`가 사용자 생성 후 access/refresh 쿠키 발급  
   출처: `services-spring/auth-service/src/main/java/com/tiketi/authservice/controller/AuthController.java:39`, `services-spring/auth-service/src/main/java/com/tiketi/authservice/controller/AuthController.java:43`, `services-spring/auth-service/src/main/java/com/tiketi/authservice/controller/AuthController.java:44`
3. 프론트는 사용자 정보를 localStorage에 저장 후 홈으로 이동  
   출처: `apps/web/src/app/register/page.tsx:20`, `apps/web/src/app/register/page.tsx:21`

### 1.2 로그인
1. 프론트 `/login`에서 `authApi.login` 호출  
   출처: `apps/web/src/app/login/page.tsx:20`
2. auth-service `/api/auth/login`이 쿠키 재발급  
   출처: `services-spring/auth-service/src/main/java/com/tiketi/authservice/controller/AuthController.java:48`, `services-spring/auth-service/src/main/java/com/tiketi/authservice/controller/AuthController.java:52`, `services-spring/auth-service/src/main/java/com/tiketi/authservice/controller/AuthController.java:53`
3. 보호 페이지 접근 시 `AuthGuard`가 `/auth/me`로 서버 검증  
   출처: `apps/web/src/components/auth-guard.tsx:35`, `apps/web/src/components/auth-guard.tsx:36`

## 2) 이벤트 탐색 플로우
1. 홈 페이지에서 이벤트 목록 조회(`eventsApi.list`)  
   출처: `apps/web/src/app/page.tsx:126`
2. 이벤트 상세(`/events/{id}`)에서 예매 가능 여부(`canBook`) 계산  
   출처: `apps/web/src/app/events/[id]/page.tsx:132`
3. 예매 버튼은 대기열(`/queue/{eventId}`)로 이동  
   출처: `apps/web/src/app/events/[id]/page.tsx:229`
4. 백엔드에서는 catalog-service `/api/events`, `/api/events/{id}`가 이벤트 조회를 담당  
   출처: `services-spring/catalog-service/src/main/java/com/tiketi/catalogservice/domain/event/controller/EventController.java:13`, `services-spring/catalog-service/src/main/java/com/tiketi/catalogservice/domain/event/controller/EventController.java:22`, `services-spring/catalog-service/src/main/java/com/tiketi/catalogservice/domain/event/controller/EventController.java:32`

## 3) 대기열 플로우
1. `/queue/{eventId}` 마운트 시 `queueApi.check` 호출  
   출처: `apps/web/src/app/queue/[eventId]/page.tsx:50`, `apps/web/src/app/queue/[eventId]/page.tsx:52`
2. queue-service `/api/queue/check/{eventId}`에서 대기/즉시입장 판정  
   출처: `services-spring/queue-service/src/main/java/com/tiketi/queueservice/controller/QueueController.java:27`, `services-spring/queue-service/src/main/java/com/tiketi/queueservice/service/QueueService.java:81`
3. 대기열이면 status polling(`nextPoll`) 진행  
   출처: `apps/web/src/hooks/use-queue-polling.ts:39`, `apps/web/src/hooks/use-queue-polling.ts:44`
4. admission worker가 Redis Lua로 큐에서 active로 승격  
   출처: `services-spring/queue-service/src/main/java/com/tiketi/queueservice/service/AdmissionWorkerService.java:79`, `services-spring/queue-service/src/main/resources/redis/admission_control.lua:31`
5. 입장 승인 시 프론트는 좌석형(`/seats`) 또는 스탠딩형(`/book`)으로 리다이렉트  
   출처: `apps/web/src/app/queue/[eventId]/page.tsx:78`, `apps/web/src/app/queue/[eventId]/page.tsx:80`

## 4) 좌석형 예매/결제 플로우

### 4.1 좌석 선택 및 임시예약
1. 좌석 페이지에서 `seatsApi.byEvent`로 좌석 조회  
   출처: `apps/web/src/app/events/[id]/seats/page.tsx:92`, `apps/web/src/app/events/[id]/seats/page.tsx:93`
2. 사용자가 좌석 선택 후 `seatsApi.reserve` 호출  
   출처: `apps/web/src/app/events/[id]/seats/page.tsx:120`
3. ticket-service `/api/seats/reserve` -> `ReservationService.reserveSeats` 실행  
   출처: `services-spring/ticket-service/src/main/java/com/tiketi/ticketservice/domain/seat/controller/SeatController.java:43`, `services-spring/ticket-service/src/main/java/com/tiketi/ticketservice/domain/seat/controller/SeatController.java:49`
4. 내부적으로 Redis 락 + DB `FOR UPDATE` + fencing token 업데이트 수행  
   출처: `services-spring/ticket-service/src/main/java/com/tiketi/ticketservice/domain/reservation/service/ReservationService.java:61`, `services-spring/ticket-service/src/main/java/com/tiketi/ticketservice/domain/reservation/service/ReservationService.java:83`, `services-spring/ticket-service/src/main/java/com/tiketi/ticketservice/domain/reservation/service/ReservationService.java:111`
5. 성공 시 프론트는 `/payment/{reservationId}` 이동  
   출처: `apps/web/src/app/events/[id]/seats/page.tsx:124`, `apps/web/src/app/events/[id]/seats/page.tsx:126`

### 4.2 결제
1. 결제 페이지에서 예약정보 조회(`reservationsApi.byId`)  
   출처: `apps/web/src/app/payment/[reservationId]/page.tsx:41`
2. Toss 경로는 `paymentsApi.prepare` 후 외부 SDK 결제 요청  
   출처: `apps/web/src/app/payment/[reservationId]/page.tsx:63`, `apps/web/src/app/payment/[reservationId]/page.tsx:70`
3. 간편(mock) 경로는 `paymentsApi.process` 즉시 호출  
   출처: `apps/web/src/app/payment/[reservationId]/page.tsx:81`
4. payment-service는 결제완료 이벤트를 Kafka `payment-events`로 발행  
   출처: `services-spring/payment-service/src/main/java/com/tiketi/paymentservice/service/PaymentService.java:318`, `services-spring/payment-service/src/main/java/com/tiketi/paymentservice/messaging/PaymentEventProducer.java:14`
5. ticket-service가 `payment-events`를 수신해 예약 확정/좌석 reserved 처리  
   출처: `services-spring/ticket-service/src/main/java/com/tiketi/ticketservice/messaging/PaymentEventConsumer.java:43`, `services-spring/ticket-service/src/main/java/com/tiketi/ticketservice/domain/reservation/service/ReservationService.java:393`, `services-spring/ticket-service/src/main/java/com/tiketi/ticketservice/domain/reservation/service/ReservationService.java:413`

## 5) 스탠딩형 예매 플로우
1. `/events/{id}/book`에서 티켓 타입 선택  
   출처: `apps/web/src/app/events/[id]/book/page.tsx:44`
2. `reservationsApi.createTicketOnly`로 예약 생성  
   출처: `apps/web/src/app/events/[id]/book/page.tsx:55`, `apps/web/src/app/events/[id]/book/page.tsx:57`
3. ticket-service `/api/reservations`에서 아이템 재고 검증 후 예약 생성  
   출처: `services-spring/ticket-service/src/main/java/com/tiketi/ticketservice/domain/reservation/controller/ReservationController.java:30`, `services-spring/ticket-service/src/main/java/com/tiketi/ticketservice/domain/reservation/service/ReservationService.java:179`, `services-spring/ticket-service/src/main/java/com/tiketi/ticketservice/domain/reservation/service/ReservationService.java:210`

## 6) 예약 취소 플로우
1. 내 예약 페이지에서 취소 액션 실행  
   출처: `apps/web/src/app/my-reservations/page.tsx:138`
2. ticket-service `/api/reservations/{id}/cancel` 호출  
   출처: `services-spring/ticket-service/src/main/java/com/tiketi/ticketservice/domain/reservation/controller/ReservationController.java:54`
3. 상태를 `cancelled/refund_requested`로 변경하고 cancellation 이벤트 발행  
   출처: `services-spring/ticket-service/src/main/java/com/tiketi/ticketservice/domain/reservation/service/ReservationService.java:494`, `services-spring/ticket-service/src/main/java/com/tiketi/ticketservice/domain/reservation/service/ReservationService.java:499`
4. payment-service가 refund 이벤트를 처리 후 stats 반영  
   출처: `services-spring/payment-service/src/main/java/com/tiketi/paymentservice/service/PaymentService.java:219`, `services-spring/stats-service/src/main/java/com/tiketi/statsservice/messaging/StatsEventConsumer.java:40`

## 7) 양도 플로우
1. 내 예약에서 양도 등록(`transfersApi.create`)  
   출처: `apps/web/src/app/my-reservations/page.tsx:148`
2. ticket transfer listing 생성(멤버십/티어/수수료 검증 포함)  
   출처: `services-spring/ticket-service/src/main/java/com/tiketi/ticketservice/domain/transfer/service/TransferService.java:30`, `services-spring/ticket-service/src/main/java/com/tiketi/ticketservice/domain/transfer/service/TransferService.java:67`, `services-spring/ticket-service/src/main/java/com/tiketi/ticketservice/domain/transfer/service/TransferService.java:83`
3. 구매자가 `/transfer-payment/{id}`에서 `paymentType=transfer`로 결제  
   출처: `apps/web/src/app/transfer-payment/[transferId]/page.tsx:62`, `apps/web/src/app/transfer-payment/[transferId]/page.tsx:80`
4. 결제 이벤트 수신 후 ticket-service가 소유권 이전(예약 user 변경)  
   출처: `services-spring/ticket-service/src/main/java/com/tiketi/ticketservice/messaging/PaymentEventConsumer.java:55`, `services-spring/ticket-service/src/main/java/com/tiketi/ticketservice/domain/transfer/service/TransferService.java:253`, `services-spring/ticket-service/src/main/java/com/tiketi/ticketservice/domain/transfer/service/TransferService.java:258`

## 8) 멤버십 플로우
1. 아티스트 페이지에서 `membershipsApi.subscribe` 호출  
   출처: `apps/web/src/app/artists/[id]/page.tsx:81`
2. ticket-service가 멤버십을 `pending`으로 생성 또는 재사용  
   출처: `services-spring/ticket-service/src/main/java/com/tiketi/ticketservice/domain/membership/service/MembershipService.java:34`, `services-spring/ticket-service/src/main/java/com/tiketi/ticketservice/domain/membership/service/MembershipService.java:67`
3. 프론트는 `/membership-payment/{membershipId}`로 이동해 `paymentType=membership` 결제  
   출처: `apps/web/src/app/artists/[id]/page.tsx:90`, `apps/web/src/app/membership-payment/[membershipId]/page.tsx:78`, `apps/web/src/app/membership-payment/[membershipId]/page.tsx:97`
4. 결제 이벤트 수신 후 ticket-service가 멤버십 `active` 전환 및 포인트 부여  
   출처: `services-spring/ticket-service/src/main/java/com/tiketi/ticketservice/messaging/PaymentEventConsumer.java:56`, `services-spring/ticket-service/src/main/java/com/tiketi/ticketservice/domain/membership/service/MembershipService.java:93`, `services-spring/ticket-service/src/main/java/com/tiketi/ticketservice/domain/membership/service/MembershipService.java:100`

## 9) 관리자/통계 플로우
1. 프론트 관리자 통계 화면이 stats API를 주기적으로 호출  
   출처: `apps/web/src/app/admin/statistics/page.tsx:90`, `apps/web/src/app/admin/statistics/page.tsx:92`, `apps/web/src/app/admin/statistics/page.tsx:105`
2. stats-service는 admin JWT를 요구하고 여러 집계 endpoint를 제공  
   출처: `services-spring/stats-service/src/main/java/com/tiketi/statsservice/controller/StatsController.java:15`, `services-spring/stats-service/src/main/java/com/tiketi/statsservice/controller/StatsController.java:28`, `services-spring/stats-service/src/main/java/com/tiketi/statsservice/controller/StatsController.java:136`

## 10) 플로우 관점에서 본 강점/리스크

### 강점
- 사용자 예매 경로가 “대기열 -> 예매 -> 결제 -> 이벤트 후처리”로 명확히 분리되어 있고, 비동기 이벤트로 후처리 확장성이 높다.  
  출처: `apps/web/src/app/queue/[eventId]/page.tsx:76`, `services-spring/payment-service/src/main/java/com/tiketi/paymentservice/messaging/PaymentEventProducer.java:23`, `services-spring/ticket-service/src/main/java/com/tiketi/ticketservice/messaging/PaymentEventConsumer.java:43`

### 리스크
- Queue fallback이 인메모리인 경우 다중 인스턴스 환경에서 일관성이 깨질 수 있다.  
  출처: `services-spring/queue-service/src/main/java/com/tiketi/queueservice/service/QueueService.java:295`
- Lambda worker action과 ticket internal endpoint 사이에 계약 불일치가 존재한다(`reservation_create` 경로).  
  출처: `lambda/ticket-worker/index.js:72`, `lambda/ticket-worker/index.js:76`, `services-spring/ticket-service/src/main/java/com/tiketi/ticketservice/internal/controller/InternalReservationController.java:41`
