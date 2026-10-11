package org.around.ieuri.auth.adapter.graphql.support
import org.around.ieuri.auth.domain.shared.AuthUserException
import org.around.ieuri.common.graphql.generated.types.ErrorCode
import org.around.ieuri.common.graphql.generated.types.UserError

/* mutation 리졸버의 공통 오류 규약. 예상 실패는 payload의 userErrors로, 그 외는 다시 던져 최상위 error로 보낸다. */

/** 예외를 UserError로 매핑한다. userErrors 대상이 아니면 null(→ 상위에서 재던짐). */
fun toUserErrorOrNull(e: Throwable): UserError? = when (e) {
    is AuthUserException -> userError(e.code, e.message ?: e.code, FIELD_BY_CODE[e.code])

    // Command 생성/도메인 require 위반 등 입력 형식·불변식 오류.
    is IllegalArgumentException -> userError(INVALID_INPUT, e.message ?: "잘못된 입력입니다.")

    else -> null
}

/** 코드가 가리키는 입력 필드 경로. 여기 없는 코드는 `field`가 null(요청 전체·계정 상태의 문제)이다. */
private val FIELD_BY_CODE: Map<String, String> = mapOf(
    "PASSWORD_POLICY_VIOLATION" to "input.password",
    "EMAIL_ALREADY_IN_USE" to "input.email",
    "INVALID_EMAIL_FORMAT" to "input.newEmail",
    "SAME_AS_CURRENT_EMAIL" to "input.newEmail",
    "INVALID_NICKNAME" to "input.nickname",
)

/**
 * `code`(정본, String)와 `codeValue`(enum 사본)를 함께 채운다.
 * - 스키마 enum에 없는 코드는 `codeValue`가 null이다. 클라이언트는 null이면 `code`를 읽는다.
 */
private fun userError(code: String, message: String, field: String? = null): UserError = UserError(
    code = code,
    codeValue = ErrorCode.entries.firstOrNull { it.name == code },
    message = message,
    field = field,
)

private const val INVALID_INPUT = "INVALID_INPUT"

/**
 * 유스케이스 호출을 감싸 예상 실패를 userErrors payload로 바꾼다.
 * [onError]는 userErrors만 채운 실패 payload를 만든다(성공 데이터 필드는 null).
 */
inline fun <T> withUserErrors(onError: (List<UserError>) -> T, block: () -> T): T = try {
    block()
} catch (e: Exception) {
    val userError = toUserErrorOrNull(e) ?: throw e
    onError(listOf(userError))
}
