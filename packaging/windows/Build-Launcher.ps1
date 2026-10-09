[CmdletBinding()]
param(
    [string]$FrontendPath,
    [string]$OutputDirectory
)

$ErrorActionPreference = 'Stop'
$repoRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
if (-not $FrontendPath) {
    $FrontendPath = Join-Path (Split-Path $repoRoot -Parent) 'Billing_App_UI\pahal-billing-web'
}
$FrontendPath = [IO.Path]::GetFullPath($FrontendPath)
if (-not (Test-Path -LiteralPath (Join-Path $FrontendPath 'package.json'))) {
    throw "Frontend project not found: $FrontendPath"
}
if (-not $OutputDirectory) { $OutputDirectory = Join-Path $repoRoot 'dist\windows\first-store-setup' }
$OutputDirectory = [IO.Path]::GetFullPath($OutputDirectory)
$compiler = Join-Path $env:WINDIR 'Microsoft.NET\Framework64\v4.0.30319\csc.exe'
if (-not (Test-Path -LiteralPath $compiler)) { throw 'The Windows .NET Framework C# compiler is required.' }

# Every build uses a new staging directory. No existing app, database, or build is deleted.
$buildRoot = Join-Path $repoRoot ('build\windows\' + [Guid]::NewGuid().ToString('N'))
$payload = Join-Path $buildRoot 'payload'
$downloads = Join-Path $repoRoot 'build\desktop-downloads'
New-Item -ItemType Directory -Force -Path $payload, $downloads, $OutputDirectory | Out-Null

Write-Host 'Building the production React UI with a relative API address...'
$previousApi = [Environment]::GetEnvironmentVariable('VITE_API_BASE_URL', 'Process')
Push-Location $FrontendPath
try {
    $env:VITE_API_BASE_URL = '/api'
    & npm.cmd run build
    if ($LASTEXITCODE -ne 0) { throw 'Frontend build failed.' }
}
finally {
    [Environment]::SetEnvironmentVariable('VITE_API_BASE_URL', $previousApi, 'Process')
    Pop-Location
}

Write-Host 'Building the desktop Spring Boot artifact (tests are not run)...'
Push-Location $repoRoot
try {
    & (Join-Path $repoRoot 'gradlew.bat') '-g' (Join-Path $repoRoot '.gradle-user-home') '--console=plain' `
        'desktopBootJar' ("-PdesktopUiDir=" + (Join-Path $FrontendPath 'dist'))
    if ($LASTEXITCODE -ne 0) { throw 'Backend desktop build failed.' }
}
finally { Pop-Location }
New-Item -ItemType Directory -Force -Path (Join-Path $payload 'app') | Out-Null
Copy-Item -LiteralPath (Join-Path $repoRoot 'build\libs\pahal-retail-desktop.jar') `
    -Destination (Join-Path $payload 'app\pahal-retail-desktop.jar')

Write-Host 'Preparing the redistributable Eclipse Temurin Java 17 runtime...'
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
$metadataPath = Join-Path $downloads 'temurin-jre.json'
# Retain metadata to pin repeat builds to the same verified runtime.
if (-not (Test-Path -LiteralPath $metadataPath)) {
    $asset = @(Invoke-RestMethod -Uri 'https://api.adoptium.net/v3/assets/latest/17/hotspot?architecture=x64&image_type=jre&os=windows&vendor=eclipse')[0]
    $asset | ConvertTo-Json -Depth 20 | Set-Content -LiteralPath $metadataPath -Encoding UTF8
}
$asset = Get-Content -LiteralPath $metadataPath -Raw | ConvertFrom-Json
$archive = Join-Path $downloads $asset.binary.package.name
if (-not (Test-Path -LiteralPath $archive)) {
    Invoke-WebRequest -Uri $asset.binary.package.link -OutFile $archive -UseBasicParsing
}
$actualHash = (Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash.ToLowerInvariant()
if ($actualHash -ne $asset.binary.package.checksum) { throw 'The Java download checksum does not match.' }
Add-Type -AssemblyName System.IO.Compression.FileSystem
$unpacked = Join-Path $buildRoot 'java-download'
[IO.Compression.ZipFile]::ExtractToDirectory($archive, $unpacked)
$runtime = @(Get-ChildItem -LiteralPath $unpacked -Directory)
if ($runtime.Count -ne 1 -or -not (Test-Path -LiteralPath (Join-Path $runtime[0].FullName 'bin\java.exe'))) {
    throw 'The Java runtime archive has an unexpected layout.'
}
Copy-Item -LiteralPath $runtime[0].FullName -Destination (Join-Path $payload 'runtime') -Recurse
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'README.md') -Destination (Join-Path $payload 'README.md')
$manifest = [ordered]@{
    application = 'Pahal Retail'
    release = '0.1.0-initial'
    architecture = 'windows-x64'
    javaVersion = $asset.version.openjdk_version
    javaDownload = $asset.binary.package.link
    javaSha256 = $actualHash
    database = 'Existing PostgreSQL; not bundled'
    apiAddress = '/api'
}
$manifest | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $payload 'package-info.json') -Encoding UTF8

Write-Host 'Creating the single executable payload...'
$zip = Join-Path $buildRoot 'payload.zip'
[IO.Compression.ZipFile]::CreateFromDirectory($payload, $zip, [IO.Compression.CompressionLevel]::Optimal, $false)
$payloadHash = (Get-FileHash -LiteralPath $zip -Algorithm SHA256).Hash.ToLowerInvariant()
$hashFile = Join-Path $buildRoot 'payload.sha256'
[IO.File]::WriteAllText($hashFile, $payloadHash, [Text.Encoding]::ASCII)

# A simple native Windows icon; the launcher uses the same icon in the system tray.
Add-Type -AssemblyName System.Drawing
$bitmap = New-Object Drawing.Bitmap 64, 64
$graphics = [Drawing.Graphics]::FromImage($bitmap)
$graphics.SmoothingMode = [Drawing.Drawing2D.SmoothingMode]::AntiAlias
$graphics.Clear([Drawing.Color]::Transparent)
$navy = New-Object Drawing.SolidBrush ([Drawing.Color]::FromArgb(19, 42, 77))
$white = New-Object Drawing.Pen ([Drawing.Color]::White), 4
$graphics.FillEllipse($navy, 0, 0, 63, 63)
$graphics.DrawRectangle($white, 17, 25, 29, 26)
$graphics.DrawArc($white, 23, 12, 17, 25, 180, 180)
$graphics.DrawLine($white, 23, 36, 29, 42)
$graphics.DrawLine($white, 29, 42, 40, 32)
$icon = [Drawing.Icon]::FromHandle($bitmap.GetHicon())
$iconPath = Join-Path $buildRoot 'pahal.ico'
$iconStream = [IO.File]::Create($iconPath)
try { $icon.Save($iconStream) }
finally { $iconStream.Dispose(); $icon.Dispose(); $graphics.Dispose(); $bitmap.Dispose(); $navy.Dispose(); $white.Dispose() }

$exe = Join-Path $OutputDirectory 'PahalRetail.exe'
$compilerArguments = @('/nologo', '/target:winexe', '/platform:anycpu', '/optimize+',
    ("/out:" + $exe), ("/win32icon:" + $iconPath),
    '/reference:System.Windows.Forms.dll', '/reference:System.Drawing.dll',
    '/reference:System.Net.Http.dll', '/reference:System.Web.Extensions.dll',
    '/reference:System.Security.dll', '/reference:System.IO.Compression.dll',
    '/reference:System.IO.Compression.FileSystem.dll',
    ("/resource:" + $zip + ',Pahal.Payload'), ("/resource:" + $hashFile + ',Pahal.PayloadHash'),
    (Join-Path $PSScriptRoot 'Launcher.cs'))
& $compiler @compilerArguments
if ($LASTEXITCODE -ne 0) { throw 'Windows launcher compilation failed.' }

Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'README.md') -Destination (Join-Path $OutputDirectory 'README.md')
$exeHash = (Get-FileHash -LiteralPath $exe -Algorithm SHA256).Hash.ToLowerInvariant()
[IO.File]::WriteAllText((Join-Path $OutputDirectory 'PahalRetail.exe.sha256'), $exeHash + '  PahalRetail.exe' + [Environment]::NewLine)
$manifest | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $OutputDirectory 'package-info.json') -Encoding UTF8
Write-Host ("Created: " + $exe)
Write-Host ("Executable size: " + [Math]::Round((Get-Item -LiteralPath $exe).Length / 1MB, 1) + " MiB")
Write-Host 'Customer machines do not need Java, Node.js, Gradle, or the source folders.'
Write-Host 'This initial build requires an existing PostgreSQL billing database.'
