$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)

function Read-Required([string]$RelativePath) {
  $path = Join-Path $root $RelativePath
  if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { throw "Missing $RelativePath." }
  return Get-Content -LiteralPath $path -Raw
}

function Assert-Contains([string]$Content, [string]$Expected, [string]$Message) {
  if (-not $Content.Contains($Expected)) { throw $Message }
}

function Assert-Matches([string]$Content, [string]$Pattern, [string]$Message) {
  if ($Content -notmatch $Pattern) { throw $Message }
}

$devCompose = Read-Required 'docker-compose.dev.yml'
$prodCompose = Read-Required 'docker-compose.prod.yml'
$devConfig = Read-Required 'src\backend\src\main\resources\application-dev.yml'
$prodConfig = Read-Required 'src\backend\src\main\resources\application-prod.yml'
$devEnv = Read-Required '.env.dev.defaults'
$prodEnv = Read-Required '.env.prod.defaults'
$frontendDockerfile = Read-Required 'src\frontend\Dockerfile'
$viteConfig = Read-Required 'src\frontend\vite.config.ts'
$dingtalkFrontend = Read-Required 'src\frontend\src\integrations\dingtalk.ts'
$authFrontend = Read-Required 'src\frontend\src\auth.ts'
$frontendEnvTypes = Read-Required 'src\frontend\src\env.d.ts'
$devWorkflow = Read-Required '.github\workflows\deploy-dev.yml'
$prodWorkflow = Read-Required '.github\workflows\deploy-prod.yml'
$secretValidator = Read-Required 'scripts\validate-env-secrets.sh'
$proxyGate = Read-Required 'scripts\verify-proxy-route.sh'
$versionWriter = Read-Required 'scripts\write-deployment-version.sh'
$gitignore = Read-Required '.gitignore'

Assert-Contains $devCompose 'SPRING_PROFILES_ACTIVE: dev' 'Dev Compose must activate the dev profile.'
Assert-Contains $prodCompose 'SPRING_PROFILES_ACTIVE: prod' 'Prod Compose must activate the prod profile.'
Assert-Contains $devCompose 'VITE_MODE: dev' 'Dev frontend must build with Vite dev mode.'
Assert-Contains $prodCompose 'VITE_MODE: prod' 'Prod frontend must build with Vite prod mode.'
Assert-Contains $devCompose 'DINGTALK_AUTO_LOGIN: ${DINGTALK_AUTO_LOGIN:-true}' 'Dev Compose must pass the automatic login switch.'
Assert-Contains $prodCompose 'DINGTALK_AUTO_LOGIN: ${DINGTALK_AUTO_LOGIN:-true}' 'Prod Compose must pass the automatic login switch.'
Assert-Contains $devCompose 'MOCK_LOGIN_ENABLED: ${MOCK_LOGIN_ENABLED:-false}' 'Dev Compose must pass the Mock login switch.'
Assert-Contains $prodCompose 'MOCK_LOGIN_ENABLED: ${MOCK_LOGIN_ENABLED:-false}' 'Prod Compose must pass the Mock login switch.'
Assert-Contains $devCompose 'name: jiabei-production' 'Dev migration must preserve the existing development Compose project.'
Assert-Contains $prodCompose 'name: jiabei' 'Prod migration must preserve the existing production Compose project.'
Assert-Contains $prodCompose 'jiabei_dingtalk_test_pgdata:/var/lib/postgresql/data' 'Prod must preserve the current database volume mapping.'
Assert-Contains $prodCompose 'jiabei_dingtalk_test_uploads:/app/uploads' 'Prod must preserve the current uploads volume mapping.'
Assert-Contains $prodCompose '"127.0.0.1:5432:5432"' 'Prod PostgreSQL must bind only to loopback.'
Assert-Contains $prodCompose '"127.0.0.1:8080:8080"' 'Prod backend must bind only to loopback.'
Assert-Contains $prodCompose '"127.0.0.1:5173:80"' 'Prod frontend must bind only to loopback.'
Assert-Matches $devCompose '(?ms)^  frontend:.*?^    networks:\s*\r?\n      backend-net:\s*\r?\n      proxy-net:\s*\r?\n        aliases:\s*\r?\n          - jiabei-dev-frontend\s*$' 'Dev frontend must join the backend and external proxy networks with its unique alias.'
Assert-Matches $prodCompose '(?ms)^  frontend:.*?^    networks:\s*\r?\n      backend-net:\s*\r?\n      proxy-net:\s*\r?\n        aliases:\s*\r?\n          - jiabei-prod-frontend\s*$' 'Prod frontend must join the backend and external proxy networks with its unique alias.'
foreach ($composeContent in @($devCompose, $prodCompose)) {
  Assert-Matches $composeContent '(?ms)^  proxy-net:\s*\r?\n    external: true\s*\r?\n    name: jiabei-proxy\s*$' 'Each environment must use the shared external jiabei-proxy network.'
}
Assert-Contains $frontendDockerfile 'ARG VITE_MODE=dev' 'Frontend image must declare an explicit Vite mode.'
Assert-Contains $frontendDockerfile 'ARG DINGTALK_CLIENT_ID=' 'Frontend image must use the unified public Client ID name.'
Assert-Contains $frontendDockerfile 'ARG DINGTALK_CORP_ID=' 'Frontend image must use the unified public Corp ID name.'
Assert-Contains $frontendDockerfile 'ARG DINGTALK_AUTO_LOGIN=' 'Frontend image must declare the automatic login switch.'
Assert-Contains $frontendDockerfile 'ARG MOCK_LOGIN_ENABLED=' 'Frontend image must declare the Mock login switch.'
Assert-Contains $frontendDockerfile 'pnpm run build -- --mode "$VITE_MODE"' 'Frontend image must pass the selected Vite mode to the build.'
Assert-Contains $viteConfig "'import.meta.env.DINGTALK_CLIENT_ID'" 'Vite must explicitly define the public Client ID.'
Assert-Contains $viteConfig "'import.meta.env.DINGTALK_CORP_ID'" 'Vite must explicitly define the public Corp ID.'
Assert-Contains $viteConfig "'import.meta.env.DINGTALK_AUTO_LOGIN'" 'Vite must explicitly define the automatic login switch.'
Assert-Contains $viteConfig "'import.meta.env.MOCK_LOGIN_ENABLED'" 'Vite must explicitly define the Mock login switch.'
Assert-Contains $dingtalkFrontend 'import.meta.env.DINGTALK_CLIENT_ID' 'Frontend code must use the unified Client ID name.'
Assert-Contains $dingtalkFrontend 'import.meta.env.DINGTALK_CORP_ID' 'Frontend code must use the unified Corp ID name.'
Assert-Contains $frontendEnvTypes 'readonly DINGTALK_CLIENT_ID: string' 'Frontend types must declare the public Client ID.'
Assert-Contains $frontendEnvTypes 'readonly DINGTALK_CORP_ID: string' 'Frontend types must declare the public Corp ID.'
Assert-Contains $frontendEnvTypes 'readonly DINGTALK_AUTO_LOGIN: string' 'Frontend types must declare the automatic login switch.'
Assert-Contains $frontendEnvTypes 'readonly MOCK_LOGIN_ENABLED: string' 'Frontend types must declare the Mock login switch.'
Assert-Contains $authFrontend "import.meta.env.DINGTALK_AUTO_LOGIN === 'true'" 'Automatic login must use its explicit switch.'
Assert-Contains $viteConfig 'envPrefix: []' 'Vite must disable automatic environment exposure.'
if ($viteConfig -match 'envPrefix:\s*[''"](?:VITE_|DINGTALK_)' -or $viteConfig -match 'DINGTALK_\*') {
  throw 'Vite must expose only explicit public keys, never a broad DingTalk prefix.'
}
$unifiedFiles = @($devCompose, $prodCompose, $devEnv, $prodEnv, $frontendDockerfile,
  $viteConfig, $dingtalkFrontend, $frontendEnvTypes, $devWorkflow, $prodWorkflow)
if (($unifiedFiles -join "`n") -match 'VITE_DINGTALK_') {
  throw 'VITE_DINGTALK_* must be removed from user configuration and application code.'
}

Assert-Contains $devConfig 'locations: classpath:db/migration' 'Dev must load only normal migrations.'
Assert-Contains $prodConfig 'locations: classpath:db/migration' 'Prod must load only normal migrations.'
Assert-Contains $prodConfig 'validate-on-migrate: false' 'Prod compatibility guard must own Flyway validation.'
if ($devConfig -match 'db/(local|dingtalk-test)' -or $prodConfig -match 'db/(local|dingtalk-test)') {
  throw 'Dev/prod profiles must not load local or dingtalk-test migrations.'
}

Assert-Contains $devEnv 'ALLOWED_ORIGINS=https://dev.jb.huixinghub.top' 'Dev example must use the dev domain.'
Assert-Contains $prodEnv 'ALLOWED_ORIGINS=https://jb.huixinghub.top' 'Prod example must use the production domain.'
Assert-Contains $devEnv 'MOREDIAN_CALLBACK_URL=https://dev.jb.huixinghub.top/api/v1/integrations/moredian/recognition-events' 'Dev callback must use the dev domain.'
Assert-Contains $prodEnv 'MOREDIAN_CALLBACK_URL=https://jb.huixinghub.top/api/v1/integrations/moredian/recognition-events' 'Prod callback must use the production domain.'
foreach ($envContent in @($devEnv, $prodEnv)) {
  Assert-Contains $envContent '# Database' 'Defaults must group database settings.'
  Assert-Contains $envContent '# DingTalk application' 'Defaults must group DingTalk settings.'
  Assert-Contains $envContent '# Moredian integration' 'Defaults must group Moredian settings.'
  Assert-Contains $envContent '# Card notifications' 'Defaults must group card notification settings.'
  Assert-Contains $envContent '# Super administrator' 'Defaults must group super administrator settings.'
  Assert-Contains $envContent '# Application and deployment' 'Defaults must group application/deployment settings.'
  foreach ($publicKey in @('DINGTALK_CLIENT_ID', 'DINGTALK_CORP_ID')) {
    if ($envContent -notmatch "(?m)^$publicKey=.+$") { throw "$publicKey must be populated in defaults." }
  }
}
foreach ($envContent in @($devEnv, $prodEnv)) {
  foreach ($secret in @('DATABASE_PASSWORD','SUPER_ADMIN_INITIAL_PASSWORD','DINGTALK_CLIENT_SECRET','MOREDIAN_ORG_AUTH_KEY')) {
    if ($envContent -match "(?m)^[A-Z_]*$secret=[^\r\n]+$") { throw "$secret must be empty in examples." }
  }
}
Assert-Contains $devEnv 'MOREDIAN_ENABLED=false' 'Dev Moredian must stay disabled while its identifiers are unavailable.'
Assert-Contains $prodEnv 'MOREDIAN_ENABLED=true' 'Prod Moredian must preserve the active production integration.'
Assert-Contains $devEnv 'DINGTALK_AUTO_LOGIN=true' 'Dev must preserve automatic DingTalk login.'
Assert-Contains $prodEnv 'DINGTALK_AUTO_LOGIN=true' 'Prod must preserve automatic DingTalk login.'
Assert-Contains $devEnv 'MOCK_LOGIN_ENABLED=false' 'Dev must disable Mock login by default.'
Assert-Contains $prodEnv 'MOCK_LOGIN_ENABLED=false' 'Prod must disable Mock login.'
Assert-Contains $devEnv 'overridden by .env.dev.secrets' 'Dev defaults must document the secrets override.'
Assert-Contains $prodEnv 'overridden by .env.prod.secrets' 'Prod defaults must document the secrets override.'
Assert-Contains $gitignore '.env.*.secrets' 'Secret environment layers must be ignored.'
Assert-Contains $gitignore '!.env.dev.defaults' 'Dev defaults must be explicitly trackable.'
Assert-Contains $gitignore '!.env.prod.defaults' 'Prod defaults must be explicitly trackable.'
foreach ($obsolete in @('.env.dev.example', '.env.prod.example', '.env.production.example')) {
  if (Test-Path -LiteralPath (Join-Path $root $obsolete)) { throw "$obsolete must be removed." }
}

Assert-Contains $devWorkflow 'environment: dev' 'Main deployment must use the dev GitHub Environment.'
Assert-Contains $devWorkflow 'name: 部署开发环境' 'Dev workflow must use the canonical display name.'
Assert-Contains $devWorkflow 'workflow_dispatch:' 'Dev deployment must preserve its existing manual trigger.'
foreach ($ignoredPath in @('AGENTS.md', 'README.md', '.github/workflows/**', 'docs/**', '项目部署流程/**', 'scripts/tests/**')) {
  Assert-Contains $devWorkflow "- $ignoredPath" "Dev workflow must ignore documentation-only path $ignoredPath."
}
foreach ($activePath in @('src/**', 'docker-compose*.yml', 'scripts/**', '.env.*')) {
  if ($devWorkflow -match "(?m)^\s+- $([regex]::Escape($activePath))\s*$") {
    throw "Dev workflow must not ignore active deployment path $activePath."
  }
}
Assert-Contains $devWorkflow '/opt/stacks/jiabei-dev/' 'Dev deployment must use the canonical development path.'
Assert-Contains $devWorkflow '--env-file .env.dev.defaults --env-file .env.dev.secrets' 'Dev deployment must load defaults before secrets.'
Assert-Contains $devWorkflow "--exclude '.env.dev.secrets'" 'Dev rsync must preserve the server secrets file.'
Assert-Contains $devWorkflow 'sh scripts/validate-env-secrets.sh .env.dev.secrets' 'Dev secrets must be validated before deployment.'
Assert-Contains $devWorkflow 'docker network inspect jiabei-proxy' 'Dev deployment must fail before sync when the external proxy network is missing.'
Assert-Contains $devWorkflow 'sh scripts/verify-proxy-route.sh jiabei-proxy nginx "$frontend_id" jiabei-dev-frontend 12 5' 'Dev deployment must use the shared bounded proxy gate.'
$devBranchTriggers = [regex]::Matches($devWorkflow, '(?m)^\s+branches:\s*(.+)$')
if ($devBranchTriggers.Count -ne 1 -or $devBranchTriggers[0].Groups[1].Value.Trim() -ne '[main]') {
  throw 'Dev deployment must trigger pushes only from main.'
}
if (Test-Path -LiteralPath (Join-Path $root '.github\workflows\deploy-main.yml')) {
  throw 'The legacy deploy-main workflow path must be removed.'
}
if ($devWorkflow.Contains('/opt/stacks/jiabei-production')) {
  throw 'The active dev workflow must not reference the legacy deployment directory.'
}
Assert-Contains $prodWorkflow 'environment: prod' 'Production deployment must use the prod GitHub Environment.'
Assert-Contains $prodWorkflow 'name: 部署生产环境' 'Production workflow must use the canonical display name.'
Assert-Matches $prodWorkflow '(?ms)^on:\s*\r?\n  workflow_dispatch:\s*\r?\n    inputs:' 'Production workflow must remain manually dispatched.'
if ($prodWorkflow -match '(?m)^  (push|pull_request|schedule|workflow_call):') {
  throw 'Production deployment must expose only workflow_dispatch.'
}
Assert-Contains $prodWorkflow "if: github.ref == 'refs/heads/prod'" 'Production workflow must only run when dispatched from prod.'
Assert-Contains $prodWorkflow 'version:' 'Production deployment must require a version input.'
Assert-Contains $prodWorkflow 'refs/tags/${{ inputs.version }}' 'Production checkout must use the selected immutable tag.'
Assert-Contains $prodWorkflow 'merge-base --is-ancestor "$release_sha" origin/prod' 'Production deployment must verify tag ancestry in prod.'
Assert-Contains $prodWorkflow '/opt/stacks/jiabei-prod/' 'Production deployment must use the canonical production path.'
Assert-Contains $prodWorkflow '--env-file .env.prod.defaults --env-file .env.prod.secrets' 'Prod deployment must load defaults before secrets.'
Assert-Contains $prodWorkflow "--exclude '.env.prod.secrets'" 'Prod rsync must preserve the server secrets file.'
Assert-Contains $prodWorkflow 'sh scripts/validate-env-secrets.sh .env.prod.secrets' 'Prod secrets must be validated before deployment.'
Assert-Contains $prodWorkflow 'docker network inspect jiabei-proxy' 'Production deployment must fail before backup or sync when the external proxy network is missing.'
Assert-Contains $prodWorkflow 'sh scripts/verify-proxy-route.sh jiabei-proxy nginx \"\$frontend_id\" jiabei-prod-frontend 12 5' 'Production deployment must use the shared bounded proxy gate.'
Assert-Contains $prodWorkflow 'sh scripts/write-deployment-version.sh .deployment-version ''$VERSION'' ''$RELEASE_SHA''' 'Production deployment must write its marker atomically.'
if (Test-Path -LiteralPath (Join-Path $root '.github\workflows\deploy-production.yml')) {
  throw 'The legacy deploy-production workflow path must be removed.'
}
if ($prodWorkflow.Contains('/opt/stacks/jiabei/')) {
  throw 'The active production workflow must not reference the legacy deployment directory.'
}
Assert-Contains $proxyGate 'docker network inspect "$network_name"' 'The shared proxy gate must inspect the external network.'
Assert-Contains $proxyGate 'getent hosts "$alias_name"' 'The shared proxy gate must resolve the environment-specific alias from Nginx.'
Assert-Contains $proxyGate 'http://$alias_name:80/' 'The shared proxy gate must probe the environment-specific alias from Nginx.'
Assert-Contains $versionWriter 'mv -f -- "$temporary" "$target"' 'The version marker must use same-filesystem atomic replacement.'
foreach ($workflow in @($devWorkflow, $prodWorkflow)) {
  if ($workflow -match 'index \.NetworkSettings\.Networks' -or
      $workflow -match 'json \.NetworkSettings\.Networks' -or
      $workflow -match '(?m)^\s*eval\s') {
    throw 'Proxy gates must not use nested Docker network templates or eval across YAML, SSH and the remote shell.'
  }
}
if ($proxyGate -match '(?m)^\s*eval\s' -or $versionWriter -match '(?m)^\s*eval\s') {
  throw 'Deployment scripts must not use eval.'
}
if ($devWorkflow.Contains('http://frontend:80') -or $prodWorkflow.Contains('http://frontend:80')) {
  throw 'Deployment proxy gates must never target the ambiguous frontend alias.'
}
$prodDump = $prodWorkflow.IndexOf('pg_dump')
$prodRestoreList = $prodWorkflow.IndexOf('pg_restore --list')
$prodSync = $prodWorkflow.IndexOf('rsync -az --delete')
$prodServiceUpdate = $prodWorkflow.IndexOf('$compose build backend frontend')
if ($prodDump -lt 0 -or $prodRestoreList -lt 0 -or $prodSync -lt 0 -or $prodServiceUpdate -lt 0 -or
    -not ($prodDump -lt $prodRestoreList -and $prodRestoreList -lt $prodSync -and $prodSync -lt $prodServiceUpdate)) {
  throw 'Production backup and pg_restore catalog validation must complete before code sync and service update.'
}
Assert-Matches $prodWorkflow 'test -s \\"\\\$backup_file\\"\s*\r?\n\s*\\\$compose exec -T db pg_restore --list < \\"\\\$backup_file\\"' 'Production must validate the new backup catalog immediately after confirming the dump is non-empty.'
if ($prodWorkflow -match '(?m)^\s+push:' -or $prodWorkflow -match 'ref:\s+prod\s*$') {
  throw 'Production deployment must remain manual and must not deploy a floating prod HEAD.'
}
if ($devWorkflow -match "--exclude '\.env\.\*'" -or $prodWorkflow -match "--exclude '\.env\.\*'") {
  throw 'Rsync must upload committed defaults instead of excluding every environment file.'
}
foreach ($secret in @('DATABASE_PASSWORD', 'SUPER_ADMIN_INITIAL_PASSWORD', 'DINGTALK_CLIENT_SECRET', 'MOREDIAN_ORG_AUTH_KEY')) {
  Assert-Contains $devWorkflow $secret "Dev must validate $secret without printing its value."
  Assert-Contains $prodWorkflow $secret "Prod must validate $secret without printing its value."
}
Assert-Contains $secretValidator 'stat -c ''%a'' "$secrets_file"' 'Secret files must have mode 600.'
Assert-Contains $secretValidator 'Required secret $key is missing or empty.' 'Validation errors may identify keys but not values.'

$legacyV10 = Join-Path $root 'src\backend\src\main\resources\db\dingtalk-test\V10__reset_super_admin_test_credential.sql'
$legacyHash = (Get-FileHash -LiteralPath $legacyV10 -Algorithm SHA256).Hash
if ($legacyHash -ne 'B992B31FFA1B8E0754C1469BFB36AD86D35A4E06947BF75E107BB617C0077E61') {
  throw 'Legacy dingtalk-test V10 must remain byte-for-byte unchanged.'
}

Write-Host 'PASS dev/prod environment migration contract'
