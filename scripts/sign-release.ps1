param([Parameter(Mandatory=$true)][string]$Keystore,[Parameter(Mandatory=$true)][string]$PasswordFile,[Parameter(Mandatory=$true)][string]$AndroidSdk,[string]$OutputApk,[string]$KeyAlias='nordhub-procontroller')
$ErrorActionPreference='Stop'
$project=Split-Path -Parent $PSScriptRoot
if(-not $OutputApk){$OutputApk=Join-Path $project 'android-app/app/build/outputs/apk/release/nordhub-procontroller.apk'}
$unsigned=Join-Path $project 'android-app/app/build/outputs/apk/release/app-release-unsigned.apk'
$aligned=Join-Path ([IO.Path]::GetTempPath()) ('nordhub-aligned-'+[Guid]::NewGuid().ToString('N')+'.apk')
$tools=Join-Path $AndroidSdk 'build-tools/35.0.0'
& (Join-Path $tools 'zipalign.exe') -f 4 $unsigned $aligned
if($LASTEXITCODE -ne 0){throw 'APK alignment failed'}
& (Join-Path $tools 'apksigner.bat') sign --ks $Keystore --ks-type PKCS12 --ks-key-alias $KeyAlias --ks-pass "file:$PasswordFile" --v1-signing-enabled false --v2-signing-enabled true --v3-signing-enabled true --v4-signing-enabled false --out $OutputApk $aligned
if($LASTEXITCODE -ne 0){throw 'APK signing failed'}
& (Join-Path $tools 'apksigner.bat') verify --verbose --print-certs $OutputApk
if($LASTEXITCODE -ne 0){throw 'APK signature verification failed'}
& (Join-Path $tools 'zipalign.exe') -c 4 $OutputApk
if($LASTEXITCODE -ne 0){throw 'APK alignment verification failed'}
Write-Output "Signed release: $OutputApk"
