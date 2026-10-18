param([string]$OutputDirectory)
$ErrorActionPreference='Stop'
$project=Split-Path -Parent $PSScriptRoot
$output=if($OutputDirectory){[IO.Path]::GetFullPath($OutputDirectory)}else{Join-Path ([IO.Path]::GetTempPath()) ('switchphone-tests-'+[Guid]::NewGuid().ToString('N'))}
New-Item -ItemType Directory -Path $output | Out-Null
$java=if($env:JAVA_HOME){Join-Path $env:JAVA_HOME 'bin/java.exe'}else{'java.exe'}
$qr=Join-Path $project 'android-app/app/libs/zxing-core-3.5.4.jar'
$sources=@('wifi/QrLuminance','wifi/PairingQr','controller/PcFeedback','controller/AdvancedInput','controller/MotionModel','controller/ControlFrame','controller/WirePacket','controller/ControllerState','controller/InputRouter','controller/PairingPulse','bluetooth/DiagnosticDescriptor','bluetooth/PairingFlow','bluetooth/BondMonitor','bluetooth/BondFailure','bluetooth/LinkExperiment','bluetooth/HidProfile','protocol/NintendoDescriptor','protocol/NintendoProtocol','layouts/ControllerGeometry') | ForEach-Object {Join-Path $project "android-app/app/src/main/java/com/switchphone/$_.java"}
$tests=@(Get-ChildItem -LiteralPath $PSScriptRoot -Filter '*Test.java')
& $java com.sun.tools.javac.Main -encoding UTF-8 -cp $qr -d $output @sources @($tests.FullName)
if($LASTEXITCODE -ne 0){throw 'Test compilation failed'}
foreach($test in $tests){
    & $java -cp "$output;$qr" $test.BaseName
    if($LASTEXITCODE -ne 0){throw "Test failed: $($test.BaseName)"}
}
Write-Output "Compiled test classes: $output"
