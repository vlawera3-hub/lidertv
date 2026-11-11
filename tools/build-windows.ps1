# Requires Android SDK 35 and JDK 17 (JAVA_HOME). Does not install an Android SDK.
$ErrorActionPreference = 'Stop'
$Root = Split-Path -Parent $PSScriptRoot
$Version = '8.11.1'
$Expected = 'f397b287023acdba1e9f6fc5ea72d22dd63669d59ed4a289a29b1a76eee151c6'
if (-not $env:JAVA_HOME -or -not (Test-Path "$env:JAVA_HOME\bin\java.exe")) {
    throw 'JDK 17 kurun ve JAVA_HOME degiskenini ayarlayin.'
}
if (-not $env:ANDROID_HOME -and (Test-Path "$env:LOCALAPPDATA\Android\Sdk")) {
    $env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
}
if (-not $env:ANDROID_HOME -or -not (Test-Path "$env:ANDROID_HOME\platforms\android-35\android.jar")) {
    throw 'Android Studio SDK Manager ile Android SDK Platform 35 kurun. ANDROID_HOME ayarini kontrol edin.'
}
$Cache = Join-Path $env:LOCALAPPDATA 'LiderTvBuild'
New-Item -ItemType Directory -Force -Path $Cache | Out-Null
$Zip = Join-Path $Cache "gradle-$Version-bin.zip"
$Gradle = Join-Path $Cache "gradle-$Version\bin\gradle.bat"
if (-not (Test-Path $Gradle)) {
    if (-not (Test-Path $Zip)) {
        Invoke-WebRequest -Uri "https://services.gradle.org/distributions/gradle-$Version-bin.zip" -OutFile $Zip -UseBasicParsing
    }
    $Hash = (Get-FileHash $Zip -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($Hash -ne $Expected) {
        Remove-Item $Zip -Force
        throw 'Gradle SHA256 dogrulamasi basarisiz. Dosya silindi; derleme durduruldu.'
    }
    Expand-Archive -LiteralPath $Zip -DestinationPath $Cache -Force
}
Push-Location $Root
try {
    & $Gradle wrapper --gradle-version $Version --distribution-type bin --gradle-distribution-sha256-sum $Expected
    if ($LASTEXITCODE -ne 0) { throw 'Gradle wrapper olusturulamadi.' }
    & "$Root\gradlew.bat" :app:assembleDebug --stacktrace
    if ($LASTEXITCODE -ne 0) { throw 'Android derlemesi basarisiz. Yukaridaki ilk hatayi inceleyin.' }
    New-Item -ItemType Directory -Force -Path "$Root\output" | Out-Null
    Copy-Item "$Root\app\build\outputs\apk\debug\app-debug.apk" "$Root\output\LiderTv_0.1.0_Telefon_TV.apk"
    Write-Host "Hazir: $Root\output\LiderTv_0.1.0_Telefon_TV.apk"
} finally { Pop-Location }
