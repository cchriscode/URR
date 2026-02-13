# ─────────────────────────────────────────────────────────────────────────────
# RDS Credentials
# ─────────────────────────────────────────────────────────────────────────────

resource "random_password" "rds_password" {
  length  = 32
  special = true
}

resource "aws_secretsmanager_secret" "rds_credentials" {
  name        = "${var.name_prefix}/rds-credentials"
  description = "PostgreSQL RDS master credentials"

  recovery_window_in_days = var.recovery_window_in_days
}

resource "aws_secretsmanager_secret_version" "rds_credentials" {
  secret_id = aws_secretsmanager_secret.rds_credentials.id

  secret_string = jsonencode({
    username = var.rds_username
    password = random_password.rds_password.result
    engine   = "postgres"
    host     = var.rds_endpoint
    port     = 5432
    dbname   = "ticket_db"
  })
}

# ─────────────────────────────────────────────────────────────────────────────
# Redis Auth Token
# ─────────────────────────────────────────────────────────────────────────────

resource "random_password" "redis_auth_token" {
  length  = 32
  special = false # Redis auth token doesn't support all special chars
}

resource "aws_secretsmanager_secret" "redis_auth_token" {
  name        = "${var.name_prefix}/redis-auth-token"
  description = "ElastiCache Redis authentication token"

  recovery_window_in_days = var.recovery_window_in_days
}

resource "aws_secretsmanager_secret_version" "redis_auth_token" {
  secret_id = aws_secretsmanager_secret.redis_auth_token.id

  secret_string = jsonencode({
    token = random_password.redis_auth_token.result
  })
}

# ─────────────────────────────────────────────────────────────────────────────
# Queue Entry Token Secret (for JWT signing)
# ─────────────────────────────────────────────────────────────────────────────

resource "random_password" "queue_entry_token_secret" {
  length  = 64
  special = false
}

resource "aws_secretsmanager_secret" "queue_entry_token_secret" {
  name        = "${var.name_prefix}/queue-entry-token-secret"
  description = "HMAC-SHA256 secret for queue entry JWT tokens"

  recovery_window_in_days = var.recovery_window_in_days
}

resource "aws_secretsmanager_secret_version" "queue_entry_token_secret" {
  secret_id = aws_secretsmanager_secret.queue_entry_token_secret.id

  secret_string = jsonencode({
    secret = random_password.queue_entry_token_secret.result
  })
}

# ─────────────────────────────────────────────────────────────────────────────
# JWT Secret (for auth-service)
# ─────────────────────────────────────────────────────────────────────────────

resource "random_password" "jwt_secret" {
  length  = 64
  special = false
}

resource "aws_secretsmanager_secret" "jwt_secret" {
  name        = "${var.name_prefix}/jwt-secret"
  description = "JWT secret for auth-service"

  recovery_window_in_days = var.recovery_window_in_days
}

resource "aws_secretsmanager_secret_version" "jwt_secret" {
  secret_id = aws_secretsmanager_secret.jwt_secret.id

  secret_string = jsonencode({
    secret = random_password.jwt_secret.result
  })
}
