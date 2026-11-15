$ErrorActionPreference = 'Stop'
$projectPath = Split-Path -Parent $PSScriptRoot
Set-Location -LiteralPath $projectPath
$keyPath = Join-Path $projectPath 'secrets/vapid.json'
if (-not (Test-Path -LiteralPath $keyPath)) { throw '먼저 node scripts/generate-vapid.mjs를 실행하세요.' }
$vapidConfig = Get-Content -Raw -LiteralPath $keyPath | ConvertFrom-Json
$env:VAPID_PUBLIC_KEY = $vapidConfig.publicKey
$env:VAPID_PRIVATE_KEY = $vapidConfig.privateKey
$env:WEB_PUSH_ENABLED = 'true'
if ([string]::IsNullOrWhiteSpace($env:VAPID_SUBJECT)) {
    $env:VAPID_SUBJECT = Read-Host 'VAPID 연락처를 mailto:본인이메일 형식으로 입력하세요'
}
if ([string]::IsNullOrWhiteSpace($env:DB_PASSWORD)) {
    $databaseCredential = Get-Credential -UserName 'root' -Message 'MySQL root 비밀번호를 입력하세요'
    $env:DB_PASSWORD = $databaseCredential.GetNetworkCredential().Password
    Remove-Variable databaseCredential
}
Remove-Variable vapidConfig
& .\mvnw.cmd spring-boot:run
