$kindDir = "C:\Users\deadl\bin"
if (-not (Test-Path $kindDir)) {
    New-Item -ItemType Directory -Path $kindDir | Out-Null
    Write-Host "Created $kindDir"
}

$kindExe = "$kindDir\kind.exe"
Write-Host "Downloading kind v0.27.0..."
Invoke-WebRequest -Uri "https://kind.sigs.k8s.io/dl/v0.27.0/kind-windows-amd64" -OutFile $kindExe

if (Test-Path $kindExe) {
    Write-Host "Downloaded kind.exe to $kindExe"
    # Add to user PATH
    $currentPath = [Environment]::GetEnvironmentVariable("PATH", "User")
    if ($currentPath -notlike "*$kindDir*") {
        [Environment]::SetEnvironmentVariable("PATH", "$currentPath;$kindDir", "User")
        Write-Host "Added $kindDir to user PATH"
    } else {
        Write-Host "$kindDir is already in PATH"
    }
    Write-Host "kind version:"
    & $kindExe version
} else {
    Write-Host "Download failed!"
    exit 1
}
