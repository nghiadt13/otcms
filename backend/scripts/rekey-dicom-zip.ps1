[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string] $InputZip,

    [Parameter(Mandatory = $true)]
    [string] $OutputZip,

    [string] $RequestId,

    [string] $BackendBaseUrl = $env:OTCMS_API_BASE_URL,

    [string] $AccessToken,

    # Manual identity fields are retained for offline archive preparation only.
    [ValidateLength(1, 64)]
    [string] $PatientId,

    [ValidateLength(1, 64)]
    [string] $PatientIssuer,

    [ValidateLength(1, 16)]
    [string] $AccessionNumber,

    [ValidateLength(1, 64)]
    [string] $AccessionIssuerLocalNamespace,

    [string] $PatientName = '',

    [string] $PatientBirthDate = '',

    [ValidateSet('', 'M', 'F', 'O')]
    [string] $PatientSex = ''
)

# Rewrites a demo DICOM ZIP using the identity assigned to an authorized imaging order.
# For online generation, the API base URL is read from frontend/.env.local when not set in the environment.
# If no explicit access token is supplied, the script signs in to the target backend using
# a runtime credential prompt and immediately uses the fresh session token.

$ErrorActionPreference = 'Stop'

$backendDirectory = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$repositoryDirectory = [System.IO.Path]::GetFullPath((Join-Path $backendDirectory '..'))
$mavenWrapper = Join-Path $backendDirectory 'mvnw.cmd'
$javaSource = Join-Path $PSScriptRoot 'RekeyDicomZip.java'
$resolvedInput = (Resolve-Path -LiteralPath $InputZip).Path
$resolvedOutput = [System.IO.Path]::GetFullPath($OutputZip)

$hasRequestId = -not [string]::IsNullOrWhiteSpace($RequestId)
if ($hasRequestId) {
    if ([string]::IsNullOrWhiteSpace($BackendBaseUrl)) {
        $frontendEnvPath = Join-Path $repositoryDirectory 'frontend/.env.local'
        if (Test-Path -LiteralPath $frontendEnvPath -PathType Leaf) {
            $apiBaseUrlLine = Get-Content -LiteralPath $frontendEnvPath |
                Where-Object { $_ -match '^\s*NEXT_PUBLIC_API_BASE_URL\s*=' } |
                Select-Object -First 1

            if ($apiBaseUrlLine -match '^\s*NEXT_PUBLIC_API_BASE_URL\s*=\s*(.*?)\s*$') {
                $BackendBaseUrl = $Matches[1].Trim()
                if ($BackendBaseUrl.Length -ge 2) {
                    $firstCharacter = $BackendBaseUrl[0]
                    $lastCharacter = $BackendBaseUrl[$BackendBaseUrl.Length - 1]
                    if (($firstCharacter -eq '"' -and $lastCharacter -eq '"') -or
                        ($firstCharacter -eq "'" -and $lastCharacter -eq "'")) {
                        $BackendBaseUrl = $BackendBaseUrl.Substring(1, $BackendBaseUrl.Length - 2)
                    }
                }
            }
        }

        if ([string]::IsNullOrWhiteSpace($BackendBaseUrl)) {
            $BackendBaseUrl = 'http://localhost:8080'
        }
    }
    if ([string]::IsNullOrWhiteSpace($AccessToken)) {
        $credential = Get-Credential -Message 'Sign in as the imaging technician used on the OTCMS tunnel.'
        if (-not $credential) {
            throw 'Imaging-technician sign-in was cancelled.'
        }

        $plainPassword = $null
        try {
            $plainPassword = $credential.GetNetworkCredential().Password
            $loginBody = @{
                username   = $credential.UserName
                password   = $plainPassword
                rememberMe = $false
            } | ConvertTo-Json -Compress
            $login = Invoke-RestMethod -Method Post `
                -Uri "$($BackendBaseUrl.TrimEnd('/'))/api/auth/staff/login" `
                -ContentType 'application/json' `
                -Body $loginBody
            $AccessToken = [string] $login.accessToken
        } finally {
            $plainPassword = $null
            $loginBody = $null
            $credential = $null
        }
    }
    if ([string]::IsNullOrWhiteSpace($AccessToken)) {
        throw 'The OTCMS staff login did not return an access token.'
    }

    # An explicitly supplied token may include quotes or wrapped lines.
    $tokenText = [System.Text.RegularExpressions.Regex]::Replace($AccessToken, '\s+', '')
    $tokenMatch = [System.Text.RegularExpressions.Regex]::Match(
        $tokenText,
        '[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+'
    )
    if (-not $tokenMatch.Success) {
        throw 'AccessToken must contain a JWT. Copy the accessToken value from the latest login response.'
    }
    $AccessToken = $tokenMatch.Value

    $baseUrl = $BackendBaseUrl.TrimEnd('/')
    $identityUrl = "$baseUrl/api/v1/imaging/requests/$RequestId/dicom-identity"
    $identity = Invoke-RestMethod -Method Get -Uri $identityUrl -Headers @{ Authorization = "Bearer $AccessToken" }
    $PatientId = [string] $identity.patientId
    $PatientIssuer = [string] $identity.patientIssuer
    $AccessionNumber = [string] $identity.accessionNumber
    $AccessionIssuerLocalNamespace = [string] $identity.accessionIssuerLocalNamespace
} elseif (
    [string]::IsNullOrWhiteSpace($PatientId) -or
    [string]::IsNullOrWhiteSpace($PatientIssuer) -or
    [string]::IsNullOrWhiteSpace($AccessionNumber) -or
    [string]::IsNullOrWhiteSpace($AccessionIssuerLocalNamespace)
) {
    throw 'Supply RequestId for automatic identity lookup, or all four manual DICOM identity fields.'
}

if ([string]::Equals($resolvedInput, $resolvedOutput, [StringComparison]::OrdinalIgnoreCase)) {
    throw 'InputZip and OutputZip must be different files; the source archive is preserved.'
}
if (Test-Path -LiteralPath $resolvedOutput) {
    throw "Output already exists: $resolvedOutput"
}
if (-not (Test-Path -LiteralPath $mavenWrapper -PathType Leaf)) {
    throw "Maven wrapper was not found: $mavenWrapper"
}
if (-not (Test-Path -LiteralPath $javaSource -PathType Leaf)) {
    throw "DICOM re-key helper was not found: $javaSource"
}

$java = Get-Command 'java' -ErrorAction SilentlyContinue
$javac = Get-Command 'javac' -ErrorAction SilentlyContinue
if (-not $java -or -not $javac) {
    throw 'Java 21 (java and javac) must be available on PATH.'
}

$tempRoot = [System.IO.Path]::GetFullPath([System.IO.Path]::GetTempPath())
$workDirectory = Join-Path $tempRoot ("otcms-dicom-rekey-" + [guid]::NewGuid().ToString('N'))
[System.IO.Directory]::CreateDirectory($workDirectory) | Out-Null
$classpathFile = Join-Path $workDirectory 'runtime-classpath.txt'

try {
    Push-Location $backendDirectory
    try {
        & $mavenWrapper -q `
            '-Dmdep.includeScope=runtime' `
            "-Dmdep.outputFile=$classpathFile" `
            'org.apache.maven.plugins:maven-dependency-plugin:3.8.1:build-classpath'
        if ($LASTEXITCODE -ne 0) {
            throw 'Maven could not resolve the backend runtime classpath.'
        }
    } finally {
        Pop-Location
    }

    $runtimeClasspath = (Get-Content -LiteralPath $classpathFile -Raw).Trim()
    if ([string]::IsNullOrWhiteSpace($runtimeClasspath)) {
        throw 'Maven returned an empty backend runtime classpath.'
    }

    & $javac.Source -proc:none -encoding UTF-8 -cp $runtimeClasspath -d $workDirectory $javaSource
    if ($LASTEXITCODE -ne 0) {
        throw 'Could not compile the DICOM re-key helper.'
    }

    $helperClasspath = "$workDirectory;$runtimeClasspath"
    $optionalPatientName = if ([string]::IsNullOrWhiteSpace($PatientName)) { ' ' } else { $PatientName.Trim() }
    $optionalBirthDate = if ([string]::IsNullOrWhiteSpace($PatientBirthDate)) { ' ' } else { $PatientBirthDate.Trim() }
    $optionalSex = if ([string]::IsNullOrWhiteSpace($PatientSex)) { ' ' } else { $PatientSex.Trim() }
    & $java.Source -cp $helperClasspath 'RekeyDicomZip' `
        $resolvedInput `
        $resolvedOutput `
        $PatientId.Trim() `
        $PatientIssuer.Trim() `
        $AccessionNumber.Trim() `
        $AccessionIssuerLocalNamespace.Trim() `
        $optionalPatientName `
        $optionalBirthDate `
        $optionalSex
    if ($LASTEXITCODE -ne 0) {
        throw 'DICOM ZIP re-keying failed; the input archive was not modified.'
    }
    if ($hasRequestId) {
        Write-Host "DICOM identity loaded from assigned imaging request $RequestId."
    }
} finally {
    $resolvedTempRoot = [System.IO.Path]::GetFullPath($tempRoot).TrimEnd('\') + '\'
    $resolvedWorkDirectory = [System.IO.Path]::GetFullPath($workDirectory)
    if (-not $resolvedWorkDirectory.StartsWith($resolvedTempRoot, [StringComparison]::OrdinalIgnoreCase)) {
        throw 'Refusing to remove a temporary path outside the system temp directory.'
    }
    if (Test-Path -LiteralPath $resolvedWorkDirectory) {
        Remove-Item -LiteralPath $resolvedWorkDirectory -Recurse -Force
    }
}
