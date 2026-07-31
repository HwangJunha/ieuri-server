package org.around.ieuri.auth.infrastructure.adapter.`in`.graphql

import org.around.ieuri.auth.application.exception.UserFacingException
import org.around.ieuri.common.graphql.generated.types.UserError

/**
 * mutation 리졸버의 공통 오류 규약(BaseResponse). 예상 가능한 도메인/입력 실패는 payload의 userErrors로
 * 변환하고, 그 외(인증/인가/토큰/시스템 오류)는 다시 던져 GraphQL 최상위 error로 전파한다.
 */

/** 예외를 UserError로 매핑한다. userErrors 대상이 아니면 null(→ 상위에서 재던짐). */
fun toUserErrorOrNull(e: Throwable): UserError? = when (e) {
    is UserFacingException -> UserError(code = e.code, message = e.message ?: e.code, field = null)
    // Command 생성/도메인 require 위반 등 입력 형식·불변식 오류.
    is IllegalArgumentException -> UserError(code = "INVALID_INPUT", message = e.message ?: "잘못된 입력입니다.", field = null)
    else -> null
}

/**
 * 유스케이스 호출을 감싸 예상 실패를 userErrors payload로 바꾼다.
 * [onError]는 userErrors만 채운 실패 payload를 만든다(성공 데이터 필드는 null).
 */
inline fun <T> withUserErrors(onError: (List<UserError>) -> T, block: () -> T): T =
    try {
        block()
    } catch (e: Exception) {
        val userError = toUserErrorOrNull(e) ?: throw e
        onError(listOf(userError))
    }
