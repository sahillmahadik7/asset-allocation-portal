$ErrorActionPreference = "Stop"

Write-Host "Checking Docker container..."

$status = docker inspect --format='{{.State.Status}}' asset-portal

if ($LASTEXITCODE -ne 0 -or $status.Trim() -ne "running") {
    Write-Error "FAIL: asset-portal container is not running."
    exit 1
}

Write-Host "Container is running."
Write-Host "Checking application HTTP response..."

try {
    $response = Invoke-WebRequest `
        -Uri "http://localhost:8081/" `
        -UseBasicParsing `
        -TimeoutSec 10

    if ($response.StatusCode -ne 200) {
        throw "Unexpected HTTP status: $($response.StatusCode)"
    }

    Write-Host "PASS: Application returned HTTP $($response.StatusCode)."
    exit 0
}
catch {
    Write-Error "FAIL: Application health check failed. $_"
    exit 1
}
