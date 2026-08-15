package org.around.ieuri.tourism.application.port.out.query

import org.around.ieuri.tourism.application.query.FestivalView
import org.around.ieuri.tourism.domain.vo.Coordinates
import org.around.ieuri.tourism.domain.vo.DistanceCursor
import org.around.ieuri.tourism.domain.vo.DomainLocale
import java.time.LocalDate

/**
 * 진행 중 축제 조회 아웃바운드 포트(SPEC-0013 R17). `spot_festival`의 최초 읽기 경로다 —
 * 지금까지 이 테이블은 배치 쓰기와 정리 삭제에만 쓰였다.
 *
 * **날짜 필터는 조회 시점에 적용한다** — 배치의 종료 축제 정리에 기대지 않는다.
 * 배치가 실패해 종료된 축제가 남아 있어도 조회 결과는 R17을 지켜야 한다.
 */
interface FestivalQueryPort {
    /**
     * 반경 안에서 [asOf]가 행사 기간에 포함된 축제. 거리 오름차순 + id 오름차순.
     * **`limit + 1`건까지 돌려준다** — 초과분은 호출자의 다음 페이지 판정용이며 응답에 싣지 않는다.
     */
    fun findOngoing(query: OngoingFestivalSearch): List<FestivalView>
}

/**
 * 진행 중 축제 조회 조건. [asOf]는 서비스가 시계에서 읽어 넘긴 '오늘'이며 어댑터가 직접 읽지
 * 않는다 — 그래야 경계 판정을 단위 테스트로 고정할 수 있다(AC-17.1~17.3).
 *
 * 경계를 포함한다: `event_start_date <= asOf AND event_end_date >= asOf`.
 */
data class OngoingFestivalSearch(
    /**
     * 원문 보유 필터. **null이면 전체**, true면 그 언어 원문이 있는 것만, false면 번역본만.
     *
     * 클라이언트가 원문을 먼저 소진한 뒤 false로 넘어오는 흐름을 위한 값이다 — 서버가
     * 정렬로 끼워넣지 않는다. 정렬로 하면 거리·인기순을 요청했는데 다른 축이 앞서는
     * 숨은 규칙이 되고, 커서에도 그 축을 실어야 해서 형식이 깨진다.
     */
    val hasOrigin: Boolean?,
    val language: DomainLocale,
    val center: Coordinates,
    val radiusMeters: Int,
    val asOf: LocalDate,
    val limit: Int,
    val after: DistanceCursor?,
)
