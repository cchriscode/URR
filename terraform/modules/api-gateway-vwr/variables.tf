variable "name_prefix" {
  description = "Prefix for resource names"
  type        = string
}

variable "lambda_invoke_arn" {
  description = "Invoke ARN of the VWR API Lambda function"
  type        = string
}

variable "stage_name" {
  description = "API Gateway stage name"
  type        = string
  default     = "v1"
}

variable "cors_origin" {
  description = "CORS allowed origin"
  type        = string
  default     = "*"
}

variable "throttling_burst_limit" {
  description = "API Gateway burst limit (TPS)"
  type        = number
  default     = 10000
}

variable "throttling_rate_limit" {
  description = "API Gateway steady-state rate limit (TPS)"
  type        = number
  default     = 5000
}
