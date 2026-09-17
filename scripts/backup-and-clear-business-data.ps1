[CmdletBinding()]
param(
  [string]$BackupDirectory = '',
  [ValidatePattern('\A[A-Za-z_][A-Za-z0-9_]*\z')][string]$Database = 'jiabei',
  [ValidatePattern('\A[A-Za-z_][A-Za-z0-9_]*\z')][string]$DatabaseUser = 'jiabei',
  [switch]$Preview,
  [switch]$Force
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$composeFile = Join-Path $projectRoot 'docker-compose.yml'
$clearSqlPath = Join-Path $PSScriptRoot 'sql\clear-business-data.sql'
if (-not $BackupDirectory) { $BackupDirectory = Join-Path $projectRoot 'artifacts\database-backups' }
$backupRoot = [System.IO.Path]::GetFullPath($BackupDirectory)

function Invoke-Compose {
  param([Parameter(Mandatory = $true)][string[]]$Arguments, [switch]$Capture)
  $output = & docker compose -f $composeFile @Arguments
  if ($LASTEXITCODE -ne 0) { throw "docker compose failed: $($Arguments -join ' ')" }
  if ($Capture) { return @($output) }
  $output | Out-Host
}

$services = @(Invoke-Compose -Arguments @('config', '--services') -Capture)
if ('db' -notin $services -or 'backend' -notin $services) { throw 'docker-compose.yml must contain db and backend services.' }

$countSql = @"
SELECT 'appointment',count(*) FROM appointment
UNION ALL SELECT 'gate_event',count(*) FROM gate_event
UNION ALL SELECT 'appointment_operation_counter',count(*) FROM appointment_operation_counter
UNION ALL SELECT 'appointment_audit',count(*) FROM audit_log WHERE entity_type='APPOINTMENT'
UNION ALL SELECT 'idempotency_record',count(*) FROM idempotency_record
UNION ALL SELECT 'daily_card',count(*) FROM daily_card
UNION ALL SELECT 'late_notification',count(*) FROM late_notification
UNION ALL SELECT 'integration_job',count(*) FROM integration_job
UNION ALL SELECT 'mock_card_delivery',count(*) FROM mock_card_delivery
UNION ALL SELECT 'mock_card_call_log',count(*) FROM mock_card_call_log
ORDER BY 1;
"@

Write-Host 'Business rows selected for removal (accounts, teams, schedules, DingTalk bindings, settings and migrations are retained):'
Invoke-Compose -Arguments @('exec', '-T', 'db', 'psql', '-U', $DatabaseUser, '-d', $Database, '-v', 'ON_ERROR_STOP=1', '-c', $countSql)
if ($Preview) { Write-Host 'Preview complete. No service was stopped and no data was changed.'; return }

if (-not $Force) {
  $answer = Read-Host "Type database name $Database to confirm backup and business-data removal"
  if ($answer -cne $Database) { Write-Host 'Cancelled.'; return }
}

$timestamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$backupName = "jiabei-business-before-clear-$timestamp.dump"
$containerBackup = "/tmp/$backupName"
$backupPath = Join-Path $backupRoot $backupName
$restoreCheckDatabase = "${Database}_restore_check_$($timestamp.Replace('-', ''))"
$backendWasRunning = 'backend' -in @(Invoke-Compose -Arguments @('ps', '--status', 'running', '--services') -Capture)
$backendStopped = $false
$restoreCheckCreated = $false

try {
  if ($backendWasRunning) { Invoke-Compose -Arguments @('stop', 'backend'); $backendStopped = $true }
  Invoke-Compose -Arguments @('exec', '-T', 'db', 'pg_dump', '-U', $DatabaseUser, '-d', $Database, '-Fc', '-f', $containerBackup)
  Invoke-Compose -Arguments @('exec', '-T', 'db', 'pg_restore', '--list', $containerBackup)
  Invoke-Compose -Arguments @('exec', '-T', 'db', 'dropdb', '-U', $DatabaseUser, '--if-exists', $restoreCheckDatabase)
  Invoke-Compose -Arguments @('exec', '-T', 'db', 'createdb', '-U', $DatabaseUser, $restoreCheckDatabase)
  $restoreCheckCreated = $true
  Invoke-Compose -Arguments @('exec', '-T', 'db', 'pg_restore', '-U', $DatabaseUser, '-d', $restoreCheckDatabase, '--exit-on-error', $containerBackup)
  Invoke-Compose -Arguments @('exec', '-T', 'db', 'psql', '-U', $DatabaseUser, '-d', $restoreCheckDatabase, '-v', 'ON_ERROR_STOP=1', '-c', 'SELECT count(*) AS restored_appointments FROM appointment;')
  Invoke-Compose -Arguments @('exec', '-T', 'db', 'dropdb', '-U', $DatabaseUser, $restoreCheckDatabase)
  $restoreCheckCreated = $false
  New-Item -ItemType Directory -Path $backupRoot -Force | Out-Null
  Invoke-Compose -Arguments @('cp', "db:$containerBackup", $backupPath)
  $backup = Get-Item -LiteralPath $backupPath
  if ($backup.Length -le 0) { throw 'Backup file is empty. Data removal was stopped.' }

  $clearSql = Get-Content -LiteralPath $clearSqlPath -Raw
  Invoke-Compose -Arguments @('exec', '-T', 'db', 'psql', '-U', $DatabaseUser, '-d', $Database, '-v', 'ON_ERROR_STOP=1', '-c', $clearSql)
  Write-Host 'Post-removal verification:'
  Invoke-Compose -Arguments @('exec', '-T', 'db', 'psql', '-U', $DatabaseUser, '-d', $Database, '-v', 'ON_ERROR_STOP=1', '-c', $countSql)
  Write-Host "Verified backup saved to: $backupPath"
  Write-Host "To restore, stop backend and run: docker compose -f `"$composeFile`" cp `"$backupPath`" db:/tmp/restore.dump"
  Write-Host "Then run: docker compose -f `"$composeFile`" exec -T db pg_restore -U $DatabaseUser -d $Database --clean --if-exists /tmp/restore.dump"
} finally {
  if ($restoreCheckCreated) {
    try { Invoke-Compose -Arguments @('exec', '-T', 'db', 'dropdb', '-U', $DatabaseUser, '--if-exists', $restoreCheckDatabase) } catch { Write-Warning 'Could not remove the temporary restore-check database.' }
  }
  try { Invoke-Compose -Arguments @('exec', '-T', 'db', 'rm', '-f', $containerBackup) } catch { Write-Warning 'Could not remove the temporary backup inside the database container.' }
  if ($backendStopped) { Invoke-Compose -Arguments @('up', '-d', 'backend') }
}
