[CmdletBinding()]
param(
  [ValidateSet('Auto', 'Docker', 'Portable')][string]$Runtime = 'Auto',
  [string]$Database = 'jiabei',
  [string]$Profile = 'local',
  [switch]$Force,
  [switch]$DryRun
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$projectRoot = Split-Path -Parent $PSScriptRoot
Import-Module (Join-Path $PSScriptRoot 'lib\MockResetSafety.psm1') -Force

function Invoke-CheckedCommand([string]$Executable, [string[]]$Arguments, [string]$InputText) {
  $previousPreference = $ErrorActionPreference
  try {
    # Windows PowerShell treats native stderr as ErrorRecords; check the exit code ourselves.
    $ErrorActionPreference = 'Continue'
    if ($InputText) { $output = $InputText | & $Executable @Arguments 2>&1 }
    else { $output = & $Executable @Arguments 2>&1 }
    $exitCode = $LASTEXITCODE
  } finally { $ErrorActionPreference = $previousPreference }
  if ($exitCode -ne 0) { throw "外部命令失败：$Executable (exit $exitCode)。重置已停止。" }
  return $output
}

# Read only configuration names consumed by this application; never print their values.
Assert-MockResetConfiguration ([Environment]::GetEnvironmentVariables())
$resolvedRuntime = Resolve-MockRuntime -Requested $Runtime -ProjectRoot $projectRoot
$databaseHost = '127.0.0.1'
$port = 55432
$composeArguments = @('compose', '--project-directory', $projectRoot, '-f', (Join-Path $projectRoot 'docker-compose.yml'))
if ($resolvedRuntime -eq 'Docker') { $databaseHost = 'db'; $port = 5432 }
Assert-SafeMockResetTarget -DatabaseHost $databaseHost -Port $port -Database $Database -Profile $Profile -ServerAddress ''
$resetMode = Get-MockResetMode -Profile $Profile -Database $Database

if ($resolvedRuntime -eq 'Docker') {
  # A Docker daemon on a remote host must never be treated as a local database.
  $endpoint = $env:DOCKER_HOST
  if (-not $endpoint -or $env:DOCKER_CONTEXT) {
    $contextName = (Invoke-CheckedCommand docker @('context', 'show') | Out-String).Trim()
    $endpoint = (Invoke-CheckedCommand docker @('context', 'inspect', $contextName, '--format', '{{.Endpoints.docker.Host}}') | Out-String).Trim()
  }
  if ($endpoint -notmatch '\A(?:npipe://[./]+pipe/|unix:///)' ) { throw '安全拒绝：Docker endpoint 必须为本机管道或 Unix socket。' }
  $compose = (Invoke-CheckedCommand docker ($composeArguments + @('config', '--format', 'json')) | Out-String) | ConvertFrom-Json
  if ($compose.services.db.environment.POSTGRES_DB -cne 'jiabei' -or
      $compose.services.db.environment.POSTGRES_USER -cne 'jiabei' -or
      $compose.services.backend.environment.SPRING_PROFILES_ACTIVE -cne 'local' -or
      $compose.services.backend.environment.DATABASE_URL -cne 'jdbc:postgresql://db:5432/jiabei') {
    throw '安全拒绝：Docker Compose 数据库或后端目标与本机配置不一致。'
  }
  $password = $compose.services.db.environment.POSTGRES_PASSWORD
  if ($resetMode -eq 'IntegrationTest' -and -not @($compose.services.db.ports | Where-Object { $_.target -eq 5432 -and $_.published -eq '5432' }).Count) {
    throw '安全拒绝：test 模式要求本机 Docker 数据库映射 5432:5432。'
  }
} else {
  $psql = Join-Path $projectRoot '.tools\postgresql-16.15\pgsql\bin\psql.exe'
  $password = 'test'
}

function Invoke-ResetSql([string]$Sql, [switch]$ViaStdin) {
  $sqlArguments = @('-tAc', $Sql)
  $inputSql = $null
  if ($ViaStdin) { $sqlArguments = @('-tA', '-f', '-'); $inputSql = $Sql }
  if ($resolvedRuntime -eq 'Docker') {
    # Explicit socket, user and database prevent inherited PG* variables redirecting the target.
    $connection = "host=/var/run/postgresql port=5432 dbname=$Database user=jiabei connect_timeout=5"
    Invoke-CheckedCommand docker ($composeArguments + @('exec', '-T', 'db', 'psql', '-X', '-w', '-v', 'ON_ERROR_STOP=1', '-d', $connection) + $sqlArguments) $inputSql
  } else {
    $connection = "host=127.0.0.1 hostaddr=127.0.0.1 port=55432 dbname=$Database user=jiabei connect_timeout=5"
    Invoke-CheckedCommand $psql (@('-X', '-w', '-v', 'ON_ERROR_STOP=1', '-d', $connection) + $sqlArguments) $inputSql
  }
}

function Get-VerifiedPortablePostmaster([string]$ActualDirectory, [string]$StartedEpoch) {
  $expectedDirectory = Join-Path $projectRoot '.tools\pgdata'
  Assert-MockDataDirectory -ExpectedDirectory $expectedDirectory -ActualDirectory $ActualDirectory
  $directoryItem = Get-Item -LiteralPath $expectedDirectory -ErrorAction Stop
  for ($ancestor = $directoryItem; $null -ne $ancestor; $ancestor = $ancestor.Parent) {
    if (($ancestor.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) {
      throw '安全拒绝：data_directory 路径含重解析链接，无法证明项目归属。'
    }
  }
  $postmasterLines = @(Get-Content -LiteralPath (Join-Path $expectedDirectory 'postmaster.pid') -ErrorAction Stop)
  if ($postmasterLines.Count -lt 4 -or $postmasterLines[0] -notmatch '\A[1-9][0-9]*\z' -or
      $postmasterLines[2] -notmatch '\A[0-9]+\z' -or $postmasterLines[3] -cne [string]$port) {
    throw '安全拒绝：PostgreSQL postmaster PID/端口记录无效。'
  }
  Assert-MockDataDirectory -ExpectedDirectory $expectedDirectory -ActualDirectory $postmasterLines[1]
  $serverEpoch = [double]::Parse($StartedEpoch, [Globalization.CultureInfo]::InvariantCulture)
  if ([Math]::Abs($serverEpoch - [long]$postmasterLines[2]) -gt 2) { throw '安全拒绝：连接与 postmaster 启动时间不一致。' }
  $postmasterProcess = Get-CimInstance Win32_Process -Filter "ProcessId=$($postmasterLines[0])" -ErrorAction Stop
  $expectedExecutable = Join-Path $projectRoot '.tools\postgresql-16.15\pgsql\bin\postgres.exe'
  if (-not $postmasterProcess -or $postmasterProcess.ExecutablePath -ine $expectedExecutable) { throw '安全拒绝：PostgreSQL 进程不是本项目便携程序。' }
  $listeners = @(Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction Stop)
  if ($listeners.Count -eq 0 -or @($listeners | Where-Object { $_.OwningProcess -ne $postmasterProcess.ProcessId }).Count -gt 0) {
    throw '安全拒绝：PostgreSQL 监听端口不属于已验证 postmaster。'
  }
  return "$($postmasterProcess.ProcessId)|$($postmasterProcess.CreationDate.Ticks)|$($postmasterLines[2])"
}

$identitySql = "SELECT current_database() || '|' || current_user || '|' || coalesce(host(inet_server_addr()),'') || '|' || current_setting('server_version_num') || '|' || current_setting('data_directory') || '|' || extract(epoch from pg_postmaster_start_time())"
$identity = (Invoke-ResetSql $identitySql | Out-String).Trim().Split('|')
if ($identity.Count -ne 6 -or $identity[3] -notmatch '\A[0-9]+\z') { throw '安全拒绝：数据库身份查询结果无效。' }
if ($identity[0] -cne $Database) { throw '安全拒绝：连接到的数据库名与请求不一致。' }
Assert-SafeMockResetTarget -DatabaseHost $databaseHost -Port $port -Database $identity[0] -Profile $Profile -ServerAddress $identity[2]
$databaseOwner = $identity[1]
Assert-SafeMockResetOwner -DatabaseOwner $databaseOwner
if ($resolvedRuntime -eq 'Portable') { $postmasterIdentity = Get-VerifiedPortablePostmaster $identity[4] $identity[5] }

Write-Host "目标：$resolvedRuntime / ${databaseHost}:$port / $Database / Profile=$Profile / owner=$databaseOwner / PostgreSQL=$($identity[3])"
Write-Host '迁移：V1 / V3 / V4 / V5 / V6 / V7__complex_mock_seed.sql'
Write-Host '预计：51 用户（1 超管、5 运营、5 观察员、10 化妆师、30 主播）；10 化妆师资源；10 团队。'
Write-Host '预约：今天 100、明天 100、前三天 34/33/33，共 300；预约审计 CREATE=300、MODIFY=40、CANCEL=189。'
if ($resetMode -eq 'IntegrationTest') {
  Write-Host '执行分支：重建 public 后，用内置 JDK/Maven 运行 ComplexMockDataExternalIT 完成 Flyway 迁移与合同验证；不启动常驻应用。'
} else {
  Write-Host '执行分支：停止本项目 backend，重建 public，通过本地启动流程运行 Flyway，再检查健康状态。'
}
if ($DryRun) { Write-Host 'DryRun：仅查询目标身份；未停止服务、未写入数据库。'; return }

# All prerequisites are checked before confirmation and before stopping anything.
$java = Join-Path $projectRoot '.tools\jdk\jdk-21.0.12.1+1\bin\java.exe'
$mavenRoot = Join-Path $projectRoot '.tools\maven\apache-maven-3.9.16'
$jar = Join-Path $projectRoot 'src\backend\target\jiabei-cloud-backend-1.0.0.jar'
function Get-VerifiedBackendProcess {
  $pidFile = Join-Path $projectRoot '.tools\backend.pid'
  if (-not (Test-Path -LiteralPath $pidFile)) { return $null }
  $processId = 0
  if (-not [int]::TryParse((Get-Content -LiteralPath $pidFile -Raw).Trim(), [ref]$processId) -or $processId -le 0) { throw '安全拒绝：backend PID 文件无效。' }
  $process = Get-CimInstance Win32_Process -Filter "ProcessId=$processId" -ErrorAction Stop
  if (-not $process) { return $null }
  if ($process.ExecutablePath -ine $java) { throw '安全拒绝：backend 不是本项目 Java 程序。' }
  foreach ($argument in @($jar, '--spring.profiles.active=local', '--spring.datasource.url=jdbc:postgresql://127.0.0.1:55432/jiabei')) {
    if ($process.CommandLine -notmatch ('(?:^|[\s"])' + [regex]::Escape($argument) + '(?:[\s"]|$)')) { throw '安全拒绝：backend 命令不是本项目本机后端。' }
  }
  return $process
}
if ($resetMode -eq 'IntegrationTest') {
  $launcher = @(Get-ChildItem -LiteralPath (Join-Path $mavenRoot 'boot') -Filter 'plexus-classworlds-*.jar')
  if (-not (Test-Path -LiteralPath $java) -or $launcher.Count -ne 1 -or
      -not (Test-Path -LiteralPath (Join-Path $mavenRoot 'bin\m2.conf'))) { throw '安全取消：内置 JDK/Maven 不完整。' }
  $connections = [int](Invoke-ResetSql 'SELECT count(*) FROM pg_stat_activity WHERE datname=current_database() AND pid<>pg_backend_pid()' | Out-String).Trim()
  if ($connections -ne 0) { throw '安全拒绝：隔离测试库仍有其他连接，请先停止使用该库的进程。' }
} elseif ($resolvedRuntime -eq 'Portable') {
  if ((Resolve-MockRuntime -Requested Auto -ProjectRoot $projectRoot) -ne 'Portable') {
    throw '安全取消：start-local 会选择 Docker；请使用 Auto 或先使运行方式一致。'
  }
  foreach ($required in @($java, $jar, (Join-Path $projectRoot 'src\frontend\dist\index.html'), (Join-Path $PSScriptRoot 'StaticSpaServer.java'))) {
    if (-not (Test-Path -LiteralPath $required)) { throw "安全取消：启动文件缺失 $required" }
  }
  Add-Type -AssemblyName System.IO.Compression.FileSystem
  $archive = [System.IO.Compression.ZipFile]::OpenRead($jar)
  try {
    if (-not $archive.GetEntry('BOOT-INF/classes/db/local/V7__complex_mock_seed.sql')) { throw '安全取消：后端 jar 尚未包含 V7，请先构建后端。' }
  } finally { $archive.Dispose() }
  $backendProcess = Get-VerifiedBackendProcess
  $listeners = @(Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue)
  foreach ($listener in $listeners) {
    if (-not $backendProcess -or $listener.OwningProcess -ne $backendProcess.ProcessId) { throw '安全拒绝：8080 端口由未验证的进程占用。' }
  }
}

if ($resetMode -eq 'IntegrationTest') {
  $jdbcUrl = "jdbc:postgresql://127.0.0.1:$port/$Database"
  Assert-SafeMockResetTarget -DatabaseHost '127.0.0.1' -Port $port -Database $Database -Profile $Profile -ServerAddress '127.0.0.1'
  $mavenArguments = @("-Dmaven.home=$mavenRoot", "-Dclassworlds.conf=$(Join-Path $mavenRoot 'bin\m2.conf')",
    "-Dmaven.multiModuleProjectDirectory=$(Join-Path $projectRoot 'src\backend')", '-cp', $launcher[0].FullName,
    'org.codehaus.plexus.classworlds.launcher.Launcher', '-B', '-Dtest=ComplexMockDataExternalIT', 'test')
  $processInfo = New-Object System.Diagnostics.ProcessStartInfo
  $processInfo.FileName = $java
  $processInfo.WorkingDirectory = Join-Path $projectRoot 'src\backend'
  $processInfo.UseShellExecute = $false
  $processInfo.CreateNoWindow = $true
  $processInfo.Arguments = ($mavenArguments | ForEach-Object {
    if ($_.Contains('"')) { throw '安全拒绝：工具路径包含非法引号。' }
    '"' + $_ + '"'
  }) -join ' '
  # Task-specific values exist only in this child; current and persistent environment are untouched.
  $processInfo.EnvironmentVariables['JIABEI_IT_JDBC_URL'] = $jdbcUrl
  $processInfo.EnvironmentVariables['JIABEI_IT_DATABASE_USER'] = $databaseOwner
  $processInfo.EnvironmentVariables['JIABEI_IT_DATABASE_PASSWORD'] = $password
}

if (-not $Force) {
  $answer = Read-Host "将重建 $Database 的 public schema。请输入数据库名确认"
  if ($answer -cne $Database) { Write-Host '已取消。'; return }
}
if ($resetMode -eq 'Application') {
  # Capture the restart boundary before stopping the verified process. This keeps
  # a clear ordering gap despite Windows process creation timestamps having
  # coarser precision than Get-Date on some hosts.
  $restartBoundary = Get-Date
  if ($resolvedRuntime -eq 'Docker') {
    Invoke-CheckedCommand docker ($composeArguments + @('stop', 'backend')) | Out-Host
  } elseif ($backendProcess) {
    # Recheck identity immediately before stopping to guard against a stale PID file.
    $currentProcess = Get-CimInstance Win32_Process -Filter "ProcessId=$($backendProcess.ProcessId)"
    if ($currentProcess -and ($currentProcess.CreationDate -ne $backendProcess.CreationDate -or $currentProcess.CommandLine -cne $backendProcess.CommandLine)) { throw '安全拒绝：backend 进程身份已改变。' }
    if ($currentProcess) { Stop-Process -Id $backendProcess.ProcessId -Force -ErrorAction Stop }
    Wait-MockBackendStopped -ProcessId $backendProcess.ProcessId
  }
}

# PostgreSQL needs CASCADE for a populated schema. Recreate atomically as the verified owner.
$resetSql = 'BEGIN; DROP SCHEMA public CASCADE; CREATE SCHEMA public AUTHORIZATION "' + $databaseOwner + '"; COMMIT;'
# Re-read server identity and local ownership after confirmation/stop, immediately before DROP.
$currentIdentity = (Invoke-ResetSql $identitySql | Out-String).Trim().Split('|')
if (($currentIdentity -join '|') -cne ($identity -join '|')) { throw '安全拒绝：数据库连接身份已改变。' }
if ($resolvedRuntime -eq 'Portable' -and (Get-VerifiedPortablePostmaster $currentIdentity[4] $currentIdentity[5]) -cne $postmasterIdentity) {
  throw '安全拒绝：PostgreSQL postmaster 身份已改变。'
}
Invoke-ResetSql $resetSql -ViaStdin | Out-Host

if ($resetMode -eq 'IntegrationTest') {
  $migrationProcess = [System.Diagnostics.Process]::Start($processInfo)
  try {
    $migrationProcess.WaitForExit()
    if ($migrationProcess.ExitCode -ne 0) { throw "集成迁移失败 (exit $($migrationProcess.ExitCode))。" }
  } finally { $migrationProcess.Dispose() }
} else {
  if ($resolvedRuntime -eq 'Docker') {
    Invoke-CheckedCommand docker ($composeArguments + @('up', '--build', '-d')) | Out-Host
  } else {
    Invoke-CheckedCommand powershell @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', (Join-Path $PSScriptRoot 'start-local.ps1')) | Out-Host
  }
  $healthy = $false
  for ($attempt = 0; $attempt -lt 90; $attempt++) {
    try {
      if ($resolvedRuntime -eq 'Portable') {
        $newBackend = Get-VerifiedBackendProcess
        $newListeners = @(Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction Stop)
        Assert-MockBackendStarted -Process $newBackend -NotBefore $restartBoundary -ListenerProcessIds @($newListeners | ForEach-Object { $_.OwningProcess })
      }
      $health = Invoke-RestMethod -Uri 'http://127.0.0.1:8080/actuator/health' -TimeoutSec 2
      if ($health.status -eq 'UP') { $healthy = $true; break }
    } catch { }
    Start-Sleep -Seconds 1
  }
  if (-not $healthy) { throw '重置后健康检查未通过；请查看后端日志。' }
}
Write-Host '角色数量：'
Invoke-ResetSql 'SELECT role,count(*) FROM app_user GROUP BY role ORDER BY role' | Out-Host
Write-Host '资源数量（化妆师/团队）：'
Invoke-ResetSql 'SELECT (SELECT count(*) FROM makeup_artist),(SELECT count(*) FROM team)' | Out-Host
Write-Host '五日预约（日期/总数/有效/取消）：'
Invoke-ResetSql "SELECT booking_date,count(*),count(*) FILTER (WHERE status='ACTIVE'),count(*) FILTER (WHERE status='CANCELLED') FROM appointment WHERE booking_date BETWEEN (now() AT TIME ZONE 'Asia/Shanghai')::date-3 AND (now() AT TIME ZONE 'Asia/Shanghai')::date+1 GROUP BY booking_date ORDER BY booking_date" | Out-Host
Write-Host '审计摘要：'
Invoke-ResetSql 'SELECT entity_type,action,count(*) FROM audit_log GROUP BY entity_type,action ORDER BY entity_type,action' | Out-Host
Write-Host '本机 Mock 重置完成。'
