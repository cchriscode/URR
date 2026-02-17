variable "name_prefix" {
  description = "Resource name prefix"
  type        = string
}

variable "oidc_provider_arn" {
  description = "ARN of EKS OIDC provider for IRSA"
  type        = string
}

variable "oidc_provider_url" {
  description = "OIDC provider URL without https:// prefix"
  type        = string
}
