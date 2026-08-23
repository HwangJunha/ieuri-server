package org.around.ieuri.tourism.infrastructure

import org.around.ieuri.tourism.application.port.out.query.CategoryExternalMappingPort
import org.around.ieuri.tourism.domain.vo.SpotCategory
import org.around.ieuri.tourism.infrastructure.config.TourApiProperties

/**
 * TourAPI contentTypeId 매핑의 테스트 대역(SPEC-0020, V59). 운영에서는 `category_external_mapping`
 * 테이블이 주지만, 클라이언트 매핑 테스트는 DB 없이 도는 게 규율이라 같은 표를 여기 고정한다.
 *
 * 표의 근거는 TourAPI 문서다 — 국문/외국어 서비스가 서로 다른 코드 체계를 쓰고, 양쪽에 모두
 * 있지는 않다(여행코스는 외국어에, 교통은 국문에 없다). 그 결손이 곧 "코드 없으면 호출 생략"
 * 동작의 근거이므로 표에서 빼지 말 것.
 */
class TourApiCategoryMappingStub : CategoryExternalMappingPort {

    private val codes: Map<String, Map<String, String>> = mapOf(
        TourApiProperties.CODESET_KO to mapOf(
            "TOURIST_SPOT" to "12",
            "CULTURAL_FACILITY" to "14",
            "FESTIVAL_EVENT" to "15",
            "TRAVEL_COURSE" to "25",
            "LEISURE_SPORTS" to "28",
            "LODGING" to "32",
            "SHOPPING" to "38",
            "RESTAURANT" to "39",
        ),
        TourApiProperties.CODESET_FOREIGN to mapOf(
            "TOURIST_SPOT" to "76",
            "CULTURAL_FACILITY" to "78",
            "FESTIVAL_EVENT" to "85",
            "LEISURE_SPORTS" to "75",
            "LODGING" to "80",
            "SHOPPING" to "79",
            "RESTAURANT" to "82",
            "TRANSPORT" to "77",
        ),
    )

    override fun externalCode(category: SpotCategory, provider: String, codeset: String): String? {
        require(provider == CategoryExternalMappingPort.PROVIDER_TOUR_API) { "예상치 못한 provider: $provider" }
        return codes[codeset]?.get(category.code)
    }

    override fun categoryOf(externalCode: String, provider: String, codeset: String): SpotCategory? {
        require(provider == CategoryExternalMappingPort.PROVIDER_TOUR_API) { "예상치 못한 provider: $provider" }
        return codes[codeset]?.entries?.firstOrNull { it.value == externalCode }?.let { SpotCategory(it.key) }
    }
}
