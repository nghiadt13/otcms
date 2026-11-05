$ErrorActionPreference = 'Stop'

# Local test launcher: product worktree + otcms_dev + patient Cloudflare tunnel.
$product = 'D:\Education\SEP490-FALL2026\tmp\otcms-product-test'
$localEnv = 'D:\Education\SEP490-FALL2026\otcms\backend\.env.dev'
$gateway = 'D:\Education\SEP490-FALL2026\tmp\tunnel-gateway.js'
$cloudflared = 'C:\Program Files (x86)\cloudflared\cloudflared.exe'
$logDir = Join-Path $env:TEMP 'otcms-launch'
$shell = (Get-Command pwsh -ErrorAction Stop).Source
$node = (Get-Command node -ErrorAction Stop).Source
$started = [System.Collections.Generic.List[int]]::new()

function Stop-ProcessTree([int]$ProcessId) {
    foreach ($child in @(Get-CimInstance Win32_Process -Filter "ParentProcessId=$ProcessId")) {
        Stop-ProcessTree ([int]$child.ProcessId)
    }
    Stop-Process -Id $ProcessId -Force -ErrorAction SilentlyContinue
}

function Start-LoggedPowerShell([string]$Name, [string]$Directory, [string]$Command) {
    $encoded = [Convert]::ToBase64String([Text.Encoding]::Unicode.GetBytes($Command))
    $process = Start-Process -FilePath $shell -ArgumentList @('-NoProfile', '-EncodedCommand', $encoded) `
        -WorkingDirectory $Directory -WindowStyle Hidden `
        -RedirectStandardOutput (Join-Path $logDir "$Name.log") `
        -RedirectStandardError (Join-Path $logDir "$Name.err") -PassThru
    $started.Add($process.Id)
}

function Wait-Http([string]$Url, [int]$Seconds) {
    for ($i = 0; $i -lt $Seconds; $i++) {
        try {
            $response = Invoke-WebRequest -UseBasicParsing -Uri $Url -TimeoutSec 15
            if ($response.StatusCode -eq 200) { return $true }
        } catch { }
        Start-Sleep -Seconds 1
    }
    return $false
}

try {
    foreach ($path in @($product, $localEnv, $gateway, $cloudflared, "$product\frontend\node_modules")) {
        if (-not (Test-Path -LiteralPath $path)) { throw "Missing required file or folder: $path" }
    }
    if (-not (Test-NetConnection -ComputerName 127.0.0.1 -Port 5432 -InformationLevel Quiet -WarningAction SilentlyContinue)) {
        throw 'PostgreSQL is not listening on 127.0.0.1:5432.'
    }
    # Stop only known OTCMS listeners; never kill an unrelated application by port alone.
    $occupied = @(Get-NetTCPConnection -State Listen -ErrorAction SilentlyContinue | Where-Object { $_.LocalPort -in 3000, 3100, 8080 })
    $roots = [System.Collections.Generic.List[int]]::new()
    foreach ($socket in $occupied) {
        $process = Get-CimInstance Win32_Process -Filter "ProcessId=$($socket.OwningProcess)"
        if (-not $process) { continue }
        $known = $false
        $rootId = [int]$process.ProcessId
        if ($socket.LocalPort -eq 3100 -and $process.CommandLine -match 'tunnel-gateway\.js') { $known = $true }
        if ($socket.LocalPort -eq 3000 -and $process.CommandLine -match '(?i)(otcms-product-test|SEP490-FALL2026\\otcms)\\frontend\\') { $known = $true }
        if ($socket.LocalPort -eq 8080 -and $process.CommandLine -match 'fu\.fa26\.sep490\.g58\.otcms_be\.OtcmsBeApplication') {
            $parent = Get-CimInstance Win32_Process -Filter "ProcessId=$($process.ParentProcessId)"
            if ($parent -and $parent.CommandLine -match '(?i)(otcms-product-test|SEP490-FALL2026\\otcms)\\backend') {
                $known = $true
                $rootId = [int]$parent.ProcessId
            }
        }
        if (-not $known) { throw "Port $($socket.LocalPort) is used by another application (PID $($socket.OwningProcess))." }
        if (-not $roots.Contains($rootId)) { $roots.Add($rootId) }
    }
    foreach ($cloud in @(Get-CimInstance Win32_Process -Filter "Name='cloudflared.exe'" | Where-Object { $_.CommandLine -match '--url[ =]+http://(localhost|127\.0\.0\.1):3100' })) {
        Stop-ProcessTree ([int]$cloud.ProcessId)
    }
    if ($roots.Count) { Write-Host 'Stopping previous OTCMS processes...' }
    foreach ($rootId in $roots) { Stop-ProcessTree $rootId }
    if ($roots.Count) { Start-Sleep -Seconds 2 }
    $occupied = @(Get-NetTCPConnection -State Listen -ErrorAction SilentlyContinue | Where-Object { $_.LocalPort -in 3000, 3100, 8080 })
    if ($occupied.Count) {
        $ports = ($occupied.LocalPort | Sort-Object -Unique) -join ', '
        throw "Port(s) $ports are already in use. Stop those applications before starting OTCMS."
    }
    New-Item -ItemType Directory -Path $logDir -Force | Out-Null

    Write-Host 'Starting gateway and Cloudflare tunnel...'
    $gatewayProcess = Start-Process -FilePath $node -ArgumentList @('"' + $gateway + '"') -WindowStyle Hidden `
        -RedirectStandardOutput (Join-Path $logDir 'gateway.log') `
        -RedirectStandardError (Join-Path $logDir 'gateway.err') -PassThru
    $started.Add($gatewayProcess.Id)
    $cfLog = Join-Path $logDir 'cloudflare.err'
    $cloudProcess = Start-Process -FilePath $cloudflared -ArgumentList @('tunnel', '--no-autoupdate', '--url', 'http://localhost:3100') `
        -WindowStyle Hidden -RedirectStandardOutput (Join-Path $logDir 'cloudflare.log') `
        -RedirectStandardError $cfLog -PassThru
    $started.Add($cloudProcess.Id)
    $publicUrl = $null
    for ($i = 0; $i -lt 45 -and -not $publicUrl; $i++) {
        Start-Sleep -Seconds 1
        if (Test-Path -LiteralPath $cfLog) {
            $match = Select-String -Path $cfLog -Pattern 'https://[a-z0-9-]+\.trycloudflare\.com' | Select-Object -First 1
            if ($match) { $publicUrl = $match.Matches[0].Value }
        }
    }
    if (-not $publicUrl) { throw "Cloudflare did not provide a URL. See $cfLog" }

    $backend = @"
`$ErrorActionPreference = 'Stop'
foreach (`$line in Get-Content -LiteralPath '$localEnv' -Encoding UTF8) {
    if (`$line -match '^([A-Z_][A-Z0-9_]*)=(.*)$') {
        [Environment]::SetEnvironmentVariable(`$matches[1], `$matches[2], 'Process')
    }
}
`$env:SPRING_PROFILES_ACTIVE = ''
`$env:SPRING_DATASOURCE_URL = 'jdbc:postgresql://127.0.0.1:5432/otcms_dev'
`$env:SPRING_DATASOURCE_USERNAME = `$env:SUPABASE_DB_USERNAME
`$env:SPRING_DATASOURCE_PASSWORD = `$env:SUPABASE_DB_PASSWORD
`$env:SERVER_PORT = '8080'
`$origins = 'http://localhost:3000,http://127.0.0.1:3000,http://localhost:3100,http://127.0.0.1:3100,$publicUrl'
`$env:APP_AUTH_PATIENT_ORIGINS = `$origins
`$env:APP_AUTH_STAFF_ORIGINS = `$origins
`$env:APP_AUTH_COOKIE_SECURE = 'false'
`$env:SPRING_APPLICATION_JSON = '{"otcms":{"visit":{"care-step-room-capability":{"SCHEDULED_APPOINTMENT":"EXAMINATION"},"room-cleanup-buffer-minutes":{"EXAMINATION":0}}}}'
Set-Location '$product\backend'
& .\mvnw.cmd spring-boot:run
"@
    $frontend = @"
`$env:NEXT_PUBLIC_API_BASE_URL = 'http://localhost:3100'
`$env:OTCMS_DEV_API_PROXY = 'false'
Set-Location '$product\frontend'
& npm.cmd run dev
"@
    Write-Host 'Starting backend and frontend...'
    Start-LoggedPowerShell 'backend' "$product\backend" $backend
    Start-LoggedPowerShell 'frontend' "$product\frontend" $frontend
    Write-Host 'Waiting for backend...'
    if (-not (Wait-Http 'http://localhost:8080/v3/api-docs' 120)) { throw "Backend did not start. See $logDir\backend.log and backend.err" }
    Write-Host 'Waiting for frontend...'
    if (-not (Wait-Http 'http://localhost:3100/login' 90)) { throw "Frontend did not start. See $logDir\frontend.log and frontend.err" }
    Write-Host 'Checking Cloudflare...'
    if (-not (Wait-Http "$publicUrl/login" 30)) { throw "Cloudflare URL did not respond. See $cfLog" }

    Write-Host ''
    Write-Host 'OTCMS is ready.' -ForegroundColor Green
    Write-Host 'Database: otcms_dev'
    Write-Host 'Local:    http://localhost:3100'
    Write-Host "Public:   $publicUrl"
    Write-Host "Logs:     $logDir"
    Write-Host 'Keep the public URL private. It changes whenever the tunnel restarts.'
} catch {
    foreach ($processId in $started) { Stop-ProcessTree $processId }
    Write-Host "Could not start OTCMS: $($_.Exception.Message)" -ForegroundColor Red
    Write-Host "Logs: $logDir"
    exit 1
}
