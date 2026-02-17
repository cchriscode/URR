# ─────────────────────────────────────────────────────────────────────────────
# Security Group for RDS
# ─────────────────────────────────────────────────────────────────────────────

resource "aws_security_group" "rds" {
  name_prefix = "${var.name_prefix}-rds-"
  description = "Security group for RDS PostgreSQL"
  vpc_id      = var.vpc_id

  tags = {
    Name = "${var.name_prefix}-rds-sg"
  }

  lifecycle {
    create_before_destroy = true
  }
}

# Allow inbound from EKS nodes (via RDS Proxy)
resource "aws_security_group_rule" "rds_ingress_from_app" {
  type                     = "ingress"
  from_port                = 5432
  to_port                  = 5432
  protocol                 = "tcp"
  source_security_group_id = var.app_security_group_id
  security_group_id        = aws_security_group.rds.id
  description              = "Allow PostgreSQL from app layer (RDS Proxy)"
}

# ─────────────────────────────────────────────────────────────────────────────
# DB Subnet Group
# ─────────────────────────────────────────────────────────────────────────────

resource "aws_db_subnet_group" "main" {
  name       = "${var.name_prefix}-db-subnet-group"
  subnet_ids = var.db_subnet_ids

  tags = {
    Name = "${var.name_prefix}-db-subnet-group"
  }
}

# ─────────────────────────────────────────────────────────────────────────────
# RDS PostgreSQL Instance (Multi-AZ)
# ─────────────────────────────────────────────────────────────────────────────

resource "aws_db_instance" "main" {
  identifier = "${var.name_prefix}-postgres"

  # Engine
  engine               = "postgres"
  engine_version       = var.engine_version
  instance_class       = var.instance_class
  allocated_storage    = var.allocated_storage
  max_allocated_storage = var.max_allocated_storage
  storage_type         = "gp3"
  storage_encrypted    = true

  # Database
  # NOTE: RDS creates only the initial database (ticket_db).
  #       Additional databases (stats_db) must be created by:
  #       - Application Flyway migrations, OR
  #       - K8s init container, OR
  #       - Manual psql command after deployment
  db_name  = var.database_name  # ticket_db
  username = var.master_username
  password = var.master_password
  port     = 5432

  # Network
  db_subnet_group_name   = aws_db_subnet_group.main.name
  vpc_security_group_ids = [aws_security_group.rds.id]
  publicly_accessible    = false

  # High Availability
  multi_az               = var.multi_az
  availability_zone      = var.multi_az ? null : var.preferred_az

  # Backup
  backup_retention_period   = var.backup_retention_period
  backup_window             = "03:00-04:00"  # UTC
  maintenance_window        = "mon:04:00-mon:05:00"  # UTC
  copy_tags_to_snapshot     = true
  skip_final_snapshot       = var.skip_final_snapshot
  final_snapshot_identifier = var.skip_final_snapshot ? null : "${var.name_prefix}-final-snapshot-${formatdate("YYYY-MM-DD-hhmm", timestamp())}"

  # Performance Insights
  enabled_cloudwatch_logs_exports = ["postgresql", "upgrade"]
  performance_insights_enabled    = var.performance_insights_enabled
  performance_insights_retention_period = var.performance_insights_enabled ? 7 : null

  # Enhanced Monitoring
  monitoring_interval = var.monitoring_interval
  monitoring_role_arn = var.monitoring_interval > 0 ? var.monitoring_role_arn : null

  # Parameter Group
  parameter_group_name = aws_db_parameter_group.main.name

  # Deletion protection
  deletion_protection = var.deletion_protection

  tags = {
    Name = "${var.name_prefix}-postgres"
  }
}

# ─────────────────────────────────────────────────────────────────────────────
# DB Parameter Group
# ─────────────────────────────────────────────────────────────────────────────

resource "aws_db_parameter_group" "main" {
  name_prefix = "${var.name_prefix}-postgres-"
  family      = "postgres16"
  description = "Custom parameter group for ${var.name_prefix}"

  # Optimize for connection pooling with RDS Proxy
  parameter {
    name  = "shared_preload_libraries"
    value = "pg_stat_statements"
  }

  parameter {
    name  = "log_statement"
    value = "ddl"
    apply_method = "pending-reboot"
  }

  parameter {
    name  = "log_min_duration_statement"
    value = "1000"  # Log queries slower than 1s
  }

  tags = {
    Name = "${var.name_prefix}-postgres-params"
  }

  lifecycle {
    create_before_destroy = true
  }
}

# ─────────────────────────────────────────────────────────────────────────────
# RDS Proxy (Connection Pooling)
# ─────────────────────────────────────────────────────────────────────────────

resource "aws_db_proxy" "main" {
  count = var.enable_rds_proxy ? 1 : 0

  name                   = "${var.name_prefix}-proxy"
  engine_family          = "POSTGRESQL"
  auth {
    auth_scheme = "SECRETS"
    iam_auth    = "DISABLED"
    secret_arn  = var.db_credentials_secret_arn
  }

  role_arn               = var.rds_proxy_role_arn
  vpc_subnet_ids         = var.app_subnet_ids  # IMPORTANT: Proxy runs in APP subnets, not DB subnets!
  require_tls            = true
  idle_client_timeout    = 1800

  tags = {
    Name = "${var.name_prefix}-rds-proxy"
  }

  depends_on = [aws_db_instance.main]
}

resource "aws_db_proxy_default_target_group" "main" {
  count = var.enable_rds_proxy ? 1 : 0

  db_proxy_name = aws_db_proxy.main[0].name

  connection_pool_config {
    connection_borrow_timeout    = 120
    max_connections_percent      = 100
    max_idle_connections_percent = 50
  }
}

resource "aws_db_proxy_target" "main" {
  count = var.enable_rds_proxy ? 1 : 0

  db_proxy_name          = aws_db_proxy.main[0].name
  target_group_name      = aws_db_proxy_default_target_group.main[0].name
  db_instance_identifier = aws_db_instance.main.identifier
}

# Security group for RDS Proxy
resource "aws_security_group" "rds_proxy" {
  count = var.enable_rds_proxy ? 1 : 0

  name_prefix = "${var.name_prefix}-rds-proxy-"
  description = "Security group for RDS Proxy"
  vpc_id      = var.vpc_id

  tags = {
    Name = "${var.name_prefix}-rds-proxy-sg"
  }

  lifecycle {
    create_before_destroy = true
  }
}

# Allow EKS nodes to connect to RDS Proxy
resource "aws_security_group_rule" "rds_proxy_ingress_from_eks" {
  count = var.enable_rds_proxy ? 1 : 0

  type                     = "ingress"
  from_port                = 5432
  to_port                  = 5432
  protocol                 = "tcp"
  source_security_group_id = var.eks_node_security_group_id
  security_group_id        = aws_security_group.rds_proxy[0].id
  description              = "Allow PostgreSQL from EKS nodes"
}

# Allow Lambda worker to connect to RDS Proxy
resource "aws_security_group_rule" "rds_proxy_ingress_from_lambda" {
  count = var.enable_rds_proxy && var.lambda_worker_security_group_id != "" ? 1 : 0

  type                     = "ingress"
  from_port                = 5432
  to_port                  = 5432
  protocol                 = "tcp"
  source_security_group_id = var.lambda_worker_security_group_id
  security_group_id        = aws_security_group.rds_proxy[0].id
  description              = "Allow PostgreSQL from Lambda worker"
}

# Allow RDS Proxy to connect to RDS
resource "aws_security_group_rule" "rds_proxy_egress_to_rds" {
  count = var.enable_rds_proxy ? 1 : 0

  type                     = "egress"
  from_port                = 5432
  to_port                  = 5432
  protocol                 = "tcp"
  source_security_group_id = aws_security_group.rds.id
  security_group_id        = aws_security_group.rds_proxy[0].id
  description              = "Allow PostgreSQL to RDS"
}

# Update RDS SG to allow from Proxy (if proxy enabled)
resource "aws_security_group_rule" "rds_ingress_from_proxy" {
  count = var.enable_rds_proxy ? 1 : 0

  type                     = "ingress"
  from_port                = 5432
  to_port                  = 5432
  protocol                 = "tcp"
  source_security_group_id = aws_security_group.rds_proxy[0].id
  security_group_id        = aws_security_group.rds.id
  description              = "Allow PostgreSQL from RDS Proxy"
}

# ─────────────────────────────────────────────────────────────────────────────
# RDS Read Replica
# ─────────────────────────────────────────────────────────────────────────────

resource "aws_db_instance" "read_replica" {
  count = var.enable_read_replica ? 1 : 0

  identifier          = "${var.name_prefix}-postgres-replica"
  replicate_source_db = aws_db_instance.main.identifier
  instance_class      = coalesce(var.read_replica_instance_class, var.instance_class)
  storage_encrypted   = true
  publicly_accessible = false

  vpc_security_group_ids = [aws_security_group.rds.id]
  parameter_group_name   = aws_db_parameter_group.main.name

  performance_insights_enabled          = var.performance_insights_enabled
  performance_insights_retention_period = var.performance_insights_enabled ? 7 : null
  monitoring_interval                   = var.monitoring_interval
  monitoring_role_arn                   = var.monitoring_interval > 0 ? var.monitoring_role_arn : null

  skip_final_snapshot = true

  tags = {
    Name = "${var.name_prefix}-postgres-replica"
  }
}
