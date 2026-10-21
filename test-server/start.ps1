param(
    [ValidateSet('26.3')][string]$Version = '26.3',
    [switch]$AcceptEula,
    [switch]$Smoke,
    [switch]$SkipBuild,
    [switch]$Update,
    [switch]$WithSounds,
    [string]$Ffmpeg,
    [string]$JavaHome = $env:JAVA_HOME,
    [int]$TimeoutSeconds = 240
)
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
if (!$JavaHome) {
    $candidate = Get-ChildItem 'C:\Program Files\Eclipse Adoptium' -Directory -ErrorAction SilentlyContinue |
        Where-Object Name -Like 'jdk-25*' | Select-Object -First 1
    if ($candidate) { $JavaHome = $candidate.FullName }
}
if (!$JavaHome -or !(Test-Path "$JavaHome/bin/java.exe")) { throw 'Install Java 25 and set JAVA_HOME.' }
if (!$SkipBuild) { & "$root/scripts/build.ps1" -JavaHome $JavaHome }
$plugin = Join-Path $root 'target/fartplugin-2.1.0.jar'
if (!(Test-Path $plugin)) { throw 'Build plugin first.' }
$runtimeName = if ($Smoke) { "runtime-$Version-smoke" } else { "runtime-$Version" }
$runtime = Join-Path $PSScriptRoot $runtimeName
New-Item -ItemType Directory -Force "$runtime/plugins" | Out-Null
if ($AcceptEula) { Set-Content "$runtime/eula.txt" 'eula=true' -Encoding ASCII }
if (!(Test-Path "$runtime/eula.txt") -or !(Select-String -Path "$runtime/eula.txt" -Pattern '^eula=true$' -Quiet)) {
    throw 'Read https://aka.ms/MinecraftEULA then run with -AcceptEula if you agree.'
}
$server = Join-Path $runtime 'paper.jar'
if ($Update -or !(Test-Path $server)) {
    $headers = @{ 'User-Agent' = 'FartMC/2.1 (local plugin development)' }
    $builds = Invoke-RestMethod "https://fill.papermc.io/v3/projects/paper/versions/$Version/builds" -Headers $headers
    $build = $builds | Where-Object channel -EQ 'STABLE' | Sort-Object id -Descending | Select-Object -First 1
    if (!$build) { throw "No stable Paper build for $Version" }
    $download = $build.downloads.'server:default'
    $temp = Join-Path $runtime 'paper.download'
    Invoke-WebRequest $download.url -Headers $headers -OutFile $temp -UseBasicParsing
    if ((Get-FileHash $temp -Algorithm SHA256).Hash -ine $download.checksums.sha256) { throw 'Paper checksum mismatch.' }
    Move-Item -LiteralPath $temp -Destination $server -Force
    $build | ConvertTo-Json -Depth 10 | Set-Content "$runtime/paper-build.json"
}
Copy-Item $plugin "$runtime/plugins/FartPlugin.jar" -Force
if ($WithSounds) {
    & "$root/scripts/build-public-pack.ps1" -JavaHome $JavaHome -Ffmpeg $Ffmpeg
    $soundPack = "$root/dist/fartmc-sounds.zip"
    New-Item -ItemType Directory -Force "$runtime/plugins/FartPlugin" | Out-Null
    Copy-Item $soundPack "$runtime/plugins/FartPlugin/resource-pack.zip" -Force
}
Copy-Item "$PSScriptRoot/server.properties" "$runtime/server.properties" -Force
if ($Smoke) {
    # Never share worlds, logs, settings or listening ports with an interactive server.
    Add-Content "$runtime/server.properties" 'server-port=25566' -Encoding ASCII
    New-Item -ItemType Directory -Force "$runtime/plugins/FartPlugin" | Out-Null
    $config = (Get-Content "$root/src/main/resources/config.yml" -Raw).
        Replace('port: 8123', 'port: 8124').
        Replace('http://127.0.0.1:8123/', 'http://127.0.0.1:8124/')
    [IO.File]::WriteAllText("$runtime/plugins/FartPlugin/config.yml", $config, [Text.UTF8Encoding]::new($false))
}
if (!$Smoke) {
    Push-Location $runtime
    try { & "$JavaHome/bin/java.exe" -Xms512M -Xmx1536M -jar paper.jar --nogui }
    finally { Pop-Location }
    exit $LASTEXITCODE
}
$info = New-Object System.Diagnostics.ProcessStartInfo
$info.FileName = "$JavaHome/bin/java.exe"
$info.Arguments = '-Xms512M -Xmx1536M -jar paper.jar --nogui'
$info.WorkingDirectory = $runtime
$info.UseShellExecute = $false
$info.CreateNoWindow = $true
$info.RedirectStandardInput = $true
$info.RedirectStandardOutput = $true
$info.RedirectStandardError = $true
$process = New-Object System.Diagnostics.Process
$process.StartInfo = $info
$started = Get-Date
try {
    [void]$process.Start()
    $stdout = $process.StandardOutput.ReadToEndAsync()
    $stderr = $process.StandardError.ReadToEndAsync()
    $ready = $false
    while (!$process.HasExited -and ((Get-Date) - $started).TotalSeconds -lt $TimeoutSeconds) {
        Start-Sleep -Seconds 2
        $log = Join-Path $runtime 'logs/latest.log'
        if ((Test-Path $log) -and (Get-Item $log).LastWriteTime -ge $started) {
            $content = Get-Content $log -Raw
            if ($content -match 'Done \(' -and $content -match 'FartPlugin 2.1 ready') { $ready = $true; break }
        }
    }
    if (!$ready) { throw 'Server did not become ready. Inspect smoke stdout/stderr and logs/latest.log.' }
    if ($WithSounds) {
        $downloaded = Join-Path $runtime 'smoke-pack.zip'
        $client = New-Object System.Net.WebClient
        $client.Proxy = $null
        try { $client.DownloadFile('http://127.0.0.1:8124/fartmc.zip', $downloaded) }
        finally { $client.Dispose() }
        if ((Get-FileHash $downloaded).Hash -ne (Get-FileHash $soundPack).Hash) {
            throw 'Served resource pack differs from generated ZIP.'
        }
        Write-Output 'PACK PASS: HTTP download matches generated ZIP.'
    }
    $process.StandardInput.WriteLine('fartreload')
    $process.StandardInput.WriteLine('fart help')
    $process.StandardInput.WriteLine('stop')
    $process.StandardInput.Flush()
    if (!$process.WaitForExit(60000)) { throw 'Server did not stop within 60 seconds.' }
    if ($process.ExitCode -ne 0) { throw "Server exit code: $($process.ExitCode)" }
    $output = $stdout.Result
    if ($output -match 'Error occurred while (enabling|disabling) FartPlugin|Could not load.*FartPlugin') { throw 'Plugin lifecycle failure.' }
    if ($output -notmatch '/fart \[classic' -or $output -notmatch 'FartPlugin:') { throw 'Plugin command smoke check failed.' }
    Write-Output "SMOKE PASS: Paper $Version; plugin enabled, world ready, clean stop."
} finally {
    if (!$process.HasExited) {
        $process.StandardInput.WriteLine('stop')
        $process.StandardInput.Flush()
        if (!$process.WaitForExit(15000)) { $process.Kill(); $process.WaitForExit() }
    }
    if ($stdout) { $stdout.Result | Set-Content "$runtime/smoke-stdout.log" }
    if ($stderr) { $stderr.Result | Set-Content "$runtime/smoke-stderr.log" }
    $process.Dispose()
}
