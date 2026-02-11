# AWS Secrets Manager → Kubernetes Secrets Integration

## Overview

This guide explains how to inject AWS Secrets Manager secrets into Kubernetes pods for secure credential management.

## Required Secrets

### 1. Redis AUTH Token
- **AWS Secret**: Created by `terraform/modules/secrets` as `redis-auth-token`
- **K8s Environment Variable**: `REDIS_PASSWORD`
- **Used by**: ticket-service, gateway-service (if caching enabled)

### 2. RDS Credentials
- **AWS Secret**: Created by `terraform/modules/secrets` as `rds-credentials`
- **K8s Environment Variables**: `TICKET_DB_PASSWORD`, `STATS_DB_PASSWORD`
- **Used by**: ticket-service, stats-service

### 3. Queue Entry Token Secret
- **AWS Secret**: `queue-entry-token-secret`
- **K8s Environment Variable**: `QUEUE_ENTRY_TOKEN_SECRET`
- **Used by**: ticket-service

## Integration Methods

### Method 1: External Secrets Operator (RECOMMENDED)

#### Step 1: Install External Secrets Operator

```bash
helm repo add external-secrets https://charts.external-secrets.io
helm install external-secrets \
  external-secrets/external-secrets \
  -n external-secrets-system \
  --create-namespace
```

#### Step 2: Configure IRSA for External Secrets

Create IAM role with Secrets Manager access:

```hcl
# In terraform/modules/iam/main.tf (ADD THIS)

resource "aws_iam_role" "external_secrets" {
  name = "${var.name_prefix}-external-secrets-irsa"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect = "Allow"
      Principal = {
        Federated = var.oidc_provider_arn
      }
      Action = "sts:AssumeRoleWithWebIdentity"
      Condition = {
        StringEquals = {
          "${replace(var.oidc_issuer_url, "https://", "")}:sub" = "system:serviceaccount:tiketi-spring:external-secrets"
          "${replace(var.oidc_issuer_url, "https://", "")}:aud" = "sts.amazonaws.com"
        }
      }
    }]
  })
}

resource "aws_iam_role_policy" "external_secrets" {
  role = aws_iam_role.external_secrets.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect = "Allow"
      Action = [
        "secretsmanager:GetSecretValue",
        "secretsmanager:DescribeSecret"
      ]
      Resource = [
        var.redis_auth_secret_arn,
        var.rds_credentials_secret_arn,
        var.queue_token_secret_arn
      ]
    }]
  })
}
```

#### Step 3: Create SecretStore

```yaml
# k8s/spring/base/secret-store.yaml
apiVersion: external-secrets.io/v1beta1
kind: SecretStore
metadata:
  name: aws-secrets-manager
  namespace: tiketi-spring
spec:
  provider:
    aws:
      service: SecretsManager
      region: ap-northeast-2
      auth:
        jwt:
          serviceAccountRef:
            name: external-secrets
```

#### Step 4: Create ExternalSecret for Redis

```yaml
# k8s/spring/base/redis-secret.yaml
apiVersion: external-secrets.io/v1beta1
kind: ExternalSecret
metadata:
  name: redis-credentials
  namespace: tiketi-spring
spec:
  refreshInterval: 1h
  secretStoreRef:
    name: aws-secrets-manager
    kind: SecretStore
  target:
    name: redis-credentials
    creationPolicy: Owner
  data:
  - secretKey: password
    remoteRef:
      key: tiketi-redis-auth-token  # AWS Secrets Manager secret name
      property: password            # JSON key in secret
```

#### Step 5: Update Deployment to Use Secret

```yaml
# k8s/spring/base/ticket-service/deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: ticket-service
spec:
  template:
    spec:
      containers:
      - name: ticket-service
        env:
        - name: REDIS_HOST
          value: "tiketi-redis.abc123.cache.amazonaws.com"
        - name: REDIS_PORT
          value: "6379"
        - name: REDIS_PASSWORD  # ADD THIS
          valueFrom:
            secretKeyRef:
              name: redis-credentials
              key: password
        - name: REDIS_SSL
          value: "true"
```

### Method 2: Secrets Store CSI Driver (Alternative)

#### Step 1: Install CSI Driver

```bash
helm repo add secrets-store-csi-driver https://kubernetes-sigs.github.io/secrets-store-csi-driver/charts
helm install csi-secrets-store \
  secrets-store-csi-driver/secrets-store-csi-driver \
  --namespace kube-system
```

#### Step 2: Install AWS Provider

```bash
kubectl apply -f https://raw.githubusercontent.com/aws/secrets-store-csi-driver-provider-aws/main/deployment/aws-provider-installer.yaml
```

#### Step 3: Create SecretProviderClass

```yaml
apiVersion: secrets-store.csi.x-k8s.io/v1
kind: SecretProviderClass
metadata:
  name: aws-secrets
  namespace: tiketi-spring
spec:
  provider: aws
  parameters:
    objects: |
      - objectName: "tiketi-redis-auth-token"
        objectType: "secretsmanager"
        jmesPath:
          - path: password
            objectAlias: redis-password
      - objectName: "tiketi-rds-credentials"
        objectType: "secretsmanager"
        jmesPath:
          - path: password
            objectAlias: db-password
  secretObjects:
  - secretName: app-secrets
    type: Opaque
    data:
    - objectName: redis-password
      key: REDIS_PASSWORD
    - objectName: db-password
      key: DB_PASSWORD
```

#### Step 4: Mount in Pod

```yaml
spec:
  volumes:
  - name: secrets-store
    csi:
      driver: secrets-store.csi.k8s.io
      readOnly: true
      volumeAttributes:
        secretProviderClass: "aws-secrets"
  containers:
  - name: ticket-service
    volumeMounts:
    - name: secrets-store
      mountPath: "/mnt/secrets"
      readOnly: true
    env:
    - name: REDIS_PASSWORD
      valueFrom:
        secretKeyRef:
          name: app-secrets
          key: REDIS_PASSWORD
```

### Method 3: Manual K8s Secret (Development Only)

**WARNING**: Not recommended for production. Secrets are base64 encoded but not encrypted.

```bash
# Get secret from AWS
REDIS_PASSWORD=$(aws secretsmanager get-secret-value \
  --secret-id tiketi-redis-auth-token \
  --query SecretString \
  --output text | jq -r .password)

# Create K8s secret
kubectl create secret generic redis-credentials \
  --from-literal=password="$REDIS_PASSWORD" \
  -n tiketi-spring
```

## Required Application Changes

### ticket-service: application.yml

```yaml
spring:
  data:
    redis:
      host: ${REDIS_HOST}
      port: ${REDIS_PORT:6379}
      password: ${REDIS_PASSWORD}  # ADD THIS LINE
      ssl:
        enabled: ${REDIS_SSL:true}  # ADD THIS for ElastiCache transit encryption
```

### stats-service: application.yml

```yaml
spring:
  datasource:
    url: ${STATS_DB_URL:jdbc:postgresql://localhost:5432/stats_db}
    username: ${STATS_DB_USERNAME:tiketi_user}
    password: ${STATS_DB_PASSWORD}  # Already exists, ensure it's set
```

## Verification

### Check if External Secrets Operator is Working

```bash
# Check ExternalSecret status
kubectl get externalsecret -n tiketi-spring

# Check generated K8s secret
kubectl get secret redis-credentials -n tiketi-spring -o yaml

# Verify secret content (base64 decoded)
kubectl get secret redis-credentials -n tiketi-spring \
  -o jsonpath='{.data.password}' | base64 -d
```

### Test Redis Connection from Pod

```bash
# Exec into ticket-service pod
kubectl exec -it ticket-service-xxx -n tiketi-spring -- sh

# Test Redis connection with AUTH
redis-cli -h $REDIS_HOST -p $REDIS_PORT -a $REDIS_PASSWORD --tls PING
# Should return: PONG
```

## Security Best Practices

1. ✅ **Use IRSA**: Never use static AWS credentials in pods
2. ✅ **Rotate secrets regularly**: Use AWS Secrets Manager rotation
3. ✅ **Limit secret access**: Only grant necessary pods access to specific secrets
4. ✅ **Use TLS**: Enable Redis transit encryption
5. ✅ **Monitor access**: Enable CloudTrail logging for Secrets Manager API calls
6. ❌ **Never commit secrets**: Don't hardcode passwords in YAML files

## Troubleshooting

### ExternalSecret shows "SecretSyncedError"

```bash
# Check pod logs
kubectl logs -n external-secrets-system \
  deployment/external-secrets -f

# Common issues:
# - IRSA role not configured correctly
# - Secret doesn't exist in Secrets Manager
# - Wrong region specified
```

### Pod can't connect to Redis

```bash
# Check environment variables
kubectl exec -it ticket-service-xxx -n tiketi-spring -- env | grep REDIS

# Expected output:
# REDIS_HOST=tiketi-redis.abc123.cache.amazonaws.com
# REDIS_PORT=6379
# REDIS_PASSWORD=<actual-password>
# REDIS_SSL=true
```

### "NOAUTH Authentication required" error

- Missing `REDIS_PASSWORD` environment variable
- Wrong password
- Redis `auth_token_enabled` is true but password not provided

## References

- [External Secrets Operator](https://external-secrets.io/)
- [Secrets Store CSI Driver](https://secrets-store-csi-driver.sigs.k8s.io/)
- [AWS Secrets Manager Best Practices](https://docs.aws.amazon.com/secretsmanager/latest/userguide/best-practices.html)
