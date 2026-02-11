# ─────────────────────────────────────────────────────────────────────────────
# Security Group for Lambda Worker
# ─────────────────────────────────────────────────────────────────────────────

resource "aws_security_group" "lambda_worker" {
  name_prefix = "${var.name_prefix}-lambda-worker-"
  description = "Security group for Lambda worker functions"
  vpc_id      = var.vpc_id

  tags = {
    Name = "${var.name_prefix}-lambda-worker-sg"
  }

  lifecycle {
    create_before_destroy = true
  }
}

# Allow outbound to RDS Proxy
resource "aws_security_group_rule" "lambda_egress_rds_proxy" {
  type                     = "egress"
  from_port                = 5432
  to_port                  = 5432
  protocol                 = "tcp"
  source_security_group_id = var.rds_proxy_security_group_id
  security_group_id        = aws_security_group.lambda_worker.id
  description              = "Allow Lambda to connect to RDS Proxy"
}

# Allow outbound to Redis
resource "aws_security_group_rule" "lambda_egress_redis" {
  type                     = "egress"
  from_port                = 6379
  to_port                  = 6379
  protocol                 = "tcp"
  source_security_group_id = var.redis_security_group_id
  security_group_id        = aws_security_group.lambda_worker.id
  description              = "Allow Lambda to connect to Redis"
}

# Allow outbound HTTPS to VPC Endpoints
resource "aws_security_group_rule" "lambda_egress_https" {
  type              = "egress"
  from_port         = 443
  to_port           = 443
  protocol          = "tcp"
  cidr_blocks       = [var.vpc_cidr]
  security_group_id = aws_security_group.lambda_worker.id
  description       = "Allow Lambda to access VPC Endpoints (Secrets Manager, etc.)"
}

# Allow all outbound traffic (for SQS via VPC Endpoint, etc.)
resource "aws_security_group_rule" "lambda_egress_all" {
  type              = "egress"
  from_port         = 0
  to_port           = 0
  protocol          = "-1"
  cidr_blocks       = ["0.0.0.0/0"]
  security_group_id = aws_security_group.lambda_worker.id
  description       = "Allow all outbound traffic"
}

# ─────────────────────────────────────────────────────────────────────────────
# Lambda Function for Ticket Event Processing
# ─────────────────────────────────────────────────────────────────────────────

# Package Lambda function code (placeholder - actual implementation needed)
data "archive_file" "worker_function" {
  type        = "zip"
  source_dir  = var.lambda_source_dir
  output_path = "${path.module}/lambda-ticket-worker.zip"
}

resource "aws_lambda_function" "ticket_worker" {
  filename         = data.archive_file.worker_function.output_path
  function_name    = "${var.name_prefix}-ticket-worker"
  role             = var.lambda_worker_role_arn
  handler          = var.lambda_handler
  source_code_hash = data.archive_file.worker_function.output_base64sha256
  runtime          = var.lambda_runtime
  timeout          = var.lambda_timeout
  memory_size      = var.lambda_memory_size
  reserved_concurrent_executions = var.reserved_concurrent_executions

  # VPC configuration to access RDS, Redis, etc.
  vpc_config {
    subnet_ids         = var.subnet_ids
    security_group_ids = [aws_security_group.lambda_worker.id]
  }

  # Environment variables
  environment {
    variables = merge(
      {
        DB_PROXY_ENDPOINT     = var.db_proxy_endpoint
        REDIS_ENDPOINT        = var.redis_endpoint
        REDIS_PORT            = var.redis_port
        REDIS_AUTH_TOKEN      = var.redis_auth_token
        ENVIRONMENT           = var.environment
      },
      var.additional_env_vars
    )
  }

  # Tracing
  tracing_config {
    mode = var.enable_xray_tracing ? "Active" : "PassThrough"
  }

  tags = {
    Name = "${var.name_prefix}-ticket-worker"
  }
}

# ─────────────────────────────────────────────────────────────────────────────
# SQS Event Source Mapping
# ─────────────────────────────────────────────────────────────────────────────

resource "aws_lambda_event_source_mapping" "sqs_trigger" {
  event_source_arn = var.sqs_queue_arn
  function_name    = aws_lambda_function.ticket_worker.arn
  enabled          = true

  # Batch settings
  batch_size                         = var.sqs_batch_size
  maximum_batching_window_in_seconds = var.sqs_batching_window_seconds

  # Error handling
  function_response_types = ["ReportBatchItemFailures"]

  scaling_config {
    maximum_concurrency = var.max_concurrency
  }
}

# ─────────────────────────────────────────────────────────────────────────────
# CloudWatch Log Group
# ─────────────────────────────────────────────────────────────────────────────

resource "aws_cloudwatch_log_group" "lambda" {
  name              = "/aws/lambda/${aws_lambda_function.ticket_worker.function_name}"
  retention_in_days = var.log_retention_days

  tags = {
    Name = "${var.name_prefix}-ticket-worker-logs"
  }
}

# ─────────────────────────────────────────────────────────────────────────────
# CloudWatch Alarms for Lambda Monitoring
# ─────────────────────────────────────────────────────────────────────────────

resource "aws_cloudwatch_metric_alarm" "lambda_errors" {
  count               = var.enable_cloudwatch_alarms ? 1 : 0
  alarm_name          = "${var.name_prefix}-ticket-worker-errors"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 2
  metric_name         = "Errors"
  namespace           = "AWS/Lambda"
  period              = 300
  statistic           = "Sum"
  threshold           = 5
  alarm_description   = "Alert when Lambda function has errors"
  treat_missing_data  = "notBreaching"

  dimensions = {
    FunctionName = aws_lambda_function.ticket_worker.function_name
  }

  alarm_actions = var.sns_topic_arn != "" ? [var.sns_topic_arn] : []
}

resource "aws_cloudwatch_metric_alarm" "lambda_duration" {
  count               = var.enable_cloudwatch_alarms ? 1 : 0
  alarm_name          = "${var.name_prefix}-ticket-worker-duration"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 2
  metric_name         = "Duration"
  namespace           = "AWS/Lambda"
  period              = 300
  statistic           = "Average"
  threshold           = var.lambda_timeout * 1000 * 0.8  # 80% of timeout
  alarm_description   = "Alert when Lambda duration is high"
  treat_missing_data  = "notBreaching"

  dimensions = {
    FunctionName = aws_lambda_function.ticket_worker.function_name
  }

  alarm_actions = var.sns_topic_arn != "" ? [var.sns_topic_arn] : []
}

resource "aws_cloudwatch_metric_alarm" "lambda_throttles" {
  count               = var.enable_cloudwatch_alarms ? 1 : 0
  alarm_name          = "${var.name_prefix}-ticket-worker-throttles"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 1
  metric_name         = "Throttles"
  namespace           = "AWS/Lambda"
  period              = 300
  statistic           = "Sum"
  threshold           = 0
  alarm_description   = "Alert when Lambda function is throttled"
  treat_missing_data  = "notBreaching"

  dimensions = {
    FunctionName = aws_lambda_function.ticket_worker.function_name
  }

  alarm_actions = var.sns_topic_arn != "" ? [var.sns_topic_arn] : []
}
