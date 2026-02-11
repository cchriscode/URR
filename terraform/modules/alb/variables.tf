variable "name_prefix" {
  description = "Prefix for resource names"
  type        = string
}

variable "vpc_id" {
  description = "VPC ID"
  type        = string
}

variable "public_subnet_ids" {
  description = "List of public subnet IDs for ALB"
  type        = list(string)
}

variable "eks_node_security_group_id" {
  description = "Security group ID for EKS nodes"
  type        = string
}

# Security Configuration

variable "use_cloudfront_prefix_list" {
  description = "Use CloudFront managed prefix list for ALB ingress (RECOMMENDED for production)"
  type        = bool
  default     = true
}

variable "cloudfront_prefix_list_id" {
  description = "CloudFront managed prefix list ID (com.amazonaws.global.cloudfront.origin-facing)"
  type        = string
  default     = "pl-22a6434b"  # Global CloudFront prefix list
}

variable "alb_ingress_cidrs" {
  description = "List of CIDR blocks allowed to access ALB (fallback when not using prefix list, NOT RECOMMENDED for production)"
  type        = list(string)
  default     = ["0.0.0.0/0"]
}

# SSL/TLS Configuration

variable "certificate_arn" {
  description = "ARN of ACM certificate for HTTPS listener"
  type        = string
  default     = ""
}

variable "enable_http_listener" {
  description = "Enable HTTP listener (port 80)"
  type        = bool
  default     = true
}

# Protection

variable "enable_deletion_protection" {
  description = "Enable deletion protection for ALB"
  type        = bool
  default     = false
}
