output "vpc_endpoints_security_group_id" {
  description = "Security group ID for VPC endpoints"
  value       = aws_security_group.vpc_endpoints.id
}

output "s3_endpoint_id" {
  description = "ID of S3 gateway endpoint"
  value       = aws_vpc_endpoint.s3.id
}

output "ec2_endpoint_id" {
  description = "ID of EC2 interface endpoint"
  value       = aws_vpc_endpoint.ec2.id
}

output "ecr_api_endpoint_id" {
  description = "ID of ECR API interface endpoint"
  value       = aws_vpc_endpoint.ecr_api.id
}

output "ecr_dkr_endpoint_id" {
  description = "ID of ECR DKR interface endpoint"
  value       = aws_vpc_endpoint.ecr_dkr.id
}

output "eks_endpoint_id" {
  description = "ID of EKS interface endpoint"
  value       = aws_vpc_endpoint.eks.id
}

output "sts_endpoint_id" {
  description = "ID of STS interface endpoint"
  value       = aws_vpc_endpoint.sts.id
}

output "logs_endpoint_id" {
  description = "ID of CloudWatch Logs interface endpoint"
  value       = aws_vpc_endpoint.logs.id
}

output "secretsmanager_endpoint_id" {
  description = "ID of Secrets Manager interface endpoint"
  value       = aws_vpc_endpoint.secretsmanager.id
}

output "elb_endpoint_id" {
  description = "ID of ELB interface endpoint"
  value       = aws_vpc_endpoint.elb.id
}

output "autoscaling_endpoint_id" {
  description = "ID of Auto Scaling interface endpoint"
  value       = aws_vpc_endpoint.autoscaling.id
}
