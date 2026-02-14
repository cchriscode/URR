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
  default     = "tiketi-prod"
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
  description = "EC2 instance types for EKS node group"
  type        = list(string)
  default     = ["t3.medium"]
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
# Domain & TLS
# ═════════════════════════════════════════════════════════════════════════════

variable "domain_aliases" {
  description = "CloudFront domain aliases"
  type        = list(string)
  default     = []
}

variable "certificate_arn" {
  description = "ACM certificate ARN (us-east-1 for CloudFront, ap-northeast-2 for ALB)"
  type        = string
  default     = ""
}

variable "cors_allowed_origins" {
  description = "Allowed origins for CORS (e.g. [\"https://tiketi.com\"])"
  type        = list(string)
  default     = []
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
