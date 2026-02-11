variable "name_prefix" {
  description = "Prefix for resource names"
  type        = string
}

variable "environment" {
  description = "Environment name (dev, staging, prod)"
  type        = string
  default     = "prod"
}

variable "enable_versioning" {
  description = "Enable S3 bucket versioning"
  type        = bool
  default     = true
}

variable "enable_lifecycle_rules" {
  description = "Enable lifecycle rules for cost optimization"
  type        = bool
  default     = true
}

variable "cors_allowed_origins" {
  description = "List of allowed origins for CORS"
  type        = list(string)
  default     = ["*"]
}

variable "cloudfront_distribution_arn" {
  description = "ARN of CloudFront distribution for bucket policy"
  type        = string
  default     = ""
}

variable "create_logs_bucket" {
  description = "Create separate bucket for application logs"
  type        = bool
  default     = true
}

variable "logs_retention_days" {
  description = "Number of days to retain logs in S3"
  type        = number
  default     = 90
}
