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
#
# **70 → 65로 내린다**(2026-09-21). 70%는 1Gi limit에서 구조적으로 초과했다 — 누수가 아니라
# 산수였다. 실측한 힙 밖 몫이 449Mi다:
#
#   메타스페이스 157Mi (32,862 클래스 / 568 로더 — Hibernate·ByteBuddy·Tomcat)
#   스레드 스택   41Mi (41개)
#   코드 캐시     14Mi
#   GC 구조체·네이티브 237Mi
#
#   1Gi × 70% = 힙 718Mi + 449Mi = 1167Mi > 1024Mi  ← 힙이 다 차면 반드시 OOM
#
# 힙 누수는 없었다: 히스토그램 상위가 전부 JDK 기본 타입이고, 메타스페이스는 20초 간격
# 측정에서 159662K로 완전히 동일했다. G1으로 바꿔 힙 회수도 정상이었다. 그런데도 죽은 것은
# 힙이 최대까지 차기 전에 컨테이너 한도에 먼저 닿기 때문이다.
#
# limit을 2Gi로 올리면 70%도 들어가지만(1883/2048) 여유가 165Mi뿐이다. 65%면 힙 1331Mi로
# 이전(718Mi)의 거의 두 배를 쓰면서 여유가 268Mi 남는다 — 힙 밖은 클래스가 늘거나 스레드가
# 늘면 같이 커지는 축이라 그만큼은 비워 둔다.
#
# **SerialGC에서 G1GC로 바꾼다**(2026-09-21 부하 측정). 원래 판단은 "2 OCPU 환경이라 병렬 GC의
# 스레드 경합보다 SerialGC가 유리하다"였고 그 자체로는 틀리지 않았다. 뒤집은 것은 측정이다:
#
#   개발 100 RPS, 힙 718Mi(1Gi × 70%)
#     med 205ms / p90 23.7s / p95 30.1s / max 54.6s
#     jcmd GC.heap_info: Tenured 170416K 중 165278K 사용(96%)
#     부하가 끝나고 CPU가 3m로 유휴인데도 메모리가 내려오지 않고 OOMKilled
#
# 중앙값과 p90의 간극이 두 자릿수 배인 것은 **Full GC의 stop-the-world**다. SerialGC의 Full GC는
# 단일 스레드라 힙이 찰수록 정지가 길어지고, 그 구간에 걸린 요청이 꼬리를 만든다. 스레드 경합을
# 피하려다 정지 시간을 얻은 셈이다.
#
# **CPU limit 상향과 한 쌍이다**(ieuri-manifest). 컨테이너가 보는 CPU는 노드의 2 OCPU가 아니라
# limit에서 온다 — 800m이면 `nproc`이 1이고, G1이 GC 스레드를 병렬로 쓰지 못한다. 매니페스트에서
# limit을 1000m으로 올려 2로 인식시키는 변경이 함께 가야 이 플래그가 제값을 한다.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=65", "-XX:+UseG1GC", "-jar", "/app/app.jar"]
