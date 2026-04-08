# Claude Java Toolkit

> AI-powered tools for Oracle DB & Java/Spring enterprise development
> Powered by [Anthropic Claude API](https://docs.anthropic.com)

[![Build](https://github.com/Sangmoo/Claude-Java-Toolkit/actions/workflows/build.yml/badge.svg)](https://github.com/Sangmoo/Claude-Java-Toolkit/actions/workflows/build.yml)
[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-1.8%2B-orange.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-2.7.x-green.svg)](https://spring.io/projects/spring-boot)
[![Version](https://img.shields.io/badge/version-1.9.0-brightgreen.svg)](#)

---

## 🌐 프로젝트 소개 페이지

**👉 [https://sangmoo.github.io/Claude-Java-Toolkit/#ko](https://sangmoo.github.io/Claude-Java-Toolkit/#ko)**

프로젝트의 전체 기능, 설치 방법, REST API 목록을 한눈에 확인할 수 있는 GitHub Pages 소개 페이지입니다.
한국어(`#ko`) / 영어(`#en`) 전환을 지원합니다.

---

## ✨ 소개

`claude-java-toolkit`은 Java/Spring 엔터프라이즈 환경에서 Claude API를 실무에 바로 활용할 수 있는 오픈소스 툴킷입니다.

Python용 Claude 통합 도구는 많지만, **JDK 1.8+ / Oracle 11g+ / Spring Boot 레거시 환경**에 특화된 Java 통합 라이브러리는 거의 없습니다.

국내 SI / 금융 / 유통 환경의 현실을 반영하여 설계되었습니다.

---

## 📦 모듈 구성

| 모듈 | 설명 | 형태 |
|------|------|------|
| [`claude-spring-boot-starter`](./claude-spring-boot-starter) | `application.yml` 설정만으로 `ClaudeClient` Bean 자동 주입. SSE 스트리밍 + 런타임 모델 전환 지원 | Library |
| [`claude-sql-advisor`](./claude-sql-advisor) | SQL / Oracle SP 리뷰, 보안 감사, 인덱스 최적화 제안, ERD 분석, Mock 데이터 생성, DB 마이그레이션 | Library + CLI |
| [`claude-doc-generator`](./claude-doc-generator) | 기술 문서(Oracle Package 포함), Javadoc 생성, 리팩터링 제안, 테스트 코드, API 명세, 코드 변환(iBatis 지원), 코드 리뷰, 복잡도 분석, pom.xml 의존성 분석, 데이터 마스킹, Spring 마이그레이션, 로그 분석, 정규식 생성, 커밋 메시지 생성 | Library + CLI |
| [`claude-toolkit-ui`](./claude-toolkit-ui) | 위 기능들을 웹 브라우저에서 사용하는 통합 대시보드 | Spring Boot Web |

---

## 🚀 Quick Start

### 1. 사전 요구사항

- JDK 1.8+
- Maven 3.6+
- [Anthropic API Key](https://console.anthropic.com)

### 2. Clone & Build

```bash
git clone https://github.com/YOUR_USERNAME/claude-java-toolkit.git
cd claude-java-toolkit
mvn clean install -DskipTests
```

> **IntelliJ 사용자**: Maven 패널에서 루트 프로젝트 → `Lifecycle` → `install` 더블클릭

### 3. Web UI 실행

```bash
# Mac / Linux
export CLAUDE_API_KEY=sk-ant-...

# Windows PowerShell
$env:CLAUDE_API_KEY="sk-ant-..."

cd claude-toolkit-ui
mvn spring-boot:run
```

브라우저에서 `http://localhost:8027` 접속

---

## 🌐 Web UI 기능 목록

웹 대시보드는 **사이드바 네비게이션** 구조로 총 **24가지 AI 도구** + 설정 + 이력/즐겨찾기 관리를 제공합니다.

---

### 🔍 분석 도구

#### 1. SQL / SP 코드 리뷰 — `/advisor`

Oracle SQL 쿼리 또는 Stored Procedure를 분석합니다.

| 기능 | 설명 |
|------|------|
| 자동 SQL 타입 감지 | SQL / PROCEDURE / FUNCTION / TRIGGER / PACKAGE 자동 구분 |
| DB 메타정보 포함 | Oracle 연결 시 FROM/JOIN 테이블의 컬럼·PK·인덱스 정보를 Claude에 전달 |
| EXPLAIN PLAN 연동 | SELECT 문 실행계획을 자동 조회하여 리뷰에 포함 |
| 보안 취약점 탭 | SQL Injection, 하드코딩 자격증명 등 보안 관점 분석 탭 별도 제공 |
| **인덱스 최적화 탭** | SQL 쿼리 분석 후 CREATE INDEX 구문 포함 인덱스 제안 |
| **SQL 리팩터링 Diff 뷰** ⭐ NEW | AI 제안 최적화 SQL과 원본을 LCS 기반 라인 diff로 시각화 — 삭제(🔴)·추가(🟢) 색상 구분 (v1.5) |
| 심각도 필터 | 결과를 **HIGH / MEDIUM / LOW** 탭으로 필터링 |
| 파일 업로드 | `.sql`, `.pls`, `.pck` 파일 직접 업로드 |
| 복사 / 새 탭 / 다운로드 | 결과를 클립보드 복사, 새 탭으로 열기, `.md` 파일 저장 |

#### 2. ERD 분석 / DDL 생성 — `/erd`

Oracle DB 스키마를 분석하여 Mermaid ERD 다이어그램을 생성하고, ERD → Oracle DDL 역변환도 지원합니다.

| 기능 | 설명 |
|------|------|
| DB 자동 스캔 | Oracle 연결 시 ALL_TABLES, ALL_CONSTRAINTS를 읽어 전체 스키마 분석 |
| 텍스트 입력 | DB 연결 없이 테이블 구조를 직접 입력하여 ERD 생성 |
| 테이블 필터 | 쉼표로 구분된 테이블명을 입력하여 원하는 테이블만 ERD 생성 |
| Mermaid 렌더링 | 브라우저에서 바로 다이어그램으로 렌더링 |
| **DDL 생성 탭** ⭐ NEW | Mermaid ERD, 테이블 구조 설명, 자연어 → Oracle CREATE TABLE DDL 자동 생성 |
| **Oracle DDL 규격** ⭐ NEW | VARCHAR2, NUMBER, DATE 타입 + PK/FK 제약조건 + FK 인덱스 + COMMENT ON COLUMN 포함 |

#### 3. 실행계획 분석 — `/explain`

Oracle EXPLAIN PLAN을 실행하고 결과를 인터랙티브 트리로 시각화합니다.

| 기능 | 설명 |
|------|------|
| **트리 시각화** | PLAN_TABLE을 파싱하여 부모-자식 트리로 렌더링 (클릭으로 접기/펼치기) |
| **오퍼레이션 색상** | TABLE ACCESS FULL(🔴), INDEX 스캔(🟢), JOIN(🟣), SORT(🟡), NESTED LOOPS(🟠) 색상 구분 |
| **비용 바** | 각 노드의 Cost를 전체 최대 Cost 대비 비율로 시각화 |
| **요약 배지** | Total Cost, Estimated Rows 요약 표시 |
| **AI 성능 분석** | Claude가 성능 이슈·최적화 제안·핵심 단계 해설을 Markdown으로 제공 |
| **⚡ 스트리밍 분석** ⭐ NEW | EXPLAIN PLAN 트리를 즉시 렌더링한 뒤, Claude AI 분석을 SSE 스트리밍으로 실시간 출력 (v1.5) |
| **원문 보기** | DBMS_XPLAN.DISPLAY() 전체 텍스트 접이식 표시 |
| 이력 저장 | 분석 결과가 리뷰 이력에 자동 저장 |

#### 3-1. 실행계획 Before/After 비교 — `/explain/compare` (v0.9.0)

최적화 전/후 SQL을 나란히 분석하여 실행계획 변화를 검증합니다.

| 기능 | 설명 |
|------|------|
| **Before/After 동시 실행** | 두 SQL을 각각 EXPLAIN PLAN 실행 후 나란히 트리로 렌더링 |
| **Cost 비교 배지** | Total Cost 변화량 (%) 표시 — 개선(🟢) / 악화(🔴) / 동일(⚪) 시각 구분 |
| **원문 나란히 보기** | Before/After DBMS_XPLAN 원문 각각 접이식 표시 |

#### 3-3. 코드 리뷰 하네스 — `/harness` ⭐ UPDATED (v1.8.0)

Java 코드 또는 SQL을 입력하거나 **파일 브라우저·DB 오브젝트**에서 직접 선택하면 **Analyst → Builder → Reviewer → Verifier** 4단계 AI 파이프라인이 자동으로 실행됩니다.

| 기능 | 설명 |
|------|------|
| **4단계 파이프라인** ⭐ NEW | 분석가(Analyst) → 개선가(Builder) → 검토자(Reviewer) → **검증자(Verifier)** 순차 실행. 대형 SP도 이어쓰기(continuation)로 완전 출력 |
| **Verifier 검증** ⭐ NEW | Java: 컴파일 가능성·Spring/JPA 호환성·위험 변경 감지. SQL: SQL 문법 오류·Oracle 의존성 깨짐·위험 변경 감지(DROP/TRUNCATE/DELETE without WHERE). VERIFIED/WARNINGS/FAILED 3단계 판정 |
| **분석 템플릿** | 일반·성능 최적화·보안 취약점·리팩터링·Oracle SQL 성능·가독성 프리셋 선택 |
| **언어 선택** | Java / SQL 탭 전환 |
| **Diff 뷰** | 원본 vs 개선 코드를 LCS 기반 라인 diff로 시각화 — 삭제(🔴)·추가(🟢)·동일 색상 구분 |
| **품질 점수 탭** | 가독성·성능·유지보수성·보안 각 X/10 점수 + 종합 판정 |
| **분석 요약** | 분석가가 발견한 문제점·안티패턴·보안 이슈 항목 목록 |
| **변경 내역** | 검토자가 정리한 각 변경 사항과 이유 목록 |
| **검토 결과** | 기대 효과 + 최종 판정 (APPROVED / NEEDS_REVISION) |
| **검증 결과 탭** ⭐ NEW | Verifier 4개 섹션 + 판정 배지(🟢 VERIFIED / 🟡 WARNINGS / 🔴 FAILED) |
| **HTML 내보내기** | 원본·개선 코드·분석 결과를 독립 HTML 파일로 저장 |
| **스트리밍 분석** | SSE 스트리밍으로 파이프라인 진행 상황 실시간 출력 |
| **복사 / MD 저장** | 원본 복사, 개선 코드 복사, 전체 결과 `.md` 저장 |
| **이력 저장 + 재분석** | 분석 결과 자동 저장 (`HARNESS_REVIEW`). 히스토리에서 원본 코드 재로드 가능 |

#### 3-4. 배치 분석 — `/harness/batch` ⭐ UPDATED (v1.7.0)

여러 Java 파일 또는 SQL 오브젝트를 한 번에 하네스 파이프라인으로 순차 분석합니다.

| 기능 | 설명 |
|------|------|
| **소스 선택** | 항목별 Java 파일 브라우저 / DB 오브젝트 검색으로 코드 직접 로드 |
| **다중 항목 관리** | 라벨·코드·언어 설정으로 분석 항목을 동적으로 추가/제거 |
| **백그라운드 실행** | 모든 항목을 순차적으로 비동기 처리 |
| **진행률 표시** | 실시간 진행률 바 (완료 n / 전체 n) |
| **결과 테이블** | 성공/실패 여부, 분석 요약 미리보기 |
| **상세 모달 → 분석 결과 열기** | 배치 이력 상세에서 각 항목 row 클릭 → 분석 결과 / 원본 코드 전체 확인 |
| **다중 이메일 알림** | 배치 완료 시 여러 수신자에게 항목별 요약 이메일 자동 발송 (SMTP 필요) |
| **배치 이력 영구 저장** | `batch_history` H2 테이블에 배치 결과 저장. 서버 재시작 후에도 이력 유지 |
| **이력 하이라이트** | 이메일 링크(`?highlight=uuid`) 클릭 시 해당 배치 행 자동 스크롤·강조 |
| **이력 자동 저장** | 각 항목이 `HARNESS_REVIEW` 이력에도 자동 저장 (`ReviewHistory`) |

#### 3-5. DB 오브젝트 의존성 분석 — `/harness/dependency` ⭐ NEW (v1.7.0)

Oracle SP/Package/Function 또는 Java 클래스의 호출 관계·테이블 의존성을 분석합니다.

| 기능 | 설명 |
|------|------|
| **소스 선택** ⭐ NEW | Java 파일 브라우저 / DB 오브젝트 검색으로 소스 직접 로드 |
| **SQL 의존성** | 참조 테이블/뷰(READ/WRITE 구분), 호출 프로시저, 파라미터, 순환 의존성 위험 |
| **Java 의존성** | 외부 라이브러리, Spring Bean 주입 관계, Repository 접근 계층, 강결합 위험 |
| **5개 섹션 보고서** | 오브젝트 정보·의존성·설계 위험 구조화 출력 |

#### 3-6. 품질 대시보드 — `/harness/dashboard` ⭐ NEW (v1.7.0)

누적 하네스 분석 이력을 기반으로 코드 품질 통계를 시각화합니다.

| 기능 | 설명 |
|------|------|
| **통계 카드** | 총 분석 수, APPROVED %, NEEDS_REVISION %, Java vs SQL 비율 |
| **판정 비율 차트** | Chart.js 도넛 차트 (APPROVED / NEEDS_REVISION / 기타) |
| **언어 분포 차트** | Chart.js 막대 차트 (Java vs SQL 건수) |
| **품질 점수 추이** | 최근 20건 품질 점수 라인 차트 |
| **최근 분석 타임라인** | 분석 일시·제목·언어·점수 테이블 |

#### 3-2(기존). SQL 성능 히스토리 대시보드 — `/explain/dashboard` ⭐ NEW (v1.2.0)

실행계획 분석 이력을 기반으로 SQL별 Cost 추이를 차트로 시각화합니다.

| 기능 | 설명 |
|------|------|
| **Cost 추이 차트** | 분석 횟수별 Root Cost 변화를 Chart.js 라인 차트로 표시 |
| **기간 필터** | 전체 / 최근 30일 / 최근 7일 기간별 필터링 |
| **색상 구분 포인트** | 이전 분석 대비 Cost 증가(🔴) / 감소(🟢) 포인트 색상 구분 |
| **통계 요약** | 총 분석 횟수, 최대/최소/평균 Cost 통계 카드 |
| **이력 테이블** | SQL 미리보기, 분석 일시, Cost 바, 변화율(%) 상세 목록 |

#### 3-3. 배치 SQL 분석 — `/sql-batch` ⭐ NEW (v1.2.0)

여러 SQL을 한 번에 입력하거나 파일로 업로드하여 일괄 리뷰합니다.

| 기능 | 설명 |
|------|------|
| **텍스트 일괄 입력** | `---` 구분자로 구분된 다중 SQL 블록을 한 번에 분석 |
| **CSV 파일 업로드** | CSV 1열에 SQL 목록을 넣어 업로드, 헤더 자동 스킵 |
| **텍스트 파일 업로드** | `.sql`, `.txt` 파일 드래그 앤 드롭 업로드 |
| **최대 30개 지원** | 한 번의 요청으로 최대 30개 SQL 일괄 분석 |
| **아코디언 결과** | 각 SQL별 분석 결과를 접이식(accordion) UI로 표시 |
| **MD 보고서 다운로드** | 전체 분석 결과를 단일 Markdown 파일로 다운로드 |
| **자동 이력 저장** | 배치 분석 결과가 리뷰 이력에 자동 저장 (`SQL_BATCH` 타입) |

#### 4. Java 코드 리뷰 — `/codereview`

Java/Spring 소스 코드를 정적 분석합니다.

| 기능 | 설명 |
|------|------|
| 일반 코드 리뷰 | 버그, 코드 품질, 설계 문제, SOLID 원칙 위반 등 종합 리뷰 |
| 보안 특화 리뷰 | OWASP Top 10, SQL Injection, XXE, 인증·세션 취약점 집중 분석 |
| 소스 유형 선택 | Controller / Service / Repository / 일반 Java 선택 |
| 프로젝트 컨텍스트 | 프로젝트 경로 설정 시 연관 파일을 컨텍스트로 제공 |
| 파일 업로드 | `.java`, `.kt`, `.groovy` 파일 직접 업로드 |

#### 4. 코드 복잡도 분석 — `/complexity`

순환 복잡도(Cyclomatic Complexity) 분석 및 리팩터링 우선순위를 제공합니다.

| 기능 | 설명 |
|------|------|
| 단일 파일 모드 | 특정 Java 파일의 메서드별 복잡도 측정 |
| 프로젝트 전체 모드 | 스캔 경로 내 전체 파일의 복잡도 통계 |
| 우선순위 필터 | HIGH / MEDIUM / LOW 리팩터링 우선순위 필터 |

---

### ⚡ 생성 도구

#### 5. 기술 문서 자동 생성 — `/docgen`

Oracle SP 또는 Java 소스 코드에서 기술 문서를 자동 생성합니다.

| 기능 | 설명 |
|------|------|
| 소스 유형 선택 | Oracle SP / Function / **Oracle Package (SPEC+BODY)** / Java Service / Controller / MyBatis XML / Spring Batch ⭐ NEW |
| 출력 형식 | **Markdown** (`.md`) / **Typst** (`.typ`) / **HTML** (`.html`) |
| 출력 언어 | **한국어** / **English** 선택 |
| 프로젝트 컨텍스트 | Spring 프로젝트 경로를 스캔하여 연관 파일을 컨텍스트로 제공 |
| 렌더링 보기 / 새 탭 | marked.js 렌더링 보기 토글, 새 탭으로 열기 |

#### 6. Javadoc 자동 생성 — `/javadoc` ⭐ NEW

Java 소스 코드에 Javadoc 주석을 자동으로 추가합니다.

| 기능 | 설명 |
|------|------|
| 주석 자동 생성 | public 클래스·메서드·필드에 한국어 Javadoc 주석 삽입 |
| 프로젝트 컨텍스트 | 설정된 프로젝트 컨텍스트를 반영하여 도메인 맞춤 주석 생성 |
| 파일 업로드 | `.java` 파일 직접 업로드 |
| 다운로드 | 결과를 `.java` 파일로 저장 |
| 실시간 보기 | SSE 스트리밍으로 생성 과정 실시간 확인 |

#### 7. 리팩터링 제안 — `/refactor` ⭐ NEW

Java 소스 코드의 문제점을 분석하고 개선 코드를 제안합니다.

| 기능 | 설명 |
|------|------|
| 코드 문제점 분석 | 중복 코드, 긴 메서드, 의존성 역전 등 리팩터링 포인트 도출 |
| 개선 코드 제공 | 리팩터링 전/후 코드를 나란히 제시 |
| 프로젝트 컨텍스트 | 프로젝트 스타일·컨벤션 반영 |
| 다운로드 | 결과를 `.java` 파일로 저장 |
| 실시간 보기 | SSE 스트리밍 지원 |

#### 8. pom.xml 의존성 분석 — `/depcheck` ⭐ NEW

Maven pom.xml을 분석하여 취약점·충돌·업그레이드 권고를 제공합니다.

| 기능 | 설명 |
|------|------|
| 취약 의존성 탐지 | 알려진 보안 취약점이 있는 라이브러리 버전 경고 |
| 버전 충돌 분석 | 의존성 간 버전 충돌 및 BOM 누락 검사 |
| 업그레이드 권고 | 최신 안정 버전으로의 업그레이드 가이드 |
| 심각도 필터 | HIGH / MEDIUM / LOW 우선순위 필터 |
| 다운로드 | 분석 결과를 `.md` 파일로 저장 |

#### 9. 테스트 코드 자동 생성 — `/testgen`

Java 소스 코드에서 JUnit 5 테스트 클래스를 자동 생성합니다.

| 소스 유형 | 생성 전략 |
|----------|----------|
| Controller | `@WebMvcTest` + `MockMvc` + `@MockBean` |
| Service | `@ExtendWith(MockitoExtension)` + `@Mock` + `@InjectMocks` |
| Mapper / Repository | `@MybatisTest` + H2 인메모리 DB |
| 일반 Java | JUnit 5 단위 테스트 + Mockito |

#### 10. API 명세 자동 생성 — `/apispec`

Spring Controller 코드에서 API 명세서를 자동 생성합니다.

| 출력 형식 | 설명 |
|---------|------|
| OpenAPI 3.0 YAML | `openapi.yaml` 파일 생성. paths, components, schemas 포함 |
| Swagger 2.0 어노테이션 | 기존 Controller 코드에 `@ApiOperation`, `@ApiParam` 등을 추가한 Java 코드 반환 |

#### 11. 코드 변환 (양방향) — `/converter`

Oracle PL/SQL ↔ Java/Spring + MyBatis 코드를 양방향으로 변환합니다.

| 변환 모드 | 설명 |
|---------|------|
| Oracle SP → Java/Spring + MyBatis | VO, Mapper.java, Mapper.xml, Service.java, ServiceImpl.java 생성 |
| Oracle SQL → MyBatis XML | SELECT/INSERT/UPDATE/DELETE를 MyBatis XML 매퍼로 변환 |
| Java/Spring + MyBatis → Oracle SP | Service/Repository + MyBatis XML을 Oracle Stored Procedure로 역변환 |
| **iBatis XML → MyBatis XML** | iBatis 레거시 매퍼를 MyBatis 3.x 형식으로 자동 변환 ⭐ NEW |

#### 12. Mock 데이터 생성 — `/mockdata`

DDL로부터 현실적인 테스트 데이터를 자동 생성합니다.

| 기능 | 설명 |
|------|------|
| 출력 형식 | Oracle `INSERT` / `MERGE` / `CSV` 선택 |
| 행 수 설정 | 1 ~ 1,000건 범위에서 생성 건수 지정 |
| 현실적 데이터 | 컬럼명·타입을 분석하여 업무 의미에 맞는 값 생성 (이름, 날짜, 코드 등) |
| 다운로드 | `.sql` 또는 `.csv` 파일 저장 |

#### 13. DB 마이그레이션 스크립트 — `/migration`

BEFORE/AFTER DDL 비교를 통해 마이그레이션 스크립트를 자동 생성합니다.

| 출력 형식 | 설명 |
|---------|------|
| Oracle DDL | `ALTER TABLE`, `CREATE INDEX`, rollback 스크립트 포함 |
| Flyway | 버전 관리 SQL 마이그레이션 파일 (`V{n}__description.sql`) |
| Liquibase | XML changeset 형식 (`changelog.xml`) |
| 위험도 필터 | HIGH / MEDIUM / LOW 변경 위험도 필터링 |

#### 14. Batch 일괄 처리 — `/batch`

여러 파일을 한 번에 처리합니다.

| 모드 | 설명 |
|------|------|
| SQL Batch 리뷰 | 여러 `.sql` 파일을 일괄 업로드하여 리뷰 결과를 ZIP으로 다운로드 |
| TestGen Batch | 여러 `.java` 파일을 일괄 업로드하여 테스트 코드를 ZIP으로 다운로드 |

#### 15. 데이터 마스킹 스크립트 생성 — `/maskgen` ⭐ NEW

CREATE TABLE DDL을 입력하면 개인정보 보호 마스킹 UPDATE 스크립트를 자동 생성합니다.

| 기능 | 설명 |
|------|------|
| 컬럼 자동 탐지 | 이름, 이메일, 전화번호, 주민번호 등 민감 컬럼 자동 식별 |
| Oracle UPDATE 생성 | `UPDATE ... SET email = REGEXP_REPLACE(...)` 형식의 마스킹 SQL 생성 |
| 다운로드 | 결과를 `.sql` 파일로 저장 |

#### 16. Spring Boot 3.x 마이그레이션 — `/migrate` ⭐ NEW

pom.xml 또는 Java 소스를 분석하여 Spring Boot 2.x → 3.x 마이그레이션 체크리스트를 생성합니다.

| 기능 | 설명 |
|------|------|
| 의존성 변경 감지 | `javax.*` → `jakarta.*` 패키지 마이그레이션 항목 추출 |
| 체크리스트 생성 | 버전 업그레이드, 설정 변경, 코드 수정 항목을 우선순위별 정리 |
| 다운로드 | 체크리스트를 `.md` 파일로 저장 |

---

### 🛠 도구

#### 17. 로그 분석기 — `/loganalyzer`

Spring Boot / Java 애플리케이션 로그를 AI로 분석합니다.

| 기능 | 설명 |
|------|------|
| 일반 분석 | 오류 원인 파악, 해결 방법, 예방 방법을 `## 오류 분석 / ## 원인 파악 / ## 해결 방법 / ## 예방 방법` 형식으로 제공 |
| 보안 위협 탐지 | SQL 인젝션, XSS, 인증 실패, 무차별 대입 시도 등 보안 이벤트 탐지 |
| 파일 업로드 | `.log`, `.txt` 파일 직접 업로드 후 분석 |
| 다운로드 | 분석 결과를 `.md` 파일로 저장 |
| 자동 임시저장 | 입력 내용 localStorage 자동 저장 |

#### 18. 정규식 생성기 — `/regex`

자연어 설명을 입력하면 정규식과 사용 예제를 생성합니다.

| 기능 | 설명 |
|------|------|
| 다국어 지원 | Java / JavaScript / Python / Oracle SQL / Kotlin 언어별 예제 코드 생성 |
| 빠른 예제 | 휴대폰번호, 이메일, 날짜, 주민등록번호, URL, HTML태그, 비밀번호, IPv4 원클릭 입력 |
| 결과 구성 | 정규식 패턴 + 설명 + 언어별 코드 예제 + 테스트 케이스 |
| 다운로드 | `.md` 파일로 저장 |

#### 19. 커밋 메시지 생성기 — `/commitmsg`

Git diff 또는 변경 내용 설명으로 커밋 메시지를 생성합니다.

| 기능 | 설명 |
|------|------|
| 입력 모드 | **Git Diff 입력** (diff/patch 파일 업로드 지원) / **변경 내용 설명** 직접 입력 |
| 커밋 스타일 | **Conventional Commits** / **Gitmoji** / **Simple** / **Angular** 스타일 선택 |
| 결과 구성 | 추천 커밋 메시지 + 대안 메시지 3개 + 변경 사항 요약 |
| 파일 업로드 | `.diff`, `.patch` 파일 직접 업로드 |

---

### 📚 기록 관리

#### 20. 리뷰 이력 — `/history`

모든 분석·생성 결과가 자동으로 저장됩니다.

| 기능 | 설명 |
|------|------|
| 자동 저장 | 22가지 도구의 모든 결과 자동 기록, **H2 파일 DB 영속화** (서버 재시작 후 복원, 최대 100건) |
| 검색 / 필터 | 키워드 검색 + 유형별 탭 필터 (SQL/문서/코드/테스트/ERD/DDL생성/실행계획/로그/정규식/커밋/Javadoc/리팩터링/의존성/마스킹/Spring이전) |
| **재실행 버튼** ⭐ NEW | 이력 상세 모달에서 "재실행" 클릭 시 해당 기능 페이지로 입력 내용 자동 전달 |
| **Diff 비교** | 두 항목을 선택하여 입력 코드 / 분석 결과를 나란히 비교 |
| **보고서 묶음 내보내기** | 다중 선택 후 Markdown 번들 파일 (`.md`) 일괄 다운로드 |
| CSV 내보내기 | 이력 전체를 Excel 호환 UTF-8 BOM CSV로 다운로드 |
| 인쇄 / PDF 저장 | 브라우저 인쇄 기능으로 PDF 저장 가능 (`@media print` 최적화) |
| 상세 보기 | 클릭 시 모달로 입력 코드 / 분석 결과 탭 전환 |
| 즐겨찾기 추가 | 항목별 ★ 버튼으로 즐겨찾기에 저장 |

#### 21. 즐겨찾기 — `/favorites`

중요한 분석 결과를 태그로 정리하여 보관합니다.

| 기능 | 설명 |
|------|------|
| 태그 관리 | 즐겨찾기 저장 시 태그 입력, 태그별 필터 |
| **H2 파일 DB 영속화** | 서버 재시작 후에도 즐겨찾기 유지 (`FavoriteRepository` JPA) ⭐ NEW |
| 상세 보기 / 복사 | 모달에서 분석 결과 확인 및 클립보드 복사 |

---

## 🔑 공통 UX 기능

| 기능 | 설명 |
|------|------|
| **다크 / 라이트 테마** | 상단 바 토글 버튼, localStorage 저장, 페이지 이동 시 FOUC 방지 |
| **입력 자동 임시저장** | 입력 2초 후 localStorage 자동 저장, 새로고침/복귀 시 복원 |
| **토큰 수 예측 표시** | 입력 글자 수 기반 예상 토큰 수 실시간 표시 (40k↑ 노랑, 80k↑ 빨강) |
| **중복 제출 방지** | 분석 실행 시 버튼 자동 비활성화 → 90초 후 자동 복원 |
| **⚡ 실시간 보기 버튼** | 모든 도구 페이지에 "실시간 보기" 버튼 — Claude 응답을 SSE 스트리밍으로 즉시 확인 |
| **프로젝트 컨텍스트 자동 주입** | Settings 컨텍스트 메모가 코드 리뷰·테스트 생성·Javadoc·리팩터링 등 **모든 AI 도구**에 자동 전달 |
| **Ctrl+Enter 단축키** | 모든 페이지에서 Ctrl+Enter(Mac: ⌘+Enter)로 즉시 분석 실행 ⭐ NEW |
| **반응형 레이아웃** | 모바일(≤768px) / 태블릿(≤992px) 화면에서 최적화된 UI ⭐ NEW |
| **사이드바 섹션 접기** | 분석/생성/기록/도구 섹션 개별 토글, 전체 사이드바 접기 — localStorage 상태 저장 |
| Syntax Highlighting | Prism.js 1.29.0 — SQL, Java, XML, YAML, diff 코드 블록 자동 하이라이팅 |
| 새 탭으로 결과 열기 | 분석 결과를 독립 탭에서 full-width로 열기 (Markdown 렌더링 포함) |
| 단계별 로딩 메시지 | "SQL 파싱 중… → DB 조회 중… → Claude 분석 중…" 순차 표시 |
| 클립보드 복사 | 분석 결과 원클릭 복사 |
| 마크다운 렌더링 | `## 헤딩`, `**굵게**`, `` `코드` ``, fenced code block, severity 배지 자동 렌더링 |
| Mermaid.js ERD | ERD 분석 결과의 `mermaid` 코드블록을 다이어그램으로 자동 렌더링 |

---

## ⚙️ Settings — `/settings`

Oracle DB 연결 정보, Java 프로젝트 경로, Claude API, 프로젝트 컨텍스트, 모델 선택을 런타임에 설정합니다.
설정은 `~/.claude-toolkit/settings.json` 파일에 자동 저장되어 **서버 재시작 후에도 유지**됩니다.

### Claude API 설정

| 기능 | 설명 |
|------|------|
| **API 키 유효성 검사** | "API 키 유효성 검사" 버튼으로 Claude API 연결 즉시 테스트 |
| 설정 방법 | 환경변수 `CLAUDE_API_KEY` 또는 `application.yml` |

```bash
export CLAUDE_API_KEY=sk-ant-...
```

### Claude 모델 선택 ⭐ NEW

Settings 페이지에서 사용할 Claude 모델을 런타임에 즉시 전환할 수 있습니다.

| 기능 | 설명 |
|------|------|
| **모델 드롭다운** | claude-opus-4-5 / claude-sonnet-4-5 / claude-sonnet-4-20250514 / claude-haiku 등 선택 |
| **런타임 전환** | 서버 재시작 없이 즉시 모델 변경 적용 (`volatile` 오버라이드) |
| **기본값 유지** | 빈 값 선택 시 `application.yml` 설정 모델 사용 |

### Oracle DB 설정

SQL 리뷰 시 테이블 메타정보 조회, EXPLAIN PLAN 실행, ERD 자동 스캔에 사용됩니다.

```yaml
# application.yml 또는 환경변수
toolkit:
  db:
    url: jdbc:oracle:thin:@//hostname:1521/SERVICE_NAME
    username: myuser
    password: mypassword
```

```bash
export ORACLE_DB_URL=jdbc:oracle:thin:@//hostname:1521/SERVICE_NAME
export ORACLE_DB_USERNAME=myuser
export ORACLE_DB_PASSWORD=mypassword
```

### Java 프로젝트 경로 설정

Spring MVC 프로젝트 소스 루트를 지정하면 기술 문서 생성 / 코드 리뷰 시 연관 파일을 컨텍스트로 자동 포함합니다.

```yaml
toolkit:
  project:
    scan-path: C:/workspace/my-spring-project/src/main/java
```

**자동 분류 파일 유형:**

| 유형 | 감지 기준 |
|------|----------|
| Controller | `@RestController`, `@Controller` |
| Service | `@Service` |
| Repository / DAO | `@Repository`, 클래스명에 `Dao` 포함 |
| Mapper | `@Mapper`, 클래스명에 `Mapper` 포함 |
| MyBatis XML | `.xml` 파일 내 `<mapper` 태그 |
| DTO / VO | `dto`, `vo`, `request`, `response` 경로 |
| Config | `@Configuration` |

> `target/`, `test/`, `build/`, `.git/` 디렉토리는 자동으로 제외됩니다.

### 프로젝트 컨텍스트 메모

프로젝트 개요, 기술 스택, 코딩 컨벤션 등을 입력하면 **모든 AI 도구에 자동으로 컨텍스트로 주입**됩니다.

- 코드 리뷰 (`/codereview`) / 테스트 생성 (`/testgen`) / 기술 문서 (`/docgen`)
- API 명세 (`/apispec`) / 로그 분석 (`/loganalyzer`) / 정규식 (`/regex`)
- 커밋 메시지 (`/commitmsg`) / **Javadoc 생성 (`/javadoc`)** / **리팩터링 제안 (`/refactor`)**
- SSE 실시간 스트리밍 (`/stream`) — 스트리밍 응답에도 동일 컨텍스트 주입

```
예시:
Spring Boot 2.7 + MyBatis + Oracle 기반 ERP 시스템
패키지: com.mycompany.erp
코딩 컨벤션: 한국어 주석 필수, Lombok 사용, 카멜케이스
주요 도메인: 주문, 재고, 회원 관리
```

---

## 🔧 Spring Boot Starter 사용법

### 의존성 추가

```xml
<dependency>
    <groupId>io.github.claude-java-toolkit</groupId>
    <artifactId>claude-spring-boot-starter</artifactId>
    <version>0.7.0-SNAPSHOT</version>
</dependency>
```

### application.yml 설정

```yaml
claude:
  api-key: ${CLAUDE_API_KEY}          # 환경변수 권장
  model: claude-sonnet-4-20250514
  max-tokens: 4096
  timeout-seconds: 120
```

### 동기 호출

```java
@Service
public class MyService {
    private final ClaudeClient claude;

    public MyService(ClaudeClient claude) {
        this.claude = claude;
    }

    public String analyzeSql(String sql) {
        return claude.chat(
            "You are an Oracle DBA. Review this SQL for performance issues.",
            sql
        );
    }
}
```

### 스트리밍 호출 (SSE)

```java
// Consumer<String> — JDK 1.8 java.util.function 사용
claude.chatStream(
    "당신은 Java 코드 리뷰 전문가입니다.",
    "다음 코드를 리뷰해주세요:\n\n" + sourceCode,
    chunk -> System.out.print(chunk)   // 청크 단위로 실시간 출력
);
```

### 런타임 모델 전환 ⭐ NEW

```java
// ClaudeClient Bean을 주입받아 런타임에 모델 교체
@Autowired
private ClaudeClient claudeClient;

public void switchModel(String model) {
    claudeClient.setModelOverride(model);  // 빈 문자열 → 기본값 복원
}

public String getCurrentModel() {
    return claudeClient.getEffectiveModel();  // 실제 사용 중인 모델 반환
}
```

---

## 💻 CLI 사용법

### SQL Advisor CLI

```bash
# SQL 파일 리뷰
java -jar claude-sql-advisor.jar review --file my_query.sql

# Stored Procedure 리뷰 + Markdown 저장
java -jar claude-sql-advisor.jar review \
  --file SP_MY_PROC.sql \
  --type STORED_PROCEDURE \
  --output report.md \
  --api-key sk-ant-...

# stdin 파이프
cat my_query.sql | java -jar claude-sql-advisor.jar review
```

### Doc Generator CLI

```bash
# Oracle SP → Markdown 문서 생성
java -jar claude-doc-generator.jar generate \
  --file SP_MY_PROC.sql \
  --format md \
  --output docs/SP_MY_PROC.md

# Java 소스 → Typst 문서 생성
java -jar claude-doc-generator.jar generate \
  --file OrderService.java \
  --type "Java Service" \
  --format typst \
  --output docs/OrderService.typ
```

---

## 🛠 IntelliJ IDEA 개발 환경

### Maven 빌드 방법

```
1. IntelliJ Maven 패널 (View → Tool Windows → Maven)
2. 루트 프로젝트(claude-java-toolkit-parent) 선택
3. Lifecycle → install 더블클릭 (또는 install -DskipTests)
4. claude-toolkit-ui → Plugins → spring-boot → spring-boot:run 실행
```

### 환경변수 설정 (Run Configuration)

```
Run → Edit Configurations → Environment variables

CLAUDE_API_KEY=sk-ant-...
ORACLE_DB_URL=jdbc:oracle:thin:@//hostname:1521/ORCL
ORACLE_DB_USERNAME=myuser
ORACLE_DB_PASSWORD=mypassword
PROJECT_SCAN_PATH=C:/workspace/my-project/src/main/java
```

---

## 🏗 아키텍처

```
브라우저 (HTML + Bootstrap + Prism.js + marked.js + Mermaid.js)
    │  EventSource (SSE) ──── GET /stream/{id}
    │  fetch / form POST ──── 각 기능 엔드포인트
    ▼
Spring MVC Controllers (claude-toolkit-ui)
    │  SqlAdvisorController      /advisor          SQL 리뷰 + 보안 + 인덱스 최적화  ★ UPDATED
    │  ErdAnalyzerController     /erd              ERD 생성 + DDL 생성 탭 (v0.9)             ★ UPDATED
    │  CodeReviewController      /codereview       코드 리뷰 + 보안
    │  ComplexityController      /complexity       순환 복잡도 분석
    │  DocGeneratorController    /docgen           기술 문서 생성 (Oracle Package 추가)  ★ UPDATED
    │  JavadocController         /javadoc          Javadoc 자동 생성                    ★ NEW
    │  RefactoringController     /refactor         리팩터링 제안                        ★ NEW
    │  DepCheckController        /depcheck         pom.xml 의존성 분석                  ★ NEW
    │  TestGeneratorController   /testgen          JUnit5 생성
    │  ApiSpecController         /apispec          OpenAPI 명세 생성
    │  CodeConverterController   /converter        SP↔Java 양방향 변환 + iBatis 변환   ★ UPDATED
    │  MockDataController        /mockdata         Mock 데이터 생성
    │  MigrationController       /migration        DB 마이그레이션 스크립트
    │  MaskGenController         /maskgen          데이터 마스킹 스크립트               ★ NEW
    │  SpringMigrateController   /migrate          Spring Boot 3.x 마이그레이션         ★ NEW
    │  BatchController           /batch            일괄 처리
    │  LogAnalyzerController     /loganalyzer      로그 분석기
    │  RegexGeneratorController  /regex            정규식 생성기
    │  CommitMsgController       /commitmsg        커밋 메시지 생성기
    │  SseStreamController       /stream           SSE 스트리밍 허브 (harness_review + sql_translate)  ★ UPDATED
    │  ExplainPlanController     /explain          단일·스트리밍 분석 + Before/After 비교
    │  HarnessController         /harness          4단계 파이프라인 + Verifier탭 + 검증판정배지  ★ UPDATED
    │  HarnessBatchController    /harness/batch    배치 분석 (비동기 + 이메일 알림)
    │  HarnessDependencyController /harness/dependency  의존성 분석 + 소스 선택 패널
    │  HarnessDashboardController /harness/dashboard   품질 대시보드 (Chart.js 통계)
    │  SqlTranslateController    /sql-translate    이종 DB SQL 번역 (Oracle↔MySQL↔PostgreSQL↔MSSQL)  ★ NEW
    │  RoiReportController       /roi-report       AI 도입 ROI 시각화 (월별·기능별 Chart.js)          ★ NEW
    │  SecurityController        /security         REST API 키 인증 + Settings 비밀번호 잠금          ★ NEW
    │  InputMaskingController    /input-masking    양방향 민감정보 마스킹 (8가지 패턴)                 ★ NEW
    │  ReviewHistoryController   /history          이력 관리 + 재실행·하네스재분석 버튼
    │  FavoriteController        /favorites        즐겨찾기
    │  SettingsController        /settings         설정 + API 검증 + 모델 선택 + 설정 비밀번호 잠금   ★ UPDATED
    ▼
Service Layer
    │  SqlAdvisorService          — SQL/SP 리뷰 + 보안 감사 + 인덱스 최적화 제안  ★ UPDATED
    │  ErdAnalyzerService         — ERD 생성 (테이블 필터 지원)
    │  MockDataGeneratorService   — Mock 데이터 생성
    │  MigrationScriptService     — DB 마이그레이션 스크립트 생성
    │  DocGeneratorService        — 기술 문서 생성 (Oracle Package 포함)          ★ UPDATED
    │  JavadocGeneratorService    — Javadoc 자동 생성 (컨텍스트 지원)             ★ NEW
    │  RefactoringService         — 리팩터링 제안 + 개선 코드 생성                ★ NEW
    │  DependencyAnalyzerService  — pom.xml 의존성 취약점·충돌·업그레이드 분석    ★ NEW
    │  DataMaskingService         — DDL → 마스킹 UPDATE 스크립트 생성             ★ NEW
    │  SpringMigrationService     — Spring Boot 2.x→3.x 마이그레이션 체크리스트   ★ NEW
    │  TestGeneratorService       — JUnit5 생성
    │  ApiSpecGeneratorService    — OpenAPI 생성
    │  CodeConverterService       — SP↔Java 양방향 변환 + iBatis→MyBatis 변환    ★ UPDATED
    │  CodeReviewService          — Java 코드 리뷰 + 보안 리뷰
    │  ComplexityAnalyzerService  — 순환 복잡도 분석
    │  LogAnalyzerService         — 로그 분석 + 보안 위협 탐지
    │  RegexGeneratorService      — 정규식 생성 (5개 언어)
    │  CommitMsgService           — 커밋 메시지 생성 (4가지 스타일)
    │  OracleMetaService          — Oracle JDBC 메타정보 조회
    │  ProjectScannerService      — Spring 소스 파일 스캔
    │  ReviewHistoryService       — H2 파일 DB 영속화 이력 관리 (JPA, 20가지 유형)
    │  ReviewHistoryRepository    — Spring Data JPA Repository (H2 파일 DB)
    │  HarnessReviewService       — Analyst·Builder·Reviewer·Verifier 4단계 파이프라인 (Verifier 완성)  ★ UPDATED
    │  HarnessBatchService        — 배치 비동기 분석 + H2 이력 저장 + 다중 이메일
    │  BatchHistory               — JPA 엔티티 (batch_history 테이블)
    │  BatchHistoryRepository     — Spring Data JPA Repository (배치 이력)
    │  HarnessCacheService        — DB 오브젝트 캐시 + cron 자동 갱신
    │  SqlTranslateService        — 이종 DB 번역 시스템 프롬프트 빌더 (4종 DB)       ★ NEW
    │  RoiCalculator              — 월별·기능별 ROI 계산 (토큰비용·절감시간·ROI%)     ★ NEW
    │  RoiSettings                — ROI 설정 영속화 (~/.claude-toolkit/roi-settings.json)  ★ NEW
    │  SecuritySettings           — API 키 인증 + Settings 비밀번호 잠금 (BCrypt)    ★ NEW
    │  AuditLogService            — API 호출·설정변경 감사 로그 H2 영속화             ★ NEW
    │  ApiKeyFilter               — X-API-Key 헤더 인증 서블릿 필터                  ★ NEW
    │  SensitiveMaskingService    — 텍스트 민감정보 토큰 치환/복원 (8가지 regex 패턴) ★ NEW
    │  FavoriteService            — 즐겨찾기 관리 (JPA 영속화)
    │  FavoriteRepository         — Spring Data JPA Repository (즐겨찾기)
    │  SettingsPersistenceService — JSON 파일 설정 영속화 (claudeModel 포함)
    ▼
ClaudeClient (claude-spring-boot-starter)
    │  chat(systemPrompt, userMessage)            — 동기 요청
    │  chatStream(systemPrompt, msg, Consumer)    — SSE 스트리밍
    │  setModelOverride(model)                    — 런타임 모델 전환             ★ NEW
    │  getEffectiveModel()                        — 현재 사용 모델 반환          ★ NEW
    │  OkHttp3 기반 HTTP 클라이언트
    │  api.anthropic.com/v1/messages
    ▼
Anthropic Claude API
```

---

## 📂 프로젝트 구조

```
claude-java-toolkit/
├── claude-spring-boot-starter/
│   └── src/main/java/io/github/claudetoolkit/starter/
│       ├── client/ClaudeClient.java            # chat() + chatStream() + modelOverride (JDK 1.8)  ★ UPDATED
│       ├── config/ClaudeAutoConfiguration.java
│       ├── model/ClaudeRequest/Response/Message.java
│       └── properties/ClaudeProperties.java
│
├── claude-sql-advisor/
│   └── src/main/java/io/github/claudetoolkit/sql/
│       ├── advisor/SqlAdvisorService.java        # SQL 리뷰 + 보안 감사 + suggestIndexes()  ★ UPDATED
│       ├── db/OracleMetaService.java             # Oracle 메타정보 + EXPLAIN PLAN
│       ├── erd/ErdAnalyzerService.java           # ERD 생성 (테이블 필터)
│       ├── ddl/DdlGeneratorService.java          # ERD/스키마 → Oracle DDL 생성        ★ NEW (v0.9)
│       ├── mockdata/MockDataGeneratorService.java
│       ├── migration/MigrationScriptService.java
│       ├── model/AdvisoryResult/SqlType.java
│       └── cli/SqlAdvisorCli.java
│
├── claude-doc-generator/
│   └── src/main/java/io/github/claudetoolkit/docgen/
│       ├── generator/DocGeneratorService.java     # Oracle Package 소스타입 추가             ★ UPDATED
│       ├── converter/CodeConverterService.java    # SP↔Java 양방향 변환 + iBatis→MyBatis    ★ UPDATED
│       ├── javadoc/JavadocGeneratorService.java   # Javadoc 자동 생성                       ★ NEW
│       ├── refactoring/RefactoringService.java    # 리팩터링 제안                           ★ NEW
│       ├── depcheck/DependencyAnalyzerService.java# pom.xml 의존성 분석                     ★ NEW
│       ├── masking/DataMaskingService.java        # 데이터 마스킹 스크립트                  ★ NEW
│       ├── migration/SpringMigrationService.java  # Spring Boot 3.x 마이그레이션            ★ NEW
│       ├── testgen/TestGeneratorService.java
│       ├── apispec/ApiSpecGeneratorService.java
│       ├── codereview/CodeReviewService.java
│       ├── complexity/ComplexityAnalyzerService.java
│       ├── loganalyzer/LogAnalyzerService.java
│       ├── regex/RegexGeneratorService.java
│       ├── commitmsg/CommitMsgService.java
│       ├── scanner/ProjectScannerService.java
│       └── cli/DocGeneratorCli.java
│
└── claude-toolkit-ui/
    └── src/main/java/io/github/claudetoolkit/ui/
        ├── config/
        │   ├── ToolkitSettings.java               # DB + 프로젝트 경로 + 컨텍스트 + claudeModel  ★ UPDATED
        │   ├── ToolkitWebConfig.java               # 서비스 빈 등록 (5종 신규 추가)              ★ UPDATED
        │   └── SettingsPersistenceService.java    # JSON 파일 설정 영속화 (claudeModel 포함)     ★ UPDATED
        ├── history/
        │   ├── ReviewHistory.java                  # JPA @Entity, harness 3개 필드 추가    ★ UPDATED
        │   ├── ReviewHistoryRepository.java         # Spring Data JPA Repository
        │   ├── ReviewHistoryService.java            # saveHarness() + deleteRecentByType()  ★ UPDATED
        │   └── HarnessHistoryCleanup.java           # 앱 시작 1회 정리 컴포넌트             ★ NEW
        ├── favorites/
        │   ├── Favorite.java                       # JPA @Entity 변환                           ★ UPDATED
        │   ├── FavoriteRepository.java              # Spring Data JPA Repository                 ★ NEW
        │   └── FavoriteService.java                # JPA @Transactional 영속화                  ★ UPDATED
        └── controller/
            ├── HomeController.java
            ├── SqlAdvisorController.java           # index 탭 추가                              ★ UPDATED
            ├── ErdAnalyzerController.java
            ├── CodeReviewController.java
            ├── ComplexityController.java
            ├── DocGeneratorController.java
            ├── JavadocController.java              # ★ NEW
            ├── RefactoringController.java          # ★ NEW
            ├── DepCheckController.java             # ★ NEW
            ├── MaskGenController.java              # ★ NEW
            ├── SpringMigrateController.java        # ★ NEW
            ├── TestGeneratorController.java
            ├── ApiSpecController.java
            ├── CodeConverterController.java
            ├── MockDataController.java
            ├── MigrationController.java
            ├── BatchController.java
            ├── LogAnalyzerController.java
            ├── RegexGeneratorController.java
            ├── CommitMsgController.java
            ├── SseStreamController.java            # sql_translate 스트리밍 추가                  ★ UPDATED
            ├── HarnessBatchController.java         # /harness/batch — 배치 분석
            ├── HarnessDependencyController.java    # /harness/dependency — 의존성 분석
            ├── HarnessDashboardController.java     # /harness/dashboard — 품질 대시보드
            ├── SqlTranslateController.java         # /sql-translate — 이종 DB SQL 번역            ★ NEW
            ├── RoiReportController.java            # /roi-report — ROI 리포트                     ★ NEW
            ├── SecurityController.java             # /security — 보안 설정 + 감사 로그            ★ NEW
            ├── InputMaskingController.java         # /input-masking — 민감정보 마스킹             ★ NEW
            ├── ReviewHistoryController.java        # diff 비교 + bundle export + purge
            ├── FavoriteController.java
            └── SettingsController.java             # 설정 비밀번호 잠금 연동                     ★ UPDATED
    └── src/main/resources/
        ├── static/css/toolkit.css                  # 반응형 레이아웃 (모바일/태블릿) 추가        ★ UPDATED
        ├── static/js/toolkit.js                    # Ctrl+Enter 단축키 추가                     ★ UPDATED
        └── templates/
            ├── fragments/sidebar.html              # 실행계획 비교 메뉴 추가 + v1.8.0 버전 표시  ★ UPDATED
            ├── index.html
            ├── advisor/index.html
            ├── erd/index.html                      # DDL 생성 탭 추가                           ★ UPDATED (v0.9)
            ├── codereview/index.html
            ├── complexity/index.html
            ├── docgen/index.html                   # Oracle Package 소스타입 추가               ★ UPDATED
            ├── javadoc/index.html                  # ★ NEW
            ├── refactor/index.html                 # ★ NEW
            ├── depcheck/index.html                 # ★ NEW
            ├── maskgen/index.html                  # ★ NEW
            ├── migrate/index.html                  # ★ NEW
            ├── testgen/index.html
            ├── apispec/index.html
            ├── converter/index.html                # iBatis 변환 탭 추가                        ★ UPDATED
            ├── mockdata/index.html
            ├── migration/index.html
            ├── batch/index.html
            ├── loganalyzer/index.html
            ├── regex/index.html
            ├── commitmsg/index.html
            ├── explain/index.html
            ├── explain/compare.html                # Before/After 비교                          ★ NEW (v0.9)
            ├── harness/index.html                  # 4단계 파이프라인·Verifier탭·검증판정배지    ★ UPDATED
            ├── harness/batch.html                  # 배치 분석 + 이력 테이블 + 항목별 결과 모달
            ├── harness/dependency.html             # 의존성 분석 + 소스 선택 패널
            ├── harness/dashboard.html              # 품질 대시보드 (Chart.js 3종 차트)
            ├── sql-translate/index.html            # 이종 DB SQL 번역 (소스/타겟 DB 선택)       ★ NEW
            ├── roi-report/index.html               # ROI 리포트 (월별·기능별 Chart.js)          ★ NEW
            ├── security/index.html                 # 보안 설정 (API 키·설정 잠금·감사 로그)     ★ NEW
            ├── security/settings-unlock.html       # Settings 비밀번호 잠금 해제 페이지         ★ NEW
            ├── input-masking/index.html            # 민감정보 마스킹 (패턴 선택·토큰 맵)        ★ NEW
            ├── history/index.html                  # 재실행 버튼 + 하네스 재분석 버튼
            ├── favorites/index.html
            └── settings/index.html                 # Claude 모델 선택 + DB캐시 cron 패널       ★ UPDATED
```

---

## 📋 주요 기술 스택

| 구분 | 기술 |
|------|------|
| Language | Java 1.8 (var / Stream.of / List.of 미사용) |
| Framework | Spring Boot 2.7.18, Spring MVC, Thymeleaf 3.0 |
| HTTP Client | OkHttp3 4.12.0 |
| JSON | Jackson 2.15.4 |
| CLI | Picocli 4.7.5 |
| DB Driver | Oracle JDBC ojdbc8 21.5.0 |
| Frontend | Bootstrap 5.3, Font Awesome 6.4, Prism.js 1.29.0, marked.js 9.x, Mermaid.js 10.x |
| Build | Maven 3.6+ (multi-module) |
| Persistence (Settings) | `~/.claude-toolkit/settings.json` (OutputStreamWriter + UTF-8) |
| Persistence (History) | Spring Data JPA 2.7 + H2 2.x 파일 DB (`~/.claude-toolkit/history-db.mv.db`) |
| Persistence (Favorites) | Spring Data JPA 2.7 + H2 2.x 파일 DB (재시작 후 즐겨찾기 복원) ⭐ NEW |

---

## 🗺 로드맵

### ✅ v0.3.0

**신규 도구 (3종)**
- [x] 로그 분석기 `/loganalyzer` — 일반 분석 + 보안 위협 탐지, 파일 업로드
- [x] 정규식 생성기 `/regex` — 5개 언어 지원, 빠른 예제 8종
- [x] 커밋 메시지 생성기 `/commitmsg` — 4가지 스타일, diff 파일 업로드

**기존 기능 개선**
- [x] 다크/라이트 테마 토글
- [x] 입력 자동 임시저장 (localStorage, 2초 디바운스)
- [x] 토큰 수 실시간 예측 표시 (색상 경고 포함)
- [x] 폼 중복 제출 방지 (버튼 자동 비활성화)
- [x] 이력 페이지 실시간 키워드 검색 + 유형별 탭 필터

**인프라 개선**
- [x] SSE 스트리밍 아키텍처 (`POST /stream/init` → `GET /stream/{id}`)
- [x] `ClaudeClient.chatStream()` — `Consumer<String>` 콜백 스트리밍
- [x] 프로젝트 컨텍스트 메모 (모든 AI 요청에 자동 추가)

---

### ✅ v0.4.0

**신규 UX 기능**
- [x] ⚡ **실시간 보기 버튼** — 모든 도구 페이지에 SSE 스트리밍 버튼 노출
- [x] 실시간 결과 패널 + 복사 버튼 + 커서 애니메이션

**프로젝트 컨텍스트 자동 주입**
- [x] Settings 컨텍스트 메모를 코드 리뷰·테스트 생성 등 8개 도구에 자동 주입

**이력 영구 저장**
- [x] **H2 파일 DB 영속화** — `~/.claude-toolkit/history-db.mv.db` (서버 재시작 후 복원)
- [x] `ReviewHistory` → JPA `@Entity` 변환 (`javax.persistence.*`, Spring Boot 2.7 호환)
- [x] `ReviewHistoryRepository` (Spring Data JPA) 신규 추가

---

### ✅ v0.5.0

**신규 기능**
- [x] **iBatis → MyBatis 매퍼 자동 변환** — `/converter` 탭 추가, iBatis XML → MyBatis 3.x XML 변환
- [x] **Claude 모델 선택** — Settings 페이지에서 런타임 모델 전환, `volatile` 오버라이드
- [x] **Ctrl+Enter 단축키** — 모든 페이지 공통 폼 제출 단축키 (toolkit.js)
- [x] **즐겨찾기 H2 DB 영속화** — `Favorite` JPA @Entity 변환, `FavoriteRepository` 신규 추가
- [x] **리뷰 diff 비교** — `/history` 에서 두 항목 나란히 입력/결과 비교 모달

---

### ✅ v0.6.0

**신규 도구 (3종)**
- [x] **Javadoc 자동 생성** `/javadoc` — Java 소스에 한국어 Javadoc 주석 자동 삽입, SSE 스트리밍 지원
- [x] **리팩터링 제안** `/refactor` — 코드 문제점 분석 + 개선 코드 생성, SSE 스트리밍 지원
- [x] **pom.xml 의존성 분석** `/depcheck` — 취약점·충돌·업그레이드 권고, HIGH/MEDIUM/LOW 필터

**반응형 레이아웃**
- [x] 모바일(≤768px) / 태블릿(≤992px) 미디어쿼리 — toolkit.css 대규모 추가
- [x] 2컬럼 레이아웃 → 모바일 단일 컬럼 자동 전환

---

### ✅ v0.7.0

**신규 도구 (2종)**
- [x] **데이터 마스킹 스크립트** `/maskgen` — DDL → 개인정보 보호 Oracle UPDATE 스크립트 생성
- [x] **Spring Boot 3.x 마이그레이션** `/migrate` — Spring Boot 2.x→3.x 마이그레이션 체크리스트 자동 생성

**기존 기능 확장**
- [x] **Oracle Package 문서화** — `/docgen` 소스 유형에 "Oracle Package (SPEC+BODY)" 추가
- [x] **인덱스 최적화 제안** — `/advisor` 세 번째 탭 추가, CREATE INDEX 구문 포함 제안 생성
- [x] **보고서 묶음 내보내기** — `/history` 다중 선택 → Markdown 번들 파일 다운로드

**사이드바 강화**
- [x] 사이드바 섹션별(분석/생성/기록/도구) 접기/펼치기 토글
- [x] 전체 사이드바 접기/펼치기 + 플로팅 열기 탭 (position:fixed 이탈 해결)
- [x] localStorage 상태 저장 (현재 페이지 섹션 자동 오픈)

---

### ✅ v0.8.0

**신규 도구 (1종)**
- [x] **Oracle 실행계획 시각화** `/explain` — EXPLAIN PLAN 결과를 인터랙티브 트리로 렌더링
  - PLAN_TABLE 쿼리 → 부모-자식 트리 구조 파싱
  - 오퍼레이션 종류별 색상 구분 (FULL=🔴, INDEX=🟢, JOIN=🟣, SORT=🟡, NESTED=🟠)
  - 노드별 Cost 비율 바 시각화
  - Claude AI 성능 이슈 / 최적화 제안 / 핵심 단계 해설

---

### ✅ v0.9.0

**신규 도구 (3종)**
- [x] **ERD → Oracle DDL 역변환** `/erd` DDL 생성 탭
  - Mermaid erDiagram, 테이블 구조 설명, 자연어 입력 지원
  - Oracle 11g/12c 호환 — VARCHAR2, NUMBER, DATE
  - PRIMARY KEY / FOREIGN KEY 제약조건 + FK 인덱스 + COMMENT ON COLUMN 자동 생성
  - 실행 순서 의존성 주석 포함

- [x] **실행계획 Before/After 비교** `/explain/compare`
  - 최적화 전/후 SQL 동시 실행계획 분석
  - 나란히 트리 비교 + Cost 변화율 (%) 배지 (개선/악화/동일)
  - DBMS_XPLAN 원문 각각 접이식 표시

- [x] **이력 재실행** `/history` 재실행 버튼
  - 이력 상세 모달 → "재실행" 버튼 클릭 시 해당 기능 페이지로 입력 내용 자동 전달
  - 22가지 기능 유형 모두 지원 (SQL 리뷰, ERD, DDL, 코드 리뷰, 테스트, 실행계획 등)

---

### ✅ v1.1.0

- [x] **프롬프트 템플릿 관리** — `/prompts` 에서 기능별 시스템 프롬프트 편집·저장·적용
- [x] **분석 결과 내보내기** — 실행계획·ERD 결과 Markdown 다운로드, 히스토리 단건 `/history/{id}/export`

### ✅ v1.0.0

- [x] GitHub Actions CI/CD 파이프라인 (자동 빌드 · master push / PR 트리거)
- [x] REST API 모드 (외부 CI/CD 파이프라인 연동용 JSON API)

#### 🔌 REST API 엔드포인트 (`/api/v1/`)

| Method | Endpoint | 설명 |
|--------|----------|------|
| GET | `/api/v1/health` | 서버 상태 및 설정 확인 |
| POST | `/api/v1/sql/review` | SQL 성능·품질 리뷰 |
| POST | `/api/v1/sql/security` | SQL 보안 취약점 검사 |
| POST | `/api/v1/sql/explain` | Oracle 실행계획 분석 |
| POST | `/api/v1/doc/generate` | 소스코드 기술 문서 생성 |
| POST | `/api/v1/code/review` | Java/Spring 코드 리뷰 |
| POST | `/api/v1/code/security` | Java/Spring 보안 감사 |
| POST | `/api/v1/erd/analyze` | ERD 분석 |

**요청/응답 예시:**
```bash
# SQL 리뷰
curl -X POST http://localhost:8027/api/v1/sql/review \
  -H "Content-Type: application/json" \
  -d '{"sql": "SELECT * FROM ORDERS WHERE STATUS = 1"}'

# 응답
{
  "success": true,
  "data": {
    "sqlType": "SELECT",
    "review":  "## Summary\n...",
    "reviewedAt": "2026-04-01 12:00:00"
  },
  "error": null,
  "timestamp": "2026-04-01 12:00:00"
}
```

---

### ✅ v1.2.0

- [x] **SQL 성능 히스토리 대시보드** `/explain/dashboard` — 실행계획 Cost 추이 Chart.js 라인 차트, 기간 필터(전체/30일/7일), 통계 카드
- [x] **배치 SQL 분석** `/sql-batch` — 텍스트 직접 입력(`---` 구분자), CSV/텍스트 파일 업로드, 최대 30개 SQL 일괄 리뷰, MD 보고서 다운로드

---

### ✅ v1.3.0

**UI / UX 개선**
- [x] **다크/라이트 테마 커스텀 색상** — 액센트 컬러 선택 (`/settings`) · 5개 프리셋 + 컬러피커, localStorage 즉시 적용
- [x] **결과 페이지 인쇄/PDF 내보내기** — 모든 분석 결과 페이지 우측 하단 인쇄 버튼, 브라우저 인쇄 최적화 CSS
- [x] **분석 결과 공유 링크** — `/history` 단건 → `POST /history/{id}/share` → 7일 유효 UUID 공유 URL 생성, `/share/{token}` 독립 뷰

**운영 편의**
- [x] **Claude 모델 사용량 모니터링** `/usage` — 실제 API 토큰 수(input/output) 자동 기록, 일별·기능별 Chart.js 차트, 모델별 비용 추정($)
- [x] **분석 스케줄링** `/schedule` — cron 표현식으로 정기 SQL 리뷰 자동 실행, Spring `TaskScheduler` 동적 등록/해제, 즉시 실행 지원, 결과 이력 자동 저장

---

### ✅ v1.4.0

**SQL 분석 고도화**
- [x] **실행계획 SQL 즐겨찾기** — 분석 결과 ★ 즐겨찾기 등록, `/explain/dashboard`에서 재실행
- [x] **SQL 최적화 제안 적용** — AI 리뷰 결과에서 SQL 코드 블록 추출 → 인라인 편집 → 재분석

**운영 편의**
- [x] **스케줄 결과 이메일 발송** — JavaMail(`spring-boot-starter-mail`) 연동, SMTP 설정(`/settings`), 수신 주소 등록 시 스케줄 완료 후 자동 발송
- [x] **다중 SQL 프로필** `/db-profiles` — Oracle DB 연결 프로필 저장·편집·삭제, 원클릭 전환(현재 Settings에 즉시 반영)

**UI / UX 개선**
- [x] **대시보드 홈 커스터마이징** — 홈(`/`) 에 최근 즐겨찾기·분석 이력 위젯 추가
- [x] **글로벌 검색** `/search` — 이력·즐겨찾기·기능 목록 통합 키워드 검색, `/` 단축키 포커스

---

### ✅ v1.5.0

**SQL 분석 고도화**
- [x] **실시간 스트리밍 실행계획 분석** — EXPLAIN PLAN 트리를 DB 쿼리 즉시 렌더링 후, Claude AI 분석을 SSE 스트리밍으로 실시간 출력
  - 2단계 분리: `POST /explain/stream-init` (DB 전용) → `GET /stream/{id}` (AI 스트리밍)
  - 결과 저장 `POST /explain/stream-save` — 스트리밍 완료 후 이력 기록
  - `ExplainPlanService.analyzePlanOnly()` 신규 추가 (DB 단계만 분리)
  - `SseStreamController.registerStream()` 내부 등록 API 추가 (HTTP 라운드트립 없이 직접 등록)
- [x] **SQL 자동 리팩터링 diff 뷰** — 원본 SQL vs AI 제안 최적화 SQL을 LCS 기반 라인 diff로 시각화
  - `/advisor` 리뷰 결과에서 최적화 SQL 코드 블록 자동 추출
  - 삭제 라인(🔴 빨강·취소선) / 추가 라인(🟢 초록) / 동일 라인 색상 구분
  - 원본 복사 / AI 제안 복사 버튼 · `.md` 저장

---

### ✅ v1.6.0

**코드 리뷰 하네스 (Harness)**
- [x] **코드 리뷰 하네스** `/harness` — Analyst → Builder → Reviewer 3단계 AI 파이프라인
  - 분석가(Analyst): 성능 문제·안티패턴·보안 취약점 파악
  - 개선가(Builder): 모든 문제를 해결한 개선 코드 생성
  - 검토자(Reviewer): 변경 내역 검증 + APPROVED/NEEDS_REVISION 판정
  - LCS 기반 원본 vs 개선 코드 side-by-side Diff 뷰 (삭제🔴/추가🟢/동일 색상)
  - SSE 스트리밍 모드: 파이프라인 진행 단계별 실시간 출력
  - Java / SQL 언어 선택, 이력 자동 저장 (`HARNESS_REVIEW`)
  - `HarnessReviewService` + `HarnessController` 신규 추가

---

### ✅ v1.7.0

**📊 분석 품질 강화**
- [x] **코드 품질 점수** — 하네스 Reviewer가 가독성·성능·유지보수성·보안 항목별 X/10 점수 + 종합 판정 산출. 전용 "품질 점수" 탭 제공
- [x] **배치 분석** `/harness/batch` — 여러 Java 파일 / SQL 오브젝트를 동적 목록에 추가 후 순차 비동기 분석. 진행률 바·결과 테이블·상세 모달 제공. 완료 후 다중 이메일 알림 자동 발송. 배치 이력 H2 DB 영구 저장 (`batch_history`) + 이력 상세에서 항목별 분석 결과 직접 확인
- [x] **하네스 분석 템플릿** — 일반·성능 최적화·보안 취약점·리팩터링·Oracle SQL 성능·가독성 6가지 프리셋 선택. Analyst 시스템 프롬프트에 목적별 집중 지시 추가

**🗄️ Oracle / DB 특화**
- [x] **DB 오브젝트 의존성 분석** `/harness/dependency` — Oracle SP/Package/Function 또는 Java 클래스의 호출 관계·테이블 의존성 5개 섹션 보고서 출력. **Java 파일 브라우저 + DB 오브젝트 검색** 소스 선택 패널 내장
- [x] **DB 캐시 자동 갱신 스케줄** — Settings에서 cron 표현식 입력(프리셋 5종). `@Scheduled(fixedRate=60s)` + `CronExpression.parse()` 기반 자동 갱신. WAS 재기동 없이 최신 오브젝트 목록 유지

**📤 내보내기 / 리포팅**
- [x] **분석 결과 HTML 내보내기** — 하네스 결과(원본 코드·개선 코드·Diff·분석 요약·변경 내역·검토 결과)를 독립 실행형 HTML 파일로 저장 (`POST /harness/export-html`)
- [x] **품질 대시보드** `/harness/dashboard` — 누적 `HARNESS_REVIEW` 이력 기반 통계 카드(총 분석 수, APPROVED %, 언어 비율) + Chart.js 판정 도넛·언어 막대·점수 추이 라인 차트 + 최근 분석 타임라인

**⚡ UX / 운영 편의**
- [x] **하네스 히스토리 재로드** — 히스토리 상세 모달에서 "하네스 재분석" 버튼 클릭 시 원본 코드·언어를 localStorage에 저장 후 `/harness` 페이지로 자동 이동·로드
- [x] **분석 알림 (이메일)** — 배치 분석 완료 시 지정 이메일로 `emailService.sendJobResult()` 자동 발송 (SMTP 설정 필요)

**🔐 보안 강화 (차기 버전으로 이월)**
- [ ] **REST API 키 인증** — REST API 호출 시 `X-API-Key` 헤더 인증 (`/settings`에서 키 발급·관리)
- [ ] **Settings 비밀번호 잠금** — 설정 페이지 접근 시 PIN 또는 비밀번호 입력 요구

---

### ✅ v1.8.0 (현재)

**🛡️ 하네스 4단계 파이프라인 — Verifier 추가**
- [x] **검증자(Verifier) 단계** — Analyst → Builder → Reviewer → **Verifier** 4단계 완성
  - **Java 검증**: `## 🛠 컴파일 가능성` (import 누락·타입 불일치·접근 제어자 오류), `## 🚨 위험 변경 감지` (메서드 시그니처 변경·NullPointerException 추가·리소스 누수, 심각도 HIGH/MEDIUM/LOW), `## 🔗 Spring/JPA 호환성` (순환 의존·N+1·LazyInitializationException·@Transactional 누락), `## 🏁 최종 검증 판정`
  - **SQL 검증**: `## 🛠 SQL 문법 검증` (Oracle 키워드·괄호 불일치), `## 🚨 위험 변경 감지` (DROP/TRUNCATE/WHERE 없는 DELETE·UPDATE), `## 🔗 Oracle 의존성 검증` (DBMS_*·파티션·시퀀스·힌트 구문), `## 🏁 최종 검증 판정`
  - **판정 배지**: 🟢 VERIFIED (문제 없음) / 🟡 WARNINGS (주의 필요) / 🔴 FAILED (심각한 문제) — 검증 결과 탭 상단에 컬러 배지 표시
  - **SSE 스트리밍**: 파이프라인 스트리밍 시 Verifier 진행 단계 실시간 표시 (`streamStepVerifier`)
  - **심각도 하이라이팅**: HIGH/MEDIUM/LOW 키워드 자동 색상 렌더링
- [x] **배치 분석 + Verifier 통합** — 배치 실행 시 각 항목에 Verifier 단계 포함. 이력 자동 저장

---

### ✅ v1.9.0

**🔄 SQL DB 번역 — `/sql-translate`**
- [x] **이종 DB SQL 번역** — Oracle / MySQL / PostgreSQL / MSSQL 간 SQL 문법 자동 변환. SSE 실시간 스트리밍
- [x] **소스/타겟 DB 선택** — 4종 DB 조합 선택 UI + 빠른 스왑 버튼
- [x] **번역 이력 저장** — 번역 결과 ReviewHistory `SQL_TRANSLATE` 유형으로 자동 저장

**📊 ROI 리포트 — `/roi-report`**
- [x] **월별 ROI 시각화** — 기간 선택(1~24개월) + Chart.js 라인/바 차트 (절감시간·비용절감·ROI%)
- [x] **기능별 분석** — 기능 유형별 이력 집계 → 절감 효과 순위 표
- [x] **단가 설정** — 시간당 인건비·AI API 단가·환율 커스터마이징 (`~/.claude-toolkit/roi-settings.json`)
- [x] **ROI 스케줄러** — `@Scheduled` 월별 요약 자동 계산

**🔐 보안 설정 — `/security`**
- [x] **REST API 키 인증** — `X-API-Key` 헤더 인증. `/security`에서 키 발급·폐기·재발급. `ApiKeyFilter` 서블릿 필터
- [x] **Settings 비밀번호 잠금** — 설정 페이지 접근 시 BCrypt 비밀번호 입력 요구. 세션 기반 잠금 해제
- [x] **감사 로그(Audit Log)** — API 호출·설정 변경 이벤트를 타임스탬프·IP와 함께 H2 DB 기록. 페이지네이션 조회
- [x] `spring-security-crypto` 의존성 추가 (BCryptPasswordEncoder, `spring-boot-starter-security` 미사용)

**🕵️ 민감정보 마스킹 — `/input-masking`**
- [x] **8가지 패턴** — 주민등록번호·신용카드·이메일·전화번호·IP주소·비밀번호·API키·계좌번호 regex 탐지
- [x] **양방향 처리** — 마스킹(토큰 치환) → Claude 전송 → 복원(토큰 → 원본) 완전 워크플로우
- [x] **토큰 맵 시각화** — 토큰↔원본 매핑 테이블 + 복사 버튼 + 유형별 카운트 배지
- [x] **LocalStorage 임시저장** — 새로고침 후에도 입력 텍스트·토큰 맵 복원

**🛡️ 하네스 Verifier 완성**
- [x] **4단계 파이프라인 완성** — Analyst → Builder → Reviewer → **Verifier** 동기·스트리밍 모두 지원
- [x] **Java 검증**: 컴파일 가능성·Spring/JPA 호환성·위험 변경 감지 (심각도 HIGH/MEDIUM/LOW)
- [x] **SQL 검증**: SQL 문법·Oracle 의존성·위험 변경 감지 (DROP/TRUNCATE/WHERE 없는 DELETE)
- [x] **판정 배지**: 🟢 VERIFIED / 🟡 WARNINGS / 🔴 FAILED — 검증 결과 탭 상단 컬러 배지

---

### 🔮 v2.0.0 (예정) — 엔터프라이즈 로드맵

> Groups 4~8: 통합 워크스페이스, 멀티유저, 운영/배포, 외부 연동, 모니터링

**🧩 통합 워크스페이스 + 플러그인 아키텍처 (Group 4)**
- [ ] **AnalysisService 플러그인 인터페이스** — 9가지 분석 유형(CODE_REVIEW·SECURITY_AUDIT·TEST_GENERATION·JAVADOC·REFACTOR·SQL_REVIEW·SQL_SECURITY·SQL_TRANSLATE·HARNESS)을 인터페이스로 추상화. `AnalysisServiceRegistry` Bean 등록
- [ ] **커스텀 시스템 프롬프트 저장** — `custom_prompt` 엔티티. 분석 유형별 프롬프트 편집·저장·초기화 UI (`/settings/prompts`)
- [ ] **통합 워크스페이스 `/workspace`** — 복수 분석 유형 동시 선택 → `CompletableFuture` 병렬 실행 → 탭별 SSE 스트리밍 결과. 언어 자동 감지. 번들 저장/불러오기
- [ ] **AI 모델 비교 분석** — `/workspace/compare`: 동일 코드를 복수 모델로 병렬 분석, 좌우 2컬럼 비교
- [ ] **멀티 언어 지원 확장** — Python·JavaScript·TypeScript·Kotlin 언어별 Verifier 분기

**👥 멀티유저 / 팀 기능 (Group 5)**
- [ ] **계정 관리 + RBAC** — `app_user` 엔티티. ADMIN / REVIEWER / VIEWER 역할. Spring Security Form 로그인. 최초 실행 시 admin/admin1234 자동 생성
- [ ] **팀 설정 공유** — `shared_config` 엔티티. 프로젝트 컨텍스트·분석 템플릿 팀 단위 공유
- [ ] **분석 결과 공유 링크** — `share_token` 엔티티. 7일 만료 단축 URL. `/share/{token}` 로그인 불필요 독립 뷰

**🚀 운영 / 배포 (Group 6)**
- [ ] **Docker 멀티스테이지 빌드** — `eclipse-temurin:11-jre-alpine` 기반. 환경변수 주입. HEALTHCHECK 포함
- [ ] **외부 DB 지원** — `application-mysql.yml` / `application-postgresql.yml` 프로파일 분리. `DB_TYPE` 환경변수로 자동 선택
- [ ] **설치 마법사 `/setup`** — 4단계 Bootstrap 스텝 위저드 (API키·DB·이메일·관리자 계정). `SetupInterceptor`로 미완료 시 자동 리다이렉트
- [ ] **헬스체크 강화** — `ClaudeApiHealthIndicator` + `OracleDbHealthIndicator` (`/actuator/health`)

**🔗 외부 연동 (Group 7)**
- [ ] **Slack / Teams 웹훅 알림** — 배치 완료·FAILED 판정 시 채널 자동 메시지 (`NotificationService`)
- [ ] **GitHub PR 자동 코멘트** — PR URL 입력 → diff 가져오기 → 하네스 분석 → PR 코멘트 자동 등록
- [ ] **Jira 연동** — FAILED / NEEDS_REVISION 판정 시 Jira Issue 자동 생성 (REST API v3)
- [ ] **Git 로컬 연동** — 로컬 저장소 커밋 간 diff 선택 → 하네스 분석 (`ProcessBuilder` git 실행)

**📊 모니터링 / AI 고도화 (Group 8)**
- [ ] **Claude API 사용량 대시보드** — 일별·기능별 토큰 누적량 Chart.js 차트. 모델별 단가 설정
- [ ] **비용 추정 + 예산 알림** — 모델별 단가 기반 월 예상 비용. 예산 초과 시 이메일 경고 (`BudgetAlertScheduler`)

---

## 🤝 기여

PR과 Issue를 환영합니다!
기여 가이드: [CONTRIBUTING.md](./docs/CONTRIBUTING.md)

---

## 📄 라이선스

[Apache License 2.0](./LICENSE)
