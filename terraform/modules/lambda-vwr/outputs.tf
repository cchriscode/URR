output "vwr_api_function_name" {
  description = "Name of the VWR API Lambda function"
  value       = aws_lambda_function.vwr_api.function_name
}

output "vwr_api_function_arn" {
  description = "ARN of the VWR API Lambda function"
  value       = aws_lambda_function.vwr_api.arn
}

output "vwr_api_invoke_arn" {
  description = "Invoke ARN of the VWR API Lambda function"
  value       = aws_lambda_function.vwr_api.invoke_arn
}

output "vwr_counter_advancer_function_name" {
  description = "Name of the counter advancer Lambda function"
  value       = aws_lambda_function.vwr_counter_advancer.function_name
}

output "vwr_lambda_role_arn" {
  description = "ARN of the VWR Lambda IAM role"
  value       = aws_iam_role.vwr_lambda.arn
}
