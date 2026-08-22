# 툴체인이 Java 25이므로 런타임도 25여야 한다. 낮추면 UnsupportedClassVersionError로 죽는다.
FROM eclipse-temurin:25-jre

WORKDIR /app

# bootJar의 archiveFileName을 app.jar로 고정해 두었다(build.gradle.kts).
# plain jar는 비활성화되어 있으므로 build/libs에는 이 파일 하나만 있다.
COPY build/libs/app.jar app.jar

RUN useradd -r -u 10001 ieuri
USER 10001

EXPOSE 8888

# 힙은 컨테이너 메모리 limit 기준으로 잡는다 — limit이 없으면 노드 전체(12GiB)를 기준으로 삼는다.
# 2 OCPU 환경이라 병렬 GC의 스레드 경합보다 SerialGC가 유리하다.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=70", "-XX:+UseSerialGC", "-jar", "/app/app.jar"]
