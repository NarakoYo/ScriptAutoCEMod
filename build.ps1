param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]] $GradleArgs = @("clean", "build")
)

$candidates = @(
    $env:JDK25_HOME,
    $env:JAVA_HOME,
    "$env:APPDATA\.minecraft\runtime\java-runtime-epsilon"
) | Where-Object {
    $_ -and (Test-Path (Join-Path $_ "bin\java.exe")) -and (Test-Path (Join-Path $_ "bin\javac.exe"))
}

$jdk25 = $candidates | Where-Object {
    $release = Join-Path $_ "release"
    (Test-Path $release) -and ((Get-Content $release -Raw) -match 'JAVA_VERSION="25')
} | Select-Object -First 1

if (-not $jdk25) {
    throw "A Java 25 JDK is required. Set JDK25_HOME to its installation directory."
}

& (Join-Path $PSScriptRoot "scripts\check-privacy.ps1")
if ($null -ne $LASTEXITCODE -and $LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}

$env:JAVA_HOME = $jdk25
& (Join-Path $PSScriptRoot "gradlew.bat") @GradleArgs
exit $LASTEXITCODE
