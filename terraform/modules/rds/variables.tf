variable "name_prefix" {
  description = "Prefix for resource names"
  type        = string
}

variable "vpc_id" {
  description = "VPC ID"
  type        = string
}

variable "db_subnet_ids" {
  description = "List of subnet IDs for RDS (private DB subnets)"
  type        = list(string)
}

variable "app_subnet_ids" {
  description = "List of subnet IDs for RDS Proxy (private app subnets)"
  type        = list(string)
}

variable "app_security_group_id" {
  description = "Security group ID for app layer (if RDS Proxy not used)"
  type        = string
  default     = ""
}

variable "eks_node_security_group_id" {
  description = "Security group ID for EKS nodes"
  type        = string
}

# RDS Instance Configuration

variable "engine_version" {
  description = "PostgreSQL engine version"
  type        = string
  default     = "16.4"
}

variable "instance_class" {
  description = "RDS instance class"
  type        = string
  default     = "db.t4g.micro"
}

variable "allocated_storage" {
  description = "Initial allocated storage in GB"
  type        = number
  default     = 20
}

variable "max_allocated_storage" {
  description = "Maximum storage for autoscaling (0 = disabled)"
  type        = number
  default     = 100
}

variable "database_name" {
  description = "Initial database name (ticket_db). See README for creating additional databases."
  type        = string
  default     = "ticket_db"
}

variable "additional_databases" {
  description = "List of additional database names (e.g., ['stats_db']). These must be created post-deployment via Flyway or init scripts."
  type        = list(string)
  default     = ["auth_db", "payment_db", "stats_db", "community_db"]
}

variable "master_username" {
  description = "Master username for RDS"
  type        = string
  default     = "urr_admin"
}

variable "master_password" {
  description = "Master password for RDS (from Secrets Manager)"
  type        = string
  sensitive   = true
}

# High Availability

variable "multi_az" {
  description = "Enable Multi-AZ deployment"
  type        = bool
  default     = true
}

variable "preferred_az" {
  description = "Preferred AZ for single-AZ deployment"
  type        = string
  default     = null
}

# Backup

variable "backup_retention_period" {
  description = "Backup retention period in days"
  type        = number
  default     = 7
}

variable "skip_final_snapshot" {
  description = "Skip final snapshot on delete (for dev/test only)"
  type        = bool
  default     = false
}

# Monitoring

variable "performance_insights_enabled" {
  description = "Enable Performance Insights"
  type        = bool
  default     = true
}

variable "monitoring_interval" {
  description = "Enhanced monitoring interval in seconds (0, 1, 5, 10, 15, 30, 60)"
  type        = number
  default     = 60
}

variable "monitoring_role_arn" {
  description = "IAM role ARN for enhanced monitoring"
  type        = string
  default     = ""
}

# Deletion Protection

variable "deletion_protection" {
  description = "Enable deletion protection"
  type        = bool
  default     = true
}

# RDS Proxy

variable "lambda_worker_security_group_id" {
  description = "Security group ID for Lambda worker (optional, for SQS consumer access to RDS Proxy)"
  type        = string
  default     = ""
}

variable "enable_rds_proxy" {
  description = "Enable RDS Proxy for connection pooling"
  type        = bool
  default     = true
}

variable "db_credentials_secret_arn" {
  description = "ARN of Secrets Manager secret containing DB credentials"
  type        = string
  default     = ""
}

variable "rds_proxy_role_arn" {
  description = "IAM role ARN for RDS Proxy to access Secrets Manager"
  type        = string
  default     = ""
}
