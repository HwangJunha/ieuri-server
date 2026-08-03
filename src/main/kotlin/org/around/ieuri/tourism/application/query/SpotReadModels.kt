package org.around.ieuri.tourism.application.query

import org.around.ieuri.tourism.domain.vo.CopyrightType
import org.around.ieuri.tourism.domain.vo.SpotCategory

/**
 * spots 쿼리 입력. 언어는 인자가 아니라 Accept-Language에서 온다(요청 스코프 컨텍스트로 주입).
 * TourAPI가 pageNo 오프셋만 지원하므로 오프셋 페이지네이션([page] 1-base, [size]).
 * 좌표·반경 형식 검증은 Coordinates VO/서비스에서, 카테고리 가용성은 서비스에서 판정한다.
 */
data class SpotsQuery(
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Int,
    val category: SpotCategory?,
    val page: Int,
    val size: Int,
)

/**
 * spots 응답. [items]는 거리 오름차순 정렬된 한 페이지, [hasNext]로 소진 판정, [totalCount]는 필터-이전 참고값.
 */
data class SpotPageView(
    val items: List<SpotView>,
    val page: Int,
    val hasNext: Boolean,
    val totalCount: Int?,
)

/**
 * spots 응답의 read DTO(카드 1장). 리졸버가 GraphQL Spot 타입으로 매핑한다(application은 DGS 무의존).
 */
data class SpotView(
    val id: Long,
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

/**
 * spotDetail 응답의 read DTO(SPEC-0003) — **본문만** 담는다. 리졸버가 GraphQL SpotDetail 타입으로 매핑한다
 * (application은 DGS 무의존). 갤러리·운영정보·이용안내·좋아요 수는 각 필드가 요청될 때 별도 조회되므로
 * 여기 담지 않는다(필드별 지연 조회).
 */
data class SpotDetailView(
    val id: Long,
    val title: String,
    val category: SpotCategory,
    val thumbnailImageUrl: String,
    val overview: String?,
    val address: String?,
    val homepage: String?,
    val tel: String?,
    val copyright: CopyrightType,
    val attributionText: String,
)

/** 운영 정보 read DTO. 카테고리별 TourAPI 필드를 공통 이름으로 정규화한 결과(어댑터가 수행). */
data class SpotIntroView(
    val businessHours: String?,
    val closedDays: String?,
    val parking: String?,
    val inquiryPhone: String?,
    val fee: String?,
    val petAllowed: String?,
    val creditCardAccepted: String?,
)

/** 이용안내 1건의 read DTO. */
data class SpotInfoItemView(
    val name: String,
    val text: String,
)

/** 갤러리 이미지 1장의 read DTO. [attributionText]는 copyright에서 도출된 값. */
data class SpotImageView(
    val imageUrl: String,
    val thumbnailUrl: String?,
    val caption: String?,
    val copyright: CopyrightType,
    val attributionText: String,
)
