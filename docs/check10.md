 P0 — CRITICAL (프로덕션 배포 전 반드시 해결)

    #: 2
  문제: COOKIE_SECURE 기본값 false
  위치: auth-service/.../CookieHelper.java:19
  설명: 프로덕션 HTTPS에서 쿠키가 평문 HTTP로도 전송 가능 → 탈취 위험  
  문서: 04-auth
  ────────────────────────────────────────
  #: 3
  문제: Staging Terraform backend 미설정
  위치: terraform/environments/staging/main.tf
  설명: 로컬 상태 파일 → 팀 협업 불가, 동시 실행 시 상태 손상
  문서: 05-infra

  ---
  P1 — HIGH (배포 직후 또는 1-2주 내 해결)

  #: 4
  문제: OAuth 사용자 더미 비밀번호
  위치: AuthService.java:233
  설명: "OAUTH_USER_NO_PASSWORD" 평문 저장. BCrypt 형식이 아님
  문서: 04-auth
  ────────────────────────────────────────
  #: 5
  문제: VWR API Lambda CORS 와일드카드
  위치: lambda/vwr-api/index.js:8
  설명: CORS_ORIGIN 미설정 시 *로 폴백 → 크로스오리진 공격 가능        
  문서: 04-auth
  ────────────────────────────────────────
  #: 6
  문제: catalog-service DB 공유
  위치: catalog-service → ticket_db
  설명: MSA "Database per Service" 원칙 위반. 스키마 변경 시 양쪽 영향 
  문서: 02-backend
  ────────────────────────────────────────
  #: 7
  문제: SSR 미활용
  위치: apps/web/ 전체
  설명: 모든 페이지가 "use client". SEO/FCP 저하. server-api.ts 미사용 
  문서: 01-frontend
  ────────────────────────────────────────
  #: 8
  문제: Lambda@Edge VWR 설정 배포 지연
  위치: vwr-active.json 번들
  설명: VWR 활성화 시 Lambda@Edge 재배포 + CloudFront 전파 수분 소요   
  문서: 03-vwr-queue
  ────────────────────────────────────────

  #: 10
  문제: 백엔드 Egress NetworkPolicy 과도
  위치: network-policies.yaml:190-191
  설명: podSelector: {}로 네임스페이스 내 모든 Pod에 egress 허용       
  문서: 04-auth, 05-infra
  ────────────────────────────────────────
  #: 11
  문제: ECR 이미지 URI 플레이스홀더
  위치: kustomization.yaml
  설명: CHANGE_ME.dkr.ecr... 잔존. 초기 배포 시 실패
  문서: 05-infra
  ────────────────────────────────────────
  #: 12
  문제: Terraform 변수 파일 부재
  위치: terraform/environments/prod/, staging/
  설명: terraform.tfvars 없음. 변수 주입 방식 불명확
  문서: 05-infra
  ────────────────────────────────────────
  #: 13
  문제: ALB 보안 그룹 직접 접근
  위치: terraform/modules/alb/
  설명: CloudFront Managed Prefix List 외 접근 차단 미적용 시 직접 ALB 
    접근 가능
  문서: 04-auth

  ---
  P2 — MEDIUM (1-4주 내 개선)

  #: 14
  문제: JdbcTemplate + Map duck-typing
  위치: ticket/payment/stats/queue 서비스 전체
  설명: Map<String, Object> 반환. 타입 안전성 없음, 컬럼명 오타 미감지 
  문서: 02-backend
  ────────────────────────────────────────
  #: 15
  문제: Kafka Consumer Map duck-typing
  위치: PaymentEventConsumer 등
  설명: LinkedHashMap의 type 필드로 이벤트 판단. 스키마 변경에 취약    
  문서: 02-backend
  ────────────────────────────────────────
  #: 16
  문제: 서비스 간 공유 코드 복제
  위치: JwtTokenParser, AuthUser, GlobalExceptionHandler 등
  설명: 각 서비스에 동일 코드 복사. 변경 시 전체 동시 수정 필요        
  문서: 02-backend
  ────────────────────────────────────────
  #: 17
  문제: 테스트 인프라 부족
  위치: 전체 서비스
  설명: Testcontainers 미사용, Kafka 통합 테스트 미흡
  문서: 02-backend
  ────────────────────────────────────────
  #: 18
  문제: Entry Token 일회성 아님
  위치: VwrEntryTokenFilter.java
  설명: 만료 전 동일 토큰으로 반복 요청 가능. replay 위험
  문서: 04-auth
  ────────────────────────────────────────
  #: 19
  문제: Rate Limiting fail-open
  위치: RateLimitFilter.java:94-98
  설명: Redis 장애 시 모든 요청 허용. DDoS 시 보호 해제
  문서: 04-auth
  ────────────────────────────────────────
  #: 20
  문제: GET 요청 Entry Token 미보호
  위치: VwrEntryTokenFilter.java:49
  설명: POST/PUT/PATCH만 보호. GET /api/seats/events/{id}는 토큰 없이  
    접근 가능
  문서: 04-auth
  ────────────────────────────────────────
  #: 21
  문제: Tier 1 익명 ID 취약성
  위치: apps/vwr/index.html localStorage
  설명: 시크릿 모드/다른 브라우저에서 순번 무제한 발급 가능. 대기열    
  오염
    위험
  문서: 03-vwr-queue
  ────────────────────────────────────────
  #: 22
  문제: Tier 1↔Tier 2 사용자 식별 불연속
  위치: VWR → queue-service 전환
  설명: Tier 1 익명 ID ↔ Tier 2 JWT userId 연결 메커니즘 없음
  문서: 03-vwr-queue
  ────────────────────────────────────────
  #: 23
  문제: Redis Cluster Lua 해시 슬롯 제약
  위치: queue-service Lua 스크립트
  설명: 같은 이벤트의 키들이 다른 슬롯에 배치될 수 있음
  문서: 03-vwr-queue, 02-backend
  ────────────────────────────────────────
  #: 24
  문제: Tier 2 status 응답 entryToken 누락
  위치: QueueService.java status 메서드
  설명: admission-worker가 active로 전환한 사용자의 status 응답에      
    entryToken 미포함
  문서: 03-vwr-queue
  ────────────────────────────────────────
  #: 25
  문제: 단일 QUEUE_THRESHOLD
  위치: queue-service 환경변수
  설명: 전역 설정. 이벤트별 동시접속 한도 설정 불가
  문서: 03-vwr-queue
  ────────────────────────────────────────
  #: 26
  문제: Grafana 알림 채널 미설정
  위치: alerts.json
  설명: 알림 규칙은 있지만 notification channel(Slack, email) 미구성.  
    수신자 없음
  문서: 06-monitoring
  ────────────────────────────────────────
  #: 27
  문제: 프로덕션 Prometheus/Grafana 배포 미정의
  위치: terraform 모듈
  설명: EKS에 kube-prometheus-stack 배포 계획 없음
  문서: 06-monitoring
  ────────────────────────────────────────
  #: 28
  문제: NetworkPolicy 외부 서비스 Egress 미정의
  위치: network-policies.yaml
  설명: RDS/Redis/Kafka CIDR 기반 egress 규칙 없음. Calico 전환 시     
    서비스 중단
  문서: 05-infra
  ────────────────────────────────────────
  #: 29
  문제: catalog-service HPA/PDB 미적용
  위치: K8s manifests
  설명: 다른 8개 서비스에는 PDB가 있지만 catalog에는 없음
  문서: 05-infra
  ────────────────────────────────────────
  #: 30
  문제: 결제 조정(Reconciliation) 불완전
  위치: PaymentReconciliationScheduler
  설명: payment↔ticket 상태 불일치 자동 복구 한정적. Kafka 유실 시     
  수동
    개입 필요
  문서: 02-backend
  ────────────────────────────────────────
  #: 31
  문제: Zipkin 인메모리 스토리지
  위치: K8s Zipkin deployment
  설명: 재시작 시 추적 데이터 소실. 프로덕션에서 ES/Cassandra 필요     
  문서: 06-monitoring
  ────────────────────────────────────────
  #: 32
  문제: 추적 샘플링 비율 100%
  위치: application.yml 각 서비스
  설명: 프로덕션에서 성능 영향. 0.1 정도로 낮춰야 함
  문서: 06-monitoring
  ────────────────────────────────────────
  #: 33
  문제: transfer-events 토픽 미소비
  위치: ticket-service Kafka producer
  설명: 발행하지만 소비하는 서비스 없음
  문서: 07-user-flow
  ────────────────────────────────────────
  #: 34
  문제: 대기열 이탈 감지 미흡
  위치: queue/[eventId]/page.tsx
  설명: beforeunload 이벤트 핸들러 없음. 브라우저 종료 시 stale        
  정리까지
    자리 점유
  문서: 07-user-flow
  ────────────────────────────────────────
  #: 35
  문제: ticket-service Redis Cluster 설정 누락
  위치: ticket-service application.yml
  설명: 좌석 락이 Redis 의존인데 prod 프로파일에 Redis Cluster 설정    
  없음
  문서: 02-backend
  ────────────────────────────────────────
  #: 36
  문제: Next.js images.remotePatterns **
  위치: next.config.ts
  설명: hostname: "**"로 모든 호스트 허용. 보안 위험
  문서: 01-frontend
  ────────────────────────────────────────
  #: 37
  문제: Standalone Docker 출력 미설정
  위치: apps/web/Dockerfile
  설명: output: "standalone" 미설정. Docker 이미지 50-80% 과대
  문서: 01-frontend

  ---
  P3 — LOW (1-6개월 장기 개선)


  #: 39
  문제: 사용자 열거 가능
  위치: AuthService.java:67-69
  설명: 등록 시 "Email already exists" 노출. Rate limiting으로 상쇄 중 
  문서: 04-auth
  ────────────────────────────────────────
  #: 40
  문제: 디자인 시스템 부재
  위치: apps/web/
  설명: 공유 컴포넌트 3개뿐. statusBadge 5곳, 스피너 20+곳에서 중복    
  문서: 01-frontend
  ────────────────────────────────────────
  #: 41
  문제: TypeScript any 남용
  위치: admin/statistics/page.tsx:65-76
  설명: 12개 상태 변수가 모두 any. eslint-disable 사용
  문서: 01-frontend
  ────────────────────────────────────────
  #: 42
  문제: <img> vs next/image 불일치
  위치: 여러 페이지
  설명: Image 컴포넌트는 아티스트 상세만 사용. 나머지 <img> 직접 사용  
  문서: 01-frontend
  ────────────────────────────────────────
  #: 46
  문제: 번들 최적화 미흡
  위치: admin/statistics
  설명: recharts 동적 임포트 안됨. 메인 번들에 포함
  문서: 01-frontend
  ────────────────────────────────────────
  #: 50
  문제: EKS Addon 버전 고정
  위치: terraform/modules/eks/
  설명: VPC CNI, kube-proxy 등 하드코딩. 업그레이드 시 호환성 문제     
  문서: 05-infra
  ────────────────────────────────────────
  #: 51
  문제: Lambda@Edge 시크릿 파일 베이크인
  위치: cloudfront/main.tf:7-13
  설명: 기술적 한계이나 시크릿 로테이션 시 재배포 필요
  문서: 05-infra
  ────────────────────────────────────────
  #: 52
  문제: Kind↔Prod 서비스명 불일치
  위치: services-env.yaml
  설명: Redis=dragonfly-spring, Kafka=kafka-spring. AWS 엔드포인트와   
    다름
  문서: 05-infra
  ────────────────────────────────────────
  #: 53
  문제: 프로브 경로 불일치
  위치: K8s deployment
  설명: /health 사용. /actuator/health가 더 풍부한 의존성 검사 제공    
  문서: 06-monitoring
  ────────────────────────────────────────
  #: 54
  문제: startupProbe 부재
  위치: K8s deployment
  설명: Spring Boot 시작 느릴 수 있음. initialDelaySeconds로만 보호    
  문서: 06-monitoring
  ────────────────────────────────────────
  #: 55
  문제: Prometheus 정적 서비스 디스커버리
  위치: prometheus.yml
  설명: static_configs 사용. 스케일링 시 수동 업데이트 필요
  문서: 06-monitoring
  ────────────────────────────────────────
  #: 56
  문제: payment/stats 커스텀 메트릭 없음
  위치: 해당 서비스
  설명: 비즈니스 메트릭이 ticket/queue에만 있음
  문서: 06-monitoring
  ────────────────────────────────────────
  #: 57
  문제: Loki 단일 인스턴스
  위치: K8s Loki deployment
  설명: 복제 계수 1. SPOF
  문서: 06-monitoring
  ────────────────────────────────────────
  #: 59
  문제: 대기 시간 추정 부정확
  위치: Tier 1/Tier 2
  설명: 트래픽 급변 시 부정확. Tier 2 초기 5초간 과대 추정
  문서: 03-vwr-queue
  ────────────────────────────────────────
  #: 60
  문제: ArgoCD Prod 수동 Sync
  위치: argocd/applications/urr-spring-prod.yaml
  설명: 안전하나 배포 자동화에 수동 개입 필요
  문서: 05-infra

  ---