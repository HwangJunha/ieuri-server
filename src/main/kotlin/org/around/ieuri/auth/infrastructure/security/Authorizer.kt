package org.around.ieuri.auth.infrastructure.security

/**
 * 인가 판정 추상화: 현재 주체가 특정 권한을 갖는가. 역할명이 아니라 권한 코드로만 판정한다.
 * - 미인증 → UnauthorizedException, 권한 없음 → ForbiddenException.
 */
interface Authorizer {

    /** 지정한 권한이 없으면 거부한다. 인증조차 없으면 먼저 Unauthorized로 거부. */
    fun require(permission: String)
}
