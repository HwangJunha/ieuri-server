package org.around.ieuri.tourism.application.detail.provided

import org.around.ieuri.tourism.domain.detail.SpotDetail
import org.around.ieuri.tourism.domain.detail.SpotImage
import org.around.ieuri.tourism.domain.detail.SpotInfoItem
import org.around.ieuri.tourism.domain.detail.SpotIntro
import org.around.ieuri.tourism.domain.shared.SpotCategory

/** 상세 DB 캐시 적재(SPEC-0004 R-14). 조회 경로가 upstream에서 받은 값을 write-behind로 쌓는다. */
interface SpotDetailCommandUseCase {
    /** 본문이 존재하는 경우의 적재. */
    fun saveDetail(spotId: Long, detail: SpotDetail)

    /** 본문 부재의 적재. NOT NULL 컬럼을 채울 [fallback](`spot` 행의 값)을 함께 받는다(SPEC-0008 R-3). */
    fun saveDetailAbsence(spotId: Long, fallback: SpotDetail)

    /** 갤러리 전량 교체. */
    fun saveGallery(spotId: Long, gallery: List<SpotImage>)

    /** 운영 정보 적재. 값이 하나도 없어도 행을 남긴다. */
    fun saveIntro(spotId: Long, category: SpotCategory, intro: SpotIntro?)

    /** 이용안내 전량 교체. */
    fun saveInfoItems(spotId: Long, category: SpotCategory, items: List<SpotInfoItem>)
}
