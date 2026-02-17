$ErrorActionPreference = "Stop"

function Assert-Endpoint {
    param(
        [Parameter(Mandatory = $true)][string]$Url,
        [Parameter(Mandatory = $true)][int]$ExpectedStatus
    )

    try {
        $response = Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 10
        $status = [int]$response.StatusCode
    }
    catch {
        if ($_.Exception.Response -and $_.Exception.Response.StatusCode) {
            $status = [int]$_.Exception.Response.StatusCode
        }
        else {
            throw
        }
    }

    if ($status -ne $ExpectedStatus) {
        throw "Unexpected status from $Url. expected=$ExpectedStatus actual=$status"
    }
}

kubectl get pods -n urr-spring

Assert-Endpoint -Url "http://localhost:3001/health" -ExpectedStatus 200
Assert-Endpoint -Url "http://localhost:3001/api/auth/me" -ExpectedStatus 401
Assert-Endpoint -Url "http://localhost:3000" -ExpectedStatus 200

Write-Host "Smoke checks passed."
