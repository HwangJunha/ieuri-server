package org.around.ieuri.tourism.application.port.out.persistence

import org.around.ieuri.tourism.domain.aggregate.SpotDetail
import org.around.ieuri.tourism.domain.vo.SpotCategory
import org.around.ieuri.tourism.domain.vo.SpotImage
import org.around.ieuri.tourism.domain.vo.SpotInfoItem
import org.around.ieuri.tourism.domain.vo.SpotIntro
import java.time.Instant

/**
 * 관광지 상세 적재·조회 아웃바운드 포트(SPEC-0004 R-13·R-14). **오퍼레이션 단위로 나눈다** —
 * Valkey 캐시 포트와 같은 이유다. 본문만 요청한 조회가 갤러리까지 적재하거나, 갤러리만 요청했는데
 * 통짜 조회가 미스 나는 상황을 피한다([TBD-7] = (b), 이슈 #25 확정).
 *
 * **읽기와 쓰기가 한 포트에 있는 이유.** 상세는 CQRS의 읽기 모델이 아니라 **캐시 계층**이다 —
 * 조회 경로가 미스일 때 그 자리에서 채우고, 다음 조회가 그것을 읽는다. 둘을 갈라 놓으면 같은
 * 테이블을 두 포트가 나눠 갖게 되어 오히려 응집도가 떨어진다.
 *
 * **신선도 임계값을 두지 않는다**([TBD-6] 결정). 관광지 상세는 잘 바뀌지 않고, 매일 배치가
 * 목록을 최신화하며 사라진 항목은 `spot` 삭제 시 FK CASCADE로 상세까지 함께 지워진다(V17).
 * 1차 신선도는 Valkey TTL(24h)이 담당한다. `fetched_at` 컬럼은 남겨 두되 조회 판정에는 쓰지 않고
 * 관측용으로만 둔다 — 나중에 임계값이 필요해지면 그때 조건만 얹으면 된다.
 *
 * 조회 메서드는 **미스와 "값이 없음"을 구분**해야 한다([SpotCachePort]와 동일한 문제) —
 * 값이 정상적으로 비어 있는 경우를 미스로 취급하면 매 조회마다 upstream을 두드리게 된다.
 * 그래서 목록형도 nullable을 돌려주고, intro는 래퍼로 감싼다.
 */
interface SpotDetailStorePort {
    /**
     * 상세 본문. **미적재면 null, 적재됐으면 [StoredDetail]**(SPEC-0008 R-3).
     *
     * 래퍼가 필요한 이유는 intro와 같다 — "조회했으나 본문이 없음"을 미적재로 오판하면 매 조회마다
     * upstream을 두드린다. 다만 intro와 달리 부재 행도 값을 갖는다(`spot`에서 빌린 title·category 등)
     * 므로, 값의 null 여부가 아니라 [StoredDetail.bodyAbsent]가 두 상태를 가른다.
     *
     * [StoredDetail.fetchedAt]은 신선도 판정(R-4)의 기준이다.
     */
    fun findDetail(spotId: Long): StoredDetail?

    /** 본문이 존재하는 경우의 적재. */
    fun saveDetail(spotId: Long, detail: SpotDetail)

    /**
     * **본문 부재의 적재**(R-3). 부재 사실과 함께, NOT NULL 컬럼을 채울 [fallback] 값을 받는다 —
     * 이 값들은 `spot` 행에서 온다(R-2).
     *
     * 이미 부재 행이 있으면 `fetched_at`을 갱신해 다음 신선도 창을 새로 시작한다(AC-15).
     */
    fun saveDetailAbsence(spotId: Long, fallback: SpotDetail)

    /**
     * 갤러리. 미적재면 null.
     * **빈 갤러리는 미적재와 구분하지 않는다** — 이미지가 0장이면 행이 없어 조회 시 null이 되고,
     * upstream에 한 번 더 묻는다. 빈 갤러리는 드물고 비용도 1콜이라, 빈 목록 전용 마커 행을 두는
     * 것보다 단순하다. intro는 값 없음이 흔해 래퍼로 구분한다(아래).
     */
    fun findGallery(spotId: Long): List<SpotImage>?
    fun saveGallery(spotId: Long, gallery: List<SpotImage>)

    /**
     * 운영 정보. 미적재면 null, **적재됐으나 값이 없으면 `StoredIntro(null)`**.
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
 * 적재된 상세 본문 1건(SPEC-0008 R-3·R-4).
 *
 * [bodyAbsent]가 true면 업스트림에 본문이 없음을 확인한 행이며, [detail]의 값들은 `spot` 행에서
 * 빌려온 것이다(overview·homepage·tel은 null). false면 업스트림 본문 그대로다.
 *
 * [fetchedAt]은 신선도 판정 기준이다 — 부재 행은 이 시각이 임계값을 넘기면 재조회된다(AC-13).
 */
data class StoredDetail(
    val detail: SpotDetail,
    val bodyAbsent: Boolean,
    val fetchedAt: Instant,
)
