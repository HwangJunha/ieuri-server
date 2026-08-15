package org.around.ieuri.tourism.infrastructure.adapter.out.persistence.mapper

import org.around.ieuri.tourism.domain.aggregate.SpotDetail
import org.around.ieuri.tourism.domain.vo.SpotCategory
import org.around.ieuri.tourism.domain.vo.SpotImage
import org.around.ieuri.tourism.domain.vo.SpotInfoItem
import org.around.ieuri.tourism.domain.vo.SpotIntro
import org.around.ieuri.tourism.infrastructure.adapter.out.persistence.entity.SpotDetailEntity
import org.around.ieuri.tourism.infrastructure.adapter.out.persistence.entity.SpotDetailImageEntity
import org.around.ieuri.tourism.infrastructure.adapter.out.persistence.entity.SpotDetailInfoEntity
import org.around.ieuri.tourism.infrastructure.adapter.out.persistence.entity.SpotDetailIntroEntity

/**
 * 적재된 본문을 도메인 [SpotDetail]로 옮긴다.
 *
 * `likeCount`는 적재 대상이 아니라 서비스가 실시간 집계로 얹으므로 여기서 채우지 않는다.
 */
fun SpotDetailEntity.toDomain(): SpotDetail =
    SpotDetail(
        title = title,
        category = SpotCategory(category),
        thumbnailImageUrl = thumbnailImageUrl,
        overview = overview,
        address = address,
        homepage = homepage,
        tel = tel,
        copyright = copyright,
    )

/** 적재된 갤러리 이미지를 도메인 [SpotImage]로 옮긴다. */
fun SpotDetailImageEntity.toDomain(): SpotImage =
    SpotImage(
        imageUrl = imageUrl,
        thumbnailUrl = thumbnailUrl,
        caption = caption,
        copyright = copyright,
    )

/** 적재된 운영 정보를 도메인 [SpotIntro]로 옮긴다. */
fun SpotDetailIntroEntity.toDomain(): SpotIntro =
    SpotIntro(
        businessHours = businessHours,
        closedDays = closedDays,
        parking = parking,
        inquiryPhone = inquiryPhone,
        fee = fee,
        petAllowed = petAllowed,
        creditCardAccepted = creditCardAccepted,
    )

/** 적재된 이용안내 항목을 도메인 [SpotInfoItem]으로 옮긴다. */
fun SpotDetailInfoEntity.toDomain(): SpotInfoItem =
    SpotInfoItem(name = name, text = text)
