# ─────────────────────────────────────────────────────────────────────────────
# Security Group for ElastiCache Redis
# ─────────────────────────────────────────────────────────────────────────────

resource "aws_security_group" "redis" {
  name_prefix = "${var.name_prefix}-redis-"
  description = "Security group for ElastiCache Redis"
  vpc_id      = var.vpc_id

  tags = {
    Name = "${var.name_prefix}-redis-sg"
  }

  lifecycle {
    create_before_destroy = true
  }
}

# Allow inbound from EKS nodes only
resource "aws_security_group_rule" "redis_ingress_from_eks" {
  type                     = "ingress"
  from_port                = 6379
  to_port                  = 6379
  protocol                 = "tcp"
  source_security_group_id = var.eks_node_security_group_id
  security_group_id        = aws_security_group.redis.id
  description              = "Allow Redis from EKS nodes"
}

# ─────────────────────────────────────────────────────────────────────────────
# Subnet Group
# ─────────────────────────────────────────────────────────────────────────────

resource "aws_elasticache_subnet_group" "main" {
  name       = "${var.name_prefix}-redis-subnet-group"
  subnet_ids = var.cache_subnet_ids

  tags = {
    Name = "${var.name_prefix}-redis-subnet-group"
  }
}

# ─────────────────────────────────────────────────────────────────────────────
# Parameter Group
# ─────────────────────────────────────────────────────────────────────────────

resource "aws_elasticache_parameter_group" "main" {
  name_prefix = "${var.name_prefix}-redis-"
  family      = var.parameter_group_family
  description = "Custom parameter group for ${var.name_prefix} Redis"

  # Optimize for queue workload
  parameter {
    name  = "timeout"
    value = "300"  # 5 minutes idle timeout
  }

  parameter {
    name  = "maxmemory-policy"
    value = "allkeys-lru"  # Evict least recently used keys when memory full
  }

  tags = {
    Name = "${var.name_prefix}-redis-params"
  }

  lifecycle {
    create_before_destroy = true
  }
}

# ─────────────────────────────────────────────────────────────────────────────
# Replication Group (Primary + Replica with auto-failover)
# ─────────────────────────────────────────────────────────────────────────────

resource "aws_elasticache_replication_group" "main" {
  replication_group_id = "${var.name_prefix}-redis"
  description          = "Redis cluster for ${var.name_prefix} ticketing system"

  # Engine
  engine               = "redis"
  engine_version       = var.engine_version
  node_type            = var.node_type
  port                 = 6379

  # Cluster configuration
  num_cache_clusters         = var.num_cache_clusters  # 1 primary + N-1 replicas
  automatic_failover_enabled = var.num_cache_clusters > 1
  multi_az_enabled           = var.num_cache_clusters > 1
  preferred_cache_cluster_azs = var.preferred_azs

  # Network
  subnet_group_name    = aws_elasticache_subnet_group.main.name
  security_group_ids   = [aws_security_group.redis.id]

  # Parameter group
  parameter_group_name = aws_elasticache_parameter_group.main.name

  # Security
  at_rest_encryption_enabled = true
  transit_encryption_enabled = true
  auth_token_enabled         = var.auth_token_enabled
  auth_token                 = var.auth_token_enabled ? var.auth_token : null

  # Maintenance & Backup
  maintenance_window       = "sun:05:00-sun:07:00"  # UTC
  snapshot_window          = "03:00-05:00"  # UTC
  snapshot_retention_limit = var.snapshot_retention_limit
  final_snapshot_identifier = var.skip_final_snapshot ? null : "${var.name_prefix}-final-snapshot-${formatdate("YYYY-MM-DD-hhmm", timestamp())}"

  # Logging
  log_delivery_configuration {
    destination      = var.slow_log_destination
    destination_type = "cloudwatch-logs"
    log_format       = "json"
    log_type         = "slow-log"
  }

  log_delivery_configuration {
    destination      = var.engine_log_destination
    destination_type = "cloudwatch-logs"
    log_format       = "json"
    log_type         = "engine-log"
  }

  # Notifications
  notification_topic_arn = var.sns_topic_arn

  # Auto-upgrade
  auto_minor_version_upgrade = var.auto_minor_version_upgrade

  tags = {
    Name = "${var.name_prefix}-redis"
  }
}
