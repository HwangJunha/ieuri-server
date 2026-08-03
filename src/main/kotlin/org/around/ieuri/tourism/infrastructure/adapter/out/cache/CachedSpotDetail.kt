package org.around.ieuri.tourism.infrastructure.adapter.out.cache

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import org.around.ieuri.tourism.domain.aggregate.SpotDetail
import org.around.ieuri.tourism.domain.vo.CopyrightType
import org.around.ieuri.tourism.domain.vo.SpotCategory
import org.around.ieuri.tourism.domain.vo.SpotImage
import org.around.ieuri.tourism.domain.vo.SpotInfoItem
import org.around.ieuri.tourism.domain.vo.SpotIntro

/**
 * 상세 캐시 직렬화 전용 DTO(어댑터 내부). 도메인 모델을 Jackson으로 직접 굽지 않는 이유:
 *  - 도메인 순수성: 도메인 타입은 Jackson 애노테이션을 모른다.
 *  - 파생 필드 배제: attributionText는 copyright에서 도출되는 계산값이라 저장할 필요가 없다.
 *  - likeCount 배제: 캐시 경계상 담지 않는다(§4). 복원 시 domain 기본값 0.
 *
 * 오퍼레이션별로 따로 캐시되므로 여기 갤러리·운영정보·이용안내를 중첩하지 않는다(각자 자기 키에 저장).
 * 스키마 변화에 견디도록 미정의 필드는 무시한다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class CachedSpotDetail(
    val id: String = "",
    val title: String = "",
    val category: SpotCategory = SpotCategory.TOURIST_SPOT,
    val thumbnailImageUrl: String = "",
    val overview: String? = null,
    val address: String? = null,
    val homepage: String? = null,
    val tel: String? = null,
    val copyright: CopyrightType = CopyrightType.UNSPECIFIED,
) {
    fun toDomain(): SpotDetail = SpotDetail(
        title = title,
        category = category,
        thumbnailImageUrl = thumbnailImageUrl,
        overview = overview,
        address = address,
        homepage = homepage,
        tel = tel,
        copyright = copyright,
        // likeCount는 캐시 비대상 — 복원 시 0(application이 실시간 집계로 덮어씀).
    )

    companion object {
        fun from(detail: SpotDetail): CachedSpotDetail = CachedSpotDetail(
            title = detail.title,
            category = detail.category,
            thumbnailImageUrl = detail.thumbnailImageUrl,
            overview = detail.overview,
            address = detail.address,
            homepage = detail.homepage,
            tel = detail.tel,
            copyright = detail.copyright,
        )
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class CachedSpotImage(
    val imageUrl: String = "",
    val thumbnailUrl: String? = null,
    val caption: String? = null,
    val copyright: CopyrightType = CopyrightType.UNSPECIFIED,
) {
    fun toDomain(): SpotImage = SpotImage(
        imageUrl = imageUrl,
        thumbnailUrl = thumbnailUrl,
        caption = caption,
        copyright = copyright,
    )

    companion object {
        fun from(image: SpotImage): CachedSpotImage = CachedSpotImage(
            imageUrl = image.imageUrl,
            thumbnailUrl = image.thumbnailUrl,
            caption = image.caption,
            copyright = image.copyright,
        )
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class CachedSpotIntro(
    val businessHours: String? = null,
    val closedDays: String? = null,
    val parking: String? = null,
    val inquiryPhone: String? = null,
    val fee: String? = null,
    val petAllowed: String? = null,
    val creditCardAccepted: String? = null,
) {
    fun toDomain(): SpotIntro = SpotIntro(
        businessHours = businessHours,
        closedDays = closedDays,
        parking = parking,
        inquiryPhone = inquiryPhone,
        fee = fee,
        petAllowed = petAllowed,
        creditCardAccepted = creditCardAccepted,
    )

    companion object {
        fun from(intro: SpotIntro): CachedSpotIntro = CachedSpotIntro(
            businessHours = intro.businessHours,
            closedDays = intro.closedDays,
            parking = intro.parking,
            inquiryPhone = intro.inquiryPhone,
            fee = intro.fee,
            petAllowed = intro.petAllowed,
            creditCardAccepted = intro.creditCardAccepted,
        )
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class CachedSpotInfoItem(
    val name: String = "",
    val text: String = "",
) {
    fun toDomain(): SpotInfoItem = SpotInfoItem(name = name, text = text)
}
