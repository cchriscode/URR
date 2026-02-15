terraform {
  backend "s3" {
    bucket         = "urr-terraform-state-staging"
    key            = "staging/terraform.tfstate"
    region         = "ap-northeast-2"
    dynamodb_table = "urr-terraform-locks"
    encrypt        = true
  }
}
