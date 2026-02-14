param(
    [string]$KindClusterName = "urr-local"
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
if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    throw "docker is not installed."
}

Invoke-Native -Command "docker info *>`$null" -ErrorMessage "Docker daemon is not running. Start Docker Desktop first."

$clusters = kind get clusters
if ($LASTEXITCODE -ne 0) {
    throw "Failed to read kind clusters."
}

$kindExists = $clusters | Select-String -Pattern "^$KindClusterName$" -Quiet
if (-not $kindExists) {
    throw "Kind cluster '$KindClusterName' not found. Create it first."
}

$services = @(
    @{ Name = "gateway-service"; Image = "urr-spring-gateway-service:local" },
    @{ Name = "auth-service"; Image = "urr-spring-auth-service:local" },
    @{ Name = "ticket-service"; Image = "urr-spring-ticket-service:local" },
    @{ Name = "payment-service"; Image = "urr-spring-payment-service:local" },
    @{ Name = "stats-service"; Image = "urr-spring-stats-service:local" },
    @{ Name = "queue-service"; Image = "urr-spring-queue-service:local" },
    @{ Name = "community-service"; Image = "urr-spring-community-service:local" },
    @{ Name = "catalog-service"; Image = "urr-spring-catalog-service:local" }
)

foreach ($service in $services) {
    $servicePath = Join-Path $repoRoot "services-spring\$($service.Name)"
    $dockerfile = Join-Path $servicePath "Dockerfile"

    if (-not (Test-Path $dockerfile)) {
        throw "Dockerfile not found: $dockerfile"
    }

    Write-Host "Building image $($service.Image) from $servicePath ..."
    docker build -t $service.Image -f $dockerfile $servicePath
    if ($LASTEXITCODE -ne 0) {
        throw "Docker build failed for $($service.Name)"
    }

    Write-Host "Loading image $($service.Image) into kind cluster $KindClusterName ..."
    kind load docker-image $service.Image --name $KindClusterName
    if ($LASTEXITCODE -ne 0) {
        throw "kind load failed for $($service.Image)"
    }
}

# Build frontend image
$frontendPath = Join-Path $repoRoot "apps\web"
$frontendDockerfile = Join-Path $frontendPath "Dockerfile"
$frontendImage = "urr-spring-frontend:local"

if (-not (Test-Path $frontendDockerfile)) {
    throw "Frontend Dockerfile not found: $frontendDockerfile"
}

Write-Host "Building frontend image $frontendImage ..."
docker build -t $frontendImage --build-arg NEXT_PUBLIC_API_URL=http://localhost:3001 -f $frontendDockerfile $frontendPath
if ($LASTEXITCODE -ne 0) {
    throw "Docker build failed for frontend"
}

Write-Host "Loading frontend image into kind cluster $KindClusterName ..."
kind load docker-image $frontendImage --name $KindClusterName
if ($LASTEXITCODE -ne 0) {
    throw "kind load failed for $frontendImage"
}

Write-Host "All images are built and loaded into kind."
