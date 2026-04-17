$baseUrl = "http://localhost:8080"
$tenantId = [guid]::NewGuid().ToString()
$customerId1 = [guid]::NewGuid().ToString()
$customerId2 = [guid]::NewGuid().ToString()
$idempotencySuccess = [guid]::NewGuid().ToString()
$idempotencyFailure = [guid]::NewGuid().ToString()

function Post-Json($url, $body, $headers = @{}) {
    Invoke-RestMethod `
        -Method Post `
        -Uri $url `
        -ContentType "application/json" `
        -Headers $headers `
        -Body ($body | ConvertTo-Json -Depth 10)
}

function Get-Metric($name, $tag) {
    $uri = "$baseUrl/actuator/metrics/$name"
    if ($tag) {
        $encodedTag = [System.Uri]::EscapeDataString($tag)
        $uri = "${uri}?tag=$encodedTag"
    }
    Invoke-RestMethod -Method Get -Uri $uri
}

Write-Host "Creating source wallet..."
$sourceWallet = Post-Json `
    "$baseUrl/api/v1/tenants/$tenantId/wallets" `
    @{
        customerId = $customerId1
        currency = "EUR"
    }

Write-Host "Creating destination wallet..."
$destinationWallet = Post-Json `
    "$baseUrl/api/v1/tenants/$tenantId/wallets" `
    @{
        customerId = $customerId2
        currency = "EUR"
    }

Write-Host "Depositing 100.00 EUR into source wallet..."
$null = Post-Json `
    "$baseUrl/api/v1/tenants/$tenantId/wallets/$($sourceWallet.walletId)/deposits" `
    @{
        amount = 100.00
        currency = "EUR"
        reference = "metrics test deposit"
    }

Write-Host "Running successful transfer..."
$successTransfer = Post-Json `
    "$baseUrl/api/v1/tenants/$tenantId/transfers" `
    @{
        sourceWalletId = $sourceWallet.walletId
        destinationWalletId = $destinationWallet.walletId
        amount = 25.00
        currency = "EUR"
        reference = "metrics success"
    } `
    @{
        "Idempotency-Key" = $idempotencySuccess
    }

Write-Host "Successful transfer id: $($successTransfer.transferId)"

Write-Host "Running failing transfer (insufficient funds)..."
try {
    $null = Post-Json `
        "$baseUrl/api/v1/tenants/$tenantId/transfers" `
        @{
            sourceWalletId = $sourceWallet.walletId
            destinationWalletId = $destinationWallet.walletId
            amount = 1000.00
            currency = "EUR"
            reference = "metrics failure"
        } `
        @{
            "Idempotency-Key" = $idempotencyFailure
        }

    Write-Host "Unexpected: failure scenario succeeded"
}
catch {
    Write-Host "Failure transfer returned expected error"
}

Start-Sleep -Seconds 1

Write-Host "`nAll transfer metric tags:"
Get-Metric "safetransfer.transfer.created" $null | ConvertTo-Json -Depth 10

Write-Host "`nSuccess counter:"
Get-Metric "safetransfer.transfer.created" "outcome:success" | ConvertTo-Json -Depth 10

Write-Host "`nInsufficient funds counter:"
Get-Metric "safetransfer.transfer.created" "outcome:insufficient_funds" | ConvertTo-Json -Depth 10

Write-Host "`nSuccess timer:"
Get-Metric "safetransfer.transfer.duration" "outcome:success" | ConvertTo-Json -Depth 10

Write-Host "`nInsufficient funds timer:"
Get-Metric "safetransfer.transfer.duration" "outcome:insufficient_funds" | ConvertTo-Json -Depth 10
