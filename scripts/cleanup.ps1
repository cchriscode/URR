param(
    [switch]$Force
)

$ErrorActionPreference = "Stop"

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path

Write-Host ""
Write-Host "==========================================" -ForegroundColor Red
Write-Host "   TIKETI Spring - Complete Cleanup" -ForegroundColor Red
Write-Host "==========================================" -ForegroundColor Red
Write-Host ""
Write-Host "This will DELETE:" -ForegroundColor Yellow
Write-Host "  - Running Spring Boot (Java) processes" -ForegroundColor White
Write-Host "  - Docker Compose DB containers + data" -ForegroundColor White
Write-Host "  - Kind cluster 'tiketi-local'" -ForegroundColor White
Write-Host "  - Port-forward processes" -ForegroundColor White
Write-Host "  - Docker images (optional)" -ForegroundColor White
Write-Host "  - node_modules (optional)" -ForegroundColor White
Write-Host "  - Gradle caches (optional)" -ForegroundColor White
Write-Host ""

if (-not $Force) {
    $confirm = Read-Host "Are you sure? (y/N)"
    if ($confirm -ne "y" -and $confirm -ne "Y") {
        Write-Host "Aborted." -ForegroundColor Yellow
        exit 0
    }
}

Write-Host ""

# ── 1. Stop Spring Boot (Java) processes ────────────────────────

Write-Host "[1/6] Stopping Spring Boot services ..." -ForegroundColor Cyan

$javaStopped = 0
Get-Process java -ErrorAction SilentlyContinue | ForEach-Object {
    try {
        $cmdLine = (Get-CimInstance Win32_Process -Filter "ProcessId = $($_.Id)" -ErrorAction SilentlyContinue).CommandLine
        if ($cmdLine -and ($cmdLine -like "*tiketi*" -or $cmdLine -like "*bootRun*" -or $cmdLine -like "*spring*")) {
            Stop-Process -Id $_.Id -Force
            $javaStopped++
        }
    } catch {}
}

if ($javaStopped -gt 0) {
    Write-Host "  Stopped $javaStopped Java process(es)" -ForegroundColor Green
} else {
    Write-Host "  No Spring processes found" -ForegroundColor Gray
}

# Stop Gradle daemons
$services = @("auth-service", "ticket-service", "payment-service", "stats-service", "gateway-service")
foreach ($svc in $services) {
    $gradlew = Join-Path $repoRoot "services-spring\$svc\gradlew.bat"
    if (Test-Path $gradlew) {
        & $gradlew -p (Join-Path $repoRoot "services-spring\$svc") --stop 2>$null
    }
}
Write-Host "  Gradle daemons stopped" -ForegroundColor Green

# ── 2. Stop port-forward processes ──────────────────────────────

Write-Host ""
Write-Host "[2/6] Stopping port-forward processes ..." -ForegroundColor Cyan

Get-Process kubectl -ErrorAction SilentlyContinue | Where-Object {
    try {
        $cmdLine = (Get-CimInstance Win32_Process -Filter "ProcessId = $($_.Id)" -ErrorAction SilentlyContinue).CommandLine
        $cmdLine -like "*port-forward*"
    } catch { $false }
} | Stop-Process -Force -ErrorAction SilentlyContinue

Write-Host "  Port-forwards stopped" -ForegroundColor Green

Start-Sleep -Seconds 1

# ── 3. Stop Docker Compose containers ───────────────────────────

Write-Host ""
Write-Host "[3/6] Stopping Docker Compose containers ..." -ForegroundColor Cyan

$composeFile = Join-Path $repoRoot "services-spring\docker-compose.databases.yml"
if (Test-Path $composeFile) {
    $dockerOutput = docker compose -f $composeFile down -v 2>&1
    if ($LASTEXITCODE -eq 0) {
        Write-Host "  Containers + volumes removed" -ForegroundColor Green
    } else {
        Write-Host "  No containers running or Docker not available" -ForegroundColor Gray
    }
} else {
    Write-Host "  docker-compose file not found (skipping)" -ForegroundColor Gray
}

# ── 4. Delete Kind cluster ──────────────────────────────────────

Write-Host ""
Write-Host "[4/6] Deleting Kind cluster ..." -ForegroundColor Cyan

if (Get-Command kind -ErrorAction SilentlyContinue) {
    $clusters = kind get clusters 2>$null
    if ($clusters -and ($clusters | Select-String -Pattern "^tiketi-local$" -Quiet)) {
        kind delete cluster --name tiketi-local
        if ($LASTEXITCODE -eq 0) {
            Write-Host "  Cluster 'tiketi-local' deleted" -ForegroundColor Green
        } else {
            Write-Host "  Failed to delete cluster" -ForegroundColor Yellow
        }
    } else {
        Write-Host "  Cluster 'tiketi-local' not found" -ForegroundColor Gray
    }
} else {
    Write-Host "  kind not installed (skipping)" -ForegroundColor Gray
}

# ── 5. Docker images (optional) ─────────────────────────────────

Write-Host ""
if (-not $Force) {
    $deleteImages = Read-Host "Delete Docker images? (y/N)"
} else {
    $deleteImages = "y"
}

if ($deleteImages -eq "y" -or $deleteImages -eq "Y") {
    Write-Host "[5/6] Deleting Docker images ..." -ForegroundColor Cyan

    $images = @(
        "tiketi-spring-gateway-service:local",
        "tiketi-spring-auth-service:local",
        "tiketi-spring-ticket-service:local",
        "tiketi-spring-payment-service:local",
        "tiketi-spring-stats-service:local"
    )

    foreach ($image in $images) {
        $exists = docker images -q $image 2>$null
        if ($exists) {
            docker rmi $image 2>$null
            Write-Host "  Deleted: $image" -ForegroundColor Green
        }
    }
} else {
    Write-Host "[5/6] Docker images kept" -ForegroundColor Gray
}

# ── 6. node_modules + Gradle caches (optional) ──────────────────

Write-Host ""
if (-not $Force) {
    $deleteCaches = Read-Host "Delete node_modules and Gradle build caches? (y/N)"
} else {
    $deleteCaches = "y"
}

if ($deleteCaches -eq "y" -or $deleteCaches -eq "Y") {
    Write-Host "[6/6] Deleting caches ..." -ForegroundColor Cyan

    # node_modules
    $webNodeModules = Join-Path $repoRoot "apps\web\node_modules"
    if (Test-Path $webNodeModules) {
        Write-Host "  Deleting apps\web\node_modules ..." -ForegroundColor Gray
        try {
            cmd /c "rmdir /s /q `"$webNodeModules`"" 2>$null
            Write-Host "  Deleted: apps\web\node_modules" -ForegroundColor Green
        } catch {
            Write-Host "  Failed to delete node_modules" -ForegroundColor Yellow
        }
    }

    # Gradle build directories
    foreach ($svc in $services) {
        $buildDir = Join-Path $repoRoot "services-spring\$svc\build"
        if (Test-Path $buildDir) {
            try {
                Remove-Item -Path $buildDir -Recurse -Force
                Write-Host "  Deleted: services-spring\$svc\build" -ForegroundColor Green
            } catch {
                Write-Host "  Failed to delete $svc build dir" -ForegroundColor Yellow
            }
        }
    }
} else {
    Write-Host "[6/6] Caches kept" -ForegroundColor Gray
}

# ── Summary ──────────────────────────────────────────────────────

Write-Host ""
Write-Host "==========================================" -ForegroundColor Green
Write-Host "   Cleanup Complete!" -ForegroundColor Green
Write-Host "==========================================" -ForegroundColor Green
Write-Host ""
Write-Host "  To start again:" -ForegroundColor White
Write-Host "    .\scripts\start-all.ps1 -Build -WithFrontend" -ForegroundColor Cyan
Write-Host ""
