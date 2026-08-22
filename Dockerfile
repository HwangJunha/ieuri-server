# Docker 공식 openjdk 이미지는 2022년 폐기되어 25 태그가 없다. eclipse-temurin이 그 후속이며
# Eclipse Adoptium이 빌드한 OpenJDK다. 툴체인이 Java 25이므로 런타임도 25여야 한다 —
# 낮추면 UnsupportedClassVersionError로 기동조차 못 한다.
#
# jre가 아니라 jdk인 것은 의도적이다: 컨테이너에 붙어 jcmd·jmap·jstack으로 진단할 수 있다.
FROM eclipse-temurin:25-jdk

WORKDIR /app

# bootJar의 archiveFileName을 app.jar로 고정하고 plain jar를 껐다(build.gradle.kts).
# 따라서 build/libs에는 이 파일 하나만 있다.
COPY build/libs/app.jar app.jar

RUN useradd -r -u 10001 ieuri
USER 10001

EXPOSE 8080

# 힙은 컨테이너 메모리 limit 기준으로 잡는다 — limit이 없으면 노드 전체(12GiB)를 기준으로 삼는다.
# 2 OCPU 환경이라 병렬 GC의 스레드 경합보다 SerialGC가 유리하다.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=70", "-XX:+UseSerialGC", "-jar", "/app/app.jar"]
