# Contributing to Claude Java Toolkit

기여해 주셔서 감사합니다! 이 문서는 개발 환경 설정과 기여 프로세스를 설명합니다.

## 개발 환경

- **IDE**: IntelliJ IDEA (권장) — `.idea/runConfigurations` 포함
- **JDK**: 1.8 이상 (레거시 호환 유지)
- **Maven**: 3.6+
- **API Key**: `CLAUDE_API_KEY` 환경 변수 필요 (통합 테스트 시)

## 브랜치 전략

```
main          ← 안정 릴리즈
develop       ← 개발 통합 브랜치
feature/xxx   ← 기능 개발
fix/xxx       ← 버그 수정
```

## PR 규칙

1. `develop` 브랜치로 PR 제출
2. 새 기능에는 단위 테스트 포함
3. 커밋 메시지: `feat: ...` / `fix: ...` / `docs: ...` / `refactor: ...`

## 코드 스타일

- Java 1.8 문법 범위 내 작성 (람다, 스트림 OK / records, sealed 클래스 X)
- Lombok 사용 가능 (단, `@Data` 남용 지양)
- Oracle 방언 관련 코드에는 주석 필수

## 테스트

```bash
# 전체 빌드 + 테스트 (API 키 불필요)
mvn clean test

# 통합 테스트 포함 (API 키 필요)
CLAUDE_API_KEY=sk-ant-... mvn verify -Pintegration-test
```

## 이슈 리포트

버그 리포트 시 다음을 포함해 주세요:
- JDK 버전
- Spring Boot 버전
- 재현 가능한 최소 코드
- 에러 스택 트레이스
