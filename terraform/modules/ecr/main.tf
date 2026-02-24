# ─────────────────────────────────────────────────────────────────────────────
# ECR Repositories for all microservices
# ─────────────────────────────────────────────────────────────────────────────

locals {
  repositories = toset(var.service_names)
}

resource "aws_ecr_repository" "services" {
  for_each = local.repositories

  name                 = "${var.name_prefix}/${each.key}"
  image_tag_mutability = "MUTABLE"

  image_scanning_configuration {
    scan_on_push = true
  }

  encryption_configuration {
    encryption_type = "AES256"
  }

  tags = {
    Name    = "${var.name_prefix}/${each.key}"
    Service = each.key
  }
}

# Lifecycle policy: keep last N tagged images, expire untagged after 7 days
resource "aws_ecr_lifecycle_policy" "services" {
  for_each   = local.repositories
  repository = aws_ecr_repository.services[each.key].name

  policy = jsonencode({
    rules = [
      {
        rulePriority = 1
        description  = "Expire untagged images after 7 days"
        selection = {
          tagStatus   = "untagged"
          countType   = "sinceImagePushed"
          countUnit   = "days"
          countNumber = 7
        }
        action = {
          type = "expire"
        }
      },
      {
        rulePriority = 2
        description  = "Keep last ${var.max_image_count} images"
        selection = {
          tagStatus   = "any"
          countType   = "imageCountMoreThan"
          countNumber = var.max_image_count
        }
        action = {
          type = "expire"
        }
      }
    ]
  })
}
