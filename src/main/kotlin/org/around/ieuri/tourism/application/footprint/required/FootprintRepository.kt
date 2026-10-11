package org.around.ieuri.tourism.application.footprint.required

import org.around.ieuri.tourism.domain.footprint.Footprint
import org.around.ieuri.tourism.domain.footprint.ShareLink

/**
 * 발자취 저장소. 루트는 회원의 방문 시군구 전부와 유효한 공유 링크를 함께 읽는다.
 * - 시군구 코드 외에는 좌표도 시각도 저장하지 않는다(POL-045).
 */
interface FootprintRepository {

    /** 아무것도 남기지 않은 회원도 빈 발자취다(null이 아니다). */
    fun findByMemberId(memberId: Long): Footprint

    /** 존재하는 시군구 코드만 남긴다. */
    fun filterExistingDistrictCodes(districtCodes: Collection<String>): Set<String>

    /** 칸에 속한 시군구 코드(오름차순). 없는 칸이면 빈 목록이다. */
    fun findDistrictCodesOfCell(cellId: Long): List<String>

    /** 방문을 넣는다. 이미 있는 코드는 무시된다. 존재하는 시군구 코드여야 한다. */
    fun addVisits(memberId: Long, districtCodes: Collection<String>)

    fun removeVisits(memberId: Long, districtCodes: Collection<String>)

    /**
     * 링크를 저장하고 저장된 링크를 돌려준다. 이미 저장된 링크([ShareLink.issuedAt]이 있음)는 그대로 돌려준다.
     * - 같은 회원의 발급이 동시에 오면 먼저 저장된 것을 돌려준다 — 회원당 유효 링크는 하나다.
     */
    fun saveShareLink(memberId: Long, link: ShareLink): ShareLink

    /** 링크를 폐기한다. 그 사이 폐기됐으면 false다. */
    fun revokeShareLink(memberId: Long, link: ShareLink): Boolean

    /** 탈퇴 회원의 방문과 링크를 지우고 지운 건수를 돌려준다. */
    fun deleteAll(memberId: Long): Int
}
