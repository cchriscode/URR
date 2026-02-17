output "amp_workspace_id" {
  description = "AMP workspace ID"
  value       = aws_prometheus_workspace.main.id
}

output "amp_remote_write_url" {
  description = "AMP remote write endpoint URL"
  value       = "${aws_prometheus_workspace.main.prometheus_endpoint}api/v1/remote_write"
}

output "amp_query_url" {
  description = "AMP Prometheus query endpoint"
  value       = aws_prometheus_workspace.main.prometheus_endpoint
}

output "amg_workspace_id" {
  description = "AMG workspace ID"
  value       = aws_grafana_workspace.main.id
}

output "amg_endpoint" {
  description = "AMG workspace endpoint URL"
  value       = aws_grafana_workspace.main.endpoint
}

output "prometheus_irsa_role_arn" {
  description = "ARN of Prometheus IRSA role for remote_write"
  value       = aws_iam_role.prometheus_remote_write.arn
}
