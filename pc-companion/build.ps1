param([Parameter(Mandatory=$true)][string]$ClientDll,[Parameter(Mandatory=$true)][string]$OutputDirectory,[string]$IconFile)
$ErrorActionPreference='Stop'
$compiler=Join-Path $env:WINDIR 'Microsoft.NET/Framework64/v4.0.30319/csc.exe'
if(-not(Test-Path -LiteralPath $compiler)){throw '.NET Framework x64 compiler unavailable'}
$clientHash=(Get-FileHash -LiteralPath $ClientDll -Algorithm SHA256).Hash.ToLowerInvariant()
if($clientHash -ne '00303f99e1968d523fbd08a7c691d60d145b7335059ec593947841d124c10875'){throw 'Unexpected native client checksum'}
New-Item -ItemType Directory -Path $OutputDirectory -Force | Out-Null
Copy-Item -LiteralPath $ClientDll -Destination (Join-Path $OutputDirectory 'ViGEmClient.dll') -Force
$qr=Join-Path $PSScriptRoot 'lib/zxing.dll'
if((Get-FileHash -LiteralPath $qr -Algorithm SHA256).Hash.ToLowerInvariant() -ne '52f00a43f9574a411009899a6236f3efb5b597fbae0f3163dfec9cbb0c1f4d38'){throw 'Unexpected QR library checksum'}
Copy-Item -LiteralPath $qr -Destination (Join-Path $OutputDirectory 'zxing.dll') -Force
$core=Join-Path $PSScriptRoot 'BridgeCore.cs'
$metadata=Join-Path $PSScriptRoot 'AssemblyInfo.cs'
$wire=@('WireProtocol.cs','WifiReceiver.cs','DsuServer.cs') | ForEach-Object {Join-Path $PSScriptRoot $_}
[string[]]$iconArgs=@()
if($IconFile){$iconArgs+="/win32icon:$IconFile"}
& $compiler /nologo /target:winexe /platform:x64 /optimize+ /reference:System.Windows.Forms.dll /reference:System.Drawing.dll "/reference:$qr" @iconArgs "/out:$(Join-Path $OutputDirectory 'Nordhub.ProController.exe')" $metadata $core @wire (Join-Path $PSScriptRoot 'QrPairing.cs') (Join-Path $PSScriptRoot 'Program.cs')
if($LASTEXITCODE -ne 0){throw 'PC companion compilation failed'}
& $compiler /nologo /target:exe /platform:x64 /optimize+ "/out:$(Join-Path $OutputDirectory 'BridgeChecks.exe')" $core @wire (Join-Path $PSScriptRoot 'BridgeChecks.cs')
if($LASTEXITCODE -ne 0){throw 'PC companion test compilation failed'}
& (Join-Path $OutputDirectory 'BridgeChecks.exe')
if($LASTEXITCODE -ne 0){throw 'PC companion mapping checks failed'}
& $compiler /nologo /target:exe /platform:x64 /optimize+ "/out:$(Join-Path $OutputDirectory 'WireChecks.exe')" $core @wire (Join-Path $PSScriptRoot 'WireChecks.cs')
if($LASTEXITCODE -ne 0){throw 'Wi-Fi test compilation failed'}
& (Join-Path $OutputDirectory 'WireChecks.exe')
if($LASTEXITCODE -ne 0){throw 'Wi-Fi/DSU checks failed'}
& $compiler /nologo /target:exe /platform:x64 /optimize+ /reference:System.Drawing.dll "/reference:$qr" "/out:$(Join-Path $OutputDirectory 'QrChecks.exe')" $core @wire (Join-Path $PSScriptRoot 'QrPairing.cs') (Join-Path $PSScriptRoot 'QrChecks.cs')
if($LASTEXITCODE -ne 0){throw 'QR checks compilation failed'}
& (Join-Path $OutputDirectory 'QrChecks.exe') $OutputDirectory
if($LASTEXITCODE -ne 0){throw 'QR checks failed'}
