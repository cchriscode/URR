param(
    [switch]$Build,
    [switch]$WithFrontend
)

$ErrorActionPreference = "Stop"

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path

Write-Host ""
Write-Host "================================================" -ForegroundColor Cyan
Write-Host "   TIKETI Spring - Start All Services" -ForegroundColor Cyan
Write-Host "================================================" -ForegroundColor Cyan
Write-Host ""

# ── 1. Docker Compose (DB + Redis) ──────────────────────────────

Write-Host "[1/3] Starting databases and Redis ..." -ForegroundColor Cyan

$composeFile = Join-Path $repoRoot "services-spring\docker-compose.databases.yml"
if (-not (Test-Path $composeFile)) {
    throw "docker-compose.databases.yml not found at $composeFile"
}

docker compose -f $composeFile up -d
if ($LASTEXITCODE -ne 0) {
    throw "docker compose up failed"
}
Write-Host "  OK: DB containers started" -ForegroundColor Green

# Wait for DBs to be ready
Write-Host "  Waiting for databases to accept connections ..." -ForegroundColor Gray
$dbPorts = @(
    @{ Name = "auth-db";    Port = 5433 },
    @{ Name = "ticket-db";  Port = 5434 },
    @{ Name = "payment-db"; Port = 5435 },
    @{ Name = "stats-db";   Port = 5436 }
)

foreach ($db in $dbPorts) {
    $retries = 0
    $maxRetries = 30
    while ($retries -lt $maxRetries) {
        try {
            $tcp = New-Object System.Net.Sockets.TcpClient
            $tcp.Connect("localhost", $db.Port)
            $tcp.Close()
            Write-Host "  OK: $($db.Name) (port $($db.Port))" -ForegroundColor Green
            break
        } catch {
            $retries++
            if ($retries -ge $maxRetries) {
                throw "$($db.Name) did not become ready within 30 seconds"
            }
            Start-Sleep -Seconds 1
        }
    }
}

# ── 2. Spring Boot Services ─────────────────────────────────────

Write-Host ""
Write-Host "[2/3] Starting Spring Boot services ..." -ForegroundColor Cyan

$services = @(
    @{ Name = "auth-service";    Port = 3005 },
    @{ Name = "ticket-service";  Port = 3002 },
    @{ Name = "payment-service"; Port = 3003 },
    @{ Name = "stats-service";   Port = 3004 },
    @{ Name = "gateway-service"; Port = 3001 }
)

foreach ($svc in $services) {
    $svcPath = Join-Path $repoRoot "services-spring\$($svc.Name)"
    $gradlew = Join-Path $svcPath "gradlew.bat"

    if (-not (Test-Path $gradlew)) {
        throw "gradlew.bat not found: $gradlew"
    }

    if ($Build) {
        Write-Host "  Building $($svc.Name) ..." -ForegroundColor Gray
        & $gradlew -p $svcPath clean build -x test
        if ($LASTEXITCODE -ne 0) {
            throw "Build failed for $($svc.Name)"
        }
    }

    Write-Host "  Starting $($svc.Name) (port $($svc.Port)) ..." -ForegroundColor Gray
    Start-Process -FilePath $gradlew `
        -ArgumentList "-p", $svcPath, "bootRun" `
        -WorkingDirectory $svcPath `
        -WindowStyle Minimized
}

# Wait for services to be ready
Write-Host ""
Write-Host "  Waiting for services to start ..." -ForegroundColor Gray
Start-Sleep -Seconds 15

foreach ($svc in $services) {
    $retries = 0
    $maxRetries = 60
    $ready = $false
    while ($retries -lt $maxRetries) {
        try {
            $response = Invoke-WebRequest -Uri "http://localhost:$($svc.Port)/actuator/health" -UseBasicParsing -TimeoutSec 2 -ErrorAction Stop
            if ($response.StatusCode -eq 200) {
                Write-Host "  OK: $($svc.Name) (port $($svc.Port))" -ForegroundColor Green
                $ready = $true
                break
            }
        } catch {
            $retries++
            if ($retries -ge $maxRetries) {
                Write-Host "  WARN: $($svc.Name) not responding yet (port $($svc.Port))" -ForegroundColor Yellow
            }
            Start-Sleep -Seconds 2
        }
    }
}

# ── 3. Frontend (optional) ──────────────────────────────────────

if ($WithFrontend) {
    Write-Host ""
    Write-Host "[3/3] Starting frontend dev server ..." -ForegroundColor Cyan

    $webPath = Join-Path $repoRoot "apps\web"
    $nodeModules = Join-Path $webPath "node_modules"

    if (-not (Test-Path $nodeModules)) {
        Write-Host "  Installing frontend dependencies ..." -ForegroundColor Gray
        npm --prefix $webPath install
        if ($LASTEXITCODE -ne 0) {
            throw "npm install failed"
        }
    }

    $env:NEXT_PUBLIC_API_URL = "http://localhost:3001"
    Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd '$webPath'; `$env:NEXT_PUBLIC_API_URL='http://localhost:3001'; npm run dev" -WindowStyle Normal
    Write-Host "  OK: Frontend dev server starting (port 3000)" -ForegroundColor Green
} else {
    Write-Host ""
    Write-Host "[3/3] Frontend skipped (use -WithFrontend to include)" -ForegroundColor Gray
}

# ── Summary ──────────────────────────────────────────────────────

Write-Host ""
Write-Host "================================================" -ForegroundColor Green
Write-Host "   All services started!" -ForegroundColor Green
Write-Host "================================================" -ForegroundColor Green
Write-Host ""
Write-Host "  Services:" -ForegroundColor White
Write-Host "    Gateway API:     http://localhost:3001" -ForegroundColor White
Write-Host "    Auth Service:    http://localhost:3005" -ForegroundColor White
Write-Host "    Ticket Service:  http://localhost:3002" -ForegroundColor White
Write-Host "    Payment Service: http://localhost:3003" -ForegroundColor White
Write-Host "    Stats Service:   http://localhost:3004" -ForegroundColor White
if ($WithFrontend) {
    Write-Host "    Frontend:        http://localhost:3000" -ForegroundColor White
}
Write-Host ""
Write-Host "  Databases:" -ForegroundColor White
Write-Host "    auth-db:    localhost:5433" -ForegroundColor Gray
Write-Host "    ticket-db:  localhost:5434" -ForegroundColor Gray
Write-Host "    payment-db: localhost:5435" -ForegroundColor Gray
Write-Host "    stats-db:   localhost:5436" -ForegroundColor Gray
Write-Host "    redis:      localhost:6379" -ForegroundColor Gray
Write-Host ""
Write-Host "  Stop:" -ForegroundColor White
Write-Host "    .\scripts\stop-all.ps1" -ForegroundColor Cyan
Write-Host ""
