# URR 모니터링 가이드

- [Part 1: Kind 로컬 환경](#part-1-kind-로컬-환경) — 현재 쓰는 법
- [Part 2: AWS 프로덕션 환경](#part-2-aws-프로덕션-환경) — 배포 후 모니터링

---

# Part 1: Kind 로컬 환경

---

## 접속 주소

| 도구 | URL | 용도 |
|------|-----|------|
| **Grafana** | http://localhost:30006 | 대시보드, 그래프, 로그 조회 |
| **Prometheus** | http://localhost:30090 | 메트릭 수집 상태 확인, 쿼리 테스트 |
| **Zipkin** | http://localhost:30411 | 분산 추적 (서비스 간 호출 체인) |

---

## 1. Grafana 사용법

### 1.1 로그인

1. 브라우저에서 `http://localhost:30006` 접속
2. 로그인 화면이 나타남
   - **Email or username**: `admin`
   - **Password**: `admin`
3. 비밀번호 변경 화면 → **Skip** 클릭

### 1.2 데이터소스 확인

이미 설정되어 있지만 확인하는 방법:

1. 좌측 메뉴 하단 **톱니바퀴 아이콘** (Settings) 클릭
2. **Data sources** 클릭
3. 두 개가 보여야 정상:
   - **Prometheus** — `http://prometheus-service:9090`
   - **Loki** — `http://loki-service:3100`

### 1.3 Explore에서 메트릭 쿼리하기

메트릭을 빠르게 조회하는 방법:

1. 좌측 메뉴에서 **나침반 아이콘** (Explore) 클릭
2. 상단 좌측 드롭다운에서 **Prometheus** 선택
3. 쿼리 입력란에 아래 예시 입력:
   ```
   up{job="spring-services"}
   ```
4. 우측 상단 **Run query** (파란 버튼) 클릭
5. 아래에 그래프/테이블이 나타남

### 1.4 Explore에서 로그 조회하기

1. 좌측 **Explore** 클릭
2. 상단 좌측 드롭다운에서 **Loki** 선택
3. 쿼리 입력란에:
   ```
   {app="ticket-service"}
   ```
4. **Run query** 클릭
5. 해당 서비스의 로그가 시간순으로 표시됨

에러 로그만 보려면:
```
{app="ticket-service"} |= "ERROR"
```

### 1.5 대시보드 만들기

#### 새 대시보드 생성

1. 좌측 메뉴 **+ 아이콘** 클릭 → **New dashboard** 클릭
2. **Add visualization** 클릭
3. 데이터소스로 **Prometheus** 선택

#### 패널 추가 (예: 서비스 상태)

1. 하단 쿼리 입력란에:
   ```
   up{job="spring-services"}
   ```
2. 우측 패널 설정:
   - **Title**: `서비스 상태`
   - **Visualization** 탭 → **Stat** 선택 (숫자 표시)
3. 우측 상단 **Apply** 클릭

#### 패널 추가 (예: HTTP 요청 수)

1. 대시보드 상단 **Add** → **Visualization** 클릭
2. 쿼리:
   ```
   sum(rate(http_server_requests_seconds_count[5m])) by (service)
   ```
3. Visualization → **Time series** (기본값, 그래프)
4. Title: `초당 HTTP 요청 수`
5. **Apply**

#### 대시보드 저장

1. 상단 **저장 아이콘** (디스크 모양) 클릭
2. 이름 입력: `URR 모니터링`
3. **Save** 클릭

---

## 2. Prometheus 사용법

### 2.1 타겟 상태 확인

어떤 서비스를 수집 중인지 확인:

1. `http://localhost:30090` 접속
2. 상단 메뉴 **Status** → **Targets** 클릭
3. 각 서비스의 **State** 가 `UP` (초록) 인지 확인

현재 수집 중인 타겟:
- auth-service, gateway-service, ticket-service, payment-service
- queue-service, stats-service, catalog-service, community-service
- dragonfly-spring (Redis Exporter)

### 2.2 메트릭 쿼리 테스트

1. `http://localhost:30090` 접속 (기본 화면이 Graph)
2. 상단 쿼리 입력란에 PromQL 입력
3. **Execute** 클릭
4. **Graph** 탭 → 시간 그래프 / **Table** 탭 → 현재 값

테스트용 쿼리:
```promql
up
```
→ 모든 타겟의 up/down 상태 (1=정상, 0=다운)

---

## 3. Zipkin 사용법

서비스 간 API 호출을 추적한다. 예: 사용자 요청이 gateway → ticket → payment 순서로 흐를 때 각 구간 소요 시간을 볼 수 있다.

### 3.1 트레이스 검색

1. `http://localhost:30411` 접속
2. 좌측 상단 **돋보기 아이콘** 클릭 (기본 화면)
3. **serviceName** 드롭다운 → 서비스 선택 (예: `gateway-service`)
4. 우측 **RUN QUERY** 버튼 클릭
5. 아래에 트레이스 목록이 나타남

### 3.2 트레이스 상세 보기

1. 목록에서 트레이스 하나 클릭
2. 워터폴 차트가 나타남:
   - 각 바 = 하나의 서비스 구간 (span)
   - 바의 길이 = 소요 시간
   - 색상별로 서비스 구분
3. 개별 span 클릭 → 상세 태그 (HTTP method, URL, status code 등)

### 3.3 Dependencies

서비스 간 의존 관계를 시각적으로 확인:

1. 상단 메뉴에서 **Dependencies** 클릭
2. 서비스 간 호출 관계가 그래프로 표시됨

---

## 4. 주요 메트릭 쿼리 모음

Grafana Explore 또는 Prometheus에서 사용 가능한 PromQL 쿼리.

### 4.1 서비스 상태

```promql
# 서비스 up/down (1=정상, 0=다운)
up{job="spring-services"}

# Redis(Dragonfly) up/down
redis_up
```

### 4.2 HTTP 요청

```promql
# 서비스별 초당 요청 수
sum(rate(http_server_requests_seconds_count[5m])) by (service)

# 서비스별 + 상태코드별 요청 수
sum(rate(http_server_requests_seconds_count[5m])) by (service, status)

# 5xx 에러만
sum(rate(http_server_requests_seconds_count{status=~"5.."}[5m])) by (service)

# 에러 비율 (%)
sum(rate(http_server_requests_seconds_count{status=~"5.."}[5m])) by (service)
/ sum(rate(http_server_requests_seconds_count[5m])) by (service) * 100

# 평균 응답 시간 (초)
sum(rate(http_server_requests_seconds_sum[5m])) by (service)
/ sum(rate(http_server_requests_seconds_count[5m])) by (service)

# 최대 응답 시간
http_server_requests_seconds_max
```

### 4.3 Redis / 대기열

```promql
# Redis 메모리 사용량 (bytes → MB로 보려면 /1024/1024)
redis_memory_used_bytes

# 메모리 사용률 (%)
redis_memory_used_bytes / redis_config_maxmemory * 100

# 연결된 클라이언트 수
redis_connected_clients

# 저장된 키 개수 (대기열 크기 포함)
redis_db_keys

# 만료 예정 키 수
redis_db_keys_expiring

# 초당 Redis 커맨드 처리량
rate(redis_commands_processed_total[5m])

# 키 hit/miss 비율
redis_keyspace_hits_total / (redis_keyspace_hits_total + redis_keyspace_misses_total)

# evicted 키 (메모리 부족 시 강제 삭제된 키)
rate(redis_evicted_keys_total[5m])
```

```promql
# queue-service의 Lua 스크립트 실행 횟수 (= 대기열 입장 처리)
rate(lettuce_command_completion_seconds_count{command="EVALSHA",service="queue-service"}[5m])

# queue-service Redis 커맨드 평균 응답 시간
rate(lettuce_command_completion_seconds_sum{service="queue-service"}[5m])
/ rate(lettuce_command_completion_seconds_count{service="queue-service"}[5m])

# Redis 서킷브레이커 상태 (1=닫힘/정상)
resilience4j_circuitbreaker_state{name="redisQueue",state="closed"}
```

### 4.4 비즈니스 메트릭

```promql
# 예매 생성 수
business_reservation_total

# 예매 확정 수
business_reservation_confirmed_total

# 예매 취소 수
business_reservation_cancelled_total

# 예매 만료 수
business_reservation_expired_total

# 예매 생성 소요 시간 (초)
business_reservation_create_duration_seconds_max

# 결제 성공 수
business_payment_processed_total

# 결제 실패 수
business_payment_failed_total

# 대기열 입장(join) 수
business_queue_joined_total

# 대기열 입장 허가(admit) 수
business_queue_admitted_total

# 대기열 이탈 수
business_queue_left_total

# 양도 완료 수
business_transfer_completed_total

# 멤버십 가입 수
business_membership_activated_total
```

### 4.5 JVM / 시스템

```promql
# JVM 힙 메모리 사용량 (서비스별)
jvm_memory_used_bytes{area="heap"}

# GC 횟수 (초당)
rate(jvm_gc_pause_seconds_count[5m])

# GC 소요 시간
rate(jvm_gc_pause_seconds_sum[5m])

# CPU 사용률
system_cpu_usage

# 프로세스 CPU 사용률
process_cpu_usage

# 활성 스레드 수
jvm_threads_live_threads

# 프로세스 업타임 (초)
process_uptime_seconds
```

### 4.6 DB 커넥션 풀 (HikariCP)

```promql
# 활성 커넥션 수
hikaricp_connections_active

# 유휴 커넥션 수
hikaricp_connections_idle

# 최대 커넥션 수
hikaricp_connections_max

# 대기 중인 요청 수 (이게 높으면 커넥션 부족)
hikaricp_connections_pending

# 커넥션 타임아웃 횟수 (이게 증가하면 심각)
hikaricp_connections_timeout_total

# 커넥션 획득 시간
hikaricp_connections_acquire_seconds_max
```

### 4.7 Kafka

```promql
# Consumer lag (처리 지연 메시지 수)
kafka_consumer_fetch_manager_records_lag_max

# 초당 메시지 소비 수
kafka_consumer_fetch_manager_records_consumed_rate

# Producer 초당 전송 수
kafka_producer_record_send_rate

# Producer 전송 실패
kafka_producer_record_error_total
```

---

## 5. 추천 대시보드 구성

Grafana에서 대시보드를 만들 때 아래 패널 구성을 참고.

### Row 1: 서비스 상태 (Stat 패널)

| 패널 | 쿼리 | Visualization |
|------|------|---------------|
| 서비스 UP/DOWN | `up{job="spring-services"}` | Stat |
| Redis UP/DOWN | `redis_up` | Stat |

### Row 2: HTTP 트래픽 (Time series)

| 패널 | 쿼리 |
|------|------|
| 초당 요청 수 | `sum(rate(http_server_requests_seconds_count[5m])) by (service)` |
| 5xx 에러 수 | `sum(rate(http_server_requests_seconds_count{status=~"5.."}[5m])) by (service)` |
| 평균 응답 시간 | `sum(rate(http_server_requests_seconds_sum[5m])) by (service) / sum(rate(http_server_requests_seconds_count[5m])) by (service)` |

### Row 3: 비즈니스 (Stat / Time series)

| 패널 | 쿼리 |
|------|------|
| 총 예매 수 | `business_reservation_total` |
| 결제 성공/실패 | `business_payment_processed_total` / `business_payment_failed_total` |
| 대기열 입장 | `rate(business_queue_joined_total[5m])` |
| 양도 완료 | `business_transfer_completed_total` |

### Row 4: Redis (Gauge / Time series)

| 패널 | 쿼리 |
|------|------|
| 메모리 사용률 | `redis_memory_used_bytes / redis_config_maxmemory * 100` |
| 연결 클라이언트 | `redis_connected_clients` |
| 키 개수 | `redis_db_keys` |
| 초당 커맨드 | `rate(redis_commands_processed_total[5m])` |

### Row 5: JVM / 인프라 (Time series)

| 패널 | 쿼리 |
|------|------|
| 힙 메모리 | `jvm_memory_used_bytes{area="heap"}` |
| CPU 사용률 | `process_cpu_usage` |
| DB 활성 커넥션 | `hikaricp_connections_active` |
| Kafka Consumer Lag | `kafka_consumer_fetch_manager_records_lag_max` |

---

## 6. 로그 조회 (Loki)

Grafana Explore에서 데이터소스를 **Loki**로 선택.

```logql
# 특정 서비스 전체 로그
{app="ticket-service"}

# 에러만
{app="payment-service"} |= "ERROR"

# 특정 키워드 검색
{app="queue-service"} |= "admitted"

# 여러 서비스 동시 조회
{app=~"ticket-service|payment-service"}

# 에러 + 특정 키워드 조합
{app="gateway-service"} |= "ERROR" |= "timeout"
```

---

## 7. 알림 확인용 체크리스트

문제가 의심될 때 순서대로 확인:

1. **서비스 다운?**
   - Prometheus: `up{job="spring-services"}` → 0인 서비스 확인
   - `kubectl get pods -n urr-spring`

2. **에러 급증?**
   - `sum(rate(http_server_requests_seconds_count{status=~"5.."}[5m])) by (service)`

3. **응답 느림?**
   - `http_server_requests_seconds_max` 확인
   - `hikaricp_connections_pending` → DB 커넥션 부족?
   - `redis_connected_clients` → Redis 커넥션 포화?

4. **Redis 문제?**
   - `redis_up` → 0이면 Redis 다운
   - `redis_memory_used_bytes / redis_config_maxmemory * 100` → 90% 이상이면 위험
   - `redis_evicted_keys_total` 증가 → 메모리 부족으로 키 강제 삭제 중

5. **Kafka 지연?**
   - `kafka_consumer_fetch_manager_records_lag_max` → 높으면 메시지 처리 지연

6. **대기열 이상?**
   - `resilience4j_circuitbreaker_state{name="redisQueue",state="open"}` → 1이면 서킷 열림 (Redis 장애)
   - Loki: `{app="queue-service"} |= "ERROR"`

---

## 8. 모니터링 아키텍처

```
Spring Services (8개)
  │ /actuator/prometheus
  ▼
Prometheus ◀─── Redis Exporter (Dragonfly 사이드카)
  │                │
  │                └─ redis_* 메트릭
  ▼
Grafana ◀─── Loki ◀─── Promtail (각 노드 로그 수집)
  │
  └─ 대시보드, 쿼리, 알림

Spring Services (8개)
  │ Micrometer Tracing
  ▼
Zipkin ─── 분산 추적
```

**메트릭 수집 주기**: 15초 (Prometheus scrape_interval)
**로그 수집**: 실시간 (Promtail → Loki)
**추적 샘플링**: 100% (Kind 환경, 프로덕션에서는 10% 권장)

---
---

# Part 2: AWS 프로덕션 환경

AWS에 배포하면 Kind와 달리 2계층 모니터링이 된다:
- **AWS 네이티브**: CloudWatch (AWS 인프라 자동 수집) + X-Ray (Lambda 추적)
- **K8s 클러스터 내부**: Prometheus + Grafana + Loki (Spring 서비스 메트릭/로그)

```
┌─────────────────────────────────────────────────────┐
│                    AWS 프로덕션                       │
│                                                      │
│  ┌─ EKS Cluster ──────────────────────────────────┐ │
│  │                                                 │ │
│  │  Spring Services (8개)                         │ │
│  │    ├─ /actuator/prometheus → Prometheus         │ │
│  │    ├─ stdout → Promtail → Loki                 │ │
│  │    └─ Brave traces → Zipkin (ES backend)       │ │
│  │                                                 │ │
│  │  Prometheus → Grafana (대시보드)                │ │
│  │  Grafana ← CloudWatch (데이터소스 추가)        │ │
│  │                                                 │ │
│  └─────────────────────────────────────────────────┘ │
│                                                      │
│  ┌─ AWS Managed Services ─────────────────────────┐ │
│  │  RDS        → CloudWatch (CPU, 커넥션, IOPS)   │ │
│  │  ElastiCache → CloudWatch (메모리, hit율)       │ │
│  │  MSK(Kafka) → CloudWatch (lag, 파티션)         │ │
│  │  ALB        → CloudWatch (응답시간, 5xx)       │ │
│  │  Lambda     → CloudWatch + X-Ray              │ │
│  │  CloudFront → CloudWatch (에러율, 캐시)        │ │
│  │  SQS        → CloudWatch (큐 깊이, DLQ)       │ │
│  └─────────────────────────────────────────────────┘ │
│                                                      │
│  CloudWatch Alarms → SNS → Slack/Email 알림         │
│  Grafana Alerts → Slack/Discord/Email 알림          │
│                                                      │
└─────────────────────────────────────────────────────┘
```

---

## 9. 모니터링 스택 배포 방법

Kind에서는 YAML로 직접 배포했지만, AWS에서는 **kube-prometheus-stack** Helm 차트를 사용한다.

### 9.1 Helm 설치 (로컬 머신)

```bash
# Helm 설치 (Windows)
choco install kubernetes-helm

# 또는 scoop
scoop install helm
```

### 9.2 모니터링 스택 배포

```bash
# Helm repo 추가
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm repo update

# namespace 생성
kubectl create namespace monitoring

# kube-prometheus-stack 설치 (Prometheus + Grafana + AlertManager 한방에)
helm install kube-prometheus-stack prometheus-community/kube-prometheus-stack \
  --namespace monitoring \
  -f k8s/spring/overlays/prod/monitoring-values.yaml
```

이미 준비된 values 파일 (`k8s/spring/overlays/prod/monitoring-values.yaml`):
```yaml
prometheus:
  prometheusSpec:
    retention: 30d              # 메트릭 30일 보관
    storageSpec:                # EBS gp3 볼륨에 저장
      volumeClaimTemplate:
        spec:
          storageClassName: gp3
          resources:
            requests:
              storage: 50Gi

grafana:
  persistence:
    enabled: true
    size: 10Gi
  adminPassword: ${GRAFANA_ADMIN_PASSWORD}   # 실제 배포 시 시크릿으로

alertmanager:
  alertmanagerSpec:
    storage:
      volumeClaimTemplate:
        spec:
          storageClassName: gp3
          resources:
            requests:
              storage: 5Gi
```

### 9.3 Grafana 접속 (AWS)

Helm 배포 후 접속하는 법:

```bash
# 방법 1: port-forward (간단, 로컬에서만)
kubectl port-forward -n monitoring svc/kube-prometheus-stack-grafana 3000:80

# 브라우저에서 http://localhost:3000 접속
# ID: admin / PW: monitoring-values.yaml에 설정한 값
```

```bash
# 방법 2: Ingress로 도메인 연결 (팀 공유)
# monitoring-values.yaml에 추가:
grafana:
  ingress:
    enabled: true
    hosts:
      - grafana.urr.example.com
    annotations:
      kubernetes.io/ingress.class: alb
      alb.ingress.kubernetes.io/scheme: internal   # 내부망만
```

---

## 10. AWS CloudWatch — 이미 자동 수집되는 것들

AWS Managed Service를 쓰면 CloudWatch가 자동으로 메트릭을 수집한다.
Terraform으로 인프라가 배포되면 별도 설정 없이 바로 볼 수 있다.

### 10.1 CloudWatch 콘솔 열기

1. AWS Console 로그인 → https://console.aws.amazon.com
2. 상단 검색창에 `CloudWatch` 입력 → 클릭
3. 좌측 메뉴 **Metrics** → **All metrics**

### 10.2 서비스별 CloudWatch 메트릭

#### RDS (PostgreSQL)

**경로**: CloudWatch → Metrics → RDS

| 메트릭 | 의미 | 위험 기준 |
|--------|------|-----------|
| `CPUUtilization` | DB CPU 사용률 | > 80% |
| `DatabaseConnections` | 현재 연결 수 | max의 80% 이상 |
| `FreeStorageSpace` | 남은 디스크 | < 10% |
| `ReadLatency` / `WriteLatency` | 읽기/쓰기 지연 | > 10ms |
| `FreeableMemory` | 사용 가능 메모리 | < 500MB |

**보는 법**:
1. CloudWatch → Metrics → RDS
2. **Per-Database Metrics** 클릭
3. 원하는 메트릭 체크박스 선택
4. 상단에서 시간 범위 선택 (1h, 3h, 12h, 1d, 1w)

**추가로 활성화된 기능**:
- **Performance Insights**: RDS 콘솔 → DB 선택 → **Performance Insights** 탭
  - 쿼리별 대기 시간, 가장 느린 쿼리 Top 10 확인 가능
- **Slow Query Log**: `log_min_duration_statement = 1000` (1초 이상 쿼리 기록)
  - CloudWatch → Log groups → `/aws/rds/instance/urr-prod/postgresql`

#### ElastiCache (Redis)

**경로**: CloudWatch → Metrics → ElastiCache

| 메트릭 | 의미 | 위험 기준 |
|--------|------|-----------|
| `CPUUtilization` | Redis CPU | > 75% |
| `DatabaseMemoryUsagePercentage` | 메모리 사용률 | > 85% |
| `CurrConnections` | 현재 연결 수 | max에 근접 |
| `Evictions` | 강제 삭제된 키 수 | > 0 (메모리 부족) |
| `CacheHitRate` | 캐시 히트율 | < 80% |
| `ReplicationLag` | 복제 지연 | > 1초 |

**보는 법**:
1. CloudWatch → Metrics → ElastiCache
2. **Redis OSS Metrics** 클릭
3. 클러스터 ID로 필터링

**추가 로그**:
- CloudWatch → Log groups → `/aws/elasticache/redis/slow-log` (느린 커맨드)
- CloudWatch → Log groups → `/aws/elasticache/redis/engine-log` (엔진 이벤트)

#### MSK (Kafka)

**경로**: CloudWatch → Metrics → Kafka

| 메트릭 | 의미 | 위험 기준 |
|--------|------|-----------|
| `ActiveControllerCount` | 활성 컨트롤러 | < 1이면 장애 |
| `OfflinePartitionsCount` | 오프라인 파티션 | > 0이면 장애 |
| `UnderReplicatedPartitions` | 복제 부족 파티션 | > 0 |
| `BytesInPerSec` / `BytesOutPerSec` | 트래픽 | 급증 감시 |
| `ConsumerLag` | 소비 지연 | 지속 증가 |
| `PartitionCount` | 파티션 수 | 확인용 |

**보는 법**:
1. CloudWatch → Metrics → Kafka
2. **Per Broker Metrics** 또는 **Per Topic Per Broker** 클릭
3. Enhanced Monitoring이 `PER_TOPIC_PER_BROKER`로 설정되어 있어 토픽별 상세 확인 가능

**추가 로그**:
- CloudWatch → Log groups → `/aws/msk/urr-prod`

#### ALB (로드밸런서)

**경로**: CloudWatch → Metrics → ApplicationELB

| 메트릭 | 의미 | 위험 기준 |
|--------|------|-----------|
| `RequestCount` | 요청 수 | 트래픽 추이 |
| `TargetResponseTime` | 백엔드 응답 시간 | > 1초 |
| `HTTPCode_Target_5XX_Count` | 5xx 에러 수 | > 0 |
| `HTTPCode_ELB_5XX_Count` | ALB 자체 에러 | > 0 |
| `UnHealthyHostCount` | 비정상 타겟 | > 0 |
| `ActiveConnectionCount` | 활성 연결 | 급증 감시 |

#### Lambda

**경로**: CloudWatch → Metrics → Lambda

| 메트릭 | 의미 | 위험 기준 |
|--------|------|-----------|
| `Invocations` | 실행 횟수 | 추이 확인 |
| `Duration` | 실행 시간 | 타임아웃의 80% |
| `Errors` | 에러 횟수 | > 0 |
| `Throttles` | 스로틀 횟수 | > 0 |
| `ConcurrentExecutions` | 동시 실행 수 | 한도 근접 |

**X-Ray 추적 (Lambda에서 활성화됨)**:
1. AWS Console → X-Ray → **Traces** 클릭
2. Lambda 함수별 호출 체인, 지연 시간 확인 가능
3. **Service Map** → Lambda ↔ DynamoDB ↔ SQS 간 호출 관계 시각화

#### CloudFront

**경로**: CloudWatch → Metrics → CloudFront

| 메트릭 | 의미 | 위험 기준 |
|--------|------|-----------|
| `Requests` | 총 요청 수 | 트래픽 추이 |
| `4xxErrorRate` | 4xx 비율 | > 5% |
| `5xxErrorRate` | 5xx 비율 | > 1% |
| `TotalErrorRate` | 전체 에러 | > 5% |
| `BytesDownloaded` | 다운로드량 | 비용 모니터링 |
| `CacheHitRate` | 캐시 히트율 | < 80% |

#### SQS

**경로**: CloudWatch → Metrics → SQS

| 메트릭 | 의미 | 위험 기준 |
|--------|------|-----------|
| `ApproximateNumberOfMessagesVisible` | 대기 메시지 수 | 지속 증가 |
| `ApproximateAgeOfOldestMessage` | 가장 오래된 메시지 나이 | > 10분 |
| `NumberOfMessagesSent` | 전송 수 | 추이 확인 |
| `NumberOfMessagesReceived` | 수신 수 | 추이 확인 |
| **DLQ** `ApproximateNumberOfMessagesVisible` | DLQ 메시지 수 | > 0이면 즉시 확인 |

---

## 11. CloudWatch 알람 — 이미 Terraform으로 설정된 것

Terraform 배포 시 자동 생성되는 알람 (`enable_cloudwatch_alarms = true`):

| 대상 | 알람 | 조건 | 알림 |
|------|------|------|------|
| Lambda Worker | Errors | 5분간 에러 > 5회 (2회 연속) | SNS → 이메일/Slack |
| Lambda Worker | Duration | 평균 실행시간 > 타임아웃의 80% | SNS |
| Lambda Worker | Throttles | 스로틀 > 0 | SNS |
| SQS DLQ | Messages | DLQ에 메시지 > 0 | SNS |
| SQS Queue | Age | 가장 오래된 메시지 > 10분 | SNS |
| MSK Kafka | Controller | 활성 컨트롤러 < 1 | SNS |
| MSK Kafka | Partitions | 오프라인 파티션 > 0 | SNS |

### 알람 확인하는 법

1. AWS Console → CloudWatch
2. 좌측 **Alarms** → **All alarms** 클릭
3. 상태별 필터:
   - **OK** (초록): 정상
   - **ALARM** (빨강): 문제 발생 중
   - **INSUFFICIENT_DATA** (회색): 데이터 부족 (배포 직후)

### SNS 알림 설정 (이메일/Slack 연동)

알람이 울려도 알림을 받을 곳을 설정해야 의미가 있다:

```bash
# 1. SNS 토픽 생성 (Terraform으로 이미 변수 준비됨)
# terraform/environments/prod/variables.tf → sns_topic_arn

# 2. 이메일 구독 추가
aws sns subscribe \
  --topic-arn arn:aws:sns:ap-northeast-2:123456789:urr-prod-alarms \
  --protocol email \
  --notification-endpoint your-email@example.com

# 3. 이메일 확인 메일의 Confirm 링크 클릭
```

**Slack 연동**:
1. Slack에서 Incoming Webhook 생성 (채널 설정 → Integrations → Webhook)
2. AWS Chatbot 서비스 사용 또는 Lambda를 통해 SNS → Slack 전달
3. 간단한 방법: https://github.com/aws-samples/aws-sns-to-slack-publisher

---

## 12. Grafana에서 CloudWatch 보기

Grafana에 CloudWatch 데이터소스를 추가하면 **한 화면에서 모든 것**을 볼 수 있다.

### 12.1 데이터소스 추가

1. Grafana 로그인
2. 좌측 **톱니바퀴** → **Data sources** → **Add data source**
3. **CloudWatch** 선택
4. 설정:
   - **Default Region**: `ap-northeast-2`
   - **Authentication Provider**: `AWS SDK Default` (EKS IRSA 사용 시)
   - 또는 Access Key / Secret Key 직접 입력
5. **Save & test** 클릭

### 12.2 CloudWatch 쿼리 예시 (Grafana에서)

데이터소스를 CloudWatch로 선택 후:

```
# RDS CPU 사용률
Namespace: AWS/RDS
Metric: CPUUtilization
Dimension: DBInstanceIdentifier = urr-prod

# ElastiCache 메모리 사용률
Namespace: AWS/ElastiCache
Metric: DatabaseMemoryUsagePercentage
Dimension: CacheClusterId = urr-prod-redis

# ALB 응답 시간
Namespace: AWS/ApplicationELB
Metric: TargetResponseTime
Statistic: p95
Dimension: LoadBalancer = <ALB ARN suffix>

# Lambda 에러 수
Namespace: AWS/Lambda
Metric: Errors
Dimension: FunctionName = urr-prod-vwr-worker
```

### 12.3 통합 대시보드 예시

Grafana에서 Prometheus + CloudWatch를 섞어서 대시보드를 만들 수 있다:

| Row | 패널 | 데이터소스 | 쿼리 |
|-----|------|-----------|------|
| 서비스 상태 | UP/DOWN | Prometheus | `up{job="spring-services"}` |
| HTTP 트래픽 | 요청수/에러 | Prometheus | `rate(http_server_requests_seconds_count[5m])` |
| DB 상태 | RDS CPU | CloudWatch | `AWS/RDS CPUUtilization` |
| DB 상태 | 커넥션 수 | CloudWatch | `AWS/RDS DatabaseConnections` |
| Redis 상태 | 메모리 | CloudWatch | `AWS/ElastiCache DatabaseMemoryUsagePercentage` |
| Redis 상태 | 히트율 | CloudWatch | `AWS/ElastiCache CacheHitRate` |
| Kafka | Consumer Lag | Prometheus | `kafka_consumer_fetch_manager_records_lag_max` |
| Kafka | 파티션 상태 | CloudWatch | `AWS/Kafka OfflinePartitionsCount` |
| 대기열 | 큐 깊이 | CloudWatch | `AWS/SQS ApproximateNumberOfMessagesVisible` |
| Lambda | 에러/지연 | CloudWatch | `AWS/Lambda Errors`, `Duration` |

---

## 13. Kind vs AWS 모니터링 차이 요약

| 항목 | Kind (로컬) | AWS (프로덕션) |
|------|------------|---------------|
| **메트릭 수집** | Prometheus (직접 배포) | kube-prometheus-stack (Helm) + CloudWatch |
| **대시보드** | Grafana (NodePort :30006) | Grafana (Ingress 또는 port-forward) |
| **로그** | Loki + Promtail | Loki + Promtail + CloudWatch Logs |
| **추적** | Zipkin (메모리 저장) | Zipkin (Elasticsearch 백엔드) 또는 X-Ray |
| **Redis 모니터링** | Redis Exporter (사이드카) | CloudWatch (ElastiCache 내장) + Redis Exporter |
| **DB 모니터링** | HikariCP 메트릭만 | CloudWatch (RDS) + Performance Insights + HikariCP |
| **Kafka 모니터링** | Micrometer Kafka 메트릭 | CloudWatch (MSK Enhanced) + Micrometer |
| **알림** | Grafana Alert (로컬) | CloudWatch Alarms → SNS + Grafana Alerts |
| **추적 샘플링** | 100% | 10% (`TRACING_SAMPLING_PROBABILITY=0.1`) |
| **메트릭 보관** | 7일 | 30일 (Prometheus) + 15개월 (CloudWatch) |
| **접속** | localhost:30006/30090/30411 | kubectl port-forward 또는 Ingress 도메인 |

---

## 14. AWS 배포 후 체크리스트

배포 직후 확인할 것들:

### 1단계: 인프라 확인

- [ ] CloudWatch → Alarms → 모든 알람이 **OK** 또는 **INSUFFICIENT_DATA** (ALARM 없어야 함)
- [ ] EKS 콘솔 → 모든 노드 Ready
- [ ] `kubectl get pods -n urr-spring` → 모든 팟 Running

### 2단계: 모니터링 스택 확인

- [ ] `kubectl get pods -n monitoring` → Prometheus, Grafana, AlertManager Running
- [ ] Grafana 접속 → 로그인 성공
- [ ] Grafana → Data sources → Prometheus **Working** 표시
- [ ] Grafana → Data sources → CloudWatch **Working** 표시 (추가한 경우)
- [ ] Prometheus → Status → Targets → 모든 서비스 UP

### 3단계: 알림 확인

- [ ] SNS 토픽에 이메일/Slack 구독 완료
- [ ] 테스트 알림 전송: CloudWatch → Alarms → 아무 알람 → **Actions** → **Test** (없으면 SNS 콘솔에서 Publish)
- [ ] 이메일/Slack에 알림 수신 확인

### 4단계: 주요 지표 베이스라인

트래픽이 들어온 후 정상 상태 기준값을 기록:
- [ ] 평균 응답 시간: ___ms
- [ ] RDS CPU 평균: ___%
- [ ] Redis 메모리 사용: ___%
- [ ] Kafka Consumer Lag: ___
- [ ] 시간당 에러율: ___건
