$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)

function Assert-Contains([string]$Content, [string]$Expected, [string]$Message) {
  if (-not $Content.Contains($Expected)) { throw $Message }
}

function Assert-UniqueMigrationVersions([string[]]$Directories, [string]$Message) {
  $versions = foreach ($directory in $Directories) {
    Get-ChildItem -LiteralPath $directory -Filter 'V*__*.sql' | ForEach-Object {
      if ($_.BaseName -match '^V([^_]+)__') { $Matches[1] }
    }
  }
  $duplicates = $versions | Group-Object | Where-Object Count -gt 1
  if ($duplicates) {
    throw "$Message Duplicate versions: $($duplicates.Name -join ', ')."
  }
}

$dockerfile = Get-Content -LiteralPath (Join-Path $root 'src\frontend\Dockerfile') -Raw
$baseCompose = Get-Content -LiteralPath (Join-Path $root 'docker-compose.yml') -Raw
$envExample = Get-Content -LiteralPath (Join-Path $root '.env.example') -Raw
$testComposePath = Join-Path $root 'docker-compose.dingtalk-test.yml'

if (-not (Test-Path -LiteralPath $testComposePath)) {
  throw 'Missing docker-compose.dingtalk-test.yml.'
}

$testCompose = Get-Content -LiteralPath $testComposePath -Raw

Assert-Contains $baseCompose 'SPRING_PROFILES_ACTIVE: local' 'Base Compose must keep the local profile.'
if ($envExample -match '(?m)^DINGTALK_TEST_DATABASE_PASSWORD=.+$') { throw 'Example file must not contain a test database password.' }
Assert-Contains $dockerfile 'ARG VITE_ENABLE_MOCK_LOGIN=true' 'Missing mock-login build argument.'
Assert-Contains $dockerfile 'ARG VITE_DINGTALK_AUTO_LOGIN=false' 'Missing auto-login build argument.'
Assert-Contains $dockerfile 'ARG VITE_DINGTALK_TEST_DIAGNOSTICS=false' 'Missing diagnostics build argument.'
Assert-Contains $testCompose 'SPRING_PROFILES_ACTIVE: dingtalk-test' 'Test override must enable dingtalk-test.'
Assert-Contains $testCompose 'jdbc:postgresql://db:5432/jiabei_dingtalk_test' 'Test override must use the isolated database.'
Assert-Contains $testCompose 'jiabei_dingtalk_test_pgdata:/var/lib/postgresql/data' 'Test override must use an isolated database volume.'
Assert-Contains $testCompose 'VITE_ENABLE_MOCK_LOGIN: "false"' 'Test frontend must disable direct mock login.'
Assert-Contains $testCompose 'VITE_DINGTALK_AUTO_LOGIN: "true"' 'Test frontend must enable automatic DingTalk login.'
Assert-Contains $testCompose 'VITE_DINGTALK_TEST_DIAGNOSTICS: "true"' 'Test frontend must enable diagnostics.'
Assert-Contains $testCompose 'DINGTALK_GROUP_OPEN_CONVERSATION_ID: ${DINGTALK_GROUP_OPEN_CONVERSATION_ID:?' 'Test backend must require the target group ID.'
Assert-Contains $testCompose 'DINGTALK_CARD_TEMPLATE_ID: ${DINGTALK_CARD_TEMPLATE_ID:?' 'Test backend must require the card template ID.'
Assert-Contains $testCompose 'APP_ENTRY_URL: ${APP_ENTRY_URL:?' 'Test backend must require the public card entry URL.'
Assert-UniqueMigrationVersions @(
  (Join-Path $root 'src\backend\src\main\resources\db\migration'),
  (Join-Path $root 'src\backend\src\main\resources\db\dingtalk-test')
) 'DingTalk test Flyway locations must use unique migration versions.'
Assert-UniqueMigrationVersions @(
  (Join-Path $root 'src\backend\src\main\resources\db\migration'),
  (Join-Path $root 'src\backend\src\main\resources\db\local')
) 'Local Flyway locations must use unique migration versions.'

Write-Host 'PASS DingTalk test deployment contract (16 cases)'