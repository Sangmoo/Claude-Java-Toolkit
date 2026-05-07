#!/usr/bin/env node
/**
 * v4.7.x — #M3 Phase 3: MCP Server entry point.
 *
 * Claude Code / Cursor / Cline 등 MCP 호환 client 가 stdio 로 spawn.
 * 환경변수:
 *   - CTK_URL          (필수) — 도구 base URL (예: http://localhost:8027)
 *   - CTK_API_KEY      (필수) — /admin/api-keys 페이지에서 발급된 키
 *   - CTK_DB_PROFILE_ID (옵션) — Live DB 프로필 default
 */

import { Server } from '@modelcontextprotocol/sdk/server/index.js';
import { StdioServerTransport } from '@modelcontextprotocol/sdk/server/stdio.js';
import {
  CallToolRequestSchema,
  ListToolsRequestSchema,
} from '@modelcontextprotocol/sdk/types.js';
import { ToolkitClient } from './client.js';
import { TOOLS, buildAnalyzeRequest } from './tools.js';

async function main() {
  // 환경변수 검증 — 잘못된 설정이면 즉시 실패
  let client: ToolkitClient;
  try {
    client = new ToolkitClient();
  } catch (e) {
    process.stderr.write(`[MCP] 시작 실패: ${(e as Error).message}\n`);
    process.exit(1);
  }

  const server = new Server(
    {
      name: '@claude-toolkit/mcp-server',
      version: '0.1.0',
    },
    {
      capabilities: {
        tools: {},
      },
    }
  );

  // Tool 목록 노출 — Claude Code 등이 탐색 시 호출
  server.setRequestHandler(ListToolsRequestSchema, async () => ({
    tools: TOOLS.map(({ name, description, inputSchema }) => ({
      name,
      description,
      inputSchema,
    })),
  }));

  // Tool 호출 — Claude 가 사용자 의도에 따라 dispatch
  server.setRequestHandler(CallToolRequestSchema, async (request) => {
    const { name, arguments: args } = request.params;
    const argsObj = (args as Record<string, unknown>) ?? {};

    try {
      const req = buildAnalyzeRequest(name, argsObj);
      const resp = await client.analyze(req);

      // 응답을 markdown 텍스트로 — MCP 표준 'text' content
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

  // stdio transport — Claude Code 가 spawn 후 stdin/stdout 으로 통신
  const transport = new StdioServerTransport();
  await server.connect(transport);

  process.stderr.write(
    `[MCP] @claude-toolkit/mcp-server 0.1.0 ready (${TOOLS.length} tools)\n`
  );
}

main().catch((e) => {
  process.stderr.write(`[MCP] Fatal: ${(e as Error).message}\n`);
  process.exit(1);
});
