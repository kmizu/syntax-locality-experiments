param([Parameter(ValueFromRemainingArguments=$true)][string[]]$Commands)
$ErrorActionPreference = 'Stop'
$jdkRoot = if ($env:SYNTAX_JAVA_HOME) { $env:SYNTAX_JAVA_HOME } else { 'C:/Program Files/Java/jdk-21' }
$launcherPath = Join-Path $PSScriptRoot '../.tools/sbt-launch-1.10.7.jar'
if (-not (Test-Path -LiteralPath $launcherPath)) {
    New-Item -ItemType Directory -Force -Path (Split-Path $launcherPath) | Out-Null
    Invoke-WebRequest -Uri 'https://repo.maven.apache.org/maven2/org/scala-sbt/sbt-launch/1.10.7/sbt-launch-1.10.7.jar' -OutFile $launcherPath
}
& "$jdkRoot/bin/java.exe" '-Dsbt.boot.server=false' '-Dsbt.server.autostart=false' '-Dfile.encoding=UTF-8' -jar $launcherPath @Commands
exit $LASTEXITCODE
