# ═════════════════════════════════════════════════════════════════════════════
# General
# ═════════════════════════════════════════════════════════════════════════════

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
  default     = "urr-prod"
}

# ═════════════════════════════════════════════════════════════════════════════
# VPC
# ═════════════════════════════════════════════════════════════════════════════

variable "vpc_cidr" {
  description = "VPC CIDR block"
  type        = string
  default     = "10.0.0.0/16"
}

# ═════════════════════════════════════════════════════════════════════════════
# EKS
# ═════════════════════════════════════════════════════════════════════════════

variable "eks_cluster_version" {
  description = "Kubernetes version for EKS cluster"
  type        = string
  default     = "1.31"
}

variable "eks_cluster_endpoint_public_access" {
  description = "Whether the EKS API server endpoint is publicly accessible"
  type        = bool
  default     = true
}

variable "eks_cluster_endpoint_public_access_cidrs" {
  description = "CIDR blocks allowed to access the EKS API server endpoint"
  type        = list(string)
  default     = ["0.0.0.0/0"]
}

variable "kms_key_arn" {
  description = "KMS key ARN for EKS secrets encryption (null = AWS managed key)"
  type        = string
  default     = null
}

variable "eks_node_desired_size" {
  description = "Desired number of EKS worker nodes"
  type        = number
  default     = 3
}

variable "eks_node_min_size" {
  description = "Minimum number of EKS worker nodes"
  type        = number
  default     = 2
}

variable "eks_node_max_size" {
  description = "Maximum number of EKS worker nodes"
  type        = number
  default     = 5
}

variable "eks_node_instance_types" {
  description = "EC2 instance types for EKS node group (ARM/Graviton for arm64 CI/CD images)"
  type        = list(string)
  default     = ["t4g.medium"]
}

variable "eks_node_capacity_type" {
  description = "EKS node capacity type (ON_DEMAND or SPOT)"
  type        = string
  default     = "ON_DEMAND"
}

# ═════════════════════════════════════════════════════════════════════════════
# RDS
# ═════════════════════════════════════════════════════════════════════════════

variable "rds_engine_version" {
  description = "PostgreSQL engine version"
  type        = string
  default     = "16.4"
}

variable "rds_instance_class" {
  description = "RDS instance class"
  type        = string
  default     = "db.t3.medium"
}

variable "rds_allocated_storage" {
  description = "RDS allocated storage in GB"
  type        = number
  default     = 50
}

# ═════════════════════════════════════════════════════════════════════════════
# MSK (Kafka)
# ═════════════════════════════════════════════════════════════════════════════

variable "msk_broker_instance_type" {
  description = "MSK broker instance type"
  type        = string
  default     = "kafka.t3.small"
}

variable "msk_broker_ebs_volume_size" {
  description = "MSK broker EBS volume size in GB"
  type        = number
  default     = 50
}

# ═════════════════════════════════════════════════════════════════════════════
# ElastiCache
# ═════════════════════════════════════════════════════════════════════════════

variable "elasticache_node_type" {
  description = "ElastiCache Redis node type"
  type        = string
  default     = "cache.t4g.medium"
}

# ═════════════════════════════════════════════════════════════════════════════
# Domain & TLS
# ═════════════════════════════════════════════════════════════════════════════

variable "cors_allowed_origins" {
  description = "Allowed origins for CORS (e.g. [\"https://urr.guru\"])"
  type        = list(string)
  default     = []

  validation {
    condition     = length(var.cors_allowed_origins) > 0
    error_message = "cors_allowed_origins must contain at least one origin for VWR API Gateway and Lambda CORS configuration."
  }
}

# ═════════════════════════════════════════════════════════════════════════════
# Secrets (external inputs)
# ═════════════════════════════════════════════════════════════════════════════

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

# ═════════════════════════════════════════════════════════════════════════════
# Monitoring
# ═════════════════════════════════════════════════════════════════════════════

variable "sns_topic_arn" {
  description = "SNS topic ARN for CloudWatch alarm notifications"
  type        = string
  default     = ""
}

# ═════════════════════════════════════════════════════════════════════════════
# WAF
# ═════════════════════════════════════════════════════════════════════════════

variable "waf_rate_limit" {
  description = "WAF rate limit: max requests per 5-min window per IP"
  type        = number
  default     = 2000
}

# ═════════════════════════════════════════════════════════════════════════════
# Route53
# ═════════════════════════════════════════════════════════════════════════════

variable "domain_name" {
  description = "Domain name for Route53 (e.g., urr.guru)"
  type        = string
  default     = ""
}

