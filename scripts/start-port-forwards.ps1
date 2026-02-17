param(
    [string]$Namespace = "urr-spring"
)

$ErrorActionPreference = "Stop"

Write-Host ""
Write-Host "================================================" -ForegroundColor Cyan
Write-Host "   URR Spring - Port Forward Setup" -ForegroundColor Cyan
Write-Host "================================================" -ForegroundColor Cyan
Write-Host ""

# ── Prerequisites ────────────────────────────────────────────────

if (-not (Get-Command kubectl -ErrorAction SilentlyContinue)) {
    throw "kubectl is not installed."
}

Write-Host "Testing cluster connection ..." -ForegroundColor Cyan
kubectl cluster-info 2>&1 | Out-Null
if ($LASTEXITCODE -ne 0) {
    throw "Cannot connect to Kubernetes cluster. Is Kind running?"
}
Write-Host "  OK: Cluster connection" -ForegroundColor Green
Write-Host ""

# ── Kill existing port-forwards ──────────────────────────────────

Write-Host "Stopping existing port-forwards ..." -ForegroundColor Yellow
Get-Process kubectl -ErrorAction SilentlyContinue | Where-Object {
    try {
        $cmdLine = (Get-CimInstance Win32_Process -Filter "ProcessId = $($_.Id)" -ErrorAction SilentlyContinue).CommandLine
        $cmdLine -like "*port-forward*" -and $cmdLine -like "*$Namespace*"
    } catch { $false }
} | Stop-Process -Force -ErrorAction SilentlyContinue

Start-Sleep -Seconds 1

# ── Check port availability ──────────────────────────────────────

Write-Host "Checking port availability ..." -ForegroundColor Cyan
$requiredPorts = @(3000, 3001, 3002, 3003, 3004, 3005, 3007, 3008)
$portsInUse = @()

foreach ($port in $requiredPorts) {
    $connections = @(Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue)
    if ($connections.Count -gt 0) {
        $process = Get-Process -Id $connections[0].OwningProcess -ErrorAction SilentlyContinue
        Write-Host "  WARN: Port $port in use by $($process.Name) (PID: $($process.Id))" -ForegroundColor Yellow
        $portsInUse += $port
    }
}

if ($portsInUse.Count -gt 0) {
    Write-Host ""
    Write-Host "  Some ports are in use. Port-forwards for those may fail." -ForegroundColor Yellow
    Write-Host "  Continuing in 3 seconds ..." -ForegroundColor Gray
    Start-Sleep -Seconds 3
} else {
    Write-Host "  OK: All ports available" -ForegroundColor Green
}

# ── Start port-forwards ─────────────────────────────────────────

Write-Host ""
Write-Host "Starting port-forwards ..." -ForegroundColor Green
Write-Host ""

$forwards = @(
    @{ Name = "Gateway";    Service = "svc/gateway-service";    LocalPort = 3001; RemotePort = 3001 },
    @{ Name = "Auth";       Service = "svc/auth-service";       LocalPort = 3005; RemotePort = 3005 },
    @{ Name = "Ticket";     Service = "svc/ticket-service";     LocalPort = 3002; RemotePort = 3002 },
    @{ Name = "Payment";    Service = "svc/payment-service";    LocalPort = 3003; RemotePort = 3003 },
    @{ Name = "Stats";      Service = "svc/stats-service";      LocalPort = 3004; RemotePort = 3004 },
    @{ Name = "Queue";      Service = "svc/queue-service";      LocalPort = 3007; RemotePort = 3007 },
    @{ Name = "Community";  Service = "svc/community-service";  LocalPort = 3008; RemotePort = 3008 },
    @{ Name = "Frontend";   Service = "svc/frontend-service";   LocalPort = 3000; RemotePort = 3000 }
)

$index = 1
foreach ($fwd in $forwards) {
    Write-Host "  [$index/$($forwards.Count)] $($fwd.Name) Service ($($fwd.LocalPort))" -ForegroundColor Cyan
    Start-Process powershell -ArgumentList "-NoExit", "-Command", "kubectl port-forward --address 0.0.0.0 -n $Namespace $($fwd.Service) $($fwd.LocalPort):$($fwd.RemotePort)" -WindowStyle Minimized
    Start-Sleep -Seconds 2
    $index++
}

# ── Health check ─────────────────────────────────────────────────

Write-Host ""
Write-Host "Verifying services (waiting 5s) ..." -ForegroundColor Cyan
Start-Sleep -Seconds 5

$allHealthy = $true
foreach ($fwd in $forwards) {
    try {
        $response = Invoke-WebRequest -Uri "http://localhost:$($fwd.LocalPort)/health" -UseBasicParsing -TimeoutSec 3 -ErrorAction Stop
        Write-Host "  OK: $($fwd.Name) (port $($fwd.LocalPort))" -ForegroundColor Green
    } catch {
        Write-Host "  WARN: $($fwd.Name) (port $($fwd.LocalPort)) - not responding yet" -ForegroundColor Yellow
        $allHealthy = $false
    }
}

# ── Summary ──────────────────────────────────────────────────────

Write-Host ""
if ($allHealthy) {
    Write-Host "================================================" -ForegroundColor Green
    Write-Host "   All port-forwards are active!" -ForegroundColor Green
    Write-Host "================================================" -ForegroundColor Green
} else {
    Write-Host "================================================" -ForegroundColor Yellow
    Write-Host "   Some services not ready yet." -ForegroundColor Yellow
    Write-Host "   They may still be starting. Try again in a minute." -ForegroundColor Yellow
    Write-Host "================================================" -ForegroundColor Yellow
}
Write-Host ""
Write-Host "  Frontend:        http://localhost:3000" -ForegroundColor White
Write-Host "  Gateway API:     http://localhost:3001" -ForegroundColor White
Write-Host "  Auth Service:    http://localhost:3005" -ForegroundColor White
Write-Host "  Ticket Service:  http://localhost:3002" -ForegroundColor White
Write-Host "  Payment Service: http://localhost:3003" -ForegroundColor White
Write-Host "  Stats Service:   http://localhost:3004" -ForegroundColor White
Write-Host "  Queue Service:   http://localhost:3007" -ForegroundColor White
Write-Host "  Community Svc:   http://localhost:3008" -ForegroundColor White
Write-Host ""
Write-Host "  Port-forwards run in minimized PowerShell windows." -ForegroundColor Gray
Write-Host "  Close those windows to stop port-forwarding." -ForegroundColor Gray
Write-Host ""
