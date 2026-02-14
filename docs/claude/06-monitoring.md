# 06. 모니터링 분석

## 목차

1. [옵저버빌리티 스택 개요](#1-옵저버빌리티-스택-개요)
2. [메트릭 수집 (Prometheus)](#2-메트릭-수집-prometheus)
3. [대시보드 (Grafana)](#3-대시보드-grafana)
4. [로그 수집 (Loki + Promtail)](#4-로그-수집-loki--promtail)
5. [분산 추적 (Zipkin)](#5-분산-추적-zipkin)
6. [헬스체크 & 프로브](#6-헬스체크--프로브)
7. [회복성 (Resilience4j)](#7-회복성-resilience4j)
8. [알림 (Grafana Alert Rules)](#8-알림-grafana-alert-rules)

---

## 1. 옵저버빌리티 스택 개요

URR 티켓팅 플랫폼은 네 가지 핵심 축으로 옵저버빌리티를 구성한다. 메트릭 수집(Prometheus), 대시보드 시각화(Grafana), 로그 집계(Loki + Promtail), 분산 추적(Zipkin)이 각각의 역할을 담당하며, 모든 구성요소는 Kubernetes(Kind 오버레이) 환경에 선언적으로 배포된다.

### 1.1 전체 아키텍처

```
                          +-------------------+
                          |    Grafana :3006   |
                          | (대시보드/알림)     |
                          +--------+----------+
                                   |
                    +--------------+--------------+
                    |                             |
           +--------v--------+          +---------v-------+
           | Prometheus :9090 |          |   Loki :3100    |
           | (메트릭 저장소)    |          | (로그 저장소)    |
           +--------+--------+          +---------+-------+
                    |                             |
    +---------------+---------------+     +-------+-------+
    |       스크래핑 (10초 간격)      |     |   Promtail    |
    |                               |     | (DaemonSet)   |
    v               v               v     +-------+-------+
+--------+  +----------+  +--------+             |
| auth   |  | gateway  |  | ticket |     +-------v-------+
| :3005  |  |  :3001   |  | :3002  |     | Pod 로그 수집  |
+--------+  +----------+  +--------+     | /var/log/*     |
+--------+  +----------+  +--------+     +---------------+
|payment |  |  stats   |  | queue  |
| :3003  |  |  :3004   |  | :3007  |
+--------+  +----------+  +--------+        +-------------+
+----------+  +----------+                  | Zipkin :9411 |
|community |  | catalog  |                  | (트레이싱)    |
|  :3008   |  |  :3009   |                  +------+------+
+----------+  +----------+                         ^
      |            |                               |
      +------------+---------- tracing spans ------+
```

### 1.2 구성요소 요약

| 구성요소 | 역할 | 이미지 버전 | 내부 포트 | NodePort | 소스 참조 |
|---------|------|-----------|----------|----------|----------|
| Prometheus | 메트릭 수집/저장 | `prom/prometheus:v2.51.0` | 9090 | 30090 | `k8s/spring/overlays/kind/prometheus.yaml:68` |
| Grafana | 대시보드/알림 시각화 | `grafana/grafana:10.2.3` | 3006 | 30006 | `k8s/spring/overlays/kind/grafana.yaml:62` |
| Loki | 로그 집계 백엔드 | `grafana/loki:2.9.3` | 3100 | ClusterIP | `k8s/spring/overlays/kind/loki.yaml:63` |
| Promtail | 로그 수집 에이전트 | `grafana/promtail:2.9.3` | 9080 | - | `k8s/spring/overlays/kind/promtail.yaml:52` |
| Zipkin | 분산 추적 | `openzipkin/zipkin:3` | 9411 | 30411 | `k8s/spring/overlays/kind/zipkin.yaml:17` |

### 1.3 데이터 영속성

모든 상태 저장 구성요소는 PersistentVolumeClaim을 통해 데이터를 보존한다.

| PVC 이름 | 용량 | 대상 구성요소 | 소스 참조 |
|---------|------|-------------|----------|
| `prometheus-pvc` | 2Gi | Prometheus | `k8s/spring/overlays/kind/pvc.yaml:56-64` |
| `grafana-pvc` | 1Gi | Grafana | `k8s/spring/overlays/kind/pvc.yaml:30-38` |
| `loki-pvc` | 2Gi | Loki | `k8s/spring/overlays/kind/pvc.yaml:43-51` |

---

## 2. 메트릭 수집 (Prometheus)

### 2.1 Prometheus 설정

Prometheus는 `prom/prometheus:v2.51.0` 이미지로 단일 레플리카 Deployment로 배포된다 (`k8s/spring/overlays/kind/prometheus.yaml:68`).

#### 전역 설정

```yaml
global:
  scrape_interval: 15s
  evaluation_interval: 15s
```
> 소스: `k8s/spring/overlays/kind/prometheus.yaml:7-9`

- **스크래핑 간격(전역)**: 15초 -- 기본 수집 주기
- **평가 간격**: 15초 -- 알림 규칙 평가 주기

#### 서비스 스크래핑 설정

```yaml
scrape_configs:
  - job_name: 'spring-services'
    metrics_path: '/actuator/prometheus'
    scrape_interval: 10s
```
> 소스: `k8s/spring/overlays/kind/prometheus.yaml:11-14`

서비스별 스크래핑 간격은 전역 설정(15초)을 오버라이드하여 **10초**로 설정되어 있다. 메트릭 경로는 Spring Boot Actuator의 Prometheus 엔드포인트인 `/actuator/prometheus`를 사용한다.

#### 스크래핑 대상 서비스

| 서비스 | 타겟 주소 | 라벨 | 소스 참조 |
|-------|----------|------|----------|
| auth-service | `auth-service:3005` | `service: auth-service` | `k8s/spring/overlays/kind/prometheus.yaml:16-19` |
| gateway-service | `gateway-service:3001` | `service: gateway-service` | `k8s/spring/overlays/kind/prometheus.yaml:20-23` |
| ticket-service | `ticket-service:3002` | `service: ticket-service` | `k8s/spring/overlays/kind/prometheus.yaml:24-27` |
| payment-service | `payment-service:3003` | `service: payment-service` | `k8s/spring/overlays/kind/prometheus.yaml:28-31` |
| stats-service | `stats-service:3004` | `service: stats-service` | `k8s/spring/overlays/kind/prometheus.yaml:32-35` |
| queue-service | `queue-service:3007` | `service: queue-service` | `k8s/spring/overlays/kind/prometheus.yaml:36-39` |
| community-service | `community-service:3008` | `service: community-service` | `k8s/spring/overlays/kind/prometheus.yaml:40-43` |
| catalog-service | `catalog-service:3009` | `service: catalog-service` | `k8s/spring/overlays/kind/prometheus.yaml:44-47` |

#### 저장 및 런타임 설정

```yaml
args:
  - "--config.file=/etc/prometheus/prometheus.yml"
  - "--storage.tsdb.retention.time=7d"
  - "--web.enable-lifecycle"
```
> 소스: `k8s/spring/overlays/kind/prometheus.yaml:69-72`

- **보존 기간**: 7일 (`--storage.tsdb.retention.time=7d`)
- **핫 리로드**: 활성화 (`--web.enable-lifecycle`) -- `/-/reload` 엔드포인트로 설정 갱신 가능
- **서비스 포트**: 9090 (내부), 30090 (NodePort) (`k8s/spring/overlays/kind/prometheus.yaml:74,119-121`)

#### Prometheus 자체 헬스체크

```yaml
livenessProbe:
  httpGet:
    path: /-/healthy
    port: 9090
  initialDelaySeconds: 15
  periodSeconds: 10
readinessProbe:
  httpGet:
    path: /-/ready
    port: 9090
  initialDelaySeconds: 5
  periodSeconds: 5
```
> 소스: `k8s/spring/overlays/kind/prometheus.yaml:87-98`

#### 리소스 할당

| 항목 | requests | limits | 소스 참조 |
|-----|----------|--------|----------|
| CPU | 100m | 500m | `k8s/spring/overlays/kind/prometheus.yaml:81-86` |
| 메모리 | 256Mi | 512Mi | `k8s/spring/overlays/kind/prometheus.yaml:81-86` |

### 2.2 서비스 메트릭 노출

각 Spring Boot 서비스는 Micrometer + Actuator 조합을 통해 Prometheus 형식의 메트릭을 노출한다.

#### 의존성 구성 (build.gradle)

모든 서비스는 다음 세 가지 핵심 모니터링 의존성을 포함한다.

| 의존성 | 역할 | 적용 서비스 |
|-------|------|-----------|
| `spring-boot-starter-actuator` | Actuator 엔드포인트 제공 | 전체 8개 서비스 |
| `micrometer-registry-prometheus` | Prometheus 메트릭 포맷 내보내기 | 전체 8개 서비스 |
| `micrometer-tracing-bridge-brave` | 분산 추적(Brave 트레이서) 연동 | 전체 8개 서비스 |
| `zipkin-reporter-brave` | Zipkin 스팬 전송 | 전체 8개 서비스 |

서비스별 의존성 선언 위치:

| 서비스 | actuator | micrometer-prometheus | tracing-bridge-brave | zipkin-reporter |
|-------|----------|----------------------|---------------------|----------------|
| ticket-service | `build.gradle:22` | `build.gradle:32` | `build.gradle:33` | `build.gradle:34` |
| gateway-service | `build.gradle:26` | `build.gradle:28` | `build.gradle:29` | `build.gradle:30` |
| auth-service | `build.gradle:22` | `build.gradle:31` | `build.gradle:32` | `build.gradle:33` |
| payment-service | `build.gradle:22` | `build.gradle:31` | `build.gradle:32` | `build.gradle:33` |
| stats-service | `build.gradle:22` | `build.gradle:29` | `build.gradle:30` | `build.gradle:31` |
| queue-service | `build.gradle:22` | `build.gradle:30` | `build.gradle:31` | `build.gradle:32` |
| community-service | `build.gradle:22` | `build.gradle:31` | `build.gradle:32` | `build.gradle:33` |
| catalog-service | `build.gradle:22` | `build.gradle:31` | `build.gradle:32` | `build.gradle:33` |

> 참고: 경로는 모두 `services-spring/{서비스명}/build.gradle` 기준이다.

#### Actuator 엔드포인트 노출

모든 서비스가 동일한 management 설정을 공유한다. 대표적으로 ticket-service의 설정을 기준으로 설명한다.

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus
```
> 소스: `services-spring/ticket-service/src/main/resources/application.yml:52-55`

노출되는 Actuator 엔드포인트는 세 가지다.

| 엔드포인트 | 경로 | 용도 |
|-----------|------|------|
| `health` | `/actuator/health` | 서비스 건강 상태 확인 (DB, Redis 연결 상태 포함) |
| `info` | `/actuator/info` | 애플리케이션 정보 |
| `prometheus` | `/actuator/prometheus` | Prometheus 메트릭 내보내기 |

서비스별 management 설정 위치:

| 서비스 | 설정 위치 (application.yml) |
|-------|--------------------------|
| ticket-service | `services-spring/ticket-service/src/main/resources/application.yml:40-55` |
| gateway-service | `services-spring/gateway-service/src/main/resources/application.yml:80-93` |
| auth-service | `services-spring/auth-service/src/main/resources/application.yml:22-35` |
| payment-service | `services-spring/payment-service/src/main/resources/application.yml:28-41` |
| stats-service | `services-spring/stats-service/src/main/resources/application.yml:30-43` |
| queue-service | `services-spring/queue-service/src/main/resources/application.yml:12-25` |
| community-service | `services-spring/community-service/src/main/resources/application.yml:15-28` |
| catalog-service | `services-spring/catalog-service/src/main/resources/application.yml:14-27` |

### 2.3 수집 메트릭 종류

Spring Boot Actuator와 Micrometer가 자동으로 수집하는 메트릭과 Grafana 대시보드에서 실제 사용하는 PromQL 쿼리를 기준으로 정리한다.

#### HTTP 요청 메트릭

| 메트릭 이름 | 유형 | 레이블 | 설명 |
|-----------|------|-------|------|
| `http_server_requests_seconds_count` | Counter | `application`, `method`, `status`, `uri` | 총 요청 수 |
| `http_server_requests_seconds_sum` | Counter | `application`, `method`, `status`, `uri` | 총 응답 시간 합계 |
| `http_server_requests_seconds_bucket` | Histogram | `application`, `method`, `status`, `uri`, `le` | 응답 시간 분포 (백분위 계산용) |

> 대시보드에서의 활용 예시 (Service Overview):
> - 서비스별 초당 요청률: `sum(rate(http_server_requests_seconds_count{...}[1m])) by (application)` (`k8s/spring/overlays/kind/grafana-dashboards.yaml:42`)
> - 5xx 에러율: `sum(rate(http_server_requests_seconds_count{status=~"5.."}[5m])) by (application)` (`k8s/spring/overlays/kind/grafana-dashboards.yaml:62`)
> - p50/p95/p99 응답 시간: `histogram_quantile(0.95, ...)` (`k8s/spring/overlays/kind/grafana-dashboards.yaml:101,121,149`)

#### JVM 메트릭

| 메트릭 이름 | 유형 | 설명 | 대시보드 참조 |
|-----------|------|------|-------------|
| `jvm_memory_used_bytes` | Gauge | JVM 메모리 사용량 (heap/nonheap) | `grafana-dashboards.yaml:293` |
| `jvm_memory_max_bytes` | Gauge | JVM 최대 메모리 | `grafana-dashboards.yaml:298` |
| `jvm_gc_pause_seconds_sum` | Counter | GC 일시 정지 시간 합계 | `grafana-dashboards.yaml:374` |
| `jvm_gc_pause_seconds_count` | Counter | GC 일시 정지 횟수 | `grafana-dashboards.yaml:379` |
| `jvm_threads_live_threads` | Gauge | 활성 스레드 수 | `grafana-dashboards.yaml:405` |
| `jvm_threads_daemon_threads` | Gauge | 데몬 스레드 수 | `grafana-dashboards.yaml:410` |
| `jvm_threads_peak_threads` | Gauge | 피크 스레드 수 | `grafana-dashboards.yaml:415` |
| `jvm_classes_loaded_classes` | Gauge | 로드된 클래스 수 | `grafana-dashboards.yaml:474` |
| `jvm_gc_memory_allocated_bytes_total` | Counter | GC 할당 메모리 총량 | `grafana-dashboards.yaml:494` |
| `jvm_gc_memory_promoted_bytes_total` | Counter | GC 승격 메모리 총량 | `grafana-dashboards.yaml:499` |
| `process_cpu_usage` | Gauge | 프로세스 CPU 사용률 | `grafana-dashboards.yaml:441` |
| `system_cpu_usage` | Gauge | 시스템 CPU 사용률 | `grafana-dashboards.yaml:446` |
| `process_uptime_seconds` | Gauge | 프로세스 가동 시간 | `grafana-dashboards.yaml:202` |

#### DB 커넥션 풀 메트릭 (HikariCP)

| 메트릭 이름 | 유형 | 설명 | 대시보드 참조 |
|-----------|------|------|-------------|
| `hikaricp_connections_active` | Gauge | 활성 커넥션 수 | `grafana-dashboards.yaml:783` |
| `hikaricp_connections_idle` | Gauge | 유휴 커넥션 수 | `grafana-dashboards.yaml:788` |
| `hikaricp_connections_max` | Gauge | 최대 커넥션 수 | `grafana-dashboards.yaml:808` |
| `hikaricp_connections_pending` | Gauge | 대기 중인 커넥션 요청 수 | `grafana-dashboards.yaml:866` |
| `hikaricp_connections_timeout_total` | Counter | 타임아웃된 커넥션 수 | `grafana-dashboards.yaml:871` |
| `hikaricp_connections_acquire_seconds_sum` | Counter | 커넥션 획득 시간 합계 | `grafana-dashboards.yaml:838` |
| `hikaricp_connections_acquire_seconds_count` | Counter | 커넥션 획득 횟수 | `grafana-dashboards.yaml:838` |

DB를 사용하는 서비스: ticket-service, auth-service, payment-service, stats-service, community-service, catalog-service

#### Redis 메트릭 (Lettuce)

| 메트릭 이름 | 유형 | 설명 | 대시보드 참조 |
|-----------|------|------|-------------|
| `lettuce_command_completion_seconds_sum` | Counter | Redis 커맨드 완료 시간 합계 | `grafana-dashboards.yaml:899` |
| `lettuce_command_completion_seconds_count` | Counter | Redis 커맨드 실행 횟수 | `grafana-dashboards.yaml:899` |
| `lettuce_command_completion_seconds_bucket` | Histogram | Redis 커맨드 응답 시간 분포 | `grafana-dashboards.yaml:904` |
| `lettuce_command_firstresponse_seconds_count` | Counter | 첫 응답 수신 횟수 | `grafana-dashboards.yaml:929` |

Redis를 사용하는 서비스: ticket-service (`application.yml:32-35`), gateway-service (`application.yml:4-7`), queue-service (`application.yml:4-7`)

#### Kafka Producer/Consumer 메트릭

| 메트릭 이름 | 유형 | 설명 | 대시보드 참조 |
|-----------|------|------|-------------|
| `kafka_consumer_records_lag_max` | Gauge | 파티션별 최대 consumer lag | `grafana-dashboards.yaml:557` |
| `kafka_consumer_records_lag` | Gauge | 파티션별 consumer lag | `grafana-dashboards.yaml:585` |
| `kafka_consumer_fetch_manager_records_consumed_total` | Counter | 소비된 메시지 총수 | `grafana-dashboards.yaml:605` |
| `kafka_producer_record_send_total` | Counter | 전송된 메시지 총수 | `grafana-dashboards.yaml:625` |
| `kafka_consumer_fetch_manager_fetch_rate` | Gauge | consumer fetch 비율 | `grafana-dashboards.yaml:645` |
| `kafka_producer_record_error_total` | Counter | producer 에러 총수 | `grafana-dashboards.yaml:665` |
| `kafka_producer_record_retry_total` | Counter | producer 재시도 총수 | `grafana-dashboards.yaml:670` |
| `kafka_consumer_coordinator_assigned_partitions` | Gauge | 할당된 파티션 수 | `grafana-dashboards.yaml:697` |
| `kafka_consumer_connection_count` | Gauge | consumer 브로커 커넥션 수 | `grafana-dashboards.yaml:949` |

Kafka를 사용하는 서비스: ticket-service (`application.yml:4-20`), payment-service (`application.yml:4-12`), stats-service (`application.yml:4-14`)

#### 기타 인프라 메트릭

| 메트릭 이름 | 유형 | 설명 | 대시보드 참조 |
|-----------|------|------|-------------|
| `up` | Gauge | Prometheus 스크래핑 성공 여부 (1=UP, 0=DOWN) | `grafana-dashboards.yaml:753` |
| `tomcat_connections_current_connections` | Gauge | 현재 Tomcat 연결 수 | `grafana-dashboards.yaml:177` |
| `tomcat_threads_busy_threads` | Gauge | 바쁜 Tomcat 스레드 수 | `grafana-dashboards.yaml:182` |
| `disk_free_bytes` | Gauge | 디스크 여유 공간 | `grafana-dashboards.yaml:977` |
| `disk_total_bytes` | Gauge | 디스크 전체 용량 | `grafana-dashboards.yaml:982` |

---

## 3. 대시보드 (Grafana)

### 3.1 Grafana 설정

Grafana는 `grafana/grafana:10.2.3` 이미지로 단일 레플리카 Deployment로 배포된다 (`k8s/spring/overlays/kind/grafana.yaml:62`).

#### 포트 설정

| 구분 | 포트 | 소스 참조 |
|------|------|----------|
| 컨테이너 내부 | 3000 (기본) | `k8s/spring/overlays/kind/grafana.yaml:64` |
| HTTP 서버 포트 (환경변수 오버라이드) | 3006 | `k8s/spring/overlays/kind/grafana.yaml:73-74` |
| NodePort (외부 접근) | 30006 | `k8s/spring/overlays/kind/grafana.yaml:131` |

#### 인증 설정

```yaml
env:
  - name: GF_SECURITY_ADMIN_USER
    value: "admin"
  - name: GF_SECURITY_ADMIN_PASSWORD
    value: "admin"
  - name: GF_USERS_ALLOW_SIGN_UP
    value: "false"
```
> 소스: `k8s/spring/overlays/kind/grafana.yaml:67-72`

- 기본 관리자 계정: `admin` / `admin`
- 사용자 자가 가입: 비활성화

#### 볼륨 마운트 구성

| 마운트 경로 | ConfigMap 소스 | 용도 | 소스 참조 |
|-----------|---------------|------|----------|
| `/var/lib/grafana` | `grafana-pvc` (PVC) | 영구 데이터 저장 | `k8s/spring/overlays/kind/grafana.yaml:76-77` |
| `/etc/grafana/provisioning/datasources` | `grafana-datasources` | 데이터소스 자동 프로비저닝 | `k8s/spring/overlays/kind/grafana.yaml:78-79` |
| `/etc/grafana/provisioning/dashboards` | `grafana-dashboard-provisioning` | 대시보드 프로비저닝 설정 | `k8s/spring/overlays/kind/grafana.yaml:80-81` |
| `/var/lib/grafana/dashboards` | `grafana-dashboards` | 대시보드 JSON 파일 | `k8s/spring/overlays/kind/grafana.yaml:82-83` |

#### Grafana 자체 헬스체크

```yaml
livenessProbe:
  httpGet:
    path: /api/health
    port: 3006
  initialDelaySeconds: 30
  periodSeconds: 10
readinessProbe:
  httpGet:
    path: /api/health
    port: 3006
  initialDelaySeconds: 10
  periodSeconds: 5
```
> 소스: `k8s/spring/overlays/kind/grafana.yaml:84-95`

#### 리소스 할당

| 항목 | requests | limits | 소스 참조 |
|-----|----------|--------|----------|
| CPU | 100m | 500m | `k8s/spring/overlays/kind/grafana.yaml:96-102` |
| 메모리 | 256Mi | 512Mi | `k8s/spring/overlays/kind/grafana.yaml:96-102` |

### 3.2 데이터소스

Grafana에 두 개의 데이터소스가 ConfigMap을 통해 자동 프로비저닝된다.

```yaml
datasources:
  - name: Prometheus
    type: prometheus
    access: proxy
    url: http://prometheus-service:9090
    isDefault: true
    editable: true
  - name: Loki
    type: loki
    access: proxy
    url: http://loki-service:3100
    isDefault: false
    editable: true
```
> 소스: `k8s/spring/overlays/kind/grafana.yaml:7-21`

| 데이터소스 | URL | 기본값 여부 | 소스 참조 |
|-----------|-----|-----------|----------|
| Prometheus | `http://prometheus-service:9090` | 예 (isDefault: true) | `k8s/spring/overlays/kind/grafana.yaml:10-15` |
| Loki | `http://loki-service:3100` | 아니오 | `k8s/spring/overlays/kind/grafana.yaml:16-21` |

### 3.3 대시보드 프로비저닝 설정

대시보드는 파일 기반 프로비저닝으로 자동 로드된다.

```yaml
providers:
  - name: 'urr-dashboards'
    orgId: 1
    folder: 'URR'
    type: file
    disableDeletion: false
    updateIntervalSeconds: 30
    allowUiUpdates: true
    options:
      path: /var/lib/grafana/dashboards
```
> 소스: `k8s/spring/overlays/kind/grafana.yaml:29-41`

- **프로바이더 이름**: `urr-dashboards`
- **폴더**: `URR` (Grafana UI에서의 폴더 분류)
- **자동 갱신 간격**: 30초 (`updateIntervalSeconds: 30`)
- **UI 편집 허용**: true (`allowUiUpdates: true`)

### 3.4 대시보드 구성

네 개의 대시보드가 `grafana-dashboards` ConfigMap에 JSON 형식으로 정의되어 있다 (`k8s/spring/overlays/kind/grafana-dashboards.yaml`).

#### 3.4.1 Service Overview 대시보드

> UID: `urr-service-overview` | 소스: `k8s/spring/overlays/kind/grafana-dashboards.yaml:9-258`

| 패널 ID | 패널 제목 | 유형 | PromQL 쿼리 요약 |
|---------|---------|------|-----------------|
| 1 | Request Rate per Service | timeseries | `rate(http_server_requests_seconds_count[1m])` by application |
| 2 | Error Rate per Service (5xx) | timeseries | 5xx 요청률 + 에러 백분율 |
| 3 | Response Time p50 | timeseries | `histogram_quantile(0.50, ...)` |
| 4 | Response Time p95 | timeseries | `histogram_quantile(0.95, ...)` |
| 5 | Response Time p99 | timeseries | `histogram_quantile(0.99, ...)` |
| 6 | Active HTTP Connections | timeseries | Tomcat 현재 연결 + 바쁜 스레드 |
| 7 | Uptime per Service | stat | `process_uptime_seconds` |
| 8 | Request Count by Status Code | piechart | 상태 코드별 요청 분포 |
| 9 | Top Endpoints by Request Rate | table | 상위 15개 엔드포인트 요청률 |

- 템플릿 변수: `$service` -- Prometheus `application` 라벨 기반 다중 선택 (`grafana-dashboards.yaml:19-31`)
- 자동 새로고침: 10초 (`grafana-dashboards.yaml:17`)
- 기본 시간 범위: 최근 1시간 (`grafana-dashboards.yaml:18`)

#### 3.4.2 JVM Metrics 대시보드

> UID: `urr-jvm-metrics` | 소스: `k8s/spring/overlays/kind/grafana-dashboards.yaml:260-512`

| 패널 ID | 패널 제목 | 유형 | 설명 |
|---------|---------|------|------|
| 1 | Heap Memory Used | timeseries | heap 사용량 vs 최대값 |
| 2 | Heap Memory Utilization % | gauge | heap 사용률 (임계값: 70% 경고, 90% 위험) |
| 3 | Non-Heap Memory Used | timeseries | non-heap 메모리 추이 |
| 4 | GC Pause Duration | timeseries | GC 일시 정지 시간 + 횟수 |
| 5 | Live Threads | timeseries | 활성/데몬/피크 스레드 |
| 6 | CPU Usage | timeseries | 프로세스 vs 시스템 CPU 사용률 |
| 7 | Loaded Classes | timeseries | 로드된 클래스 수 추이 |
| 8 | GC Memory Allocated / Promoted | timeseries | GC 할당/승격 메모리 속도 |

#### 3.4.3 Kafka Metrics 대시보드

> UID: `urr-kafka-metrics` | 소스: `k8s/spring/overlays/kind/grafana-dashboards.yaml:514-718`

| 패널 ID | 패널 제목 | 유형 | 설명 |
|---------|---------|------|------|
| 1 | Consumer Lag (Max per Partition) | timeseries | 파티션별 최대 lag (임계값: 100 경고, 1000 위험) |
| 2 | Consumer Lag (Records) | timeseries | 파티션별 상세 lag |
| 3 | Messages Consumed per Second | timeseries | 토픽별 소비 속도 |
| 4 | Messages Produced per Second | timeseries | 서비스별 생산 속도 |
| 5 | Consumer Fetch Rate | timeseries | consumer fetch 비율 |
| 6 | Producer Error Rate | timeseries | producer 에러/재시도 비율 |
| 7 | Consumer Group Status | stat | 할당된 파티션 수 |

- 추가 템플릿 변수: `$topic` -- Kafka 토픽 기반 다중 선택 (`grafana-dashboards.yaml:537-545`)

#### 3.4.4 Infrastructure 대시보드

> UID: `urr-infrastructure` | 소스: `k8s/spring/overlays/kind/grafana-dashboards.yaml:720-1001`

| 패널 ID | 패널 제목 | 유형 | 설명 |
|---------|---------|------|------|
| 1 | Service Health Status | stat | UP/DOWN 상태 (up 메트릭) |
| 2 | HikariCP Active Connections | timeseries | DB 커넥션 풀 활성/유휴 |
| 3 | HikariCP Connection Pool Utilization | gauge | 풀 사용률 (임계값: 60% 경고, 85% 위험) |
| 4 | HikariCP Connection Acquire Time | timeseries | 커넥션 획득 평균 시간 |
| 5 | HikariCP Pending Connection Requests | timeseries | 대기 중 커넥션 요청 + 타임아웃 |
| 6 | Redis Command Latency | timeseries | Redis 커맨드 평균/p95 지연 시간 |
| 7 | Redis Command Rate | timeseries | Redis 초당 커맨드 실행률 |
| 8 | Kafka Broker Availability | stat | consumer 브로커 커넥션 수 |
| 9 | Disk Usage | timeseries | 디스크 여유/전체 공간 |

#### 3.4.5 URR Alert Rules 대시보드

> UID: `urr-alerts` | 소스: `k8s/spring/overlays/kind/grafana-dashboards.yaml:1003-1219`

상세 내용은 [8장 알림](#8-알림-grafana-alert-rules)에서 다룬다.

---

## 4. 로그 수집 (Loki + Promtail)

### 4.1 Promtail

Promtail은 Kubernetes 클러스터의 모든 노드에서 Pod 로그를 수집하여 Loki로 전송하는 에이전트다. DaemonSet으로 배포되어 클러스터의 모든 노드에서 실행된다 (`k8s/spring/overlays/kind/promtail.yaml:35`).

#### 배포 구성

| 항목 | 값 | 소스 참조 |
|-----|-----|----------|
| 배포 방식 | DaemonSet | `k8s/spring/overlays/kind/promtail.yaml:35` |
| 이미지 | `grafana/promtail:2.9.3` | `k8s/spring/overlays/kind/promtail.yaml:52` |
| HTTP 포트 | 9080 | `k8s/spring/overlays/kind/promtail.yaml:9,64` |
| gRPC 포트 | 0 (비활성화) | `k8s/spring/overlays/kind/promtail.yaml:10` |
| 서비스 계정 | `promtail` | `k8s/spring/overlays/kind/promtail.yaml:49` |

#### 리소스 할당

| 항목 | requests | limits | 소스 참조 |
|-----|----------|--------|----------|
| CPU | 50m | 200m | `k8s/spring/overlays/kind/promtail.yaml:67-72` |
| 메모리 | 128Mi | 256Mi | `k8s/spring/overlays/kind/promtail.yaml:67-72` |

#### Loki 전송 설정

```yaml
clients:
  - url: http://loki-service:3100/loki/api/v1/push
```
> 소스: `k8s/spring/overlays/kind/promtail.yaml:15-16`

#### 로그 수집 설정 (scrape_configs)

```yaml
scrape_configs:
  - job_name: kubernetes-pods
    kubernetes_sd_configs:
      - role: pod
        namespaces:
          names:
            - urr-spring
```
> 소스: `k8s/spring/overlays/kind/promtail.yaml:18-24`

- **수집 대상**: `urr-spring` 네임스페이스의 모든 Pod
- **서비스 디스커버리**: Kubernetes Pod SD (`role: pod`)

#### 레이블 재매핑

```yaml
relabel_configs:
  - source_labels: [__meta_kubernetes_pod_label_app]
    target_label: app
  - source_labels: [__meta_kubernetes_pod_name]
    target_label: pod
  - source_labels: [__meta_kubernetes_namespace]
    target_label: namespace
```
> 소스: `k8s/spring/overlays/kind/promtail.yaml:25-31`

Kubernetes 메타데이터에서 세 가지 레이블을 추출하여 Loki 로그 스트림에 태깅한다.

| 소스 레이블 | 대상 레이블 | 용도 |
|-----------|-----------|------|
| `__meta_kubernetes_pod_label_app` | `app` | 서비스 식별 |
| `__meta_kubernetes_pod_name` | `pod` | Pod 인스턴스 식별 |
| `__meta_kubernetes_namespace` | `namespace` | 네임스페이스 구분 |

#### 볼륨 마운트

| 마운트 경로 | 호스트 경로 | 읽기 전용 | 소스 참조 |
|-----------|-----------|---------|----------|
| `/etc/promtail` | ConfigMap | - | `k8s/spring/overlays/kind/promtail.yaml:56-57` |
| `/var/log` | `/var/log` | 아니오 | `k8s/spring/overlays/kind/promtail.yaml:58-59` |
| `/var/lib/docker/containers` | `/var/lib/docker/containers` | 예 | `k8s/spring/overlays/kind/promtail.yaml:60-62` |

#### RBAC 설정

Promtail이 Kubernetes API를 통해 Pod 메타데이터를 조회할 수 있도록 ClusterRole과 ClusterRoleBinding이 구성되어 있다.

```yaml
rules:
  - apiGroups: [""]
    resources:
      - nodes
      - nodes/proxy
      - services
      - endpoints
      - pods
    verbs: ["get", "watch", "list"]
```
> 소스: `k8s/spring/overlays/kind/promtail.yaml:95-103`

- ClusterRole: `promtail` (`k8s/spring/overlays/kind/promtail.yaml:92-103`)
- ClusterRoleBinding: `promtail` -- `urr-spring` 네임스페이스의 서비스 계정에 바인딩 (`k8s/spring/overlays/kind/promtail.yaml:106-117`)

### 4.2 Loki

Loki는 Promtail이 수집한 로그를 저장하고 Grafana에서 쿼리할 수 있도록 하는 로그 집계 백엔드다.

#### 배포 구성

| 항목 | 값 | 소스 참조 |
|-----|-----|----------|
| 이미지 | `grafana/loki:2.9.3` | `k8s/spring/overlays/kind/loki.yaml:63` |
| 레플리카 | 1 | `k8s/spring/overlays/kind/loki.yaml:52` |
| HTTP 포트 | 3100 | `k8s/spring/overlays/kind/loki.yaml:11,67` |
| gRPC 포트 | 9096 | `k8s/spring/overlays/kind/loki.yaml:12,69-70` |
| 서비스 유형 | ClusterIP | `k8s/spring/overlays/kind/loki.yaml:111` |

#### 서버 설정

```yaml
auth_enabled: false

server:
  http_listen_port: 3100
  grpc_listen_port: 9096
```
> 소스: `k8s/spring/overlays/kind/loki.yaml:8-12`

- 인증: 비활성화 (`auth_enabled: false`) -- 개발 환경 전용

#### 스토리지 설정

```yaml
common:
  path_prefix: /loki
  storage:
    filesystem:
      chunks_directory: /loki/chunks
      rules_directory: /loki/rules
  replication_factor: 1
  ring:
    kvstore:
      store: inmemory
```
> 소스: `k8s/spring/overlays/kind/loki.yaml:14-23`

- **스토리지 유형**: 파일시스템 기반 (단일 노드 환경에 적합)
- **복제 팩터**: 1 (고가용성 미적용)
- **KV 저장소**: 인메모리 (해시 링 관리용)

#### 스키마 설정

```yaml
schema_config:
  configs:
    - from: 2020-10-24
      store: boltdb-shipper
      object_store: filesystem
      schema: v11
      index:
        prefix: index_
        period: 24h
```
> 소스: `k8s/spring/overlays/kind/loki.yaml:25-33`

- **인덱스 저장소**: BoltDB Shipper
- **오브젝트 저장소**: 파일시스템
- **인덱스 주기**: 24시간

#### 캐시 설정

```yaml
storage_config:
  boltdb_shipper:
    active_index_directory: /loki/boltdb-shipper-active
    cache_location: /loki/boltdb-shipper-cache
    cache_ttl: 24h
    shared_store: filesystem
```
> 소스: `k8s/spring/overlays/kind/loki.yaml:35-42`

- **캐시 TTL**: 24시간

#### Loki 헬스체크

```yaml
livenessProbe:
  httpGet:
    path: /ready
    port: 3100
  initialDelaySeconds: 45
  periodSeconds: 10
readinessProbe:
  httpGet:
    path: /ready
    port: 3100
  initialDelaySeconds: 30
  periodSeconds: 5
```
> 소스: `k8s/spring/overlays/kind/loki.yaml:76-87`

#### 리소스 할당

| 항목 | requests | limits | 소스 참조 |
|-----|----------|--------|----------|
| CPU | 100m | 500m | `k8s/spring/overlays/kind/loki.yaml:88-94` |
| 메모리 | 256Mi | 512Mi | `k8s/spring/overlays/kind/loki.yaml:88-94` |

### 4.3 구조화 로그

모든 서비스는 동일한 로그 패턴을 사용하여 분산 추적 정보를 로그에 포함시킨다.

#### 로그 패턴

```yaml
logging:
  pattern:
    level: "%5p [${spring.application.name:},%X{traceId:-},%X{spanId:-}]"
```

이 패턴은 다음 형식의 로그를 생성한다:

```
 INFO [ticket-service,6a3f2b1c9d4e8f,a1b2c3d4e5f6]  실제 로그 메시지
```

| 구성요소 | 설명 | 예시 |
|---------|------|------|
| `%5p` | 로그 레벨 (5자리 우측 정렬) | ` INFO`, `ERROR` |
| `${spring.application.name:}` | 서비스 이름 | `ticket-service` |
| `%X{traceId:-}` | MDC의 traceId (Micrometer Brave가 자동 주입) | `6a3f2b1c9d4e8f` |
| `%X{spanId:-}` | MDC의 spanId (Micrometer Brave가 자동 주입) | `a1b2c3d4e5f6` |

서비스별 로깅 패턴 설정 위치:

| 서비스 | 설정 위치 (application.yml) |
|-------|--------------------------|
| ticket-service | `services-spring/ticket-service/src/main/resources/application.yml:63-65` |
| gateway-service | `services-spring/gateway-service/src/main/resources/application.yml:101-103` |
| auth-service | `services-spring/auth-service/src/main/resources/application.yml:43-45` |
| payment-service | `services-spring/payment-service/src/main/resources/application.yml:49-51` |
| stats-service | `services-spring/stats-service/src/main/resources/application.yml:51-53` |
| queue-service | `services-spring/queue-service/src/main/resources/application.yml:33-35` |
| community-service | `services-spring/community-service/src/main/resources/application.yml:36-38` |
| catalog-service | `services-spring/catalog-service/src/main/resources/application.yml:35-37` |

#### 로그-트레이싱 연관

로그에 포함된 `traceId`와 `spanId`는 Zipkin의 트레이스 ID와 동일하다. 이를 통해 다음과 같은 워크플로가 가능하다.

1. Grafana Loki에서 에러 로그 발견
2. 로그에 포함된 `traceId` 확인
3. Zipkin UI에서 해당 `traceId`로 검색
4. 요청의 전체 서비스 간 호출 경로 확인

이 연관은 `micrometer-tracing-bridge-brave` 라이브러리가 Logback MDC에 `traceId`와 `spanId`를 자동으로 주입하기 때문에 별도 코드 없이 작동한다.

---

## 5. 분산 추적 (Zipkin)

### 5.1 Zipkin 설정

Zipkin은 마이크로서비스 간의 요청 흐름을 추적하고 시각화하는 분산 추적 시스템이다.

#### 배포 구성

| 항목 | 값 | 소스 참조 |
|-----|-----|----------|
| 이미지 | `openzipkin/zipkin:3` | `k8s/spring/overlays/kind/zipkin.yaml:17` |
| 레플리카 | 1 | `k8s/spring/overlays/kind/zipkin.yaml:6` |
| 컨테이너 포트 | 9411 | `k8s/spring/overlays/kind/zipkin.yaml:19` |
| NodePort | 30411 | `k8s/spring/overlays/kind/zipkin.yaml:39` |
| 서비스 이름 | `zipkin-spring` | `k8s/spring/overlays/kind/zipkin.yaml:31` |

#### 리소스 할당

| 항목 | requests | limits | 소스 참조 |
|-----|----------|--------|----------|
| CPU | 100m | 500m | `k8s/spring/overlays/kind/zipkin.yaml:21-26` |
| 메모리 | 256Mi | 512Mi | `k8s/spring/overlays/kind/zipkin.yaml:21-26` |

> 참고: Zipkin은 기본 인메모리 스토리지를 사용한다. PVC가 할당되어 있지 않으므로 Pod 재시작 시 추적 데이터가 유실된다. 프로덕션 환경에서는 Elasticsearch 또는 Cassandra 백엔드 구성이 필요하다.

### 5.2 서비스 통합

모든 서비스는 Micrometer의 Brave 트레이서를 사용하여 Zipkin에 스팬(span)을 전송한다.

#### 추적 설정 (공통)

```yaml
management:
  tracing:
    sampling:
      probability: ${TRACING_SAMPLING_PROBABILITY:1.0}
  zipkin:
    tracing:
      endpoint: ${ZIPKIN_ENDPOINT:http://localhost:9411/api/v2/spans}
```

| 설정 항목 | 값 | 설명 |
|---------|-----|------|
| 샘플링 확률 | `1.0` (100%) | 모든 요청을 추적 (개발 환경) |
| Zipkin 엔드포인트 | `http://localhost:9411/api/v2/spans` | 로컬 기본값, K8s에서는 환경변수로 오버라이드 |

서비스별 추적 설정 위치:

| 서비스 | tracing 설정 | zipkin 설정 | 소스 참조 |
|-------|-------------|------------|----------|
| ticket-service | `application.yml:56-58` | `application.yml:59-61` | `services-spring/ticket-service/src/main/resources/application.yml` |
| gateway-service | `application.yml:94-96` | `application.yml:97-99` | `services-spring/gateway-service/src/main/resources/application.yml` |
| auth-service | `application.yml:36-38` | `application.yml:39-41` | `services-spring/auth-service/src/main/resources/application.yml` |
| payment-service | `application.yml:42-44` | `application.yml:45-47` | `services-spring/payment-service/src/main/resources/application.yml` |
| stats-service | `application.yml:44-46` | `application.yml:47-49` | `services-spring/stats-service/src/main/resources/application.yml` |
| queue-service | `application.yml:26-28` | `application.yml:29-31` | `services-spring/queue-service/src/main/resources/application.yml` |
| community-service | `application.yml:29-31` | `application.yml:32-34` | `services-spring/community-service/src/main/resources/application.yml` |
| catalog-service | `application.yml:28-30` | `application.yml:31-33` | `services-spring/catalog-service/src/main/resources/application.yml` |

#### 의존성 체인

추적 기능은 세 가지 라이브러리의 조합으로 동작한다.

```
micrometer-tracing-bridge-brave  -->  Brave 트레이서 연동 (SpanContext 생성)
         |
         v
zipkin-reporter-brave            -->  Brave 스팬을 Zipkin 형식으로 변환/전송
         |
         v
Zipkin Server (:9411)            -->  스팬 수집/저장/시각화
```

### 5.3 추적 흐름

일반적인 티켓 예매 요청의 추적 흐름은 다음과 같다.

```
[Client] ---> [Gateway :3001] ---> [Ticket Service :3002] ---> PostgreSQL
                   |                       |
                   |                       +---> Redis (예약 잠금)
                   |                       |
                   |                       +---> Kafka (이벤트 발행)
                   |
                   +---> [Auth Service :3005] ---> PostgreSQL (JWT 검증)
```

**traceId/spanId 전파 메커니즘:**

1. 게이트웨이에서 최초 요청 수신 시 traceId 생성 (또는 클라이언트가 전달한 값 사용)
2. 다운스트림 서비스 호출 시 HTTP 헤더(`b3` 또는 `traceparent`)를 통해 traceId 전파
3. 각 서비스는 새로운 spanId를 생성하고, parentSpanId로 호출자의 spanId를 기록
4. Kafka 메시지 헤더에도 traceId/spanId가 자동 전파 (Spring Kafka + Micrometer 통합)
5. 모든 스팬은 Zipkin 서버(`zipkin-spring:9411`)로 비동기 전송

게이트웨이의 라우팅 규칙에 따라 추적되는 서비스 간 호출 경로 (`services-spring/gateway-service/src/main/resources/application.yml:10-75`):

| 요청 경로 패턴 | 라우팅 대상 서비스 |
|-------------|----------------|
| `/api/v1/auth/**` | auth-service |
| `/api/v1/tickets/**`, `/api/v1/seats/**`, `/api/v1/reservations/**` | ticket-service |
| `/api/v1/payments/**` | payment-service |
| `/api/v1/stats/**` | stats-service |
| `/api/v1/queue/**` | queue-service |
| `/api/v1/events/**`, `/api/v1/admin/**`, `/api/v1/artists/**` | catalog-service |
| `/api/v1/community/**`, `/api/v1/news/**` | community-service |

---

## 6. 헬스체크 & 프로브

### 6.1 Kubernetes 프로브 개요

모든 백엔드 서비스는 동일한 프로브 패턴을 따른다. Readiness Probe와 Liveness Probe 모두 `/health` 경로를 사용한다.

#### Readiness Probe (준비 상태 확인)

```yaml
readinessProbe:
  httpGet:
    path: /health
    port: <서비스포트>
  initialDelaySeconds: 10
  periodSeconds: 10
```

| 설정 항목 | 값 | 설명 |
|---------|-----|------|
| 경로 | `/health` | Actuator 헬스 엔드포인트 |
| 초기 대기 시간 | 10초 | 컨테이너 시작 후 첫 검사까지 대기 |
| 검사 주기 | 10초 | 이후 10초마다 반복 검사 |
| 성공 의미 | 트래픽 수신 가능 | Service 엔드포인트에 Pod 등록 |

서비스별 Readiness Probe 설정 위치:

| 서비스 | deployment.yaml 라인 | 포트 |
|-------|---------------------|------|
| ticket-service | `k8s/spring/base/ticket-service/deployment.yaml:39-44` | 3002 |
| gateway-service | `k8s/spring/base/gateway-service/deployment.yaml:39-44` | 3001 |
| auth-service | `k8s/spring/base/auth-service/deployment.yaml:39-44` | 3005 |
| payment-service | `k8s/spring/base/payment-service/deployment.yaml:39-44` | 3003 |
| stats-service | `k8s/spring/base/stats-service/deployment.yaml:39-44` | 3004 |
| queue-service | `k8s/spring/base/queue-service/deployment.yaml:39-44` | 3007 |
| community-service | `k8s/spring/base/community-service/deployment.yaml:39-44` | 3008 |
| catalog-service | `k8s/spring/base/catalog-service/deployment.yaml:39-44` | 3009 |

#### Liveness Probe (생존 상태 확인)

```yaml
livenessProbe:
  httpGet:
    path: /health
    port: <서비스포트>
  initialDelaySeconds: 20
  periodSeconds: 20
```

| 설정 항목 | 값 | 설명 |
|---------|-----|------|
| 경로 | `/health` | Actuator 헬스 엔드포인트 |
| 초기 대기 시간 | 20초 | 컨테이너 시작 후 첫 검사까지 대기 |
| 검사 주기 | 20초 | 이후 20초마다 반복 검사 |
| 실패 시 동작 | Pod 재시작 | kubelet이 컨테이너를 강제 종료 후 재시작 |

서비스별 Liveness Probe 설정 위치:

| 서비스 | deployment.yaml 라인 | 포트 |
|-------|---------------------|------|
| ticket-service | `k8s/spring/base/ticket-service/deployment.yaml:45-50` | 3002 |
| gateway-service | `k8s/spring/base/gateway-service/deployment.yaml:45-50` | 3001 |
| auth-service | `k8s/spring/base/auth-service/deployment.yaml:45-50` | 3005 |
| payment-service | `k8s/spring/base/payment-service/deployment.yaml:45-50` | 3003 |
| stats-service | `k8s/spring/base/stats-service/deployment.yaml:45-50` | 3004 |
| queue-service | `k8s/spring/base/queue-service/deployment.yaml:45-50` | 3007 |
| community-service | `k8s/spring/base/community-service/deployment.yaml:45-50` | 3008 |
| catalog-service | `k8s/spring/base/catalog-service/deployment.yaml:45-50` | 3009 |

### 6.2 프로브 타이밍 다이어그램

```
컨테이너 시작
    |
    |--- 10초 대기 ----> [Readiness 첫 검사]
    |                         |
    |                    (성공) 트래픽 수신 시작
    |                         |
    |--- 20초 대기 ----> [Liveness 첫 검사]
    |                         |
    |                    이후 매 10초마다 Readiness 검사
    |                    이후 매 20초마다 Liveness 검사
    |
    v 시간 진행 -->
```

### 6.3 Actuator 헬스체크 상세

#### 공통 헬스체크 설정

모든 서비스가 동일한 Actuator 헬스 설정을 사용한다.

```yaml
management:
  endpoint:
    health:
      show-details: always
      show-components: always
```

| 설정 | 값 | 설명 |
|-----|-----|------|
| `show-details` | `always` | 모든 헬스 인디케이터의 상세 정보 표시 |
| `show-components` | `always` | 각 구성요소(DB, Redis 등)의 개별 상태 표시 |

> 대표 소스: `services-spring/ticket-service/src/main/resources/application.yml:43-46`

#### 서비스별 헬스 인디케이터

각 서비스가 활성화한 헬스 인디케이터는 사용하는 인프라에 따라 다르다.

| 서비스 | DB 헬스 | Redis 헬스 | 소스 참조 |
|-------|---------|-----------|----------|
| ticket-service | `db.enabled: true` | `redis.enabled: true` | `application.yml:48-51` |
| gateway-service | - | `redis.enabled: true` | `application.yml:88-89` |
| auth-service | `db.enabled: true` | - | `application.yml:30-31` |
| payment-service | `db.enabled: true` | - | `application.yml:36-37` |
| stats-service | `db.enabled: true` | - | `application.yml:38-39` |
| queue-service | - | `redis.enabled: true` | `application.yml:20-21` |
| community-service | `db.enabled: true` | - | `application.yml:23-24` |
| catalog-service | `db.enabled: true` | - | `application.yml:22-23` |

> 참고: 경로는 모두 `services-spring/{서비스명}/src/main/resources/application.yml` 기준이다.

#### 헬스 응답 예시

`/health` 또는 `/actuator/health` 요청 시 다음과 같은 형태의 JSON 응답이 반환된다.

```json
{
  "status": "UP",
  "components": {
    "db": {
      "status": "UP",
      "details": {
        "database": "PostgreSQL",
        "validationQuery": "isValid()"
      }
    },
    "redis": {
      "status": "UP",
      "details": {
        "version": "7.x"
      }
    },
    "diskSpace": {
      "status": "UP",
      "details": {
        "total": ...,
        "free": ...,
        "threshold": ...
      }
    },
    "ping": {
      "status": "UP"
    }
  }
}
```

전체 `status`가 `DOWN`이 되면 HTTP 503 응답을 반환하고, 이에 따라 Readiness Probe 실패 시 트래픽 차단, Liveness Probe 실패 시 Pod 재시작이 발생한다.

---

## 7. 회복성 (Resilience4j)

서비스 간 내부 통신에 대해 Resilience4j를 통한 Circuit Breaker와 Retry 패턴이 적용되어 있다. 이 설정은 `internalService`라는 이름의 인스턴스로 정의된다.

### 7.1 Resilience4j 적용 서비스

| 서비스 | Circuit Breaker | Retry | Resilience4j 의존성 | 소스 참조 |
|-------|----------------|-------|-------------------|----------|
| ticket-service | `internalService` | `internalService` | `build.gradle:28` | `application.yml:87-105` |
| payment-service | `internalService` | `internalService` | `build.gradle:27` | `application.yml:53-71` |
| queue-service | `internalService`, `redisQueue` | `internalService` | `build.gradle:26` | `application.yml:58-83` |
| community-service | `internalService` | `internalService` | `build.gradle:26` | `application.yml:44-62` |
| catalog-service | `internalService` | `internalService` | `build.gradle:27` | `application.yml:49-67` |

> 참고: 경로는 모두 `services-spring/{서비스명}/` 기준이다. auth-service, gateway-service, stats-service에는 Resilience4j 설정이 없다.

### 7.2 Circuit Breaker 설정 (internalService)

```yaml
resilience4j:
  circuitbreaker:
    instances:
      internalService:
        sliding-window-size: 10
        failure-rate-threshold: 50
        wait-duration-in-open-state: 10s
        permitted-number-of-calls-in-half-open-state: 3
        slow-call-duration-threshold: 3s
        slow-call-rate-threshold: 80
```
> 대표 소스: `services-spring/ticket-service/src/main/resources/application.yml:87-96`

| 설정 항목 | 값 | 설명 |
|---------|-----|------|
| `sliding-window-size` | 10 | 슬라이딩 윈도우 크기 (최근 10개 요청 기준으로 판단) |
| `failure-rate-threshold` | 50% | 실패율이 50%를 넘으면 서킷 오픈 |
| `wait-duration-in-open-state` | 10초 | 서킷 오픈 후 half-open 전환까지 대기 시간 |
| `permitted-number-of-calls-in-half-open-state` | 3 | half-open 상태에서 허용하는 테스트 호출 수 |
| `slow-call-duration-threshold` | 3초 | 이 시간을 초과하면 "느린 호출"로 분류 |
| `slow-call-rate-threshold` | 80% | 느린 호출 비율이 80%를 넘으면 서킷 오픈 |

#### Circuit Breaker 상태 전이

```
[CLOSED] ----실패율 >= 50% 또는 느린호출률 >= 80%----> [OPEN]
                                                       |
                                                  10초 대기
                                                       |
                                                       v
                                                  [HALF-OPEN]
                                                  (3건 테스트)
                                                    /      \
                                              성공률 충분    실패 지속
                                                  |          |
                                                  v          v
                                              [CLOSED]    [OPEN]
```

### 7.3 Circuit Breaker 설정 (redisQueue) -- queue-service 전용

queue-service는 Redis 큐 연산에 대해 별도의 Circuit Breaker 인스턴스를 추가로 운영한다.

```yaml
redisQueue:
  sliding-window-size: 10
  failure-rate-threshold: 50
  wait-duration-in-open-state: 30s
  permitted-number-of-calls-in-half-open-state: 3
  record-exceptions:
    - org.springframework.data.redis.RedisConnectionFailureException
```
> 소스: `services-spring/queue-service/src/main/resources/application.yml:68-74`

| 설정 항목 | internalService | redisQueue | 차이점 |
|---------|----------------|------------|-------|
| `wait-duration-in-open-state` | 10초 | **30초** | Redis 장애 시 더 긴 대기 |
| `record-exceptions` | (기본값) | `RedisConnectionFailureException` | Redis 연결 실패만 기록 |

### 7.4 Retry 설정

```yaml
resilience4j:
  retry:
    instances:
      internalService:
        max-attempts: 3
        wait-duration: 500ms
        exponential-backoff-multiplier: 2
        retry-exceptions:
          - org.springframework.web.client.ResourceAccessException
          - java.net.ConnectException
```
> 대표 소스: `services-spring/ticket-service/src/main/resources/application.yml:97-105`

| 설정 항목 | 값 | 설명 |
|---------|-----|------|
| `max-attempts` | 3 | 최대 3회 시도 (최초 1회 + 재시도 2회) |
| `wait-duration` | 500ms | 첫 재시도까지 대기 시간 |
| `exponential-backoff-multiplier` | 2 | 지수 백오프 배수 |
| `retry-exceptions` | `ResourceAccessException`, `ConnectException` | 재시도 대상 예외 |

#### 재시도 타이밍

```
1차 시도 --실패--> [500ms 대기] --> 2차 시도 --실패--> [1000ms 대기] --> 3차 시도
                                                    (500ms * 2^1)
```

- 1차 재시도: 500ms 후
- 2차 재시도: 1000ms 후 (500ms * 2)
- 총 대기 시간: 최대 1500ms

#### 재시도 대상 예외

| 예외 클래스 | 발생 상황 |
|-----------|---------|
| `ResourceAccessException` | RestTemplate/WebClient 호출 시 I/O 오류 (타임아웃, 연결 거부 등) |
| `ConnectException` | TCP 연결 수립 실패 (서비스 다운, 네트워크 단절) |

이 두 예외는 일시적 네트워크 문제로 인한 것이므로 재시도를 통해 복구 가능성이 있다. 4xx/5xx HTTP 응답에 대해서는 재시도하지 않는다.

### 7.5 Resilience4j 적용 서비스별 설정 소스 참조

| 서비스 | Circuit Breaker 위치 | Retry 위치 |
|-------|---------------------|-----------|
| ticket-service | `application.yml:87-96` | `application.yml:97-105` |
| payment-service | `application.yml:53-62` | `application.yml:63-71` |
| queue-service (internalService) | `application.yml:58-67` | `application.yml:75-83` |
| queue-service (redisQueue) | `application.yml:68-74` | - |
| community-service | `application.yml:44-53` | `application.yml:54-62` |
| catalog-service | `application.yml:49-58` | `application.yml:59-67` |

> 참고: 경로는 모두 `services-spring/{서비스명}/src/main/resources/application.yml` 기준이다.

---

## 8. 알림 (Grafana Alert Rules)

### 8.1 현재 구성 상태

이 프로젝트는 **Prometheus AlertManager를 별도 배포하지 않는다**. 대신, Grafana 대시보드 내장 알림(Grafana Alerting)을 사용한다. `grafana-dashboards` ConfigMap의 `alerts.json` 대시보드에 네 가지 알림 규칙이 패널별로 정의되어 있다.

> 소스: `k8s/spring/overlays/kind/grafana-dashboards.yaml:1003-1219`

### 8.2 정의된 알림 규칙

#### 8.2.1 High Error Rate (높은 에러율)

```
PromQL: sum(rate(http_server_requests_seconds_count{status=~"5.."}[5m])) by (application)
        / sum(rate(http_server_requests_seconds_count[5m])) by (application) * 100
```
> 소스: `k8s/spring/overlays/kind/grafana-dashboards.yaml:1036-1038`

| 항목 | 값 | 소스 참조 |
|-----|-----|----------|
| 이름 | `High Error Rate` | `grafana-dashboards.yaml:1055` |
| 조건 | 5xx 에러율 > 5% (5분 평균) | `grafana-dashboards.yaml:1062` |
| 평가 주기 | 1분 | `grafana-dashboards.yaml:1065` |
| 발동 지속 시간 | 5분 | `grafana-dashboards.yaml:1066` |
| 데이터 없음 시 | `no_data` | `grafana-dashboards.yaml:1067` |
| 메시지 | "Service {{ application }} error rate exceeds 5% for more than 5 minutes." | `grafana-dashboards.yaml:1056` |

#### 8.2.2 Service Down (서비스 다운)

```
PromQL: up{job="spring-services"}
```
> 소스: `k8s/spring/overlays/kind/grafana-dashboards.yaml:1079`

| 항목 | 값 | 소스 참조 |
|-----|-----|----------|
| 이름 | `Service Down` | `grafana-dashboards.yaml:1101` |
| 조건 | `up` 메트릭 < 1 (마지막 값) | `grafana-dashboards.yaml:1108` |
| 평가 주기 | 30초 | `grafana-dashboards.yaml:1111` |
| 발동 지속 시간 | 1분 | `grafana-dashboards.yaml:1112` |
| 데이터 없음 시 | `alerting` (알림 발동) | `grafana-dashboards.yaml:1113` |
| 메시지 | "Service {{ service }} has been down for more than 1 minute." | `grafana-dashboards.yaml:1102` |

#### 8.2.3 High Latency (높은 지연 시간)

```
PromQL: histogram_quantile(0.95, sum(rate(http_server_requests_seconds_bucket[5m])) by (le, application))
```
> 소스: `k8s/spring/overlays/kind/grafana-dashboards.yaml:1125`

| 항목 | 값 | 소스 참조 |
|-----|-----|----------|
| 이름 | `High Latency` | `grafana-dashboards.yaml:1145` |
| 조건 | p95 응답 시간 > 3초 (5분 평균) | `grafana-dashboards.yaml:1152` |
| 평가 주기 | 1분 | `grafana-dashboards.yaml:1155` |
| 발동 지속 시간 | 5분 | `grafana-dashboards.yaml:1156` |
| 데이터 없음 시 | `no_data` | `grafana-dashboards.yaml:1157` |
| 메시지 | "Service {{ application }} p95 latency exceeds 3 seconds for more than 5 minutes." | `grafana-dashboards.yaml:1146` |

#### 8.2.4 Low Disk Space (디스크 부족)

```
PromQL: disk_free_bytes / disk_total_bytes * 100
```
> 소스: `k8s/spring/overlays/kind/grafana-dashboards.yaml:1169`

| 항목 | 값 | 소스 참조 |
|-----|-----|----------|
| 이름 | `Low Disk Space` | `grafana-dashboards.yaml:1191` |
| 조건 | 여유 디스크 < 10% (마지막 값) | `grafana-dashboards.yaml:1198` |
| 평가 주기 | 1분 | `grafana-dashboards.yaml:1201` |
| 발동 지속 시간 | 5분 | `grafana-dashboards.yaml:1202` |
| 데이터 없음 시 | `no_data` | `grafana-dashboards.yaml:1203` |
| 메시지 | "Service {{ application }} disk space is below 10% free." | `grafana-dashboards.yaml:1192` |

### 8.3 알림 규칙 요약 테이블

> 소스: `k8s/spring/overlays/kind/grafana-dashboards.yaml:1214` (대시보드 내 Alert Summary 패널)

| 알림 | 조건 | 지속 시간 | 심각도 |
|------|------|---------|-------|
| High Error Rate | 에러율 > 5% | 5분 | Critical |
| Service Down | `up` == 0 | 1분 | Critical |
| High Latency | p95 > 3초 | 5분 | Warning |
| Low Disk Space | 여유 공간 < 10% | 5분 | Warning |

### 8.4 미구성 항목 및 권장 사항

현재 구성에서 다음 항목이 누락되어 있다.

#### 누락된 인프라 구성

| 항목 | 현황 | 권장 사항 |
|-----|------|---------|
| Prometheus AlertManager | 미배포 | 별도 AlertManager 배포로 Slack/PagerDuty 등 알림 채널 연동 |
| PrometheusRule CRD | 미사용 | Prometheus Operator 도입 시 PrometheusRule로 알림 규칙 관리 |
| Grafana 알림 채널 | 미설정 | 알림 수신 채널(이메일, Slack, Webhook 등) 설정 필요 |

#### 권장 추가 알림 규칙

| 알림 규칙 | PromQL 예시 | 임계값 |
|---------|-----------|-------|
| Circuit Breaker 트립 | `resilience4j_circuitbreaker_state{state="open"} == 1` | 서킷 오픈 상태 지속 |
| Pod 재시작 루프 | `increase(kube_pod_container_status_restarts_total[1h]) > 3` | 1시간 내 3회 초과 재시작 |
| JVM 메모리 과다 | `jvm_memory_used_bytes{area="heap"} / jvm_memory_max_bytes{area="heap"} > 0.9` | 힙 사용률 90% 초과 |
| Kafka consumer lag 증가 | `kafka_consumer_records_lag_max > 1000` | 최대 lag 1000건 초과 |
| DB 커넥션 풀 고갈 | `hikaricp_connections_active / hikaricp_connections_max > 0.85` | 풀 사용률 85% 초과 |
| Redis 연결 실패 | `increase(lettuce_command_completion_seconds_count{status="ERROR"}[5m]) > 0` | 5분 내 에러 발생 |

---

## 부록: 파일 참조 인덱스

본 문서에서 참조한 모든 소스 파일을 정리한다. 모든 경로는 프로젝트 루트(`C:\Users\USER\project-ticketing-copy`) 기준 상대 경로이다.

### Kubernetes 매니페스트

| 파일 | 주요 내용 |
|------|---------|
| `k8s/spring/overlays/kind/prometheus.yaml` | Prometheus ConfigMap, Deployment, Service |
| `k8s/spring/overlays/kind/grafana.yaml` | Grafana 데이터소스, 프로비저닝, Deployment, Service |
| `k8s/spring/overlays/kind/grafana-dashboards.yaml` | 대시보드 JSON (4개 대시보드 + 알림 규칙) |
| `k8s/spring/overlays/kind/loki.yaml` | Loki ConfigMap, Deployment, Service |
| `k8s/spring/overlays/kind/promtail.yaml` | Promtail ConfigMap, DaemonSet, RBAC |
| `k8s/spring/overlays/kind/zipkin.yaml` | Zipkin Deployment, Service |
| `k8s/spring/overlays/kind/pvc.yaml` | PVC (Prometheus, Grafana, Loki) |
| `k8s/spring/base/{서비스명}/deployment.yaml` | 서비스 Deployment (프로브, 리소스) |

### 서비스 설정

| 파일 | 주요 내용 |
|------|---------|
| `services-spring/{서비스명}/src/main/resources/application.yml` | Actuator, 추적, 로깅, Resilience4j 설정 |
| `services-spring/{서비스명}/build.gradle` | Micrometer, Zipkin, Resilience4j 의존성 |
