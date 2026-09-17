[CmdletBinding()]
param(
  [Parameter(Mandatory = $true)][string]$PostgresBin,
  [int]$Port = 55439
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$targetRoot = (Resolve-Path (Join-Path $projectRoot 'src\backend\target')).Path
$testRoot = Join-Path $targetRoot 'business-clear-it'
$data = Join-Path $testRoot 'data'
$log = Join-Path $testRoot 'postgres.log'

function Invoke-Pg {
  param([Parameter(Mandatory = $true)][string]$Tool, [Parameter(Mandatory = $true)][string[]]$Arguments)
  & (Join-Path $PostgresBin "$Tool.exe") @Arguments
  if ($LASTEXITCODE -ne 0) { throw "$Tool failed with exit code $LASTEXITCODE" }
}

if (Test-Path -LiteralPath $testRoot) {
  $resolved = (Resolve-Path -LiteralPath $testRoot).Path
  if (-not $resolved.StartsWith($targetRoot + [IO.Path]::DirectorySeparatorChar)) { throw 'Unsafe integration test directory.' }
  Remove-Item -LiteralPath $resolved -Recurse -Force
}
New-Item -ItemType Directory -Path $testRoot | Out-Null
Invoke-Pg initdb @('-D', $data, '-U', 'postgres', '-A', 'trust', '--no-locale') | Out-Null
Invoke-Pg pg_ctl @('-D', $data, '-l', $log, '-o', "-p $Port -h 127.0.0.1", 'start') | Out-Null

try {
  Invoke-Pg createdb @('-h', '127.0.0.1', '-p', "$Port", '-U', 'postgres', 'jiabei_business_clear_it') | Out-Null
  $migrationRoot = Join-Path $projectRoot 'src\backend\src\main\resources\db\migration'
  $migrations = Get-ChildItem $migrationRoot -Filter 'V*.sql' | Sort-Object {
    [int]([regex]::Match($_.Name, '^V(\d+)').Groups[1].Value)
  }
  foreach ($migration in $migrations) {
    Invoke-Pg psql @('-h', '127.0.0.1', '-p', "$Port", '-U', 'postgres', '-d', 'jiabei_business_clear_it', '-v', 'ON_ERROR_STOP=1', '-f', $migration.FullName) | Out-Null
  }

  $seed = @'
INSERT INTO team(id,name) VALUES ('00000000-0000-0000-0000-000000000001','retained-team');
INSERT INTO makeup_artist(id,name) VALUES ('00000000-0000-0000-0000-000000000002','retained-artist');
INSERT INTO app_user(id,username,nickname,role,is_active,is_attending,default_team_id,last_makeup_artist_id,last_start_time,last_team_id) VALUES ('00000000-0000-0000-0000-000000000003','streamer-it','retained-streamer','STREAMER',true,true,'00000000-0000-0000-0000-000000000001','00000000-0000-0000-0000-000000000002','12:00','00000000-0000-0000-0000-000000000001');
INSERT INTO gate_event(id,external_event_id,dingtalk_user_id,occurred_at,raw_payload) VALUES ('00000000-0000-0000-0000-000000000004','event-it','ding-it',now(),'{}');
INSERT INTO appointment(id,booking_date,start_at,end_at,streamer_user_id,makeup_artist_id,team_id,streamer_name_snapshot,makeup_artist_name_snapshot,team_name_snapshot,source,created_by_user_id,attendance_event_id,attendance_evidence_at) VALUES ('00000000-0000-0000-0000-000000000005',(now() AT TIME ZONE 'Asia/Shanghai')::date,date_trunc('day',now() AT TIME ZONE 'Asia/Shanghai') AT TIME ZONE 'Asia/Shanghai' + interval '12 hours',date_trunc('day',now() AT TIME ZONE 'Asia/Shanghai') AT TIME ZONE 'Asia/Shanghai' + interval '12 hours 20 minutes','00000000-0000-0000-0000-000000000003','00000000-0000-0000-0000-000000000002','00000000-0000-0000-0000-000000000001','streamer','artist','team','STREAMER','00000000-0000-0000-0000-000000000003','00000000-0000-0000-0000-000000000004',now());
INSERT INTO appointment_operation_counter VALUES ('00000000-0000-0000-0000-000000000003',(now() AT TIME ZONE 'Asia/Shanghai')::date,1,1,now());
INSERT INTO audit_log(id,entity_type,entity_id,action,actor_name_snapshot,trace_id) VALUES ('00000000-0000-0000-0000-000000000006','APPOINTMENT','00000000-0000-0000-0000-000000000005','CREATE','test','trace');
INSERT INTO idempotency_record(actor_user_id,operation,idempotency_key,request_hash,status,expires_at) VALUES ('00000000-0000-0000-0000-000000000003','TEST','key',repeat('0',64),'PROCESSING',now()+interval '1 day');
INSERT INTO daily_card(id,business_date,group_open_conversation_id,out_track_id,template_id,status) VALUES ('00000000-0000-0000-0000-000000000007',current_date,'group','track','template','PENDING');
INSERT INTO late_notification(appointment_id,out_track_id,dingtalk_user_id) VALUES ('00000000-0000-0000-0000-000000000005','late-track','ding-it');
INSERT INTO integration_job(id,job_type,business_key,status) VALUES ('00000000-0000-0000-0000-000000000008','LATE_REMINDER','00000000-0000-0000-0000-000000000005','PENDING');
INSERT INTO mock_card_delivery(out_track_id,group_id,business_date,card_data,private_data,content_version,status) VALUES ('track','group',current_date,'{}','{}',1,'ACTIVE');
INSERT INTO mock_card_call_log(id,out_track_id,operation,content_version,result_code) VALUES ('00000000-0000-0000-0000-000000000009','track','CREATE',1,'OK');
'@
  Invoke-Pg psql @('-h', '127.0.0.1', '-p', "$Port", '-U', 'postgres', '-d', 'jiabei_business_clear_it', '-v', 'ON_ERROR_STOP=1', '-c', $seed) | Out-Null

  $dump = Join-Path $testRoot 'before-clear.dump'
  Invoke-Pg pg_dump @('-h', '127.0.0.1', '-p', "$Port", '-U', 'postgres', '-d', 'jiabei_business_clear_it', '-Fc', '-f', $dump) | Out-Null
  Invoke-Pg pg_restore @('--list', $dump) | Out-Null
  Invoke-Pg psql @('-h', '127.0.0.1', '-p', "$Port", '-U', 'postgres', '-d', 'jiabei_business_clear_it', '-v', 'ON_ERROR_STOP=1', '-f', (Join-Path $projectRoot 'scripts\sql\clear-business-data.sql')) | Out-Null
  $clearCheck = & (Join-Path $PostgresBin 'psql.exe') -h 127.0.0.1 -p $Port -U postgres -d jiabei_business_clear_it -Atqc "SELECT (SELECT count(*) FROM appointment),(SELECT count(*) FROM gate_event),(SELECT count(*) FROM integration_job),(SELECT count(*) FROM app_user),(SELECT count(*) FROM team),(SELECT count(*) FROM makeup_artist),(SELECT count(*) FROM app_user WHERE last_makeup_artist_id IS NOT NULL OR last_start_time IS NOT NULL OR last_team_id IS NOT NULL)"
  if ($clearCheck -ne '0|0|0|1|1|1|0') { throw "Unexpected clear result: $clearCheck" }

  Invoke-Pg createdb @('-h', '127.0.0.1', '-p', "$Port", '-U', 'postgres', 'jiabei_business_restore_it') | Out-Null
  Invoke-Pg pg_restore @('-h', '127.0.0.1', '-p', "$Port", '-U', 'postgres', '-d', 'jiabei_business_restore_it', $dump) | Out-Null
  $restoreCheck = & (Join-Path $PostgresBin 'psql.exe') -h 127.0.0.1 -p $Port -U postgres -d jiabei_business_restore_it -Atqc "SELECT (SELECT count(*) FROM appointment),(SELECT count(*) FROM gate_event),(SELECT count(*) FROM app_user)"
  if ($restoreCheck -ne '1|1|1') { throw "Unexpected restore result: $restoreCheck" }
  Write-Host "PASS isolated backup-clear-restore: clear=$clearCheck restore=$restoreCheck"
} finally {
  Invoke-Pg pg_ctl @('-D', $data, 'stop', '-m', 'fast') | Out-Null
}
