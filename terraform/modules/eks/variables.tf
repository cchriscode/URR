variable "name_prefix" {
  description = "Prefix for resource names"
  type        = string
}

variable "vpc_id" {
  description = "VPC ID"
  type        = string
}

variable "app_subnet_ids" {
  description = "List of app subnet IDs for EKS nodes"
  type        = list(string)
}

variable "public_subnet_ids" {
  description = "List of public subnet IDs for EKS control plane ENIs"
  type        = list(string)
}

# IAM Roles (from iam module)

variable "eks_cluster_role_arn" {
  description = "ARN of EKS cluster IAM role"
  type        = string
}

variable "eks_node_role_arn" {
  description = "ARN of EKS node IAM role"
  type        = string
}

# Cluster Configuration

variable "cluster_version" {
  description = "Kubernetes version for EKS cluster"
  type        = string
  default     = "1.28"
}

variable "cluster_endpoint_public_access" {
  description = "Enable public API server endpoint (DISABLE for production security)"
  type        = bool
  default     = false  # Changed from true to false for security
}

variable "cluster_endpoint_public_access_cidrs" {
  description = "List of CIDR blocks that can access the public API server endpoint"
  type        = list(string)
  default     = ["0.0.0.0/0"]
}

variable "kms_key_arn" {
  description = "KMS key ARN for EKS secrets encryption"
  type        = string
  default     = ""
}

# Node Group Configuration

variable "node_group_desired_size" {
  description = "Desired number of worker nodes"
  type        = number
  default     = 2
}

variable "node_group_min_size" {
  description = "Minimum number of worker nodes"
  type        = number
  default     = 1
}

variable "node_group_max_size" {
  description = "Maximum number of worker nodes"
  type        = number
  default     = 10
}

variable "node_instance_types" {
  description = "List of instance types for worker nodes"
  type        = list(string)
  default     = ["t3.medium"]
}

variable "node_capacity_type" {
  description = "Type of capacity for nodes (ON_DEMAND or SPOT)"
  type        = string
  default     = "ON_DEMAND"
}

variable "node_disk_size" {
  description = "Disk size in GB for worker nodes"
  type        = number
  default     = 20
}

# EKS Addon Versions

variable "vpc_cni_version" {
  description = "Version of vpc-cni addon (null = use most recent compatible version)"
  type        = string
  default     = null
}

variable "kube_proxy_version" {
  description = "Version of kube-proxy addon (null = use most recent compatible version)"
  type        = string
  default     = null
}

variable "coredns_version" {
  description = "Version of coredns addon (null = use most recent compatible version)"
  type        = string
  default     = null
}

variable "ebs_csi_driver_version" {
  description = "Version of aws-ebs-csi-driver addon (null = use most recent compatible version)"
  type        = string
  default     = null
}

# Logging

variable "log_retention_days" {
  description = "CloudWatch log retention in days"
  type        = number
  default     = 7
}
