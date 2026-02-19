locals {
  azs = slice(data.aws_availability_zones.available.names, 0, 2)

  # Subnet CIDR calculations
  public_subnets     = [for k, v in local.azs : cidrsubnet(var.vpc_cidr, 8, k)]
  app_subnets        = [for k, v in local.azs : cidrsubnet(var.vpc_cidr, 8, k + 10)]
  db_subnets         = [for k, v in local.azs : cidrsubnet(var.vpc_cidr, 8, k + 20)]
  cache_subnets      = [for k, v in local.azs : cidrsubnet(var.vpc_cidr, 8, k + 30)]
  streaming_subnets  = [for k, v in local.azs : cidrsubnet(var.vpc_cidr, 8, k + 40)]
}

data "aws_availability_zones" "available" {
  state = "available"
}

# ─────────────────────────────────────────────────────────────────────────────
# VPC
# ─────────────────────────────────────────────────────────────────────────────

resource "aws_vpc" "main" {
  cidr_block           = var.vpc_cidr
  enable_dns_hostnames = true
  enable_dns_support   = true

  tags = {
    Name = "${var.name_prefix}-vpc"
  }
}

# ─────────────────────────────────────────────────────────────────────────────
# Internet Gateway
# ─────────────────────────────────────────────────────────────────────────────

resource "aws_internet_gateway" "main" {
  vpc_id = aws_vpc.main.id

  tags = {
    Name = "${var.name_prefix}-igw"
  }
}

# ─────────────────────────────────────────────────────────────────────────────
# Public Subnets
# ─────────────────────────────────────────────────────────────────────────────

resource "aws_subnet" "public" {
  count = length(local.azs)

  vpc_id                  = aws_vpc.main.id
  cidr_block              = local.public_subnets[count.index]
  availability_zone       = local.azs[count.index]
  map_public_ip_on_launch = true

  tags = {
    Name                     = "${var.name_prefix}-public-${local.azs[count.index]}"
    "kubernetes.io/role/elb" = "1"
  }
}

resource "aws_route_table" "public" {
  vpc_id = aws_vpc.main.id

  tags = {
    Name = "${var.name_prefix}-public-rt"
  }
}

resource "aws_route" "public_internet" {
  route_table_id         = aws_route_table.public.id
  destination_cidr_block = "0.0.0.0/0"
  gateway_id             = aws_internet_gateway.main.id
}

resource "aws_route_table_association" "public" {
  count = length(aws_subnet.public)

  subnet_id      = aws_subnet.public[count.index].id
  route_table_id = aws_route_table.public.id
}

# ─────────────────────────────────────────────────────────────────────────────
# NAT Gateways (one per AZ for HA)
# ─────────────────────────────────────────────────────────────────────────────

resource "aws_eip" "nat" {
  count  = var.single_nat_gateway ? 1 : length(local.azs)
  domain = "vpc"

  tags = {
    Name = "${var.name_prefix}-nat-eip-${local.azs[count.index]}"
  }

  depends_on = [aws_internet_gateway.main]
}

resource "aws_nat_gateway" "main" {
  count = var.single_nat_gateway ? 1 : length(local.azs)

  allocation_id = aws_eip.nat[count.index].id
  subnet_id     = aws_subnet.public[count.index].id

  tags = {
    Name = "${var.name_prefix}-nat-${local.azs[count.index]}"
  }
}

# ─────────────────────────────────────────────────────────────────────────────
# Private App Subnets (for EKS nodes)
# ─────────────────────────────────────────────────────────────────────────────

resource "aws_subnet" "app" {
  count = length(local.azs)

  vpc_id            = aws_vpc.main.id
  cidr_block        = local.app_subnets[count.index]
  availability_zone = local.azs[count.index]

  tags = {
    Name                              = "${var.name_prefix}-app-${local.azs[count.index]}"
    "kubernetes.io/role/internal-elb" = "1"
    "karpenter.sh/discovery"          = var.name_prefix
  }
}

resource "aws_route_table" "app" {
  count = length(local.azs)

  vpc_id = aws_vpc.main.id

  tags = {
    Name = "${var.name_prefix}-app-rt-${local.azs[count.index]}"
  }
}

resource "aws_route" "app_nat" {
  count = length(local.azs)

  route_table_id         = aws_route_table.app[count.index].id
  destination_cidr_block = "0.0.0.0/0"
  nat_gateway_id         = aws_nat_gateway.main[min(count.index, length(aws_nat_gateway.main) - 1)].id
}

resource "aws_route_table_association" "app" {
  count = length(aws_subnet.app)

  subnet_id      = aws_subnet.app[count.index].id
  route_table_id = aws_route_table.app[count.index].id
}

# ─────────────────────────────────────────────────────────────────────────────
# Private Database Subnets
# ─────────────────────────────────────────────────────────────────────────────

resource "aws_subnet" "db" {
  count = length(local.azs)

  vpc_id            = aws_vpc.main.id
  cidr_block        = local.db_subnets[count.index]
  availability_zone = local.azs[count.index]

  tags = {
    Name = "${var.name_prefix}-db-${local.azs[count.index]}"
  }
}

resource "aws_route_table" "db" {
  vpc_id = aws_vpc.main.id

  tags = {
    Name = "${var.name_prefix}-db-rt"
  }
}

resource "aws_route_table_association" "db" {
  count = length(aws_subnet.db)

  subnet_id      = aws_subnet.db[count.index].id
  route_table_id = aws_route_table.db.id
}

# ─────────────────────────────────────────────────────────────────────────────
# Private Cache Subnets
# ─────────────────────────────────────────────────────────────────────────────

resource "aws_subnet" "cache" {
  count = length(local.azs)

  vpc_id            = aws_vpc.main.id
  cidr_block        = local.cache_subnets[count.index]
  availability_zone = local.azs[count.index]

  tags = {
    Name = "${var.name_prefix}-cache-${local.azs[count.index]}"
  }
}

resource "aws_route_table_association" "cache" {
  count = length(aws_subnet.cache)

  subnet_id      = aws_subnet.cache[count.index].id
  route_table_id = aws_route_table.db.id
}

# ─────────────────────────────────────────────────────────────────────────────
# Private Streaming Subnets (for Lambda workers)
# ─────────────────────────────────────────────────────────────────────────────

resource "aws_subnet" "streaming" {
  count = length(local.azs)

  vpc_id            = aws_vpc.main.id
  cidr_block        = local.streaming_subnets[count.index]
  availability_zone = local.azs[count.index]

  tags = {
    Name = "${var.name_prefix}-streaming-${local.azs[count.index]}"
  }
}

resource "aws_route_table" "streaming" {
  count = length(local.azs)

  vpc_id = aws_vpc.main.id

  tags = {
    Name = "${var.name_prefix}-streaming-rt-${local.azs[count.index]}"
  }
}

resource "aws_route" "streaming_nat" {
  count = length(local.azs)

  route_table_id         = aws_route_table.streaming[count.index].id
  destination_cidr_block = "0.0.0.0/0"
  nat_gateway_id         = aws_nat_gateway.main[min(count.index, length(aws_nat_gateway.main) - 1)].id
}

resource "aws_route_table_association" "streaming" {
  count = length(aws_subnet.streaming)

  subnet_id      = aws_subnet.streaming[count.index].id
  route_table_id = aws_route_table.streaming[count.index].id
}
