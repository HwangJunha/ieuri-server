package org.around.ieuri.tourism.application.port.out.query

import org.around.ieuri.tourism.domain.vo.SpotCategory

/**
 * 우리 카테고리 ↔ 외부 제공자 코드 조회(SPEC-0020, V59). TourAPI의 contentTypeId가 첫 사용처다.
 *
 * 설정이 아니라 테이블에서 읽는 이유는 신규 카테고리의 **수집**까지 배포 없이 되게 하려는
 * 것이다 — 설정에 있으면 저장·조회는 데이터로 되는데 수집만 배포가 필요한 반쪽이 된다.
 *
 * 매핑은 자주 바뀌지 않으므로 구현이 캐시해도 된다.
 */
interface CategoryExternalMappingPort {
    /**
     * 이 카테고리의 외부 코드. **없으면 null이다** — 그 코드셋에 대응 코드가 없는 카테고리가
     * 실제로 있다(여행코스는 외국어 서비스에, 교통은 국문 서비스에 없다). 호출자는 null을
     * 보고 해당 upstream 호출을 생략한다.
     */
    fun externalCode(category: SpotCategory, provider: String, codeset: String): String?

    /** 외부 코드가 가리키는 우리 카테고리. 모르는 코드면 null이며, 호출자가 폴백을 정한다. */
    fun categoryOf(externalCode: String, provider: String, codeset: String): SpotCategory?

    companion object {
        const val PROVIDER_TOUR_API = "TOUR_API"
    }
}
