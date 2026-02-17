output "web_acl_arn" {
  description = "ARN of the WAFv2 Web ACL (for CloudFront association)"
  value       = aws_wafv2_web_acl.cloudfront.arn
}

output "web_acl_id" {
  description = "ID of the WAFv2 Web ACL"
  value       = aws_wafv2_web_acl.cloudfront.id
}
