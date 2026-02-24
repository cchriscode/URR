# ═════════════════════════════════════════════════════════════════════════════
# VPC
# ═════════════════════════════════════════════════════════════════════════════

output "vpc_id" {
  description = "VPC ID"
  value       = module.vpc.vpc_id
}

output "vpc_cidr" {
  description = "VPC CIDR block"
  value       = module.vpc.vpc_cidr
}

# ═════════════════════════════════════════════════════════════════════════════
# EKS
# ═════════════════════════════════════════════════════════════════════════════

output "eks_cluster_name" {
  description = "EKS cluster name"
  value       = module.eks.cluster_name
}

output "eks_cluster_endpoint" {
  description = "EKS cluster API endpoint"
  value       = module.eks.cluster_endpoint
}

output "eks_cluster_certificate_authority" {
  description = "EKS cluster CA certificate data"
  value       = module.eks.cluster_certificate_authority_data
  sensitive   = true
}

output "eks_oidc_provider_arn" {
  description = "EKS OIDC provider ARN for IRSA"
  value       = module.eks.oidc_provider_arn
}

# ═════════════════════════════════════════════════════════════════════════════
# RDS
# ═════════════════════════════════════════════════════════════════════════════

output "rds_endpoint" {
  description = "RDS instance endpoint (host:port)"
  value       = module.rds.db_instance_endpoint
}

output "rds_proxy_endpoint" {
  description = "RDS Proxy endpoint"
  value       = module.rds.rds_proxy_endpoint
}

output "rds_connection_string" {
  description = "RDS JDBC connection string"
  value       = module.rds.connection_string
  sensitive   = true
}

# ═════════════════════════════════════════════════════════════════════════════
# ElastiCache (Redis)
# ═════════════════════════════════════════════════════════════════════════════

output "redis_primary_endpoint" {
  description = "Redis primary endpoint"
  value       = module.elasticache.primary_endpoint_address
}

output "redis_reader_endpoint" {
  description = "Redis reader endpoint"
  value       = module.elasticache.reader_endpoint_address
}

output "redis_connection_string" {
  description = "Redis connection string"
  value       = module.elasticache.connection_string
  sensitive   = true
}

# ═════════════════════════════════════════════════════════════════════════════
# MSK (Kafka)
# ═════════════════════════════════════════════════════════════════════════════

output "msk_cluster_arn" {
  description = "MSK cluster ARN"
  value       = module.msk.cluster_arn
}

output "msk_bootstrap_brokers_tls" {
  description = "MSK TLS bootstrap broker connection string"
  value       = module.msk.bootstrap_brokers_tls
}

output "msk_bootstrap_brokers_iam" {
  description = "MSK IAM bootstrap broker connection string"
  value       = module.msk.bootstrap_brokers_iam
}

# ═════════════════════════════════════════════════════════════════════════════
# ALB
# ═════════════════════════════════════════════════════════════════════════════

output "alb_dns_name" {
  description = "ALB DNS name"
  value       = module.alb.alb_dns_name
}

output "alb_zone_id" {
  description = "ALB hosted zone ID (for Route53 alias records)"
  value       = module.alb.alb_zone_id
}

# ═════════════════════════════════════════════════════════════════════════════
# CloudFront
# ═════════════════════════════════════════════════════════════════════════════

output "cloudfront_distribution_id" {
  description = "CloudFront distribution ID"
  value       = module.cloudfront.distribution_id
}

output "cloudfront_domain_name" {
  description = "CloudFront distribution domain name"
  value       = module.cloudfront.distribution_domain_name
}

output "cloudfront_hosted_zone_id" {
  description = "CloudFront hosted zone ID (for Route53 alias records)"
  value       = module.cloudfront.distribution_hosted_zone_id
}

# ═════════════════════════════════════════════════════════════════════════════
# S3
# ═════════════════════════════════════════════════════════════════════════════

output "frontend_bucket_name" {
  description = "S3 frontend bucket name"
  value       = module.s3.frontend_bucket_name
}

output "logs_bucket_name" {
  description = "S3 logs bucket name"
  value       = module.s3.logs_bucket_name
}

# ═════════════════════════════════════════════════════════════════════════════
# SQS
# ═════════════════════════════════════════════════════════════════════════════

output "sqs_queue_url" {
  description = "SQS FIFO queue URL"
  value       = module.sqs.queue_url
}

output "sqs_queue_arn" {
  description = "SQS FIFO queue ARN"
  value       = module.sqs.queue_arn
}

output "sqs_dlq_url" {
  description = "SQS dead-letter queue URL"
  value       = module.sqs.dlq_url
}

# ═════════════════════════════════════════════════════════════════════════════
# Secrets Manager
# ═════════════════════════════════════════════════════════════════════════════

output "rds_credentials_secret_arn" {
  description = "Secrets Manager ARN for RDS credentials"
  value       = module.secrets.rds_credentials_secret_arn
}

output "redis_auth_token_secret_arn" {
  description = "Secrets Manager ARN for Redis auth token"
  value       = module.secrets.redis_auth_token_secret_arn
}

output "jwt_secret_arn" {
  description = "Secrets Manager ARN for JWT signing secret"
  value       = module.secrets.jwt_secret_arn
}

# ═════════════════════════════════════════════════════════════════════════════
# Lambda Worker
# ═════════════════════════════════════════════════════════════════════════════

output "ticket_worker_function_name" {
  description = "Lambda ticket worker function name"
  value       = module.lambda_worker.function_name
}

output "ticket_worker_log_group" {
  description = "Lambda ticket worker CloudWatch log group"
  value       = module.lambda_worker.log_group_name
}

# ═════════════════════════════════════════════════════════════════════════════
# ACM Certificates
# ═════════════════════════════════════════════════════════════════════════════

output "acm_cloudfront_certificate_arn" {
  description = "ACM certificate ARN for CloudFront (us-east-1)"
  value       = var.domain_name != "" ? module.acm[0].cloudfront_certificate_arn : ""
}

output "acm_alb_certificate_arn" {
  description = "ACM certificate ARN for ALB (ap-northeast-2)"
  value       = var.domain_name != "" ? module.acm[0].alb_certificate_arn : ""
}

# ═════════════════════════════════════════════════════════════════════════════
# Route53
# ═════════════════════════════════════════════════════════════════════════════

output "route53_name_servers" {
  description = "Route53 hosted zone name servers (set these in GoDaddy)"
  value       = var.domain_name != "" ? aws_route53_zone.main[0].name_servers : []
}
