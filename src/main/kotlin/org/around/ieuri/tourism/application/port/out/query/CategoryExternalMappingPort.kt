package org.around.ieuri.tourism.application.port.out.query

import org.around.ieuri.tourism.domain.vo.SpotCategory

/**
 * 우리 카테고리 ↔ 외부 제공자 코드 조회(SPEC-0020). 테이블에서 읽는다.
 * - 매핑은 자주 바뀌지 않으므로 구현이 캐시해도 된다.
 */
interface CategoryExternalMappingPort {
    /** 이 카테고리의 외부 코드. 대응 코드가 없으면 null이며, 호출자는 해당 upstream 호출을 생략한다. */
    fun externalCode(category: SpotCategory, provider: String, codeset: String): String?

    /** 외부 코드가 가리키는 우리 카테고리. 모르는 코드면 null이며, 호출자가 폴백을 정한다. */
    fun categoryOf(externalCode: String, provider: String, codeset: String): SpotCategory?

    companion object {
        const val PROVIDER_TOUR_API = "TOUR_API"
    }
}
