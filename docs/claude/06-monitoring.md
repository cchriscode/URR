# 모니터링 및 관측성 분석

Tiketi 플랫폼의 모니터링, 로깅, 추적, 알림 인프라에 대한 기술 분석 문서이다. 모든 설정값은 실제 소스 코드에서 추출하였으며, 각 항목에 출처(파일 경로 및 라인 번호)를 명시한다.

---

## 1. Prometheus (메트릭 수집)

Prometheus는 모든 Spring 서비스로부터 메트릭을 수집하는 중앙 메트릭 서버이다.

### 1.1 스크레이프 설정

글로벌 스크레이프 간격은 15초이며, Spring 서비스 전용 job은 10초 간격으로 `/actuator/prometheus` 경로에서 메트릭을 수집한다.

```yaml
global:
  scrape_interval: 15s
  evaluation_interval: 15s

scrape_configs:
  - job_name: 'spring-services'
    metrics_path: '/actuator/prometheus'
    scrape_interval: 10s
```

> 출처: `k8s/spring/overlays/kind/prometheus.yaml:7-14`

### 1.2 수집 대상 서비스 (8개)

| 서비스 | 타겟 주소 | 레이블 |
|--------|-----------|--------|
| auth-service | `auth-service:3005` | `service: auth-service` |
| gateway-service | `gateway-service:3001` | `service: gateway-service` |
| ticket-service | `ticket-service:3002` | `service: ticket-service` |
| payment-service | `payment-service:3003` | `service: payment-service` |
| stats-service | `stats-service:3004` | `service: stats-service` |
| queue-service | `queue-service:3007` | `service: queue-service` |
| community-service | `community-service:3008` | `service: community-service` |
| catalog-service | `catalog-service:3009` | `service: catalog-service` |

> 출처: `k8s/spring/overlays/kind/prometheus.yaml:15-47`

### 1.3 Deployment 설정

- **이미지**: `prom/prometheus:v2.51.0`
- **데이터 보존 기간**: 7일 (`--storage.tsdb.retention.time=7d`)
- **Hot Reload 지원**: `--web.enable-lifecycle` 플래그 활성화
- **설정 파일 경로**: `/etc/prometheus/prometheus.yml`
- **저장소 경로**: `/prometheus` (PVC: `prometheus-pvc`)

```yaml
containers:
  - name: prometheus
    image: prom/prometheus:v2.51.0
    args:
      - "--config.file=/etc/prometheus/prometheus.yml"
      - "--storage.tsdb.retention.time=7d"
      - "--web.enable-lifecycle"
```

> 출처: `k8s/spring/overlays/kind/prometheus.yaml:67-72`

### 1.4 리소스 제한

| 항목 | requests | limits |
|------|----------|--------|
| CPU | 100m | 500m |
| Memory | 256Mi | 512Mi |

> 출처: `k8s/spring/overlays/kind/prometheus.yaml:81-86`

### 1.5 헬스 체크

- **Liveness Probe**: `GET /-/healthy` (port 9090), 15초 초기 지연, 10초 주기
- **Readiness Probe**: `GET /-/ready` (port 9090), 5초 초기 지연, 5초 주기

> 출처: `k8s/spring/overlays/kind/prometheus.yaml:87-98`

### 1.6 서비스 노출

- **타입**: NodePort
- **포트**: 9090 -> NodePort 30090

```yaml
spec:
  type: NodePort
  ports:
    - port: 9090
      targetPort: 9090
      nodePort: 30090
```

> 출처: `k8s/spring/overlays/kind/prometheus.yaml:114-122`

---

## 2. Grafana (시각화 대시보드)

Grafana는 Prometheus 메트릭과 Loki 로그를 시각화하는 통합 대시보드이다.

### 2.1 데이터소스 설정

두 개의 데이터소스가 자동 프로비저닝된다.

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

- Prometheus가 기본(default) 데이터소스로 설정되어 있다.
- Loki는 로그 조회용 보조 데이터소스이다.
- 두 데이터소스 모두 UI에서 편집 가능(`editable: true`)하다.

> 출처: `k8s/spring/overlays/kind/grafana.yaml:7-21`

### 2.2 대시보드 프로비저닝

파일 기반 대시보드 자동 로딩이 설정되어 있다.

```yaml
providers:
  - name: 'tiketi-dashboards'
    orgId: 1
    folder: 'Tiketi'
    type: file
    disableDeletion: false
    updateIntervalSeconds: 30
    allowUiUpdates: true
    options:
      path: /var/lib/grafana/dashboards
      foldersFromFilesStructure: false
```

- **폴더**: `Tiketi`
- **갱신 주기**: 30초마다 파일 변경 감지
- **UI 수정 허용**: `allowUiUpdates: true`
- **대시보드 경로**: `/var/lib/grafana/dashboards` (ConfigMap `grafana-dashboards`에서 마운트)

> 출처: `k8s/spring/overlays/kind/grafana.yaml:29-41`

### 2.3 Deployment 설정

- **이미지**: `grafana/grafana:10.2.3`
- **HTTP 포트**: 3006 (`GF_SERVER_HTTP_PORT`)
- **관리자 계정**: `admin` / `admin`
- **회원가입 비활성화**: `GF_USERS_ALLOW_SIGN_UP: false`

```yaml
env:
  - name: GF_SECURITY_ADMIN_USER
    value: "admin"
  - name: GF_SECURITY_ADMIN_PASSWORD
    value: "admin"
  - name: GF_USERS_ALLOW_SIGN_UP
    value: "false"
  - name: GF_SERVER_HTTP_PORT
    value: "3006"
```

> 출처: `k8s/spring/overlays/kind/grafana.yaml:62-74`

### 2.4 볼륨 마운트

| 볼륨 이름 | 마운트 경로 | 소스 |
|-----------|------------|------|
| grafana-storage | `/var/lib/grafana` | PVC: `grafana-pvc` |
| grafana-datasources | `/etc/grafana/provisioning/datasources` | ConfigMap: `grafana-datasources` |
| grafana-dashboard-provisioning | `/etc/grafana/provisioning/dashboards` | ConfigMap: `grafana-dashboard-provisioning` |
| grafana-dashboards | `/var/lib/grafana/dashboards` | ConfigMap: `grafana-dashboards` |

> 출처: `k8s/spring/overlays/kind/grafana.yaml:75-115`

### 2.5 리소스 제한 및 헬스 체크

| 항목 | requests | limits |
|------|----------|--------|
| CPU | 100m | 500m |
| Memory | 256Mi | 512Mi |

- **Liveness Probe**: `GET /api/health` (port 3006), 30초 초기 지연, 10초 주기
- **Readiness Probe**: `GET /api/health` (port 3006), 10초 초기 지연, 5초 주기

> 출처: `k8s/spring/overlays/kind/grafana.yaml:84-102`

### 2.6 서비스 노출

- **타입**: NodePort
- **포트**: 3006 -> NodePort 30006

> 출처: `k8s/spring/overlays/kind/grafana.yaml:128-133`

---

## 3. 분산 추적 (Zipkin)

Zipkin은 마이크로서비스 간 요청 흐름을 추적하는 분산 추적 시스템이다.

### 3.1 Deployment 설정

- **이미지**: `openzipkin/zipkin:3`
- **컨테이너 포트**: 9411
- **리소스**: requests (100m CPU, 256Mi), limits (500m CPU, 512Mi)

```yaml
containers:
  - name: zipkin
    image: openzipkin/zipkin:3
    ports:
      - containerPort: 9411
    resources:
      requests:
        memory: "256Mi"
        cpu: "100m"
      limits:
        memory: "512Mi"
        cpu: "500m"
```

> 출처: `k8s/spring/overlays/kind/zipkin.yaml:1-26`

### 3.2 서비스 노출

- **타입**: NodePort
- **포트**: 9411 -> NodePort 30411

```yaml
spec:
  type: NodePort
  ports:
    - port: 9411
      targetPort: 9411
      nodePort: 30411
```

> 출처: `k8s/spring/overlays/kind/zipkin.yaml:32-39`

### 3.3 서비스별 추적 설정

모든 서비스에 동일한 추적 설정이 적용된다.

```yaml
management:
  tracing:
    sampling:
      probability: ${TRACING_SAMPLING_PROBABILITY:1.0}
  zipkin:
    tracing:
      endpoint: ${ZIPKIN_ENDPOINT:http://localhost:9411/api/v2/spans}
```

- **샘플링 확률**: 기본값 `1.0` (100% 전체 추적)
- **Zipkin 엔드포인트**: `http://localhost:9411/api/v2/spans` (환경변수로 오버라이드 가능)

각 서비스별 설정 출처:

| 서비스 | 출처 |
|--------|------|
| ticket-service | `services-spring/ticket-service/src/main/resources/application.yml:56-61` |
| gateway-service | `services-spring/gateway-service/src/main/resources/application.yml:94-99` |
| auth-service | `services-spring/auth-service/src/main/resources/application.yml:36-41` |
| payment-service | `services-spring/payment-service/src/main/resources/application.yml:42-47` |
| stats-service | `services-spring/stats-service/src/main/resources/application.yml:44-49` |
| queue-service | `services-spring/queue-service/src/main/resources/application.yml:26-31` |
| community-service | `services-spring/community-service/src/main/resources/application.yml:29-34` |
| catalog-service | `services-spring/catalog-service/src/main/resources/application.yml:28-33` |

### 3.4 로그 패턴과 추적 ID 통합

모든 서비스의 로그 패턴에 `traceId`와 `spanId`가 포함되어 있어, 로그와 추적 정보를 상관 분석할 수 있다.

```yaml
logging:
  pattern:
    level: "%5p [${spring.application.name:},%X{traceId:-},%X{spanId:-}]"
```

이 패턴은 다음과 같은 로그 출력을 생성한다:

```
 INFO [ticket-service,abc123def456,789xyz000111] ...
```

> 출처: `services-spring/ticket-service/src/main/resources/application.yml:63-65`

---

## 4. 로그 수집 (Loki + Promtail)

### 4.1 Loki (로그 저장소)

Loki는 Grafana에서 조회 가능한 경량 로그 집계 시스템이다.

#### 4.1.1 서버 설정

```yaml
auth_enabled: false

server:
  http_listen_port: 3100
  grpc_listen_port: 9096
```

- **인증**: 비활성화 (`auth_enabled: false`)
- **HTTP 포트**: 3100
- **gRPC 포트**: 9096

> 출처: `k8s/spring/overlays/kind/loki.yaml:8-12`

#### 4.1.2 스토리지 설정

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

- **스토리지 방식**: 로컬 파일시스템
- **복제 팩터**: 1 (단일 노드 환경)
- **KV 스토어**: 인메모리 (클러스터링 없음)

> 출처: `k8s/spring/overlays/kind/loki.yaml:14-23`

#### 4.1.3 스키마 설정

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

- **인덱스 저장소**: boltdb-shipper
- **오브젝트 저장소**: filesystem
- **스키마 버전**: v11
- **인덱스 주기**: 24시간

> 출처: `k8s/spring/overlays/kind/loki.yaml:25-33`

#### 4.1.4 BoltDB Shipper 캐시 설정

```yaml
storage_config:
  boltdb_shipper:
    active_index_directory: /loki/boltdb-shipper-active
    cache_location: /loki/boltdb-shipper-cache
    cache_ttl: 24h
    shared_store: filesystem
```

- **캐시 TTL**: 24시간
- **공유 스토어**: filesystem

> 출처: `k8s/spring/overlays/kind/loki.yaml:35-42`

#### 4.1.5 Deployment 설정

- **이미지**: `grafana/loki:2.9.3`
- **리소스**: requests (100m CPU, 256Mi), limits (500m CPU, 512Mi)
- **스토리지**: PVC `loki-pvc`에 `/loki` 마운트
- **Liveness Probe**: `GET /ready` (port 3100), 45초 초기 지연
- **Readiness Probe**: `GET /ready` (port 3100), 30초 초기 지연

> 출처: `k8s/spring/overlays/kind/loki.yaml:45-101`

#### 4.1.6 서비스 노출

- **타입**: ClusterIP (클러스터 내부 전용)
- **포트**: 3100 (HTTP), 9096 (gRPC)

> 출처: `k8s/spring/overlays/kind/loki.yaml:103-122`

### 4.2 Promtail (로그 수집 에이전트)

Promtail은 DaemonSet으로 모든 노드에서 실행되며, 컨테이너 로그를 수집하여 Loki로 전송한다.

#### 4.2.1 클라이언트 설정

```yaml
server:
  http_listen_port: 9080
  grpc_listen_port: 0

positions:
  filename: /tmp/positions.yaml

clients:
  - url: http://loki-service:3100/loki/api/v1/push
```

- **Loki 전송 URL**: `http://loki-service:3100/loki/api/v1/push`
- **포지션 파일**: `/tmp/positions.yaml` (로그 읽기 위치 추적)

> 출처: `k8s/spring/overlays/kind/promtail.yaml:8-16`

#### 4.2.2 Kubernetes Pod 디스커버리

```yaml
scrape_configs:
  - job_name: kubernetes-pods
    kubernetes_sd_configs:
      - role: pod
        namespaces:
          names:
            - tiketi-spring
    relabel_configs:
      - source_labels: [__meta_kubernetes_pod_label_app]
        target_label: app
      - source_labels: [__meta_kubernetes_pod_name]
        target_label: pod
      - source_labels: [__meta_kubernetes_namespace]
        target_label: namespace
```

- **대상 네임스페이스**: `tiketi-spring`
- **레이블 릴레이블링**:
  - `__meta_kubernetes_pod_label_app` -> `app` (서비스 이름 식별)
  - `__meta_kubernetes_pod_name` -> `pod` (개별 파드 식별)
  - `__meta_kubernetes_namespace` -> `namespace` (네임스페이스 식별)

> 출처: `k8s/spring/overlays/kind/promtail.yaml:18-31`

#### 4.2.3 DaemonSet 배포

- **이미지**: `grafana/promtail:2.9.3`
- **서비스 어카운트**: `promtail`
- **리소스**: requests (50m CPU, 128Mi), limits (200m CPU, 256Mi)

볼륨 마운트:

| 볼륨 | 마운트 경로 | 소스 | 비고 |
|------|------------|------|------|
| config | `/etc/promtail` | ConfigMap: `promtail-config` | 설정 파일 |
| varlog | `/var/log` | HostPath: `/var/log` | 노드 로그 |
| varlibdockercontainers | `/var/lib/docker/containers` | HostPath: `/var/lib/docker/containers` | 컨테이너 로그 (읽기 전용) |

> 출처: `k8s/spring/overlays/kind/promtail.yaml:34-82`

#### 4.2.4 RBAC 설정

Promtail이 Kubernetes API를 통해 파드 정보를 조회하기 위한 권한 설정이다.

```yaml
apiVersion: rbac.authorization.k8s.io/v1
kind: ClusterRole
metadata:
  name: promtail
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

- **ServiceAccount**: `promtail` (네임스페이스: `tiketi-spring`)
- **ClusterRole**: `nodes`, `nodes/proxy`, `services`, `endpoints`, `pods`에 대한 `get`, `watch`, `list` 권한
- **ClusterRoleBinding**: ServiceAccount `promtail`을 ClusterRole `promtail`에 바인딩

> 출처: `k8s/spring/overlays/kind/promtail.yaml:85-117`

---

## 5. 구조화된 로깅

모든 서비스는 분산 추적 컨텍스트를 포함하는 통일된 로그 패턴을 사용한다.

### 5.1 로그 패턴 형식

```yaml
logging:
  pattern:
    level: "%5p [${spring.application.name:},%X{traceId:-},%X{spanId:-}]"
```

패턴 구성요소:

| 요소 | 설명 | 예시 |
|------|------|------|
| `%5p` | 로그 레벨 (5자 우측 정렬) | ` INFO`, `DEBUG`, `ERROR` |
| `${spring.application.name:}` | 서비스 이름 | `ticket-service` |
| `%X{traceId:-}` | 분산 추적 ID | `abc123def456789` |
| `%X{spanId:-}` | 스팬 ID | `789xyz000111` |

### 5.2 출력 예시

```
 INFO [ticket-service,6a3b8c9d2e1f0a4b,3c5d7e9f1a2b4c6d] c.t.t.service.TicketService : Ticket created
ERROR [payment-service,6a3b8c9d2e1f0a4b,8e0f2a4b6c8d0e2f] c.t.p.service.PaymentService : Payment failed
```

동일한 `traceId`(`6a3b8c9d2e1f0a4b`)를 가진 로그 라인들은 하나의 요청 흐름에 속하며, Zipkin UI 또는 Grafana에서 상관 분석이 가능하다.

### 5.3 서비스별 로깅 설정 출처

| 서비스 | 출처 |
|--------|------|
| ticket-service | `services-spring/ticket-service/src/main/resources/application.yml:63-65` |
| gateway-service | `services-spring/gateway-service/src/main/resources/application.yml:101-103` |
| auth-service | `services-spring/auth-service/src/main/resources/application.yml:43-45` |
| payment-service | `services-spring/payment-service/src/main/resources/application.yml:49-51` |
| stats-service | `services-spring/stats-service/src/main/resources/application.yml:51-53` |
| queue-service | `services-spring/queue-service/src/main/resources/application.yml:33-35` |
| community-service | `services-spring/community-service/src/main/resources/application.yml:36-38` |
| catalog-service | `services-spring/catalog-service/src/main/resources/application.yml:35-37` |

---

## 6. Health Check 및 Actuator

### 6.1 Actuator 엔드포인트 설정

모든 서비스에 다음과 같은 공통 Actuator 설정이 적용되어 있다.

```yaml
management:
  endpoint:
    health:
      show-details: always
      show-components: always
  endpoints:
    web:
      exposure:
        include: health,info,prometheus
```

- **노출 엔드포인트**: `health`, `info`, `prometheus`
- **헬스 상세 정보**: 항상 표시 (`show-details: always`)
- **컴포넌트 정보**: 항상 표시 (`show-components: always`)

> 출처 (ticket-service 기준): `services-spring/ticket-service/src/main/resources/application.yml:40-55`

### 6.2 서비스별 헬스 체크 구성요소

각 서비스는 의존하는 인프라에 따라 다른 헬스 인디케이터가 활성화되어 있다.

| 서비스 | DB | Redis | 출처 |
|--------|:--:|:-----:|------|
| ticket-service | O | O | `application.yml:48-51` |
| gateway-service | - | O | `application.yml:88-89` |
| auth-service | O | - | `application.yml:29-31` |
| payment-service | O | - | `application.yml:35-37` |
| stats-service | O | - | `application.yml:37-39` |
| queue-service | - | O | `application.yml:19-21` |
| community-service | O | - | `application.yml:22-24` |
| catalog-service | O | - | `application.yml:21-23` |

### 6.3 Kubernetes 프로브 설정

서비스 Deployment에 Readiness/Liveness 프로브가 설정되어 있다 (ticket-service 예시).

```yaml
readinessProbe:
  httpGet:
    path: /health
    port: 3002
  initialDelaySeconds: 10
  periodSeconds: 10
livenessProbe:
  httpGet:
    path: /health
    port: 3002
  initialDelaySeconds: 20
  periodSeconds: 20
```

| 프로브 | 경로 | 초기 지연 | 주기 |
|--------|------|-----------|------|
| Readiness | `/health` | 10초 | 10초 |
| Liveness | `/health` | 20초 | 20초 |

> 출처: `k8s/spring/base/ticket-service/deployment.yaml:39-50`

---

## 7. Circuit Breaker 모니터링 (Resilience4j)

내부 서비스 간 통신에 Circuit Breaker와 Retry 패턴이 적용되어 있다. `internalService`라는 이름의 인스턴스로 통일 설정되어 있다.

### 7.1 Circuit Breaker 설정

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

| 파라미터 | 값 | 설명 |
|----------|-----|------|
| `sliding-window-size` | 10 | 최근 10개 요청으로 실패율 계산 |
| `failure-rate-threshold` | 50% | 실패율 50% 초과 시 OPEN 전환 |
| `wait-duration-in-open-state` | 10초 | OPEN 상태 유지 시간 |
| `permitted-number-of-calls-in-half-open-state` | 3 | HALF-OPEN에서 시도할 요청 수 |
| `slow-call-duration-threshold` | 3초 | 느린 호출 판정 기준 |
| `slow-call-rate-threshold` | 80% | 느린 호출 비율 80% 초과 시 OPEN 전환 |

> 출처: `services-spring/ticket-service/src/main/resources/application.yml:87-96`

### 7.2 상태 전이 다이어그램

```
CLOSED ──(실패율 >= 50% 또는 느린호출 >= 80%)──> OPEN
   ^                                                  |
   |                                            10초 대기
   |                                                  |
   |                                                  v
   └──────(3회 시도 성공)──── HALF-OPEN ──(실패)──> OPEN
```

### 7.3 Retry 설정

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

| 파라미터 | 값 | 설명 |
|----------|-----|------|
| `max-attempts` | 3 | 최대 재시도 횟수 |
| `wait-duration` | 500ms | 초기 대기 시간 |
| `exponential-backoff-multiplier` | 2 | 지수 백오프 배수 |
| 재시도 대상 예외 | `ResourceAccessException`, `ConnectException` | 네트워크 관련 예외만 재시도 |

재시도 간격: 500ms -> 1000ms -> 2000ms (지수 백오프)

> 출처: `services-spring/ticket-service/src/main/resources/application.yml:97-105`

### 7.4 Resilience4j 설정 적용 서비스

| 서비스 | Circuit Breaker | Retry | 출처 |
|--------|:---------------:|:-----:|------|
| ticket-service | O | O | `application.yml:87-105` |
| payment-service | O | O | `application.yml:53-71` |
| queue-service | O | O | `application.yml:58-76` |
| community-service | O | O | `application.yml:44-62` |
| catalog-service | O | O | `application.yml:49-67` |
| auth-service | - | - | 설정 없음 |
| gateway-service | - | - | 설정 없음 |
| stats-service | - | - | 설정 없음 |

---

## 8. 모니터링 접근 포인트

### 8.1 인프라 도구 접근

| 도구 | URL | NodePort | 용도 |
|------|-----|----------|------|
| Prometheus | `http://localhost:30090` | 30090 | 메트릭 조회 및 PromQL |
| Grafana | `http://localhost:30006` | 30006 | 대시보드 시각화 (admin/admin) |
| Zipkin | `http://localhost:30411` | 30411 | 분산 추적 UI |

### 8.2 서비스별 Actuator 엔드포인트

| 서비스 | Health | Prometheus Metrics | 서비스 포트 |
|--------|--------|--------------------|-------------|
| auth-service | `/actuator/health` | `/actuator/prometheus` | 3005 |
| gateway-service | `/actuator/health` | `/actuator/prometheus` | 3001 |
| ticket-service | `/actuator/health` | `/actuator/prometheus` | 3002 |
| payment-service | `/actuator/health` | `/actuator/prometheus` | 3003 |
| stats-service | `/actuator/health` | `/actuator/prometheus` | 3004 |
| queue-service | `/actuator/health` | `/actuator/prometheus` | 3007 |
| community-service | `/actuator/health` | `/actuator/prometheus` | 3008 |
| catalog-service | `/actuator/health` | `/actuator/prometheus` | 3009 |

### 8.3 관측성 데이터 흐름도

```
                        ┌─────────────┐
                        │   Grafana   │
                        │ :30006      │
                        └──────┬──────┘
                          ┌────┴────┐
                          │         │
                    ┌─────▼──┐  ┌───▼────┐
                    │Promethe│  │  Loki  │
                    │us:30090│  │ :3100  │
                    └────▲───┘  └───▲────┘
                         │          │
              ┌──────────┤     ┌────┴─────┐
              │ scrape   │     │ push     │
              │ (10s)    │     │          │
     ┌────────┴──────────┴─┐  ┌▼─────────┐
     │  Spring Services    │  │ Promtail  │
     │  /actuator/prometheus│  │ DaemonSet │
     │  /actuator/health   │  └───────────┘
     └─────────┬───────────┘
               │ spans
               ▼
         ┌───────────┐
         │  Zipkin   │
         │  :30411   │
         └───────────┘
```

**메트릭 흐름**: Spring Services -> (Prometheus scrape 10s) -> Prometheus -> Grafana

**로그 흐름**: Spring Services -> 컨테이너 stdout -> Promtail (DaemonSet) -> Loki -> Grafana

**추적 흐름**: Spring Services -> (HTTP push) -> Zipkin

---

## 파일 출처 색인

본 문서에서 참조한 전체 파일 목록이다.

| 파일 | 설명 |
|------|------|
| `k8s/spring/overlays/kind/prometheus.yaml` | Prometheus ConfigMap, Deployment, Service |
| `k8s/spring/overlays/kind/grafana.yaml` | Grafana 데이터소스, 대시보드, Deployment, Service |
| `k8s/spring/overlays/kind/zipkin.yaml` | Zipkin Deployment, Service |
| `k8s/spring/overlays/kind/loki.yaml` | Loki ConfigMap, Deployment, Service |
| `k8s/spring/overlays/kind/promtail.yaml` | Promtail ConfigMap, DaemonSet, RBAC |
| `k8s/spring/base/ticket-service/deployment.yaml` | K8s 프로브 설정 예시 |
| `services-spring/ticket-service/src/main/resources/application.yml` | Actuator, 추적, 로깅, Resilience4j |
| `services-spring/gateway-service/src/main/resources/application.yml` | Actuator, 추적, 로깅 |
| `services-spring/auth-service/src/main/resources/application.yml` | Actuator, 추적, 로깅 |
| `services-spring/payment-service/src/main/resources/application.yml` | Actuator, 추적, 로깅, Resilience4j |
| `services-spring/stats-service/src/main/resources/application.yml` | Actuator, 추적, 로깅 |
| `services-spring/queue-service/src/main/resources/application.yml` | Actuator, 추적, 로깅, Resilience4j |
| `services-spring/community-service/src/main/resources/application.yml` | Actuator, 추적, 로깅, Resilience4j |
| `services-spring/catalog-service/src/main/resources/application.yml` | Actuator, 추적, 로깅, Resilience4j |

모든 파일 경로는 프로젝트 루트(`C:\Users\USER\project-ticketing-copy`) 기준 상대 경로이다.
