$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$config = (Resolve-Path -LiteralPath (Join-Path $root 'src\frontend\nginx.conf')).Path
$dockerCommand = Get-Command docker -ErrorAction SilentlyContinue
$docker = if ($dockerCommand) {
  $dockerCommand.Source
} else {
  Join-Path $env:LOCALAPPDATA 'Programs\DockerDesktop\resources\bin\docker.exe'
}
if (-not (Test-Path -LiteralPath $docker)) { throw 'Docker CLI is unavailable.' }

$image = 'nginx@sha256:65645c7bb6a0661892a8b03b89d0743208a18dd2f3f17a54ef4b76fb8e2f2a10'
$suffix = [Guid]::NewGuid().ToString('N').Substring(0, 12)
$network = "jiabei-nginx-syntax-$suffix"
$upstream = "jiabei-nginx-upstream-$suffix"
$networkCreated = $false
$upstreamCreated = $false

try {
  & $docker version --format '{{.Server.Version}}' | Out-Null
  if ($LASTEXITCODE -ne 0) { throw 'Docker Engine is unavailable.' }

  & $docker network create $network | Out-Null
  if ($LASTEXITCODE -ne 0) { throw 'Unable to create the isolated syntax-test network.' }
  $networkCreated = $true

  & $docker run -d --name $upstream --network $network --network-alias backend --entrypoint sh $image -c 'sleep 300' | Out-Null
  if ($LASTEXITCODE -ne 0) { throw 'Unable to start the isolated upstream resolver target.' }
  $upstreamCreated = $true

  $mount = "type=bind,source=$config,target=/etc/nginx/conf.d/default.conf,readonly"
  $syntaxOutput = & $docker run --rm --network $network --mount $mount $image nginx -t 2>&1
  if ($LASTEXITCODE -ne 0) {
    $summary = ($syntaxOutput | Select-Object -Last 5) -join [Environment]::NewLine
    throw "Project nginx.conf failed the real Nginx parser:`n$summary"
  }

  Write-Host 'PASS frontend nginx syntax contract'
} finally {
  if ($upstreamCreated) { & $docker rm -f $upstream | Out-Null }
  if ($networkCreated) { & $docker network rm $network | Out-Null }
}
