output "rds_credentials_secret_arn" {
  description = "ARN of RDS credentials secret"
  value       = aws_secretsmanager_secret.rds_credentials.arn
}

output "rds_password" {
  description = "RDS master password (sensitive)"
  value       = random_password.rds_password.result
  sensitive   = true
}

output "redis_auth_token_secret_arn" {
  description = "ARN of Redis auth token secret"
  value       = aws_secretsmanager_secret.redis_auth_token.arn
}

output "redis_auth_token" {
  description = "Redis auth token (sensitive)"
  value       = random_password.redis_auth_token.result
  sensitive   = true
}

output "queue_entry_token_secret_arn" {
  description = "ARN of queue entry token secret"
  value       = aws_secretsmanager_secret.queue_entry_token_secret.arn
}

output "queue_entry_token_secret_value" {
  description = "Queue entry token secret value (sensitive)"
  value       = random_password.queue_entry_token_secret.result
  sensitive   = true
}

output "jwt_secret_arn" {
  description = "ARN of JWT secret"
  value       = aws_secretsmanager_secret.jwt_secret.arn
}

output "jwt_secret_value" {
  description = "JWT secret value (sensitive)"
  value       = random_password.jwt_secret.result
  sensitive   = true
}
