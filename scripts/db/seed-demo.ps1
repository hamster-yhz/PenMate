param(
    [string]$HostName = $(if ($env:DB_HOST) { $env:DB_HOST } else { "localhost" }),
    [int]$Port = $(if ($env:DB_PORT) { [int]$env:DB_PORT } else { 5432 }),
    [string]$Database = $(if ($env:DB_NAME) { $env:DB_NAME } else { "penmate" }),
    [string]$Username = $(if ($env:DB_USER) { $env:DB_USER } else { "postgres" }),
    [string]$Password = $(if ($env:DB_PASS) { $env:DB_PASS } else { "postgres" }),
    [string]$Psql = $(if ($env:PSQL_BIN) { $env:PSQL_BIN } else { "psql" }),
    [switch]$AllowRemote
)

$ErrorActionPreference = "Stop"
$localHosts = @("localhost", "127.0.0.1", "::1")
$remoteOverride = $env:PENMATE_ALLOW_REMOTE_DB -eq "true"
if (-not $AllowRemote -and -not $remoteOverride -and $HostName -notin $localHosts) {
    throw "Refusing to seed non-local PostgreSQL host '$HostName'. Pass -AllowRemote to confirm."
}

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..\..")
$seedDirectory = Join-Path $repoRoot "penmate-backend\src\test\resources\db\cases\seed"
$env:PGPASSWORD = $Password

try {
    Get-ChildItem -LiteralPath $seedDirectory -Filter "*.sql" | Sort-Object Name | ForEach-Object {
        Write-Host "Loading $($_.Name)"
        & $Psql -v ON_ERROR_STOP=1 -h $HostName -p $Port -U $Username -d $Database -f $_.FullName
        if ($LASTEXITCODE -ne 0) { throw "psql failed for $($_.Name)" }
    }
} finally {
    Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue
}
