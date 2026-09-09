param([switch]$Force)
$ErrorActionPreference = 'Stop'

if (-not $Force) {
  $answer = Read-Host '这会删除加贝云本地 PostgreSQL 数据卷和上传文件，输入 RESET 继续'
  if ($answer -ne 'RESET') { Write-Host '已取消。'; exit 0 }
}

docker compose down --volumes
docker compose up --build -d
docker compose ps
