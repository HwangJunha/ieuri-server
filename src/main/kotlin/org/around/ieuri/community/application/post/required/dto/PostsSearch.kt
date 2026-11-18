package org.around.ieuri.community.application.post.required.dto

import org.around.ieuri.community.application.shared.required.dto.ViewerContext
import org.around.ieuri.community.domain.post.PostSortOrder
import org.around.ieuri.community.domain.post.TargetCategory
import org.around.ieuri.community.domain.shared.ContentLanguage

/** 글 피드 조회 조건. 반경은 좌표로 넘겨 글 조회에 직접 건다. */
data class PostsSearch(
    /** 이 장소에 붙은 글만. `spot.id`가 아니라 카탈로그 id다(SPEC-0019). null이면 좁히지 않는다. */
    val catalogId: Long?,
    /** 좌표 반경. null이면 반경으로 좁히지 않는다. */
    val area: PostsArea?,
    /** 카테고리 선택. 비어 있으면 좁히지 않고, 값이 여럿이면 IN으로 거른다(OR). */
    val categories: Set<TargetCategory>,
    /** 정규화된 태그. null이면 태그로 좁히지 않는다. */
    val normalizedTag: String?,
    /**
     * 정렬 방식이자 노출 범위.
     * - LATEST는 [area]로 좁혀도 장소 없는 글을 함께 담는다.
     * - DISTANCE는 반경 안 장소에 붙은 글만 담는다.
     */
    val sort: PostSortOrder,
    /** true면 숨긴 글만 돌려준다(해제 화면). 이때는 숨김 필터를 우회한다. */
    val hiddenByViewer: Boolean,
    /**
     * 요청의 활성 언어(SPEC-0029).
     * - 이 언어에서 해소되지 않는 장소가 붙은 글은 오류 없이 목록에서 빠진다.
     * - 장소가 붙지 않은 글은 언어와 무관하게 남는다.
     */
    val language: ContentLanguage,
    val first: Int,
    val after: String?,
    val viewer: ViewerContext,
)
