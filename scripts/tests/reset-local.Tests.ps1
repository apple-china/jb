$ErrorActionPreference = 'Stop'
Import-Module (Join-Path $PSScriptRoot '..\lib\MockResetSafety.psm1') -Force

function Assert-Throws([scriptblock]$Action, [string]$Expected) {
  $caught = $false
  try { & $Action }
  catch {
    $caught = $true
    if (-not $_.Exception.Message.Contains($Expected)) { throw }
  }
  if (-not $caught) { throw "Expected exception containing: $Expected" }
}

Assert-SafeMockResetTarget -DatabaseHost '127.0.0.1' -Port 55432 -Database 'jiabei' -Profile 'local' -ServerAddress '127.0.0.1'
Assert-SafeMockResetTarget -DatabaseHost 'localhost' -Port 5432 -Database 'jiabei_mock_it' -Profile 'test' -ServerAddress '127.0.0.1'
Assert-SafeMockResetTarget -DatabaseHost 'localhost' -Port 5432 -Database 'jiabei' -Profile 'test' -ServerAddress '::1'
Assert-SafeMockResetTarget -DatabaseHost 'db' -Port 5432 -Database 'jiabei' -Profile 'local' -ServerAddress ''
Assert-Throws { Assert-SafeMockResetTarget -DatabaseHost 'db.prod.example' -Port 5432 -Database 'jiabei' -Profile 'local' -ServerAddress '10.0.0.4' } '本机'
Assert-Throws { Assert-SafeMockResetTarget -DatabaseHost '127.0.0.1' -Port 5432 -Database 'jiabei' -Profile 'production' -ServerAddress '127.0.0.1' } 'Profile'
Assert-Throws { Assert-SafeMockResetTarget -DatabaseHost '127.0.0.1' -Port 6432 -Database 'jiabei' -Profile 'test' -ServerAddress '127.0.0.1' } '端口'
Assert-Throws { Assert-SafeMockResetTarget -DatabaseHost '127.0.0.1' -Port 5432 -Database 'postgres' -Profile 'test' -ServerAddress '127.0.0.1' } '数据库名'
Assert-Throws { Assert-SafeMockResetTarget -DatabaseHost '127.0.0.1' -Port 5432 -Database 'jiabei;DROP DATABASE postgres' -Profile 'test' -ServerAddress '127.0.0.1' } '数据库名'
Assert-Throws { Assert-SafeMockResetTarget -DatabaseHost '127.0.0.1' -Port 5432 -Database 'jiabei' -Profile 'test' -ServerAddress '10.0.0.4' } '本机'
Assert-Throws { Assert-SafeMockResetTarget -DatabaseHost 'db' -Port 55432 -Database 'jiabei' -Profile 'test' -ServerAddress '' } '端口'
Assert-Throws { Assert-SafeMockResetTarget -DatabaseHost 'localhost' -Port 5432 -Database 'jiabei' -Profile 'test' -ServerAddress 'localhost' } '本机'
Assert-SafeMockResetOwner -DatabaseOwner 'jiabei'
Assert-SafeMockResetOwner -DatabaseOwner '_local123'
Assert-Throws { Assert-SafeMockResetOwner -DatabaseOwner 'jiabei"; DROP ROLE postgres; --' } 'owner'
Assert-Throws { Resolve-MockRuntime -Requested Portable -ProjectRoot $PSScriptRoot } '便携'
if ((Get-MockResetMode -Profile local -Database jiabei) -ne 'Application') { throw 'Wrong local mode' }
if ((Get-MockResetMode -Profile test -Database jiabei_mock_it) -ne 'IntegrationTest') { throw 'Wrong test mode' }
Assert-Throws { Get-MockResetMode -Profile test -Database jiabei } '组合'
Assert-Throws { Get-MockResetMode -Profile local -Database jiabei_mock_it } '组合'
Write-Host 'PASS reset-local safety rules (20 cases)'

Assert-MockDataDirectory -ExpectedDirectory 'D:\repo\.tools\pgdata' -ActualDirectory 'd:/repo/.tools/pgdata/'
Assert-Throws { Assert-MockDataDirectory -ExpectedDirectory 'D:\repo\.tools\pgdata' -ActualDirectory 'D:\other\.tools\pgdata' } 'data_directory'
Assert-Throws { Assert-MockDataDirectory -ExpectedDirectory 'D:\repo\.tools\pgdata' -ActualDirectory 'D:\repo\.tools\pgdata-old' } 'data_directory'
Assert-Throws { Assert-MockDataDirectory -ExpectedDirectory 'D:\repo\.tools\pgdata' -ActualDirectory '.tools\pgdata' } 'data_directory'
Assert-MockResetConfiguration @{ DATABASE_URL='jdbc:postgresql://localhost:5432/jiabei'; SPRING_PROFILES_ACTIVE='local'; OTHER_SERVICE_URL='https://production.example' }
Assert-MockResetConfiguration @{ DATABASE_URL=''; SPRING_PROFILES_ACTIVE='test'; OTHER_JDBC_URL='jdbc:postgresql://prod:5432/other' }
Assert-Throws { Assert-MockResetConfiguration @{ SPRING_PROFILES_ACTIVE='local,production' } } '生产配置'
Assert-Throws { Assert-MockResetConfiguration @{ DATABASE_URL='jdbc:postgresql://prod.example:5432/jiabei?password=secret' } } '数据库配置'
Assert-Throws { Assert-MockResetConfiguration @{ SPRING_DATASOURCE_URL='jdbc:postgresql://127.0.0.1:6432/jiabei' } } '数据库配置'
Assert-Throws { Assert-MockResetConfiguration @{ SPRING_APPLICATION_JSON='{"spring":{"profiles":{"active":"production"}}}' } } '生产配置'
Assert-Throws { Assert-MockResetConfiguration @{ SPRING_APPLICATION_JSON='{"spring.datasource.url":"jdbc:postgresql://prod:5432/jiabei"}' } } '数据库配置'

$safetyModule = Get-Module MockResetSafety
& $safetyModule {
  # Only OS polling is replaced; the real wait loop must observe both exit and port release.
  $script:poll = 0
  function Get-Process { if ($script:poll -eq 0) { [pscustomobject]@{ Id=42 } } }
  function Get-NetTCPConnection { if ($script:poll -lt 2) { [pscustomobject]@{ OwningProcess=42 } } }
  function Start-Sleep { $script:poll++ }
  try {
    Wait-MockBackendStopped -ProcessId 42 -Attempts 3
    if ($script:poll -ne 2) { throw 'Stop wait failed to require process exit AND released port' }
    $script:poll = 0
    $threw = $false
    try { Wait-MockBackendStopped -ProcessId 42 -Attempts 2 } catch { $threw = $_.Exception.Message.Contains('8080') }
    if (-not $threw) { throw 'Stop wait must fail before reset while port remains occupied' }
  } finally {
    Remove-Item Function:Get-Process,Function:Get-NetTCPConnection,Function:Start-Sleep
  }
}
Write-Host 'PASS reset-local ownership/configuration/stop guards (13 cases)'

$restartAt = [datetime]'2026-09-09T12:00:00'
Assert-MockBackendStarted -Process ([pscustomobject]@{ ProcessId=43; CreationDate=$restartAt.AddSeconds(1) }) -NotBefore $restartAt -ListenerProcessIds @(43)
Assert-Throws { Assert-MockBackendStarted -Process ([pscustomobject]@{ ProcessId=42; CreationDate=$restartAt.AddSeconds(-1) }) -NotBefore $restartAt -ListenerProcessIds @(42) } '新启动'
Assert-Throws { Assert-MockBackendStarted -Process ([pscustomobject]@{ ProcessId=43; CreationDate=$restartAt.AddSeconds(1) }) -NotBefore $restartAt -ListenerProcessIds @(99) } '新启动'
Assert-Throws { Assert-MockBackendStarted -Process ([pscustomobject]@{ ProcessId=43; CreationDate=$restartAt.AddSeconds(1) }) -NotBefore $restartAt -ListenerProcessIds @() } '新启动'
Write-Host 'PASS reset-local fresh backend identity guards (4 cases)'
