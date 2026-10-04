package org.around.ieuri.community.infrastructure.adapter.`in`.graphql

import org.around.ieuri.common.graphql.generated.types.UserError
import org.around.ieuri.community.domain.shared.CommunityUserException

/** 예외를 UserError로 매핑한다. userErrors 대상이 아니면 null(→ 상위에서 재던짐). */
fun toCommunityUserErrorOrNull(e: Throwable): UserError? = when (e) {
    is CommunityUserException -> UserError(code = e.code, message = e.message ?: e.code, field = null)

    // Command 생성·도메인 require 위반 등 입력 형식·불변식 오류.
    is IllegalArgumentException ->
        UserError(code = "INVALID_INPUT", message = e.message ?: "잘못된 입력입니다.", field = null)

    else -> null
}

/**
 * 유스케이스 호출을 감싸 예상 실패를 userErrors payload로 바꾼다.
 * 인증 실패(UnauthorizedException)는 여기서 잡히지 않고 최상위 errors[]로 전파된다.
 */
inline fun <T> withCommunityUserErrors(onError: (List<UserError>) -> T, block: () -> T): T = try {
    block()
} catch (e: Exception) {
    val userError = toCommunityUserErrorOrNull(e) ?: throw e
    onError(listOf(userError))
}
