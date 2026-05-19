# -----------------------------------------------------------------------------
# claude-toolkit-mcp — Windows 서비스 설치 스크립트 (NSSM 기반)
# -----------------------------------------------------------------------------
# 사전 준비:
#   1) NSSM 설치 (https://nssm.cc/download) — choco install nssm 도 가능
#   2) Node.js 18+ 설치
#   3) 본 스크립트는 "관리자" PowerShell 에서 실행
#
# 사용:
#   .\install-windows-service.ps1
#   .\install-windows-service.ps1 -Port 8030 -Host 127.0.0.1
#
# 환경변수 파일 (C:\ProgramData\claude-toolkit-mcp\service.env) 가 우선,
# -Port / -Host 인자는 그 다음 우선순위.
# -----------------------------------------------------------------------------

[CmdletBinding()]
param(
    [string]$ServiceName = "ClaudeToolkitMcp",
    [string]$ProjectRoot = "C:\Sangmoo\claude-java-toolkit\claude-toolkit-mcp",
    [string]$NodeExe     = "C:\Program Files\nodejs\node.exe",
    [string]$CtkUrl      = "http://localhost:8027",
    [string]$BindHost    = "0.0.0.0",
    [int]$Port           = 8028,
    [string]$LogDir      = "C:\ProgramData\claude-toolkit-mcp\logs"
)

$ErrorActionPreference = "Stop"

# 1) 사전 검증
if (-not (Get-Command nssm -ErrorAction SilentlyContinue)) {
    throw "NSSM 미설치 — https://nssm.cc/download 에서 받아 PATH 에 추가하세요."
}
if (-not (Test-Path $NodeExe)) {
    throw "Node.js 가 $NodeExe 에 없음. -NodeExe 로 경로 지정하거나 설치 후 재시도."
}
$entryJs = Join-Path $ProjectRoot "dist\index.js"
if (-not (Test-Path $entryJs)) {
    throw "$entryJs 가 없음. 먼저 'npm run build' 실행."
}

# 2) 로그 디렉터리
New-Item -ItemType Directory -Force -Path $LogDir | Out-Null

# 3) 기존 서비스가 있으면 제거
$existing = Get-Service -Name $ServiceName -ErrorAction SilentlyContinue
if ($existing) {
    Write-Host "기존 서비스 '$ServiceName' 제거 중..."
    nssm stop $ServiceName confirm | Out-Null
    nssm remove $ServiceName confirm | Out-Null
}

# 4) 서비스 설치
Write-Host "서비스 설치 중: $ServiceName"
nssm install $ServiceName $NodeExe $entryJs
nssm set $ServiceName AppDirectory $ProjectRoot
nssm set $ServiceName DisplayName "Claude Toolkit MCP Server"
nssm set $ServiceName Description "Streamable HTTP MCP server (사내망 공유, per-request X-Api-Key)"
nssm set $ServiceName Start SERVICE_AUTO_START

# 5) 환경변수
nssm set $ServiceName AppEnvironmentExtra `
    "CTK_URL=$CtkUrl" `
    "MCP_TRANSPORT=http" `
    "MCP_HTTP_HOST=$BindHost" `
    "MCP_HTTP_PORT=$Port"

# 6) 로그 — stderr/stdout 모두 파일로
nssm set $ServiceName AppStdout (Join-Path $LogDir "out.log")
nssm set $ServiceName AppStderr (Join-Path $LogDir "err.log")
nssm set $ServiceName AppRotateFiles 1
nssm set $ServiceName AppRotateOnline 1
nssm set $ServiceName AppRotateBytes 10485760  # 10MB

# 7) 시작
Write-Host "서비스 시작 중..."
nssm start $ServiceName

# 8) 검증
Start-Sleep -Seconds 2
$status = (Get-Service -Name $ServiceName).Status
Write-Host ""
Write-Host "✅ 설치 완료: $ServiceName ($status)"
Write-Host "   bind     : ${BindHost}:${Port}"
Write-Host "   backend  : $CtkUrl"
Write-Host "   logs     : $LogDir"
Write-Host ""
Write-Host "health check:"
Write-Host "   curl http://${env:COMPUTERNAME}:${Port}/healthz"
