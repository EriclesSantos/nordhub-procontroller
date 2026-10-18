param([Parameter(Mandatory=$true)][string]$OutputDirectory)
$ErrorActionPreference='Stop'
$target=[IO.Path]::GetFullPath($OutputDirectory)
New-Item -ItemType Directory -Path $target -Force | Out-Null
$temporary=Join-Path $target ('nuget-'+[Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $temporary | Out-Null
$package=Join-Path $temporary 'client.zip'
Invoke-WebRequest -Uri 'https://api.nuget.org/v3-flatcontainer/nefarius.vigem.client/1.21.256/nefarius.vigem.client.1.21.256.nupkg' -OutFile $package
$expected='898245d61f23b0b1a41f01d82cae5ac3158181f4918474ac9af5d9de952dce69cb13769a8b3baaec015fd467478f5cacbd00194fb8f447440534de168364f77e'
if((Get-FileHash -LiteralPath $package -Algorithm SHA512).Hash.ToLowerInvariant() -ne $expected){throw 'Unexpected NuGet package checksum'}
Expand-Archive -LiteralPath $package -DestinationPath (Join-Path $temporary 'package')
$assembly=[Reflection.Assembly]::LoadFile((Join-Path $temporary 'package/lib/netstandard2.0/Nefarius.ViGEm.Client.dll'))
$stream=$assembly.GetManifestResourceStream('costura64.vigemclient.dll')
if($null -eq $stream){throw 'Expected x64 native client resource missing'}
$memory=[IO.MemoryStream]::new()
try{$stream.CopyTo($memory);$bytes=$memory.ToArray()}finally{$stream.Dispose();$memory.Dispose()}
$sha=[Security.Cryptography.SHA256]::Create()
try{$hash=([BitConverter]::ToString($sha.ComputeHash($bytes))).Replace('-','').ToLowerInvariant()}finally{$sha.Dispose()}
if($hash -ne '00303f99e1968d523fbd08a7c691d60d145b7335059ec593947841d124c10875'){throw 'Unexpected x64 native client checksum'}
[IO.File]::WriteAllBytes((Join-Path $target 'ViGEmClient64.dll'),$bytes)
Write-Output 'ViGEmClient x64 verified. Driver installation is separate.'
