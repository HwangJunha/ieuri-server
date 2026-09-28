package org.around.ieuri.common.security

import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component

/**
 * 현재 인증된 회원(memberId)을 SecurityContext에서 조회한다. JWT 필터가 설정한 principal(Long)을 읽는다.
 */
@Component
class CurrentMemberProvider {

    fun currentMemberId(): Long? = SecurityContextHolder.getContext().authentication?.principal as? Long

    fun requireMemberId(): Long = currentMemberId() ?: throw UnauthorizedException("인증이 필요합니다.")

    /**
     * 신원이 선택인 연산의 회원 id(SPEC-0032 R1). 비로그인은 null이다.
     * - 토큰을 보냈는데 거절된 요청([TokenPresentation.REJECTED])은 [StaleTokenException]을 던진다.
     */
    fun optionalMemberId(presentation: TokenPresentation): Long? {
        if (presentation == TokenPresentation.REJECTED) {
            throw StaleTokenException("토큰이 만료되었거나 유효하지 않습니다. 갱신 후 다시 시도해 주세요.")
        }
        return currentMemberId()
    }
}
