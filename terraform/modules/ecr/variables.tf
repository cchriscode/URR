variable "name_prefix" {
  description = "Prefix for repository names (e.g., urr)"
  type        = string
}

variable "service_names" {
  description = "List of service names to create repositories for"
  type        = list(string)
  default = [
    "gateway",
    "auth",
    "ticket",
    "payment",
    "queue",
    "stats",
    "catalog",
    "community",
    "frontend",
  ]
}

variable "max_image_count" {
  description = "Maximum number of tagged images to keep per repository"
  type        = number
  default     = 30
}
