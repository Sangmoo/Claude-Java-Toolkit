# ═══════════════════════════════════════════════════════════════
# Claude Java Toolkit — Multi-stage Docker Build (JDK 1.8)
# ═══════════════════════════════════════════════════════════════

# Stage 1: Build (Maven 3.6 + JDK 8)
FROM maven:3.6.3-jdk-8-slim AS builder
WORKDIR /build
COPY pom.xml .
COPY claude-spring-boot-starter/pom.xml claude-spring-boot-starter/pom.xml
COPY claude-sql-advisor/pom.xml claude-sql-advisor/pom.xml
COPY claude-doc-generator/pom.xml claude-doc-generator/pom.xml
COPY claude-toolkit-ui/pom.xml claude-toolkit-ui/pom.xml
# Best-effort pre-download of external dependencies for Docker layer caching.
#
# NOTE: dependency:go-offline 은 멀티모듈 빌드에서 내부 SNAPSHOT 모듈
# (claude-spring-boot-starter) 을 찾을 수 없어 실패하지만, 이 단계에서 이미 다운받은
# 외부 의존성(Spring Boot / Oracle JDBC / 기타) 은 .m2 캐시에 남아 다음 레이어에서
# 재사용된다. 다음 줄의 `mvn package -am` 이 올바른 모듈 순서로 실제 빌드를 수행하므로
# 여기의 실패는 최종 결과물에 영향을 주지 않는다 — 로그만 거슬리지 않도록 stdout/stderr
# 을 모두 /dev/null 로 돌리고 exit 0 보장.
RUN mvn dependency:go-offline -B -q >/dev/null 2>&1 || true
COPY . .
# frontend-maven-plugin이 Node.js 자동 설치 → npm install → npm run build
RUN mvn package -DskipTests -pl claude-toolkit-ui -am -B && \
    ls -la /build/claude-toolkit-ui/target/*.jar

# Stage 2: Runtime (JRE 8) — Ubuntu (Jammy) 기반
# ── 중요: Alpine(musl) 대신 Ubuntu(glibc) 기반 이미지를 사용.
#    Alpine + JDK 8 조합에서 api.anthropic.com 과의 TLS 핸드셰이크가
#    "Received fatal alert: handshake_failure" 로 실패하는 이슈가 있어
#    Debian/Ubuntu glibc + 최신 ca-certificates 로 변경.
FROM eclipse-temurin:8-jre-jammy
LABEL maintainer="Claude Java Toolkit"

# 루트 CA 최신화 + curl
RUN apt-get update && \
    apt-get install -y --no-install-recommends curl ca-certificates tzdata && \
    update-ca-certificates && \
    ln -snf /usr/share/zoneinfo/Asia/Seoul /etc/localtime && \
    echo "Asia/Seoul" > /etc/timezone && \
    rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY --from=builder /build/claude-toolkit-ui/target/claude-toolkit-ui-*.jar app.jar

# H2 데이터 영속화 볼륨
VOLUME /root/.claude-toolkit

# 환경변수
#
#  • TLS: JDK 8 기본 프로토콜을 TLSv1.2/1.3 으로 고정
#  • DNS: getaddrinfo 캐시 비활성화 — 컨테이너 네트워크 DNS 변경 즉시 반영
#  • IPv4 우선: 사내망 IPv6 경로가 막혀있을 때의 hang 방지
#  • Proxy: HTTP(S)_PROXY 는 사내망용 forward proxy 설정 (선택)
ENV CLAUDE_API_KEY="" \
    CLAUDE_BASE_URL="" \
    SPRING_PROFILES_ACTIVE="h2" \
    DB_TYPE="h2" \
    DB_HOST="localhost" \
    DB_PORT="" \
    DB_NAME="" \
    DB_USERNAME="" \
    DB_PASSWORD="" \
    HTTP_PROXY="" \
    HTTPS_PROXY="" \
    NO_PROXY="localhost,127.0.0.1" \
    TZ="Asia/Seoul" \
    JAVA_OPTS="-Xmx512m \
-Dhttps.protocols=TLSv1.2,TLSv1.3 \
-Djdk.tls.client.protocols=TLSv1.2,TLSv1.3 \
-Djava.net.preferIPv4Stack=true \
-Dnetworkaddress.cache.ttl=60 \
-Dnetworkaddress.cache.negative.ttl=10 \
-Dsun.security.ssl.allowUnsafeRenegotiation=true \
-Duser.timezone=Asia/Seoul \
-Doracle.jdbc.timezoneAsRegion=false \
-Doracle.jdbc.autoCommitSpecCompliant=false"

EXPOSE 8027

# v4.4.x — readiness probe 사용:
#   /actuator/health/readiness 는 StartupReadiness 가 Settings/DB warmup 을
#   완료한 후에야 UP 으로 응답. 이전 /actuator/health 는 Spring Boot 시작 즉시
#   UP 이라 사용자가 "준비 안된" 상태에서 화면 진입해 빈 결과 / 오류를 봤음.
# start-period 도 90초로 증가 (warmup 시간 확보)
HEALTHCHECK --interval=30s --timeout=10s --start-period=90s --retries=3 \
    CMD curl -f http://localhost:8027/actuator/health/readiness || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar --spring.profiles.active=${DB_TYPE:-h2}"]
