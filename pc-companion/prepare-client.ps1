param([string]$OutputDirectory=(Join-Path $PSScriptRoot '.switchphone-tools/client'))
$ErrorActionPreference='Stop'
New-Item -ItemType Directory -Path $OutputDirectory -Force | Out-Null
$OutputDirectory=[IO.Path]::GetFullPath($OutputDirectory)
$package=Join-Path $OutputDirectory 'nefarius.vigem.client.1.21.256.zip'
Invoke-WebRequest -UseBasicParsing -Uri 'https://api.nuget.org/v3-flatcontainer/nefarius.vigem.client/1.21.256/nefarius.vigem.client.1.21.256.nupkg' -OutFile $package
$sha=[Security.Cryptography.SHA512]::Create()
try{$actual=[Convert]::ToBase64String($sha.ComputeHash([IO.File]::ReadAllBytes($package)))}finally{$sha.Dispose()}
if($actual -ne 'iYJF1h8jsLGkHwHYLK5awxWBgfSRhHSsmvXZ3pUtzmnLE3aaizuq7AFf1GdHj1ysvQAZT7j0R0QFNN4Wg2T3fg=='){throw 'NuGet package SHA-512 mismatch'}
$unpacked=Join-Path $OutputDirectory ('unpacked-'+[Guid]::NewGuid().ToString('N'))
Add-Type -AssemblyName System.IO.Compression.FileSystem
[IO.Compression.ZipFile]::ExtractToDirectory($package,$unpacked)
$assembly=[Reflection.Assembly]::LoadFile((Join-Path $unpacked 'lib/netstandard2.0/Nefarius.ViGEm.Client.dll'))
$stream=$assembly.GetManifestResourceStream('costura64.vigemclient.dll')
if($null -eq $stream){throw 'Native x64 client resource missing'}
$client=Join-Path $OutputDirectory 'ViGEmClient.dll'
$destination=[IO.File]::Create($client)
try{$stream.CopyTo($destination)}finally{$destination.Dispose();$stream.Dispose()}
if((Get-FileHash -LiteralPath $client -Algorithm SHA256).Hash.ToLowerInvariant() -ne '00303f99e1968d523fbd08a7c691d60d145b7335059ec593947841d124c10875'){throw 'Native client SHA-256 mismatch'}
Write-Output "Verified native client: $client"
