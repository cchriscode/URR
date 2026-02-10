param(
    [string]$KindClusterName = "tiketi-local",
    [switch]$RecreateCluster,
    [switch]$SkipBuild
)

$ErrorActionPreference = "Stop"

function Invoke-Native {
    param(
        [Parameter(Mandatory = $true)][string]$Command,
        [Parameter(Mandatory = $true)][string]$ErrorMessage
    )

    try {
        Invoke-Expression $Command | Out-Null
    }
    catch {
        throw $ErrorMessage
    }

    if ($LASTEXITCODE -ne 0) {
        throw $ErrorMessage
    }
}

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
Set-Location $repoRoot

if (-not (Get-Command kind -ErrorAction SilentlyContinue)) {
    throw "kind is not installed."
}
if (-not (Get-Command kubectl -ErrorAction SilentlyContinue)) {
    throw "kubectl is not installed."
}
if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    throw "docker is not installed."
}

Invoke-Native -Command "docker info *>`$null" -ErrorMessage "Docker daemon is not running. Start Docker Desktop first."

$clusters = kind get clusters
if ($LASTEXITCODE -ne 0) {
    throw "Failed to read kind clusters."
}
$clusterExists = $clusters | Select-String -Pattern "^$KindClusterName$" -Quiet

if ($RecreateCluster -and $clusterExists) {
    Write-Host "Deleting existing kind cluster '$KindClusterName' ..."
    kind delete cluster --name $KindClusterName
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to delete kind cluster '$KindClusterName'."
    }
    $clusterExists = $false
}

if (-not $clusterExists) {
    Write-Host "Creating kind cluster '$KindClusterName' ..."
    kind create cluster --name $KindClusterName --config kind-config.yaml
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to create kind cluster '$KindClusterName'."
    }
}

kubectl config use-context "kind-$KindClusterName" | Out-Null
if ($LASTEXITCODE -ne 0) {
    throw "Failed to switch kubectl context to kind-$KindClusterName."
}

if (-not $SkipBuild) {
    & "$repoRoot\scripts\spring-kind-build-load.ps1" -KindClusterName $KindClusterName
}

Write-Host "Applying spring kind overlay ..."
kubectl apply -k "$repoRoot\k8s\spring\overlays\kind"
if ($LASTEXITCODE -ne 0) {
    throw "kubectl apply failed for k8s/spring/overlays/kind"
}

$namespace = "tiketi-spring"
$deployments = @(
    "postgres-spring",
    "dragonfly-spring",
    "auth-service",
    "ticket-service",
    "payment-service",
    "stats-service",
    "gateway-service",
    "frontend",
    "loki",
    "grafana"
)

foreach ($deployment in $deployments) {
    Write-Host "Waiting for deployment/$deployment ..."
    kubectl rollout status "deployment/$deployment" -n $namespace --timeout=300s
    if ($LASTEXITCODE -ne 0) {
        throw "Deployment rollout failed: $deployment"
    }
}

Write-Host ""
Write-Host "Current pods:"
kubectl get pods -n $namespace -o wide

Write-Host ""
Write-Host "Frontend:  http://localhost:3000"
Write-Host "Gateway:   http://localhost:3001"
Write-Host "Grafana:   http://localhost:3006 (admin/admin)"
Write-Host "Health:    http://localhost:3001/health"
Write-Host "Namespace: $namespace"
