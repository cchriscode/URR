# ─────────────────────────────────────────────────────────────────────────────
# Route53 Hosted Zone & Records
# ─────────────────────────────────────────────────────────────────────────────

resource "aws_route53_zone" "main" {
  count = var.create_hosted_zone ? 1 : 0

  name    = var.domain_name
  comment = "${var.name_prefix} hosted zone"

  tags = {
    Name = "${var.name_prefix}-zone"
  }
}

locals {
  zone_id = var.create_hosted_zone ? aws_route53_zone.main[0].zone_id : var.hosted_zone_id
}

# A record: domain → CloudFront
resource "aws_route53_record" "cloudfront_alias" {
  zone_id = local.zone_id
  name    = var.domain_name
  type    = "A"

  alias {
    name                   = var.cloudfront_domain_name
    zone_id                = var.cloudfront_hosted_zone_id
    evaluate_target_health = false
  }
}

# www → same CloudFront (optional)
resource "aws_route53_record" "www_alias" {
  count   = var.create_www_record ? 1 : 0
  zone_id = local.zone_id
  name    = "www.${var.domain_name}"
  type    = "A"

  alias {
    name                   = var.cloudfront_domain_name
    zone_id                = var.cloudfront_hosted_zone_id
    evaluate_target_health = false
  }
}
