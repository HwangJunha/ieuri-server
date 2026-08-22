package org.around.ieuri.tourism.application.port.out.persistence

import java.time.Instant

/**
 * 공유 링크의 저장·조회 포트(SPEC-0030 R12~R14).
 *
 * 읽기와 쓰기를 한 포트에 두는 이유는 발급이 **"있으면 그것, 없으면 새로"**라 두 연산이
 * 한 트랜잭션 안에서 붙어 다니기 때문이다. 조회 전용 경로(`sharedMap`)만 별개다.
 */
interface MapShareLinkPort {

    /** 토큰으로 **유효한** 링크를 찾는다. 폐기됐거나 없으면 null이다 — 둘을 구분하지 않는다. */
    fun findActiveByToken(token: String): MapShareLinkRecord?

    /**
     * 링크를 폐기한다. 본인 것이 아니거나 이미 폐기됐으면 아무 일도 없다.
     *
     * @return 실제로 폐기했으면 true.
     */
    fun revoke(memberId: Long, token: String): Boolean

    /**
     * 회원의 링크를 폐기가 아니라 **삭제**한다(탈퇴, §9.1).
     *
     * 폐기로 두면 토큰 행이 남는데, 탈퇴한 회원의 링크에는 남길 것이 없다.
     *
     * @return 지운 건수.
     */
    fun deleteAll(memberId: Long): Int
}

/** 저장된 공유 링크 1건. */
data class MapShareLinkRecord(
    val memberId: Long,
    val token: String,
    val createdAt: Instant,
)

/**
 * 공유 링크 발급 포트(SPEC-0030 R12).
 *
 * **조회·폐기와 포트를 나눈 이유는 구현 수단이 다르기 때문이다.** 회원당 1건 제약이 부분
 * UNIQUE 인덱스(`revoked_at IS NULL`)라 JPA로 표현할 수 없고, 먼저 조회해 없으면 넣는 방식은
 * 동시 발급에서 링크가 둘 생긴다. 나머지 연산은 파생 쿼리로 끝난다.
 */
interface MapShareLinkIssuePort {

    /**
     * 회원의 유효한 링크를 가져오거나 없으면 발급한다.
     *
     * **발급을 반복해도 링크가 늘지 않는다**(계약). 충돌하면 [token]은 버려지고 이미 있는
     * 행이 돌아온다.
     */
    fun findOrCreate(memberId: Long, token: String): MapShareLinkRecord
}
