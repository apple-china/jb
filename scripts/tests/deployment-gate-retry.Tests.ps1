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

function To-BashPath([string]$Path) {
  return $Path.Replace('\', '/').Replace('C:', '/c').Replace('D:', '/d')
}

$bash = 'C:\Program Files\Git\bin\bash.exe'
if (-not (Test-Path -LiteralPath $bash -PathType Leaf)) { throw 'Git Bash is required for deployment script contracts.' }

$proxyGateRelative = 'scripts\verify-proxy-route.sh'
$versionWriterRelative = 'scripts\write-deployment-version.sh'
$proxyGate = Read-Required $proxyGateRelative
$versionWriter = Read-Required $versionWriterRelative
$devWorkflow = Read-Required '.github\workflows\deploy-dev.yml'
$prodWorkflow = Read-Required '.github\workflows\deploy-production.yml'

Assert-Contains $devWorkflow 'sh scripts/verify-proxy-route.sh jiabei-proxy nginx "$frontend_id" jiabei-dev-frontend 12 5' 'Dev workflow must use the shared bounded proxy gate.'
Assert-Contains $prodWorkflow 'sh scripts/verify-proxy-route.sh jiabei-proxy nginx \"\$frontend_id\" jiabei-prod-frontend 12 5' 'Prod workflow must use the shared bounded proxy gate.'
Assert-Contains $prodWorkflow 'sh scripts/write-deployment-version.sh .deployment-version ''$VERSION'' ''$RELEASE_SHA''' 'Prod workflow must use the atomic version marker writer.'
if ($devWorkflow -match '(?m)^\s*eval\s' -or $prodWorkflow -match '(?m)^\s*eval\s') { throw 'Deployment workflows must not use eval.' }

foreach ($required in @('PROXY_GATE_START', 'PROXY_GATE_SUCCESS', 'PROXY_GATE_FAILED item=dns',
    'PROXY_GATE_FAILED item=target_ip', 'PROXY_GATE_FAILED item=http')) {
  Assert-Contains $proxyGate $required "Proxy gate must report $required."
}
Assert-Contains $versionWriter 'mktemp "$directory/${filename}.tmp.XXXXXX"' 'Version writer must create its temporary file beside the target.'
Assert-Contains $versionWriter 'chmod 600 "$temporary"' 'Version writer must set mode 600 before replacement.'
Assert-Contains $versionWriter 'test "$(cat "$temporary")" = "$expected"' 'Version writer must verify exact content before replacement.'
Assert-Contains $versionWriter 'mv -f -- "$temporary" "$target"' 'Version writer must atomically replace the target on the same filesystem.'
Assert-Contains $versionWriter 'rm -f -- "$temporary"' 'Version writer must clean a failed temporary file.'
if ($proxyGate -match '(?m)^\s*eval\s' -or $versionWriter -match '(?m)^\s*eval\s') { throw 'Deployment scripts must not use eval.' }

$tempRoot = Join-Path ([IO.Path]::GetTempPath()) ("jiabei-deployment-contract-" + [Guid]::NewGuid().ToString('N'))
$fakeBin = Join-Path $tempRoot 'bin'
$stateDir = Join-Path $tempRoot 'state'
New-Item -ItemType Directory -Path $fakeBin, $stateDir | Out-Null
try {
  $fakeDocker = @'
#!/usr/bin/env bash
set -u
next_count() {
  name="$1"
  file="$FAKE_DOCKER_STATE/$name"
  count=0
  [[ -f "$file" ]] && count=$(cat "$file")
  count=$((count + 1))
  printf '%s' "$count" > "$file"
  printf '%s' "$count"
}
if [[ "$1" == network && "$2" == inspect ]]; then
  printf 'nginx 10.0.0.2/24\njiabei-frontend-1 10.0.0.3/24\n'
  exit 0
fi
if [[ "$1" == inspect ]]; then
  printf '/jiabei-frontend-1\n'
  exit 0
fi
if [[ "$1" == exec ]]; then
  shift 2
  if [[ "$1" == getent ]]; then
    count=$(next_count getent)
    case "$FAKE_SCENARIO" in
      success)
        [[ "$count" == 1 ]] && exit 2
        [[ "$count" == 3 ]] && { printf '10.0.0.99 alias\n'; exit 0; }
        printf '10.0.0.3 alias\n'
        ;;
      dns-fail) exit 2 ;;
      ip-fail)
        [[ "$count" == 1 ]] && printf '10.0.0.3 alias\n' || printf '10.0.0.99 alias\n'
        ;;
      http-fail) printf '10.0.0.3 alias\n' ;;
    esac
    exit 0
  fi
  if [[ "$1" == curl ]]; then
    count=$(next_count curl)
    if [[ "$FAKE_SCENARIO" == success && "$count" -gt 1 ]]; then
      printf '200'
      exit 0
    fi
    printf '503'
    exit 22
  fi
fi
exit 64
'@
  $dockerPath = Join-Path $fakeBin 'docker'
  [IO.File]::WriteAllText($dockerPath, $fakeDocker.Replace("`r`n", "`n"), [Text.UTF8Encoding]::new($false))
  & $bash -lc "chmod +x '$(To-BashPath $dockerPath)'"
  if ($LASTEXITCODE -ne 0) { throw 'Unable to prepare fake Docker command.' }

  $proxyGatePath = To-BashPath (Join-Path $root $proxyGateRelative)
  $env:FAKE_DOCKER_STATE = To-BashPath $stateDir
  try {
    $env:FAKE_SCENARIO = 'success'
    $successOutput = @(& $bash -c 'PATH="$1:/usr/bin:/bin"; shift; exec sh "$@"' _ (To-BashPath $fakeBin) $proxyGatePath jiabei-proxy nginx frontend-id jiabei-dev-frontend 3 0 2>&1)
    if ($LASTEXITCODE -ne 0) { throw "Proxy gate should recover within its retry budget: $($successOutput -join ' ')" }
    if (($successOutput | Select-String '^PROXY_GATE_START').Count -ne 1 -or
        ($successOutput | Select-String '^PROXY_GATE_SUCCESS').Count -ne 1 -or
        ($successOutput | Select-String '^PROXY_GATE_FAILED').Count -ne 0) {
      throw 'Successful retries must log only one start and one success summary.'
    }
    if ((Get-Content (Join-Path $stateDir 'getent') -Raw) -ne '4' -or
        (Get-Content (Join-Path $stateDir 'curl') -Raw) -ne '2') {
      throw 'Proxy gate did not independently retry DNS, IP consistency and HTTP.'
    }

    foreach ($case in @(
        @{ Scenario = 'dns-fail'; Item = 'dns'; Expected = 'expected=resolved actual=unresolved' },
        @{ Scenario = 'ip-fail'; Item = 'target_ip'; Expected = 'expected=target-container-ip actual=different' },
        @{ Scenario = 'http-fail'; Item = 'http'; Expected = 'expected=200 actual=503' }
      )) {
      Get-ChildItem -LiteralPath $stateDir -Force | Remove-Item -Force
      $env:FAKE_SCENARIO = $case.Scenario
      $failureOutput = @(& $bash -c 'PATH="$1:/usr/bin:/bin"; shift; exec sh "$@"' _ (To-BashPath $fakeBin) $proxyGatePath jiabei-proxy nginx frontend-id jiabei-dev-frontend 3 0 2>&1)
      if ($LASTEXITCODE -eq 0) { throw "$($case.Item) failure must reject the deployment." }
      $summary = $failureOutput -join "`n"
      if ($summary -notmatch "PROXY_GATE_FAILED item=$($case.Item)" -or -not $summary.Contains($case.Expected)) {
        throw "$($case.Item) failure must report a sanitized expected/actual summary."
      }
      if ($summary -match '(?:\d{1,3}\.){3}\d{1,3}') { throw 'Failure summaries must not print raw IP addresses.' }
    }
  } finally {
    Remove-Item Env:FAKE_DOCKER_STATE -ErrorAction SilentlyContinue
    Remove-Item Env:FAKE_SCENARIO -ErrorAction SilentlyContinue
  }

  $versionWriterPath = To-BashPath (Join-Path $root $versionWriterRelative)
  $target = Join-Path $tempRoot '.deployment-version'
  [IO.File]::WriteAllText($target, "v1.0.0 1111111111111111111111111111111111111111`n", [Text.UTF8Encoding]::new($false))
  $inodeBefore = & $bash -lc "stat -c '%i' '$(To-BashPath $target)'"
  & $bash -c 'PATH="/usr/bin:/bin"; exec sh "$@"' _ $versionWriterPath (To-BashPath $target) v1.0.1 2222222222222222222222222222222222222222 | Out-Null
  if ($LASTEXITCODE -ne 0) { throw 'Atomic version writer should succeed for valid input.' }
  if ((Get-Content -LiteralPath $target -Raw).TrimEnd() -ne 'v1.0.1 2222222222222222222222222222222222222222') {
    throw 'Atomic version writer produced unexpected content.'
  }
  $inodeAfter = & $bash -lc "stat -c '%i' '$(To-BashPath $target)'"
  if ($inodeBefore -eq $inodeAfter) { throw 'Atomic version writer must replace the target inode.' }
  if (Get-ChildItem -LiteralPath $tempRoot -Filter '.deployment-version.tmp.*') { throw 'Successful atomic replacement left a temporary file.' }
} finally {
  if (Test-Path -LiteralPath $tempRoot) { Remove-Item -LiteralPath $tempRoot -Recurse -Force }
}

Write-Host 'PASS deployment gate retries and atomic version marker contract'
