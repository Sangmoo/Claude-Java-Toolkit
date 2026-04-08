package io.github.claudetoolkit.ui.prompt;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CustomPromptRepository extends JpaRepository<CustomPrompt, Long> {

    /** 분석 유형의 활성 프롬프트 1건 조회 */
    Optional<CustomPrompt> findByAnalysisTypeAndIsActiveTrue(String analysisType);

    /** 분석 유형의 모든 프롬프트 조회 (최신순) */
    List<CustomPrompt> findByAnalysisTypeOrderByUpdatedAtDesc(String analysisType);

    /** 분석 유형의 활성 프롬프트 전체 비활성화 */
    @Modifying
    @Query("UPDATE CustomPrompt c SET c.isActive = false WHERE c.analysisType = :type")
    void deactivateAllByType(@Param("type") String analysisType);
}
