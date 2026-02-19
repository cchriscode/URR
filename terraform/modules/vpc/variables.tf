variable "name_prefix" {
  description = "Prefix for all resource names"
  type        = string
}

variable "vpc_cidr" {
  description = "CIDR block for VPC"
  type        = string
  default     = "10.0.0.0/16"
}

variable "environment" {
  description = "Environment name (dev/staging/prod)"
  type        = string
}

variable "single_nat_gateway" {
  description = "Use single NAT gateway instead of per-AZ (cost savings for non-prod)"
  type        = bool
  default     = false
}
