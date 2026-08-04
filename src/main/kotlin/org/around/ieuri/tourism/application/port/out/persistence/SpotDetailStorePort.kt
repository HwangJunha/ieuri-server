package org.around.ieuri.tourism.application.port.out.persistence

import org.around.ieuri.tourism.domain.aggregate.SpotDetail
import org.around.ieuri.tourism.domain.vo.SpotCategory
import org.around.ieuri.tourism.domain.vo.SpotImage
import org.around.ieuri.tourism.domain.vo.SpotInfoItem
import org.around.ieuri.tourism.domain.vo.SpotIntro
import java.time.Instant

/**
 * 관광지 상세의 DB 캐시 계층 포트. 조회가 미스일 때 그 자리에서 채우므로 읽기와 쓰기가 한 포트에
 * 있으며, 오퍼레이션(본문·갤러리·운영정보·이용안내) 단위로 나눠 필드별 지연 조회를 깨지 않는다.
 *
 * 조회 메서드는 **미스와 "값이 없음"을 구분**한다 — 값이 정상적으로 비어 있는 경우를 미스로 취급하면
 * 매 조회마다 upstream을 두드린다. 목록형은 nullable을, intro는 래퍼를 쓴다.
 */
interface SpotDetailStorePort {
    /**
     * 상세 본문. 미적재면 null, 적재됐으면 [StoredDetail]. 부재 행도 값을 가지므로 "본문 없음"은
     * 값의 null 여부가 아니라 [StoredDetail.bodyAbsent]로 판별한다.
     */
    fun findDetail(spotId: Long): StoredDetail?

    /** 본문이 존재하는 경우의 적재. */
    fun saveDetail(spotId: Long, detail: SpotDetail)

    /**
     * 본문 부재의 적재. NOT NULL 컬럼을 채울 [fallback] 값(`spot` 행에서 온다)을 함께 받는다.
     * 이미 부재 행이 있으면 `fetched_at`을 갱신해 다음 신선도 창을 새로 시작한다.
     */
    fun saveDetailAbsence(spotId: Long, fallback: SpotDetail)

    /**
     * 갤러리. 미적재면 null. **빈 갤러리는 미적재와 구분하지 않으므로** 이미지가 0장인 spot은
     * 조회마다 upstream에 한 번 더 묻는다.
     */
    fun findGallery(spotId: Long): List<SpotImage>?
    fun saveGallery(spotId: Long, gallery: List<SpotImage>)

    /**
     * 운영 정보. 미적재면 null, 적재됐으나 값이 없으면 `StoredIntro(null)`.
     * intro는 정상적으로 null일 수 있어 래퍼 없이는 두 상태를 구분할 수 없다.
     */
    fun findIntro(spotId: Long, category: SpotCategory): StoredIntro?
    fun saveIntro(spotId: Long, category: SpotCategory, intro: SpotIntro?)

    /** 이용안내. 미적재면 null. 빈 목록은 갤러리와 같은 이유로 미적재와 구분하지 않는다. */
    fun findInfoItems(spotId: Long, category: SpotCategory): List<SpotInfoItem>?
    fun saveInfoItems(spotId: Long, category: SpotCategory, items: List<SpotInfoItem>)
}

/** 적재 여부와 "값이 null임"을 구분하기 위한 래퍼(intro 전용). */
data class StoredIntro(val value: SpotIntro?)

/**
 * 적재된 상세 본문 1건.
 *
 * [bodyAbsent]가 true면 업스트림에 본문이 없음을 확인한 행이며, [detail]의 값들은 `spot` 행에서
 * 빌려온 것이다(overview·homepage·tel은 null). false면 업스트림 본문 그대로다.
 * 부재 행은 [fetchedAt]이 신선도 임계값을 넘기면 재조회된다.
 */
data class StoredDetail(
    val detail: SpotDetail,
    val bodyAbsent: Boolean,
    val fetchedAt: Instant,
)
