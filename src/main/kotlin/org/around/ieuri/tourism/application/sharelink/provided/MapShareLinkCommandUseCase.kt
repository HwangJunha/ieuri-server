package org.around.ieuri.tourism.application.sharelink.provided

import org.around.ieuri.tourism.application.shared.provided.dto.MapShareLinkView

/** 전국 지도 공유 링크 쓰기(SPEC-0030 R12·R14). */
interface MapShareLinkCommandUseCase {

    /** 공유 링크를 발급한다(R12). 이미 유효한 링크가 있으면 **같은 토큰**을 돌려준다. */
    fun createShareLink(memberId: Long): MapShareLinkView

    /**
     * 공유 링크를 폐기한다(R14). 발급 때 받은 URL을 그대로 넘긴다.
     * @return 실제로 폐기했으면 true. 본인 것이 아니거나 이미 폐기됐으면 false다.
     */
    fun revokeShareLink(memberId: Long, shareUrl: String): Boolean

    /** 탈퇴 정리. 탈퇴 커밋 뒤 리스너가 부르며 새 트랜잭션이다. 지운 건수(로그용). */
    fun eraseMemberContent(memberId: Long): Int
}
