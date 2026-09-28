package org.around.ieuri.auth.infrastructure.security

import org.around.ieuri.auth.application.exception.InvalidTokenException
import org.around.ieuri.auth.application.port.out.token.TokenProviderPort
import org.around.ieuri.common.security.UnauthorizedException
import org.springframework.graphql.server.WebGraphQlInterceptor
import org.springframework.graphql.server.WebGraphQlRequest
import org.springframework.graphql.server.WebGraphQlResponse
import org.springframework.graphql.server.WebSocketGraphQlInterceptor
import org.springframework.graphql.server.WebSocketGraphQlRequest
import org.springframework.graphql.server.WebSocketSessionInfo
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import java.time.Clock
import java.time.Instant

/**
 * WebSocket `connection_init`에서 1회 인증하고, 요청마다 인증 주체를 실행 스레드에 세운다(SPEC-0017 R10, POL-048).
 * - HTTP JWT 필터는 이 경로를 타지 않는다.
 * - 토큰 만료로 기존 연결을 끊지 않는다. 만료는 새 `subscribe`의 인가에서 판정한다.
 */
@Component
class GraphqlWebSocketAuthInterceptor(
    private val tokenProvider: TokenProviderPort,
    private val clock: Clock,
) : WebSocketGraphQlInterceptor {

    /** 토큰이 없거나 무효면 연결 자체를 거부한다. */
    override fun handleConnectionInitialization(
        sessionInfo: WebSocketSessionInfo,
        connectionInitPayload: Map<String, Any>,
    ): Mono<Any> {
        // 연결 거부는 리졸버까지 가지 않아 로그가 없다. 페이로드 키 이름만 남기고 값(토큰)은 기록하지 않는다(POL-013).
        log.debug(
            "WS connection_init: payloadKeys={} headerNames={}",
            connectionInitPayload.keys,
            sessionInfo.headers.headerNames(),
        )

        // 페이로드를 먼저 보고 없으면 핸드셰이크 헤더를 본다(브라우저는 커스텀 헤더를 붙일 수 없다).
        val raw = payloadToken(connectionInitPayload) ?: headerToken(sessionInfo)
        if (raw == null) {
            log.warn(
                "WS connection_init 거부: 토큰이 없다. payloadKeys={} headerNames={}",
                connectionInitPayload.keys,
                sessionInfo.headers.headerNames(),
            )
            return Mono.error(
                UnauthorizedException(
                    "인증이 필요합니다. connection_init 페이로드나 Authorization 헤더에 " +
                        "'Bearer <accessToken>'을 넣어 주세요.",
                ),
            )
        }

        return try {
            val claims = tokenProvider.parseAccessToken(raw)
            sessionInfo.attributes[MEMBER_ID] = claims.memberId
            sessionInfo.attributes[PERMISSIONS] = claims.permissions.toList()
            // 구독 인가가 이 시각으로부터 액세스 TTL 초과를 판정한다(SPEC-0032 R7).
            sessionInfo.attributes[CONNECTED_AT] = clock.instant()
            log.debug("WS connection_init 성공: memberId={}", claims.memberId)
            Mono.empty()
        } catch (e: InvalidTokenException) {
            log.warn("WS connection_init 거부: 토큰이 유효하지 않다 ({})", e.message)
            Mono.error(UnauthorizedException("토큰이 유효하지 않습니다."))
        }
    }

    /** `connection_init` 페이로드의 토큰. 대소문자가 갈리는 클라이언트가 있어 둘 다 본다. */
    private fun payloadToken(payload: Map<String, Any>): String? = (payload[AUTHORIZATION] ?: payload[AUTHORIZATION.lowercase()])
        ?.let { it as? String }
        ?.let(::bearerToken)

    /** 핸드셰이크 헤더의 토큰. HttpHeaders는 헤더 이름의 대소문자를 가리지 않는다. */
    private fun headerToken(sessionInfo: WebSocketSessionInfo): String? = sessionInfo.headers.getFirst(AUTHORIZATION)?.let(::bearerToken)

    /** `Bearer <token>` 형식일 때만 토큰을 돌려준다. 접두어가 없으면 받지 않는다. */
    private fun bearerToken(raw: String): String? = raw.takeIf { it.startsWith(BEARER_PREFIX) }?.removePrefix(BEARER_PREFIX)?.takeIf { it.isNotBlank() }

    /**
     * 요청 실행 동안 세션의 인증 주체를 SecurityContext에 세우고, 끝나면 반드시 지운다.
     * - 지우지 않으면 재사용되는 스레드에서 다음 요청이 앞 회원의 신원을 물려받는다(필터 체인이 정리해 주지 않는다).
     * - `Mono.defer`로 감싸야 한다. try/finally만 쓰면 조립 시점에 지워져 리졸버 실행 시점엔 비어 있다.
     */
    override fun intercept(
        request: WebGraphQlRequest,
        chain: WebGraphQlInterceptor.Chain,
    ): Mono<WebGraphQlResponse> {
        if (request !is WebSocketGraphQlRequest) return chain.next(request)

        // 연결 수립 시각을 GraphQL 컨텍스트에 싣는다(SPEC-0032 R7). 구독 인가가 액세스 TTL 초과를 판정한다.
        (request.sessionInfo.attributes[CONNECTED_AT] as? Instant)?.let {
            request.configureExecutionInput { _, builder ->
                builder.graphQLContext(mapOf(CONNECTED_AT to it)).build()
            }
        }

        return Mono.defer {
            applyToSecurityContext(request.sessionInfo.attributes)
            chain.next(request)
        }.doFinally {
            // 취소·오류로 끝나도 반드시 지운다 — 남기면 다음 요청이 남의 신원을 물려받는다.
            SecurityContextHolder.clearContext()
        }
    }

    private fun applyToSecurityContext(attributes: Map<String, Any>) {
        val memberId = attributes[MEMBER_ID] as? Long
        if (memberId == null) {
            // 인증 정보가 없으면 **비워 둔다.** 앞 요청의 신원이 남아 있으면 그것으로 통과한다.
            SecurityContextHolder.clearContext()
            return
        }
        @Suppress("UNCHECKED_CAST")
        val permissions = (attributes[PERMISSIONS] as? Collection<String>).orEmpty()
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(
                memberId,
                null,
                permissions.map { SimpleGrantedAuthority(it) },
            )
    }

    companion object {
        private val log = org.slf4j.LoggerFactory.getLogger(GraphqlWebSocketAuthInterceptor::class.java)

        private const val AUTHORIZATION = "Authorization"
        private const val BEARER_PREFIX = "Bearer "
        private const val MEMBER_ID = "ieuri.memberId"
        private const val PERMISSIONS = "ieuri.permissions"

        /** 연결 수립 시각. 세션 속성이자 GraphQL 컨텍스트 키다(SPEC-0032 R7). */
        const val CONNECTED_AT = "ieuri.connectedAt"
    }
}
