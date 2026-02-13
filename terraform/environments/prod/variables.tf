variable "aws_region" {
  description = "AWS region"
  type        = string
  default     = "ap-northeast-2"
}

variable "environment" {
  description = "Environment name"
  type        = string
  default     = "prod"
}

variable "name_prefix" {
  description = "Prefix for resource names"
  type        = string
  default     = "tiketi-prod"
}

# VPC
variable "vpc_id" {
  description = "VPC ID"
  type        = string
}

variable "vpc_cidr" {
  description = "VPC CIDR block"
  type        = string
}

variable "private_subnet_ids" {
  description = "Private subnet IDs"
  type        = list(string)
}

variable "public_subnet_ids" {
  description = "Public subnet IDs"
  type        = list(string)
}

# ALB
variable "alb_dns_name" {
  description = "ALB DNS name"
  type        = string
}

# Security Groups
variable "rds_proxy_security_group_id" {
  description = "Security group ID for RDS Proxy"
  type        = string
}

variable "redis_security_group_id" {
  description = "Security group ID for Redis"
  type        = string
}

# Database
variable "db_proxy_endpoint" {
  description = "RDS Proxy endpoint"
  type        = string
}

# Redis
variable "redis_endpoint" {
  description = "Redis primary endpoint"
  type        = string
}

variable "redis_auth_token" {
  description = "Redis AUTH token"
  type        = string
  sensitive   = true
}

# IAM
variable "lambda_edge_role_arn" {
  description = "Lambda@Edge IAM role ARN"
  type        = string
}

variable "lambda_worker_role_arn" {
  description = "Lambda Worker IAM role ARN"
  type        = string
}

variable "eks_node_role_arns" {
  description = "EKS node role ARNs (for SQS send permissions)"
  type        = list(string)
  default     = []
}

# Secrets
variable "queue_entry_token_secret" {
  description = "HMAC secret for queue entry token JWT"
  type        = string
  sensitive   = true
}

variable "cloudfront_custom_header_value" {
  description = "Secret header value for CloudFront → ALB verification"
  type        = string
  sensitive   = true
}

variable "internal_api_token" {
  description = "Internal API token for service-to-service auth"
  type        = string
  sensitive   = true
}

# Domain
variable "domain_aliases" {
  description = "CloudFront domain aliases"
  type        = list(string)
  default     = []
}

variable "certificate_arn" {
  description = "ACM certificate ARN (us-east-1)"
  type        = string
  default     = ""
}

# SNS
variable "sns_topic_arn" {
  description = "SNS topic ARN for alarms"
  type        = string
  default     = ""
}
