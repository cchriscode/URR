# URR 인프라 종합 분석

> 분석일: 2026-02-16
> 분석 대상: Terraform 모듈 18개, K8s 매니페스트 4개 환경, CI/CD 워크플로 15개, ArgoCD 3개 환경

---

## 1. 전체 AWS 아키텍처

### 1.1 아키텍처 다이어그램 (ASCII)

```
                            [사용자]
                               |
                          [Route 53]
                               |
                        [CloudFront] -----> [S3: 정적 자산]
                        |    |    |          (frontend, _next/static/*)
                        |    |    |
              Lambda@Edge    |    CloudFront Function
           (큐 토큰 검증)    |    (VWR 페이지 리라이트)
                        |    |
              +---------+    +--------+
              |                       |
        [ALB: HTTPS]          [API Gateway]
        (CloudFront Only)      (VWR Tier 1)
              |                       |
     +--------+--------+      [Lambda: VWR API]
     |  Public Subnets  |            |
     +--------+---------+     [DynamoDB]
              |                (counters, positions)
     +--------+---------+
     |  App Subnets      |
     |  (EKS Nodes)      |
     |                    |
     |  +--[Gateway:3001]-+---> [Auth:3005]
     |  |                 |---> [Ticket:3002] ---> [Kafka/MSK]
     |  |                 |---> [Payment:3003] --> [Kafka/MSK]
     |  |                 |---> [Stats:3004] ----> [Kafka/MSK]
     |  |                 |---> [Queue:3007] ----> [SQS FIFO]
     |  |                 |---> [Catalog:3009]
     |  |                 |---> [Community:3008]
     |  |                 |
     |  +--[Frontend:3000]|
     |                    |
     +--------+-----------+
              |
     +--------+-----------+     +-------------------+
     |  DB Subnets        |     |  Cache Subnets     |
     |  [RDS Proxy] ------+---> |  [ElastiCache      |
     |       |             |     |   Redis Cluster]   |
     |  [RDS PostgreSQL]   |     +-------------------+
     |  (Multi-AZ)         |
     +---------------------+     +-------------------+
                                 |  Streaming Subnets |
                                 |  [MSK Kafka]       |
                                 |  [Lambda Worker]   |
                                 |       |            |
                                 |  [SQS FIFO] ---+   |
                                 |       |        |   |
                                 |  [SQS DLQ]     |   |
                                 +----------------+---+
                                                  |
                                          [CloudWatch Alarms]
                                          [Secrets Manager]
                                          [KMS]
```

### 1.2 트래픽 흐름 요약

| 흐름 | 경로 | 프로토콜 |
|------|------|----------|
| 웹 요청 (API) | User -> CloudFront -> ALB(443) -> Gateway(3001) -> 내부 서비스 | HTTPS -> HTTP |
| 웹 요청 (정적) | User -> CloudFront -> S3 (OAC) | HTTPS |
| VWR 대기열 | User -> CloudFront -> API Gateway -> Lambda -> DynamoDB | HTTPS |
| 이벤트 처리 | Ticket-service -> SQS FIFO -> Lambda Worker -> RDS/Redis | 내부 |
| Kafka 이벤트 | Service -> MSK -> Stats/Payment 등 Consumer | TLS(9094) |
| DB 접근 | EKS Pod -> RDS Proxy(app subnet) -> RDS(db subnet) | TLS(5432) |
| 캐시 접근 | EKS Pod -> ElastiCache Redis(cache subnet) | TLS(6379) |

---

## 2. 사용 중인 AWS 서비스 상세

### 2.1 AWS 서비스 전체 목록

| AWS 서비스 | 용도 | Terraform 모듈 | 핵심 설정 |
|-----------|------|---------------|-----------|
| **VPC** | 네트워크 격리 | `terraform/modules/vpc/` | 10.0.0.0/16, 2 AZ, 5개 서브넷 계층 |
| **EKS** | 쿠버네티스 클러스터 | `terraform/modules/eks/` | v1.28, OIDC/IRSA, KMS 암호화 |
| **RDS PostgreSQL** | 관계형 데이터베이스 | `terraform/modules/rds/` | v16.4, Multi-AZ, gp3, RDS Proxy |
| **RDS Proxy** | DB 연결 풀링 | `terraform/modules/rds/` (내장) | TLS 필수, app 서브넷 배치 |
| **ElastiCache Redis** | 캐시/세션 | `terraform/modules/elasticache/` | 자동 Failover, TLS, AUTH 토큰 |
| **MSK (Kafka)** | 이벤트 스트리밍 | `terraform/modules/msk/` | v3.6.0, 2 브로커, TLS+IAM |
| **CloudFront** | CDN/WAF | `terraform/modules/cloudfront/` | Lambda@Edge, OAC, 보안 헤더 |
| **Lambda@Edge** | 큐 토큰 검증 | `terraform/modules/cloudfront/` (내장) | Node.js 20.x, us-east-1 |
| **Lambda (VWR API)** | VWR 대기열 API | `terraform/modules/lambda-vwr/` | Node.js 20.x, DynamoDB 접근 |
| **Lambda (Counter Advancer)** | VWR 카운터 증가 | `terraform/modules/lambda-vwr/` (내장) | EventBridge 1분 트리거 |
| **Lambda (Ticket Worker)** | SQS 이벤트 처리 | `terraform/modules/lambda-worker/` | VPC 내부, X-Ray 추적 |
| **S3** | 프론트엔드 정적 자산 | `terraform/modules/s3/` | 버전 관리, AES256, 공개 차단 |
| **S3 (로그)** | ALB/앱 로그 저장 | `terraform/modules/s3/` (내장) | 수명 주기: 30d IA, 90d Glacier |
| **ALB** | 로드 밸런서 | `terraform/modules/alb/` | TLS 1.3, CloudFront Prefix List |
| **SQS FIFO** | 티켓 이벤트 큐 | `terraform/modules/sqs/` | 중복 제거, DLQ, Long Polling |
| **DynamoDB** | VWR 대기열 상태 | `terraform/modules/dynamodb-vwr/` | PAY_PER_REQUEST, GSI, TTL |
| **API Gateway** | VWR REST API | `terraform/modules/api-gateway-vwr/` | REGIONAL, 스로틀링 |
| **IAM** | 권한 관리 | `terraform/modules/iam/` | EKS/Lambda/RDS Proxy 역할 |
| **Secrets Manager** | 시크릿 관리 | `terraform/modules/secrets/` | RDS/Redis/JWT/Queue 토큰 |
| **KMS** | 암호화 키 | EKS 모듈에서 참조 | EKS secrets 암호화 |
| **CloudWatch** | 모니터링/로깅 | 각 모듈 내장 | EKS/Lambda/MSK/SQS 로그 |
| **EventBridge** | 스케줄 트리거 | `terraform/modules/lambda-vwr/` (내장) | VWR 카운터 1분 간격 |
| **VPC Endpoints** | 프라이빗 서비스 접근 | `terraform/modules/vpc-endpoints/` | 9개 Interface + 2개 Gateway |
| **ECR** | 컨테이너 레지스트리 | CI/CD에서 사용 | VPC Endpoint로 프라이빗 접근 |

### 2.2 VPC Endpoints 상세

| 엔드포인트 | 유형 | 서브넷 | 용도 |
|-----------|------|--------|------|
| `ec2` | Interface | app | EKS 노드 통신 |
| `ecr.api` | Interface | app | ECR API 호출 |
| `ecr.dkr` | Interface | app | Docker 이미지 Pull |
| `eks` | Interface | app | EKS API 호출 |
| `sts` | Interface | app | IRSA 역할 Assume |
| `logs` | Interface | app | CloudWatch 로그 전송 |
| `secretsmanager` | Interface | app | 시크릿 조회 |
| `elasticloadbalancing` | Interface | app | ALB 관리 |
| `autoscaling` | Interface | app | 노드 그룹 스케일링 |
| `s3` | Gateway | 전체 라우트 테이블 | S3 접근 (무료) |
| `dynamodb` | Gateway | 전체 라우트 테이블 | DynamoDB 접근 (무료) |

> 파일 위치: `C:\Users\USER\URR\terraform\modules\vpc-endpoints\main.tf` (라인 46-198)

---

## 3. 네트워크 구조

### 3.1 VPC 서브넷 설계

```
VPC CIDR: 10.0.0.0/16 (65,536 IPs)
AZ 구성: 2개 AZ (자동 감지)

+-----------------------------------------------------------+
|  VPC: 10.0.0.0/16                                         |
|                                                           |
|  +--Public Subnets--+  +--Public Subnets--+               |
|  | 10.0.0.0/24      |  | 10.0.1.0/24      |  <-- ALB, NAT|
|  | (AZ-a)           |  | (AZ-b)           |              |
|  +-------------------+  +-------------------+              |
|                                                           |
|  +--App Subnets-----+  +--App Subnets-----+               |
|  | 10.0.10.0/24     |  | 10.0.11.0/24     |  <-- EKS     |
|  | (AZ-a)           |  | (AZ-b)           |              |
|  +-------------------+  +-------------------+              |
|                                                           |
|  +--DB Subnets------+  +--DB Subnets------+               |
|  | 10.0.20.0/24     |  | 10.0.21.0/24     |  <-- RDS     |
|  | (AZ-a)           |  | (AZ-b)           |              |
|  +-------------------+  +-------------------+              |
|                                                           |
|  +--Cache Subnets---+  +--Cache Subnets---+               |
|  | 10.0.30.0/24     |  | 10.0.31.0/24     |  <-- Redis   |
|  | (AZ-a)           |  | (AZ-b)           |              |
|  +-------------------+  +-------------------+              |
|                                                           |
|  +--Streaming-------+  +--Streaming-------+               |
|  | 10.0.40.0/24     |  | 10.0.41.0/24     |  <-- MSK,    |
|  | (AZ-a)           |  | (AZ-b)           |     Lambda   |
|  +-------------------+  +-------------------+              |
+-----------------------------------------------------------+
```

> 파일 위치: `C:\Users\USER\URR\terraform\modules\vpc\main.tf` (라인 1-9)

### 3.2 라우팅 구성

| 서브넷 계층 | 아웃바운드 경로 | 인바운드 접근 |
|-----------|--------------|-------------|
| **Public** | Internet Gateway -> 0.0.0.0/0 | 인터넷에서 직접 접근 가능 |
| **App** | NAT Gateway -> 0.0.0.0/0 (AZ별) | VPC 내부 + ALB에서만 |
| **DB** | 라우트 없음 (격리) | App 서브넷에서만 (SG 제한) |
| **Cache** | DB 라우트 테이블 공유 (격리) | App 서브넷에서만 (SG 제한) |
| **Streaming** | NAT Gateway -> 0.0.0.0/0 (AZ별) | App 서브넷 + Lambda에서 |

### 3.3 보안 그룹 연결 관계

```
ALB SG ----[443/80]----> (CloudFront Prefix List만 허용)
   |
   +----[0-65535/tcp]--> EKS Nodes SG
                              |
                              +----[5432/tcp]----> RDS Proxy SG ----> RDS SG
                              +----[6379/tcp]----> Redis SG
                              +----[9092,9094,9098/tcp]----> MSK SG
                              |
Lambda Worker SG ----[5432]----> RDS Proxy SG
                 ----[6379]----> Redis SG
                 ----[443]-----> VPC Endpoints SG
```

> 파일 위치:
> - ALB SG: `C:\Users\USER\URR\terraform\modules\alb\main.tf` (라인 5-88)
> - EKS SG: `C:\Users\USER\URR\terraform\modules\eks\main.tf` (라인 5-91)
> - RDS SG: `C:\Users\USER\URR\terraform\modules\rds\main.tf` (라인 5-256)
> - Redis SG: `C:\Users\USER\URR\terraform\modules\elasticache\main.tf` (라인 5-40)
> - MSK SG: `C:\Users\USER\URR\terraform\modules\msk\main.tf` (라인 5-87)
> - Lambda SG: `C:\Users\USER\URR\terraform\modules\lambda-worker\main.tf` (라인 5-61)

---

## 4. Terraform 구조

### 4.1 모듈 구조 및 의존성

```
terraform/
  modules/
    vpc/                  # VPC, 서브넷, NAT, IGW, 라우트 테이블
    vpc-endpoints/        # 11개 VPC Endpoint (Interface 9 + Gateway 2)
    iam/                  # IAM 역할 6개 (EKS Cluster/Node, Lambda x2, RDS Monitoring/Proxy)
    eks/                  # EKS 클러스터, 노드 그룹, Addon 4개, OIDC, IRSA
    rds/                  # RDS PostgreSQL, RDS Proxy, 파라미터 그룹
    elasticache/          # Redis Replication Group, 파라미터 그룹
    msk/                  # MSK Kafka 클러스터, 구성, CloudWatch 알람
    alb/                  # ALB, 타겟 그룹, HTTPS/HTTP 리스너
    cloudfront/           # CloudFront 배포, Lambda@Edge, CF Function, 캐시 정책
    s3/                   # S3 버킷 (프론트엔드 + 로그)
    sqs/                  # SQS FIFO 큐 + DLQ + CloudWatch 알람
    dynamodb-vwr/         # DynamoDB 테이블 2개 (VWR counters + positions)
    api-gateway-vwr/      # API Gateway REST API (VWR 대기열)
    lambda-vwr/           # Lambda 2개 (VWR API + Counter Advancer)
    lambda-worker/        # Lambda 1개 (SQS -> RDS/Redis 이벤트 처리)
    secrets/              # Secrets Manager 4개 (RDS/Redis/JWT/Queue Token)
  environments/
    prod/                 # 프로덕션 환경 (15개 모듈 사용)
    staging/              # 스테이징 환경 (14개 모듈 - CloudFront 제외)
```

### 4.2 Terraform 모듈 의존성 그래프

```
[iam] --------+
              |
[vpc] --------+--------> [vpc-endpoints]
              |
              +--------> [eks] --------> [alb]
              |            |               |
              |            +--------> [rds] (+ RDS Proxy)
              |            |
              |            +--------> [elasticache]
              |            |
              |            +--------> [msk]
              |
[secrets] ----+--------> [cloudfront] <--- [alb], [s3], [lambda-vwr]
              |
              +--------> [sqs]
              |
[dynamodb-vwr] --------> [lambda-vwr] <--- [api-gateway-vwr]
              |
              +--------> [lambda-worker] <--- [sqs], [rds], [elasticache], [msk]
```

### 4.3 상태 관리 (Backend)

| 항목 | 프로덕션 | 스테이징 |
|------|---------|---------|
| **Backend** | S3 | 미설정 (로컬 추정) |
| **State 버킷** | `urr-terraform-state-prod` | - |
| **State 키** | `prod/terraform.tfstate` | - |
| **잠금 테이블** | `urr-terraform-locks` (DynamoDB) | - |
| **리전** | `ap-northeast-2` | - |
| **암호화** | 활성화 | - |

> 파일 위치: `C:\Users\USER\URR\terraform\environments\prod\main.tf` (라인 11-17)

### 4.4 환경별 차이점

| 설정 항목 | Production | Staging |
|----------|------------|---------|
| **RDS Multi-AZ** | `true` | `false` |
| **RDS Proxy** | 활성화 | 활성화 |
| **RDS 삭제 보호** | `true` | `false` |
| **ElastiCache 노드** | 2개 (Primary + Replica) | 1개 (Primary만) |
| **ALB 삭제 보호** | `true` | `false` |
| **CloudFront** | 전체 구성 | 미사용 (ALB 직접 접근) |
| **Lambda@Edge** | 활성화 | 미사용 |
| **VWR API throttle** | burst 10,000 / rate 5,000 | burst 2,000 / rate 1,000 |
| **VWR Lambda 동시성** | 100 | 20 |
| **Counter Advancer 배치** | 500 | 100 |
| **Lambda Worker 동시성** | 10 | 5 |
| **DynamoDB PITR** | `true` | `false` |
| **MSK** | TLS + IAM, 2 브로커 | TLS + IAM, 2 브로커 |
| **S3 CloudFront 정책** | OAC 연결 | 미연결 |

> 파일 위치:
> - Prod: `C:\Users\USER\URR\terraform\environments\prod\main.tf`
> - Staging: `C:\Users\USER\URR\terraform\environments\staging\main.tf`

### 4.5 시크릿 관리 흐름

```
[Terraform random_password] --> [Secrets Manager]
                                      |
                    +-----------------+------------------+
                    |                 |                  |
              [RDS credentials]  [Redis token]    [JWT secret]
              (username/password/ (auth_token)    [Queue token]
               host/port/dbname)
                    |                 |                  |
              [RDS Proxy]        [ElastiCache]    [K8s Secret]
              (IAM으로 읽기)     (auth_token)     (secretGenerator)
                    |                 |                  |
              [EKS Pod]          [EKS Pod]        [EKS Pod]
              (환경 변수)        (환경 변수)       (환경 변수)
```

Secrets Manager에 저장되는 시크릿 4개:
1. `{prefix}/rds-credentials` - PostgreSQL 접속 정보 (라인 10-28)
2. `{prefix}/redis-auth-token` - Redis AUTH 토큰 (라인 39-52)
3. `{prefix}/queue-entry-token-secret` - HMAC-SHA256 큐 토큰 (라인 63-76)
4. `{prefix}/jwt-secret` - Auth 서비스 JWT 시크릿 (라인 87-100)

> 파일 위치: `C:\Users\USER\URR\terraform\modules\secrets\main.tf`

---

## 5. Kubernetes 구조

### 5.1 K8s 리소스 요약

| 리소스 유형 | 개수 | 대상 서비스 |
|-----------|------|-----------|
| **Deployment** | 9 | gateway, auth, ticket, payment, stats, queue, catalog, community, frontend |
| **Service (ClusterIP)** | 9 | 위 서비스 각각 |
| **Service (Preview)** | 4 | gateway, ticket, payment, queue (Blue/Green) |
| **NetworkPolicy** | 11 | default-deny + 서비스별 ingress/egress |
| **HPA** | 4 | gateway, ticket, queue, payment |
| **PDB** | 7 | gateway, auth, ticket, payment, stats, queue, community |
| **Rollout (Argo)** | 4 | gateway, ticket, payment, queue |
| **AnalysisTemplate** | 1 | health-check (모든 Rollout 공유) |
| **ConfigMap** | 환경별 1 | spring-{env}-config |
| **Secret** | 환경별 1 | spring-{env}-secret |

### 5.2 서비스 포트 매핑

| 서비스 | 포트 | 프로파일 | DB 연결 | Kafka 사용 | Redis 사용 |
|--------|------|---------|---------|-----------|-----------|
| **gateway-service** | 3001 | prod | - | - | O |
| **auth-service** | 3005 | prod | auth_db | - | - |
| **ticket-service** | 3002 | prod | ticket_db | O (Producer) | O |
| **payment-service** | 3003 | prod | payment_db | O (Producer) | - |
| **stats-service** | 3004 | prod | stats_db | O (Consumer) | - |
| **queue-service** | 3007 | prod | - | - | O |
| **catalog-service** | 3009 | prod | ticket_db | O | O |
| **community-service** | 3008 | prod | community_db | - | - |
| **frontend** | 3000 | - | - | - | - |

> 파일 위치: `C:\Users\USER\URR\k8s\spring\overlays\prod\patches\services-env.yaml`

### 5.3 Kustomize 구조

```
k8s/spring/
  base/                          # 기본 리소스 정의
    kustomization.yaml           # 네임스페이스: urr-dev
    network-policies.yaml        # 11개 NetworkPolicy
    {service}/deployment.yaml    # 9개 Deployment (공통 보안 컨텍스트)
    {service}/service.yaml       # 9개 ClusterIP Service
  overlays/
    dev/                         # 개발 환경 (base만 사용)
      kustomization.yaml
    kind/                        # 로컬 Kind 클러스터
      kustomization.yaml         # 네임스페이스: urr-spring
      namespace.yaml
      postgres.yaml              # PostgreSQL StatefulSet
      dragonfly.yaml             # Dragonfly (Redis 호환)
      kafka.yaml                 # Kafka (단일 노드)
      zipkin.yaml                # 분산 추적
      pvc.yaml                   # PersistentVolumeClaim
      loki.yaml                  # 로그 수집
      promtail.yaml              # 로그 포워딩
      grafana.yaml               # 대시보드
      grafana-dashboards.yaml    # 대시보드 ConfigMap
      prometheus.yaml            # 메트릭 수집
      patches/*.yaml             # 서비스별 환경 변수 패치 + NodePort
    staging/                     # 스테이징 환경
      kustomization.yaml         # 네임스페이스: urr-staging
      patches/replicas.yaml      # 레플리카 설정
      patches/services-env.yaml  # 환경 변수 패치
    prod/                        # 프로덕션 환경
      kustomization.yaml         # 네임스페이스: urr-spring
      hpa.yaml                   # HPA 4개
      pdb.yaml                   # PDB 7개
      kafka.yaml                 # Kafka 참조 설정
      redis.yaml                 # Redis 참조 설정
      analysis-template.yaml     # Argo Rollout 분석 템플릿
      preview-services.yaml      # 프리뷰 서비스 4개
      rollouts/*.yaml            # Argo Rollout 4개
      patches/replicas.yaml      # 프로덕션 레플리카
      patches/services-env.yaml  # 프로덕션 환경 변수
```

### 5.4 프로덕션 레플리카 및 HPA 설정

| 서비스 | 기본 레플리카 | HPA Min | HPA Max | CPU 임계값 | PDB minAvailable |
|--------|-------------|---------|---------|-----------|-----------------|
| **gateway-service** | 3 | 3 | 10 | 70% | 1 |
| **ticket-service** | 3 | 3 | 10 | 70% | 1 |
| **queue-service** | 3 | 3 | 8 | 70% | 1 |
| **payment-service** | 2 | 2 | 6 | 70% | 1 |
| **auth-service** | 2 | - | - | - | 1 |
| **stats-service** | 2 | - | - | - | 1 |
| **community-service** | 2 | - | - | - | 1 |
| **frontend** | 2 | - | - | - | - |
| **catalog-service** | - | - | - | - | - |

> 파일 위치:
> - 레플리카: `C:\Users\USER\URR\k8s\spring\overlays\prod\patches\replicas.yaml`
> - HPA: `C:\Users\USER\URR\k8s\spring\overlays\prod\hpa.yaml`
> - PDB: `C:\Users\USER\URR\k8s\spring\overlays\prod\pdb.yaml`

### 5.5 NetworkPolicy 설계

기본 정책은 **default-deny-all** (Ingress/Egress 모두 차단)이며, 서비스별로 필요한 통신만 허용한다.

| 정책명 | 대상 Pod | 허용 소스 | 허용 포트 |
|--------|---------|----------|----------|
| `default-deny-all` | 전체 | 없음 | 없음 |
| `allow-gateway-ingress` | gateway | 모든 소스 | 3001 |
| `allow-frontend-ingress` | frontend | 모든 소스 | 3000 |
| `allow-auth-service-ingress` | auth | gateway, catalog | 3005 |
| `allow-ticket-service-ingress` | ticket | gateway, payment, catalog | 3002 |
| `allow-catalog-service-ingress` | catalog | gateway, queue | 3009 |
| `allow-payment-service-ingress` | payment | gateway | 3003 |
| `allow-stats-service-ingress` | stats | gateway | 3004 |
| `allow-queue-service-ingress` | queue | gateway | 3007 |
| `allow-community-service-ingress` | community | gateway | 3008 |
| `allow-backend-egress` | tier: backend | 네임스페이스 내부 + DNS | 53 (UDP/TCP) |
| `allow-gateway-to-services` | gateway | tier: backend + DNS | 53 (UDP) |

> 파일 위치: `C:\Users\USER\URR\k8s\spring\base\network-policies.yaml`

### 5.6 Argo Rollouts (Blue/Green 배포)

프로덕션 환경에서 핵심 서비스 4개에 Blue/Green 배포 전략을 적용한다.

**적용 서비스:**
- `gateway-service` -> `gateway-service-preview`
- `ticket-service` -> `ticket-service-preview`
- `payment-service` -> `payment-service-preview`
- `queue-service` -> `queue-service-preview`

**배포 전략 구성 (ticket-service 예시):**
```yaml
strategy:
  blueGreen:
    activeService: ticket-service
    previewService: ticket-service-preview
    autoPromotionEnabled: false           # 수동 승격 필요
    prePromotionAnalysis:
      templates:
        - templateName: health-check      # /health 엔드포인트 5회 검증
      args:
        - name: service-name
          value: ticket-service-preview
        - name: port
          value: "3002"
    scaleDownDelaySeconds: 30
```

**AnalysisTemplate (health-check):**
- 10초 간격으로 5회 헬스 체크 수행
- 실패 허용: 1회
- 성공 조건: HTTP 200 응답

> 파일 위치:
> - Rollout: `C:\Users\USER\URR\k8s\spring\overlays\prod\rollouts\ticket-service.yaml`
> - AnalysisTemplate: `C:\Users\USER\URR\k8s\spring\overlays\prod\analysis-template.yaml`
> - Argo Rollouts 설치: `C:\Users\USER\URR\k8s\argo-rollouts\kustomization.yaml` (v1.7.2)

### 5.7 컨테이너 보안 설정

모든 Deployment에 다음 보안 컨텍스트가 적용된다:

```yaml
spec:
  securityContext:
    runAsNonRoot: true
    runAsUser: 1000
    fsGroup: 1000
  containers:
    - securityContext:
        allowPrivilegeEscalation: false
        readOnlyRootFilesystem: false
        capabilities:
          drop: [ALL]
```

> 파일 위치: `C:\Users\USER\URR\k8s\spring\base\gateway-service\deployment.yaml` (라인 18-31)

---

## 6. CI/CD 파이프라인

### 6.1 GitHub Actions 워크플로 전체 목록

| 워크플로 | 파일명 | 트리거 | 용도 |
|---------|--------|--------|------|
| **PR Validation** | `pr-validation.yml` | PR to main | 변경된 서비스만 테스트 (matrix) |
| **Reusable Spring CI/CD** | `reusable-spring-ci-cd.yml` | workflow_call | 공용 빌드/테스트/배포 파이프라인 |
| **Auth Service** | `auth-service-ci-cd.yml` | push(main) + dispatch | auth-service 배포 |
| **Ticket Service** | `ticket-service-ci-cd.yml` | push(main) + dispatch | ticket-service 배포 |
| **Payment Service** | `payment-service-ci-cd.yml` | push(main) + dispatch | payment-service 배포 |
| **Stats Service** | `stats-service-ci-cd.yml` | push(main) + dispatch | stats-service 배포 |
| **Gateway Service** | `gateway-service-ci-cd.yml` | push(main) + dispatch | gateway-service 배포 |
| **Queue Service** | `queue-service-ci-cd.yml` | push(main) + dispatch | queue-service 배포 |
| **Catalog Service** | `catalog-service-ci-cd.yml` | push(main) + dispatch | catalog-service 배포 |
| **Community Service** | `community-service-ci-cd.yml` | push(main) + dispatch | community-service 배포 |
| **Frontend CI/CD** | `frontend-ci-cd.yml` | push(main) + dispatch | Next.js 프론트엔드 배포 |
| **E2E Tests** | `e2e-tests.yml` | workflow_call + dispatch | Playwright E2E 테스트 |
| **Load Tests** | `load-tests.yml` | workflow_call + 주간 스케줄 | k6 부하/카오스 테스트 |
| **Manual Rollback** | `rollback.yml` | workflow_dispatch | 수동 롤백 (서비스/태그 선택) |

### 6.2 Reusable CI/CD 파이프라인 흐름

```
[코드 Push to main]
       |
       v
+--[서비스별 워크플로]--+
| (path-based trigger) |
| auth-service-ci-cd   |
| ticket-service-ci-cd |
| ...                  |
+---------+------------+
          |
          | (prod인 경우)
          +----> [prod-approval] (GitHub Environment: production)
          |
          v
+--[reusable-spring-ci-cd.yml]--+
|                                |
|  1. [unit-test]                |   JDK 21, Gradle 캐시
|       |                        |
|  2. [integration-test]         |   integrationTest 태스크
|       |                        |
|  3. [build-and-push]           |   Docker Buildx (arm64)
|     - OIDC AWS 인증             |   ECR Push (3 태그)
|     - Trivy 보안 스캔           |   SARIF 결과 출력
|       |                        |
|  4. [update-manifests]         |   Kustomize 이미지 태그 업데이트
|     - sed로 kustomization.yaml |   Git commit + push (5회 재시도)
|     - [skip ci] 커밋           |
|       |                        |
|  5. [e2e-gate] (staging만)     |   Playwright 테스트
|       |                        |
|  6. [load-test] (staging만)    |   k6 부하 테스트
|       |                        |
|  7. [notify]                   |   Discord 웹훅 알림
+--------------------------------+
```

> 파일 위치: `C:\Users\USER\URR\.github\workflows\reusable-spring-ci-cd.yml`

### 6.3 이미지 태깅 전략

```
태그 형식: {short-sha}-{YYYYMMDD-HHMMSS}
예시: a1b2c3d-20260216-143022

ECR에 3개 태그 동시 Push:
1. {registry}/urr/{service}:{sha-timestamp}  <- 고유 식별자
2. {registry}/urr/{service}:latest            <- 최신 빌드
3. {registry}/urr/{service}:{environment}     <- 환경별 최신
```

### 6.4 Docker 이미지 빌드

**Spring Boot 서비스 Dockerfile (multi-stage):**
```
Stage 1: eclipse-temurin:21-jdk (빌드)
  - Gradle 의존성 캐시
  - bootJar 빌드 (테스트 제외)
Stage 2: eclipse-temurin:21-jre (실행)
  - app.jar 복사
  - 비루트 사용자 (uid 1001)
```

**Frontend Dockerfile (multi-stage):**
```
Stage 1: node:20-alpine (빌드)
  - npm ci
  - NEXT_PUBLIC_API_URL ARG
  - npm run build
Stage 2: node:20-alpine (실행)
  - 프로덕션 종속성만 설치
  - 비루트 사용자 (uid 1001)
```

빌드 플랫폼: **linux/arm64** (Graviton 인스턴스 최적화)

> 파일 위치:
> - Spring: `C:\Users\USER\URR\services-spring\ticket-service\Dockerfile`
> - Frontend: `C:\Users\USER\URR\apps\web\Dockerfile`

### 6.5 부하/카오스 테스트 시나리오

| 카테고리 | 시나리오 | 설명 |
|---------|---------|------|
| **Load** | `browse-events` | 이벤트 목록 조회 부하 |
| **Load** | `booking-flow` | 예매 전체 흐름 부하 |
| **Load** | `queue-rush` | 대기열 집중 부하 |
| **Load** | `mixed-traffic` | 혼합 트래픽 패턴 |
| **Chaos** | `service-failure` | 서비스 장애 주입 |
| **Chaos** | `network-latency` | 네트워크 지연 주입 |
| **Chaos** | `redis-failure` | Redis 장애 주입 |

스케줄: 매주 월요일 02:00 UTC 자동 실행

> 파일 위치: `C:\Users\USER\URR\.github\workflows\load-tests.yml`

### 6.6 롤백 절차

```
[수동 트리거: workflow_dispatch]
  - 서비스 선택 (9개 중 택 1)
  - 이미지 태그 입력 (롤백 대상)
  - 환경 선택 (staging/prod)
       |
       v
  (prod인 경우 GitHub Environment 승인 필요)
       |
       v
  [kustomization.yaml 이미지 태그 변경]
       |
       v
  [Git commit + push] -> [ArgoCD 감지] -> [K8s 롤백]
       |
       v
  [Discord 알림: ROLLBACK 메시지]
```

> 파일 위치: `C:\Users\USER\URR\.github\workflows\rollback.yml`

---

## 7. ArgoCD 구성

### 7.1 Application 정의

| 환경 | Application 이름 | 소스 경로 | 대상 네임스페이스 | Sync 정책 |
|------|-----------------|----------|----------------|----------|
| **Dev** | `urr-spring-dev` | `k8s/spring/overlays/dev` | `urr-dev` | automated (prune + selfHeal) |
| **Staging** | `urr-spring-staging` | `k8s/spring/overlays/staging` | `urr-staging` | automated (prune + selfHeal) |
| **Prod** | `urr-spring-prod` | `k8s/spring/overlays/prod` | `urr-spring` | 수동 sync (automated 없음) |

**GitOps 흐름:**
1. CI/CD가 `kustomization.yaml`에서 이미지 태그를 업데이트하고 Git push
2. ArgoCD가 Git 변경 감지
3. Dev/Staging: 자동 sync (prune + selfHeal)
4. Prod: 수동 sync 필요 (안전 장치)

> 파일 위치:
> - `C:\Users\USER\URR\argocd\applications\urr-spring-dev.yaml`
> - `C:\Users\USER\URR\argocd\applications\urr-spring-staging.yaml`
> - `C:\Users\USER\URR\argocd\applications\urr-spring-prod.yaml`

---

## 8. 로컬 개발 환경 (Kind)

### 8.1 Kind 클러스터 구성

```yaml
# kind-config-single.yaml
kind: Cluster
name: urr-local
nodes:
  - role: control-plane
    extraPortMappings:
      - containerPort: 30000, hostPort: 3001  # Backend API
      - containerPort: 30005, hostPort: 3000  # Frontend
      - containerPort: 30006, hostPort: 3006  # Grafana
      - containerPort: 30432, hostPort: 15432 # PostgreSQL (디버깅)
```

### 8.2 Kind 환경 인프라 구성

Kind 오버레이에서 AWS 관리형 서비스를 로컬 대체품으로 교체한다:

| AWS 서비스 | Kind 대체 | 파일 |
|-----------|----------|------|
| RDS PostgreSQL | PostgreSQL StatefulSet | `postgres.yaml` |
| ElastiCache Redis | Dragonfly (Redis 호환) | `dragonfly.yaml` |
| MSK Kafka | Kafka (단일 노드) | `kafka.yaml` |
| CloudWatch | Prometheus + Grafana | `prometheus.yaml`, `grafana.yaml` |
| CloudWatch Logs | Loki + Promtail | `loki.yaml`, `promtail.yaml` |
| X-Ray | Zipkin | `zipkin.yaml` |
| S3/CloudFront | NodePort 직접 접근 | `patches/frontend-service-nodeport.yaml` |

> 파일 위치: `C:\Users\USER\URR\k8s\spring\overlays\kind\kustomization.yaml`

---

## 9. 모니터링 및 옵저버빌리티

### 9.1 CloudWatch 알람 목록

| 서비스 | 알람 | 조건 | 파일 |
|--------|------|------|------|
| **SQS DLQ** | ticket-events-dlq-messages | DLQ 메시지 > 0 | `sqs/main.tf:97` |
| **SQS** | ticket-events-message-age | 최고령 메시지 > 600s | `sqs/main.tf:117` |
| **MSK** | msk-no-active-controller | 활성 컨트롤러 < 1 | `msk/main.tf:202` |
| **MSK** | msk-offline-partitions | 오프라인 파티션 > 0 | `msk/main.tf:222` |
| **Lambda Worker** | ticket-worker-errors | 에러 > 5 (5분) | `lambda-worker/main.tf:153` |
| **Lambda Worker** | ticket-worker-duration | 지속시간 > timeout*80% | `lambda-worker/main.tf:173` |
| **Lambda Worker** | ticket-worker-throttles | 스로틀 > 0 | `lambda-worker/main.tf:193` |

### 9.2 로깅 구성

| 로그 소스 | 대상 | 보존 기간 |
|----------|------|----------|
| EKS Control Plane | CloudWatch `/aws/eks/{prefix}/cluster` | 7일 (기본값) |
| MSK Broker | CloudWatch `/aws/msk/{prefix}` | 모듈 변수로 설정 |
| Lambda Worker | CloudWatch `/aws/lambda/{function-name}` | 모듈 변수로 설정 |
| ElastiCache Redis | CloudWatch (slow-log + engine-log) | JSON 형식 |
| RDS PostgreSQL | CloudWatch (postgresql + upgrade 로그) | - |
| ALB 접근 로그 | S3 `{prefix}-logs-{env}` | 30일 IA, 90일 Glacier |

### 9.3 추적 (Tracing)

- **Lambda Worker**: AWS X-Ray Active 모드
- **EKS 서비스**: Zipkin 엔드포인트 (`zipkin-spring:9411/api/v2/spans`)
- **RDS**: Performance Insights 활성화 (7일 보존)

---

## 10. 강점 및 약점 분석

### 10.1 강점

**인프라 설계**
- **5계층 서브넷 분리**: Public/App/DB/Cache/Streaming으로 세밀한 네트워크 격리 달성. DB와 Cache 서브넷은 인터넷 경로가 없어 외부 접근이 원천 차단된다.
- **AZ별 NAT Gateway**: App/Streaming 서브넷에 AZ별 독립 NAT로 고가용성 확보.
- **VPC Endpoints 완비**: 9개 Interface + 2개 Gateway 엔드포인트로 프라이빗 서비스 접근을 보장하고, NAT Gateway 비용을 절감한다.
- **RDS Proxy 적용**: EKS Pod의 연결 폭증을 흡수하고, Lambda Worker의 VPC Cold Start를 개선한다.

**보안**
- **Zero-Trust NetworkPolicy**: default-deny-all 정책 후 서비스별 최소 권한 허용.
- **ALB CloudFront Prefix List 제한**: ALB에 CloudFront 이외의 직접 접근을 차단.
- **EKS Secrets KMS 암호화**: etcd에 저장되는 Kubernetes Secret을 KMS로 암호화.
- **컨테이너 보안**: runAsNonRoot, drop ALL capabilities, 비루트 사용자(uid 1001) 실행.
- **IRSA (IAM Roles for Service Accounts)**: VPC CNI와 EBS CSI에 최소 권한 IAM 역할 할당.
- **TLS 전구간**: CloudFront(TLSv1.2) -> ALB(TLS 1.3) -> MSK(TLS) -> Redis(transit encryption) -> RDS(TLS).

**CI/CD**
- **Reusable Workflow 패턴**: 8개 Spring 서비스가 단일 reusable 워크플로를 공유하여 유지보수 비용이 낮다.
- **GitOps 구현**: CI가 이미지 태그를 Git에 커밋하면 ArgoCD가 자동 배포하는 깨끗한 GitOps 흐름.
- **Trivy 보안 스캔**: 매 빌드마다 CRITICAL/HIGH 취약점을 차단한다.
- **E2E + Load Test 게이트**: Staging 배포 후 Playwright E2E와 k6 부하 테스트를 자동 실행한다.
- **카오스 테스트 내장**: service-failure, network-latency, redis-failure 시나리오가 주간 스케줄로 실행된다.

**운영**
- **Argo Rollouts Blue/Green**: 핵심 4개 서비스에 수동 승격 + 헬스 체크 기반 자동 분석.
- **수동 롤백 워크플로**: 서비스/태그/환경을 선택하여 즉시 롤백 가능.
- **Discord 알림**: 배포 성공/실패/롤백 모든 이벤트를 Discord로 알림.

### 10.2 약점 및 개선 권장사항

**[높음] Staging Backend 미설정**
- `terraform/environments/staging/main.tf`에 S3 backend 설정이 없다. 로컬 상태 파일로 팀 협업이 불가능하며, 상태 잠금이 되지 않아 동시 실행 시 상태 손상 위험이 있다.
- **권장**: Staging 전용 S3 backend와 DynamoDB lock 테이블을 추가해야 한다.

**[높음] Terraform 변수 파일 부재**
- `terraform/environments/prod/`와 `staging/`에 `terraform.tfvars` 또는 `variables.tf`가 확인되지 않는다. 변수 값이 어떻게 주입되는지 불명확하다.
- **권장**: 환경별 `terraform.tfvars` 파일을 만들거나, CI에서 `-var-file` 또는 `TF_VAR_*` 환경 변수를 사용하는 방식을 명시해야 한다.

**[높음] ECR 이미지 레지스트리 URI 플레이스홀더**
- `kustomization.yaml`의 `newName`이 `CHANGE_ME.dkr.ecr.ap-northeast-2.amazonaws.com`으로 남아 있다. CI/CD가 sed로 갱신하지만, 초기 배포 시 실패할 수 있다.
- **권장**: ECR 레지스트리 URI를 GitHub Secrets에서 주입하거나, ArgoCD의 Image Updater를 도입하여 Kustomize 파일 직접 수정을 제거하는 것이 좋다.

**[중간] catalog-service에 HPA/PDB 미적용**
- 다른 8개 서비스에는 PDB가 있지만 catalog-service에는 없다. HPA도 gateway, ticket, queue, payment 4개에만 적용되어 있다.
- **권장**: catalog-service에 PDB를 추가하고, 트래픽 패턴에 따라 auth, stats, community, catalog에도 HPA 적용을 검토해야 한다.

**[중간] NetworkPolicy에 외부 서비스 Egress 미정의**
- `allow-backend-egress` 정책이 네임스페이스 내부 Pod와 DNS만 허용한다. RDS, Redis, Kafka, SQS 등 VPC 내 외부 서비스(CIDR 기반)로의 Egress가 명시적으로 허용되지 않는다.
- **권장**: DB(5432), Redis(6379), Kafka(9092/9094), SQS VPC Endpoint(443) CIDR 기반 Egress 규칙을 추가해야 한다. 현재 NetworkPolicy가 적용되지 않는 CNI를 사용 중이라면 문제없지만, Calico 등 Policy 지원 CNI로 전환 시 서비스 중단이 발생할 수 있다.

**[중간] DB 서브넷 라우팅 제한**
- DB 서브넷과 Cache 서브넷이 동일 라우트 테이블을 공유하며, NAT Gateway 경로가 없다. PostgreSQL 확장 기능(예: pg_cron의 외부 HTTP 호출)이나 패치 다운로드가 불가능하다.
- **권장**: 필요시 DB 서브넷에 NAT 경로를 추가하거나, RDS 자동 패치 적용 설정을 확인해야 한다.

**[중간] ArgoCD Prod의 수동 Sync만 존재**
- Prod ArgoCD Application에 `syncPolicy.automated`가 없어 수동으로 Sync해야 한다. 이는 안전하지만, 배포 자동화 흐름에 수동 개입이 필요하다.
- **권장**: Argo Rollouts Blue/Green이 이미 안전 장치 역할을 하므로, `automated` + `selfHeal`을 활성화하고 Rollout의 `autoPromotionEnabled: false`로 최종 안전 장치를 유지하는 방안을 검토할 수 있다.

**[낮음] Lambda@Edge 시크릿 하드코딩**
- Lambda@Edge는 환경 변수를 지원하지 않아 `config.json`에 시크릿이 파일로 베이크인된다 (`cloudfront/main.tf` 라인 7-13). 이는 Lambda@Edge의 기술적 제한이지만, 시크릿 로테이션 시 재배포가 필요하다.
- **권장**: 시크릿 로테이션 주기를 Terraform으로 자동화하거나, CloudFront Function (JS 2.0)에서 KV Store를 사용하는 대안을 검토할 수 있다.

**[낮음] EKS Addon 버전 고정**
- VPC CNI(v1.15.1), kube-proxy(v1.28.2), CoreDNS(v1.10.1), EBS CSI(v1.25.0) 버전이 하드코딩되어 있다. EKS 클러스터 버전 업그레이드 시 호환성 문제가 발생할 수 있다.
- **권장**: Addon 버전을 `most_recent = true`로 변경하거나, Dependabot/Renovate로 정기 업데이트를 자동화해야 한다.

**[낮음] Kind 환경과 프로덕션 환경 간 서비스 이름 불일치**
- Kind 환경에서 Redis는 `dragonfly-spring`, Kafka는 `kafka-spring`이지만, 프로덕션 `services-env.yaml`에서도 동일한 이름을 사용한다. 실제 AWS 환경에서는 MSK/ElastiCache 엔드포인트가 다를 것이므로, ConfigMap으로 분리되어야 한다.
- **권장**: `config.env` 파일에서 환경별 엔드포인트를 정확히 설정하고 있는지 확인해야 한다. `services-env.yaml`의 하드코딩된 값들이 ConfigMap 값으로 재정의되는지 검증이 필요하다.

---

## 11. 배포 아키텍처 권장사항 요약

| 영역 | 현재 상태 | 권장 조치 | 우선순위 |
|------|----------|----------|---------|
| Terraform State (Staging) | 로컬 | S3 + DynamoDB backend 추가 | 높음 |
| Terraform 변수 관리 | 불명확 | tfvars 파일 또는 CI 변수 명시화 | 높음 |
| ECR URI 플레이스홀더 | CHANGE_ME | ArgoCD Image Updater 또는 Sealed 값 | 높음 |
| catalog-service PDB | 미적용 | PDB 추가 | 중간 |
| NetworkPolicy 외부 Egress | 미정의 | CIDR 기반 Egress 추가 | 중간 |
| ArgoCD Prod Auto-Sync | 수동 | automated + Rollout 안전 장치 검토 | 중간 |
| EKS Addon 버전 | 고정 | 자동 업데이트 전략 도입 | 낮음 |
| Lambda@Edge 시크릿 | 파일 베이크인 | 로테이션 자동화 | 낮음 |
