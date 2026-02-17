# Final Validation

Run from `C:\Users\USER\project-ticketing-copy`.

## Prerequisites

- Spring stack is running (recommended via `scripts/spring-kind-dev.ps1` or `scripts/spring-kind-up.ps1`)
- Docker/kind/kubectl available for integration checks

## Full validation

```powershell
npm run validate:final
```

This runs:

- Spring unit tests (`auth`, `ticket`, `payment`, `stats`, `gateway`)
- Frontend static checks (`web:lint`, `web:build`)
- Expanded API integration checks against `http://localhost:3001`

Expanded API checks include:

- auth register/login/me, admin promotion flow
- admin event/ticket/reservation APIs
- seats + queue polling APIs
- reservations + payments (`prepare/confirm/cancel/process`)
- news CRUD
- stats admin APIs

## Extra prerequisite for expanded API checks

- `kubectl` access to kind namespace `urr-spring`
- `postgres-spring` pod reachable (script uses SQL fixture inserts for admin role + seat fixture)

## Optional flags

```powershell
# Skip unit tests
powershell -ExecutionPolicy Bypass -File .\scripts\validation\run-final-validation.ps1 -SkipUnit

# Skip frontend checks
powershell -ExecutionPolicy Bypass -File .\scripts\validation\run-final-validation.ps1 -SkipWeb

# Skip API checks
powershell -ExecutionPolicy Bypass -File .\scripts\validation\run-final-validation.ps1 -SkipApi

# Compare selected endpoints with legacy stack
powershell -ExecutionPolicy Bypass -File .\scripts\validation\run-final-validation.ps1 -LegacyBaseUrl http://localhost:3010
```

## Report output

- Latest: `reports/final-validation/latest.json`
- Timestamped: `reports/final-validation/validation-<timestamp>.json`
