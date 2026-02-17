output "db_instance_id" {
  description = "RDS instance ID"
  value       = aws_db_instance.main.id
}

output "db_instance_endpoint" {
  description = "RDS instance endpoint"
  value       = aws_db_instance.main.endpoint
}

output "db_instance_address" {
  description = "RDS instance address"
  value       = aws_db_instance.main.address
}

output "db_instance_port" {
  description = "RDS instance port"
  value       = aws_db_instance.main.port
}

output "db_name" {
  description = "Database name"
  value       = aws_db_instance.main.db_name
}

output "rds_proxy_endpoint" {
  description = "RDS Proxy endpoint (use this for app connections)"
  value       = var.enable_rds_proxy ? aws_db_proxy.main[0].endpoint : null
}

output "rds_security_group_id" {
  description = "Security group ID for RDS"
  value       = aws_security_group.rds.id
}

output "rds_proxy_security_group_id" {
  description = "Security group ID for RDS Proxy"
  value       = var.enable_rds_proxy ? aws_security_group.rds_proxy[0].id : null
}

output "connection_string" {
  description = "Connection string for applications (via RDS Proxy if enabled)"
  value       = var.enable_rds_proxy ? "postgresql://${var.master_username}@${aws_db_proxy.main[0].endpoint}:5432/${aws_db_instance.main.db_name}" : "postgresql://${var.master_username}@${aws_db_instance.main.endpoint}/${aws_db_instance.main.db_name}"
  sensitive   = true
}

output "read_replica_endpoint" {
  description = "Read replica endpoint (use for read-only queries)"
  value       = var.enable_read_replica ? aws_db_instance.read_replica[0].endpoint : null
}

output "read_replica_address" {
  description = "Read replica hostname"
  value       = var.enable_read_replica ? aws_db_instance.read_replica[0].address : null
}
