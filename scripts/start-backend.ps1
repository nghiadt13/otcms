$ErrorActionPreference = 'Stop'

$repositoryRoot = Split-Path -Parent $PSScriptRoot
$backendDirectory = Join-Path $repositoryRoot 'backend'
$environmentFile = Join-Path $backendDirectory '.env'

if (-not (Test-Path -LiteralPath $environmentFile)) {
    throw 'Chưa có backend/.env. Sao chép backend/.env.example và điền cấu hình Supabase.'
}

$previousValues = @{}

try {
    foreach ($line in Get-Content -LiteralPath $environmentFile) {
        $trimmedLine = $line.Trim()
        if ($trimmedLine.Length -eq 0 -or $trimmedLine.StartsWith('#')) { continue }

        $separator = $line.IndexOf('=')
        if ($separator -lt 1) { throw 'Dòng cấu hình môi trường không hợp lệ.' }
        $key = $line.Substring(0, $separator).Trim()
        if ($key -notmatch '^[A-Z_][A-Z0-9_]*$') { throw 'Tên biến môi trường không hợp lệ.' }

        $previousValues[$key] = [Environment]::GetEnvironmentVariable($key, 'Process')
        [Environment]::SetEnvironmentVariable($key, $line.Substring($separator + 1), 'Process')
    }

    Push-Location $backendDirectory
    try {
        & .\mvnw.cmd spring-boot:run
    } finally {
        Pop-Location
    }
} finally {
    foreach ($key in $previousValues.Keys) {
        [Environment]::SetEnvironmentVariable($key, $previousValues[$key], 'Process')
    }
}
