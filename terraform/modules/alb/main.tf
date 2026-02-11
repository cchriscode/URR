# ─────────────────────────────────────────────────────────────────────────────
# Security Group for ALB
# ─────────────────────────────────────────────────────────────────────────────

resource "aws_security_group" "alb" {
  name_prefix = "${var.name_prefix}-alb-"
  description = "Security group for Application Load Balancer"
  vpc_id      = var.vpc_id

  tags = {
    Name = "${var.name_prefix}-alb-sg"
  }

  lifecycle {
    create_before_destroy = true
  }
}

# Allow HTTPS from CloudFront ONLY (using managed prefix list)
# SECURITY: This prevents direct access to ALB, forcing all traffic through CloudFront
resource "aws_security_group_rule" "alb_ingress_https_cloudfront" {
  count             = var.use_cloudfront_prefix_list ? 1 : 0
  type              = "ingress"
  from_port         = 443
  to_port           = 443
  protocol          = "tcp"
  prefix_list_ids   = [var.cloudfront_prefix_list_id]
  security_group_id = aws_security_group.alb.id
  description       = "Allow HTTPS from CloudFront only (managed prefix list)"
}

# Fallback: Allow HTTPS from custom CIDRs (less secure, for testing only)
resource "aws_security_group_rule" "alb_ingress_https_cidr" {
  count             = var.use_cloudfront_prefix_list ? 0 : 1
  type              = "ingress"
  from_port         = 443
  to_port           = 443
  protocol          = "tcp"
  cidr_blocks       = var.alb_ingress_cidrs
  security_group_id = aws_security_group.alb.id
  description       = "Allow HTTPS from custom CIDRs (fallback for testing)"
}

# Allow HTTP from CloudFront for redirect to HTTPS
resource "aws_security_group_rule" "alb_ingress_http_cloudfront" {
  count             = var.enable_http_listener && var.use_cloudfront_prefix_list ? 1 : 0
  type              = "ingress"
  from_port         = 80
  to_port           = 80
  protocol          = "tcp"
  prefix_list_ids   = [var.cloudfront_prefix_list_id]
  security_group_id = aws_security_group.alb.id
  description       = "Allow HTTP from CloudFront for redirect"
}

# Fallback: Allow HTTP from custom CIDRs
resource "aws_security_group_rule" "alb_ingress_http_cidr" {
  count             = var.enable_http_listener && !var.use_cloudfront_prefix_list ? 1 : 0
  type              = "ingress"
  from_port         = 80
  to_port           = 80
  protocol          = "tcp"
  cidr_blocks       = var.alb_ingress_cidrs
  security_group_id = aws_security_group.alb.id
  description       = "Allow HTTP from custom CIDRs (fallback for testing)"
}

# Allow all outbound traffic to EKS nodes
resource "aws_security_group_rule" "alb_egress_all" {
  type              = "egress"
  from_port         = 0
  to_port           = 0
  protocol          = "-1"
  cidr_blocks       = ["0.0.0.0/0"]
  security_group_id = aws_security_group.alb.id
  description       = "Allow all outbound traffic"
}

# Add rule to EKS nodes to allow traffic from ALB
resource "aws_security_group_rule" "eks_nodes_ingress_from_alb" {
  type                     = "ingress"
  from_port                = 0
  to_port                  = 65535
  protocol                 = "tcp"
  source_security_group_id = aws_security_group.alb.id
  security_group_id        = var.eks_node_security_group_id
  description              = "Allow traffic from ALB to EKS nodes"
}

# ─────────────────────────────────────────────────────────────────────────────
# Application Load Balancer
# ─────────────────────────────────────────────────────────────────────────────

resource "aws_lb" "main" {
  name               = "${var.name_prefix}-alb"
  internal           = false
  load_balancer_type = "application"
  security_groups    = [aws_security_group.alb.id]
  subnets            = var.public_subnet_ids

  enable_deletion_protection = var.enable_deletion_protection
  enable_http2               = true
  enable_cross_zone_load_balancing = true

  drop_invalid_header_fields = true

  tags = {
    Name = "${var.name_prefix}-alb"
  }
}

# ─────────────────────────────────────────────────────────────────────────────
# Target Group for Gateway Service
# Architecture: CloudFront → ALB → Gateway-Service (port 3001)
#              Gateway internally routes to: ticket, payment, stats, auth services
# ─────────────────────────────────────────────────────────────────────────────

resource "aws_lb_target_group" "gateway_service" {
  name     = "${var.name_prefix}-gateway-tg"
  port     = 3001
  protocol = "HTTP"
  vpc_id   = var.vpc_id

  target_type = "ip"

  health_check {
    enabled             = true
    path                = "/actuator/health"
    protocol            = "HTTP"
    port                = "traffic-port"
    healthy_threshold   = 2
    unhealthy_threshold = 3
    timeout             = 5
    interval            = 30
    matcher             = "200"
  }

  deregistration_delay = 30

  stickiness {
    type            = "lb_cookie"
    cookie_duration = 86400
    enabled         = true
  }

  tags = {
    Name = "${var.name_prefix}-gateway-tg"
  }
}

# ─────────────────────────────────────────────────────────────────────────────
# HTTPS Listener (if certificate provided)
# All traffic goes to gateway-service which handles internal routing
# ─────────────────────────────────────────────────────────────────────────────

resource "aws_lb_listener" "https" {
  count             = var.certificate_arn != "" ? 1 : 0
  load_balancer_arn = aws_lb.main.arn
  port              = "443"
  protocol          = "HTTPS"
  ssl_policy        = "ELBSecurityPolicy-TLS13-1-2-2021-06"
  certificate_arn   = var.certificate_arn

  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.gateway_service.arn
  }

  tags = {
    Name = "${var.name_prefix}-https-listener"
  }
}

# ─────────────────────────────────────────────────────────────────────────────
# HTTP Listener (redirect to HTTPS or serve directly)
# ─────────────────────────────────────────────────────────────────────────────

resource "aws_lb_listener" "http" {
  count             = var.enable_http_listener ? 1 : 0
  load_balancer_arn = aws_lb.main.arn
  port              = "80"
  protocol          = "HTTP"

  default_action {
    type = var.certificate_arn != "" ? "redirect" : "forward"

    dynamic "redirect" {
      for_each = var.certificate_arn != "" ? [1] : []
      content {
        port        = "443"
        protocol    = "HTTPS"
        status_code = "HTTP_301"
      }
    }

    target_group_arn = var.certificate_arn == "" ? aws_lb_target_group.gateway_service.arn : null
  }

  tags = {
    Name = "${var.name_prefix}-http-listener"
  }
}
