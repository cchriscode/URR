# Tiketi 인프라스트럭처 기술 분석서

> **문서 버전**: 1.0
> **작성일**: 2026-02-13
> **대상 시스템**: Tiketi 티켓팅 플랫폼
> **분석 범위**: Kubernetes, Docker, Terraform(AWS), CI/CD, Lambda, 옵저버빌리티

---

## 목차

1. [Kubernetes 로컬 환경 (Kind)](#1-kubernetes-로컬-환경-kind)
2. [Kubernetes 프로덕션 환경](#2-kubernetes-프로덕션-환경)
3. [Docker 컨테이너 전략](#3-docker-컨테이너-전략)
4. [Terraform AWS 인프라](#4-terraform-aws-인프라)
5. [CI/CD 파이프라인](#5-cicd-파이프라인)
6. [운영 스크립트](#6-운영-스크립트)
7. [옵저버빌리티 스택](#7-옵저버빌리티-스택)
8. [Lambda 함수](#8-lambda-함수)

---

## 1. Kubernetes 로컬 환경 (Kind)

### 1.1 Base 매니페스트 구조

**파일**: `k8s/spring/base/kustomization.yaml` (22줄)

Base Kustomization은 8개의 마이크로서비스 배포 매니페스트와 네트워크 정책을 하나의 리소스 집합으로 구성한다. 기본 네임스페이스는 `tiketi-dev`로 지정되어 있으며, 오버레이에서 환경별로 재정의한다.

```yaml
# k8s/spring/base/kustomization.yaml (line 1-4)
apiVersion: kustomize.config.k8s.io/v1beta1
kind: Kustomization
namespace: tiketi-dev
resources:
```

포함 서비스 목록 (line 5-21):

| 순번 | 서비스 | Deployment | Service |
|------|--------|------------|---------|
| 1 | Gateway Service | `gateway-service/deployment.yaml` | `gateway-service/service.yaml` |
| 2 | Ticket Service | `ticket-service/deployment.yaml` | `ticket-service/service.yaml` |
| 3 | Payment Service | `payment-service/deployment.yaml` | `payment-service/service.yaml` |
| 4 | Stats Service | `stats-service/deployment.yaml` | `stats-service/service.yaml` |
| 5 | Auth Service | `auth-service/deployment.yaml` | `auth-service/service.yaml` |
| 6 | Queue Service | `queue-service/deployment.yaml` | `queue-service/service.yaml` |
| 7 | Community Service | `community-service/deployment.yaml` | `community-service/service.yaml` |
| 8 | Frontend | `frontend/deployment.yaml` | `frontend/service.yaml` |

마지막 리소스로 `network-policies.yaml`이 포함되어 전체 네임스페이스에 네트워크 격리를 적용한다 (line 21).

---

### 1.2 네트워크 정책 (NetworkPolicy)

**파일**: `k8s/spring/base/network-policies.yaml` (92줄)

제로 트러스트 네트워크 모델을 기반으로 4개의 NetworkPolicy를 정의한다. 기본적으로 모든 트래픽을 차단(default-deny)하고, 필요한 통신 경로만 명시적으로 허용한다.

#### 정책 1: 기본 전체 차단 (lines 1-9)

```yaml
# k8s/spring/base/network-policies.yaml (line 1-9)
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

빈 `podSelector`로 네임스페이스 내 모든 Pod에 적용된다. Ingress와 Egress 모두 차단하여, 이후 정책에서 명시적으로 허용하지 않은 트래픽은 모두 거부된다.

#### 정책 2: Gateway 인그레스 허용 (lines 11-23)

```yaml
# k8s/spring/base/network-policies.yaml (line 14-23)
name: allow-gateway-ingress
spec:
  podSelector:
    matchLabels:
      app: gateway-service
  policyTypes:
    - Ingress
  ingress:
    - ports:
        - port: 3001
```

외부에서 Gateway Service의 포트 3001로 인바운드 트래픽을 허용한다. 소스 제한이 없으므로 클러스터 외부(NodePort, LoadBalancer)에서의 접근이 가능하다.

#### 정책 3: Frontend 인그레스 허용 (lines 25-37)

```yaml
# k8s/spring/base/network-policies.yaml (line 28-37)
name: allow-frontend-ingress
spec:
  podSelector:
    matchLabels:
      app: frontend
  policyTypes:
    - Ingress
  ingress:
    - ports:
        - port: 3000
```

Frontend 서비스의 포트 3000에 대한 인바운드 트래픽을 허용한다.

#### 정책 4: 내부 백엔드 통신 (lines 39-67)

```yaml
# k8s/spring/base/network-policies.yaml (line 42-67)
name: allow-internal-communication
spec:
  podSelector:
    matchLabels:
      tier: backend
```

`tier: backend` 레이블을 가진 Pod 간의 양방향 통신을 허용한다. 추가로 kube-dns(포트 53, UDP/TCP)로의 이그레스를 허용하여 서비스 디스커버리가 정상 동작하도록 보장한다.

**인그레스 규칙** (line 50-54): 동일한 `tier: backend` 레이블을 가진 Pod으로부터의 트래픽만 허용
**이그레스 규칙** (line 55-67): 모든 Pod으로의 아웃바운드 허용 + kube-dns 네임스페이스 간 DNS 쿼리 허용

#### 정책 5: Gateway 이그레스 (lines 69-92)

```yaml
# k8s/spring/base/network-policies.yaml (line 72-92)
name: allow-gateway-to-services
spec:
  podSelector:
    matchLabels:
      app: gateway-service
  policyTypes:
    - Egress
```

Gateway Service가 `tier: backend` 레이블을 가진 Pod과 kube-dns로 아웃바운드 통신할 수 있도록 허용한다 (line 80-92).

---

### 1.3 서비스별 Deployment 스펙

**파일 위치**: `k8s/spring/base/{service}/deployment.yaml`

모든 서비스는 동일한 보안 컨텍스트를 적용한다:

```yaml
securityContext:
  runAsNonRoot: true
  capabilities:
    drop:
      - ALL
```

#### 리소스 할당 상세

| 서비스 | 포트 | CPU 요청 | CPU 제한 | 메모리 요청 | 메모리 제한 | 비고 |
|--------|------|----------|----------|------------|------------|------|
| Gateway Service | 3001 | 200m | 1 | 256Mi | 1Gi | API 라우팅 진입점 |
| Auth Service | 3005 | 200m | 1 | 256Mi | 1Gi | JWT 발급/검증 |
| Ticket Service | 3002 | 200m | 1 | 256Mi | 1Gi | 핵심 도메인 서비스 |
| Payment Service | 3003 | 200m | 1 | 256Mi | 1Gi | 결제 처리 |
| Stats Service | 3004 | 100m | 500m | 256Mi | 512Mi | 통계 집계 |
| Queue Service | 3007 | 100m | 500m | 256Mi | 512Mi | 대기열 관리 |
| Community Service | 3008 | 100m | 500m | 256Mi | 512Mi | 커뮤니티 기능 |
| Frontend | 3000 | 100m | 500m | 128Mi | 256Mi | Next.js SSR |

핵심 서비스(Gateway, Auth, Ticket, Payment)는 CPU 1코어까지 버스트가 가능하도록 설정되어 있고, 보조 서비스(Stats, Queue, Community)는 500m으로 제한된다. Frontend는 정적 자산 서빙이 주 역할이므로 메모리 할당이 가장 작다(128Mi/256Mi).

---

### 1.4 Kind 오버레이 구성

**파일**: `k8s/spring/overlays/kind/kustomization.yaml` (67줄)

#### 네임스페이스 및 리소스 (lines 1-15)

```yaml
# k8s/spring/overlays/kind/kustomization.yaml (line 3)
namespace: tiketi-spring
```

Base의 `tiketi-dev`를 `tiketi-spring`으로 재정의하며, 인프라 컴포넌트를 추가 리소스로 포함한다:

- `namespace.yaml` - 네임스페이스 생성
- `postgres.yaml` - PostgreSQL 데이터베이스
- `dragonfly.yaml` - Redis 호환 캐시
- `kafka.yaml` - 메시지 브로커
- `zipkin.yaml` - 분산 추적
- `pvc.yaml` - 영구 볼륨 클레임
- `loki.yaml` - 로그 수집
- `promtail.yaml` - 로그 에이전트
- `grafana.yaml` - 시각화 대시보드

#### ConfigMap/Secret 생성기 (lines 17-28)

```yaml
# k8s/spring/overlays/kind/kustomization.yaml (line 17-28)
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

`disableNameSuffixHash: true`로 해시 접미사를 비활성화하여 서비스에서 고정된 이름으로 참조할 수 있도록 한다.

#### 이미지 매핑 (lines 42-66)

ECR URI 플레이스홀더(`YOUR_ECR_URI`)를 로컬 이미지 이름으로 매핑한다. 모든 서비스는 `tiketi-spring-{service}:local` 태그를 사용한다.

```yaml
# k8s/spring/overlays/kind/kustomization.yaml (line 43-46) 예시
- name: YOUR_ECR_URI/gateway-service
  newName: tiketi-spring-gateway-service
  newTag: local
```

#### 패치 파일 (lines 30-40)

10개의 패치 파일이 적용되어 환경별 설정(환경 변수, NodePort 설정 등)을 주입한다:

- `patches/gateway-service.yaml` ~ `patches/community-service.yaml` (7개 서비스 패치)
- `patches/gateway-service-nodeport.yaml` - Gateway NodePort 설정
- `patches/frontend-service-nodeport.yaml` - Frontend NodePort 설정
- `patches/frontend.yaml` - Frontend 환경 설정

---

### 1.5 인프라 컴포넌트 상세

#### PostgreSQL

**파일**: `k8s/spring/overlays/kind/postgres.yaml` (91줄)

```yaml
# k8s/spring/overlays/kind/postgres.yaml (line 31)
image: postgres:15-alpine
```

**데이터베이스 초기화** (lines 6-11):

ConfigMap `postgres-init-spring`에서 5개 데이터베이스를 초기화한다:

```sql
CREATE DATABASE auth_db;
CREATE DATABASE ticket_db;
CREATE DATABASE payment_db;
CREATE DATABASE stats_db;
CREATE DATABASE community_db;
```

**볼륨 마운트** (lines 47-51):
- `/var/lib/postgresql/data` - PVC `postgres-pvc` (5Gi)
- `/docker-entrypoint-initdb.d` - 초기화 SQL ConfigMap

**헬스 체크** (lines 52-67):
- Readiness: `pg_isready -U "$POSTGRES_USER" -d postgres` (5초 후 시작, 5초 간격)
- Liveness: 동일 명령어 (20초 후 시작, 10초 간격)

**서비스** (lines 76-91): NodePort 타입, 내부 5432 포트를 외부 30432로 노출

#### Dragonfly (Redis 호환 캐시)

**파일**: `k8s/spring/overlays/kind/dragonfly.yaml` (63줄)

```yaml
# k8s/spring/overlays/kind/dragonfly.yaml (line 19)
image: docker.dragonflydb.io/dragonflydb/dragonfly:latest
```

**실행 인자** (lines 21-25):

```yaml
args:
  - --maxmemory=512mb
  - --proactor_threads=1
  - --snapshot_cron=* * * * *
  - --dir=/data
  - --dbfilename=dump
```

매분 스냅샷을 생성하며, 메모리 상한은 512MB로 설정된다. 단일 proactor 스레드로 로컬 환경에 최적화되어 있다.

**볼륨**: PVC `dragonfly-pvc` (1Gi)를 `/data`에 마운트 (lines 28-30)
**헬스 체크** (lines 31-44): `redis-cli ping` 명령으로 Readiness(5초 후, 5초 간격)와 Liveness(20초 후, 10초 간격) 확인

#### Apache Kafka

**파일**: `k8s/spring/overlays/kind/kafka.yaml` (84줄)

```yaml
# k8s/spring/overlays/kind/kafka.yaml (line 19)
image: apache/kafka:3.7.0
```

**KRaft 모드 구성** (lines 23-47):

ZooKeeper 없이 단일 노드에서 broker와 controller 역할을 동시에 수행한다:

| 환경 변수 | 값 | 설명 |
|-----------|-----|------|
| `KAFKA_NODE_ID` | `1` | 노드 식별자 |
| `KAFKA_PROCESS_ROLES` | `broker,controller` | 단일 노드 겸용 |
| `KAFKA_LISTENERS` | `PLAINTEXT://0.0.0.0:9092,CONTROLLER://0.0.0.0:9093` | 리스너 설정 |
| `KAFKA_ADVERTISED_LISTENERS` | `PLAINTEXT://kafka-spring:9092` | 클러스터 내 광고 주소 |
| `KAFKA_CONTROLLER_QUORUM_VOTERS` | `1@localhost:9093` | 컨트롤러 쿼럼 |
| `KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR` | `1` | 단일 브로커 |
| `CLUSTER_ID` | `MkU3OEVBNTcwNTJENDM2Qk` | 고정 클러스터 ID |

**리소스**: CPU 200m/1, 메모리 512Mi/1Gi (lines 48-54)

**프로브 설정** (lines 55-70):
- Startup: TCP 9092, 10초 후 시작, 10초 간격, 최대 15회 실패 허용
- Readiness: TCP 9092, 5초 후 시작, 10초 간격
- Liveness: TCP 9092, 10초 후 시작, 20초 간격

#### Zipkin

**파일**: `k8s/spring/overlays/kind/zipkin.yaml` (40줄)

```yaml
# k8s/spring/overlays/kind/zipkin.yaml (line 17)
image: openzipkin/zipkin:3
```

포트 9411을 NodePort 30411로 노출한다 (line 39). 리소스는 CPU 100m/500m, 메모리 256Mi/512Mi.

#### Loki

**파일**: `k8s/spring/overlays/kind/loki.yaml` (123줄)

```yaml
# k8s/spring/overlays/kind/loki.yaml (line 63)
image: grafana/loki:2.9.3
```

**스토리지 구성** (ConfigMap, lines 1-43):
- 인덱스 저장: `boltdb-shipper` (line 28)
- 오브젝트 저장: `filesystem` (line 29)
- 스키마: v11, 24시간 주기 인덱스 (lines 32-33)
- 복제 팩터: 1 (line 20)
- KV 저장소: `inmemory` (line 23)

**볼륨**: PVC `loki-pvc` (2Gi)를 `/loki`에 마운트 (lines 74-75)
**서비스** (lines 103-123): ClusterIP 타입, HTTP(3100) + gRPC(9096)

#### Promtail

**파일**: `k8s/spring/overlays/kind/promtail.yaml` (118줄)

```yaml
# k8s/spring/overlays/kind/promtail.yaml (line 52)
image: grafana/promtail:2.9.3
```

**DaemonSet 배포** (line 35): 모든 노드에서 실행되어 컨테이너 로그를 수집한다.

**스크레이프 설정** (lines 18-31):
- 대상: `tiketi-spring` 네임스페이스의 Kubernetes Pod
- 레이블 매핑: `app`, `pod`, `namespace`

**RBAC 구성** (lines 84-117):
- ServiceAccount: `promtail` (line 88)
- ClusterRole: nodes, services, endpoints, pods에 대한 get/watch/list 권한 (lines 91-103)
- ClusterRoleBinding: `tiketi-spring` 네임스페이스의 ServiceAccount에 바인딩 (lines 106-117)

**볼륨 마운트** (lines 56-62):
- `/etc/promtail` - 설정 ConfigMap
- `/var/log` - 호스트 로그 디렉토리
- `/var/lib/docker/containers` - 컨테이너 로그 (읽기 전용)

#### Grafana

**파일**: `k8s/spring/overlays/kind/grafana.yaml` (98줄)

```yaml
# k8s/spring/overlays/kind/grafana.yaml (line 36)
image: grafana/grafana:10.2.3
```

**데이터소스 자동 프로비저닝** (ConfigMap, lines 1-16):

```yaml
# k8s/spring/overlays/kind/grafana.yaml (line 10-15)
datasources:
  - name: Loki
    type: loki
    access: proxy
    url: http://loki-service:3100
    isDefault: true
```

**환경 변수** (lines 41-48): admin/admin 기본 인증, 회원가입 비활성화, HTTP 포트 3006
**서비스** (lines 81-98): NodePort 타입, 내부 3006을 외부 30006으로 노출
**리소스**: CPU 100m/500m, 메모리 256Mi/512Mi (lines 66-72)

#### PVC 구성

**파일**: `k8s/spring/overlays/kind/pvc.yaml` (52줄)

| PVC 이름 | 용량 | 접근 모드 | 스토리지 클래스 | 사용처 |
|----------|------|----------|---------------|--------|
| `postgres-pvc` (line 5) | 5Gi | ReadWriteOnce | standard | PostgreSQL 데이터 |
| `dragonfly-pvc` (line 18) | 1Gi | ReadWriteOnce | standard | Dragonfly 스냅샷 |
| `grafana-pvc` (line 31) | 1Gi | ReadWriteOnce | standard | Grafana 대시보드 |
| `loki-pvc` (line 44) | 2Gi | ReadWriteOnce | standard | Loki 로그 인덱스/청크 |

---

## 2. Kubernetes 프로덕션 환경

**디렉토리**: `k8s/spring/overlays/prod/`

### 2.1 Horizontal Pod Autoscaler (HPA)

**파일**: `k8s/spring/overlays/prod/hpa.yaml` (76줄)

모든 HPA는 `autoscaling/v2` API를 사용하며, CPU 사용률 70%를 스케일링 임계값으로 설정한다.

| 서비스 | 최소 레플리카 | 최대 레플리카 | CPU 타겟 | 스케일링 근거 |
|--------|-------------|-------------|----------|-------------|
| Gateway Service (line 1-18) | 3 | 10 | 70% | API 진입점, 트래픽 직접 수신 |
| Ticket Service (line 20-37) | 3 | 10 | 70% | 핵심 비즈니스 로직, 높은 부하 |
| Queue Service (line 39-56) | 3 | 8 | 70% | 대기열 처리, 이벤트 시 급증 |
| Payment Service (line 58-76) | 2 | 6 | 70% | 결제 처리, 상대적 저빈도 |

Gateway와 Ticket이 가장 높은 최대 레플리카(10)를 가지며, 이는 티켓팅 시스템 특성상 이 두 서비스가 가장 높은 트래픽을 수신하기 때문이다.

### 2.2 Pod Disruption Budget (PDB)

**파일**: `k8s/spring/overlays/prod/pdb.yaml` (70줄)

7개 서비스 모두 `minAvailable: 1`로 설정되어, 노드 드레인이나 롤링 업데이트 시 최소 1개 Pod이 항상 가용하도록 보장한다.

```yaml
# k8s/spring/overlays/prod/pdb.yaml (line 1-9) 예시
apiVersion: policy/v1
kind: PodDisruptionBudget
metadata:
  name: gateway-service-pdb
spec:
  minAvailable: 1
  selector:
    matchLabels:
      app: gateway-service
```

PDB 적용 서비스 목록:

| PDB 이름 | 라인 범위 | 대상 서비스 |
|----------|----------|-----------|
| `gateway-service-pdb` | lines 1-9 | gateway-service |
| `ticket-service-pdb` | lines 11-19 | ticket-service |
| `queue-service-pdb` | lines 21-29 | queue-service |
| `payment-service-pdb` | lines 31-39 | payment-service |
| `auth-service-pdb` | lines 41-49 | auth-service |
| `stats-service-pdb` | lines 51-59 | stats-service |
| `community-service-pdb` | lines 61-70 | community-service |

### 2.3 프로덕션 레플리카 설정

**파일**: `k8s/spring/overlays/prod/patches/replicas.yaml` (56줄)

| 서비스 | 기본 레플리카 | 라인 범위 | HPA 범위 |
|--------|-------------|----------|----------|
| Gateway Service | 3 | lines 1-6 | 3-10 |
| Auth Service | 2 | lines 8-13 | - |
| Ticket Service | 3 | lines 15-20 | 3-10 |
| Payment Service | 2 | lines 22-27 | 2-6 |
| Stats Service | 2 | lines 29-34 | - |
| Queue Service | 3 | lines 36-41 | 3-8 |
| Community Service | 2 | lines 43-48 | - |
| Frontend | 2 | lines 50-55 | - |

HPA가 적용되지 않은 서비스(Auth, Stats, Community, Frontend)는 고정 레플리카로 운영된다.

---

## 3. Docker 컨테이너 전략

### 3.1 Spring 서비스 빌드 패턴

모든 Spring Boot 서비스는 동일한 멀티스테이지 빌드 패턴을 사용한다:

| 스테이지 | 베이스 이미지 | 목적 |
|---------|------------|------|
| Build | `eclipse-temurin:21-jdk` | Gradle 빌드, 의존성 해결 |
| Runtime | `eclipse-temurin:21-jre` | 경량 런타임, JRE만 포함 |

**보안 설정**: 모든 컨테이너는 UID 1001의 비루트 사용자로 실행한다.

**CRLF 보정**: `queue-service`와 `community-service`의 Dockerfile에서 Windows 환경 호환을 위해 `sed -i 's/\r$//' gradlew` 명령을 포함한다.

### 3.2 Frontend 빌드 패턴

- 베이스 이미지: `node:20-alpine` 멀티스테이지
- 빌드 스테이지에서 Next.js 앱을 빌드하고, 런타임 스테이지에서 경량 실행

### 3.3 로컬 개발 데이터베이스

**파일**: `services-spring/docker-compose.databases.yml` (74줄)

| 서비스 | 이미지 | 데이터베이스 | 외부 포트 | 라인 범위 |
|--------|--------|------------|----------|----------|
| auth-db | `postgres:16` | auth_db | 5438 | lines 2-9 |
| ticket-db | `postgres:16` | ticket_db | 5434 | lines 11-18 |
| payment-db | `postgres:16` | payment_db | 5435 | lines 20-27 |
| stats-db | `postgres:16` | stats_db | 5436 | lines 29-36 |
| community-db | `postgres:16` | community_db | 5437 | lines 38-45 |
| redis | `redis:7` | - | 6379 | lines 47-50 |
| zipkin | `openzipkin/zipkin:3` | - | 9411 | lines 52-55 |
| kafka | `apache/kafka:3.7.0` | - | 9092 | lines 57-74 |

모든 PostgreSQL 인스턴스는 동일한 자격 증명(`tiketi_user`/`tiketi_password`)을 사용한다. Kafka는 KRaft 모드로 구성되며, Kind 환경과 동일한 설정 패턴(`CLUSTER_ID: MkU3OEVBNTcwNTJENDM2Qk`)을 사용한다.

---

## 4. Terraform AWS 인프라

### 4.1 Application Load Balancer (ALB)

**파일**: `terraform/modules/alb/main.tf` (203줄)

#### 보안 그룹 (lines 1-88)

```hcl
# terraform/modules/alb/main.tf (line 21-30)
resource "aws_security_group_rule" "alb_ingress_https_cloudfront" {
  count             = var.use_cloudfront_prefix_list ? 1 : 0
  type              = "ingress"
  from_port         = 443
  to_port           = 443
  protocol          = "tcp"
  prefix_list_ids   = [var.cloudfront_prefix_list_id]
  security_group_id = aws_security_group.alb.id
}
```

**핵심 보안 설계**: CloudFront의 관리형 프리픽스 리스트를 사용하여 ALB로의 직접 접근을 차단한다 (line 20 주석). 테스트용 CIDR 폴백도 제공한다 (lines 33-42).

| 규칙 | 프로토콜 | 포트 | 소스 | 조건 | 라인 |
|------|---------|------|------|------|------|
| HTTPS from CF | TCP | 443 | CF Prefix List | `use_cloudfront_prefix_list=true` | 21-30 |
| HTTPS from CIDR | TCP | 443 | Custom CIDR | 폴백 | 33-42 |
| HTTP from CF | TCP | 80 | CF Prefix List | 리다이렉트용 | 45-54 |
| HTTP from CIDR | TCP | 80 | Custom CIDR | 폴백 | 57-66 |
| EKS Ingress | TCP | 0-65535 | ALB SG | ALB에서 EKS로 | 80-88 |

#### ALB 본체 (lines 90-110)

```hcl
# terraform/modules/alb/main.tf (line 94-110)
resource "aws_lb" "main" {
  name               = "${var.name_prefix}-alb"
  internal           = false
  load_balancer_type = "application"
  enable_http2               = true
  enable_cross_zone_load_balancing = true
  drop_invalid_header_fields = true
}
```

HTTP/2 활성화, 크로스존 로드밸런싱 활성화, 잘못된 헤더 필드를 드롭하여 HTTP 요청 스머글링 공격을 방지한다.

#### Target Group (lines 112-149)

```hcl
# terraform/modules/alb/main.tf (line 118-149)
resource "aws_lb_target_group" "gateway_service" {
  port     = 3001
  protocol = "HTTP"
  target_type = "ip"
  ...
  health_check {
    path    = "/health"
    matcher = "200"
  }
  stickiness {
    type            = "lb_cookie"
    cookie_duration = 86400  # 24시간
  }
}
```

- 타겟: Gateway Service 포트 3001 (IP 타입, EKS Pod 직접 라우팅)
- 헬스 체크: `/health` 경로, 30초 간격, 2회 성공/3회 실패 임계값
- 세션 고정: LB 쿠키 기반, 24시간 유지

#### HTTPS 리스너 (lines 151-172)

```hcl
# terraform/modules/alb/main.tf (line 161)
ssl_policy = "ELBSecurityPolicy-TLS13-1-2-2021-06"
```

TLS 1.3/1.2만 허용하는 최신 보안 정책을 적용한다.

#### HTTP 리스너 (lines 174-202)

인증서가 존재할 경우 HTTPS로 301 리다이렉트, 없으면 직접 포워딩한다 (line 185-196).

---

### 4.2 CloudFront CDN

**파일**: `terraform/modules/cloudfront/main.tf` (317줄)

#### Lambda@Edge 함수 (lines 1-39)

```hcl
# terraform/modules/cloudfront/main.tf (line 23-38)
resource "aws_lambda_function" "edge_queue_check" {
  provider      = aws.us_east_1
  runtime       = "nodejs20.x"
  timeout       = 5
  memory_size   = 128
  publish       = true  # Lambda@Edge 필수
}
```

대기열 토큰 검증을 위한 viewer-request Lambda@Edge 함수. `us-east-1`에 배포되어야 한다 (CloudFront 요구사항).

#### CloudFront Distribution (lines 54-191)

**오리진 구성**:

| 오리진 ID | 타입 | 대상 | 프로토콜 | 라인 |
|-----------|------|------|---------|------|
| `alb` | Custom Origin | ALB DNS | HTTPS only, TLSv1.2 | lines 67-82 |
| `s3` | S3 Origin | Static Assets Bucket | OAC 인증 | lines 85-92 |

**캐시 동작**:

| 경로 패턴 | 오리진 | TTL | Lambda@Edge | 라인 |
|-----------|--------|-----|------------|------|
| `*` (기본) | ALB | 0 (캐시 없음) | viewer-request 토큰 검증 | lines 95-113 |
| `/static/*` | S3 | 기본 1시간, 최대 24시간 | 없음 | lines 116-135 |
| `/_next/static/*` | S3 | 1년 (불변 파일) | 없음 | lines 138-156 |

**커스텀 에러 응답** (lines 174-186):
- 404 / 403 오류를 `/index.html`로 200 응답 (SPA 라우팅 지원, 캐싱 TTL 10초)

#### API 캐시 정책 (lines 193-223)

```hcl
# terraform/modules/cloudfront/main.tf (line 200-202)
default_ttl = 0
max_ttl     = 0
min_ttl     = 0
```

API 트래픽은 캐싱하지 않으며, 모든 쿠키, Authorization 헤더, 쿼리 스트링을 오리진으로 전달한다.

#### 보안 헤더 정책 (lines 249-304)

```hcl
# terraform/modules/cloudfront/main.tf (line 258-283)
strict_transport_security {
  access_control_max_age_sec = 31536000  # 1년
  include_subdomains         = true
  preload                    = true
}
frame_options { frame_option = "DENY" }
xss_protection { mode_block = true; protection = true }
```

| 보안 헤더 | 값 | 라인 |
|-----------|-----|------|
| Strict-Transport-Security | max-age=31536000; includeSubDomains; preload | 258-263 |
| X-Content-Type-Options | nosniff | 265-267 |
| X-Frame-Options | DENY | 269-272 |
| X-XSS-Protection | 1; mode=block | 274-278 |
| Referrer-Policy | strict-origin-when-cross-origin | 280-283 |

**SSL/TLS** (lines 166-171): `TLSv1.2_2021` 최소 프로토콜, SNI 전용

---

### 4.3 RDS (PostgreSQL)

**파일**: `terraform/modules/rds/main.tf` (244줄)

#### 보안 그룹 (lines 1-28)

포트 5432에 대해 애플리케이션 보안 그룹으로부터의 인바운드만 허용한다 (line 20-28).

#### RDS 인스턴스 (lines 43-105)

```hcl
# terraform/modules/rds/main.tf (line 51-57)
engine               = "postgres"
engine_version       = var.engine_version
storage_type         = "gp3"
storage_encrypted    = true
```

| 설정 항목 | 값 | 라인 |
|----------|-----|------|
| 엔진 | PostgreSQL 16 | line 51-52 |
| 스토리지 | gp3, 암호화 | lines 56-57 |
| Multi-AZ | 변수 기반 | line 76 |
| 백업 윈도우 | 03:00-04:00 UTC | line 81 |
| 유지보수 윈도우 | 월요일 04:00-05:00 UTC | line 82 |
| Performance Insights | 7일 보존 | lines 89-90 |
| 퍼블릭 접근 | false | line 73 |

#### DB 파라미터 그룹 (lines 107-140)

```hcl
# terraform/modules/rds/main.tf (line 113)
family = "postgres16"
```

| 파라미터 | 값 | 목적 | 라인 |
|---------|-----|------|------|
| `shared_preload_libraries` | `pg_stat_statements` | 쿼리 성능 통계 | 118-120 |
| `log_statement` | `ddl` | DDL 로깅 | 122-125 |
| `log_min_duration_statement` | `1000` (1초) | 슬로우 쿼리 로깅 | 128-131 |

#### RDS Proxy (lines 142-204)

```hcl
# terraform/modules/rds/main.tf (line 159)
require_tls         = true
# terraform/modules/rds/main.tf (line 160)
idle_client_timeout = 1800  # 30분
```

| 설정 | 값 | 라인 |
|------|-----|------|
| 엔진 패밀리 | POSTGRESQL | line 150 |
| TLS 필수 | true | line 159 |
| 유휴 타임아웃 | 1800초 (30분) | line 160 |
| 최대 연결 비율 | 100% | line 175 |
| 최대 유휴 연결 비율 | 50% | line 176 |
| 연결 대여 타임아웃 | 120초 | line 174 |

프록시는 APP 서브넷에 배포되며 (line 158 주석), EKS 노드 -> RDS Proxy -> RDS 경로의 보안 그룹 체인이 구성된다 (lines 189-243).

---

### 4.4 ElastiCache (Redis)

**파일**: `terraform/modules/elasticache/main.tf` (135줄)

#### 파라미터 그룹 (lines 43-70)

```hcl
# terraform/modules/elasticache/main.tf (line 59-61)
parameter {
  name  = "maxmemory-policy"
  value = "allkeys-lru"
}
```

| 파라미터 | 값 | 목적 | 라인 |
|---------|-----|------|------|
| `timeout` | 300 (5분) | 유휴 연결 타임아웃 | 54-56 |
| `maxmemory-policy` | `allkeys-lru` | 메모리 포화 시 LRU 축출 | 58-61 |

#### Replication Group (lines 72-135)

```hcl
# terraform/modules/elasticache/main.tf (line 100-103)
at_rest_encryption_enabled = true
transit_encryption_enabled = true
auth_token_enabled         = var.auth_token_enabled
```

| 설정 | 값 | 라인 |
|------|-----|------|
| Multi-AZ | replica > 0일 때 자동 활성화 | line 89 |
| 미사용 시 암호화 | true | line 100 |
| 전송 중 암호화 | true | line 101 |
| 인증 토큰 | 변수 기반 | line 102-103 |
| 유지보수 윈도우 | 일요일 05:00-07:00 UTC | line 106 |
| 스냅샷 윈도우 | 03:00-05:00 UTC | line 107 |

**CloudWatch 로깅** (lines 112-124): slow-log와 engine-log를 JSON 형식으로 전송한다.

---

### 4.5 S3 스토리지

**파일**: `terraform/modules/s3/main.tf` (259줄)

#### Frontend 정적 자산 버킷 (lines 1-121)

| 설정 | 값 | 라인 |
|------|-----|------|
| 버저닝 | 변수 기반 활성화 | lines 19-25 |
| 서버 측 암호화 | AES256, Bucket Key 활성화 | lines 28-37 |
| 퍼블릭 접근 차단 | 전면 차단 (4개 옵션 모두 true) | lines 40-47 |
| CORS | GET/HEAD 허용, ETag 노출 | lines 74-84 |

**라이프사이클 규칙** (lines 49-71):
- `delete-old-versions`: 이전 버전 30일 후 삭제 (line 58-60)
- `abort-incomplete-multipart-uploads`: 미완료 멀티파트 7일 후 중단 (line 67-69)

**CloudFront OAC 버킷 정책** (lines 86-121): `cloudfront.amazonaws.com` 서비스 프린시펄에 대한 `s3:GetObject` 허용. `AWS:SourceArn` 조건으로 특정 Distribution만 접근 가능하도록 제한한다 (line 110-112).

#### 로그 버킷 (lines 123-259)

| 라이프사이클 | 전환/만료 | 라인 |
|------------|----------|------|
| 로그 만료 | 변수 기반 일수 후 삭제 | lines 162-169 |
| Standard IA 전환 | 30일 | lines 175-178 |
| Glacier 전환 | 90일 | lines 180-183 |

ALB와 AWS 로그 딜리버리 서비스가 쓰기 가능하도록 버킷 정책이 구성된다 (lines 190-252).

---

### 4.6 Lambda Worker

**파일**: `terraform/modules/lambda-worker/main.tf` (212줄)

#### 보안 그룹 (lines 1-61)

| 이그레스 규칙 | 대상 | 포트 | 라인 |
|-------------|------|------|------|
| RDS Proxy | 프록시 SG | 5432 | lines 20-28 |
| Redis | Redis SG | 6379 | lines 31-39 |
| VPC Endpoints | VPC CIDR | 443 | lines 42-50 |
| 전체 | 0.0.0.0/0 | 모두 | lines 53-61 |

#### Lambda 함수 (lines 63-113)

```hcl
# terraform/modules/lambda-worker/main.tf (line 106-108)
tracing_config {
  mode = var.enable_xray_tracing ? "Active" : "PassThrough"
}
```

VPC 내에 배포되어 RDS Proxy, Redis 등 내부 리소스에 접근할 수 있다 (lines 86-89). X-Ray 트레이싱이 선택적으로 활성화된다.

#### SQS 이벤트 소스 매핑 (lines 115-134)

```hcl
# terraform/modules/lambda-worker/main.tf (line 125-126)
batch_size                         = var.sqs_batch_size
maximum_batching_window_in_seconds = var.sqs_batching_window_seconds
```

`ReportBatchItemFailures` 패턴을 사용하여 개별 메시지 단위로 실패를 보고한다 (line 129).

#### CloudWatch 알람 (lines 149-211)

| 알람 | 메트릭 | 임계값 | 평가 기간 | 라인 |
|------|--------|--------|----------|------|
| Errors | Lambda Errors (Sum) | 5 | 2회 x 300초 | lines 153-171 |
| Duration | Lambda Duration (Average) | 타임아웃의 80% | 2회 x 300초 | lines 173-191 |
| Throttles | Lambda Throttles (Sum) | 0 | 1회 x 300초 | lines 193-211 |

---

## 5. CI/CD 파이프라인

**파일 패턴**: `.github/workflows/*-ci-cd.yml` (각 약 262줄)

### 5.1 파이프라인 구조

Auth Service 기준으로 분석한다 (`.github/workflows/auth-service-ci-cd.yml`).

#### 트리거 (lines 3-12)

```yaml
# .github/workflows/auth-service-ci-cd.yml (line 4-6)
on:
  push:
    branches: [final, develop]
    paths:
      - 'services/auth-service/**'
```

- `final` 브랜치: 프로덕션 배포
- `develop` 브랜치: 스테이징 배포
- `workflow_dispatch`: 수동 실행 (환경 선택 가능)

#### Job 1: Build & Push to ECR (lines 28-123)

**AWS 인증**: OIDC 기반 (`aws-actions/configure-aws-credentials@v4`, line 69-72)

**빌드 파이프라인**:

| 단계 | 액션 | 설명 | 라인 |
|------|------|------|------|
| 환경 감지 | 커스텀 스크립트 | final=prod, 그 외=staging | lines 46-57 |
| 이미지 태그 생성 | SHA 7자리 + 타임스탬프 | 고유 식별자 | lines 59-66 |
| Docker Buildx | `docker/build-push-action@v5` | linux/arm64, GHA 캐시 | lines 84-103 |
| Trivy 스캔 | `aquasecurity/trivy-action` | CRITICAL/HIGH 취약점, exit-code 1 | lines 105-113 |

**이미지 태그 전략** (lines 94-97):
- `{sha}-{timestamp}` - 고유 버전
- `latest` - 최신 빌드
- `{environment}` - 환경별 태그

#### Job 2: Update Kustomize Manifests (lines 125-210)

```yaml
# .github/workflows/auth-service-ci-cd.yml (line 164)
sed -i "s|newName: .*tiketi-${{ env.SERVICE_NAME }}.*|newName: $ECR_REGISTRY/${{ env.ECR_REPOSITORY }}|g" "$KUSTOMIZE_FILE"
```

Kustomization 파일의 이미지 태그를 업데이트하고 Git 커밋/푸시한다.

**충돌 해결 전략** (lines 184-197): 최대 5회 재시도하며 `git pull --rebase`로 병렬 워크플로우 충돌을 해소한다. 재시도 간격은 2-4초 랜덤이다.

#### Job 3: Discord 알림 (lines 212-262)

```yaml
# .github/workflows/auth-service-ci-cd.yml (line 248)
"title": "${EMOJI} Auth Service 배포 ${STATUS_TEXT}",
```

배포 결과(성공/실패)를 Discord 웹훅으로 전송한다. Embed 메시지에 서비스명, 환경, 이미지 태그, GitHub Actions 링크를 포함한다.

---

## 6. 운영 스크립트

### 6.1 Kind 클러스터 관리

**spring-kind-up.sh** (94줄):
- Kind 클러스터 생성
- Docker 이미지 빌드
- Kustomize 적용
- `rollout status` 대기로 모든 Deployment 정상 기동 확인

### 6.2 포트 포워딩

**start-port-forwards.sh** (151줄):
- 모든 서비스에 대한 `kubectl port-forward` 실행
- 포트 포워딩 후 헬스 체크 수행
- 백그라운드 프로세스 관리

### 6.3 정리 스크립트

**cleanup.ps1** (PowerShell):
- Docker 이미지 삭제
- Gradle 데몬 중지

---

## 7. 옵저버빌리티 스택

### 7.1 아키텍처 개요

```
[Spring Boot App] --logs--> [Promtail DaemonSet] --push--> [Loki] <--query-- [Grafana]
[Spring Boot App] --traces-> [Zipkin]
[Spring Boot App] --metrics-> /actuator/prometheus (Prometheus scrape 가능)
```

### 7.2 구성 요소별 상세

| 구성 요소 | 이미지 | 포트 | 매니페스트 | 역할 |
|----------|--------|------|----------|------|
| Grafana | `grafana/grafana:10.2.3` | 3006 (NodePort 30006) | `grafana.yaml` | 시각화 대시보드 |
| Loki | `grafana/loki:2.9.3` | 3100 (ClusterIP) | `loki.yaml` | 로그 저장/쿼리 엔진 |
| Promtail | `grafana/promtail:2.9.3` | 9080 | `promtail.yaml` | 로그 수집 에이전트 |
| Zipkin | `openzipkin/zipkin:3` | 9411 (NodePort 30411) | `zipkin.yaml` | 분산 추적 |

### 7.3 로그 형식

Spring Boot 애플리케이션은 다음 패턴으로 로그를 출력한다:

```
%5p [{app},{traceId},{spanId}]
```

- `{app}`: 애플리케이션 이름 (spring.application.name)
- `{traceId}`: 분산 추적 ID (Zipkin/Micrometer)
- `{spanId}`: 스팬 ID

이 형식을 통해 Loki에서 traceId로 검색하고, Zipkin에서 해당 추적의 전체 경로를 시각화할 수 있다.

### 7.4 Prometheus 메트릭

모든 Spring Boot 서비스는 `/actuator/prometheus` 엔드포인트를 노출하여 Prometheus 스크레이핑을 지원한다. 현재 Kind 환경에서는 Prometheus가 별도로 배포되지 않으나, 프로덕션 환경에서 추가 구성 가능하다.

---

## 8. Lambda 함수

### 8.1 Edge Queue Check (CloudFront Lambda@Edge)

**파일**: `lambda/edge-queue-check/index.js` (175줄)

#### 목적

CloudFront의 viewer-request 이벤트에서 실행되어, 보호된 API 경로에 대한 대기열 진입 토큰을 검증한다. 유효하지 않은 토큰을 가진 요청은 대기열 페이지로 리다이렉트된다.

#### 시크릿 관리 (lines 16-25)

```javascript
// lambda/edge-queue-check/index.js (line 17-22)
let SECRET;
try {
  const config = require('./config.json');
  SECRET = config.secret;
} catch (e) {
  SECRET = process.env.QUEUE_ENTRY_TOKEN_SECRET;
}
```

Lambda@Edge는 환경 변수를 지원하지 않으므로, Terraform이 빌드 타임에 `config.json`을 생성하여 시크릿을 주입한다. 폴백으로 환경 변수도 지원한다.

#### 경로 분류 (lines 28-43)

**보호 대상 경로** (line 28-33):

| 경로 | 보호 이유 |
|------|----------|
| `/api/reservations` | 좌석 예매 |
| `/api/tickets` | 티켓 구매 |
| `/api/seats` | 좌석 조회/선택 |
| `/api/admin` | 관리자 기능 |

**우회 경로** (lines 36-43):

| 경로 | 우회 이유 |
|------|----------|
| `/api/queue` | 대기열 자체 접근 |
| `/api/auth` | 인증 플로우 |
| `/api/events` | 이벤트 목록 (비보호) |
| `/api/stats` | 통계 조회 |
| `/health` | 헬스 체크 |
| `/actuator` | 액추에이터 |

#### JWT 검증 (lines 105-139)

```javascript
// lambda/edge-queue-check/index.js (line 116-123)
const expectedSignature = crypto
  .createHmac('sha256', secret)
  .update(data)
  .digest('base64url');

if (signatureB64.length !== expectedSignature.length ||
    !crypto.timingSafeEqual(Buffer.from(signatureB64), Buffer.from(expectedSignature))) {
```

HMAC-SHA256 서명을 검증하며, `crypto.timingSafeEqual`을 사용하여 타이밍 공격을 방지한다 (line 122). 만료 시간(`exp` 클레임)도 검증한다 (line 130).

#### eventId 교차 검증 (lines 144-148)

```javascript
// lambda/edge-queue-check/index.js (line 146)
const match = uri.match(/\/api\/(?:seats|reservations|tickets)\/([a-f0-9-]{36})/i);
```

요청 경로의 UUID를 추출하여 토큰의 `sub` 클레임과 비교한다. 다른 이벤트의 토큰으로 접근을 시도하면 거부된다.

#### 리다이렉트 처리 (lines 153-174)

토큰이 없거나 유효하지 않으면 302 리다이렉트로 `/queue/{eventId}` 페이지로 보낸다. `Cache-Control: no-store, no-cache, must-revalidate` 헤더를 포함하여 리다이렉트 응답이 캐싱되지 않도록 한다 (line 169).

---

### 8.2 Ticket Worker (SQS FIFO Consumer)

**파일**: `lambda/ticket-worker/index.js` (95줄)

#### 목적

SQS FIFO 큐에서 티켓 처리 메시지를 소비하여 ticket-service의 내부 API를 호출한다.

#### 설정 (lines 12-28)

```javascript
// lambda/ticket-worker/index.js (line 14-15)
const TICKET_SERVICE_URL = process.env.TICKET_SERVICE_URL || 'http://localhost:3002';
const INTERNAL_API_TOKEN = process.env.INTERNAL_API_TOKEN;
```

내부 서비스 인증을 위해 `Authorization: Bearer {token}` 헤더를 사용한다 (line 26). 요청 타임아웃은 10초이다 (line 19).

#### ReportBatchItemFailures 패턴 (lines 30-53)

```javascript
// lambda/ticket-worker/index.js (line 31)
const batchItemFailures = [];
```

배치 내 개별 메시지의 성공/실패를 독립적으로 처리한다. 실패한 메시지만 `batchItemFailures` 배열에 추가하여 SQS가 해당 메시지만 재시도하도록 한다 (line 46-48).

#### 지원 액션 (lines 55-94)

| 액션 | 내부 API | 필수 필드 | 라인 |
|------|---------|----------|------|
| `seat_reserve` | `POST /internal/seats/reserve` | eventId, userId, seatIds | lines 59-69 |
| `reservation_create` | `POST /internal/reservations` | eventId, userId, items | lines 72-83 |
| `admitted` | 로그만 기록 | userId, eventId | lines 85-89 |

`admitted` 액션은 대기열 서비스가 진입 토큰을 이미 발급한 후이므로 로그만 기록한다 (line 87 주석).

알 수 없는 액션은 경고 로그를 출력하고 메시지를 건너뛴다 (line 92). 예외를 발생시키지 않으므로 DLQ로 이동하지 않는다.

---

## 부록: 인프라 아키텍처 요약도

### 트래픽 흐름 (프로덕션)

```
사용자
  |
  v
CloudFront (TLS 1.2+, Lambda@Edge 토큰 검증)
  |
  +-- /static/*, /_next/static/* --> S3 (OAC)
  |
  +-- /* (API) --> ALB (HTTPS, CF prefix list only)
                    |
                    v
                  Gateway Service (:3001)
                    |
                    +---> Auth Service (:3005)
                    +---> Ticket Service (:3002)
                    +---> Payment Service (:3003)
                    +---> Stats Service (:3004)
                    +---> Queue Service (:3007)
                    +---> Community Service (:3008)
                    |
                    +---> PostgreSQL (RDS Proxy -> RDS Multi-AZ)
                    +---> Redis (ElastiCache, 암호화/인증)
                    +---> Kafka (메시지 브로커)

SQS FIFO --> Lambda Worker --> Ticket Service (내부 API)
```

### 환경별 비교

| 항목 | Kind (로컬) | 프로덕션 (AWS) |
|------|------------|--------------|
| 네임스페이스 | `tiketi-spring` | `tiketi-prod` |
| PostgreSQL | `postgres:15-alpine` 단일 Pod | RDS PostgreSQL 16, Multi-AZ |
| Redis/캐시 | Dragonfly 512MB | ElastiCache, 암호화/인증 |
| Kafka | 단일 브로커 KRaft | 관리형 또는 별도 구성 |
| 레플리카 | 각 1개 | 2-3개 기본, HPA 최대 10개 |
| 스케일링 | 없음 | HPA (CPU 70%) |
| 가용성 | 없음 | PDB (minAvailable=1) |
| 인증서 | 없음 | ACM, TLS 1.3/1.2 |
| 모니터링 | Grafana + Loki + Zipkin | CloudWatch + X-Ray |
| CDN | 없음 | CloudFront + Lambda@Edge |
| 스토리지 | PVC (standard) | gp3 암호화, S3 버저닝 |
