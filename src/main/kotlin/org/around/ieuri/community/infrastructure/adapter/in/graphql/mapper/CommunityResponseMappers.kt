package org.around.ieuri.community.infrastructure.adapter.`in`.graphql.mapper

import org.around.ieuri.common.pagination.CursorPageInfo
import org.around.ieuri.community.application.query.ConversationSpotQaOriginView
import org.around.ieuri.community.application.query.PostAuthorView
import org.around.ieuri.common.graphql.generated.types.ConversationSpotQaOrigin as GqlConversationSpotQaOrigin
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

/**
 * DM 출처 스냅샷의 응답 변환(SPEC-0026 R14).
 *
 * 리졸버 안에 인라인으로 두지 않는 이유는 필드가 여섯이고 그중 셋이 `Long`, 셋이 `String`이라
 * **뒤바뀌어도 컴파일이 통과하기** 때문이다 — 질문 발췌 자리에 답변 발췌가 실리면 맥락 카드가
 * 영구히 틀린 값을 보여준다(스냅샷은 대화 생성 시 한 번 쓰이고 다시 만들어지지 않는다).
 */
fun ConversationSpotQaOriginView.toGql(): GqlConversationSpotQaOrigin = GqlConversationSpotQaOrigin(
    catalogId = catalogId,
    spotTitle = spotTitle,
    questionId = questionId,
    questionBodySnapshot = questionBodySnapshot,
    answerId = answerId,
    answerBodySnapshot = answerBodySnapshot,
)
