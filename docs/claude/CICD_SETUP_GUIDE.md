# CI/CD 파이프라인 연결 설정 가이드

이 문서는 URR 프로젝트의 CI/CD 파이프라인을 실제로 동작시키기 위해 GitHub, AWS, ArgoCD에서 설정해야 하는 모든 항목을 화면 단위로 설명합니다.

---

## 목차

1. [사전 준비사항](#1-사전-준비사항)
2. [AWS 설정](#2-aws-설정)
   - 2.1 ECR 리포지토리 생성
   - 2.2 IAM OIDC Provider 생성
   - 2.3 IAM Role 생성 (GitHub Actions용)
3. [GitHub 설정](#3-github-설정)
   - 3.1 Repository Secrets 등록
   - 3.2 Repository Variables 등록
   - 3.3 Environments 생성
   - 3.4 Branch Protection Rules
4. [코드 내 플레이스홀더 교체](#4-코드-내-플레이스홀더-교체)
   - 4.1 ArgoCD Application 파일
   - 4.2 Kustomization 이미지 레지스트리
5. [ArgoCD 설정](#5-argocd-설정)
   - 5.1 ArgoCD 설치
   - 5.2 Git Repository 연결
   - 5.3 Application 배포
6. [Discord Webhook 설정](#6-discord-webhook-설정)
7. [설정 검증 체크리스트](#7-설정-검증-체크리스트)

---

## 1. 사전 준비사항

시작 전 다음이 준비되어 있어야 합니다:

| 항목 | 설명 |
|------|------|
| AWS 계정 | ap-northeast-2 (서울) 리전 사용 |
| EKS 클러스터 | 동작 중인 Kubernetes 클러스터 |
| kubectl | EKS 클러스터에 연결된 상태 |
| GitHub Repository | 이 코드가 push된 리포지토리 |
| AWS CLI | 로컬에 설치 및 인증 완료 |
| Helm | ArgoCD 설치용 |

---

## 2. AWS 설정

### 2.1 ECR 리포지토리 생성 (9개)

#### 콘솔 경로
```
AWS Console → 서비스 검색: "ECR" → Amazon Elastic Container Registry → Private registry → Repositories
```

#### 단계

1. **좌측 메뉴** → `Private registry` → `Repositories` 클릭
2. 우측 상단 **`Create repository`** 버튼 클릭
3. 아래 설정으로 9개 리포지토리를 각각 생성:

| Repository name | 설명 |
|-----------------|------|
| `urr/gateway` | Gateway Service |
| `urr/auth` | Auth Service |
| `urr/ticket` | Ticket Service |
| `urr/payment` | Payment Service |
| `urr/stats` | Stats Service |
| `urr/queue` | Queue Service |
| `urr/catalog` | Catalog Service |
| `urr/community` | Community Service |
| `urr/frontend` | Frontend (Next.js) |

각 리포지토리 생성 시 설정:
- **Visibility**: `Private`
- **Repository name**: 위 표의 이름 입력 (예: `urr/gateway`)
- **Tag immutability**: `Disabled` (CI가 latest 태그를 덮어씀)
- **Scan on push**: `Enabled` (선택, Trivy와 별도로 ECR 자체 스캔)
- **KMS encryption**: 기본값 유지
- **`Create repository`** 클릭

#### AWS CLI로 일괄 생성 (선택)
```bash
for repo in urr/gateway urr/auth urr/ticket urr/payment urr/stats urr/queue urr/catalog urr/community urr/frontend; do
  aws ecr create-repository \
    --repository-name "$repo" \
    --region ap-northeast-2 \
    --image-scanning-configuration scanOnPush=true
done
```

> 생성 후 리포지토리 URI를 확인합니다. 형식: `{ACCOUNT_ID}.dkr.ecr.ap-northeast-2.amazonaws.com/urr/gateway`
> 여기서 `{ACCOUNT_ID}`는 12자리 AWS 계정 ID입니다.

---

### 2.2 IAM OIDC Provider 생성 (GitHub Actions용)

GitHub Actions가 AWS_ACCESS_KEY 없이 OIDC로 인증할 수 있도록 설정합니다.

#### 콘솔 경로
```
AWS Console → IAM → 좌측 메뉴: Identity providers → Add provider
```

#### 단계

1. **IAM 콘솔** → 좌측 메뉴 `Identity providers` 클릭
2. 우측 상단 **`Add provider`** 클릭
3. 아래와 같이 입력:

| 항목 | 값 |
|------|------|
| Provider type | `OpenID Connect` 선택 |
| Provider URL | `https://token.actions.githubusercontent.com` |
| Audience | `sts.amazonaws.com` |

4. **`Get thumbprint`** 클릭 → Thumbprint가 자동으로 채워짐
5. **`Add provider`** 클릭

---

### 2.3 IAM Role 생성 (GitHub Actions용)

#### 콘솔 경로
```
AWS Console → IAM → 좌측 메뉴: Roles → Create role
```

#### 단계

1. **IAM 콘솔** → 좌측 메뉴 `Roles` → **`Create role`** 클릭

2. **Step 1: Trusted entity type**
   - `Web identity` 선택
   - Identity provider: `token.actions.githubusercontent.com` (방금 만든 것)
   - Audience: `sts.amazonaws.com`
   - **`Next`** 클릭

3. **Step 2: Permissions**
   - 검색창에 `AmazonEC2ContainerRegistryPowerUser` 입력 → 체크
   - **`Next`** 클릭

4. **Step 3: Name and review**
   - Role name: `github-actions-urr-cicd`
   - Description: `GitHub Actions CI/CD role for URR project`
   - **`Create role`** 클릭

5. **Trust Policy 수정** (특정 리포지토리만 허용)
   - 생성된 Role 클릭 → `Trust relationships` 탭 → **`Edit trust policy`**
   - 아래 내용으로 교체:

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
          "token.actions.githubusercontent.com:sub": "repo:{GITHUB_ORG}/{GITHUB_REPO}:*"
        }
      }
    }
  ]
}
```

> `{ACCOUNT_ID}` → 12자리 AWS 계정 ID
> `{GITHUB_ORG}/{GITHUB_REPO}` → 예: `myorg/urr`

6. **`Update policy`** 클릭

7. Role의 **ARN 복사** (다음 단계에서 사용)
   - 형식: `arn:aws:iam::{ACCOUNT_ID}:role/github-actions-urr-cicd`

---

## 3. GitHub 설정

### 3.1 Repository Secrets 등록 (3개)

#### 경로
```
GitHub Repository → Settings 탭 → 좌측 메뉴: Secrets and variables → Actions
```

#### 단계

1. Repository 페이지에서 상단 **`Settings`** 탭 클릭
2. 좌측 메뉴에서 `Secrets and variables` 펼치기 → **`Actions`** 클릭
3. `Secrets` 탭에서 **`New repository secret`** 클릭
4. 아래 3개를 각각 등록:

| Name | Value | 설명 |
|------|-------|------|
| `AWS_ROLE_ARN` | `arn:aws:iam::{ACCOUNT_ID}:role/github-actions-urr-cicd` | 2.3에서 생성한 IAM Role ARN |
| `AWS_ACCOUNT_ID` | `123456789012` (12자리) | AWS 계정 ID |
| `DISCORD_WEBHOOK` | `https://discord.com/api/webhooks/...` | Discord 알림 Webhook URL (6장 참고) |

각 Secret 등록 시:
- **Name** 필드에 위 표의 Name 입력
- **Secret** 필드에 실제 값 입력
- **`Add secret`** 클릭

---

### 3.2 Repository Variables 등록 (1개)

#### 경로 (같은 페이지)
```
Settings → Secrets and variables → Actions → Variables 탭
```

#### 단계

1. `Secrets and variables > Actions` 페이지에서 **`Variables`** 탭 클릭
2. **`New repository variable`** 클릭

| Name | Value | 설명 |
|------|-------|------|
| `STAGING_URL` | `https://staging.urr.guru` | Staging 환경 URL (E2E/Load 테스트 대상) |

- **`Add variable`** 클릭

---

### 3.3 Environments 생성 (2개)

#### 경로
```
Settings → 좌측 메뉴: Environments
```

#### Environment 1: staging

1. **`New environment`** 클릭
2. Name: `staging` 입력 → **`Configure environment`** 클릭
3. 설정:
   - **Deployment branches**: `Selected branches` 선택 → `main` 추가
   - Protection rules는 비워둠 (자동 배포)
4. **`Save protection rules`** 클릭

#### Environment 2: production

1. **`New environment`** 클릭
2. Name: `production` 입력 → **`Configure environment`** 클릭
3. 설정:
   - **Required reviewers**: 체크 활성화 → 승인자 GitHub 계정 추가 (본인 또는 팀원)
   - **Deployment branches**: `Selected branches` 선택 → `main` 추가
4. **`Save protection rules`** 클릭

> production 환경의 Required reviewers는 Rollback 워크플로우에서 프로덕션 배포 전 수동 승인을 강제합니다.

---

### 3.4 Branch Protection Rules

#### 경로
```
Settings → 좌측 메뉴: Rules → Rulesets (또는 Branches)
```

#### 단계

1. **`Add ruleset`** (또는 `Add branch protection rule`) 클릭
2. Branch name pattern: `main`
3. 활성화할 규칙:
   - [x] **Require a pull request before merging**
     - Required approvals: `1`
   - [x] **Require status checks to pass before merging**
     - 검색하여 추가: `Test auth-service`, `Test ticket-service` 등 (PR Validation의 job 이름들)
   - [x] **Require branches to be up to date before merging**
4. **`Create`** (또는 `Save changes`) 클릭

---

## 4. 코드 내 플레이스홀더 교체

### 4.1 ArgoCD Application 파일 (3개)

아래 3개 파일에서 `YOUR_ORG/YOUR_REPO`를 실제 GitHub 리포지토리로 교체합니다.

#### 대상 파일
- `argocd/applications/urr-spring-dev.yaml`
- `argocd/applications/urr-spring-staging.yaml`
- `argocd/applications/urr-spring-prod.yaml`

#### 변경 내용
```yaml
# 변경 전
repoURL: https://github.com/YOUR_ORG/YOUR_REPO.git

# 변경 후 (예시)
repoURL: https://github.com/myorg/urr.git
```

---

### 4.2 Kustomization 이미지 레지스트리 (3개)

아래 3개 파일에서 `CHANGE_ME`를 실제 AWS Account ID로 교체합니다.

#### 대상 파일
- `k8s/spring/overlays/dev/kustomization.yaml`
- `k8s/spring/overlays/staging/kustomization.yaml`
- `k8s/spring/overlays/prod/kustomization.yaml`

#### 변경 내용 (각 파일의 images 섹션, 9개 서비스 모두)
```yaml
# 변경 전
- name: YOUR_ECR_URI/gateway-service
  newName: CHANGE_ME.dkr.ecr.ap-northeast-2.amazonaws.com/urr/gateway
  newTag: latest

# 변경 후 (예시: Account ID가 123456789012인 경우)
- name: YOUR_ECR_URI/gateway-service
  newName: 123456789012.dkr.ecr.ap-northeast-2.amazonaws.com/urr/gateway
  newTag: latest
```

> `CHANGE_ME` 부분만 AWS Account ID(12자리 숫자)로 교체합니다.
> name 필드의 `YOUR_ECR_URI/...`는 Kustomize 내부 참조용이므로 그대로 둡니다.

#### 빠른 교체 (sed 명령)
```bash
# 실제 Account ID로 변경
ACCOUNT_ID="123456789012"

sed -i "s/CHANGE_ME/$ACCOUNT_ID/g" k8s/spring/overlays/dev/kustomization.yaml
sed -i "s/CHANGE_ME/$ACCOUNT_ID/g" k8s/spring/overlays/staging/kustomization.yaml
sed -i "s/CHANGE_ME/$ACCOUNT_ID/g" k8s/spring/overlays/prod/kustomization.yaml
```

---

## 5. ArgoCD 설정

### 5.1 ArgoCD 설치 (EKS 클러스터)

```bash
# 1. ArgoCD 네임스페이스 생성
kubectl create namespace argocd

# 2. ArgoCD 설치
kubectl apply -n argocd -f https://raw.githubusercontent.com/argoproj/argo-cd/stable/manifests/install.yaml

# 3. Argo Rollouts 설치 (Blue/Green 배포용)
kubectl apply -k k8s/argo-rollouts/

# 4. ArgoCD 초기 비밀번호 확인
kubectl -n argocd get secret argocd-initial-admin-secret -o jsonpath="{.data.password}" | base64 -d

# 5. ArgoCD 서버 포트포워딩 (로컬 접속용)
kubectl port-forward svc/argocd-server -n argocd 8080:443
```

> 브라우저에서 `https://localhost:8080` 접속
> Username: `admin`, Password: 4번에서 확인한 값

---

### 5.2 Git Repository 연결

#### ArgoCD UI 경로
```
https://localhost:8080 → 좌측 메뉴: Settings (톱니바퀴) → Repositories
```

#### 단계

1. 좌측 **Settings (톱니바퀴 아이콘)** 클릭
2. **`Repositories`** 클릭
3. **`+ Connect Repo`** 클릭
4. 아래와 같이 입력:

| 항목 | 값 |
|------|------|
| Choose your connection method | `VIA HTTPS` |
| Type | `git` |
| Project | `default` |
| Repository URL | `https://github.com/{YOUR_ORG}/{YOUR_REPO}.git` |
| Username | GitHub 계정 (또는 비워둠, PAT 사용 시) |
| Password | GitHub Personal Access Token (repo 권한 필요) |

> **Private Repository인 경우**: GitHub Personal Access Token이 필요합니다.
>
> **PAT 생성 경로**: GitHub → Settings → Developer settings → Personal access tokens → Tokens (classic) → Generate new token
> - 필요 권한: `repo` (Full control)

5. **`Connect`** 클릭
6. 상태가 `Successful`로 표시되는지 확인

---

### 5.3 Application 배포 (3개 환경)

#### 방법 A: CLI로 배포 (권장)

```bash
# 네임스페이스 생성
kubectl create namespace urr-dev
kubectl create namespace urr-staging
kubectl create namespace urr-spring

# ArgoCD Application 배포
kubectl apply -f argocd/applications/urr-spring-dev.yaml
kubectl apply -f argocd/applications/urr-spring-staging.yaml
kubectl apply -f argocd/applications/urr-spring-prod.yaml
```

#### 방법 B: ArgoCD UI로 배포

```
ArgoCD UI → Applications → + NEW APP
```

각 환경별로:

**Dev 환경:**

| 항목 | 값 |
|------|------|
| Application Name | `urr-spring-dev` |
| Project Name | `default` |
| Sync Policy | `Automatic` |
| Auto-Prune | 체크 |
| Self Heal | 체크 |
| Repository URL | (5.2에서 연결한 리포지토리) |
| Revision | `main` |
| Path | `k8s/spring/overlays/dev` |
| Cluster URL | `https://kubernetes.default.svc` |
| Namespace | `urr-dev` |

**Staging 환경:**

| 항목 | 값 |
|------|------|
| Application Name | `urr-spring-staging` |
| Project Name | `default` |
| Sync Policy | `Automatic` |
| Auto-Prune | 체크 |
| Self Heal | 체크 |
| Path | `k8s/spring/overlays/staging` |
| Namespace | `urr-staging` |

**Production 환경:**

| 항목 | 값 |
|------|------|
| Application Name | `urr-spring-prod` |
| Project Name | `default` |
| Sync Policy | `Manual` (자동 동기화 비활성) |
| Path | `k8s/spring/overlays/prod` |
| Namespace | `urr-spring` |

각각 **`CREATE`** 클릭

---

## 6. Discord Webhook 설정

CI/CD 빌드 알림을 받을 Discord 채널에 Webhook을 생성합니다.

#### Discord 경로
```
Discord 서버 → 채널 설정(톱니바퀴) → 연동(Integrations) → 웹후크(Webhooks)
```

#### 단계

1. 알림을 받을 Discord 서버의 채널로 이동
2. 채널 이름 옆 **톱니바퀴(채널 편집)** 클릭
3. 좌측 메뉴에서 **`연동`** (Integrations) 클릭
4. **`웹후크`** (Webhooks) 클릭
5. **`새 웹후크`** (New Webhook) 클릭
6. 설정:
   - 이름: `CI/CD Bot` (자유)
   - 채널: 알림 받을 채널 선택
7. **`웹후크 URL 복사`** 클릭
8. 복사한 URL을 GitHub Secrets의 `DISCORD_WEBHOOK`에 등록 (3.1 참고)

---

## 7. 설정 검증 체크리스트

모든 설정을 마친 후 아래 항목을 순서대로 확인합니다.

### AWS 확인

```bash
# ECR 리포지토리 9개 존재 확인
aws ecr describe-repositories --region ap-northeast-2 \
  --query 'repositories[?starts_with(repositoryName, `urr/`)].repositoryName' \
  --output table

# IAM OIDC Provider 확인
aws iam list-open-id-connect-providers

# IAM Role 확인
aws iam get-role --role-name github-actions-urr-cicd \
  --query 'Role.Arn' --output text
```

### GitHub 확인

- [ ] Settings → Secrets → `AWS_ROLE_ARN` 등록됨
- [ ] Settings → Secrets → `AWS_ACCOUNT_ID` 등록됨
- [ ] Settings → Secrets → `DISCORD_WEBHOOK` 등록됨
- [ ] Settings → Variables → `STAGING_URL` 등록됨
- [ ] Settings → Environments → `staging` 생성됨
- [ ] Settings → Environments → `production` 생성됨 (Required reviewers 설정)
- [ ] Settings → Branches → `main` 보호 규칙 설정됨

### 코드 플레이스홀더

- [ ] `argocd/applications/*.yaml` → `YOUR_ORG/YOUR_REPO` 교체됨
- [ ] `k8s/spring/overlays/*/kustomization.yaml` → `CHANGE_ME` 교체됨

### ArgoCD 확인

- [ ] ArgoCD 설치 완료 + UI 접속 가능
- [ ] Argo Rollouts 설치 완료
- [ ] Git Repository 연결 성공 (Status: Successful)
- [ ] 3개 Application 생성됨 (dev, staging, prod)

### 파이프라인 동작 테스트

```
GitHub → Actions 탭 → 아무 서비스 워크플로우 → Run workflow
```

1. `Auth Service CI/CD` 워크플로우 선택
2. **`Run workflow`** 클릭 → Branch: `main`, Environment: `staging`
3. 워크플로우 실행 확인:
   - [x] Test job 통과
   - [x] Build & Push to ECR 성공
   - [x] Update Kustomize Manifests 성공
   - [x] Discord 알림 수신
4. ArgoCD UI에서 `urr-spring-staging` 앱이 자동 Sync되는지 확인

---

## 부록: 전체 설정 값 요약

| 구분 | 키 | 값 (예시) |
|------|------|-----------|
| AWS Region | - | `ap-northeast-2` |
| AWS Account ID | `AWS_ACCOUNT_ID` | `123456789012` |
| IAM Role ARN | `AWS_ROLE_ARN` | `arn:aws:iam::123456789012:role/github-actions-urr-cicd` |
| ECR Registry | - | `123456789012.dkr.ecr.ap-northeast-2.amazonaws.com` |
| GitHub Repo | - | `https://github.com/myorg/urr.git` |
| Staging URL | `STAGING_URL` | `https://staging.urr.guru` |
| Discord Webhook | `DISCORD_WEBHOOK` | `https://discord.com/api/webhooks/...` |
| K8s Namespace (Dev) | - | `urr-dev` |
| K8s Namespace (Staging) | - | `urr-staging` |
| K8s Namespace (Prod) | - | `urr-spring` |
