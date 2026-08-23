> spec: SPEC-0009 · source: `Specs/SPEC-0009/spec.md` (Obsidian) · status: draft

# SPEC-0009 — 서버 설계

요구사항과 수용 기준은 spec에 있다. 이 문서는 그것을 옮겨 적지 않고 요구사항 번호로 참조한다.

## 현황 실측

리팩토링 대상의 규모다. worktree 중복을 제외한 main 소스 기준(176파일 / 10,318 LOC).

| 항목 | 수량 | 위치 |
|---|---|---|
| JdbcTemplate 보유 어댑터 | 4 | 전부 tourism |
| 생 SQL 문자열 | 22 | `SpotDetailJdbcAdapter` 8, `SpotQueryAdapter` 6, `FestivalJdbcAdapter` 4, `SpotJpaAdapter` 4 |
| `@Query` (전부 JPQL, native 0) | 5 | `AuthJpaRepositories` 3, `RbacJpaRepositories` 1, `SpotJpaRepository` 1 |
| 변환 함수 | 18 | tourism 15, auth 3 |
| 문자열 WHERE 조립 헬퍼 | 2 | `SpotQueryAdapter.whereClause`, `.likedWhereClause` |
| KDoc 15줄 초과 블록 | 19 | 최대 `BatchInfrastructureConfig` 50줄 |

QueryDSL은 이미 도입되어 있다 — `querydsl-jpa:6.11`, kapt로 Q클래스 13개 생성, `QuerydslConfig`의 `JPAQueryFactory` 빈, 그리고 `SpotQueryAdapter`·`MemberQueryAdapter`·`RoleQueryAdapter`가 이미 소비 중이다. 따라서 R1·R3은 도구 도입이 아니라 기존 도구의 적용 범위를 넓히는 작업이다.

## 작업 순서

순서에 의존성이 있다. R4를 먼저 하는 것이 이 spec의 핵심 설계 판단이다.

1. **R4·R5·R6 — 변환 추출과 테스트** (선행)
2. **R1·R2·R3 — 쿼리 재작성**
3. **R7·R8 — WHERE 헬퍼 제거와 커서 분리**
4. **R14 — 어댑터 클래스명 정정** (분할이 끝난 뒤라야 최종 이름이 정해진다)
5. **R9·R10 — 주석 정비**

R11·R12·R13은 특정 단계가 아니라 매 단계에서 지켜야 할 불변 조건이다.

변환 함수를 먼저 `mapper` 패키지로 빼면 프레임워크 의존이 없는 순수 함수가 되고, 여기에 R5·R6의 단위 테스트가 붙는다. 그 다음 쿼리를 재작성하면 "행을 어떻게 가져오는가"가 바뀌어도 "행을 도메인으로 어떻게 옮기는가"는 테스트로 고정되어 있다. 순서를 뒤집으면 쿼리 재작성 시점에 검증 수단이 없다.

이 spec의 테스트 범위는 변환 함수뿐이다(spec 비범위 참조). 쿼리 자체의 정확성을 증명하는 테스트는 명세하지 않는다 — 어댑터 SQL은 DB 없이 관측할 수 없고, 이 플랫폼은 인프라 테스트를 명세 대상에서 제외한다. 이 한계는 감추지 않고 여기 적어둔다: **R1·R3의 동작 보존은 이 spec의 수용 기준으로 증명되지 않으며, 구현 시 수동 확인에 의존한다.**

## R4·R5·R6 — 변환 추출

### 패키지 배치

각 바운디드 컨텍스트의 인프라 계층 안에 둔다. 변환은 엔티티(인프라)와 도메인을 잇는 일이므로 도메인 계층에 두면 도메인이 JPA 엔티티를 알게 된다.

```
tourism/infrastructure/adapter/out/persistence/mapper/
auth/infrastructure/adapter/out/persistence/mapper/
```

외부 API 응답 변환(`TourApiClient` 등 15개 중 다수)은 성격이 다르다. 이쪽은 `tourism/infrastructure/adapter/out/tourapi/mapper/`에 별도로 둔다 — 엔티티 변환과 섞으면 한 패키지가 다시 비대해진다.

### 형태

변환 원본 타입의 확장 함수로 선언하고, 도메인 모델을 반환한다.

```
fun SpotEntity.toDomain(): Spot
fun TourApiDetailResponse.toSpotDetail(): SpotDetail
```

`LayeredArchitectureTest`가 인프라→애플리케이션→도메인 방향을 강제하므로, `mapper`가 인프라 하위에 있는 한 규칙 위반이 없다(R12-2).

### 테스트

`src/test/kotlin/.../mapper/`에 컨텍스트별로 둔다. Spring 컨텍스트도 DB도 띄우지 않는 순수 함수 테스트다(R5-1, R5-2).

단언은 **필드별로** 쓴다(R6-1). 전 필드를 채운 픽스처 하나와 객체 동등성 비교로 갈음하지 않는다 — 그 방식은 실패했을 때 어느 필드가 틀렸는지 알려주지 않는다. 필드별 단언은 필드가 새로 추가될 때 자동으로 깨지지 않는다는 약점이 있으나, 그 대가로 실패 지점을 즉시 지목한다.

값 객체를 품은 엔티티는 값 객체 내부 필드까지 개별 단언한다(R6-3). nullable 필드가 전부 null인 경우를 별도 케이스로 둔다(R5-4).

테스트를 담을 단위:

| 단위 | 대상 |
|---|---|
| `SpotMapperTest` | `SpotEntity` ↔ `Spot` |
| `SpotDetailMapperTest` | 상세 및 하위 값 객체 |
| `FestivalMapperTest` | 축제 엔티티 |
| `SpotReactionMapperTest` | 반응 엔티티 |
| `MemberMapperTest` | `MemberEntity` ↔ `Member`, 프로필 포함 |
| `EmailVerificationMapperTest` | 기존 `toDomain` private 함수 이관분 |
| `PasswordResetMapperTest` | 기존 `toDomain` private 함수 이관분 |
| `TourApiResponseMapperTest` | 외부 응답 → 도메인 (기존 `TourApiDetailMappingTest`와 중복 확인 필요) |

`TourApiDetailMappingTest`가 이미 있으므로, 외부 응답 변환 테스트는 신설이 아니라 이관·정리가 될 수 있다. 중복 작성하지 않는다.

POL-012·POL-013 확인 지점: auth 변환 함수를 옮길 때 이메일 암·복호화가 일어나는 위치가 바뀌면 안 되고, 변환 확장 함수가 개인정보를 `toString`으로 노출하지 않아야 한다.

## R1·R2·R3 — 쿼리 재작성

### 남기는 것과 없애는 것

생 SQL을 전부 없앨 수는 없다. 남기는 두 부류는 **예외로 인정하되 격리**한다(R2).

| 부류 | 처리 | 이유 |
|---|---|---|
| PostGIS 공간 쿼리 | 전용 어댑터로 격리 | `ST_DWithin`·`ST_Distance`·`ST_MakePoint`를 QueryDSL로 표현할 수 없다. Hibernate Spatial 도입은 비범위 |
| `INSERT ... ON CONFLICT` UPSERT | 전용 어댑터로 격리 | 모든 DB가 UPSERT를 지원하지는 않으나 그 수준까지 추상화하지 않는다. JPA `merge`로 바꾸면 select-then-insert 경합이 새로 생기고 동시성 동작이 달라진다 |
| 그 외 SELECT·DELETE | QueryDSL 또는 파생 쿼리 | 타입 안전하게 표현 가능하다 |

격리의 의미는 "그 두 종류의 어댑터에서만 `JdbcTemplate` 의존이 발견된다"는 것이다(R2-1). 조회 어댑터가 저장 겸 조회를 함께 하는 현재 구조에서는 이 분리가 곧 클래스 분할을 뜻한다.

### 어댑터별 처리

| 현재 | 생 SQL | 처리 |
|---|---|---|
| `SpotQueryAdapter` | 6 | 공간 쿼리 2개(`searchNearby`·`countNearby`)는 공간 전용 어댑터로 분리. 나머지 4개(`reactedSpotIds`, `findIdentity`, `findSpotBasics`, `findLikedSpots`)는 QueryDSL로 전환 |
| `SpotDetailJdbcAdapter` | 8 | UPSERT 계열은 UPSERT 전용 어댑터로. SELECT·DELETE는 QueryDSL 또는 파생 쿼리 |
| `FestivalJdbcAdapter` | 4 | `UPSERT_SQL`은 UPSERT 전용으로. SELECT 조인 2개는 QueryDSL, 청크 DELETE는 QueryDSL `delete().where(in)` |
| `SpotJpaAdapter` | 4 | 이름과 달리 JdbcTemplate을 쓰고 있다. `UPSERT_SQL`은 UPSERT 전용으로, 나머지는 QueryDSL. 클래스명 정정은 R14 |

포트 인터페이스(`SpotQueryPort` 등)의 시그니처는 바꾸지 않는다(R13). 애플리케이션 계층이 어댑터 분할을 알 필요가 없고, 시그니처가 유지되어야 포트를 스텁으로 두는 R1-2·R1-4·R3-2·R3-3·R7-3·R11-2·R12-1이 성립한다. 어댑터를 둘로 쪼개도 포트는 하나로 남으며, 분할된 어댑터를 조합하는 책임은 인프라 계층 안에서 끝난다.

### `@Query` 5개

전부 JPQL이고 native가 없으므로 DB 종속 위험 자체는 낮다. 그래도 R3의 대상인 이유는 조회 정의가 애노테이션 문자열과 QueryDSL 두 곳에 나뉘어 있는 상태를 없애기 위해서다.

| 위치 | 성격 | 처리 |
|---|---|---|
| `AuthJpaRepositories` 3개 | `@Modifying` 벌크 소비 2개, 최신 생성시각 조회 1개 | 벌크 갱신은 QueryDSL `update()`. 조회는 파생 쿼리로 충분 |
| `RbacJpaRepositories` 1개 | 프로젝션 인터페이스 반환 | QueryDSL `Projections`로 전환. 프로젝션 타입이 사라지면 호출부도 함께 정리 |
| `SpotJpaRepository` 1개 | contentId+language 단건 조회 | 메서드명 파생 쿼리로 전환 가능 |

`@Modifying` 벌크 연산을 QueryDSL로 옮길 때 영속성 컨텍스트 flush·clear 동작이 달라질 수 있다. 같은 트랜잭션 안에서 갱신 후 재조회하는 호출부가 있는지 확인하고, 있으면 동작을 맞춘다.

## R14 — 어댑터 클래스명 정정

`SpotJpaAdapter`는 이름이 JPA를 표방하지만 실제로는 `JdbcTemplate`으로 생 SQL을 실행한다. 이름이 구현을 잘못 알려주면 다음 사람이 "여기는 이미 JPA다"라고 믿고 지나친다 — 이번 리팩토링이 겨냥하는 문제를 이름이 숨기고 있는 셈이다.

R1·R2를 거치면 이 클래스는 UPSERT 전용 부분과 QueryDSL 조회 부분으로 갈라진다. 분할이 끝난 시점의 실제 수단에 맞춰 이름을 정한다. 분할 전에 미리 이름만 바꾸면 한 번 더 바꾸게 되므로, **R1·R2 완료 후에 수행한다.**

이름 변경의 파급은 스프링 빈 주입에 한정된다. 어댑터는 포트 인터페이스로 주입되므로 타입 기준 해소가 유지되는 한 호출부는 영향을 받지 않는다(R14-2). 포트 인터페이스명과 시그니처는 건드리지 않는다(R14-3, R13과 일관). 빈 이름을 문자열로 지정한 곳이 있는지만 확인하면 된다.

`FestivalJdbcAdapter`·`SpotDetailJdbcAdapter`는 이름이 이미 수단과 일치하지만, 분할 후 남는 책임에 따라 이름이 맞지 않게 되면 같은 기준을 적용한다.

## R7·R8 — WHERE 헬퍼 제거와 커서 분리

`SpotQueryAdapter`의 `whereClause`와 `likedWhereClause`는 `Pair<String, List<Any>>`를 반환한다. SQL 문자열과 위치 파라미터 목록을 손으로 맞춰야 하고, 실제로 파라미터 순서를 경고하는 주석이 붙어 있다. QueryDSL 전환이 이 문제를 자연히 없앤다 — 조건이 `BooleanExpression`이 되면 파라미터 순서라는 개념이 사라진다.

제거의 요지는 "조건을 각 조회 메서드 안에서 완결시킨다"는 것이다(R7-2). 두 메서드가 공유하던 조건을 굳이 다시 공용 함수로 묶지 않는다. `searchNearby`와 `countNearby`처럼 조건이 같아야 하는 쌍은 같은 어댑터 안에 두어 눈으로 대조 가능하게 한다.

auth의 `MemberQueryAdapter.baseWhere`는 대상이 아니다. 이미 QueryDSL `BooleanBuilder`라 타입 안전하고, 페이지 조회와 카운트 조회가 같은 조건을 써야 한다는 요구도 명확하다.

커서 헬퍼(`encodeCursor`·`decodeCursor`·`LikedCursor`)는 유지한다(R8). 다만 현재 `findLikedSpots`에서 keyset 조건 문자열이 `likedWhereClause`의 결과에 이어 붙는 구조라 둘이 얽혀 있다. 분리 후 커서는 "커서를 (시각, id)로 푼다"까지만 하고, 그것을 조건으로 만드는 일은 조회 메서드가 한다.

## R9·R10 — 주석 정비

POL-033(신규)이 판정 기준이다. 주석은 대상이 무엇을 하는지 요약하고, 근거는 spec 문서로 링크한다.

주 대상은 KDoc 15줄 초과 블록 19개다. 주석 비율이 높은 순으로:

| 파일 | 주석/전체 | 성격 |
|---|---|---|
| `BatchInfrastructureConfig` | 50/62 (80%) | 배치 인프라 구성 근거 서술 |
| `FestivalStorePort` | 52/69 (75%) | 포트 계약 근거 서술 |
| `SpotDetailStorePort` | 54/87 (62%) | 동일 |
| `SpotQueryUseCase` | 48/79 (60%) | 동일 |
| `SpotQueryPort` | 73/140 (52%) | 동일 |

포트 인터페이스가 상위를 차지하는 것은 우연이 아니다. 포트는 계약이라 "왜 이렇게 정했는가"를 적고 싶어지는 자리인데, 그 근거야말로 spec에 있어야 하는 내용이다. 포트 주석은 각 메서드가 무엇을 반환하는지 한 줄로 줄이고, 근거는 해당 SPEC 문서를 가리킨다.

배치 인프라(`SpotSyncTasklet` 등)는 주석만 줄이고 로직은 건드리지 않는다.

스키마 설명은 계약 초안(`Specs/SPEC-0009/contract.graphql`)에 재작성안이 있다. 삭제 대상은 SPEC id, 외부 API 오퍼레이션명과 호출 수, 캐시·배치 전략, 채택하지 않은 대안의 근거다. 보존 대상은 클라이언트가 실제로 분기하는 의미론 — `gallery`·`infoItems`의 null과 빈 배열 구분, `overview`의 빈 문자열 정규화, HTML 정제 책임, 페이지네이션 기본값·상한(POL-018), 정렬 보장(POL-019)이다.

## R11·R12 — 동작 보존

계약 파일에서 설명문을 제외하고 비교했을 때 타입·필드·인자·기본값·nullability·enum 값이 동일해야 한다(R11-1). DGS 코드젠이 생성하는 Kotlin 타입 시그니처가 바뀌지 않으므로 클라이언트 재생성도 불필요하다.

기존 테스트 30개(auth 13, tourism 16, 아키텍처 1)는 수정 없이 통과해야 한다(R12-1). `SpotQueryServiceTest`(963줄)는 포트를 목킹하므로 어댑터 내부 변경의 영향을 받지 않아야 정상이다 — 이 테스트가 깨진다면 포트 시그니처를 바꿨다는 뜻이고, 그것은 이 spec의 범위를 벗어난 변경이다.

`LayeredArchitectureTest`는 `com.querydsl..`을 이미 허용 목록에 두고 있어 QueryDSL 사용 확대로 깨지지 않는다. 신설되는 `mapper` 패키지가 인프라 하위에 있는지만 확인하면 된다(R12-2).

## 알려진 한계

- 재작성 대상 4개 어댑터에 현재 테스트가 하나도 없고, 이 spec도 어댑터 테스트를 명세하지 않는다. 22개 쿼리의 동작 보존은 수용 기준으로 증명되지 않는다.
- PostGIS 쿼리와 UPSERT는 생 SQL로 남는다. "생 SQL 제거"는 완전한 제거가 아니라 격리와 축소다.
- 벌크 `@Modifying` 연산의 영속성 컨텍스트 동작 차이는 정적으로 확인하기 어렵다. 호출부 확인이 필요하다.
