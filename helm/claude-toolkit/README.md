# claude-toolkit Helm Chart

> ⚠️ **Status**: 준비용 차트 (v0.1.0) — 실제 K8s 배포 환경은 아직 공식적으로 검증되지 않았습니다. 차트 자체는 `helm lint` / `helm template` 으로 정적 검증되었습니다. 실제 운영 환경에 적용하기 전 반드시 테스트 클러스터에서 동작 확인을 권장합니다.

## 사전 요구사항

- Kubernetes 1.23+
- Helm 3.8+
- (옵션) Ingress Controller (nginx-ingress 등)
- (옵션) StorageClass (H2 모드에서 PVC 사용 시)
- 컨테이너 이미지가 빌드되어 레지스트리에 푸시되어 있어야 함

## 빠른 시작

### 1. 이미지 빌드 + 레지스트리 푸시

프로젝트 루트에서:
```bash
docker build -t ghcr.io/<your-org>/claude-java-toolkit:4.3.0 .
docker push ghcr.io/<your-org>/claude-java-toolkit:4.3.0
```

### 2. Claude API Key Secret 사전 생성 (권장)

```bash
kubectl create namespace claude-toolkit
kubectl create secret generic claude-api-secret \
  --from-literal=CLAUDE_API_KEY=sk-ant-XXXXX \
  -n claude-toolkit
```

### 3. Helm 설치 (H2 in-memory)

```bash
helm install claude-toolkit ./helm/claude-toolkit \
  -n claude-toolkit \
  --set image.repository=ghcr.io/<your-org>/claude-java-toolkit \
  --set image.tag=4.3.0 \
  --set secret.existingSecret=claude-api-secret \
  --set persistence.size=10Gi
```

### 4. 외부 PostgreSQL 사용

```bash
kubectl create secret generic db-secret \
  --from-literal=DB_PASSWORD=mypassword \
  -n claude-toolkit

helm install claude-toolkit ./helm/claude-toolkit \
  -n claude-toolkit \
  --set db.type=postgresql \
  --set db.host=postgres.example.com \
  --set db.port=5432 \
  --set db.name=claude_toolkit \
  --set db.username=claude \
  --set dbSecret.existingSecret=db-secret \
  --set persistence.enabled=false \
  --set secret.existingSecret=claude-api-secret
```

### 5. Ingress 활성화 + HPA

```bash
helm upgrade claude-toolkit ./helm/claude-toolkit \
  -n claude-toolkit \
  --reuse-values \
  --set ingress.enabled=true \
  --set ingress.host=claude.example.com \
  --set ingress.className=nginx \
  --set autoscaling.enabled=true \
  --set autoscaling.minReplicas=2 \
  --set autoscaling.maxReplicas=5
```

### 6. Prometheus 모니터링 (prometheus-operator 사용 시)

```bash
helm upgrade claude-toolkit ./helm/claude-toolkit \
  -n claude-toolkit \
  --reuse-values \
  --set monitoring.enabled=true \
  --set monitoring.serviceMonitor.enabled=true
```

## 주요 values 옵션

| 키 | 기본값 | 설명 |
|----|--------|------|
| `image.repository` | `ghcr.io/sangmoo/claude-java-toolkit` | Docker 이미지 |
| `image.tag` | `""` (Chart.appVersion) | 이미지 태그 |
| `replicaCount` | `1` | Pod 복제 수 |
| `db.type` | `h2` | `h2` / `mysql` / `postgresql` |
| `claude.apiKey` | `""` | Anthropic API Key (또는 `secret.existingSecret`) |
| `claude.model` | `claude-sonnet-4-5` | 기본 모델 |
| `persistence.size` | `5Gi` | H2 데이터 PVC 크기 |
| `ingress.enabled` | `false` | Ingress 활성화 |
| `autoscaling.enabled` | `false` | HPA 활성화 |
| `monitoring.enabled` | `false` | Prometheus 스크랩 어노테이션 |

전체 옵션은 [values.yaml](./values.yaml) 참고.

## 검증 명령어

```bash
# 차트 정적 검증
helm lint ./helm/claude-toolkit

# 렌더링된 매니페스트 확인 (실제 적용 안 함)
helm template claude-toolkit ./helm/claude-toolkit \
  --set claude.apiKey=test \
  --debug | less

# 드라이런
helm install claude-toolkit ./helm/claude-toolkit \
  --dry-run --debug -n claude-toolkit
```

## 업그레이드 / 삭제

```bash
helm upgrade claude-toolkit ./helm/claude-toolkit -n claude-toolkit \
  --reuse-values --set image.tag=4.3.1

helm uninstall claude-toolkit -n claude-toolkit
```
