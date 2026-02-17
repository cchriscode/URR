variable "name_prefix" {
  description = "Prefix for repository names (e.g., urr)"
  type        = string
}

variable "service_names" {
  description = "List of service names to create repositories for"
  type        = list(string)
  default = [
    "gateway-service",
    "auth-service",
    "ticket-service",
    "payment-service",
    "queue-service",
    "stats-service",
    "catalog-service",
    "community-service",
    "frontend",
  ]
}

variable "max_image_count" {
  description = "Maximum number of tagged images to keep per repository"
  type        = number
  default     = 30
}
