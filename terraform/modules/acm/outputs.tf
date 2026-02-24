output "cloudfront_certificate_arn" {
  description = "ACM certificate ARN in us-east-1 for CloudFront"
  value       = aws_acm_certificate_validation.cloudfront.certificate_arn
}

output "alb_certificate_arn" {
  description = "ACM certificate ARN in ap-northeast-2 for ALB"
  value       = aws_acm_certificate_validation.alb.certificate_arn
}
