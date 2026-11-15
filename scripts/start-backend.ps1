param([switch]$CheckOnly, [string]$Subject)
$ErrorActionPreference = 'Stop'
$projectPath = Split-Path -Parent $PSScriptRoot
Set-Location -LiteralPath $projectPath
$keyPath = Join-Path $projectPath 'secrets/vapid.json'
if (-not (Test-Path -LiteralPath $keyPath)) { throw '먼저 node scripts/generate-vapid.mjs를 실행하세요.' }
try { $vapidConfig = Get-Content -Raw -Encoding UTF8 -LiteralPath $keyPath | ConvertFrom-Json }
catch { throw 'VAPID_FILE_UNREADABLE_OR_INVALID_JSON: 키 파일을 읽을 수 없습니다. 내용은 출력하지 않습니다.' }
$env:VAPID_PUBLIC_KEY = ([string]$vapidConfig.publicKey).Trim()
$env:VAPID_PRIVATE_KEY = ([string]$vapidConfig.privateKey).Trim()
$env:WEB_PUSH_ENABLED = 'true'
if ($PSBoundParameters.ContainsKey('Subject')) { $env:VAPID_SUBJECT = $Subject }
if ([string]::IsNullOrWhiteSpace($env:VAPID_SUBJECT)) {
    $env:VAPID_SUBJECT = Read-Host 'VAPID 연락처 이메일 또는 HTTPS 연락처 URL을 입력하세요'
}
$env:VAPID_SUBJECT = $env:VAPID_SUBJECT.Trim()
if ($env:VAPID_SUBJECT -match '^[^\s@:/?#]+@[^\s@:/?#]+\.[^\s@:/?#]+$') { $env:VAPID_SUBJECT = 'mailto:' + $env:VAPID_SUBJECT }
& node (Join-Path $PSScriptRoot 'check-vapid.mjs') --check-env
if ($LASTEXITCODE -ne 0) { throw 'VAPID 사전 검증 실패: 위 원인 코드를 확인하세요. 서버와 DB는 시작하지 않았습니다.' }
Remove-Variable vapidConfig
if ($CheckOnly) { Write-Host 'VAPID 검사 완료: 키 파일을 변경하지 않았으며 서버와 DB를 시작하지 않았습니다.'; return }
if ([string]::IsNullOrWhiteSpace($env:DB_PASSWORD)) {
    $databaseCredential = Get-Credential -UserName 'root' -Message 'MySQL root 비밀번호를 입력하세요'
    $env:DB_PASSWORD = $databaseCredential.GetNetworkCredential().Password
    Remove-Variable databaseCredential
}
& .\mvnw.cmd spring-boot:run
if ($LASTEXITCODE -ne 0) { throw '백엔드 실행이 실패했습니다. 비밀정보를 제외한 오류 코드를 확인하세요.' }
