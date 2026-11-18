package org.around.ieuri.tourism.adapter.cache

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import org.around.ieuri.tourism.domain.category.SpotCategory
import org.around.ieuri.tourism.domain.shared.CopyrightType
import org.around.ieuri.tourism.domain.spot.SpotDetail
/**
 * 상세 캐시 직렬화 전용 DTO(어댑터 내부). 미정의 필드는 무시한다.
 * - attributionText(파생값)는 담지 않는다. 옛 항목의 `bodyAbsent`는 무시된다. 갤러리 등은 각자 자기 키에 저장된다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class CachedSpotDetail(
    val title: String = "",
    /** 카테고리 코드 문자열 — 값 클래스는 Jackson 왕복이 보장되지 않는다. */
    val category: String = SpotCategory.TOURIST_SPOT.code,
    val thumbnailImageUrl: String = "",
    val overview: String? = null,
    val address: String? = null,
    val homepage: String? = null,
    val tel: String? = null,
    val copyright: CopyrightType = CopyrightType.UNSPECIFIED,
) {
    fun toDomain(): SpotDetail = SpotDetail(
        title = title,
        category = SpotCategory(category),
        thumbnailImageUrl = thumbnailImageUrl,
        overview = overview,
        address = address,
        homepage = homepage,
        tel = tel,
        copyright = copyright,
    )

    companion object {
        fun from(detail: SpotDetail): CachedSpotDetail = CachedSpotDetail(
            title = detail.title,
            category = detail.category.code,
            thumbnailImageUrl = detail.thumbnailImageUrl,
            overview = detail.overview,
            address = detail.address,
            homepage = detail.homepage,
            tel = detail.tel,
            copyright = detail.copyright,
        )
    }
}
