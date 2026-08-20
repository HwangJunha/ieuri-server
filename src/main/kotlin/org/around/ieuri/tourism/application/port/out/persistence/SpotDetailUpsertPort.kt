package org.around.ieuri.tourism.application.port.out.persistence

import org.around.ieuri.tourism.domain.aggregate.SpotDetail
import org.around.ieuri.tourism.domain.vo.SpotCategory
import org.around.ieuri.tourism.domain.vo.SpotImage
import org.around.ieuri.tourism.domain.vo.SpotInfoItem
import org.around.ieuri.tourism.domain.vo.SpotIntro

/**
 * 관광지 상세의 DB 캐시 **쓰기** 포트. 쓰기가 전부 `INSERT ... ON CONFLICT`라 **생 SQL 어댑터가
 * 이 포트 뒤에 있고**, 그래서 [SpotDetailStorePort](QueryDSL 읽기)와 갈라져 있다.
 *
 * 포트를 따로 두는 이유는 `infrastructure → infrastructure` 주입을 막기 위해서다 — 어댑터가 다른
 * 어댑터를 주입받으면 "어느 경로가 생 SQL을 쓰는가"가 애플리케이션에서 보이지 않는다.
 *
 * 오퍼레이션(본문·갤러리·운영정보·이용안내) 단위로 나뉜 것은 읽기와 같다 — 필드별 지연 조회를
 * 깨지 않기 위해서다.
 */
interface SpotDetailUpsertPort {
    /** 본문이 존재하는 경우의 적재. */
    fun saveDetail(spotId: Long, detail: SpotDetail)

    /**
     * 본문 부재의 적재. NOT NULL 컬럼을 채울 [fallback] 값(`spot` 행에서 온다)을 함께 받는다.
     * 이미 부재 행이 있으면 `fetched_at`을 갱신해 다음 신선도 창을 새로 시작한다.
     */
    fun saveDetailAbsence(spotId: Long, fallback: SpotDetail)

    /** 갤러리 전량 교체. 상류가 이미지를 지우거나 순서를 바꿨을 때 낡은 행이 남지 않는다. */
    fun saveGallery(spotId: Long, gallery: List<SpotImage>)

    /** 운영 정보 적재. **값이 하나도 없어도 행을 남긴다** — 미적재와 구분해야 한다. */
    fun saveIntro(spotId: Long, category: SpotCategory, intro: SpotIntro?)

    /** 이용안내 전량 교체. 갤러리와 같은 규율이다. */
    fun saveInfoItems(spotId: Long, category: SpotCategory, items: List<SpotInfoItem>)
}
