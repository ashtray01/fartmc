param([string]$Ffmpeg, [switch]$LocalOnly)
$ErrorActionPreference = 'Stop'
if (!$LocalOnly) {
    throw 'Myinstants redistribution rights are unverified. This script is local-only: pass -LocalOnly. Use build-public-pack.ps1 for distributable audio.'
}
$root = Split-Path $PSScriptRoot -Parent
$cache = Join-Path $root '.cache'
New-Item -ItemType Directory -Force $cache | Out-Null
if (!$Ffmpeg) {
    $installed = Get-Command ffmpeg -ErrorAction SilentlyContinue
    if ($installed) { $Ffmpeg = $installed.Source }
    elseif (Test-Path 'C:/Program Files/VoiceStudio/ffmpeg.exe') { $Ffmpeg = 'C:/Program Files/VoiceStudio/ffmpeg.exe' }
    else {
        $local = Get-ChildItem "$cache/ffmpeg" -Filter ffmpeg.exe -Recurse -ErrorAction SilentlyContinue | Select-Object -First 1
        if (!$local) {
            $url = 'https://www.gyan.dev/ffmpeg/builds/ffmpeg-release-essentials.zip'
            Invoke-WebRequest $url -OutFile "$cache/ffmpeg.zip" -UseBasicParsing
            Expand-Archive "$cache/ffmpeg.zip" "$cache/ffmpeg" -Force
            $local = Get-ChildItem "$cache/ffmpeg" -Filter ffmpeg.exe -Recurse | Select-Object -First 1
        }
        $Ffmpeg = $local.FullName
    }
}
if (!(Test-Path $Ffmpeg)) { throw 'FFmpeg executable missing.' }
$pack = Join-Path $cache 'pack'
New-Item -ItemType Directory -Force "$pack/assets/fartmc/sounds", "$cache/audio" | Out-Null
$sounds = [ordered]@{}
$provenance = @()
foreach ($source in (Get-Content "$root/resource-pack/sources.json" -Raw | ConvertFrom-Json)) {
    $html = (Invoke-WebRequest $source.page -UseBasicParsing).Content
    $match = [regex]::Match($html, 'property="og:audio"\s+content="([^"]+)"')
    if (!$match.Success) { throw "Audio URL missing: $($source.page)" }
    $url = [Net.WebUtility]::HtmlDecode($match.Groups[1].Value)
    if ($url -notlike 'https://www.myinstants.com/media/sounds/*') { throw "Unexpected audio host: $url" }
    $mp3 = Join-Path $cache "audio/$($source.style).mp3"
    Invoke-WebRequest $url -OutFile $mp3 -UseBasicParsing
    $digest = (Get-FileHash $mp3 -Algorithm SHA256).Hash
    if ($provenance.sha256 -contains $digest) { throw "Duplicate recording for $($source.style); choose another source." }
    $ogg = Join-Path $pack "assets/fartmc/sounds/$($source.style).ogg"
    & $Ffmpeg -hide_banner -loglevel error -y -i $mp3 -t 5 -ac 1 -ar 44100 -af 'loudnorm=I=-20:TP=-3:LRA=7' -c:a libvorbis -q:a 4 $ogg
    if ($LASTEXITCODE -ne 0) { throw "Conversion failed: $($source.style)" }
    $sounds[$source.style] = @{ sounds = @(@{ name = "fartmc:$($source.style)"; attenuation_distance = 16 }) }
    $provenance += @{ style=$source.style; page=$source.page; audio=$url; sha256=$digest }
}
$utf8 = New-Object System.Text.UTF8Encoding($false)
[IO.File]::WriteAllText("$pack/assets/fartmc/sounds.json", ($sounds | ConvertTo-Json -Depth 8), $utf8)
$metadata = @{ pack = @{ description='FartMC - Myinstants sound selection'; min_format=@(88,0); max_format=@(97,1) } }
[IO.File]::WriteAllText("$pack/pack.mcmeta", ($metadata | ConvertTo-Json -Depth 5), $utf8)
[IO.File]::WriteAllText("$pack/SOURCES.json", ($provenance | ConvertTo-Json -Depth 5), $utf8)
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = Join-Path $cache 'fartmc-pack.zip'
# Delete only the explicitly named generated archive, never directories.
if (Test-Path $zip) { Remove-Item -LiteralPath $zip }
[IO.Compression.ZipFile]::CreateFromDirectory($pack, $zip)
Write-Output "Pack ready: $zip"
Get-FileHash $zip -Algorithm SHA1
