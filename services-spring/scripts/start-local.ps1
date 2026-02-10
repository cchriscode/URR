param(
  [switch]$BuildFirst
)

$services = @("auth-service", "ticket-service", "payment-service", "stats-service", "gateway-service")
$base = Split-Path -Parent $MyInvocation.MyCommand.Path
$root = Resolve-Path (Join-Path $base "..")

foreach ($svc in $services) {
  $svcPath = Join-Path $root $svc
  if ($BuildFirst) {
    Write-Host "Building $svc ..."
    & "$svcPath\gradlew.bat" -p $svcPath clean build -x test
  }

  Start-Process -FilePath "$svcPath\gradlew.bat" -ArgumentList "-p", $svcPath, "bootRun" -WorkingDirectory $svcPath
}

Write-Host "Started Spring services in separate windows/processes."
