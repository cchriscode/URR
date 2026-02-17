# ═════════════════════════════════════════════════════════════════════════════
# Amazon Managed Prometheus (AMP)
# ═════════════════════════════════════════════════════════════════════════════

resource "aws_prometheus_workspace" "main" {
  alias = "${var.name_prefix}-amp"

  tags = {
    Name = "${var.name_prefix}-amp"
  }
}

# ═════════════════════════════════════════════════════════════════════════════
# Amazon Managed Grafana (AMG)
# ═════════════════════════════════════════════════════════════════════════════

resource "aws_grafana_workspace" "main" {
  name                     = "${var.name_prefix}-grafana"
  account_access_type      = "CURRENT_ACCOUNT"
  authentication_providers = ["AWS_SSO"]
  permission_type          = "SERVICE_MANAGED"
  role_arn                 = aws_iam_role.grafana_workspace.arn
  data_sources             = ["PROMETHEUS"]

  configuration = jsonencode({
    plugins = {
      pluginAdminEnabled = true
    }
  })

  tags = {
    Name = "${var.name_prefix}-amg"
  }
}

# ═════════════════════════════════════════════════════════════════════════════
# IAM - Grafana Workspace Role
# ═════════════════════════════════════════════════════════════════════════════

resource "aws_iam_role" "grafana_workspace" {
  name = "${var.name_prefix}-amg-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Principal = {
          Service = "grafana.amazonaws.com"
        }
        Action = "sts:AssumeRole"
      }
    ]
  })

  tags = {
    Name = "${var.name_prefix}-amg-role"
  }
}

resource "aws_iam_role_policy" "grafana_amp_query" {
  name = "${var.name_prefix}-amg-amp-query"
  role = aws_iam_role.grafana_workspace.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "aps:ListWorkspaces",
          "aps:DescribeWorkspace",
          "aps:QueryMetrics",
          "aps:GetLabels",
          "aps:GetSeries",
          "aps:GetMetricMetadata"
        ]
        Resource = "*"
      }
    ]
  })
}

# ═════════════════════════════════════════════════════════════════════════════
# IAM - Prometheus IRSA Role for Remote Write
# ═════════════════════════════════════════════════════════════════════════════

data "aws_iam_policy_document" "prometheus_assume_role" {
  statement {
    effect  = "Allow"
    actions = ["sts:AssumeRoleWithWebIdentity"]

    principals {
      type        = "Federated"
      identifiers = [var.oidc_provider_arn]
    }

    condition {
      test     = "StringEquals"
      variable = "${var.oidc_provider_url}:sub"
      values   = ["system:serviceaccount:monitoring:kube-prometheus-stack-prometheus"]
    }

    condition {
      test     = "StringEquals"
      variable = "${var.oidc_provider_url}:aud"
      values   = ["sts.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "prometheus_remote_write" {
  name               = "${var.name_prefix}-prometheus-amp-irsa"
  assume_role_policy = data.aws_iam_policy_document.prometheus_assume_role.json

  tags = {
    Name = "${var.name_prefix}-prometheus-amp-irsa"
  }
}

resource "aws_iam_role_policy" "prometheus_amp_write" {
  name = "${var.name_prefix}-prometheus-amp-write"
  role = aws_iam_role.prometheus_remote_write.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "aps:RemoteWrite",
          "aps:GetSeries",
          "aps:GetLabels",
          "aps:GetMetricMetadata"
        ]
        Resource = "*"
      }
    ]
  })
}
