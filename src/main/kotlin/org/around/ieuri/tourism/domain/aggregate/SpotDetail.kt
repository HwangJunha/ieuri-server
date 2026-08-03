package org.around.ieuri.tourism.domain.aggregate

import org.around.ieuri.tourism.domain.vo.CopyrightType
import org.around.ieuri.tourism.domain.vo.SpotCategory

/**
 * 관광지 상세 본문(도메인 모델). Spot의 확장이 아니라 상세 화면 전용 read shape다.
 *
 * **여기 담기는 건 detailCommon2가 주는 본문뿐이다.** 갤러리·운영정보·이용안내는 각각 별도 오퍼레이션이며
 * GraphQL 필드가 요청될 때만 조회되므로(필드별 지연 조회) 이 모델에 미리 담지 않는다 —
 * 담아 두면 안 쓰는 필드까지 항상 채워야 해서 지연 조회가 무의미해진다.
 *
 * [likeCount]도 어댑터가 채우지 않는다. application이 [withLikeCount]로 실시간 집계값을 얹는다(§5).
 * 기본값 0은 "아직 집계 전" 상태다.
 *
 * 순수 Kotlin — 프레임워크/JPA/DGS 의존 없음.
 */
data class SpotDetail(
    /**
     * 관광지 식별자(= `spot.id`). **어댑터가 채우지 않는다** — TourAPI는 자기 `contentId`만 알 뿐
     * 우리 PK를 모르고, DB 조회 경로도 조회 키를 그대로 돌려줄 뿐이다. application이 [withId]로
     * 요청받은 id를 얹는다. [likeCount]와 정확히 같은 패턴이다.
     *
     * 기본값 0은 "아직 식별자가 얹히지 않은" 상태다. nullable로 두지 않는 이유는 응답 시점에는
     * 항상 값이 있어야 하기 때문이다 — nullable이면 그 보장이 타입에서 사라지고 이 값을 읽는
     * 지연 필드 리졸버마다 null 처리를 강요한다.
     */
    val id: Long = 0,
    val title: String,
    val category: SpotCategory,
    val thumbnailImageUrl: String,
    val overview: String?,
    val address: String?,
    val homepage: String?,
    val tel: String?,
    val copyright: CopyrightType,
    val likeCount: Long = 0,
) {
    init {
        require(id >= 0) { "id는 음수일 수 없습니다." }
        require(title.isNotBlank()) { "title은 비어 있을 수 없습니다." }
        require(thumbnailImageUrl.startsWith("https://")) { "썸네일 URL은 https로 정규화되어야 합니다." }
        require(likeCount >= 0) { "좋아요 수는 음수일 수 없습니다." }
    }

    /** 항목 저작권 표준 문구(전문). copyright 유형에서 도출(별도 저장 없음). */
    val attributionText: String get() = copyright.attributionText

    /** 실시간 집계한 좋아요 수를 얹은 복제본. TourAPI 파생 필드는 캐시 대상이나 likeCount는 캐시하지 않는다(§5). */
    fun withLikeCount(likeCount: Long): SpotDetail = copy(likeCount = likeCount)

    /**
     * 식별자를 얹은 복제본. 어댑터가 만든 본문에 application이 요청받은 `spot.id`를 결합한다.
     * **캐시에는 id 없이 저장한다** — 값이 키에 종속되면 같은 본문을 재사용할 수 없다.
     */
    fun withId(id: Long): SpotDetail = copy(id = id)

    companion object {
        /** 갤러리 최대 장수(AC-8). 갤러리는 별도 조회지만 상한은 상세의 계약이라 여기 둔다. */
        const val MAX_GALLERY_SIZE = 10
    }
}
