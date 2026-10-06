package org.around.ieuri.tourism.adapter.cache

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import org.around.ieuri.tourism.domain.detail.SpotDetail
import org.around.ieuri.tourism.domain.detail.SpotImage
import org.around.ieuri.tourism.domain.detail.SpotInfoItem
import org.around.ieuri.tourism.domain.detail.SpotIntro
import org.around.ieuri.tourism.domain.shared.CopyrightType
import org.around.ieuri.tourism.domain.shared.SpotCategory

/**
 * 상세 캐시 직렬화 전용 DTO(어댑터 내부). 미정의 필드는 무시한다.
 * - attributionText(파생값)·likeCount(캐시 비대상)는 담지 않는다. 갤러리 등은 각자 자기 키에 저장된다.
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
    /**
     * 업스트림에 본문이 없음을 확인한 값인지(SPEC-0008 R-3). true면 overview·homepage·tel은 null이다.
     * - 기본값 false가 이 필드가 없던 옛 항목의 올바른 해석이다.
     */
    val bodyAbsent: Boolean = false,
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
        // likeCount는 캐시 비대상 — 복원 시 0(application이 덮어쓴다).
    )

    companion object {
        fun from(detail: SpotDetail, bodyAbsent: Boolean = false): CachedSpotDetail = CachedSpotDetail(
            title = detail.title,
            category = detail.category.code,
            thumbnailImageUrl = detail.thumbnailImageUrl,
            overview = detail.overview,
            address = detail.address,
            homepage = detail.homepage,
            tel = detail.tel,
            copyright = detail.copyright,
            bodyAbsent = bodyAbsent,
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
