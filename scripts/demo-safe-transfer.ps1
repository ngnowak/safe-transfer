param(
    [string] $BaseUrl = "http://localhost:8080"
)

$ErrorActionPreference = "Stop"
. "$PSScriptRoot\demo-common.ps1"

$tenantId = [guid]::NewGuid().ToString()
$sourceCustomerId = [guid]::NewGuid().ToString()
$destinationCustomerId = [guid]::NewGuid().ToString()
$idempotencyKey = "demo-transfer-$([guid]::NewGuid())"
$currency = "EUR"

Write-Host "SafeTransfer interview demo"
Write-Host "Base URL: $BaseUrl"
Write-Host "Tenant:   $tenantId"

Write-Step "1. Check application health"
$health = Invoke-Api -Method GET -Uri "$BaseUrl/actuator/health"
Assert-Status $health @(200) "health"
Write-Json $health.Body

Write-Step "2. Create two tenant-scoped wallets"
$sourceWalletResponse = Invoke-Api `
    -Method POST `
    -Uri "$BaseUrl/api/v1/tenants/$tenantId/wallets" `
    -Body @{
        customerId = $sourceCustomerId
        currency   = $currency
    }
Assert-Status $sourceWalletResponse @(200) "create source wallet"
$sourceWallet = $sourceWalletResponse.Body

$destinationWalletResponse = Invoke-Api `
    -Method POST `
    -Uri "$BaseUrl/api/v1/tenants/$tenantId/wallets" `
    -Body @{
        customerId = $destinationCustomerId
        currency   = $currency
    }
Assert-Status $destinationWalletResponse @(200) "create destination wallet"
$destinationWallet = $destinationWalletResponse.Body

Write-Host "Source wallet:      $($sourceWallet.walletId)"
Write-Host "Destination wallet: $($destinationWallet.walletId)"

Write-Step "3. Deposit money into the source wallet"
$depositResponse = Invoke-Api `
    -Method POST `
    -Uri "$BaseUrl/api/v1/tenants/$tenantId/wallets/$($sourceWallet.walletId)/deposits" `
    -Body @{
        amount    = 100.00
        currency  = $currency
        reference = "interview demo initial funding"
    }
Assert-Status $depositResponse @(200) "deposit"
Write-Json $depositResponse.Body

Write-Step "4. Show ledger-derived balances before transfer"
$sourceBalanceBefore = Invoke-Api -Method GET -Uri "$BaseUrl/api/v1/tenants/$tenantId/wallets/$($sourceWallet.walletId)/balance"
$destinationBalanceBefore = Invoke-Api -Method GET -Uri "$BaseUrl/api/v1/tenants/$tenantId/wallets/$($destinationWallet.walletId)/balance"
Assert-Status $sourceBalanceBefore @(200) "source balance before transfer"
Assert-Status $destinationBalanceBefore @(200) "destination balance before transfer"
Write-Host "Source balance before:      $($sourceBalanceBefore.Body.balance) $currency"
Write-Host "Destination balance before: $($destinationBalanceBefore.Body.balance) $currency"

Write-Step "5. Create an idempotent wallet-to-wallet transfer"
$transferRequest = @{
    sourceWalletId      = $sourceWallet.walletId
    destinationWalletId = $destinationWallet.walletId
    amount              = 25.00
    currency            = $currency
    reference           = "interview demo transfer"
}

$transferResponse = Invoke-Api `
    -Method POST `
    -Uri "$BaseUrl/api/v1/tenants/$tenantId/transfers" `
    -Headers @{ "Idempotency-Key" = $idempotencyKey } `
    -Body $transferRequest
Assert-Status $transferResponse @(201) "create transfer"
$transfer = $transferResponse.Body
Write-Host "HTTP status:  $($transferResponse.StatusCode)"
Write-Host "Transfer id:  $($transfer.transferId)"
Write-Host "Status:       $($transfer.status)"

Write-Step "6. Replay the same request with the same Idempotency-Key"
$replayResponse = Invoke-Api `
    -Method POST `
    -Uri "$BaseUrl/api/v1/tenants/$tenantId/transfers" `
    -Headers @{ "Idempotency-Key" = $idempotencyKey } `
    -Body $transferRequest
Assert-Status $replayResponse @(200) "idempotent replay"
Write-Host "HTTP status:       $($replayResponse.StatusCode)"
Write-Host "Returned transfer: $($replayResponse.Body.transferId)"

Write-Step "7. Reuse the same Idempotency-Key with a different body"
$conflictingRequest = @{
    sourceWalletId      = $sourceWallet.walletId
    destinationWalletId = $destinationWallet.walletId
    amount              = 30.00
    currency            = $currency
    reference           = "same key different body"
}

$conflictResponse = Invoke-Api `
    -Method POST `
    -Uri "$BaseUrl/api/v1/tenants/$tenantId/transfers" `
    -Headers @{ "Idempotency-Key" = $idempotencyKey } `
    -Body $conflictingRequest `
    -AllowErrorStatus
Assert-Status $conflictResponse @(409) "idempotency conflict"
Write-Host "HTTP status: $($conflictResponse.StatusCode)"
Write-Json $conflictResponse.Body

Write-Step "8. Show final balances"
$sourceBalanceAfter = Invoke-Api -Method GET -Uri "$BaseUrl/api/v1/tenants/$tenantId/wallets/$($sourceWallet.walletId)/balance"
$destinationBalanceAfter = Invoke-Api -Method GET -Uri "$BaseUrl/api/v1/tenants/$tenantId/wallets/$($destinationWallet.walletId)/balance"
Assert-Status $sourceBalanceAfter @(200) "source balance after transfer"
Assert-Status $destinationBalanceAfter @(200) "destination balance after transfer"
Write-Host "Source balance after:      $($sourceBalanceAfter.Body.balance) $currency"
Write-Host "Destination balance after: $($destinationBalanceAfter.Body.balance) $currency"

Write-Step "9. Fetch transfer by id"
$getTransferResponse = Invoke-Api -Method GET -Uri "$BaseUrl/api/v1/tenants/$tenantId/transfers/$($transfer.transferId)"
Assert-Status $getTransferResponse @(200) "get transfer"
Write-Json $getTransferResponse.Body

Write-Step "Demo talking points"
Write-Host "Swagger UI:       $BaseUrl/swagger-ui.html"
Write-Host "OpenAPI JSON:     $BaseUrl/v3/api-docs"
Write-Host "Prometheus data:  $BaseUrl/actuator/prometheus"
Write-Host "Kafka proof:      .\scripts\demo-kafka-local.ps1"
Write-Host ""
Write-Host "What this demonstrated:"
Write-Host "- Tenant-scoped wallets"
Write-Host "- Immutable ledger entries for balance calculation"
Write-Host "- Idempotent transfers with request hash conflict detection"
Write-Host "- Transfer lookup and operational endpoints"
