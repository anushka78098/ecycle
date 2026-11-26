$ErrorActionPreference = 'Stop'
$repositoryRoot = Split-Path -Parent $PSScriptRoot
$envFile = Join-Path $repositoryRoot '.env'
$frontendVariables = @(
    'ECYCLE_API',
    'ECYCLE_TILE_URL',
    'ECYCLE_GOOGLE_CLIENT_ID',
    'ECYCLE_GOOGLE_CLIENT_SECRET'
)

if (Test-Path -LiteralPath $envFile) {
    foreach ($line in Get-Content -LiteralPath $envFile) {
        if ($line -match '^\s*(?:export\s+)?([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*)\s*$') {
            $name = $matches[1]
            if ($frontendVariables -contains $name -and
                [string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($name, 'Process'))) {
                $value = $matches[2].Trim()
                if ($value.Length -ge 2 -and
                    (($value[0] -eq '"' -and $value[$value.Length - 1] -eq '"') -or
                     ($value[0] -eq "'" -and $value[$value.Length - 1] -eq "'"))) {
                    $value = $value.Substring(1, $value.Length - 2)
                }
                [Environment]::SetEnvironmentVariable($name, $value, 'Process')
            }
        }
    }
}

Set-Location (Join-Path $repositoryRoot 'frontend')
& mvn -q org.openjfx:javafx-maven-plugin:0.0.8:run
exit $LASTEXITCODE
