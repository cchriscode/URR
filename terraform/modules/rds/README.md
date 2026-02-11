# RDS PostgreSQL Module

## Overview

This module creates an Amazon RDS PostgreSQL instance with RDS Proxy for connection pooling.

## Database Structure

The RDS instance hosts multiple databases for different services:

### Initial Database (Created by Terraform)
- `ticket_db` - Created automatically when RDS instance is provisioned

### Additional Databases (Created by Application)
- `stats_db` - Must be created by stats-service Flyway migrations

## Database Creation Methods

### Option 1: Flyway Migration (Recommended)
Each service includes Flyway migrations that create their database if it doesn't exist:

```sql
-- V1__Create_Database.sql
CREATE DATABASE IF NOT EXISTS stats_db;
```

**Note**: PostgreSQL doesn't support `CREATE DATABASE` inside a transaction, so this must be handled carefully.

### Option 2: Manual Creation
Connect to RDS Proxy or RDS instance and run:

```bash
psql -h <rds-proxy-endpoint> -U <master-username> -d postgres -c "CREATE DATABASE stats_db;"
```

### Option 3: Init Container (Kubernetes)
Deploy an init container in K8s that creates additional databases before application startup:

```yaml
initContainers:
- name: create-db
  image: postgres:16
  command:
  - sh
  - -c
  - |
    psql -h $DB_HOST -U $DB_USER -d postgres -c "CREATE DATABASE IF NOT EXISTS stats_db;"
```

## Connection Strings

### ticket-service
```yaml
TICKET_DB_URL: jdbc:postgresql://<rds-proxy-endpoint>:5432/ticket_db
```

### stats-service
```yaml
STATS_DB_URL: jdbc:postgresql://<rds-proxy-endpoint>:5432/stats_db
```

## Security

- RDS instance is in private DB subnets
- RDS Proxy is in private App subnets (for EKS access)
- Only EKS nodes can connect via RDS Proxy security group
- Credentials stored in AWS Secrets Manager
- Encryption at rest enabled
- Transit encryption (SSL/TLS) enabled

## Architecture

```
EKS Pods (App Subnet)
    ↓
RDS Proxy (App Subnet)  ← Connection pooling
    ↓
RDS Primary (DB Subnet)
    ↓
RDS Standby (DB Subnet)  ← Multi-AZ failover
```
