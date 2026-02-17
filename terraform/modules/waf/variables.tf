variable "name_prefix" {
  description = "Prefix for resource names"
  type        = string
}

variable "rate_limit" {
  description = "Maximum requests per 5-minute window per IP"
  type        = number
  default     = 2000
}
