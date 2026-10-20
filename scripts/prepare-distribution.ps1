param()
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
Push-Location $root
try {
    # A source ZIP must correspond to the exact committed build inputs.
    & git diff --quiet HEAD
    if ($LASTEXITCODE -ne 0) { throw 'Commit source changes before preparing a distribution.' }
    $pending = & git status --porcelain --untracked-files=all
    if ($LASTEXITCODE -ne 0 -or $pending) { throw 'Distribution requires a clean worktree, including new source files.' }
    $tracked = & git ls-files
    if ($LASTEXITCODE -ne 0) { throw 'Cannot enumerate source files.' }
    if ($tracked | Where-Object { $_ -match '(^|/)(\.cache|runtime-[^/]+|target|dist)/|\.(jar|mp3|ogg|wav|exe|zip)$' }) {
        throw 'Downloaded/generated binaries or runtime files are tracked.'
    }
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $jars = @(Get-ChildItem target -Filter 'fartplugin-*.jar')
    if ($jars.Count -ne 1) { throw 'Expected exactly one freshly built plugin JAR.' }
    $jar = [IO.Compression.ZipFile]::OpenRead($jars[0].FullName)
    try {
        $entries = @($jar.Entries | Where-Object { !$_.FullName.EndsWith('/') })
        foreach ($entry in $entries) {
            if ($entry.FullName -notmatch '^(io/github/ashtray01/fartmc/[^/]+\.class|META-INF/(MANIFEST\.MF|LICENSE|NOTICE\.md|maven/io\.github\.ashtray01/fartplugin/pom\.(xml|properties))|config\.yml|plugin\.yml)$') {
                throw "Unexpected plugin entry (possible bundled dependency): $($entry.FullName)"
            }
        }
        foreach ($required in @('META-INF/LICENSE','META-INF/NOTICE.md','io/github/ashtray01/fartmc/FartPlugin.class')) {
            if (!$jar.GetEntry($required)) { throw "Missing JAR entry: $required" }
        }
    } finally { $jar.Dispose() }

    $packPath = Join-Path $root 'dist/fartmc-sounds.zip'
    $pack = [IO.Compression.ZipFile]::OpenRead($packPath)
    try {
        function Read-ZipText($archive, [string]$name) {
            $entry = $archive.GetEntry($name)
            if (!$entry) { throw "Missing ZIP entry: $name" }
            $reader = [IO.StreamReader]::new($entry.Open())
            try { return $reader.ReadToEnd() } finally { $reader.Dispose() }
        }
        $manifest = Read-ZipText $pack 'SOURCES.json' | ConvertFrom-Json
        if ($manifest.distribution -ne 'public-original' -or $manifest.license -ne 'CC0-1.0' -or $manifest.generator -ne 'scripts/SynthesizeSounds.java') {
            throw 'Pack is not the approved original-synthesis distribution.'
        }
        $expected = @('bubbles','classic','rattle','squeak','thunder','trumpet','whisper')
        if ((($manifest.sounds.style | Sort-Object) -join ',') -cne ($expected -join ',')) { throw 'Wrong sound inventory.' }
        if (($manifest.sounds.sha256 | Sort-Object -Unique).Count -ne 7) { throw 'Duplicate audio recordings.' }
        $definitions = Read-ZipText $pack 'assets/fartmc/sounds.json' | ConvertFrom-Json
        $meta = Read-ZipText $pack 'pack.mcmeta' | ConvertFrom-Json
        if (($meta.pack.min_format -join '.') -ne '88.0' -or ($meta.pack.max_format -join '.') -ne '97.1') { throw 'Wrong pack formats.' }
        $license = Read-ZipText $pack 'LICENSE.txt'
        if ($license -cne (Get-Content licenses/CC0-1.0.txt -Raw)) { throw 'CC0 license differs from the reviewed text.' }
        $allowed = @('pack.mcmeta','SOURCES.json','LICENSE.txt','NOTICE.txt','assets/fartmc/sounds.json')
        foreach ($sound in $manifest.sounds) {
            if ($sound.license -ne 'CC0-1.0' -or $sound.origin -ne 'procedural synthesis, no samples') { throw 'Unknown sound rights.' }
            $name = "assets/fartmc/sounds/$($sound.style).ogg"
            $allowed += $name
            $entry = $pack.GetEntry($name)
            if (!$entry) { throw "Missing sound: $name" }
            $stream = $entry.Open()
            $hash = [Security.Cryptography.SHA256]::Create()
            try {
                $actual = [BitConverter]::ToString($hash.ComputeHash($stream)).Replace('-','').ToLowerInvariant()
                if ($actual -ne $sound.sha256) { throw "Audio checksum mismatch: $name" }
            } finally { $stream.Dispose(); $hash.Dispose() }
            $definition = $definitions.($sound.style)
            if (@($definition.sounds).Count -ne 1 -or $definition.sounds[0].name -ne "fartmc:$($sound.style)") { throw "Wrong sound definition: $name" }
        }
        $actualNames = @($pack.Entries | ForEach-Object FullName | Sort-Object)
        if (($actualNames -join ',') -cne (($allowed | Sort-Object) -join ',')) { throw 'Unexpected, duplicate or missing pack entries.' }
    } finally { $pack.Dispose() }

    Copy-Item $jars[0].FullName dist/ -Force
    Copy-Item LICENSE, NOTICE.md dist/ -Force
    & git archive --format=zip --output=dist/fartmc-source.zip HEAD
    if ($LASTEXITCODE -ne 0) { throw 'Source archive creation failed.' }
    $sums = foreach ($path in @("dist/$($jars[0].Name)",'dist/fartmc-source.zip','dist/fartmc-sounds.zip')) {
        $hash = (Get-FileHash $path -Algorithm SHA256).Hash.ToLowerInvariant()
        "$hash  $([IO.Path]::GetFileName($path))"
    }
    [IO.File]::WriteAllLines((Join-Path $root 'dist/SHA256SUMS'), $sums, [Text.UTF8Encoding]::new($false))
    Write-Output 'Distribution checks passed: own plugin classes, GPL + source, seven original CC0 sounds, no bundled third-party binaries.'
} finally { Pop-Location }
