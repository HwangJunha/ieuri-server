package org.around.ieuri.tourism.application.query

import org.around.ieuri.tourism.domain.vo.SpotCategory

/**
 * 앱·언어에 노출되는 카테고리 1건(read DTO). **표시 정보를 함께 담는다**(SPEC-0020) —
 * 클라이언트가 모르는 카테고리도 그릴 수 있어야 앱 업데이트 없이 새 카테고리가 나간다.
 *
 * **노출 순서는 담지 않는다**(V59) — 서버는 무엇을 보일지만 정하고 순서는 클라이언트가 정한다.
 */
data class AppCategoryView(
    val category: SpotCategory,
    /** 요청 언어 표시 문구. 번역이 없으면 코드가 그대로 들어온다. */
    val label: String,
    /** 칩·마커 이미지. 설정되지 않았으면 null이다. */
    val iconUrl: String?,
)

/**
 * 카테고리의 표시 정보(read DTO). 목록·지도 응답이 카테고리를 그릴 때 쓴다.
 *
 * [AppCategoryView]와 달리 노출 순서를 갖지 않는다 — 그것은 앱별 구성의 성질이고,
 * 이쪽은 카테고리 자체의 성질이다.
 */
data class CategoryDisplay(
    val category: SpotCategory,
    val label: String,
    val iconUrl: String?,
) {
    companion object {
        /** 번역·정의가 없는 코드의 표현. 코드를 그대로 라벨로 쓴다 — 빈 칩보다 낫다. */
        fun fallback(category: SpotCategory) =
            CategoryDisplay(category = category, label = category.code, iconUrl = null)
    }
}

/**
 * 같은 구성을 **문자열 축으로** 표현한 read DTO. 다른 도메인이 카테고리 구성을 읽을 때 쓴다.
 * 도메인 타입을 넘기지 않아야 그 도메인을 따로 떼어낼 수 있다.
 */
data class AppCategoryCode(
    val category: String,
    val label: String,
    val iconUrl: String?,
)
