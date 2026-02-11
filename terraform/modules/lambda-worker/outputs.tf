output "function_arn" {
  description = "ARN of the Lambda function"
  value       = aws_lambda_function.ticket_worker.arn
}

output "function_name" {
  description = "Name of the Lambda function"
  value       = aws_lambda_function.ticket_worker.function_name
}

output "function_version" {
  description = "Version of the Lambda function"
  value       = aws_lambda_function.ticket_worker.version
}

output "log_group_name" {
  description = "Name of CloudWatch log group"
  value       = aws_cloudwatch_log_group.lambda.name
}

output "event_source_mapping_uuid" {
  description = "UUID of SQS event source mapping"
  value       = aws_lambda_event_source_mapping.sqs_trigger.uuid
}

output "security_group_id" {
  description = "Security group ID for Lambda worker"
  value       = aws_security_group.lambda_worker.id
}
