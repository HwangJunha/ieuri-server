package org.around.ieuri.tourism.application.footprint.provided.dto

import org.around.ieuri.tourism.domain.footprint.CellImageKind
import org.around.ieuri.tourism.domain.shared.Coordinates
import org.around.ieuri.tourism.domain.shared.CopyrightType

/** 전국 지도 칸 1개(SPEC-0030). [name]·[provinceName]은 이미 활성 언어로 해소된 값이다. */
data class MapCellView(
    val id: Long,
    val districtCodes: List<String>,
    val name: String,
    val provinceName: String,
    val provinceCode: String,
    val center: Coordinates,
    val viewerVisited: Boolean,
)

/** 칸의 대표 이미지. [attributionText]는 [copyright]에서 파생된다. */
data class CellImageView(
    val imageUrl: String,
    val thumbnailUrl: String?,
    val caption: String,
    val kind: CellImageKind,
    val copyright: CopyrightType,
    val attributionText: String,
)

/** 칸에 속한 장소 1건. [likeCount]는 정렬 근거이며 활성 언어 기준이다. */
data class CellSpotView(
    val id: Long,
    val title: String,
    val thumbnailImageUrl: String,
    val categoryCode: String,
    val coordinates: Coordinates,
    val likeCount: Long,
)

/** 공유 지도 조회 결과. */
data class SharedMapView(
    val ownerNickname: String,
    val visitedCellIds: List<Long>,
    /** 비인증 조회면 null이다 — 빈 목록(겹치는 칸 없음)과 구분된다. */
    val overlappingCellIds: List<Long>?,
    val visitedCount: Int,
)

/** 발급된 공유 링크. 토큰이 아니라 서버가 조립한 완성 URL이다. */
data class MapShareLinkView(
    val shareUrl: String,
    val createdAt: java.time.Instant,
)
