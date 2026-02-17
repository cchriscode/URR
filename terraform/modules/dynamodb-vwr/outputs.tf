output "counters_table_name" {
  description = "Name of the VWR counters DynamoDB table"
  value       = aws_dynamodb_table.vwr_counters.name
}

output "counters_table_arn" {
  description = "ARN of the VWR counters DynamoDB table"
  value       = aws_dynamodb_table.vwr_counters.arn
}

output "positions_table_name" {
  description = "Name of the VWR positions DynamoDB table"
  value       = aws_dynamodb_table.vwr_positions.name
}

output "positions_table_arn" {
  description = "ARN of the VWR positions DynamoDB table"
  value       = aws_dynamodb_table.vwr_positions.arn
}

output "positions_table_gsi_arn" {
  description = "ARN of the eventId-position GSI"
  value       = "${aws_dynamodb_table.vwr_positions.arn}/index/eventId-position-index"
}
