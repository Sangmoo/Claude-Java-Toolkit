/**
 * v4.7.x — #M3 Phase 3: MCP Tool 카탈로그.
 *
 * 12개 tool 을 MCP 표준 형식으로 노출.
 * Claude Code / Cursor / Cline 이 자동 발견하여 사용 가능.
 */

import type { Tool } from '@modelcontextprotocol/sdk/types.js';

/**
 * 모든 tool 의 공통 input schema 부분 (Live DB 통합).
 */
const liveDbField = {
  dbProfileId: {
    type: 'number',
    description: 'Live DB 프로필 ID (옵션) — 설정 시 EXPLAIN PLAN + 통계 + 인덱스 메타 자동 첨부. 환경변수 CTK_DB_PROFILE_ID 가 default.',
  },
} as const;

/**
 * 도구의 12개 분석 tool 정의.
 * 각 tool 은 MCP server 가 호출 시 backend feature 에 매핑.
 */
export const TOOLS: Array<Tool & { feature: string }> = [
  // ── SQL 분석 (6개) — Live DB 통합 ─────────────────────────────────
  {
    name: 'sql_review',
    feature: 'sql_review',
    description:
      'SQL 코드 리뷰 — 성능 / 보안 / 가독성 이슈 발견 + 개선 권장사항. ' +
      'dbProfileId 설정 시 실데이터 통계 (NUM_ROWS / 인덱스) 자동 첨부.',
    inputSchema: {
      type: 'object',
      properties: {
        input: { type: 'string', description: '분석할 SQL' },
        reviewType: {
          type: 'string',
          enum: ['review', 'security'],
          description: 'review (일반 리뷰) / security (보안 중심)',
          default: 'review',
        },
        ...liveDbField,
      },
      required: ['input'],
    },
  },
  {
    name: 'explain_plan',
    feature: 'explain_plan',
    description:
      'SQL 실행계획 분석 — EXPLAIN PLAN 해석 + 병목 발견 + 튜닝 권장. ' +
      'dbProfileId 설정 시 EXPLAIN PLAN 본문 자동 수집 (SQL 만 입력하면 됨).',
    inputSchema: {
      type: 'object',
      properties: {
        input: { type: 'string', description: '분석할 SQL' },
        explainPlan: {
          type: 'string',
          description: 'EXPLAIN PLAN 본문 (옵션 — Live DB 활성 시 자동 수집)',
        },
        ...liveDbField,
      },
      required: ['input'],
    },
  },
  {
    name: 'index_advisor',
    feature: 'index_advisor',
    description:
      '인덱스 추천 — WHERE/JOIN 절 분석하여 누락된 인덱스 DDL 생성 + 영향도 평가.',
    inputSchema: {
      type: 'object',
      properties: {
        input: { type: 'string', description: '분석할 SQL' },
        ...liveDbField,
      },
      required: ['input'],
    },
  },
  {
    name: 'sql_translate',
    feature: 'sql_translate',
    description:
      'SQL DB 간 번역 — Oracle ↔ MySQL ↔ PostgreSQL ↔ MSSQL. ' +
      '문법 차이 + 함수 매핑 + 호환성 주의사항 표시.',
    inputSchema: {
      type: 'object',
      properties: {
        input: { type: 'string', description: '원본 SQL' },
        sourceDb: {
          type: 'string',
          enum: ['oracle', 'mysql', 'postgresql', 'mssql'],
          description: '원본 DB',
        },
        targetDb: {
          type: 'string',
          enum: ['oracle', 'mysql', 'postgresql', 'mssql'],
          description: '대상 DB',
        },
      },
      required: ['input', 'sourceDb', 'targetDb'],
    },
  },
  {
    name: 'sql_batch',
    feature: 'sql_batch',
    description: '여러 SQL 일괄 분석 — 배치 작업의 SQL 모음을 한 번에 리뷰',
    inputSchema: {
      type: 'object',
      properties: {
        input: { type: 'string', description: '여러 SQL (세미콜론 또는 빈 줄로 구분)' },
        ...liveDbField,
      },
      required: ['input'],
    },
  },
  {
    name: 'erd_analysis',
    feature: 'erd_analysis',
    description:
      'ERD 분석 — DDL / SQL / Java 엔티티 코드에서 테이블 관계 추출 + Mermaid 다이어그램',
    inputSchema: {
      type: 'object',
      properties: {
        input: { type: 'string', description: 'DDL 또는 JPA Entity 코드' },
        ...liveDbField,
      },
      required: ['input'],
    },
  },

  // ── Java/Code 분석 (6개) ─────────────────────────────────────────
  {
    name: 'code_review',
    feature: 'code_review',
    description:
      'Java 코드 리뷰 — 성능 / 보안 / 가독성 이슈 + 개선 권장사항',
    inputSchema: {
      type: 'object',
      properties: {
        input: { type: 'string', description: '분석할 Java 코드' },
      },
      required: ['input'],
    },
  },
  {
    name: 'doc_gen',
    feature: 'doc_gen',
    description: '기술 문서 생성 — Java 클래스 코드 → 마크다운 기술 문서',
    inputSchema: {
      type: 'object',
      properties: {
        input: { type: 'string', description: 'Java 클래스 코드' },
      },
      required: ['input'],
    },
  },
  {
    name: 'complexity',
    feature: 'complexity',
    description:
      '복잡도 분석 — Cyclomatic / Halstead / 유지보수 점수 + 리팩터링 권장',
    inputSchema: {
      type: 'object',
      properties: {
        input: { type: 'string', description: 'Java 메서드/클래스 코드' },
      },
      required: ['input'],
    },
  },
  {
    name: 'commit_msg',
    feature: 'commit_msg',
    description:
      '커밋 메시지 생성 — git diff → conventional commits 형식 + 한국어 옵션',
    inputSchema: {
      type: 'object',
      properties: {
        input: { type: 'string', description: 'git diff 출력' },
      },
      required: ['input'],
    },
  },
  {
    name: 'regex_gen',
    feature: 'regex_gen',
    description: '정규식 생성 — 자연어 요구사항 → 정규식 + 테스트 케이스',
    inputSchema: {
      type: 'object',
      properties: {
        input: { type: 'string', description: '자연어 요구사항' },
        language: {
          type: 'string',
          enum: ['java', 'javascript', 'python'],
          description: '대상 언어',
          default: 'java',
        },
      },
      required: ['input'],
    },
  },
  {
    name: 'test_gen',
    feature: 'test_gen',
    description: '단위 테스트 생성 — Java 코드 → JUnit 5 테스트 + 에지 케이스 커버',
    inputSchema: {
      type: 'object',
      properties: {
        input: { type: 'string', description: 'Java 코드 (테스트 대상)' },
      },
      required: ['input'],
    },
  },
];

/**
 * Tool 호출 인자 → backend AnalyzeRequest 변환.
 * Tool 별로 input2 / sourceType 매핑이 다르므로 케이스 분기.
 */
export function buildAnalyzeRequest(toolName: string, args: Record<string, unknown>): {
  feature: string;
  input: string;
  input2?: string;
  sourceType?: string;
  dbProfileId?: number;
} {
  const tool = TOOLS.find((t) => t.name === toolName);
  if (!tool) throw new Error(`Unknown tool: ${toolName}`);

  const input = String(args.input ?? '');
  if (!input) throw new Error(`'input' 인자 필수`);

  const dbProfileId = typeof args.dbProfileId === 'number' ? args.dbProfileId : undefined;

  // Tool 별 매핑
  switch (toolName) {
    case 'sql_review':
      return {
        feature: tool.feature,
        input,
        sourceType: typeof args.reviewType === 'string' ? args.reviewType : 'review',
        dbProfileId,
      };
    case 'explain_plan':
      return {
        feature: tool.feature,
        input,
        input2: typeof args.explainPlan === 'string' ? args.explainPlan : undefined,
        dbProfileId,
      };
    case 'sql_translate':
      return {
        feature: tool.feature,
        input,
        input2: typeof args.sourceDb === 'string' ? args.sourceDb : undefined,
        sourceType: typeof args.targetDb === 'string' ? args.targetDb : undefined,
      };
    case 'regex_gen':
      return {
        feature: tool.feature,
        input,
        sourceType: typeof args.language === 'string' ? args.language : 'java',
      };
    default:
      return {
        feature: tool.feature,
        input,
        dbProfileId,
      };
  }
}
