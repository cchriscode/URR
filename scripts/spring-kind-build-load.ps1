param(
    [string]$KindClusterName = "urr-local",
    [string[]]$Services,
    [int]$Parallel = 4
)

$ErrorActionPreference = "Stop"

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
Set-Location $repoRoot

if (-not (Get-Command kind -ErrorAction SilentlyContinue)) {
    throw "kind is not installed."
}
if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    throw "docker is not installed."
}

$clusters = kind get clusters
if ($LASTEXITCODE -ne 0) {
    throw "Failed to read kind clusters."
}

$kindExists = $clusters | Select-String -Pattern "^$KindClusterName$" -Quiet
if (-not $kindExists) {
    throw "Kind cluster '$KindClusterName' not found. Create it first."
}

# All available services
$allServices = @(
    @{ Name = "gateway-service"; Image = "urr-spring-gateway-service:local" },
    @{ Name = "auth-service"; Image = "urr-spring-auth-service:local" },
    @{ Name = "ticket-service"; Image = "urr-spring-ticket-service:local" },
    @{ Name = "payment-service"; Image = "urr-spring-payment-service:local" },
    @{ Name = "stats-service"; Image = "urr-spring-stats-service:local" },
    @{ Name = "queue-service"; Image = "urr-spring-queue-service:local" },
    @{ Name = "community-service"; Image = "urr-spring-community-service:local" },
    @{ Name = "catalog-service"; Image = "urr-spring-catalog-service:local" }
)

# Filter to selected services if -Services provided
if ($Services -and $Services.Count -gt 0) {
    $selectedServices = $allServices | Where-Object { $Services -contains $_.Name }
    if ($selectedServices.Count -eq 0) {
        $valid = ($allServices | ForEach-Object { $_.Name }) -join ", "
        throw "No matching services found. Valid names: $valid"
    }
    Write-Host "Selective build: $($selectedServices.Count) service(s)" -ForegroundColor Cyan
} else {
    $selectedServices = $allServices
}

# --- Phase 1: Parallel Docker builds ---
Write-Host ""
Write-Host "=== Phase 1: Building $($selectedServices.Count) service image(s) (parallel=$Parallel) ===" -ForegroundColor Yellow

$buildJobs = @()
foreach ($service in $selectedServices) {
    $servicePath = Join-Path $repoRoot "services-spring\$($service.Name)"
    $dockerfile = Join-Path $servicePath "Dockerfile"

    if (-not (Test-Path $dockerfile)) {
        throw "Dockerfile not found: $dockerfile"
    }

    $buildJobs += @{
        Name = $service.Name
        Image = $service.Image
        Path = $servicePath
        Dockerfile = $dockerfile
    }
}

# Run docker builds in parallel batches
$jobResults = @()
for ($i = 0; $i -lt $buildJobs.Count; $i += $Parallel) {
    $batch = $buildJobs[$i .. [math]::Min($i + $Parallel - 1, $buildJobs.Count - 1)]
    $batchNames = ($batch | ForEach-Object { $_.Name }) -join ", "
    Write-Host "Building batch: $batchNames" -ForegroundColor Cyan

    $runningJobs = @()
    foreach ($job in $batch) {
        $j = Start-Job -ScriptBlock {
            param($img, $df, $ctx)
            docker build -t $img -f $df $ctx 2>&1
            if ($LASTEXITCODE -ne 0) { throw "Docker build failed for $img" }
        } -ArgumentList $job.Image, $job.Dockerfile, $job.Path
        $runningJobs += @{ Job = $j; Name = $job.Name }
    }

    foreach ($rj in $runningJobs) {
        $result = Receive-Job -Job $rj.Job -Wait
        if ($rj.Job.State -eq "Failed") {
            Remove-Job -Job $rj.Job -Force
            throw "Docker build failed for $($rj.Name)"
        }
        Remove-Job -Job $rj.Job -Force
        Write-Host "  OK: $($rj.Name)" -ForegroundColor Green
    }
}

# --- Phase 1b: Build frontend (if not selective or frontend requested) ---
$buildFrontend = (-not $Services) -or ($Services -contains "frontend")
if ($buildFrontend) {
    $frontendPath = Join-Path $repoRoot "apps\web"
    $frontendDockerfile = Join-Path $frontendPath "Dockerfile"
    $frontendImage = "urr-spring-frontend:local"

    if (-not (Test-Path $frontendDockerfile)) {
        throw "Frontend Dockerfile not found: $frontendDockerfile"
    }

    Write-Host "Building frontend image ..." -ForegroundColor Cyan
    docker build -t $frontendImage --build-arg NEXT_PUBLIC_API_URL=http://localhost:3001 -f $frontendDockerfile $frontendPath
    if ($LASTEXITCODE -ne 0) {
        throw "Docker build failed for frontend"
    }
    Write-Host "  OK: frontend" -ForegroundColor Green
}

# --- Phase 2: Parallel kind load ---
Write-Host ""
Write-Host "=== Phase 2: Loading images into kind cluster ===" -ForegroundColor Yellow

$imagesToLoad = $selectedServices | ForEach-Object { $_.Image }
if ($buildFrontend) {
    $imagesToLoad += "urr-spring-frontend:local"
}

$loadJobs = @()
foreach ($img in $imagesToLoad) {
    $j = Start-Job -ScriptBlock {
        param($image, $cluster)
        kind load docker-image $image --name $cluster 2>&1
        if ($LASTEXITCODE -ne 0) { throw "kind load failed for $image" }
    } -ArgumentList $img, $KindClusterName
    $loadJobs += @{ Job = $j; Image = $img }
}

foreach ($lj in $loadJobs) {
    $result = Receive-Job -Job $lj.Job -Wait
    if ($lj.Job.State -eq "Failed") {
        Remove-Job -Job $lj.Job -Force
        throw "kind load failed for $($lj.Image)"
    }
    Remove-Job -Job $lj.Job -Force
    $shortName = $lj.Image -replace "urr-spring-", "" -replace ":local", ""
    Write-Host "  Loaded: $shortName" -ForegroundColor Green
}

Write-Host ""
Write-Host "All images are built and loaded into kind." -ForegroundColor Green
