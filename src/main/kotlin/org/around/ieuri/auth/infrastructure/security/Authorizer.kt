package org.around.ieuri.auth.infrastructure.security

/**
 * 인가 판정 추상화. "현재 요청 주체가 특정 권한(permission)을 갖는가"를 판정한다.
 *
 * 판정 메커니즘(현재는 Spring Security의 SecurityContext)에 호출부가 묶이지 않도록 인터페이스로 분리한다.
 * 추후 다른 인가 백엔드로 교체하더라도 @RequiresPermission 애스펙트나 호출부는 그대로 두고 구현만 바꾼다.
 * 역할명이 아니라 권한 코드로만 판정한다(엔터프라이즈 관례).
 *
 * - 미인증(토큰 없음/무효) → UnauthorizedException(401 상당)
 * - 인증됐으나 권한 없음 → ForbiddenException(403 상당)
 */
interface Authorizer {

    /** 지정한 권한이 없으면 거부한다. 인증조차 없으면 먼저 Unauthorized로 거부. */
    fun require(permission: String)
}
