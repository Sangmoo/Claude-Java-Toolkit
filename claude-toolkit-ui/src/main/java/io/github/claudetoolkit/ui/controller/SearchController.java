package io.github.claudetoolkit.ui.controller;

import io.github.claudetoolkit.ui.favorites.Favorite;
import io.github.claudetoolkit.ui.favorites.FavoriteRepository;
import io.github.claudetoolkit.ui.history.ReviewHistory;
import io.github.claudetoolkit.ui.history.ReviewHistoryRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Controller
@RequestMapping("/search")
public class SearchController {

    // Static feature list for feature-level search
    // {표시명, 경로, 설명, 검색 키워드}
    private static final List<String[]> FEATURES = Arrays.asList(
        // ── 분석 ──
        new String[]{"통합 워크스페이스",          "/workspace",        "9가지 분석 동시 실행, 모델 비교, 소스 선택기", "workspace 워크스페이스 통합 병렬 모델비교"},
        new String[]{"SQL 리뷰 & 최적화",        "/advisor",          "SQL 코드의 성능·품질·보안을 AI로 분석",        "SQL 리뷰 advisor oracle 성능"},
        new String[]{"SQL DB 번역",             "/sql-translate",    "Oracle↔MySQL↔PostgreSQL↔MSSQL SQL 문법 변환", "sql translate 번역 변환 oracle mysql postgresql mssql 마이그레이션"},
        new String[]{"배치 SQL 분석",            "/sql-batch",        "여러 SQL을 일괄 분석",                         "sql batch 배치 일괄"},
        new String[]{"ERD 분석",                "/erd",              "Oracle 스키마 → Mermaid ERD 다이어그램 생성",    "erd 다이어그램 schema 스키마 테이블 관계"},
        new String[]{"실행계획 분석",             "/explain",          "Oracle EXPLAIN PLAN 트리 시각화",              "explain plan oracle 실행계획 cost"},
        new String[]{"성능 비교",                "/explain/compare",  "Before/After SQL 성능 비교",                   "SQL 비교 before after cost"},
        new String[]{"성능 대시보드",             "/explain/dashboard","EXPLAIN PLAN Cost 추이 시각화",                "dashboard 대시보드 chart cost 히스토리"},
        new String[]{"코드 리뷰",                "/codereview",       "Java 코드 품질·보안 분석",                      "code review java 코드 리뷰"},
        new String[]{"복잡도 분석",               "/complexity",       "코드 복잡도 측정 및 리팩터링 제안",              "complexity 복잡도 refactor"},
        new String[]{"코드 리뷰 하네스",           "/harness",          "Analyst→Builder→Reviewer→Verifier 4단계 AI 파이프라인", "harness 하네스 pipeline diff 리팩터링 개선 verifier"},
        new String[]{"배치 분석",                "/harness/batch",    "하네스 배치 분석, 여러 파일 일괄 4단계 분석",     "harness batch 하네스 배치 일괄"},
        new String[]{"의존성 분석",               "/harness/dependency","프로젝트 의존성 구조 분석",                    "dependency 의존성 pom maven"},
        new String[]{"품질 대시보드",             "/harness/dashboard","코드 품질 추이 시각화",                        "quality dashboard 품질 대시보드"},
        // ── 생성 ──
        new String[]{"기술 문서 생성",            "/docgen",           "Java/Spring 코드로 기술 문서 자동 생성",        "docgen 문서 javadoc api document"},
        new String[]{"API 명세 생성",            "/apispec",          "Spring Controller → OpenAPI 3.0 YAML 생성",   "api spec openapi swagger yaml 명세"},
        new String[]{"테스트 코드 생성",           "/testgen",          "JUnit5 / Mockito 테스트 자동 생성",            "test junit mockito 테스트"},
        new String[]{"코드 변환",                "/converter",        "Oracle SP ↔ Java/Spring 양방향 변환",          "convert 변환 language 코드변환 mybatis"},
        new String[]{"Mock 데이터 생성",           "/mockdata",         "SQL INSERT 더미 데이터 생성",                  "mock 더미 insert data"},
        new String[]{"DB 마이그레이션",            "/migration",        "DDL 변경 → Flyway/Liquibase 스크립트 생성",    "migration flyway liquibase ddl"},
        new String[]{"Batch 일괄 처리",           "/batch",            "여러 파일 일괄 리뷰",                          "batch 일괄 zip"},
        new String[]{"의존성 분석 (pom.xml)",      "/depcheck",         "pom.xml 의존성 취약점·호환성 분석",             "depcheck dependency pom maven 의존성"},
        new String[]{"Spring 마이그레이션",        "/migrate",          "Spring Boot 버전 업그레이드 가이드",            "migrate spring boot 마이그레이션 업그레이드"},
        // ── 기록 ──
        new String[]{"즐겨찾기",                 "/favorites",        "분석 결과 즐겨찾기 저장·관리",                  "favorite 즐겨찾기 star"},
        new String[]{"리뷰 히스토리",             "/history",          "분석 이력 조회 및 공유 링크 생성",              "history 이력 share 공유"},
        new String[]{"사용량 모니터링",            "/usage",            "Claude API 토큰 사용량 통계",                  "usage token 사용량 cost 토큰"},
        new String[]{"스케줄 분석",               "/schedule",         "SQL 자동 분석 스케줄 등록·관리",                "schedule cron 스케줄 자동"},
        new String[]{"DB 프로필",                "/db-profiles",      "Oracle DB 연결 프로필 저장·전환",               "db profile oracle 연결 프로필"},
        new String[]{"AI 도입 ROI 리포트",        "/roi-report",       "AI 도입 비용 대비 절감 효과 시각화, 월별 ROI 차트", "ROI 리포트 report 비용 절감 효과 도입 투자 수익률"},
        // ── 도구 ──
        new String[]{"로그 분석",                "/loganalyzer",      "Spring Boot 로그 분석 및 오류 진단",            "log 로그 분석 error exception stacktrace"},
        new String[]{"정규식 생성",               "/regex",            "요구사항 → 정규식 자동 생성",                   "regex 정규식 정규표현식 pattern"},
        new String[]{"커밋 메시지 생성",           "/commitmsg",        "코드 변경 → Git 커밋 메시지 생성",              "commit 커밋 git message"},
        new String[]{"데이터 마스킹 생성",         "/maskgen",          "개인정보 마스킹 규칙 자동 생성",                "mask 마스킹 개인정보 anonymize"},
        new String[]{"민감정보 마스킹",            "/input-masking",    "입력 텍스트 민감정보 탐지·마스킹·복원",          "masking 마스킹 민감정보 주민번호 카드번호 PII"},
        // ── 설정 ──
        new String[]{"AI 프롬프트 관리",           "/prompts",          "분석 유형별 시스템 프롬프트 편집·저장·초기화",    "prompt 프롬프트 template 시스템 커스텀"},
        new String[]{"Settings",                "/settings",         "API 키, DB 연결, 이메일, 테마 등 설정",          "settings 설정 api key smtp email 테마"},
        new String[]{"보안 설정",                "/security",         "REST API 키 인증, 설정 비밀번호 잠금, 감사 로그", "security 보안 api key 인증 audit 감사"}
    );

    private final ReviewHistoryRepository historyRepo;
    private final FavoriteRepository      favoriteRepo;

    public SearchController(ReviewHistoryRepository historyRepo,
                            FavoriteRepository favoriteRepo) {
        this.historyRepo  = historyRepo;
        this.favoriteRepo = favoriteRepo;
    }

    @GetMapping
    public String search(
            @RequestParam(value = "q", defaultValue = "") String q,
            Model model) {

        model.addAttribute("q", q);

        if (q.trim().isEmpty()) {
            model.addAttribute("historyResults",  new ArrayList<ReviewHistory>());
            model.addAttribute("favoriteResults", new ArrayList<Favorite>());
            model.addAttribute("featureResults",  new ArrayList<String[]>());
            model.addAttribute("totalCount", 0);
            return "search/index";
        }

        String keyword = q.trim().toLowerCase();

        // History search
        List<ReviewHistory> historyResults =
                historyRepo.searchByKeyword(keyword, PageRequest.of(0, 20));

        // Favorites search
        List<Favorite> favoriteResults =
                favoriteRepo.searchByKeyword(keyword, PageRequest.of(0, 20));

        // Feature list search
        List<String[]> featureResults = new ArrayList<String[]>();
        for (String[] f : FEATURES) {
            String searchable = (f[0] + " " + f[2] + " " + f[3]).toLowerCase();
            if (searchable.contains(keyword)) {
                featureResults.add(f);
            }
        }

        int total = historyResults.size() + favoriteResults.size() + featureResults.size();

        model.addAttribute("historyResults",  historyResults);
        model.addAttribute("favoriteResults", favoriteResults);
        model.addAttribute("featureResults",  featureResults);
        model.addAttribute("totalCount", total);

        return "search/index";
    }
}
