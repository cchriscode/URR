provider "aws" {
  region = var.aws_region
  default_tags {
    tags = {
      Project     = "URR"
      Environment = "staging"
      ManagedBy   = "terraform"
    }
  }
}
