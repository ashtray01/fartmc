param([string]$JavaHome = $env:JAVA_HOME)
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
if (!$JavaHome) {
    $candidate = Get-ChildItem 'C:\Program Files\Eclipse Adoptium' -Directory -ErrorAction SilentlyContinue |
        Where-Object Name -Like 'jdk-25*' | Select-Object -First 1
    if ($candidate) { $JavaHome = $candidate.FullName }
}
if (!$JavaHome -or !(Test-Path "$JavaHome/bin/javac.exe")) { throw 'Install JDK 25 and set JAVA_HOME.' }
$env:JAVA_HOME = $JavaHome
$cache = Join-Path $root '.cache'
New-Item -ItemType Directory -Force $cache | Out-Null
$maven = Join-Path $cache 'apache-maven-3.9.11/bin/mvn.cmd'
if (!(Test-Path $maven)) {
    $url = 'https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.11/apache-maven-3.9.11-bin.zip'
    $zip = Join-Path $cache 'maven.zip'
    Invoke-WebRequest $url -OutFile $zip -UseBasicParsing
    $expected = (Invoke-RestMethod "$url.sha512").ToString().Trim().Split(' ')[0]
    if ((Get-FileHash $zip -Algorithm SHA512).Hash -ine $expected) { throw 'Maven checksum mismatch.' }
    Expand-Archive $zip $cache -Force
}
Push-Location $root
try {
    & $maven "-Dmaven.repo.local=$cache/m2" --batch-mode verify
    if ($LASTEXITCODE -ne 0) { throw "Build failed: $LASTEXITCODE" }
} finally { Pop-Location }
