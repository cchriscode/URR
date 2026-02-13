# mTLS Strategy for URR Ticketing Platform

**Audience:** DevOps engineers implementing service mesh mTLS on the URR (tiketi) Kubernetes cluster.
**Last updated:** 2026-02-13

---

## Table of Contents

1. [Current State](#1-current-state)
2. [Recommended Approach: Istio Service Mesh](#2-recommended-approach-istio-service-mesh)
3. [Step-by-Step Rollout Plan](#3-step-by-step-rollout-plan)
4. [Certificate Management](#4-certificate-management)
5. [Monitoring mTLS with Kiali](#5-monitoring-mtls-with-kiali)
6. [Alternatives Considered](#6-alternatives-considered)

---

## 1. Current State

### Service Inventory

| Service            | Port | Namespace      | Role                        |
|--------------------|------|----------------|-----------------------------|
| gateway-service    | 3001 | tiketi-spring  | API gateway, route dispatch |
| auth-service       | 3005 | tiketi-spring  | Authentication, JWT issuing |
| ticket-service     | 3002 | tiketi-spring  | Booking, reservations, seats|
| catalog-service    | 3009 | tiketi-spring  | Events, artists, admin      |
| payment-service    | 3003 | tiketi-spring  | Payment processing          |
| stats-service      | 3004 | tiketi-spring  | Analytics and metrics       |
| queue-service      | 3007 | tiketi-spring  | Virtual waiting room        |
| community-service  | 3008 | tiketi-spring  | News, community features    |

### Internal Communication Patterns

All inter-service calls use **plain HTTP** over Kubernetes ClusterIP services:

```
gateway-service  -->  auth-service       (http://auth-service:3005)
gateway-service  -->  ticket-service     (http://ticket-service:3002)
gateway-service  -->  catalog-service    (http://catalog-service:3009)
gateway-service  -->  payment-service    (http://payment-service:3003)
gateway-service  -->  stats-service      (http://stats-service:3004)
gateway-service  -->  queue-service      (http://queue-service:3007)
gateway-service  -->  community-service  (http://community-service:3008)

payment-service  -->  ticket-service     (reservation/transfer/membership validation)
queue-service    -->  catalog-service    (event queue info)
catalog-service  -->  ticket-service     (seat operations)
catalog-service  -->  auth-service       (user batch lookup)
```

### Current Security Controls

**Bearer token authentication for internal APIs.** Services share a single `INTERNAL_API_TOKEN` secret (injected via `spring-kind-secret`). Each internal HTTP call includes an `Authorization: Bearer <token>` header. The `InternalTokenValidator` class performs timing-safe comparison against the shared secret.

Reference implementation in `payment-service`:
```java
// TicketInternalClient.java
.header("Authorization", "Bearer " + internalToken)
```

**NetworkPolicy rules.** Four policies are deployed in the namespace:

| Policy                       | Effect                                                    |
|------------------------------|-----------------------------------------------------------|
| `default-deny-all`           | Blocks all ingress and egress by default                  |
| `allow-gateway-ingress`      | Permits external traffic to gateway-service on port 3001  |
| `allow-internal-communication` | Allows pod-to-pod traffic within `tier: backend` label  |
| `allow-gateway-to-services`  | Permits gateway egress to backend pods and kube-dns       |

### Gaps

1. **No transport encryption.** All inter-service traffic is plaintext HTTP. A compromised pod in the namespace can sniff traffic between any two services.
2. **Shared static secret.** Every service uses the same `INTERNAL_API_TOKEN`. Rotation requires redeploying all services simultaneously. A leak of this single token grants access to every internal endpoint.
3. **No mutual identity verification.** Services cannot cryptographically verify the identity of callers. The bearer token proves knowledge of a shared secret, not the identity of the calling workload.
4. **NetworkPolicy is L3/L4 only.** It restricts which pods can connect, but does not authenticate identity or encrypt the connection.

---

## 2. Recommended Approach: Istio Service Mesh

### Why Istio

Istio adds mTLS to the platform **without application code changes**. The Envoy sidecar proxies handle TLS handshake, certificate presentation, and peer verification transparently. The services continue to speak plain HTTP to `localhost`; the sidecar encrypts traffic on the wire.

### What Istio Provides

| Capability                | How It Works                                                       |
|---------------------------|--------------------------------------------------------------------|
| Automatic mTLS            | Envoy sidecars negotiate TLS between pods using SPIFFE identities  |
| Certificate management    | Istio CA (istiod) issues and rotates short-lived X.509 certs      |
| Identity-based AuthZ      | `AuthorizationPolicy` restricts which service accounts can call which endpoints |
| Observability             | Kiali dashboard visualizes mTLS status per connection              |
| Zero code changes         | Sidecars intercept traffic at the network level                    |

### Target Architecture

```
                         [Istio Ingress Gateway]
                                  |
                                  | (TLS terminated)
                                  v
                         [gateway-service + envoy]
                        /    |     |     |    \
                      mTLS  mTLS  mTLS  mTLS  mTLS
                      /      |     |     |      \
            [auth]  [ticket] [catalog] [payment] [queue] [community] [stats]
             +envoy  +envoy   +envoy    +envoy   +envoy   +envoy     +envoy

All inter-service arrows carry mTLS-encrypted traffic.
Envoy sidecars present SPIFFE identities:
  spiffe://cluster.local/ns/tiketi-spring/sa/payment-service
```

---

## 3. Step-by-Step Rollout Plan

### Prerequisites

- Kubernetes cluster version 1.26 or later
- `kubectl` and `istioctl` CLI tools installed
- Cluster admin access
- Existing `tiketi-spring` namespace with all 8 services running

### Phase 1: Install Istio (Day 1)

**Step 1.1 -- Install Istio with the `default` profile.**

```bash
istioctl install --set profile=default -y
```

This installs `istiod` (control plane) and the Istio ingress gateway in the `istio-system` namespace.

**Step 1.2 -- Verify the installation.**

```bash
istioctl verify-install
kubectl get pods -n istio-system
```

Expected output: `istiod` pod running, `istio-ingressgateway` pod running.

**Step 1.3 -- Create dedicated service accounts for each service.** If your deployments do not already specify `serviceAccountName`, create a ServiceAccount per service. This is required for SPIFFE identity differentiation.

```yaml
# k8s/spring/base/service-accounts.yaml
apiVersion: v1
kind: ServiceAccount
metadata:
  name: gateway-service
---
apiVersion: v1
kind: ServiceAccount
metadata:
  name: auth-service
---
apiVersion: v1
kind: ServiceAccount
metadata:
  name: ticket-service
---
apiVersion: v1
kind: ServiceAccount
metadata:
  name: catalog-service
---
apiVersion: v1
kind: ServiceAccount
metadata:
  name: payment-service
---
apiVersion: v1
kind: ServiceAccount
metadata:
  name: stats-service
---
apiVersion: v1
kind: ServiceAccount
metadata:
  name: queue-service
---
apiVersion: v1
kind: ServiceAccount
metadata:
  name: community-service
```

Add `serviceAccountName` to each Deployment spec:

```yaml
spec:
  template:
    spec:
      serviceAccountName: payment-service  # match the service name
```

### Phase 2: Enable Sidecar Injection (Day 2-3)

**Step 2.1 -- Label the namespace for automatic sidecar injection.**

```bash
kubectl label namespace tiketi-spring istio-injection=enabled
```

**Step 2.2 -- Restart deployments to inject sidecars.** Roll each service one at a time. Start with low-risk services.

Recommended rollout order:

1. `stats-service` (read-only analytics, lowest impact)
2. `community-service` (independent features)
3. `queue-service` (no downstream internal callers)
4. `catalog-service`
5. `auth-service`
6. `ticket-service`
7. `payment-service`
8. `gateway-service` (last, as it routes to everything)

```bash
kubectl rollout restart deployment/stats-service -n tiketi-spring
kubectl rollout status deployment/stats-service -n tiketi-spring --timeout=120s
```

Repeat for each service. After each restart, verify:

```bash
# Confirm sidecar is running (should show 2/2 containers)
kubectl get pods -n tiketi-spring -l app=stats-service

# Confirm mTLS negotiation is active
istioctl proxy-status
```

**Step 2.3 -- Verify sidecar injection on all pods.**

```bash
kubectl get pods -n tiketi-spring -o jsonpath='{range .items[*]}{.metadata.name}{"\t"}{range .spec.containers[*]}{.name}{","}{end}{"\n"}{end}'
```

Every pod should list two containers: the application container and `istio-proxy`.

### Phase 3: Permissive mTLS Mode (Day 3-5)

Istio defaults to `PERMISSIVE` mode. This accepts both plaintext and mTLS traffic. Keep this mode active for 2-3 days while verifying that all service-to-service calls work through the sidecars.

**Step 3.1 -- Apply namespace-wide PeerAuthentication in PERMISSIVE mode (explicit).**

```yaml
# k8s/spring/base/istio/peer-authentication.yaml
apiVersion: security.istio.io/v1
kind: PeerAuthentication
metadata:
  name: default
  namespace: tiketi-spring
spec:
  mtls:
    mode: PERMISSIVE
```

**Step 3.2 -- Verify that existing traffic flows work.**

Run the following checks for each critical call path:

```bash
# Check payment-service -> ticket-service (reservation validation)
kubectl exec -n tiketi-spring deploy/payment-service -c istio-proxy -- \
  pilot-agent request GET /stats/prometheus 2>/dev/null | grep istio_requests_total

# Use istioctl to verify mTLS status between specific services
istioctl x describe pod <payment-service-pod> -n tiketi-spring
```

**Step 3.3 -- Monitor for plaintext fallback.** In Kiali or via Prometheus, watch for connections that are NOT using mTLS. All intra-mesh traffic should show mTLS after sidecars are injected on both ends.

### Phase 4: Strict mTLS Mode (Day 6-7)

Once all 8 services have sidecars and traffic is verified:

**Step 4.1 -- Switch to STRICT mode.**

```yaml
# k8s/spring/base/istio/peer-authentication.yaml
apiVersion: security.istio.io/v1
kind: PeerAuthentication
metadata:
  name: default
  namespace: tiketi-spring
spec:
  mtls:
    mode: STRICT
```

```bash
kubectl apply -f k8s/spring/base/istio/peer-authentication.yaml
```

After applying, any plaintext connection to a service in the `tiketi-spring` namespace will be rejected.

**Step 4.2 -- Exclude external dependencies from mTLS.** Services that connect to infrastructure (PostgreSQL, Redis/Dragonfly, Kafka, Zipkin) outside the mesh need `DestinationRule` overrides if those targets do not have sidecars:

```yaml
# k8s/spring/base/istio/destination-rules.yaml
apiVersion: networking.istio.io/v1
kind: DestinationRule
metadata:
  name: postgres-no-mtls
  namespace: tiketi-spring
spec:
  host: postgres-spring.tiketi-spring.svc.cluster.local
  trafficPolicy:
    tls:
      mode: DISABLE
---
apiVersion: networking.istio.io/v1
kind: DestinationRule
metadata:
  name: dragonfly-no-mtls
  namespace: tiketi-spring
spec:
  host: dragonfly-spring.tiketi-spring.svc.cluster.local
  trafficPolicy:
    tls:
      mode: DISABLE
---
apiVersion: networking.istio.io/v1
kind: DestinationRule
metadata:
  name: kafka-no-mtls
  namespace: tiketi-spring
spec:
  host: kafka-spring.tiketi-spring.svc.cluster.local
  trafficPolicy:
    tls:
      mode: DISABLE
```

**Step 4.3 -- Validate strict mode is working.**

```bash
# This should FAIL (plaintext from outside the mesh)
kubectl run test-curl --rm -it --image=curlimages/curl --restart=Never -- \
  curl -v http://ticket-service.tiketi-spring:3002/health

# This should SUCCEED (from a pod with a sidecar)
kubectl exec -n tiketi-spring deploy/gateway-service -c gateway-service -- \
  curl -s http://ticket-service:3002/health
```

### Phase 5: Authorization Policies (Day 8-10)

With mTLS enforced, layer identity-based access control using `AuthorizationPolicy`. This restricts which services can call which endpoints based on SPIFFE identity.

```yaml
# k8s/spring/base/istio/authorization-policies.yaml

# Default: deny all traffic within the namespace
apiVersion: security.istio.io/v1
kind: AuthorizationPolicy
metadata:
  name: deny-all
  namespace: tiketi-spring
spec: {}
---
# Allow gateway-service to reach all backend services
apiVersion: security.istio.io/v1
kind: AuthorizationPolicy
metadata:
  name: allow-gateway-to-backends
  namespace: tiketi-spring
spec:
  action: ALLOW
  rules:
    - from:
        - source:
            principals:
              - cluster.local/ns/tiketi-spring/sa/gateway-service
---
# Allow payment-service to call ticket-service internal endpoints
apiVersion: security.istio.io/v1
kind: AuthorizationPolicy
metadata:
  name: allow-payment-to-ticket
  namespace: tiketi-spring
spec:
  selector:
    matchLabels:
      app: ticket-service
  action: ALLOW
  rules:
    - from:
        - source:
            principals:
              - cluster.local/ns/tiketi-spring/sa/payment-service
      to:
        - operation:
            paths:
              - /internal/*
---
# Allow queue-service to call catalog-service
apiVersion: security.istio.io/v1
kind: AuthorizationPolicy
metadata:
  name: allow-queue-to-catalog
  namespace: tiketi-spring
spec:
  selector:
    matchLabels:
      app: catalog-service
  action: ALLOW
  rules:
    - from:
        - source:
            principals:
              - cluster.local/ns/tiketi-spring/sa/queue-service
---
# Allow catalog-service to call ticket-service and auth-service
apiVersion: security.istio.io/v1
kind: AuthorizationPolicy
metadata:
  name: allow-catalog-to-ticket-and-auth
  namespace: tiketi-spring
spec:
  selector:
    matchLabels:
      app.kubernetes.io/part-of: tiketi
  action: ALLOW
  rules:
    - from:
        - source:
            principals:
              - cluster.local/ns/tiketi-spring/sa/catalog-service
      to:
        - operation:
            paths:
              - /internal/*
  # Apply to both ticket-service and auth-service via separate policies
---
apiVersion: security.istio.io/v1
kind: AuthorizationPolicy
metadata:
  name: allow-catalog-to-ticket
  namespace: tiketi-spring
spec:
  selector:
    matchLabels:
      app: ticket-service
  action: ALLOW
  rules:
    - from:
        - source:
            principals:
              - cluster.local/ns/tiketi-spring/sa/catalog-service
      to:
        - operation:
            paths:
              - /internal/*
---
apiVersion: security.istio.io/v1
kind: AuthorizationPolicy
metadata:
  name: allow-catalog-to-auth
  namespace: tiketi-spring
spec:
  selector:
    matchLabels:
      app: auth-service
  action: ALLOW
  rules:
    - from:
        - source:
            principals:
              - cluster.local/ns/tiketi-spring/sa/catalog-service
      to:
        - operation:
            paths:
              - /internal/*
```

### Phase 6: Deprecate Internal Bearer Tokens (Day 11+)

After mTLS and AuthorizationPolicy are verified in production:

1. Remove `INTERNAL_API_TOKEN` from `secrets.env`.
2. Remove `InternalTokenValidator` classes from each service.
3. Remove `Authorization: Bearer <internalToken>` headers from internal HTTP clients (e.g., `TicketInternalClient`).
4. Remove the `INTERNAL_API_TOKEN` environment variable from deployment patches.

This is safe because Istio AuthorizationPolicy now handles caller identity verification at the network level, replacing the shared bearer token.

---

## 4. Certificate Management

### How Istio CA Works

Istio's built-in certificate authority (running inside `istiod`) handles the full certificate lifecycle:

```
1. Sidecar starts  -->  Generates CSR with SPIFFE ID
2. CSR sent to istiod  -->  istiod signs with CA key
3. Certificate returned  -->  Sidecar uses for mTLS
4. Cert nears expiry  -->  Sidecar auto-requests new cert
```

### Default Configuration

| Parameter           | Default Value   | Recommended for Production |
|---------------------|-----------------|----------------------------|
| Cert lifetime       | 24 hours        | 24 hours (keep default)    |
| Root CA lifetime    | 10 years        | 3 years with rotation plan |
| Key size            | RSA 2048        | RSA 2048 or ECDSA P256     |
| SAN format          | SPIFFE URI      | No change needed           |

### Bring Your Own Root CA (Recommended for Production)

For production, replace the self-signed Istio root CA with a CA signed by your organization's PKI:

```bash
# Create the cacerts secret with your certificates
kubectl create secret generic cacerts -n istio-system \
  --from-file=ca-cert.pem \
  --from-file=ca-key.pem \
  --from-file=root-cert.pem \
  --from-file=cert-chain.pem
```

Then restart istiod to pick up the new CA:

```bash
kubectl rollout restart deployment/istiod -n istio-system
```

### Verifying Certificate Rotation

```bash
# Check cert details for a specific sidecar
istioctl proxy-config secret deploy/payment-service -n tiketi-spring -o json | \
  jq '.dynamicActiveSecrets[0].secret.tlsCertificate.certificateChain.inlineBytes' | \
  tr -d '"' | base64 -d | openssl x509 -text -noout

# Check cert expiration across all proxies
for deploy in gateway-service auth-service ticket-service catalog-service \
              payment-service stats-service queue-service community-service; do
  echo "=== $deploy ==="
  istioctl proxy-config secret deploy/$deploy -n tiketi-spring -o json | \
    jq -r '.dynamicActiveSecrets[0].secret.tlsCertificate.certificateChain.inlineBytes' | \
    base64 -d | openssl x509 -noout -enddate
done
```

---

## 5. Monitoring mTLS with Kiali

### Install Kiali and Dependencies

```bash
# Install Prometheus (required for Kiali metrics)
kubectl apply -f https://raw.githubusercontent.com/istio/istio/release-1.24/samples/addons/prometheus.yaml

# Install Kiali
kubectl apply -f https://raw.githubusercontent.com/istio/istio/release-1.24/samples/addons/kiali.yaml

# Wait for deployment
kubectl rollout status deployment/kiali -n istio-system --timeout=120s
```

Note: The `tiketi-spring` overlay already deploys Prometheus and Grafana. Point Kiali at the existing Prometheus instance by setting the `external_services.prometheus.url` field in the Kiali ConfigMap.

### Access Kiali Dashboard

```bash
istioctl dashboard kiali
```

### What to Monitor

**Graph view.** Navigate to Graph > Namespace: tiketi-spring. Look for:

- Lock icons on edges: indicates mTLS is active on that connection.
- Red edges: indicate failed connections or plaintext fallback.
- Missing edges: indicate a service-to-service path that is not going through the mesh.

**Security view.** Check the Security tab for:

- mTLS status per workload (should show "mTLS enabled" for all 8 services).
- Policy compliance (AuthorizationPolicy violations show as 403 responses).

### Prometheus Queries for mTLS Health

```promql
# Total mTLS connections by source and destination
sum(rate(istio_requests_total{
  connection_security_policy="mutual_tls",
  destination_workload_namespace="tiketi-spring"
}[5m])) by (source_workload, destination_workload)

# Plaintext connections (should be zero after STRICT mode)
sum(rate(istio_requests_total{
  connection_security_policy="none",
  destination_workload_namespace="tiketi-spring"
}[5m])) by (source_workload, destination_workload)

# AuthorizationPolicy denials
sum(rate(istio_requests_total{
  response_code="403",
  destination_workload_namespace="tiketi-spring"
}[5m])) by (source_workload, destination_workload)
```

### Grafana Dashboard

Import the Istio dashboards into the existing Grafana deployment:

- **Istio Mesh Dashboard** (ID: 7639) -- mesh-wide mTLS overview
- **Istio Service Dashboard** (ID: 7636) -- per-service mTLS metrics
- **Istio Workload Dashboard** (ID: 7630) -- per-pod connection security

---

## 6. Alternatives Considered

### Linkerd

| Factor            | Linkerd                                | Istio                                    |
|-------------------|----------------------------------------|------------------------------------------|
| Resource overhead | Lower (Rust-based proxy, ~10MB per pod)| Higher (Envoy, ~50MB per pod)            |
| mTLS              | On by default, auto-rotation           | Requires PeerAuthentication config       |
| AuthZ policies    | Server-based only, less granular       | Full L7 path/method-based policies       |
| Observability     | Built-in dashboard, simpler            | Kiali + Grafana, more comprehensive      |
| Ecosystem         | Smaller, CNCF graduated                | Larger, CNCF graduated, broader adoption |
| Learning curve    | Lower                                  | Higher                                   |

**Why not Linkerd:** The URR platform needs fine-grained AuthorizationPolicy rules (e.g., restricting `payment-service` to only `/internal/*` paths on `ticket-service`). Linkerd's `Server` and `ServerAuthorization` resources are less flexible for L7 path-based restrictions. Additionally, the team's existing Grafana/Prometheus stack integrates more naturally with Istio's telemetry.

### Manual cert-manager + Application-Level TLS

| Factor            | cert-manager approach                  | Istio                                    |
|-------------------|----------------------------------------|------------------------------------------|
| Code changes      | Required (configure TLS in Spring Boot)| None (transparent sidecar)               |
| Certificate scope | Per-service, manual wiring             | Automatic per-pod SPIFFE identity        |
| Rotation          | cert-manager handles issuance; app must reload | Fully automatic, zero-downtime    |
| AuthZ             | Must implement in application code     | Declarative Kubernetes resources         |
| Maintenance       | High (TLS config in every service)     | Low (mesh-wide configuration)            |

**Why not cert-manager:** Configuring TLS listeners in 8 Spring Boot services requires significant code changes. Each service would need TLS keystore configuration, cert reloading logic, and client trust store management. The Istio sidecar approach achieves the same result with zero application changes. cert-manager remains useful for managing the Istio root CA certificate itself if using a custom PKI.

### No Service Mesh (NetworkPolicy Only)

The current NetworkPolicy setup provides L3/L4 isolation but does not encrypt traffic or verify workload identity. For a ticketing platform that processes payments and personal data, transport encryption between services is a baseline production security requirement. NetworkPolicy alone is insufficient.

---

## Appendix: Rollout Checklist

Use this checklist to track progress during implementation.

```
[ ] Phase 1: Istio Installation
    [ ] Install Istio with default profile
    [ ] Verify istiod and ingress gateway pods are running
    [ ] Create ServiceAccount per service
    [ ] Add serviceAccountName to each Deployment

[ ] Phase 2: Sidecar Injection
    [ ] Label tiketi-spring namespace for injection
    [ ] Restart stats-service, verify 2/2 containers
    [ ] Restart community-service, verify 2/2 containers
    [ ] Restart queue-service, verify 2/2 containers
    [ ] Restart catalog-service, verify 2/2 containers
    [ ] Restart auth-service, verify 2/2 containers
    [ ] Restart ticket-service, verify 2/2 containers
    [ ] Restart payment-service, verify 2/2 containers
    [ ] Restart gateway-service, verify 2/2 containers

[ ] Phase 3: Permissive mTLS
    [ ] Apply PeerAuthentication (PERMISSIVE)
    [ ] Verify gateway -> all backend routes work
    [ ] Verify payment-service -> ticket-service validation calls
    [ ] Verify queue-service -> catalog-service calls
    [ ] Verify catalog-service -> ticket-service calls
    [ ] Verify catalog-service -> auth-service calls
    [ ] Monitor for 2-3 days with no errors

[ ] Phase 4: Strict mTLS
    [ ] Apply PeerAuthentication (STRICT)
    [ ] Apply DestinationRules for postgres, dragonfly, kafka
    [ ] Verify plaintext connections are rejected
    [ ] Verify all service health endpoints respond

[ ] Phase 5: Authorization Policies
    [ ] Apply deny-all default policy
    [ ] Apply allow-gateway-to-backends policy
    [ ] Apply allow-payment-to-ticket policy
    [ ] Apply allow-queue-to-catalog policy
    [ ] Apply allow-catalog-to-ticket policy
    [ ] Apply allow-catalog-to-auth policy
    [ ] Verify all call paths still work
    [ ] Verify unauthorized call paths are rejected (403)

[ ] Phase 6: Cleanup
    [ ] Remove INTERNAL_API_TOKEN from secrets.env
    [ ] Remove InternalTokenValidator from all services
    [ ] Remove bearer token headers from internal HTTP clients
    [ ] Redeploy all services without internal token config

[ ] Monitoring
    [ ] Install Kiali and connect to Prometheus
    [ ] Import Istio Grafana dashboards
    [ ] Verify lock icons on all edges in Kiali graph
    [ ] Confirm zero plaintext connections in Prometheus
```
