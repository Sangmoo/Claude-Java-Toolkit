#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
# v4.4.0 — Helm Chart Kind 검증 스크립트
# ─────────────────────────────────────────────────────────────────────────────
# 로컬 Kind (Kubernetes in Docker) 클러스터에 claude-toolkit Helm Chart 를
# 실제로 install / test / uninstall 해서 차트 동작을 검증합니다.
#
# 사전 요구사항:
#   - Docker Desktop (실행 중)
#   - kind (winget install Kubernetes.kind 또는 brew install kind)
#   - kubectl (winget install Kubernetes.kubectl 또는 brew install kubectl)
#   - helm (choco install kubernetes-helm 또는 brew install helm)
#
# 사용법:
#   bash scripts/test-helm.sh              # 모든 시나리오 실행
#   bash scripts/test-helm.sh --skip-build  # 이미지 빌드 생략 (재실행 시)
#   bash scripts/test-helm.sh --keep        # 검증 후 클러스터 유지 (수동 디버깅용)
#
# 결과: helm/claude-toolkit/VALIDATION.md 의 체크리스트를 채울 수 있도록
#       각 단계 출력을 명확히 표시합니다.

set -e

# ── 색상 출력 ─────────────────────────────────────────────────────────────
RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; BLUE='\033[0;34m'; NC='\033[0m'
ok()    { echo -e "${GREEN}✓${NC} $1"; }
warn()  { echo -e "${YELLOW}⚠${NC} $1"; }
fail()  { echo -e "${RED}✗${NC} $1"; exit 1; }
info()  { echo -e "${BLUE}▶${NC} $1"; }

# ── 옵션 파싱 ─────────────────────────────────────────────────────────────
SKIP_BUILD=false
KEEP_CLUSTER=false
for arg in "$@"; do
  case $arg in
    --skip-build) SKIP_BUILD=true ;;
    --keep)       KEEP_CLUSTER=true ;;
    --help|-h)
      head -n 22 "$0" | tail -n 21 | sed 's|^# ||;s|^#||'
      exit 0 ;;
  esac
done

CLUSTER_NAME="claude-test"
NAMESPACE="claude-toolkit"
IMAGE_NAME="claude-toolkit:test"
RELEASE_NAME="ct"
CHART_PATH="./helm/claude-toolkit"

# ── 사전 체크 ─────────────────────────────────────────────────────────────
info "사전 도구 확인"
for cmd in docker kind kubectl helm; do
  if ! command -v $cmd &> /dev/null; then
    fail "$cmd 명령을 찾을 수 없습니다. 설치 후 다시 실행하세요."
  fi
  ok "$cmd 발견 ($(command -v $cmd))"
done

if ! docker info &> /dev/null; then
  fail "Docker 가 실행 중이 아닙니다. Docker Desktop 을 시작하세요."
fi
ok "Docker 데몬 응답 정상"

# ── 1. Kind 클러스터 생성 ─────────────────────────────────────────────────
info "Kind 클러스터 '$CLUSTER_NAME' 준비"
if kind get clusters 2>/dev/null | grep -q "^${CLUSTER_NAME}$"; then
  warn "기존 클러스터 발견 — 재사용 ($KEEP_CLUSTER 옵션으로 유지된 상태)"
else
  kind create cluster --name "$CLUSTER_NAME" --wait 60s
  ok "클러스터 생성 완료"
fi
kubectl cluster-info --context "kind-${CLUSTER_NAME}" | head -3

# ── 2. 이미지 빌드 + Kind 클러스터에 로드 ─────────────────────────────────
if [ "$SKIP_BUILD" = false ]; then
  info "Docker 이미지 빌드: $IMAGE_NAME"
  docker build -t "$IMAGE_NAME" .
  ok "이미지 빌드 완료"

  info "이미지를 Kind 클러스터에 로드 (registry 없이)"
  kind load docker-image "$IMAGE_NAME" --name "$CLUSTER_NAME"
  ok "이미지 로드 완료"
else
  warn "--skip-build 옵션 — 이미지 빌드 생략"
fi

# ── 3. Namespace + Secret ─────────────────────────────────────────────────
info "Namespace + Secret 준비"
kubectl create namespace "$NAMESPACE" --dry-run=client -o yaml | kubectl apply -f -

API_KEY="${CLAUDE_API_KEY:-sk-ant-test-key-placeholder}"
kubectl create secret generic claude-api-secret \
  --from-literal=CLAUDE_API_KEY="$API_KEY" \
  -n "$NAMESPACE" --dry-run=client -o yaml | kubectl apply -f -
ok "Secret 'claude-api-secret' 생성/갱신"

# ── 4. helm lint ──────────────────────────────────────────────────────────
info "helm lint — 차트 정적 검증"
helm lint "$CHART_PATH"
ok "helm lint 통과"

# ── 5. helm template — 매니페스트 미리보기 ────────────────────────────────
info "helm template — 매니페스트 렌더링 검증"
helm template "$RELEASE_NAME" "$CHART_PATH" \
  --set image.repository="${IMAGE_NAME%:*}" \
  --set image.tag="${IMAGE_NAME##*:}" \
  --set image.pullPolicy=Never \
  --set secret.existingSecret=claude-api-secret \
  > /tmp/helm-rendered.yaml
ok "helm template 렌더링 성공 (/tmp/helm-rendered.yaml, $(wc -l < /tmp/helm-rendered.yaml) lines)"

# ── 6. helm install (시나리오 A: H2 단일 인스턴스) ────────────────────────
info "시나리오 A: H2 + 단일 인스턴스 + healthcheck"
helm upgrade --install "$RELEASE_NAME" "$CHART_PATH" \
  -n "$NAMESPACE" \
  --set image.repository="${IMAGE_NAME%:*}" \
  --set image.tag="${IMAGE_NAME##*:}" \
  --set image.pullPolicy=Never \
  --set secret.existingSecret=claude-api-secret \
  --set healthcheck.initialDelaySeconds=90 \
  --wait --timeout=5m
ok "helm install 성공"

# ── 7. Pod 상태 확인 ─────────────────────────────────────────────────────
info "Pod 상태 확인"
kubectl get pods -n "$NAMESPACE" -l "app.kubernetes.io/instance=$RELEASE_NAME"
kubectl wait --for=condition=ready pod \
  -l "app.kubernetes.io/instance=$RELEASE_NAME" \
  -n "$NAMESPACE" --timeout=180s
ok "Pod READY 1/1"

# ── 8. 서비스 + 헬스체크 ─────────────────────────────────────────────────
info "Service / 헬스체크 검증"
kubectl get svc -n "$NAMESPACE"
POD=$(kubectl get pod -n "$NAMESPACE" -l "app.kubernetes.io/instance=$RELEASE_NAME" -o jsonpath='{.items[0].metadata.name}')
HEALTH=$(kubectl exec -n "$NAMESPACE" "$POD" -- curl -sf http://localhost:8027/actuator/health || echo "FAIL")
if [[ "$HEALTH" == *"\"status\":\"UP\""* ]]; then
  ok "헬스체크 응답: status=UP"
else
  warn "헬스체크 응답 비정상: $HEALTH"
fi

# ── 9. Prometheus 메트릭 노출 확인 ───────────────────────────────────────
info "Prometheus 메트릭 엔드포인트 검증"
METRICS_LINES=$(kubectl exec -n "$NAMESPACE" "$POD" -- curl -sf http://localhost:8027/actuator/prometheus | grep -c "^claude_" || echo 0)
if [ "$METRICS_LINES" -gt 0 ]; then
  ok "claude_* 메트릭 $METRICS_LINES 줄 발견"
else
  warn "claude_* 메트릭이 노출되지 않음 — 확인 필요"
fi

# ── 10. PVC 확인 (H2 모드) ───────────────────────────────────────────────
info "PVC (H2 영속화) 확인"
PVC_STATUS=$(kubectl get pvc -n "$NAMESPACE" -l "app.kubernetes.io/instance=$RELEASE_NAME" -o jsonpath='{.items[*].status.phase}' 2>/dev/null || echo "NONE")
ok "PVC 상태: ${PVC_STATUS:-(없음 — persistence 비활성)}"

# ── 11. 로그 확인 (Spring Boot 시작 메시지) ─────────────────────────────
info "Spring Boot 시작 로그 확인"
if kubectl logs -n "$NAMESPACE" "$POD" --tail=50 | grep -q "Started.*Application"; then
  ok "Spring Boot 정상 시작 확인"
else
  warn "Spring Boot 시작 로그를 찾지 못함 — 수동 확인 필요"
  kubectl logs -n "$NAMESPACE" "$POD" --tail=20
fi

# ── 12. 포트 포워드 안내 ─────────────────────────────────────────────────
info "수동 검증을 위한 포트 포워드 안내"
echo "  kubectl port-forward -n $NAMESPACE svc/$RELEASE_NAME-claude-toolkit 8027:80"
echo "  → 브라우저에서 http://localhost:8027 접속 (admin/admin1234)"

# ── 정리 ─────────────────────────────────────────────────────────────────
echo ""
echo "═══════════════════════════════════════════════════════════════════════"
ok "모든 검증 단계 통과!"
echo "═══════════════════════════════════════════════════════════════════════"

if [ "$KEEP_CLUSTER" = false ]; then
  echo ""
  read -p "클러스터를 삭제하시겠습니까? [Y/n] " -n 1 -r
  echo ""
  if [[ ! $REPLY =~ ^[Nn]$ ]]; then
    helm uninstall "$RELEASE_NAME" -n "$NAMESPACE" --wait || true
    kubectl delete pvc -l "app.kubernetes.io/instance=$RELEASE_NAME" -n "$NAMESPACE" || true
    kind delete cluster --name "$CLUSTER_NAME"
    ok "클러스터 + 차트 정리 완료"
  fi
else
  warn "--keep 옵션 — 클러스터를 유지합니다. 수동 정리:"
  echo "  helm uninstall $RELEASE_NAME -n $NAMESPACE"
  echo "  kind delete cluster --name $CLUSTER_NAME"
fi
