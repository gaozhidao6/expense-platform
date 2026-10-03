<# Run from any directory. MySQL must already be initialized. Ctrl+C stops both app processes. #>
$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
foreach ($tool in @('java', 'mvn', 'npm')) {
    if (-not (Get-Command $tool -ErrorAction SilentlyContinue)) { throw "Missing prerequisite: $tool" }
}
foreach ($port in @(8080, 5173)) {
    if (Get-NetTCPConnection -State Listen -LocalPort $port -ErrorAction SilentlyContinue) {
        throw "Port $port is already occupied. Stop its service before running this script."
    }
}
Push-Location $projectRoot
$backendProcess = $null
$frontendProcess = $null
try {
    if (-not $env:JWT_SECRET) { . (Join-Path $PSScriptRoot 'Set-JwtSecret.ps1') }
    & mvn -B -f backend/pom.xml -DskipTests package
    if ($LASTEXITCODE -ne 0) { throw 'Backend build failed' }
    Push-Location (Join-Path $projectRoot 'frontend')
    try { & npm ci; if ($LASTEXITCODE -ne 0) { throw 'npm ci failed' } } finally { Pop-Location }
    $runDirectory = Join-Path $projectRoot '.run'
    New-Item -ItemType Directory -Path $runDirectory -Force | Out-Null
    $backendProcess = Start-Process java -WorkingDirectory (Join-Path $projectRoot 'backend') -ArgumentList @('-jar', 'target/expense-platform-0.0.1-SNAPSHOT.jar') -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $runDirectory 'backend.stdout.log') -RedirectStandardError (Join-Path $runDirectory 'backend.stderr.log')
    $ready = $false
    for ($attempt = 0; $attempt -lt 60; $attempt++) {
        if ($backendProcess.HasExited) { throw 'Backend exited. See .run/backend.stderr.log and backend.stdout.log' }
        try {
            $response = Invoke-WebRequest 'http://127.0.0.1:8080/api/health' -TimeoutSec 2
            if ($response.StatusCode -eq 200) { $ready = $true; break }
        } catch { }
        Start-Sleep -Seconds 2
    }
    if (-not $ready) { throw 'Backend readiness timed out. Check MySQL and logs in .run' }
    $nodePath = (Get-Command node -ErrorAction Stop).Source
    $frontendProcess = Start-Process $nodePath -WorkingDirectory (Join-Path $projectRoot 'frontend') -ArgumentList @('node_modules/vite/bin/vite.js', '--host', '127.0.0.1') -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $runDirectory 'frontend.stdout.log') -RedirectStandardError (Join-Path $runDirectory 'frontend.stderr.log')
    Write-Host 'Open http://localhost:5173 | Ctrl+C stops both processes | Logs: .run/'
    while (-not $backendProcess.HasExited -and -not $frontendProcess.HasExited) { Start-Sleep -Seconds 1 }
    throw 'An application process exited; check .run logs.'
} finally {
    # Only the exact processes started by this invocation are stopped.
    foreach ($startedProcess in @($backendProcess, $frontendProcess)) {
        if ($null -ne $startedProcess -and -not $startedProcess.HasExited) { $startedProcess.Kill() }
    }
    Pop-Location
}
