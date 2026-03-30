# Claude Java Toolkit

> AI-powered tools for Oracle DB & Java/Spring enterprise development
> Powered by [Anthropic Claude API](https://docs.anthropic.com)

[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-1.8%2B-orange.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-2.7.x-green.svg)](https://spring.io/projects/spring-boot)
[![Version](https://img.shields.io/badge/version-0.7.0-brightgreen.svg)](#)

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

웹 대시보드는 **사이드바 네비게이션** 구조로 총 **20가지 AI 도구** + 설정 + 이력/즐겨찾기 관리를 제공합니다.

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
| **인덱스 최적화 탭** | SQL 쿼리 분석 후 CREATE INDEX 구문 포함 인덱스 제안 ⭐ NEW |
| 심각도 필터 | 결과를 **HIGH / MEDIUM / LOW** 탭으로 필터링 |
| 파일 업로드 | `.sql`, `.pls`, `.pck` 파일 직접 업로드 |
| 복사 / 새 탭 / 다운로드 | 결과를 클립보드 복사, 새 탭으로 열기, `.md` 파일 저장 |

#### 2. ERD 분석 — `/erd`

Oracle DB 스키마를 분석하여 Mermaid ERD 다이어그램을 생성합니다.

| 기능 | 설명 |
|------|------|
| DB 자동 스캔 | Oracle 연결 시 ALL_TABLES, ALL_CONSTRAINTS를 읽어 전체 스키마 분석 |
| 텍스트 입력 | DB 연결 없이 테이블 구조를 직접 입력하여 ERD 생성 |
| 테이블 필터 | 쉼표로 구분된 테이블명을 입력하여 원하는 테이블만 ERD 생성 |
| Mermaid 렌더링 | 브라우저에서 바로 다이어그램으로 렌더링 |

#### 3. Java 코드 리뷰 — `/codereview`

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
| 자동 저장 | 20가지 도구의 모든 결과 자동 기록, **H2 파일 DB 영속화** (서버 재시작 후 복원, 최대 100건) |
| 검색 / 필터 | 키워드 검색 + 유형별 탭 필터 (SQL/문서/코드/테스트/ERD/로그/정규식/커밋/Javadoc/리팩터링/의존성/마스킹/Spring이전) |
| **Diff 비교** | 두 항목을 선택하여 입력 코드 / 분석 결과를 나란히 비교 ⭐ NEW |
| **보고서 묶음 내보내기** | 다중 선택 후 Markdown 번들 파일 (`.md`) 일괄 다운로드 ⭐ NEW |
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
    │  ErdAnalyzerController     /erd              ERD 생성
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
    │  SseStreamController       /stream           SSE 스트리밍 허브 (9개 feature key) ★ UPDATED
    │  ReviewHistoryController   /history          이력 관리 + diff + bundle export    ★ UPDATED
    │  FavoriteController        /favorites        즐겨찾기
    │  SettingsController        /settings         설정 + API 검증 + 모델 선택         ★ UPDATED
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
    │  FavoriteService            — 즐겨찾기 관리 (JPA 영속화)                   ★ UPDATED
    │  FavoriteRepository         — Spring Data JPA Repository (즐겨찾기)        ★ NEW
    │  SettingsPersistenceService — JSON 파일 설정 영속화 (claudeModel 포함)      ★ UPDATED
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
        │   ├── ReviewHistory.java                  # JPA @Entity, 20가지 유형
        │   ├── ReviewHistoryRepository.java         # Spring Data JPA Repository
        │   └── ReviewHistoryService.java            # H2 파일 DB 영속화 (@Transactional)
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
            ├── SseStreamController.java            # javadoc_gen / refactor_gen / index_opt 추가  ★ UPDATED
            ├── ReviewHistoryController.java        # diff 비교 + bundle export 추가              ★ UPDATED
            ├── FavoriteController.java
            └── SettingsController.java             # claudeModel 저장 + availableModels 목록    ★ UPDATED
    └── src/main/resources/
        ├── static/css/toolkit.css                  # 반응형 레이아웃 (모바일/태블릿) 추가        ★ UPDATED
        ├── static/js/toolkit.js                    # Ctrl+Enter 단축키 추가                     ★ UPDATED
        └── templates/
            ├── fragments/sidebar.html              # 5개 신규 메뉴 + v0.7.0 버전 표시           ★ UPDATED
            ├── index.html
            ├── advisor/index.html                  # 인덱스 최적화 탭 추가                      ★ UPDATED
            ├── erd/index.html
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
            ├── history/index.html                  # diff 비교 + 선택 내보내기 추가             ★ UPDATED
            ├── favorites/index.html
            └── settings/index.html                 # Claude 모델 선택 패널 추가                 ★ UPDATED
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

### ✅ v0.7.0 (현재)

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

### 🔜 v0.8.0 (예정)

- [ ] Maven Central 배포 (`io.github.claude-java-toolkit`)
- [ ] GitHub Actions CI/CD 파이프라인
- [ ] Oracle Explain Plan 시각화 (트리 형태)
- [ ] 멀티 탭 결과 비교 (동일 쿼리 다른 모델 비교)

---

## 🤝 기여

PR과 Issue를 환영합니다!
기여 가이드: [CONTRIBUTING.md](./docs/CONTRIBUTING.md)

---

## 📄 라이선스

[Apache License 2.0](./LICENSE)
