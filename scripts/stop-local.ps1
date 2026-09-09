$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$toolDir = Join-Path $projectRoot '.tools'

foreach ($name in @('frontend', 'backend')) {
  $pidFile = Join-Path $toolDir "$name.pid"
  if (Test-Path $pidFile) {
    $processId = [int](Get-Content $pidFile -Raw)
    $process = Get-Process -Id $processId -ErrorAction SilentlyContinue
    if ($process) { Stop-Process -Id $processId -Force }
    Remove-Item -LiteralPath $pidFile -Force
  }
}

$pgCtl = Join-Path $projectRoot '.tools\postgresql-16.15\pgsql\bin\pg_ctl.exe'
$pgData = Join-Path $projectRoot '.tools\pgdata'
if ((Test-Path $pgCtl) -and (Test-Path $pgData)) {
  & $pgCtl -D $pgData status *> $null
  if ($LASTEXITCODE -eq 0) { & $pgCtl -D $pgData -m fast stop }
}
Remove-Item -LiteralPath (Join-Path $toolDir 'database.pid') -Force -ErrorAction SilentlyContinue

Write-Host 'JiaBei portable services stopped.'
