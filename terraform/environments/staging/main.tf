terraform {
  required_version = ">= 1.9"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }

  backend "s3" {
    bucket         = "urr-terraform-state-staging"
    key            = "staging/terraform.tfstate"
    region         = "ap-northeast-2"
    dynamodb_table = "urr-terraform-locks"
    encrypt        = true
  }
}

# ═════════════════════════════════════════════════════════════════════════════
# 1. IAM Roles (no dependencies)
# ═════════════════════════════════════════════════════════════════════════════

module "iam" {
  source = "../../modules/iam"

  name_prefix              = var.name_prefix
  sqs_queue_arn            = module.sqs.queue_arn
  db_credentials_secret_arn = module.secrets.rds_credentials_secret_arn
}

# ═════════════════════════════════════════════════════════════════════════════
# 2. VPC (no dependencies)
# ═════════════════════════════════════════════════════════════════════════════

module "vpc" {
  source = "../../modules/vpc"

  name_prefix = var.name_prefix
  vpc_cidr    = var.vpc_cidr
  environment = var.environment
}

# ═════════════════════════════════════════════════════════════════════════════
# 3. Secrets Manager (depends on: RDS endpoint for secret version)
# ═════════════════════════════════════════════════════════════════════════════

module "secrets" {
  source = "../../modules/secrets"

  name_prefix  = var.name_prefix
  rds_username = "urr_admin"
  rds_endpoint = module.rds.db_instance_address
}

# ═════════════════════════════════════════════════════════════════════════════
# 4. VPC Endpoints (depends on: VPC)
# ═════════════════════════════════════════════════════════════════════════════

module "vpc_endpoints" {
  source = "../../modules/vpc-endpoints"

  name_prefix    = var.name_prefix
  vpc_id         = module.vpc.vpc_id
  vpc_cidr       = module.vpc.vpc_cidr
  region         = var.aws_region
  app_subnet_ids = module.vpc.app_subnet_ids
  route_table_ids = module.vpc.route_table_ids
}

# ═════════════════════════════════════════════════════════════════════════════
# 5. EKS Cluster (depends on: VPC, IAM) - Reduced sizing for staging
# ═════════════════════════════════════════════════════════════════════════════

module "eks" {
  source = "../../modules/eks"

  name_prefix        = var.name_prefix
  vpc_id             = module.vpc.vpc_id
  app_subnet_ids     = module.vpc.app_subnet_ids
  public_subnet_ids  = module.vpc.public_subnet_ids
  eks_cluster_role_arn = module.iam.eks_cluster_role_arn
  eks_node_role_arn  = module.iam.eks_node_role_arn

  cluster_version                   = var.eks_cluster_version
  cluster_endpoint_public_access    = var.eks_cluster_endpoint_public_access
  cluster_endpoint_public_access_cidrs = var.eks_cluster_endpoint_public_access_cidrs
  kms_key_arn                       = var.kms_key_arn
  node_group_desired_size           = var.eks_node_desired_size
  node_group_min_size               = var.eks_node_min_size
  node_group_max_size               = var.eks_node_max_size
  node_instance_types               = var.eks_node_instance_types
  node_capacity_type                = var.eks_node_capacity_type
}

# ═════════════════════════════════════════════════════════════════════════════
# 6. RDS PostgreSQL - Single-AZ, no proxy, deletion_protection off
# ═════════════════════════════════════════════════════════════════════════════

module "rds" {
  source = "../../modules/rds"

  name_prefix                 = var.name_prefix
  vpc_id                      = module.vpc.vpc_id
  db_subnet_ids               = module.vpc.db_subnet_ids
  app_subnet_ids              = module.vpc.app_subnet_ids
  eks_node_security_group_id      = module.eks.node_security_group_id
  lambda_worker_security_group_id = module.lambda_worker.security_group_id
  master_password                 = module.secrets.rds_password
  db_credentials_secret_arn       = module.secrets.rds_credentials_secret_arn
  rds_proxy_role_arn              = module.iam.rds_proxy_role_arn
  monitoring_role_arn             = module.iam.rds_monitoring_role_arn

  engine_version       = var.rds_engine_version
  instance_class       = var.rds_instance_class
  allocated_storage    = var.rds_allocated_storage
  multi_az             = false
  enable_rds_proxy     = true
  deletion_protection  = false
  enable_read_replica  = false
}

# ═════════════════════════════════════════════════════════════════════════════
# 7. ElastiCache Redis - 1 replica for staging
# ═════════════════════════════════════════════════════════════════════════════

module "elasticache" {
  source = "../../modules/elasticache"

  name_prefix                = var.name_prefix
  vpc_id                     = module.vpc.vpc_id
  cache_subnet_ids           = module.vpc.cache_subnet_ids
  eks_node_security_group_id = module.eks.node_security_group_id
  preferred_azs              = module.vpc.availability_zones

  node_type          = var.elasticache_node_type
  auth_token_enabled = true
  auth_token         = module.secrets.redis_auth_token
  num_cache_clusters = 1
}

# ═════════════════════════════════════════════════════════════════════════════
# 8. Amazon MSK - Kafka (same 2 brokers, smaller instance)
# ═════════════════════════════════════════════════════════════════════════════

module "msk" {
  source = "../../modules/msk"

  name_prefix                = var.name_prefix
  vpc_id                     = module.vpc.vpc_id
  streaming_subnet_ids       = module.vpc.streaming_subnet_ids
  eks_node_security_group_id = module.eks.node_security_group_id

  kafka_version          = "3.6.0"
  number_of_broker_nodes = 2
  broker_instance_type   = var.msk_broker_instance_type
  broker_ebs_volume_size = var.msk_broker_ebs_volume_size
  default_partitions     = 3

  enable_tls       = true
  enable_iam_auth  = true
  enable_plaintext = false
}

# ═════════════════════════════════════════════════════════════════════════════
# 9. ALB (depends on: VPC, EKS) - No deletion protection for staging
# ═════════════════════════════════════════════════════════════════════════════

module "alb" {
  source = "../../modules/alb"

  name_prefix                = var.name_prefix
  vpc_id                     = module.vpc.vpc_id
  public_subnet_ids          = module.vpc.public_subnet_ids
  eks_node_security_group_id = module.eks.node_security_group_id
  certificate_arn            = var.certificate_arn
  enable_deletion_protection = false
}

# ═════════════════════════════════════════════════════════════════════════════
# 10. S3 Buckets (no CloudFront in staging - use ALB directly)
# ═════════════════════════════════════════════════════════════════════════════

module "s3" {
  source = "../../modules/s3"

  name_prefix                = var.name_prefix
  environment                = var.environment
  cloudfront_distribution_arn = ""
  cors_allowed_origins       = var.cors_allowed_origins
}

# ═════════════════════════════════════════════════════════════════════════════
# 11. SQS FIFO Queue (same as prod)
# ═════════════════════════════════════════════════════════════════════════════

module "sqs" {
  source = "../../modules/sqs"

  name_prefix              = var.name_prefix
  allowed_sender_role_arns = [module.iam.eks_node_role_arn]
  lambda_worker_role_arn   = module.iam.lambda_worker_role_arn
  enable_cloudwatch_alarms = true
  sns_topic_arn            = var.sns_topic_arn
}

# ═════════════════════════════════════════════════════════════════════════════
# 12. DynamoDB - VWR Tier 1 Tables (no dependencies)
# ═════════════════════════════════════════════════════════════════════════════

module "dynamodb_vwr" {
  source = "../../modules/dynamodb-vwr"

  name_prefix                   = var.name_prefix
  enable_point_in_time_recovery = false
}

# ═════════════════════════════════════════════════════════════════════════════
# 13. VWR API Gateway + Lambda (depends on: DynamoDB VWR, Secrets)
# ═════════════════════════════════════════════════════════════════════════════

module "api_gateway_vwr" {
  source = "../../modules/api-gateway-vwr"

  name_prefix            = var.name_prefix
  lambda_invoke_arn      = module.lambda_vwr.vwr_api_invoke_arn
  cors_origin            = var.cors_allowed_origins[0]
  throttling_burst_limit = 2000
  throttling_rate_limit  = 1000
}

module "lambda_vwr" {
  source = "../../modules/lambda-vwr"

  name_prefix                    = var.name_prefix
  lambda_source_dir              = "${path.root}/../../lambda/vwr-api"
  counter_advancer_source_dir    = "${path.root}/../../lambda/vwr-counter-advancer"
  reserved_concurrent_executions = 20
  counter_advance_batch_size     = 100

  dynamodb_counters_table_name = module.dynamodb_vwr.counters_table_name
  dynamodb_positions_table_name = module.dynamodb_vwr.positions_table_name
  dynamodb_table_arns = [
    module.dynamodb_vwr.counters_table_arn,
    module.dynamodb_vwr.positions_table_arn,
    module.dynamodb_vwr.positions_table_gsi_arn,
  ]

  vwr_token_secret          = module.secrets.queue_entry_token_secret_value
  cors_origin               = var.cors_allowed_origins[0]
  api_gateway_execution_arn = module.api_gateway_vwr.api_gateway_execution_arn
}

# ═════════════════════════════════════════════════════════════════════════════
# 14. Lambda Worker - SQS Consumer (depends on: VPC, RDS, ElastiCache, SQS, IAM)
# ═════════════════════════════════════════════════════════════════════════════

module "lambda_worker" {
  source = "../../modules/lambda-worker"

  name_prefix            = var.name_prefix
  lambda_worker_role_arn = module.iam.lambda_worker_role_arn
  lambda_source_dir      = "${path.root}/../../lambda/ticket-worker"
  lambda_timeout         = 30
  lambda_memory_size     = 256
  reserved_concurrent_executions = 5

  # VPC
  vpc_id                      = module.vpc.vpc_id
  vpc_cidr                    = module.vpc.vpc_cidr
  subnet_ids                  = module.vpc.streaming_subnet_ids
  rds_proxy_security_group_id = module.rds.rds_proxy_security_group_id
  redis_security_group_id     = module.elasticache.security_group_id

  # Environment
  db_proxy_endpoint = module.rds.rds_proxy_endpoint
  redis_endpoint    = module.elasticache.primary_endpoint_address
  redis_auth_token  = module.secrets.redis_auth_token
  environment       = var.environment

  additional_env_vars = {
    TICKET_SERVICE_URL      = "http://ticket-service.urr-spring.svc.cluster.local:3002"
    INTERNAL_API_TOKEN      = var.internal_api_token
    KAFKA_BOOTSTRAP_SERVERS = module.msk.bootstrap_brokers_tls
  }

  # SQS
  sqs_queue_arn               = module.sqs.queue_arn
  sqs_batch_size              = 10
  sqs_batching_window_seconds = 5
  max_concurrency             = 5

  # Monitoring
  enable_xray_tracing      = true
  enable_cloudwatch_alarms = true
  sns_topic_arn            = var.sns_topic_arn
}

# ═════════════════════════════════════════════════════════════════════════════
# 15. Monitoring - AMP + AMG (depends on: EKS for OIDC)
# ═════════════════════════════════════════════════════════════════════════════

module "monitoring" {
  source = "../../modules/monitoring"

  name_prefix       = var.name_prefix
  oidc_provider_arn = module.eks.oidc_provider_arn
  oidc_provider_url = replace(module.eks.cluster_oidc_issuer_url, "https://", "")
}
