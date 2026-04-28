package io.github.claudetoolkit.ui.compliance;

/**
 * v4.6.x — 한국 컴플라이언스 리포트 타입 정의.
 *
 * <p>Stage 1 (현재) — {@link #FSS} 만 활성화. 나머지 3종은 enum 등록만 두고
 * 백엔드는 "준비 중" 응답으로 대응 (UI 에서는 비활성 옵션으로 표시).
 *
 * <p>각 타입의 실제 서식은 {@code prompts/compliance/{key}.md} 또는 코드 내
 * 템플릿 빌더에서 관리. 규정이 개정되면 해당 파일/빌더만 수정하면 된다.
 */
public enum ComplianceReportType {

    /** 전자금융감독규정 보안 점검 — Stage 1 */
    FSS("fss",
        "전자금융감독규정 보안 점검",
        "SQL Injection / 권한 / 로그 / 암호화 항목별 진단 + 분석 활동 통계",
        true),

    /** 개인정보보호법 데이터 처리 흐름 — Stage 2 활성 */
    PRIVACY("privacy",
        "개인정보보호법 데이터 처리 흐름",
        "개인정보 처리 활동 + 마스킹 + 처리방침 / 동의 / 처리위탁 점검 안내",
        true),

    /** 정보통신망법 보안 점검 — Stage 2 활성 */
    NETWORK_ACT("network-act",
        "정보통신망법 보안 점검",
        "접근통제 / 접속기록 보존 / 침해사고 대응 / Brute-force 탐지",
        true),

    /** 외부감사 대응 종합 리포트 — Stage 2 활성 */
    EXTERNAL_AUDIT("external-audit",
        "외부감사 대응 종합 리포트",
        "FSS · PIPA · 정보통신망법 횡단 비교 + 위험사항 + 증빙 체크리스트",
        true);

    private final String key;
    private final String label;
    private final String description;
    private final boolean enabled;

    ComplianceReportType(String key, String label, String description, boolean enabled) {
        this.key         = key;
        this.label       = label;
        this.description = description;
        this.enabled     = enabled;
    }

    public String  getKey()         { return key; }
    public String  getLabel()       { return label; }
    public String  getDescription() { return description; }
    public boolean isEnabled()      { return enabled; }

    /** key (예: "fss") 로 enum 조회. 없거나 비활성이면 null. */
    public static ComplianceReportType fromKey(String key) {
        if (key == null) return null;
        for (ComplianceReportType t : values()) {
            if (t.key.equalsIgnoreCase(key.trim())) return t;
        }
        return null;
    }
}
