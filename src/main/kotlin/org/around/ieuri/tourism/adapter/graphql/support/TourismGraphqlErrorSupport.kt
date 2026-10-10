package org.around.ieuri.tourism.adapter.graphql.support

import org.around.ieuri.common.graphql.generated.types.UserError
import org.around.ieuri.tourism.domain.catalog.ReactionUserException

/**
 * tourism 뮤테이션(reactToSpot)의 오류 규약(BaseResponse).
 * - ReactionUserException·IllegalArgumentException은 payload의 userErrors로, 그 외(인증 실패 포함)는 최상위 errors[]로 나간다(SPEC-0005 R15).
 */
fun toReactionUserErrorOrNull(e: Throwable): UserError? = when (e) {
    is ReactionUserException -> UserError(code = e.code, message = e.message ?: e.code, field = null)
    is IllegalArgumentException -> UserError(code = "INVALID_INPUT", message = e.message ?: "잘못된 입력입니다.", field = null)
    else -> null
}

inline fun <T> withReactionUserErrors(onError: (List<UserError>) -> T, block: () -> T): T = try {
    block()
} catch (e: Exception) {
    val userError = toReactionUserErrorOrNull(e) ?: throw e
    onError(listOf(userError))
}
