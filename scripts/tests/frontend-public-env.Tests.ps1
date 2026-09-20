param([ValidateSet('dev', 'prod')][string]$Mode)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$frontend = Join-Path $root 'src\frontend'
$clientValue = "public-client-$Mode-7f42"
$corpValue = "public-corp-$Mode-8a31"
$secretValue = "must-not-leak-$Mode-5d90"

$env:DINGTALK_CLIENT_ID = $clientValue
$env:DINGTALK_CORP_ID = $corpValue
$env:DINGTALK_AUTO_LOGIN = 'true'
$env:MOCK_LOGIN_ENABLED = 'false'
$env:DINGTALK_CLIENT_SECRET = $secretValue
Remove-Item Env:VITE_DINGTALK_CLIENT_ID,Env:VITE_DINGTALK_CORP_ID -ErrorAction SilentlyContinue

Push-Location $frontend
try {
  pnpm run build -- --mode $Mode
  if ($LASTEXITCODE -ne 0) { throw "$Mode frontend build failed." }
  $textAssets = @(Get-ChildItem dist -Recurse -File | Where-Object Extension -In @('.js', '.html', '.css'))
  $publicText = ($textAssets | Get-Content -Raw) -join "`n"
  if (-not $publicText.Contains($clientValue) -or -not $publicText.Contains($corpValue)) {
    throw "$Mode build did not expose both explicitly allowed public DingTalk values."
  }
  if ($publicText.Contains($secretValue)) {
    throw "$Mode build leaked DINGTALK_CLIENT_SECRET."
  }
} finally {
  Pop-Location
}

Write-Host "PASS $Mode frontend public-environment allowlist"
