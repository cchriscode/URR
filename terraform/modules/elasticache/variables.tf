variable "name_prefix" {
  description = "Prefix for resource names"
  type        = string
}

variable "vpc_id" {
  description = "VPC ID"
  type        = string
}

variable "cache_subnet_ids" {
  description = "List of subnet IDs for ElastiCache (private cache subnets)"
  type        = list(string)
}

variable "eks_node_security_group_id" {
  description = "Security group ID for EKS nodes"
  type        = string
}

variable "lambda_worker_security_group_id" {
  description = "Security group ID for Lambda worker (optional, for SQS consumer access to Redis)"
  type        = string
  default     = ""
}

# Engine Configuration

variable "engine_version" {
  description = "Redis engine version"
  type        = string
  default     = "7.1"
}

variable "node_type" {
  description = "ElastiCache node type"
  type        = string
  default     = "cache.t4g.micro"
}

variable "parameter_group_family" {
  description = "Parameter group family"
  type        = string
  default     = "redis7"
}

# Cluster Configuration

variable "num_cache_clusters" {
  description = "Number of cache clusters (1 primary + N-1 replicas)"
  type        = number
  default     = 2
}

variable "preferred_azs" {
  description = "List of preferred AZs for cache clusters"
  type        = list(string)
  default     = []
}

# Security

variable "auth_token_enabled" {
  description = "Enable Redis AUTH token"
  type        = bool
  default     = true
}

variable "auth_token" {
  description = "Redis AUTH token (from Secrets Manager)"
  type        = string
  sensitive   = true
  default     = ""
}

# Backup & Maintenance

variable "snapshot_retention_limit" {
  description = "Number of days to retain automatic snapshots (0 = disabled)"
  type        = number
  default     = 5
}

variable "skip_final_snapshot" {
  description = "Skip final snapshot on delete (for dev/test only)"
  type        = bool
  default     = false
}

variable "auto_minor_version_upgrade" {
  description = "Auto-upgrade minor versions during maintenance window"
  type        = bool
  default     = true
}

# Logging

variable "slow_log_destination" {
  description = "CloudWatch log group name for slow queries"
  type        = string
  default     = "/aws/elasticache/redis/slow-log"
}

variable "engine_log_destination" {
  description = "CloudWatch log group name for engine logs"
  type        = string
  default     = "/aws/elasticache/redis/engine-log"
}

# Notifications

variable "sns_topic_arn" {
  description = "SNS topic ARN for ElastiCache notifications"
  type        = string
  default     = ""
}
