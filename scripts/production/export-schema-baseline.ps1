param(
    [string]$OutputPath = "scripts/production/schema-baseline-v27.sql"
)

$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($env:QA_DATABASE_URL)) {
    throw "Set QA_DATABASE_URL to the direct Neon libpq connection string before exporting."
}

$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot "../..")).Path
$resolvedOutput = [System.IO.Path]::GetFullPath((Join-Path $repositoryRoot $OutputPath))
$allowedDirectory = [System.IO.Path]::GetFullPath($PSScriptRoot)

if (-not $resolvedOutput.StartsWith($allowedDirectory, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw "The schema baseline must be written under scripts/production."
}
if (Test-Path -LiteralPath $resolvedOutput) {
    throw "Refusing to overwrite existing baseline: $resolvedOutput"
}

$outputDirectory = Split-Path -Parent $resolvedOutput
$outputFileName = Split-Path -Leaf $resolvedOutput
$pgDump = if (-not [string]::IsNullOrWhiteSpace($env:PG_BIN_DIR)) {
    Join-Path $env:PG_BIN_DIR "pg_dump.exe"
} else {
    (Get-Command pg_dump -ErrorAction SilentlyContinue).Source
}

if ($pgDump -and (Test-Path -LiteralPath $pgDump)) {
    $previousPgDatabase = $env:PGDATABASE
    try {
        $env:PGDATABASE = $env:QA_DATABASE_URL
        & $pgDump --schema-only --no-owner --no-privileges --file=$resolvedOutput
    } finally {
        $env:PGDATABASE = $previousPgDatabase
    }
} else {
    $dumpCommand = 'exec pg_dump --dbname="$QA_DATABASE_URL" --schema-only --no-owner --no-privileges --file="/output/' + $outputFileName + '"'
    docker run --rm `
        --env QA_DATABASE_URL `
        --mount "type=bind,source=$outputDirectory,destination=/output" `
        postgres:18 `
        sh -c $dumpCommand
}

if ($LASTEXITCODE -ne 0) {
    throw "pg_dump failed with exit code $LASTEXITCODE."
}

$forbiddenPatterns = @(
    '^COPY ',
    '^INSERT INTO ',
    'OWNER TO',
    'PASSWORD\s+'
)
$matches = Select-String -LiteralPath $resolvedOutput -Pattern $forbiddenPatterns -CaseSensitive:$false
if ($matches) {
    throw "Baseline validation failed: data, ownership, or credentials were detected."
}

$hash = Get-FileHash -LiteralPath $resolvedOutput -Algorithm SHA256
Write-Output "Created schema-only baseline: $resolvedOutput"
Write-Output "SHA-256: $($hash.Hash)"
