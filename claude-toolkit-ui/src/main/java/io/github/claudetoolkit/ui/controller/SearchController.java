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
    private static final List<String[]> FEATURES = Arrays.asList(
        new String[]{"SQL 리뷰 & 최적화",      "/advisor",          "SQL 코드의 성능·품질·보안을 AI로 분석",     "SQL 리뷰 advisor oracle 성능"},
        new String[]{"실행계획 분석",            "/explain",          "Oracle EXPLAIN PLAN 트리 시각화",           "explain plan oracle 실행계획 cost"},
        new String[]{"성능 비교",               "/explain/compare",  "Before/After SQL 성능 비교",                "SQL 비교 before after cost"},
        new String[]{"성능 대시보드",            "/explain/dashboard","EXPLAIN PLAN Cost 추이 시각화",             "dashboard 대시보드 chart cost"},
        new String[]{"코드 리뷰",               "/codereview",       "Java 코드 품질·보안 분석",                   "code review java 코드 리뷰"},
        new String[]{"복잡도 분석",              "/complexity",       "코드 복잡도 측정 및 리팩터링 제안",           "complexity 복잡도 refactor"},
        new String[]{"기술 문서 생성",           "/docgen",           "Java/Spring 코드로 API 문서 자동 생성",      "docgen 문서 javadoc api"},
        new String[]{"테스트 코드 생성",          "/testgen",          "JUnit5 / Mockito 테스트 자동 생성",          "test junit mockito 테스트"},
        new String[]{"코드 변환",               "/converter",        "다른 언어로 코드 변환",                      "convert 변환 language"},
        new String[]{"Mock 데이터 생성",          "/mockdata",         "SQL INSERT 더미 데이터 생성",                "mock 더미 insert data"},
        new String[]{"DB 마이그레이션",           "/migration",        "DDL 변경 → Flyway/Liquibase 스크립트 생성", "migration flyway liquibase ddl"},
        new String[]{"Batch 일괄 처리",          "/batch",            "여러 파일 일괄 리뷰",                        "batch 일괄 zip"},
        new String[]{"프롬프트 템플릿",           "/prompts",          "기능별 프롬프트 커스터마이징",                "prompt template 프롬프트"},
        new String[]{"즐겨찾기",                "/favorites",        "분석 결과 즐겨찾기 저장·관리",                "favorite 즐겨찾기 star"},
        new String[]{"리뷰 히스토리",            "/history",          "분석 이력 조회 및 공유 링크 생성",            "history 이력 share 공유"},
        new String[]{"사용량 모니터링",           "/usage",            "Claude API 토큰 사용량 통계",                "usage token 사용량 cost"},
        new String[]{"스케줄 분석",              "/schedule",         "SQL 자동 분석 스케줄 등록·관리",              "schedule cron 스케줄"},
        new String[]{"DB 프로필",               "/db-profiles",      "Oracle DB 연결 프로필 저장·전환",             "db profile oracle 연결"},
        new String[]{"Settings",               "/settings",         "API 키, DB 연결, 테마 등 설정",               "settings 설정 api key smtp"}
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
