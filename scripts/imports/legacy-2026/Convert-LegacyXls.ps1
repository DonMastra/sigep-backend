[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$SourceDirectory,

    [Parameter(Mandatory = $true)]
    [string]$OutputDirectory
)

$ErrorActionPreference = 'Stop'
$sourceRoot = (Resolve-Path -LiteralPath $SourceDirectory).Path
$outputRoot = [System.IO.Path]::GetFullPath($OutputDirectory)
[System.IO.Directory]::CreateDirectory($outputRoot) | Out-Null

$conversions = @(
    @{
        Source = '4.Listado de responsables.xls'
        Target = '4.Listado de responsables.converted.xlsx'
    },
    @{
        Source = '5.Listado de alumnos.xls'
        Target = '5.Listado de alumnos.converted.xlsx'
    }
)

foreach ($conversion in $conversions) {
    $sourcePath = Join-Path $sourceRoot $conversion.Source
    $targetPath = Join-Path $outputRoot $conversion.Target
    if (-not (Test-Path -LiteralPath $sourcePath -PathType Leaf)) {
        throw "Missing source workbook: $($conversion.Source)"
    }
    if (Test-Path -LiteralPath $targetPath) {
        throw "Refusing to overwrite converted workbook: $($conversion.Target)"
    }
}

$excel = $null
try {
    $excel = New-Object -ComObject Excel.Application
    $excel.Visible = $false
    $excel.DisplayAlerts = $false

    foreach ($conversion in $conversions) {
        $sourcePath = Join-Path $sourceRoot $conversion.Source
        $targetPath = Join-Path $outputRoot $conversion.Target
        $workbook = $null
        try {
            $workbook = $excel.Workbooks.Open($sourcePath, 0, $true)
            $workbook.SaveAs($targetPath, 51)
        }
        finally {
            if ($null -ne $workbook) {
                $workbook.Close($false)
                [System.Runtime.InteropServices.Marshal]::ReleaseComObject($workbook) | Out-Null
            }
        }
    }
}
finally {
    if ($null -ne $excel) {
        $excel.Quit()
        [System.Runtime.InteropServices.Marshal]::ReleaseComObject($excel) | Out-Null
    }
    [GC]::Collect()
    [GC]::WaitForPendingFinalizers()
}

$conversions | ForEach-Object {
    $targetPath = Join-Path $outputRoot $_.Target
    $hash = Get-FileHash -LiteralPath $targetPath -Algorithm SHA256
    [pscustomobject]@{
        File = $_.Target
        Bytes = (Get-Item -LiteralPath $targetPath).Length
        Sha256 = $hash.Hash.ToLowerInvariant()
    }
} | Format-Table -AutoSize
