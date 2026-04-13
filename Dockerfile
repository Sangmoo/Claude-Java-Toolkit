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
# Download dependencies first (layer cache)
RUN mvn dependency:go-offline -B -q 2>/dev/null || true
COPY . .
# frontend-maven-plugin이 Node.js 자동 설치 → npm install → npm run build
RUN mvn package -DskipTests -pl claude-toolkit-ui -am -B && \
    ls -la /build/claude-toolkit-ui/target/*.jar

# Stage 2: Runtime (JRE 8)
FROM eclipse-temurin:8-jre-alpine
LABEL maintainer="Claude Java Toolkit"

RUN apk add --no-cache curl

WORKDIR /app
COPY --from=builder /build/claude-toolkit-ui/target/claude-toolkit-ui-*.jar app.jar

# H2 데이터 영속화 볼륨
VOLUME /root/.claude-toolkit

# 환경변수
ENV CLAUDE_API_KEY="" \
    SPRING_PROFILES_ACTIVE="h2" \
    DB_TYPE="h2" \
    DB_HOST="localhost" \
    DB_PORT="" \
    DB_NAME="" \
    DB_USERNAME="" \
    DB_PASSWORD="" \
    JAVA_OPTS="-Xmx512m"

EXPOSE 8027

HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8027/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar --spring.profiles.active=${DB_TYPE:-h2}"]
