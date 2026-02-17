output "cluster_id" {
  description = "EKS cluster ID"
  value       = aws_eks_cluster.main.id
}

output "cluster_name" {
  description = "EKS cluster name"
  value       = aws_eks_cluster.main.name
}

output "cluster_endpoint" {
  description = "Endpoint for EKS control plane"
  value       = aws_eks_cluster.main.endpoint
}

output "cluster_security_group_id" {
  description = "Security group ID attached to the EKS cluster"
  value       = aws_security_group.eks_cluster.id
}

output "cluster_certificate_authority_data" {
  description = "Base64 encoded certificate data required to communicate with the cluster"
  value       = aws_eks_cluster.main.certificate_authority[0].data
  sensitive   = true
}

output "cluster_oidc_issuer_url" {
  description = "The URL on the EKS cluster OIDC Issuer"
  value       = aws_eks_cluster.main.identity[0].oidc[0].issuer
}

output "oidc_provider_arn" {
  description = "ARN of the OIDC Provider for EKS"
  value       = aws_iam_openid_connect_provider.cluster.arn
}

output "node_security_group_id" {
  description = "Security group ID for EKS nodes (used by RDS, Redis, etc.)"
  value       = aws_security_group.eks_nodes.id
}

output "node_group_id" {
  description = "EKS node group ID"
  value       = aws_eks_node_group.main.id
}

output "node_group_status" {
  description = "Status of the EKS node group"
  value       = aws_eks_node_group.main.status
}

output "cloudwatch_log_group_name" {
  description = "Name of CloudWatch log group for EKS"
  value       = aws_cloudwatch_log_group.eks.name
}

output "vpc_cni_role_arn" {
  description = "ARN of VPC CNI IRSA role"
  value       = aws_iam_role.vpc_cni.arn
}

output "ebs_csi_driver_role_arn" {
  description = "ARN of EBS CSI driver IRSA role"
  value       = aws_iam_role.ebs_csi.arn
}

output "karpenter_role_arn" {
  description = "ARN of Karpenter controller IRSA role"
  value       = aws_iam_role.karpenter.arn
}
