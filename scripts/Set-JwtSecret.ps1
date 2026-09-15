<#
.SYNOPSIS
生成并设置当前 PowerShell 会话使用的 JWT_SECRET。

.DESCRIPTION
请使用点号调用本脚本：
    . .\scripts\Set-JwtSecret.ps1

点号调用后，JWT_SECRET 会保留在当前会话中，随后可以直接启动后端。
#>

$rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$bytes = New-Object byte[] 32
try {
    $rng.GetBytes($bytes)
}
finally {
    $rng.Dispose()
}

$env:JWT_SECRET = [Convert]::ToBase64String($bytes)
$env:JWT_EXPIRATION_SECONDS = if ($env:JWT_EXPIRATION_SECONDS) {
    $env:JWT_EXPIRATION_SECONDS
} else {
    '7200'
}

Write-Host "JWT_SECRET configured for the current PowerShell session"
Write-Host "JWT_EXPIRATION_SECONDS=$env:JWT_EXPIRATION_SECONDS"
