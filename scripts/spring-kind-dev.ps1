param(
    [switch]$RecreateCluster,
    [switch]$SkipBuild
)

$ErrorActionPreference = "Stop"

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
Set-Location $repoRoot

Write-Host "Starting full stack on kind ..."
$upArgs = @()
if ($RecreateCluster) { $upArgs += "-RecreateCluster" }
if ($SkipBuild) { $upArgs += "-SkipBuild" }
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
