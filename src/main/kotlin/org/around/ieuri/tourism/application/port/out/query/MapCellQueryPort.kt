package org.around.ieuri.tourism.application.port.out.query

import org.around.ieuri.tourism.application.query.CellSpotView
import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.around.ieuri.tourism.domain.vo.MapCellDistrictRow

/**
 * 전국 지도 칸의 읽기 포트(SPEC-0030).
 *
 * `district`·`spot_catalog`의 공간 컬럼이 JPA에 매핑되어 있지 않아 구현은 생 SQL이다
 * ([org.around.ieuri.tourism.infrastructure.adapter.out.query.MapCellQueryJdbcAdapter]).
 */
interface MapCellQueryPort {

    /**
     * 칸을 이루는 시군구 전체(252행). 칸 접기는 도메인이 한다
     * ([org.around.ieuri.tourism.domain.vo.MapCellGrouping]).
     *
     * 161칸이 고정이고 전량을 클라이언트가 필요로 하므로 페이지네이션하지 않는다(POL-017 예외).
     */
    fun findAllCellDistricts(): List<MapCellDistrictRow>

    /** 칸 1개를 이루는 시군구. 없는 칸이면 빈 목록이다. */
    fun findCellDistricts(cellId: Long): List<MapCellDistrictRow>

    /**
     * 여러 칸을 이루는 시군구를 **한 번에** 읽는다.
     *
     * 칸마다 [findCellDistricts]를 부르지 않는다 — 사진 동기화가 좌표 500건을 보내면 한 번에
     * 수십 칸이 방문으로 바뀌고, 그만큼 질의가 나간다.
     */
    fun findCellDistrictsIn(cellIds: Collection<Long>): List<MapCellDistrictRow>


    /**
     * 칸에 속한 장소(R11). 좋아요 수 내림차순, 같은 수는 id 오름차순이다.
     *
     * **페이지네이션이 없다** — 상위 [limit]건만 읽는다.
     */
    fun findCellSpots(
        cellId: Long,
        language: DomainLocale,
        limit: Int,
    ): List<CellSpotView>

}

/**
 * 시군구 → 칸 대응 포트(SPEC-0030).
 *
 * **[MapCellQueryPort]와 나눈 이유는 구현 수단이 다르기 때문이다.** 저쪽은 `boundary`·`center`
 * 같은 PostGIS 컬럼을 읽어 생 SQL이어야 하지만, 이 대응은 매핑 표를 그대로 읽는 것이라
 * 엔티티로 충분하다. 한 포트로 묶으면 어느 쪽이 왜 생 SQL인지가 어댑터 안에 숨는다.
 */
interface MapCellDistrictQueryPort {

    /**
     * 시군구 코드 → 그 코드가 속한 칸 id.
     *
     * **중복이 제거되고 칸 id 오름차순으로 정렬된다** — 광역시 칸은 시군구 여러 개가 같은
     * 칸을 가리키므로 중복이 실제로 발생하고, 순서가 호출마다 달라지면 공유 지도의 목록을
     * 클라이언트가 비교할 수 없다. 없는 코드는 결과에서 빠진다.
     */
    fun findCellIdsByDistrictCodes(districtCodes: Collection<String>): List<Long>
}
