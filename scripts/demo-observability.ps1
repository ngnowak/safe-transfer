param(
    [string] $BaseUrl = "http://localhost:8080"
)

$ErrorActionPreference = "Stop"
. "$PSScriptRoot\demo-common.ps1"

$tenantId = [guid]::NewGuid().ToString()
$sourceCustomerId = [guid]::NewGuid().ToString()
$destinationCustomerId = [guid]::NewGuid().ToString()
$successKey = "observability-success-$([guid]::NewGuid())"
$conflictKey = "observability-conflict-$([guid]::NewGuid())"
$currency = "EUR"

function Get-Metric {
    param(
        [string] $Name,
        [string] $Tag = $null
    )

    $uri = "$BaseUrl/actuator/metrics/$Name"
    if (-not [string]::IsNullOrWhiteSpace($Tag)) {
        $encodedTag = [System.Uri]::EscapeDataString($Tag)
        $uri = "${uri}?tag=$encodedTag"
    }

    return (Invoke-Api -Method GET -Uri $uri).Body
}

function Show-Metric {
    param(
        [string] $Title,
        [string] $Name,
        [string] $Tag = $null,
        [string] $MissingMessage = "Metric has no samples yet."
    )

    Write-Host ""
    Write-Host $Title -ForegroundColor Yellow
    try {
        $metric = Get-Metric -Name $Name -Tag $Tag
        Write-Json $metric
    }
    catch {
        Write-Host $MissingMessage -ForegroundColor DarkYellow
        Write-Host "Metric: $Name $Tag" -ForegroundColor DarkGray
    }
}

Write-Host "SafeTransfer observability demo"
Write-Host "Base URL: $BaseUrl"
Write-Host "Tenant:   $tenantId"

Write-Step "1. Health endpoint"
$health = Invoke-Api -Method GET -Uri "$BaseUrl/actuator/health"
Assert-Status $health @(200) "health"
Write-Json $health.Body

Write-Step "2. Metrics endpoint"
$metrics = Invoke-Api -Method GET -Uri "$BaseUrl/actuator/metrics"
Assert-Status $metrics @(200) "metrics"
$metrics.Body.names |
    Where-Object { $_ -like "safetransfer.*" -or $_ -in @("http.server.requests", "jvm.memory.used", "process.uptime") } |
    Sort-Object |
    ForEach-Object { Write-Host $_ }

Write-Step "3. Generate successful transfer metric"
$sourceWallet = (Invoke-Api `
    -Method POST `
    -Uri "$BaseUrl/api/v1/tenants/$tenantId/wallets" `
    -Body @{
        customerId = $sourceCustomerId
        currency   = $currency
    }).Body

$destinationWallet = (Invoke-Api `
    -Method POST `
    -Uri "$BaseUrl/api/v1/tenants/$tenantId/wallets" `
    -Body @{
        customerId = $destinationCustomerId
        currency   = $currency
    }).Body

$null = Invoke-Api `
    -Method POST `
    -Uri "$BaseUrl/api/v1/tenants/$tenantId/wallets/$($sourceWallet.walletId)/deposits" `
    -Body @{
        amount    = 100.00
        currency  = $currency
        reference = "observability demo funding"
    }

$successfulTransfer = Invoke-Api `
    -Method POST `
    -Uri "$BaseUrl/api/v1/tenants/$tenantId/transfers" `
    -Headers @{ "Idempotency-Key" = $successKey } `
    -Body @{
        sourceWalletId      = $sourceWallet.walletId
        destinationWalletId = $destinationWallet.walletId
        amount              = 25.00
        currency            = $currency
        reference           = "observability success"
    }
Assert-Status $successfulTransfer @(201) "successful transfer"
Write-Host "Successful transfer id: $($successfulTransfer.Body.transferId)"

Write-Step "4. Generate insufficient funds metric"
$insufficientFunds = Invoke-Api `
    -Method POST `
    -Uri "$BaseUrl/api/v1/tenants/$tenantId/transfers" `
    -Headers @{ "Idempotency-Key" = "observability-failure-$([guid]::NewGuid())" } `
    -Body @{
        sourceWalletId      = $sourceWallet.walletId
        destinationWalletId = $destinationWallet.walletId
        amount              = 1000.00
        currency            = $currency
        reference           = "observability insufficient funds"
    } `
    -AllowErrorStatus
Assert-Status $insufficientFunds @(409) "insufficient funds transfer"
Write-Host "Expected status: $($insufficientFunds.StatusCode)"

Write-Step "5. Generate idempotency conflict metric"
$conflictRequest = @{
    sourceWalletId      = $sourceWallet.walletId
    destinationWalletId = $destinationWallet.walletId
    amount              = 10.00
    currency            = $currency
    reference           = "observability idempotency first"
}

$firstConflictTransfer = Invoke-Api `
    -Method POST `
    -Uri "$BaseUrl/api/v1/tenants/$tenantId/transfers" `
    -Headers @{ "Idempotency-Key" = $conflictKey } `
    -Body $conflictRequest
Assert-Status $firstConflictTransfer @(201) "first conflict transfer"

$conflict = Invoke-Api `
    -Method POST `
    -Uri "$BaseUrl/api/v1/tenants/$tenantId/transfers" `
    -Headers @{ "Idempotency-Key" = $conflictKey } `
    -Body @{
        sourceWalletId      = $sourceWallet.walletId
        destinationWalletId = $destinationWallet.walletId
        amount              = 11.00
        currency            = $currency
        reference           = "observability idempotency different body"
    } `
    -AllowErrorStatus
Assert-Status $conflict @(409) "idempotency conflict"
Write-Host "Expected status: $($conflict.StatusCode)"

Write-Step "6. Show custom transfer metrics"
Show-Metric "Transfer attempts by outcome" "safetransfer.transfer.created"
Show-Metric "Successful transfer counter" "safetransfer.transfer.created" "outcome:success"
Show-Metric "Insufficient funds counter" "safetransfer.transfer.created" "outcome:insufficient_funds"
Show-Metric "Idempotency conflict counter" "safetransfer.transfer.created" "outcome:idempotency_conflict"
Show-Metric "Successful transfer duration" "safetransfer.transfer.duration" "outcome:success"

Write-Step "7. Show outbox metrics"
Write-Host "Waiting for the scheduled outbox publisher..."
Start-Sleep -Seconds 6
Show-Metric "Outbox publish success" "safetransfer.outbox.publish.success"
Show-Metric `
    "Outbox publish failure" `
    "safetransfer.outbox.publish.failure" `
    -MissingMessage "No outbox publish failures recorded. This is expected in the happy-path demo."
Show-Metric `
    "Outbox fatal events" `
    "safetransfer.outbox.fatal" `
    -MissingMessage "No fatal outbox events recorded. This is expected unless retries are exhausted."

Write-Step "8. Prometheus endpoint"
Write-Host "Open this endpoint or point Prometheus at it:"
Write-Host "$BaseUrl/actuator/prometheus" -ForegroundColor Yellow
Write-Host ""
Write-Host "Useful interview line:"
Write-Host "The app exposes process health, standard JVM/HTTP metrics, and business metrics for transfer outcomes and outbox publishing reliability."
