$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
if (-not $env:JAVA_HOME -or -not (Test-Path "$env:JAVA_HOME/bin/java.exe")) {
    $studioJdk = 'C:/Program Files/Android/Android Studio/jbr'
    if (Test-Path "$studioJdk/bin/java.exe") { $env:JAVA_HOME = $studioJdk }
}
Push-Location $projectRoot
try {
    & ./gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
    if ($LASTEXITCODE -ne 0) { throw "Android verification failed with exit code $LASTEXITCODE" }
} finally { Pop-Location }
