Set-StrictMode -Version Latest

function Assert-SafeMockResetTarget {
  param([string]$DatabaseHost, [int]$Port, [string]$Database, [string]$Profile,
    [AllowEmptyString()][string]$ServerAddress)
  if ($DatabaseHost -cnotin @('127.0.0.1', 'localhost', 'db')) {
    throw '安全拒绝：仅允许本机数据库目标。'
  }
  if ($Port -notin @(5432, 55432) -or ($DatabaseHost -ceq 'db' -and $Port -ne 5432)) {
    throw '安全拒绝：数据库端口仅允许 5432/55432，Docker db 必须使用 5432。'
  }
  if ($Profile -cnotin @('local', 'test')) { throw '安全拒绝：Profile 仅允许 local/test。' }
  if ($Database -cnotmatch '\Ajiabei(?:_mock_it)?\z') { throw '安全拒绝：数据库名仅允许 jiabei/jiabei_mock_it。' }
  if ($ServerAddress -ne '') {
    $address = $null
    if (-not [System.Net.IPAddress]::TryParse($ServerAddress, [ref]$address) -or
        -not [System.Net.IPAddress]::IsLoopback($address)) {
      throw '安全拒绝：服务器实际地址不是本机回环地址。'
    }
  }
}

function Assert-SafeMockResetOwner {
  param([string]$DatabaseOwner)
  if ($DatabaseOwner -cnotmatch '\A[A-Za-z_][A-Za-z0-9_]*\z') {
    throw '安全拒绝：数据库 owner 未通过标识符白名单。'
  }
}

function Resolve-MockRuntime {
  param([ValidateSet('Auto', 'Docker', 'Portable')][string]$Requested = 'Auto',
    [Parameter(Mandatory = $true)][string]$ProjectRoot)
  if ($Requested -ne 'Portable') {
    $dockerReady = $false
    if (Get-Command docker -ErrorAction SilentlyContinue) {
      $previousPreference = $ErrorActionPreference
      try {
        $ErrorActionPreference = 'Continue'
        & docker info *> $null
        $dockerReady = $LASTEXITCODE -eq 0
      } finally { $ErrorActionPreference = $previousPreference }
    }
    if ($dockerReady) { return 'Docker' }
    if ($Requested -eq 'Docker') { throw 'Docker daemon 不可用，安全取消。' }
  }
  $psql = Join-Path $ProjectRoot '.tools\postgresql-16.15\pgsql\bin\psql.exe'
  $data = Join-Path $ProjectRoot '.tools\pgdata'
  if (-not (Test-Path -LiteralPath $psql -PathType Leaf) -or
      -not (Test-Path -LiteralPath $data -PathType Container)) {
    throw '便携 PostgreSQL 不完整：需要 psql.exe 和 .tools/pgdata。'
  }
  return 'Portable'
}

function Get-MockResetMode {
  param([string]$Profile, [string]$Database)
  if ($Profile -ceq 'local' -and $Database -ceq 'jiabei') { return 'Application' }
  if ($Profile -ceq 'test' -and $Database -ceq 'jiabei_mock_it') { return 'IntegrationTest' }
  throw '安全拒绝：重置组合仅支持 local/jiabei 或 test/jiabei_mock_it。'
}

Export-ModuleMember -Function Assert-SafeMockResetTarget, Assert-SafeMockResetOwner, Resolve-MockRuntime, Get-MockResetMode
