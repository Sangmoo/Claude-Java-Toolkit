package io.github.claudetoolkit.ui.livedb;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * v4.7.x — #G3 Live DB Phase 2: Live DB 컨텍스트 자동 첨부 대상 feature 화이트리스트.
 *
 * <p>SQL 관련 feature 만 허용 — Java 코드 리뷰 / 문서 생성 등에 EXPLAIN PLAN
 * 컨텍스트가 들어가면 노이즈만 됨. 다음 feature 만 첨부:
 *
 * <ul>
 *   <li>{@code sql_review} — SQL 리뷰 (/advisor)</li>
 *   <li>{@code explain_plan} — 실행계획 분석 (/explain)</li>
 *   <li>{@code index_advisor} — 인덱스 추천 (/sql/index-advisor)</li>
 *   <li>{@code sql_translate} — DB 간 번역 (/sql-translate)</li>
 *   <li>{@code sql_batch} — 배치 SQL 분석 (/sql-batch)</li>
 *   <li>{@code erd_analysis} — ERD 분석 (/erd) — DBA 메타로 검증 보강</li>
 * </ul>
 *
 * <p>이 패키지에서 정의해서 controller 가 참조 — 추후 신규 SQL feature 추가 시
 * 여기만 갱신하면 됨.
 */
public final class SqlAnalysisFeatures {

    private SqlAnalysisFeatures() {}

    private static final Set<String> SQL_FEATURES;
    static {
        Set<String> s = new HashSet<String>();
        s.add("sql_review");
        s.add("explain_plan");
        s.add("index_advisor");
        s.add("sql_translate");
        s.add("sql_batch");
        s.add("erd_analysis");
        SQL_FEATURES = Collections.unmodifiableSet(s);
    }

    /**
     * 주어진 feature 가 Live DB 컨텍스트 자동 첨부 대상인지.
     * null/빈 문자열 → false (안전 측 default)
     */
    public static boolean shouldAttachLiveDbContext(String feature) {
        return feature != null && SQL_FEATURES.contains(feature);
    }

    /** 화이트리스트 직접 노출 (테스트 / 어드민 디버깅용). 수정 불가. */
    public static Set<String> all() {
        return SQL_FEATURES;
    }
}
