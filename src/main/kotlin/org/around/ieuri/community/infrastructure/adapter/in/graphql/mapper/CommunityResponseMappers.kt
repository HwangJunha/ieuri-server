package org.around.ieuri.community.infrastructure.adapter.`in`.graphql.mapper

import org.around.ieuri.common.pagination.CursorPageInfo
import org.around.ieuri.community.application.query.PostAuthorView
import org.around.ieuri.common.graphql.generated.types.PageInfo as GqlPageInfo
import org.around.ieuri.common.graphql.generated.types.PostAuthor as GqlPostAuthor

/**
 * 커뮤니티 표면이 공유하는 응답 변환. 글·댓글·장소 질문/답변이 같은 모양을 쓴다 —
 * 리졸버마다 복사하면 한 곳만 고쳐지는 날이 온다.
 */

/** 탈퇴한 회원의 표기. 회원 정보가 없어도 글은 남으므로 이름 자리를 비워 두지 않는다. */
const val WITHDRAWN_NICKNAME = "알 수 없음"

/**
 * 작성자. **회원 조회가 비어도 실패하지 않는다** — 탈퇴해도 글·질문은 보존되므로
 * 대체 표기로 채운다. id는 원래 작성자 id를 그대로 둔다(차단·신고 대상 식별에 쓰인다).
 */
fun PostAuthorView?.toGqlOrPlaceholder(memberId: Long): GqlPostAuthor = GqlPostAuthor(
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
