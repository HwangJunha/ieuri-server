package org.around.ieuri.community.application.shared

import org.around.ieuri.community.application.shared.required.CommunityQueryPort
import org.around.ieuri.community.application.shared.required.ViewerContext

/** 조회자의 양방향 차단 회원과 숨긴 글. 피드·단건·댓글 조회가 모두 이 값을 조건으로 건다 — 한 경로라도 빠지면 차단·숨김이 무력화된다. */
fun CommunityQueryPort.viewerContext(memberId: Long): ViewerContext = ViewerContext(
    memberId = memberId,
    blockedMemberIds = findBlockedMemberIds(memberId),
    hiddenPostIds = findHiddenPostIds(memberId),
)

/** 상한 초과는 오류가 아니라 절삭이다. */
fun CommunityPolicy.clampPageSize(first: Int): Int = first.coerceIn(1, maxPageSize)
