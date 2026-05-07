/**
 * v4.7.x — #M3 Phase 3: 도구 backend REST client.
 *
 * Claude Toolkit 의 /api/v1/analyze 를 호출하는 단순 wrapper.
 * 환경변수 CTK_URL + CTK_API_KEY 를 사용.
 */

export interface AnalyzeRequest {
  feature: string;
  input: string;
  input2?: string;
  sourceType?: string;
  dbProfileId?: number;
}

export interface AnalyzeResponse {
  success: boolean;
  feature: string;
  result: string;
  cached: boolean;
  tokens?: { input: number; output: number };
  costUsd?: number;
  elapsedMs?: number;
  model?: string;
  error?: string;
}

export class ToolkitClient {
  private readonly baseUrl: string;
  private readonly apiKey: string;
  private readonly defaultDbProfileId?: number;

  constructor() {
    const url = process.env.CTK_URL;
    const key = process.env.CTK_API_KEY;
    if (!url) {
      throw new Error(
        '환경변수 CTK_URL 미설정 — 도구 base URL 필요 (예: http://localhost:8027)'
      );
    }
    if (!key) {
      throw new Error(
        '환경변수 CTK_API_KEY 미설정 — /admin/api-keys 페이지에서 발급'
      );
    }
    this.baseUrl = url.replace(/\/+$/, '');
    this.apiKey = key;

    const dbId = process.env.CTK_DB_PROFILE_ID;
    if (dbId && /^\d+$/.test(dbId)) {
      this.defaultDbProfileId = parseInt(dbId, 10);
    }
  }

  /**
   * POST /api/v1/analyze 호출.
   * dbProfileId 가 명시되지 않으면 환경변수 CTK_DB_PROFILE_ID 사용.
   */
  async analyze(req: AnalyzeRequest): Promise<AnalyzeResponse> {
    const body: AnalyzeRequest = {
      ...req,
      dbProfileId: req.dbProfileId ?? this.defaultDbProfileId,
    };

    const res = await fetch(`${this.baseUrl}/api/v1/analyze`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'X-Api-Key': this.apiKey,
      },
      body: JSON.stringify(body),
    });

    const json = (await res.json().catch(() => null)) as AnalyzeResponse | null;
    if (!res.ok) {
      const errMsg = json?.error || `HTTP ${res.status}`;
      throw new Error(`Claude Toolkit API 호출 실패: ${errMsg}`);
    }
    if (!json) {
      throw new Error('Claude Toolkit API 응답 파싱 실패');
    }
    if (!json.success) {
      throw new Error(json.error || 'API 호출 실패 (success=false)');
    }
    return json;
  }

  /** GET /api/v1/features — discovery */
  async features(): Promise<Array<{ feature: string; label: string; description: string; liveDbCapable: boolean }>> {
    const res = await fetch(`${this.baseUrl}/api/v1/features`, {
      headers: { 'X-Api-Key': this.apiKey },
    });
    if (!res.ok) throw new Error(`features 조회 실패 (HTTP ${res.status})`);
    const json = (await res.json()) as {
      success: boolean;
      features: Array<{ feature: string; label: string; description: string; liveDbCapable: boolean }>;
    };
    return json.features ?? [];
  }
}
