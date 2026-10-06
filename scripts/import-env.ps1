# Dot-source this file to import only the supported variables; values are never printed.
$ErrorActionPreference = 'Stop'
$envFilePath = Join-Path $PSScriptRoot '../.env'
if (-not (Test-Path -LiteralPath $envFilePath)) { throw 'Missing .env' }
foreach ($envLine in Get-Content -LiteralPath $envFilePath) {
    if ($envLine -match '^\s*(OPENAI_API_KEY|OPENAI_MODEL|OPENAI_PROJECT|OPENAI_ORGANIZATION)\s*=\s*(.*?)\s*$') {
        $envName = $Matches[1]
        $envValue = $Matches[2]
        if (($envValue.StartsWith('"') -and $envValue.EndsWith('"')) -or ($envValue.StartsWith("'") -and $envValue.EndsWith("'"))) { $envValue = $envValue.Substring(1, $envValue.Length - 2) }
        [Environment]::SetEnvironmentVariable($envName, $envValue, 'Process')
    }
}
