$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$nginx = Get-Content -LiteralPath (Join-Path $root 'src\frontend\nginx.conf') -Raw

if ($nginx -notmatch 'location\s*=\s*/index\.html[\s\S]*?Cache-Control[\s\S]*?no-cache, must-revalidate') {
  throw 'index.html must explicitly use no-cache, must-revalidate.'
}
if ($nginx -notmatch 'location\s*=\s*/\s*\{[\s\S]*?Cache-Control[\s\S]*?no-cache, must-revalidate') {
  throw 'The HTML root must explicitly use no-cache, must-revalidate.'
}
$hashedStart = $nginx.IndexOf('location ~* "^/assets/')
$fallbackStart = $nginx.IndexOf('location /assets/')
if ($hashedStart -lt 0 -or $fallbackStart -le $hashedStart) {
  throw 'Hashed and non-hashed asset locations must both exist in the expected order.'
}
$hashedBlock = $nginx.Substring($hashedStart, $fallbackStart - $hashedStart)
if ($hashedBlock -notmatch 'Cache-Control\s+"public, max-age=31536000, immutable"') {
  throw 'Only a hashed Vite asset regex location may use the one-year immutable cache policy.'
}
if ($hashedBlock -match '\balways\b') {
  throw 'Immutable headers must not be added to 4xx or 5xx asset responses.'
}
if ($nginx -notmatch '(?ms)location\s+/assets/\s*\{.*?try_files\s+\$uri\s+=404;.*?\}') {
  throw 'Non-hashed assets must have a separate non-immutable fallback location.'
}
$immutableOccurrences = ([regex]::Matches($nginx, 'max-age=31536000, immutable')).Count
if ($immutableOccurrences -ne 1) { throw 'Immutable caching must be declared in exactly one hashed-asset location.' }
$viteHashedAsset = '/assets/index-D9-KSdqu.js'
$nonHashedAsset = '/assets/logo.js'
$hashPattern = '^/assets/.+-[A-Za-z0-9_-]{8}\.(?:js|css|png|jpe?g|webp|svg|woff2?)$'
if ($viteHashedAsset -notmatch $hashPattern) { throw 'The hashed asset contract must match actual Vite output names.' }
if ($nonHashedAsset -match $hashPattern) { throw 'Non-hashed assets must not match the immutable cache contract.' }
if ($nginx -notmatch 'try_files\s+\$uri\s+\$uri/\s+/index\.html') {
  throw 'SPA fallback must continue to resolve through index.html.'
}
$index = Get-Content -LiteralPath (Join-Path $root 'src\frontend\index.html') -Raw
if ($index -notmatch '页面正在加载' -or $index -notmatch '刷新页面') {
  throw 'The HTML shell must retain a static loading and refresh fallback.'
}

Write-Host 'PASS frontend cache contract'
