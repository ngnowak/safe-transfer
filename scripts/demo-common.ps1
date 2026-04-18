$ErrorActionPreference = "Stop"

function Write-Step([string] $message) {
    Write-Host ""
    Write-Host "== $message ==" -ForegroundColor Cyan
}

function Convert-ResponseContentToString($content) {
    if ($null -eq $content) {
        return $null
    }

    if ($content -is [byte[]]) {
        return [System.Text.Encoding]::UTF8.GetString($content)
    }

    if ($content -is [array] -and $content.Count -gt 0 -and $content[0] -is [byte]) {
        return [System.Text.Encoding]::UTF8.GetString([byte[]] $content)
    }

    return [string] $content
}

function Write-Json($value) {
    if ($value -is [byte[]]) {
        $value = Convert-ResponseContentToString $value
        try {
            $value = $value | ConvertFrom-Json
        }
        catch {
        }
    }

    Write-Host ($value | ConvertTo-Json -Depth 20)
}

function Invoke-Api {
    param(
        [ValidateSet("GET", "POST")]
        [string] $Method,
        [string] $Uri,
        [object] $Body = $null,
        [hashtable] $Headers = @{},
        [switch] $AllowErrorStatus
    )

    $arguments = @{
        Method  = $Method
        Uri     = $Uri
        Headers = $Headers
    }

    if ($null -ne $Body) {
        $arguments.ContentType = "application/json"
        $arguments.Body = $Body | ConvertTo-Json -Depth 20
    }

    try {
        $response = Invoke-WebRequest @arguments
        $content = Convert-ResponseContentToString $response.Content
        $parsedBody = $null
        if (-not [string]::IsNullOrWhiteSpace($content)) {
            $parsedBody = $content | ConvertFrom-Json
        }

        return [pscustomobject]@{
            StatusCode = [int] $response.StatusCode
            Body       = $parsedBody
        }
    }
    catch {
        if (-not $AllowErrorStatus) {
            throw
        }

        $statusCode = $null
        $content = Convert-ResponseContentToString $_.ErrorDetails.Message

        if ($null -ne $_.Exception.Response) {
            $statusCode = [int] $_.Exception.Response.StatusCode
            if ([string]::IsNullOrWhiteSpace($content) -and $_.Exception.Response.GetResponseStream) {
                $stream = $_.Exception.Response.GetResponseStream()
                if ($null -ne $stream) {
                    $reader = [System.IO.StreamReader]::new($stream)
                    $content = $reader.ReadToEnd()
                }
            }
        }

        $parsedBody = $content
        if (-not [string]::IsNullOrWhiteSpace($content)) {
            try {
                $parsedBody = $content | ConvertFrom-Json
            }
            catch {
                $parsedBody = $content
            }
        }

        return [pscustomobject]@{
            StatusCode = $statusCode
            Body       = $parsedBody
        }
    }
}

function Assert-Status {
    param(
        [object] $Response,
        [int[]] $ExpectedStatus,
        [string] $Message
    )

    if ($ExpectedStatus -notcontains $Response.StatusCode) {
        Write-Host "Unexpected response for: $Message" -ForegroundColor Red
        Write-Host "Status: $($Response.StatusCode)" -ForegroundColor Red
        Write-Json $Response.Body
        exit 1
    }
}
