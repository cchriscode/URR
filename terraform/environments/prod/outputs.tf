# SQS
output "sqs_queue_url" {
  description = "SQS FIFO queue URL"
  value       = module.sqs.queue_url
}

output "sqs_queue_arn" {
  description = "SQS FIFO queue ARN"
  value       = module.sqs.queue_arn
}

# CloudFront
output "cloudfront_distribution_id" {
  description = "CloudFront distribution ID"
  value       = module.cloudfront.distribution_id
}

output "cloudfront_domain_name" {
  description = "CloudFront distribution domain name"
  value       = module.cloudfront.distribution_domain_name
}

# Lambda Worker
output "ticket_worker_function_name" {
  description = "Lambda ticket worker function name"
  value       = module.lambda_worker.function_name
}

output "ticket_worker_log_group" {
  description = "Lambda ticket worker log group"
  value       = module.lambda_worker.log_group_name
}
