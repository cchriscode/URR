variable "name_prefix" {
  description = "Prefix for resource names"
  type        = string
}

variable "enable_point_in_time_recovery" {
  description = "Enable point-in-time recovery for DynamoDB tables"
  type        = bool
  default     = false
}
