# AWS 수동 배포 + CI/CD + ArgoCD 완전 가이드

> **주의**: 이 가이드는 Terraform 없이 AWS 콘솔에서 직접 수동으로 인프라를 구축하는 절차입니다.
> AWS 콘솔 한국어(ko) 기준으로 작성되었습니다.
> 리전: **아시아 태평양 (서울) ap-northeast-2** (별도 표기가 없으면 서울 리전)

---

## 목차

1. [사전 준비](#1-사전-준비)
2. [VPC 생성](#2-vpc-생성)
3. [IAM 역할 생성](#3-iam-역할-생성)
4. [EKS 클러스터 생성](#4-eks-클러스터-생성)
5. [RDS PostgreSQL 생성](#5-rds-postgresql-생성)
6. [ElastiCache Redis 생성](#6-elasticache-redis-생성)
7. [MSK Kafka 생성](#7-msk-kafka-생성)
8. [ECR 레포지토리 생성](#8-ecr-레포지토리-생성)
9. [S3 버킷 생성](#9-s3-버킷-생성)
10. [Secrets Manager 설정](#10-secrets-manager-설정)
11. [SQS 대기열 생성](#11-sqs-대기열-생성)
12. [ALB 생성](#12-alb-생성)
13. [CloudFront 배포 생성](#13-cloudfront-배포-생성)
14. [Lambda 함수 생성](#14-lambda-함수-생성)
15. [DynamoDB 테이블 생성](#15-dynamodb-테이블-생성)
16. [API Gateway 생성](#16-api-gateway-생성)
17. [WAF 설정](#17-waf-설정)
18. [AMP/AMG 모니터링 설정](#18-ampamg-모니터링-설정)
19. [GitHub Actions CI/CD 설정](#19-github-actions-cicd-설정)
20. [ArgoCD 설정](#20-argocd-설정)
21. [최종 검증 및 배포](#21-최종-검증-및-배포)

---

## 1. 사전 준비

### 1.1 필요한 도구 설치

```bash
# AWS CLI v2
# https://docs.aws.amazon.com/cli/latest/userguide/getting-started-install.html

# kubectl
# https://kubernetes.io/docs/tasks/tools/install-kubectl-windows/

# Helm 3
# https://helm.sh/docs/intro/install/

# eksctl (EKS OIDC 설정에 필요)
# https://eksctl.io/installation/

# ArgoCD CLI (선택)
# https://argo-cd.readthedocs.io/en/stable/cli_installation/
```

### 1.2 AWS CLI 설정

```bash
aws configure
# AWS Access Key ID: [IAM에서 발급한 키]
# AWS Secret Access Key: [IAM에서 발급한 시크릿]
# Default region name: ap-northeast-2
# Default output format: json
```

### 1.3 계정 ID 확인

```bash
aws sts get-caller-identity --query Account --output text
# 12자리 숫자가 나옴 → 메모해두기 (이후 {ACCOUNT_ID}로 표기)
```

---

## 2. VPC 생성

### 2.1 VPC 생성

1. AWS 콘솔 → 상단 검색창에 `VPC` 입력 → **VPC** 클릭
2. 왼쪽 메뉴 **VPC** → 오른쪽 상단 **VPC 생성** 버튼 클릭
3. **VPC 등** 탭 선택 (VPC, 서브넷, 라우팅 테이블, IGW를 한번에 생성)

| 항목 | 입력값 |
|------|--------|
| 이름 태그 자동 생성 | `urr-prod` |
| IPv4 CIDR 블록 | `10.0.0.0/16` |
| IPv6 CIDR 블록 | 없음 |
| 테넌시 | 기본값 |
| 가용 영역(AZ) 수 | **2** |
| 첫 번째 AZ | `ap-northeast-2a` |
| 두 번째 AZ | `ap-northeast-2c` |
| 퍼블릭 서브넷 수 | **2** |
| 프라이빗 서브넷 수 | **2** |
| NAT 게이트웨이 | **AZ당 1개** (2개) |
| VPC 엔드포인트 | **S3 게이트웨이** |
| DNS 호스트 이름 | 활성화 |
| DNS 확인 | 활성화 |

4. **VPC 생성** 클릭 → 생성 완료까지 약 3-5분 대기

### 2.2 추가 서브넷 생성 (DB, Cache, Streaming용)

자동 생성된 서브넷은 Public 2개 + Private 2개뿐이므로, 용도별 추가 서브넷을 생성합니다.

1. VPC 대시보드 → 왼쪽 **서브넷** → **서브넷 생성**

**Database 서브넷 (2개):**

| 항목 | AZ-a 값 | AZ-c 값 |
|------|---------|---------|
| VPC | urr-prod-vpc |urr-prod-vpc |
| 서브넷 이름 | `urr-prod-db-a` | `urr-prod-db-c` |
| 가용 영역 | ap-northeast-2a | ap-northeast-2c |
| IPv4 CIDR | `10.0.20.0/24` | `10.0.21.0/24` |

**Cache 서브넷 (2개):**

| 항목 | AZ-a 값 | AZ-c 값 |
|------|---------|---------|
| 서브넷 이름 | `urr-prod-cache-a` | `urr-prod-cache-c` |
| 가용 영역 | ap-northeast-2a | ap-northeast-2c |
| IPv4 CIDR | `10.0.30.0/24` | `10.0.31.0/24` |

**Streaming 서브넷 (2개):**

| 항목 | AZ-a 값 | AZ-c 값 |
|------|---------|---------|
| 서브넷 이름 | `urr-prod-streaming-a` | `urr-prod-streaming-c` |
| 가용 영역 | ap-northeast-2a | ap-northeast-2c |
| IPv4 CIDR | `10.0.40.0/24` | `10.0.41.0/24` |

2. 각 서브넷 생성 후 **라우팅 테이블 연결**:
   - DB 서브넷 → 인터넷 라우팅 **없음** (기본 로컬만)
   - Cache 서브넷 → 기존 Private 라우팅 테이블 연결 (NAT Gateway 포함)
   - Streaming 서브넷 → 기존 Private 라우팅 테이블 연결 (NAT Gateway 포함)

   방법: 서브넷 선택 → **라우팅 테이블** 탭 → **라우팅 테이블 연결 편집** → Private 라우팅 테이블 선택 → **저장**

### 2.3 서브넷 태그 추가 (EKS/ALB용)

1. VPC → **서브넷** → 퍼블릭 서브넷 각각 선택 → **태그** 탭 → **태그 관리**

| 키 | 값 |
|---|---|
| `kubernetes.io/role/elb` | `1` |
| `kubernetes.io/cluster/urr-prod` | `shared` |

2. 프라이빗(App) 서브넷 각각에 태그 추가:

| 키 | 값 |
|---|---|
| `kubernetes.io/role/internal-elb` | `1` |
| `kubernetes.io/cluster/urr-prod` | `shared` |

### 2.4 VPC 엔드포인트 보안 그룹 생성

> **먼저** 엔드포인트용 보안 그룹을 생성합니다. 모든 Interface 엔드포인트가 공유합니다.

1. VPC → **보안 그룹** → **보안 그룹 생성**

| 항목 | 값 |
|------|---|
| 이름 | `urr-prod-vpc-endpoints-sg` |
| 설명 | Security group for VPC Interface Endpoints |
| VPC | urr-prod-vpc |

**인바운드 규칙:**

| 유형 | 포트 | 소스 | 설명 |
|------|------|------|------|
| HTTPS | 443 | `10.0.0.0/16` (VPC CIDR) | VPC 내부 → 엔드포인트 |

### 2.5 VPC 엔드포인트 추가 생성

VPC → 왼쪽 **엔드포인트** → **엔드포인트 생성** — 아래 **8개**를 각각 생성:

> 모든 Interface 엔드포인트 공통 설정:
> - VPC: `urr-prod-vpc`
> - 서브넷: **App 프라이빗 서브넷 2개**
> - 보안 그룹: `urr-prod-vpc-endpoints-sg`
> - 프라이빗 DNS 이름 활성화: **체크**

| # | 이름 | 서비스 | 중요도 |
|---|------|--------|--------|
| 1 | `urr-prod-ecr-api-endpoint` | `com.amazonaws.ap-northeast-2.ecr.api` | **CRITICAL** (이미지 pull) |
| 2 | `urr-prod-ecr-dkr-endpoint` | `com.amazonaws.ap-northeast-2.ecr.dkr` | **CRITICAL** (이미지 pull) |
| 3 | `urr-prod-secretsmanager-endpoint` | `com.amazonaws.ap-northeast-2.secretsmanager` | 필수 |
| 4 | `urr-prod-sqs-endpoint` | `com.amazonaws.ap-northeast-2.sqs` | 필수 |
| 5 | `urr-prod-logs-endpoint` | `com.amazonaws.ap-northeast-2.logs` | 필수 |
| 6 | `urr-prod-sts-endpoint` | `com.amazonaws.ap-northeast-2.sts` | 필수 (IRSA) |
| 7 | `urr-prod-ec2-endpoint` | `com.amazonaws.ap-northeast-2.ec2` | 권장 (VPC CNI) |
| 8 | `urr-prod-eks-endpoint` | `com.amazonaws.ap-northeast-2.eks` | 권장 |

> **CRITICAL**: ECR API + DKR 엔드포인트가 없으면 프라이빗 서브넷의 EKS 노드가 컨테이너 이미지를 pull할 수 없어 Pod가 시작되지 않습니다!

**DynamoDB Gateway 엔드포인트** (선택):

| 항목 | 값 |
|------|---|
| 이름 | `urr-prod-dynamodb-endpoint` |
| 서비스 | `com.amazonaws.ap-northeast-2.dynamodb` |
| 유형 | **Gateway** (Interface 아님) |
| 라우팅 테이블 | Private 라우팅 테이블 선택 |

> **메모**: VPC ID, 각 서브넷 ID를 메모장에 기록해두세요. 이후 단계에서 계속 사용합니다.

---

## 3. IAM 역할 생성

### 3.1 EKS 클러스터 역할

1. AWS 콘솔 → **IAM** → 왼쪽 **역할** → **역할 생성**

| 항목 | 값 |
|------|---|
| 신뢰할 수 있는 엔터티 유형 | **AWS 서비스** |
| 사용 사례 | **EKS** → **EKS - Cluster** |

2. **다음** → 권한 정책 확인 (자동 선택됨):
   - `AmazonEKSClusterPolicy`
   - `AmazonEKSVPCResourceController`
3. 역할 이름: `urr-prod-eks-cluster-role` → **역할 생성**

### 3.2 EKS 노드 역할

1. IAM → **역할** → **역할 생성**

| 항목 | 값 |
|------|---|
| 신뢰할 수 있는 엔터티 유형 | **AWS 서비스** |
| 사용 사례 | **EC2** |

2. 권한 정책 추가 (검색해서 4개 선택):
   - `AmazonEKSWorkerNodePolicy`
   - `AmazonEKS_CNI_Policy`
   - `AmazonEC2ContainerRegistryReadOnly`
   - `AmazonSSMManagedInstanceCore`
3. 역할 이름: `urr-prod-eks-node-role` → **역할 생성**

### 3.3 Lambda 실행 역할 (Worker용)

1. IAM → **역할** → **역할 생성**

| 항목 | 값 |
|------|---|
| 신뢰할 수 있는 엔터티 유형 | **AWS 서비스** |
| 사용 사례 | **Lambda** |

2. 권한 정책 추가:
   - `AWSLambdaBasicExecutionRole`
   - `AWSLambdaVPCAccessExecutionRole`
3. 역할 이름: `urr-prod-lambda-worker-role` → **역할 생성**
4. 생성된 역할 클릭 → **인라인 정책 추가** → **JSON** 탭:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "sqs:ReceiveMessage",
        "sqs:DeleteMessage",
        "sqs:GetQueueAttributes",
        "sqs:ChangeMessageVisibility"
      ],
      "Resource": "arn:aws:sqs:ap-northeast-2:*:urr-prod-*"
    },
    {
      "Effect": "Allow",
      "Action": [
        "secretsmanager:GetSecretValue"
      ],
      "Resource": "arn:aws:secretsmanager:ap-northeast-2:*:urr-prod/*"
    }
  ]
}
```
5. 정책 이름: `urr-prod-lambda-worker-custom` → **정책 생성**

### 3.4 Lambda@Edge 역할

1. IAM → **역할** → **역할 생성**
2. 신뢰할 수 있는 엔터티: **AWS 서비스** → **Lambda**
3. 정책: `AWSLambdaBasicExecutionRole`
4. 역할 이름: `urr-prod-lambda-edge-role` → **역할 생성**
5. 생성 후 → **신뢰 관계** 탭 → **신뢰 정책 편집**:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Principal": {
        "Service": [
          "lambda.amazonaws.com",
          "edgelambda.amazonaws.com"
        ]
      },
      "Action": "sts:AssumeRole"
    }
  ]
}
```
6. **정책 업데이트** 클릭

### 3.5 RDS 향상된 모니터링 역할

1. IAM → **역할** → **역할 생성**
2. 신뢰 엔터티: **AWS 서비스** → **RDS** → **RDS - Enhanced Monitoring**
3. 정책: `AmazonRDSEnhancedMonitoringRole` (자동 선택)
4. 역할 이름: `urr-prod-rds-monitoring-role` → **역할 생성**

### 3.6 GitHub Actions OIDC 역할 (CI/CD용)

1. IAM → 왼쪽 **자격 증명 공급자** → **공급자 추가**

| 항목 | 값 |
|------|---|
| 공급자 유형 | **OpenID Connect** |
| 공급자 URL | `https://token.actions.githubusercontent.com` |
| 대상 | `sts.amazonaws.com` |

2. **공급자 추가** 클릭

3. IAM → **역할** → **역할 생성**

| 항목 | 값 |
|------|---|
| 신뢰할 수 있는 엔터티 유형 | **웹 자격 증명** |
| 자격 증명 공급자 | `token.actions.githubusercontent.com` |
| 대상 | `sts.amazonaws.com` |

4. **신뢰 정책** 수정 (조건 추가):

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Principal": {
        "Federated": "arn:aws:iam::{ACCOUNT_ID}:oidc-provider/token.actions.githubusercontent.com"
      },
      "Action": "sts:AssumeRoleWithWebIdentity",
      "Condition": {
        "StringEquals": {
          "token.actions.githubusercontent.com:aud": "sts.amazonaws.com"
        },
        "StringLike": {
          "token.actions.githubusercontent.com:sub": "repo:cchriscode/URR:*"
        }
      }
    }
  ]
}
```

5. 권한 정책 추가:
   - `AmazonEC2ContainerRegistryPowerUser` (ECR push/pull)
   - `AmazonEKSClusterPolicy` (kubectl 접근)

6. **인라인 정책 추가** (EKS 접근 + K8s manifest 업데이트):

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "eks:DescribeCluster",
        "eks:ListClusters"
      ],
      "Resource": "*"
    }
  ]
}
```

7. 역할 이름: `urr-prod-github-actions-role` → **역할 생성**
8. 생성된 역할의 **ARN 복사** → 메모 (GitHub Secrets에 사용)

### 3.7 RDS Proxy 역할

1. IAM → **역할** → **역할 생성**
2. 신뢰 엔터티: **AWS 서비스** → **RDS**
3. 역할 이름: `urr-prod-rds-proxy-role` → **역할 생성**
4. 생성 후 → **신뢰 관계** 탭 → **신뢰 정책 편집**:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Principal": {
        "Service": "rds.amazonaws.com"
      },
      "Action": "sts:AssumeRole"
    }
  ]
}
```

5. **인라인 정책 추가** → JSON:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": "secretsmanager:GetSecretValue",
      "Resource": "arn:aws:secretsmanager:ap-northeast-2:*:urr-prod/rds-credentials"
    }
  ]
}
```
6. 정책 이름: `urr-prod-rds-proxy-secrets` → **정책 생성**

### 3.8 VWR Lambda 역할

1. IAM → **역할** → **역할 생성**
2. 신뢰 엔터티: **AWS 서비스** → **Lambda**
3. 정책: `AWSLambdaBasicExecutionRole`
4. 역할 이름: `urr-prod-vwr-lambda-role` → **역할 생성**
5. **인라인 정책 추가** → JSON:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "dynamodb:GetItem",
        "dynamodb:PutItem",
        "dynamodb:UpdateItem",
        "dynamodb:Query",
        "dynamodb:Scan"
      ],
      "Resource": [
        "arn:aws:dynamodb:ap-northeast-2:*:table/urr-prod-vwr-*",
        "arn:aws:dynamodb:ap-northeast-2:*:table/urr-prod-vwr-*/index/*"
      ]
    }
  ]
}
```
6. 정책 이름: `urr-prod-vwr-dynamodb` → **정책 생성**

### 3.9 AMG (Grafana) Workspace 역할

1. IAM → **역할** → **역할 생성**
2. 신뢰 엔터티: **사용자 지정 신뢰 정책**:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Principal": {
        "Service": "grafana.amazonaws.com"
      },
      "Action": "sts:AssumeRole"
    }
  ]
}
```

3. **다음** → 권한 정책 건너뛰기 → 역할 이름: `urr-prod-amg-role` → **역할 생성**
4. **인라인 정책 추가** → JSON:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "aps:ListWorkspaces",
        "aps:DescribeWorkspace",
        "aps:QueryMetrics",
        "aps:GetLabels",
        "aps:GetSeries",
        "aps:GetMetricMetadata"
      ],
      "Resource": "*"
    }
  ]
}
```
5. 정책 이름: `urr-prod-amg-amp-query` → **정책 생성**

---

## 4. EKS 클러스터 생성

### 4.1 클러스터 생성

1. AWS 콘솔 → **EKS** → **클러스터** → **클러스터 생성**

**1단계: 클러스터 구성**

| 항목 | 값 |
|------|---|
| 이름 | `urr-prod` |
| Kubernetes 버전 | `1.31` |
| 클러스터 서비스 역할 | `urr-prod-eks-cluster-role` |

**2단계: 네트워킹**

| 항목 | 값 |
|------|---|
| VPC | `urr-prod-vpc` |
| 서브넷 | App 프라이빗 서브넷 2개 선택 |
| 보안 그룹 | (기본값, EKS가 자동 생성) |
| 클러스터 엔드포인트 액세스 | **퍼블릭 및 프라이빗** |

**3단계: 옵저버빌리티**

| 항목 | 값 |
|------|---|
| 컨트롤 플레인 로깅 | API, 감사, 인증자, 컨트롤러 매니저, 스케줄러 **전부 활성화** |

> **로그 그룹 보존기간 설정**: 클러스터 생성 후 → CloudWatch → **로그 그룹** → `/aws/eks/urr-prod/cluster` → **편집** → **보존 기간**: `30`일 (기본 무제한이므로 비용 관리를 위해 설정)

**4단계: 추가 기능 (Add-on)**

기본 선택된 항목 유지:
- Amazon VPC CNI
- CoreDNS
- kube-proxy
- Amazon EBS CSI Driver (추가로 선택)

2. **생성** 클릭 → **약 10-15분 소요**

### 4.2 노드 그룹 생성

클러스터 생성 완료 후:

1. EKS → `urr-prod` 클릭 → **컴퓨팅** 탭 → **노드 그룹 추가**

**노드 그룹 구성:**

| 항목 | 값 |
|------|---|
| 이름 | `urr-prod-ng-initial` |
| 노드 IAM 역할 | `urr-prod-eks-node-role` |

**컴퓨팅 구성:**

| 항목 | 값 |
|------|---|
| AMI 유형 | Amazon Linux 2 (**AL2_ARM_64**) |
| 용량 유형 | **온디맨드** |
| 인스턴스 유형 | `t4g.medium` (Graviton ARM) |
| 디스크 크기 | `20` GiB |

> **중요: ARM64 아키텍처 사용 이유**
> CI/CD 파이프라인이 `linux/arm64` Docker 이미지를 빌드합니다. 따라서 EKS 노드도 반드시 ARM 기반(Graviton)이어야 합니다. x86_64 노드에서는 arm64 이미지가 `exec format error`로 실행 실패합니다. Graviton 인스턴스(`t4g`, `m6g`)는 x86 대비 ~20% 비용 절감 효과도 있습니다.

**조정 구성:**

| 항목 | 값 |
|------|---|
| 원하는 크기 | `3` |
| 최소 크기 | `2` |
| 최대 크기 | `5` |
| 업데이트 구성 | 최대 사용 불가 수: `1` |

**네트워킹:**

| 항목 | 값 |
|------|---|
| 서브넷 | App 프라이빗 서브넷 2개 (urr-prod-private-a, urr-prod-private-c) |
| 원격 액세스 허용 | 아니요 |

2. **생성** 클릭 → **약 5-10분 소요**

### 4.3 kubeconfig 설정

```bash
aws eks update-kubeconfig --region ap-northeast-2 --name urr-prod
kubectl get nodes  # 3개 노드 Ready 확인
```

### 4.4 EBS CSI Driver IRSA 설정

```bash
# OIDC 공급자 연결 확인
aws eks describe-cluster --name urr-prod --query "cluster.identity.oidc.issuer" --output text
# 예: https://oidc.eks.ap-northeast-2.amazonaws.com/id/EXAMPLED539D4633E53DE1B71EXAMPLE

# OIDC 공급자가 IAM에 등록되어 있는지 확인
aws iam list-open-id-connect-providers | grep $(aws eks describe-cluster --name urr-prod --query "cluster.identity.oidc.issuer" --output text | cut -d '/' -f 5)

# 없으면 등록
eksctl utils associate-iam-oidc-provider --cluster urr-prod --approve
```

### 4.5 네임스페이스 생성

```bash
kubectl create namespace urr-spring
kubectl create namespace urr-staging
kubectl create namespace argo-rollouts
kubectl create namespace argocd
kubectl create namespace monitoring
```

### 4.6 EKS Access Entries 설정 (클러스터 접근 권한)

> **중요**: IAM 역할/사용자가 EKS 클러스터 API를 호출하려면 Access Entry 등록이 필요합니다.

**방법 A: AWS 콘솔 (EKS API Access 탭)**

1. EKS → `urr-prod` 클릭 → **액세스** 탭 → **IAM 액세스 항목** → **액세스 항목 생성**

**본인 IAM 사용자/역할:**

| 항목 | 값 |
|------|---|
| IAM 보안 주체 ARN | (본인 IAM 사용자 또는 역할 ARN) |
| 유형 | **표준** |
| 정책 이름 추가 | `AmazonEKSClusterAdminPolicy` |
| 액세스 범위 | **클러스터** |

**GitHub Actions 역할:**

| 항목 | 값 |
|------|---|
| IAM 보안 주체 ARN | `arn:aws:iam::{ACCOUNT_ID}:role/urr-prod-github-actions-role` |
| 유형 | **표준** |
| 정책 이름 추가 | `AmazonEKSClusterAdminPolicy` |
| 액세스 범위 | **클러스터** |

**방법 B: aws-auth ConfigMap (CLI)**

```bash
# 현재 aws-auth 확인
kubectl get configmap aws-auth -n kube-system -o yaml

# aws-auth에 역할 매핑 추가
kubectl edit configmap aws-auth -n kube-system
```

`mapRoles` 섹션에 추가:

```yaml
mapRoles: |
  - rolearn: arn:aws:iam::{ACCOUNT_ID}:role/urr-prod-eks-node-role
    username: system:node:{{EC2PrivateDNSName}}
    groups:
      - system:bootstrappers
      - system:nodes
  - rolearn: arn:aws:iam::{ACCOUNT_ID}:role/urr-prod-github-actions-role
    username: github-actions
    groups:
      - system:masters
```

### 4.7 추가 IRSA 역할 생성

> IRSA (IAM Roles for Service Accounts)는 K8s 서비스 계정에 AWS IAM 역할을 연결합니다.
> EKS OIDC Provider가 필요하므로 반드시 4.4 단계 이후에 수행합니다.

**OIDC Provider ID 확인:**

```bash
OIDC_ID=$(aws eks describe-cluster --name urr-prod --query "cluster.identity.oidc.issuer" --output text | cut -d '/' -f 5)
echo $OIDC_ID
```

#### 4.7.1 VPC CNI IRSA

1. IAM → **역할** → **역할 생성** → **웹 자격 증명**

| 항목 | 값 |
|------|---|
| 자격 증명 공급자 | `oidc.eks.ap-northeast-2.amazonaws.com/id/{OIDC_ID}` |
| Audience | `sts.amazonaws.com` |

2. 신뢰 정책에 **조건 추가**:

```json
{
  "Condition": {
    "StringEquals": {
      "oidc.eks.ap-northeast-2.amazonaws.com/id/{OIDC_ID}:sub": "system:serviceaccount:kube-system:aws-node",
      "oidc.eks.ap-northeast-2.amazonaws.com/id/{OIDC_ID}:aud": "sts.amazonaws.com"
    }
  }
}
```

3. 정책: `AmazonEKS_CNI_Policy`
4. 역할 이름: `urr-prod-vpc-cni-irsa` → **역할 생성**
5. EKS → 추가 기능 → `vpc-cni` → **편집** → 서비스 계정 역할 ARN에 이 역할 지정

#### 4.7.2 EBS CSI Driver IRSA

동일 과정, 다른 값:

| 항목 | 값 |
|------|---|
| 서비스 계정 | `system:serviceaccount:kube-system:ebs-csi-controller-sa` |
| 정책 | `AmazonEBSCSIDriverPolicy` |
| 역할 이름 | `urr-prod-ebs-csi-irsa` |

EKS → 추가 기능 → `aws-ebs-csi-driver` → **편집** → 서비스 계정 역할 ARN 지정

#### 4.7.3 Karpenter Controller IRSA

| 항목 | 값 |
|------|---|
| 서비스 계정 | `system:serviceaccount:kube-system:karpenter` |
| 역할 이름 | `urr-prod-karpenter-controller` |

인라인 정책 (JSON):

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "KarpenterEC2",
      "Effect": "Allow",
      "Action": [
        "ec2:CreateFleet",
        "ec2:CreateLaunchTemplate",
        "ec2:CreateTags",
        "ec2:DeleteLaunchTemplate",
        "ec2:DescribeAvailabilityZones",
        "ec2:DescribeImages",
        "ec2:DescribeInstances",
        "ec2:DescribeInstanceTypeOfferings",
        "ec2:DescribeInstanceTypes",
        "ec2:DescribeLaunchTemplates",
        "ec2:DescribeSecurityGroups",
        "ec2:DescribeSubnets",
        "ec2:RunInstances",
        "pricing:GetProducts",
        "ssm:GetParameter"
      ],
      "Resource": "*"
    },
    {
      "Sid": "KarpenterPassRole",
      "Effect": "Allow",
      "Action": "iam:PassRole",
      "Resource": "arn:aws:iam::{ACCOUNT_ID}:role/urr-prod-eks-node-role"
    },
    {
      "Sid": "ConditionalEC2Termination",
      "Effect": "Allow",
      "Action": "ec2:TerminateInstances",
      "Resource": "*",
      "Condition": {
        "StringLike": {
          "ec2:ResourceTag/karpenter.sh/nodepool": "*"
        }
      }
    },
    {
      "Sid": "EKSClusterAccess",
      "Effect": "Allow",
      "Action": "eks:DescribeCluster",
      "Resource": "arn:aws:eks:ap-northeast-2:{ACCOUNT_ID}:cluster/urr-prod"
    }
  ]
}
```

#### 4.7.4 Prometheus Remote Write IRSA

| 항목 | 값 |
|------|---|
| 서비스 계정 | `system:serviceaccount:monitoring:kube-prometheus-stack-prometheus` |
| 역할 이름 | `urr-prod-prometheus-amp-irsa` |

인라인 정책:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "aps:RemoteWrite",
        "aps:GetSeries",
        "aps:GetLabels",
        "aps:GetMetricMetadata"
      ],
      "Resource": "*"
    }
  ]
}
```

### 4.8 AWS Load Balancer Controller 설치

> **CRITICAL**: ALB 대상 그룹의 target_type이 `ip` (K8s Pod IP 직접 등록)이므로 AWS Load Balancer Controller가 반드시 필요합니다. 이것이 없으면 ALB가 K8s Pod에 트래픽을 전달할 수 없습니다.

#### 4.8.1 LBC용 IRSA 역할 생성

1. IAM → **역할** → **역할 생성** → **웹 자격 증명**

| 항목 | 값 |
|------|---|
| 서비스 계정 | `system:serviceaccount:kube-system:aws-load-balancer-controller` |
| 역할 이름 | `urr-prod-aws-lbc-irsa` |

2. 인라인 정책: [AWS 공식 IAM 정책](https://raw.githubusercontent.com/kubernetes-sigs/aws-load-balancer-controller/main/docs/install/iam_policy.json) 다운로드

```bash
# 정책 JSON 다운로드
curl -o iam_policy.json https://raw.githubusercontent.com/kubernetes-sigs/aws-load-balancer-controller/main/docs/install/iam_policy.json

# IAM 정책 생성
aws iam create-policy \
  --policy-name AWSLoadBalancerControllerIAMPolicy \
  --policy-document file://iam_policy.json

# 역할에 정책 연결
aws iam attach-role-policy \
  --role-name urr-prod-aws-lbc-irsa \
  --policy-arn arn:aws:iam::{ACCOUNT_ID}:policy/AWSLoadBalancerControllerIAMPolicy
```

#### 4.8.2 Helm으로 LBC 설치

```bash
# Helm 레포 추가
helm repo add eks https://aws.github.io/eks-charts
helm repo update

# AWS Load Balancer Controller 설치
helm install aws-load-balancer-controller eks/aws-load-balancer-controller \
  --namespace kube-system \
  --set clusterName=urr-prod \
  --set serviceAccount.create=true \
  --set serviceAccount.name=aws-load-balancer-controller \
  --set serviceAccount.annotations."eks\.amazonaws\.com/role-arn"=arn:aws:iam::{ACCOUNT_ID}:role/urr-prod-aws-lbc-irsa \
  --set region=ap-northeast-2 \
  --set vpcId={VPC_ID}

# 설치 확인
kubectl get deployment -n kube-system aws-load-balancer-controller
```

#### 4.8.3 TargetGroupBinding 생성

ALB 대상 그룹에 K8s Pod IP를 자동 등록하기 위해 `TargetGroupBinding` CRD를 생성합니다.

```bash
cat <<'EOF' | kubectl apply -f -
apiVersion: elbv2.k8s.aws/v1beta1
kind: TargetGroupBinding
metadata:
  name: gateway-tgb
  namespace: urr-spring
spec:
  serviceRef:
    name: gateway-service
    port: 3001
  targetGroupARN: arn:aws:elasticloadbalancing:ap-northeast-2:{ACCOUNT_ID}:targetgroup/urr-prod-gateway-tg/{TG_ID}
  targetType: ip
---
apiVersion: elbv2.k8s.aws/v1beta1
kind: TargetGroupBinding
metadata:
  name: frontend-tgb
  namespace: urr-spring
spec:
  serviceRef:
    name: frontend-service
    port: 3000
  targetGroupARN: arn:aws:elasticloadbalancing:ap-northeast-2:{ACCOUNT_ID}:targetgroup/urr-prod-frontend-tg/{TG_ID}
  targetType: ip
EOF
```

> `{TG_ID}`: EC2 → 대상 그룹 → 각 대상 그룹 ARN에서 마지막 슬래시 뒤 ID

### 4.9 Karpenter 설치 (노드 자동 스케일링)

> Karpenter는 K8s Pod 수요에 따라 EC2 노드를 자동으로 프로비저닝/해제합니다.

#### 4.9.1 서브넷/보안 그룹 태그 추가

Karpenter가 서브넷과 보안 그룹을 자동 발견하려면 태그가 필요합니다.

1. VPC → **서브넷** → App 프라이빗 서브넷 각각 선택 → **태그 관리**:

| 키 | 값 |
|---|---|
| `karpenter.sh/discovery` | `urr-prod` |

2. EKS 노드 보안 그룹에도 동일 태그 추가

#### 4.9.2 Helm으로 Karpenter 설치

```bash
# Karpenter 설치
helm install karpenter oci://public.ecr.aws/karpenter/karpenter \
  --namespace kube-system \
  --version 1.1.1 \
  --set "serviceAccount.annotations.eks\.amazonaws\.com/role-arn=arn:aws:iam::{ACCOUNT_ID}:role/urr-prod-karpenter-controller" \
  --set settings.clusterName=urr-prod \
  --set settings.clusterEndpoint=$(aws eks describe-cluster --name urr-prod --query "cluster.endpoint" --output text) \
  --set replicas=1

# 설치 확인
kubectl get pods -n kube-system -l app.kubernetes.io/name=karpenter
```

#### 4.9.3 NodePool + EC2NodeClass 생성

```bash
cat <<'EOF' | kubectl apply -f -
apiVersion: karpenter.sh/v1
kind: NodePool
metadata:
  name: default
spec:
  template:
    metadata:
      labels:
        role: karpenter
    spec:
      requirements:
        - key: kubernetes.io/arch
          operator: In
          values: ["arm64"]
        - key: karpenter.sh/capacity-type
          operator: In
          values: ["on-demand", "spot"]
        - key: node.kubernetes.io/instance-type
          operator: In
          values: ["t4g.medium", "t4g.large", "t4g.xlarge", "m6g.large", "m6g.xlarge"]
      nodeClassRef:
        group: karpenter.k8s.aws
        kind: EC2NodeClass
        name: default
  limits:
    cpu: "32"
    memory: "64Gi"
  disruption:
    consolidationPolicy: WhenEmptyOrUnderutilized
    consolidateAfter: 60s
---
apiVersion: karpenter.k8s.aws/v1
kind: EC2NodeClass
metadata:
  name: default
spec:
  amiSelectorTerms:
    - alias: "al2023@latest"
  subnetSelectorTerms:
    - tags:
        karpenter.sh/discovery: urr-prod
  securityGroupSelectorTerms:
    - tags:
        karpenter.sh/discovery: urr-prod
  role: urr-prod-eks-node-role
  blockDeviceMappings:
    - deviceName: /dev/xvda
      ebs:
        volumeSize: 30Gi
        volumeType: gp3
        deleteOnTermination: true
EOF

# 확인
kubectl get nodepools
kubectl get ec2nodeclasses
```

---

## 5. RDS PostgreSQL 생성

### 5.1 DB 서브넷 그룹 생성

1. AWS 콘솔 → **RDS** → 왼쪽 **서브넷 그룹** → **DB 서브넷 그룹 생성**

| 항목 | 값 |
|------|---|
| 이름 | `urr-prod-db-subnet-group` |
| 설명 | URR prod database subnet group |
| VPC | urr-prod-vpc |
| 가용 영역 | ap-northeast-2a, ap-northeast-2c |
| 서브넷 | `urr-prod-db-a` (10.0.20.0/24), `urr-prod-db-c` (10.0.21.0/24) |

2. **생성** 클릭

### 5.2 파라미터 그룹 생성

1. RDS → **파라미터 그룹** → **파라미터 그룹 생성**

| 항목 | 값 |
|------|---|
| 패밀리 | `postgres16` |
| 유형 | DB Parameter Group |
| 그룹 이름 | `urr-prod-postgres16` |
| 설명 | URR prod PostgreSQL 16 parameters |

2. 생성된 그룹 클릭 → **파라미터 편집** → 검색해서 수정:

| 파라미터 | 값 | 적용 |
|---------|---|------|
| `shared_preload_libraries` | `pg_stat_statements` | 재부팅 필요 |
| `log_statement` | `ddl` | 재부팅 필요 |
| `log_min_duration_statement` | `1000` | 즉시 |

3. **변경 사항 저장**

### 5.3 보안 그룹 생성 (RDS용)

1. VPC → **보안 그룹** → **보안 그룹 생성**

| 항목 | 값 |
|------|---|
| 이름 | `urr-prod-rds-sg` |
| 설명 | RDS PostgreSQL security group |
| VPC | urr-prod-vpc |

**인바운드 규칙:**

| 유형 | 포트 | 소스 | 설명 |
|------|------|------|------|
| PostgreSQL | 5432 | EKS 노드 보안 그룹 | EKS → RDS |
| PostgreSQL | 5432 | Lambda Worker 보안 그룹 | Lambda → RDS |

> **참고**: EKS 노드 보안 그룹 ID는 EKS 콘솔 → 클러스터 → 네트워킹 탭에서 확인 가능

2. **보안 그룹 생성** 클릭

### 5.4 RDS 인스턴스 생성

1. RDS → **데이터베이스** → **데이터베이스 생성**

| 항목 | 값 |
|------|---|
| 생성 방법 | **표준 생성** |
| 엔진 유형 | **PostgreSQL** |
| 엔진 버전 | **16.4** |
| 템플릿 | **프로덕션** |
| 가용성 및 내구성 | **다중 AZ DB 인스턴스** |
| DB 인스턴스 식별자 | `urr-prod-postgres` |
| 마스터 사용자 이름 | `urr_admin` |
| 자격 증명 관리 | **자체 관리** |
| 마스터 암호 | (32자 이상 복잡한 암호 입력 → **메모 필수!**) |

**인스턴스 구성:**

| 항목 | 값 |
|------|---|
| DB 인스턴스 클래스 | **버스터블 클래스** → `db.t3.medium` |

**스토리지:**

| 항목 | 값 |
|------|---|
| 스토리지 유형 | gp3 |
| 할당된 스토리지 | `50` GiB |
| 스토리지 자동 조정 | 활성화, 최대: `100` GiB |

**연결:**

| 항목 | 값 |
|------|---|
| VPC | urr-prod-vpc |
| DB 서브넷 그룹 | `urr-prod-db-subnet-group` |
| 퍼블릭 액세스 | **아니요** |
| VPC 보안 그룹 | **기존 항목 선택** → `urr-prod-rds-sg` |
| 가용 영역 | 기본 설정 없음 |
| 데이터베이스 포트 | `5432` |

**데이터베이스 인증:**

| 항목 | 값 |
|------|---|
| 인증 | 암호 인증 |

**추가 구성:**

| 항목 | 값 |
|------|---|
| 초기 데이터베이스 이름 | `ticket_db` |
| DB 파라미터 그룹 | `urr-prod-postgres16` |
| 백업 보존 기간 | `7`일 |
| 백업 기간 | 03:00-04:00 UTC |
| 유지 관리 기간 | 월요일 04:00-05:00 UTC |
| 삭제 보호 | **활성화** |
| Performance Insights | **활성화** (7일 보존) |
| 향상된 모니터링 | **활성화** (60초, 역할: `urr-prod-rds-monitoring-role`) |
| 로그 내보내기 | PostgreSQL 로그, 업그레이드 로그 체크 |

2. **데이터베이스 생성** 클릭 → **약 15-20분 소요**

3. 생성 완료 후 **엔드포인트** 복사 → 메모 (예: `urr-prod-postgres.xxxx.ap-northeast-2.rds.amazonaws.com`)

### 5.5 RDS Read Replica 생성

1. RDS → **데이터베이스** → `urr-prod-postgres` 선택 → **작업** → **읽기 전용 복제본 생성**

| 항목 | 값 |
|------|---|
| DB 인스턴스 식별자 | `urr-prod-postgres-replica` |
| DB 인스턴스 클래스 | `db.t3.medium` (Primary와 동일) |
| 가용 영역 | (Primary와 다른 AZ 권장) |
| 퍼블릭 액세스 | **아니요** |
| VPC 보안 그룹 | `urr-prod-rds-sg` (Primary와 동일) |
| 파라미터 그룹 | `urr-prod-postgres16` |
| Performance Insights | **활성화** (7일) |
| 향상된 모니터링 | **활성화** (60초, `urr-prod-rds-monitoring-role`) |
| 스토리지 암호화 | **활성화** |

2. **읽기 전용 복제본 생성** → 약 15-20분 소요
3. Replica 엔드포인트 메모 (읽기 전용 쿼리 분산에 사용)

### 5.6 RDS Proxy 생성

> RDS Proxy는 데이터베이스 연결을 풀링하여 Lambda/EKS의 연결 폭주를 방지합니다.

#### 5.6.1 RDS Proxy 보안 그룹 생성

1. VPC → **보안 그룹** → **보안 그룹 생성**

| 항목 | 값 |
|------|---|
| 이름 | `urr-prod-rds-proxy-sg` |
| 설명 | RDS Proxy security group |
| VPC | urr-prod-vpc |

**인바운드 규칙:**

| 유형 | 포트 | 소스 | 설명 |
|------|------|------|------|
| PostgreSQL | 5432 | EKS 노드 보안 그룹 | EKS → Proxy |
| PostgreSQL | 5432 | Lambda Worker 보안 그룹 | Lambda → Proxy |

2. `urr-prod-rds-sg`에 **인바운드 규칙 추가**:

| 유형 | 포트 | 소스 |
|------|------|------|
| PostgreSQL | 5432 | `urr-prod-rds-proxy-sg` |

#### 5.6.2 RDS Proxy 생성

1. RDS → 왼쪽 **프록시** → **프록시 생성**

| 항목 | 값 |
|------|---|
| 엔진 패밀리 | **PostgreSQL** |
| 프록시 식별자 | `urr-prod-proxy` |
| 유휴 클라이언트 연결 제한 시간 | `1800`초 |
| TLS 필요 | **체크** |
| IAM 역할 | `urr-prod-rds-proxy-role` |

**Secrets Manager 보안 암호:**

| 항목 | 값 |
|------|---|
| 보안 암호 | `urr-prod/rds-credentials` |

**연결:**

| 항목 | 값 |
|------|---|
| 서브넷 | App 프라이빗 서브넷 2개 (**DB 서브넷 아님!**) |
| VPC 보안 그룹 | `urr-prod-rds-proxy-sg` |

**대상 그룹 구성:**

| 항목 | 값 |
|------|---|
| 데이터베이스 | `urr-prod-postgres` |
| 연결 차용 제한 시간 | `120`초 |
| 최대 연결 비율 | `100`% |
| 유휴 연결 비율 | `50`% |

2. **프록시 생성** → 약 10분 소요
3. 생성 완료 후 **프록시 엔드포인트** 복사 → 메모

> **중요**: 서비스에서 DB 접속 시 RDS 엔드포인트 대신 **Proxy 엔드포인트**를 사용하세요!
> config.env의 `*_DB_URL`에 Proxy 엔드포인트를 입력합니다.

---

## 6. ElastiCache Redis 생성

### 6.1 서브넷 그룹 생성

1. AWS 콘솔 → **ElastiCache** → 왼쪽 **서브넷 그룹** → **서브넷 그룹 생성**

| 항목 | 값 |
|------|---|
| 이름 | `urr-prod-redis-subnet-group` |
| 설명 | URR prod Redis subnet group |
| VPC | urr-prod-vpc |
| 서브넷 | `urr-prod-cache-a`, `urr-prod-cache-c` 선택 |

### 6.2 보안 그룹 생성

1. VPC → **보안 그룹** → **보안 그룹 생성**

| 항목 | 값 |
|------|---|
| 이름 | `urr-prod-redis-sg` |
| VPC | urr-prod-vpc |

**인바운드 규칙:**

| 유형 | 포트 | 소스 |
|------|------|------|
| 사용자 지정 TCP | 6379 | EKS 노드 보안 그룹 |
| 사용자 지정 TCP | 6379 | Lambda Worker 보안 그룹 |

### 6.3 파라미터 그룹 생성

1. ElastiCache → **파라미터 그룹** → **파라미터 그룹 생성**

| 항목 | 값 |
|------|---|
| 패밀리 | `redis7` |
| 이름 | `urr-prod-redis7` |
| 설명 | URR prod Redis 7 parameters |

2. 생성 후 클릭 → 파라미터 편집:

| 파라미터 | 값 |
|---------|---|
| `timeout` | `300` |
| `maxmemory-policy` | `allkeys-lru` |

### 6.4 Redis 복제 그룹 생성

1. ElastiCache → **Redis OSS 캐시** → **Redis OSS 캐시 생성**

| 항목 | 값 |
|------|---|
| 배포 옵션 | **자체 캐시 설계** |
| 생성 방법 | **클러스터 캐시** → **아니요** (복제 그룹) |
| 이름 | `urr-prod-redis` |
| 설명 | URR prod Redis replication group |
| 엔진 버전 | `7.1` |
| 포트 | `6379` |
| 노드 유형 | `cache.t4g.medium` |
| 복제본 수 | `1` (Primary 1 + Replica 1 = 총 2개) |
| 다중 AZ | **활성화** |
| 자동 장애 조치 | **활성화** |

**연결:**

| 항목 | 값 |
|------|---|
| 서브넷 그룹 | `urr-prod-redis-subnet-group` |
| 보안 그룹 | `urr-prod-redis-sg` |

**보안:**

| 항목 | 값 |
|------|---|
| 전송 중 암호화 | **활성화** |
| 저장 시 암호화 | **활성화** |
| AUTH 토큰 | (32자 비밀번호 입력 → **메모 필수!** 특수문자 제외) |

**백업:**

| 항목 | 값 |
|------|---|
| 자동 백업 활성화 | 체크 |
| 보존 기간 | `5`일 |
| 백업 기간 | 03:00-05:00 UTC |

**유지 관리:**

| 항목 | 값 |
|------|---|
| 유지 관리 기간 | 일요일 05:00-07:00 UTC |
| 자동 마이너 버전 업그레이드 | 활성화 |

| 파라미터 그룹 | `urr-prod-redis7` |

**로그:**

| 항목 | 값 |
|------|---|
| Slow 로그 전달 | **CloudWatch Logs** → `/aws/elasticache/urr-prod-redis/slow-log` |
| 엔진 로그 전달 | **CloudWatch Logs** → `/aws/elasticache/urr-prod-redis/engine-log` |

2. **생성** → 약 10분 소요
3. 생성 완료 후 **기본 엔드포인트** 복사 → 메모

---

## 7. MSK Kafka 생성

### 7.1 보안 그룹 생성

1. VPC → **보안 그룹** → **보안 그룹 생성**

| 항목 | 값 |
|------|---|
| 이름 | `urr-prod-msk-sg` |
| VPC | urr-prod-vpc |

**인바운드 규칙:**

| 유형 | 포트 | 소스 | 설명 |
|------|------|------|------|
| 사용자 지정 TCP | 9094 | EKS 노드 보안 그룹 | TLS |
| 사용자 지정 TCP | 9098 | EKS 노드 보안 그룹 | IAM Auth |
| 사용자 지정 TCP | 2181 | 자기 자신 (자체 SG) | ZooKeeper |

### 7.2 MSK 구성 생성

1. AWS 콘솔 → **Amazon MSK** → 왼쪽 **사용자 지정 구성** → **구성 생성**

| 항목 | 값 |
|------|---|
| 이름 | `urr-prod-msk-config` |

**구성 내용:**
```
auto.create.topics.enable=true
default.replication.factor=2
min.insync.replicas=1
num.io.threads=8
num.network.threads=5
num.partitions=3
num.replica.fetchers=2
replica.lag.time.max.ms=30000
socket.receive.buffer.bytes=102400
socket.request.max.bytes=104857600
socket.send.buffer.bytes=102400
unclean.leader.election.enable=false
log.retention.hours=168
log.retention.bytes=-1
```

### 7.3 MSK 클러스터 생성

1. MSK → **클러스터** → **클러스터 생성**

| 항목 | 값 |
|------|---|
| 생성 방법 | **사용자 지정 생성** |
| 클러스터 이름 | `urr-prod-msk` |
| 클러스터 유형 | **프로비저닝** |
| Apache Kafka 버전 | `3.6.0` |

**브로커:**

| 항목 | 값 |
|------|---|
| 브로커 유형 | `kafka.t3.small` |
| AZ당 브로커 수 | `1` (총 2개) |
| 스토리지 | `50` GiB per broker |

**네트워킹:**

| 항목 | 값 |
|------|---|
| VPC | urr-prod-vpc |
| 서브넷 | `urr-prod-streaming-a`, `urr-prod-streaming-c` |
| 보안 그룹 | `urr-prod-msk-sg` |

**보안 (액세스 제어):**

| 항목 | 값 |
|------|---|
| 액세스 제어 방법 | **IAM 역할 기반 인증** 체크 |
| TLS를 통한 데이터 암호화 | **TLS 암호화** 체크 |
| 브로커 간 암호화 | **TLS** |

**모니터링:**

| 항목 | 값 |
|------|---|
| CloudWatch 지표 수준 | **주제별 브로커별** (PER_TOPIC_PER_BROKER) |
| 브로커 로그 전달 | CloudWatch Logs → `/aws/msk/urr-prod` |

**구성:**
| 항목 | 값 |
|------|---|
| 서버 구성 | `urr-prod-msk-config` |

2. **클러스터 생성** → **약 20-30분 소요**
3. 생성 완료 후 **클라이언트 정보 보기** → **부트스트랩 서버** 복사 (IAM 인증 엔드포인트)

### 7.4 MSK 로그 그룹 보존기간

CloudWatch → **로그 그룹** → `/aws/msk/urr-prod` → **편집** → **보존 기간**: `14`일

### 7.5 MSK CloudWatch 알람

1. CloudWatch → **알람** → **알람 생성**

**알람 1: 활성 컨트롤러 없음**

| 항목 | 값 |
|------|---|
| 지표 네임스페이스 | `AWS/Kafka` |
| 지표 이름 | `ActiveControllerCount` |
| 통계 | 합계 |
| 기간 | `300`초 |
| 조건 | 보다 작음: `1` |
| 평가 기간 | 1/1 |
| 알람 이름 | `urr-prod-msk-no-active-controller` |
| 알람 작업 | (SNS 토픽 또는 건너뛰기) |

**알람 2: 오프라인 파티션**

| 항목 | 값 |
|------|---|
| 지표 이름 | `OfflinePartitionsCount` |
| 통계 | 합계 |
| 조건 | 보다 큼: `0` |
| 알람 이름 | `urr-prod-msk-offline-partitions` |

---

## 8. ECR 레포지토리 생성

### 8.1 레포지토리 9개 생성

1. AWS 콘솔 → **ECR** → **리포지토리** → **리포지토리 생성**

아래 9개를 **각각** 생성합니다:

| 리포지토리 이름 | 태그 변경 가능 여부 | 스캔 |
|----------------|-------------------|------|
| `urr/gateway` | **변경 가능** (Mutable) | 푸시 시 스캔 |
| `urr/auth` | 변경 가능 | 푸시 시 스캔 |
| `urr/ticket` | 변경 가능 | 푸시 시 스캔 |
| `urr/payment` | 변경 가능 | 푸시 시 스캔 |
| `urr/stats` | 변경 가능 | 푸시 시 스캔 |
| `urr/queue` | 변경 가능 | 푸시 시 스캔 |
| `urr/catalog` | 변경 가능 | 푸시 시 스캔 |
| `urr/community` | 변경 가능 | 푸시 시 스캔 |
| `urr/frontend` | 변경 가능 | 푸시 시 스캔 |

각각 생성 시:
- 가시성: **프라이빗**
- 암호화: **AES-256**

### 8.2 수명 주기 정책 추가

각 레포지토리 클릭 → 왼쪽 **수명 주기 정책** → **수명 주기 정책 규칙 생성**

**규칙 1:**

| 항목 | 값 |
|------|---|
| 규칙 우선 순위 | `1` |
| 설명 | Expire untagged after 7 days |
| 이미지 상태 | 태그가 지정되지 않음 |
| 일 수 이후 | `7` |

**규칙 2:**

| 항목 | 값 |
|------|---|
| 규칙 우선 순위 | `2` |
| 설명 | Keep max 30 images |
| 이미지 상태 | 태그가 지정됨 |
| 수 초과 | `30` |

---

## 9. S3 버킷 생성

### 9.1 프론트엔드 에셋 버킷

1. AWS 콘솔 → **S3** → **버킷 만들기**

| 항목 | 값 |
|------|---|
| 버킷 이름 | `urr-prod-frontend-assets` (전역 고유해야 함) |
| 리전 | ap-northeast-2 |
| 객체 소유권 | ACL 비활성화 |
| 모든 퍼블릭 액세스 차단 | **체크** (전부 차단) |
| 버킷 버전 관리 | **활성화** |
| 암호화 | SSE-S3 (Amazon S3 관리형 키) |
| 버킷 키 | **활성화** |

2. **버킷 만들기** 클릭

3. 생성된 버킷 → **권한** 탭 → **버킷 정책** → **편집**:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "AllowCloudFrontOAC",
      "Effect": "Allow",
      "Principal": {
        "Service": "cloudfront.amazonaws.com"
      },
      "Action": "s3:GetObject",
      "Resource": "arn:aws:s3:::urr-prod-frontend-assets/*",
      "Condition": {
        "StringEquals": {
          "AWS:SourceArn": "arn:aws:cloudfront::{ACCOUNT_ID}:distribution/{DISTRIBUTION_ID}"
        }
      }
    }
  ]
}
```
> **참고**: `{DISTRIBUTION_ID}`는 CloudFront 생성 후 업데이트해야 합니다.

4. **CORS 구성** → 버킷 → 권한 → CORS:

```json
[
  {
    "AllowedHeaders": ["*"],
    "AllowedMethods": ["GET", "HEAD"],
    "AllowedOrigins": ["*"],
    "ExposeHeaders": ["ETag"],
    "MaxAgeSeconds": 3600
  }
]
```

### 9.2 S3 수명 주기 규칙

버킷 선택 → **관리** 탭 → **수명 주기 규칙 생성**

**규칙 1: 이전 버전 정리**

| 항목 | 값 |
|------|---|
| 이름 | `cleanup-old-versions` |
| 범위 | 전체 버킷에 적용 |
| 규칙 작업 | **비현재 버전의 객체를 영구적으로 삭제** 체크 |
| 비현재 객체 유지 일수 | `30`일 |

**규칙 2: 미완료 멀티파트 업로드 정리**

| 항목 | 값 |
|------|---|
| 이름 | `abort-incomplete-multipart` |
| 범위 | 전체 버킷에 적용 |
| 규칙 작업 | **만료된 객체 삭제 마커 또는 불완전한 멀티파트 업로드 삭제** 체크 |
| 불완전한 멀티파트 업로드 삭제 일수 | `7`일 |

---

## 10. Secrets Manager 설정

### 10.1 RDS 자격 증명

1. AWS 콘솔 → **Secrets Manager** → **새 보안 암호 저장**

| 항목 | 값 |
|------|---|
| 보안 암호 유형 | **기타 유형의 보안 암호** |

**키/값 쌍:**

| 키 | 값 |
|---|---|
| `username` | `urr_admin` |
| `password` | (RDS 생성 시 입력한 비밀번호) |
| `engine` | `postgres` |
| `host` | (RDS 엔드포인트) |
| `port` | `5432` |
| `dbname` | `ticket_db` |

| 항목 | 값 |
|------|---|
| 보안 암호 이름 | `urr-prod/rds-credentials` |

2. **저장** 클릭

### 10.2 Redis Auth Token

동일 방법으로:

| 이름 | 키 | 값 |
|------|---|---|
| `urr-prod/redis-auth-token` | `token` | (ElastiCache AUTH 토큰) |

### 10.3 Queue Entry Token Secret

| 이름 | 키 | 값 |
|------|---|---|
| `urr-prod/queue-entry-token-secret` | `secret` | (64자 랜덤 문자열 생성) |

```bash
# 랜덤 문자열 생성
openssl rand -hex 32
```

### 10.4 JWT Secret

| 이름 | 키 | 값 |
|------|---|---|
| `urr-prod/jwt-secret` | `secret` | (64자 랜덤 문자열 생성) |

---

## 11. SQS 대기열 생성

### 11.1 DLQ (Dead Letter Queue) 먼저 생성

1. AWS 콘솔 → **SQS** → **대기열 생성**

| 항목 | 값 |
|------|---|
| 유형 | **FIFO** |
| 이름 | `urr-prod-ticket-events-dlq.fifo` |
| 메시지 보존 기간 | `14`일 |
| 가시성 제한 시간 | `300`초 |

2. **대기열 생성**

### 11.2 메인 FIFO 큐 생성

1. SQS → **대기열 생성**

| 항목 | 값 |
|------|---|
| 유형 | **FIFO** |
| 이름 | `urr-prod-ticket-events.fifo` |
| 콘텐츠 기반 중복 제거 | **활성화** |
| 중복 제거 범위 | `messageGroup` |
| 처리량 제한 | `perMessageGroupId` |
| 메시지 보존 기간 | `4`일 |
| 가시성 제한 시간 | `300`초 |
| 메시지 수신 대기 시간 | `10`초 |
| 암호화 | SQS 관리형 (SSE-SQS) |

**배달 못한 편지 대기열:**

| 항목 | 값 |
|------|---|
| 활성화 | **체크** |
| 대기열 | `urr-prod-ticket-events-dlq.fifo` |
| 최대 수신 수 | `3` |

2. **대기열 생성** → **대기열 URL 복사** → 메모

### 11.3 액세스 정책

메인 큐 선택 → **액세스 정책** 탭 → **편집**:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Principal": {
        "AWS": "arn:aws:iam::{ACCOUNT_ID}:role/urr-prod-eks-node-role"
      },
      "Action": [
        "sqs:SendMessage",
        "sqs:GetQueueAttributes",
        "sqs:GetQueueUrl"
      ],
      "Resource": "arn:aws:sqs:ap-northeast-2:{ACCOUNT_ID}:urr-prod-ticket-events.fifo"
    },
    {
      "Effect": "Allow",
      "Principal": {
        "AWS": "arn:aws:iam::{ACCOUNT_ID}:role/urr-prod-lambda-worker-role"
      },
      "Action": [
        "sqs:ReceiveMessage",
        "sqs:DeleteMessage",
        "sqs:ChangeMessageVisibility",
        "sqs:GetQueueAttributes"
      ],
      "Resource": "arn:aws:sqs:ap-northeast-2:{ACCOUNT_ID}:urr-prod-ticket-events.fifo"
    }
  ]
}
```

### 11.4 SQS CloudWatch 알람

CloudWatch → **알람** → **알람 생성**

**알람 1: DLQ 메시지 도착 (장애 감지)**

| 항목 | 값 |
|------|---|
| 지표 네임스페이스 | `AWS/SQS` |
| 지표 이름 | `ApproximateNumberOfMessagesVisible` |
| 대기열 이름 | `urr-prod-ticket-events-dlq.fifo` |
| 통계 | 합계 |
| 기간 | `300`초 |
| 조건 | 보다 큼: `0` |
| 평가 기간 | 1/1 |
| 알람 이름 | `urr-prod-sqs-dlq-has-messages` |
| 누락 데이터 처리 | notBreaching |

**알람 2: 메시지 체류 시간 초과**

| 항목 | 값 |
|------|---|
| 지표 이름 | `ApproximateAgeOfOldestMessage` |
| 대기열 이름 | `urr-prod-ticket-events.fifo` |
| 통계 | 최대값 |
| 조건 | 보다 큼: `600` (10분, 초 단위) |
| 알람 이름 | `urr-prod-sqs-message-age-high` |

---

## 12. ALB 생성

### 12.1 ALB 보안 그룹

1. VPC → **보안 그룹** → **보안 그룹 생성**

| 항목 | 값 |
|------|---|
| 이름 | `urr-prod-alb-sg` |
| VPC | urr-prod-vpc |

**인바운드 규칙:**

| 유형 | 포트 | 소스 | 설명 |
|------|------|------|------|
| HTTPS | 443 | **관리형 접두사 목록** → `com.amazonaws.global.cloudfront.origin-facing` | CloudFront만 허용 (권장) |
| HTTP | 80 | **관리형 접두사 목록** → `com.amazonaws.global.cloudfront.origin-facing` | HTTP 트래픽 |

> **보안 권장**: `0.0.0.0/0` 대신 **CloudFront 관리형 접두사 목록**을 사용하면 ALB에 직접 접근을 차단하여 WAF 우회를 방지합니다.
> 소스 선택 시 → **관리형 접두사 목록** 탭 → `com.amazonaws.global.cloudfront.origin-facing` 선택
>
> **도메인 없이 테스트**: HTTP 80만 열어도 됩니다. CloudFront가 HTTP로 연결하므로.

**아웃바운드 규칙:**

| 유형 | 포트 | 대상 |
|------|------|------|
| 모든 트래픽 | 전체 | `0.0.0.0/0` |

2. **EKS 노드 보안 그룹에 규칙 추가**:
   - EKS 노드 SG → **인바운드 규칙 편집** → **규칙 추가**:

| 유형 | 포트 범위 | 소스 |
|------|----------|------|
| 사용자 지정 TCP | 0-65535 | `urr-prod-alb-sg` |

### 12.2 대상 그룹 생성

1. EC2 → **대상 그룹** → **대상 그룹 생성**

**Gateway 대상 그룹:**

| 항목 | 값 |
|------|---|
| 대상 유형 | **IP 주소** |
| 이름 | `urr-prod-gateway-tg` |
| 프로토콜 | HTTP |
| 포트 | `3001` |
| VPC | urr-prod-vpc |
| 프로토콜 버전 | HTTP1 |

**상태 검사:**

| 항목 | 값 |
|------|---|
| 경로 | `/health` |
| 프로토콜 | HTTP |
| 정상 임계값 | `2` |
| 비정상 임계값 | `3` |
| 시간 초과 | `5`초 |
| 간격 | `30`초 |
| 성공 코드 | `200` |

**고급 속성:**

| 항목 | 값 |
|------|---|
| 등록 취소 지연 | `30`초 |
| 고정 세션 | **활성화** → LB 쿠키, 86400초 |

2. **다음** → 대상 등록 건너뛰기 (K8s가 자동 등록) → **대상 그룹 생성**

**Frontend 대상 그룹:** 동일하게 생성

| 항목 | 값 |
|------|---|
| 이름 | `urr-prod-frontend-tg` |
| 포트 | `3000` |
| 상태 검사 경로 | `/` |
| 고정 세션 | 비활성화 |

### 12.3 ALB 생성

1. EC2 → **로드 밸런서** → **로드 밸런서 생성** → **Application Load Balancer** 선택

| 항목 | 값 |
|------|---|
| 이름 | `urr-prod-alb` |
| 체계 | **인터넷 경계** |
| IP 주소 유형 | IPv4 |

**네트워크 매핑:**

| 항목 | 값 |
|------|---|
| VPC | urr-prod-vpc |
| 가용 영역 | ap-northeast-2a (퍼블릭 서브넷), ap-northeast-2c (퍼블릭 서브넷) |

**보안 그룹:** `urr-prod-alb-sg`

**리스너:**

---

**A. 도메인 있는 경우 (프로덕션 — HTTPS + HTTP→HTTPS 리다이렉트):**

> **사전 조건**: ACM 인증서가 ap-northeast-2에 발급 완료되어야 합니다 (섹션 12.5 참조).

| 리스너 | 포트 | 기본 작업 |
|--------|------|----------|
| HTTPS | 443 | 전달 → `urr-prod-frontend-tg` |
| HTTP | 80 | 리디렉션 → HTTPS:443 (301) |

**HTTPS 리스너 설정:**

| 항목 | 값 |
|------|---|
| 보안 정책 | `ELBSecurityPolicy-TLS13-1-2-2021-06` |
| 기본 SSL/TLS 인증서 | ACM 인증서 선택 (ap-northeast-2 리전) |

**HTTPS 리스너 규칙 추가** (ALB 생성 후):

ALB 선택 → **리스너** 탭 → HTTPS:443 리스너 클릭 → **규칙** → **규칙 추가**

**규칙 1:**

| 항목 | 값 |
|------|---|
| 이름 | `api-to-gateway` |
| 우선 순위 | `100` |
| 조건 | 경로 패턴: `/api/*` |
| 작업 | 전달: `urr-prod-gateway-tg` |

**규칙 2:**

| 항목 | 값 |
|------|---|
| 이름 | `internal-to-gateway` |
| 우선 순위 | `99` |
| 조건 | 경로 패턴: `/internal/*` |
| 작업 | 전달: `urr-prod-gateway-tg` |

---

**B. 도메인 없이 테스트하는 경우 (HTTP만):**

| 리스너 | 포트 | 기본 작업 |
|--------|------|----------|
| HTTP | 80 | 전달 → `urr-prod-frontend-tg` |

**HTTP 리스너 규칙 추가** (동일한 규칙):

| 이름 | 우선 순위 | 조건 | 작업 |
|------|----------|------|------|
| `api-to-gateway` | `100` | `/api/*` | 전달: `urr-prod-gateway-tg` |
| `internal-to-gateway` | `99` | `/internal/*` | 전달: `urr-prod-gateway-tg` |

---

2. **로드 밸런서 생성** → ALB DNS 이름 복사 → 메모

### 12.4 ACM 인증서 생성 (도메인 사용 시)

> 도메인 없이 테스트만 하는 경우 이 섹션을 건너뛰세요.

#### 12.4.1 ALB용 인증서 (ap-northeast-2)

1. AWS 콘솔 → **Certificate Manager (ACM)** → **인증서 요청**

| 항목 | 값 |
|------|---|
| 인증서 유형 | **퍼블릭 인증서 요청** |
| 도메인 이름 | `urr.guru` |
| 다른 이름 추가 | `*.urr.guru` |
| 검증 방법 | **DNS 검증** |

2. **요청** 클릭 → 인증서 상세 → **Route 53에서 레코드 생성** 클릭 (Route53 존이 있는 경우)
3. DNS 검증 완료까지 대기 (수 분 ~ 최대 30분)
4. 상태가 **발급됨**이 되면 ARN 복사 → 메모

#### 12.4.2 CloudFront용 인증서 (us-east-1)

> **중요**: CloudFront는 반드시 **us-east-1 (버지니아 북부)** 리전의 인증서만 사용 가능!

1. **리전을 us-east-1로 변경**
2. ACM → **인증서 요청** (동일한 도메인/와일드카드)
3. DNS 검증 (Route53이면 자동, 아니면 CNAME 수동 추가)
4. 발급 후 ARN 복사 → 메모
5. **리전을 다시 ap-northeast-2로 돌리기!**

### 12.5 Route53 호스트 영역 생성 (도메인 사용 시)

> 도메인 없이 테스트만 하는 경우 이 섹션을 건너뛰세요.

1. AWS 콘솔 → **Route 53** → **호스트 영역** → **호스트 영역 생성**

| 항목 | 값 |
|------|---|
| 도메인 이름 | `urr.guru` |
| 유형 | **퍼블릭 호스트 영역** |

2. **호스트 영역 생성** 클릭
3. **NS 레코드 4개** 복사 → **GoDaddy에서 네임서버 변경** (NS 전파 최대 48시간)
4. NS 전파 후 → **레코드 생성**:

**A 레코드 (루트 도메인):**

| 항목 | 값 |
|------|---|
| 레코드 이름 | (비워둠 = `urr.guru`) |
| 레코드 유형 | **A** |
| 별칭 | **예** |
| 트래픽 라우팅 대상 | **CloudFront 배포에 대한 별칭** → 배포 도메인 선택 |

**A 레코드 (www):**

| 항목 | 값 |
|------|---|
| 레코드 이름 | `www` |
| 레코드 유형 | **A** |
| 별칭 → 대상 | 동일 CloudFront 배포 |

---

## 13. CloudFront 배포 생성

### 13.1 OAC (Origin Access Control) 생성

1. AWS 콘솔 → **CloudFront** → 왼쪽 **원본 액세스** → **제어 설정 생성**

| 항목 | 값 |
|------|---|
| 이름 | `urr-prod-s3-oac` |
| 설명 | OAC for S3 frontend bucket |
| 원본 유형 | **S3** |
| 서명 동작 | **항상 요청 서명** |
| 서명 프로토콜 | SigV4 |

### 13.2 CloudFront 배포 생성

1. CloudFront → **배포** → **배포 생성**

**원본 1 (ALB):**

| 항목 | 값 |
|------|---|
| 원본 도메인 | (ALB DNS 이름 선택 드롭다운에서) |
| 프로토콜 | **HTTP만** (도메인 없는 경우) |
| HTTP 포트 | 80 |
| 원본 경로 | (비워둠) |

**사용자 정의 헤더 추가:**
→ **헤더 추가** 클릭

| 헤더 이름 | 값 |
|----------|---|
| `X-CloudFront-Verified` | (복잡한 비밀 문자열 입력 → **메모!**) |

**기본 캐시 동작:**

| 항목 | 값 |
|------|---|
| 경로 패턴 | 기본값 (*) |
| 뷰어 프로토콜 정책 | **HTTP와 HTTPS로 리디렉션** |
| 허용된 HTTP 메서드 | **GET, HEAD, OPTIONS, PUT, POST, PATCH, DELETE** |
| 캐시 키 및 원본 요청 | **캐시 정책 및 원본 요청 정책** |
| 캐시 정책 | `urr-prod-frontend-ssr` (아래에서 생성) 또는 **CachingDisabled** |
| 원본 요청 정책 | **AllViewer** |
| 응답 헤더 정책 | `urr-prod-security-headers` |

2. **배포 생성** 클릭

### 13.2a 커스텀 캐시 정책 생성 (선택 — 성능 최적화)

> 기본 **CachingDisabled**로도 동작하지만, 아래 커스텀 정책을 생성하면 성능이 향상됩니다.

CloudFront → **정책** → **캐시** 탭 → **캐시 정책 생성**

**정책 1: Frontend SSR 캐시** (기본 동작용)

| 항목 | 값 |
|------|---|
| 이름 | `urr-prod-frontend-ssr` |
| 최소 TTL | `0` |
| 기본 TTL | `0` |
| 최대 TTL | `60` |
| 압축 지원 | Gzip ✅, Brotli ✅ |
| 캐시 키 쿠키 | **모두** |
| 캐시 키 헤더 | **허용 목록**: `Authorization`, `Host` |
| 캐시 키 쿼리 문자열 | **모두** |

**정책 2: API 캐시 (캐싱 없음)** (`/api/*` 동작용)

| 항목 | 값 |
|------|---|
| 이름 | `urr-prod-api-no-cache` |
| 최소/기본/최대 TTL | 전부 `0` |
| 압축 지원 | Gzip ✅, Brotli ✅ |
| 캐시 키 쿠키 | **모두** |
| 캐시 키 헤더 | **허용 목록**: `Authorization`, `CloudFront-Viewer-Country`, `Host` |
| 캐시 키 쿼리 문자열 | **모두** |

**정책 3: VWR 정적 캐시** (`/vwr/*` 동작용)

| 항목 | 값 |
|------|---|
| 이름 | `urr-prod-vwr-static` |
| 최소 TTL | `0` |
| 기본 TTL | `300` (5분) |
| 최대 TTL | `3600` (1시간) |
| 압축 지원 | Gzip ✅, Brotli ✅ |
| 캐시 키 쿠키/헤더/쿼리 | **없음** (정적 파일) |

### 13.3 보안 응답 헤더 정책 생성

배포 전에 보안 헤더 정책을 먼저 생성합니다.

1. CloudFront → 왼쪽 **정책** → **응답 헤더** 탭 → **응답 헤더 정책 생성**

| 항목 | 값 |
|------|---|
| 이름 | `urr-prod-security-headers` |

**보안 헤더:**

| 헤더 | 값 | 재정의 |
|------|---|--------|
| Strict-Transport-Security (HSTS) | `max-age=31536000; includeSubDomains; preload` | 예 |
| X-Content-Type-Options | `nosniff` | 예 |
| X-Frame-Options | `DENY` | 예 |
| X-XSS-Protection | `1; mode=block` | 예 |
| Referrer-Policy | `strict-origin-when-cross-origin` | 예 |

**CORS:**

| 항목 | 값 |
|------|---|
| Access-Control-Allow-Credentials | `true` |
| Access-Control-Allow-Headers | `*` |
| Access-Control-Allow-Methods | 전체 |
| Access-Control-Allow-Origin | `*` (또는 특정 도메인) |
| Access-Control-Max-Age | `3600` |
| 원본 재정의 | 아니요 |

2. **정책 생성**

### 13.4 추가 캐시 동작 생성

배포 클릭 → **동작** 탭 → **동작 생성**

**동작 1: /api/***

| 항목 | 값 |
|------|---|
| 경로 패턴 | `/api/*` |
| 원본 | ALB 원본 |
| 뷰어 프로토콜 | HTTPS로 리디렉션 |
| HTTP 메서드 | GET, HEAD, OPTIONS, PUT, POST, PATCH, DELETE |
| 캐시 정책 | **CachingDisabled** |
| 원본 요청 정책 | **AllViewer** |
| 응답 헤더 정책 | `urr-prod-security-headers` |

**동작 2: /_next/static/***

| 항목 | 값 |
|------|---|
| 경로 패턴 | `/_next/static/*` |
| 원본 | S3 원본 (아래에서 추가) |
| 뷰어 프로토콜 | HTTPS로 리디렉션 |
| 캐시 정책 | **CachingOptimized** |

### 13.4 S3 원본 추가

배포 → **원본** 탭 → **원본 생성**

| 항목 | 값 |
|------|---|
| 원본 도메인 | S3 버킷 선택 (`urr-prod-frontend-assets`) |
| 원본 액세스 | **원본 액세스 제어 설정 (권장)** |
| OAC | `urr-prod-s3-oac` |

> S3 버킷 정책을 업데이트하라는 배너 뜸 → **정책 복사** → S3 버킷 정책에 붙여넣기

### 13.5 CloudFront Functions 생성

> CloudFront Functions는 Lambda@Edge보다 가볍고, URL 재작성에 사용됩니다.

#### 13.5.1 VWR 페이지 리라이트 함수

1. CloudFront → 왼쪽 **함수** → **함수 생성**

| 항목 | 값 |
|------|---|
| 이름 | `urr-prod-vwr-page-rewrite` |
| 설명 | Rewrites /vwr/{eventId} to /vwr/index.html for S3 static page |
| 런타임 | **cloudfront-js-2.0** |

2. **함수 코드**에 입력:

```javascript
function handler(event) {
  var request = event.request;
  request.uri = '/vwr/index.html';
  return request;
}
```

3. **변경 사항 저장** → **게시** 탭 → **함수 게시**

#### 13.5.2 VWR API 리라이트 함수

동일 방법으로 생성:

| 항목 | 값 |
|------|---|
| 이름 | `urr-prod-vwr-api-rewrite` |
| 설명 | Strips /vwr-api prefix before forwarding to API Gateway |

코드:

```javascript
function handler(event) {
  var request = event.request;
  request.uri = request.uri.replace(/^\/vwr-api/, '');
  if (request.uri === '') request.uri = '/';
  return request;
}
```

**게시** 후 → 배포에 연결합니다.

#### 13.5.3 CloudFront에 VWR 동작 + 함수 연결

배포 → **동작** 탭 → **동작 생성**

**동작: /vwr/***

| 항목 | 값 |
|------|---|
| 경로 패턴 | `/vwr/*` |
| 원본 | S3 원본 (urr-prod-frontend-assets) |
| 뷰어 프로토콜 | HTTPS로 리디렉션 |
| 캐시 정책 | 사용자 지정 (TTL: 기본 300초, 최대 3600초) |
| **함수 연결** → 뷰어 요청 | **CloudFront Functions** → `urr-prod-vwr-page-rewrite` |

**동작: /vwr-api/*** (API Gateway VWR 원본이 있을 때)

| 항목 | 값 |
|------|---|
| 경로 패턴 | `/vwr-api/*` |
| 원본 | API Gateway 원본 (아래 추가) |
| 뷰어 프로토콜 | HTTPS로 리디렉션 |
| 캐시 정책 | **CachingDisabled** |
| **함수 연결** → 뷰어 요청 | **CloudFront Functions** → `urr-prod-vwr-api-rewrite` |

### 13.6 VWR API Gateway 원본 추가

배포 → **원본** 탭 → **원본 생성**

| 항목 | 값 |
|------|---|
| 원본 도메인 | `{API_GATEWAY_ID}.execute-api.ap-northeast-2.amazonaws.com` |
| 프로토콜 | **HTTPS만** |
| 원본 경로 | `/v1` (API Gateway 스테이지) |

3. CloudFront 도메인 이름 복사 → 메모 (예: `d1234abcd.cloudfront.net`)

---

## 14. Lambda 함수 생성

### 14.1 Lambda Worker (SQS Consumer)

#### 보안 그룹 생성

VPC → 보안 그룹 → 보안 그룹 생성

| 항목 | 값 |
|------|---|
| 이름 | `urr-prod-lambda-worker-sg` |
| VPC | urr-prod-vpc |

**아웃바운드 규칙:**

| 유형 | 포트 | 대상 | 설명 |
|------|------|------|------|
| 모든 트래픽 | 전체 | `0.0.0.0/0` | 인터넷 접근 |

**ALB 보안 그룹에 규칙 추가:**
`urr-prod-alb-sg` → 인바운드 → 규칙 추가:

| 유형 | 포트 | 소스 |
|------|------|------|
| HTTPS | 443 | `urr-prod-lambda-worker-sg` |
| HTTP | 80 | `urr-prod-lambda-worker-sg` |

#### Lambda 함수 생성

1. AWS 콘솔 → **Lambda** → **함수 생성**

| 항목 | 값 |
|------|---|
| 작성 방법 | **새로 작성** |
| 함수 이름 | `urr-prod-ticket-worker` |
| 런타임 | Node.js 20.x |
| 아키텍처 | x86_64 |
| 실행 역할 | **기존 역할 사용** → `urr-prod-lambda-worker-role` |

**고급 설정:**

| 항목 | 값 |
|------|---|
| VPC 활성화 | **체크** |
| VPC | urr-prod-vpc |
| 서브넷 | Streaming 서브넷 2개 |
| 보안 그룹 | `urr-prod-lambda-worker-sg` |

2. **함수 생성** → 생성 후 **구성** 탭:

**일반 구성 편집:**

| 항목 | 값 |
|------|---|
| 메모리 | `256` MB |
| 시간 초과 | `30`초 |

**환경 변수:**

| 키 | 값 |
|---|---|
| `ENVIRONMENT` | `prod` |
| `TICKET_SERVICE_URL` | `http://{ALB_DNS_NAME}` (도메인 없는 경우) |
| `INTERNAL_API_TOKEN` | (내부 API 토큰) |

**동시성:**
| 항목 | 값 |
|------|---|
| 예약된 동시성 | `10` |

**트리거 추가:**
→ **트리거 추가** → SQS

| 항목 | 값 |
|------|---|
| SQS 대기열 | `urr-prod-ticket-events.fifo` |
| 배치 크기 | `10` |
| 배치 기간 | `5`초 |
| 최대 동시성 | `10` |
| 함수 응답 유형 | **ReportBatchItemFailures** 체크 |

> **중요**: `ReportBatchItemFailures`를 활성화하지 않으면 배치 내 1개 메시지 실패 시 **전체 배치가 재처리**됩니다.

3. **코드 업로드**: `lambda/ticket-worker/` 디렉토리를 zip → 업로드

```bash
cd lambda/ticket-worker
zip -r ../../ticket-worker.zip .
# Lambda 콘솔 → 코드 → .zip 파일 업로드
```

4. **모니터링 설정** (구성 → 모니터링 및 운영 도구):

| 항목 | 값 |
|------|---|
| X-Ray 활성 추적 | **활성화** |

5. **로그 그룹 보존기간**: CloudWatch → 로그 그룹 → `/aws/lambda/urr-prod-ticket-worker` → 보존 기간: `7`일

### 14.1a Lambda Worker CloudWatch 알람

CloudWatch → **알람** → **알람 생성**

**알람 1: Lambda 에러 감지**

| 항목 | 값 |
|------|---|
| 지표 | `AWS/Lambda` → `Errors` |
| 차원 | FunctionName: `urr-prod-ticket-worker` |
| 통계 | 합계 |
| 기간 | `300`초 |
| 조건 | 보다 큼: `5` |
| 평가 기간 | 2/2 |
| 알람 이름 | `urr-prod-lambda-worker-errors` |
| 누락 데이터 | notBreaching |

**알람 2: Lambda 실행 시간 과다**

| 항목 | 값 |
|------|---|
| 지표 | `AWS/Lambda` → `Duration` |
| 통계 | 평균 |
| 조건 | 보다 큼: `24000` (ms, 타임아웃 30초의 80%) |
| 알람 이름 | `urr-prod-lambda-worker-duration` |

**알람 3: Lambda 스로틀 감지**

| 항목 | 값 |
|------|---|
| 지표 | `AWS/Lambda` → `Throttles` |
| 통계 | 합계 |
| 조건 | 보다 큼: `0` |
| 알람 이름 | `urr-prod-lambda-worker-throttles` |

### 14.2 Lambda@Edge (CloudFront Queue Check)

> **중요**: Lambda@Edge는 **us-east-1 (버지니아 북부)** 리전에서 생성해야 합니다!

1. 리전을 **미국 동부 (버지니아 북부) us-east-1**로 변경
2. Lambda → 함수 생성

| 항목 | 값 |
|------|---|
| 함수 이름 | `urr-prod-edge-queue-check` |
| 런타임 | Node.js 20.x |
| 역할 | `urr-prod-lambda-edge-role` |

**구성:**

| 항목 | 값 |
|------|---|
| 메모리 | `128` MB |
| 시간 초과 | `5`초 |

3. **코드 패키징 전 config.json 생성** (CRITICAL):

> Lambda@Edge는 환경 변수를 사용할 수 없으므로, 설정을 config.json 파일에 포함시켜야 합니다!

```bash
# lambda/edge-queue-check/ 디렉토리에 config.json 생성
cat > lambda/edge-queue-check/config.json << 'EOF'
{
  "queueEntryTokenSecret": "{QUEUE_ENTRY_TOKEN_SECRET}",
  "vwrApiEndpoint": "{API_GATEWAY_INVOKE_URL}",
  "cookieName": "urr_queue_token"
}
EOF

# vwr-active.json 생성 (현재 대기열 활성 이벤트 목록)
cat > lambda/edge-queue-check/vwr-active.json << 'EOF'
{
  "activeEvents": []
}
EOF
```

> `{QUEUE_ENTRY_TOKEN_SECRET}`: Secrets Manager의 queue-entry-token-secret 값
> `{API_GATEWAY_INVOKE_URL}`: API Gateway 호출 URL

4. zip 패키징 후 업로드:

```bash
cd lambda/edge-queue-check
zip -r ../../edge-queue-check.zip .
# Lambda 콘솔 → 코드 → .zip 파일 업로드
```

5. **작업** → **새 버전 게시** (Lambda@Edge는 게시 필수)
6. 게시된 ARN 복사 (`:1` 포함된 qualified ARN)

6. **리전을 다시 ap-northeast-2로 돌리기!**

7. CloudFront → 배포 → 동작 → `/api/*` 동작 편집:
   - **함수 연결** → **Lambda@Edge** → **뷰어 요청** → 게시된 ARN 입력

### 14.3 VWR Lambda 함수 생성

#### 14.3.1 VWR API 함수 (Tier 1 대기열 API)

1. Lambda → **함수 생성** (리전: ap-northeast-2)

| 항목 | 값 |
|------|---|
| 함수 이름 | `urr-prod-vwr-api` |
| 런타임 | Node.js 20.x |
| 아키텍처 | x86_64 |
| 실행 역할 | `urr-prod-vwr-lambda-role` |

2. **구성** 탭:

**일반 구성:**

| 항목 | 값 |
|------|---|
| 메모리 | `256` MB |
| 시간 초과 | `10`초 |

**환경 변수:**

| 키 | 값 |
|---|---|
| `TABLE_COUNTERS` | `urr-prod-vwr-counters` |
| `TABLE_POSITIONS` | `urr-prod-vwr-positions` |
| `VWR_TOKEN_SECRET` | (HMAC 비밀키, 64자 랜덤 문자열) |
| `CORS_ORIGIN` | `https://{CLOUDFRONT_DOMAIN}` (또는 `*`) |

**동시성:**

| 항목 | 값 |
|------|---|
| 예약된 동시성 | `500` |

3. `lambda/vwr-api/` 디렉토리를 zip → 업로드

#### 14.3.2 VWR Counter Advancer 함수

1. Lambda → **함수 생성**

| 항목 | 값 |
|------|---|
| 함수 이름 | `urr-prod-vwr-counter-advancer` |
| 런타임 | Node.js 20.x |
| 실행 역할 | `urr-prod-vwr-lambda-role` (VWR API와 동일 역할) |

2. **구성**:

| 항목 | 값 |
|------|---|
| 메모리 | `128` MB |
| 시간 초과 | `70`초 (내부 6회 루프 × 10초 + 여유) |

**환경 변수:**

| 키 | 값 |
|---|---|
| `TABLE_COUNTERS` | `urr-prod-vwr-counters` |
| `BATCH_SIZE` | `500` |

3. `lambda/vwr-counter-advancer/` 디렉토리를 zip → 업로드

#### 14.3.3 EventBridge 스케줄러 연결

1. AWS 콘솔 → **Amazon EventBridge** → **규칙** → **규칙 생성**

| 항목 | 값 |
|------|---|
| 이름 | `urr-prod-vwr-counter-advance` |
| 설명 | Advance VWR serving counter every 10 seconds |
| 이벤트 버스 | default |
| 규칙 유형 | **스케줄** |
| 스케줄 표현식 | `rate(1 minute)` |

2. **대상**:

| 항목 | 값 |
|------|---|
| 대상 유형 | **Lambda 함수** |
| 함수 | `urr-prod-vwr-counter-advancer` |

3. **규칙 생성** 클릭

> 1분 주기로 호출되고, Lambda 내부에서 10초 간격으로 6회 루프하여 실질적으로 10초마다 카운터 진행

---

## 15. DynamoDB 테이블 생성

### 15.1 VWR Counters 테이블

1. AWS 콘솔 → **DynamoDB** → **테이블 생성**

| 항목 | 값 |
|------|---|
| 테이블 이름 | `urr-prod-vwr-counters` |
| 파티션 키 | `eventId` (문자열) |
| 정렬 키 | (없음) |
| 테이블 설정 | **사용자 지정** |
| 용량 모드 | **온디맨드** |
| 특정 시점 복구 (PITR) | **활성화** |

### 15.2 VWR Positions 테이블

| 항목 | 값 |
|------|---|
| 테이블 이름 | `urr-prod-vwr-positions` |
| 파티션 키 | `eventId` (문자열) |
| 정렬 키 | `requestId` (문자열) |
| 용량 모드 | 온디맨드 |
| TTL | **활성화** → 속성 이름: `ttl` |
| PITR | 활성화 |

**글로벌 보조 인덱스 (GSI) 추가:**

| 항목 | 값 |
|------|---|
| 인덱스 이름 | `eventId-position-index` |
| 파티션 키 | `eventId` (문자열) |
| 정렬 키 | `position` (숫자) |
| 프로젝션 | **모두** |

---

## 16. API Gateway 생성

### 16.1 REST API 생성

1. AWS 콘솔 → **API Gateway** → **API 생성** → **REST API** → **구축**

| 항목 | 값 |
|------|---|
| API 이름 | `urr-prod-vwr-api` |
| 엔드포인트 유형 | **리전** |

### 16.2 리소스 및 메서드 생성

**리소스 구조:**
```
/
├── /vwr
│   ├── /assign
│   │   └── /{eventId}     POST
│   ├── /check
│   │   └── /{eventId}
│   │       └── /{requestId} GET
│   └── /status
│       └── /{eventId}     GET
```

각 리소스 생성: **작업** → **리소스 생성** → 경로 입력

각 메서드 생성: 리소스 선택 → **작업** → **메서드 생성**

| 항목 | 값 |
|------|---|
| 통합 유형 | **Lambda 함수** |
| Lambda 프록시 통합 사용 | **체크** |
| Lambda 함수 | `urr-prod-vwr-api` |

### 16.3 CORS 활성화

각 리소스에서: **작업** → **CORS 활성화**

| 항목 | 값 |
|------|---|
| Access-Control-Allow-Origin | (CORS 원본, 예: `https://urr.guru` 또는 `*`) |
| Access-Control-Allow-Headers | `Content-Type,Authorization` |
| Access-Control-Allow-Methods | `GET,POST,OPTIONS` |

### 16.4 배포

1. **작업** → **API 배포**

| 항목 | 값 |
|------|---|
| 배포 스테이지 | **새 스테이지** |
| 스테이지 이름 | `v1` |

2. 호출 URL 복사 → 메모 (예: `https://xxxxx.execute-api.ap-northeast-2.amazonaws.com/v1`)

---

## 17. WAF 설정

> **중요**: CloudFront용 WAF는 **us-east-1 (버지니아 북부)**에서 생성해야 합니다!

1. 리전을 **us-east-1**로 변경
2. AWS 콘솔 → **WAF & Shield** → **Web ACL** → **Web ACL 생성**

| 항목 | 값 |
|------|---|
| 이름 | `urr-prod-cloudfront-waf` |
| 리소스 유형 | **Amazon CloudFront 배포** |
| 연결된 AWS 리소스 | (생성한 CloudFront 배포 선택) |

**규칙 추가:**

**규칙 1: 속도 제한**
→ **규칙 추가** → **나만의 규칙 추가**

| 항목 | 값 |
|------|---|
| 규칙 유형 | **속도 기반 규칙** |
| 이름 | `rate-limit-2000` |
| 속도 제한 | `2000` (5분당) |
| 집계 키 | **소스 IP 주소** |
| 작업 | **차단** |

**규칙 2: AWS 관리형 규칙**
→ **규칙 추가** → **AWS 관리형 규칙 그룹 추가**

체크할 규칙:
- ✅ **코어 규칙 집합** (AWSManagedRulesCommonRuleSet)
- ✅ **알려진 잘못된 입력** (AWSManagedRulesKnownBadInputsRuleSet)
- ✅ **SQL 삽입** (AWSManagedRulesSQLiRuleSet)

**기본 작업:** `Allow` (규칙에 매칭되지 않으면 허용)

3. **Web ACL 생성** 클릭
4. **리전을 다시 ap-northeast-2로 돌리기!**

---

## 18. AMP/AMG 모니터링 설정

> AMP (Amazon Managed Prometheus) + AMG (Amazon Managed Grafana)로 K8s 클러스터와 서비스를 모니터링합니다.

### 18.1 Amazon Managed Prometheus (AMP) 생성

1. AWS 콘솔 → 상단 검색 `Prometheus` → **Amazon Managed Service for Prometheus**
2. **워크스페이스 생성**

| 항목 | 값 |
|------|---|
| 별칭 | `urr-prod-amp` |

3. **워크스페이스 생성** → 즉시 생성됨
4. **원격 쓰기 엔드포인트** 복사 → 메모 (예: `https://aps-workspaces.ap-northeast-2.amazonaws.com/workspaces/ws-xxxxx/api/v1/remote_write`)

### 18.2 Amazon Managed Grafana (AMG) 생성

> **사전 조건**: AWS IAM Identity Center (SSO) 활성화 필요

1. AWS 콘솔 → 검색 `Grafana` → **Amazon Managed Grafana**
2. **워크스페이스 생성**

| 항목 | 값 |
|------|---|
| 이름 | `urr-prod-grafana` |
| 인증 | **AWS IAM Identity Center (SSO)** |
| 권한 유형 | **서비스 관리형** |
| IAM 역할 | `urr-prod-amg-role` |
| 데이터 소스 | **Amazon Managed Service for Prometheus** 체크 |
| 플러그인 관리 | **활성화** |

3. **워크스페이스 생성** → 약 5분 소요
4. 생성 후 → **사용자 할당** → SSO 사용자/그룹을 **Admin**으로 할당
5. Grafana URL 메모 (예: `https://g-xxxxx.grafana-workspace.ap-northeast-2.amazonaws.com`)

### 18.3 Prometheus 스택 설치 (EKS에서)

```bash
# kube-prometheus-stack Helm 레포 추가
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm repo update

# 설치 (IRSA 역할 연결 + AMP 원격 쓰기)
helm install kube-prometheus-stack prometheus-community/kube-prometheus-stack \
  --namespace monitoring \
  --set prometheus.serviceAccount.annotations."eks\.amazonaws\.com/role-arn"=arn:aws:iam::{ACCOUNT_ID}:role/urr-prod-prometheus-amp-irsa \
  --set prometheus.prometheusSpec.remoteWrite[0].url={AMP_REMOTE_WRITE_ENDPOINT} \
  --set prometheus.prometheusSpec.remoteWrite[0].sigv4.region=ap-northeast-2 \
  --set prometheus.prometheusSpec.remoteWrite[0].queueConfig.maxSamplesPerSend=1000 \
  --set prometheus.prometheusSpec.remoteWrite[0].queueConfig.maxShards=200 \
  --set prometheus.prometheusSpec.remoteWrite[0].queueConfig.capacity=2500

# 확인
kubectl get pods -n monitoring
```

### 18.4 Grafana에 AMP 데이터 소스 연결

1. Grafana URL 접속 → 로그인
2. 왼쪽 **Configuration** (⚙️) → **Data Sources** → **Add data source**
3. **Prometheus** 선택

| 항목 | 값 |
|------|---|
| URL | AMP 쿼리 엔드포인트 (예: `https://aps-workspaces.ap-northeast-2.amazonaws.com/workspaces/ws-xxxxx`) |
| Auth | **SigV4 auth** 활성화 |
| Default Region | `ap-northeast-2` |

4. **Save & test** → 성공 확인

---

## 19. GitHub Actions CI/CD 설정

### 19.1 GitHub Secrets 설정

1. GitHub → 레포지토리 (`cchriscode/URR`) → **Settings** → 왼쪽 **Secrets and variables** → **Actions**

**Repository secrets** 탭 → **New repository secret**:

| 이름 | 값 | 설명 |
|------|---|------|
| `AWS_ROLE_ARN` | `arn:aws:iam::{ACCOUNT_ID}:role/urr-prod-github-actions-role` | OIDC 역할 ARN |
| `AWS_ACCOUNT_ID` | `{12자리 계정 ID}` | AWS 계정 ID |
| `DISCORD_WEBHOOK` | (Discord 웹훅 URL) | 배포 알림용 |

### 19.2 GitHub Variables 설정

**Repository variables** 탭 → **New repository variable**:

| 이름 | 값 |
|------|---|
| `STAGING_URL` | `https://d1234abcd.cloudfront.net` (CloudFront 도메인) |

### 19.3 GitHub Environment 설정

1. Settings → **Environments** → **New environment**

| 이름 | 값 |
|------|---|
| Environment name | `production` |

2. 생성 후 → **Required reviewers** 체크 → 본인 GitHub 계정 추가
3. **Save protection rules**

> 이렇게 하면 `prod` 환경 배포 시 수동 승인이 필요합니다.

### 19.4 워크플로 동작 확인

워크플로는 이미 `.github/workflows/` 에 있으므로 별도 생성 불필요.

**자동 트리거:**
- `services-spring/auth-service/**` 경로 push → `auth-service-ci-cd.yml` 실행
- `services-spring/gateway-service/**` push → `gateway-service-ci-cd.yml` 실행
- (나머지 서비스 동일)
- `apps/web/**` push → `frontend-ci-cd.yml` 실행

**수동 트리거:**
- Actions 탭 → 워크플로 선택 → **Run workflow** → 환경 선택 (staging/prod)

### 19.5 CI/CD 파이프라인 흐름

```
코드 push (main 브랜치)
  ↓
GitHub Actions 트리거
  ↓
1. test: Gradle 빌드 + 단위 테스트
  ↓
2. build-and-push: Docker 이미지 빌드 → ECR 푸시
  ↓
3. update-manifests: K8s kustomization.yaml 이미지 태그 업데이트
  ↓
4. Git commit (자동) → ArgoCD가 감지 → K8s에 배포
```

---

## 20. ArgoCD 설정

### 20.1 ArgoCD 설치

```bash
# ArgoCD 설치
kubectl create namespace argocd
kubectl apply -n argocd -f https://raw.githubusercontent.com/argoproj/argo-cd/stable/manifests/install.yaml

# ArgoCD 서버 접근 (포트 포워딩)
kubectl port-forward svc/argocd-server -n argocd 8080:443

# 초기 admin 비밀번호 확인
kubectl -n argocd get secret argocd-initial-admin-secret -o jsonpath="{.data.password}" | base64 -d
```

### 20.2 ArgoCD 웹 UI 접속

1. 브라우저에서 `https://localhost:8080` 접속
2. **Username**: `admin`
3. **Password**: (위에서 확인한 비밀번호)

### 20.3 Git 레포지토리 연결

1. ArgoCD UI → 왼쪽 **Settings** (⚙️) → **Repositories** → **Connect Repo**

| 항목 | 값 |
|------|---|
| Method | **VIA HTTPS** |
| Type | git |
| Repository URL | `https://github.com/cchriscode/URR.git` |
| Username | (GitHub 사용자명) |
| Password | (GitHub Personal Access Token) |

> **PAT 생성**: GitHub → Settings → Developer settings → Personal access tokens → **Generate new token (classic)** → `repo` 권한 체크

2. **Connect** 클릭 → 연결 성공 확인

### 20.4 ArgoCD Application 생성

#### 방법 A: ArgoCD UI에서 생성

1. ArgoCD UI → **Applications** → **+ New App**

**Production Application:**

| 항목 | 값 |
|------|---|
| Application Name | `urr-spring-prod` |
| Project | `default` |
| Sync Policy | **Automatic** |
| → AUTO-CREATE NAMESPACE | 체크 |
| → SELF HEAL | 체크 |
| → PRUNE RESOURCES | 체크 |
| Repository URL | `https://github.com/cchriscode/URR.git` |
| Revision | `main` |
| Path | `k8s/spring/overlays/prod` |
| Cluster URL | `https://kubernetes.default.svc` |
| Namespace | `urr-spring` |

2. **Create** 클릭

#### 방법 B: YAML로 생성

```bash
cat <<'EOF' | kubectl apply -f -
apiVersion: argoproj.io/v1alpha1
kind: Application
metadata:
  name: urr-spring-prod
  namespace: argocd
spec:
  project: default
  source:
    repoURL: https://github.com/cchriscode/URR.git
    targetRevision: main
    path: k8s/spring/overlays/prod
  destination:
    server: https://kubernetes.default.svc
    namespace: urr-spring
  syncPolicy:
    automated:
      prune: true
      selfHeal: true
    syncOptions:
      - CreateNamespace=true
EOF
```

### 20.5 Argo Rollouts 설치

```bash
kubectl apply -n argo-rollouts -f https://github.com/argoproj/argo-rollouts/releases/download/v1.7.2/install.yaml

# 확인
kubectl get pods -n argo-rollouts
```

### 20.6 ArgoCD 동작 확인

```bash
# Application 상태 확인
kubectl get applications -n argocd

# 동기화 상태
kubectl describe application urr-spring-prod -n argocd

# Pod 상태 확인
kubectl get pods -n urr-spring
```

ArgoCD UI에서:
- **Sync Status**: `Synced` (녹색)
- **Health Status**: `Healthy` (녹색 하트)

---

## 21. 최종 검증 및 배포

### 21.1 kustomization.yaml 프로덕션 설정 확인

배포 전 `k8s/spring/overlays/prod/kustomization.yaml`이 프로덕션에 맞게 설정되어 있는지 확인합니다.

**이미 적용된 프로덕션 변경사항:**

| 항목 | 변경 이유 |
|------|-----------|
| `kafka.yaml` 제거 | 프로덕션은 AWS MSK(관리형 Kafka)를 사용. 인클러스터 Kafka StatefulSet 불필요 |
| `redis.yaml` 제거 | 프로덕션은 AWS ElastiCache(관리형 Redis)를 사용. 인클러스터 Redis Cluster 불필요 |
| `zipkin.yaml` 추가 | 분산 추적을 위한 Zipkin 서버 (모든 서비스가 참조) |
| `secretGenerator` 제거 | `secrets.env`는 `.gitignore` 대상 → ArgoCD Git 동기화 시 파일 없어서 kustomize build 실패. Secret은 수동 생성 |

> **중요**: 만약 `kustomization.yaml`에 아직 `kafka.yaml`, `redis.yaml`이 있거나 `secretGenerator` 블록이 있다면, 위 표대로 수정 후 커밋하세요. 그렇지 않으면 ArgoCD 동기화가 실패하거나 불필요한 리소스가 배포됩니다.

### 21.2 K8s Config/Secret 파일 준비

배포 전 `config.env`와 `secrets.env`를 실제 값으로 채워야 합니다.

**config.env 업데이트** (`k8s/spring/overlays/prod/config.env`):

> 실제 파일의 **모든 변수**를 빠짐없이 설정해야 합니다. `CHANGE_ME` 부분만 실제 값으로 교체하세요.

```env
# === DB 연결 (RDS Proxy 엔드포인트 사용! 직접 RDS가 아닌 Proxy!) ===
# terraform output: module.rds.rds_proxy_endpoint
AUTH_DB_URL=jdbc:postgresql://{RDS_PROXY_ENDPOINT}:5432/auth_db
TICKET_DB_URL=jdbc:postgresql://{RDS_PROXY_ENDPOINT}:5432/ticket_db
PAYMENT_DB_URL=jdbc:postgresql://{RDS_PROXY_ENDPOINT}:5432/payment_db
STATS_DB_URL=jdbc:postgresql://{RDS_PROXY_ENDPOINT}:5432/stats_db
CATALOG_DB_URL=jdbc:postgresql://{RDS_PROXY_ENDPOINT}:5432/catalog_db
COMMUNITY_DB_URL=jdbc:postgresql://{RDS_PROXY_ENDPOINT}:5432/community_db

# === 서비스 디스커버리 (K8s 내부 DNS, 변경 불필요) ===
AUTH_SERVICE_URL=http://auth-service:3005
TICKET_SERVICE_URL=http://ticket-service:3002
PAYMENT_SERVICE_URL=http://payment-service:3003
STATS_SERVICE_URL=http://stats-service:3004
QUEUE_SERVICE_URL=http://queue-service:3007
COMMUNITY_SERVICE_URL=http://community-service:3008
CATALOG_SERVICE_URL=http://catalog-service:3009

# === Redis (ElastiCache 콘솔에서 복사) ===
# terraform output: module.elasticache.primary_endpoint_address
REDIS_HOST={ELASTICACHE_PRIMARY_ENDPOINT}
REDIS_PORT=6379

# === AWS 리전 ===
AWS_REGION=ap-northeast-2

# === 분산 추적 (Zipkin, K8s 내부) ===
ZIPKIN_ENDPOINT=http://zipkin-spring:9411/api/v2/spans
TRACING_SAMPLING_PROBABILITY=0.1

# === SQS ===
SQS_ENABLED=true
# terraform output: module.sqs.queue_url
SQS_QUEUE_URL={SQS_QUEUE_URL}

# === CORS (도메인 설정에 맞게 변경) ===
CORS_ALLOWED_ORIGINS=https://{DOMAIN_OR_CLOUDFRONT}

# === Kafka (MSK 콘솔 → 클라이언트 정보 → Bootstrap servers 복사) ===
KAFKA_TOPIC_REPLICATION_FACTOR=2
# terraform output: module.msk.bootstrap_brokers_tls
KAFKA_BOOTSTRAP_SERVERS={MSK_BOOTSTRAP_SERVERS_TLS}

# === 보안 ===
COOKIE_SECURE=true
```

> **주의**: `KAFKA_BOOTSTRAP_SERVERS`는 반드시 MSK 부트스트랩 서버 주소로 입력하세요. 기본 config.env에 있는 `kafka-spring-0.kafka-spring-headless:9092` 같은 인클러스터 주소는 MSK를 사용하는 프로덕션에서는 동작하지 않습니다.

**secrets.env 생성** (`k8s/spring/overlays/prod/secrets.env`):

> `secrets.env.example`을 복사하여 모든 24개 키를 채워야 합니다.

```env
# === RDS 마스터 계정 ===
DB_HOST={RDS_PROXY_ENDPOINT}
POSTGRES_USER=urr_admin
POSTGRES_PASSWORD={RDS_마스터_비밀번호}

# === 서비스별 DB 계정 (RDS에서 별도 생성 필요) ===
AUTH_DB_USERNAME={auth_db_유저}
AUTH_DB_PASSWORD={auth_db_비밀번호}
TICKET_DB_USERNAME={ticket_db_유저}
TICKET_DB_PASSWORD={ticket_db_비밀번호}
PAYMENT_DB_USERNAME={payment_db_유저}
PAYMENT_DB_PASSWORD={payment_db_비밀번호}
STATS_DB_USERNAME={stats_db_유저}
STATS_DB_PASSWORD={stats_db_비밀번호}
CATALOG_DB_USERNAME={catalog_db_유저}
CATALOG_DB_PASSWORD={catalog_db_비밀번호}
COMMUNITY_DB_USERNAME={community_db_유저}
COMMUNITY_DB_PASSWORD={community_db_비밀번호}

# === 인증/보안 토큰 ===
JWT_SECRET={base64_인코딩_최소_32바이트}
INTERNAL_API_TOKEN={강력한_랜덤_토큰}
QUEUE_ENTRY_TOKEN_SECRET={최소_32자_시크릿}

# === 외부 서비스 키 ===
TOSS_CLIENT_KEY={토스_프로덕션_클라이언트_키}
GOOGLE_CLIENT_ID={구글_OAuth_클라이언트_ID}

# === AWS 리소스 ===
SQS_QUEUE_URL={SQS_FIFO_큐_URL}
AWS_S3_BUCKET={S3_버킷_이름}
CLOUDFRONT_SECRET={X-CloudFront-Verified_헤더_값}
REDIS_PASSWORD={ElastiCache_AUTH_토큰}
```

> **서비스별 DB 계정 생성 방법**: RDS에 접속하여 각 데이터베이스마다 전용 유저를 생성합니다:
> ```sql
> -- 예: auth_db 전용 유저
> CREATE USER auth_user WITH PASSWORD 'strong_password';
> GRANT ALL PRIVILEGES ON DATABASE auth_db TO auth_user;
> ```

> **주의**: `secrets.env`는 `.gitignore`에 의해 git에 올라가지 않습니다. 수동으로 관리하세요.
> `kustomization.yaml`에서 `secretGenerator`는 제거되어 있습니다 (ArgoCD가 Git에서 secrets.env를 찾을 수 없으므로).
> Secret은 아래처럼 수동으로 생성합니다.

### 21.3 Secret 생성 및 데이터베이스 초기화

```bash
# 1. secrets.env를 K8s Secret으로 수동 생성
kubectl create secret generic spring-prod-secret \
  --from-env-file=k8s/spring/overlays/prod/secrets.env \
  -n urr-spring --dry-run=client -o yaml | kubectl apply -f -

# ArgoCD가 이 Secret을 삭제/덮어쓰지 않도록 레이블 추가
kubectl label secret spring-prod-secret -n urr-spring \
  argocd.argoproj.io/compare-options=IgnoreExtraneous \
  --overwrite

# DB 초기화 Job 실행
kubectl apply -f k8s/spring/overlays/prod/init-databases.yaml -n urr-spring

# Job 완료 확인
kubectl get jobs -n urr-spring
kubectl logs job/init-databases -n urr-spring
```

### 21.4 첫 배포 트리거

**방법 1: 수동 워크플로 실행**
1. GitHub → Actions → `gateway-service-ci-cd` 선택
2. **Run workflow** → Environment: `staging` → **Run workflow**
3. 나머지 서비스도 동일하게 실행

**방법 2: 코드 push**
```bash
# 아무 서비스의 사소한 변경 후 push
git push origin main
# → CI/CD 자동 트리거 → ECR push → K8s manifest 업데이트 → ArgoCD 동기화
```

### 21.5 검증 체크리스트

```bash
# 1. Pod 상태 확인
kubectl get pods -n urr-spring
# 모든 pod가 Running/Ready 상태인지 확인

# 2. 서비스 확인
kubectl get svc -n urr-spring

# 3. 로그 확인
kubectl logs -f deployment/gateway-service -n urr-spring

# 4. 헬스체크
kubectl exec -it deployment/gateway-service -n urr-spring -- curl localhost:3001/health

# 5. CloudFront 접속 테스트
curl -I https://{CLOUDFRONT_DOMAIN}
# HTTP/2 200 이면 성공

# 6. API 테스트
curl https://{CLOUDFRONT_DOMAIN}/api/health
```

### 21.6 전체 아키텍처 흐름 요약

```
사용자 브라우저
  ↓ HTTPS
CloudFront (d1234.cloudfront.net)
  ├── /api/* → ALB → Gateway Service (3001) → 각 서비스
  ├── /_next/static/* → S3 (정적 에셋)
  ├── /vwr/* → S3 (대기열 페이지)
  └── /* → ALB → Frontend (3000, Next.js SSR)

CI/CD:
  GitHub Push → GitHub Actions → ECR Push → K8s Manifest Update
  → ArgoCD 감지 → EKS 자동 배포 (Blue-Green)
```

---

## 부록: 생성된 리소스 메모 템플릿

배포 중 생성되는 값들을 여기에 기록하세요:

```
=== AWS 리소스 메모 ===
Account ID:
VPC ID:
OIDC Provider ID:
Public Subnet AZ-a:
Public Subnet AZ-c:
Private Subnet AZ-a:
Private Subnet AZ-c:
DB Subnet AZ-a:
DB Subnet AZ-c:
Cache Subnet AZ-a:
Cache Subnet AZ-c:
Streaming Subnet AZ-a:
Streaming Subnet AZ-c:

EKS Cluster Name: urr-prod
EKS Node SG ID:
EKS Cluster Endpoint:

RDS Endpoint (Primary):
RDS Read Replica Endpoint:
RDS Proxy Endpoint:
RDS Master Password:
Redis Primary Endpoint:
Redis AUTH Token:
MSK Bootstrap Servers (IAM):

SQS Queue URL:
ALB DNS Name:
CloudFront Domain:
CloudFront Distribution ID:
Gateway Target Group ARN:
Frontend Target Group ARN:

Lambda Edge ARN (qualified):
API Gateway Invoke URL:
VWR Token Secret:

AMP Remote Write Endpoint:
AMG Grafana URL:

GitHub Actions Role ARN:
X-CloudFront-Verified Header Value:
JWT Secret:
Queue Entry Token Secret:
Internal API Token:
```
