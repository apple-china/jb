$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)

function Assert-Contains([string]$Content, [string]$Expected, [string]$Message) {
  if (-not $Content.Contains($Expected)) { throw $Message }
}

$compose = Get-Content -LiteralPath (Join-Path $root 'docker-compose.production.yml') -Raw
$envExample = Get-Content -LiteralPath (Join-Path $root '.env.production.example') -Raw
$productionConfig = Get-Content -LiteralPath (Join-Path $root 'src\backend\src\main\resources\application-production.yml') -Raw
$gitignore = Get-Content -LiteralPath (Join-Path $root '.gitignore') -Raw

Assert-Contains $compose 'name: jiabei-production' 'Production Compose must have an isolated project name.'
Assert-Contains $compose 'SPRING_PROFILES_ACTIVE: production' 'Production Compose must activate only the production profile.'
Assert-Contains $compose 'PRODUCTION_DATABASE_NAME:?' 'Production database name must be required.'
Assert-Contains $compose 'PRODUCTION_DATABASE_PASSWORD:?' 'Production database password must be required.'
Assert-Contains $compose 'postgres-data:/var/lib/postgresql/data' 'Production database must use its own named volume.'
Assert-Contains $compose 'uploads-data:/app/uploads' 'Production uploads must use their own named volume.'
$portBlocks = ([regex]::Matches($compose, '(?m)^\s+ports:\s*$')).Count
if ($portBlocks -ne 1) { throw 'Production Compose must publish only the database loopback port.' }
Assert-Contains $compose '"127.0.0.1:5433:5432"' 'Production database management port must bind only to loopback.'
Assert-Contains $compose 'VITE_ENABLE_MOCK_LOGIN: "false"' 'Production frontend must disable mock login.'
Assert-Contains $compose 'VITE_DINGTALK_AUTO_LOGIN: ${DINGTALK_ENABLED:-false}' 'Production DingTalk auto login must follow the explicit integration switch.'
Assert-Contains $compose 'DINGTALK_ENABLED: ${DINGTALK_ENABLED:-false}' 'Production DingTalk integration must default to disabled.'
Assert-Contains $compose 'MOREDIAN_ENABLED: ${MOREDIAN_ENABLED:-false}' 'Production Moredian integration must default to disabled.'
Assert-Contains $compose 'jiabei-production-frontend' 'Production frontend must expose a unique network alias for Nginx.'
Assert-Contains $compose 'condition: service_healthy' 'Production dependencies must wait for health checks.'
Assert-Contains $compose 'restart: unless-stopped' 'Production services must define a restart policy.'
Assert-Contains $compose 'max-size: 10m' 'Production logs must be size limited.'

Assert-Contains $envExample 'PRODUCTION_DATABASE_NAME=jiabei_production' 'Production database must use a production-specific name.'
Assert-Contains $envExample 'PRODUCTION_DATABASE_USER=jiabei_production' 'Production database must use a production-specific user.'
Assert-Contains $envExample 'ALLOWED_ORIGINS=https://jbei.huixinghub.top' 'Production origin must use the production domain.'
Assert-Contains $envExample 'APP_ENTRY_URL=https://jbei.huixinghub.top' 'Production entry URL must use the production domain.'
Assert-Contains $envExample 'DINGTALK_ENABLED=false' 'Production DingTalk integration example must default to disabled.'
Assert-Contains $envExample 'MOREDIAN_ENABLED=false' 'Production Moredian integration example must default to disabled.'
Assert-Contains $gitignore '!.env.production.example' 'The production example file must be committed explicitly.'

$secretNames = @(
  'PRODUCTION_DATABASE_PASSWORD',
  'SUPER_ADMIN_INITIAL_PASSWORD',
  'DINGTALK_CLIENT_SECRET',
  'MOREDIAN_ORG_AUTH_KEY'
)
foreach ($name in $secretNames) {
  if ($envExample -match "(?m)^$name=[^\r\n]+$") {
    throw "$name must be empty in the production example file."
  }
}

Assert-Contains $productionConfig 'url: ${DATABASE_URL}' 'Production datasource URL must not inherit a local default.'
Assert-Contains $productionConfig 'password: ${DATABASE_PASSWORD}' 'Production datasource password must not inherit a local default.'
Assert-Contains $productionConfig 'allowed-origins: ${ALLOWED_ORIGINS}' 'Production allowed origins must be explicit.'
Assert-Contains $productionConfig 'enabled: ${DINGTALK_ENABLED:false}' 'Production DingTalk beans must require the explicit enable switch.'
Assert-Contains $productionConfig 'enabled: ${MOREDIAN_ENABLED:false}' 'Production Moredian callback must require the explicit enable switch.'

if ($compose -match 'jiabei_dingtalk_test|jb\.huixinghub\.top|jiabei_local|SPRING_PROFILES_ACTIVE:\s*(local|dingtalk-test)') {
  throw 'Production Compose must not reference test/local resources or profiles.'
}

Write-Host 'PASS Production deployment contract'
