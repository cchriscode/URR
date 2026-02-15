# ─────────────────────────────────────────────────────────────────────────────
# Lambda Function for VWR Tier 1 API
# ─────────────────────────────────────────────────────────────────────────────

data "archive_file" "vwr_api" {
  type        = "zip"
  source_dir  = var.lambda_source_dir
  output_path = "${path.module}/builds/vwr-api.zip"

  excludes = ["node_modules/.cache", "*.md"]
}

resource "aws_lambda_function" "vwr_api" {
  function_name    = "${var.name_prefix}-vwr-api"
  role             = aws_iam_role.vwr_lambda.arn
  handler          = "index.handler"
  runtime          = "nodejs20.x"
  timeout          = var.lambda_timeout
  memory_size      = var.lambda_memory_size
  filename         = data.archive_file.vwr_api.output_path
  source_code_hash = data.archive_file.vwr_api.output_base64sha256

  reserved_concurrent_executions = var.reserved_concurrent_executions

  environment {
    variables = {
      TABLE_COUNTERS   = var.dynamodb_counters_table_name
      TABLE_POSITIONS  = var.dynamodb_positions_table_name
      VWR_TOKEN_SECRET = var.vwr_token_secret
      CORS_ORIGIN      = var.cors_origin
    }
  }

  tags = {
    Name = "${var.name_prefix}-vwr-api"
  }
}

# ─────────────────────────────────────────────────────────────────────────────
# IAM Role for VWR Lambda
# ─────────────────────────────────────────────────────────────────────────────

resource "aws_iam_role" "vwr_lambda" {
  name = "${var.name_prefix}-vwr-lambda-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Action = "sts:AssumeRole"
      Effect = "Allow"
      Principal = {
        Service = "lambda.amazonaws.com"
      }
    }]
  })
}

# CloudWatch Logs
resource "aws_iam_role_policy_attachment" "vwr_lambda_logs" {
  role       = aws_iam_role.vwr_lambda.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole"
}

# DynamoDB access
resource "aws_iam_role_policy" "vwr_lambda_dynamodb" {
  name = "${var.name_prefix}-vwr-lambda-dynamodb"
  role = aws_iam_role.vwr_lambda.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect = "Allow"
      Action = [
        "dynamodb:GetItem",
        "dynamodb:PutItem",
        "dynamodb:UpdateItem",
        "dynamodb:Query",
        "dynamodb:Scan"
      ]
      Resource = var.dynamodb_table_arns
    }]
  })
}

# ─────────────────────────────────────────────────────────────────────────────
# Lambda Permission for API Gateway
# ─────────────────────────────────────────────────────────────────────────────

resource "aws_lambda_permission" "api_gateway" {
  statement_id  = "AllowAPIGatewayInvoke"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.vwr_api.function_name
  principal     = "apigateway.amazonaws.com"
  source_arn    = "${var.api_gateway_execution_arn}/*/*"
}

# ─────────────────────────────────────────────────────────────────────────────
# Counter Advancer Lambda (runs on schedule)
# ─────────────────────────────────────────────────────────────────────────────

data "archive_file" "vwr_counter_advancer" {
  type        = "zip"
  source_dir  = var.counter_advancer_source_dir
  output_path = "${path.module}/builds/vwr-counter-advancer.zip"
}

resource "aws_lambda_function" "vwr_counter_advancer" {
  function_name    = "${var.name_prefix}-vwr-counter-advancer"
  role             = aws_iam_role.vwr_lambda.arn
  handler          = "index.handler"
  runtime          = "nodejs20.x"
  timeout          = 70  # 6 cycles x 10s = 60s + 10s margin for DynamoDB latency
  memory_size      = 128
  filename         = data.archive_file.vwr_counter_advancer.output_path
  source_code_hash = data.archive_file.vwr_counter_advancer.output_base64sha256

  environment {
    variables = {
      TABLE_COUNTERS = var.dynamodb_counters_table_name
      BATCH_SIZE     = tostring(var.counter_advance_batch_size)
    }
  }

  tags = {
    Name = "${var.name_prefix}-vwr-counter-advancer"
  }
}

# EventBridge rule to trigger counter advancer every 10 seconds
resource "aws_cloudwatch_event_rule" "vwr_counter_advance" {
  name                = "${var.name_prefix}-vwr-counter-advance"
  description         = "Advance VWR serving counter every 10 seconds"
  schedule_expression = "rate(1 minute)"
  # Note: Minimum EventBridge rate is 1 minute.
  # The Lambda itself runs a loop for finer granularity within the invocation.
}

resource "aws_cloudwatch_event_target" "vwr_counter_advance" {
  rule = aws_cloudwatch_event_rule.vwr_counter_advance.name
  arn  = aws_lambda_function.vwr_counter_advancer.arn
}

resource "aws_lambda_permission" "eventbridge_counter" {
  statement_id  = "AllowEventBridgeInvoke"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.vwr_counter_advancer.function_name
  principal     = "events.amazonaws.com"
  source_arn    = aws_cloudwatch_event_rule.vwr_counter_advance.arn
}
