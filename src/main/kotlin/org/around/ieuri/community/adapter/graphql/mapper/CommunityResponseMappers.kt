package org.around.ieuri.community.adapter.graphql.mapper

import org.around.ieuri.common.pagination.CursorPageInfo
import org.around.ieuri.community.application.conversation.provided.dto.ConversationSpotQaOriginView
import org.around.ieuri.community.application.shared.required.dto.MemberSummaryView
import org.around.ieuri.common.graphql.generated.types.ConversationSpotQaOrigin as GqlConversationSpotQaOrigin
import org.around.ieuri.common.graphql.generated.types.PageInfo as GqlPageInfo
import org.around.ieuri.common.graphql.generated.types.PostAuthor as GqlPostAuthor

/*
 * 커뮤니티 리졸버들이 공유하는 응답 변환.
 */

/** 탈퇴한 회원의 닉네임 표기. */
const val WITHDRAWN_NICKNAME = "알 수 없음"

/** 작성자. 회원 조회가 비면 대체 표기로 채우되 id는 원래 작성자 id를 둔다(차단·신고 식별용). */
fun MemberSummaryView?.toGqlOrPlaceholder(memberId: Long): GqlPostAuthor = GqlPostAuthor(
    id = this?.id ?: memberId,
    nickname = this?.nickname ?: WITHDRAWN_NICKNAME,
    profileImageUrl = this?.profileImageUrl,
)

fun CursorPageInfo.toGql(): GqlPageInfo = GqlPageInfo(
    hasNextPage = hasNextPage,
    hasPreviousPage = hasPreviousPage,
    startCursor = startCursor,
    endCursor = endCursor,
)

/** DM 출처 스냅샷의 응답 변환(SPEC-0026 R14). */
fun ConversationSpotQaOriginView.toGql(): GqlConversationSpotQaOrigin = GqlConversationSpotQaOrigin(
    catalogId = catalogId,
    spotTitle = spotTitle,
    questionId = questionId,
    questionBodySnapshot = questionBodySnapshot,
    answerId = answerId,
    answerBodySnapshot = answerBodySnapshot,
)
