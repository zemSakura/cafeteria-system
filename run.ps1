param(
    [switch]$Clean,
    [switch]$NoDownload
)

$ErrorActionPreference = "Stop"
$ProjectRoot = $PSScriptRoot
$DeployScript = Join-Path $ProjectRoot "deploy.ps1"
$OutputDir = Join-Path $ProjectRoot "deploy-output"
$GeneratedRunScript = Join-Path $OutputDir "run.ps1"

function Fail {
    param([string]$Message)
    Write-Host ""
    Write-Host "[ERROR] $Message" -ForegroundColor Red
    exit 1
}

if (-not (Test-Path -LiteralPath $DeployScript)) {
    Fail "deploy.ps1 was not found. Make sure you are running this script from the cloned project root."
}

$deployArgs = @(
    "-ProjectRoot", $ProjectRoot,
    "-OutputDir", $OutputDir,
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

if (-not (Test-Path -LiteralPath $GeneratedRunScript)) {
    Fail "Generated startup script was not found: $GeneratedRunScript"
}

Write-Host ""
Write-Host "Starting Canteen-Simulation..."
& powershell -NoProfile -ExecutionPolicy Bypass -File $GeneratedRunScript
