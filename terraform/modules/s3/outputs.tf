output "frontend_bucket_id" {
  description = "ID of frontend S3 bucket"
  value       = aws_s3_bucket.frontend.id
}

output "frontend_bucket_arn" {
  description = "ARN of frontend S3 bucket"
  value       = aws_s3_bucket.frontend.arn
}

output "frontend_bucket_name" {
  description = "Name of frontend S3 bucket"
  value       = aws_s3_bucket.frontend.bucket
}

output "frontend_bucket_regional_domain_name" {
  description = "Regional domain name of frontend S3 bucket"
  value       = aws_s3_bucket.frontend.bucket_regional_domain_name
}

output "logs_bucket_id" {
  description = "ID of logs S3 bucket"
  value       = var.create_logs_bucket ? aws_s3_bucket.logs[0].id : null
}

output "logs_bucket_arn" {
  description = "ARN of logs S3 bucket"
  value       = var.create_logs_bucket ? aws_s3_bucket.logs[0].arn : null
}

output "logs_bucket_name" {
  description = "Name of logs S3 bucket"
  value       = var.create_logs_bucket ? aws_s3_bucket.logs[0].bucket : null
}
