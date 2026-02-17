param(
    [switch]$RecreateCluster,
    [switch]$SkipBuild,
    [switch]$SingleNode,
    [string[]]$Services,
    [int]$Parallel = 4
)

$ErrorActionPreference = "Stop"

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
Set-Location $repoRoot

Write-Host "Starting full stack on kind ..."
$upArgs = @{}
if ($RecreateCluster) { $upArgs.RecreateCluster = $true }
if ($SkipBuild) { $upArgs.SkipBuild = $true }
if ($SingleNode) { $upArgs.SingleNode = $true }
if ($Services -and $Services.Count -gt 0) { $upArgs.Services = $Services }
$upArgs.Parallel = $Parallel
& "$repoRoot\scripts\spring-kind-up.ps1" @upArgs

Write-Host ""
Write-Host "================================================" -ForegroundColor Green
Write-Host "   All services deployed to Kind!" -ForegroundColor Green
Write-Host "================================================" -ForegroundColor Green
Write-Host ""
Write-Host "  Frontend:  http://localhost:3000" -ForegroundColor White
Write-Host "  Gateway:   http://localhost:3001" -ForegroundColor White
Write-Host "  Grafana:   http://localhost:3006 (admin/admin)" -ForegroundColor White
Write-Host ""
Write-Host "  Kind NodePort mapping handles port access." -ForegroundColor Gray
Write-Host "  For individual service access, run:" -ForegroundColor Gray
Write-Host "    .\scripts\start-port-forwards.ps1" -ForegroundColor Cyan
Write-Host ""
