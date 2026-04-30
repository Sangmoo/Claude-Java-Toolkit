package io.github.claudetoolkit.ui.livedb;

import javax.sql.DataSource;
import java.util.List;

/**
 * v4.7.x — #G3 Live DB Phase 4: 인덱스 시뮬레이션 인터페이스.
 *
 * <p>구현체는 {@link OracleIndexSimulator} (Phase 4) — INVISIBLE INDEX 트릭으로
 * 운영 영향 0 으로 시뮬레이션. 다른 DBMS 는 기능 미지원으로 disabled 반환.
 *
 * <p><b>SECURITY 모델:</b>
 * <ul>
 *   <li>각 시뮬레이션은 *최대 5개* 인덱스 (DOS 방지)</li>
 *   <li>인덱스 이름 prefix 강제 ({@code CTK_SIM_*}) — 시뮬레이션 인덱스 식별 + 실수 방지</li>
 *   <li>INVISIBLE 옵션 강제 — 운영 SQL 의 실행계획에 영향 0</li>
 *   <li>try/finally 로 항상 DROP — 중간 실패해도 cleanup 보장</li>
 *   <li>statement timeout 60초 강제 — 대용량 테이블 인덱스 생성 무한 대기 방지</li>
 * </ul>
 */
public interface IndexSimulator {

    /** "oracle" — DbProfile.url 매칭과 동일 */
    String getDbType();

    /**
     * INVISIBLE INDEX 시뮬레이션 — 인덱스 정의 목록을 받아 각각 *현재 vs 적용 후* 비용 비교.
     *
     * @param userSql       비교 대상 SQL (반드시 read-only — SELECT/EXPLAIN/WITH/DESC 만)
     * @param indexDefs     CREATE INDEX 구문 목록 (사용자가 제공한 추천 DDL).
     *                      각 구문은 자동으로 INVISIBLE 옵션 추가 + 이름 prefix 검증.
     * @param dataSource    분석 전용 DataSource (read-only 가 *아닌* 채널 — DDL 가능)
     * @return 시뮬레이션 결과 (graceful — 일부 실패해도 가능한 만큼 반환)
     * @throws SecurityException userSql 이 read-only 가 아니거나 인덱스 5개 초과
     */
    IndexSimulationResult simulate(String userSql, List<String> indexDefs, DataSource dataSource);
}
