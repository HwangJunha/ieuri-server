package org.around.ieuri.tourism.application.query

import org.around.ieuri.tourism.domain.vo.CellImageKind
import org.around.ieuri.tourism.domain.vo.Coordinates
import org.around.ieuri.tourism.domain.vo.CopyrightType

/**
 * 전국 지도 칸 1개의 조회 결과(SPEC-0030 R7·R8).
 *
 * [name]·[provinceName]은 **이미 활성 언어로 해소된 값이다** — 4개 언어 원본은 도메인
 * [org.around.ieuri.tourism.domain.vo.AreaName]이 갖고 있고 서비스가 폴백까지 마친 뒤 여기 담는다.
 *
 * [districtCodes]를 함께 내보내는 이유는 클라이언트가 좌표 판정 결과(시군구 코드)를 칸에
 * 대응시켜야 하기 때문이다.
 */
data class MapCellView(
    val id: Long,
    val districtCodes: List<String>,
    val name: String,
    val provinceName: String,
    val provinceCode: String,
    val center: Coordinates,
    val viewerVisited: Boolean,
)

/**
 * 칸의 대표 이미지(R9).
 *
 * [attributionText]는 [copyright]에서 파생된다 — 적재하지 않는 이유이기도 하다(둘이 어긋날 수 있다).
 */
data class CellImageView(
    val imageUrl: String,
    val thumbnailUrl: String?,
    val caption: String,
    val kind: CellImageKind,
    val copyright: CopyrightType,
    val attributionText: String,
)


/** 칸에 속한 장소 목록 조회 입력(R11). */
data class CellSpotsQuery(
    val cellId: Long,
    val first: Int,
    val after: String?,
)

/**
 * 칸에 속한 장소 1건.
 *
 * [likeCount]는 **정렬 근거라 노드가 아니라 엣지의 값이다** — 순위와 무관한 다른 조회에서는
 * 같은 장소에 대해 의미가 없다(`popularSpots`의 선례).
 */
data class CellSpotView(
    val id: Long,
    val title: String,
    val thumbnailImageUrl: String,
    val categoryCode: String,
    val coordinates: Coordinates,
    val likeCount: Long,
)

/** 공유 지도 조회 결과(R13·R15). */
data class SharedMapView(
    val ownerNickname: String,
    val visitedCellIds: List<Long>,
    /** 비인증 조회면 null이다 — 빈 목록(겹치는 칸 없음)과 구분된다. */
    val overlappingCellIds: List<Long>?,
    val visitedCount: Int,
)

/**
 * 발급된 공유 링크(R12).
 *
 * **토큰이 아니라 완성된 URL을 담는다.** 링크 형태(커스텀 스킴 → 웹 URL)가 바뀔 예정이고,
 * 서버가 조립하면 그 전환에 앱 배포가 필요 없다.
 */
data class MapShareLinkView(
    val shareUrl: String,
    val createdAt: java.time.Instant,
)
