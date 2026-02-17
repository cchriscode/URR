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
# Lambda@Edge Function for Queue Token Verification
# (Must be deployed in us-east-1 for CloudFront)
# ─────────────────────────────────────────────────────────────────────────────

# SECRET ROTATION PROCEDURE:
# 1. Update secret in AWS Secrets Manager
# 2. Run: terraform apply (triggers Lambda@Edge redeployment)
# 3. Wait for CloudFront distribution propagation (~5-15 minutes)
# Automation: CI/CD can detect secret version changes and trigger terraform apply

# Generate config.json with secrets baked in (Lambda@Edge cannot use env vars)
resource "local_file" "edge_config" {
  content = jsonencode({
    secret    = var.queue_entry_token_secret
    vwrSecret = var.vwr_token_secret != "" ? var.vwr_token_secret : var.queue_entry_token_secret
  })
  filename = "${var.lambda_source_dir}/config.json"
}

# Generate vwr-active.json with active VWR events (empty by default)
resource "local_file" "vwr_active_config" {
  content  = jsonencode({ activeEvents = var.vwr_active_events })
  filename = "${var.lambda_source_dir}/vwr-active.json"
}

# Package Lambda function code
# NOTE: lambda_source_dir must be absolute path or use ${path.root}/lambda/edge-queue-check
data "archive_file" "edge_function" {
  type        = "zip"
  source_dir  = var.lambda_source_dir
  output_path = "${path.module}/lambda-edge-queue-check.zip"

  depends_on = [local_file.edge_config, local_file.vwr_active_config]
}

# Lambda function (in us-east-1)
resource "aws_lambda_function" "edge_queue_check" {
  provider = aws.us_east_1

  filename         = data.archive_file.edge_function.output_path
  function_name    = "${var.name_prefix}-edge-queue-check"
  role             = var.lambda_edge_role_arn
  handler          = "index.handler"
  source_code_hash = data.archive_file.edge_function.output_base64sha256
  runtime          = "nodejs20.x"
  timeout          = 5
  memory_size      = 128
  publish          = true  # Must publish for Lambda@Edge

  tags = {
    Name = "${var.name_prefix}-edge-queue-check"
  }
}

# ─────────────────────────────────────────────────────────────────────────────
# CloudFront Origin Access Control (for S3 origin)
# ─────────────────────────────────────────────────────────────────────────────

resource "aws_cloudfront_origin_access_control" "s3" {
  name                              = "${var.name_prefix}-s3-oac"
  description                       = "OAC for S3 bucket ${var.s3_bucket_name}"
  origin_access_control_origin_type = "s3"
  signing_behavior                  = "always"
  signing_protocol                  = "sigv4"
}

# ─────────────────────────────────────────────────────────────────────────────
# CloudFront Distribution
# ─────────────────────────────────────────────────────────────────────────────

resource "aws_cloudfront_distribution" "main" {
  enabled         = true
  is_ipv6_enabled = true
  comment         = "${var.name_prefix} ticketing system distribution"
  price_class     = var.price_class
  aliases         = var.aliases
  web_acl_id      = var.web_acl_arn

  # ALB origin for API traffic
  origin {
    domain_name = var.alb_dns_name
    origin_id   = "alb"

    custom_origin_config {
      http_port              = 80
      https_port             = 443
      origin_protocol_policy = "https-only"
      origin_ssl_protocols   = ["TLSv1.2"]
    }

    custom_header {
      name  = "X-Custom-Header"
      value = var.cloudfront_custom_header_value
    }
  }

  # S3 origin for static assets (/_next/static/*, /vwr/*)
  origin {
    domain_name              = var.s3_bucket_regional_domain_name
    origin_id                = "s3"
    origin_access_control_id = aws_cloudfront_origin_access_control.s3.id
  }

  # VWR API Gateway origin (if provided)
  dynamic "origin" {
    for_each = var.vwr_api_gateway_domain != "" ? [1] : []
    content {
      domain_name = var.vwr_api_gateway_domain
      origin_id   = "vwr-api"
      origin_path = "/${var.vwr_api_gateway_stage}"

      custom_origin_config {
        http_port              = 80
        https_port             = 443
        origin_protocol_policy = "https-only"
        origin_ssl_protocols   = ["TLSv1.2"]
      }
    }
  }

  # Default cache behavior: ALB → Frontend Pod (Next.js SSR)
  default_cache_behavior {
    allowed_methods        = ["DELETE", "GET", "HEAD", "OPTIONS", "PATCH", "POST", "PUT"]
    cached_methods         = ["GET", "HEAD", "OPTIONS"]
    target_origin_id       = "alb"
    viewer_protocol_policy = "redirect-to-https"
    compress               = true

    cache_policy_id            = aws_cloudfront_cache_policy.frontend_ssr.id
    origin_request_policy_id   = aws_cloudfront_origin_request_policy.api.id
    response_headers_policy_id = aws_cloudfront_response_headers_policy.security.id
  }

  # API traffic to ALB (no caching, with Lambda@Edge for VWR queue token verification)
  ordered_cache_behavior {
    path_pattern           = "/api/*"
    allowed_methods        = ["DELETE", "GET", "HEAD", "OPTIONS", "PATCH", "POST", "PUT"]
    cached_methods         = ["GET", "HEAD", "OPTIONS"]
    target_origin_id       = "alb"
    viewer_protocol_policy = "redirect-to-https"
    compress               = true

    cache_policy_id            = aws_cloudfront_cache_policy.api.id
    origin_request_policy_id   = aws_cloudfront_origin_request_policy.api.id
    response_headers_policy_id = aws_cloudfront_response_headers_policy.security.id

    lambda_function_association {
      event_type   = "viewer-request"
      lambda_arn   = aws_lambda_function.edge_queue_check.qualified_arn
      include_body = false
    }
  }

  # Cache behavior for Next.js static files (immutable, 1-year TTL)
  ordered_cache_behavior {
    path_pattern           = "/_next/static/*"
    allowed_methods        = ["GET", "HEAD", "OPTIONS"]
    cached_methods         = ["GET", "HEAD", "OPTIONS"]
    target_origin_id       = "s3"
    viewer_protocol_policy = "redirect-to-https"
    compress               = true

    cache_policy_id            = data.aws_cloudfront_cache_policy.caching_optimized.id
    origin_request_policy_id   = data.aws_cloudfront_origin_request_policy.cors_s3.id
    response_headers_policy_id = aws_cloudfront_response_headers_policy.security.id
    # TTL is controlled by Managed-CachingOptimized cache policy (default 86400s)
    # For immutable Next.js assets, origin sends Cache-Control: max-age=31536000
  }

  # Cache behavior for VWR static waiting page (S3, no Lambda@Edge)
  # CloudFront Function rewrites /vwr/{eventId} -> /vwr/index.html for S3
  ordered_cache_behavior {
    path_pattern           = "/vwr/*"
    allowed_methods        = ["GET", "HEAD", "OPTIONS"]
    cached_methods         = ["GET", "HEAD", "OPTIONS"]
    target_origin_id       = "s3"
    viewer_protocol_policy = "redirect-to-https"
    compress               = true

    cache_policy_id            = aws_cloudfront_cache_policy.vwr_static.id
    origin_request_policy_id   = data.aws_cloudfront_origin_request_policy.cors_s3.id
    response_headers_policy_id = aws_cloudfront_response_headers_policy.security.id

    function_association {
      event_type   = "viewer-request"
      function_arn = aws_cloudfront_function.vwr_page_rewrite.arn
    }
  }

  # Cache behavior for VWR API (API Gateway, no caching)
  # CloudFront Function strips /vwr-api prefix before forwarding to API Gateway
  dynamic "ordered_cache_behavior" {
    for_each = var.vwr_api_gateway_domain != "" ? [1] : []
    content {
      path_pattern           = "/vwr-api/*"
      allowed_methods        = ["DELETE", "GET", "HEAD", "OPTIONS", "PATCH", "POST", "PUT"]
      cached_methods         = ["GET", "HEAD", "OPTIONS"]
      target_origin_id       = "vwr-api"
      viewer_protocol_policy = "redirect-to-https"
      compress               = true

      cache_policy_id            = aws_cloudfront_cache_policy.api.id
      origin_request_policy_id   = aws_cloudfront_origin_request_policy.api.id
      response_headers_policy_id = aws_cloudfront_response_headers_policy.security.id

      function_association {
        event_type   = "viewer-request"
        function_arn = aws_cloudfront_function.vwr_api_rewrite[0].arn
      }
    }
  }

  # Restrictions
  restrictions {
    geo_restriction {
      restriction_type = "none"
    }
  }

  # SSL/TLS certificate
  viewer_certificate {
    cloudfront_default_certificate = var.certificate_arn == ""
    acm_certificate_arn            = var.certificate_arn != "" ? var.certificate_arn : null
    ssl_support_method             = var.certificate_arn != "" ? "sni-only" : null
    minimum_protocol_version       = "TLSv1.2_2021"
  }

  tags = {
    Name = "${var.name_prefix}-cloudfront"
  }
}

# ─────────────────────────────────────────────────────────────────────────────
# Cache Policy for API Traffic (no caching, forward all)
# ─────────────────────────────────────────────────────────────────────────────

# ─────────────────────────────────────────────────────────────────────────────
# Cache Policy for Frontend SSR (short TTL, forward cookies for auth)
# ─────────────────────────────────────────────────────────────────────────────

resource "aws_cloudfront_cache_policy" "frontend_ssr" {
  name        = "${var.name_prefix}-frontend-ssr-cache-policy"
  comment     = "Cache policy for Next.js SSR pages - short TTL"
  default_ttl = 0
  max_ttl     = 60
  min_ttl     = 0

  parameters_in_cache_key_and_forwarded_to_origin {
    enable_accept_encoding_brotli = true
    enable_accept_encoding_gzip   = true

    cookies_config {
      cookie_behavior = "all"
    }

    headers_config {
      header_behavior = "whitelist"
      headers {
        items = ["Authorization", "Host"]
      }
    }

    query_strings_config {
      query_string_behavior = "all"
    }
  }
}

resource "aws_cloudfront_cache_policy" "vwr_static" {
  name        = "${var.name_prefix}-vwr-static-cache-policy"
  comment     = "Cache policy for VWR static waiting page"
  default_ttl = 300
  max_ttl     = 3600
  min_ttl     = 0

  parameters_in_cache_key_and_forwarded_to_origin {
    enable_accept_encoding_brotli = true
    enable_accept_encoding_gzip   = true

    cookies_config {
      cookie_behavior = "none"
    }

    headers_config {
      header_behavior = "none"
    }

    query_strings_config {
      query_string_behavior = "none"
    }
  }
}

resource "aws_cloudfront_cache_policy" "api" {
  name        = "${var.name_prefix}-api-cache-policy"
  comment     = "Cache policy for API traffic - no caching"
  default_ttl = 0
  max_ttl     = 0
  min_ttl     = 0

  parameters_in_cache_key_and_forwarded_to_origin {
    enable_accept_encoding_brotli = true
    enable_accept_encoding_gzip   = true

    cookies_config {
      cookie_behavior = "all"
    }

    headers_config {
      header_behavior = "whitelist"
      headers {
        items = ["Authorization", "CloudFront-Viewer-Country", "Host"]
      }
    }

    query_strings_config {
      query_string_behavior = "all"
    }
  }
}

# ─────────────────────────────────────────────────────────────────────────────
# Origin Request Policy for API Traffic
# ─────────────────────────────────────────────────────────────────────────────

resource "aws_cloudfront_origin_request_policy" "api" {
  name    = "${var.name_prefix}-api-origin-request"
  comment = "Origin request policy for API traffic"

  cookies_config {
    cookie_behavior = "all"
  }

  headers_config {
    header_behavior = "allViewerAndWhitelistCloudFront"
    headers {
      items = ["CloudFront-Viewer-Country"]
    }
  }

  query_strings_config {
    query_string_behavior = "all"
  }
}

# ─────────────────────────────────────────────────────────────────────────────
# Response Headers Policy (Security headers)
# ─────────────────────────────────────────────────────────────────────────────

resource "aws_cloudfront_response_headers_policy" "security" {
  name    = "${var.name_prefix}-security-headers"
  comment = "Security headers policy"

  security_headers_config {
    strict_transport_security {
      access_control_max_age_sec = 31536000
      include_subdomains         = true
      preload                    = true
      override                   = true
    }

    content_type_options {
      override = true
    }

    frame_options {
      frame_option = "DENY"
      override     = true
    }

    xss_protection {
      mode_block = true
      protection = true
      override   = true
    }

    referrer_policy {
      referrer_policy = "strict-origin-when-cross-origin"
      override        = true
    }
  }

  cors_config {
    access_control_allow_credentials = true

    access_control_allow_headers {
      items = ["*"]
    }

    access_control_allow_methods {
      items = ["GET", "HEAD", "OPTIONS", "POST", "PUT", "PATCH", "DELETE"]
    }

    access_control_allow_origins {
      items = var.cors_allowed_origins
    }

    access_control_max_age_sec = 3600
    origin_override            = false
  }
}

# ─────────────────────────────────────────────────────────────────────────────
# Managed Cache Policies (reference existing AWS managed policies)
# ─────────────────────────────────────────────────────────────────────────────

# ─────────────────────────────────────────────────────────────────────────────
# CloudFront Function to strip /vwr-api prefix for API Gateway origin
# ─────────────────────────────────────────────────────────────────────────────

resource "aws_cloudfront_function" "vwr_api_rewrite" {
  count   = var.vwr_api_gateway_domain != "" ? 1 : 0
  name    = "${var.name_prefix}-vwr-api-rewrite"
  runtime = "cloudfront-js-2.0"
  comment = "Strips /vwr-api prefix before forwarding to API Gateway"
  publish = true

  code = <<-EOF
    function handler(event) {
      var request = event.request;
      request.uri = request.uri.replace(/^\/vwr-api/, '');
      if (request.uri === '') request.uri = '/';
      return request;
    }
  EOF
}

# Rewrites /vwr/{eventId} to /vwr/index.html so S3 serves the SPA
resource "aws_cloudfront_function" "vwr_page_rewrite" {
  name    = "${var.name_prefix}-vwr-page-rewrite"
  runtime = "cloudfront-js-2.0"
  comment = "Rewrites /vwr/{eventId} to /vwr/index.html for S3 static page"
  publish = true

  code = <<-EOF
    function handler(event) {
      var request = event.request;
      request.uri = '/vwr/index.html';
      return request;
    }
  EOF
}

data "aws_cloudfront_cache_policy" "caching_optimized" {
  name = "Managed-CachingOptimized"
}

data "aws_cloudfront_origin_request_policy" "cors_s3" {
  name = "Managed-CORS-S3Origin"
}
