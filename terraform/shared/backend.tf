# Terraform Backend Configuration
# S3 bucket and DynamoDB table must be created manually before first apply:
#
# aws s3api create-bucket \
#   --bucket urr-terraform-state-prod \
#   --region ap-northeast-2 \
#   --create-bucket-configuration LocationConstraint=ap-northeast-2
#
# aws s3api put-bucket-versioning \
#   --bucket urr-terraform-state-prod \
#   --versioning-configuration Status=Enabled
#
# aws s3api put-bucket-encryption \
#   --bucket urr-terraform-state-prod \
#   --server-side-encryption-configuration '{
#     "Rules": [{
#       "ApplyServerSideEncryptionByDefault": {
#         "SSEAlgorithm": "AES256"
#       }
#     }]
#   }'
#
# aws dynamodb create-table \
#   --table-name urr-terraform-locks \
#   --attribute-definitions AttributeName=LockID,AttributeType=S \
#   --key-schema AttributeName=LockID,KeyType=HASH \
#   --billing-mode PAY_PER_REQUEST \
#   --region ap-northeast-2

terraform {
  backend "s3" {
    # Backend config is environment-specific
    # Set via -backend-config flags or backend.hcl files
  }
}
