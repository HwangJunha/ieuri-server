package org.around.ieuri.auth.infrastructure.security

import org.around.ieuri.common.security.ForbiddenException
import org.around.ieuri.common.security.UnauthorizedException
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component

/**
 * Spring Security 기반 Authorizer 구현. 인증 필터가 accessToken의 perms를 authority로 세팅해 두면,
 * SecurityContext의 authority 목록에서 필요한 권한 코드 보유 여부를 판정한다(임시 AdminGuard 대체).
 */
@Component
class SecurityContextAuthorizer : Authorizer {

    override fun require(permission: String) {
        val authentication = SecurityContextHolder.getContext().authentication
            ?: throw UnauthorizedException("인증이 필요합니다.")
        if (authentication.authorities.none { it.authority == permission }) {
            throw ForbiddenException("권한이 필요합니다: $permission")
        }
    }
}
