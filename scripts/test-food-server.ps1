param([string]$JavaHome = $env:JAVA_HOME, [switch]$SkipBuild)
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
if (!$JavaHome) {
    $JavaHome = (Get-ChildItem 'C:/Program Files/Eclipse Adoptium' -Directory |
        Where-Object Name -Like 'jdk-25*' | Select-Object -First 1).FullName
}
if (!$SkipBuild) { & "$PSScriptRoot/build.ps1" -JavaHome $JavaHome }
$compiled = "$root/target/test-classes/io/github/ashtray01/fartmc/integration"
if (!(Test-Path "$compiled/FoodIntegrationPlugin.class")) { throw 'Build test classes first.' }
$stage = Join-Path $root ('.cache/food-probe-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Force "$stage/io/github/ashtray01/fartmc/integration" | Out-Null
Copy-Item "$compiled/*.class" "$stage/io/github/ashtray01/fartmc/integration/"
$descriptor = @'
name: FartFoodProbe
version: '1'
main: io.github.ashtray01.fartmc.integration.FoodIntegrationPlugin
api-version: '26.1'
depend: [FartPlugin]
'@
[IO.File]::WriteAllText("$stage/plugin.yml", $descriptor, [Text.UTF8Encoding]::new($false))
$probe = "$root/test-server/runtime-26.2-smoke/plugins/FartFoodProbe.jar"
New-Item -ItemType Directory -Force (Split-Path $probe -Parent) | Out-Null
& "$JavaHome/bin/jar.exe" --create --file $probe -C $stage .
if ($LASTEXITCODE -ne 0) { throw 'Could not package food probe.' }
try {
    & "$root/test-server/start.ps1" -Version 26.2 -Smoke -FoodTests -Offline -AcceptEula -WithSounds -SkipBuild -JavaHome $JavaHome
} finally {
    Remove-Item -LiteralPath $probe -Force
}
