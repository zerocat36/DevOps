# ── Stage 1: build ──────────────────────────────────────────────
# Gradle + JDK 로 bootJar 빌드
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app

# Gradle wrapper + 빌드 스크립트만 먼저 복사 → 의존성 레이어 캐시
COPY gradlew .
COPY gradle/ gradle/
COPY build.gradle settings.gradle ./

# 의존성 다운로드 (소스 없이 — 캐시 레이어 분리)
RUN ./gradlew dependencies --no-daemon 2>/dev/null || true

# 소스 복사 후 빌드 (테스트 제외 — 테스트는 CI 단계에서 실행)
COPY src/ src/
RUN ./gradlew bootJar --no-daemon -x test
# 결과물: /app/build/libs/app.jar


# ── Stage 2: run ─────────────────────────────────────────────────
# JRE 만 포함한 경량 이미지 → JDK, Gradle 없음
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# 보안: root 가 아닌 전용 유저로 실행
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

# 빌드된 JAR 만 복사
COPY --from=builder /app/build/libs/*.jar app.jar

EXPOSE 8080

# Spring Boot 실행
# -XX:+UseContainerSupport : K8s 리소스 제한을 JVM 이 인식하도록
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-jar", "app.jar"]