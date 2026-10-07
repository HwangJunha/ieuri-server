package org.around.ieuri.tourism.domain.footprint

/**
 * 회원의 발자취 — 방문한 시군구와, 그 지도를 남에게 보여 주는 공유 링크(SPEC-0030).
 * - 방문은 시군구 수(252)로, 유효한 공유 링크는 회원당 하나로 묶여 있어 루트가 들고 있다.
 * - 시군구 코드 외에는 좌표도 시각도 남기지 않는다(R5, POL-045).
 */
data class Footprint(
    val memberId: Long,
    val visitedDistrictCodes: Set<String>,
    /** 폐기되지 않은 링크. 없으면 null이다. */
    val shareLink: ShareLink?,
) {
    init {
        require(memberId > 0) { "memberId는 양수여야 합니다." }
    }

    /**
     * 시군구를 방문으로 남긴다(R3). 빈 코드는 버리고 중복은 하나로 접는다(처음 나온 순서 유지).
     * - [knownDistrictCodes]에 없는 코드는 남기지 않고 [Visit.unknownDistrictCodes]로 돌려준다.
     * - 이미 방문한 코드는 [Visit.addedDistrictCodes]에 들지 않는다 — 오류가 아니다.
     */
    fun visit(districtCodes: List<String>, knownDistrictCodes: Set<String>): Visit {
        val requested = districtCodes.filter { it.isNotBlank() }.distinct()
        val (known, unknown) = requested.partition { it in knownDistrictCodes }
        val added = known.filterNot { it in visitedDistrictCodes }
        return Visit(
            footprint = copy(visitedDistrictCodes = visitedDistrictCodes + added),
            addedDistrictCodes = added,
            unknownDistrictCodes = unknown,
        )
    }

    /** 방문을 해제한다(R4). 방문하지 않은 코드가 섞여 있어도 오류가 아니다. 실제로 지운 코드를 돌려준다. */
    fun unvisit(districtCodes: Collection<String>): Unvisit {
        val removed = districtCodes.filter { it in visitedDistrictCodes }.toSet()
        return Unvisit(footprint = copy(visitedDistrictCodes = visitedDistrictCodes - removed), removedDistrictCodes = removed)
    }

    /** 공유한다(R12). 이미 유효한 링크가 있으면 같은 링크다 — 회원당 하나다. */
    fun share(): ShareLink = shareLink ?: ShareLink.issue()

    /** 공유를 거둔다(R14). 지금 유효한 링크의 토큰이 아니면(남의 것·이미 폐기) null이다. */
    fun revokeShare(token: String): ShareLink? = shareLink?.takeIf { it.token == token }

    data class Visit(
        val footprint: Footprint,
        val addedDistrictCodes: List<String>,
        val unknownDistrictCodes: List<String>,
    )

    data class Unvisit(
        val footprint: Footprint,
        val removedDistrictCodes: Set<String>,
    )
}
