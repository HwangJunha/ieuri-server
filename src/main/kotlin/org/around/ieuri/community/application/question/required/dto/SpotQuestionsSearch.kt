package org.around.ieuri.community.application.question.required.dto

import org.around.ieuri.community.application.shared.required.dto.ViewerContext
import org.around.ieuri.community.domain.shared.ContentLanguage

/**
 * 질문 목록 조회 조건.
 * - [ViewerContext]의 `hiddenPostIds`는 여기서 비어 있는 채로 온다(질문에는 개인 숨김이 없다).
 */
data class SpotQuestionsSearch(
    /** 이 장소의 질문만. null이면 좁히지 않는다. */
    val catalogId: Long?,
    /** 이 작성자의 질문만. null이면 좁히지 않는다 — 호출자 신원은 응용 계층이 채운다. */
    val authorId: Long? = null,
    /** 이 id들만. null이면 좁히지 않고, 빈 목록이면 결과가 없다. */
    val ids: List<Long>? = null,
    /** 요청의 활성 언어(SPEC-0029). 항상 걸리며, 이 언어에서 해소되지 않는 장소의 질문은 빠진다. */
    val language: ContentLanguage,
    val first: Int,
    val after: String?,
    val viewer: ViewerContext,
)
