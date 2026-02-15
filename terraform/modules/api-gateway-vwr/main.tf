# ─────────────────────────────────────────────────────────────────────────────
# API Gateway REST API for VWR Tier 1
# ─────────────────────────────────────────────────────────────────────────────

resource "aws_api_gateway_rest_api" "vwr" {
  name        = "${var.name_prefix}-vwr-api"
  description = "VWR Tier 1 API - Queue position assignment and checking"

  endpoint_configuration {
    types = ["REGIONAL"]
  }
}

# ─────────────────────────────────────────────────────────────────────────────
# Resources: /vwr/assign/{eventId}
# ─────────────────────────────────────────────────────────────────────────────

resource "aws_api_gateway_resource" "vwr" {
  rest_api_id = aws_api_gateway_rest_api.vwr.id
  parent_id   = aws_api_gateway_rest_api.vwr.root_resource_id
  path_part   = "vwr"
}

resource "aws_api_gateway_resource" "assign" {
  rest_api_id = aws_api_gateway_rest_api.vwr.id
  parent_id   = aws_api_gateway_resource.vwr.id
  path_part   = "assign"
}

resource "aws_api_gateway_resource" "assign_event" {
  rest_api_id = aws_api_gateway_rest_api.vwr.id
  parent_id   = aws_api_gateway_resource.assign.id
  path_part   = "{eventId}"
}

# POST /vwr/assign/{eventId}
resource "aws_api_gateway_method" "assign_post" {
  rest_api_id   = aws_api_gateway_rest_api.vwr.id
  resource_id   = aws_api_gateway_resource.assign_event.id
  http_method   = "POST"
  authorization = "NONE"
}

resource "aws_api_gateway_integration" "assign_post" {
  rest_api_id             = aws_api_gateway_rest_api.vwr.id
  resource_id             = aws_api_gateway_resource.assign_event.id
  http_method             = aws_api_gateway_method.assign_post.http_method
  integration_http_method = "POST"
  type                    = "AWS_PROXY"
  uri                     = var.lambda_invoke_arn
}

# ─────────────────────────────────────────────────────────────────────────────
# Resources: /vwr/check/{eventId}/{requestId}
# ─────────────────────────────────────────────────────────────────────────────

resource "aws_api_gateway_resource" "check" {
  rest_api_id = aws_api_gateway_rest_api.vwr.id
  parent_id   = aws_api_gateway_resource.vwr.id
  path_part   = "check"
}

resource "aws_api_gateway_resource" "check_event" {
  rest_api_id = aws_api_gateway_rest_api.vwr.id
  parent_id   = aws_api_gateway_resource.check.id
  path_part   = "{eventId}"
}

resource "aws_api_gateway_resource" "check_request" {
  rest_api_id = aws_api_gateway_rest_api.vwr.id
  parent_id   = aws_api_gateway_resource.check_event.id
  path_part   = "{requestId}"
}

# GET /vwr/check/{eventId}/{requestId}
resource "aws_api_gateway_method" "check_get" {
  rest_api_id   = aws_api_gateway_rest_api.vwr.id
  resource_id   = aws_api_gateway_resource.check_request.id
  http_method   = "GET"
  authorization = "NONE"

  request_parameters = {
    "method.request.path.eventId"   = true
    "method.request.path.requestId" = true
  }
}

resource "aws_api_gateway_integration" "check_get" {
  rest_api_id             = aws_api_gateway_rest_api.vwr.id
  resource_id             = aws_api_gateway_resource.check_request.id
  http_method             = aws_api_gateway_method.check_get.http_method
  integration_http_method = "POST"
  type                    = "AWS_PROXY"
  uri                     = var.lambda_invoke_arn
}

# ─────────────────────────────────────────────────────────────────────────────
# Resources: /vwr/status/{eventId}
# ─────────────────────────────────────────────────────────────────────────────

resource "aws_api_gateway_resource" "status" {
  rest_api_id = aws_api_gateway_rest_api.vwr.id
  parent_id   = aws_api_gateway_resource.vwr.id
  path_part   = "status"
}

resource "aws_api_gateway_resource" "status_event" {
  rest_api_id = aws_api_gateway_rest_api.vwr.id
  parent_id   = aws_api_gateway_resource.status.id
  path_part   = "{eventId}"
}

# GET /vwr/status/{eventId}
resource "aws_api_gateway_method" "status_get" {
  rest_api_id   = aws_api_gateway_rest_api.vwr.id
  resource_id   = aws_api_gateway_resource.status_event.id
  http_method   = "GET"
  authorization = "NONE"
}

resource "aws_api_gateway_integration" "status_get" {
  rest_api_id             = aws_api_gateway_rest_api.vwr.id
  resource_id             = aws_api_gateway_resource.status_event.id
  http_method             = aws_api_gateway_method.status_get.http_method
  integration_http_method = "POST"
  type                    = "AWS_PROXY"
  uri                     = var.lambda_invoke_arn
}

# ─────────────────────────────────────────────────────────────────────────────
# CORS: OPTIONS methods for all endpoints
# ─────────────────────────────────────────────────────────────────────────────

# CORS for /vwr/assign/{eventId}
resource "aws_api_gateway_method" "assign_options" {
  rest_api_id   = aws_api_gateway_rest_api.vwr.id
  resource_id   = aws_api_gateway_resource.assign_event.id
  http_method   = "OPTIONS"
  authorization = "NONE"
}

resource "aws_api_gateway_integration" "assign_options" {
  rest_api_id = aws_api_gateway_rest_api.vwr.id
  resource_id = aws_api_gateway_resource.assign_event.id
  http_method = aws_api_gateway_method.assign_options.http_method
  type        = "MOCK"

  request_templates = {
    "application/json" = "{\"statusCode\": 200}"
  }
}

resource "aws_api_gateway_method_response" "assign_options" {
  rest_api_id = aws_api_gateway_rest_api.vwr.id
  resource_id = aws_api_gateway_resource.assign_event.id
  http_method = aws_api_gateway_method.assign_options.http_method
  status_code = "200"

  response_parameters = {
    "method.response.header.Access-Control-Allow-Headers" = true
    "method.response.header.Access-Control-Allow-Methods" = true
    "method.response.header.Access-Control-Allow-Origin"  = true
  }
}

resource "aws_api_gateway_integration_response" "assign_options" {
  rest_api_id = aws_api_gateway_rest_api.vwr.id
  resource_id = aws_api_gateway_resource.assign_event.id
  http_method = aws_api_gateway_method.assign_options.http_method
  status_code = aws_api_gateway_method_response.assign_options.status_code

  response_parameters = {
    "method.response.header.Access-Control-Allow-Headers" = "'Content-Type'"
    "method.response.header.Access-Control-Allow-Methods" = "'GET,POST,OPTIONS'"
    "method.response.header.Access-Control-Allow-Origin"  = "'${var.cors_origin}'"
  }
}

# ─────────────────────────────────────────────────────────────────────────────
# Deployment & Stage
# ─────────────────────────────────────────────────────────────────────────────

resource "aws_api_gateway_deployment" "vwr" {
  rest_api_id = aws_api_gateway_rest_api.vwr.id

  depends_on = [
    aws_api_gateway_integration.assign_post,
    aws_api_gateway_integration.check_get,
    aws_api_gateway_integration.status_get,
    aws_api_gateway_integration.assign_options,
  ]

  lifecycle {
    create_before_destroy = true
  }

  triggers = {
    redeployment = sha1(jsonencode([
      aws_api_gateway_resource.assign_event,
      aws_api_gateway_resource.check_request,
      aws_api_gateway_resource.status_event,
      aws_api_gateway_method.assign_post,
      aws_api_gateway_method.check_get,
      aws_api_gateway_method.status_get,
      aws_api_gateway_integration.assign_post,
      aws_api_gateway_integration.check_get,
      aws_api_gateway_integration.status_get,
    ]))
  }
}

resource "aws_api_gateway_stage" "vwr" {
  deployment_id = aws_api_gateway_deployment.vwr.id
  rest_api_id   = aws_api_gateway_rest_api.vwr.id
  stage_name    = var.stage_name
}

# Throttling
resource "aws_api_gateway_method_settings" "vwr" {
  rest_api_id = aws_api_gateway_rest_api.vwr.id
  stage_name  = aws_api_gateway_stage.vwr.stage_name
  method_path = "*/*"

  settings {
    throttling_burst_limit = var.throttling_burst_limit
    throttling_rate_limit  = var.throttling_rate_limit
  }
}
