provider "aws" {
  region = var.aws_region

  default_tags {
    tags = {
      Project     = "URR"
      Environment = var.environment
      ManagedBy   = "Terraform"
    }
  }
}
