package org.around.ieuri.common.security

import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component

/**
 * 현재 인증된 회원(memberId)을 SecurityContext에서 조회한다(공유 커널). JWT 필터가 설정한 principal(Long)을 읽는다.
 * 여러 도메인의 리졸버가 재사용하므로 특정 도메인이 아닌 common에 둔다.
 */
@Component
class CurrentMemberProvider {

    fun currentMemberId(): Long? =
        SecurityContextHolder.getContext().authentication?.principal as? Long

    fun requireMemberId(): Long =
        currentMemberId() ?: throw UnauthorizedException("인증이 필요합니다.")
}
