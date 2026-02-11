output "vpc_id" {
  description = "VPC ID"
  value       = aws_vpc.main.id
}

output "vpc_cidr" {
  description = "VPC CIDR block"
  value       = aws_vpc.main.cidr_block
}

output "public_subnet_ids" {
  description = "List of public subnet IDs"
  value       = aws_subnet.public[*].id
}

output "app_subnet_ids" {
  description = "List of private app subnet IDs"
  value       = aws_subnet.app[*].id
}

output "db_subnet_ids" {
  description = "List of private database subnet IDs"
  value       = aws_subnet.db[*].id
}

output "cache_subnet_ids" {
  description = "List of private cache subnet IDs"
  value       = aws_subnet.cache[*].id
}

output "streaming_subnet_ids" {
  description = "List of private streaming subnet IDs"
  value       = aws_subnet.streaming[*].id
}

output "availability_zones" {
  description = "List of availability zones used"
  value       = local.azs
}

output "nat_gateway_ids" {
  description = "List of NAT Gateway IDs"
  value       = aws_nat_gateway.main[*].id
}

output "route_table_ids" {
  description = "List of all route table IDs (for VPC endpoints)"
  value = concat(
    [aws_route_table.public.id],
    aws_route_table.app[*].id,
    [aws_route_table.db.id],
    aws_route_table.streaming[*].id
  )
}

output "public_route_table_id" {
  description = "Public route table ID"
  value       = aws_route_table.public.id
}

output "app_route_table_ids" {
  description = "App route table IDs"
  value       = aws_route_table.app[*].id
}

output "db_route_table_id" {
  description = "DB route table ID"
  value       = aws_route_table.db.id
}

output "streaming_route_table_ids" {
  description = "Streaming route table IDs"
  value       = aws_route_table.streaming[*].id
}
