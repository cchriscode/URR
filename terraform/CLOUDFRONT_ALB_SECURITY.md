# CloudFront → ALB Security Best Practices

## Current Security Implementation

### Layer 1: Network Security (✅ IMPLEMENTED)

**ALB Security Group restricted to CloudFront Prefix List**

```hcl
# terraform/modules/alb/main.tf
resource "aws_security_group_rule" "alb_ingress_https_cloudfront" {
  type            = "ingress"
  from_port       = 443
  to_port         = 443
  protocol        = "tcp"
  prefix_list_ids = [var.cloudfront_prefix_list_id]  # pl-22a6434b
  security_group_id = aws_security_group.alb.id
}
```

**Security Level**: HIGH
- Blocks ALL direct access to ALB
- Only CloudFront IPs can reach ALB
- AWS-managed prefix list auto-updates

### Layer 2: Custom Header Validation (OPTIONAL)

**Add CloudFront custom header validation to ALB listener**

#### Why Use Custom Headers?

- **Defense in Depth**: Extra layer beyond network restrictions
- **Protection against**: Misconfigured security groups or compromised CloudFront
- **Compliance**: Some security frameworks require application-layer validation

#### Implementation

**Step 1: Generate Secret Header Value**

```bash
# Generate random secret
CUSTOM_HEADER_SECRET=$(openssl rand -hex 32)
echo $CUSTOM_HEADER_SECRET
```

**Step 2: Add to CloudFront Origin Custom Headers**

Already configured in `terraform/modules/cloudfront/main.tf`:

```hcl
origin {
  domain_name = var.alb_dns_name
  origin_id   = "alb"

  custom_header {
    name  = "X-CloudFront-Verified"
    value = var.cloudfront_custom_header_value  # Use secret here
  }
}
```

**Step 3: Add ALB Listener Rule to Validate Header**

Create new file: `terraform/modules/alb/listener-rules.tf`

```hcl
# Optional: Add header validation for defense-in-depth
resource "aws_lb_listener_rule" "validate_cloudfront_header" {
  count        = var.enable_custom_header_validation && var.certificate_arn != "" ? 1 : 0
  listener_arn = aws_lb_listener.https[0].arn
  priority     = 1  # Highest priority

  action {
    type = "fixed-response"
    fixed_response {
      content_type = "text/plain"
      message_body = "Forbidden"
      status_code  = "403"
    }
  }

  condition {
    http_header {
      http_header_name = "X-CloudFront-Verified"
      values           = [var.cloudfront_custom_header_value]
    }
  }

  # IMPORTANT: This rule BLOCKS requests WITH the header
  # We need to invert this logic by making default action forward
  # and using this rule to REJECT requests WITHOUT the header
}
```

**Better approach**: Use default action to reject, and condition to allow:

```hcl
# Modify HTTPS listener default action
resource "aws_lb_listener" "https" {
  ...

  # Default action: Reject all
  default_action {
    type = var.enable_custom_header_validation ? "fixed-response" : "forward"

    dynamic "fixed_response" {
      for_each = var.enable_custom_header_validation ? [1] : []
      content {
        content_type = "text/plain"
        message_body = "Forbidden - Invalid origin"
        status_code  = "403"
      }
    }

    target_group_arn = !var.enable_custom_header_validation ? aws_lb_target_group.gateway_service.arn : null
  }
}

# Rule to allow traffic WITH valid header
resource "aws_lb_listener_rule" "allow_cloudfront" {
  count        = var.enable_custom_header_validation && var.certificate_arn != "" ? 1 : 0
  listener_arn = aws_lb_listener.https[0].arn
  priority     = 1

  action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.gateway_service.arn
  }

  condition {
    http_header {
      http_header_name = "X-CloudFront-Verified"
      values           = [var.cloudfront_custom_header_value]
    }
  }
}
```

## Security Comparison

| Method | Security Level | Complexity | Cost | Maintenance |
|--------|---------------|------------|------|-------------|
| **Security Group Only** | High | Low | Free | Auto-updated by AWS |
| **SG + Custom Header** | Very High | Medium | Free | Manual secret rotation |
| **WAF Rules** | Highest | High | $5-10/month | Rule management |

## Recommendation

### For Most Production Workloads:
✅ **Use Security Group restriction ONLY** (already implemented)
- Simpler to maintain
- AWS manages prefix list updates
- Sufficient for 99% of use cases

### For High-Security / Compliance Workloads:
✅ **Add Custom Header Validation** (optional enhancement)
- Extra protection layer
- Meets compliance requirements
- Minimal overhead

### For Maximum Security:
✅ **Use AWS WAF with CloudFront** (not implemented, but recommended for enterprise)
- DDoS protection
- Rate limiting
- Geo-blocking
- Bot protection

## WAF Integration (Future Enhancement)

```hcl
# terraform/modules/cloudfront/waf.tf
resource "aws_wafv2_web_acl" "cloudfront" {
  provider = aws.us_east_1  # WAF for CloudFront must be in us-east-1
  name     = "${var.name_prefix}-cloudfront-waf"
  scope    = "CLOUDFRONT"

  default_action {
    allow {}
  }

  # Rate limiting: 2000 requests per 5 minutes per IP
  rule {
    name     = "rate-limit"
    priority = 1

    action {
      block {}
    }

    statement {
      rate_based_statement {
        limit              = 2000
        aggregate_key_type = "IP"
      }
    }

    visibility_config {
      cloudwatch_metrics_enabled = true
      metric_name                = "RateLimitRule"
      sampled_requests_enabled   = true
    }
  }

  # AWS Managed Rules: Core Rule Set
  rule {
    name     = "aws-managed-core"
    priority = 10

    override_action {
      none {}
    }

    statement {
      managed_rule_group_statement {
        vendor_name = "AWS"
        name        = "AWSManagedRulesCommonRuleSet"
      }
    }

    visibility_config {
      cloudwatch_metrics_enabled = true
      metric_name                = "AWSManagedCore"
      sampled_requests_enabled   = true
    }
  }

  visibility_config {
    cloudwatch_metrics_enabled = true
    metric_name                = "CloudFrontWAF"
    sampled_requests_enabled   = true
  }
}

# Attach WAF to CloudFront
resource "aws_cloudfront_distribution" "main" {
  ...
  web_acl_id = aws_wafv2_web_acl.cloudfront.arn
}
```

## Testing

### Test 1: Direct ALB Access (Should Fail)

```bash
# Try to access ALB directly (should timeout or be rejected)
curl -v https://urr-alb-123456.ap-northeast-2.elb.amazonaws.com

# Expected: Connection timeout or 403 Forbidden
```

### Test 2: CloudFront Access (Should Succeed)

```bash
# Access via CloudFront (should work)
curl -v https://urr.example.com

# Expected: 200 OK
```

### Test 3: Custom Header Validation (If Enabled)

```bash
# Try ALB with correct header from different IP (should fail due to SG)
curl -v https://urr-alb-123456.ap-northeast-2.elb.amazonaws.com \
  -H "X-CloudFront-Verified: <secret-value>"

# Expected: Connection timeout (SG blocks non-CloudFront IPs)
```

## Monitoring

### CloudWatch Metrics to Monitor

- `TargetResponseTime`: Check ALB performance
- `HTTPCode_Target_4XX_Count`: Application errors
- `HTTPCode_ELB_5XX_Count`: Infrastructure errors
- `RejectedConnectionCount`: Security group rejections

### CloudWatch Alarms

```hcl
resource "aws_cloudwatch_metric_alarm" "alb_4xx_errors" {
  alarm_name          = "${var.name_prefix}-alb-4xx-errors"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 2
  metric_name         = "HTTPCode_Target_4XX_Count"
  namespace           = "AWS/ApplicationELB"
  period              = 300
  statistic           = "Sum"
  threshold           = 100

  dimensions = {
    LoadBalancer = aws_lb.main.arn_suffix
  }
}
```

## Incident Response

### If ALB is Directly Accessed

1. Check CloudTrail logs for security group modifications
2. Verify ALB security group still has CloudFront prefix list only
3. Review CloudFront access logs for suspicious patterns
4. Rotate custom header secret if compromised

### If DDoS Attack Detected

1. Enable AWS Shield Standard (automatically enabled)
2. Consider AWS Shield Advanced ($3000/month)
3. Add WAF rate limiting rules
4. Contact AWS DDoS Response Team (DRT) if needed

## Cost Impact

- **Security Group**: $0 (included)
- **Custom Header**: $0 (no extra cost)
- **WAF**: ~$5-10/month for basic rules
- **Shield Advanced**: $3000/month (enterprise only)

## Status

- ✅ **Security Group Restriction**: IMPLEMENTED
- ⏳ **Custom Header Validation**: NOT IMPLEMENTED (optional)
- ❌ **WAF Integration**: NOT IMPLEMENTED (future enhancement)

## Next Steps

1. ✅ Deploy with current security group restriction
2. ⏳ Test direct ALB access is blocked
3. ⏳ Monitor CloudWatch metrics for suspicious activity
4. 🔄 Consider adding WAF for production workloads with high traffic
