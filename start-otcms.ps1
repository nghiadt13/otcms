param([switch] $BackendInIntelliJ)

$ErrorActionPreference = 'Stop'

# Local test launcher: this checkout (otcms, nhánh đang checkout) + otcms_dev + Orthanc/OHIF (Docker)
# + Cloudflare tunnel. Một cổng duy nhất 3001 (nginx imaging-gateway trong compose.yaml) gom
# /otcms (frontend 3000), /api và /dicom-web (backend 8080), /viewer (OHIF).
$product = [System.IO.Path]::GetFullPath($PSScriptRoot)
$localEnv = Join-Path $product 'backend/.env'
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
    return $process
}

# Chờ URL trả 200 tối đa $Seconds giây (tính theo đồng hồ thật). Nếu tiến trình con đã thoát thì
# dừng ngay thay vì chờ hết hạn: lỗi khởi động hiện ra sau vài giây, không phải sau vài phút.
function Wait-Http([string]$Url, [int]$Seconds, $Process = $null) {
    $deadline = (Get-Date).AddSeconds($Seconds)
    while ((Get-Date) -lt $deadline) {
        if ($Process -and $Process.HasExited) { return $false }
        try {
            $response = Invoke-WebRequest -UseBasicParsing -Uri $Url -TimeoutSec 5
            if ($response.StatusCode -eq 200) { return $true }
        } catch { }
        Start-Sleep -Seconds 1
    }
    return $false
}

try {
    foreach ($path in @($product, $localEnv, $cloudflared)) {
        if (-not (Test-Path -LiteralPath $path)) { throw "Missing required file or folder: $path" }
    }
    # Thư mục node_modules có thể tồn tại nhưng rỗng/hỏng: kiểm tra đúng lệnh `next` và tự cài lại.
    $nextCmd = "$product\frontend\node_modules\.bin\next.cmd"
    if (-not (Test-Path -LiteralPath $nextCmd)) {
        Write-Host 'Frontend dependencies missing, running npm ci (takes a few minutes)...'
        Push-Location "$product\frontend"
        try { & npm.cmd ci } finally { Pop-Location }
        if (-not (Test-Path -LiteralPath $nextCmd)) { throw "npm ci did not install next. Run it manually in $product\frontend" }
    }
    & docker info *> $null
    if ($LASTEXITCODE -ne 0) { throw 'Docker is not running. Start Docker Desktop first (needed for Orthanc/OHIF and the 3001 gateway).' }
    $databasePort = 5433
    $databasePortLine = Get-Content -LiteralPath $localEnv -Encoding UTF8 |
        Where-Object { $_ -match '^\s*OTCMS_DB_PORT\s*=' } |
        Select-Object -First 1
    if ($databasePortLine -match '^\s*OTCMS_DB_PORT\s*=\s*(\d+)\s*$') {
        $databasePort = [int] $Matches[1]
    }
    # Stop only known OTCMS listeners; never kill an unrelated application by port alone.
    # In IntelliJ mode, port 8080 belongs to the visible backend and must be preserved.
    # Cổng 3001 do container Docker giữ nên không nằm trong danh sách này.
    $managedPorts = if ($BackendInIntelliJ) { @(3000) } else { @(3000, 8080) }
    $occupied = @(Get-NetTCPConnection -State Listen -ErrorAction SilentlyContinue | Where-Object { $_.LocalPort -in $managedPorts })
    $roots = [System.Collections.Generic.List[int]]::new()
    $escapedProduct = [regex]::Escape($product)
    $escapedLegacyProduct = [regex]::Escape('D:\Education\SEP490-FALL2026\otcms')
    foreach ($socket in $occupied) {
        $process = Get-CimInstance Win32_Process -Filter "ProcessId=$($socket.OwningProcess)"
        if (-not $process) { continue }
        $known = $false
        $rootId = [int]$process.ProcessId
        if ($socket.LocalPort -eq 3000 -and $process.CommandLine -match "(?i)(?:$escapedProduct|$escapedLegacyProduct)\\frontend\\") { $known = $true }
        if ($socket.LocalPort -eq 8080 -and $process.CommandLine -match 'fu\.fa26\.sep490\.g58\.otcms_be\.OtcmsBeApplication') {
            $parent = Get-CimInstance Win32_Process -Filter "ProcessId=$($process.ParentProcessId)"
            if ($parent -and $parent.CommandLine -match "(?i)(?:$escapedProduct|$escapedLegacyProduct)\\backend") {
                $known = $true
                $rootId = [int]$parent.ProcessId
            }
        }
        if (-not $known) { throw "Port $($socket.LocalPort) is used by another application (PID $($socket.OwningProcess))." }
        if (-not $roots.Contains($rootId)) { $roots.Add($rootId) }
    }
    if (-not $BackendInIntelliJ) {
        foreach ($cloud in @(Get-CimInstance Win32_Process -Filter "Name='cloudflared.exe'" | Where-Object { $_.CommandLine -match '--url[ =]+http://(localhost|127\.0\.0\.1):(3001|3100)' })) {
            Stop-ProcessTree ([int]$cloud.ProcessId)
        }
    }
    if ($roots.Count) { Write-Host 'Stopping previous OTCMS processes...' }
    foreach ($rootId in $roots) { Stop-ProcessTree $rootId }
    if ($roots.Count) { Start-Sleep -Seconds 2 }
    $occupied = @(Get-NetTCPConnection -State Listen -ErrorAction SilentlyContinue | Where-Object { $_.LocalPort -in $managedPorts })
    if ($occupied.Count) {
        $ports = ($occupied.LocalPort | Sort-Object -Unique) -join ', '
        throw "Port(s) $ports are already in use. Stop those applications before starting OTCMS."
    }
    New-Item -ItemType Directory -Path $logDir -Force | Out-Null

    Write-Host 'Starting Orthanc, OHIF and gateway (Docker)...'
    Push-Location $product
    try {
        & docker compose --env-file $localEnv -f docker-compose.final-db.yml up -d postgres
        if ($LASTEXITCODE -ne 0) { throw 'Could not start the final-db PostgreSQL container.' }
        & docker compose --env-file $localEnv -f compose.yaml up -d orthanc ohif imaging-gateway
    } finally { Pop-Location }
    if ($LASTEXITCODE -ne 0) { throw 'docker compose up failed.' }
    $databaseReady = $false
    for ($i = 0; $i -lt 45 -and -not $databaseReady; $i++) {
        $databaseReady = Test-NetConnection -ComputerName 127.0.0.1 -Port $databasePort `
            -InformationLevel Quiet -WarningAction SilentlyContinue
        if (-not $databaseReady) { Start-Sleep -Seconds 1 }
    }
    if (-not $databaseReady) { throw "PostgreSQL is not listening on 127.0.0.1:$databasePort." }
    $cfLog = Join-Path $logDir 'cloudflare.err'
    $publicUrl = $null
    if ($BackendInIntelliJ) {
        # Keep the current tunnel URL so it matches CORS settings already loaded by IntelliJ.
        $cloudProcesses = @(Get-CimInstance Win32_Process -Filter "Name='cloudflared.exe'" -ErrorAction SilentlyContinue |
            Where-Object { $_.CommandLine -match '--url[ =]+http://(localhost|127\.0\.0\.1):(3001|3100)' })
        if (-not $cloudProcesses.Count) {
            throw 'No existing Cloudflare tunnel to localhost:3001 was found. Start OTCMS normally once, then rerun with -BackendInIntelliJ.'
        }
        if (Test-Path -LiteralPath $cfLog) {
            $match = Select-String -Path $cfLog -Pattern 'https://[a-z0-9-]+\.trycloudflare\.com' | Select-Object -First 1
            if ($match) { $publicUrl = $match.Matches[0].Value }
        }
        if (-not $publicUrl) { throw "Could not read the existing tunnel URL from $cfLog." }
        if (-not (Wait-Http 'http://localhost:8080/v3/api-docs' 5)) {
            throw 'Backend in IntelliJ is not responding on port 8080. Start its Spring Boot run configuration first.'
        }
    } else {
        Write-Host 'Starting Cloudflare tunnel...'
        $cloudProcess = Start-Process -FilePath $cloudflared -ArgumentList @('tunnel', '--no-autoupdate', '--url', 'http://localhost:3001') `
            -WindowStyle Hidden -RedirectStandardOutput (Join-Path $logDir 'cloudflare.log') `
            -RedirectStandardError $cfLog -PassThru
        $started.Add($cloudProcess.Id)
        for ($i = 0; $i -lt 45 -and -not $publicUrl; $i++) {
            Start-Sleep -Seconds 1
            if (Test-Path -LiteralPath $cfLog) {
                $match = Select-String -Path $cfLog -Pattern 'https://[a-z0-9-]+\.trycloudflare\.com' | Select-Object -First 1
                if ($match) { $publicUrl = $match.Matches[0].Value }
            }
        }
        if (-not $publicUrl) { throw "Cloudflare did not provide a URL. See $cfLog" }
    }

    $backend = @"
`$ErrorActionPreference = 'Stop'
foreach (`$line in Get-Content -LiteralPath '$localEnv' -Encoding UTF8) {
    if (`$line -match '^([A-Z_][A-Z0-9_]*)=(.*)$') {
        [Environment]::SetEnvironmentVariable(`$matches[1], `$matches[2], 'Process')
    }
}
`$env:SPRING_PROFILES_ACTIVE = ''
`$env:SPRING_DATASOURCE_URL = "jdbc:postgresql://127.0.0.1:$databasePort/otcms_dev"
`$env:SPRING_DATASOURCE_USERNAME = `$env:OTCMS_DB_USERNAME
`$env:SPRING_DATASOURCE_PASSWORD = `$env:OTCMS_DB_PASSWORD
`$env:SERVER_PORT = '8080'
`$origins = 'http://localhost:3000,http://127.0.0.1:3000,http://localhost:3001,http://127.0.0.1:3001,$publicUrl'
`$env:APP_AUTH_PATIENT_ORIGINS = `$origins
`$env:APP_AUTH_STAFF_ORIGINS = `$origins
`$env:APP_AUTH_COOKIE_SECURE = 'false'
`$env:SPRING_APPLICATION_JSON = '{"otcms":{"visit":{"care-step-room-capability":{"SCHEDULED_APPOINTMENT":"EXAMINATION"},"room-cleanup-buffer-minutes":{"EXAMINATION":0}}}}'
`$env:OTCMS_IMAGING_ENABLED = 'true'
`$env:OTCMS_IMAGING_MAX_UPLOAD_BYTES = '80000000'
`$env:OTCMS_IMAGING_MAX_UPLOAD_FILES = '128'
`$env:OTCMS_IMAGING_MAX_CONCURRENT_UPLOADS = '1'
`$env:OTCMS_IMAGING_ORTHANC_BASE_URI = 'http://localhost:8042'
`$env:OTCMS_IMAGING_ORTHANC_CONNECT_TIMEOUT = 'PT3S'
`$env:OTCMS_IMAGING_ORTHANC_RESPONSE_TIMEOUT = 'PT30S'
Set-Location '$product\backend'
& .\mvnw.cmd spring-boot:run
"@
    $frontend = @"
`$env:NEXT_BASE_PATH = '/otcms'
`$env:NEXT_PUBLIC_API_BASE_URL = ''
`$env:NEXT_PUBLIC_OHIF_VIEWER_PATH = '/viewer'
`$env:OTCMS_DEV_API_PROXY = 'false'
Set-Location '$product\frontend'
& npm.cmd run dev
"@
    if ($BackendInIntelliJ) {
        Write-Host 'Backend is running in IntelliJ; starting frontend only...'
    } else {
        Write-Host 'Starting backend and frontend...'
        $backendProcess = Start-LoggedPowerShell 'backend' "$product\backend" $backend
    }
    $frontendProcess = Start-LoggedPowerShell 'frontend' "$product\frontend" $frontend
    if (-not $BackendInIntelliJ) {
        Write-Host 'Waiting for backend...'
        if (-not (Wait-Http 'http://localhost:8080/v3/api-docs' 120 $backendProcess)) { throw "Backend did not start. See $logDir\backend.log and backend.err" }
    }
    Write-Host 'Waiting for frontend...'
    if (-not (Wait-Http 'http://localhost:3001/otcms/login' 90 $frontendProcess)) { throw "Frontend did not start. See $logDir\frontend.log and frontend.err" }
    Write-Host 'Checking Cloudflare...'
    if (-not (Wait-Http "$publicUrl/otcms/login" 30)) { throw "Cloudflare URL did not respond. See $cfLog" }

    Write-Host ''
    Write-Host 'OTCMS is ready.' -ForegroundColor Green
    Write-Host 'Database: otcms_dev'
    if ($BackendInIntelliJ) { Write-Host 'Backend:  IntelliJ on port 8080' }
    Write-Host 'Local:    http://localhost:3001/otcms/login'
    Write-Host "Public:   $publicUrl/otcms/login"
    Write-Host "Logs:     $logDir"
    Write-Host 'Keep the public URL private. It changes whenever the tunnel restarts.'
} catch {
    foreach ($processId in $started) { Stop-ProcessTree $processId }
    Write-Host "Could not start OTCMS: $($_.Exception.Message)" -ForegroundColor Red
    Write-Host "Logs: $logDir"
    exit 1
}
