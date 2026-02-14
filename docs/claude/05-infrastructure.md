# 05. 인프라 분석

## 1. 쿠버네티스 구조

### 1.1 디렉토리 구조

URR 프로젝트는 Kustomize 기반의 쿠버네티스 매니페스트 구조를 사용하며, `k8s/spring/` 디렉토리 아래 `base`와 `overlays`로 분리되어 환경별 구성을 관리한다.

```
k8s/spring/
├── base/                          # 기본 매니페스트 (환경 공통)
│   ├── kustomization.yaml         # 리소스 목록 및 네임스페이스 정의
│   ├── network-policies.yaml      # 네트워크 정책
│   ├── gateway-service/           # deployment.yaml, service.yaml
│   ├── auth-service/
│   ├── ticket-service/
│   ├── payment-service/
│   ├── stats-service/
│   ├── queue-service/
│   ├── catalog-service/
│   ├── community-service/
│   └── frontend/
├── overlays/
│   ├── kind/                      # 로컬 개발 환경 (Kind 클러스터)
│   ├── dev/                       # 개발 환경
│   └── prod/                      # 프로덕션 환경
```

**base/kustomization.yaml** 파일은 모든 서비스의 Deployment와 Service 매니페스트, 그리고 네트워크 정책을 리소스로 선언한다 (`k8s/spring/base/kustomization.yaml:1-23`). 총 9개 서비스(gateway, ticket, payment, stats, auth, queue, catalog, community, frontend)의 `deployment.yaml`과 `service.yaml`을 포함하며, `network-policies.yaml`도 base 리소스에 속한다 (`k8s/spring/base/kustomization.yaml:4-23`).

**Kind overlay**는 base를 참조하면서 인프라 컴포넌트(PostgreSQL, Dragonfly, Kafka, Zipkin, Loki, Promtail, Grafana, Prometheus)를 추가 리소스로 선언한다 (`k8s/spring/overlays/kind/kustomization.yaml:5-17`). ConfigMap과 Secret은 `configMapGenerator`와 `secretGenerator`를 통해 `config.env`와 `secrets.env` 파일로부터 자동 생성되며, `disableNameSuffixHash: true` 설정으로 이름 해시 접미사를 비활성화한다 (`k8s/spring/overlays/kind/kustomization.yaml:19-30`). 이미지 태그는 `images` 섹션에서 `YOUR_ECR_URI/*` 플레이스홀더를 `urr-spring-*:local`로 재정의한다 (`k8s/spring/overlays/kind/kustomization.yaml:45-72`).

**Dev overlay**는 base만 참조하는 최소 구성이다 (`k8s/spring/overlays/dev/kustomization.yaml:1-6`).

**Prod overlay**는 base에 더해 `pdb.yaml`(PodDisruptionBudget), `hpa.yaml`(HorizontalPodAutoscaler), `kafka.yaml`(StatefulSet 3노드 Kafka), `redis.yaml`(StatefulSet 6노드 Redis Cluster)을 추가한다 (`k8s/spring/overlays/prod/kustomization.yaml:5-10`). `spring-prod-config`와 `spring-prod-secret`이라는 이름으로 ConfigMap/Secret을 생성한다 (`k8s/spring/overlays/prod/kustomization.yaml:12-23`).

### 1.2 네임스페이스

각 환경별로 고유한 네임스페이스를 사용한다.

| 환경 | 네임스페이스 | 정의 위치 |
|------|-------------|-----------|
| base (기본값) | `urr-dev` | `k8s/spring/base/kustomization.yaml:3` |
| Kind (로컬) | `urr-spring` | `k8s/spring/overlays/kind/kustomization.yaml:3` |
| dev | `urr-dev` | `k8s/spring/overlays/dev/kustomization.yaml:3` |
| prod | `urr-spring` | `k8s/spring/overlays/prod/kustomization.yaml:3` |

Kind 환경에서는 `namespace.yaml` 파일로 명시적으로 `urr-spring` 네임스페이스를 생성한다 (`k8s/spring/overlays/kind/namespace.yaml:1-4`).

### 1.3 서비스 매니페스트 구조

#### Deployment 공통 패턴

모든 백엔드 서비스 Deployment는 다음 공통 구조를 따른다.

**보안 컨텍스트**: Pod 레벨에서 `runAsNonRoot: true`, `runAsUser: 1000`, `fsGroup: 1000`으로 비루트 실행을 강제한다. 컨테이너 레벨에서는 `allowPrivilegeEscalation: false`와 `capabilities.drop: ALL`로 권한 상승을 차단한다 (`k8s/spring/base/gateway-service/deployment.yaml:18-31`).

**프로브 설정**: 모든 서비스는 `/health` 엔드포인트로 `readinessProbe`(initialDelaySeconds: 10, periodSeconds: 10)와 `livenessProbe`(initialDelaySeconds: 20, periodSeconds: 20)를 구성한다 (`k8s/spring/base/gateway-service/deployment.yaml:39-50`).

**리소스 제한**: 주요 서비스(gateway, ticket, auth, payment)는 CPU 200m/1코어, 메모리 256Mi/1Gi를 할당한다 (`k8s/spring/base/gateway-service/deployment.yaml:51-57`). 보조 서비스(queue, catalog, community)는 CPU 100m/500m, 메모리 256Mi/512Mi로 설정한다 (`k8s/spring/base/queue-service/deployment.yaml:52-57`).

**환경 변수**: base에서는 `PORT`와 `SPRING_PROFILES_ACTIVE=prod`만 설정하고, overlay 패치에서 환경별 구체적인 URL과 시크릿을 주입한다 (`k8s/spring/base/gateway-service/deployment.yaml:34-38`).

**이미지 참조**: `YOUR_ECR_URI/{service-name}:latest` 형태의 플레이스홀더를 사용하여 overlay의 `images` 섹션에서 환경별 이미지를 지정한다 (`k8s/spring/base/gateway-service/deployment.yaml:24`).

각 서비스의 Deployment 상세:

| 서비스 | 컨테이너 포트 | CPU (req/lim) | 메모리 (req/lim) | 정의 위치 |
|--------|-------------|--------------|-----------------|-----------|
| gateway-service | 3001 | 200m / 1 | 256Mi / 1Gi | `k8s/spring/base/gateway-service/deployment.yaml:33` |
| ticket-service | 3002 | 200m / 1 | 256Mi / 1Gi | `k8s/spring/base/ticket-service/deployment.yaml:33` |
| payment-service | 3003 | 200m / 1 | 256Mi / 1Gi | `k8s/spring/base/payment-service/deployment.yaml:33` |
| stats-service | 3004 | 200m / 1 | 256Mi / 1Gi | `k8s/spring/base/stats-service/deployment.yaml:33` |
| auth-service | 3005 | 200m / 1 | 256Mi / 1Gi | `k8s/spring/base/auth-service/deployment.yaml:33` |
| queue-service | 3007 | 100m / 500m | 256Mi / 512Mi | `k8s/spring/base/queue-service/deployment.yaml:33` |
| community-service | 3008 | 100m / 500m | 256Mi / 512Mi | `k8s/spring/base/community-service/deployment.yaml:33` |
| catalog-service | 3009 | 100m / 500m | 256Mi / 512Mi | `k8s/spring/base/catalog-service/deployment.yaml:33` |
| frontend | 3000 | 100m / 500m | 128Mi / 256Mi | `k8s/spring/base/frontend/deployment.yaml:32` |

#### Service 공통 패턴

base의 Service 리소스는 기본적으로 ClusterIP 타입(type 미지정 시 기본값)으로 생성된다 (`k8s/spring/base/gateway-service/service.yaml:1-14`). Kind 환경에서는 gateway-service와 frontend-service에 NodePort 패치를 적용한다.

- **gateway-service**: NodePort 30000, hostPort 3001로 매핑 (`k8s/spring/overlays/kind/patches/gateway-service-nodeport.yaml:6-11`)
- **frontend-service**: NodePort 30005, hostPort 3000으로 매핑 (`k8s/spring/overlays/kind/patches/frontend-service-nodeport.yaml:6-11`)

#### Kind 패치: initContainers

Kind 환경에서 ticket-service 등은 `initContainers`를 통해 PostgreSQL, Redis(Dragonfly), Kafka의 준비 상태를 기다린 후에 메인 컨테이너가 시작된다 (`k8s/spring/overlays/kind/patches/ticket-service.yaml:8-26`). `busybox:1.36` 이미지로 `nc -z` 명령을 사용하여 각 인프라 컴포넌트의 포트 가용성을 확인한다.

### 1.4 네트워크 정책

`network-policies.yaml`에서 네임스페이스 전체에 대한 기본 차단(Default Deny) 정책과 서비스별 허용 규칙을 정의한다.

**기본 차단 정책**: `default-deny-all` 정책으로 모든 Pod에 대해 Ingress와 Egress를 차단한다 (`k8s/spring/base/network-policies.yaml:1-9`).

**Ingress 허용 규칙**:

| 정책 이름 | 대상 서비스 | 허용 출처 | 포트 | 정의 위치 |
|-----------|-----------|----------|------|-----------|
| `allow-gateway-ingress` | gateway-service | 모든 출처 (외부 진입점) | 3001 | `k8s/spring/base/network-policies.yaml:11-23` |
| `allow-frontend-ingress` | frontend | 모든 출처 | 3000 | `k8s/spring/base/network-policies.yaml:25-37` |
| `allow-auth-service-ingress` | auth-service | gateway-service, catalog-service | 3005 | `k8s/spring/base/network-policies.yaml:40-59` |
| `allow-ticket-service-ingress` | ticket-service | gateway-service, payment-service, catalog-service | 3002 | `k8s/spring/base/network-policies.yaml:61-83` |
| `allow-catalog-service-ingress` | catalog-service | gateway-service, queue-service | 3009 | `k8s/spring/base/network-policies.yaml:85-104` |
| `allow-payment-service-ingress` | payment-service | gateway-service | 3003 | `k8s/spring/base/network-policies.yaml:106-122` |
| `allow-stats-service-ingress` | stats-service | gateway-service | 3004 | `k8s/spring/base/network-policies.yaml:124-140` |
| `allow-queue-service-ingress` | queue-service | gateway-service | 3007 | `k8s/spring/base/network-policies.yaml:142-158` |
| `allow-community-service-ingress` | community-service | gateway-service | 3008 | `k8s/spring/base/network-policies.yaml:160-176` |

**Egress 허용 규칙**:

- **`allow-backend-egress`**: `tier: backend` 라벨을 가진 모든 백엔드 Pod에 대해 네임스페이스 내 모든 Pod 간 통신과 DNS 조회(포트 53 UDP/TCP)를 허용한다 (`k8s/spring/base/network-policies.yaml:178-201`).
- **`allow-gateway-to-services`**: gateway-service에서 `tier: backend` 라벨이 있는 Pod과 DNS 서비스로의 Egress를 허용한다 (`k8s/spring/base/network-policies.yaml:203-226`).

---

## 2. Docker 설정

### 2.1 백엔드 서비스 Dockerfile

모든 Spring Boot 백엔드 서비스는 동일한 멀티스테이지 빌드 구조를 사용한다.

**빌드 스테이지** (`build`):
- 기반 이미지: `eclipse-temurin:21-jdk` (Java 21 JDK) (`services-spring/gateway-service/Dockerfile:1`)
- 작업 디렉토리: `/workspace` (`services-spring/gateway-service/Dockerfile:2`)
- Gradle Wrapper 파일을 복사한 후 `./gradlew --no-daemon clean bootJar` 명령으로 실행 가능 JAR를 빌드한다 (`services-spring/gateway-service/Dockerfile:11`)

**런타임 스테이지**:
- 기반 이미지: `eclipse-temurin:21-jre` (JRE만 포함하여 이미지 크기 최소화) (`services-spring/gateway-service/Dockerfile:13`)
- 작업 디렉토리: `/app` (`services-spring/gateway-service/Dockerfile:14`)
- 빌드 아티팩트 복사: `build/libs/*.jar`를 `app.jar`로 복사한다 (`services-spring/gateway-service/Dockerfile:16`)
- 비루트 유저 생성 및 전환: `app` 그룹(GID 1001)과 `app` 유저(UID 1001)를 생성하고 `USER app`으로 전환한다 (`services-spring/gateway-service/Dockerfile:18-19`)
- 엔트리포인트: `java -jar /app/app.jar` (`services-spring/gateway-service/Dockerfile:22`)

서비스별 차이점은 `EXPOSE` 포트뿐이다.

| 서비스 | EXPOSE 포트 | 정의 위치 |
|--------|-----------|-----------|
| gateway-service | 3001 | `services-spring/gateway-service/Dockerfile:21` |
| ticket-service | 3002 | `services-spring/ticket-service/Dockerfile:21` |

모든 백엔드 서비스의 Dockerfile은 위 구조와 동일하며, 포트 번호만 각 서비스에 맞게 다르다.

### 2.2 프론트엔드 Dockerfile

프론트엔드(Next.js) 애플리케이션도 멀티스테이지 빌드를 사용한다.

**빌드 스테이지** (`builder`):
- 기반 이미지: `node:20-alpine` (`apps/web/Dockerfile:2`)
- 빌드 인자: `NEXT_PUBLIC_API_URL`을 `ARG`로 선언하며, 기본값은 `http://localhost:3001`이다 (`apps/web/Dockerfile:6`)
- 빌드 시 `NEXT_PUBLIC_API_URL` 환경 변수가 Next.js 번들에 주입된다 (`apps/web/Dockerfile:8`)
- `npm ci`로 의존성을 설치하고, `npm run build`로 프로덕션 빌드를 수행한다 (`apps/web/Dockerfile:11-14`)

**런타임 스테이지** (`runner`):
- 기반 이미지: `node:20-alpine` (`apps/web/Dockerfile:17`)
- `NODE_ENV=production`으로 설정한다 (`apps/web/Dockerfile:21`)
- 빌드 결과물(`package*.json`, `.next/`, `public/`, `node_modules/`)을 복사한다 (`apps/web/Dockerfile:23-26`)
- 비루트 유저: `app` 그룹(GID 1001)과 `app` 유저(UID 1001)를 생성하고 `/app` 디렉토리의 소유권을 변경한 후 전환한다 (`apps/web/Dockerfile:28-30`)
- 포트 3000을 노출하고, `npm run start`로 실행한다 (`apps/web/Dockerfile:32-34`)

---

## 3. Kind 클러스터 (로컬 개발)

### 3.1 클러스터 설정

Kind 클러스터는 `kind-config.yaml` 파일로 구성된다.

- **클러스터 이름**: `urr-local` (`kind-config.yaml:3`)
- **노드 구성**: control-plane 1개 + worker 2개 총 3노드 (`kind-config.yaml:4-34`)
  - control-plane 노드에 `ingress-ready=true` 라벨을 설정한다 (`kind-config.yaml:11`)
  - worker 노드는 `workload: application`과 `workload: data` 라벨로 분류한다 (`kind-config.yaml:30-34`)

**NodePort 매핑 (extraPortMappings)**:

| 용도 | containerPort (NodePort) | hostPort | 정의 위치 |
|------|-------------------------|----------|-----------|
| Backend API (Gateway) | 30000 | 3001 | `kind-config.yaml:14-16` |
| Frontend | 30005 | 3000 | `kind-config.yaml:18-20` |
| Grafana Dashboard | 30006 | 3006 | `kind-config.yaml:22-24` |
| PostgreSQL (디버깅용) | 30432 | 15432 | `kind-config.yaml:26-28` |

### 3.2 자동화 스크립트

모든 스크립트는 `scripts/` 디렉토리에 Bash(`.sh`)와 PowerShell(`.ps1`) 버전으로 제공된다.

#### spring-kind-dev.sh -- 전체 스택 배포 (원스텝)

최상위 진입점 스크립트로, `spring-kind-up.sh`를 호출하여 클러스터 생성부터 배포까지 전체 과정을 실행한다 (`scripts/spring-kind-dev.sh:29`). `--recreate-cluster`와 `--skip-build` 옵션을 지원한다 (`scripts/spring-kind-dev.sh:16-21`). 완료 후 Frontend(3000), Gateway(3001), Grafana(3006) 접근 URL을 출력한다 (`scripts/spring-kind-dev.sh:36-38`).

#### spring-kind-up.sh -- 클러스터 생성 + 빌드 + 배포

1. **선행 조건 검사**: `kind`, `kubectl`, `docker` CLI 존재 여부와 Docker 데몬 실행 상태를 확인한다 (`scripts/spring-kind-up.sh:26-30`)
2. **클러스터 생성/재생성**: `kind create cluster --name urr-local --config kind-config.yaml`로 클러스터를 생성한다. `--recreate-cluster` 옵션 시 기존 클러스터를 삭제 후 재생성한다 (`scripts/spring-kind-up.sh:39-48`)
3. **이미지 빌드 및 로드**: `--skip-build`가 아니면 `spring-kind-build-load.sh`를 호출한다 (`scripts/spring-kind-up.sh:54-56`)
4. **Kustomize 배포**: `kubectl apply -k k8s/spring/overlays/kind`로 전체 매니페스트를 적용한다 (`scripts/spring-kind-up.sh:61`)
5. **롤아웃 대기**: 14개 Deployment(인프라 포함)의 `kubectl rollout status`를 순차적으로 확인한다. 타임아웃은 300초이다 (`scripts/spring-kind-up.sh:66-86`)

#### spring-kind-build-load.sh -- 이미지 빌드 + Kind 로드

8개 백엔드 서비스를 순차적으로 `docker build` 후 `kind load docker-image`로 Kind 클러스터에 로드한다 (`scripts/spring-kind-build-load.sh:30-54`). 이미지 이름은 `urr-spring-{service-name}:local` 형식이다 (`scripts/spring-kind-build-load.sh:31-39`).

프론트엔드는 별도로 빌드하며, `--build-arg NEXT_PUBLIC_API_URL=http://localhost:3001`을 전달한다 (`scripts/spring-kind-build-load.sh:65-67`).

#### spring-kind-down.sh -- 네임스페이스/클러스터 삭제

기본적으로 `urr-spring` 네임스페이스만 삭제한다 (`scripts/spring-kind-down.sh:20-21`). `--delete-cluster` 옵션을 사용하면 Kind 클러스터 전체를 삭제한다 (`scripts/spring-kind-down.sh:14-17`).

#### spring-kind-smoke.sh -- 헬스체크

`curl`로 3개의 핵심 엔드포인트를 검증한다 (`scripts/spring-kind-smoke.sh:24-26`):
- `http://localhost:3001/health` -- 200 응답 확인
- `http://localhost:3001/api/auth/me` -- 401 응답 확인 (인증 필요)
- `http://localhost:3000` -- 200 응답 확인 (프론트엔드)

#### start-port-forwards.sh -- kubectl 포트포워딩

8개 서비스에 대해 `kubectl port-forward`를 백그라운드로 실행한다 (`scripts/start-port-forwards.sh:72-81`). 포트 사용 가능 여부를 사전 검사하고 (`scripts/start-port-forwards.sh:45-64`), 포워딩 시작 후 각 서비스의 `/health` 엔드포인트로 헬스 체크를 수행한다 (`scripts/start-port-forwards.sh:109-123`).

포트포워딩 대상:

| 서비스 | 로컬 포트 | 정의 위치 |
|--------|----------|-----------|
| Gateway | 3001 | `scripts/start-port-forwards.sh:73` |
| Auth | 3005 | `scripts/start-port-forwards.sh:74` |
| Ticket | 3002 | `scripts/start-port-forwards.sh:75` |
| Payment | 3003 | `scripts/start-port-forwards.sh:76` |
| Stats | 3004 | `scripts/start-port-forwards.sh:77` |
| Queue | 3007 | `scripts/start-port-forwards.sh:78` |
| Community | 3008 | `scripts/start-port-forwards.sh:79` |
| Frontend | 3000 | `scripts/start-port-forwards.sh:80` |

### 3.3 인프라 컴포넌트 (Kind 내)

Kind 환경에서는 모든 인프라 컴포넌트가 동일 네임스페이스(`urr-spring`) 내에 Pod으로 배포된다.

#### PostgreSQL

- **이미지**: `postgres:15-alpine` (`k8s/spring/overlays/kind/postgres.yaml:31`)
- **서비스 이름**: `postgres-spring` (`k8s/spring/overlays/kind/postgres.yaml:79`)
- **포트**: 5432 (NodePort 30432) (`k8s/spring/overlays/kind/postgres.yaml:87-90`)
- **초기화 데이터베이스**: ConfigMap `postgres-init-spring`을 통해 5개 DB를 생성한다: `auth_db`, `ticket_db`, `payment_db`, `stats_db`, `community_db` (`k8s/spring/overlays/kind/postgres.yaml:6-11`)
- **인증 정보**: `spring-kind-secret`의 `POSTGRES_USER`와 `POSTGRES_PASSWORD`를 참조한다 (`k8s/spring/overlays/kind/postgres.yaml:35-44`)
- **영구 볼륨**: `postgres-pvc`(5Gi)에 데이터를 저장한다 (`k8s/spring/overlays/kind/pvc.yaml:5-12`)
- **프로브**: `pg_isready` 명령으로 readiness(5초 간격)와 liveness(10초 간격)를 검사한다 (`k8s/spring/overlays/kind/postgres.yaml:52-67`)

#### Redis (Dragonfly)

- **이미지**: `docker.dragonflydb.io/dragonflydb/dragonfly:latest` (`k8s/spring/overlays/kind/dragonfly.yaml:19`)
- **서비스 이름**: `dragonfly-spring` (`k8s/spring/overlays/kind/dragonfly.yaml:53`)
- **포트**: 6379 (ClusterIP) (`k8s/spring/overlays/kind/dragonfly.yaml:61-62`)
- **설정 인자**: `--maxmemory=512mb`, `--proactor_threads=1`, 1분마다 스냅샷 저장 (`k8s/spring/overlays/kind/dragonfly.yaml:21-25`)
- **영구 볼륨**: `dragonfly-pvc`(1Gi) (`k8s/spring/overlays/kind/pvc.yaml:18-25`)
- **프로브**: `redis-cli ping`으로 readiness(5초 간격)와 liveness(10초 간격)를 검사한다 (`k8s/spring/overlays/kind/dragonfly.yaml:31-43`)

#### Kafka

- **이미지**: `apache/kafka:3.7.0` (`k8s/spring/overlays/kind/kafka.yaml:19`)
- **서비스 이름**: `kafka-spring` (`k8s/spring/overlays/kind/kafka.yaml:75`)
- **포트**: 9092(client) + 9093(controller) (`k8s/spring/overlays/kind/kafka.yaml:21-22`)
- **모드**: KRaft 모드(Zookeeper 미사용). `KAFKA_PROCESS_ROLES=broker,controller`로 단일 노드에서 두 역할을 수행한다 (`k8s/spring/overlays/kind/kafka.yaml:27`)
- **리소스**: CPU 200m/1코어, 메모리 512Mi/1Gi (`k8s/spring/overlays/kind/kafka.yaml:48-54`)
- **프로브**: TCP 소켓(9092) 기반. startupProbe(failureThreshold: 15), readinessProbe, livenessProbe 구성 (`k8s/spring/overlays/kind/kafka.yaml:55-70`)
- **복제 팩터**: 로컬 환경이므로 모두 1로 설정한다 (`k8s/spring/overlays/kind/kafka.yaml:38-43`)

#### Zipkin

- **이미지**: `openzipkin/zipkin:3` (`k8s/spring/overlays/kind/zipkin.yaml:17`)
- **서비스 이름**: `zipkin-spring` (`k8s/spring/overlays/kind/zipkin.yaml:32`)
- **포트**: 9411 (NodePort 30411) (`k8s/spring/overlays/kind/zipkin.yaml:37-39`)
- **리소스**: CPU 100m/500m, 메모리 256Mi/512Mi (`k8s/spring/overlays/kind/zipkin.yaml:20-26`)

#### Prometheus

- **이미지**: `prom/prometheus:v2.51.0` (`k8s/spring/overlays/kind/prometheus.yaml:68`)
- **서비스 이름**: `prometheus-service` (`k8s/spring/overlays/kind/prometheus.yaml:111`)
- **포트**: 9090 (NodePort 30090) (`k8s/spring/overlays/kind/prometheus.yaml:119-121`)
- **스크래핑 설정**: 8개 백엔드 서비스의 `/actuator/prometheus` 엔드포인트를 10초 간격으로 스크래핑한다 (`k8s/spring/overlays/kind/prometheus.yaml:11-47`)
- **데이터 보존**: 7일간 보관 (`k8s/spring/overlays/kind/prometheus.yaml:71`)
- **영구 볼륨**: `prometheus-pvc`(2Gi) (`k8s/spring/overlays/kind/pvc.yaml:57-64`)
- **프로브**: HTTP `/-/healthy`(liveness), `/-/ready`(readiness) (`k8s/spring/overlays/kind/prometheus.yaml:87-98`)

#### Grafana

- **이미지**: `grafana/grafana:10.2.3` (`k8s/spring/overlays/kind/grafana.yaml:62`)
- **서비스 이름**: `grafana-service` (`k8s/spring/overlays/kind/grafana.yaml:121`)
- **포트**: 3006 (NodePort 30006) (`k8s/spring/overlays/kind/grafana.yaml:129-131`)
- **기본 인증**: admin/admin (`k8s/spring/overlays/kind/grafana.yaml:68-70`)
- **데이터 소스**: Prometheus(`http://prometheus-service:9090`, 기본)와 Loki(`http://loki-service:3100`)를 자동 프로비저닝한다 (`k8s/spring/overlays/kind/grafana.yaml:7-21`)
- **대시보드**: `grafana-dashboards` ConfigMap에서 `/var/lib/grafana/dashboards`로 마운트한다 (`k8s/spring/overlays/kind/grafana.yaml:82-83`)
- **영구 볼륨**: `grafana-pvc`(1Gi) (`k8s/spring/overlays/kind/pvc.yaml:31-38`)

#### Loki

- **이미지**: `grafana/loki:2.9.3` (`k8s/spring/overlays/kind/loki.yaml:63`)
- **서비스 이름**: `loki-service` (`k8s/spring/overlays/kind/loki.yaml:107`)
- **포트**: 3100(HTTP) + 9096(gRPC), ClusterIP 타입 (`k8s/spring/overlays/kind/loki.yaml:111-122`)
- **스토리지**: 파일시스템 기반. `boltdb-shipper` 인덱스, 24시간 캐시 TTL (`k8s/spring/overlays/kind/loki.yaml:25-42`)
- **영구 볼륨**: `loki-pvc`(2Gi) (`k8s/spring/overlays/kind/pvc.yaml:44-51`)

#### Promtail

- **타입**: DaemonSet (모든 노드에 1개씩 배포) (`k8s/spring/overlays/kind/promtail.yaml:35`)
- **이미지**: `grafana/promtail:2.9.3` (`k8s/spring/overlays/kind/promtail.yaml:52`)
- **Loki 연동**: `http://loki-service:3100/loki/api/v1/push`로 로그를 전송한다 (`k8s/spring/overlays/kind/promtail.yaml:16`)
- **스크래핑 대상**: `urr-spring` 네임스페이스의 모든 Pod. `app`, `pod`, `namespace` 라벨을 릴레이블링한다 (`k8s/spring/overlays/kind/promtail.yaml:18-31`)
- **호스트 볼륨 마운트**: `/var/log`와 `/var/lib/docker/containers`(읽기 전용)를 마운트한다 (`k8s/spring/overlays/kind/promtail.yaml:58-62`)
- **RBAC**: ServiceAccount, ClusterRole(nodes, pods, services 등에 대한 get/watch/list), ClusterRoleBinding을 구성한다 (`k8s/spring/overlays/kind/promtail.yaml:85-117`)
- **리소스**: CPU 50m/200m, 메모리 128Mi/256Mi (`k8s/spring/overlays/kind/promtail.yaml:67-72`)

#### PersistentVolumeClaim 요약

| PVC 이름 | 크기 | StorageClass | 정의 위치 |
|----------|------|-------------|-----------|
| postgres-pvc | 5Gi | standard | `k8s/spring/overlays/kind/pvc.yaml:5-12` |
| dragonfly-pvc | 1Gi | standard | `k8s/spring/overlays/kind/pvc.yaml:18-25` |
| grafana-pvc | 1Gi | standard | `k8s/spring/overlays/kind/pvc.yaml:31-38` |
| loki-pvc | 2Gi | standard | `k8s/spring/overlays/kind/pvc.yaml:44-51` |
| prometheus-pvc | 2Gi | standard | `k8s/spring/overlays/kind/pvc.yaml:57-64` |

---

## 4. AWS 연결 설계

### 4.1 컴퓨팅: Amazon EKS

현재 로컬 Kind 클러스터에서 운영되는 워크로드는 Amazon EKS로 마이그레이션이 가능하도록 설계되어 있다. Kustomize 기반의 overlay 패턴을 사용하므로, prod overlay에서 이미지 레지스트리(ECR)와 환경 변수만 변경하면 EKS에 배포할 수 있다.

**프로덕션 환경 준비 상태**:
- **레플리카 확장**: prod overlay의 `replicas.yaml`에서 gateway-service(3), ticket-service(3), queue-service(3), payment-service(2), auth-service(2), stats-service(2), community-service(2), frontend(2)로 설정되어 있다 (`k8s/spring/overlays/prod/patches/replicas.yaml:1-55`)
- **HPA**: gateway-service(3-10), ticket-service(3-10), queue-service(3-8), payment-service(2-6)에 CPU 70% 기준 오토스케일링이 구성되어 있다 (`k8s/spring/overlays/prod/hpa.yaml:1-76`)
- **PDB**: 모든 백엔드 서비스에 `minAvailable: 1`로 PodDisruptionBudget이 설정되어 있다 (`k8s/spring/overlays/prod/pdb.yaml:1-70`)
- **이미지 참조**: base 매니페스트의 `YOUR_ECR_URI` 플레이스홀더를 ECR URI로 교체한다 (`k8s/spring/base/gateway-service/deployment.yaml:24`)

### 4.2 데이터베이스: Amazon RDS (PostgreSQL)

Kind 환경에서는 단일 PostgreSQL 인스턴스 내에 5개 데이터베이스를 생성한다 (`k8s/spring/overlays/kind/postgres.yaml:7-11`). 프로덕션에서는 Amazon RDS for PostgreSQL로 대체하며, 서비스별 독립 DB 또는 단일 인스턴스 내 스키마 분리 방식을 선택할 수 있다.

**프로덕션 DB URL 설정**: prod overlay의 `config.env`에서 각 서비스별 RDS 엔드포인트를 지정한다 (`k8s/spring/overlays/prod/config.env:1-5`):
- `AUTH_DB_URL=jdbc:postgresql://CHANGE_ME_RDS_ENDPOINT:5432/auth_db`
- `TICKET_DB_URL=jdbc:postgresql://CHANGE_ME_RDS_ENDPOINT:5432/ticket_db`
- `PAYMENT_DB_URL=jdbc:postgresql://CHANGE_ME_RDS_ENDPOINT:5432/payment_db`
- `STATS_DB_URL=jdbc:postgresql://CHANGE_ME_RDS_ENDPOINT:5432/stats_db`
- `COMMUNITY_DB_URL=jdbc:postgresql://CHANGE_ME_RDS_ENDPOINT:5432/community_db`

prod 패치에서는 각 서비스의 `*_DB_URL`을 ConfigMap에서 주입한다. 예를 들어 ticket-service는 `spring-prod-config`의 `TICKET_DB_URL` 키를 참조한다 (`k8s/spring/overlays/prod/patches/services-env.yaml:97-101`).

### 4.3 캐시: Amazon ElastiCache (Redis)

현재 Kind 환경에서는 Dragonfly(`dragonfly-spring:6379`)를 Redis 호환 캐시로 사용한다 (`k8s/spring/overlays/kind/dragonfly.yaml:53`). 프로덕션에서는 Amazon ElastiCache (Redis)로 마이그레이션한다.

**프로덕션 Redis 구성**: prod overlay에서는 Redis Cluster 모드의 StatefulSet(6노드, 3 마스터 + 3 레플리카)을 자체 배포한다 (`k8s/spring/overlays/prod/redis.yaml:62-151`).
- **이미지**: `redis:7.2-alpine` (`k8s/spring/overlays/prod/redis.yaml:95`)
- **클러스터 설정**: `cluster-enabled yes`, `maxmemory 200mb`, `appendonly yes` (`k8s/spring/overlays/prod/redis.yaml:7-23`)
- **Pod Anti-Affinity**: 같은 호스트에 Redis Pod이 겹치지 않도록 `preferredDuringSchedulingIgnoredDuringExecution`을 설정한다 (`k8s/spring/overlays/prod/redis.yaml:80-91`)
- **클러스터 초기화**: Job `redis-cluster-init`이 6개 Pod이 모두 준비된 후 `redis-cli --cluster create --cluster-replicas 1`로 클러스터를 구성한다 (`k8s/spring/overlays/prod/redis.yaml:154-207`)
- **영구 볼륨**: `volumeClaimTemplates`로 Pod당 1Gi PVC를 자동 생성한다 (`k8s/spring/overlays/prod/redis.yaml:143-151`)

ElastiCache로 마이그레이션 시 Transit Encryption(TLS) 지원이 필요하며, `REDIS_PASSWORD`와 `REDIS_SSL=true` 환경 변수를 추가한다 (`k8s/AWS_SECRETS_INTEGRATION.md:149-156`).

### 4.4 메시징: Amazon SQS FIFO

queue-service는 AWS SQS FIFO 큐를 사용하여 입장 허가(admission) 이벤트를 발행한다.

**의존성**: `software.amazon.awssdk:sqs:2.29.0`과 `software.amazon.awssdk:sts:2.29.0`을 사용한다 (`services-spring/queue-service/build.gradle:28-29`).

**SqsPublisher 구현** (`services-spring/queue-service/src/main/java/guru/urr/queueservice/service/SqsPublisher.java`):
- SQS 활성화 여부는 `aws.sqs.enabled` 속성과 `SqsClient` null 체크, `queue-url` 비어있지 않은지 여부로 결정한다 (`services-spring/queue-service/src/main/java/guru/urr/queueservice/service/SqsPublisher.java:31`)
- `publishAdmission()` 메서드에서 FIFO 큐 전용 파라미터를 설정한다:
  - `messageGroupId`: `eventId.toString()` -- 같은 이벤트의 입장 메시지가 순서를 보장한다 (`services-spring/queue-service/src/main/java/guru/urr/queueservice/service/SqsPublisher.java:59`)
  - `messageDeduplicationId`: `userId + ":" + eventId` -- 5분 중복 제거 창 내에서 동일 사용자의 중복 입장을 방지한다 (`services-spring/queue-service/src/main/java/guru/urr/queueservice/service/SqsPublisher.java:54,60`)
- 메시지 본문은 `action`, `eventId`, `userId`, `entryToken`, `timestamp`를 포함하는 JSON이다 (`services-spring/queue-service/src/main/java/guru/urr/queueservice/service/SqsPublisher.java:44-50`)
- SQS 발행 실패 시 로그를 남기고 Redis-only 모드로 폴백한다 (`services-spring/queue-service/src/main/java/guru/urr/queueservice/service/SqsPublisher.java:64-67`)

**환경 설정**:
- Kind 환경: `SQS_ENABLED=false` (`k8s/spring/overlays/kind/config.env:16`)
- Prod 환경: `SQS_ENABLED=true`, `SQS_QUEUE_URL=CHANGE_ME_IN_PRODUCTION` (`k8s/spring/overlays/prod/config.env:18-19`)
- prod 패치에서 queue-service에 `SQS_ENABLED`과 `SQS_QUEUE_URL`을 ConfigMap으로 주입한다 (`k8s/spring/overlays/prod/patches/services-env.yaml:200-209`)

### 4.5 메시징: Amazon MSK (Kafka)

Kafka는 서비스 간 비동기 이벤트 통신의 핵심 인프라이다.

**토픽 구성**: `KafkaConfig` 클래스에서 4개 토픽을 선언한다 (`services-spring/ticket-service/src/main/java/guru/urr/ticketservice/shared/config/KafkaConfig.java:10-34`):

| 토픽 이름 | 파티션 수 | 용도 | 정의 위치 |
|-----------|----------|------|-----------|
| `payment-events` | 3 | 결제 이벤트 | `services-spring/ticket-service/src/main/java/guru/urr/ticketservice/shared/config/KafkaConfig.java:16-18` |
| `reservation-events` | 3 | 예약 생성/확인/취소 | `services-spring/ticket-service/src/main/java/guru/urr/ticketservice/shared/config/KafkaConfig.java:21-23` |
| `transfer-events` | 3 | 티켓 양도 | `services-spring/ticket-service/src/main/java/guru/urr/ticketservice/shared/config/KafkaConfig.java:26-28` |
| `membership-events` | 3 | 멤버십 활성화 | `services-spring/ticket-service/src/main/java/guru/urr/ticketservice/shared/config/KafkaConfig.java:31-33` |

복제 팩터는 `kafka.topic.replication-factor` 속성으로 제어한다 (`services-spring/ticket-service/src/main/java/guru/urr/ticketservice/shared/config/KafkaConfig.java:12-13`). Kind 환경에서는 1, prod 환경에서는 3을 사용한다 (`k8s/spring/overlays/kind/config.env:21`, `k8s/spring/overlays/prod/config.env:21`).

**이벤트 발행**: `TicketEventProducer`가 예약, 결제, 양도, 멤버십 관련 이벤트를 Kafka 토픽에 비동기로 발행한다 (`services-spring/ticket-service/src/main/java/guru/urr/ticketservice/messaging/TicketEventProducer.java:14-78`).

**Kind 환경**: 단일 브로커 Deployment (`k8s/spring/overlays/kind/kafka.yaml:1-84`)
- KRaft 모드(Zookeeper 미사용), `KAFKA_PROCESS_ROLES=broker,controller` (`k8s/spring/overlays/kind/kafka.yaml:27`)
- 부트스트랩 서버: `kafka-spring:9092` (`k8s/spring/overlays/kind/config.env:13`)

**Prod 환경**: 3노드 StatefulSet (`k8s/spring/overlays/prod/kafka.yaml:32-131`)
- KRaft 모드 3노드 클러스터, `KAFKA_CONTROLLER_QUORUM_VOTERS`에 3개 노드가 등록된다 (`k8s/spring/overlays/prod/kafka.yaml:72`)
- 기본 복제 팩터: 3, `min.insync.replicas`: 2 (`k8s/spring/overlays/prod/kafka.yaml:79-82`)
- 기본 파티션: 6, Pod당 20Gi 영구 볼륨 (`k8s/spring/overlays/prod/kafka.yaml:86,130`)
- 리소스: CPU 500m/2코어, 메모리 1Gi/2Gi (`k8s/spring/overlays/prod/kafka.yaml:98-103`)
- 부트스트랩 서버: `kafka-spring-0.kafka-spring-headless:9092,kafka-spring-1.kafka-spring-headless:9092,kafka-spring-2.kafka-spring-headless:9092` (`k8s/spring/overlays/prod/config.env:22`)

MSK 마이그레이션 시 StatefulSet을 제거하고 부트스트랩 서버 주소를 MSK 엔드포인트로 변경하면 된다.

### 4.6 스토리지: Amazon S3

catalog-service는 이벤트 이미지를 S3에 저장하기 위해 AWS SDK S3 의존성을 포함한다.

**의존성**: `software.amazon.awssdk:s3:2.31.68` (`services-spring/catalog-service/build.gradle:34`)

**환경 변수**:
- Kind 환경: `AWS_S3_BUCKET=local-mock-bucket`, `AWS_REGION=ap-northeast-2` (`k8s/spring/overlays/kind/config.env:10-11`)
- Prod 환경: `AWS_S3_BUCKET`은 시크릿으로 관리한다 (`k8s/spring/overlays/prod/secrets.env.example:17`)

### 4.7 CDN: Amazon CloudFront

프론트엔드 정적 자산 배포 및 API 요청에 대한 CloudFront CDN을 사용한다. Lambda@Edge에서 VWR(Virtual Waiting Room) 토큰 검증을 수행하고, 검증 완료 시 `X-CloudFront-Verified` 헤더를 주입한다.

**VwrEntryTokenFilter CloudFront 바이패스 로직** (`services-spring/gateway-service/src/main/java/guru/urr/gatewayservice/filter/VwrEntryTokenFilter.java`):
- `cloudfront.secret` 설정값을 가져와 CloudFront 시크릿으로 저장한다 (`services-spring/gateway-service/src/main/java/guru/urr/gatewayservice/filter/VwrEntryTokenFilter.java:40-43`)
- 요청에 `X-CloudFront-Verified` 헤더가 포함되어 있고, 해당 값이 `cloudfront.secret`과 상수 시간 비교(`MessageDigest.isEqual`)로 일치하면 VWR 토큰 검증을 건너뛴다 (`services-spring/gateway-service/src/main/java/guru/urr/gatewayservice/filter/VwrEntryTokenFilter.java:62-69`)
- CloudFront 시크릿이 설정되지 않은 경우(로컬 환경) 이 바이패스 로직은 비활성화된다 (`services-spring/gateway-service/src/main/java/guru/urr/gatewayservice/filter/VwrEntryTokenFilter.java:42-43`)
- 보호 대상 경로: `/api/seats/`, `/api/reservations`에 대한 POST, PUT, PATCH 요청 (`services-spring/gateway-service/src/main/java/guru/urr/gatewayservice/filter/VwrEntryTokenFilter.java:33,114`)

**환경 변수**:
- Kind 환경: `CLOUDFRONT_SECRET=local-dev-cloudfront-secret` (`k8s/spring/overlays/kind/config.env:18`)
- Prod 환경: `CLOUDFRONT_SECRET`은 시크릿으로 관리한다 (`k8s/spring/overlays/prod/secrets.env.example:18`)
- prod 패치에서 gateway-service에 `spring-prod-secret`의 `CLOUDFRONT_SECRET`을 주입한다 (`k8s/spring/overlays/prod/patches/services-env.yaml:41-44`)

### 4.8 시크릿 관리: AWS Secrets Manager

프로덕션 환경에서는 AWS Secrets Manager와 External Secrets Operator를 연동하여 시크릿을 자동 동기화한다 (`k8s/AWS_SECRETS_INTEGRATION.md:1-349`).

**관리 대상 시크릿** (`k8s/AWS_SECRETS_INTEGRATION.md:9-23`):

| AWS Secret 이름 | K8s 환경 변수 | 사용 서비스 |
|-----------------|-------------|-----------|
| `redis-auth-token` | `REDIS_PASSWORD` | ticket-service, gateway-service |
| `rds-credentials` | `TICKET_DB_PASSWORD`, `STATS_DB_PASSWORD` | ticket-service, stats-service |
| `queue-entry-token-secret` | `QUEUE_ENTRY_TOKEN_SECRET` | ticket-service |

**통합 방법 (권장: External Secrets Operator)** (`k8s/AWS_SECRETS_INTEGRATION.md:26-129`):
1. Helm으로 External Secrets Operator를 설치한다 (`k8s/AWS_SECRETS_INTEGRATION.md:30-36`)
2. Terraform으로 IRSA용 IAM Role을 생성하고, `secretsmanager:GetSecretValue`와 `secretsmanager:DescribeSecret` 권한을 부여한다 (`k8s/AWS_SECRETS_INTEGRATION.md:42-84`)
3. `SecretStore` CRD로 AWS Secrets Manager를 연결한다 (`k8s/AWS_SECRETS_INTEGRATION.md:89-105`)
4. `ExternalSecret` CRD로 원격 시크릿을 K8s Secret으로 동기화한다. `refreshInterval: 1h`로 자동 교체를 지원한다 (`k8s/AWS_SECRETS_INTEGRATION.md:109-129`)

**보안 권장사항** (`k8s/AWS_SECRETS_INTEGRATION.md:301-308`):
- IRSA(IAM Roles for Service Accounts) 사용: Pod에 정적 AWS 자격 증명을 주입하지 않음
- 시크릿 정기 교체: AWS Secrets Manager 교체 기능 활용
- TLS 활성화: Redis Transit Encryption
- CloudTrail 감사 로그: Secrets Manager API 호출 모니터링

### 4.9 IAM: IRSA (IAM Roles for Service Accounts)

IRSA는 K8s ServiceAccount를 AWS IAM Role과 연동하여 Pod 레벨의 세밀한 권한 관리를 가능하게 한다.

**IRSA 구성 예시** (`k8s/AWS_SECRETS_INTEGRATION.md:42-84`):
- `sts:AssumeRoleWithWebIdentity` 액션을 통해 OIDC Provider와 연동한다
- ServiceAccount 이름과 네임스페이스를 조건으로 특정 Pod에만 역할을 부여한다
- 리소스 ARN 기반의 최소 권한 원칙을 적용한다

**IRSA가 필요한 AWS 서비스**:
- **SQS**: queue-service에서 `sendMessage` 호출 (`services-spring/queue-service/src/main/java/guru/urr/queueservice/service/SqsPublisher.java:56-61`)
- **S3**: catalog-service에서 이미지 업로드 (`services-spring/catalog-service/build.gradle:34`)
- **Secrets Manager**: External Secrets Operator에서 시크릿 조회 (`k8s/AWS_SECRETS_INTEGRATION.md:71-83`)

---

## 5. 서비스 디스커버리

쿠버네티스 내부 DNS를 통해 서비스 간 통신이 이루어진다. 각 서비스는 `{service-name}:{port}` 형식의 DNS 이름으로 접근 가능하다.

**config.env 서비스 URL 설정** (`k8s/spring/overlays/kind/config.env:1-7`):

```
AUTH_SERVICE_URL=http://auth-service:3005
TICKET_SERVICE_URL=http://ticket-service:3002
PAYMENT_SERVICE_URL=http://payment-service:3003
STATS_SERVICE_URL=http://stats-service:3004
QUEUE_SERVICE_URL=http://queue-service:3007
CATALOG_SERVICE_URL=http://catalog-service:3009
COMMUNITY_SERVICE_URL=http://community-service:3008
```

**인프라 서비스 디스커버리** (`k8s/spring/overlays/kind/config.env:8-14`):
- Redis: `REDIS_HOST=dragonfly-spring`, `REDIS_PORT=6379`
- Kafka: `KAFKA_BOOTSTRAP_SERVERS=kafka-spring:9092`
- Zipkin: `ZIPKIN_ENDPOINT=http://zipkin-spring:9411/api/v2/spans`

Kind 패치에서도 gateway-service 등에 동일한 URL을 환경 변수로 명시적으로 주입하여 DNS 기반 디스커버리를 보장한다 (`k8s/spring/overlays/kind/patches/gateway-service.yaml:21-34`).

prod 환경에서는 동일한 패턴을 사용하되, DB URL만 RDS 엔드포인트로 변경하고 Kafka 부트스트랩 서버를 StatefulSet headless DNS로 변경한다 (`k8s/spring/overlays/prod/config.env:6-15`).

---

## 6. 포트 매핑 요약 테이블

### 애플리케이션 서비스

| 서비스 | 컨테이너 포트 | ClusterIP 포트 | NodePort (Kind) | hostPort (Kind) | 정의 위치 |
|--------|-------------|---------------|----------------|----------------|-----------|
| frontend | 3000 | 3000 | 30005 | 3000 | `k8s/spring/base/frontend/service.yaml:11`, `k8s/spring/overlays/kind/patches/frontend-service-nodeport.yaml:10` |
| gateway-service | 3001 | 3001 | 30000 | 3001 | `k8s/spring/base/gateway-service/service.yaml:12`, `k8s/spring/overlays/kind/patches/gateway-service-nodeport.yaml:10` |
| ticket-service | 3002 | 3002 | - | - | `k8s/spring/base/ticket-service/service.yaml:12` |
| payment-service | 3003 | 3003 | - | - | `k8s/spring/base/payment-service/service.yaml:12` |
| stats-service | 3004 | 3004 | - | - | `k8s/spring/base/stats-service/service.yaml:12` |
| auth-service | 3005 | 3005 | - | - | `k8s/spring/base/auth-service/service.yaml:12` |
| queue-service | 3007 | 3007 | - | - | `k8s/spring/base/queue-service/service.yaml:12` |
| community-service | 3008 | 3008 | - | - | `k8s/spring/base/community-service/service.yaml:12` |
| catalog-service | 3009 | 3009 | - | - | `k8s/spring/base/catalog-service/service.yaml:12` |

### 인프라 컴포넌트

| 컴포넌트 | 컨테이너 포트 | ClusterIP/NodePort 포트 | NodePort (Kind) | hostPort (Kind) | 정의 위치 |
|----------|-------------|------------------------|----------------|----------------|-----------|
| PostgreSQL | 5432 | 5432 (NodePort) | 30432 | 15432 | `k8s/spring/overlays/kind/postgres.yaml:88-90` |
| Dragonfly (Redis) | 6379 | 6379 (ClusterIP) | - | - | `k8s/spring/overlays/kind/dragonfly.yaml:61-62` |
| Kafka | 9092 | 9092 (ClusterIP) | - | - | `k8s/spring/overlays/kind/kafka.yaml:82-83` |
| Zipkin | 9411 | 9411 (NodePort) | 30411 | - | `k8s/spring/overlays/kind/zipkin.yaml:37-39` |
| Prometheus | 9090 | 9090 (NodePort) | 30090 | - | `k8s/spring/overlays/kind/prometheus.yaml:119-121` |
| Grafana | 3006 | 3006 (NodePort) | 30006 | 3006 | `k8s/spring/overlays/kind/grafana.yaml:129-131` |
| Loki | 3100 | 3100 (ClusterIP) | - | - | `k8s/spring/overlays/kind/loki.yaml:115-116` |
| Promtail | 9080 | - (DaemonSet) | - | - | `k8s/spring/overlays/kind/promtail.yaml:64` |

---

## 7. 환경 설정 관리

### 7.1 ConfigMap (config.env)

Kind 환경의 `config.env`에서 관리하는 설정 항목 (`k8s/spring/overlays/kind/config.env:1-22`):

**서비스 URL**:
- `AUTH_SERVICE_URL=http://auth-service:3005` (라인 1)
- `TICKET_SERVICE_URL=http://ticket-service:3002` (라인 2)
- `PAYMENT_SERVICE_URL=http://payment-service:3003` (라인 3)
- `STATS_SERVICE_URL=http://stats-service:3004` (라인 4)
- `QUEUE_SERVICE_URL=http://queue-service:3007` (라인 5)
- `CATALOG_SERVICE_URL=http://catalog-service:3009` (라인 6)
- `COMMUNITY_SERVICE_URL=http://community-service:3008` (라인 7)

**인프라 연결**:
- `REDIS_HOST=dragonfly-spring` (라인 8)
- `REDIS_PORT=6379` (라인 9)
- `KAFKA_BOOTSTRAP_SERVERS=kafka-spring:9092` (라인 13)
- `ZIPKIN_ENDPOINT=http://zipkin-spring:9411/api/v2/spans` (라인 14)

**AWS 설정**:
- `AWS_REGION=ap-northeast-2` (라인 10)
- `AWS_S3_BUCKET=local-mock-bucket` (라인 11)
- `SQS_ENABLED=false` (라인 16)
- `SQS_QUEUE_URL=` (라인 17)

**기타 설정**:
- `GOOGLE_CLIENT_ID=721028631258-...` (라인 12)
- `TRACING_SAMPLING_PROBABILITY=1.0` (라인 15, Kind에서는 전수 추적)
- `CLOUDFRONT_SECRET=local-dev-cloudfront-secret` (라인 18)
- `CORS_ALLOWED_ORIGINS=http://localhost:3000` (라인 19)
- `COOKIE_SECURE=false` (라인 20)
- `KAFKA_TOPIC_REPLICATION_FACTOR=1` (라인 21)

**Prod 환경 차이점** (`k8s/spring/overlays/prod/config.env:1-24`):
- DB URL이 RDS 엔드포인트를 가리킨다 (라인 1-5)
- `TRACING_SAMPLING_PROBABILITY=0.1` -- 10% 샘플링 (라인 17)
- `SQS_ENABLED=true` (라인 18)
- `CORS_ALLOWED_ORIGINS=https://urr.guru,https://www.urr.guru` (라인 20)
- `KAFKA_TOPIC_REPLICATION_FACTOR=3` (라인 21)
- `COOKIE_SECURE=true` (라인 23)
- Kafka 부트스트랩 서버가 3노드 headless DNS를 사용한다 (라인 22)

### 7.2 Secrets (secrets.env)

시크릿 값은 `secrets.env` 파일로 관리되며, 저장소에는 `secrets.env.example` 파일로 필요한 키만 공개한다.

**Kind 환경 시크릿 키** (`k8s/spring/overlays/kind/secrets.env.example:1-39`):

| 카테고리 | 키 | 설명 | 정의 위치 |
|---------|---|----|-----------|
| DB 인증 | `POSTGRES_USER` | 공유 PostgreSQL 유저 | `k8s/spring/overlays/kind/secrets.env.example:3` |
| DB 인증 | `POSTGRES_PASSWORD` | 공유 PostgreSQL 비밀번호 | `k8s/spring/overlays/kind/secrets.env.example:4` |
| DB 인증 | `AUTH_DB_USERNAME` / `AUTH_DB_PASSWORD` | Auth 서비스 DB | `k8s/spring/overlays/kind/secrets.env.example:7-8` |
| DB 인증 | `TICKET_DB_USERNAME` / `TICKET_DB_PASSWORD` | Ticket 서비스 DB | `k8s/spring/overlays/kind/secrets.env.example:11-12` |
| DB 인증 | `PAYMENT_DB_USERNAME` / `PAYMENT_DB_PASSWORD` | Payment 서비스 DB | `k8s/spring/overlays/kind/secrets.env.example:15-16` |
| DB 인증 | `STATS_DB_USERNAME` / `STATS_DB_PASSWORD` | Stats 서비스 DB | `k8s/spring/overlays/kind/secrets.env.example:19-20` |
| DB 인증 | `COMMUNITY_DB_USERNAME` / `COMMUNITY_DB_PASSWORD` | Community 서비스 DB | `k8s/spring/overlays/kind/secrets.env.example:23-24` |
| 인증 | `JWT_SECRET` | JWT 서명 시크릿 (Base64 인코딩, 최소 32바이트) | `k8s/spring/overlays/kind/secrets.env.example:28` |
| 인증 | `INTERNAL_API_TOKEN` | 서비스 간 내부 API 토큰 | `k8s/spring/overlays/kind/secrets.env.example:31` |
| 대기열 | `QUEUE_ENTRY_TOKEN_SECRET` | VWR 입장 토큰 HMAC 시크릿 (최소 32자) | `k8s/spring/overlays/kind/secrets.env.example:35` |
| 결제 | `TOSS_CLIENT_KEY` | Toss Payments 클라이언트 키 | `k8s/spring/overlays/kind/secrets.env.example:39` |

**Prod 환경 추가 시크릿** (`k8s/spring/overlays/prod/secrets.env.example:1-18`):
- 기본 구조는 Kind와 동일하나 다음 키가 추가된다:
  - `SQS_QUEUE_URL` -- SQS FIFO 큐 URL (라인 15)
  - `GOOGLE_CLIENT_ID` -- Google OAuth 클라이언트 ID (라인 16)
  - `AWS_S3_BUCKET` -- 프로덕션 S3 버킷 이름 (라인 17)
  - `CLOUDFRONT_SECRET` -- CloudFront 서명 시크릿 (라인 18)

ConfigMap과 Secret은 Kustomize의 `configMapGenerator`와 `secretGenerator`를 통해 생성되며, `disableNameSuffixHash: true`로 이름 해시 없이 일관된 참조가 가능하다 (`k8s/spring/overlays/kind/kustomization.yaml:19-30`).
