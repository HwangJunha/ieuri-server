package org.around.ieuri.tourism.application.port.out.persistence

import java.time.Instant

/** 공유 링크의 조회·폐기·삭제 포트(SPEC-0030). */
interface MapShareLinkPort {

    /** 토큰으로 유효한 링크를 찾는다. 폐기됐거나 없으면 null이다(둘을 구분하지 않는다). */
    fun findActiveByToken(token: String): MapShareLinkRecord?

    /** 링크를 폐기한다. 본인 것이 아니거나 이미 폐기됐으면 아무 일도 없고 false다. */
    fun revoke(memberId: Long, token: String): Boolean

    /** 탈퇴 회원의 링크를 폐기가 아니라 삭제하고 지운 건수를 돌려준다. */
    fun deleteAll(memberId: Long): Int
}

/** 저장된 공유 링크 1건. */
data class MapShareLinkRecord(
    val memberId: Long,
    val token: String,
    val createdAt: Instant,
)

/** 공유 링크 발급 포트(SPEC-0030). 회원당 유효 링크 1건을 upsert로 보장한다. */
interface MapShareLinkIssuePort {

    /** 회원의 유효한 링크를 가져오거나 없으면 발급한다. 이미 있으면 [token]은 버려진다. */
    fun findOrCreate(memberId: Long, token: String): MapShareLinkRecord
}
