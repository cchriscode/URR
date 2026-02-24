variable "name_prefix" {
  description = "Prefix for resource names"
  type        = string
}

variable "domain_name" {
  description = "Domain name for the certificate (e.g., urr.guru)"
  type        = string
}

variable "zone_id" {
  description = "Route53 hosted zone ID for DNS validation"
  type        = string
}
