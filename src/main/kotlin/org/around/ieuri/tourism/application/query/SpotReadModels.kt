package org.around.ieuri.tourism.application.query

import org.around.ieuri.tourism.domain.vo.CategorySelection
import org.around.ieuri.tourism.domain.vo.CopyrightType
import org.around.ieuri.tourism.domain.vo.SpotCategory
import java.time.Instant
import java.time.LocalDate

/**
 * spots 쿼리 입력. 언어는 인자가 아니라 Accept-Language에서 온다(요청 스코프 컨텍스트로 주입).
 * TourAPI가 pageNo 오프셋만 지원하므로 오프셋 페이지네이션([page] 1-base, [size]).
 * 좌표·반경 형식 검증은 Coordinates VO/서비스에서, 카테고리 가용성은 서비스에서 판정한다.
 */
data class SpotsQuery(
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Int,
    /** 카테고리 선택. 비어 있으면 전체이며, 값이 여럿이면 OR로 결합된다(SPEC-0020 R5). */
    val categories: CategorySelection,
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
    /** 경도. **본문 캐시에 담기지 않고 뷰 조립 시점에 카탈로그에서 얹힌다**(SPEC-0027). */
    val mapX: Double,
    /** 위도. 출처와 조립 시점은 [mapX]와 같다. */
    val mapY: Double,
)

/**
 * 카탈로그 한 행이 보유한 최소 식별 정보. **upstream 호출 없이** 만들어진다.
 *
 * 다른 도메인이 장소 id의 유효성 확인과 요약 표시에 쓰는 형태다 — 상세 본문이 필요 없는
 * 호출자에게 [SpotDetailView]를 주면 TourAPI 왕복이 딸려 온다.
 */
data class SpotSummaryView(
    /**
     * `spot_catalog.id`(SPEC-0019 R-12). **다른 도메인이 장소를 저장할 때 쓰는 축이다** —
     * 언어별 `spot.id`를 주면 같은 장소의 글이 언어별로 갈린다.
     */
    val id: Long,
    val title: String,
    val thumbnailImageUrl: String,
    /**
     * 카테고리의 **안정 식별자 문자열**(SpotCategory의 이름). 도메인 enum을 그대로 노출하지
     * 않는 이유는 이 DTO를 다른 도메인이 읽기 때문이다 — enum을 넘기면 상대가 tourism의
     * domain 패키지에 의존하게 되고, 그것이 도메인 추출을 막는다.
     */
    val categoryCode: String,
    /**
     * 경도·위도. **다른 도메인이 이 좌표를 복사해 두고 자기 반경 조회에 쓴다** —
     * 매번 tourism에 spot 목록을 물으면 그 목록의 상한이 곧 상대 도메인의 조회 한계가 된다.
     */
    val longitude: Double,
    val latitude: Double,
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

/**
 * 좋아요 목록 조회 입력(SPEC-0008 R-15). 언어는 인자가 아니라 Accept-Language에서 오고,
 * 회원은 인증 컨텍스트에서 온다 — 둘 다 클라이언트가 고를 수 있는 값이 아니다(AC-48·AC-49).
 */
data class LikedSpotsQuery(
    /** 카테고리 선택. 비어 있으면 전체이며, 값이 여럿이면 OR로 결합된다(`spots`와 같은 규약). */
    val categories: CategorySelection,
    val first: Int,
    val after: String?,
)

/**
 * 좋아요 목록 항목 1건의 read DTO. 필드가 전부 `spot` 행에 있어 `spot_detail`을 조인하지 않는다 —
 * 상세가 부재한 spot도 목록에는 정상적으로 나타난다.
 *
 * [likedAt]은 **노드가 아니라 엣지의 값이지만** 프로젝션 단계에서 함께 읽어야 하므로 여기 담고,
 * 리졸버가 엣지로 옮긴다. "언제 좋아요했는가"는 회원과 spot의 관계 속성이라 GraphQL 노드에는
 * 노출하지 않는다.
 */
data class LikedSpotView(
    val id: Long,
    val title: String,
    val thumbnailImageUrl: String,
    val address: String?,
    val category: SpotCategory,
    val copyright: CopyrightType,
    val attributionText: String,
    val likedAt: Instant,
    /**
     * 소속 카탈로그. **커서의 정렬 키다**(SPEC-0019 §4.3) — 좋아요가 카탈로그 단위로 기록되므로
     * `spot.id`로는 페이지 경계를 만들 수 없다. 응답에는 나가지 않는다.
     */
    val catalogId: Long,
)

/**
 * spot 검색 입력(SPEC-0013 R1-R8). 언어는 인자가 아니라 Accept-Language에서 온다.
 * [query] 트림·길이 검증은 서비스가 포트 호출 **이전에** 수행한다(AC-8.1).
 *
 * **오프셋 페이지네이션이다**([page] 1-base). 관련도가 검색 엔진의 계산값이라 커서 조건으로
 * 표현할 수 없어서이며, 이유는 `SpotSearchQuery`에 적어 두었다.
 */
data class SearchSpotsQuery(
    val query: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Int,
    /** 카테고리 선택. 비어 있으면 전체이며, 값이 여럿이면 OR로 결합된다(SPEC-0020 R5). */
    val categories: CategorySelection,
    val page: Int,
    val size: Int,
)

/**
 * 검색 응답. 검증 실패도 예외가 아니라 이 형태로 돌아온다(POL-015) — [userErrors]가 비어 있지
 * 않으면 [items]가 비고 [totalCount]가 0이다. 클라이언트가 null 분기 없이 읽게 하기 위함이다.
 */
data class SpotSearchResultView(
    val userErrors: List<SearchUserError>,
    val items: List<SpotView>,
    val page: Int,
    val hasNext: Boolean,
    val totalCount: Int,
) {
    val success: Boolean get() = userErrors.isEmpty()
}

/**
 * 검색 입력 검증 실패 1건. GraphQL UserError로 매핑되지만 application은 DGS를 모르므로
 * 자체 타입으로 둔다. [code]는 클라이언트 분기 기준이며 메시지가 아니다.
 */
data class SearchUserError(
    val code: String,
    val message: String,
    val field: String?,
)

/** 자동완성 항목 1건(R9). 제목 접두 매칭 결과이며 거리·주소를 담지 않는다. */
data class SpotSuggestionView(
    val id: Long,
    val title: String,
    val category: SpotCategory,
)

/** 진행 중 축제 조회 입력(R17). '오늘'은 인자가 아니라 서버 시계에서 온다. */
data class OngoingFestivalsQuery(
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Int,
    val first: Int,
    val after: String?,
)

/**
 * 진행 중 축제 1건의 read DTO. `spot`과 `spot_festival`을 조인한 값이다.
 * 날짜는 원본이 DATE 컬럼이라 시각 성분이 없다 — 리졸버가 ISO-8601 문자열로 옮긴다.
 */
data class FestivalView(
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
    val eventStartDate: LocalDate,
    val eventEndDate: LocalDate,
)

/** 인기 spot 조회 입력(R18). 좋아요 최소 기준은 인자가 아니라 정책이다. */
data class PopularSpotsQuery(
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Int,
    /** 카테고리 선택. 비어 있으면 전체이며, 값이 여럿이면 OR로 결합된다(SPEC-0020 R5). */
    val categories: CategorySelection,
    val first: Int,
    val after: String?,
)

/**
 * 인기 spot 1건의 read DTO. [likeCount]는 순위 근거라 노드가 아니라 엣지의 값이지만,
 * 프로젝션 단계에서 함께 읽어야 하므로 여기 담고 리졸버가 엣지로 옮긴다.
 */
data class PopularSpotView(
    val spot: SpotView,
    val likeCount: Long,
)

/**
 * recommendedSpots 입력(SPEC-0035). 인자는 `spots`와 같되 페이지가 아니라 묶음 크기 [first]만 받는다.
 * 언어는 Accept-Language, 회원은 인증 컨텍스트에서 온다.
 */
data class RecommendedSpotsQuery(
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Int,
    /** 카테고리 선택. 비어 있으면 전체이며, 값이 여럿이면 OR로 결합된다(`spots`와 같은 규약). */
    val categories: CategorySelection,
    val first: Int,
)
