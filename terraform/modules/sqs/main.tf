# ─────────────────────────────────────────────────────────────────────────────
# SQS Queue for Ticket Events
# ─────────────────────────────────────────────────────────────────────────────

resource "aws_sqs_queue" "ticket_events" {
  name                       = "${var.name_prefix}-ticket-events.fifo"
  fifo_queue                 = true
  content_based_deduplication = true
  deduplication_scope        = "messageGroup"
  fifo_throughput_limit      = "perMessageGroupId"

  # Message retention
  message_retention_seconds = var.message_retention_seconds
  visibility_timeout_seconds = var.visibility_timeout_seconds
  receive_wait_time_seconds  = 10  # Enable long polling

  # Redrive policy (send to DLQ after max receives)
  redrive_policy = jsonencode({
    deadLetterTargetArn = aws_sqs_queue.ticket_events_dlq.arn
    maxReceiveCount     = var.max_receive_count
  })

  # Encryption
  sqs_managed_sse_enabled = true

  tags = {
    Name = "${var.name_prefix}-ticket-events"
  }
}

# Dead Letter Queue
resource "aws_sqs_queue" "ticket_events_dlq" {
  name                       = "${var.name_prefix}-ticket-events-dlq.fifo"
  fifo_queue                 = true
  message_retention_seconds  = 1209600  # 14 days
  sqs_managed_sse_enabled    = true

  tags = {
    Name = "${var.name_prefix}-ticket-events-dlq"
  }
}

# ─────────────────────────────────────────────────────────────────────────────
# Queue Policy
# ─────────────────────────────────────────────────────────────────────────────

data "aws_iam_policy_document" "queue_policy" {
  # Allow EKS pods (ticket-service) to send messages
  statement {
    sid    = "AllowTicketServiceSendMessage"
    effect = "Allow"

    principals {
      type        = "AWS"
      identifiers = var.allowed_sender_role_arns
    }

    actions = [
      "sqs:SendMessage",
      "sqs:GetQueueAttributes",
      "sqs:GetQueueUrl"
    ]

    resources = [aws_sqs_queue.ticket_events.arn]
  }

  # Allow Lambda worker to receive and delete messages
  statement {
    sid    = "AllowLambdaWorkerReceiveMessage"
    effect = "Allow"

    principals {
      type        = "AWS"
      identifiers = var.lambda_worker_role_arn != "" ? [var.lambda_worker_role_arn] : []
    }

    actions = [
      "sqs:ReceiveMessage",
      "sqs:DeleteMessage",
      "sqs:GetQueueAttributes",
      "sqs:ChangeMessageVisibility"
    ]

    resources = [aws_sqs_queue.ticket_events.arn]
  }
}

resource "aws_sqs_queue_policy" "ticket_events" {
  queue_url = aws_sqs_queue.ticket_events.id
  policy    = data.aws_iam_policy_document.queue_policy.json
}

# ─────────────────────────────────────────────────────────────────────────────
# CloudWatch Alarms for Queue Monitoring
# ─────────────────────────────────────────────────────────────────────────────

resource "aws_cloudwatch_metric_alarm" "dlq_messages" {
  count               = var.enable_cloudwatch_alarms ? 1 : 0
  alarm_name          = "${var.name_prefix}-ticket-events-dlq-messages"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 1
  metric_name         = "ApproximateNumberOfMessagesVisible"
  namespace           = "AWS/SQS"
  period              = 300
  statistic           = "Average"
  threshold           = 0
  alarm_description   = "Alert when messages appear in DLQ"
  treat_missing_data  = "notBreaching"

  dimensions = {
    QueueName = aws_sqs_queue.ticket_events_dlq.name
  }

  alarm_actions = var.sns_topic_arn != "" ? [var.sns_topic_arn] : []
}

resource "aws_cloudwatch_metric_alarm" "queue_age" {
  count               = var.enable_cloudwatch_alarms ? 1 : 0
  alarm_name          = "${var.name_prefix}-ticket-events-message-age"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 2
  metric_name         = "ApproximateAgeOfOldestMessage"
  namespace           = "AWS/SQS"
  period              = 300
  statistic           = "Maximum"
  threshold           = 600  # 10 minutes
  alarm_description   = "Alert when messages are not being processed"
  treat_missing_data  = "notBreaching"

  dimensions = {
    QueueName = aws_sqs_queue.ticket_events.name
  }

  alarm_actions = var.sns_topic_arn != "" ? [var.sns_topic_arn] : []
}
