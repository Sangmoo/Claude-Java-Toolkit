# Helm Chart 실 환경 검증 체크리스트

> 이 문서는 `claude-toolkit` Helm 차트가 실제 Kubernetes 클러스터에서 정상 동작하는지 검증한 결과를 기록합니다.

**테스트 환경**: Kind (Kubernetes in Docker) — 로컬 PC 검증용
**대상 차트 버전**: 0.1.0 (`appVersion: 4.4.0`)

---

## 🚀 자동 검증 실행

### 한 번에 실행
```bash
# 환경변수 설정 (실제 API 키가 있으면 더 좋음 — 없어도 동작)
export CLAUDE_API_KEY=sk-ant-...

# 검증 스크립트 실행 (10~15분 소요, Docker 빌드 포함)
bash scripts/test-helm.sh
```

### 옵션
```bash
bash scripts/test-helm.sh --skip-build   # 이미지 재사용 (재시도 시 빠름)
bash scripts/test-helm.sh --keep         # 검증 후 클러스터 유지 (수동 디버깅)
```

### 결과 예시 (성공 시)
```
✓ docker 발견 (/usr/bin/docker)
✓ kind 발견 (/usr/local/bin/kind)
✓ Docker 데몬 응답 정상
▶ Kind 클러스터 'claude-test' 준비
✓ 클러스터 생성 완료
▶ Docker 이미지 빌드: claude-toolkit:test
✓ 이미지 빌드 완료
▶ 이미지를 Kind 클러스터에 로드
✓ 이미지 로드 완료
▶ Namespace + Secret 준비
✓ Secret 'claude-api-secret' 생성/갱신
▶ helm lint
✓ helm lint 통과
▶ helm install (시나리오 A: H2)
✓ helm install 성공
▶ Pod 상태 확인
✓ Pod READY 1/1
▶ Service / 헬스체크 검증
✓ 헬스체크 응답: status=UP
▶ Prometheus 메트릭 엔드포인트 검증
✓ claude_* 메트릭 47 줄 발견
✓ Spring Boot 정상 시작 확인
═══════════════════════════════════════════════════════════════════════
✓ 모든 검증 단계 통과!
═══════════════════════════════════════════════════════════════════════
```

---

## 📋 5 시나리오 체크리스트

자동 스크립트는 **시나리오 A** 만 실행합니다. 나머지는 수동 검증 권장.

### ✅ 시나리오 A: H2 + 단일 인스턴스 (자동 검증 대상)

| 항목 | 명령 | 기대 결과 | 결과 |
|------|------|----------|------|
| Pod READY | `kubectl get pod -n claude-toolkit` | `1/1 Running` | ⬜ |
| 헬스체크 | `kubectl exec ... -- curl /actuator/health` | `{"status":"UP"}` | ⬜ |
| Prometheus | `... -- curl /actuator/prometheus` | `claude_*` 메트릭 노출 | ⬜ |
| PVC bound | `kubectl get pvc -n claude-toolkit` | `Bound` | ⬜ |
| Spring 시작 로그 | `kubectl logs ...` | `Started ... Application` | ⬜ |
| 포트 포워드 | `kubectl port-forward ... 8027:80` | `http://localhost:8027` 접속 | ⬜ |

### ✅ 시나리오 B: 외부 PostgreSQL

먼저 Kind 안에 Postgres 띄우기:
```bash
kubectl create namespace claude-toolkit
helm install pg bitnami/postgresql -n claude-toolkit \
  --set auth.postgresPassword=secret \
  --set auth.database=claude_toolkit
```

차트 설치:
```bash
kubectl create secret generic db-secret \
  --from-literal=DB_PASSWORD=secret \
  -n claude-toolkit

helm upgrade --install ct ./helm/claude-toolkit \
  -n claude-toolkit \
  --set image.repository=claude-toolkit --set image.tag=test --set image.pullPolicy=Never \
  --set secret.existingSecret=claude-api-secret \
  --set db.type=postgresql \
  --set db.host=pg-postgresql \
  --set db.port=5432 \
  --set db.username=postgres \
  --set dbSecret.existingSecret=db-secret \
  --set persistence.enabled=false \
  --wait
```

| 항목 | 기대 결과 | 결과 |
|------|----------|------|
| Pod READY | `1/1 Running` | ⬜ |
| DB 연결 로그 | "HikariPool-1 - Start completed" | ⬜ |
| 테이블 자동 생성 | `kubectl exec pg-pod -- psql -c "\dt"` | review_history 등 보임 | ⬜ |

### ✅ 시나리오 C: Ingress + TLS

Kind 에 nginx-ingress 설치:
```bash
kubectl apply -f https://raw.githubusercontent.com/kubernetes/ingress-nginx/main/deploy/static/provider/kind/deploy.yaml
kubectl wait --namespace ingress-nginx --for=condition=ready pod \
  --selector=app.kubernetes.io/component=controller --timeout=120s
```

차트 업그레이드:
```bash
helm upgrade ct ./helm/claude-toolkit -n claude-toolkit \
  --reuse-values \
  --set ingress.enabled=true \
  --set ingress.host=ct.local \
  --set ingress.className=nginx
```

호스트 hosts 파일에 추가:
```
127.0.0.1  ct.local
```

| 항목 | 기대 결과 | 결과 |
|------|----------|------|
| Ingress 생성 | `kubectl get ingress -n claude-toolkit` | ADDRESS 할당됨 | ⬜ |
| HTTP 접속 | `curl http://ct.local/actuator/health` | 200 OK | ⬜ |

### ✅ 시나리오 D: HPA 자동 확장

```bash
helm upgrade ct ./helm/claude-toolkit -n claude-toolkit \
  --reuse-values \
  --set autoscaling.enabled=true \
  --set autoscaling.minReplicas=2 \
  --set autoscaling.maxReplicas=4 \
  --set autoscaling.targetCPUUtilizationPercentage=50
```

부하 도구로 CPU 90% 만들기 (별도 터미널):
```bash
kubectl run loadtest --rm -it --image=busybox --restart=Never -- \
  /bin/sh -c "while true; do wget -q -O- http://ct-claude-toolkit/actuator/health; done"
```

| 항목 | 기대 결과 | 결과 |
|------|----------|------|
| HPA 생성 | `kubectl get hpa -n claude-toolkit` | minReplicas=2 | ⬜ |
| Pod 수 증가 | `kubectl get pod -n claude-toolkit -w` | 부하 후 2→4로 증가 | ⬜ |
| 부하 종료 후 축소 | (5분 대기 후) | 2로 감소 | ⬜ |

### ✅ 시나리오 E: prometheus-operator + ServiceMonitor

prometheus-operator 설치:
```bash
helm install prom prometheus-community/kube-prometheus-stack -n monitoring --create-namespace
```

차트 업그레이드:
```bash
helm upgrade ct ./helm/claude-toolkit -n claude-toolkit \
  --reuse-values \
  --set monitoring.enabled=true \
  --set monitoring.serviceMonitor.enabled=true
```

| 항목 | 기대 결과 | 결과 |
|------|----------|------|
| ServiceMonitor 생성 | `kubectl get servicemonitor -n claude-toolkit` | ct 보임 | ⬜ |
| Prometheus 타깃 | `kubectl port-forward -n monitoring svc/prom-kube-prometheus-stack-prometheus 9090` → http://localhost:9090/targets | ct UP 상태 | ⬜ |
| 메트릭 검색 | Prometheus UI 에서 `claude_api_calls_total` 검색 | 결과 있음 | ⬜ |

---

## 🐛 알려진 제약사항 / 흔한 함정

| 문제 | 원인 | 해결 |
|------|------|------|
| `ImagePullBackOff` | `image.pullPolicy=Always` 가 기본 | `--set image.pullPolicy=Never` (Kind) |
| Pod 가 5분 후 OOMKilled | `resources.limits.memory` 너무 낮음 | `--set resources.limits.memory=2Gi` |
| readiness 가 계속 실패 | Spring Boot 시작이 느림 (~90초) | startupProbe 가 보호 (v4.4.0) |
| Ingress 에서 502 | nginx-ingress 미설치 | Kind nginx-ingress 매니페스트 적용 |
| HPA 가 동작 안함 | metrics-server 없음 | `kubectl apply -f metrics-server.yaml` |
| H2 데이터 사라짐 | PVC 삭제됨 | `kubectl delete pvc` 명시적으로 안하면 유지됨 |

---

## 📝 수동 검증 후 결과 기입

검증 완료 시점에 결과 칸을 ✅/❌로 채워주세요. 발견된 차트 결함이 있으면 다음 형식으로 추가:

```
### 발견 이슈
- [ ] (P1) 시나리오 X 에서 ... 실패. 원인: ... 수정: helm/claude-toolkit/templates/...
- [x] (P2) ... → values.yaml 의 ... 을 ... 로 변경하여 해결됨 (커밋 abc123)
```

---

## 🔗 참고

- Kind 설치: https://kind.sigs.k8s.io/docs/user/quick-start/
- Helm 설치: https://helm.sh/docs/intro/install/
- 차트 옵션: [`values.yaml`](./values.yaml)
- 시나리오별 helm 명령: [`README.md`](./README.md)
