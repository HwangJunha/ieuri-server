package org.around.ieuri.tourism.infrastructure.adapter.`in`.graphql

import org.around.ieuri.tourism.application.exception.ReactionUserException
import org.around.ieuri.common.graphql.generated.types.UserError

/**
 * tourism 뮤테이션(reactToSpot)의 공통 오류 규약(BaseResponse). 예상 실패(ReactionUserException: SPOT_NOT_FOUND)와
 * 입력 형식 오류(IllegalArgumentException)는 payload의 userErrors로, 그 외는 재던져 최상위 error로.
 *
 * 인증 실패(UnauthorizedException)는 ReactionUserException이 아니므로 여기서 붙잡히지 않고 최상위
 * errors[]로 전파된다(SPEC-0005 R15). userErrors[]는 세션 상태를 담지 않는다.
 *
 * 생성 UserError 타입은 스키마 codegen 산출물(common.graphql.generated)로 전역 공유된다.
 */
fun toReactionUserErrorOrNull(e: Throwable): UserError? = when (e) {
    is ReactionUserException -> UserError(code = e.code, message = e.message ?: e.code, field = null)
    is IllegalArgumentException -> UserError(code = "INVALID_INPUT", message = e.message ?: "잘못된 입력입니다.", field = null)
    else -> null
}

inline fun <T> withReactionUserErrors(onError: (List<UserError>) -> T, block: () -> T): T =
    try {
        block()
    } catch (e: Exception) {
        val userError = toReactionUserErrorOrNull(e) ?: throw e
        onError(listOf(userError))
    }
