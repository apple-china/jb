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

function Assert-MockDataDirectory {
  param([string]$ExpectedDirectory, [string]$ActualDirectory)
  if (-not [System.IO.Path]::IsPathRooted($ActualDirectory)) { throw '安全拒绝：data_directory 不是绝对路径。' }
  try {
    $expectedPath = [System.IO.Path]::GetFullPath($ExpectedDirectory).TrimEnd('\', '/')
    $actualPath = [System.IO.Path]::GetFullPath($ActualDirectory).TrimEnd('\', '/')
  } catch { throw '安全拒绝：data_directory 路径无效。' }
  if ($actualPath -ine $expectedPath) { throw '安全拒绝：PostgreSQL data_directory 不属于本项目。' }
}

function Assert-MockResetConfiguration {
  param([System.Collections.IDictionary]$Configuration)
  $profiles = @($Configuration['SPRING_PROFILES_ACTIVE'], $Configuration['SPRING_PROFILES_DEFAULT'])
  $urls = @($Configuration['DATABASE_URL'], $Configuration['SPRING_DATASOURCE_URL'])
  if ($Configuration['SPRING_APPLICATION_JSON']) {
    try { $settings = $Configuration['SPRING_APPLICATION_JSON'] | ConvertFrom-Json }
    catch { throw '安全拒绝：Spring 应用配置无法解析。' }
    # Only settings which can affect this application's profile/datasource are relevant.
    foreach ($property in $settings.PSObject.Properties) {
      if ($property.Name -in @('spring.profiles.active','spring.profiles.default')) { $profiles += $property.Value }
      if ($property.Name -eq 'spring.datasource.url') { $urls += $property.Value }
      if ($property.Name -eq 'spring') {
        foreach ($springProperty in $property.Value.PSObject.Properties) {
          if ($springProperty.Name -eq 'profiles') {
            foreach ($profileProperty in $springProperty.Value.PSObject.Properties) {
              if ($profileProperty.Name -in @('active','default')) { $profiles += $profileProperty.Value }
            }
          }
          if ($springProperty.Name -eq 'datasource') {
            foreach ($sourceProperty in $springProperty.Value.PSObject.Properties) {
              if ($sourceProperty.Name -eq 'url') { $urls += $sourceProperty.Value }
            }
          }
        }
      }
    }
  }
  foreach ($configuredProfile in $profiles) {
    if ($configuredProfile -and $configuredProfile -match '(?i)(?:^|[,;\s])prod(?:uction)?(?:$|[,;\s])') {
      throw '安全拒绝：当前应用含生产配置 Profile。'
    }
  }
  foreach ($configuredUrl in $urls) {
    if (-not $configuredUrl) { continue }
    # Do not echo the URL: it may contain credentials.
    if ($configuredUrl -notmatch '\Ajdbc:postgresql://(127\.0\.0\.1|localhost|db)(?::(5432|55432))?/(jiabei(?:_mock_it)?)(?:\?[^\r\n]*)?\z') {
      throw '安全拒绝：当前应用数据库配置不是白名单本机 JDBC 目标。'
    }
  }
}

function Wait-MockBackendStopped {
  param([int]$ProcessId, [ValidateRange(1,120)][int]$Attempts = 60)
  for ($poll = 0; $poll -lt $Attempts; $poll++) {
    $process = Get-Process -Id $ProcessId -ErrorAction SilentlyContinue
    $listeners = @(Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue)
    if (-not $process -and $listeners.Count -eq 0) { return }
    if ($poll -lt $Attempts - 1) { Start-Sleep -Milliseconds 500 }
  }
  throw '安全取消：backend PID 尚未退出或 8080 端口尚未释放。'
}

function Assert-MockBackendStarted {
  param($Process, [datetime]$NotBefore, [int[]]$ListenerProcessIds)
  if (-not $Process -or $Process.CreationDate -lt $NotBefore -or @($ListenerProcessIds).Count -eq 0 -or
      @($ListenerProcessIds | Where-Object { $_ -ne $Process.ProcessId }).Count -gt 0) {
    throw '后端健康检查尚未对应新启动且持有 8080 端口的本项目进程。'
  }
}

Export-ModuleMember -Function Assert-SafeMockResetTarget, Assert-SafeMockResetOwner, Resolve-MockRuntime, Get-MockResetMode, Assert-MockDataDirectory, Assert-MockResetConfiguration, Wait-MockBackendStopped, Assert-MockBackendStarted
