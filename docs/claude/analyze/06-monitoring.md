# URR 모니터링 및 옵저버빌리티 분석

## 1. 개요

URR 시스템은 4계층 옵저버빌리티 스택을 채택하고 있다.

| 계층 | 기술 스택 | 역할 |
|------|----------|------|
| 메트릭 | Micrometer + Prometheus + Grafana | 시계열 메트릭 수집/시각화 |
| 분산 추적 | Brave + Zipkin | 서비스 간 요청 추적 |
| 로깅 | SLF4J + Promtail + Loki + Grafana | 구조화 로그 수집/검색 |
| 알림 | Grafana Alerts (K8s) + CloudWatch Alarms (AWS) | 이상 감지/통보 |

---

## 2. 메트릭 수집 (Micrometer + Prometheus)

### 2.1 의존성 구성

전체 8개 서비스가 동일한 Micrometer 의존성을 사용한다.

| 서비스 | build.gradle 라인 | 의존성 |
|--------|-------------------|--------|
| auth-service | `build.gradle:31` | `io.micrometer:micrometer-registry-prometheus` |
| gateway-service | `build.gradle:28` | `io.micrometer:micrometer-registry-prometheus` |
| ticket-service | `build.gradle:32` | `io.micrometer:micrometer-registry-prometheus` |
| payment-service | `build.gradle:31` | `io.micrometer:micrometer-registry-prometheus` |
| stats-service | `build.gradle:29` | `io.micrometer:micrometer-registry-prometheus` |
| queue-service | `build.gradle:31` | `io.micrometer:micrometer-registry-prometheus` |
| catalog-service | `build.gradle:31` | `io.micrometer:micrometer-registry-prometheus` |
| community-service | `build.gradle:31` | `io.micrometer:micrometer-registry-prometheus` |

**파일 경로**: `C:\Users\USER\URR\services-spring\{service-name}\build.gradle`

### 2.2 Actuator 엔드포인트 노출

모든 서비스가 동일한 management 설정을 사용한다.

```yaml
# 모든 서비스의 application.yml (공통 패턴)
management:
  server:
    port: ${MANAGEMENT_PORT:}     # 별도 관리 포트 (선택)
  endpoint:
    health:
      show-details: always
      show-components: always
  endpoints:
    web:
      exposure:
        include: health,info,prometheus  # 3개 엔드포인트만 노출
```

**파일 경로**: `C:\Users\USER\URR\services-spring\auth-service\src\main\resources\application.yml:22-35`
(모든 서비스 동일 패턴)

노출 엔드포인트:
- `/actuator/health` -- 헬스체크 (상세 정보 포함)
- `/actuator/info` -- 서비스 정보
- `/actuator/prometheus` -- Prometheus 스크래핑 대상

### 2.3 Prometheus 스크래핑 구성

```yaml
# prometheus.yaml (ConfigMap)
scrape_configs:
  - job_name: 'spring-services'
    metrics_path: '/actuator/prometheus'
    scrape_interval: 10s
    static_configs:
      - targets: ['auth-service:3005']
      - targets: ['gateway-service:3001']
      - targets: ['ticket-service:3002']
      - targets: ['payment-service:3003']
      - targets: ['stats-service:3004']
      - targets: ['queue-service:3007']
      - targets: ['community-service:3008']
      - targets: ['catalog-service:3009']
```

**파일 경로**: `C:\Users\USER\URR\k8s\spring\overlays\kind\prometheus.yaml:1-47`

| 설정 항목 | 값 | 비고 |
|-----------|-----|------|
| 글로벌 스크래핑 간격 | 15초 | `global.scrape_interval` |
| Spring 서비스 스크래핑 간격 | 10초 | `scrape_configs[0].scrape_interval` |
| 데이터 보존 기간 | 7일 | `--storage.tsdb.retention.time=7d` |
| Prometheus 버전 | v2.51.0 | `prometheus.yaml:68` |
| 스토리지 | PVC (prometheus-pvc) | 영구 저장소 |

### 2.4 자동 수집 메트릭 (Spring Boot Actuator 기본)

Spring Boot 3.5.0 + Micrometer가 자동으로 노출하는 주요 메트릭:

| 카테고리 | 메트릭 이름 | 설명 |
|----------|------------|------|
| HTTP | `http_server_requests_seconds_count` | 요청 수 |
| HTTP | `http_server_requests_seconds_bucket` | 응답 시간 히스토그램 |
| JVM | `jvm_memory_used_bytes` | JVM 메모리 사용량 |
| JVM | `jvm_memory_max_bytes` | JVM 최대 메모리 |
| JVM | `jvm_gc_pause_seconds_sum/count` | GC 일시 중지 |
| JVM | `jvm_threads_live_threads` | 라이브 스레드 수 |
| JVM | `jvm_classes_loaded_classes` | 로드된 클래스 수 |
| 프로세스 | `process_cpu_usage` | 프로세스 CPU |
| 프로세스 | `process_uptime_seconds` | 업타임 |
| Tomcat | `tomcat_connections_current_connections` | 현재 연결 수 |
| Tomcat | `tomcat_threads_busy_threads` | 바쁜 스레드 수 |
| HikariCP | `hikaricp_connections_active/idle/max` | DB 커넥션 풀 |
| HikariCP | `hikaricp_connections_acquire_seconds` | 커넥션 획득 시간 |
| HikariCP | `hikaricp_connections_pending` | 대기 중인 커넥션 요청 |
| Redis (Lettuce) | `lettuce_command_completion_seconds` | Redis 명령 지연 시간 |
| Kafka Consumer | `kafka_consumer_records_lag` | 컨슈머 랙 |
| Kafka Consumer | `kafka_consumer_fetch_manager_records_consumed_total` | 소비된 메시지 수 |
| Kafka Producer | `kafka_producer_record_send_total` | 전송된 메시지 수 |
| Kafka Producer | `kafka_producer_record_error_total` | 전송 실패 수 |
| 디스크 | `disk_free_bytes` / `disk_total_bytes` | 디스크 공간 |

### 2.5 커스텀 비즈니스 메트릭

#### ticket-service: BusinessMetrics

**파일 경로**: `C:\Users\USER\URR\services-spring\ticket-service\src\main\java\guru\urr\ticketservice\shared\metrics\BusinessMetrics.java`

```java
@Component
public class BusinessMetrics {
    // Counter 메트릭
    private final Counter reservationCreated;    // business.reservation.created.total
    private final Counter reservationConfirmed;  // business.reservation.confirmed.total
    private final Counter reservationCancelled;  // business.reservation.cancelled.total
    private final Counter reservationExpired;    // business.reservation.expired.total
    private final Counter paymentProcessed;      // business.payment.processed.total
    private final Counter paymentFailed;         // business.payment.failed.total
    private final Counter transferCompleted;     // business.transfer.completed.total
    private final Counter membershipActivated;   // business.membership.activated.total

    // Timer 메트릭
    private final Timer reservationCreateTimer;  // business.reservation.create.duration
}
```

| 메트릭 이름 | 타입 | 설명 | 파일:라인 |
|------------|------|------|-----------|
| `business.reservation.created.total` | Counter | 생성된 예약 수 | `BusinessMetrics.java:22` |
| `business.reservation.confirmed.total` | Counter | 결제 완료된 예약 수 | `BusinessMetrics.java:25` |
| `business.reservation.cancelled.total` | Counter | 취소된 예약 수 | `BusinessMetrics.java:28` |
| `business.reservation.expired.total` | Counter | 만료된 예약 수 | `BusinessMetrics.java:31` |
| `business.payment.processed.total` | Counter | 성공한 결제 수 | `BusinessMetrics.java:34` |
| `business.payment.failed.total` | Counter | 실패한 결제 수 | `BusinessMetrics.java:37` |
| `business.transfer.completed.total` | Counter | 완료된 양도 수 | `BusinessMetrics.java:40` |
| `business.membership.activated.total` | Counter | 활성화된 멤버십 수 | `BusinessMetrics.java:43` |
| `business.reservation.create.duration` | Timer | 예약 생성 소요 시간 | `BusinessMetrics.java:46` |

#### queue-service: QueueMetrics

**파일 경로**: `C:\Users\USER\URR\services-spring\queue-service\src\main\java\guru\urr\queueservice\shared\metrics\QueueMetrics.java`

| 메트릭 이름 | 타입 | 설명 | 파일:라인 |
|------------|------|------|-----------|
| `business.queue.joined.total` | Counter | 대기열에 참가한 사용자 수 | `QueueMetrics.java:15` |
| `business.queue.admitted.total` | Counter | 대기열에서 입장 허가된 사용자 수 | `QueueMetrics.java:18` |
| `business.queue.left.total` | Counter | 대기열을 떠난 사용자 수 | `QueueMetrics.java:21` |

---

## 3. 분산 추적 (Distributed Tracing)

### 3.1 의존성 구성

모든 8개 서비스가 Brave 기반 분산 추적을 사용한다.

| 라이브러리 | 역할 |
|-----------|------|
| `io.micrometer:micrometer-tracing-bridge-brave` | Micrometer-Brave 브릿지 |
| `io.zipkin.reporter2:zipkin-reporter-brave` | Zipkin 리포터 |

**파일 경로 예시**: `C:\Users\USER\URR\services-spring\auth-service\build.gradle:32-33`
(모든 서비스 동일)

### 3.2 추적 구성

```yaml
# 모든 서비스의 application.yml
management:
  tracing:
    sampling:
      probability: ${TRACING_SAMPLING_PROBABILITY:1.0}  # 기본 100% 샘플링
  zipkin:
    tracing:
      endpoint: ${ZIPKIN_ENDPOINT:http://localhost:9411/api/v2/spans}
```

**파일 경로 예시**: `C:\Users\USER\URR\services-spring\auth-service\src\main\resources\application.yml:36-41`

| 설정 항목 | 기본값 | 비고 |
|-----------|--------|------|
| 샘플링 확률 | 1.0 (100%) | 환경 변수로 조절 가능 |
| Zipkin 엔드포인트 | `http://localhost:9411/api/v2/spans` | 환경 변수로 오버라이드 |

### 3.3 Zipkin 배포 구성

**파일 경로**: `C:\Users\USER\URR\k8s\spring\overlays\kind\zipkin.yaml`

| 설정 항목 | 값 |
|-----------|-----|
| 이미지 | `openzipkin/zipkin:3` |
| 서비스 포트 | 9411 |
| NodePort | 30411 |
| 리소스 요청 | CPU 100m, Memory 256Mi |
| 리소스 제한 | CPU 500m, Memory 512Mi |
| 스토리지 | 인메모리 (기본) |

### 3.4 추적 흐름 다이어그램

```
브라우저 요청
    |
    v
[Gateway Service :3001]  -- traceId/spanId 생성 --
    |
    |-- Spring Cloud Gateway MVC 라우팅 -->
    |
    v
[Backend Service]  -- 동일 traceId, 새 spanId --
    |
    |-- RestTemplate/WebClient (서비스 간 호출) -->
    |
    v
[다른 Backend Service]  -- 동일 traceId, 새 spanId --
    |
    +--> Kafka Producer (traceId 전파)
           |
           v
         [Consumer Service]  -- Kafka 헤더에서 traceId 복원 --
```

서비스 간 추적 전파 방식:
- HTTP: Brave의 `TracingFilter`가 B3 전파 헤더 자동 주입/추출
- Kafka: `spring-kafka`의 자동 계측으로 레코드 헤더에 traceId/spanId 전파
- Gateway: Spring Cloud Gateway MVC의 프록시 요청에 traceId 자동 전달

---

## 4. 로깅

### 4.1 로그 패턴

모든 서비스가 동일한 구조화 로그 패턴을 사용한다.

```yaml
# 모든 서비스의 application.yml
logging:
  pattern:
    level: "%5p [${spring.application.name:},%X{traceId:-},%X{spanId:-}]"
```

**파일 경로 예시**: `C:\Users\USER\URR\services-spring\auth-service\src\main\resources\application.yml:43-45`

출력 형식:
```
 INFO [auth-service,6f23a4b8c91d2e3f,a1b2c3d4e5f60718] ...
 ^^^^  ^^^^^^^^^^^^^  ^^^^^^^^^^^^^^^^  ^^^^^^^^^^^^^^^^
 레벨  서비스이름      traceId           spanId
```

이 패턴을 통해:
- **서비스 식별**: 어떤 서비스에서 발생한 로그인지 식별
- **요청 추적**: 동일한 traceId로 서비스 간 요청을 연결
- **스팬 식별**: spanId로 특정 서비스 내의 처리 단계를 식별

### 4.2 로그 수집 파이프라인 (Promtail + Loki)

```
[Pod stdout/stderr]
      |
      v
[Promtail DaemonSet]  -- 각 노드에서 실행 --
      |
      | HTTP push
      v
[Loki :3100]  -- 로그 저장소 --
      |
      v
[Grafana]  -- Loki 데이터소스로 로그 검색/시각화 --
```

#### Promtail 구성

**파일 경로**: `C:\Users\USER\URR\k8s\spring\overlays\kind\promtail.yaml`

```yaml
scrape_configs:
  - job_name: kubernetes-pods
    kubernetes_sd_configs:
      - role: pod
        namespaces:
          names:
            - urr-spring        # URR 네임스페이스만 대상
    relabel_configs:
      - source_labels: [__meta_kubernetes_pod_label_app]
        target_label: app       # app 라벨로 서비스 식별
      - source_labels: [__meta_kubernetes_pod_name]
        target_label: pod       # 파드 이름
      - source_labels: [__meta_kubernetes_namespace]
        target_label: namespace # 네임스페이스
```

| 설정 항목 | 값 |
|-----------|-----|
| 배포 방식 | DaemonSet (모든 노드에 1개씩) |
| 이미지 | `grafana/promtail:2.9.3` |
| 수집 대상 | `/var/log`, `/var/lib/docker/containers` |
| 라벨 | `app`, `pod`, `namespace` |
| 리소스 요청 | CPU 50m, Memory 128Mi |
| 리소스 제한 | CPU 200m, Memory 256Mi |
| RBAC | ClusterRole로 pods/services/endpoints 읽기 권한 |

#### Loki 구성

**파일 경로**: `C:\Users\USER\URR\k8s\spring\overlays\kind\loki.yaml`

| 설정 항목 | 값 |
|-----------|-----|
| 이미지 | `grafana/loki:2.9.3` |
| HTTP 포트 | 3100 |
| gRPC 포트 | 9096 |
| 스토리지 | Filesystem (PVC: loki-pvc) |
| 스키마 | boltdb-shipper, v11 |
| 인덱스 주기 | 24시간 |
| 캐시 TTL | 24시간 |
| 복제 계수 | 1 (단일 인스턴스) |
| 인증 | 비활성화 (`auth_enabled: false`) |

---

## 5. 헬스체크

### 5.1 커스텀 /health 엔드포인트

모든 서비스는 `OpsController`에서 `/health` 엔드포인트를 제공한다. 이 엔드포인트는 Actuator의 `/actuator/health`와 별도로, K8s 프로브에서 사용되는 간이 헬스체크이다.

**파일 경로 (gateway-service 예시)**: `C:\Users\USER\URR\services-spring\gateway-service\src\main\java\guru\urr\gatewayservice\controller\OpsController.java`

```java
@GetMapping("/health")
public ResponseEntity<Map<String, Object>> health() {
    // Redis ping 확인
    // 성공 시 200 + {"status": "ok"}
    // 실패 시 503 + {"status": "degraded"}
}
```

서비스별 헬스체크 대상:

| 서비스 | 의존성 확인 | Actuator health 구성요소 |
|--------|------------|-------------------------|
| auth-service | PostgreSQL | `health.db.enabled: true` |
| gateway-service | Redis | `health.redis.enabled: true` |
| ticket-service | PostgreSQL, Redis | `health.db.enabled: true`, `health.redis.enabled: true` |
| payment-service | PostgreSQL | `health.db.enabled: true` |
| stats-service | PostgreSQL | `health.db.enabled: true` |
| queue-service | Redis | `health.redis.enabled: true` |
| catalog-service | PostgreSQL | `health.db.enabled: true` |
| community-service | PostgreSQL | `health.db.enabled: true` |

### 5.2 K8s Liveness / Readiness 프로브

모든 서비스는 동일한 프로브 패턴을 사용한다.

**파일 경로 예시**: `C:\Users\USER\URR\k8s\spring\base\auth-service\deployment.yaml:39-50`

```yaml
readinessProbe:
  httpGet:
    path: /health
    port: 3005        # 서비스 포트
  initialDelaySeconds: 10
  periodSeconds: 10
livenessProbe:
  httpGet:
    path: /health
    port: 3005
  initialDelaySeconds: 20
  periodSeconds: 20
```

| 서비스 | 포트 | readiness 시작 | readiness 주기 | liveness 시작 | liveness 주기 |
|--------|------|----------------|----------------|---------------|---------------|
| auth-service | 3005 | 10초 | 10초 | 20초 | 20초 |
| gateway-service | 3001 | 10초 | 10초 | 20초 | 20초 |
| ticket-service | 3002 | 10초 | 10초 | 20초 | 20초 |
| payment-service | 3003 | 10초 | 10초 | 20초 | 20초 |
| stats-service | 3004 | 10초 | 10초 | 20초 | 20초 |
| queue-service | 3007 | 10초 | 10초 | 20초 | 20초 |
| catalog-service | 3009 | 10초 | 10초 | 20초 | 20초 |
| community-service | 3008 | 10초 | 10초 | 20초 | 20초 |

프로브 경로는 `/health`로 설정되어 있으나, 이것은 Actuator의 `/actuator/health`가 아닌 커스텀 `OpsController`의 엔드포인트이다. Actuator 헬스체크가 더 세밀한 의존성 확인을 제공하지만, 프로브에서는 간이 엔드포인트를 사용한다.

### 5.3 인프라 컴포넌트 프로브

| 컴포넌트 | Liveness Path | Readiness Path | 시작 지연 |
|----------|---------------|----------------|-----------|
| Prometheus | `/-/healthy:9090` | `/-/ready:9090` | 15초/5초 |
| Grafana | `/api/health:3006` | `/api/health:3006` | 30초/10초 |
| Loki | `/ready:3100` | `/ready:3100` | 45초/30초 |

**파일 경로**: `C:\Users\USER\URR\k8s\spring\overlays\kind\prometheus.yaml:87-98`, `grafana.yaml:84-95`, `loki.yaml:76-87`

---

## 6. 대시보드 (Grafana)

### 6.1 Grafana 배포 구성

**파일 경로**: `C:\Users\USER\URR\k8s\spring\overlays\kind\grafana.yaml`

| 설정 항목 | 값 |
|-----------|-----|
| 이미지 | `grafana/grafana:10.2.3` |
| 서비스 포트 | 3006 |
| NodePort | 30006 |
| 관리자 계정 | `admin:admin` |
| 회원가입 허용 | false |
| 데이터소스 | Prometheus (기본), Loki |

### 6.2 데이터소스 구성

**파일 경로**: `C:\Users\USER\URR\k8s\spring\overlays\kind\grafana.yaml:1-21`

| 데이터소스 | 타입 | URL | 기본값 |
|-----------|------|-----|--------|
| Prometheus | prometheus | `http://prometheus-service:9090` | 기본 데이터소스 |
| Loki | loki | `http://loki-service:3100` | 보조 데이터소스 |

### 6.3 프로비저닝된 대시보드

**파일 경로**: `C:\Users\USER\URR\k8s\spring\overlays\kind\grafana-dashboards.yaml`

4개의 대시보드가 자동 프로비저닝된다:

#### Dashboard 1: Service Overview (`urr-service-overview`)

| 패널 ID | 패널 이름 | 메트릭 | 타입 |
|---------|----------|--------|------|
| 1 | Request Rate per Service | `http_server_requests_seconds_count` | timeseries |
| 2 | Error Rate per Service (5xx) | `http_server_requests_seconds_count{status=~"5.."}` | timeseries |
| 3 | Response Time p50 | `histogram_quantile(0.50, ...)` | timeseries |
| 4 | Response Time p95 | `histogram_quantile(0.95, ...)` | timeseries |
| 5 | Response Time p99 | `histogram_quantile(0.99, ...)` | timeseries |
| 6 | Active HTTP Connections | `tomcat_connections_current_connections`, `tomcat_threads_busy_threads` | timeseries |
| 7 | Uptime per Service | `process_uptime_seconds` | stat |
| 8 | Request Count by Status Code | `http_server_requests_seconds_count` by status | piechart |
| 9 | Top Endpoints by Request Rate | `http_server_requests_seconds_count` by uri | table |

임계값 설정:
- p95 응답 시간: 1초 (주의), 3초 (위험)
- p99 응답 시간: 2초 (주의), 5초 (위험)
- 에러율: 0.1 req/s (주의), 1 req/s (위험)

#### Dashboard 2: JVM Metrics (`urr-jvm-metrics`)

| 패널 ID | 패널 이름 | 메트릭 |
|---------|----------|--------|
| 1 | Heap Memory Used | `jvm_memory_used_bytes{area="heap"}`, `jvm_memory_max_bytes` |
| 2 | Heap Memory Utilization % | used/max * 100 |
| 3 | Non-Heap Memory Used | `jvm_memory_used_bytes{area="nonheap"}` |
| 4 | GC Pause Duration | `jvm_gc_pause_seconds_sum/count` |
| 5 | Live Threads | `jvm_threads_live/daemon/peak_threads` |
| 6 | CPU Usage | `process_cpu_usage`, `system_cpu_usage` |
| 7 | Loaded Classes | `jvm_classes_loaded_classes` |
| 8 | GC Memory Allocated / Promoted | `jvm_gc_memory_allocated/promoted_bytes_total` |

임계값 설정:
- 힙 메모리 사용률: 70% (주의), 90% (위험)

#### Dashboard 3: Kafka Metrics (`urr-kafka-metrics`)

| 패널 ID | 패널 이름 | 메트릭 |
|---------|----------|--------|
| 1 | Consumer Lag (Max per Partition) | `kafka_consumer_records_lag_max` |
| 2 | Consumer Lag (Records) | `kafka_consumer_records_lag` |
| 3 | Messages Consumed per Second | `kafka_consumer_fetch_manager_records_consumed_total` |
| 4 | Messages Produced per Second | `kafka_producer_record_send_total` |
| 5 | Consumer Fetch Rate | `kafka_consumer_fetch_manager_fetch_rate` |
| 6 | Producer Error Rate | `kafka_producer_record_error/retry_total` |
| 7 | Consumer Group Status | `kafka_consumer_coordinator_assigned_partitions` |

임계값 설정:
- Consumer Lag: 100 (주의), 1000 (위험)
- Producer Error Rate: 1 (위험)

#### Dashboard 4: Infrastructure (`urr-infrastructure`)

| 패널 ID | 패널 이름 | 메트릭 |
|---------|----------|--------|
| 1 | Service Health Status | `up{job="spring-services"}` |
| 2 | HikariCP Active Connections | `hikaricp_connections_active/idle` |
| 3 | HikariCP Connection Pool Utilization | active/max * 100 |
| 4 | HikariCP Connection Acquire Time | `hikaricp_connections_acquire_seconds` |
| 5 | HikariCP Pending Connection Requests | `hikaricp_connections_pending/timeout_total` |
| 6 | Redis Command Latency | `lettuce_command_completion_seconds` |
| 7 | Redis Command Rate | `lettuce_command_completion_seconds_count` |
| 8 | Kafka Broker Availability | `kafka_consumer_connection_count` |
| 9 | Disk Usage | `disk_free_bytes`, `disk_total_bytes` |

임계값 설정:
- 커넥션 풀 사용률: 60% (주의), 85% (위험)
- 커넥션 획득 시간: 0.1초 (주의), 1초 (위험)
- Pending 요청: 1 (주의), 5 (위험)

---

## 7. 알림

### 7.1 Grafana Alert Rules (K8s 환경)

**파일 경로**: `C:\Users\USER\URR\k8s\spring\overlays\kind\grafana-dashboards.yaml` (`alerts.json` 섹션, 라인 1003-1218)

| 알림 이름 | 조건 | 지속 시간 | 심각도 |
|----------|------|-----------|--------|
| High Error Rate | 에러율 > 5% | 5분 | Critical |
| Service Down | `up == 0` | 1분 | Critical |
| High Latency | p95 > 3초 | 5분 | Warning |
| Low Disk Space | 여유 공간 < 10% | 5분 | Warning |

상세 설정:

```
High Error Rate:
  expr: sum(rate(http_server_requests_seconds_count{status=~"5.."}[5m])) by (application)
        / sum(rate(http_server_requests_seconds_count[5m])) by (application) * 100
  evaluator: > 5  (5% 초과)
  for: 5m
  frequency: 1m
  noDataState: no_data

Service Down:
  expr: up{job="spring-services"}
  evaluator: < 1
  for: 1m
  frequency: 30s
  noDataState: alerting   # 데이터 없음 = 서비스 다운으로 간주
```

### 7.2 AWS CloudWatch 알람 (프로덕션 환경)

Terraform 모듈에서 정의된 CloudWatch 알람:

#### Lambda Worker 알람

**파일 경로**: `C:\Users\USER\URR\terraform\modules\lambda-worker\main.tf:153-211`

| 알람 이름 | 메트릭 | 조건 | SNS 연동 |
|----------|--------|------|----------|
| `{prefix}-ticket-worker-errors` | `AWS/Lambda Errors` | Sum > 5 (2회 연속, 5분) | `var.sns_topic_arn` |
| `{prefix}-ticket-worker-duration` | `AWS/Lambda Duration` | Avg > timeout*80% (2회 연속) | `var.sns_topic_arn` |
| `{prefix}-ticket-worker-throttles` | `AWS/Lambda Throttles` | Sum > 0 (1회, 5분) | `var.sns_topic_arn` |

#### SQS 대기열 알람

**파일 경로**: `C:\Users\USER\URR\terraform\modules\sqs\main.tf:97-135`

| 알람 이름 | 메트릭 | 조건 | SNS 연동 |
|----------|--------|------|----------|
| `{prefix}-ticket-events-dlq-messages` | `AWS/SQS ApproximateNumberOfMessagesVisible` | DLQ에 메시지 > 0 | `var.sns_topic_arn` |
| `{prefix}-ticket-events-message-age` | `AWS/SQS ApproximateAgeOfOldestMessage` | 최고 메시지 수명 > 600초(10분) | `var.sns_topic_arn` |

#### MSK (Kafka) 알람

**파일 경로**: `C:\Users\USER\URR\terraform\modules\msk\main.tf:202-240`

| 알람 이름 | 메트릭 | 조건 | SNS 연동 |
|----------|--------|------|----------|
| `{prefix}-msk-no-active-controller` | `AWS/Kafka ActiveControllerCount` | Max < 1 | `var.sns_topic_arn` |
| `{prefix}-msk-offline-partitions` | `AWS/Kafka OfflinePartitionsCount` | Max > 0 | `var.sns_topic_arn` |

#### 프로덕션 환경에서의 활성화

**파일 경로**: `C:\Users\USER\URR\terraform\environments\prod\main.tf:232-233,356-357`

```hcl
# Lambda Worker 모듈
enable_cloudwatch_alarms = true
sns_topic_arn            = var.sns_topic_arn

# SQS 모듈
enable_cloudwatch_alarms = true
sns_topic_arn            = var.sns_topic_arn
```

#### 추가 AWS 모니터링

| 리소스 | 모니터링 방식 | 파일 경로 |
|--------|--------------|-----------|
| RDS | Performance Insights, CloudWatch Logs (`postgresql`, `upgrade`) | `terraform/modules/rds/main.tf:88-94` |
| RDS | Enhanced Monitoring (`monitoring_interval`) | `terraform/modules/rds/main.tf:93-94` |
| RDS | 느린 쿼리 로깅 (`log_min_duration_statement: 1000ms`) | `terraform/modules/rds/main.tf:129-131` |
| EKS | Control Plane Logs (`api`, `audit`, `authenticator`, `controllerManager`, `scheduler`) | `terraform/modules/eks/main.tf:110` |
| MSK | CloudWatch Logs (브로커 로그) | `terraform/modules/msk/main.tf:120,185` |
| ElastiCache | SNS 알림 토픽 | `terraform/modules/elasticache/main.tf:139` |

---

## 8. 성능 모니터링 요약

### 8.1 모니터링 데이터 흐름

```
[Spring Boot Services]
   |
   |-- /actuator/prometheus --> [Prometheus :9090]
   |                               |
   |                               +--> [Grafana :3006]
   |                                      |
   |-- stdout/stderr ---------> [Promtail] --> [Loki :3100]
   |                                              |
   |                                              +--> [Grafana :3006]
   |
   |-- Brave spans -----------> [Zipkin :9411]
   |
   |-- (AWS prod only)
       |
       +-- CloudWatch Metrics --> CloudWatch Alarms --> SNS --> 알림
       +-- CloudWatch Logs
       +-- RDS Performance Insights
```

### 8.2 NodePort 접근 경로 (Kind 환경)

| 서비스 | NodePort | 용도 |
|--------|----------|------|
| Prometheus | 30090 | 메트릭 조회 |
| Grafana | 30006 | 대시보드 |
| Zipkin | 30411 | 분산 추적 UI |

---

## 9. 강점과 약점

### 강점

1. **일관된 설정**: 전체 8개 서비스가 동일한 메트릭/추적/로깅 패턴을 사용하여 운영 복잡도가 낮다.
2. **구조화된 로그**: traceId/spanId가 로그 패턴에 포함되어 로그와 추적을 연결할 수 있다.
3. **커스텀 비즈니스 메트릭**: 예약, 결제, 대기열 등 핵심 비즈니스 지표가 Prometheus로 노출된다.
4. **4개 Grafana 대시보드**: Service Overview, JVM, Kafka, Infrastructure로 주요 영역을 포괄한다.
5. **이중 알림 체계**: K8s에서는 Grafana Alerts, AWS에서는 CloudWatch Alarms + SNS로 환경별 알림을 제공한다.
6. **전체 로그 파이프라인**: Promtail(DaemonSet) -> Loki -> Grafana로 중앙 집중식 로그 검색이 가능하다.
7. **AWS 관리형 서비스 모니터링**: RDS Performance Insights, MSK CloudWatch 로그 등 인프라 수준의 관찰도 구성되어 있다.

### 약점

1. **프로브 경로 불일치**: K8s 프로브가 `/health`를 사용하지만 Actuator의 `/actuator/health`는 더 풍부한 의존성 검사를 제공한다. 프로브가 Actuator 엔드포인트를 사용하도록 변경하면 더 정확한 헬스체크가 가능하다.
2. **샘플링 비율 1.0 기본값**: 프로덕션에서 100% 샘플링은 성능에 영향을 미칠 수 있다. 환경 변수로 조절 가능하지만, 프로덕션 기본값을 0.1 정도로 낮추는 것이 적절하다.
3. **Zipkin 인메모리 스토리지**: Kind 환경에서 Zipkin이 인메모리로 운영되어, 재시작 시 추적 데이터가 소실된다. 프로덕션에서는 Elasticsearch나 Cassandra 백엔드가 필요하다.
4. **Loki 단일 인스턴스**: 복제 계수 1, 단일 인스턴스로 SPOF가 된다.
5. **Grafana 알림 채널 미설정**: `alerts.json`에 알림 규칙이 정의되어 있지만, 실제 notification channel(Slack, email 등)은 구성되어 있지 않다. 알림이 발생해도 수신자가 없다.
6. **startup probe 부재**: Spring Boot 애플리케이션은 시작이 느릴 수 있는데, `startupProbe`가 없어 `initialDelaySeconds`로만 보호하고 있다. JVM 워밍업 시간이 긴 경우 불안정할 수 있다.
7. **서비스 디스커버리 부재**: Prometheus가 `static_configs`로 타겟을 관리하여, 서비스 스케일링 시 수동 업데이트가 필요하다. Kubernetes ServiceMonitor나 annotations 기반 자동 디스커버리가 더 적합하다.
8. **payment-service, stats-service에 커스텀 메트릭 없음**: 비즈니스 메트릭이 ticket-service와 queue-service에만 존재하며, payment-service와 stats-service 자체에는 커스텀 메트릭 클래스가 없다.
9. **AWS 프로덕션 환경에 Application-level Prometheus/Grafana 구성 부재**: terraform 모듈에 Prometheus/Grafana 배포가 포함되어 있지 않아, Kind 환경의 모니터링 스택을 프로덕션 EKS에 어떻게 배포할지 결정되어 있지 않다.
