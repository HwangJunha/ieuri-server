package org.around.ieuri.tourism.application.footprint.required

/** 시군구 → 칸 대응 포트(SPEC-0030). 공간 컬럼을 읽지 않아 [MapCellQueryPort]와 나뉜다. */
interface MapCellDistrictQueryPort {

    /**
     * 시군구 코드 → 그 코드가 속한 칸 id.
     * - 중복이 제거되고 칸 id 오름차순이다. 없는 코드는 결과에서 빠진다.
     */
    fun findCellIdsByDistrictCodes(districtCodes: Collection<String>): List<Long>
}
