[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$WorkbookPath,

    [Parameter(Mandatory = $true)]
    [string]$WorkDirectory,

    [Parameter(Mandatory = $true)]
    [string]$ArtifactToolRoot,

    [Parameter(Mandatory = $true)]
    [string]$PostgresJdbcJar,

    [string]$OriginalImportRunId = 'LEGACY-2026-UAT-20260814A',

    [string]$ExpectedOriginalManifestSha256 = '5ae5afd0e2736370b62109ca8652ce256057dec653fae7c3df4da724bbd7daab',

    [string]$ExpectedDatabase = 'sigep_prod',

    [string]$RunId = "LEGACY-RECON-2026-$([DateTime]::UtcNow.ToString('yyyyMMddTHHmmssZ'))",

    [string]$NodeExecutable = 'node',

    [string]$JavaExecutable = 'java',

    [string]$JavacExecutable = 'javac',

    [switch]$Execute,

    [switch]$KeepGeneratedSql
)

$ErrorActionPreference = 'Stop'
$scriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$backendRoot = (Resolve-Path -LiteralPath (Join-Path $scriptRoot '..\..\..')).Path
$reconciliationWorkbook = (Resolve-Path -LiteralPath $WorkbookPath).Path
$workRoot = [System.IO.Path]::GetFullPath($WorkDirectory)
$artifactRoot = (Resolve-Path -LiteralPath $ArtifactToolRoot).Path
$jdbcJar = (Resolve-Path -LiteralPath $PostgresJdbcJar).Path
[System.IO.Directory]::CreateDirectory($workRoot) | Out-Null

$gitSafeRoot = $backendRoot.Replace('\', '/')
$gitCommit = git -c "safe.directory=$gitSafeRoot" -C $backendRoot rev-parse HEAD
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($gitCommit)) {
    throw 'Cannot resolve backend git commit'
}

$generatedSql = Join-Path $workRoot "$RunId.generated.sql"
if (Test-Path -LiteralPath $generatedSql) {
    throw "Refusing to overwrite generated reconciliation SQL: $generatedSql"
}

$preparationOutput = & $NodeExecutable (Join-Path $scriptRoot 'prepare-legacy-reconciliation.mjs') `
    --workbook $reconciliationWorkbook `
    --output $generatedSql `
    --artifact-tool-root $artifactRoot `
    --expected-database $ExpectedDatabase `
    --original-import-run-id $OriginalImportRunId `
    --expected-original-manifest-sha256 $ExpectedOriginalManifestSha256 `
    --git-commit $gitCommit.Trim() `
    --run-id $RunId
if ($LASTEXITCODE -ne 0) { throw 'Legacy reconciliation preparation failed' }

$preparationJson = $preparationOutput -join [Environment]::NewLine
$preparation = $preparationJson | ConvertFrom-Json
Write-Host $preparationJson
$sqlHash = (Get-FileHash -LiteralPath $generatedSql -Algorithm SHA256).Hash.ToLowerInvariant()
Write-Host "Generated reconciliation SQL SHA-256: $sqlHash"

if (-not $Execute) {
    Write-Host 'Preparation completed. No database write was attempted.'
    return
}

if ($preparation.summary.confirmedTotal -eq 0) {
    throw 'Refusing database execution because the workbook contains no CONFIRMADO rows'
}

foreach ($name in 'DATABASE_URL', 'DATABASE_USERNAME', 'DATABASE_PASSWORD') {
    $value = [Environment]::GetEnvironmentVariable($name, 'Process')
    if ([string]::IsNullOrWhiteSpace($value)) {
        throw "Missing required process environment variable: $name"
    }
}

$runnerOutput = Join-Path $workRoot 'jdbc-reconciliation-runner'
[System.IO.Directory]::CreateDirectory($runnerOutput) | Out-Null
& $JavacExecutable -d $runnerOutput (Join-Path $scriptRoot 'JdbcSqlScriptRunner.java')
if ($LASTEXITCODE -ne 0) { throw 'JDBC script runner compilation failed' }

$classPath = "$runnerOutput$([System.IO.Path]::PathSeparator)$jdbcJar"
& $JavaExecutable -cp $classPath JdbcSqlScriptRunner $generatedSql
if ($LASTEXITCODE -ne 0) { throw 'Legacy reconciliation failed and was rolled back'
}

if (-not $KeepGeneratedSql) {
    Remove-Item -LiteralPath $generatedSql -Force
    Write-Host 'Removed the generated SQL containing identified reconciliation data.'
}
