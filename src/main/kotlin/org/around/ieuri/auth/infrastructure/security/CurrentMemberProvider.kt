package org.around.ieuri.auth.infrastructure.security

import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component

/**
 * 현재 인증된 회원(memberId)을 SecurityContext에서 조회한다. JWT 필터가 설정한 값을 읽는다.
 */
@Component
class CurrentMemberProvider {

    fun currentMemberId(): Long? =
        SecurityContextHolder.getContext().authentication?.principal as? Long

    fun requireMemberId(): Long =
        currentMemberId() ?: throw UnauthorizedException("인증이 필요합니다.")
}
