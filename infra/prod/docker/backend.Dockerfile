# 백엔드 런타임 이미지 (linux/amd64 · t3.medium)
#
# jar 는 CI 가 이미 만든다 — ./gradlew build 가 테스트와 함께 bootJar 를 돌린다.
# 여기서 gradle 을 다시 돌리면 빌드 시간이 두 배가 되고 CI 5분 예산을 넘긴다.
# 그래서 COPY 만 한다.
#
# 빌드 컨텍스트는 backend/ 다:
#   ./gradlew bootJar
#   docker build -f infra/prod/docker/backend.Dockerfile backend/

ARG JAVA_IMAGE=eclipse-temurin:21-jre

# ── 레이어 분해 ──────────────────────────────────────────────────────────
# fat jar 를 통째로 한 레이어에 넣으면 코드 한 줄만 고쳐도 전체를 다시 받는다.
# 의존성/로더/애플리케이션을 나누면 앞 레이어가 캐시되어 pull 이 빨라진다.
FROM ${JAVA_IMAGE} AS layers
WORKDIR /app
COPY build/libs/*.jar app.jar

# Spring Boot 4 에서 layertools jarmode 는 제거됐다.
#   java -Djarmode=layertools -jar app.jar  ->  Unsupported jarmode 'layertools'
# 4.1.1 에서 실제로 확인하고 tools 로 바꿨다.
RUN java -Djarmode=tools -jar app.jar extract --layers --launcher --destination extracted

# ── 런타임 ───────────────────────────────────────────────────────────────
# alpine(musl) 이 100MB 가볍지만 네이티브 라이브러리 이슈 여지가 있다.
# 배포 시 pull 몇 초 차이라 안전한 glibc 쪽으로 간다.
FROM ${JAVA_IMAGE}
WORKDIR /app

RUN useradd --system --uid 10001 --no-create-home twojo

# 변경이 드문 것부터 복사한다 — 순서가 곧 캐시 효율이다.
COPY --from=layers /app/extracted/dependencies/ ./
COPY --from=layers /app/extracted/spring-boot-loader/ ./
COPY --from=layers /app/extracted/snapshot-dependencies/ ./
COPY --from=layers /app/extracted/application/ ./

USER twojo
EXPOSE 8080

# MaxRAMPercentage 60 -> mem_limit 1280m 기준 힙 ~768m.
# ExitOnOutOfMemoryError: OOM 후 절뚝이지 않고 죽는다 -> restart 정책이 재기동한다.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=60 -XX:+ExitOnOutOfMemoryError"

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
