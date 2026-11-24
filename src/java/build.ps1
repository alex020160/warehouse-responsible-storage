param([switch]$Test)
$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot
if (-not $env:JAVA_HOME) { throw 'Set JAVA_HOME to JDK 21.' }
$javac = Join-Path $env:JAVA_HOME 'bin/javac.exe'
$java = Join-Path $env:JAVA_HOME 'bin/java.exe'
$version = & $javac -version 2>&1
if ($version -notmatch '^javac 21[.]') { throw "JDK 21 required; detected $version" }
New-Item -ItemType Directory -Force lib,build/classes | Out-Null
$jar = Join-Path $PSScriptRoot 'lib/gson-2.13.2.jar'
if (-not (Test-Path -LiteralPath $jar)) {
    Invoke-WebRequest 'https://repo.maven.apache.org/maven2/com/google/code/gson/gson/2.13.2/gson-2.13.2.jar' -OutFile $jar
}
if (([BitConverter]::ToString([Security.Cryptography.SHA256]::Create().ComputeHash([IO.File]::ReadAllBytes($jar))).Replace('-','').ToLowerInvariant()) -ne 'dd0ce1b55a3ed2080cb70f9c655850cda86c206862310009dcb5e5c95265a5e0') { throw 'Invalid Gson SHA-256.' }
$sources = Get-ChildItem src/main/java,src/test/java -Recurse -Filter *.java | ForEach-Object { '"' + $_.FullName.Replace('\','/') + '"' }
[IO.File]::WriteAllLines((Join-Path $PSScriptRoot 'build/sources.txt'),$sources,[Text.UTF8Encoding]::new($false))
& $javac --release 21 -encoding UTF-8 -cp $jar -d build/classes '@build/sources.txt'
if ($LASTEXITCODE -ne 0) { throw 'Compilation failed.' }
if ($Test) {
    & $java -cp 'build/classes;lib/gson-2.13.2.jar' ru.mirea.warehouse.VerificationMain --self-test
    if ($LASTEXITCODE -ne 0) { throw 'Verification failed.' }
}
