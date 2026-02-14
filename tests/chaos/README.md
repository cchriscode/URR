# Chaos Engineering Tests

Chaos test suite for the URR ticketing platform. These k6 scripts validate
that the system degrades gracefully under infrastructure failures -- service
outages, network latency, and Redis unavailability.

## Prerequisites

### k6

Install the k6 load testing tool.

**Windows (winget):**

```powershell
winget install grafana.k6
```

**macOS (Homebrew):**

```bash
brew install k6
```

**Linux (apt):**

```bash
sudo gpg -k
sudo gpg --no-default-keyring --keyring /usr/share/keyrings/k6-archive-keyring.gpg \
  --keyserver hkp://keyserver.ubuntu.com:80 --recv-keys C5AD17C747E3415A3642D57D77C6C491D6AC1D68
echo "deb [signed-by=/usr/share/keyrings/k6-archive-keyring.gpg] https://dl.k6.io/deb stable main" \
  | sudo tee /etc/apt/sources.list.d/k6.list
sudo apt-get update && sudo apt-get install k6
```

### kubectl

A working `kubectl` configured to target your Kind cluster.

### Chaos Mesh

Chaos Mesh is a Kubernetes-native chaos engineering platform.  It provides
CRDs (`PodChaos`, `NetworkChaos`, etc.) that let you inject faults
declaratively.

## Installing Chaos Mesh on Kind

1. Make sure your Kind cluster is running:

```bash
kind get clusters
```

2. Install Chaos Mesh via Helm:

```bash
helm repo add chaos-mesh https://charts.chaos-mesh.org
helm repo update

kubectl create namespace chaos-mesh

helm install chaos-mesh chaos-mesh/chaos-mesh \
  --namespace chaos-mesh \
  --set chaosDaemon.runtime=containerd \
  --set chaosDaemon.socketPath=/run/containerd/containerd.sock \
  --version 2.7.1
```

3. Verify the installation:

```bash
kubectl get pods -n chaos-mesh
```

All three components should be running: `chaos-controller-manager`,
`chaos-daemon`, and `chaos-dashboard`.

## Running the k6 Scripts

### Basic Usage

Each script targets the gateway service.  The base URL defaults to
`http://localhost:30080` (the Kind NodePort) and can be overridden via the
`BASE_URL` environment variable.

```bash
# Service failure scenario (50 VUs, 3 minutes)
k6 run tests/chaos/scenarios/service-failure.js

# Network latency scenario (up to 100 VUs, 5 minutes)
k6 run tests/chaos/scenarios/network-latency.js

# Redis failure scenario (200 VUs, 2 minutes)
k6 run tests/chaos/scenarios/redis-failure.js
```

### Custom Base URL

```bash
k6 run -e BASE_URL=http://localhost:3001 tests/chaos/scenarios/service-failure.js
```

### Running with Reduced VUs (Local Development)

```bash
k6 run --vus 10 --duration 30s tests/chaos/scenarios/service-failure.js
```

## Chaos Mesh Experiment Manifests

Apply these manifests **before** or **during** a k6 run to inject the
corresponding fault.  Remove them afterward with `kubectl delete`.

### PodChaos: Kill Catalog Service

Kills the catalog-service pod every 30 seconds for the duration of the
experiment.  The Kubernetes deployment will restart the pod, but there will be
a window of unavailability on each kill.

```yaml
# chaos-kill-catalog.yaml
apiVersion: chaos-mesh.org/v1alpha1
kind: PodChaos
metadata:
  name: kill-catalog-service
  namespace: default
spec:
  action: pod-kill
  mode: one
  selector:
    namespaces:
      - default
    labelSelectors:
      app: catalog-service
  scheduler:
    cron: "*/30 * * * * *"
  duration: "3m"
```

```bash
kubectl apply -f chaos-kill-catalog.yaml
k6 run tests/chaos/scenarios/service-failure.js
kubectl delete -f chaos-kill-catalog.yaml
```

### PodChaos: Kill Ticket Service

```yaml
# chaos-kill-ticket.yaml
apiVersion: chaos-mesh.org/v1alpha1
kind: PodChaos
metadata:
  name: kill-ticket-service
  namespace: default
spec:
  action: pod-kill
  mode: all
  selector:
    namespaces:
      - default
    labelSelectors:
      app: ticket-service
  scheduler:
    cron: "*/45 * * * * *"
  duration: "3m"
```

### NetworkChaos: Inject Latency on Backend Services

Adds 3 seconds of latency (with 1 second of jitter) to all traffic reaching
backend service pods.  Use this with the `network-latency.js` script.

```yaml
# chaos-network-latency.yaml
apiVersion: chaos-mesh.org/v1alpha1
kind: NetworkChaos
metadata:
  name: backend-latency
  namespace: default
spec:
  action: delay
  mode: all
  selector:
    namespaces:
      - default
    labelSelectors:
      tier: backend
  delay:
    latency: "3s"
    jitter: "1s"
    correlation: "50"
  direction: to
  duration: "5m"
```

```bash
kubectl apply -f chaos-network-latency.yaml
k6 run tests/chaos/scenarios/network-latency.js
kubectl delete -f chaos-network-latency.yaml
```

### PodChaos: Kill Redis

Kills the Redis pod to test fail-open rate limiting and queue service
degradation.  Use this with the `redis-failure.js` script.

```yaml
# chaos-kill-redis.yaml
apiVersion: chaos-mesh.org/v1alpha1
kind: PodChaos
metadata:
  name: kill-redis
  namespace: default
spec:
  action: pod-kill
  mode: all
  selector:
    namespaces:
      - default
    labelSelectors:
      app: redis
  duration: "2m"
```

```bash
kubectl apply -f chaos-kill-redis.yaml
k6 run tests/chaos/scenarios/redis-failure.js
kubectl delete -f chaos-kill-redis.yaml
```

### NetworkChaos: Block Redis Port

An alternative to killing Redis -- blocks all traffic on port 6379 so that
the Redis process stays alive but is unreachable.  This tests connection
timeout handling rather than connection-refused handling.

```yaml
# chaos-block-redis-port.yaml
apiVersion: chaos-mesh.org/v1alpha1
kind: NetworkChaos
metadata:
  name: block-redis-port
  namespace: default
spec:
  action: partition
  mode: all
  selector:
    namespaces:
      - default
    labelSelectors:
      app: redis
  direction: both
  duration: "2m"
```

## Interpreting Results

### Key Metrics

| Metric | Script | Meaning |
|--------|--------|---------|
| `graceful_degradation` | service-failure | Percentage of responses that are structured errors, not raw 500s |
| `raw_500_errors` | service-failure | Count of unstructured 500 responses |
| `timeout_handled_gracefully` | network-latency | Percentage of responses where the timeout was enforced cleanly |
| `rate_limit_fail_open` | redis-failure | Percentage of requests that were NOT spuriously 429'd |
| `spurious_429_responses` | redis-failure | Count of 429s that occurred without a working Redis |
| `queue_graceful_degradation` | redis-failure | Percentage of queue requests that degraded cleanly |

### Threshold Failures

If any threshold fails, k6 exits with a non-zero exit code.  Common causes:

- **`graceful_degradation` below 90%**: The gateway or a service is returning
  unstructured HTML error pages instead of JSON.  Check the Spring error
  handling configuration and the gateway's error filters.

- **`rate_limit_fail_open` below 95%**: The `RateLimitFilter` is not catching
  Redis exceptions correctly.  Review the `catch` block in
  `RateLimitFilter.doFilterInternal()`.

- **`http_req_duration` p95 above threshold**: The gateway is not enforcing
  its own timeout.  Check the Spring Cloud Gateway timeout configuration and
  verify that circuit breakers are configured on downstream routes.

## Test Execution Order

For a full chaos engineering session, run the scripts in this order:

1. **Baseline** (no faults): Run each script without any Chaos Mesh experiments
   to establish normal-operation metrics.
2. **Service failure**: Apply `chaos-kill-catalog.yaml`, run `service-failure.js`.
3. **Network latency**: Apply `chaos-network-latency.yaml`, run `network-latency.js`.
4. **Redis failure**: Apply `chaos-kill-redis.yaml`, run `redis-failure.js`.

Compare chaos results against the baseline to quantify degradation.
