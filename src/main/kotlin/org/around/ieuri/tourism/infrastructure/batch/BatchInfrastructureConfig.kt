package org.around.ieuri.tourism.infrastructure.batch

import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing
import org.springframework.batch.core.configuration.annotation.EnableJdbcJobRepository
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

/**
 * 배치 메타데이터 저장소를 **DB로 고정한다**(Spring Batch 6 / Boot 4).
 *
 * ## 왜 이 클래스가 필요한가
 *
 * Boot 3까지는 DataSource가 있으면 JobRepository가 자동으로 JDBC 기반이었다. **Boot 4 / Batch 6에서
 * 기본값이 [org.springframework.batch.core.repository.support.ResourcelessJobRepository]로 바뀌었다** —
 * 메타데이터를 쓰지 않는 잡에까지 인메모리 DB 의존성을 강제하던 것을 없애, DB 연결·트랜잭션 없이
 * 도는 일회성 잡의 성능과 메모리 사용을 개선하려는 변경이다.
 *
 * 합리적인 변경이지만 **우리 잡은 그 대상이 아니다.** 공식 문서가 `ResourcelessJobRepository`를
 * 쓰면 안 되는 경우로 못박은 조건 중 셋에 정면으로 걸린다:
 *
 *  1. **재시작이 필요한 경우** — AC-8이 실패 지점 재개를 요구한다. 언어별 Step 분리의 존재 이유다.
 *  2. **ExecutionContext를 쓰는 경우** — [SpotSyncTasklet]·[FestivalSyncTasklet]이 재개 지점
 *     (`nextPage`)과 관측값(`written`/`dropped`/`deleted`)을 전부 거기 저장한다.
 *  3. **Step 간 상태 공유** — 위와 같다.
 *
 * 즉 이 애플리케이션은 **애초부터 영속 메타데이터에 의존하도록 설계돼 있다.**
 *
 * ## 이것이 없으면 조용히 깨지는 것
 *
 * `ResourcelessJobRepository`는 JobExecution을 자바 객체 필드에 들고 있다가 프로세스와 함께 버린다.
 * 잡은 정상적으로 돌고 업무 데이터도 정상 적재되므로(그쪽은 [SpotJpaAdapter]의 별도 JdbcTemplate
 * 경로다) **실패가 눈에 보이지 않는다.** 실제로 그렇게 121,913건이 적재되는 동안 `BATCH_*` 테이블은
 * 한 줄도 쓰이지 않았고, 시퀀스 `last_value`가 전부 null이었다(2026-08-03 실측).
 *
 *  - **중복 실행 방지(SPEC-0004 AC-6)가 죽는다.** `runDate`를 식별 파라미터로 넣어 "같은 날짜 =
 *    같은 JobInstance"로 막는 구조인데, `ResourcelessJobRepository.isJobInstanceExists()`는
 *    `false`를 하드코딩한다 — [SpotSyncLauncher]가 잡는 `JobInstanceAlreadyCompleteException`이
 *    **발생할 수 없다.** 매 트리거가 전량을 다시 수집하며, 이는 TourAPI 호출 한도를 그대로 소모한다.
 *  - **실패 지점 재개(AC-8)가 죽는다.** 완료된 언어 Step을 건너뛰려면 `BATCH_STEP_EXECUTION` 행이
 *    있어야 하는데, 매 실행이 빈 저장소로 시작하므로 항상 KO부터 다시 돈다.
 *
 * ## 왜 이 방식인가
 *
 * `@EnableJdbcJobRepository`는 **Batch 6에서 이 시나리오를 위해 새로 도입된 정식 경로다** — 옛 방식의
 * 우회가 아니라 마이그레이션 가이드가 지정한 방법이다. 암묵적 기본값이 사라졌을 뿐 JDBC 저장소는
 * 여전히 1급 시민이다.
 *
 * `dataSourceRef`·`transactionManagerRef`를 지정하지 않는다 — 기본값이 각각 `"dataSource"`·
 * `"transactionManager"`로 Boot가 만드는 빈 이름과 일치한다. 애플리케이션 DataSource를 그대로 쓰므로
 * 잡 메타데이터와 업무 데이터가 한 트랜잭션 경계를 공유한다.
 *
 * 메타 테이블 스키마는 **Flyway가 소유한다**(V14). Batch가 자체 초기화하면 그 DDL이
 * `flyway_schema_history`에 남지 않아 스키마 형상 관리가 이원화된다.
 *
 * **batch 프로파일에서만 로드된다** — API 서버 프로세스는 잡을 실행하지 않으므로 JobRepository도
 * 필요 없다.
 */
@Configuration
@Profile(BATCH_PROFILE)
@EnableBatchProcessing
@EnableJdbcJobRepository
class BatchInfrastructureConfig
