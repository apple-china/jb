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
