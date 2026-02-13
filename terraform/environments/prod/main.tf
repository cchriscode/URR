terraform {
  required_version = ">= 1.9"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }

  backend "s3" {
    bucket         = "tiketi-terraform-state-prod"
    key            = "prod/queue-infra/terraform.tfstate"
    region         = "ap-northeast-2"
    dynamodb_table = "tiketi-terraform-locks"
    encrypt        = true
  }
}

provider "aws" {
  region = var.aws_region

  default_tags {
    tags = {
      Project     = "Tiketi"
      Environment = var.environment
      ManagedBy   = "Terraform"
    }
  }
}

# Lambda@Edge requires us-east-1
provider "aws" {
  alias  = "us_east_1"
  region = "us-east-1"

  default_tags {
    tags = {
      Project     = "Tiketi"
      Environment = var.environment
      ManagedBy   = "Terraform"
    }
  }
}

# ─────────────────────────────────────────────────────────────────────────────
# SQS FIFO Queue
# ─────────────────────────────────────────────────────────────────────────────

module "sqs" {
  source = "../../modules/sqs"

  name_prefix            = var.name_prefix
  allowed_sender_role_arns = var.eks_node_role_arns
  lambda_worker_role_arn = var.lambda_worker_role_arn
  enable_cloudwatch_alarms = true
  sns_topic_arn          = var.sns_topic_arn
}

# ─────────────────────────────────────────────────────────────────────────────
# CloudFront + Lambda@Edge
# ─────────────────────────────────────────────────────────────────────────────

module "cloudfront" {
  source = "../../modules/cloudfront"

  providers = {
    aws           = aws
    aws.us_east_1 = aws.us_east_1
  }

  name_prefix                    = var.name_prefix
  alb_dns_name                   = var.alb_dns_name
  lambda_edge_role_arn           = var.lambda_edge_role_arn
  lambda_source_dir              = "${path.root}/../../lambda/edge-queue-check"
  queue_entry_token_secret       = var.queue_entry_token_secret
  cloudfront_custom_header_value = var.cloudfront_custom_header_value
  aliases                        = var.domain_aliases
  certificate_arn                = var.certificate_arn
  price_class                    = "PriceClass_200"
}

# ─────────────────────────────────────────────────────────────────────────────
# Lambda Worker (SQS Consumer)
# ─────────────────────────────────────────────────────────────────────────────

module "lambda_worker" {
  source = "../../modules/lambda-worker"

  name_prefix                    = var.name_prefix
  lambda_worker_role_arn         = var.lambda_worker_role_arn
  lambda_source_dir              = "${path.root}/../../lambda/ticket-worker"
  lambda_timeout                 = 30
  lambda_memory_size             = 256
  reserved_concurrent_executions = 10

  # VPC
  vpc_id                         = var.vpc_id
  vpc_cidr                       = var.vpc_cidr
  subnet_ids                     = var.private_subnet_ids
  rds_proxy_security_group_id    = var.rds_proxy_security_group_id
  redis_security_group_id        = var.redis_security_group_id

  # Environment
  db_proxy_endpoint = var.db_proxy_endpoint
  redis_endpoint    = var.redis_endpoint
  redis_auth_token  = var.redis_auth_token
  environment       = var.environment

  additional_env_vars = {
    TICKET_SERVICE_URL = "http://ticket-service.tiketi-spring.svc.cluster.local:3002"
    INTERNAL_API_TOKEN = var.internal_api_token
  }

  # SQS
  sqs_queue_arn            = module.sqs.queue_arn
  sqs_batch_size           = 10
  sqs_batching_window_seconds = 5
  max_concurrency          = 10

  # Monitoring
  enable_xray_tracing      = true
  enable_cloudwatch_alarms = true
  sns_topic_arn            = var.sns_topic_arn
}
