package io.github.claudetoolkit.ui.compliance;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ComplianceReportRepository extends JpaRepository<ComplianceReportRecord, Long> {

    /** 최근 N건 — 페이지 진입시 목록 표시용 */
    @Query("SELECT r FROM ComplianceReportRecord r ORDER BY r.createdAt DESC")
    List<ComplianceReportRecord> findRecent(Pageable pageable);

    /** 타입별 필터 — 같은 컴플라이언스 종류 추이 비교용 */
    @Query("SELECT r FROM ComplianceReportRecord r WHERE r.type = :type ORDER BY r.createdAt DESC")
    List<ComplianceReportRecord> findByType(@Param("type") String type, Pageable pageable);

    /** 보존 정책 — N개월 이전 자동 삭제 (스케줄러용, 선택적) */
    long deleteByCreatedAtBefore(LocalDateTime cutoff);

    /** 가장 오래된 레코드 — 상한 초과 시 prune 용 */
    ComplianceReportRecord findTopByOrderByCreatedAtAsc();
}
