variable "name_prefix" {
  description = "Prefix for resource names"
  type        = string
}

variable "message_retention_seconds" {
  description = "Message retention period in seconds"
  type        = number
  default     = 345600  # 4 days
}

variable "visibility_timeout_seconds" {
  description = "Visibility timeout in seconds"
  type        = number
  default     = 300  # 5 minutes
}

variable "max_receive_count" {
  description = "Maximum receives before moving to DLQ"
  type        = number
  default     = 3
}

variable "allowed_sender_role_arns" {
  description = "List of IAM role ARNs allowed to send messages (e.g., EKS node roles)"
  type        = list(string)
  default     = []
}

variable "lambda_worker_role_arn" {
  description = "IAM role ARN for Lambda worker"
  type        = string
  default     = ""
}

variable "enable_cloudwatch_alarms" {
  description = "Enable CloudWatch alarms for queue monitoring"
  type        = bool
  default     = true
}

variable "sns_topic_arn" {
  description = "SNS topic ARN for alarm notifications"
  type        = string
  default     = ""
}
