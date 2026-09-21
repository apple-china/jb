$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)

function Assert-Contains([string]$Content, [string]$Expected, [string]$Message) {
  if (-not $Content.Contains($Expected)) { throw $Message }
}

$compose = Get-Content -LiteralPath (Join-Path $root 'docker-compose.production.yml') -Raw
$productionConfig = Get-Content -LiteralPath (Join-Path $root 'src\backend\src\main\resources\application-production.yml') -Raw

Assert-Contains $compose 'name: jiabei-production' 'Production Compose must have an isolated project name.'
Assert-Contains $compose 'SPRING_PROFILES_ACTIVE: production' 'Production Compose must activate only the production profile.'
Assert-Contains $compose 'PRODUCTION_DATABASE_NAME:?' 'Production database name must be required.'
Assert-Contains $compose 'PRODUCTION_DATABASE_PASSWORD:?' 'Production database password must be required.'
Assert-Contains $compose 'postgres-data:/var/lib/postgresql/data' 'Production database must use its own named volume.'
Assert-Contains $compose 'uploads-data:/app/uploads' 'Production uploads must use their own named volume.'
$portBlocks = ([regex]::Matches($compose, '(?m)^\s+ports:\s*$')).Count
if ($portBlocks -ne 1) { throw 'Production Compose must publish only the database loopback port.' }
Assert-Contains $compose '"127.0.0.1:5433:5432"' 'Production database management port must bind only to loopback.'
Assert-Contains $compose 'VITE_MODE: production' 'Production frontend must use the production Vite mode.'
Assert-Contains $compose 'DINGTALK_CLIENT_ID: ${DINGTALK_CLIENT_ID:-}' 'Production frontend must use the unified public Client ID.'
Assert-Contains $compose 'DINGTALK_CORP_ID: ${DINGTALK_CORP_ID:-}' 'Production frontend must use the unified public Corp ID.'
if ($compose -match 'VITE_DINGTALK_|VITE_ENABLE_MOCK_LOGIN') { throw 'Legacy Vite DingTalk configuration must not remain.' }
Assert-Contains $compose 'DINGTALK_ENABLED: ${DINGTALK_ENABLED:-false}' 'Production DingTalk integration must default to disabled.'
Assert-Contains $compose 'MOREDIAN_ENABLED: ${MOREDIAN_ENABLED:-false}' 'Production Moredian integration must default to disabled.'
Assert-Contains $compose 'jiabei-production-frontend' 'Production frontend must expose a unique network alias for Nginx.'
Assert-Contains $compose 'outbound-net' 'Production backend must have a dedicated outbound network for third-party APIs.'
if ($compose -notmatch '(?ms)^  backend-net:\s*\r?\n    internal: true\s*$') {
  throw 'The production application/database network must remain internal.'
}
$dbBlock = [regex]::Match($compose, '(?ms)^  db:.*?(?=^  backend:)').Value
$backendBlock = [regex]::Match($compose, '(?ms)^  backend:.*?(?=^  frontend:)').Value
if ($backendBlock -notmatch '(?ms)^    networks:\s*\r?\n      - backend-net\s*\r?\n      - outbound-net\s*$') {
  throw 'Only the production backend should attach to the dedicated outbound network.'
}
if ($dbBlock -match 'outbound-net') {
  throw 'The production database must not attach to the outbound network.'
}
Assert-Contains $compose 'condition: service_healthy' 'Production dependencies must wait for health checks.'
Assert-Contains $compose 'restart: unless-stopped' 'Production services must define a restart policy.'
Assert-Contains $compose 'max-size: 10m' 'Production logs must be size limited.'

Assert-Contains $productionConfig 'url: ${DATABASE_URL}' 'Production datasource URL must not inherit a local default.'
Assert-Contains $productionConfig 'password: ${DATABASE_PASSWORD}' 'Production datasource password must not inherit a local default.'
Assert-Contains $productionConfig 'allowed-origins: ${ALLOWED_ORIGINS}' 'Production allowed origins must be explicit.'
Assert-Contains $productionConfig 'enabled: ${DINGTALK_ENABLED:false}' 'Production DingTalk beans must require the explicit enable switch.'
Assert-Contains $productionConfig 'enabled: ${MOREDIAN_ENABLED:false}' 'Production Moredian callback must require the explicit enable switch.'

if ($compose -match 'jiabei_dingtalk_test|jb\.huixinghub\.top|jiabei_local|SPRING_PROFILES_ACTIVE:\s*(local|dingtalk-test)') {
  throw 'Production Compose must not reference test/local resources or profiles.'
}

Write-Host 'PASS Production deployment contract'
