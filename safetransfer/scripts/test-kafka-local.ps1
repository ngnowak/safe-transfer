$ErrorActionPreference = "Stop"

$baseUrl = "http://localhost:8080"
$kafkaContainer = "safe-transfer-kafka"
$kcatImage = "edenhill/kcat:1.7.1"
$topic = "wallet.transfer.completed"
$tenantId = [guid]::NewGuid().ToString()
$customerId1 = [guid]::NewGuid().ToString()
$customerId2 = [guid]::NewGuid().ToString()
$idempotencyKey = [guid]::NewGuid().ToString()

function Post-Json($url, $body, $headers = @{}) {
    Invoke-RestMethod `
        -Method Post `
        -Uri $url `
        -ContentType "application/json" `
        -Headers $headers `
        -Body ($body | ConvertTo-Json -Depth 10)
}

function Assert-ContainerRunning($name) {
    $containerId = docker ps --filter "name=$name" --format "{{.ID}}"
    if ([string]::IsNullOrWhiteSpace($containerId)) {
        throw "Kafka container '$name' is not running. Start it with: docker compose -f .\docker\docker-compose.yml up -d"
    }
}

function Read-KafkaMessages($topicName) {
    $network = docker inspect $kafkaContainer --format '{{range $name, $conf := .NetworkSettings.Networks}}{{$name}}{{end}}'
    if ([string]::IsNullOrWhiteSpace($network)) {
        throw "Cannot resolve Docker network for '$kafkaContainer'"
    }

    docker run --rm --network $network $kcatImage `
        -b "${kafkaContainer}:29092" `
        -C `
        -t $topicName `
        -o beginning `
        -e `
        -q `
        -m 10000
}

Assert-ContainerRunning $kafkaContainer

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
        reference = "kafka local test deposit"
    }

Write-Host "Running transfer..."
$transfer = Post-Json `
    "$baseUrl/api/v1/tenants/$tenantId/transfers" `
    @{
        sourceWalletId = $sourceWallet.walletId
        destinationWalletId = $destinationWallet.walletId
        amount = 25.00
        currency = "EUR"
        reference = "kafka local test"
    } `
    @{
        "Idempotency-Key" = $idempotencyKey
    }

$transferId = $transfer.transferId
Write-Host "Transfer created: $transferId"
Write-Host "Reading Kafka topic '$topic'..."

$matchedMessage = $null
$messages = @()
for ($attempt = 1; $attempt -le 6; $attempt++) {
    Start-Sleep -Seconds 5
    $messages = Read-KafkaMessages $topic
    $matchedMessage = $messages | Where-Object { $_ -like "*$tenantId*" -and $_ -like "*$transferId*" } | Select-Object -First 1

    if (-not [string]::IsNullOrWhiteSpace($matchedMessage)) {
        break
    }

    Write-Host "Kafka message not found yet, retrying ($attempt/6)..."
}

if ([string]::IsNullOrWhiteSpace($matchedMessage)) {
    Write-Host "No Kafka message found for tenant $tenantId and transfer $transferId"
    Write-Host "`nMessages read from topic:"
    $messages
    exit 1
}

Write-Host "Kafka message found for transfer $transferId"
Write-Host $matchedMessage
