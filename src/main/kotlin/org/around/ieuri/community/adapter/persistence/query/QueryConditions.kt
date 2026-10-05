package org.around.ieuri.community.adapter.persistence.query

import org.around.ieuri.community.adapter.persistence.mapper.toTimestamptz
import org.around.ieuri.community.application.port.out.query.ViewerContext
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.notInList
import org.jetbrains.exposed.v1.core.or
import java.time.OffsetDateTime

// 읽기 어댑터가 공유하는 모더레이션·keyset 조건. 필터는 조회 뒤가 아니라 쿼리 조건으로 건다(페이지 크기 보장).

/** 차단 관계(양방향)인 작성자 제외. 차단이 없으면 조건 없음(null). */
fun blockExclusion(viewer: ViewerContext, authorId: Column<Long>): Op<Boolean>? = viewer.blockedMemberIds.takeIf { it.isNotEmpty() }?.let { authorId notInList it }

/** 숨겨진 콘텐츠는 작성자 본인에게만 남는다. */
fun hiddenExclusion(viewer: ViewerContext, hidden: Column<Boolean>, authorId: Column<Long>): Op<Boolean> = (hidden eq false) or (authorId eq viewer.memberId)

/** `(time DESC, id DESC)` keyset: 커서보다 뒤의 행. */
fun keysetBefore(time: Column<OffsetDateTime>, id: Column<Long>, cursor: CommunityCursor): Op<Boolean> {
    val at = cursor.createdAt.toTimestamptz()
    return (time less at) or ((time eq at) and (id less cursor.id))
}

/** null 조건은 건너뛰고 AND로 묶는다. 하나도 없으면 참이다. */
fun allOf(vararg conditions: Op<Boolean>?): Op<Boolean> = conditions.filterNotNull().fold(Op.TRUE as Op<Boolean>) { acc, op -> acc and op }
