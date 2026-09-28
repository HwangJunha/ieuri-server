package org.around.ieuri.tourism.application.port.out.persistence

import org.around.ieuri.tourism.domain.aggregate.SpotDetail
import org.around.ieuri.tourism.domain.vo.SpotCategory
import org.around.ieuri.tourism.domain.vo.SpotImage
import org.around.ieuri.tourism.domain.vo.SpotInfoItem
import org.around.ieuri.tourism.domain.vo.SpotIntro

/** 관광지 상세의 DB 캐시 쓰기 포트. 전부 `INSERT ... ON CONFLICT`라 생 SQL 어댑터가 구현한다. */
interface SpotDetailUpsertPort {
    /** 본문이 존재하는 경우의 적재. */
    fun saveDetail(spotId: Long, detail: SpotDetail)

    /**
     * 본문 부재의 적재. NOT NULL 컬럼을 채울 [fallback] 값(`spot` 행에서 온다)을 함께 받는다.
     * - 이미 부재 행이 있으면 `fetched_at`을 갱신한다.
     */
    fun saveDetailAbsence(spotId: Long, fallback: SpotDetail)

    /** 갤러리 전량 교체. */
    fun saveGallery(spotId: Long, gallery: List<SpotImage>)

    /** 운영 정보 적재. 값이 하나도 없어도 행을 남긴다. */
    fun saveIntro(spotId: Long, category: SpotCategory, intro: SpotIntro?)

    /** 이용안내 전량 교체. */
    fun saveInfoItems(spotId: Long, category: SpotCategory, items: List<SpotInfoItem>)
}
