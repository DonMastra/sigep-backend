param(
    [string]$BaselinePath = "scripts/production/schema-baseline-v27.sql",
    [switch]$ApplyRuntimeGrants
)

$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($env:TARGET_DATABASE_URL)) {
    throw "Set TARGET_DATABASE_URL to the target branch owner connection string before restoring."
}

$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot "../..")).Path
$resolvedBaseline = [System.IO.Path]::GetFullPath((Join-Path $repositoryRoot $BaselinePath))
$productionDirectory = [System.IO.Path]::GetFullPath($PSScriptRoot)

if (-not $resolvedBaseline.StartsWith($productionDirectory, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw "The baseline must be read from scripts/production."
}
if (-not (Test-Path -LiteralPath $resolvedBaseline)) {
    throw "Baseline not found: $resolvedBaseline"
}

$baselineFileName = Split-Path -Leaf $resolvedBaseline
$psql = if (-not [string]::IsNullOrWhiteSpace($env:PG_BIN_DIR)) {
    Join-Path $env:PG_BIN_DIR "psql.exe"
} else {
    (Get-Command psql -ErrorAction SilentlyContinue).Source
}

if ($psql -and (Test-Path -LiteralPath $psql)) {
    $previousPgDatabase = $env:PGDATABASE
    try {
        $env:PGDATABASE = $env:TARGET_DATABASE_URL
        & $psql --no-psqlrc --set=ON_ERROR_STOP=1 --file=$resolvedBaseline
    } finally {
        $env:PGDATABASE = $previousPgDatabase
    }
} else {
    $restoreCommand = 'exec psql --dbname="$TARGET_DATABASE_URL" --no-psqlrc --set=ON_ERROR_STOP=1 --file="/work/' + $baselineFileName + '"'
    docker run --rm `
        --env TARGET_DATABASE_URL `
        --mount "type=bind,source=$productionDirectory,destination=/work,readonly" `
        postgres:18 `
        sh -c $restoreCommand
}

if ($LASTEXITCODE -ne 0) {
    throw "Schema restore failed with exit code $LASTEXITCODE."
}

if ($ApplyRuntimeGrants) {
    if ($psql -and (Test-Path -LiteralPath $psql)) {
        $previousPgDatabase = $env:PGDATABASE
        try {
            $env:PGDATABASE = $env:TARGET_DATABASE_URL
            & $psql --no-psqlrc --set=ON_ERROR_STOP=1 --file=(Join-Path $productionDirectory "apply-runtime-grants.sql")
        } finally {
            $env:PGDATABASE = $previousPgDatabase
        }
    } else {
        docker run --rm `
            --env TARGET_DATABASE_URL `
            --mount "type=bind,source=$productionDirectory,destination=/work,readonly" `
            postgres:18 `
            sh -c 'exec psql --dbname="$TARGET_DATABASE_URL" --no-psqlrc --set=ON_ERROR_STOP=1 --file=/work/apply-runtime-grants.sql'
    }

    if ($LASTEXITCODE -ne 0) {
        throw "Runtime grants failed with exit code $LASTEXITCODE."
    }
}

Write-Output "Schema baseline restored successfully."
