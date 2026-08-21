[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$SourceDirectory,

    [Parameter(Mandatory = $true)]
    [string]$WorkDirectory,

    [Parameter(Mandatory = $true)]
    [string]$ArtifactToolRoot,

    [Parameter(Mandatory = $true)]
    [string]$PostgresJdbcJar,

    [string]$ExpectedDatabase = 'sigep_prod',

    [string]$RunId = "LEGACY-2026-$([DateTime]::UtcNow.ToString('yyyyMMddTHHmmssZ'))",

    [string]$NodeExecutable = 'node',

    [string]$JavaExecutable = 'java',

    [string]$JavacExecutable = 'javac',

    [switch]$Execute,

    [switch]$KeepGeneratedSql
)

$ErrorActionPreference = 'Stop'
$scriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$backendRoot = (Resolve-Path -LiteralPath (Join-Path $scriptRoot '..\..\..')).Path
$sourceRoot = (Resolve-Path -LiteralPath $SourceDirectory).Path
$workRoot = [System.IO.Path]::GetFullPath($WorkDirectory)
$artifactRoot = (Resolve-Path -LiteralPath $ArtifactToolRoot).Path
$jdbcJar = (Resolve-Path -LiteralPath $PostgresJdbcJar).Path
[System.IO.Directory]::CreateDirectory($workRoot) | Out-Null

$convertedGuardian = Join-Path $workRoot '4.Listado de responsables.converted.xlsx'
$convertedStudent = Join-Path $workRoot '5.Listado de alumnos.converted.xlsx'
if (-not (Test-Path -LiteralPath $convertedGuardian) -or -not (Test-Path -LiteralPath $convertedStudent)) {
    & (Join-Path $scriptRoot 'Convert-LegacyXls.ps1') -SourceDirectory $sourceRoot -OutputDirectory $workRoot
    if ($LASTEXITCODE -ne 0) { throw 'Legacy XLS conversion failed' }
}

$gitSafeRoot = $backendRoot.Replace('\', '/')
$gitCommit = git -c "safe.directory=$gitSafeRoot" -C $backendRoot rev-parse HEAD
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($gitCommit)) {
    throw 'Cannot resolve backend git commit'
}

$generatedSql = Join-Path $workRoot "$RunId.generated.sql"
if (Test-Path -LiteralPath $generatedSql) {
    throw "Refusing to overwrite generated import SQL: $generatedSql"
}

& $NodeExecutable (Join-Path $scriptRoot 'prepare-legacy-2026.mjs') `
    --source-dir $sourceRoot `
    --converted-dir $workRoot `
    --output $generatedSql `
    --artifact-tool-root $artifactRoot `
    --expected-database $ExpectedDatabase `
    --git-commit $gitCommit.Trim() `
    --run-id $RunId
if ($LASTEXITCODE -ne 0) { throw 'Legacy import preparation failed' }

$sqlHash = (Get-FileHash -LiteralPath $generatedSql -Algorithm SHA256).Hash.ToLowerInvariant()
Write-Host "Generated import SQL SHA-256: $sqlHash"

if (-not $Execute) {
    Write-Host 'Preparation completed. No database write was attempted.'
    return
}

foreach ($name in 'DATABASE_URL', 'DATABASE_USERNAME', 'DATABASE_PASSWORD') {
    $value = [Environment]::GetEnvironmentVariable($name, 'Process')
    if ([string]::IsNullOrWhiteSpace($value)) {
        throw "Missing required process environment variable: $name"
    }
}

$runnerOutput = Join-Path $workRoot 'jdbc-runner'
[System.IO.Directory]::CreateDirectory($runnerOutput) | Out-Null
& $JavacExecutable -d $runnerOutput (Join-Path $scriptRoot 'JdbcSqlScriptRunner.java')
if ($LASTEXITCODE -ne 0) { throw 'JDBC script runner compilation failed' }

$classPath = "$runnerOutput$([System.IO.Path]::PathSeparator)$jdbcJar"
& $JavaExecutable -cp $classPath JdbcSqlScriptRunner $generatedSql
if ($LASTEXITCODE -ne 0) { throw 'Legacy database import failed and was rolled back' }

if (-not $KeepGeneratedSql) {
    Remove-Item -LiteralPath $generatedSql -Force
    Write-Host 'Removed the generated SQL containing identified source data.'
}
