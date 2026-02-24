terraform {
  required_providers {
    aws = {
      source                = "hashicorp/aws"
      version               = ">= 5.0"
      configuration_aliases = [aws.us_east_1]
    }
  }
}

# ─────────────────────────────────────────────────────────────────────────────
# ACM Certificate for CloudFront (must be in us-east-1)
# ─────────────────────────────────────────────────────────────────────────────

resource "aws_acm_certificate" "cloudfront" {
  provider          = aws.us_east_1
  domain_name       = var.domain_name
  subject_alternative_names = ["*.${var.domain_name}"]
  validation_method = "DNS"

  lifecycle {
    create_before_destroy = true
  }

  tags = {
    Name = "${var.name_prefix}-cloudfront-cert"
  }
}

# ─────────────────────────────────────────────────────────────────────────────
# ACM Certificate for ALB (must be in the same region as ALB)
# ─────────────────────────────────────────────────────────────────────────────

resource "aws_acm_certificate" "alb" {
  domain_name       = var.domain_name
  subject_alternative_names = ["*.${var.domain_name}"]
  validation_method = "DNS"

  lifecycle {
    create_before_destroy = true
  }

  tags = {
    Name = "${var.name_prefix}-alb-cert"
  }
}

# ─────────────────────────────────────────────────────────────────────────────
# DNS Validation Records (shared between both certificates)
# ─────────────────────────────────────────────────────────────────────────────

locals {
  # Merge validation options from both certs, deduplicate by domain name
  cloudfront_dvos = {
    for dvo in aws_acm_certificate.cloudfront.domain_validation_options : dvo.domain_name => {
      name   = dvo.resource_record_name
      record = dvo.resource_record_value
      type   = dvo.resource_record_type
    }
  }
  alb_dvos = {
    for dvo in aws_acm_certificate.alb.domain_validation_options : dvo.domain_name => {
      name   = dvo.resource_record_name
      record = dvo.resource_record_value
      type   = dvo.resource_record_type
    }
  }
  # Same domain produces same validation CNAME, so merge deduplicates
  all_dvos = merge(local.cloudfront_dvos, local.alb_dvos)
}

resource "aws_route53_record" "validation" {
  for_each = local.all_dvos

  zone_id         = var.zone_id
  name            = each.value.name
  type            = each.value.type
  records         = [each.value.record]
  ttl             = 60
  allow_overwrite = true
}

# ─────────────────────────────────────────────────────────────────────────────
# Certificate Validation (wait for DNS validation to complete)
# ─────────────────────────────────────────────────────────────────────────────

resource "aws_acm_certificate_validation" "cloudfront" {
  provider                = aws.us_east_1
  certificate_arn         = aws_acm_certificate.cloudfront.arn
  validation_record_fqdns = [for record in aws_route53_record.validation : record.fqdn]
}

resource "aws_acm_certificate_validation" "alb" {
  certificate_arn         = aws_acm_certificate.alb.arn
  validation_record_fqdns = [for record in aws_route53_record.validation : record.fqdn]
}
