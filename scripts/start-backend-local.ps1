$ErrorActionPreference = 'Stop'

# Chạy backend với DB Postgres local (otcms_dev) và profile demo, đọc cấu hình từ backend/.env.dev
# (đã bị Git bỏ qua). Không dùng backend/.env nên không chạm Supabase.
# Biến môi trường tiến trình có ưu tiên cao hơn backend/.env mà Spring vẫn import.
$repositoryRoot = Split-Path -Parent $PSScriptRoot
$backendDirectory = Join-Path $repositoryRoot 'backend'
$environmentFile = Join-Path $backendDirectory '.env.dev'

if (-not (Test-Path -LiteralPath $environmentFile)) {
    throw 'Chưa có backend/.env.dev.'
}

# -Encoding UTF8: Windows PowerShell 5.1 đọc file không BOM theo ANSI, làm hỏng chữ có dấu (APP_DEMO_FULL_NAME).
foreach ($line in Get-Content -LiteralPath $environmentFile -Encoding UTF8) {
    $trimmedLine = $line.Trim()
    if ($trimmedLine.Length -eq 0 -or $trimmedLine.StartsWith('#')) { continue }
    $separator = $line.IndexOf('=')
    if ($separator -lt 1) { throw 'Dòng cấu hình môi trường không hợp lệ.' }
    [Environment]::SetEnvironmentVariable($line.Substring(0, $separator).Trim(), $line.Substring($separator + 1), 'Process')
}

Push-Location $backendDirectory
try { & .\mvnw.cmd spring-boot:run } finally { Pop-Location }
