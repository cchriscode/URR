# 종합 평가 --- 장점 및 미흡점 분석

URR 티켓팅 플랫폼 전체에 대한 기술적 강점과 미흡점을 영역별로 분석한 문서이다. 각 항목은 실제 소스 코드를 근거로 작성되었으며, 파일 경로와 라인 번호를 출처로 명시한다.

---

## 1. 프론트엔드

### 장점

**최신 기술 스택 채택**

Next.js 16.1.6과 React 19.2.3을 사용하고 있으며, TypeScript 5.9.3을 strict 모드로 운영한다. Tailwind CSS v4, Vitest, Playwright 등 현대적인 도구 체인을 갖추고 있다.

- 출처: `apps/web/package.json` (라인 17-19) --- `"next": "16.1.6"`, `"react": "19.2.3"`
- 출처: `apps/web/tsconfig.json` (라인 7) --- `"strict": true`

**잘 설계된 API 클라이언트 인터셉터 패턴**

Axios 인터셉터에서 401 응답 시 자동 토큰 갱신(silent refresh)을 수행하며, 갱신 중 발생하는 추가 요청을 큐에 보관했다가 갱신 완료 후 일괄 재시도한다. 429 응답에 대해서는 지수 백오프(exponential backoff)를 적용하여 최대 2회 재시도한다.

- 출처: `apps/web/src/lib/api-client.ts` (라인 52-67) --- `failedQueue` 배열과 `processQueue` 함수
- 출처: `apps/web/src/lib/api-client.ts` (라인 119-128) --- 429 재시도 로직, `Math.pow(2, retryCount)` 백오프

**서버 시간 동기화 기반 카운트다운**

카운트다운 훅이 `useServerTime`을 통해 서버와의 시간 차이(offset)를 계산하여 사용하므로, 클라이언트 시계가 부정확해도 정확한 카운트다운을 표시할 수 있다. 만료 시 무한 루프를 방지하는 로직도 포함되어 있다.

- 출처: `apps/web/src/hooks/use-countdown.ts` (라인 44, 53) --- `useServerTime()` 호출, `getServerNow(offset)` 사용
- 출처: `apps/web/src/hooks/use-countdown.ts` (라인 67-68) --- 리마운트 시 무한 루프 방지 주석 및 로직

**CSP 및 보안 헤더 적용**

Next.js 미들웨어에서 nonce 기반 CSP(Content Security Policy), X-Frame-Options, X-Content-Type-Options, Referrer-Policy를 설정한다. `frame-ancestors 'none'`으로 클릭재킹을 방지한다.

- 출처: `apps/web/src/middleware.ts` (라인 5-17) --- nonce 생성 및 CSP 헤더 조합
- 출처: `apps/web/src/middleware.ts` (라인 23-26) --- X-Frame-Options, X-Content-Type-Options, Referrer-Policy 설정

**한국어 로컬라이제이션**

UI 텍스트가 한국어로 작성되어 있다. 카운트다운 포맷도 `개월`, `일`, `시간`, `분`, `초` 단위로 출력된다.

- 출처: `apps/web/src/hooks/use-countdown.ts` (라인 94-103) --- `"만료됨"`, `"개월"`, `"시간"` 등 한국어 단위 사용

### 미흡점

**전역 상태 관리 부재**

SWR, React Query, Zustand 등 전역 상태 관리 라이브러리가 `package.json`에 포함되어 있지 않다. 각 페이지가 독립적으로 데이터를 fetch하므로, 동일한 API를 여러 컴포넌트에서 중복 호출할 가능성이 있으며 클라이언트 캐싱이 불가능하다.

- 출처: `apps/web/package.json` (라인 14-20) --- dependencies에 상태 관리 라이브러리 없음

**SSR 데이터 페칭 미활용**

API 클라이언트 파일 최상단에 `"use client"` 지시어가 선언되어 있어, 모든 데이터 페칭이 클라이언트 사이드에서 이루어진다. Next.js의 서버 컴포넌트, `generateMetadata`, 서버 액션 등 SSR 기능을 활용하지 않아 SEO와 초기 로드 성능에 불리하다.

- 출처: `apps/web/src/lib/api-client.ts` (라인 1) --- `"use client"` 선언

**localStorage 기반 인증 정보 저장 (XSS 취약)**

사용자 정보를 `localStorage`에 저장하고 있다. XSS 공격이 발생할 경우 localStorage의 내용이 탈취될 수 있다. HttpOnly 쿠키를 사용하는 방식이 더 안전하다.

- 출처: `apps/web/src/lib/storage.ts` (라인 9, 19) --- `localStorage.getItem(USER_KEY)`, `localStorage.setItem(USER_KEY, ...)`

**루트 레벨 에러 바운더리만 존재**

`error.tsx`가 `app/` 루트에만 존재하며, 하위 라우트별 에러 바운더리가 없다. 특정 페이지에서 오류가 발생하면 전체 앱이 에러 화면으로 전환된다. 에러 메시지도 한국어가 아닌 영문("Something went wrong")으로 작성되어 있어 로컬라이제이션 일관성이 떨어진다.

- 출처: `apps/web/src/app/error.tsx` (라인 13-16) --- 영문 에러 메시지, 루트 레벨에만 존재
- 확인: `apps/web/src/app/` 하위에 다른 `error.tsx` 파일 없음

**loading.tsx 스켈레톤 파일 부재**

Next.js의 `loading.tsx` 파일이 어디에도 없다. 페이지 전환 시 로딩 상태를 보여주지 않아 레이아웃 시프트(layout shift)가 발생할 수 있으며, 사용자 경험이 저하된다.

- 확인: `apps/web/src/app/` 전체에서 `loading.tsx` 파일 없음

**i18n 프레임워크 미적용**

한국어 전용 콘텐츠임에도 `next-intl`, `react-i18next` 같은 국제화 프레임워크 없이 하드코딩된 문자열을 사용한다. 향후 다국어 지원이 필요할 경우 전면 수정이 불가피하다.

- 출처: `apps/web/package.json` --- i18n 관련 패키지 없음

---

## 2. 백엔드

### 장점

**3단계 좌석 잠금 메커니즘**

좌석 예약 시 3단계 동시성 제어를 적용한다. Phase 1에서 Redis Lua 스크립트로 분산 락을 획득하고, Phase 2에서 PostgreSQL `SELECT FOR UPDATE`로 DB 수준 비관적 잠금을 수행하며, Phase 3에서 `version` 컬럼을 통한 낙관적 잠금으로 최종 업데이트를 보호한다.

- 출처: `services-spring/ticket-service/.../domain/reservation/service/ReservationService.java` (라인 62-76) --- Phase 1: Redis Lua 락 획득
- 출처: 동일 파일 (라인 79-86) --- Phase 2: `SELECT ... FOR UPDATE`
- 출처: 동일 파일 (라인 104-119) --- Phase 3: `WHERE id = ? AND version = ?` 낙관적 잠금

**Fencing Token 패턴 적용**

좌석 락 서비스에서 Fencing Token을 발급하여, 락 만료 후 지연된 요청이 이미 새로운 소유자에게 할당된 좌석을 덮어쓰는 것을 방지한다. 결제 확인 시에도 Fencing Token을 검증한다.

- 출처: `services-spring/ticket-service/.../domain/seat/service/SeatLockService.java` (라인 23) --- `SeatLockResult(boolean success, long fencingToken)` 레코드
- 출처: 동일 파일 (라인 81-98) --- `verifyForPayment` 메서드
- 출처: `services-spring/ticket-service/.../domain/reservation/service/ReservationService.java` (라인 374-388) --- 결제 시 Fencing Token 검증

**Redis ZSET 기반 가상 대기열 (VWR)**

대기열 서비스가 Redis Sorted Set을 활용하여 대기 순서를 관리한다. 대기 위치에 따라 폴링 간격을 동적으로 조절하고(1~60초), 처리량(throughput) 기반으로 대기 시간을 추정한다.

- 출처: `services-spring/queue-service/.../service/QueueService.java` (라인 231-238) --- `calculateNextPoll`: 위치별 폴링 간격 조절
- 출처: 동일 파일 (라인 242-258) --- `estimateWait`: throughput 기반 대기 시간 추정, `recentAdmissions` 활용

**이벤트 기반 아키텍처 (Kafka)**

Kafka를 통한 서비스 간 비동기 통신을 지원한다. 예약 취소 이벤트가 발행되면 결제 서비스에서 환불을 처리하는 등 도메인 이벤트 패턴을 적용한다.

- 출처: `services-spring/ticket-service/src/main/resources/application.yml` (라인 5-20) --- Kafka producer/consumer 설정, `acks: all`
- 출처: `services-spring/ticket-service/.../domain/reservation/service/ReservationService.java` (라인 497-501) --- `ticketEventProducer.publishReservationCancelled`

**Circuit Breaker 및 Retry 패턴 (Resilience4j)**

서비스 간 내부 통신에 Circuit Breaker와 지수 백오프 Retry를 적용한다. 실패율 50% 초과 시 회로를 열고, 느린 호출(3초 이상) 비율도 모니터링한다.

- 출처: `services-spring/ticket-service/src/main/resources/application.yml` (라인 87-105) --- `resilience4j.circuitbreaker`, `resilience4j.retry` 설정

**서비스별 DB 분리 및 Flyway 마이그레이션**

auth_db, ticket_db, payment_db, stats_db, community_db로 데이터베이스가 분리되어 있으며, Flyway를 통해 스키마 버전 관리를 한다.

- 출처: `k8s/spring/overlays/kind/postgres.yaml` (라인 7-11) --- 5개 데이터베이스 생성
- 출처: `services-spring/ticket-service/src/main/resources/application.yml` (라인 29-31) --- `flyway.enabled: true`

### 미흡점

**Catalog 서비스의 DB 공유**

Catalog 서비스가 자체 DB를 사용하지 않고 ticket_db에 직접 접속한다. 마이크로서비스 아키텍처의 DB-per-service 원칙을 위반하며, 두 서비스 간 스키마 변경 시 결합도가 높아진다.

- 출처: `services-spring/catalog-service/src/main/resources/application.yml` (라인 5) --- `url: ${TICKET_DB_URL:jdbc:postgresql://localhost:5434/ticket_db}`
- 비교: ticket-service도 동일한 `ticket_db` 사용 (`services-spring/ticket-service/src/main/resources/application.yml` 라인 22)

**대기열 서비스의 인메모리 폴백**

Redis 장애 시 `ConcurrentHashMap` 기반 인메모리 폴백으로 전환된다. 코드 주석에서도 "NOT suitable for multi-instance deployment"라고 명시하고 있으나, 실제 다중 인스턴스 환경에서 이 폴백이 활성화되면 대기열 상태가 인스턴스마다 달라져 심각한 일관성 문제가 발생한다.

- 출처: `services-spring/queue-service/.../service/QueueService.java` (라인 37-38) --- `fallbackQueue`, `fallbackActive` 필드
- 출처: 동일 파일 (라인 295-296) --- 경고 로그: "This mode is NOT suitable for multi-instance deployment"

**예약 생성 시 멱등성 키 없음**

예약 생성 API에 멱등성(idempotency) 키가 없다. 네트워크 재시도나 사용자의 중복 클릭으로 인해 동일한 예약이 중복 생성될 수 있다.

- 출처: `services-spring/ticket-service/.../domain/reservation/service/ReservationService.java` (라인 160-234) --- `createReservation` 메서드에 멱등성 검사 로직 없음

**데이터 액세스 패턴 불일치**

ticket-service는 JPA entity와 JdbcTemplate을 혼용하며(JPA `validate` 모드 + 직접 SQL), catalog-service는 Flyway를 비활성화한 채 JdbcTemplate만 사용한다. 서비스 간 데이터 접근 패턴이 통일되어 있지 않아 유지보수 복잡도가 높아진다.

- 출처: `services-spring/ticket-service/src/main/resources/application.yml` (라인 26-28) --- `jpa.hibernate.ddl-auto: validate`
- 출처: `services-spring/catalog-service/src/main/resources/application.yml` (라인 8-9) --- `flyway.enabled: false`

**분산 트랜잭션 미처리 (Saga 패턴 미구현)**

예약-결제-좌석 간 트랜잭션에서 부분 실패 시 보상 트랜잭션을 체계적으로 처리하는 Saga 패턴이 구현되어 있지 않다. 결제 실패 시 좌석 상태 복원은 개별 코드로 처리되며, 중간 단계 실패 시 일관성이 깨질 수 있다.

**API 버전 관리 전략 부재**

Gateway 라우팅에서 `/api/v1/` 경로를 사용하지만 각 백엔드 서비스의 컨트롤러에는 버전 구분 없이 `/api/` 경로를 사용한다. 향후 API 변경 시 하위 호환성 유지가 어렵다.

- 출처: `services-spring/gateway-service/src/main/resources/application.yml` (라인 14-15) --- `Path=/api/v1/auth/**,/api/auth/**` 이중 경로

---

## 3. 보안

### 장점

**타이밍 안전한 토큰 비교**

내부 API 인증 필터에서 `MessageDigest.isEqual()`을 사용하여 상수 시간 문자열 비교를 수행한다. 타이밍 사이드채널 공격을 방지한다.

- 출처: `services-spring/auth-service/.../security/InternalApiAuthFilter.java` (라인 37-39) --- `MessageDigest.isEqual()` 사용

**Redis 기반 Rate Limiting (Lua 스크립트)**

Gateway에서 Redis Lua 스크립트를 이용한 원자적 Rate Limiting을 구현한다. 인증, 대기열, 예매, 일반 요청을 카테고리별로 분리하여 차등 제한을 적용한다.

- 출처: `services-spring/gateway-service/.../filter/RateLimitFilter.java` (라인 39-42) --- 카테고리별 RPM 설정: auth 20, queue 60, booking 10, general 100
- 출처: 동일 파일 (라인 109-124) --- Redis Lua 스크립트 실행

**Default Deny 네트워크 정책**

Kubernetes NetworkPolicy에서 기본적으로 모든 Ingress/Egress를 차단(deny-all)하고, 각 서비스에 대해 허용된 호출자만 명시적으로 열어준다.

- 출처: `k8s/spring/base/network-policies.yaml` (라인 1-9) --- `default-deny-all` 정책
- 출처: 동일 파일 (라인 40-59) --- auth-service는 gateway-service와 catalog-service에서만 접근 허용

**Pod 보안 설정**

Dockerfile에서 non-root 사용자를 생성하여 실행한다. 컨테이너가 root 권한으로 실행되지 않는다.

- 출처: `services-spring/auth-service/Dockerfile` (라인 18-19) --- `adduser --system --uid 1001`, `USER app`

**JWT 최소 키 길이 검증**

Gateway의 Rate Limit 필터에서 JWT 시크릿이 32바이트 미만일 경우 예외를 발생시킨다.

- 출처: `services-spring/gateway-service/.../filter/RateLimitFilter.java` (라인 69-70) --- `if (keyBytes.length < 32) { throw ... }`

### 미흡점

**시크릿 평문 파일 관리**

Kubernetes 시크릿이 평문 `.env` 파일로 관리되고 있다. Sealed Secrets, External Secrets Operator, AWS Secrets Manager 같은 시크릿 관리 도구를 사용하지 않는다.

- 출처: `k8s/spring/overlays/kind/secrets.env` (라인 1-14) --- DB 비밀번호, JWT 시크릿, API 토큰이 평문으로 노출
- 특히: `INTERNAL_API_TOKEN=dev-internal-token-change-me` (라인 12) --- 기본값이 변경되지 않은 토큰

**정적 공유 내부 API 토큰**

모든 서비스가 동일한 `INTERNAL_API_TOKEN`을 공유한다. 토큰 순환(rotation) 메커니즘이 없고, 서비스별 개별 토큰도 없다. 하나의 서비스가 침해되면 모든 내부 API에 접근 가능하다.

- 출처: `services-spring/catalog-service/src/main/resources/application.yml` (라인 42) --- `api-token: ${INTERNAL_API_TOKEN}`
- 출처: `k8s/spring/overlays/kind/secrets.env` (라인 12) --- 단일 공유 토큰

**Rate Limiter Fail-Open 정책**

Redis 장애 시 Rate Limiter가 모든 요청을 통과시킨다(fail-open). 의도적으로 Redis를 마비시키면 Rate Limiting을 우회할 수 있다.

- 출처: `services-spring/gateway-service/.../filter/RateLimitFilter.java` (라인 125-128) --- `catch (Exception e) { ... filterChain.doFilter(...); return; }`

**Permissions-Policy 헤더 미설정**

CSP, X-Frame-Options 등은 설정되어 있으나, `Permissions-Policy` (구 Feature-Policy) 헤더가 없다. 카메라, 마이크, 위치 정보 등 브라우저 기능에 대한 접근 제어가 누락되어 있다.

- 출처: `apps/web/src/middleware.ts` (라인 22-27) --- `Permissions-Policy` 헤더 미포함

**서비스 간 mTLS 미적용**

서비스 간 통신이 평문 HTTP로 이루어지며, 상호 TLS(mTLS) 인증이 적용되어 있지 않다. NetworkPolicy만으로는 네트워크 레벨의 도청이나 MITM 공격을 방지할 수 없다.

**관리자 작업 감사 로그 없음**

이벤트 생성, 예약 상태 변경, 좌석 초기화 등 관리자 API에 대한 감사(audit) 로깅이 구현되어 있지 않다.

**Refresh Token 사용 시 미순환**

토큰 갱신 시 기존 Refresh Token을 무효화하고 새 토큰을 발급하는 Rotation 정책이 보이지 않는다. Refresh Token이 탈취되면 만료 시까지 계속 사용 가능하다.

---

## 4. 인프라

### 장점

**멀티 노드 Kind 클러스터**

로컬 개발 환경에서 control-plane 1개, worker 2개(application, data 레이블 분리)로 구성된 Kind 클러스터를 사용한다. 프로덕션 환경의 멀티 노드 토폴로지를 모방한다.

- 출처: `kind-config.yaml` (라인 4-34) --- 3개 노드, `workload: application`/`workload: data` 레이블

**핵심 서비스 HPA 구성**

프로덕션 오버레이에서 gateway, ticket, queue, payment 서비스에 HPA(Horizontal Pod Autoscaler)가 설정되어 있다. CPU 사용률 70% 기준으로 자동 스케일링된다.

- 출처: `k8s/spring/overlays/prod/hpa.yaml` (라인 1-76) --- 4개 서비스 HPA, gateway/ticket `minReplicas: 3, maxReplicas: 10`

**PDB로 가용성 보장**

모든 핵심 서비스(7개)에 PodDisruptionBudget이 설정되어 있어, 클러스터 업데이트 시에도 최소 1개 Pod가 유지된다.

- 출처: `k8s/spring/overlays/prod/pdb.yaml` (라인 1-70) --- 7개 서비스 PDB, `minAvailable: 1`

**멀티 스테이지 Docker 빌드**

빌드 단계(JDK)와 런타임 단계(JRE)를 분리하여 이미지 크기를 줄이고, non-root 사용자로 실행한다.

- 출처: `services-spring/auth-service/Dockerfile` (라인 1, 13) --- `FROM eclipse-temurin:21-jdk AS build`, `FROM eclipse-temurin:21-jre`

**Kustomize 기반 환경 분리**

Kind(개발)과 prod(운영) 환경을 Kustomize overlay로 분리하여 환경별 설정을 관리한다.

- 확인: `k8s/spring/overlays/kind/`, `k8s/spring/overlays/prod/` 디렉터리 존재

### 미흡점

**단일 PostgreSQL 인스턴스**

5개 데이터베이스가 모두 하나의 PostgreSQL Pod에서 운영된다. 이 Pod가 장애를 일으키면 전체 플랫폼이 중단되는 단일 장애 지점(SPOF)이 된다.

- 출처: `k8s/spring/overlays/kind/postgres.yaml` (라인 19-20) --- `replicas: 1`, 단일 Deployment
- 출처: 동일 파일 (라인 7-11) --- 하나의 인스턴스에서 5개 DB 생성

**데이터베이스 백업 전략 없음**

PostgreSQL에 대한 백업 CronJob, pg_dump 스크립트, WAL 아카이빙 설정이 없다. 데이터 손실 시 복구 수단이 없다.

**PersistentVolume 미보장 (Kind 환경)**

Kind 클러스터에서 PVC를 사용하지만, Kind 기본 StorageClass는 hostPath를 사용하므로 클러스터 삭제 시 데이터가 함께 삭제된다.

- 출처: `k8s/spring/overlays/kind/postgres.yaml` (라인 71) --- `persistentVolumeClaim: postgres-pvc` (Kind에서는 ephemeral)

**프로덕션 오버레이 미완성**

prod 오버레이에 Ingress Controller, cert-manager, TLS 인증서 설정이 없다. 실제 프로덕션 배포에 필요한 구성이 빠져 있다.

- 확인: `k8s/spring/overlays/prod/` 내용물 --- `hpa.yaml`, `pdb.yaml`, `kafka.yaml`, `redis.yaml`만 존재, Ingress 관련 파일 없음

**네임스페이스 수준 리소스 쿼터 없음**

ResourceQuota, LimitRange가 설정되어 있지 않아, 특정 Pod가 노드의 자원을 독점할 가능성이 있다.

**GitOps 미연결**

ArgoCD 관련 디렉터리가 존재하나, 실제 Application 리소스나 연결 설정이 완성되어 있지 않다.

---

## 5. 모니터링

### 장점

**Prometheus + Grafana 모니터링 스택**

Prometheus가 모든 Spring 서비스(8개)의 `/actuator/prometheus` 엔드포인트를 10초 간격으로 스크래핑한다. Grafana 대시보드를 위한 NodePort 서비스도 구성되어 있다.

- 출처: `k8s/spring/overlays/kind/prometheus.yaml` (라인 6-47) --- 8개 서비스 scrape 설정, `scrape_interval: 10s`

**분산 추적 (Zipkin)**

모든 서비스에 Zipkin 분산 추적이 설정되어 있으며, 개발 환경에서는 100% 샘플링 비율을 사용한다.

- 출처: `services-spring/ticket-service/src/main/resources/application.yml` (라인 56-61) --- `sampling.probability: 1.0`, Zipkin 엔드포인트 설정

**구조화된 로깅 (traceId/spanId)**

로그 패턴에 `traceId`와 `spanId`가 포함되어 있어, 분산 환경에서 요청 추적이 가능하다.

- 출처: `services-spring/ticket-service/src/main/resources/application.yml` (라인 63-65) --- `"%5p [${spring.application.name:},%X{traceId:-},%X{spanId:-}]"`

**Actuator 헬스 엔드포인트**

모든 서비스에서 Actuator의 health, info, prometheus 엔드포인트를 노출하며, DB 및 Redis 헬스 체크도 활성화되어 있다.

- 출처: `services-spring/ticket-service/src/main/resources/application.yml` (라인 40-55) --- `show-details: always`, `db.enabled: true`, `redis.enabled: true`

### 미흡점

**알림 규칙 미설정 (AlertManager 없음)**

Prometheus에 AlertManager가 연결되어 있지 않고, alert rule 파일도 없다. 서비스 장애, 높은 에러율, 리소스 부족 등의 상황을 자동 감지하여 알림을 보내는 체계가 없다.

- 출처: `k8s/spring/overlays/kind/prometheus.yaml` (라인 6-48) --- `alerting` 섹션 없음, `rule_files` 없음

**커스텀 Grafana 대시보드 없음**

Grafana 프로비저닝 설정만 있을 뿐, 티켓팅 플랫폼에 특화된 대시보드(예약 성공률, 대기열 깊이, 결제 실패율 등)가 없다.

**SLO/SLI 미정의**

서비스 수준 목표(SLO)와 서비스 수준 지표(SLI)가 정의되어 있지 않다. 예를 들어 "예약 API 응답시간 p99 < 500ms", "가용성 99.9%" 등의 목표가 없어 서비스 품질을 객관적으로 측정할 수 없다.

**비즈니스 KPI 메트릭 없음**

Actuator가 제공하는 기본 메트릭(JVM, HTTP 요청 등) 외에, 비즈니스 관련 커스텀 메트릭(시간당 예매 수, 대기열 이탈률, 결제 전환율 등)이 구현되어 있지 않다.

**정적 스크래핑 타겟**

Prometheus가 정적 `static_configs`로 타겟을 지정하고 있어, Pod 수가 변경될 때 자동 탐지(service discovery)가 되지 않는다. ServiceMonitor CRD나 Kubernetes SD를 사용해야 한다.

- 출처: `k8s/spring/overlays/kind/prometheus.yaml` (라인 13-47) --- `static_configs`만 사용

**로그 기반 알림 없음**

Loki로 로그를 수집하지만, 특정 패턴(ERROR 급증, OOM 발생 등)에 대한 자동 알림이 구성되어 있지 않다.

---

## 6. 종합 개선 제안

영향도와 긴급도에 따라 우선순위를 분류한다. AWS 배포 시 관리형 서비스로 보완 가능한 항목은 **AWS 보완 방안** 열에 명시한다.

### 높은 영향도 / 높은 긴급도

| 순번 | 영역 | 개선 항목 | 사유 | AWS 보완 방안 |
|------|------|-----------|------|---------------|
| 1 | 보안 | 시크릿 관리 체계 도입 (Sealed Secrets 또는 External Secrets) | 평문 시크릿 파일이 코드 저장소에 포함되어 있어 보안 사고 위험이 높음 | **AWS Secrets Manager** + External Secrets Operator로 K8s Secret을 자동 동기화. 자동 순환(rotation) 스케줄 지원 |
| 2 | 인프라 | PostgreSQL 이중화 (최소 primary-replica 구성) | 단일 인스턴스 장애 시 전체 플랫폼 중단 | **Amazon RDS Multi-AZ** 배포로 해결. 자동 장애 조치(failover), 동기식 대기 복제본, 5개 DB를 단일 RDS 인스턴스 또는 서비스별 개별 RDS로 운영 가능 |
| 3 | 보안 | 내부 API 토큰을 서비스별 개별 발급 + 자동 순환 | 단일 토큰 유출 시 전체 내부 API 접근 가능 | **AWS Secrets Manager**의 자동 순환 Lambda와 결합하여 서비스별 토큰을 주기적으로 갱신. 또는 **AWS IAM Roles for Service Accounts (IRSA)** 로 토큰 없는 인증 전환 |
| 4 | 백엔드 | 예약 생성 API에 멱등성 키 추가 | 네트워크 재시도로 인한 중복 예약 위험 | 코드 레벨 개선 필요 (AWS 서비스로 대체 불가) |

### 높은 영향도 / 보통 긴급도

| 순번 | 영역 | 개선 항목 | 사유 | AWS 보완 방안 |
|------|------|-----------|------|---------------|
| 5 | 프론트엔드 | React Query 또는 SWR 도입으로 서버 상태 관리 | API 중복 호출 방지, 캐싱, 낙관적 업데이트 지원 | 코드 레벨 개선. **CloudFront** 캐싱으로 API 응답 캐시는 부분 보완 가능 |
| 6 | 모니터링 | AlertManager 설정 및 핵심 알림 규칙 정의 | 장애 발생 시 자동 감지/통보 불가 | **Amazon CloudWatch Alarms** + **SNS** 토픽으로 알림 체계 구성. 또는 **Amazon Managed Service for Prometheus (AMP)** + AlertManager 사용 |
| 7 | 인프라 | DB 백업 CronJob 및 복구 절차 수립 | 데이터 손실 시 복구 수단 전무 | **RDS 자동 백업** (일일 스냅샷 + 트랜잭션 로그, 최대 35일 보존). 수동 스냅샷으로 영구 백업. **Point-in-Time Recovery**로 5분 단위 복원 가능 |
| 8 | 백엔드 | Catalog 서비스 전용 DB 분리 또는 내부 API 호출로 전환 | DB-per-service 원칙 위반으로 인한 결합도 | 서비스별 **개별 RDS 인스턴스** 생성으로 물리적 분리 용이. RDS 비용 최적화를 위해 `db.t3.micro` 등 소형 인스턴스 활용 |
| 9 | 백엔드 | 대기열 서비스 인메모리 폴백 제거 또는 경고 수준 상향 | 다중 인스턴스에서 데이터 불일치 유발 | **Amazon ElastiCache for Redis** (클러스터 모드) 사용 시 Multi-AZ 자동 장애 조치로 Redis 다운타임을 최소화하여 폴백 시나리오 자체를 방지 |
| 10 | 보안 | Rate Limiter fail-closed 정책 검토 | Redis 장애 시 무제한 트래픽 허용 가능 | **AWS WAF** Rate-based Rules로 1차 방어 계층 추가. ElastiCache Multi-AZ로 Redis 가용성 보장하여 fail-open 시나리오 최소화 |

### 보통 영향도 / 낮은 긴급도

| 순번 | 영역 | 개선 항목 | 사유 | AWS 보완 방안 |
|------|------|-----------|------|---------------|
| 11 | 프론트엔드 | 핵심 페이지 SSR 적용 (이벤트 목록, 이벤트 상세) | SEO 및 초기 로드 성능 개선 | 코드 레벨 개선. **CloudFront + Lambda@Edge**로 SSR 응답 캐싱은 보완 가능 |
| 12 | 프론트엔드 | 라우트별 error.tsx, loading.tsx 추가 | 사용자 경험 개선, 레이아웃 시프트 방지 | 코드 레벨 개선 (AWS 서비스로 대체 불가) |
| 13 | 모니터링 | SLO/SLI 정의 및 비즈니스 커스텀 메트릭 추가 | 서비스 품질 객관적 측정 | **CloudWatch Custom Metrics** + **CloudWatch SLO** 기능으로 SLI/SLO 대시보드 구성 가능 |
| 14 | 모니터링 | Prometheus ServiceMonitor CRD 전환 | HPA 스케일링 시 자동 타겟 탐지 | **AMP (Amazon Managed Prometheus)** 사용 시 EKS 서비스 디스커버리 자동 지원 |
| 15 | 인프라 | 프로덕션 Ingress + TLS 구성 완성 | 실제 배포를 위한 필수 구성 | **ALB Ingress Controller** + **AWS Certificate Manager (ACM)** 무료 TLS 인증서 자동 갱신. Terraform `aws_lb`, `aws_acm_certificate` 리소스로 구성 (프로젝트 내 `terraform/` 디렉터리에 ALB/CloudFront 설정 이미 존재) |
| 16 | 백엔드 | 서비스 간 데이터 액세스 패턴 통일 | 유지보수 복잡도 감소 | 코드 레벨 개선 (AWS 서비스로 대체 불가) |
| 17 | 보안 | 서비스 간 mTLS 적용 (Istio 또는 Linkerd) | 네트워크 레벨 보안 강화 | **AWS App Mesh** (Envoy 기반 서비스 메시) 또는 EKS에서 **Istio** 운영. VPC 내부 통신은 기본 암호화되므로 mTLS 긴급도는 낮아짐 |
| 18 | 프론트엔드 | localStorage 인증 정보를 HttpOnly 쿠키로 전환 | XSS 공격 시 토큰 탈취 방지 | 코드 레벨 개선. **CloudFront Functions**로 쿠키 보안 속성(Secure, SameSite) 강제 부여는 보조 가능 |

---

## 7. AWS 배포 시 아키텍처 보완 요약

현재 프로젝트는 Kind 클러스터(로컬 개발)와 EKS(프로덕션) 배포를 모두 고려하고 있다. `terraform/` 디렉터리에 ALB, CloudFront, Lambda@Edge 설정이 이미 존재하며, AWS 관리형 서비스를 활용하면 위 미흡점의 상당 부분이 인프라 수준에서 해결된다.

### 핵심 AWS 관리형 서비스 매핑

| 미흡점 카테고리 | AWS 서비스 | 해결 범위 |
|-----------------|-----------|-----------|
| DB 단일 장애점 / 백업 없음 | **Amazon RDS Multi-AZ** | 자동 장애 조치, 일일 자동 백업, Point-in-Time Recovery, Read Replica로 읽기 분산 |
| 시크릿 평문 관리 / 토큰 순환 없음 | **AWS Secrets Manager** | 자동 순환(Lambda 기반), K8s External Secrets Operator 연동, 암호화 저장 |
| Redis 가용성 (폴백 문제) | **Amazon ElastiCache Multi-AZ** | 자동 장애 조치, 클러스터 모드 샤딩, 인메모리 폴백 불필요 |
| Kafka 운영 부담 | **Amazon MSK (Managed Kafka)** | 브로커 관리 자동화, Multi-AZ, 자동 스케일링 |
| TLS 인증서 / Ingress 미완성 | **ACM + ALB Ingress Controller** | 무료 TLS 인증서 자동 갱신, ALB에서 TLS 종료 |
| 알림 체계 없음 | **CloudWatch Alarms + SNS** | CPU/메모리/에러율 알림, Slack/이메일 통보 |
| 모니터링 스크래핑 문제 | **AMP + AMG (Managed Grafana)** | 서비스 디스커버리 자동화, 관리형 Grafana 대시보드 |
| Rate Limiting 보조 | **AWS WAF** | L7 Rate-based Rules, IP 기반 차단, SQL Injection/XSS 방어 |
| mTLS / 서비스 메시 | **AWS App Mesh** | Envoy 기반 mTLS 자동 적용, 트래픽 관찰성 |
| 네트워크 보안 | **VPC + Security Groups** | 서브넷 격리(public/private), SG로 포트 수준 접근 제어, VPC 내부 트래픽 암호화 |

### AWS 배포 후에도 코드 레벨 개선이 필요한 항목

다음 항목들은 AWS 인프라로 대체할 수 없으며 코드 변경이 반드시 필요하다:

1. **예약 멱등성 키** --- 애플리케이션 로직에서 중복 요청을 식별해야 함
2. **React Query / SWR 도입** --- 프론트엔드 상태 관리는 클라이언트 코드 변경
3. **SSR 적용** --- Next.js 서버 컴포넌트 활용은 코드 아키텍처 변경
4. **error.tsx / loading.tsx 추가** --- Next.js 라우팅 파일 추가
5. **데이터 액세스 패턴 통일** --- JPA/JdbcTemplate 혼용 정리
6. **Catalog 서비스 DB 분리** --- 서비스 코드 리팩터링 (RDS로 물리적 분리는 용이)
7. **Saga 패턴 구현** --- 분산 트랜잭션 보상 로직은 코드 구현
8. **localStorage → HttpOnly 쿠키** --- 인증 흐름 코드 변경
9. **비즈니스 커스텀 메트릭** --- Micrometer 커스텀 카운터/게이지 코드 추가
