package org.around.ieuri.tourism.application.shared.provided

import org.around.ieuri.tourism.domain.shared.SpotCategory

/**
 * 우리 카테고리 ↔ 외부 제공자 코드(SPEC-0020). TourAPI 클라이언트가 요청을 만들고 응답을 읽으며 부른다.
 * - 호출마다 자기 트랜잭션이다 — 사전 수집이 코루틴 스레드에서 클라이언트를 불러 호출자 트랜잭션이 없다.
 */
interface CategoryMappingQueryUseCase {
    /** 이 카테고리의 외부 코드. 대응 코드가 없으면 null이며, 호출자는 해당 upstream 호출을 생략한다. */
    fun externalCode(category: SpotCategory, provider: String, codeset: String): String?

    /** 외부 코드가 가리키는 우리 카테고리. 모르는 코드면 null이며, 호출자가 폴백을 정한다. */
    fun categoryOf(externalCode: String, provider: String, codeset: String): SpotCategory?
}
