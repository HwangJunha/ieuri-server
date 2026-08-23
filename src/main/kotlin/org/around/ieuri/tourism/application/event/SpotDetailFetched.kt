package org.around.ieuri.tourism.application.event

import org.around.ieuri.tourism.domain.aggregate.SpotDetail
import org.around.ieuri.tourism.domain.vo.SpotCategory
import org.around.ieuri.tourism.domain.vo.SpotImage
import org.around.ieuri.tourism.domain.vo.SpotInfoItem
import org.around.ieuri.tourism.domain.vo.SpotIntro

/**
 * upstream에서 상세를 가져왔을 때 발행되는 이벤트(SPEC-0004 R-14). 리스너가 이를 받아 DB에
 * 적재하며, **조회 응답은 적재를 기다리지 않는다**(AC-39).
 *
 * 오퍼레이션별로 이벤트를 나눈 것은 저장 포트와 같은 이유다 — 요청된 필드만 적재한다.
 * auth 도메인의 `VerificationEmailRequested`와 같은 관례를 따른다(POL-028: 이벤트는
 * Spring ApplicationEvent, Kafka 이관은 후속).
 */
data class SpotDetailFetched(
    val spotId: Long,
    val detail: SpotDetail,
)

/**
 * upstream에 상세 본문이 **없음을 확인했을 때** 발행되는 이벤트(SPEC-0008 R-3).
 *
 * [SpotDetailFetched]와 별도 이벤트인 이유는 적재 방식이 다르기 때문이다 — 부재는 본문 컬럼을
 * null로 두고 `body_absent`를 세우며, NOT NULL 컬럼은 [fallback](= `spot` 행의 값)으로 채운다.
 * 한 이벤트에 플래그로 담으면 리스너가 매번 분기해야 하고, "부재를 적재한다"는 사실이 타입에서
 * 사라진다.
 *
 * **실패 시에는 발행하지 않는다**(AC-16·AC-4). 장애는 부재의 재확인이 아니므로 신선도 창을
 * 연장해서는 안 된다 — 예외가 그대로 전파되어 이 지점에 도달하지 않는다.
 */
data class SpotDetailAbsent(
    val spotId: Long,
    val fallback: SpotDetail,
)

data class SpotGalleryFetched(
    val spotId: Long,
    val gallery: List<SpotImage>,
)

/** [intro]는 정상적으로 null일 수 있다 — "값 없음"도 적재해야 다음 조회가 미스로 오판하지 않는다. */
data class SpotIntroFetched(
    val spotId: Long,
    val category: SpotCategory,
    val intro: SpotIntro?,
)

data class SpotInfoItemsFetched(
    val spotId: Long,
    val category: SpotCategory,
    val items: List<SpotInfoItem>,
)
