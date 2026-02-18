variable "name_prefix" {
  description = "Prefix for resource names"
  type        = string
}

variable "lambda_source_dir" {
  description = "Path to VWR API Lambda source directory"
  type        = string
}

variable "counter_advancer_source_dir" {
  description = "Path to counter advancer Lambda source directory"
  type        = string
}

variable "lambda_timeout" {
  description = "Lambda timeout in seconds"
  type        = number
  default     = 10
}

variable "lambda_memory_size" {
  description = "Lambda memory in MB"
  type        = number
  default     = 256
}

variable "reserved_concurrent_executions" {
  description = "Reserved concurrent executions for VWR API Lambda"
  type        = number
  default     = 500
}

variable "dynamodb_counters_table_name" {
  description = "Name of the VWR counters DynamoDB table"
  type        = string
}

variable "dynamodb_positions_table_name" {
  description = "Name of the VWR positions DynamoDB table"
  type        = string
}

variable "dynamodb_table_arns" {
  description = "List of DynamoDB table ARNs (including GSI ARNs) for Lambda IAM policy"
  type        = list(string)
}

variable "vwr_token_secret" {
  description = "HMAC secret for signing Tier 1 VWR JWT tokens"
  type        = string
  sensitive   = true
}

variable "cors_origin" {
  description = "CORS allowed origin for VWR API"
  type        = string
  default     = "*"
}

variable "api_gateway_execution_arn" {
  description = "API Gateway execution ARN for Lambda permission"
  type        = string
}

variable "counter_advance_batch_size" {
  description = "Number of positions to advance per batch"
  type        = number
  default     = 500
}
