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
    $provenance = Get-Content sounds/PROVENANCE.json -Raw -Encoding UTF8 | ConvertFrom-Json
    $approvedAudio = @($provenance.files | ForEach-Object { "sounds/$($_.file)" })
    if ($approvedAudio.Count -ne 21 -or ($approvedAudio | Sort-Object -Unique).Count -ne 21) { throw 'Expected 21 approved source recordings.' }
    if ($tracked | Where-Object { $_ -match '(^|/)(\.cache|runtime-[^/]+|target|dist)/|\.(jar|mp3|ogg|wav|exe|zip)$' -and $_ -cnotin $approvedAudio }) {
        throw 'Downloaded/generated binaries or runtime files are tracked.'
    }
    foreach ($source in $provenance.files) {
        if ($source.file -notmatch '^[a-z0-9_-]+\.mp3$' -or "sounds/$($source.file)" -cnotin $tracked -or
            (Get-FileHash "sounds/$($source.file)" -Algorithm SHA256).Hash -ine $source.sha256) { throw 'Approved source audio mismatch.' }
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
        if ($manifest.distribution -ne 'fartmc-complete' -or $manifest.license -ne 'mixed' -or $manifest.generator -ne 'scripts/build-public-pack.ps1') {
            throw 'Pack is not the approved complete FartMC distribution.'
        }
        $expected = @('bubbles','classic','rattle','squeak','thunder','trumpet','whisper')
        if ($manifest.sounds.Count -ne 28 -or ($manifest.sounds.name | Sort-Object -Unique).Count -ne 28 -or
            (($manifest.sounds.style | Sort-Object -Unique) -join ',') -cne ($expected -join ',')) { throw 'Wrong sound inventory.' }
        $profiles = Get-Content sounds/profiles.json -Raw -Encoding UTF8 | ConvertFrom-Json
        $definitions = Read-ZipText $pack 'assets/fartmc/sounds.json' | ConvertFrom-Json
        $meta = Read-ZipText $pack 'pack.mcmeta' | ConvertFrom-Json
        if (($meta.pack.min_format -join '.') -ne '84.0' -or ($meta.pack.max_format -join '.') -ne '97.1') { throw 'Wrong pack formats: expected Minecraft 26.1-26.3 (84.0-97.1).' }
        if ((Read-ZipText $pack 'CC0-1.0.txt') -cne (Get-Content licenses/CC0-1.0.txt -Raw)) { throw 'CC0 license differs from the reviewed text.' }
        if ((Read-ZipText $pack 'LICENSE.txt') -cne (Get-Content sounds/RIGHTS.txt -Raw -Encoding UTF8)) { throw 'Audio rights notice mismatch.' }
        if ((Read-ZipText $pack 'PROVENANCE.json') -cne (Get-Content sounds/PROVENANCE.json -Raw -Encoding UTF8)) { throw 'Audio provenance mismatch.' }
        $allowed = @('pack.mcmeta','SOURCES.json','LICENSE.txt','NOTICE.txt','CC0-1.0.txt','PROVENANCE.json','assets/fartmc/sounds.json')
        foreach ($sound in $manifest.sounds) {
            if ($sound.name -ceq $sound.style) {
                if ($sound.license -ne 'CC0-1.0' -or $sound.origin -ne 'procedural synthesis, no samples') { throw 'Unknown synthesized sound rights.' }
            } else {
                $source = @($provenance.files | Where-Object file -CEQ $sound.file)
                if ($source.Count -ne 1 -or $sound.license -ne 'LicenseRef-User-Provided' -or
                    $sound.origin -ne 'project-owner-provided recording' -or
                    $sound.source_sha256 -cne $source[0].sha256 -or
                    $sound.name -cne [IO.Path]::GetFileNameWithoutExtension($sound.file) -or
                    $sound.file -cnotin $profiles.($sound.style)) { throw 'Unknown supplied sound or provenance mismatch.' }
            }
            $name = "assets/fartmc/sounds/$($sound.name).ogg"
            $allowed += $name
            $entry = $pack.GetEntry($name)
            if (!$entry) { throw "Missing sound: $name" }
            $stream = $entry.Open()
            $hash = [Security.Cryptography.SHA256]::Create()
            try {
                $actual = [BitConverter]::ToString($hash.ComputeHash($stream)).Replace('-','').ToLowerInvariant()
                if ($actual -ne $sound.sha256) { throw "Audio checksum mismatch: $name" }
            } finally { $stream.Dispose(); $hash.Dispose() }
        }
        if ((($definitions.PSObject.Properties.Name | Sort-Object) -join ',') -cne ($expected -join ',')) { throw 'Wrong profile inventory.' }
        foreach ($style in $expected) {
            $wanted = @("fartmc:$style") + @($profiles.$style | ForEach-Object { 'fartmc:' + [IO.Path]::GetFileNameWithoutExtension($_) })
            $actual = @($definitions.$style.sounds | ForEach-Object name)
            if ($actual.Count -ne 4 -or (($actual | Sort-Object) -join ',') -cne (($wanted | Sort-Object) -join ',')) { throw "Wrong variants: $style" }
        }
        $actualNames = @($pack.Entries | ForEach-Object FullName | Sort-Object)
        if (($actualNames -join ',') -cne (($allowed | Sort-Object) -join ',')) { throw 'Unexpected, duplicate or missing pack entries.' }
    } finally { $pack.Dispose() }

    Copy-Item $jars[0].FullName dist/ -Force
    Copy-Item LICENSE, NOTICE.md dist/ -Force
    # Keep archived sources identical to the committed LF blobs on Windows too.
    & git -c core.autocrlf=false archive --format=zip --output=dist/fartmc-source.zip HEAD
    if ($LASTEXITCODE -ne 0) { throw 'Source archive creation failed.' }
    $sums = foreach ($path in @("dist/$($jars[0].Name)",'dist/fartmc-source.zip','dist/fartmc-sounds.zip')) {
        $hash = (Get-FileHash $path -Algorithm SHA256).Hash.ToLowerInvariant()
        "$hash  $([IO.Path]::GetFileName($path))"
    }
    [IO.File]::WriteAllLines((Join-Path $root 'dist/SHA256SUMS'), $sums, [Text.UTF8Encoding]::new($false))
    Write-Output 'Distribution checks passed: GPL plugin + corresponding source; 21 approved supplied recordings + 7 original CC0 sounds; Minecraft 26.1-26.3.'
} finally { Pop-Location }
