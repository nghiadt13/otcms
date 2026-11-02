$ErrorActionPreference = 'Stop'

# Chạy backend và frontend, mỗi service một cửa sổ PowerShell riêng để log tách biệt.
# Đóng cửa sổ nào thì dừng service đó. Không bật Docker (Orthanc/OHIF) — chạy riêng bằng `docker compose up -d`.
$repositoryRoot = Split-Path -Parent $PSScriptRoot
$frontendDirectory = Join-Path $repositoryRoot 'frontend'
$backendScript = Join-Path $PSScriptRoot 'start-backend.ps1'
$shell = (Get-Process -Id $PID).Path

if (-not (Test-Path -LiteralPath (Join-Path $frontendDirectory 'node_modules'))) {
    throw 'Chưa có frontend/node_modules. Chạy `npm ci` trong thư mục frontend trước.'
}

Start-Process -FilePath $shell -ArgumentList @('-NoExit', '-File', $backendScript)
Start-Process -FilePath $shell -WorkingDirectory $frontendDirectory -ArgumentList @('-NoExit', '-Command', 'npm run dev')

Write-Host 'Backend:  http://localhost:8080  (swagger: /swagger-ui/index.html)'
Write-Host 'Frontend: http://localhost:3000'
