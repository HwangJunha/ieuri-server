package org.around.ieuri.tourism.application.spot.provided

import org.around.ieuri.tourism.domain.shared.SpotCategory
import org.around.ieuri.tourism.domain.spot.SpotDetail
import org.around.ieuri.tourism.domain.spot.SpotImage
import org.around.ieuri.tourism.domain.spot.SpotInfoItem
import org.around.ieuri.tourism.domain.spot.SpotIntro

/** 원본 관광지의 상세 적재(SPEC-0004 R-14·SPEC-0037). 조회 경로의 write-behind와 사전 수집이 부른다. 갈래마다 한 트랜잭션이다. */
interface SpotCommandUseCase {
    /** 본문이 존재하는 경우의 적재. */
    fun saveDetail(spotId: Long, detail: SpotDetail)

    /** 본문 부재의 적재. NOT NULL 컬럼을 채울 [absent](원본 값으로 만든 상세)를 받는다(SPEC-0008 R-3). */
    fun saveDetailAbsence(spotId: Long, absent: SpotDetail)

    /** 갤러리 전량 교체. */
    fun saveGallery(spotId: Long, gallery: List<SpotImage>)

    /** 운영 정보 적재. 값이 하나도 없어도 행을 남긴다. */
    fun saveIntro(spotId: Long, category: SpotCategory, intro: SpotIntro?)

    /** 이용안내 전량 교체. */
    fun saveInfoItems(spotId: Long, category: SpotCategory, items: List<SpotInfoItem>)
}
