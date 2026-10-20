# Negative tests for the publication boundary; operates only on ignored build outputs.
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
Add-Type -AssemblyName System.IO.Compression.FileSystem
& "$PSScriptRoot/prepare-distribution.ps1"
$scratch = Join-Path $root ('.cache/distribution-tests-' + [Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Force $scratch | Out-Null
$pack = Join-Path $root 'dist/fartmc-sounds.zip'
$jar = (Get-ChildItem "$root/target" -Filter 'fartplugin-*.jar' | Select-Object -First 1).FullName
Copy-Item $pack "$scratch/pack.zip"
Copy-Item $jar "$scratch/plugin.jar"
function Assert-Rejected([string]$path, [string]$backup, [string]$name, [string]$expected, [scriptblock]$mutate) {
    try {
        $archive = [IO.Compression.ZipFile]::Open($path, [IO.Compression.ZipArchiveMode]::Update)
        try { & $mutate $archive } finally { $archive.Dispose() }
        $message = $null
        try { & "$PSScriptRoot/prepare-distribution.ps1" } catch { $message = $_.Exception.Message }
        if (!$message -or $message -notlike $expected) { throw "Negative test failed: $name (got: $message)" }
        Write-Output "REJECTION PASS: $name"
    } finally { Copy-Item $backup $path -Force }
}
Assert-Rejected $pack "$scratch/pack.zip" 'unreviewed sound file' 'Unexpected*pack entries*' {
    param($archive)
    [void]$archive.CreateEntry('assets/fartmc/sounds/unreviewed.ogg')
}
Assert-Rejected $pack "$scratch/pack.zip" 'missing license' 'Missing ZIP entry: LICENSE.txt' {
    param($archive)
    $archive.GetEntry('LICENSE.txt').Delete()
}
Assert-Rejected $pack "$scratch/pack.zip" 'changed audio bytes' 'Audio checksum mismatch:*' {
    param($archive)
    $entry = $archive.GetEntry('assets/fartmc/sounds/classic.ogg')
    $stream = $entry.Open()
    try { $stream.SetLength(0); $stream.WriteByte(0) } finally { $stream.Dispose() }
}
Assert-Rejected $pack "$scratch/pack.zip" 'local pack provenance' 'Pack is not the approved*' {
    param($archive)
    $archive.GetEntry('SOURCES.json').Delete()
    $entry = $archive.CreateEntry('SOURCES.json')
    $writer = [IO.StreamWriter]::new($entry.Open())
    try { $writer.Write('{"distribution":"local-myinstants","license":"unknown"}') } finally { $writer.Dispose() }
}
Assert-Rejected $jar "$scratch/plugin.jar" 'original/foreign class' 'Unexpected plugin entry*' {
    param($archive)
    [void]$archive.CreateEntry('com/denji/fartplugin/Original.class')
}
# Restore a valid distributable set after all corruption tests.
& "$PSScriptRoot/prepare-distribution.ps1"
Write-Output 'All five negative publication tests passed.'
