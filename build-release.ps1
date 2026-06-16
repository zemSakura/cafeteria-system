param(
    [string]$ProjectRoot = $PSScriptRoot,
    [string]$ReleaseRoot = (Join-Path $PSScriptRoot "release-output"),
    [switch]$Clean,
    [switch]$NoDownload
)

$ErrorActionPreference = "Stop"

$AppName = "Canteen-Simulation"
$DeployOutput = Join-Path $ProjectRoot "deploy-output"
$DeployScript = Join-Path $ProjectRoot "deploy.ps1"
$InputDir = Join-Path $ReleaseRoot "build\input"
$AppImageParent = Join-Path $ReleaseRoot "app-image"
$AppImageDir = Join-Path $AppImageParent $AppName
$PortableZip = Join-Path $ReleaseRoot "$AppName-Portable-Windows.zip"
$FlatLafJar = Join-Path $DeployOutput "lib\flatlaf-3.4.1.jar"
$AppJar = Join-Path $DeployOutput "$AppName.jar"

function Write-Step {
    param([string]$Message)
    Write-Host ""
    Write-Host "==> $Message"
}

function Fail {
    param([string]$Message)
    Write-Host ""
    Write-Host "[ERROR] $Message" -ForegroundColor Red
    exit 1
}

Write-Host "Canteen-Simulation Portable Release Builder"
Write-Host "Project root : $ProjectRoot"
Write-Host "Release root : $ReleaseRoot"

Write-Step "Checking packaging tools"
if (-not (Test-Path -LiteralPath $DeployScript)) {
    Fail "deploy.ps1 was not found: $DeployScript"
}

$jpackage = Get-Command jpackage -ErrorAction SilentlyContinue
if (-not $jpackage) {
    Fail "jpackage was not found. Install JDK 17 or newer on the packaging machine."
}

$java = Get-Command java -ErrorAction SilentlyContinue
$javac = Get-Command javac -ErrorAction SilentlyContinue
if (-not $java -or -not $javac) {
    Fail "Both java and javac are required on the packaging machine."
}

Write-Host "jpackage: $($jpackage.Source)"

Write-Step "Preparing release directories"
if ($Clean -and (Test-Path -LiteralPath $ReleaseRoot)) {
    Remove-Item -LiteralPath $ReleaseRoot -Recurse -Force
}

foreach ($dir in @($InputDir, $AppImageParent)) {
    if (Test-Path -LiteralPath $dir) {
        Remove-Item -LiteralPath $dir -Recurse -Force
    }
    New-Item -ItemType Directory -Force -Path $dir | Out-Null
}

Write-Step "Building project jar"
$deployArgs = @(
    "-ProjectRoot", $ProjectRoot,
    "-OutputDir", $DeployOutput,
    "-NoPackage"
)
if ($Clean) {
    $deployArgs += "-Clean"
}
if ($NoDownload) {
    $deployArgs += "-NoDownload"
}

& powershell -NoProfile -ExecutionPolicy Bypass -File $DeployScript @deployArgs
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}

if (-not (Test-Path -LiteralPath $AppJar)) {
    Fail "Application jar was not generated: $AppJar"
}
if (-not (Test-Path -LiteralPath $FlatLafJar)) {
    Fail "FlatLaf dependency was not generated: $FlatLafJar"
}

Write-Step "Preparing jpackage input"
Copy-Item -LiteralPath $AppJar -Destination (Join-Path $InputDir "$AppName.jar") -Force
New-Item -ItemType Directory -Force -Path (Join-Path $InputDir "lib") | Out-Null
Copy-Item -LiteralPath $FlatLafJar -Destination (Join-Path $InputDir "lib\flatlaf-3.4.1.jar") -Force

$releaseReadme = @"
Canteen-Simulation 免配置运行版

使用方法：
1. 解压整个文件夹。
2. 双击 Canteen-Simulation.exe。

说明：
- 本发布包已经内置 Java Runtime，不需要安装 JDK/JRE。
- 不需要配置 JAVA_HOME 或 Path。
- 请不要删除 app、runtime 等子目录。
"@
Set-Content -LiteralPath (Join-Path $InputDir "README-PORTABLE.txt") -Value $releaseReadme -Encoding UTF8

Write-Step "Creating portable app image"
$jpackageArgs = @(
    "--type", "app-image",
    "--name", $AppName,
    "--input", $InputDir,
    "--main-jar", "$AppName.jar",
    "--dest", $AppImageParent,
    "--app-version", "1.0.0",
    "--java-options", "-Dfile.encoding=UTF-8"
)

& jpackage @jpackageArgs
if ($LASTEXITCODE -ne 0) {
    Fail "jpackage failed."
}

$exePath = Join-Path $AppImageDir "$AppName.exe"
if (-not (Test-Path -LiteralPath $exePath)) {
    Fail "Portable executable was not generated: $exePath"
}

Write-Step "Writing portable instructions"
Set-Content -LiteralPath (Join-Path $AppImageDir "使用说明.txt") -Value $releaseReadme -Encoding UTF8

Write-Step "Creating portable zip"
if (Test-Path -LiteralPath $PortableZip) {
    Remove-Item -LiteralPath $PortableZip -Force
}
Compress-Archive -Path $AppImageDir -DestinationPath $PortableZip -Force

Write-Step "Portable release completed"
Write-Host "App directory : $AppImageDir"
Write-Host "Executable    : $exePath"
Write-Host "Portable zip  : $PortableZip"
Write-Host ""
Write-Host "VM test path:"
Write-Host "  1. Copy or download $PortableZip"
Write-Host "  2. Extract it"
Write-Host "  3. Double-click $AppName.exe"

