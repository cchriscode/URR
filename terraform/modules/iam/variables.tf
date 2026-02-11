variable "name_prefix" {
  description = "Prefix for all IAM role names"
  type        = string
}

variable "sqs_queue_arn" {
  description = "ARN of SQS queue for Lambda worker access"
  type        = string
  default     = ""
}

variable "db_credentials_secret_arn" {
  description = "ARN of Secrets Manager secret for RDS credentials"
  type        = string
  default     = ""
}
