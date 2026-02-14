variable "name_prefix" {
  description = "Prefix for resource names"
  type        = string
}

variable "vpc_id" {
  description = "VPC ID"
  type        = string
}

variable "streaming_subnet_ids" {
  description = "List of subnet IDs for MSK brokers (one per AZ)"
  type        = list(string)
}

variable "eks_node_security_group_id" {
  description = "Security group ID for EKS nodes"
  type        = string
}

variable "lambda_worker_security_group_id" {
  description = "Security group ID for Lambda worker (optional)"
  type        = string
  default     = ""
}

# Kafka Configuration

variable "kafka_version" {
  description = "Apache Kafka version"
  type        = string
  default     = "3.6.0"
}

variable "number_of_broker_nodes" {
  description = "Number of broker nodes (must equal or be a multiple of AZ count)"
  type        = number
  default     = 2
}

variable "broker_instance_type" {
  description = "MSK broker instance type"
  type        = string
  default     = "kafka.t3.small"
}

variable "broker_ebs_volume_size" {
  description = "EBS volume size per broker in GB"
  type        = number
  default     = 50
}

variable "default_partitions" {
  description = "Default number of partitions for auto-created topics"
  type        = number
  default     = 3
}

# Retention

variable "log_retention_hours" {
  description = "Kafka log retention in hours"
  type        = number
  default     = 168 # 7 days
}

variable "log_retention_bytes" {
  description = "Kafka log retention in bytes per partition (-1 = unlimited)"
  type        = number
  default     = -1
}

# Security

variable "enable_plaintext" {
  description = "Enable plaintext (unauthenticated) access"
  type        = bool
  default     = false
}

variable "enable_tls" {
  description = "Enable TLS client access"
  type        = bool
  default     = true
}

variable "enable_iam_auth" {
  description = "Enable IAM authentication for MSK"
  type        = bool
  default     = true
}

variable "kms_key_arn" {
  description = "KMS key ARN for encryption at rest (null = AWS managed key)"
  type        = string
  default     = null
}

# Monitoring

variable "enhanced_monitoring_level" {
  description = "Enhanced monitoring level (DEFAULT, PER_BROKER, PER_TOPIC_PER_BROKER, PER_TOPIC_PER_PARTITION)"
  type        = string
  default     = "PER_TOPIC_PER_BROKER"
}

variable "log_group_retention_days" {
  description = "CloudWatch log retention in days"
  type        = number
  default     = 7
}

variable "enable_cloudwatch_alarms" {
  description = "Enable CloudWatch alarms for MSK"
  type        = bool
  default     = true
}

variable "sns_topic_arn" {
  description = "SNS topic ARN for alarm notifications"
  type        = string
  default     = ""
}
