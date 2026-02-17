variable "name_prefix" {
  description = "Prefix for resource names"
  type        = string
}

variable "domain_name" {
  description = "Domain name (e.g., urr.guru)"
  type        = string
}

variable "create_hosted_zone" {
  description = "Whether to create a new hosted zone (false to use existing)"
  type        = bool
  default     = true
}

variable "hosted_zone_id" {
  description = "Existing hosted zone ID (required when create_hosted_zone = false)"
  type        = string
  default     = ""
}

variable "cloudfront_domain_name" {
  description = "CloudFront distribution domain name"
  type        = string
}

variable "cloudfront_hosted_zone_id" {
  description = "CloudFront Route53 hosted zone ID"
  type        = string
}

variable "create_www_record" {
  description = "Whether to create a www subdomain record"
  type        = bool
  default     = false
}
