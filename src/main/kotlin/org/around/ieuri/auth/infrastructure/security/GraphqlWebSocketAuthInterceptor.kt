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

/**
 * WebSocket 연결 수립(`connection_init`) 시 1회 인증하고, 이후 프레임마다 인증 주체를
 * 실행 스레드에 세운다(SPEC-0017 R10, POL-048).
 *
 * **HTTP JWT 필터가 이 경로를 타지 않는다** — WebSocket 핸드셰이크 이후의 프레임은 서블릿
 * 필터 체인을 거치지 않으므로 인증을 여기서 다뤄야 한다.
 *
 * 인증은 **연결당 1회**이고, 구독별 참여자 검사는 `subscribe` 시점에 따로 한다 — 연결이
 * 살아 있다는 것이 특정 대화를 볼 자격의 증명은 아니다.
 *
 * **토큰 만료로 기존 연결을 끊지 않는다.** accessToken은 5분 만료라(POL-008) 대화 중에 반드시
 * 만료되는데, 끊으면 타이핑 표시가 주기적으로 사라진다. 만료는 새 `subscribe` 시점부터
 * 적용되며, 그 판정은 구독을 여는 유스케이스의 참여자 검사가 맡는다.
 *
 * auth에 두는 이유는 토큰 해석이 이 도메인의 책임이기 때문이다 — community는 이 클래스를
 * 알지 못하고 SecurityContext를 통해 memberId만 읽는다(JwtAuthenticationFilter와 같은 구조).
 */
@Component
class GraphqlWebSocketAuthInterceptor(
    private val tokenProvider: TokenProviderPort,
) : WebSocketGraphQlInterceptor {

    /**
     * 토큰이 없거나 무효면 **연결 자체를 거부한다.** 구독 시점까지 미루면 클라이언트가 연결에
     * 성공했다고 믿고 이벤트를 기다린다.
     */
    override fun handleConnectionInitialization(
        sessionInfo: WebSocketSessionInfo,
        connectionInitPayload: Map<String, Any>,
    ): Mono<Any> {
        // **연결 수립이 실패하면 리졸버까지 가지 않아 아무 로그도 남지 않는다.**
        // 페이로드 키 이름만 남긴다 — 값에는 토큰이 들어 있어 기록하지 않는다(POL-013).
        log.debug(
            "WS connection_init: payloadKeys={} headerNames={}",
            connectionInitPayload.keys,
            sessionInfo.headers.headerNames(),
        )

        // **두 경로를 모두 받는다.** 페이로드를 먼저 보고 없으면 핸드셰이크 헤더를 본다.
        //
        // 브라우저의 WebSocket API는 커스텀 헤더를 붙일 수 없어 페이로드가 유일한 수단이고,
        // 네이티브 클라이언트·API 도구(Postman 등)는 헤더를 쓰는 쪽이 흔하다. 한쪽만 받으면
        // 나머지 절반이 "토큰을 넣었는데 인증이 안 된다"는 상태에 빠진다.
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
            log.debug("WS connection_init 성공: memberId={}", claims.memberId)
            Mono.empty()
        } catch (e: InvalidTokenException) {
            log.warn("WS connection_init 거부: 토큰이 유효하지 않다 ({})", e.message)
            Mono.error(UnauthorizedException("토큰이 유효하지 않습니다."))
        }
    }

    /** `connection_init` 페이로드의 토큰. 대소문자가 갈리는 클라이언트가 있어 둘 다 본다. */
    private fun payloadToken(payload: Map<String, Any>): String? =
        (payload[AUTHORIZATION] ?: payload[AUTHORIZATION.lowercase()])
            ?.let { it as? String }
            ?.let(::bearerToken)

    /** 핸드셰이크 헤더의 토큰. HttpHeaders는 헤더 이름의 대소문자를 가리지 않는다. */
    private fun headerToken(sessionInfo: WebSocketSessionInfo): String? =
        sessionInfo.headers.getFirst(AUTHORIZATION)?.let(::bearerToken)

    /** `Bearer <token>` 형식일 때만 토큰을 돌려준다. 접두어가 없으면 받지 않는다. */
    private fun bearerToken(raw: String): String? =
        raw.takeIf { it.startsWith(BEARER_PREFIX) }?.removePrefix(BEARER_PREFIX)?.takeIf { it.isNotBlank() }

    /**
     * 구독 요청이 실행되기 직전에 세션의 인증 주체를 **현재 스레드의 SecurityContext로 옮기고,
     * 끝나면 반드시 지운다.**
     *
     * 지우는 것이 이 메서드의 핵심이다. WebSocket 프레임은 서블릿 필터 체인을 타지 않아
     * `SecurityContextHolder`를 정리해 주는 주체가 **없고**, 처리 스레드는 연결과 요청을 가로질러
     * 재사용된다. 세우기만 하면 앞 요청의 회원 신원이 다음 요청에 그대로 남아 **다른 사람의
     * 대화를 그의 권한으로 조회하게 된다.** 인증 실패가 아니라 잘못된 성공이라 조용하다.
     *
     * 리졸버가 스트림을 만들 때까지만 필요하다 — 구독이 열린 뒤 흘러오는 이벤트는 참여자
     * 검사를 이미 통과한 것이라 그 시점의 SecurityContext를 요구하지 않는다.
     *
     * **`Mono.defer`로 감싸는 것이 핵심이다.** `chain.next()`는 콜드 Mono를 돌려주므로 그냥
     * try/finally로 감싸면 finally가 **조립 시점에** 돌아, 정작 리졸버가 실행되는 구독 시점에는
     * 컨텍스트가 이미 비어 있다. 세우기와 지우기를 둘 다 리액티브 생명주기 안으로 넣어야
     * 실행 구간을 정확히 감싼다.
     */
    override fun intercept(
        request: WebGraphQlRequest,
        chain: WebGraphQlInterceptor.Chain,
    ): Mono<WebGraphQlResponse> {
        if (request !is WebSocketGraphQlRequest) return chain.next(request)

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

    private companion object {
        private val log = org.slf4j.LoggerFactory.getLogger(GraphqlWebSocketAuthInterceptor::class.java)

        const val AUTHORIZATION = "Authorization"
        const val BEARER_PREFIX = "Bearer "
        const val MEMBER_ID = "ieuri.memberId"
        const val PERMISSIONS = "ieuri.permissions"
    }
}
