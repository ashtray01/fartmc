param([string]$JavaHome = $env:JAVA_HOME, [string]$Ffmpeg)
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
if (!$JavaHome) {
    $jdk = Get-ChildItem 'C:/Program Files/Eclipse Adoptium' -Directory -ErrorAction SilentlyContinue |
        Where-Object Name -Like 'jdk-25*' | Select-Object -First 1
    if ($jdk) { $JavaHome = $jdk.FullName }
}
$javaName = if ($IsLinux -or $IsMacOS) { 'java' } else { 'java.exe' }
$java = if ($JavaHome) { Join-Path $JavaHome "bin/$javaName" } else { (Get-Command java -ErrorAction Stop).Source }
if (!$Ffmpeg) {
    $tool = Get-Command ffmpeg -ErrorAction SilentlyContinue
    if ($tool) { $Ffmpeg = $tool.Source }
    elseif (Test-Path 'C:/Program Files/VoiceStudio/ffmpeg.exe') { $Ffmpeg = 'C:/Program Files/VoiceStudio/ffmpeg.exe' }
    else { throw 'Install FFmpeg or pass -Ffmpeg. Public builds do not download arbitrary binaries.' }
}
# Always use a new empty staging directory: never mix in a cached Myinstants pack.
$stage = Join-Path $root ('.cache/public-pack-' + [Guid]::NewGuid().ToString('N'))
$pack = Join-Path $stage 'pack'
$audio = Join-Path $pack 'assets/fartmc/sounds'
New-Item -ItemType Directory -Force $audio, "$stage/wav", "$root/dist" | Out-Null
& $java --source 25 "$PSScriptRoot/SynthesizeSounds.java" "$stage/wav"
if ($LASTEXITCODE -ne 0) { throw 'Sound synthesis failed.' }
$sounds = [ordered]@{}
$sources = @()
foreach ($wav in (Get-ChildItem "$stage/wav" -Filter '*.wav' | Sort-Object Name)) {
    $style = $wav.BaseName
    $ogg = Join-Path $audio "$style.ogg"
    & $Ffmpeg -hide_banner -loglevel error -y -i $wav.FullName -map_metadata -1 -ac 1 -ar 44100 -c:a libvorbis -q:a 4 $ogg
    if ($LASTEXITCODE -ne 0) { throw "Vorbis conversion failed: $style" }
    $sounds[$style] = @{ sounds = @(@{ name="fartmc:$style"; attenuation_distance=16 }) }
    $sources += @{ style=$style; origin='procedural synthesis, no samples'; license='CC0-1.0'; sha256=(Get-FileHash $ogg -Algorithm SHA256).Hash.ToLowerInvariant() }
}
$utf8 = New-Object System.Text.UTF8Encoding($false)
[IO.File]::WriteAllText("$pack/assets/fartmc/sounds.json", ($sounds | ConvertTo-Json -Depth 8), $utf8)
$meta = @{pack=@{description='FartMC original synthesized sounds (CC0)'; min_format=@(88,0); max_format=@(97,1)}}
[IO.File]::WriteAllText("$pack/pack.mcmeta", ($meta | ConvertTo-Json -Depth 5), $utf8)
$manifest = @{distribution='public-original'; generator='scripts/SynthesizeSounds.java'; license='CC0-1.0'; sounds=$sources}
[IO.File]::WriteAllText("$pack/SOURCES.json", ($manifest | ConvertTo-Json -Depth 8), $utf8)
Copy-Item "$root/licenses/CC0-1.0.txt" "$pack/LICENSE.txt"
[IO.File]::WriteAllText("$pack/NOTICE.txt", 'Original mathematical sound synthesis by FartMC contributors. No third-party samples. Audio and pack metadata: CC0-1.0. Generator source: GPL-3.0-only at https://github.com/ashtray01/fartmc .', $utf8)
Add-Type -AssemblyName System.IO.Compression.FileSystem
$temporaryZip = Join-Path $stage 'fartmc-sounds.zip'
[IO.Compression.ZipFile]::CreateFromDirectory($pack, $temporaryZip)
Copy-Item $temporaryZip "$root/dist/fartmc-sounds.zip" -Force
Write-Output "Public CC0 sound pack: $root/dist/fartmc-sounds.zip"
