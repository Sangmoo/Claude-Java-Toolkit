package io.github.claudetoolkit.docgen.masking;

import io.github.claudetoolkit.starter.client.ClaudeClient;

public class DataMaskingService {

    private static final String SYSTEM_MASKING =
        "당신은 개인정보 보호 및 데이터 마스킹 전문가입니다. 입력된 DDL(CREATE TABLE)을 분석하여 다음 형식으로 응답하세요:\n\n" +
        "## 개인정보 컬럼 식별 결과\n" +
        "| 테이블 | 컬럼명 | 데이터 타입 | 개인정보 분류 | 마스킹 방법 |\n\n" +
        "개인정보 분류 기준 (한국 개인정보보호법 기준):\n" +
        "- 고유식별정보: 주민등록번호, 여권번호, 운전면허번호, 외국인등록번호\n" +
        "- 민감정보: 건강정보, 신용정보, 범죄경력\n" +
        "- 일반 개인정보: 이름, 주소, 전화번호, 이메일, 생년월일, 계좌번호\n\n" +
        "## Oracle 마스킹 UPDATE 스크립트\n" +
        "```sql\n" +
        "-- 각 테이블별 마스킹 UPDATE 문\n" +
        "-- 마스킹 패턴: 주민번호 → 앞 6자리만 유지(*), 전화번호 → 중간 4자리 *, 이름 → 첫 글자만\n" +
        "```\n\n" +
        "## 마스킹 검증 SELECT\n" +
        "```sql\n" +
        "-- 마스킹 결과 확인용 SELECT\n" +
        "```\n\n" +
        "## 주의사항 및 권고\n" +
        "개인정보 처리 시 법적 고려사항 및 추가 조치 권고\n\n" +
        "컬럼명/주석으로 용도를 파악하고, 판단이 불명확한 컬럼은 '확인 필요'로 표시하세요.";

    private final ClaudeClient claudeClient;

    public DataMaskingService(ClaudeClient claudeClient) {
        this.claudeClient = claudeClient;
    }

    public String generateScript(String ddl) {
        return claudeClient.chat(SYSTEM_MASKING, ddl);
    }
}
