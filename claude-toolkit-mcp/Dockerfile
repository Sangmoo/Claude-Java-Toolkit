# ═══════════════════════════════════════════════════════════════
# claude-toolkit-mcp — Streamable HTTP transport (사내망 공유)
# ═══════════════════════════════════════════════════════════════
# Build context = repo root (docker-compose 가 그렇게 호출).
#   docker-compose build claude-toolkit-mcp
#
# Runtime:
#   - 8028 listen, X-Api-Key per-request 패스스루
#   - CTK_URL 은 docker network DNS 로 backend 에 연결 (default: http://claude-toolkit:8027)

# ── Stage 1: build ──────────────────────────────────────────────
FROM node:20-alpine AS builder
WORKDIR /build

# manifest 만 먼저 — npm ci layer 캐시 최대화
COPY claude-toolkit-mcp/package.json claude-toolkit-mcp/package-lock.json ./
RUN npm ci

# 소스 복사 + 빌드
COPY claude-toolkit-mcp/tsconfig.json ./
COPY claude-toolkit-mcp/src ./src
RUN npm run build

# 운영 의존성만 추리기 — 다음 stage 가 쓸 작은 node_modules
RUN npm prune --omit=dev


# ── Stage 2: runtime ────────────────────────────────────────────
FROM node:20-alpine
LABEL maintainer="Claude Java Toolkit"

WORKDIR /app

# tini 로 PID 1 시그널 처리 — docker stop 시 graceful shutdown
RUN apk add --no-cache tini curl

COPY --from=builder /build/dist ./dist
COPY --from=builder /build/node_modules ./node_modules
COPY --from=builder /build/package.json ./

ENV NODE_ENV=production \
    MCP_TRANSPORT=http \
    MCP_HTTP_HOST=0.0.0.0 \
    MCP_HTTP_PORT=8028 \
    CTK_URL=http://claude-toolkit:8027 \
    TZ=Asia/Seoul

EXPOSE 8028

HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
    CMD curl -fsS http://localhost:8028/healthz || exit 1

ENTRYPOINT ["/sbin/tini", "--"]
CMD ["node", "dist/index.js"]
