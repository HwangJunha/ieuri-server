package org.around.ieuri.alarm.adapter.graphql.support

import org.around.ieuri.common.graphql.generated.types.ErrorCode
import org.around.ieuri.common.graphql.generated.types.UserError

/**
 * alarm 뮤테이션의 오류 규약(POL-015·POL-016). 사용자 오류는 `INVALID_INPUT` 하나뿐이다.
 * - 인증 실패는 여기서 잡지 않고 최상위 `errors[]`로 나간다.
 */
inline fun <T> withAlarmUserErrors(onError: (List<UserError>) -> T, block: () -> T): T = try {
    block()
} catch (e: IllegalArgumentException) {
    // Command 생성과 도메인 require 위반. 그 밖의 예외는 잡지 않고 최상위로 흘린다.
    onError(listOf(invalidInput(e.message ?: "잘못된 입력입니다.")))
}

fun invalidInput(message: String): UserError = UserError(
    code = INVALID_INPUT,
    codeValue = ErrorCode.entries.firstOrNull { it.name == INVALID_INPUT },
    message = message,
    field = null,
)

const val INVALID_INPUT: String = "INVALID_INPUT"
