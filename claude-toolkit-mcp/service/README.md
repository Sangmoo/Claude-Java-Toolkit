# claude-toolkit-mcp — 서비스 실행 스크립트

사내망 1대(또는 여러 대 사내 호스트)에서 MCP 서버를 **상시 가동**하기 위한 스크립트 모음.

## 파일

| 파일 | 대상 OS | 설명 |
|------|---------|------|
| `claude-toolkit-mcp.service` | Linux | systemd unit |
| `claude-toolkit-mcp.env.example` | 공통 | 환경변수 템플릿 |
| `install-windows-service.ps1` | Windows | NSSM 기반 서비스 등록 |

## Linux (systemd)

```bash
# 1) 사용자/디렉터리 준비
sudo useradd --system --no-create-home claude-toolkit
sudo mkdir -p /opt/claude-toolkit-mcp /var/log/claude-toolkit-mcp
sudo chown -R claude-toolkit:claude-toolkit /opt/claude-toolkit-mcp /var/log/claude-toolkit-mcp

# 2) 빌드 산출물 배포
sudo cp -r dist/ package.json package-lock.json /opt/claude-toolkit-mcp/
cd /opt/claude-toolkit-mcp && sudo -u claude-toolkit npm ci --omit=dev

# 3) 서비스 + 환경변수 설치
sudo cp service/claude-toolkit-mcp.service /etc/systemd/system/
sudo cp service/claude-toolkit-mcp.env.example /etc/claude-toolkit-mcp.env
sudo $EDITOR /etc/claude-toolkit-mcp.env       # CTK_URL 등 편집

# 4) 기동
sudo systemctl daemon-reload
sudo systemctl enable --now claude-toolkit-mcp

# 5) 검증
sudo systemctl status claude-toolkit-mcp
curl http://localhost:8028/healthz
journalctl -u claude-toolkit-mcp -f
```

## Windows (NSSM)

```powershell
# 1) 빌드
cd C:\Sangmoo\claude-java-toolkit\claude-toolkit-mcp
npm install
npm run build

# 2) NSSM 설치 (관리자 PowerShell)
choco install nssm   # 또는 https://nssm.cc/download 에서 수동

# 3) 서비스 등록 (관리자 PowerShell)
.\service\install-windows-service.ps1

# 옵션 변경 예
.\service\install-windows-service.ps1 -Port 8030 -BindHost 127.0.0.1

# 4) 검증
Get-Service ClaudeToolkitMcp
curl http://$env:COMPUTERNAME:8028/healthz
Get-Content C:\ProgramData\claude-toolkit-mcp\logs\err.log -Tail 50 -Wait
```

## 운영 체크리스트

- [ ] backend(`CTK_URL`, 기본 :8027) 가 같은 호스트(또는 도달 가능한 곳)에서 LIVE
- [ ] 방화벽 inbound 8028 (사내망 한정 — 외부 인터넷 노출 금지)
- [ ] `/admin/api-keys` 에서 사용자별 키 발급 (audit 분리)
- [ ] `/healthz` 200 응답 확인
- [ ] 클라이언트 PC 1대에서 `npx mcp-remote http://호스트:8028/mcp --header X-Api-Key:...` 으로 연결 테스트
