output "queue_arn" {
  description = "ARN of the ticket events SQS queue"
  value       = aws_sqs_queue.ticket_events.arn
}

output "queue_url" {
  description = "URL of the ticket events SQS queue"
  value       = aws_sqs_queue.ticket_events.url
}

output "queue_name" {
  description = "Name of the ticket events SQS queue"
  value       = aws_sqs_queue.ticket_events.name
}

output "dlq_arn" {
  description = "ARN of the dead letter queue"
  value       = aws_sqs_queue.ticket_events_dlq.arn
}

output "dlq_url" {
  description = "URL of the dead letter queue"
  value       = aws_sqs_queue.ticket_events_dlq.url
}

output "dlq_name" {
  description = "Name of the dead letter queue"
  value       = aws_sqs_queue.ticket_events_dlq.name
}
