# ─────────────────────────────────────────────────────────────────────────────
# DynamoDB Tables for VWR (Virtual Waiting Room) Tier 1
# ─────────────────────────────────────────────────────────────────────────────

# Counters table: atomic position assignment + serving counter per event
resource "aws_dynamodb_table" "vwr_counters" {
  name         = "${var.name_prefix}-vwr-counters"
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "eventId"

  attribute {
    name = "eventId"
    type = "S"
  }

  point_in_time_recovery {
    enabled = var.enable_point_in_time_recovery
  }

  tags = {
    Name = "${var.name_prefix}-vwr-counters"
  }
}

# Positions table: individual queue position records
resource "aws_dynamodb_table" "vwr_positions" {
  name         = "${var.name_prefix}-vwr-positions"
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "eventId"
  range_key    = "requestId"

  attribute {
    name = "eventId"
    type = "S"
  }

  attribute {
    name = "requestId"
    type = "S"
  }

  attribute {
    name = "position"
    type = "N"
  }

  # TTL: auto-delete old position records after 24 hours
  ttl {
    attribute_name = "ttl"
    enabled        = true
  }

  # GSI for querying by position within an event
  global_secondary_index {
    name            = "eventId-position-index"
    hash_key        = "eventId"
    range_key       = "position"
    projection_type = "ALL"
  }

  point_in_time_recovery {
    enabled = var.enable_point_in_time_recovery
  }

  tags = {
    Name = "${var.name_prefix}-vwr-positions"
  }
}
