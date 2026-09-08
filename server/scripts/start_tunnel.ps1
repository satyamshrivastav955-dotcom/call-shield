# antAI Cloudflare Tunnel Runner
# Exposes the local antAI server (port 8765) to Cloudflare's global edge network.
[CmdletBinding()]
param(
    [string]$TargetUrl = "http://localhost:8765",
    [string]$CloudflaredPath = ""
)

$ErrorActionPreference = "Stop"

# Resolve paths
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ServerDir = Split-Path -Parent $ScriptDir
$RootDir = Split-Path -Parent $ServerDir

if (-not $CloudflaredPath) {
    $Candidate = Join-Path $RootDir "tools\cloudflared.exe"
    if (Test-Path $Candidate) {
        $CloudflaredPath = $Candidate
    } else {
        $InPath = Get-Command cloudflared.exe -ErrorAction SilentlyContinue
        if ($InPath) {
            $CloudflaredPath = $InPath.Source
        }
    }
}

if (-not $CloudflaredPath -or -not (Test-Path $CloudflaredPath)) {
    Write-Host "[!] cloudflared.exe not found. Downloading standalone binary..." -ForegroundColor Yellow
    $ToolsDir = Join-Path $RootDir "tools"
    if (-not (Test-Path $ToolsDir)) { New-Item -ItemType Directory -Path $ToolsDir | Out-Null }
    $CloudflaredPath = Join-Path $ToolsDir "cloudflared.exe"
    Invoke-WebRequest -Uri "https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-windows-amd64.exe" -OutFile $CloudflaredPath
    Write-Host "[+] Downloaded cloudflared to $CloudflaredPath" -ForegroundColor Green
}

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "  antAI Cloudflare Tunnel Gateway" -ForegroundColor Cyan
Write-Host "  Target Local Service: $TargetUrl" -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

# Test if local antAI server is running
try {
    $testResp = Invoke-WebRequest -Uri "$TargetUrl/" -TimeoutSec 2 -UseBasicParsing -ErrorAction SilentlyContinue
    Write-Host "[+] Local antAI server is active on $TargetUrl" -ForegroundColor Green
} catch {
    Write-Host "[!] Note: No active server detected at $TargetUrl yet." -ForegroundColor Yellow
    Write-Host "    Remember to start antAI server: python run_dev.py" -ForegroundColor Yellow
}

Write-Host "`nStarting Cloudflare Quick Tunnel... (Please wait)`n" -ForegroundColor Gray

$UrlFile = Join-Path $ServerDir "tunnel_url.txt"
if (Test-Path $UrlFile) { Remove-Item $UrlFile -Force }

$psi = New-Object System.Diagnostics.ProcessStartInfo
$psi.FileName = $CloudflaredPath
$psi.Arguments = "tunnel --url $TargetUrl"
$psi.RedirectStandardError = $true
$psi.RedirectStandardOutput = $true
$psi.UseShellExecute = $false
$psi.CreateNoWindow = $false

$proc = New-Object System.Diagnostics.Process
$proc.StartInfo = $psi
$proc.Start() | Out-Null

$foundUrl = $null
$regex = "https://[a-zA-Z0-9-]+\.trycloudflare\.com"

try {
    while (-not $proc.HasExited) {
        $line = $proc.StandardError.ReadLine()
        if ($line) {
            Write-Host $line
            if (-not $foundUrl -and $line -match $regex) {
                $foundUrl = $Matches[0]
                $foundUrl | Out-File -FilePath $UrlFile -Encoding utf8
                Write-Host "`n==========================================================" -ForegroundColor Green
                Write-Host "  CLOUDFLARE PUBLIC TUNNEL ACTIVE!" -ForegroundColor Green
                Write-Host "  Public HTTPS:  $foundUrl" -ForegroundColor Green
                Write-Host "  Public WSS:    $($foundUrl -replace '^https:', 'wss:')" -ForegroundColor Green
                Write-Host "  Dashboard:     $foundUrl/dashboard/" -ForegroundColor Green
                Write-Host "  API Docs:      $foundUrl/docs" -ForegroundColor Green
                Write-Host "==========================================================" -ForegroundColor Green
                Write-Host "Saved to: $UrlFile" -ForegroundColor Gray
                Write-Host "`nTo connect Android App, enter in Server Config:" -ForegroundColor Yellow
                Write-Host "  $foundUrl" -ForegroundColor White
                Write-Host "==========================================================`n" -ForegroundColor Green
            }
        }
    }
} finally {
    if (-not $proc.HasExited) {
        $proc.Kill()
    }
}
