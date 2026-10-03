$ErrorActionPreference = 'Stop'
$taskRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$taskOutput = Join-Path $taskRoot 'artifacts/google-asr-reprobe'
$taskSdk = $env:ANDROID_HOME
$taskTools = Join-Path $taskSdk 'build-tools/35.0.0'
$taskAndroid = Join-Path $taskSdk 'platforms/android-35/android.jar'
$taskJdk = Join-Path $env:JAVA_HOME 'bin'
New-Item -ItemType Directory -Force (Join-Path $taskOutput 'classes'), (Join-Path $taskOutput 'dex') | Out-Null
function Assert-Exit { if ($LASTEXITCODE -ne 0) { throw "Probe build failed: $LASTEXITCODE" } }
& "$taskJdk/javac.exe" -encoding UTF-8 -source 8 -target 8 -classpath $taskAndroid -d "$taskOutput/classes" "$PSScriptRoot/ProbeActivity.java"
Assert-Exit
& "$taskJdk/jar.exe" cf "$taskOutput/classes.jar" -C "$taskOutput/classes" .
Assert-Exit
& "$taskTools/d8.bat" --lib $taskAndroid --min-api 31 --output "$taskOutput/dex" "$taskOutput/classes.jar"
Assert-Exit
& "$taskTools/aapt.exe" package -f -M "$PSScriptRoot/AndroidManifest.xml" -I $taskAndroid -F "$taskOutput/unsigned.apk"
Assert-Exit
& "$taskJdk/jar.exe" uf "$taskOutput/unsigned.apk" -C "$taskOutput/dex" classes.dex
Assert-Exit
& "$taskTools/zipalign.exe" -f 4 "$taskOutput/unsigned.apk" "$taskOutput/aligned.apk"
Assert-Exit
& "$taskTools/apksigner.bat" sign --ks "$env:USERPROFILE/.android/debug.keystore" --ks-key-alias androiddebugkey --ks-pass pass:android --key-pass pass:android --out "$taskOutput/probe.apk" "$taskOutput/aligned.apk"
Assert-Exit
& "$taskTools/apksigner.bat" verify --verbose "$taskOutput/probe.apk"
Assert-Exit
Write-Output "Standalone probe built: $taskOutput/probe.apk"
