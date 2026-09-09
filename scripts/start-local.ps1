$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot

function Test-ListeningPort([int]$Port) {
  $client = New-Object System.Net.Sockets.TcpClient
  try {
    $pending = $client.BeginConnect('127.0.0.1', $Port, $null, $null)
    if (-not $pending.AsyncWaitHandle.WaitOne(500)) { return $false }
    $client.EndConnect($pending)
    return $true
  } catch {
    return $false
  } finally {
    $client.Close()
  }
}

function Wait-ForPort([int]$Port, [string]$Name, [int]$Seconds = 60) {
  for ($i = 0; $i -lt $Seconds; $i++) {
    if (Test-ListeningPort $Port) { return }
    Start-Sleep -Seconds 1
  }
  throw "$Name did not start on port $Port. Check the log files in .tools."
}

$dockerReady = $false
if (Get-Command docker -ErrorAction SilentlyContinue) {
  $previousErrorPreference = $ErrorActionPreference
  $ErrorActionPreference = 'SilentlyContinue'
  & docker info *> $null
  $dockerReady = $LASTEXITCODE -eq 0
  $ErrorActionPreference = $previousErrorPreference
}

if ($dockerReady) {
  Push-Location $projectRoot
  try {
    docker compose up --build -d
    docker compose ps
  } finally { Pop-Location }
} else {
  $java = Join-Path $projectRoot '.tools\jdk\jdk-21.0.12.1+1\bin\java.exe'
  $pgCtl = Join-Path $projectRoot '.tools\postgresql-16.15\pgsql\bin\pg_ctl.exe'
  $postgres = Join-Path $projectRoot '.tools\postgresql-16.15\pgsql\bin\postgres.exe'
  $pgData = Join-Path $projectRoot '.tools\pgdata'
  $jar = Join-Path $projectRoot 'src\backend\target\jiabei-cloud-backend-1.0.0.jar'
  $dist = Join-Path $projectRoot 'src\frontend\dist'
  $spaServer = Join-Path $PSScriptRoot 'StaticSpaServer.java'
  foreach ($required in @($java, $pgCtl, $postgres, $pgData, $jar, (Join-Path $dist 'index.html'), $spaServer)) {
    if (-not (Test-Path $required)) {
      throw "Docker is unavailable and the portable runtime is incomplete: $required"
    }
  }

  $toolDir = Join-Path $projectRoot '.tools'
  if (-not (Test-ListeningPort 55432)) {
    $postmasterPid = Join-Path $pgData 'postmaster.pid'
    if (Test-Path $postmasterPid) {
      $oldPid = [int](Get-Content $postmasterPid -First 1)
      if (-not (Get-Process -Id $oldPid -ErrorAction SilentlyContinue)) {
        Remove-Item -LiteralPath $postmasterPid -Force
      }
    }
    $database = Start-Process -FilePath $postgres -ArgumentList @('-D', $pgData, '-p', '55432') `
      -WorkingDirectory $projectRoot -WindowStyle Hidden -PassThru `
      -RedirectStandardOutput (Join-Path $toolDir 'postgresql.out.log') `
      -RedirectStandardError (Join-Path $toolDir 'postgresql.err.log')
    Set-Content -Path (Join-Path $toolDir 'database.pid') -Value $database.Id -Encoding Ascii
    Wait-ForPort 55432 'PostgreSQL'
  }

  if (-not (Test-ListeningPort 8080)) {
    $backendArgs = @(
      '-jar', $jar,
      '--spring.profiles.active=local',
      '--spring.datasource.url=jdbc:postgresql://127.0.0.1:55432/jiabei',
      '--spring.datasource.username=jiabei',
      '--spring.datasource.password=test',
      '--server.port=8080'
    )
    $backend = Start-Process -FilePath $java -ArgumentList $backendArgs -WorkingDirectory $projectRoot `
      -WindowStyle Hidden -PassThru `
      -RedirectStandardOutput (Join-Path $toolDir 'backend.out.log') `
      -RedirectStandardError (Join-Path $toolDir 'backend.err.log')
    Set-Content -Path (Join-Path $toolDir 'backend.pid') -Value $backend.Id -Encoding Ascii
    Wait-ForPort 8080 'Backend' 90
  }

  if (-not (Test-ListeningPort 5173)) {
    $frontend = Start-Process -FilePath $java -ArgumentList @($spaServer, $dist, '5173', 'http://127.0.0.1:8080') `
      -WorkingDirectory $projectRoot -WindowStyle Hidden -PassThru `
      -RedirectStandardOutput (Join-Path $toolDir 'frontend.out.log') `
      -RedirectStandardError (Join-Path $toolDir 'frontend.err.log')
    Set-Content -Path (Join-Path $toolDir 'frontend.pid') -Value $frontend.Id -Encoding Ascii
    Wait-ForPort 5173 'Frontend'
  }
}

$health = Invoke-RestMethod -Uri 'http://127.0.0.1:8080/actuator/health'
if ($health.status -ne 'UP') { throw 'Backend health check did not return UP.' }

Write-Host 'JiaBei Cloud Mock is running:'
Write-Host '  Web:      http://127.0.0.1:5173/login'
Write-Host '  Backend:  http://127.0.0.1:8080'
Write-Host '  Health:   http://127.0.0.1:8080/actuator/health'
Write-Host '  OpenAPI:  http://127.0.0.1:8080/swagger-ui/index.html'
if (-not $dockerReady) { Write-Host '  Runtime:  portable mode (Docker/virtualization not required)' }
