variable "name_prefix" {
  description = "Prefix for resource names"
  type        = string
}

variable "lambda_worker_role_arn" {
  description = "ARN of Lambda worker IAM role"
  type        = string
}

variable "lambda_source_dir" {
  description = "Directory containing Lambda function source code"
  type        = string
}

variable "lambda_handler" {
  description = "Lambda function handler"
  type        = string
  default     = "index.handler"
}

variable "lambda_runtime" {
  description = "Lambda runtime"
  type        = string
  default     = "nodejs20.x"
}

variable "lambda_timeout" {
  description = "Lambda timeout in seconds"
  type        = number
  default     = 300
}

variable "lambda_memory_size" {
  description = "Lambda memory size in MB"
  type        = number
  default     = 512
}

variable "reserved_concurrent_executions" {
  description = "Reserved concurrent executions for Lambda"
  type        = number
  default     = -1  # No reservation
}

# VPC Configuration

variable "vpc_id" {
  description = "VPC ID for security group creation"
  type        = string
}

variable "vpc_cidr" {
  description = "VPC CIDR block"
  type        = string
}

variable "subnet_ids" {
  description = "List of subnet IDs for Lambda VPC configuration (streaming subnets)"
  type        = list(string)
}

variable "rds_proxy_security_group_id" {
  description = "Security group ID for RDS Proxy"
  type        = string
}

variable "redis_security_group_id" {
  description = "Security group ID for Redis"
  type        = string
}

# Environment Variables

variable "environment" {
  description = "Environment name (dev, staging, prod)"
  type        = string
  default     = "prod"
}

variable "additional_env_vars" {
  description = "Additional environment variables"
  type        = map(string)
  default     = {}
}

# SQS Configuration

variable "sqs_queue_arn" {
  description = "ARN of SQS queue to process"
  type        = string
}

variable "sqs_batch_size" {
  description = "Maximum number of messages to retrieve in a batch"
  type        = number
  default     = 10
}

variable "sqs_batching_window_seconds" {
  description = "Maximum batching window in seconds"
  type        = number
  default     = 5
}

variable "max_concurrency" {
  description = "Maximum concurrent Lambda invocations"
  type        = number
  default     = 10
}

# Monitoring

variable "enable_xray_tracing" {
  description = "Enable AWS X-Ray tracing"
  type        = bool
  default     = true
}

variable "enable_cloudwatch_alarms" {
  description = "Enable CloudWatch alarms"
  type        = bool
  default     = true
}

variable "log_retention_days" {
  description = "CloudWatch log retention in days"
  type        = number
  default     = 7
}

variable "sns_topic_arn" {
  description = "SNS topic ARN for alarm notifications"
  type        = string
  default     = ""
}
