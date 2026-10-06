package org.around.ieuri.tourism.adapter.client.tourapi

import org.around.ieuri.tourism.adapter.config.TourApiProperties
import org.around.ieuri.tourism.application.shared.required.CategoryExternalMappingPort
import org.around.ieuri.tourism.domain.shared.SpotCategory

/**
 * TourAPI contentTypeId 매핑의 테스트 대역(SPEC-0020, V59). `category_external_mapping` 표를 DB 없이 고정한다.
 * - 코드셋마다 빠진 칸(여행코스는 외국어, 교통은 국문)을 표에서 빼지 말 것 — "코드 없으면 호출 생략"의 근거다.
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
