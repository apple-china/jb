$ErrorActionPreference = 'Stop'
$scriptPath = Join-Path $PSScriptRoot '..\backup-and-clear-business-data.ps1'
$integrationPath = Join-Path $PSScriptRoot 'business-data-clear.integration.ps1'
$sqlPath = Join-Path $PSScriptRoot '..\sql\clear-business-data.sql'
foreach ($path in @($scriptPath, $integrationPath)) {
  $tokens = $null
  $errors = $null
  [System.Management.Automation.Language.Parser]::ParseFile($path, [ref]$tokens, [ref]$errors) | Out-Null
  if ($errors.Count) { throw "Maintenance script has a PowerShell syntax error: $path - $($errors[0].Message)" }
}

$content = Get-Content -LiteralPath $scriptPath -Raw
$sql = Get-Content -LiteralPath $sqlPath -Raw
foreach ($required in @(
  'pg_dump', 'pg_restore', 'createdb', '--exit-on-error', "DELETE FROM appointment;", "DELETE FROM gate_event;",
  "DELETE FROM late_notification;", "DELETE FROM integration_job;",
  "DELETE FROM audit_log WHERE entity_type='APPOINTMENT';", 'BEGIN;', 'COMMIT;'
)) {
  if (-not ($content.Contains($required) -or $sql.Contains($required))) { throw "Maintenance workflow is missing: $required" }
}
foreach ($protected in @('DELETE FROM app_user', 'DELETE FROM team', 'DELETE FROM makeup_artist', 'DELETE FROM system_setting', 'DROP SCHEMA')) {
  if ($sql.Contains($protected)) { throw "Maintenance SQL must retain base data: $protected" }
}
if ($content.IndexOf("'pg_restore', '-U'") -gt $content.IndexOf('$clearSql = Get-Content')) { throw 'A real test restore must precede the removal transaction.' }
if (-not $content.Contains('if ($Preview)')) { throw 'Maintenance script must support preview mode.' }
if (-not $content.Contains("Get-Content -LiteralPath `$clearSqlPath -Raw")) { throw 'Maintenance script must execute the reviewed SQL file.' }
Write-Host 'PASS business-data backup/clear safety contract'
