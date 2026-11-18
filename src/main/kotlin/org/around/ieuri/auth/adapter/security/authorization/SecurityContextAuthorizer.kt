package org.around.ieuri.auth.adapter.security.authorization
import org.around.ieuri.common.security.ForbiddenException
import org.around.ieuri.common.security.UnauthorizedException
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component

/** Spring Security 기반 Authorizer 구현. SecurityContext의 authority에서 권한 코드 보유를 판정한다. */
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
