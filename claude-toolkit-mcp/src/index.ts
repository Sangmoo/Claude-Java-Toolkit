#!/usr/bin/env node
/**
 * v4.7.x — #M3 Phase 3 + 사내망 공유: MCP Server entry point.
 *
 * 두 가지 transport 모드:
 *   - stdio (default) — Claude Code / Cursor / Cline 등이 spawn 하여 stdin/stdout 으로 통신
 *   - http            — Streamable HTTP, 사내망 공유. 클라이언트는 mcp-remote 등으로 접속
 *
 * 환경변수:
 *   공통:
 *     - CTK_URL                (필수) — 도구 base URL (예: http://localhost:8027)
 *     - CTK_DB_PROFILE_ID      (옵션) — Live DB 프로필 default
 *     - MCP_TRANSPORT          (옵션, default=stdio) — "stdio" 또는 "http"
 *   stdio 전용:
 *     - CTK_API_KEY            (필수) — /admin/api-keys 페이지에서 발급된 키
 *   http 전용:
 *     - MCP_HTTP_PORT          (옵션, default=8028)
 *     - MCP_HTTP_HOST          (옵션, default=0.0.0.0)
 *     - 클라이언트가 X-Api-Key 헤더로 키 전달 (per-request 패스스루)
 */

import { Server } from '@modelcontextprotocol/sdk/server/index.js';
import { StdioServerTransport } from '@modelcontextprotocol/sdk/server/stdio.js';
import { StreamableHTTPServerTransport } from '@modelcontextprotocol/sdk/server/streamableHttp.js';
import {
  CallToolRequestSchema,
  ListToolsRequestSchema,
} from '@modelcontextprotocol/sdk/types.js';
import express, { type Request, type Response } from 'express';
import { ToolkitClient } from './client.js';
import { TOOLS, buildAnalyzeRequest } from './tools.js';

/**
 * client 와 묶인 MCP Server 인스턴스를 만든다.
 * stdio: process 생애 동안 1개 (env 의 CTK_API_KEY)
 * http : 요청마다 1개 (요청 헤더의 X-Api-Key)
 */
function createServer(client: ToolkitClient): Server {
  const server = new Server(
    {
      name: '@claude-toolkit/mcp-server',
      version: '0.2.0',
    },
    {
      capabilities: {
        tools: {},
      },
    }
  );

  server.setRequestHandler(ListToolsRequestSchema, async () => ({
    tools: TOOLS.map(({ name, description, inputSchema }) => ({
      name,
      description,
      inputSchema,
    })),
  }));

  server.setRequestHandler(CallToolRequestSchema, async (request) => {
    const { name, arguments: args } = request.params;
    const argsObj = (args as Record<string, unknown>) ?? {};

    try {
      const req = buildAnalyzeRequest(name, argsObj);
      const resp = await client.analyze(req);

      const meta = [
        resp.cached ? '⚡ 캐시 적중' : null,
        resp.tokens ? `토큰: in=${resp.tokens.input}, out=${resp.tokens.output}` : null,
        resp.costUsd != null ? `비용: $${resp.costUsd.toFixed(4)}` : null,
        resp.elapsedMs != null ? `${resp.elapsedMs}ms` : null,
        resp.model ? `모델: ${resp.model}` : null,
      ].filter(Boolean).join(' · ');

      const fullText = meta
        ? `${resp.result}\n\n---\n_${meta}_`
        : resp.result;

      return {
        content: [{ type: 'text', text: fullText }],
      };
    } catch (e) {
      const msg = (e as Error).message ?? '알 수 없는 오류';
      return {
        content: [{ type: 'text', text: `❌ 분석 실패: ${msg}` }],
        isError: true,
      };
    }
  });

  return server;
}

async function runStdio(): Promise<void> {
  let client: ToolkitClient;
  try {
    client = new ToolkitClient();
  } catch (e) {
    process.stderr.write(`[MCP] stdio 시작 실패: ${(e as Error).message}\n`);
    process.exit(1);
  }
  const server = createServer(client);
  const transport = new StdioServerTransport();
  await server.connect(transport);
  process.stderr.write(
    `[MCP] stdio ready — @claude-toolkit/mcp-server 0.2.0 (${TOOLS.length} tools)\n`
  );
}

async function runHttp(): Promise<void> {
  const baseUrl = process.env.CTK_URL;
  if (!baseUrl) {
    process.stderr.write('[MCP] http 시작 실패: 환경변수 CTK_URL 미설정\n');
    process.exit(1);
  }

  const port = parseInt(process.env.MCP_HTTP_PORT ?? '8028', 10);
  const host = process.env.MCP_HTTP_HOST ?? '0.0.0.0';

  const app = express();
  app.use(express.json({ limit: '4mb' }));

  // 단순 health check — 운영 모니터링용
  app.get('/healthz', (_req: Request, res: Response) => {
    res.json({ status: 'ok', tools: TOOLS.length, transport: 'http' });
  });

  // 메인 MCP 엔드포인트 — Streamable HTTP, stateless
  app.post('/mcp', async (req: Request, res: Response) => {
    const apiKey = req.header('x-api-key') ?? req.header('X-Api-Key');
    if (!apiKey) {
      res.status(401).json({
        jsonrpc: '2.0',
        error: { code: -32000, message: 'X-Api-Key 헤더 필요' },
        id: null,
      });
      return;
    }

    let client: ToolkitClient;
    try {
      client = new ToolkitClient({ baseUrl, apiKey });
    } catch (e) {
      res.status(500).json({
        jsonrpc: '2.0',
        error: { code: -32000, message: (e as Error).message },
        id: null,
      });
      return;
    }

    const server = createServer(client);
    const transport = new StreamableHTTPServerTransport({
      sessionIdGenerator: undefined, // stateless — request 마다 신규
    });

    res.on('close', () => {
      transport.close().catch(() => undefined);
      server.close().catch(() => undefined);
    });

    try {
      await server.connect(transport);
      await transport.handleRequest(req, res, req.body);
    } catch (e) {
      process.stderr.write(`[MCP] http 처리 실패: ${(e as Error).message}\n`);
      if (!res.headersSent) {
        res.status(500).json({
          jsonrpc: '2.0',
          error: { code: -32603, message: 'Internal error' },
          id: null,
        });
      }
    }
  });

  // stateless — GET/DELETE 는 405
  const notAllowed = (_req: Request, res: Response) => {
    res.status(405).json({
      jsonrpc: '2.0',
      error: { code: -32000, message: 'Method Not Allowed (stateless mode)' },
      id: null,
    });
  };
  app.get('/mcp', notAllowed);
  app.delete('/mcp', notAllowed);

  app.listen(port, host, () => {
    process.stderr.write(
      `[MCP] http ready — listening on ${host}:${port} (${TOOLS.length} tools, stateless, per-request X-Api-Key)\n`
    );
  });
}

async function main(): Promise<void> {
  const transport = (process.env.MCP_TRANSPORT ?? 'stdio').toLowerCase();
  switch (transport) {
    case 'stdio':
      await runStdio();
      break;
    case 'http':
      await runHttp();
      break;
    default:
      process.stderr.write(
        `[MCP] Unknown MCP_TRANSPORT="${transport}" (expected: stdio | http)\n`
      );
      process.exit(1);
  }
}

main().catch((e) => {
  process.stderr.write(`[MCP] Fatal: ${(e as Error).message}\n`);
  process.exit(1);
});
