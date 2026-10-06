package org.around.ieuri.tourism.domain.visit

/**
 * 회원이 방문으로 기록하려는 시군구 코드(SPEC-0030 R3). 좌표·시각은 담지 않는다(R5, POL-045).
 * - 빈 코드는 버리고 중복은 하나로 접는다(처음 나온 순서 유지).
 */
data class DistrictVisits(
    val memberId: Long,
    val districtCodes: List<String>,
) {
    init {
        require(memberId > 0) { "memberId는 양수여야 합니다." }
    }

    val isEmpty: Boolean get() = districtCodes.isEmpty()

    /** 이 중 [existing]에 있는 코드만 남긴다. 없는 시군구는 기록하지 않는다. */
    fun keepOnly(existing: Set<String>): DistrictVisits = copy(districtCodes = districtCodes.filter { it in existing })

    companion object {
        fun of(memberId: Long, rawCodes: List<String>): DistrictVisits = DistrictVisits(memberId, rawCodes.filter { it.isNotBlank() }.distinct())
    }
}
