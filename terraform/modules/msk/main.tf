# ─────────────────────────────────────────────────────────────────────────────
# Security Group for MSK
# ─────────────────────────────────────────────────────────────────────────────

resource "aws_security_group" "msk" {
  name_prefix = "${var.name_prefix}-msk-"
  description = "Security group for Amazon MSK (Kafka)"
  vpc_id      = var.vpc_id

  tags = {
    Name = "${var.name_prefix}-msk-sg"
  }

  lifecycle {
    create_before_destroy = true
  }
}

# Allow inbound from EKS nodes (plaintext)
resource "aws_security_group_rule" "msk_ingress_from_eks_plaintext" {
  count                    = var.enable_plaintext ? 1 : 0
  type                     = "ingress"
  from_port                = 9092
  to_port                  = 9092
  protocol                 = "tcp"
  source_security_group_id = var.eks_node_security_group_id
  security_group_id        = aws_security_group.msk.id
  description              = "Allow Kafka plaintext from EKS nodes"
}

# Allow inbound from EKS nodes (TLS)
resource "aws_security_group_rule" "msk_ingress_from_eks_tls" {
  count                    = var.enable_tls ? 1 : 0
  type                     = "ingress"
  from_port                = 9094
  to_port                  = 9094
  protocol                 = "tcp"
  source_security_group_id = var.eks_node_security_group_id
  security_group_id        = aws_security_group.msk.id
  description              = "Allow Kafka TLS from EKS nodes"
}

# Allow inbound from EKS nodes (IAM auth)
resource "aws_security_group_rule" "msk_ingress_from_eks_iam" {
  count                    = var.enable_iam_auth ? 1 : 0
  type                     = "ingress"
  from_port                = 9098
  to_port                  = 9098
  protocol                 = "tcp"
  source_security_group_id = var.eks_node_security_group_id
  security_group_id        = aws_security_group.msk.id
  description              = "Allow Kafka IAM auth from EKS nodes"
}

# Allow inbound from Lambda worker
resource "aws_security_group_rule" "msk_ingress_from_lambda" {
  count                    = var.lambda_worker_security_group_id != "" ? 1 : 0
  type                     = "ingress"
  from_port                = var.enable_tls ? 9094 : 9092
  to_port                  = var.enable_tls ? 9094 : 9092
  protocol                 = "tcp"
  source_security_group_id = var.lambda_worker_security_group_id
  security_group_id        = aws_security_group.msk.id
  description              = "Allow Kafka from Lambda worker"
}

# Allow ZooKeeper access between brokers
resource "aws_security_group_rule" "msk_ingress_zookeeper" {
  type              = "ingress"
  from_port         = 2181
  to_port           = 2181
  protocol          = "tcp"
  self              = true
  security_group_id = aws_security_group.msk.id
  description       = "Allow ZooKeeper between brokers"
}

# Allow all outbound
resource "aws_security_group_rule" "msk_egress_all" {
  type              = "egress"
  from_port         = 0
  to_port           = 0
  protocol          = "-1"
  cidr_blocks       = ["0.0.0.0/0"]
  security_group_id = aws_security_group.msk.id
  description       = "Allow all outbound traffic"
}

# ─────────────────────────────────────────────────────────────────────────────
# MSK Configuration
# ─────────────────────────────────────────────────────────────────────────────

resource "aws_msk_configuration" "main" {
  name              = "${var.name_prefix}-msk-config"
  kafka_versions    = [var.kafka_version]
  description       = "MSK configuration for ${var.name_prefix} ticketing system"

  server_properties = <<-PROPERTIES
    auto.create.topics.enable=true
    default.replication.factor=${var.number_of_broker_nodes > 1 ? 2 : 1}
    min.insync.replicas=${var.number_of_broker_nodes > 2 ? 2 : 1}
    num.io.threads=8
    num.network.threads=5
    num.partitions=${var.default_partitions}
    num.replica.fetchers=2
    replica.lag.time.max.ms=30000
    socket.receive.buffer.bytes=102400
    socket.request.max.bytes=104857600
    socket.send.buffer.bytes=102400
    unclean.leader.election.enable=false
    log.retention.hours=${var.log_retention_hours}
    log.retention.bytes=${var.log_retention_bytes}
  PROPERTIES
}

# ─────────────────────────────────────────────────────────────────────────────
# CloudWatch Log Group for MSK Broker Logs
# ─────────────────────────────────────────────────────────────────────────────

resource "aws_cloudwatch_log_group" "msk" {
  name              = "/aws/msk/${var.name_prefix}"
  retention_in_days = var.log_group_retention_days

  tags = {
    Name = "${var.name_prefix}-msk-logs"
  }
}

# ─────────────────────────────────────────────────────────────────────────────
# MSK Cluster
# ─────────────────────────────────────────────────────────────────────────────

resource "aws_msk_cluster" "main" {
  cluster_name           = "${var.name_prefix}-msk"
  kafka_version          = var.kafka_version
  number_of_broker_nodes = var.number_of_broker_nodes

  configuration_info {
    arn      = aws_msk_configuration.main.arn
    revision = aws_msk_configuration.main.latest_revision
  }

  broker_node_group_info {
    instance_type   = var.broker_instance_type
    client_subnets  = var.streaming_subnet_ids
    security_groups = [aws_security_group.msk.id]

    storage_info {
      ebs_storage_info {
        volume_size = var.broker_ebs_volume_size
      }
    }

    connectivity_info {
      public_access {
        type = "DISABLED"
      }
    }
  }

  encryption_info {
    encryption_in_transit {
      client_broker = var.enable_tls ? "TLS" : "TLS_PLAINTEXT"
      in_cluster    = true
    }

    encryption_at_rest_kms_key_arn = var.kms_key_arn
  }

  client_authentication {
    unauthenticated = var.enable_plaintext

    dynamic "sasl" {
      for_each = var.enable_iam_auth ? [1] : []
      content {
        iam = true
      }
    }
  }

  logging_info {
    broker_logs {
      cloudwatch_logs {
        enabled   = true
        log_group = aws_cloudwatch_log_group.msk.name
      }
    }
  }

  # Enhanced monitoring
  enhanced_monitoring = var.enhanced_monitoring_level

  tags = {
    Name = "${var.name_prefix}-msk"
  }
}

# ─────────────────────────────────────────────────────────────────────────────
# CloudWatch Alarms
# ─────────────────────────────────────────────────────────────────────────────

resource "aws_cloudwatch_metric_alarm" "msk_active_controller" {
  count               = var.enable_cloudwatch_alarms ? 1 : 0
  alarm_name          = "${var.name_prefix}-msk-no-active-controller"
  comparison_operator = "LessThanThreshold"
  evaluation_periods  = 1
  metric_name         = "ActiveControllerCount"
  namespace           = "AWS/Kafka"
  period              = 300
  statistic           = "Maximum"
  threshold           = 1
  alarm_description   = "Alert when MSK has no active controller"
  treat_missing_data  = "breaching"

  dimensions = {
    "Cluster Name" = aws_msk_cluster.main.cluster_name
  }

  alarm_actions = var.sns_topic_arn != "" ? [var.sns_topic_arn] : []
}

resource "aws_cloudwatch_metric_alarm" "msk_offline_partitions" {
  count               = var.enable_cloudwatch_alarms ? 1 : 0
  alarm_name          = "${var.name_prefix}-msk-offline-partitions"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 1
  metric_name         = "OfflinePartitionsCount"
  namespace           = "AWS/Kafka"
  period              = 300
  statistic           = "Maximum"
  threshold           = 0
  alarm_description   = "Alert when MSK has offline partitions"
  treat_missing_data  = "notBreaching"

  dimensions = {
    "Cluster Name" = aws_msk_cluster.main.cluster_name
  }

  alarm_actions = var.sns_topic_arn != "" ? [var.sns_topic_arn] : []
}
