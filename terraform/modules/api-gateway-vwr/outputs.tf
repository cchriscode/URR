output "api_gateway_id" {
  description = "ID of the VWR API Gateway"
  value       = aws_api_gateway_rest_api.vwr.id
}

output "api_gateway_execution_arn" {
  description = "Execution ARN of the VWR API Gateway"
  value       = aws_api_gateway_rest_api.vwr.execution_arn
}

output "api_gateway_invoke_url" {
  description = "Invoke URL of the VWR API Gateway stage"
  value       = aws_api_gateway_stage.vwr.invoke_url
}

output "api_gateway_stage_name" {
  description = "Stage name of the VWR API Gateway"
  value       = aws_api_gateway_stage.vwr.stage_name
}
