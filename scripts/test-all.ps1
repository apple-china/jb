$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot

& powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $root 'scripts\tests\reset-local.Tests.ps1')
if ($LASTEXITCODE -ne 0) { throw 'reset-local safety tests failed.' }

& powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $root 'scripts\tests\dingtalk-test-deployment.Tests.ps1')
if ($LASTEXITCODE -ne 0) { throw 'DingTalk test deployment contract failed.' }

& powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $root 'scripts\tests\production-deployment.Tests.ps1')
if ($LASTEXITCODE -ne 0) { throw 'Production deployment contract failed.' }

& powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $root 'scripts\tests\environment-migration.Tests.ps1')
if ($LASTEXITCODE -ne 0) { throw 'Dev/prod environment migration contract failed.' }

$maven = Get-Command mvn -ErrorAction SilentlyContinue
if (-not $maven) {
  $portableJdk = Join-Path $root '.tools\jdk\jdk-21.0.12.1+1'
  $portableMaven = Join-Path $root '.tools\maven\apache-maven-3.9.16\bin\mvn.cmd'
  if (-not (Test-Path -LiteralPath $portableMaven) -or -not (Test-Path -LiteralPath (Join-Path $portableJdk 'bin\java.exe'))) {
    throw 'Maven is unavailable and the project portable Maven/JDK runtime is incomplete.'
  }
  $env:JAVA_HOME = $portableJdk
  $maven = $portableMaven
}

$mavenRepository = Join-Path ([Environment]::GetFolderPath('UserProfile')) '.m2\repository'
function Invoke-Maven([string[]]$Arguments) {
  & $maven "-Dmaven.repo.local=$mavenRepository" @Arguments
  if ($LASTEXITCODE -ne 0) {
    throw "Maven failed with exit code $LASTEXITCODE."
  }
}

$dockerReady = $false
if (Get-Command docker -ErrorAction SilentlyContinue) {
  $previousErrorPreference = $ErrorActionPreference
  $ErrorActionPreference = 'SilentlyContinue'
  & docker info *> $null
  $dockerReady = $LASTEXITCODE -eq 0
  $ErrorActionPreference = $previousErrorPreference
}

Push-Location "$root/src/backend"
try {
  Invoke-Maven @('test')
  if ($env:JIABEI_IT_JDBC_URL) {
    Invoke-Maven @('-Dtest=ExternalPostgreSqlIT,CardMockExternalIT,ProductionMigrationExternalIT', 'test')
  } elseif ($dockerReady) {
    Invoke-Maven @('-Dtest=PostgreSqlConstraintIT', 'test')
  } else {
    Write-Warning '未设置 JIABEI_IT_JDBC_URL 且无 Docker：数据库集成测试未执行。'
  }
} finally { Pop-Location }

Push-Location "$root/src/frontend"
try {
  pnpm test
  pnpm run build
  pnpm test:e2e
} finally { Pop-Location }

Write-Host '常规单元测试、前端构建与 Playwright 已完成。'
Write-Host '数据库集成测试状态见上方输出；执行约定详见 README。'
