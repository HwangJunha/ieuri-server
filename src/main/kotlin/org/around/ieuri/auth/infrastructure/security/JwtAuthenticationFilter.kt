package org.around.ieuri.auth.infrastructure.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.around.ieuri.auth.application.exception.InvalidTokenException
import org.around.ieuri.auth.application.port.out.token.TokenProviderPort
import org.around.ieuri.common.security.TOKEN_PRESENTATION_ATTRIBUTE
import org.around.ieuri.common.security.TokenPresentation
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * accessToken 인증 필터. 유효하면 memberId를 SecurityContext에 세우고, 없거나 무효면 인증 없이 통과시킨다.
 * - 판정 결과(없음/무효)를 요청 속성에 남긴다(SPEC-0032 R1, [org.around.ieuri.common.graphql.tokenPresentation]).
 */
@Component
class JwtAuthenticationFilter(
    private val tokenProvider: TokenProviderPort,
) : OncePerRequestFilter() {

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, filterChain: FilterChain) {
        val header = request.getHeader("Authorization")
        val presentation = if (header != null && header.startsWith(BEARER_PREFIX)) {
            authenticate(header.substring(BEARER_PREFIX.length))
        } else {
            TokenPresentation.NONE
        }
        request.setAttribute(TOKEN_PRESENTATION_ATTRIBUTE, presentation)
        filterChain.doFilter(request, response)
    }

    private fun authenticate(token: String): TokenPresentation = try {
        val claims = tokenProvider.parseAccessToken(token)
        // 권한(perms)을 authority로 세팅 → Authorizer가 권한 코드로 인가를 판정한다.
        val authorities = claims.permissions.map { SimpleGrantedAuthority(it) }
        val authentication = UsernamePasswordAuthenticationToken(claims.memberId, null, authorities)
        SecurityContextHolder.getContext().authentication = authentication
        TokenPresentation.VALID
    } catch (ex: InvalidTokenException) {
        // 무효 토큰은 인증 없이 통과. SecurityContext는 설정하지 않는다.
        SecurityContextHolder.clearContext()
        TokenPresentation.REJECTED
    }

    private companion object {
        const val BEARER_PREFIX = "Bearer "
    }
}
