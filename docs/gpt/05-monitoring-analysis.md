# 모니터링 상세 분석 (Metrics/Logs/Tracing/Alerts)

## 1) 메트릭 수집 경로
- 각 Spring 서비스는 actuator `prometheus` 엔드포인트를 노출하도록 설정되어 있다.  
  출처: `services-spring/gateway-service/src/main/resources/application.yml:89`, `services-spring/ticket-service/src/main/resources/application.yml:55`, `services-spring/payment-service/src/main/resources/application.yml:41`, `services-spring/stats-service/src/main/resources/application.yml:43`, `services-spring/auth-service/src/main/resources/application.yml:35`, `services-spring/queue-service/src/main/resources/application.yml:25`, `services-spring/catalog-service/src/main/resources/application.yml:27`, `services-spring/community-service/src/main/resources/application.yml:28`
- Prometheus scrape 설정은 `/actuator/prometheus` 경로를 10초 간격으로 긁는다.  
  출처: `k8s/spring/overlays/kind/prometheus.yaml:13`, `k8s/spring/overlays/kind/prometheus.yaml:14`
- scrape 대상이 모든 주요 서비스(auth/gateway/ticket/payment/stats/queue/community/catalog)로 등록되어 있다.  
  출처: `k8s/spring/overlays/kind/prometheus.yaml:17`, `k8s/spring/overlays/kind/prometheus.yaml:45`

## 2) 대시보드(Grafana)
- Grafana datasource provisioning에 Prometheus와 Loki를 등록한다.  
  출처: `k8s/spring/overlays/kind/grafana.yaml:10`, `k8s/spring/overlays/kind/grafana.yaml:17`
- dashboard provisioning config를 통해 파일 기반 대시보드를 자동 로드한다.  
  출처: `k8s/spring/overlays/kind/grafana.yaml:27`, `k8s/spring/overlays/kind/grafana.yaml:40`
- Grafana Service는 NodePort(30006)로 외부 접근 가능하다(kind).  
  출처: `k8s/spring/overlays/kind/grafana.yaml:125`, `k8s/spring/overlays/kind/grafana.yaml:131`
- 대시보드 ConfigMap에는 서비스 메트릭 패널과 alerts 패널 정의가 포함된다.  
  출처: `k8s/spring/overlays/kind/grafana-dashboards.yaml:1003`, `k8s/spring/overlays/kind/grafana-dashboards.yaml:1054`, `k8s/spring/overlays/kind/grafana-dashboards.yaml:1214`

## 3) 로그 수집 경로
- Loki는 단일 Deployment + PVC로 구성되어 로그 저장소 역할을 한다.  
  출처: `k8s/spring/overlays/kind/loki.yaml:46`, `k8s/spring/overlays/kind/loki.yaml:99`
- Promtail DaemonSet이 노드 로그를 수집해 Loki push API로 전송한다.  
  출처: `k8s/spring/overlays/kind/promtail.yaml:35`, `k8s/spring/overlays/kind/promtail.yaml:16`
- Promtail은 K8s metadata 리라벨링으로 `app/pod/namespace` 라벨을 붙인다.  
  출처: `k8s/spring/overlays/kind/promtail.yaml:26`, `k8s/spring/overlays/kind/promtail.yaml:30`

## 4) 트레이싱 경로
- 모든 주요 서비스 설정에 Micrometer tracing + Zipkin endpoint가 있다.  
  출처: `services-spring/gateway-service/src/main/resources/application.yml:90`, `services-spring/gateway-service/src/main/resources/application.yml:95`, `services-spring/ticket-service/src/main/resources/application.yml:56`, `services-spring/ticket-service/src/main/resources/application.yml:61`, `services-spring/payment-service/src/main/resources/application.yml:42`, `services-spring/payment-service/src/main/resources/application.yml:47`
- 로그 패턴에 traceId/spanId를 포함해 로그-트레이스 상호 추적을 지원한다.  
  출처: `services-spring/gateway-service/src/main/resources/application.yml:99`, `services-spring/ticket-service/src/main/resources/application.yml:65`, `services-spring/payment-service/src/main/resources/application.yml:51`
- kind에 Zipkin Deployment/Service가 별도 배포된다.  
  출처: `k8s/spring/overlays/kind/zipkin.yaml:2`, `k8s/spring/overlays/kind/zipkin.yaml:29`

## 5) 애플리케이션 레벨 모니터링 UI
- 프론트 관리자 통계 페이지는 30초 주기로 실시간 재조회한다.  
  출처: `apps/web/src/app/admin/statistics/page.tsx:79`, `apps/web/src/app/admin/statistics/page.tsx:115`
- `overview/hourly/events` 등 다중 통계 API를 병행 호출한다.  
  출처: `apps/web/src/app/admin/statistics/page.tsx:90`, `apps/web/src/app/admin/statistics/page.tsx:92`, `apps/web/src/app/admin/statistics/page.tsx:105`

## 6) 운영상 미흡점
- Grafana에 Slack/PagerDuty contact-point 정의가 보이지 않고 datasource/dashboard provisioning만 존재한다.  
  출처: `k8s/spring/overlays/kind/grafana.yaml:7`, `k8s/spring/overlays/kind/grafana.yaml:29`
- alerts 패널 텍스트는 정의되어 있으나, 실제 알림 라우팅 설정(채널/수신자)과 분리되어 있어 즉시 운영 연동 상태는 불명확하다.  
  출처: `k8s/spring/overlays/kind/grafana-dashboards.yaml:1003`, `k8s/spring/overlays/kind/grafana-dashboards.yaml:1214`
- stats 조회 API 일부가 하드코딩/합성값을 반환해 대시보드 해석 정확도를 떨어뜨린다.  
  출처: `services-spring/stats-service/src/main/java/com/tiketi/statsservice/service/StatsQueryService.java:66`, `services-spring/stats-service/src/main/java/com/tiketi/statsservice/service/StatsQueryService.java:228`, `services-spring/stats-service/src/main/java/com/tiketi/statsservice/service/StatsQueryService.java:549`
- kind 환경 Grafana admin 계정이 고정(`admin/admin`)이라 보안 운영 정책과 분리되어 있다.  
  출처: `k8s/spring/overlays/kind/grafana.yaml:67`, `k8s/spring/overlays/kind/grafana.yaml:70`

## 7) 좋은 점
- 메트릭/로그/트레이싱 스택이 모두 같은 오버레이에서 기동되어 로컬 검증이 빠르다.  
  출처: `k8s/spring/overlays/kind/kustomization.yaml:13`, `k8s/spring/overlays/kind/kustomization.yaml:17`
- Prometheus scrape 대상이 서비스 전반을 커버해 기본 관측 공백이 적다.  
  출처: `k8s/spring/overlays/kind/prometheus.yaml:17`, `k8s/spring/overlays/kind/prometheus.yaml:47`
- 로그 패턴에 trace/span을 심어 분산 추적 연계를 고려했다.  
  출처: `services-spring/gateway-service/src/main/resources/application.yml:99`

## 8) 개선 제안
1. Grafana Unified Alerting contact-point/notification policy를 IaC로 명시.
2. stats-service의 합성 통계값을 실제 집계 로직으로 대체.
3. 프로덕션용 모니터링 접근제어(SSO, 비밀번호 회전, RBAC) 문서화/자동화.
4. 경보 임계치 기반 부하테스트(큐 대기시간, 결제실패율, seat lock 충돌률)를 포함한 SLO 정의.
