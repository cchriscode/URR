# 인프라 상세 분석 (K8s/Terraform/AWS/Lambda)

## 1) Kubernetes 배포 구조

### 1.1 Base 레이어
- Base kustomization에 백엔드 8개 서비스 + 프론트 + 네트워크 정책이 등록되어 있다.  
  출처: `k8s/spring/base/kustomization.yaml:5`, `k8s/spring/base/kustomization.yaml:23`
- Base Deployment 이미지는 모두 `YOUR_ECR_URI/...:latest` placeholder를 사용한다.  
  출처: `k8s/spring/base/gateway-service/deployment.yaml:24`, `k8s/spring/base/ticket-service/deployment.yaml:24`, `k8s/spring/base/payment-service/deployment.yaml:24`, `k8s/spring/base/stats-service/deployment.yaml:24`, `k8s/spring/base/auth-service/deployment.yaml:24`, `k8s/spring/base/queue-service/deployment.yaml:24`, `k8s/spring/base/catalog-service/deployment.yaml:24`, `k8s/spring/base/community-service/deployment.yaml:24`, `k8s/spring/base/frontend/deployment.yaml:23`

### 1.2 Kind 오버레이(개발)
- Kind 오버레이는 Postgres/Dragonfly/Kafka/Zipkin + Loki/Promtail/Grafana/Prometheus를 함께 띄운다.  
  출처: `k8s/spring/overlays/kind/kustomization.yaml:8`, `k8s/spring/overlays/kind/kustomization.yaml:17`
- Gateway/Frontend는 NodePort로 외부 노출한다.  
  출처: `k8s/spring/overlays/kind/patches/gateway-service-nodeport.yaml:10`, `k8s/spring/overlays/kind/patches/frontend-service-nodeport.yaml:10`
- Gateway는 kind patch에서 각 서비스 URL/Redis/Zipkin 환경변수를 주입받는다.  
  출처: `k8s/spring/overlays/kind/patches/gateway-service.yaml:21`, `k8s/spring/overlays/kind/patches/gateway-service.yaml:35`, `k8s/spring/overlays/kind/patches/gateway-service.yaml:39`
- Kind Kafka는 단일 replica + replication factor 1 설정이다.  
  출처: `k8s/spring/overlays/kind/kafka.yaml:8`, `k8s/spring/overlays/kind/kafka.yaml:39`, `k8s/spring/overlays/kind/kafka.yaml:41`

### 1.3 Prod 오버레이(운영)
- Prod는 base 위에 `pdb/hpa/kafka/redis` 리소스를 추가한다.  
  출처: `k8s/spring/overlays/prod/kustomization.yaml:6`, `k8s/spring/overlays/prod/kustomization.yaml:10`
- HPA가 gateway/ticket/queue/payment에 적용되어 autoscaling을 설정한다.  
  출처: `k8s/spring/overlays/prod/hpa.yaml:4`, `k8s/spring/overlays/prod/hpa.yaml:23`, `k8s/spring/overlays/prod/hpa.yaml:42`, `k8s/spring/overlays/prod/hpa.yaml:61`
- PDB가 핵심 서비스에 설정되어 자발적 중단 내성을 준다.  
  출처: `k8s/spring/overlays/prod/pdb.yaml:4`, `k8s/spring/overlays/prod/pdb.yaml:14`, `k8s/spring/overlays/prod/pdb.yaml:24`, `k8s/spring/overlays/prod/pdb.yaml:34`
- Prod Kafka는 StatefulSet 3 replica + RF3 + min ISR2로 설정되어 있다.  
  출처: `k8s/spring/overlays/prod/kafka.yaml:40`, `k8s/spring/overlays/prod/kafka.yaml:73`, `k8s/spring/overlays/prod/kafka.yaml:81`
- Prod Redis는 6노드(cluster-replicas 1) 구성을 명시한다.  
  출처: `k8s/spring/overlays/prod/redis.yaml:70`, `k8s/spring/overlays/prod/redis.yaml:201`

## 2) 네트워크 정책
- 기본 정책은 ingress/egress 전부 deny로 시작한다.  
  출처: `k8s/spring/base/network-policies.yaml:4`, `k8s/spring/base/network-policies.yaml:8`
- Gateway, frontend, backend 서비스별 ingress 허용 포트가 개별 선언되어 있다.  
  출처: `k8s/spring/base/network-policies.yaml:14`, `k8s/spring/base/network-policies.yaml:28`, `k8s/spring/base/network-policies.yaml:64`, `k8s/spring/base/network-policies.yaml:109`
- backend egress는 DNS 및 내부 대상 위주 허용 정책을 둔다.  
  출처: `k8s/spring/base/network-policies.yaml:182`, `k8s/spring/base/network-policies.yaml:196`

## 3) 구성값/시크릿 운영
- kind `config.env`는 내부 서비스 URL/Redis/Kafka/Zipkin/SQS 토글 등을 정의한다.  
  출처: `k8s/spring/overlays/kind/config.env:1`, `k8s/spring/overlays/kind/config.env:13`, `k8s/spring/overlays/kind/config.env:16`
- prod `config.env`에는 Kafka bootstrap이 2회 선언되어 키 중복이 존재한다.  
  출처: `k8s/spring/overlays/prod/config.env:15`, `k8s/spring/overlays/prod/config.env:22`

## 4) Terraform 인프라 구성

### 4.1 상태 저장/락
- prod 환경 terraform backend는 S3 state + DynamoDB lock + encrypt 설정을 사용한다.  
  출처: `terraform/environments/prod/main.tf:11`, `terraform/environments/prod/main.tf:15`, `terraform/environments/prod/main.tf:16`

### 4.2 구성 모듈
- prod 환경에서 현재 직접 조립하는 모듈은 `sqs`, `cloudfront`, `lambda_worker`다.  
  출처: `terraform/environments/prod/main.tf:50`, `terraform/environments/prod/main.tf:64`, `terraform/environments/prod/main.tf:87`
- CloudFront는 ALB origin 앞단에 두고 Lambda@Edge viewer-request 검증을 연결한다.  
  출처: `terraform/modules/cloudfront/main.tf:67`, `terraform/modules/cloudfront/main.tf:108`
- SQS는 FIFO + DLQ + CloudWatch alarm 구성이다.  
  출처: `terraform/modules/sqs/main.tf:6`, `terraform/modules/sqs/main.tf:19`, `terraform/modules/sqs/main.tf:97`
- Lambda worker는 SQS event source mapping으로 연결되어 배치 처리한다.  
  출처: `terraform/modules/lambda-worker/main.tf:119`, `terraform/modules/lambda-worker/main.tf:125`

### 4.3 네트워크/데이터 인프라 모듈(준비됨)
- VPC 모듈은 public/app/db/cache/streaming subnet 분할과 NAT 구성을 제공한다.  
  출처: `terraform/modules/vpc/main.tf:5`, `terraform/modules/vpc/main.tf:6`, `terraform/modules/vpc/main.tf:8`, `terraform/modules/vpc/main.tf:85`
- EKS 모듈은 private endpoint, secret encryption, OIDC(IRSA), managed addons 구성을 포함한다.  
  출처: `terraform/modules/eks/main.tf:105`, `terraform/modules/eks/main.tf:113`, `terraform/modules/eks/main.tf:133`, `terraform/modules/eks/main.tf:200`
- RDS 모듈은 encrypted storage + optional RDS Proxy를 구성한다.  
  출처: `terraform/modules/rds/main.tf:57`, `terraform/modules/rds/main.tf:146`
- ElastiCache 모듈은 transit/at-rest encryption을 켠 replication group 구성을 제공한다.  
  출처: `terraform/modules/elasticache/main.tf:88`, `terraform/modules/elasticache/main.tf:112`, `terraform/modules/elasticache/main.tf:113`
- Secrets 모듈은 DB/Redis/JWT/Queue entry secret을 Secrets Manager에 저장한다.  
  출처: `terraform/modules/secrets/main.tf:10`, `terraform/modules/secrets/main.tf:39`, `terraform/modules/secrets/main.tf:63`, `terraform/modules/secrets/main.tf:87`

## 5) Lambda 구성
- `edge-queue-check`는 queue entry token을 viewer-request 단계에서 검증한다.  
  출처: `lambda/edge-queue-check/index.js:2`, `lambda/edge-queue-check/index.js:93`
- 보호 경로와 우회 경로를 코드에서 명시한다.  
  출처: `lambda/edge-queue-check/index.js:28`, `lambda/edge-queue-check/index.js:40`
- `ticket-worker`는 SQS 메시지 액션을 해석해 ticket internal API를 호출한다.  
  출처: `lambda/ticket-worker/index.js:4`, `lambda/ticket-worker/index.js:63`, `lambda/ticket-worker/index.js:76`

## 6) 좋은 점
- dev(kind)와 prod 오버레이 분리가 잘 되어 환경별 운영전략을 분리했다.  
  출처: `k8s/spring/overlays/kind/kustomization.yaml:1`, `k8s/spring/overlays/prod/kustomization.yaml:1`
- prod Kafka/Redis 설정은 HA/내결함성을 의식한 값(RF3, min ISR2, Redis 6노드)을 사용한다.  
  출처: `k8s/spring/overlays/prod/kafka.yaml:73`, `k8s/spring/overlays/prod/kafka.yaml:81`, `k8s/spring/overlays/prod/redis.yaml:70`
- Terraform 모듈 분해가 비교적 잘 되어 있고(CloudFront/SQS/Lambda/RDS/VPC/EKS), 재사용성이 높다.  
  출처: `terraform/environments/prod/main.tf:50`, `terraform/environments/prod/main.tf:64`, `terraform/environments/prod/main.tf:87`, `terraform/modules/vpc/main.tf:20`, `terraform/modules/eks/main.tf:97`

## 7) 미흡한 점
- base 이미지가 placeholder 상태라 릴리즈 파이프라인 연계 전 수동오류 위험이 있다.  
  출처: `k8s/spring/base/gateway-service/deployment.yaml:24`
- prod config env의 중복 키(`KAFKA_BOOTSTRAP_SERVERS`)는 운영 오해를 유발한다.  
  출처: `k8s/spring/overlays/prod/config.env:15`, `k8s/spring/overlays/prod/config.env:22`
- kind grafana 기본 계정이 고정 문자열이라 내부망 외 환경에 그대로 쓰기 어렵다.  
  출처: `k8s/spring/overlays/kind/grafana.yaml:67`, `k8s/spring/overlays/kind/grafana.yaml:70`
- CloudFront custom header명이 gateway 기대값과 달라 우회검증 경로 정합성이 깨져 있다.  
  출처: `terraform/modules/cloudfront/main.tf:79`, `services-spring/gateway-service/src/main/java/com/tiketi/gatewayservice/filter/VwrEntryTokenFilter.java:32`

## 8) 개선 제안
1. Base 이미지 placeholder 제거: CI에서 digest pinning + Kustomize images 자동치환.
2. prod `config.env` 중복 키 정리 및 검증 스크립트 추가.
3. CloudFront->Gateway 헤더 계약(`name/value`)을 단일 사양으로 정리하고 통합 테스트 추가.
4. kind/dev 시크릿도 예제-실제 파일 분리 규칙을 강제(`secrets.env.example` + gitignore 정책).
