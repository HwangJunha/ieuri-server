package org.around.ieuri.auth.infrastructure.adapter.`in`.graphql

import org.around.ieuri.auth.application.exception.UserFacingException
import org.around.ieuri.common.graphql.generated.types.ErrorCode
import org.around.ieuri.common.graphql.generated.types.UserError

/*
 * mutation 리졸버의 공통 오류 규약(BaseResponse). 예상 가능한 도메인/입력 실패는 payload의 userErrors로
 * 변환하고, 그 외(인증/인가/토큰/시스템 오류)는 다시 던져 GraphQL 최상위 error로 전파한다.
 */

/** 예외를 UserError로 매핑한다. userErrors 대상이 아니면 null(→ 상위에서 재던짐). */
fun toUserErrorOrNull(e: Throwable): UserError? = when (e) {
    is UserFacingException -> userError(e.code, e.message ?: e.code, FIELD_BY_CODE[e.code])

    // Command 생성/도메인 require 위반 등 입력 형식·불변식 오류.
    is IllegalArgumentException -> userError(INVALID_INPUT, e.message ?: "잘못된 입력입니다.")

    else -> null
}

/**
 * 코드가 특정 입력 필드를 가리키는 경우의 경로. 클라이언트가 그 입력에 오류를 표시한다.
 *
 * 여기 없는 코드는 `field`가 null이다 — 특정 입력이 아니라 요청 전체나 계정 상태의 문제라는 뜻이다.
 */
private val FIELD_BY_CODE: Map<String, String> = mapOf(
    "PASSWORD_POLICY_VIOLATION" to "input.password",
    "EMAIL_ALREADY_IN_USE" to "input.email",
    "INVALID_EMAIL_FORMAT" to "input.newEmail",
    "SAME_AS_CURRENT_EMAIL" to "input.newEmail",
    "INVALID_NICKNAME" to "input.nickname",
)

/**
 * `code`(정본, String)와 `codeValue`(enum 사본)를 함께 채운다.
 *
 * **스키마 enum에 없는 코드는 `codeValue`가 null이다** — 새 코드를 추가할 때 스키마를 함께
 * 고치지 않아도 응답이 깨지지 않게 하려는 것이다. 클라이언트는 `codeValue`로 분기하고
 * null이면 `code`를 읽어 기본 처리한다.
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
