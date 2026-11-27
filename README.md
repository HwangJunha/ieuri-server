# ieuri-server

외국인 방문객을 위한 위치 기반 관광 앱 **K-Trip Spot**의 백엔드입니다. 한국관광공사 TourAPI 등 공공 데이터를 수집해 4개 언어(영어, 한국어, 일본어, 중국어)로 제공하고, 회원, 커뮤니티, 푸시 알림을 GraphQL API 하나로 노출합니다.

- 앱: [ieuri-k-trip-spot](https://github.com/HwangJunha/ieuri-k-trip-spot) (Flutter)
- 스토어: [App Store](https://apps.apple.com/us/app/k-trip-spot/id6805503140) · [Google Play](https://play.google.com/store/apps/details?id=kr.ieuri.ktripspot)

1인 개발 프로젝트입니다. 서버, 앱, 인프라를 한 사람이 설계하고 운영합니다.

## 기술 스택

| 영역 | 사용 기술 |
|---|---|
| 언어, 런타임 | Kotlin 2.3, Java 25 |
| 프레임워크 | Spring Boot 4, Spring Security, Spring Batch |
| API | GraphQL (Netflix DGS, 스키마 우선, `src/main/resources/schema/*.graphqls`) |
| 데이터베이스 | PostgreSQL (PostGIS, pgvector), Spring Data JPA + QueryDSL, 일부 영속성은 Exposed |
| 검색 | 별도 검색 노드의 pg_search(BM25, 한국어 형태소 분석) |
| 캐시, 토큰 저장 | Valkey (Redis 프로토콜 호환) |
| 마이그레이션 | Flyway (메인 DB와 검색 노드를 각각 관리) |
| 파일 저장 | S3 호환 객체 저장소 |
| 알림 | Firebase Cloud Messaging |
| 관측 | Micrometer, Prometheus, OpenTelemetry 트레이싱 |
| 품질 검사 | JUnit 5, MockK, ArchUnit, Konsist, ktlint |
| 배포 | Docker 이미지, Kubernetes, GitHub Actions |

## 아키텍처

도메인(바운디드 컨텍스트)별로 나누고, 각 도메인 안에서 헥사고날 구조(포트와 어댑터)를 따릅니다. 지금은 서버 하나로 배포하지만 규모가 커지면 도메인을 별도 애플리케이션으로 떼어 낼 수 있도록, 도메인 사이의 의존을 막아 두었습니다.

```
src/main/kotlin/org/around/ieuri/
├── auth        회원, 토큰, 권한(RBAC), SNS 로그인, 감사 로그
├── tourism     장소, 카탈로그, 지역, 추천, 혼잡도, 발자국, 데이터 수집 배치
├── community   장소 이야기, 대화(DM), 장소 Q&A
├── alarm       기기 등록, 수신 설정, 푸시 발송
└── common      페이지네이션, 보안, 에러, 이벤트, 로깅, 관측
```

각 도메인은 같은 모양입니다.

```
<도메인>/
├── domain        순수 도메인 모델과 규칙. 프레임워크에 의존하지 않음
├── application   유스케이스, 포트(인터페이스)
└── adapter       GraphQL, 영속성, 외부 API 클라이언트, 배치, 이벤트, 설정
```

의존 방향은 `adapter → application → domain` 한 방향이고, 이 규칙은 문서가 아니라 테스트가 지킵니다.

- **ArchUnit**: 계층 의존 방향, 도메인 순수성, 엔티티 위치, 도메인 간 격리를 검사합니다 (`src/test/.../architecture`).
- **Konsist**: 코드 컨벤션과 일관성 규칙을 검사합니다.
- **ktlint**: 코드 스타일을 `check`에 포함합니다.
- 변환 코드(GraphQL 입력과 도메인, 도메인과 엔티티, 외부 API 응답과 도메인)는 순수 함수로 분리해 단위 테스트합니다.

## 주요 설계

- **스키마 우선 GraphQL.** `schema/*.graphqls`에서 입출력 타입을 생성합니다. 도메인마다 스키마 파일이 하나씩입니다.
- **데이터 수집.** TourAPI 장소 정보와 한국관광공사 빅데이터(지역별 방문자 수, 관광지 집중률), 서울시 도시데이터를 Spring Batch와 클라이언트로 가져와 저장합니다. 외부 호출은 요청 속도 제한(Resilience4j)과 일일 한도로 제어합니다.
- **추천.** 장소 텍스트의 임베딩(1024차원)을 pgvector에 저장하고, 거리 순 목록이 한 종류의 장소로 쏠리는 지역 편중을 임베딩 유사도를 더해 완화합니다.
- **검색 노드 분리.** 장소 검색은 별도 PostgreSQL 노드의 BM25 인덱스로 처리합니다. 메인 DB의 장소 테이블이 논리 복제로 넘어가는 읽기 전용 노드이고, 마이그레이션도 별도 Flyway 인스턴스(`db/search-node`)로 관리합니다.
- **읽기 복제본 라우팅.** 읽기 전용 트랜잭션은 복제본으로 보냅니다 (`ieuri.read-replica`).
- **인증.** JWT 액세스 토큰과 Valkey에 저장하는 refresh 토큰, 구글, 카카오, 애플 id_token 검증, 선언적 인가(`@RequiresPermission`)를 사용합니다. 회원 탈퇴 시에는 SNS 연동 해제를 함께 처리합니다.
- **관측.** `/actuator/prometheus`로 메트릭을 노출하고 GraphQL과 JDBC를 계측해 OpenTelemetry로 트레이스를 내보냅니다.

설계 결정과 근거는 `problem/` 아래 문서(SPEC, PERF)에 기능 단위로 남겨 두었습니다.

## 실행

필요한 것

- JDK 25 (Gradle 툴체인이 설정합니다)
- PostgreSQL (`postgis`, `vector` 확장), 검색 노드용 PostgreSQL (`pg_search` 확장), Valkey 또는 Redis
- 외부 API 키: 한국관광공사 TourAPI, 서울 열린데이터광장, 임베딩 API
- FCM 서비스 계정 파일 (푸시 알림을 쓸 때)

환경변수 이름과 기본값은 `src/main/resources/application.yaml`과 `application-local.yaml`에 있습니다. DB 비밀번호, JWT 서명 키, 암호화 키, 외부 API 키는 모두 환경변수로 주입하며 저장소에 값은 없습니다.

```sh
./gradlew bootRun        # local 프로파일, 포트 8888
```

GraphQL 엔드포인트는 `http://localhost:8888/graphql`입니다. 배포 환경은 8080을 씁니다.

## 빌드와 검사

```sh
./gradlew check          # 테스트 + 아키텍처 검사 + 스타일 (CI와 동일)
./gradlew lintKotlin     # 스타일만 검사
./gradlew formatKotlin   # 스타일 자동 수정
./gradlew bootJar        # 실행 가능한 jar (build/libs/app.jar)
```

GitHub Actions가 `test`, `lint`, `build` 워크플로를 PR마다 실행합니다. `Dockerfile`은 빌드된 jar를 Temurin 25 이미지에 올리고 비루트 사용자로 실행합니다.

## 디렉터리

```
.
├── src/main/kotlin            애플리케이션 코드 (위 아키텍처)
├── src/main/resources
│   ├── schema                 GraphQL 스키마 (도메인별)
│   ├── db/migration           메인 DB Flyway 마이그레이션
│   └── db/search-node         검색 노드 Flyway 마이그레이션
├── src/test                   단위 테스트와 아키텍처 테스트
├── problem                    기능별 설계 문서, 성능 측정 기록
├── Dockerfile
└── .github/workflows          CI
```
