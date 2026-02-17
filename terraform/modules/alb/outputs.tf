output "alb_arn" {
  description = "ARN of the Application Load Balancer"
  value       = aws_lb.main.arn
}

output "alb_dns_name" {
  description = "DNS name of the Application Load Balancer"
  value       = aws_lb.main.dns_name
}

output "alb_zone_id" {
  description = "Zone ID of the Application Load Balancer"
  value       = aws_lb.main.zone_id
}

output "alb_security_group_id" {
  description = "Security group ID for ALB"
  value       = aws_security_group.alb.id
}

output "https_listener_arn" {
  description = "ARN of HTTPS listener"
  value       = var.certificate_arn != "" ? aws_lb_listener.https[0].arn : null
}

output "http_listener_arn" {
  description = "ARN of HTTP listener"
  value       = var.enable_http_listener ? aws_lb_listener.http[0].arn : null
}

# Target Group ARN and Name (for Kubernetes TargetGroupBinding)
output "gateway_service_target_group_arn" {
  description = "ARN of gateway service target group"
  value       = aws_lb_target_group.gateway_service.arn
}

output "gateway_service_target_group_name" {
  description = "Name of gateway service target group (for K8s TargetGroupBinding)"
  value       = aws_lb_target_group.gateway_service.name
}

output "frontend_target_group_arn" {
  description = "ARN of frontend target group"
  value       = aws_lb_target_group.frontend.arn
}

output "frontend_target_group_name" {
  description = "Name of frontend target group (for K8s TargetGroupBinding)"
  value       = aws_lb_target_group.frontend.name
}
