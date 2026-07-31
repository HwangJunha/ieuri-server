package org.around.ieuri.tourism.application.query

import org.around.ieuri.tourism.domain.vo.CopyrightType
import org.around.ieuri.tourism.domain.vo.SpotCategory

/**
 * spots 쿼리 입력. 언어는 인자가 아니라 Accept-Language에서 온다(요청 스코프 컨텍스트로 주입).
 * 좌표·반경 형식 검증은 Coordinates VO/서비스에서, 카테고리 가용성은 서비스에서 판정한다.
 */
data class SpotsQuery(
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Int,
    val category: SpotCategory?,
    val first: Int,
    val after: String?,
)

/**
 * spots 응답의 read DTO(카드 1장). 리졸버가 GraphQL Spot 타입으로 매핑한다(application은 DGS 무의존).
 */
data class SpotView(
    val id: String,
    val title: String,
    val address: String?,
    val thumbnailImageUrl: String,
    val distanceMeters: Double,
    val category: SpotCategory,
    val copyright: CopyrightType,
    val attributionText: String,
    val mapX: Double,
    val mapY: Double,
)
