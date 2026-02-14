# 인프라 분석

URR 티켓팅 플랫폼(Tiketi)의 인프라 구성을 분석한 문서이다. 로컬 개발 환경(Kind 클러스터, Docker Compose)부터 프로덕션 환경(AWS EKS, Terraform)까지 전체 인프라 스택을 다룬다.

---

## 1. Kind 클러스터 구성

Kind(Kubernetes in Docker) 클러스터를 사용하여 로컬 개발 환경에서 Kubernetes를 시뮬레이션한다.

**출처**: `kind-config.yaml:1-35`

### 노드 구성

| 역할 | 수량 | 레이블 | 설명 |
|------|------|--------|------|
| control-plane | 1 | `ingress-ready=true` | 클러스터 제어, 포트 매핑 호스트 |
| worker | 1 | `workload=application` | 애플리케이션 서비스 실행 |
| worker | 1 | `workload=data` | 데이터 인프라(DB, Redis, Kafka) 실행 |

### 포트 매핑

control-plane 노드에서 호스트로 포워딩되는 포트 목록이다.

| 용도 | containerPort | hostPort | 프로토콜 |
|------|---------------|----------|----------|
| Frontend | 30005 | 3000 | TCP |
| Backend API (Gateway) | 30000 | 3001 | TCP |
| Grafana Dashboard | 30006 | 3006 | TCP |
| PostgreSQL (디버깅용) | 30432 | 15432 | TCP |

```yaml
# kind-config.yaml:1-35
kind: Cluster
apiVersion: kind.x-k8s.io/v1alpha4
name: tiketi-local
nodes:
  - role: control-plane
    kubeadmConfigPatches:
      - |
        kind: InitConfiguration
        nodeRegistration:
          kubeletExtraArgs:
            node-labels: "ingress-ready=true"
    extraPortMappings:
      - containerPort: 30000
        hostPort: 3001
        protocol: TCP
      - containerPort: 30005
        hostPort: 3000
        protocol: TCP
      - containerPort: 30006
        hostPort: 3006
        protocol: TCP
      - containerPort: 30432
        hostPort: 15432
        protocol: TCP
  - role: worker
    labels:
      workload: application
  - role: worker
    labels:
      workload: data
```

---

## 2. Kubernetes 구조 (Kustomize)

Kustomize를 사용하여 base/overlay 패턴으로 환경별 설정을 관리한다.

### 2.1 Base 구성

모든 환경에서 공통으로 사용되는 리소스를 정의한다.

**출처**: `k8s/spring/base/kustomization.yaml:1-24`

- **Namespace**: `tiketi-dev`
- **서비스 수**: 9개 (backend 8 + frontend 1)

```yaml
# k8s/spring/base/kustomization.yaml:1-24
apiVersion: kustomize.config.k8s.io/v1beta1
kind: Kustomization
namespace: tiketi-dev
resources:
  - gateway-service/deployment.yaml
  - gateway-service/service.yaml
  - ticket-service/deployment.yaml
  - ticket-service/service.yaml
  - payment-service/deployment.yaml
  - payment-service/service.yaml
  - stats-service/deployment.yaml
  - stats-service/service.yaml
  - auth-service/deployment.yaml
  - auth-service/service.yaml
  - queue-service/deployment.yaml
  - queue-service/service.yaml
  - catalog-service/deployment.yaml
  - catalog-service/service.yaml
  - community-service/deployment.yaml
  - community-service/service.yaml
  - frontend/deployment.yaml
  - frontend/service.yaml
  - network-policies.yaml
```

**리소스 목록**:

| 서비스 | Deployment | Service |
|--------|-----------|---------|
| gateway-service | O | O |
| ticket-service | O | O |
| payment-service | O | O |
| stats-service | O | O |
| auth-service | O | O |
| queue-service | O | O |
| catalog-service | O | O |
| community-service | O | O |
| frontend | O | O |

추가로 `network-policies.yaml`에서 네임스페이스 내 네트워크 정책을 정의한다.

### 2.2 Kind Overlay

로컬 Kind 클러스터 전용 오버레이 설정이다.

**출처**: `k8s/spring/overlays/kind/kustomization.yaml:1-73`

- **Namespace**: `tiketi-spring` (base의 `tiketi-dev`를 오버라이드)
- **추가 리소스**: PostgreSQL, Dragonfly(Redis), Kafka, Zipkin, PVC, Loki, Promtail, Grafana, Prometheus

```yaml
# k8s/spring/overlays/kind/kustomization.yaml:1-17
namespace: tiketi-spring

resources:
  - namespace.yaml
  - ../../base
  - postgres.yaml
  - dragonfly.yaml
  - kafka.yaml
  - zipkin.yaml
  - pvc.yaml
  - loki.yaml
  - promtail.yaml
  - grafana.yaml
  - grafana-dashboards.yaml
  - prometheus.yaml
```

**ConfigMap/Secret 생성**:

```yaml
# k8s/spring/overlays/kind/kustomization.yaml:19-30
configMapGenerator:
  - name: spring-kind-config
    envs:
      - config.env

secretGenerator:
  - name: spring-kind-secret
    envs:
      - secrets.env

generatorOptions:
  disableNameSuffixHash: true
```

**이미지 치환**: ECR URI를 로컬 이미지명으로 대체한다.

```yaml
# k8s/spring/overlays/kind/kustomization.yaml:45-72
images:
  - name: YOUR_ECR_URI/gateway-service
    newName: tiketi-spring-gateway-service
    newTag: local
  - name: YOUR_ECR_URI/auth-service
    newName: tiketi-spring-auth-service
    newTag: local
  # ... 8개 서비스 + frontend 총 9개 이미지 치환
```

**서비스 패치**: 각 서비스별 패치 파일과 NodePort 패치를 적용한다.

```yaml
# k8s/spring/overlays/kind/kustomization.yaml:32-43
patches:
  - path: patches/gateway-service.yaml
  - path: patches/auth-service.yaml
  - path: patches/ticket-service.yaml
  - path: patches/payment-service.yaml
  - path: patches/stats-service.yaml
  - path: patches/queue-service.yaml
  - path: patches/catalog-service.yaml
  - path: patches/community-service.yaml
  - path: patches/gateway-service-nodeport.yaml
  - path: patches/frontend-service-nodeport.yaml
  - path: patches/frontend.yaml
```

### 2.3 Production Overlay

프로덕션 환경 전용 오버레이이다.

**출처**: `k8s/spring/overlays/prod/kustomization.yaml:1-28`

```yaml
# k8s/spring/overlays/prod/kustomization.yaml:1-28
apiVersion: kustomize.config.k8s.io/v1beta1
kind: Kustomization
namespace: tiketi-spring

resources:
  - ../../base
  - pdb.yaml
  - hpa.yaml
  - kafka.yaml
  - redis.yaml

configMapGenerator:
  - name: spring-prod-config
    envs:
      - config.env

secretGenerator:
  - name: spring-prod-secret
    envs:
      - secrets.env

patches:
  - path: patches/replicas.yaml
  - path: patches/services-env.yaml
```

#### HPA (Horizontal Pod Autoscaler)

CPU 사용률 70% 기준으로 자동 스케일링한다.

**출처**: `k8s/spring/overlays/prod/hpa.yaml:1-76`

| 서비스 | minReplicas | maxReplicas | CPU 임계값 |
|--------|-------------|-------------|-----------|
| gateway-service | 3 | 10 | 70% |
| ticket-service | 3 | 10 | 70% |
| queue-service | 3 | 8 | 70% |
| payment-service | 2 | 6 | 70% |

```yaml
# k8s/spring/overlays/prod/hpa.yaml:1-18 (gateway-service 예시)
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: gateway-service
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: gateway-service
  minReplicas: 3
  maxReplicas: 10
  metrics:
    - type: Resource
      resource:
        name: cpu
        target:
          type: Utilization
          averageUtilization: 70
```

#### PDB (PodDisruptionBudget)

모든 핵심 서비스에 `minAvailable: 1`을 설정하여 유지보수 중에도 최소 1개 Pod가 가용하도록 보장한다.

**출처**: `k8s/spring/overlays/prod/pdb.yaml:1-70`

| PDB 이름 | 대상 서비스 | minAvailable |
|----------|-----------|-------------|
| gateway-service-pdb | gateway-service | 1 |
| ticket-service-pdb | ticket-service | 1 |
| queue-service-pdb | queue-service | 1 |
| payment-service-pdb | payment-service | 1 |
| auth-service-pdb | auth-service | 1 |
| stats-service-pdb | stats-service | 1 |
| community-service-pdb | community-service | 1 |

---

## 3. 서비스 Deployment 패턴

gateway-service를 대표 사례로 Deployment 패턴을 분석한다.

**출처**: `k8s/spring/base/gateway-service/deployment.yaml:1-58`

### 보안 컨텍스트

Pod 레벨과 컨테이너 레벨에서 이중 보안을 적용한다.

```yaml
# k8s/spring/base/gateway-service/deployment.yaml:18-31
spec:
  securityContext:
    runAsNonRoot: true       # root 실행 금지
    runAsUser: 1000          # UID 1000으로 실행
    fsGroup: 1000            # 파일시스템 그룹
  containers:
    - name: gateway-service
      securityContext:
        allowPrivilegeEscalation: false  # 권한 상승 금지
        readOnlyRootFilesystem: false
        capabilities:
          drop:
            - ALL                        # 모든 Linux capability 제거
```

### 리소스 제한

```yaml
# k8s/spring/base/gateway-service/deployment.yaml:51-57
resources:
  requests:
    cpu: "200m"
    memory: "256Mi"
  limits:
    cpu: "1"
    memory: "1Gi"
```

### 헬스 프로브

```yaml
# k8s/spring/base/gateway-service/deployment.yaml:39-50
readinessProbe:
  httpGet:
    path: /health
    port: 3001
  initialDelaySeconds: 10
  periodSeconds: 10
livenessProbe:
  httpGet:
    path: /health
    port: 3001
  initialDelaySeconds: 20
  periodSeconds: 20
```

### 네트워크 정책

base에 정의된 `network-policies.yaml`에서 기본 거부 정책과 서비스별 허용 규칙을 설정한다.

**출처**: `k8s/spring/base/network-policies.yaml:1-226`

- **기본 정책**: 모든 Ingress/Egress 거부 (`default-deny-all`, 라인 1-9)
- **Gateway**: 외부에서 포트 3001 수신 허용 (라인 11-23)
- **Backend 서비스**: gateway-service 등 특정 Pod에서만 수신 허용
- **Egress**: `tier: backend` 레이블 Pod는 같은 네임스페이스 내 통신 및 DNS 허용 (라인 178-201)

```yaml
# k8s/spring/base/network-policies.yaml:1-9 (기본 거부 정책)
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: default-deny-all
spec:
  podSelector: {}
  policyTypes:
    - Ingress
    - Egress
```

---

## 4. Dockerfile 패턴

모든 Spring Boot 서비스가 동일한 멀티 스테이지 빌드 패턴을 사용한다.

**출처**: `services-spring/gateway-service/Dockerfile:1-22`

### 빌드 스테이지

```dockerfile
# services-spring/gateway-service/Dockerfile:1-11
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

COPY gradlew gradlew
COPY gradlew.bat gradlew.bat
COPY gradle gradle
COPY settings.gradle settings.gradle
COPY build.gradle build.gradle
COPY src src

RUN chmod +x gradlew && ./gradlew --no-daemon clean bootJar
```

- **베이스 이미지**: `eclipse-temurin:21-jdk` (빌드 전용)
- **빌드 도구**: Gradle Wrapper
- **산출물**: `build/libs/*.jar` (Spring Boot fat JAR)

### 런타임 스테이지

```dockerfile
# services-spring/gateway-service/Dockerfile:13-22
FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=build /workspace/build/libs/*.jar app.jar

RUN addgroup --system --gid 1001 app && adduser --system --uid 1001 --ingroup app app
USER app

EXPOSE 3001
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
```

- **베이스 이미지**: `eclipse-temurin:21-jre` (JRE만 포함, 이미지 크기 절감)
- **비루트 사용자**: `app` (UID 1001, GID 1001)
- **포트**: 서비스별 상이 (gateway: 3001)

---

## 5. Docker Compose 로컬 개발

Kind 클러스터 없이 로컬에서 개발할 때 사용하는 인프라 컨테이너 구성이다.

**출처**: `services-spring/docker-compose.databases.yml:1-74`

### PostgreSQL 인스턴스

| 서비스명 | 데이터베이스 | 호스트 포트 | 컨테이너 포트 |
|----------|------------|-----------|-------------|
| auth-db | auth_db | 5438 | 5432 |
| ticket-db | ticket_db | 5434 | 5432 |
| payment-db | payment_db | 5435 | 5432 |
| stats-db | stats_db | 5436 | 5432 |
| community-db | community_db | 5437 | 5432 |

모든 인스턴스에서 동일한 자격 증명을 사용한다: `tiketi_user` / `tiketi_password`

### 공유 인프라

| 서비스 | 이미지 | 호스트 포트 |
|--------|--------|-----------|
| Redis | `redis:7` | 6379 |
| Zipkin | `openzipkin/zipkin:3` | 9411 |
| Kafka | `apache/kafka:3.7.0` | 9092 |

### Kafka 구성 (로컬)

```yaml
# services-spring/docker-compose.databases.yml:57-74
kafka:
  image: apache/kafka:3.7.0
  environment:
    KAFKA_NODE_ID: 1
    KAFKA_PROCESS_ROLES: broker,controller   # KRaft 모드 (ZooKeeper 미사용)
    KAFKA_LISTENERS: PLAINTEXT://0.0.0.0:9092,CONTROLLER://0.0.0.0:9093
    KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://localhost:9092
    KAFKA_CONTROLLER_QUORUM_VOTERS: 1@kafka:9093
    KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
    CLUSTER_ID: MkU3OEVBNTcwNTJENDM2Qk
```

---

## 6. 데이터 인프라

### 6.1 PostgreSQL

#### Kind 환경 (단일 인스턴스)

Kind 환경에서는 하나의 PostgreSQL 인스턴스에 5개 데이터베이스를 생성한다.

**출처**: `k8s/spring/overlays/kind/postgres.yaml:1-91`

**Init 스크립트** (ConfigMap):

```sql
-- k8s/spring/overlays/kind/postgres.yaml:7-11
CREATE DATABASE auth_db;
CREATE DATABASE ticket_db;
CREATE DATABASE payment_db;
CREATE DATABASE stats_db;
CREATE DATABASE community_db;
```

**Deployment 특징**:
- 이미지: `postgres:15-alpine`
- Replicas: 1
- 자격 증명: Secret `spring-kind-secret`에서 주입
- 볼륨: PVC `postgres-pvc`에 데이터 영속화
- Init 스크립트: ConfigMap `postgres-init-spring`을 `/docker-entrypoint-initdb.d`에 마운트
- 헬스 프로브: `pg_isready` 명령 사용

**Service**: NodePort 타입, `nodePort: 30432` (호스트의 15432로 매핑)

```yaml
# k8s/spring/overlays/kind/postgres.yaml:76-91
apiVersion: v1
kind: Service
metadata:
  name: postgres-spring
spec:
  type: NodePort
  selector:
    app: postgres-spring
  ports:
    - name: postgres
      port: 5432
      targetPort: 5432
      nodePort: 30432
```

### 6.2 Redis

#### Kind 환경 (Dragonfly)

개발 환경에서는 Redis 호환 인메모리 스토어인 Dragonfly를 사용한다.

**출처**: `k8s/spring/overlays/kind/dragonfly.yaml:1-63`

```yaml
# k8s/spring/overlays/kind/dragonfly.yaml:17-26
containers:
  - name: dragonfly
    image: docker.dragonflydb.io/dragonflydb/dragonfly:latest
    args:
      - --maxmemory=512mb
      - --proactor_threads=1
      - --snapshot_cron=* * * * *
      - --dir=/data
      - --dbfilename=dump
```

- **이미지**: `dragonflydb/dragonfly:latest`
- **메모리 제한**: 512MB
- **스냅샷**: 매분 자동 저장
- **Replicas**: 1
- **Service**: ClusterIP, 포트 6379

#### Production 환경 (Redis 6-노드 클러스터)

프로덕션에서는 Redis 클러스터 모드(3 master + 3 replica)를 운영한다.

**출처**: `k8s/spring/overlays/prod/redis.yaml:1-207`

**ConfigMap** (라인 1-23):

```conf
# k8s/spring/overlays/prod/redis.yaml:8-23
cluster-enabled yes
cluster-config-file nodes.conf
cluster-node-timeout 5000
appendonly yes
appendfsync everysec
maxmemory 200mb
maxmemory-policy allkeys-lru
save 900 1
save 300 10
save 60 10000
tcp-keepalive 60
```

**StatefulSet** (라인 62-151):
- **이미지**: `redis:7.2-alpine`
- **Replicas**: 6 (3 master + 3 replica, `--cluster-replicas 1`)
- **podManagementPolicy**: `Parallel` (빠른 시작)
- **Anti-affinity**: `preferredDuringSchedulingIgnoredDuringExecution` (노드 분산)
- **리소스**: requests 100m/128Mi, limits 250m/256Mi
- **스토리지**: PVC 1Gi per Pod (`ReadWriteOnce`)

**클러스터 초기화 Job** (라인 154-207):
- 6개 Pod 준비 대기 후 `redis-cli --cluster create` 실행
- 이미 클러스터가 형성된 경우 건너뜀
- `ttlSecondsAfterFinished: 300` (완료 후 5분 뒤 자동 삭제)

### 6.3 Kafka

#### Kind 환경 (단일 노드 KRaft)

**출처**: `k8s/spring/overlays/kind/kafka.yaml:1-84`

```yaml
# k8s/spring/overlays/kind/kafka.yaml:19-47
image: apache/kafka:3.7.0
env:
  - name: KAFKA_NODE_ID
    value: "1"
  - name: KAFKA_PROCESS_ROLES
    value: "broker,controller"      # KRaft 모드: 단일 노드가 broker+controller
  - name: KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR
    value: "1"
  - name: KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR
    value: "1"
```

- **Replicas**: 1
- **모드**: KRaft (ZooKeeper 미사용)
- **리소스**: requests 200m/512Mi, limits 1/1Gi
- **프로브**: startupProbe(failureThreshold=15), readinessProbe, livenessProbe 모두 TCP 9092

#### Production 환경 (3노드 StatefulSet)

**출처**: `k8s/spring/overlays/prod/kafka.yaml:1-131`

```yaml
# k8s/spring/overlays/prod/kafka.yaml:32-44
apiVersion: apps/v1
kind: StatefulSet
metadata:
  name: kafka-spring
spec:
  serviceName: kafka-spring-headless
  replicas: 3
  podManagementPolicy: Parallel
```

**핵심 설정** (라인 58-88):

| 설정 | 개발 | 프로덕션 |
|------|------|---------|
| Replicas | 1 | 3 |
| Replication Factor | 1 | 3 |
| Min ISR | 1 | 2 |
| Default Partitions | - | 6 |
| Rebalance Delay | 0ms | 3000ms |

```yaml
# k8s/spring/overlays/prod/kafka.yaml:71-88
- name: KAFKA_CONTROLLER_QUORUM_VOTERS
  value: "0@kafka-spring-0.kafka-spring-headless:9093,1@kafka-spring-1.kafka-spring-headless:9093,2@kafka-spring-2.kafka-spring-headless:9093"
- name: KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR
  value: "3"
- name: KAFKA_MIN_INSYNC_REPLICAS
  value: "2"
- name: KAFKA_NUM_PARTITIONS
  value: "6"
```

- **Node ID 자동 할당**: Pod ordinal에서 추출 (`${POD_NAME##*-}`)
- **리소스**: requests 500m/1Gi, limits 2/2Gi
- **스토리지**: PVC 20Gi per Pod
- **Headless Service**: `kafka-spring-headless` (Pod 간 직접 통신)

### 6.4 Zipkin (분산 트레이싱)

**출처**: `k8s/spring/overlays/kind/zipkin.yaml:1-40`

```yaml
# k8s/spring/overlays/kind/zipkin.yaml:16-26
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

- NodePort 30411로 외부 접근 가능

---

## 7. Terraform (AWS)

프로덕션 AWS 인프라를 Terraform으로 관리한다.

**출처**: `terraform/environments/prod/main.tf:1-126`, `terraform/environments/prod/variables.tf:1-130`

### 모듈 구조

```
terraform/
  modules/
    alb/            # Application Load Balancer
    cloudfront/     # CDN + Lambda@Edge
    eks/            # Elastic Kubernetes Service
    elasticache/    # Redis 클러스터
    iam/            # IAM 역할 및 정책
    lambda-worker/  # SQS 소비 Lambda
    rds/            # PostgreSQL (RDS)
    s3/             # 오브젝트 스토리지
    secrets/        # Secrets Manager
    sqs/            # FIFO 큐
    vpc/            # VPC 네트워크
    vpc-endpoints/  # VPC 엔드포인트
  environments/
    prod/           # 프로덕션 환경 설정
```

### Terraform Backend

```hcl
# terraform/environments/prod/main.tf:11-17
backend "s3" {
  bucket         = "tiketi-terraform-state-prod"
  key            = "prod/queue-infra/terraform.tfstate"
  region         = "ap-northeast-2"
  dynamodb_table = "tiketi-terraform-locks"
  encrypt        = true
}
```

- **State 저장소**: S3 + DynamoDB 잠금
- **리전**: `ap-northeast-2` (서울)
- **암호화**: 활성화

### 핵심 모듈

#### SQS FIFO Queue

**출처**: `terraform/environments/prod/main.tf:50-58`

```hcl
module "sqs" {
  source = "../../modules/sqs"
  name_prefix              = var.name_prefix
  allowed_sender_role_arns = var.eks_node_role_arns
  lambda_worker_role_arn   = var.lambda_worker_role_arn
  enable_cloudwatch_alarms = true
  sns_topic_arn            = var.sns_topic_arn
}
```

- EKS 노드에서 SQS 전송 권한 부여
- CloudWatch 알람 연동

#### CloudFront + Lambda@Edge

**출처**: `terraform/environments/prod/main.tf:64-81`

```hcl
module "cloudfront" {
  source = "../../modules/cloudfront"
  providers = {
    aws           = aws
    aws.us_east_1 = aws.us_east_1    # Lambda@Edge는 us-east-1 필수
  }
  name_prefix                    = var.name_prefix
  alb_dns_name                   = var.alb_dns_name
  lambda_edge_role_arn           = var.lambda_edge_role_arn
  lambda_source_dir              = "${path.root}/../../lambda/edge-queue-check"
  queue_entry_token_secret       = var.queue_entry_token_secret
  cloudfront_custom_header_value = var.cloudfront_custom_header_value
  price_class                    = "PriceClass_200"
}
```

- **Lambda@Edge**: 대기열 진입 토큰 검증 (`edge-queue-check`)
- **보안 헤더**: CloudFront -> ALB 커스텀 헤더 검증
- **가격 등급**: PriceClass_200 (아시아/유럽/북미)

#### Lambda Worker (SQS Consumer)

**출처**: `terraform/environments/prod/main.tf:87-125`

```hcl
module "lambda_worker" {
  source = "../../modules/lambda-worker"
  lambda_source_dir              = "${path.root}/../../lambda/ticket-worker"
  lambda_timeout                 = 30
  lambda_memory_size             = 256
  reserved_concurrent_executions = 10
  # VPC 내 배포 (RDS, Redis 접근)
  vpc_id      = var.vpc_id
  subnet_ids  = var.private_subnet_ids
  # SQS 트리거
  sqs_queue_arn               = module.sqs.queue_arn
  sqs_batch_size              = 10
  sqs_batching_window_seconds = 5
  max_concurrency             = 10
  # X-Ray + CloudWatch
  enable_xray_tracing      = true
  enable_cloudwatch_alarms = true
}
```

- **기능**: SQS에서 티켓 처리 요청을 배치로 소비
- **VPC 배포**: Private subnet에서 RDS Proxy, Redis 접근
- **동시성**: 최대 10 (reserved)
- **배치**: 10건/5초 윈도우

### 보안 아키텍처

**출처**: `terraform/CLOUDFRONT_ALB_SECURITY.md`

| 보안 계층 | 상태 | 설명 |
|----------|------|------|
| Security Group (ALB) | 구현됨 | CloudFront Prefix List만 허용 |
| Custom Header 검증 | 미구현 (선택) | CloudFront -> ALB 커스텀 헤더 |
| AWS WAF | 미구현 (향후) | DDoS, Rate Limiting, Bot 차단 |

---

## 8. 배포 자동화 스크립트

### 8.1 spring-kind-up.ps1 (클러스터 생성 + 배포)

**출처**: `scripts/spring-kind-up.ps1:1-116`

실행 흐름:

1. **사전 조건 검증** (라인 30-41): `kind`, `kubectl`, `docker` 설치 확인
2. **클러스터 생성/확인** (라인 42-63): `-RecreateCluster` 플래그로 재생성 가능
3. **이미지 빌드** (라인 70-72): `-SkipBuild` 플래그로 건너뛰기 가능
4. **Kustomize 적용** (라인 74-78): `kubectl apply -k k8s/spring/overlays/kind`
5. **롤아웃 대기** (라인 80-104): 15개 Deployment의 rollout 완료 대기 (300초 타임아웃)

```powershell
# scripts/spring-kind-up.ps1:81-96 (대기 대상 Deployment 목록)
$deployments = @(
    "postgres-spring",
    "dragonfly-spring",
    "kafka-spring",
    "auth-service",
    "ticket-service",
    "payment-service",
    "stats-service",
    "queue-service",
    "community-service",
    "catalog-service",
    "gateway-service",
    "frontend",
    "loki",
    "grafana"
)
```

### 8.2 spring-kind-build-load.ps1 (Docker 빌드 + Kind 로드)

**출처**: `scripts/spring-kind-build-load.ps1:1-101`

8개 Spring Boot 서비스와 1개 Frontend 이미지를 빌드하고 Kind 클러스터에 로드한다.

```powershell
# scripts/spring-kind-build-load.ps1:47-56
$services = @(
    @{ Name = "gateway-service"; Image = "tiketi-spring-gateway-service:local" },
    @{ Name = "auth-service";    Image = "tiketi-spring-auth-service:local" },
    @{ Name = "ticket-service";  Image = "tiketi-spring-ticket-service:local" },
    @{ Name = "payment-service"; Image = "tiketi-spring-payment-service:local" },
    @{ Name = "stats-service";   Image = "tiketi-spring-stats-service:local" },
    @{ Name = "queue-service";   Image = "tiketi-spring-queue-service:local" },
    @{ Name = "community-service"; Image = "tiketi-spring-community-service:local" },
    @{ Name = "catalog-service"; Image = "tiketi-spring-catalog-service:local" }
)
```

Frontend 이미지는 `NEXT_PUBLIC_API_URL=http://localhost:3001` 빌드 인자와 함께 빌드된다 (라인 89).

### 8.3 spring-kind-smoke.ps1 (헬스 체크)

**출처**: `scripts/spring-kind-smoke.ps1:1-33`

3개 엔드포인트를 검증한다:

```powershell
# scripts/spring-kind-smoke.ps1:29-31
Assert-Endpoint -Url "http://localhost:3001/health" -ExpectedStatus 200
Assert-Endpoint -Url "http://localhost:3001/api/auth/me" -ExpectedStatus 401
Assert-Endpoint -Url "http://localhost:3000" -ExpectedStatus 200
```

| 엔드포인트 | 기대 상태 | 검증 내용 |
|-----------|----------|----------|
| `localhost:3001/health` | 200 | Gateway 정상 동작 |
| `localhost:3001/api/auth/me` | 401 | 인증 미들웨어 동작 |
| `localhost:3000` | 200 | Frontend 정상 서빙 |

### 8.4 start-all.ps1 (로컬 개발 시작)

**출처**: `scripts/start-all.ps1:1-204`

Kind 클러스터 없이 로컬에서 모든 서비스를 실행하는 스크립트이다.

실행 흐름:

1. **환경 변수 설정** (라인 18-31): JWT, Kafka, Zipkin 등
2. **Docker Compose 실행** (라인 37-47): `docker-compose.databases.yml` (DB, Redis, Kafka, Zipkin)
3. **포트 대기** (라인 52-80): 5개 DB + Kafka + Zipkin 준비 확인
4. **Spring Boot 서비스 시작** (라인 87-145): 7개 서비스를 `gradlew bootRun`으로 개별 프로세스 실행
5. **Frontend (선택)** (라인 149-170): `-WithFrontend` 플래그 시 `npm run dev` 실행

```powershell
# scripts/start-all.ps1:87-94 (서비스 실행 순서)
$services = @(
    @{ Name = "auth-service";      Port = 3005 },
    @{ Name = "ticket-service";    Port = 3002 },
    @{ Name = "payment-service";   Port = 3003 },
    @{ Name = "stats-service";     Port = 3004 },
    @{ Name = "queue-service";     Port = 3007 },
    @{ Name = "community-service"; Port = 3008 },
    @{ Name = "gateway-service";   Port = 3001 }
)
```

---

## 9. 환경 설정

### 9.1 Kind 환경 ConfigMap

**출처**: `k8s/spring/overlays/kind/config.env:1-22`

```properties
# 서비스 URL (Kubernetes 내부 DNS)
AUTH_SERVICE_URL=http://auth-service:3005
TICKET_SERVICE_URL=http://ticket-service:3002
PAYMENT_SERVICE_URL=http://payment-service:3003
STATS_SERVICE_URL=http://stats-service:3004
QUEUE_SERVICE_URL=http://queue-service:3007
CATALOG_SERVICE_URL=http://catalog-service:3009
COMMUNITY_SERVICE_URL=http://community-service:3008

# 인프라
REDIS_HOST=dragonfly-spring
REDIS_PORT=6379
KAFKA_BOOTSTRAP_SERVERS=kafka-spring:9092
ZIPKIN_ENDPOINT=http://zipkin-spring:9411/api/v2/spans
TRACING_SAMPLING_PROBABILITY=1.0

# AWS (로컬 비활성화)
AWS_REGION=ap-northeast-2
AWS_S3_BUCKET=local-mock-bucket
SQS_ENABLED=false

# 기타
CORS_ALLOWED_ORIGINS=http://localhost:3000
COOKIE_SECURE=false
KAFKA_TOPIC_REPLICATION_FACTOR=1
```

### 9.2 Kind 환경 Secret

**출처**: `k8s/spring/overlays/kind/secrets.env:1-15`

```properties
# 데이터베이스
POSTGRES_USER=tiketi_user
POSTGRES_PASSWORD=tiketi_password
AUTH_DB_USERNAME=tiketi_user
AUTH_DB_PASSWORD=tiketi_password
TICKET_DB_USERNAME=tiketi_user
TICKET_DB_PASSWORD=tiketi_password
PAYMENT_DB_USERNAME=tiketi_user
PAYMENT_DB_PASSWORD=tiketi_password
STATS_DB_USERNAME=tiketi_user
STATS_DB_PASSWORD=tiketi_password

# 보안 토큰
JWT_SECRET=c3ByaW5nLWtpbmQtdGVzdC1qd3Qtc2VjcmV0LTIwMjYtMDItMTA=
INTERNAL_API_TOKEN=dev-internal-token-change-me
QUEUE_ENTRY_TOKEN_SECRET=kind-test-entry-token-secret-min-32-chars-here
TOSS_CLIENT_KEY=test_ck_dummy
```

모든 DB에 동일한 자격 증명(`tiketi_user`/`tiketi_password`)을 사용한다. 프로덕션에서는 AWS Secrets Manager를 통해 서비스별 개별 자격 증명을 관리해야 한다.

---

## 환경별 인프라 비교 요약

| 구성 요소 | 로컬 (Docker Compose) | Kind 클러스터 | Production (AWS) |
|-----------|---------------------|-------------|-----------------|
| **PostgreSQL** | 5개 개별 컨테이너 | 1개 인스턴스, 5 DB | RDS + RDS Proxy |
| **Redis** | redis:7 단일 | Dragonfly 단일 | Redis 6노드 클러스터 |
| **Kafka** | 단일 KRaft | 단일 KRaft | 3노드 StatefulSet |
| **트레이싱** | Zipkin | Zipkin + Loki/Grafana | X-Ray |
| **오토스케일링** | 없음 | 없음 | HPA (CPU 70%) |
| **네트워크 정책** | 없음 | NetworkPolicy | VPC + Security Group |
| **CDN** | 없음 | 없음 | CloudFront + Lambda@Edge |
| **큐** | Kafka 직접 | Kafka 직접 | SQS FIFO + Lambda Worker |
