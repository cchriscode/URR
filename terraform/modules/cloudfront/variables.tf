variable "name_prefix" {
  description = "Prefix for resource names"
  type        = string
}

variable "alb_dns_name" {
  description = "DNS name of the Application Load Balancer"
  type        = string
}

variable "lambda_edge_role_arn" {
  description = "ARN of Lambda@Edge IAM role"
  type        = string
}

variable "lambda_source_dir" {
  description = "Directory containing Lambda@Edge source code (use absolute path or path.root)"
  type        = string
  default     = ""  # Must be provided by root module
}

variable "queue_entry_token_secret" {
  description = "Secret for queue entry token verification"
  type        = string
  sensitive   = true
}

variable "s3_bucket_name" {
  description = "S3 bucket name for static assets (optional)"
  type        = string
  default     = ""
}

variable "s3_bucket_regional_domain_name" {
  description = "S3 bucket regional domain name"
  type        = string
  default     = ""
}

variable "certificate_arn" {
  description = "ARN of ACM certificate in us-east-1 for CloudFront"
  type        = string
  default     = ""
}

variable "aliases" {
  description = "List of domain aliases for CloudFront distribution"
  type        = list(string)
  default     = []
}

variable "price_class" {
  description = "CloudFront price class"
  type        = string
  default     = "PriceClass_100"
}

variable "cloudfront_custom_header_value" {
  description = "Custom header value to verify requests from CloudFront"
  type        = string
  sensitive   = true
}

variable "cors_allowed_origins" {
  description = "List of allowed origins for CORS"
  type        = list(string)
  default     = ["*"]
}
