output "distribution_id" {
  description = "ID of the CloudFront distribution"
  value       = aws_cloudfront_distribution.main.id
}

output "distribution_arn" {
  description = "ARN of the CloudFront distribution"
  value       = aws_cloudfront_distribution.main.arn
}

output "distribution_domain_name" {
  description = "Domain name of the CloudFront distribution"
  value       = aws_cloudfront_distribution.main.domain_name
}

output "distribution_hosted_zone_id" {
  description = "CloudFront Route 53 zone ID"
  value       = aws_cloudfront_distribution.main.hosted_zone_id
}

output "lambda_edge_function_arn" {
  description = "ARN of Lambda@Edge function (qualified)"
  value       = aws_lambda_function.edge_queue_check.qualified_arn
}

output "lambda_edge_function_version" {
  description = "Version of Lambda@Edge function"
  value       = aws_lambda_function.edge_queue_check.version
}
