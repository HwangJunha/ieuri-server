package org.around.ieuri.tourism.application.spot.provided.dto

import org.around.ieuri.tourism.domain.category.SpotCategory
import org.around.ieuri.tourism.domain.shared.CopyrightType

/** spotDetail 본문(SPEC-0003). 갤러리·운영정보·이용안내·좋아요 수는 필드별로 따로 조회된다. */
data class SpotDetailView(
    val id: Long,
    val title: String,
    val category: SpotCategory,
    val thumbnailImageUrl: String,
    val overview: String?,
    val address: String?,
    val homepage: String?,
    val tel: String?,
    val copyright: CopyrightType,
    val attributionText: String,
    /** 경도. 본문 캐시에 담기지 않고 뷰 조립 시점에 카탈로그에서 얹힌다. */
    val mapX: Double,
    /** 위도. 출처는 [mapX]와 같다. */
    val mapY: Double,
)

/** 운영 정보. 카테고리별 TourAPI 필드를 공통 이름으로 정규화한 결과다. */
data class SpotIntroView(
    val businessHours: String?,
    val closedDays: String?,
    val parking: String?,
    val inquiryPhone: String?,
    val fee: String?,
    val petAllowed: String?,
    val creditCardAccepted: String?,
)

/** 이용안내 1건. */
data class SpotInfoItemView(
    val name: String,
    val text: String,
)

/** 갤러리 이미지 1장. [attributionText]는 copyright에서 도출된 값이다. */
data class SpotImageView(
    val imageUrl: String,
    val thumbnailUrl: String?,
    val caption: String?,
    val copyright: CopyrightType,
    val attributionText: String,
)
