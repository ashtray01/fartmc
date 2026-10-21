param([string]$Ffmpeg)
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
if (!$Ffmpeg) {
    $tool = Get-Command ffmpeg -ErrorAction SilentlyContinue
    if ($tool) { $Ffmpeg = $tool.Source }
    elseif (Test-Path 'C:/Program Files/VoiceStudio/ffmpeg.exe') { $Ffmpeg = 'C:/Program Files/VoiceStudio/ffmpeg.exe' }
    else { throw 'Install FFmpeg or pass -Ffmpeg to build the user sound pack.' }
}
$profiles = Get-Content "$root/sounds/profiles.json" -Raw -Encoding UTF8 | ConvertFrom-Json
$expected = @('bubbles','classic','rattle','squeak','thunder','trumpet','whisper')
if ((($profiles.PSObject.Properties.Name | Sort-Object) -join ',') -cne ($expected -join ',')) {
    throw 'Expected all seven sound profiles in sounds/profiles.json.'
}
$mapped = @()
foreach ($profile in $profiles.PSObject.Properties) {
    if (@($profile.Value).Count -eq 0) { throw "Empty profile: $($profile.Name)" }
    foreach ($file in $profile.Value) {
        if ($file -notmatch '^[a-z0-9_-]+\.mp3$' -or !(Test-Path -LiteralPath "$root/sounds/$file" -PathType Leaf)) {
            throw "Missing or invalid recording: $file"
        }
        $mapped += $file
    }
}
$available = @(Get-ChildItem "$root/sounds" -Filter '*.mp3' -File | ForEach-Object Name)
if (($mapped | Sort-Object -Unique).Count -ne $mapped.Count -or
    (($mapped | Sort-Object) -join ',') -cne (($available | Sort-Object) -join ',')) {
    throw 'Map each MP3 exactly once in sounds/profiles.json before building.'
}
# Fresh staging; keep this user pack separate from the public synthesized CC0 pack.
$stage = Join-Path $root ('.cache/user-pack-' + [Guid]::NewGuid().ToString('N'))
$pack = Join-Path $stage 'pack'
$audio = Join-Path $pack 'assets/fartmc/sounds'
New-Item -ItemType Directory -Force $audio, "$root/dist" | Out-Null
$sounds = [ordered]@{}
$sources = @()
foreach ($profile in $profiles.PSObject.Properties) {
    $variants = @()
    foreach ($file in $profile.Value) {
        $name = [IO.Path]::GetFileNameWithoutExtension($file)
        $inputPath = Join-Path "$root/sounds" $file
        $ogg = Join-Path $audio "$name.ogg"
        & $Ffmpeg -hide_banner -loglevel error -y -i $inputPath -vn -map_metadata -1 -ac 1 -ar 44100 -c:a libvorbis -q:a 4 $ogg
        if ($LASTEXITCODE -ne 0) { throw "Vorbis conversion failed: $file" }
        $variants += @{ name="fartmc:$name"; attenuation_distance=16 }
        $sources += @{
            style=$profile.Name; file=$file; origin='user-provided sounds directory'
            rights='royalty-free as described by the user; original terms apply, not CC0'
            source_sha256=(Get-FileHash $inputPath -Algorithm SHA256).Hash.ToLowerInvariant()
            sha256=(Get-FileHash $ogg -Algorithm SHA256).Hash.ToLowerInvariant()
        }
    }
    $sounds[$profile.Name] = @{ sounds=$variants }
}
$utf8 = New-Object System.Text.UTF8Encoding($false)
[IO.File]::WriteAllText("$pack/assets/fartmc/sounds.json", ($sounds | ConvertTo-Json -Depth 8), $utf8)
$meta = @{pack=@{description='FartMC user-provided sounds'; min_format=@(84,0); max_format=@(97,1)}}
[IO.File]::WriteAllText("$pack/pack.mcmeta", ($meta | ConvertTo-Json -Depth 5), $utf8)
$manifest = @{distribution='local-user-recordings'; sounds=$sources}
[IO.File]::WriteAllText("$pack/SOURCES.json", ($manifest | ConvertTo-Json -Depth 8), $utf8)
[IO.File]::WriteAllText("$pack/NOTICE.txt", 'User-provided recordings described as royalty-free. Original audio terms apply; these recordings are not relicensed under CC0 or GPL. This local pack is separate from the public synthesized CC0 pack.', $utf8)
Add-Type -AssemblyName System.IO.Compression.FileSystem
$temporaryZip = Join-Path $stage 'fartmc-user-sounds.zip'
$archive = [IO.Compression.ZipFile]::Open($temporaryZip, 'Create')
try {
    foreach ($file in Get-ChildItem $pack -Recurse -File) {
        $name = $file.FullName.Substring($pack.Length + 1).Replace('\', '/')
        [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($archive, $file.FullName, $name) | Out-Null
    }
} finally { $archive.Dispose() }
Copy-Item $temporaryZip "$root/dist/fartmc-user-sounds.zip" -Force
Write-Output "User sound pack: $root/dist/fartmc-user-sounds.zip ($($sources.Count) recordings)"
