package org.around.ieuri.auth.infrastructure.security

import org.around.ieuri.auth.infrastructure.config.AdminProperties
import org.springframework.stereotype.Component
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes

/**
 * 관리자 임시 가드. 요청 헤더 `X-Admin-Api-Key`를 설정값과 비교한다(본격 RBAC 전까지).
 * 정지/복구 리졸버가 처리 전에 호출한다.
 */
@Component
class AdminGuard(
    private val properties: AdminProperties,
) {
    fun verify() {
        val request = (RequestContextHolder.getRequestAttributes() as? ServletRequestAttributes)?.request
        val provided = request?.getHeader(ADMIN_KEY_HEADER)
        if (provided.isNullOrBlank() || provided != properties.apiKey) {
            throw ForbiddenException("관리자 권한이 필요합니다.")
        }
    }

    private companion object {
        const val ADMIN_KEY_HEADER = "X-Admin-Api-Key"
    }
}
