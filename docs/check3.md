# Comprehensive Frontend and Infrastructure Audit Report

**Project**: URR Ticketing MSA (project-ticketing-copy)
**Date**: 2026-02-12
**Scope**: Frontend (apps/web), Kubernetes (k8s), Terraform (terraform), Lambda (lambda), Scripts (scripts), Cross-Cutting Concerns

---

## Summary of Findings

| Severity | Count |
|----------|-------|
| CRITICAL | 4     |
| HIGH     | 11    |
| MEDIUM   | 12    |
| LOW      | 6     |
| **Total**| **33**|

---

## CRITICAL Findings

### C-01. Lambda@Edge Does Not Support Environment Variables

**File**: `C:\Users\USER\project-ticketing-copy\terraform\modules\cloudfront\main.tf`, lines 28-32
**File**: `C:\Users\USER\project-ticketing-copy\lambda\edge-queue-check\index.js`, lines 15-17

**Description**: The Terraform CloudFront module defines an `environment` block on the Lambda@Edge function:

```hcl
environment {
  variables = {
    QUEUE_ENTRY_TOKEN_SECRET = var.queue_entry_token_secret
  }
}
```

AWS Lambda@Edge does NOT support environment variables. This will cause a Terraform apply error at deployment time, or the Lambda function will deploy but `process.env.QUEUE_ENTRY_TOKEN_SECRET` will be `undefined`, causing the function to throw on line 17 of `index.js`:

```js
if (!SECRET) {
  throw new Error('QUEUE_ENTRY_TOKEN_SECRET environment variable is required');
}
```

This means all CloudFront viewer-request invocations will fail, effectively blocking all traffic through CloudFront.

**Fix**: Remove the `environment` block from the Lambda resource. Instead, embed the secret at build time by writing it into the Lambda deployment package (e.g., via a `.env` file in the zip, or by using a build step that injects the value). Alternatively, use AWS Systems Manager Parameter Store or Secrets Manager and fetch the secret at cold-start (note: this adds latency and requires IAM permissions, which Lambda@Edge does not support for custom policies). The recommended approach for Lambda@Edge is to bake the secret into the deployment artifact via CI/CD.

---

### C-02. secrets.env Tracked in Git Despite gitignore

**File**: `C:\Users\USER\project-ticketing-copy\k8s\spring\overlays\kind\secrets.env`

**Description**: The `.gitignore` file contains `**/secrets.env` on line 13, but the file `k8s/spring/overlays/kind/secrets.env` is already tracked by git (confirmed via `git ls-files`). This means the file and its contents -- including plaintext database passwords, JWT secrets, and internal API tokens -- are stored in the git history:

```
POSTGRES_PASSWORD=urr_password
JWT_SECRET=c3ByaW5nLWtpbmQtdGVzdC1qd3Qtc2VjcmV0LTIwMjYtMDItMTA=
INTERNAL_API_TOKEN=dev-internal-token-change-me
QUEUE_ENTRY_TOKEN_SECRET=kind-test-entry-token-secret-min-32-chars-here
```

Even though these are development/Kind credentials, the pattern of committing secrets to source control is dangerous. If production credentials are ever placed in a similarly structured file, they would be exposed.

**Fix**:
1. Run `git rm --cached k8s/spring/overlays/kind/secrets.env` to untrack the file.
2. Commit the removal.
3. Verify no `secrets.env` files remain tracked with `git ls-files | grep secrets.env`.
4. Consider using `git filter-branch` or `git filter-repo` to purge the file from history if the repository is public or shared with untrusted parties.
5. Create a `secrets.env.example` file with placeholder values for developer onboarding.

---

### C-03. Client-Side Admin Authorization Trusts localStorage

**File**: `C:\Users\USER\project-ticketing-copy\apps\web\src\components\auth-guard.tsx`, lines 14-16
**File**: `C:\Users\USER\project-ticketing-copy\apps\web\src\lib\storage.ts`, lines 23-31

**Description**: The `AuthGuard` component performs admin authorization by reading the user object from localStorage:

```tsx
const user = getUser();
const unauthorized = !token || (adminOnly && user?.role !== "admin");
```

The `getUser()` function parses JSON directly from `localStorage.getItem("user")`. Any user can open browser DevTools and execute:

```js
localStorage.setItem("user", JSON.stringify({...existingUser, role: "admin"}));
```

This grants them visual access to all admin pages and the ability to call admin API endpoints from the frontend. While backend endpoints should enforce their own authorization, this pattern creates a false sense of security and exposes the admin UI surface area.

**Fix**: The frontend admin check should be treated as a UX convenience only. Ensure that every `/api/admin/*` backend endpoint performs its own JWT role verification. Additionally, consider fetching the user profile from the server on each admin page load rather than trusting localStorage.

---

### C-04. Membership Payment Amount Read from URL Search Params

**File**: `C:\Users\USER\project-ticketing-copy\apps\web\src\app\membership-payment\[membershipId]\page.tsx`, lines 33, 43-44

**Description**: The membership payment page reads the price directly from URL search parameters:

```tsx
const price = Number(searchParams.get("price") ?? "30000");
```

This value is then sent to the payment API:

```tsx
const prepRes = await paymentsApi.prepare({
  amount: price,
  paymentType: "membership",
  referenceId: params.membershipId,
});
```

A user can modify the URL to set any arbitrary price (e.g., `?price=1`), and if the backend does not independently verify the price against the membership record, the payment will be processed for the wrong amount.

**Fix**: The backend `/api/payments/prepare` endpoint must look up the canonical membership price from the database using the `referenceId` (membershipId) and ignore the client-submitted `amount`. The frontend can still display the price from the URL for UX purposes, but the server-of-record price must prevail. Alternatively, have the frontend fetch the price from a dedicated API endpoint rather than relying on query parameters.

---

## HIGH Findings

### H-01. JWT Tokens Stored in localStorage (XSS Vulnerability)

**File**: `C:\Users\USER\project-ticketing-copy\apps\web\src\lib\storage.ts`, lines 8-16

**Description**: JWT authentication tokens are stored in `localStorage`:

```tsx
export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY);
}
export function setToken(token: string): void {
  localStorage.setItem(TOKEN_KEY, token);
}
```

localStorage is accessible to any JavaScript running on the page. If an XSS vulnerability exists anywhere in the application, an attacker can exfiltrate the JWT token and impersonate the user. Unlike HttpOnly cookies, localStorage provides no built-in protection against script access.

**Fix**: Store authentication tokens in HttpOnly, Secure, SameSite cookies instead of localStorage. The backend should set the cookie on login and the browser will automatically include it in requests. This eliminates the XSS token theft vector entirely.

---

### H-02. CLOUDFRONT_SECRET Stored in ConfigMap Instead of Secret

**File**: `C:\Users\USER\project-ticketing-copy\k8s\spring\overlays\prod\patches\services-env.yaml`, lines 38-42
**File**: `C:\Users\USER\project-ticketing-copy\k8s\spring\overlays\kind\patches\gateway-service.yaml`, lines 39-43

**Description**: The `CLOUDFRONT_SECRET` is referenced from a ConfigMap:

```yaml
- name: CLOUDFRONT_SECRET
  valueFrom:
    configMapKeyRef:
      name: spring-prod-config
      key: CLOUDFRONT_SECRET
```

ConfigMaps are not encrypted at rest in etcd (unless etcd encryption is configured separately), are visible to anyone with read access to the namespace, and are not treated as sensitive data by Kubernetes RBAC defaults. A secret value like `CLOUDFRONT_SECRET` should be stored in a Kubernetes Secret object.

**Fix**: Move `CLOUDFRONT_SECRET` from `config.env` to `secrets.env` in both Kind and Prod overlays. Update the references from `configMapKeyRef` to `secretKeyRef`:

```yaml
- name: CLOUDFRONT_SECRET
  valueFrom:
    secretKeyRef:
      name: spring-prod-secret
      key: CLOUDFRONT_SECRET
```

---

### H-03. Database URLs in ConfigMap Instead of Secret

**File**: `C:\Users\USER\project-ticketing-copy\k8s\spring\overlays\prod\patches\services-env.yaml`, lines 68-72, 96-99, 130-134, 161-165, 228-232
**File**: `C:\Users\USER\project-ticketing-copy\k8s\spring\overlays\prod\config.env`, lines 1-5

**Description**: Database connection URLs (containing hostnames and database names) are stored in ConfigMaps and referenced via `configMapKeyRef`:

```
AUTH_DB_URL=jdbc:postgresql://CHANGE_ME_RDS_ENDPOINT:5432/auth_db
TICKET_DB_URL=jdbc:postgresql://CHANGE_ME_RDS_ENDPOINT:5432/ticket_db
```

While the current placeholder values do not contain credentials, the JDBC URL pattern for production often includes credentials in the format `jdbc:postgresql://user:password@host:port/db`. Even if credentials are separate, the database endpoint hostname is sensitive infrastructure information.

**Fix**: Move `*_DB_URL` values to `secrets.env` and reference via `secretKeyRef`. Database usernames and passwords are already in secrets, but the URLs should be there too, or use a pattern where the URL is constructed from non-sensitive host + sensitive credentials.

---

### H-04. Prod Config Uses In-Cluster Redis/Kafka/Zipkin Instead of Managed Services

**File**: `C:\Users\USER\project-ticketing-copy\k8s\spring\overlays\prod\config.env`, lines 12-16

**Description**: The production configuration points to in-cluster services:

```
REDIS_HOST=dragonfly-spring
KAFKA_BOOTSTRAP_SERVERS=kafka-spring:9092
ZIPKIN_ENDPOINT=http://zipkin-spring:9411/api/v2/spans
```

These are the same hostnames used in the Kind (local development) overlay. In a production environment, these should reference managed services (e.g., Amazon ElastiCache for Redis, Amazon MSK for Kafka, AWS X-Ray or managed Jaeger for tracing) for reliability, persistence, and scalability.

**Fix**: Update production config to use managed service endpoints. For Redis, use an ElastiCache endpoint. For Kafka, use an MSK bootstrap server list. For tracing, use the OTEL collector endpoint or X-Ray daemon. If in-cluster services are intentional for cost reasons, add a comment documenting this decision.

---

### H-05. No Security Contexts on Any K8s Deployments

**File**: `C:\Users\USER\project-ticketing-copy\k8s\spring\base\gateway-service\deployment.yaml` (and all other base deployment.yaml files)

**Description**: None of the base Kubernetes deployments define a `securityContext`. This means containers run as root by default, have full write access to the filesystem, and can potentially escalate privileges:

```yaml
containers:
  - name: gateway-service
    image: YOUR_ECR_URI/gateway-service:latest
    # No securityContext defined
```

**Fix**: Add security contexts to all deployments (in base or as a prod patch):

```yaml
spec:
  template:
    spec:
      securityContext:
        runAsNonRoot: true
        runAsUser: 1000
        fsGroup: 1000
      containers:
        - name: gateway-service
          securityContext:
            allowPrivilegeEscalation: false
            readOnlyRootFilesystem: true
            capabilities:
              drop: ["ALL"]
```

---

### H-06. No Network Policies in K8s

**File**: `C:\Users\USER\project-ticketing-copy\k8s\spring\base\kustomization.yaml`
**File**: `C:\Users\USER\project-ticketing-copy\k8s\spring\overlays\prod\kustomization.yaml`

**Description**: Neither the base nor prod Kustomization references any NetworkPolicy resources. Without NetworkPolicies, any pod in the cluster can communicate with any other pod. This means a compromised container (e.g., the frontend) could directly access database pods, Redis, or internal service endpoints.

**Fix**: Create NetworkPolicy manifests that restrict ingress/egress per service. For example, `ticket-service` should only accept traffic from `gateway-service` and `payment-service`, not from `frontend` or external pods.

---

### H-07. Lambda ticket-worker Defaults INTERNAL_API_TOKEN to Empty String

**File**: `C:\Users\USER\project-ticketing-copy\lambda\ticket-worker\index.js`, line 15

**Description**: The internal API token defaults to an empty string if not set:

```js
const INTERNAL_API_TOKEN = process.env.INTERNAL_API_TOKEN || '';
```

If the environment variable is missing during deployment, the Lambda will make authenticated requests with `Authorization: Bearer ` (empty token). Depending on the backend's handling, this could either fail silently or bypass authentication if the backend treats empty Bearer tokens as anonymous.

**Fix**: Throw an error on startup if `INTERNAL_API_TOKEN` is not set, similar to how the edge function handles `QUEUE_ENTRY_TOKEN_SECRET`:

```js
const INTERNAL_API_TOKEN = process.env.INTERNAL_API_TOKEN;
if (!INTERNAL_API_TOKEN) {
  throw new Error('INTERNAL_API_TOKEN environment variable is required');
}
```

---

### H-08. News Page Write Access for All Authenticated Users

**File**: `C:\Users\USER\project-ticketing-copy\apps\web\src\app\news\page.tsx`, line 35

**Description**: The news page shows the "Write" button to any logged-in user:

```tsx
{token && (
  <Link href="/news/create" ...>글쓰기</Link>
)}
```

This allows any authenticated user to navigate to the news creation page and submit articles to what appears to be an announcement/notice board ("공지사항"). While the backend may enforce admin-only access on the POST `/api/news` endpoint, the UI gives the impression that all users can create announcements.

**Fix**: Either (a) check the user's role on the frontend and only show the button to admins: `{user?.role === "admin" && ...}`, or (b) if this is intentionally a community board, rename it from "공지사항" (Announcements) to something more appropriate.

---

### H-09. Missing HorizontalPodAutoscaler for Prod

**File**: `C:\Users\USER\project-ticketing-copy\k8s\spring\overlays\prod\kustomization.yaml`

**Description**: The prod overlay defines static replica counts in `patches/replicas.yaml` but no HorizontalPodAutoscaler (HPA) resources. For a ticketing system that experiences extreme traffic spikes during on-sale events, static replicas are insufficient. The gateway (3 replicas), ticket-service (3 replicas), and queue-service (3 replicas) need to scale dynamically.

**Fix**: Create HPA resources for at least gateway-service, ticket-service, and queue-service:

```yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: gateway-service-hpa
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: gateway-service
  minReplicas: 3
  maxReplicas: 20
  metrics:
    - type: Resource
      resource:
        name: cpu
        target:
          type: Utilization
          averageUtilization: 70
```

---

### H-10. Entry Token Cookie Secure Flag Incompatible with HTTP Localhost

**File**: `C:\Users\USER\project-ticketing-copy\apps\web\src\hooks\use-queue-polling.ts`, line 49
**File**: `C:\Users\USER\project-ticketing-copy\apps\web\src\app\queue\[eventId]\page.tsx`, line 52

**Description**: The entry token cookie is set with the `Secure` flag:

```js
document.cookie = `urr-entry-token=${data.entryToken}; path=/; max-age=600; SameSite=Strict; Secure`;
```

The `Secure` flag instructs the browser to only send the cookie over HTTPS connections. During local development (http://localhost:3000), the cookie will be set by the browser (localhost is a special case for some browsers) but behavior is inconsistent across browsers. Chrome treats localhost as a "potentially trustworthy origin" and allows it, but other browsers may not.

**Fix**: Conditionally set the `Secure` flag based on the current protocol:

```js
const secure = window.location.protocol === 'https:' ? '; Secure' : '';
document.cookie = `urr-entry-token=${data.entryToken}; path=/; max-age=600; SameSite=Strict${secure}`;
```

---

### H-11. Docker Image Copies Full node_modules Instead of Standalone Output

**File**: `C:\Users\USER\project-ticketing-copy\apps\web\Dockerfile`, lines 23-26

**Description**: The production stage copies the entire `node_modules` directory:

```dockerfile
COPY --from=builder /app/node_modules ./node_modules
```

Next.js supports a `standalone` output mode that produces a self-contained server with only the necessary dependencies. The current approach results in a much larger Docker image (potentially hundreds of MB larger), slower pull times, and a larger attack surface.

**Fix**: Enable standalone output in `next.config.js`:

```js
module.exports = { output: 'standalone' }
```

Then update the Dockerfile to copy only the standalone output:

```dockerfile
COPY --from=builder /app/.next/standalone ./
COPY --from=builder /app/.next/static ./.next/static
COPY --from=builder /app/public ./public
CMD ["node", "server.js"]
```

---

## MEDIUM Findings

### M-01. entryToken Field Used But Not in QueueStatus Type

**File**: `C:\Users\USER\project-ticketing-copy\apps\web\src\lib\types.ts`, lines 120-135
**File**: `C:\Users\USER\project-ticketing-copy\apps\web\src\hooks\use-queue-polling.ts`, line 48
**File**: `C:\Users\USER\project-ticketing-copy\apps\web\src\app\queue\[eventId]\page.tsx`, line 51

**Description**: The code accesses `data.entryToken` from the queue API response, but the `QueueStatus` interface does not include an `entryToken` field:

```tsx
// types.ts - no entryToken field
export interface QueueStatus {
  status?: "queued" | "active" | "not_in_queue";
  queued: boolean;
  position?: number;
  // ...
}

// use-queue-polling.ts
if (data.entryToken) { // TypeScript should flag this
```

This indicates either a TypeScript strict mode gap or the use of `any` type leaking through axios response types.

**Fix**: Add `entryToken?: string;` to the `QueueStatus` interface in `types.ts`.

---

### M-02. Duplicated resolveBaseUrl() Function

**File**: `C:\Users\USER\project-ticketing-copy\apps\web\src\lib\api-client.ts`, lines 7-35
**File**: `C:\Users\USER\project-ticketing-copy\apps\web\src\hooks\use-server-time.ts`, lines 5-12

**Description**: The `resolveBaseUrl()` function is duplicated in two files with slightly different implementations. The api-client version has more robust detection (including `window.__API_URL` and private IP detection), while the use-server-time version is simpler.

**Fix**: Export `resolveBaseUrl()` from `api-client.ts` and import it in `use-server-time.ts` to maintain a single source of truth.

---

### M-03. No Error Boundary in Root Layout

**File**: `C:\Users\USER\project-ticketing-copy\apps\web\src\app\layout.tsx`, lines 22-31

**Description**: The root layout renders children directly without any error boundary:

```tsx
export default function RootLayout({ children }) {
  return (
    <html lang="ko">
      <body>
        <SiteHeader />
        <main>{children}</main>
      </body>
    </html>
  );
}
```

An unhandled error in any page component will crash the entire application and show a white screen. Next.js App Router supports `error.tsx` files, but without a root-level error boundary, deeply nested errors may not be caught gracefully.

**Fix**: Create `C:\Users\USER\project-ticketing-copy\apps\web\src\app\error.tsx` with a user-friendly error UI and retry button. Also consider creating `global-error.tsx` for root layout errors.

---

### M-04. dangerouslySetInnerHTML in Admin Page

**File**: `C:\Users\USER\project-ticketing-copy\apps\web\src\app\admin\page.tsx`, line 60

**Description**: The admin dashboard uses `dangerouslySetInnerHTML` to render card icons:

```tsx
<div className="text-2xl mb-3" dangerouslySetInnerHTML={{ __html: card.icon }} />
```

While the current values are hardcoded HTML entities (`&#127915;`, `&#127903;`, `&#128200;`), the use of `dangerouslySetInnerHTML` establishes a dangerous pattern. If a future developer changes the `cards` array to include dynamic data, this becomes an XSS vector.

**Fix**: Replace HTML entities with Unicode characters or React-compatible emoji rendering:

```tsx
const cards = [
  { icon: "\uD83C\uDFAB", label: "이벤트 관리", ... },
  // or simply use: icon: "🎫"
];
// Then render: <div>{card.icon}</div>
```

---

### M-05. CORS_ALLOWED_ORIGINS in Prod ConfigMap Is Hardcoded

**File**: `C:\Users\USER\project-ticketing-copy\k8s\spring\overlays\prod\config.env`, line 21
**File**: `C:\Users\USER\project-ticketing-copy\k8s\spring\overlays\prod\patches\services-env.yaml`, lines 43-47

**Description**: The production CORS allowed origins are hardcoded in the ConfigMap:

```
CORS_ALLOWED_ORIGINS=https://urr.guru,https://www.urr.guru
```

This is referenced by the gateway service via `configMapKeyRef`. While not a security issue per se, changing CORS origins requires a ConfigMap update and pod restart. The value should be easily configurable and the gateway should validate the `Origin` header against this list strictly.

**Fix**: Verify that the gateway service's CORS middleware strictly validates against this allowlist and does not use wildcard matching or reflection patterns.

---

### M-06. PDB Missing for auth-service, stats-service, community-service, and frontend

**File**: `C:\Users\USER\project-ticketing-copy\k8s\spring\overlays\prod\pdb.yaml`, lines 1-40

**Description**: PodDisruptionBudgets are defined only for gateway-service, ticket-service, queue-service, and payment-service. Missing PDBs for auth-service (2 replicas), stats-service (2 replicas), community-service (2 replicas), and frontend. During node drains or cluster upgrades, these services could have all pods simultaneously disrupted.

**Fix**: Add PDB resources for all services with 2+ replicas:

```yaml
---
apiVersion: policy/v1
kind: PodDisruptionBudget
metadata:
  name: auth-service-pdb
spec:
  minAvailable: 1
  selector:
    matchLabels:
      app: auth-service
```

---

### M-07. IAM Policies Fall Back to Wildcard Resource

**File**: `C:\Users\USER\project-ticketing-copy\terraform\modules\iam\main.tf`, line 122
**File**: `C:\Users\USER\project-ticketing-copy\terraform\modules\iam\main.tf`, line 220

**Description**: Two IAM policies fall back to `Resource: ["*"]` when ARN variables are empty:

```hcl
Resource = var.sqs_queue_arn != "" ? [var.sqs_queue_arn] : ["*"]
```

```hcl
Resource = var.db_credentials_secret_arn != "" ? [var.db_credentials_secret_arn] : ["*"]
```

If these variables are not properly set during Terraform apply, the Lambda worker gets SQS access to ALL queues in the account, and the RDS Proxy gets Secrets Manager access to ALL secrets.

**Fix**: Remove the wildcard fallbacks and make the ARN variables required (non-empty) with validation:

```hcl
variable "sqs_queue_arn" {
  type = string
  validation {
    condition     = length(var.sqs_queue_arn) > 0
    error_message = "sqs_queue_arn must not be empty"
  }
}
```

---

### M-08. Base Deployments Use Placeholder Image URIs

**File**: `C:\Users\USER\project-ticketing-copy\k8s\spring\base\gateway-service\deployment.yaml`, line 19

**Description**: All base deployments use `YOUR_ECR_URI/service-name:latest` as the image reference. While the Kind overlay overrides these with `images:` in kustomization.yaml, the prod overlay does NOT define image overrides. This means production would attempt to pull from `YOUR_ECR_URI/`, which would fail.

**Fix**: Add an `images:` section to the prod kustomization.yaml that maps `YOUR_ECR_URI/` to actual ECR repository URIs, or use a CI/CD pipeline that patches images at deploy time.

---

### M-09. Google Client ID Exposed in Kind config.env

**File**: `C:\Users\USER\project-ticketing-copy\k8s\spring\overlays\kind\config.env`, line 11

**Description**: A Google OAuth Client ID is present in the Kind config:

```
GOOGLE_CLIENT_ID=721028631258-dhjgd4gquphib49fsoitiubusbo3t9e9.apps.googleusercontent.com
```

While Client IDs are generally considered public (they are embedded in frontend code), having a real Google OAuth Client ID in a development config that is committed to version control could lead to confusion about which environments use which OAuth app, and the ID could be used for phishing if the associated Google project has permissive redirect URIs.

**Fix**: Use a separate Google OAuth app for development with restricted redirect URIs, or replace with a placeholder value.

---

### M-10. SQS_ENABLED Stored in ConfigMap Instead of Direct Value

**File**: `C:\Users\USER\project-ticketing-copy\k8s\spring\overlays\prod\patches\services-env.yaml`, lines 198-202

**Description**: The queue-service references `SQS_ENABLED` and `SQS_QUEUE_URL` from ConfigMap:

```yaml
- name: SQS_ENABLED
  valueFrom:
    configMapKeyRef:
      name: spring-prod-config
      key: SQS_ENABLED
```

The prod config.env has `SQS_QUEUE_URL=CHANGE_ME_IN_PRODUCTION`. If this is not updated before deployment, the queue-service will attempt to use SQS with an invalid queue URL while `SQS_ENABLED=true`.

**Fix**: Add a validation step in the deployment pipeline that checks for `CHANGE_ME` placeholders in config before applying to production.

---

### M-11. Payment Success Page Uses English While App is Korean

**File**: `C:\Users\USER\project-ticketing-copy\apps\web\src\app\payment-success\[reservationId]\page.tsx`, lines 11-13

**Description**: This page uses English text:

```tsx
<h1>Payment Successful</h1>
<p>Reservation <span>{reservationId}</span> has been confirmed.</p>
```

The rest of the application is consistently in Korean (e.g., "결제 실패", "내 예매", "홈으로 돌아가기"). This creates an inconsistent user experience.

**Fix**: Translate to Korean: "결제 완료", "예매 {reservationId}이(가) 확인되었습니다."

---

### M-12. CloudFront Custom Error Responses Expose Application Structure

**File**: `C:\Users\USER\project-ticketing-copy\terraform\modules\cloudfront\main.tf`, lines 172-184

**Description**: Both 404 and 403 errors are mapped to return HTTP 200 with `/index.html`:

```hcl
custom_error_response {
  error_code         = 404
  response_code      = 200
  response_page_path = "/index.html"
}
custom_error_response {
  error_code         = 403
  response_code      = 200
  response_page_path = "/index.html"
}
```

This is typical for SPA routing, but it means that 403 Forbidden responses (which might indicate authorization failures) are silently turned into 200 OK with the SPA shell. API requests routed through CloudFront that return 404 or 403 from the origin will have their status codes masked.

**Fix**: Restrict these custom error responses to only apply to the S3 static asset origin, not the ALB API origin. Or use path-based cache behaviors to ensure API responses are never rewritten.

---

## LOW Findings

### L-01. statusBadge Utility Function Duplicated Across Pages

**Description**: Multiple pages (reservations, admin/reservations, transfers) contain similar inline `statusBadge()` helper functions that map status strings to CSS classes and Korean labels. This is duplicated logic.

**Fix**: Extract a shared `statusBadge(status: string)` utility function into `C:\Users\USER\project-ticketing-copy\apps\web\src\lib\format.ts` or a dedicated `C:\Users\USER\project-ticketing-copy\apps\web\src\lib\status.ts`.

---

### L-02. Inconsistent Type Field Naming (snake_case vs camelCase)

**File**: `C:\Users\USER\project-ticketing-copy\apps\web\src\lib\types.ts`, lines 26-40

**Description**: The `EventDetail` interface defines both snake_case and camelCase versions of the same fields:

```tsx
export interface EventDetail extends EventSummary {
  event_date?: string;     // from EventSummary
  eventDate?: string;      // camelCase alias
  seat_layout_id?: string | null;
  seatLayoutId?: string | null;
  ticket_types?: TicketType[];
  ticketTypes?: TicketType[];
}
```

This dual representation adds complexity and creates ambiguity about which field will be populated.

**Fix**: Normalize the API response at the boundary (in api-client.ts interceptors or a transformation layer) to a single casing convention, and use only that convention in type definitions.

---

### L-03. Empty catch Blocks Suppress Errors Silently

**File**: `C:\Users\USER\project-ticketing-copy\apps\web\src\app\admin\page.tsx`, line 15
**File**: `C:\Users\USER\project-ticketing-copy\apps\web\src\app\queue\[eventId]\page.tsx`, line 55

**Description**: Several API calls use empty catch blocks:

```tsx
adminApi.dashboard().then(...).catch(() => {});
queueApi.check(eventId).then(...).catch(() => setJoined(true));
```

Errors are silently swallowed with no logging, user notification, or retry logic.

**Fix**: At minimum, log errors to the console. Preferably, show a toast notification or error state to the user.

---

### L-04. start-all.ps1 Hardcodes Development Secrets

**File**: `C:\Users\USER\project-ticketing-copy\scripts\start-all.ps1`, lines 20-30

**Description**: The script sets plaintext development secrets as environment variables:

```powershell
$env:JWT_SECRET = "local-dev-jwt-secret-minimum-32-characters-long"
$env:INTERNAL_API_TOKEN = "local-dev-internal-api-token"
```

While these are development values, hardcoding secrets in scripts that are committed to version control normalizes the practice of embedding secrets in code.

**Fix**: Read development secrets from a `.env.local` file that is gitignored, or document that these values are intentionally non-sensitive development defaults.

---

### L-05. Base Kustomization Uses urr-dev Namespace While Overlays Use urr-spring

**File**: `C:\Users\USER\project-ticketing-copy\k8s\spring\base\kustomization.yaml`, line 3
**File**: `C:\Users\USER\project-ticketing-copy\k8s\spring\overlays\kind\kustomization.yaml`, line 3
**File**: `C:\Users\USER\project-ticketing-copy\k8s\spring\overlays\prod\kustomization.yaml`, line 3

**Description**: The base kustomization sets `namespace: urr-dev`, while both overlays set `namespace: urr-spring`. The overlay namespace correctly overrides the base, but the base namespace is misleading and could cause confusion if someone applies the base directly.

**Fix**: Either remove the namespace from the base (let overlays define it) or set it to a neutral value like `urr-base` with a comment that overlays override this.

---

### L-06. Lambda@Edge Redirect Function Uses Inconsistent Path Matching

**File**: `C:\Users\USER\project-ticketing-copy\lambda\edge-queue-check\index.js`, lines 137-141, 146-149

**Description**: The `extractEventIdFromPath()` function matches UUIDs from `/api/seats|reservations|tickets/{uuid}` paths, but the `redirectToQueue()` function tries to extract eventId from `/event/{id}` (without the `/api` prefix). These patterns target different URL structures, and the redirect may produce incorrect queue URLs.

**Fix**: Align the path extraction patterns to consistently use the actual API URL structure, or pass the eventId from the already-extracted claims rather than re-parsing the URL.

---

## Cross-Cutting Concern Analysis

### Port Mapping Consistency: PASS

All service port mappings are consistent across configurations:
- gateway-service: 3001
- ticket-service: 3002
- payment-service: 3003
- stats-service: 3004
- auth-service: 3005
- queue-service: 3007
- community-service: 3008
- frontend: 3000

These match across base deployments, Kind patches, Prod patches, scripts/start-all.ps1, and api-client.ts.

### Service URL Consistency: PASS

Internal service URLs (`http://service-name:port`) are consistent between the Kind config.env, Prod config.env, and the explicit `env:` values in services-env.yaml patches.

### ConfigMap/Secret Reference Consistency: PASS

All services in both Kind and Prod overlays reference the correct ConfigMap and Secret names (`spring-kind-config`/`spring-kind-secret` and `spring-prod-config`/`spring-prod-secret` respectively).

### Environment Variable Coverage: PASS WITH NOTES

All environment variables used by services in the deployment patches have corresponding entries in either config.env or secrets.env. However, the prod `config.env` contains multiple `CHANGE_ME` placeholder values that must be replaced before deployment.

---

## Recommendations by Priority

### Immediate (Block Production Deployment)
1. **C-01**: Remove environment block from Lambda@Edge Terraform resource
2. **C-02**: Untrack secrets.env from git, purge from history
3. **C-04**: Backend must validate membership payment amount server-side
4. **H-07**: Lambda ticket-worker must fail if INTERNAL_API_TOKEN is unset
5. **M-08**: Add image overrides to prod kustomization.yaml

### Before Production Traffic
6. **H-02, H-03**: Move sensitive values from ConfigMap to Secret
7. **H-04**: Configure managed service endpoints for prod
8. **H-05**: Add security contexts to all deployments
9. **H-06**: Implement network policies
10. **H-09**: Create HPA resources for traffic-facing services
11. **M-07**: Remove wildcard IAM resource fallbacks

### Short-Term Improvements
12. **C-03**: Add server-side admin API authorization verification
13. **H-01**: Migrate token storage from localStorage to HttpOnly cookies
14. **H-08**: Fix news page write access control
15. **H-10**: Conditional Secure flag on entry token cookie
16. **H-11**: Use Next.js standalone output for Docker

### Ongoing Quality
17. **M-01 through M-12**: Address type safety, code duplication, error handling, and i18n consistency
18. **L-01 through L-06**: Clean up code duplication, naming conventions, and error suppression
