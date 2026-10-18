param([Parameter(Mandatory=$true)][string]$ClientDll,[Parameter(Mandatory=$true)][string]$OutputDirectory)
$ErrorActionPreference='Stop'
$target=[IO.Path]::GetFullPath($OutputDirectory)
New-Item -ItemType Directory -Path $target -Force | Out-Null
$classes=Join-Path $target 'java-classes'
& (Join-Path $PSScriptRoot 'run.ps1') -OutputDirectory $classes
& (Join-Path (Split-Path $PSScriptRoot -Parent) 'pc-companion/build.ps1') -ClientDll $ClientDll -OutputDirectory (Join-Path $target 'windows')
$java=if($env:JAVA_HOME){Join-Path $env:JAVA_HOME 'bin/java.exe'}else{'java.exe'}
$inputVector=Join-Path $target 'java-packet.bin'
$feedbackVector=Join-Path $target 'csharp-feedback.bin'
& $java -cp $classes WirePacketTest export $inputVector
if($LASTEXITCODE -ne 0){throw 'Java vector export failed'}
& (Join-Path $target 'windows/WireChecks.exe') $inputVector $feedbackVector
if($LASTEXITCODE -ne 0){throw 'C# interoperability failed'}
& $java -cp $classes WirePacketTest feedback $feedbackVector
if($LASTEXITCODE -ne 0){throw 'Java feedback interoperability failed'}
$qrJar=Join-Path (Split-Path $PSScriptRoot -Parent) 'android-app/app/libs/zxing-core-3.5.4.jar'
& $java -cp "$classes;$qrJar" PairingQrTest (Join-Path $target 'windows/qr-pairing.png')
if($LASTEXITCODE -ne 0){throw 'QR camera decoder interoperability failed'}
Write-Output 'Java/C# Wi-Fi interoperability passed in both directions; no APK generated.'
