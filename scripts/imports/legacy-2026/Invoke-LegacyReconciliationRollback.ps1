[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$RunId,

    [Parameter(Mandatory = $true)]
    [string]$WorkDirectory,

    [Parameter(Mandatory = $true)]
    [string]$PostgresJdbcJar,

    [string]$ExpectedDatabase = 'sigep_prod',

    [string]$NodeExecutable = 'node',

    [string]$JavaExecutable = 'java',

    [string]$JavacExecutable = 'javac',

    [switch]$Execute,

    [switch]$KeepGeneratedSql
)

$ErrorActionPreference = 'Stop'
$scriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$workRoot = [System.IO.Path]::GetFullPath($WorkDirectory)
$jdbcJar = (Resolve-Path -LiteralPath $PostgresJdbcJar).Path
[System.IO.Directory]::CreateDirectory($workRoot) | Out-Null

$generatedSql = Join-Path $workRoot "$RunId.rollback.generated.sql"
if (Test-Path -LiteralPath $generatedSql) {
    throw "Refusing to overwrite generated rollback SQL: $generatedSql"
}

& $NodeExecutable (Join-Path $scriptRoot 'prepare-legacy-reconciliation-rollback.mjs') `
    --output $generatedSql `
    --expected-database $ExpectedDatabase `
    --run-id $RunId
if ($LASTEXITCODE -ne 0) { throw 'Legacy reconciliation rollback preparation failed' }

$sqlHash = (Get-FileHash -LiteralPath $generatedSql -Algorithm SHA256).Hash.ToLowerInvariant()
Write-Host "Generated rollback SQL SHA-256: $sqlHash"

if (-not $Execute) {
    Write-Host 'Rollback preparation completed. No database write was attempted.'
    return
}

foreach ($name in 'DATABASE_URL', 'DATABASE_USERNAME', 'DATABASE_PASSWORD') {
    $value = [Environment]::GetEnvironmentVariable($name, 'Process')
    if ([string]::IsNullOrWhiteSpace($value)) {
        throw "Missing required process environment variable: $name"
    }
}

$runnerOutput = Join-Path $workRoot 'jdbc-reconciliation-rollback-runner'
[System.IO.Directory]::CreateDirectory($runnerOutput) | Out-Null
& $JavacExecutable -d $runnerOutput (Join-Path $scriptRoot 'JdbcSqlScriptRunner.java')
if ($LASTEXITCODE -ne 0) { throw 'JDBC script runner compilation failed' }

$classPath = "$runnerOutput$([System.IO.Path]::PathSeparator)$jdbcJar"
& $JavaExecutable -cp $classPath JdbcSqlScriptRunner $generatedSql
if ($LASTEXITCODE -ne 0) { throw 'Legacy reconciliation rollback failed and was rolled back' }

if (-not $KeepGeneratedSql) {
    Remove-Item -LiteralPath $generatedSql -Force
    Write-Host 'Removed the generated rollback SQL.'
}
