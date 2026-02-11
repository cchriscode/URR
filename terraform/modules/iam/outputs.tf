output "eks_cluster_role_arn" {
  description = "ARN of EKS cluster IAM role"
  value       = aws_iam_role.eks_cluster.arn
}

output "eks_node_role_arn" {
  description = "ARN of EKS node IAM role"
  value       = aws_iam_role.eks_node.arn
}

output "eks_node_role_name" {
  description = "Name of EKS node IAM role"
  value       = aws_iam_role.eks_node.name
}

output "lambda_worker_role_arn" {
  description = "ARN of Lambda worker IAM role"
  value       = aws_iam_role.lambda_worker.arn
}

output "lambda_edge_role_arn" {
  description = "ARN of Lambda@Edge IAM role"
  value       = aws_iam_role.lambda_edge.arn
}

output "rds_monitoring_role_arn" {
  description = "ARN of RDS enhanced monitoring IAM role"
  value       = aws_iam_role.rds_monitoring.arn
}

output "rds_proxy_role_arn" {
  description = "ARN of RDS Proxy IAM role"
  value       = aws_iam_role.rds_proxy.arn
}
