variable "name_prefix" {
  description = "Prefix for all secret names"
  type        = string
}

variable "recovery_window_in_days" {
  description = "Number of days to retain deleted secrets"
  type        = number
  default     = 7
}

variable "rds_username" {
  description = "RDS master username"
  type        = string
  default     = "tiketi_admin"
}

variable "rds_endpoint" {
  description = "RDS endpoint (set after RDS creation)"
  type        = string
  default     = ""
}
