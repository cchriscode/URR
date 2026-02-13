$ErrorActionPreference = "Stop"

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path

Write-Host ""
Write-Host "================================================" -ForegroundColor Cyan
Write-Host "   TIKETI Spring - Stop All Services" -ForegroundColor Cyan
Write-Host "================================================" -ForegroundColor Cyan
Write-Host ""

# ── 1. Stop Spring Boot (Java) processes ────────────────────────

Write-Host "[1/2] Stopping Spring Boot services ..." -ForegroundColor Cyan

$javaStopped = 0
Get-Process java -ErrorAction SilentlyContinue | ForEach-Object {
    try {
        $cmdLine = (Get-CimInstance Win32_Process -Filter "ProcessId = $($_.Id)" -ErrorAction SilentlyContinue).CommandLine
        if ($cmdLine -and $cmdLine -like "*tiketi*") {
            Stop-Process -Id $_.Id -Force
            $javaStopped++
        }
    } catch {}
}

if ($javaStopped -gt 0) {
    Write-Host "  Stopped $javaStopped Java process(es)" -ForegroundColor Green
} else {
    # Fallback: stop all gradle bootRun related java processes
    Get-Process java -ErrorAction SilentlyContinue | ForEach-Object {
        try {
            $cmdLine = (Get-CimInstance Win32_Process -Filter "ProcessId = $($_.Id)" -ErrorAction SilentlyContinue).CommandLine
            if ($cmdLine -and ($cmdLine -like "*bootRun*" -or $cmdLine -like "*spring*")) {
                Stop-Process -Id $_.Id -Force
                $javaStopped++
            }
        } catch {}
    }
    if ($javaStopped -gt 0) {
        Write-Host "  Stopped $javaStopped Java process(es)" -ForegroundColor Green
    } else {
        Write-Host "  No running Spring services found" -ForegroundColor Gray
    }
}

# Stop any gradle daemon processes used by bootRun
$gradleStopped = 0
$services = @("auth-service", "ticket-service", "payment-service", "stats-service", "queue-service", "community-service", "gateway-service")
foreach ($svc in $services) {
    $gradlew = Join-Path $repoRoot "services-spring\$svc\gradlew.bat"
    if (Test-Path $gradlew) {
        & $gradlew -p (Join-Path $repoRoot "services-spring\$svc") --stop 2>$null
        $gradleStopped++
    }
}
if ($gradleStopped -gt 0) {
    Write-Host "  Stopped Gradle daemons" -ForegroundColor Green
}

# ── 2. Stop Docker Compose (DB + Redis) ─────────────────────────

Write-Host ""
Write-Host "[2/2] Stopping databases, Redis, Kafka, and Zipkin ..." -ForegroundColor Cyan

$composeFile = Join-Path $repoRoot "services-spring\docker-compose.databases.yml"
if (Test-Path $composeFile) {
    docker compose -f $composeFile down
    if ($LASTEXITCODE -eq 0) {
        Write-Host "  OK: DB containers stopped" -ForegroundColor Green
    } else {
        Write-Host "  WARN: docker compose down returned error" -ForegroundColor Yellow
    }
} else {
    Write-Host "  docker-compose.databases.yml not found (skipping)" -ForegroundColor Gray
}

Write-Host ""
Write-Host "================================================" -ForegroundColor Green
Write-Host "   All services stopped." -ForegroundColor Green
Write-Host "================================================" -ForegroundColor Green
Write-Host ""
